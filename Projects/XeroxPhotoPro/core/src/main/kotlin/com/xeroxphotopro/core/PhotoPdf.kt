package com.xeroxphotopro.core

import java.io.OutputStream
import java.util.Locale

/** Small PDF 1.4 writer. One or two RGB JPEG XObjects are reused across copies on exactly one page.
 * Fractional point MediaBox avoids integer-point A4 rounding. No customer metadata is written.
 */
data class PdfPhoto(val jpeg: ByteArray, val pixelWidth: Int, val pixelHeight: Int)

object PhotoPdf {
    private fun n(value: Double) = String.format(Locale.US, "%.6f", value)
    fun write(out: OutputStream, layout: SheetLayout, jpeg: ByteArray, pixelWidth: Int, pixelHeight: Int) {
        write(out, layout, listOf(PdfPhoto(jpeg,pixelWidth,pixelHeight)))
    }
    fun write(out: OutputStream, layout: SheetLayout, images: List<PdfPhoto>) {
        PhotoSlots.validate(layout,images.size)
        images.forEach { image ->
            require(image.jpeg.size>2 && image.jpeg[0]==0xff.toByte() && image.jpeg[1]==0xd8.toByte()) { "Expected JPEG image." }
            require(image.pixelWidth>0 && image.pixelHeight>0)
        }
        val c=layout.config
        val photos=layout.placements
        val commands = buildString {
                photos.forEachIndexed { index,p ->
                    append("q ${n(mmToPoints(p.width))} 0 0 ${n(mmToPoints(p.height))} ${n(mmToPoints(p.x))} ${n(mmToPoints(layout.height - p.y - p.height))} cm /Photo${PhotoSlots.imageIndex(index, images.size)} Do Q\n")
                    if (c.border) {
                        // 0.2 pt stroke lies entirely INSIDE the photo. Finished outer size is unchanged.
                        val half = .1
                        append("0.55 G 0.2 w ${n(mmToPoints(p.x) + half)} ${n(mmToPoints(layout.height - p.y - p.height) + half)} ${n(mmToPoints(p.width) - .2)} ${n(mmToPoints(p.height) - .2)} re S\n")
                    }
                    if (c.guides) {
                        append("0.55 G 0.2 w\n")
                        LayoutEngine.marks(p).forEach { s ->
                            append("${n(mmToPoints(s.x1))} ${n(mmToPoints(layout.height - s.y1))} m ${n(mmToPoints(s.x2))} ${n(mmToPoints(layout.height - s.y2))} l S\n")
                        }
                    }
                }
            }
        writePage(out,layout.width,layout.height,images,commands)
    }
    /** Shared one-page serializer for passport grids and mixed-image sheets. */
    fun writePage(out: OutputStream, width: Double, height: Double, images: List<PdfPhoto>, commands: String) {
        require(width>0 && height>0 && images.isNotEmpty())
        val pageId=3+images.size
        var count=0L
        val offsets=mutableListOf(0L)
        fun bytes(b: ByteArray) { out.write(b);count+=b.size }
        fun text(s: String)=bytes(s.toByteArray(Charsets.ISO_8859_1))
        fun obj(id: Int,body: ()->Unit) {
            check(id==offsets.size);offsets.add(count);text("$id 0 obj\n");body();text("\nendobj\n")
        }
        fun stream(data: ByteArray,extra: String="") {
            text("<< /Length ${data.size} $extra >>\nstream\n");bytes(data);text("\nendstream")
        }
        text("%PDF-1.4\n%âãÏÓ\n")
        obj(1) { text("<< /Type /Catalog /Pages 2 0 R >>") }
        obj(2) { text("<< /Type /Pages /Kids [$pageId 0 R] /Count 1 >>") }
        images.forEachIndexed { i,image ->
            obj(3+i) { stream(image.jpeg,"/Type /XObject /Subtype /Image /Width ${image.pixelWidth} /Height ${image.pixelHeight} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode") }
        }
        val resources=images.indices.joinToString(" ") { "/Photo$it ${3+it} 0 R" }
        obj(pageId) { text("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${n(mmToPoints(width))} ${n(mmToPoints(height))}] /Resources << /XObject << $resources >> >> /Contents ${pageId+1} 0 R >>") }
        obj(pageId+1) { stream(commands.toByteArray(Charsets.US_ASCII)) }
        val xref=count
        text("xref\n0 ${offsets.size}\n0000000000 65535 f \n")
        offsets.drop(1).forEach { text(String.format(Locale.US,"%010d 00000 n \n",it)) }
        text("trailer\n<< /Size ${offsets.size} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n");out.flush()
    }
}
