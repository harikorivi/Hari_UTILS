package com.printassist.android
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.cert.X509CRL
import java.time.Instant
import java.util.Date
import org.junit.Assert.*
import org.junit.Test

/** Synthetic CA only. No real customer documents or private keys. */
class VerificationTest {
 private fun bytes(n: String)=checkNotNull(javaClass.getResourceAsStream("/fixtures/$n")).use { it.readBytes() }
 private val cf=CertificateFactory.getInstance("X.509")
 private fun cert(n: String)=cf.generateCertificate(bytes("$n.cer").inputStream()) as X509Certificate
 private fun crl(n: String)=cf.generateCRL(bytes("$n.crl").inputStream()) as X509CRL
 private fun material()=TrustMaterial(roots=listOf(cert("root")),crls=listOf(crl("root"),crl("inter")),approvedUidai=setOf(fingerprint(cert("leaf").encoded),fingerprint(cert("old").encoded)))
 private fun check(n: String="valid",m: TrustMaterial=material(),aadhaar: Boolean=true,badRange: Boolean=false, subFilter: String="adbe.pkcs7.detached"): SignatureCheck {
  val original=bytes("$n.pdf");val text=original.toString(Charsets.ISO_8859_1)
  val g=Regex("/ByteRange\\s*\\[\\s*(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s*]").find(text)!!.groupValues
  val r=(1..4).map { g[it].toInt() }.toIntArray();val hex=text.substring(r[1]+1,r[2]-1).filterNot { it.isWhitespace() }
  val c=ByteArray(hex.length/2) { hex.substring(it*2,it*2+2).toInt(16).toByte() };if(badRange)r[1]-=1
  return CmsVerifier(m).verify(original,r,c,subFilter,aadhaar,Date.from(Instant.parse("2026-10-22T00:00:00Z")))
 }
 @Test fun validSignatureWithExplicitTrustAndFreshCrlIsGreen(){val r=check();assertEquals(r.reason,Verdict.VERIFIED,r.verdict)}
 @Test fun signedBytesTamperingIsInvalid(){assertEquals(Verdict.INVALID,check("tampered").verdict)}
 @Test fun laterBytesNeverGreen(){val r=check("appended");assertTrue(r.integrity);assertFalse(r.wholeFile);assertEquals(Verdict.INDETERMINATE,r.verdict)}
 @Test fun byteRangeMustBindExactContentsGap(){assertNotEquals(Verdict.VERIFIED,check(badRange=true).verdict)}
 @Test fun embeddedRootIsNotTrusted(){assertNotEquals(Verdict.VERIFIED,check(m=material().copy(roots=emptyList())).verdict)}
 @Test fun wrongRootCannotValidate(){assertNotEquals(Verdict.VERIFIED,check(m=material().copy(roots=listOf(cert("bad")))).verdict)}
 @Test fun missingIntermediateNeedsAuthenticatedChain(){assertNotEquals(Verdict.VERIFIED,check("missing-inter").verdict);assertEquals(Verdict.VERIFIED,check("missing-inter",material().copy(intermediates=listOf(cert("inter")))).verdict)}
 @Test fun noRevocationEvidenceNeverGreen(){assertNotEquals(Verdict.VERIFIED,check(m=material().copy(crls=emptyList())).verdict)}
 @Test fun staleCrlNeverGreen(){assertNotEquals(Verdict.VERIFIED,check(m=material().copy(crls=listOf(crl("root"),crl("stale")))).verdict)}
 @Test fun unauthenticatedCrlNeverGreen(){assertNotEquals(Verdict.VERIFIED,check(m=material().copy(crls=listOf(crl("root"),crl("wrong")))).verdict)}
 @Test fun revokedCertificateIsInvalid(){assertEquals(Verdict.INVALID,check(m=material().copy(crls=listOf(crl("root"),crl("revoked")))).verdict)}
 @Test fun aadhaarRequiresApprovedSigner(){val m=material().copy(approvedUidai=emptySet());assertNotEquals(Verdict.VERIFIED,check(m=m).verdict);assertEquals(Verdict.VERIFIED,check(m=m,aadhaar=false).verdict)}
 @Test fun claimedHistoricalDateCannotRescueExpiredCertificate(){assertNotEquals(Verdict.VERIFIED,check("expired").verdict)}
 @Test fun trustedTimestampAndHistoricalCrlCanValidateExpiredSigner(){val m=material().copy(crls=material().crls+listOf(crl("historic-root"),crl("historic-inter")));val r=check("timestamp",m);assertEquals(r.reason,Verdict.VERIFIED,r.verdict);assertEquals("2023-01-01T00:00:00Z",r.trustedTime)}
 @Test fun timestampWithoutHistoricalEvidenceNeverGreen(){assertNotEquals(Verdict.VERIFIED,check("timestamp").verdict)}
 @Test fun historicalRevokedCertificateIsInvalid(){val m=material().copy(crls=material().crls+listOf(crl("historic-root"),crl("historic-revoked")));assertEquals(Verdict.INVALID,check("timestamp",m).verdict)}
 @Test fun authenticatedOcspCanEstablishStatus(){val m=material().copy(crls=listOf(crl("root")),ocsps=listOf(bytes("good.ocsp")));assertEquals(Verdict.VERIFIED,check(m=m).verdict)}
 @Test fun ocspRevokedIsInvalid(){val m=material().copy(crls=listOf(crl("root")),ocsps=listOf(bytes("revoked.ocsp")));assertEquals(Verdict.INVALID,check(m=m).verdict)}
 @Test fun staleOrUnauthorizedOcspNeverGreen(){for(n in listOf("stale","wrong")){val m=material().copy(crls=listOf(crl("root")),ocsps=listOf(bytes("$n.ocsp")));assertNotEquals(n,Verdict.VERIFIED,check(m=m).verdict)}}
 @Test fun filenameReusesNameAndPreventsPathTraversal(){assertEquals("Devarakonda_Keerthi",ExportNames.base("Devarakonda Keerthi"));assertFalse(ExportNames.base("../../foo/bar").contains('/'));assertEquals("Document",ExportNames.base("   "))}
 @Test fun cadesCertificateBindingIsValidated(){assertEquals(Verdict.VERIFIED,check(subFilter="ETSI.CAdES.detached").verdict)}
 @Test fun invalidTimestampCannotRescueExpiredSigner(){val m=material().copy(crls=material().crls+listOf(crl("historic-root"),crl("historic-inter")));val r=check("untrusted-timestamp",m);assertTrue(r.integrity);assertNotEquals(Verdict.VERIFIED,r.verdict)}
}
