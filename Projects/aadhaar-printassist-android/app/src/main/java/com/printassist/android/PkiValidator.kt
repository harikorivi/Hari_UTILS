package com.printassist.android

import java.security.cert.*
import java.util.Date
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.ocsp.*
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder

/** Offline, explicit roots only. No AIA/CRL HTTP fetching or embedded-root trust. */
class PkiValidator(private val material: TrustMaterial) {
    val provider = BouncyCastleProvider()
    private val strongSignatures = setOf(
        "1.2.840.113549.1.1.11", "1.2.840.113549.1.1.12", "1.2.840.113549.1.1.13",
        "1.2.840.10045.4.3.2", "1.2.840.10045.4.3.3", "1.2.840.10045.4.3.4"
    ) // Unsupported algorithms remain indeterminate, including unreviewed PSS parameters.
    data class Result(val trusted: Boolean, val revocation: String)
    fun evaluate(leaf: X509Certificate, supplied: List<X509Certificate>, at: Date): Result {
        if (material.roots.isEmpty()) return Result(false, "UNKNOWN")
        val chain = try {
            val selector = X509CertSelector().apply { certificate = leaf }
            val parameters = PKIXBuilderParameters(material.roots.map { TrustAnchor(it, null) }.toSet(), selector)
            parameters.date = at
            parameters.isRevocationEnabled = false // Validated separately, mandatory below.
            parameters.maxPathLength = 8
            parameters.addCertStore(CertStore.getInstance("Collection", CollectionCertStoreParameters(supplied + material.intermediates + material.roots)))
            val built = CertPathBuilder.getInstance("PKIX", provider).build(parameters) as PKIXCertPathBuilderResult
            val certificates = built.certPath.certificates.map { it as X509Certificate } + built.trustAnchor.trustedCert
            certificates.forEach {
                it.checkValidity(at)
                require(it.sigAlgOID in strongSignatures)
                val key = it.publicKey
                if (key is java.security.interfaces.RSAKey) require(key.modulus.bitLength() >= 2048)
                if (key is java.security.interfaces.ECKey) require(key.params.order.bitLength() >= 256)
            }
            certificates
        } catch (_: Exception) { return Result(false, "UNKNOWN") }
        if (chain.size < 2) return Result(false, "UNKNOWN") // Leaf cannot serve as its own root.
        val results = chain.zipWithNext().map { (cert, issuer) -> status(cert, issuer, at) }
        return Result(true, when { "REVOKED" in results -> "REVOKED"; results.all { it == "GOOD" } -> "GOOD"; else -> "UNKNOWN" })
    }

    private fun crlStatus(cert: X509Certificate, issuer: X509Certificate, at: Date): String {
        var good = false
        for (crl in material.crls) try {
            if (crl.issuerX500Principal != issuer.subjectX500Principal) continue
            if (issuer.keyUsage != null && !issuer.keyUsage[6]) continue
            // Delta/indirect/partitioned CRLs need additional scope processing; never count them as full coverage.
            if (crl.getExtensionValue("2.5.29.27") != null || crl.getExtensionValue("2.5.29.28") != null) continue
            if (!crl.criticalExtensionOIDs.orEmpty().all { it in setOf("2.5.29.20", "2.5.29.35") }) continue
            if (crl.thisUpdate.after(at) || crl.nextUpdate == null || crl.nextUpdate.before(at)) continue
            if (crl.sigAlgOID !in strongSignatures) continue
            require(org.bouncycastle.cert.jcajce.JcaX509CRLHolder(crl).isSignatureValid(
                JcaContentVerifierProviderBuilder().setProvider(provider).build(issuer.publicKey)))
            val entry = crl.getRevokedCertificate(cert.serialNumber)
            if (entry != null && !entry.revocationDate.after(at)) return "REVOKED"
            good = true
        } catch (_: Exception) { /* Unauthenticated records do not establish a status. */ }
        return if (good) "GOOD" else "UNKNOWN"
    }

