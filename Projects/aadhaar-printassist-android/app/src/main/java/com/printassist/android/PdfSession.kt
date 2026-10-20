package com.printassist.android

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.rendering.ImageType
import com.tom_roush.pdfbox.pdfparser.PDFParser
import com.tom_roush.pdfbox.io.RandomAccessBuffer
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.cos.COSDictionary
import java.security.cert.CertificateFactory
import java.security.cert.X509CRL
import java.security.cert.X509Certificate
import java.util.Date
import java.io.OutputStream
import android.graphics.RectF
import android.print.PrintAttributes
import android.print.pdf.PrintedPdfDocument
import android.os.CancellationSignal
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import kotlin.math.min
import kotlin.math.sqrt

/** Original bytes are never rewritten. Only a parsed document and one preview stay in memory. */
class PdfSession(context: Context) : Closeable {
    private val appContext = context.applicationContext
    private val resolver = context.applicationContext.contentResolver
    private var document: PDDocument? = null
    private var originalBytes: ByteArray? = null
    private var signatureAppearanceAllowed = false
    var strictParsing = true; private set
    init { PDFBoxResourceLoader.init(context.applicationContext) }

    @Synchronized fun open(uri: Uri, password: String): Int =
        resolver.openInputStream(uri)?.use { open(it, password) }
            ?: error("The selected file could not be opened.")

    @Synchronized fun open(input: InputStream, password: String): Int {
        close()
        val bytes = readLimited(input)
        try {
            val parsed = try {
                val source = RandomAccessBuffer(bytes)
                try {
                    val parser = PDFParser(source, password)
                    parser.isLenient = false
                    parser.parse()
                    strictParsing = true
                    parser.pdDocument
                } catch (e: Exception) { source.close(); throw e }
            } catch (e: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) { throw e }
            catch (_: java.io.IOException) {
                strictParsing = false
                PDDocument.load(bytes, password)
            }
            try {
                require(parsed.numberOfPages in 1..100) { "Choose a PDF with 1–100 pages." }
                originalBytes = bytes
                document = parsed
                return parsed.numberOfPages
            } catch (e: Exception) { parsed.close(); throw e }
        } catch (e: Exception) { bytes.fill(0); throw e }
    }

    @Synchronized fun render(page: Int, dpi: Int = 108): Bitmap {
        val pdf = checkNotNull(document) { "Open the document again." }
        require(page in 0 until pdf.numberOfPages)
        val box = pdf.getPage(page).cropBox
        val w = box.width; val h = box.height
        require(w.isFinite() && h.isFinite() && w > 0 && h > 0 && w <= 14400 && h <= 14400) {
            "This page size is not supported."
        }
        require(dpi in 72..300)
        val pixelBudget = if (dpi <= 108) 2_000_000f else 8_000_000f
        val scale = min(dpi / 72f, sqrt(pixelBudget / (w * h)))
        val renderer = PDFRenderer(pdf)
        if (!signatureAppearanceAllowed) {
            // Suppress the annotation, not its rectangular page region: underlying content remains.
            val signatureWidgets = pdf.signatureFields.flatMap { it.widgets }.map { it.cosObject }.toSet()
            renderer.setAnnotationsFilter { annotation ->
                annotation.cosObject !in signatureWidgets && !isSignatureWidget(annotation.cosObject)
            }
        }
        return renderer.renderImage(page, scale, ImageType.RGB)
    }

