from io import BytesIO
from types import SimpleNamespace

from pyhanko.pdf_utils import generic
from pyhanko.pdf_utils.incremental_writer import IncrementalPdfFileWriter
from pyhanko.sign.diff_analysis import DiffResult, ModificationLevel, SuspiciousModification
from pyhanko.sign.validation.status import SignatureCoverageLevel as Coverage

from printassist.validation import assess_document_changes, verify
from test_workflow import trust_for


def test_real_archival_update_can_verify(certificates,pdf_factory):
    original=pdf_factory(certificates[2],encrypted=False)
    writer=IncrementalPdfFileWriter(BytesIO(original))
    writer.root['/DSS']=writer.add_object(generic.DictionaryObject())
    writer.update_root()
    output=BytesIO()
    writer.write(output)
    report=verify(output.getvalue(),'',trust_for(certificates))
    assert report.status=='valid',report.json()
    sig=report.signatures[0]
    assert sig.integrity and not sig.entire_file and sig.changes_accepted
    assert sig.coverage=='ENTIRE_REVISION'
    assert sig.modification_level=='LTA_UPDATES'
    assert sig.revocation=='verified with required evidence'


def test_real_changed_page_is_rejected(certificates,pdf_factory):
    original=pdf_factory(certificates[2],encrypted=False)
    writer=IncrementalPdfFileWriter(BytesIO(original))
    page_ref=writer.root['/Pages']['/Kids'].raw_get(0)
    page=page_ref.get_object()
    page['/Contents']=writer.add_object(generic.StreamObject(stream_data=b'0 0 0 rg 0 0 595 842 re f'))
    writer.mark_update(page_ref)
    output=BytesIO();writer.write(output)
    report=verify(output.getvalue(),'',trust_for(certificates))
    assert report.status=='invalid',report.json()
    assert report.signatures[0].integrity
    assert report.signatures[0].changes_accepted is False
    assert report.signatures[0].revocation=='verified with required evidence'
    assert 'revision checks' in report.signatures[0].reason


def test_unassessed_revision_is_incomplete():
    result=SimpleNamespace(coverage=Coverage.ENTIRE_REVISION,diff_result=None,docmdp_ok=None)
    assert assess_document_changes(result)[0] is None


def test_unencrypted_trailing_bytes_are_not_a_permitted_revision(certificates,pdf_factory):
    raw=pdf_factory(certificates[2],encrypted=False)+b'\n% appended outside revisions\n'
    report=verify(raw,'',trust_for(certificates))
    assert report.status=='invalid',report.json()
    assert 'trailing bytes' in report.signatures[0].reason


def test_form_updates_require_review_for_aadhaar():
    result=SimpleNamespace(coverage=Coverage.ENTIRE_REVISION,
        diff_result=DiffResult(ModificationLevel.FORM_FILLING,set()),
        modification_level=ModificationLevel.FORM_FILLING,docmdp_ok=True)
    assert assess_document_changes(result)[0] is None


def test_signed_version_recovery_preserves_upload_and_verifies_selected_bytes(tmp_path,certificates,pdf_factory):
    from printassist.document import Job
    from hashlib import sha256
    original=pdf_factory(certificates[2])
    uploaded=original+b'\n% later unsigned bytes\n'
    source=tmp_path/'customer.pdf';source.write_bytes(uploaded)
    job=Job.open(source,'Suresh Kumar','1990',trust_for(certificates),signed_version=True)
    try:
        assert job.report.status=='valid',job.report.json()
        assert job.report.scope=='signed_revision'
        assert job.report.parser_mode=='strict'
        assert job.original==original
        assert job.source_original==uploaded
        assert job.report.original_sha256==sha256(original).hexdigest()
        assert job.report.source_sha256==sha256(uploaded).hexdigest()
        saved=tmp_path/'saved-upload.pdf';job.save_original(saved)
        assert saved.read_bytes()==uploaded
        assert source.read_bytes()==uploaded
    finally:
        job.close()
    assert job.source_original==b''


def test_recovery_never_repairs_tampered_signed_content(tmp_path,certificates,pdf_factory):
    from printassist.document import Job
    raw=pdf_factory(certificates[2],encrypted=False).replace(b'250 120',b'251 120',1)
    source=tmp_path/'customer.pdf';source.write_bytes(raw+b'\n% unsigned bytes\n')
    job=Job.open(source,'Suresh Kumar','1990',trust_for(certificates),signed_version=True)
    try:
        assert job.report.status=='invalid'
    finally:
        job.close()


def test_unsigned_document_has_no_recoverable_signed_version(tmp_path,pdf_factory):
    import pytest
    from printassist.document import Job
    from printassist.validation import Trust
    source=tmp_path/'unsigned.pdf';source.write_bytes(pdf_factory())
    with pytest.raises(ValueError,match='No extractable'):
        Job.open(source,'Suresh Kumar','1990',Trust(),signed_version=True)
