"""Synthetic documents only. Never use real customer Aadhaar in test fixtures."""
from datetime import datetime, timedelta, timezone
from io import BytesIO

import pytest
from asn1crypto import keys, x509 as asn_x509
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID
from pyhanko.pdf_utils import generic
from pyhanko.pdf_utils.writer import PdfFileWriter, PageObject
from pyhanko.sign import signers
from pyhanko.sign.fields import SigFieldSpec
from pyhanko_certvalidator.registry import SimpleCertificateStore


@pytest.fixture(scope='session')
def certificates():
    now = datetime.now(timezone.utc)
    root_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    leaf_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    root_name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, 'PrintAssist TEST CA - NOT UIDAI')])
    leaf_name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, 'Synthetic document signer - NOT UIDAI')])
    root = (x509.CertificateBuilder().subject_name(root_name).issuer_name(root_name)
            .public_key(root_key.public_key()).serial_number(1001)
            .not_valid_before(now-timedelta(days=2)).not_valid_after(now+timedelta(days=30))
            .add_extension(x509.BasicConstraints(ca=True, path_length=0), critical=True)
            .add_extension(x509.KeyUsage(False, False, False, False, False, True, True, False, False), critical=True)
            .add_extension(x509.SubjectKeyIdentifier.from_public_key(root_key.public_key()), critical=False)
            .sign(root_key, hashes.SHA256()))
    leaf = (x509.CertificateBuilder().subject_name(leaf_name).issuer_name(root_name)
            .public_key(leaf_key.public_key()).serial_number(1002)
            .not_valid_before(now-timedelta(days=1)).not_valid_after(now+timedelta(days=10))
            .add_extension(x509.BasicConstraints(ca=False, path_length=None), critical=True)
            .add_extension(x509.KeyUsage(True, True, False, False, False, False, False, False, False), critical=True)
            .add_extension(x509.AuthorityKeyIdentifier.from_issuer_public_key(root_key.public_key()), critical=False)
            .add_extension(x509.SubjectKeyIdentifier.from_public_key(leaf_key.public_key()), critical=False)
            .sign(root_key, hashes.SHA256()))
    def crl(revoked=False, stale=False):
        builder = (x509.CertificateRevocationListBuilder().issuer_name(root_name)
                   .last_update(now-timedelta(days=3 if stale else 1))
                   .next_update(now-timedelta(days=2) if stale else now+timedelta(days=1))
                   .add_extension(x509.AuthorityKeyIdentifier.from_issuer_public_key(root_key.public_key()), critical=False))
        if revoked:
            builder = builder.add_revoked_certificate(x509.RevokedCertificateBuilder()
                .serial_number(1002).revocation_date(now-timedelta(hours=2)).build())
        return builder.sign(root_key, hashes.SHA256()).public_bytes(serialization.Encoding.DER)
    root_asn = asn_x509.Certificate.load(root.public_bytes(serialization.Encoding.DER))
    leaf_asn = asn_x509.Certificate.load(leaf.public_bytes(serialization.Encoding.DER))
    key_asn = keys.PrivateKeyInfo.load(leaf_key.private_bytes(serialization.Encoding.DER,
                       serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
    store = SimpleCertificateStore()
    store.register(root_asn)
    signer = signers.SimpleSigner(leaf_asn, key_asn, store)
    return root_asn, leaf_asn, signer, crl


def make_pdf(signer=None, encrypted=True, pages=1, signature_box=None, page_size=(595,842), rotation=0):
    writer = PdfFileWriter()
    # Uncompressed stream makes a byte-preserving tampering test possible.
    for _ in range(pages):
        stream = writer.add_object(generic.StreamObject(stream_data=b'0.1 0.4 0.7 rg 40 40 250 120 re f\n'))
        page = PageObject(stream, (0, 0, *page_size))
        if rotation:
            page['/Rotate'] = generic.NumberObject(rotation)
        writer.insert_page(page)
    if encrypted:
        writer.encrypt('synthetic-owner-secret', 'SURE1990')
    output = BytesIO()
    if signer:
        signers.sign_pdf(writer, signers.PdfSignatureMetadata(field_name='Signature1'),
                         signer, new_field_spec=SigFieldSpec('Signature1', box=signature_box or (0,0,0,0)), output=output)
    else:
        writer.write(output)
    return output.getvalue()


@pytest.fixture
def pdf_factory():
    return make_pdf
