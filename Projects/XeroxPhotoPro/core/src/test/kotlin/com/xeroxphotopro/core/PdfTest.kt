package com.xeroxphotopro.core

import org.junit.Assert.*
import org.junit.Test
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.pdfparser.PDFStreamParser
import org.apache.pdfbox.contentstream.operator.Operator
import org.apache.pdfbox.cos.COSNumber
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

class PdfTest {
    private fun pdf(c: SheetConfig): ByteArray {
        val image = BufferedImage(pixelsFor(c.photo.width), pixelsFor(c.photo.height), BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply { color = Color(18, 100, 100); fillRect(0, 0, image.width, image.height); color = Color.WHITE; fillRect(image.width / 4, image.height / 4, image.width / 2, image.height / 2); dispose() }
        val jpeg = ByteArrayOutputStream().also { ImageIO.write(image, "jpg", it) }.toByteArray()
        return ByteArrayOutputStream().also { PhotoPdf.write(it, LayoutEngine.calculate(c), jpeg, image.width, image.height) }.toByteArray()
    }
    @Test fun `independent parser confirms one page exact A4 and every photo matrix`() {
        for (orientation in Orientation.values()) for (size in listOf(PhotoSize(), PhotoSize(50.0,50.0), PhotoSize(32.5,41.7))) for (border in listOf(false,true)) {
            val c = SheetConfig(photo = size, orientation = orientation, border = border)
            val layout = LayoutEngine.calculate(c)
            PDDocument.load(pdf(c)).use { doc ->
                assertEquals(1, doc.numberOfPages)
                val page = doc.getPage(0)
                assertEquals(layout.width, page.mediaBox.width / POINTS_PER_MM, .0001)
                assertEquals(layout.height, page.mediaBox.height / POINTS_PER_MM, .0001)
                val parser = PDFStreamParser(page); parser.parse(); val tokens = parser.tokens
                val matrices = tokens.indices.filter { (tokens[it] as? Operator)?.name == "cm" }
                assertEquals(layout.placements.size, matrices.size)
                matrices.forEachIndexed { i, pos ->
                    val v = tokens.subList(pos - 6, pos).map { (it as COSNumber).floatValue().toDouble() }; val p = layout.placements[i]
                    assertEquals(p.width, v[0] / POINTS_PER_MM, .0001)
                    assertEquals(p.height, v[3] / POINTS_PER_MM, .0001)
                    assertEquals(p.x, v[4] / POINTS_PER_MM, .0001)
                    assertEquals(layout.height - p.y - p.height, v[5] / POINTS_PER_MM, .0001)
                }
                if (border) assertEquals(layout.placements.size, tokens.count { (it as? Operator)?.name == "re" })
            }
        }
    }
    @Test fun `maximum sheet renders and shares one embedded image`() {
        val bytes = pdf(SheetConfig())
        PDDocument.load(bytes).use { doc ->
            val image = PDFRenderer(doc).renderImageWithDPI(0, 72f)
            assertTrue(image.width in 595..596 && image.height in 841..842)
            assertEquals(Color.WHITE.rgb, image.getRGB(2, 2))
            assertNotEquals(Color.WHITE.rgb, image.getRGB(34, 20))
            assertEquals(1, doc.getPage(0).resources.xObjectNames.count())
            File("build/evidence").mkdirs()
            File("build/evidence/sample-single-a4.pdf").writeBytes(bytes)
            ImageIO.write(image, "png", File("build/evidence/sample-single-a4.png"))
        }
    }
    @Test fun `one huge custom photo has exact physical size`() {
        val c = SheetConfig(PhotoSize(190.0, 280.0))
        PDDocument.load(pdf(c)).use { assertEquals(1, it.numberOfPages) }
    }
}
