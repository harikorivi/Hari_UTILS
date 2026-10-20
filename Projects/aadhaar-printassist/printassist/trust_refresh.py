"""Refresh only known issuer CRLs; never upload a document or expand trust."""
import asyncio
from hashlib import sha256
from pathlib import Path

import requests
from asn1crypto import pem, x509
from pyhanko_certvalidator import CertificateValidator, ValidationContext

from .document import atomic_write
from .errors import UserError
from .validation import Trust

# These are fixed issuer endpoints, not URLs taken from customer PDFs.
CRL_SOURCES = {
    'cca-india-2022.crl': 'https://www.cca.gov.in/rw/resources/CCAIndia2022Latest.crl',
    'ncode-ca-2022.crl': 'http://www.ncodesolutions.com/repository/ncodeca22.crl',
    'ncode-subca-dsc-2022.crl': 'http://www.ncodesolutions.com/repository/ncodeca22subca1.crl',
}
MAX_CRL_BYTES = 8 * 1024 * 1024


def validate_evidence(trust, signer, evidence):
    """Authenticate and check fresh issuer evidence before any installation."""
    if sha256(signer.dump()).hexdigest() not in trust.approved_signers:
        raise UserError('The configured signer is not approved. No trust files were changed.')
    try:
        context = ValidationContext(
            trust_roots=trust.roots, other_certs=trust.intermediates,
            crls=list(evidence), ocsps=trust.ocsps,
            allow_fetching=False, revocation_mode='require',
        )
        validator = CertificateValidator(signer, intermediate_certs=trust.intermediates,
                                         validation_context=context)
        asyncio.run(validator.async_validate_usage({'non_repudiation'}))
    except Exception:
        raise UserError('Issuer evidence is invalid, stale, revoked or incomplete. No trust files were changed.') from None


def refresh(directory: Path):
    trust = Trust.load(directory)
    raw = (directory / 'signers' / 'uidai-06-2026.cer').read_bytes()
    if pem.detect(raw):
        raw = pem.unarmor(raw)[2]
    signer = x509.Certificate.load(raw)
    evidence = {}
    for filename, url in CRL_SOURCES.items():
        try:
            with requests.get(url, stream=True, timeout=(10, 30)) as response:
                response.raise_for_status()
                chunks = []
                size = 0
                for chunk in response.iter_content(65536):
                    size += len(chunk)
                    if size > MAX_CRL_BYTES:
                        raise ValueError()
                    chunks.append(chunk)
                evidence[filename] = b''.join(chunks)
        except Exception:
            raise UserError('An issuer revocation list could not be downloaded. Existing trust files were kept.') from None
    # HTTP transport is acceptable for this signed public evidence because all
    # issuer signatures, applicability, freshness and revocation are checked here.
    # A network attacker cannot replace a CRL with unsigned or out-of-date data.
    validate_evidence(trust, signer, evidence.values())
    for filename, payload in evidence.items():
        atomic_write(directory / 'crls' / filename, lambda stream, data=payload: stream.write(data))
