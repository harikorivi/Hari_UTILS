package com.printassist.android

import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.security.cert.X509CRL
import java.util.Date

enum class Verdict { VERIFIED, INVALID, INDETERMINATE, UNSIGNED }
data class SignatureCheck(
    val verdict: Verdict = Verdict.INDETERMINATE,
    val integrity: Boolean = false,
    val wholeFile: Boolean = false,
    val trust: Boolean = false,
    val revocation: String = "UNKNOWN",
    val signer: String = "", val issuer: String = "", val serial: String = "",
    val fingerprint: String = "", val validity: String = "", val algorithm: String = "",
    val trustedTime: String = "", val reason: String = "Unsupported or incomplete evidence.",
    val appearance: String = ""
)
data class VerificationReport(val sha256: String, val checkedAt: String,
    val verdict: Verdict, val signatures: List<SignatureCheck>, val note: String = "") {
    fun text(): String = buildString {
        appendLine("Digital signature verification: $verdict")
        appendLine("Checked: $checkedAt")
        appendLine("Original SHA-256: $sha256")
        if (note.isNotEmpty()) appendLine(note)
        signatures.forEachIndexed { index, s ->
            appendLine("\nSignature ${index+1}: ${s.verdict}")
            appendLine("Integrity: ${s.integrity}; entire file: ${s.wholeFile}")
            appendLine("Certificate path trusted: ${s.trust}; revocation: ${s.revocation}")
            appendLine("Signer: ${s.signer}\nIssuer: ${s.issuer}\nSerial: ${s.serial}")
            appendLine("Validity: ${s.validity}\nFingerprint: ${s.fingerprint}\nAlgorithm: ${s.algorithm}")
            appendLine("Trusted timestamp: ${s.trustedTime.ifEmpty { "not established" }}")
            appendLine(s.reason)
            if (s.appearance.isNotEmpty()) appendLine(s.appearance)
        }
        appendLine("\nChecks apply to original bytes. PDF signature validation is not UIDAI authentication. Image and print copies have no original cryptographic signature.")
    }
}
data class TrustMaterial(val roots: List<X509Certificate> = emptyList(),
    val intermediates: List<X509Certificate> = emptyList(), val crls: List<X509CRL> = emptyList(),
    val ocsps: List<ByteArray> = emptyList(), val approvedUidai: Set<String> = emptySet())
fun fingerprint(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
fun utc(date: Date): String = date.toInstant().toString()
