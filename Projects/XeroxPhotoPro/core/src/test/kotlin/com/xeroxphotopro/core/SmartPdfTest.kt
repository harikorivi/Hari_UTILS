package com.xeroxphotopro.core
import org.junit.Assert.*
import org.junit.Test
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
class SmartPdfTest {
    private fun photo(color: Color): PdfPhoto {
        val image=BufferedImage(100,150,BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply {this.color=color;fillRect(0,0,100,150);dispose()}
        return PdfPhoto(ByteArrayOutputStream().also {ImageIO.write(image,"jpg",it)}.toByteArray(),100,150)
    }
    @Test fun `mixed size PDF has one precise page and correct image per placement`() {
        for(o in listOf(Orientation.PORTRAIT,Orientation.LANDSCAPE)) {
            val req=listOf(SmartRequest("A",PhotoSize(),8),SmartRequest("B",PhotoSize(50.0,50.0),4),SmartRequest("C",PhotoSize(20.0,30.0),6))
            val layout=SmartLayoutEngine.calculate(req,SmartSettings(mode=SmartMode.BEST_FIT,orientation=o))
            val colors=mapOf("A" to Color.RED,"B" to Color.BLUE,"C" to Color.GREEN)
            val bytes=ByteArrayOutputStream().also {SmartPdf.write(it,layout,colors.mapValues {(_,v)->photo(v)})}.toByteArray()
            PDDocument.load(bytes).use {doc->
                assertEquals(1,doc.numberOfPages);val page=doc.getPage(0)
                assertEquals(layout.width,page.mediaBox.width/POINTS_PER_MM,.0001);assertEquals(layout.height,page.mediaBox.height/POINTS_PER_MM,.0001)
                val image=PDFRenderer(doc).renderImageWithDPI(0,72f)
                layout.placements.forEach {p->val c=Color(image.getRGB(mmToPoints(p.rect.x+p.rect.width/2).toInt(),mmToPoints(p.rect.y+p.rect.height/2).toInt()));val expected=colors.getValue(p.id)
                    assertTrue(kotlin.math.abs(c.red-expected.red)<5 && kotlin.math.abs(c.blue-expected.blue)<5 && kotlin.math.abs(c.green-expected.green)<5)
                }
            }
        }
    }
}
