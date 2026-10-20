"""Strict xref rejection must not conceal cryptographic results or become green."""
from hashlib import sha256
from io import BytesIO
import re

import pytest
from pyhanko.pdf_utils.incremental_writer import IncrementalPdfFileWriter
from pyhanko.pdf_utils.misc import PdfStrictReadError
from pyhanko.pdf_utils.reader import PdfFileReader
from pyhanko.sign import signers
from pyhanko.sign.fields import SigFieldSpec

from printassist import validation
from printassist.validation import Trust, verify
from test_workflow import trust_for


def legacy_xref_pdf(pdf_factory, signer=None):
    """Introduce the strict-only xref incompatibility BEFORE signing."""
    base = pdf_factory(encrypted=False)
    reader = PdfFileReader(BytesIO(base))
    root = reader.trailer.raw_get('/Root')
    previous = re.findall(rb'startxref\s+(\d+)', base)[-1].decode('ascii')
    trailer = (f'xref\n999 1\n0000000000 00000 f \ntrailer\n'
               f'<< /Size 1000 /Root {root.idnum} {root.generation} R /Prev {previous} >>\n'
               f'startxref\n{len(base)}\n%%EOF\n').encode('ascii')
    original = base + trailer
    if signer:
        writer = IncrementalPdfFileWriter(BytesIO(original), strict=False)
        output = BytesIO()
        signers.sign_pdf(writer, signers.PdfSignatureMetadata(field_name='Signature1'),
                         signer, new_field_spec=SigFieldSpec('Signature1'), output=output)
        original = output.getvalue()
    return original


def test_real_legacy_xref_checks_signature_without_green(certificates,pdf_factory):
    raw = legacy_xref_pdf(pdf_factory,certificates[2])
    with pytest.raises(PdfStrictReadError):
        PdfFileReader(BytesIO(raw),strict=True)
    report = verify(raw,'',trust_for(certificates))
    assert report.parser_mode == 'compatibility', report.json()
    assert report.diagnostic_code == 'strict_pdf_syntax_rejected'
    assert report.warnings
    assert report.status == 'warning', report.json()
    sig = report.signatures[0]
    assert sig.integrity and sig.entire_file and sig.chain_trusted
    assert sig.approved_uidai_signer
    assert sig.revocation == 'verified with required evidence'
    assert sig.status == 'warning'
    assert report.original_sha256 == sha256(raw).hexdigest()


def test_legacy_xref_tamper_still_invalid(certificates,pdf_factory):
    raw = legacy_xref_pdf(pdf_factory,certificates[2]).replace(b'250 120',b'251 120',1)
    report = verify(raw,'',trust_for(certificates))
    assert report.parser_mode == 'compatibility'
    assert report.status == 'invalid',report.json()
    assert report.signatures[0].integrity is False


def test_legacy_xref_no_signature_remains_incomplete(pdf_factory):
    report = verify(legacy_xref_pdf(pdf_factory),'',Trust())
    assert report.status == 'warning'
    assert report.parser_mode == 'compatibility'
    assert 'not conclusive' in report.note


def test_generic_parser_failure_is_not_retried(monkeypatch):
    calls = []
    def fail(*args,**kwargs):
        calls.append(kwargs['strict'])
        raise RuntimeError('do not expose arbitrary document text')
    monkeypatch.setattr(validation,'PdfFileReader',fail)
    report = verify(b'%PDF-bad','',Trust())
    assert calls == [True]
    assert report.diagnostic_code == 'signature_read_failed'
    assert 'arbitrary document text' not in report.json()


def test_failed_compatibility_reports_specific_safe_failure(monkeypatch):
    calls = []
    def fail(*args,**kwargs):
        calls.append(kwargs['strict'])
        if kwargs['strict']:
            raise PdfStrictReadError('private parser diagnostic')
        raise RuntimeError('private customer data')
    monkeypatch.setattr(validation,'PdfFileReader',fail)
    report = verify(b'%PDF-bad','',Trust())
    assert calls == [True,False]
    assert report.status == 'warning'
    assert report.diagnostic_code == 'compatibility_signature_read_failed'
    assert 'private' not in report.json()


def test_encrypted_compatibility_uses_same_original_bytes(monkeypatch,certificates,pdf_factory):
    raw = pdf_factory(certificates[2])
    actual = validation.PdfFileReader
    inputs = []
    def strict_rejection(stream,**kwargs):
        inputs.append((stream.getvalue(), kwargs['strict']))
        if kwargs['strict']:
            raise PdfStrictReadError('synthetic strict-only rejection')
        return actual(stream,**kwargs)
    monkeypatch.setattr(validation,'PdfFileReader',strict_rejection)
    report = verify(raw,'SURE1990',trust_for(certificates))
    assert inputs == [(raw,True),(raw,False)]
    assert report.status == 'warning'
    assert report.signatures[0].integrity and report.signatures[0].entire_file
    assert 'SURE1990' not in report.json()