    @Synchronized fun verify(material: TrustMaterial, aadhaar: Boolean): VerificationReport {
        val pdf = checkNotNull(document)
        val bytes = checkNotNull(originalBytes)
        val now = Date()
        signatureAppearanceAllowed = false
        try {
            val cf = CertificateFactory.getInstance("X.509")
            val dss = pdf.documentCatalog.cosObject.getDictionaryObject(COSName.getPDFName("DSS")) as? COSDictionary
            var evidenceBytes = 0
            fun records(key: String): List<ByteArray> {
                val array = dss?.getCOSArray(COSName.getPDFName(key)) ?: return emptyList()
                require(array.size() <= 64)
                return (0 until array.size()).mapNotNull { index ->
                    val stream = array.getObject(index) as? COSStream ?: return@mapNotNull null
                    stream.createInputStream().use { input ->
                        val buffer = ByteArrayOutputStream()
                        val block = ByteArray(8192)
                        while (true) { val n = input.read(block); if (n < 0) break; require(buffer.size() + n <= 2*1024*1024); evidenceBytes += n; require(evidenceBytes <= 20*1024*1024); buffer.write(block,0,n) }
                        buffer.toByteArray()
                    }
                }
            }
            val combined = material.copy(
                intermediates = material.intermediates + records("Certs").map { cf.generateCertificate(it.inputStream()) as X509Certificate },
                crls = material.crls + records("CRLs").map { cf.generateCRL(it.inputStream()) as X509CRL },
                ocsps = material.ocsps + records("OCSPs"))
            val verifier = CmsVerifier(combined)
            val signatures = pdf.signatureDictionaries
            require(signatures.size <= 20)
            val checks = signatures.map { signature ->
                val appearances = pdf.signatureFields.filter { it.signature?.cosObject == signature.cosObject }.flatMap { field ->
                    field.widgets.map { widget ->
                        val ap = widget.appearance?.normalAppearance
                        val stream = widget.cosObject.getCOSDictionary(COSName.AP)?.getDictionaryObject(COSName.N) as? COSStream
                        "Widget Rect=${widget.rectangle}; AP/N=${if (ap != null) "present" else "missing"}; BBox=${stream?.getCOSArray(COSName.BBOX)}; Matrix=${stream?.getCOSArray(COSName.MATRIX) ?: "identity"}; original appearance preserved."
                    }
                }.joinToString("\n")
                verifier.verify(bytes, signature.byteRange, signature.contents, signature.subFilter.orEmpty(), aadhaar, now)
                    .copy(appearance = appearances)
            }
            val verdict = when {
                checks.any { it.verdict == Verdict.INVALID } -> Verdict.INVALID
                !strictParsing -> Verdict.INDETERMINATE
                checks.isEmpty() -> Verdict.UNSIGNED
                checks.all { it.verdict == Verdict.VERIFIED } -> Verdict.VERIFIED
                else -> Verdict.INDETERMINATE
            }
            signatureAppearanceAllowed = verdict == Verdict.VERIFIED
            return VerificationReport(fingerprint(bytes), utc(now), verdict, checks,
                if (strictParsing) "Strict parser. Original file unchanged." else "Compatibility parser: syntax required recovery; green verification is disabled.")
        } catch (_: Exception) {
            return VerificationReport(fingerprint(bytes), utc(now), Verdict.INDETERMINATE, emptyList(), "Signature or embedded evidence parsing incomplete. No trust is assumed.")
        }
    }

    @Synchronized fun writeOriginal(output: OutputStream) { output.write(checkNotNull(originalBytes)) }
    @Synchronized fun writeImage(output: OutputStream, page: Int, jpeg: Boolean, dpi: Int) {
        val image = render(page, dpi)
        try { check(image.compress(if (jpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG, 95, output)) }
        finally { image.recycle() }
    }
    @Synchronized fun printPdf(output: OutputStream, attributes: PrintAttributes, pages: Set<Int>,
                               cancellation: CancellationSignal = CancellationSignal()) {
        val count = checkNotNull(document).numberOfPages
        require(pages.size <= 10) { "Print/export up to 10 pages per operation." }
        require(pages.all { it in 0 until count })
        val print = PrintedPdfDocument(appContext, attributes)
        try {
            for (index in pages.sorted()) {
                cancellation.throwIfCanceled()
                val page = print.startPage(index)
                try {
                    val dpi = min(attributes.resolution?.horizontalDpi ?: 200, 200).coerceAtLeast(72)
                    val image = render(index, dpi)
                    try {
                    val area = RectF(page.info.contentRect)
                    val scale = min(area.width()/image.width, area.height()/image.height)
                    val w = image.width*scale; val h = image.height*scale
                    val target = RectF(area.centerX()-w/2, area.centerY()-h/2, area.centerX()+w/2, area.centerY()+h/2)
                    page.canvas.drawBitmap(image, null, target, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                    } finally { image.recycle() }
                } finally { print.finishPage(page) }
            }
            cancellation.throwIfCanceled()
            print.writeTo(output)
        } finally { print.close() }
    }

    @Synchronized override fun close() {
        try { document?.close() } finally {
            signatureAppearanceAllowed = false
            document = null
            originalBytes?.fill(0)
            originalBytes = null
        }
    }

    private fun isSignatureWidget(annotation: COSDictionary): Boolean {
        if (annotation.getNameAsString(COSName.SUBTYPE) != "Widget") return false
        var field: COSDictionary? = annotation
        val visited = mutableSetOf<COSDictionary>()
        while (field != null && visited.add(field) && visited.size <= 32) {
            val type = field.getNameAsString(COSName.FT)
            if (type != null) return type == "Sig"
            field = field.getDictionaryObject(COSName.PARENT) as? COSDictionary
        }
        return false
    }

    companion object {
        const val MAX_BYTES = 20 * 1024 * 1024
        fun readLimited(input: InputStream): ByteArray {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                require(total <= MAX_BYTES) { "Choose a PDF smaller than 20 MB." }
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        }
    }
}
