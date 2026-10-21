package com.xeroxphotopro.core

import java.io.OutputStream
import java.util.Locale

/** Small PDF 1.4 writer. One RGB JPEG XObject is reused across copies on exactly one page.
 * Fractional point MediaBox avoids integer-point A4 rounding. No customer metadata is written.
 */
object PhotoPdf {
    private fun n(value: Double) = String.format(Locale.US, "%.6f", value)
    fun write(out: OutputStream, layout: SheetLayout, jpeg: ByteArray, pixelWidth: Int, pixelHeight: Int) {
        require(jpeg.size > 2 && jpeg[0] == 0xff.toByte() && jpeg[1] == 0xd8.toByte()) { "Expected JPEG image." }
        require(pixelWidth > 0 && pixelHeight > 0)
        var count = 0L
        val offsets = mutableListOf(0L)
        fun bytes(b: ByteArray) { out.write(b); count += b.size }
        fun text(s: String) = bytes(s.toByteArray(Charsets.ISO_8859_1))
        fun obj(id: Int, body: () -> Unit) {
            check(id == offsets.size)
            offsets.add(count); text("$id 0 obj\n"); body(); text("\nendobj\n")
        }
        fun stream(data: ByteArray, extra: String = "") {
            text("<< /Length ${data.size} $extra >>\nstream\n"); bytes(data); text("\nendstream")
        }
        text("%PDF-1.4\n%âãÏÓ\n")
        obj(1) { text("<< /Type /Catalog /Pages 2 0 R >>") }
        obj(2) { text("<< /Type /Pages /Kids [4 0 R] /Count 1 >>") }
        obj(3) { stream(jpeg, "/Type /XObject /Subtype /Image /Width $pixelWidth /Height $pixelHeight /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode") }
        val c = layout.config
        run {
            val id = 4
            val photos = layout.placements
            obj(id) { text("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${n(mmToPoints(layout.width))} ${n(mmToPoints(layout.height))}] /Resources << /XObject << /Photo 3 0 R >> >> /Contents ${id + 1} 0 R >>") }
            val commands = buildString {
                photos.forEach { p ->
                    append("q ${n(mmToPoints(p.width))} 0 0 ${n(mmToPoints(p.height))} ${n(mmToPoints(p.x))} ${n(mmToPoints(layout.height - p.y - p.height))} cm /Photo Do Q\n")
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
            obj(id + 1) { stream(commands.toByteArray(Charsets.US_ASCII)) }
        }
        val xref = count
        text("xref\n0 ${offsets.size}\n0000000000 65535 f \n")
        offsets.drop(1).forEach { text(String.format(Locale.US, "%010d 00000 n \n", it)) }
        text("trailer\n<< /Size ${offsets.size} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        out.flush()
    }
}
