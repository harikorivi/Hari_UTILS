"""Verify the untouched input bytes, using explicit administrator-managed trust.

No network fetching, system trust fallback, or auto-trust of embedded certificates.
"""
from .errors import UserError

from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from hashlib import sha256
from io import BytesIO
from pathlib import Path
import json
import re
import math

from asn1crypto import pem, x509
from pyhanko.pdf_utils.reader import PdfFileReader
from pyhanko.pdf_utils.misc import PdfStrictReadError
from pyhanko.pdf_utils.crypt.api import PdfMacStatus
from pyhanko.sign.validation import validate_pdf_signature
from pyhanko.sign.validation.status import SignatureCoverageLevel
from pyhanko.sign.diff_analysis import ModificationLevel, SuspiciousModification
from pyhanko_certvalidator import ValidationContext
from .historical import HistoricalResult, inspect_historical_evidence, validate_historically


@dataclass
class SignatureResult:
    status: str = 'warning'
    reason: str = 'Verification could not be completed.'
    signer: str = ''
    issuer: str = ''
    fingerprint_sha256: str = ''
    valid_from: str = ''
    valid_until: str = ''
    integrity: bool | None = None
    entire_file: bool = False
    coverage: str = 'unknown'
    modification_level: str = 'unknown'
    changes_accepted: bool | None = None
    later_revisions: int = 0
    chain_trusted: bool = False
    revocation: str = 'not established'
    approved_uidai_signer: bool = False
    page_index: int | None = None
    signature_rect: tuple[float, float, float, float] | None = None
    validation_basis: str = 'current_time'
    historical: HistoricalResult = field(default_factory=HistoricalResult)


@dataclass
class Report:
    original_sha256: str
    checked_at: str
    status: str = 'warning'
    signatures: list[SignatureResult] = field(default_factory=list)
    parser_mode: str = 'strict'
    diagnostic_code: str = ''
    warnings: list[str] = field(default_factory=list)
    scope: str = 'uploaded_original'
    source_sha256: str = ''
    note: str = ('Application verification of the original PDF; not UIDAI authentication. '
                 'Exported images and print PDFs do not retain the original digital signature.')

    def json(self):
        return json.dumps(asdict(self), indent=2)


@dataclass
class Trust:
    roots: list = field(default_factory=list)
    intermediates: list = field(default_factory=list)
    crls: list = field(default_factory=list)
    ocsps: list = field(default_factory=list)
    approved_signers: set[str] = field(default_factory=set)
    historical_crls: list = field(default_factory=list)
    historical_ocsps: list = field(default_factory=list)

    @classmethod
    def load(cls, directory: Path):
        trust = cls()
        try:
            for sub, target in [('roots', trust.roots), ('intermediates', trust.intermediates)]:
                for path in sorted((directory / sub).glob('*')):
                    if path.suffix.lower() not in ('.pem', '.cer', '.crt', '.der'):
                        continue
                    raw = path.read_bytes()
                    if pem.detect(raw):
                        raw = pem.unarmor(raw)[2]
                    target.append(x509.Certificate.load(raw))
            for sub, target in [('crls', trust.crls), ('ocsps', trust.ocsps),
                                ('historical-crls', trust.historical_crls), ('historical-ocsps', trust.historical_ocsps)]:
                for path in sorted((directory / sub).glob('*')):
                    if path.is_file():
                        raw = path.read_bytes()
                        target.append(pem.unarmor(raw)[2] if pem.detect(raw) else raw)
            pins = directory / 'approved-signers.txt'
            if pins.exists():
                for line in pins.read_text(encoding='utf-8').splitlines():
                    line = line.split('#', 1)[0].replace(':', '').strip().lower()
                    if line:
                        if not re.fullmatch('[0-9a-f]{64}', line):
                            raise UserError()
                        trust.approved_signers.add(line)
        except Exception:
            raise UserError('Trust configuration is invalid; ask the administrator to check it.') from None
        return trust

    def context(self, mode):
        return ValidationContext(
            trust_roots=self.roots, other_certs=self.intermediates,
            crls=self.crls, ocsps=self.ocsps,
            allow_fetching=False, revocation_mode=mode,
        )


COMPATIBILITY_WARNING = (
    'The PDF uses syntax rejected by strict parsing. Compatibility mode checked '
    'the unchanged original bytes, but this parsing limitation prevents a green '
    'verification result. No document repair or rewriting was performed.'
)


class _PdfIntegrityFailure(Exception):
    pass


