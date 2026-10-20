"""Offline historical verification with authenticated timestamp/AdES evidence.

A claimed PDF/CMS date or operator date is never treated as proof of existence.
"""
import asyncio
from dataclasses import dataclass,field
from datetime import datetime,timezone

from asn1crypto import crl,ocsp
from cryptography import x509 as crypto_x509
from cryptography.hazmat.primitives import hashes
from pyhanko.sign.ades.report import AdESPassed
from pyhanko.sign.validation.ades import ades_lta_validation
from pyhanko.sign.validation.settings import KeyUsageConstraints
from pyhanko.sign.validation.policy_decl import (
    LocalKnowledge,PdfSignatureValidationSpec,SignatureValidationSpec,
    RevocationInfoGatheringSpec,RevinfoOnlineFetchingRule,
)
from pyhanko_certvalidator.context import CertValidationPolicySpec
from pyhanko_certvalidator.policy_decl import CertRevTrustPolicy,RevocationCheckingPolicy
from pyhanko_certvalidator.registry import SimpleTrustManager
from pyhanko_certvalidator.revinfo.archival import CRLContainer,OCSPContainer


@dataclass
class HistoricalResult:
    status: str = 'not_checked'
    reason: str = ''
    certificate_expired_now: bool = False
    issuer_chain_authenticated: bool = False
    claimed_signing_time: str = ''
    signature_timestamp_tokens: int = 0
    document_timestamps: int = 0
    dss_present: bool = False
    configured_crls: int = 0
    configured_ocsps: int = 0
    trusted_time: str = ''
    trusted_time_source: str = ''
    chain_trusted_at_proven_time: bool = False
    revocation_established_at_proven_time: bool = False
    ades_subindication: str = ''
    reason_codes: list[str] = field(default_factory=list)
    revocation_evidence: list[dict] = field(default_factory=list)


def _issuer_links_authenticated(leaf,roots,candidates):
    """Issuer signature linkage only; not a temporal/revocation verdict."""
    roots=[crypto_x509.load_der_x509_certificate(cert.dump()) for cert in roots]
    candidates=[crypto_x509.load_der_x509_certificate(cert.dump()) for cert in candidates]
    def follow(cert,visited):
        if any(cert==root for root in roots):return True
        if len(visited)>=8:return False
        for issuer in candidates+roots:
            identity=issuer.fingerprint(hashes.SHA256())
            if (issuer.subject.rfc4514_string(),identity) in visited:continue
            try:
                if not issuer.extensions.get_extension_for_class(crypto_x509.BasicConstraints).value.ca:continue
                cert.verify_directly_issued_by(issuer)
                if follow(issuer,visited|{(issuer.subject.rfc4514_string(),identity)}):return True
            except Exception:continue
        return False
    return follow(crypto_x509.load_der_x509_certificate(leaf.dump()),set())


def inspect_historical_evidence(signature,trust):
    result=HistoricalResult()
    cert=signature.signer_cert
    result.certificate_expired_now=datetime.now(timezone.utc)>cert.not_valid_after
    for attr in signature.signer_info['signed_attrs']:
        if attr['type'].native=='signing_time' and len(attr['values']):
            value=attr['values'][0].native
            result.claimed_signing_time=value.isoformat() if hasattr(value,'isoformat') else ''
    result.signature_timestamp_tokens=sum(len(attr['values']) for attr in signature.signer_info['unsigned_attrs']
        if attr['type'].native=='signature_time_stamp_token')
    result.document_timestamps=len(signature.reader.embedded_timestamp_signatures)
    result.dss_present='/DSS' in signature.reader.root
    embedded=[item.chosen for item in signature.signed_data['certificates'] if item.name=='certificate']
    result.issuer_chain_authenticated=_issuer_links_authenticated(cert,trust.roots,trust.intermediates+embedded)
    all_crls=trust.crls+trust.historical_crls
    result.configured_crls=len(all_crls)
    result.configured_ocsps=len(trust.ocsps+trust.historical_ocsps)
    candidates=[crypto_x509.load_der_x509_certificate(item.dump()) for item in trust.roots+trust.intermediates+embedded]
    for raw in all_crls:
        try:
            parsed=crypto_x509.load_der_x509_crl(raw)
            valid=any(parsed.issuer==issuer.subject and parsed.is_signature_valid(issuer.public_key()) for issuer in candidates)
            result.revocation_evidence.append({
                'this_update':parsed.last_update_utc.isoformat(),
                'next_update':parsed.next_update_utc.isoformat() if parsed.next_update_utc else '',
                'issuer_signature_valid':valid,
                'matches_signer_issuer':parsed.issuer==crypto_x509.load_der_x509_certificate(cert.dump()).issuer,
                'issued_after_signer_expiry':parsed.last_update_utc>cert.not_valid_after,
            })
        except Exception:
            result.revocation_evidence.append({'parse_failed':True})
    return result


def validate_historically(signature,trust,result=None):
    result=result or inspect_historical_evidence(signature,trust)
    result.status='indeterminate'
    if not result.signature_timestamp_tokens and not result.document_timestamps:
        result.reason='No independently trusted timestamp proves when this signature existed. Historical validity is indeterminate.'
        result.reason_codes.append('missing_trusted_time_evidence')
        return result
    try:
        policy=CertValidationPolicySpec(
            trust_manager=SimpleTrustManager.build(trust_roots=trust.roots),
            revinfo_policy=CertRevTrustPolicy(RevocationCheckingPolicy.from_legacy('require')),
        )
        knowledge=LocalKnowledge(
            known_certs=trust.intermediates,
            known_crls=[CRLContainer(crl.CertificateList.load(raw)) for raw in trust.crls+trust.historical_crls],
            known_ocsps=[OCSPContainer(ocsp.OCSPResponse.load(raw)) for raw in trust.ocsps+trust.historical_ocsps],
        )
        spec=SignatureValidationSpec(
            cert_validation_policy=policy,ts_cert_validation_policy=policy,
            revinfo_gathering_policy=RevocationInfoGatheringSpec(
                online_fetching_rule=RevinfoOnlineFetchingRule.LOCAL_ONLY),
            local_knowledge=knowledge,
            key_usage_settings=KeyUsageConstraints(key_usage={"non_repudiation"}),
        )
        validation=asyncio.run(ades_lta_validation(signature,PdfSignatureValidationSpec(spec)))
        result.ades_subindication=validation.ades_subindic.standard_name
        timestamp_result=validation.signature_timestamp_status
        trusted_signature_ts=(timestamp_result is not None and timestamp_result.ades_subindic==AdESPassed.OK)
        trusted_document_ts=validation.oldest_evidence_record_timestamp is not None
        api=validation.api_status
        passed=(validation.ades_subindic==AdESPassed.OK and api is not None
                and api.intact and api.valid and api.trusted and api.bottom_line
                and (trusted_signature_ts or trusted_document_ts))
        if passed:
            result.status='passed'
            result.trusted_time=validation.best_signature_time.isoformat()
            result.trusted_time_source=('validated RFC 3161 signature timestamp' if trusted_signature_ts
                                       else 'validated PDF document timestamp chain')
            result.chain_trusted_at_proven_time=True
            result.revocation_established_at_proven_time=True
            result.reason='Historical signature, trusted time, certificate path and required revocation checks passed.'
        else:
            result.reason='Timestamp, historical certificate path or historical revocation evidence is incomplete or untrusted.'
            result.reason_codes.append('historical_validation_not_passed')
    except Exception:
        result.reason='Historical evidence could not be fully validated. No historical trust is assumed.'
        result.reason_codes.append('historical_validation_incomplete')
    return result
