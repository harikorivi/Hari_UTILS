package com.xeroxphotopro.core
import java.io.OutputStream
import java.util.Locale
object SmartPdf {
    fun write(out: OutputStream, layout: SmartLayout, images: Map<String,PdfPhoto>) {
        val ids=layout.placements.map { it.id }.distinct()
        require(ids.all { images.containsKey(it) })
        fun n(v: Double)=String.format(Locale.US,"%.6f",v)
        fun pt(v: Double)=n(mmToPoints(v))
        val commands=buildString {
            layout.placements.forEach { item ->
                val p=item.rect;val index=ids.indexOf(item.id)
                if(!item.rotated) append("q ${pt(p.width)} 0 0 ${pt(p.height)} ${pt(p.x)} ${pt(layout.height-p.y-p.height)} cm /Photo$index Do Q\n")
                else append("q 0 -${pt(p.height)} ${pt(p.width)} 0 ${pt(p.x)} ${pt(layout.height-p.y)} cm /Photo$index Do Q\n")
                val cut=layout.settings.cut
                if(cut==CutStyle.CORNERS) {
                    append("0.55 G 0.2 w [] 0 d\n")
                    LayoutEngine.marks(p).forEach { a->append("${pt(a.x1)} ${pt(layout.height-a.y1)} m ${pt(a.x2)} ${pt(layout.height-a.y2)} l S\n") }
                } else if(cut!=CutStyle.NONE) {
                    append(if(cut==CutStyle.GRAY) "0.55 G " else "0 G ")
                    append(if(cut==CutStyle.DASHED) "[2 2] 0 d " else "[] 0 d ")
                    append("0.2 w ${n(mmToPoints(p.x)+.1)} ${n(mmToPoints(layout.height-p.y-p.height)+.1)} ${n(mmToPoints(p.width)-.2)} ${n(mmToPoints(p.height)-.2)} re S\n")
                }
            }
        }
        PhotoPdf.writePage(out,layout.width,layout.height,ids.map { images.getValue(it) },commands)
    }
}
