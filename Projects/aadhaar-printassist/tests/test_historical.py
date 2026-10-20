"""Synthetic archived PKI: never customer identity data."""
from datetime import datetime,timedelta,timezone
from io import BytesIO
from unittest.mock import patch
import pytest
from asn1crypto import keys,x509 as ax,crl as ac
from cryptography import x509
from cryptography.hazmat.primitives import hashes,serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID,ExtendedKeyUsageOID
from pyhanko.pdf_utils import generic
from pyhanko.pdf_utils.writer import PdfFileWriter,PageObject
from pyhanko.sign import signers
from pyhanko.pdf_utils.incremental_writer import IncrementalPdfFileWriter
from pyhanko.sign.validation.dss import DocumentSecurityStore
from pyhanko.sign.fields import SigFieldSpec
from pyhanko.sign.timestamps import DummyTimeStamper
from pyhanko_certvalidator.registry import SimpleCertificateStore
from printassist.validation import Trust,verify

@pytest.fixture(scope='module')
def archive():
    now=datetime.now(timezone.utc); past=now-timedelta(days=20)
    rk=rsa.generate_private_key(public_exponent=65537,key_size=2048)
    lk=rsa.generate_private_key(public_exponent=65537,key_size=2048)
    tk=rsa.generate_private_key(public_exponent=65537,key_size=2048)
    rn=x509.Name([x509.NameAttribute(NameOID.COMMON_NAME,'Historical TEST CA - NOT UIDAI')])
    def cert(key,serial,ca=False,tsa=False):
        subject=rn if ca else x509.Name([x509.NameAttribute(NameOID.COMMON_NAME,'TEST TSA' if tsa else 'TEST signer')])
        b=(x509.CertificateBuilder().subject_name(subject).issuer_name(rn).public_key(key.public_key())
           .serial_number(serial).not_valid_before(now-timedelta(days=60))
           .not_valid_after(now+timedelta(days=365) if ca or tsa else now-timedelta(days=10))
           .add_extension(x509.BasicConstraints(ca=ca,path_length=0 if ca else None),True)
           .add_extension(x509.SubjectKeyIdentifier.from_public_key(key.public_key()),False)
           .add_extension(x509.AuthorityKeyIdentifier.from_issuer_public_key(rk.public_key()),False)
           .add_extension(x509.KeyUsage(not ca,not ca and not tsa,False,False,False,ca,ca,False,False),True))
        if tsa:b=b.add_extension(x509.ExtendedKeyUsage([ExtendedKeyUsageOID.TIME_STAMPING]),True)
        return ax.Certificate.load(b.sign(rk,hashes.SHA256()).public_bytes(serialization.Encoding.DER))
    root=cert(rk,9001,ca=True); leaf=cert(lk,9002); tsa=cert(tk,9003,tsa=True)
    def pk(key):return keys.PrivateKeyInfo.load(key.private_bytes(serialization.Encoding.DER,serialization.PrivateFormat.PKCS8,serialization.NoEncryption()))
    store=SimpleCertificateStore();store.register(root)
    signer=signers.SimpleSigner(leaf,pk(lk),store)
    stamp=DummyTimeStamper(tsa,pk(tk),store,fixed_dt=past)
    def crl(start,end,revoked=False,revocation_days=25):
        b=(x509.CertificateRevocationListBuilder().issuer_name(rn).last_update(start).next_update(end)
           .add_extension(x509.AuthorityKeyIdentifier.from_issuer_public_key(rk.public_key()),False))
        if revoked:b=b.add_revoked_certificate(x509.RevokedCertificateBuilder().serial_number(9002).revocation_date(now-timedelta(days=revocation_days)).build())
        return b.sign(rk,hashes.SHA256()).public_bytes(serialization.Encoding.DER)
    current=crl(now-timedelta(days=1),now+timedelta(days=1),True,11)
    class HistoricalClock(datetime):
        @classmethod
        def now(cls,tz=None):return past.astimezone(tz)
    def pdf(timestamp=True,archived=True,revoked=False):
        w=PdfFileWriter(); stream=w.add_object(generic.StreamObject(stream_data=b'0.1 0.4 0.7 rg 40 40 250 120 re f\n'))
        w.insert_page(PageObject(stream,(0,0,595,842))); out=BytesIO()
        with patch('pyhanko.sign.signers.pdf_signer.datetime',HistoricalClock):
            signers.sign_pdf(w,signers.PdfSignatureMetadata(field_name='Signature1'),signer,
                timestamper=stamp if timestamp else None,new_field_spec=SigFieldSpec('Signature1',box=(380,180,570,260)),output=out)
        if timestamp:
            DocumentSecurityStore.add_dss(out,None,certs=[leaf,tsa,root],
                crls=[ac.CertificateList.load(crl(now-timedelta(days=19),now-timedelta(days=5),revoked))] if archived else [])
            out.seek(0)
            stamped=BytesIO()
            signers.PdfTimeStamper(DummyTimeStamper(tsa,pk(tk),store,fixed_dt=past+timedelta(days=2))).timestamp_pdf(IncrementalPdfFileWriter(out),'sha256',output=stamped)
            return stamped.getvalue()
        return out.getvalue()
    def trust(archived=True,trusted=True,revoked=False):
        return Trust(roots=[root] if trusted else [],crls=[current],
            historical_crls=[crl(now-timedelta(days=19),now-timedelta(days=5),revoked)] if archived else [],
            approved_signers={leaf.sha256.hex()})
    return pdf,trust

def test_valid_historical_timestamp(archive):
    pdf,trust=archive; report=verify(pdf(),'',trust())
    assert report.status=='valid',report
    sig=report.signatures[0]
    assert sig.validation_basis=='trusted_historical_time'
    assert sig.historical.status=='passed'
    assert sig.historical.certificate_expired_now
    assert sig.historical.trusted_time

def test_claimed_date_is_not_trusted_time(archive):
    pdf,trust=archive; report=verify(pdf(False),'',trust())
    assert report.status!='valid'
    assert 'missing_trusted_time_evidence' in report.signatures[0].historical.reason_codes

@pytest.mark.parametrize('options',[{'trusted':False},{'archived':False},{'revoked':True}])
def test_missing_or_bad_historical_evidence(archive,options):
    pdf,trust=archive;report=verify(pdf(archived=options.get('archived',True),revoked=options.get('revoked',False)),'',trust(**options))
    assert report.status!='valid',report

def test_tampering_cannot_be_cured_by_timestamp(archive):
    pdf,trust=archive;report=verify(pdf().replace(b'250 120',b'251 120'),'',trust())
    assert report.status=='invalid'
