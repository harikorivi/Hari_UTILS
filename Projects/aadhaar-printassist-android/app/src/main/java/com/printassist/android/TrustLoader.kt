package com.printassist.android

import android.content.Context
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.cert.X509CRL

object TrustLoader {
    fun load(context: Context): TrustMaterial {
        val assets = context.assets
        val cf = CertificateFactory.getInstance("X.509")
        fun certs(dir: String) = assets.list("trust/$dir").orEmpty().map { name -> assets.open("trust/$dir/$name").use { cf.generateCertificate(it) as X509Certificate } }
        fun crls(dir: String) = assets.list("trust/$dir").orEmpty().map { name -> assets.open("trust/$dir/$name").use { cf.generateCRL(it) as X509CRL } }
        val pins = assets.open("trust/approved-signers.txt").bufferedReader().useLines { lines -> lines.map { it.substringBefore('#').trim() }.filter { it.length == 64 }.toSet() }
        val publicFiles = java.io.File(context.filesDir, "public-evidence").listFiles().orEmpty().filter { it.length() <= 5*1024*1024 }
        val extraCerts = mutableListOf<X509Certificate>(); val extraCrls = mutableListOf<X509CRL>(); val extraOcsps = mutableListOf<ByteArray>()
        for (file in publicFiles) try {
            when (file.extension) {
                "cer" -> file.inputStream().use { extraCerts += cf.generateCertificate(it) as X509Certificate }
                "crl" -> file.inputStream().use { extraCrls += cf.generateCRL(it) as X509CRL }
                "ocsp" -> extraOcsps += file.readBytes()
            }
        } catch (_: Exception) { /* Corrupt optional public evidence never expands trust. */ }
        return TrustMaterial(certs("roots"), certs("intermediates") + extraCerts,
            crls("crls") + crls("historical-crls") + extraCrls, extraOcsps, pins)
    }
}