    fun status(cert: X509Certificate, issuer: X509Certificate, at: Date): String {
        val crlResult = crlStatus(cert, issuer, at)
        if (crlResult == "REVOKED") return crlResult
        var good = crlResult == "GOOD"
        for (bytes in material.ocsps) try {
            val response = OCSPResp(bytes)
            if (response.status != 0) continue
            val basic = response.responseObject as? BasicOCSPResp ?: continue
            if (basic.producedAt.after(at)) continue
            if (basic.signatureAlgOID.id !in strongSignatures) continue
            if (basic.hasExtensions() && basic.criticalExtensionOIDs.isNotEmpty()) continue
            val candidates = listOf(issuer) + basic.certs.map { JcaX509CertificateConverter().setProvider(provider).getCertificate(it) }
            val responder = candidates.firstOrNull { candidate ->
                try {
                    candidate.checkValidity(at)
                    val holder = JcaX509CertificateHolder(candidate)
                    val digests = JcaDigestCalculatorProviderBuilder().setProvider(provider).build()
                    require(basic.responderId == RespID(holder.subject) ||
                        basic.responderId == RespID(holder.subjectPublicKeyInfo, digests.get(CertificateID.HASH_SHA1)))
                    require(candidate.sigAlgOID in strongSignatures)
                    val candidateKey = candidate.publicKey
                    if (candidateKey is java.security.interfaces.RSAKey) require(candidateKey.modulus.bitLength() >= 2048)
                    if (candidateKey is java.security.interfaces.ECKey) require(candidateKey.params.order.bitLength() >= 256)
                    if (candidate != issuer) {
                        val path = CertificateFactory.getInstance("X.509", provider).generateCertPath(listOf(candidate))
                        val params = PKIXParameters(setOf(TrustAnchor(issuer, null))).apply {
                            date = at; isRevocationEnabled = false
                        }
                        CertPathValidator.getInstance("PKIX", provider).validate(path, params)
                        require(JcaX509CertificateHolder(candidate).isSignatureValid(
                            JcaContentVerifierProviderBuilder().setProvider(provider).build(issuer.publicKey)))
                        require(candidate.issuerX500Principal == issuer.subjectX500Principal)
                        require(candidate.extendedKeyUsage?.contains("1.3.6.1.5.5.7.3.9") == true)
                        require(candidate.keyUsage == null || candidate.keyUsage[0])
                        // Delegated responder needs its own CRL evidence unless authorized with OCSP no-check.
                        val noCheck = candidate.getExtensionValue("1.3.6.1.5.5.7.48.1.5")
                        val validNoCheck = noCheck != null && try {
                            val octets = org.bouncycastle.asn1.ASN1OctetString.getInstance(noCheck).octets
                            org.bouncycastle.asn1.ASN1Primitive.fromByteArray(octets) is org.bouncycastle.asn1.ASN1Null
                        } catch (_: Exception) { false }
                        require(validNoCheck || crlStatus(candidate, issuer, at) == "GOOD")
                    }
                    basic.isSignatureValid(JcaContentVerifierProviderBuilder().setProvider(provider).build(candidate.publicKey))
                } catch (_: Exception) { false }
            } ?: continue
            if (responder.publicKey is java.security.interfaces.RSAKey && (responder.publicKey as java.security.interfaces.RSAKey).modulus.bitLength() < 2048) continue
            for (single in basic.responses) {
                if (single.certID.serialNumber != cert.serialNumber) continue
                if (!single.certID.matchesIssuer(JcaX509CertificateHolder(issuer), JcaDigestCalculatorProviderBuilder().setProvider(provider).build())) continue
                if (single.thisUpdate.after(at) || single.nextUpdate == null || single.nextUpdate.before(at)) continue
                if (single.hasExtensions() && single.criticalExtensionOIDs.isNotEmpty()) continue
                val state = single.certStatus
                if (state is RevokedStatus && !state.revocationTime.after(at)) return "REVOKED"
                if (state == CertificateStatus.GOOD) good = true
            }
        } catch (_: Exception) { /* Missing or unsupported evidence stays unknown. */ }
        return if (good) "GOOD" else "UNKNOWN"
    }
}
