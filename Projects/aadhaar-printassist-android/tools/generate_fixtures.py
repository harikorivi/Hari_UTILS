"""Optional synthetic fixture regeneration; no customer data or keys are saved. Run with pyHanko 0.35.0 and its dependencies. Fixtures use 2020–2035 test certificates and a frozen JVM evaluation time in October 2026."""
from pathlib import Path
from datetime import datetime, timezone
from io import BytesIO
from unittest.mock import patch
from cryptography import x509
from cryptography.x509 import ocsp
from cryptography.x509.oid import NameOID,ExtendedKeyUsageOID
from cryptography.hazmat.primitives import hashes,serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from asn1crypto import keys,x509 as ax
from pyhanko.sign import signers
from pyhanko.sign.fields import SigFieldSpec
from pyhanko.sign.timestamps import DummyTimeStamper
from pyhanko.pdf_utils.writer import PdfFileWriter,PageObject
from pyhanko.pdf_utils import generic
from pyhanko_certvalidator.registry import SimpleCertificateStore
out=Path(__file__).resolve().parents[1]/'app/src/androidTest/assets/fixtures';out.mkdir(parents=True,exist_ok=True)
def dt(y): return datetime(y,1,1,tzinfo=timezone.utc)
def key():return rsa.generate_private_key(public_exponent=65537,key_size=2048)
rk,ik,lk,tk,bk=[key() for _ in range(5)]
def name(n):return x509.Name([x509.NameAttribute(NameOID.COMMON_NAME,'SYNTHETIC TEST ONLY '+n)])
def cert(k,n,serial,issuer=None,issuerkey=None,ca=False,expired=False,tsa=False):
 b=(x509.CertificateBuilder().subject_name(name(n)).issuer_name(issuer.subject if issuer else name(n)).public_key(k.public_key()).serial_number(serial).not_valid_before(dt(2020)).not_valid_after(dt(2024 if expired else 2035)).add_extension(x509.BasicConstraints(ca=ca,path_length=2 if ca else None),True).add_extension(x509.SubjectKeyIdentifier.from_public_key(k.public_key()),False).add_extension(x509.AuthorityKeyIdentifier.from_issuer_public_key((issuerkey or k).public_key()),False).add_extension(x509.KeyUsage(not ca,not ca and not tsa,False,False,False,ca,ca,False,False),True))
 if tsa:b=b.add_extension(x509.ExtendedKeyUsage([ExtendedKeyUsageOID.TIME_STAMPING]),True)
 return b.sign(issuerkey or k,hashes.SHA256())
root=cert(rk,'ROOT',1,ca=True); inter=cert(ik,'INTERMEDIATE',2,root,rk,ca=True)
leaf=cert(lk,'SIGNER',3,inter,ik); old=cert(lk,'EXPIRED SIGNER',4,inter,ik,expired=True);tsa=cert(tk,'TSA',5,root,rk,tsa=True);bad=cert(bk,'UNTRUSTED',6,ca=True)
for n,c in [('root',root),('inter',inter),('leaf',leaf),('old',old),('tsa',tsa),('bad',bad)]: (out/(n+'.cer')).write_bytes(c.public_bytes(serialization.Encoding.DER))
def crl(n,issuer,k,start=2025,end=2030,revoked=None,wrong=False):
 b=x509.CertificateRevocationListBuilder().issuer_name(issuer.subject).last_update(dt(start)).next_update(dt(end)).add_extension(x509.AuthorityKeyIdentifier.from_issuer_public_key(k.public_key()),False)
 if revoked:b=b.add_revoked_certificate(x509.RevokedCertificateBuilder().serial_number(revoked).revocation_date(dt(start)).build())
 (out/(n+'.crl')).write_bytes(b.sign(bk if wrong else k,hashes.SHA256()).public_bytes(serialization.Encoding.DER))