def _read_signatures(original: bytes, password: str, *, strict: bool):
    reader = PdfFileReader(BytesIO(original), strict=strict)
    if reader.encrypted:
        auth = reader.decrypt(password)
        if not auth.status:
            raise UserError('Signature reader could not authenticate the PDF password.')
        if auth.mac_status == PdfMacStatus.FAILED:
            raise _PdfIntegrityFailure()
    # Keep the reader and its original-byte stream alive throughout validation.
    return reader, list(reader.embedded_regular_signatures)


def assess_document_changes(status):
    """Accept a whole signed file or explicitly assessed archival-only updates."""
    if status.coverage == SignatureCoverageLevel.ENTIRE_FILE:
        return True, 'The signature covers the complete file.'
    if status.coverage == SignatureCoverageLevel.ENTIRE_REVISION:
        if isinstance(status.diff_result, SuspiciousModification) or status.docmdp_ok is False:
            return False, 'Post-signature changes failed the document revision checks.'
        if status.diff_result is None or status.docmdp_ok is not True:
            return None, 'A later PDF revision exists; its changes could not be fully assessed.'
        if status.modification_level in (ModificationLevel.NONE, ModificationLevel.LTA_UPDATES):
            return True, 'Later revisions passed checks for unchanged content or archival validation data.'
        return None, 'Later form or annotation updates require review for this Aadhaar workflow.'
    if status.coverage == SignatureCoverageLevel.UNCLEAR:
        return None, 'The signed byte-range coverage could not be established.'
    return False, 'The signature does not cover a complete PDF revision.'


def extract_signed_version(original: bytes, password: str) -> bytes:
    """Discover a signed prefix; the caller MUST verify that prefix separately.

    Discovery tolerates original-file syntax/MAC failures, but never approves
    that file. The selected prefix undergoes normal verification after opening.
    """
    try:
        reader = PdfFileReader(BytesIO(original), strict=False)
        if reader.encrypted and not reader.decrypt(password).status:
            raise UserError('Could not open the signed version with these customer details.')
        endpoints = []
        for signature in reader.embedded_regular_signatures:
            byte_range = list(signature.byte_range)
            if len(byte_range) != 4 or not all(isinstance(value, int) for value in byte_range):
                continue
            start, length, second, rest = byte_range
            end = second + rest
            if start == 0 and 0 < length < second < end <= len(original):
                endpoints.append(end)
        if not endpoints:
            raise UserError('No extractable signed PDF version was found.')
        return original[:max(endpoints)]
    except UserError:
        raise
    except Exception:
        raise UserError('The original signed version could not be read from this PDF.') from None


def signature_location(reader, signature):
    """Read widget geometry only; missing/ambiguous geometry never gets guessed."""
    try:
        widget = signature.sig_field
        if '/Rect' not in widget:
            kids = widget.get('/Kids', [])
            if len(kids) != 1:
                return None, None
            widget = kids[0].get_object()
        rect = tuple(float(value) for value in widget['/Rect'])
        if len(rect) != 4 or not all(math.isfinite(value) for value in rect):
            return None, None
        if rect[2] <= rect[0] or rect[3] <= rect[1]:
            return None, None
        page_ref = widget.raw_get('/P')
        count = min(10, int(reader.root['/Pages']['/Count']))
        for index in range(count):
            candidate, _ = reader.find_page_for_modification(index)
            if candidate == page_ref:
                return index, rect
    except Exception:
        pass
    return None, None


