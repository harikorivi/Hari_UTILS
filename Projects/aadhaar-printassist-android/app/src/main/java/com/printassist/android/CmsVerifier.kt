package com.printassist.android

import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Date
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.cms.ContentInfo
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.tsp.TimeStampToken

class CmsVerifier(private val material: TrustMaterial) {
    private val pki = PkiValidator(material)
    /** Require exactly the Contents hex string as the only unsigned gap. */
    fun verify(original: ByteArray, range: IntArray, contents: ByteArray, subFilter: String,
               aadhaar: Boolean, now: Date = Date()): SignatureCheck {
        var result = SignatureCheck()
        try {
            require(range.size == 4 && range[0] == 0 && range[1] > 0 && range[2] > range[1] && range[3] > 0)
            val end = range[2].toLong() + range[3].toLong()
            require(end <= original.size && range[2] <= original.size)
            val gap = original.copyOfRange(range[1], range[2]).toString(Charsets.US_ASCII)
            require(gap.startsWith("<") && !gap.startsWith("<<") && gap.endsWith(">"))
            val hex = gap.substring(1, gap.length - 1).filterNot { it in " \t\r\n\u000c\u0000" }
            require(hex.length % 2 == 0 && hex.all { it in "0123456789abcdefABCDEF" })
            val decoded = ByteArray(hex.length / 2) { hex.substring(it*2, it*2+2).toInt(16).toByte() }
            require(MessageDigest.isEqual(decoded, contents))
            result = result.copy(wholeFile = end == original.size.toLong())
            if (subFilter !in setOf("adbe.pkcs7.detached", "ETSI.CAdES.detached")) return result.copy(reason = "Unsupported signature subfilter: $subFilter")
            val signed = original.copyOfRange(0, range[1]) + original.copyOfRange(range[2], end.toInt())
            val contentInfo = ASN1InputStream(ByteArrayInputStream(contents)).use { ContentInfo.getInstance(it.readObject()) }
            val cms = CMSSignedData(CMSProcessableByteArray(signed), contentInfo)
            require(cms.signedContentTypeOID == "1.2.840.113549.1.7.1")
            val signers = cms.signerInfos.signers
            if (signers.size != 1) return result.copy(reason = "Multiple or missing CMS signers require further review.")
            val signer = signers.first()
            if (signer.digestAlgOID !in setOf("2.16.840.1.101.3.4.2.1", "2.16.840.1.101.3.4.2.2", "2.16.840.1.101.3.4.2.3")) return result.copy(reason = "Signature digest algorithm is outside the supported SHA-2 policy.")
            val holder = cms.certificates.getMatches(null).singleOrNull { signer.sid.match(it) } ?: return result.copy(reason = "Signing certificate missing or ambiguous.")
            val converter = JcaX509CertificateConverter().setProvider(pki.provider)
            val cert = converter.getCertificate(holder)
            val certificates = cms.certificates.getMatches(null).map { converter.getCertificate(it) }
            val essAttrs = signer.signedAttributes?.getAll(org.bouncycastle.asn1.ASN1ObjectIdentifier("1.2.840.113549.1.9.16.2.47"))
            if (subFilter == "ETSI.CAdES.detached" || essAttrs != null) {
                require(essAttrs != null && essAttrs.size() == 1)
                val attr = org.bouncycastle.asn1.cms.Attribute.getInstance(essAttrs.get(0))
                require(attr.attrValues.size() == 1)
                val ess = org.bouncycastle.asn1.ess.SigningCertificateV2.getInstance(attr.attrValues.getObjectAt(0)).certs.first()
                require(ess.hashAlgorithm.algorithm.id in setOf("2.16.840.1.101.3.4.2.1", "2.16.840.1.101.3.4.2.2", "2.16.840.1.101.3.4.2.3"))
                require(MessageDigest.isEqual(ess.certHash, MessageDigest.getInstance(ess.hashAlgorithm.algorithm.id, pki.provider).digest(cert.encoded)))
            }
            result = result.copy(signer = cert.subjectX500Principal.name, issuer = cert.issuerX500Principal.name,
                serial = cert.serialNumber.toString(16), fingerprint = fingerprint(cert.encoded),
                validity = "${utc(cert.notBefore)} to ${utc(cert.notAfter)}", algorithm = "digest ${signer.digestAlgOID}; signature ${signer.encryptionAlgOID}")
            // Verify raw cryptography without letting an untrusted CMS signingTime set certificate-validity policy.
            val verifier = JcaSimpleSignerInfoVerifierBuilder().setProvider(pki.provider).build(cert.publicKey)
            if (!signer.verify(verifier)) return result.copy(verdict = Verdict.INVALID, reason = "Signed bytes or cryptographic signature do not match.")
            result = result.copy(integrity = true)
            if (cert.keyUsage != null && !cert.keyUsage[0] && !cert.keyUsage[1]) return result.copy(reason = "Signing certificate does not permit signing.")
            var at = now
            var evaluated = pki.evaluate(cert, certificates, at)
            var trustedTime = ""
            if (!evaluated.trusted || evaluated.revocation != "GOOD") {
                val attrs = signer.unsignedAttributes?.getAll(PKCSObjectIdentifiers.id_aa_signatureTimeStampToken)
                if (attrs != null && attrs.size() == 1) {
                    try {
                        val attr = org.bouncycastle.asn1.cms.Attribute.getInstance(attrs.get(0))
                        require(attr.attrValues.size() == 1)
                        val token = TimeStampToken(ContentInfo.getInstance(attr.attrValues.getObjectAt(0)))
                        val info = token.timeStampInfo
                        require(info.messageImprintAlgOID.id in setOf("2.16.840.1.101.3.4.2.1", "2.16.840.1.101.3.4.2.2", "2.16.840.1.101.3.4.2.3"))
                        require(!info.genTime.after(now))
                        require(MessageDigest.isEqual(info.messageImprintDigest, MessageDigest.getInstance(info.messageImprintAlgOID.id, pki.provider).digest(signer.signature)))
                        val tsaHolder = token.certificates.getMatches(null).single { token.sid.match(it) }
                        val tsa = converter.getCertificate(tsaHolder)
                        require(token.toCMSSignedData().signerInfos.signers.single().digestAlgOID in setOf("2.16.840.1.101.3.4.2.1", "2.16.840.1.101.3.4.2.2", "2.16.840.1.101.3.4.2.3"))
                        token.validate(JcaSimpleSignerInfoVerifierBuilder().setProvider(pki.provider).build(tsa))
                        val tsaPath = pki.evaluate(tsa, token.certificates.getMatches(null).map { converter.getCertificate(it) }, now)
                        require(tsaPath.trusted && tsaPath.revocation == "GOOD")
                        // Expired/revoked TSA requires an LTA preservation chain, not an operator clock override.
                        at = info.genTime
                        trustedTime = utc(at)
                        evaluated = pki.evaluate(cert, certificates, at)
                    } catch (_: Exception) { /* An untrusted timestamp never permits backdating. */ }
                }
            }
            result = result.copy(trust = evaluated.trusted, revocation = evaluated.revocation, trustedTime = trustedTime)
            if (evaluated.revocation == "REVOKED") return result.copy(verdict = Verdict.INVALID, reason = "Certificate revoked at the evaluated time.")
            if (!result.wholeFile) return result.copy(reason = "Signed revision is intact, but later bytes/revisions exist. Their acceptability is unassessed; no green result.")
            if (aadhaar && fingerprint(cert.encoded) !in material.approvedUidai) return result.copy(reason = "Signer certificate is not in the independently authenticated UIDAI allowlist.")
            if (!evaluated.trusted || evaluated.revocation != "GOOD") return result.copy(reason = "Certificate path or required fresh revocation evidence is incomplete. Expired signers require a trusted timestamp and suitable historical evidence.")
            return result.copy(verdict = Verdict.VERIFIED, reason = if (trustedTime.isEmpty()) "Integrity, entire-file coverage, explicit certificate trust and revocation checks passed at the current time." else "Integrity and certificate/revocation checks passed at the independently validated RFC 3161 time.")
        } catch (_: org.bouncycastle.cms.CMSSignerDigestMismatchException) {
            return result.copy(verdict = Verdict.INVALID, reason = "Signed content digest does not match the signature.")
        } catch (_: Exception) {
            return result.copy(verdict = Verdict.INDETERMINATE,
                reason = "Signature parsing, byte-range binding or cryptographic validation failed. No trust is assumed.")
        }
    }
}
