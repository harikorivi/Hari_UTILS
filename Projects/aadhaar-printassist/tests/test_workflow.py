from hashlib import sha256
from contextlib import closing
from pathlib import Path

import pypdfium2 as pdfium
import pytest
from PIL import Image

from printassist.document import Job, MAX_FILE, PrintSettings, atomic_write
from printassist.passwords import password_for
from printassist.validation import Trust, verify


@pytest.mark.parametrize('name,birth,expected', [
    ('SURESH KUMAR', '1990', 'SURE1990'), ('SAI KUMAR','1990','SAIK1990'),
    ('P. KUMAR','1990','P.KU1990'), ('RIA','1990','RIA1990'),
    ('  Ramesh  Kumar ', '15/08/1995', 'RAME1995'), ('Suresh Kumar', '1990-05-22','SURE1990')])
def test_uidai_examples(name,birth,expected):
    assert password_for(name,birth) == expected


@pytest.mark.parametrize('name,birth', [('', '1990'), ('12345','1990'), ('Suresh','31/02/1990'),
                                       ('Suresh','2999'), ('Suresh','90'), ('सुरेश','1990')])
def test_invalid_details(name,birth):
    with pytest.raises(ValueError):
        password_for(name,birth)


def trust_for(certificates, *, evidence=True, approved=True, revoked=False, stale=False):
    root, leaf, _, crl = certificates
    return Trust(roots=[root], crls=[crl(revoked, stale)] if evidence else [],
                 approved_signers={sha256(leaf.dump()).hexdigest()} if approved else set())


def test_genuine_green_with_full_evidence(certificates,pdf_factory):
    raw = pdf_factory(certificates[2])
    report = verify(raw,'SURE1990',trust_for(certificates))
    assert report.status == 'valid', report.json()
    assert report.signatures[0].integrity
    assert report.signatures[0].entire_file
    assert report.signatures[0].chain_trusted
    assert report.original_sha256 == sha256(raw).hexdigest()
    assert 'SURE1990' not in report.json()


@pytest.mark.parametrize('evidence,approved', [(False,True),(True,False),(False,False)])
def test_missing_evidence_or_identity_never_green(certificates,pdf_factory,evidence,approved):
    report = verify(pdf_factory(certificates[2]), 'SURE1990',
                    trust_for(certificates,evidence=evidence,approved=approved))
    assert report.status == 'warning', report.json()
    assert report.signatures[0].integrity is True


def test_untrusted_embedded_root_not_accepted(certificates,pdf_factory):
    report = verify(pdf_factory(certificates[2]),'SURE1990',Trust())
    assert report.status == 'warning'
    assert not report.signatures[0].chain_trusted


def test_revoked_never_green(certificates,pdf_factory):
    report = verify(pdf_factory(certificates[2]),'SURE1990',trust_for(certificates,revoked=True))
    assert report.status == 'invalid', report.json()


def test_stale_evidence_never_green(certificates,pdf_factory):
    report = verify(pdf_factory(certificates[2]),'SURE1990',trust_for(certificates,stale=True))
    assert report.status == 'warning', report.json()


def test_unsigned_and_malformed(pdf_factory):
    assert verify(pdf_factory(), 'SURE1990',Trust()).status == 'unsigned'
    assert verify(b'not a PDF','SURE1990',Trust()).status == 'warning'


def test_document_tamper(certificates,pdf_factory):
    raw = pdf_factory(certificates[2],encrypted=False)
    assert b'250 120' in raw
    raw = raw.replace(b'250 120',b'251 120',1)
    report = verify(raw,'',trust_for(certificates))
    assert report.status == 'invalid', report.json()


def test_unsigned_trailing_bytes_rejected(certificates,pdf_factory):
    raw = pdf_factory(certificates[2]) + b'\n% post-signing addition\n'
    assert verify(raw,'SURE1990',trust_for(certificates)).status == 'invalid'


def test_open_preview_export_original_and_cleanup(tmp_path,certificates,pdf_factory):
    raw = pdf_factory(certificates[2],pages=2)
    original = tmp_path/'input.pdf'
    original.write_bytes(raw)
    job = Job.open(original,'Suresh Kumar','1990',trust_for(certificates))
    assert len(job) == 2
    assert job.report.status == 'valid'
    image = job.render(0,150)
    assert image.width > 1000
    image.close()
    for extension in ('jpg','png'):
        path = tmp_path/f'copy.{extension}'
        job.export_image(path,0)
        with Image.open(path) as exported:
            assert exported.width > 1000
    output = tmp_path/'print.pdf'
    job.export_pdf(output,PrintSettings())
    with pdfium.PdfDocument(output) as doc:
        assert len(doc) == 2
        with closing(doc[0]) as page:
            assert page.get_size() == (595,842)
    assert verify(output.read_bytes(),'',Trust()).status == 'unsigned'
    landscape = tmp_path/'landscape.pdf'
    job.export_pdf(landscape,PrintSettings(150,True,75))
    with pdfium.PdfDocument(landscape) as doc:
        with closing(doc[0]) as page:
            assert page.get_size() == (842,595)
    preserved = tmp_path/'original-copy.pdf'
    job.save_original(preserved)
    assert preserved.read_bytes() == raw
    assert original.read_bytes() == raw
    job.close()
    assert job.original == b'' and job.document is None and job.report is None
    assert not list(tmp_path.glob('.printassist-*'))


def test_wrong_password_and_limits(tmp_path,pdf_factory):
    path = tmp_path/'input.pdf'
    path.write_bytes(pdf_factory())
    with pytest.raises(ValueError,match='Password did not match'):
        Job.open(path,'Wrong Name','1990',Trust())
    path.write_bytes(pdf_factory(pages=11))
    with pytest.raises(ValueError,match='1–10'):
        Job.open(path,'Suresh Kumar','1990',Trust())
    path.write_bytes(b'%PDF-'+b'0'*MAX_FILE)
    with pytest.raises(ValueError,match='20 MB'):
        Job.open(path,'Suresh Kumar','1990',Trust())


def test_failed_export_does_not_leave_temporary_or_overwrite(tmp_path):
    path = tmp_path/'existing.pdf'
    path.write_bytes(b'original')
    def fail(stream):
        stream.write(b'sensitive')
        raise OSError('disk unavailable')
    with pytest.raises(OSError):
        atomic_write(path,fail)
    assert path.read_bytes() == b'original'
    assert not list(tmp_path.glob('.printassist-*'))


def test_bad_trust_config_rejected(tmp_path):
    (tmp_path/'approved-signers.txt').write_text('not-a-fingerprint')
    with pytest.raises(ValueError,match='Trust configuration'):
        Trust.load(tmp_path)