crl('root',root,rk);crl('inter',inter,ik);crl('stale',inter,ik,2020,2021);crl('revoked',inter,ik,revoked=3);crl('wrong',inter,ik,wrong=True);crl('historic-root',root,rk,2022,2024);crl('historic-inter',inter,ik,2022,2024);crl('historic-revoked',inter,ik,2022,2024,4)
def ac(c):return ax.Certificate.load(c.public_bytes(serialization.Encoding.DER))
def pk(k):return keys.PrivateKeyInfo.load(k.private_bytes(serialization.Encoding.DER,serialization.PrivateFormat.PKCS8,serialization.NoEncryption()))
store=SimpleCertificateStore();store.register(ac(inter));store.register(ac(root))
class Clock(datetime):
 @classmethod
 def now(cls,tz=None):return dt(2023).astimezone(tz)
def pdf(n,c=leaf,timestamp=False,missing=False):
 w=PdfFileWriter();s=w.add_object(generic.StreamObject(stream_data=b'0.1 0.4 0.7 rg 40 40 250 120 re f\n'));w.insert_page(PageObject(s,(0,0,595,842)))
 signer=signers.SimpleSigner(ac(c),pk(lk),SimpleCertificateStore() if missing else store)
 ts=DummyTimeStamper(ac(tsa),pk(tk),store,fixed_dt=dt(2023)) if timestamp else None
 b=BytesIO()
 with patch('pyhanko.sign.signers.pdf_signer.datetime',Clock):
  signers.sign_pdf(w,signers.PdfSignatureMetadata(field_name='TestSignature'),signer,timestamper=ts,new_field_spec=SigFieldSpec('TestSignature',box=(380,180,590,270)),output=b)
 (out/(n+'.pdf')).write_bytes(b.getvalue())
pdf('valid');pdf('missing-inter',missing=True);pdf('expired',old);pdf('timestamp',old,True)
v=(out/'valid.pdf').read_bytes();(out/'tampered.pdf').write_bytes(v.replace(b'250 120',b'251 120'));(out/'appended.pdf').write_bytes(v+b'\n% later bytes\n')
for n,status,wrong,stale in [('good',ocsp.OCSPCertStatus.GOOD,False,False),('revoked',ocsp.OCSPCertStatus.REVOKED,False,False),('wrong',ocsp.OCSPCertStatus.GOOD,True,False),('stale',ocsp.OCSPCertStatus.GOOD,False,True)]:
 b=ocsp.OCSPResponseBuilder().add_response(cert=leaf,issuer=inter,algorithm=hashes.SHA1(),cert_status=status,this_update=dt(2020 if stale else 2025),next_update=dt(2021 if stale else 2030),revocation_time=dt(2025) if status==ocsp.OCSPCertStatus.REVOKED else None,revocation_reason=x509.ReasonFlags.key_compromise if status==ocsp.OCSPCertStatus.REVOKED else None).responder_id(ocsp.OCSPResponderEncoding.HASH,bad if wrong else inter)
 (out/(n+'.ocsp')).write_bytes(b.sign(bk if wrong else ik,hashes.SHA256()).public_bytes(serialization.Encoding.DER))
print('Created synthetic public-only fixtures:',len(list(out.iterdir())))

# Corrupt only the unsigned timestamp token; the PDF signer signature stays intact.
from asn1crypto import cms
import re
b=(out/'timestamp.pdf').read_bytes();r=list(map(int,re.search(rb'/ByteRange\s*\[([^]]+)',b)[1].split()))
raw=bytes.fromhex(b[r[1]+1:r[2]-1].decode());c=cms.ContentInfo.load(raw)
t=c['content']['signer_infos'][0]['unsigned_attrs'][0]['values'][0]
si=t['content']['signer_infos'][0];v=bytearray(si['signature'].native);v[0]^=1;si['signature']=bytes(v)
new=c.dump();new+=bytes(len(raw)-len(new))
(out/'untrusted-timestamp.pdf').write_bytes(b[:r[1]+1]+new.hex().upper().encode()+b[r[2]-1:])