def verify(original: bytes, password: str, trust: Trust) -> Report:
    report = Report(sha256(original).hexdigest(), datetime.now(timezone.utc).isoformat())
    try:
        try:
            reader, signatures = _read_signatures(original, password, strict=True)
        except PdfStrictReadError:
            # Retry only a strict-syntax rejection, never a bad signature or
            # arbitrary verification error. Both readers receive identical bytes.
            report.parser_mode = 'compatibility'
            report.diagnostic_code = 'strict_pdf_syntax_rejected'
            report.warnings.append(COMPATIBILITY_WARNING)
            reader, signatures = _read_signatures(original, password, strict=False)
        if not signatures:
            if report.parser_mode == 'compatibility':
                report.note = ('Compatibility parsing found no regular document signatures; '
                               'strict parsing failed, so signature absence is not conclusive.')
            else:
                report.status = 'unsigned'
            return report
        for sig in signatures:
            result = SignatureResult()
            report.signatures.append(result)
            try:
                result.page_index, result.signature_rect = signature_location(reader, sig)
                cert = sig.signer_cert
                result.signer = cert.subject.human_friendly
                result.issuer = cert.issuer.human_friendly
                result.fingerprint_sha256 = sha256(cert.dump()).hexdigest()
                result.valid_from = cert['tbs_certificate']['validity']['not_before'].native.isoformat()
                result.valid_until = cert['tbs_certificate']['validity']['not_after'].native.isoformat()
                result.approved_uidai_signer = result.fingerprint_sha256 in trust.approved_signers
                # Both passes are real cryptographic validations. The first separates
                # basic path trust from required fresh revocation evidence.
                basic = validate_pdf_signature(sig, signer_validation_context=trust.context('soft-fail'))
                result.integrity = bool(basic.intact and basic.valid)
                result.entire_file = basic.coverage == SignatureCoverageLevel.ENTIRE_FILE
                result.coverage = basic.coverage.name if basic.coverage is not None else 'unknown'
                result.modification_level = basic.modification_level.name if basic.modification_level is not None else 'unknown'
                result.changes_accepted, change_reason = assess_document_changes(basic)
                result.later_revisions = reader.xrefs.total_revisions - sig.signed_revision - 1
                if not result.entire_file and result.later_revisions == 0:
                    result.changes_accepted = False
                    change_reason = 'Unexplained trailing bytes exist outside the signed PDF revision.'
                result.chain_trusted = bool(basic.trusted)
                if not result.integrity:
                    result.status = 'invalid'
                    result.reason = 'The signed-byte digest or cryptographic signature check failed.'
                    continue
                strict = validate_pdf_signature(sig, signer_validation_context=trust.context('require'))
                if basic.revoked or strict.revoked:
                    result.status = 'invalid'
                    result.revocation = 'revoked'
                    result.reason = 'Signing certificate is revoked.'
                else:
                    if strict.trusted:
                        result.revocation = 'verified with required evidence'
                    if result.changes_accepted is False:
                        result.status = 'invalid'
                        result.reason = change_reason
                    elif result.changes_accepted is None:
                        result.reason = change_reason
                    elif strict.bottom_line and strict.trusted and result.approved_uidai_signer:
                        result.status = 'valid'
                        result.reason = 'Integrity, document revision, trust, revocation and approved signer checks passed.'
                    elif strict.bottom_line and strict.trusted:
                        result.reason = 'Signature verified, but this certificate is not an approved UIDAI signer.'
                    else:
                        result.reason = 'Integrity passed; current certificate trust or revocation evidence is incomplete.'
            except Exception:
                # Parser, unsupported algorithm and evidence failures are unknown,
                # never mistaken for a valid signature or absence of a signature.
                result.reason = 'Verification incomplete; certificate, algorithm or evidence needs review.'
            if result.integrity:
                try:
                    result.historical = inspect_historical_evidence(sig, trust)
                    if result.status == 'valid':
                        result.historical.status = 'not_needed'
                        result.historical.reason = 'Current-time verification passed; historical fallback was not required.'
                    elif result.changes_accepted is True:
                        result.historical = validate_historically(sig, trust, result.historical)
                        if result.historical.status == 'passed' and result.approved_uidai_signer:
                            result.status = 'valid'
                            result.validation_basis = 'trusted_historical_time'
                            result.chain_trusted = True
                            result.revocation = 'verified at independently trusted historical time'
                            result.reason = result.historical.reason
                except Exception:
                    result.historical.status = 'indeterminate'
                    result.historical.reason = 'Historical evidence inspection was incomplete.'
        if report.parser_mode == 'compatibility':
            for result in report.signatures:
                if result.status == 'valid':
                    result.status = 'warning'
                    result.reason = ('Cryptographic, trust and signer checks passed in compatibility '
                                     'mode; strict PDF syntax checks did not pass.')
        statuses = {s.status for s in report.signatures}
        report.status = ('invalid' if 'invalid' in statuses else
                         'valid' if statuses == {'valid'} else 'warning')
    except _PdfIntegrityFailure:
        report.status = 'invalid'
        report.diagnostic_code = 'pdf_encryption_integrity_failed'
        report.note = 'The PDF encryption integrity check failed. Do not treat this document as verified.'
    except UserError:
        report.status = 'warning'
        report.diagnostic_code = 'signature_reader_authentication_failed'
        report.note = 'Signature reader could not authenticate the PDF. Check the customer details.'
    except Exception:
        report.status = 'warning'
        if report.parser_mode == 'compatibility':
            report.diagnostic_code = 'compatibility_signature_read_failed'
            report.note = ('Strict PDF parsing rejected the document, and compatibility mode could not '
                           'read its signatures. Signature verification is incomplete.')
        else:
            report.diagnostic_code = 'signature_read_failed'
            report.note = ('The signature reader could not parse the PDF or its embedded signature data. '
                           'Signature verification is incomplete.')
    return report
