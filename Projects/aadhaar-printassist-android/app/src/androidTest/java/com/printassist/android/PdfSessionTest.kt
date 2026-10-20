package com.printassist.android
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class) class PdfSessionTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun encrypted(bits: Int = 128): ByteArray {
        PDFBoxResourceLoader.init(context)
        return PDDocument().use { pdf ->
            val page = PDPage()
            pdf.addPage(page)
            PDPageContentStream(pdf, page).use {
                it.setNonStrokingColor(255, 0, 0)
                it.addRect(0f, 0f, 612f, 792f)
                it.fill()
            }
            val policy = StandardProtectionPolicy("test-owner", "KORI1978", AccessPermission())
            policy.encryptionKeyLength = bits
            policy.isPreferAES = true
            pdf.protect(policy)
            ByteArrayOutputStream().also { pdf.save(it) }.toByteArray()
        }
    }
    @Test fun correctPasswordRendersAndCloseDiscardsDocument() {
        PdfSession(context).use { session ->
            assertEquals(1, session.open(encrypted().inputStream(), "KORI1978"))
            val image = session.render(0)
            assertTrue(image.width > 0 && image.height > 0)
            val center = image.getPixel(image.width / 2, image.height / 2)
            assertEquals(255, android.graphics.Color.red(center))
            assertEquals(0, android.graphics.Color.green(center))
            assertTrue(image.width.toLong() * image.height <= 2_000_000)
            session.close()
            try { session.render(0); fail("Closed session must not render") } catch (_: IllegalStateException) { }
        }
    }
    @Test fun aes256OpensWithoutChangingInput() {
        val original = encrypted(256)
        val expected = original.copyOf()
        PdfSession(context).use { session ->
            assertEquals(1, session.open(original.inputStream(), "KORI1978"))
            assertTrue(session.render(0).width > 0)
        }
        assertArrayEquals(expected, original)
    }
    @Test fun unencryptedGeneralPdfOpens() {
        PDFBoxResourceLoader.init(context)
        val original = PDDocument().use { pdf ->
            pdf.addPage(PDPage()); pdf.addPage(PDPage())
            ByteArrayOutputStream().also { pdf.save(it) }.toByteArray()
        }
        PdfSession(context).use { session ->
            assertEquals(2, session.open(original.inputStream(), ""))
            assertTrue(session.render(1).width > 0)
        }
    }
    @Test fun wrongPasswordIsRejected() {
        PdfSession(context).use { session ->
            try { session.open(encrypted().inputStream(), "WRONG"); fail("Wrong password accepted") } catch (_: InvalidPasswordException) { }
        }
    }
    @Test fun inputLimitIsEnforced() {
        try { PdfSession.readLimited(ByteArray(PdfSession.MAX_BYTES + 1).inputStream()); fail("Size limit ignored") }
        catch (_: IllegalArgumentException) { }
    }
}
