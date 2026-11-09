package com.xeroxphotopro.core

import kotlin.math.*

enum class SmartMode { MAXIMUM, EXACT, BEST_FIT }
enum class CutStyle { NONE, BLACK, GRAY, DASHED, CORNERS }
data class SmartRequest(val id: String, val size: PhotoSize, val copies: Int = 1, val allowRotation: Boolean = false) {
    init { require(id.isNotBlank());require(copies in 1..1000) { "Use 1–1000 copies or ratio units." } }
}
data class SmartSettings(val mode: SmartMode = SmartMode.MAXIMUM, val orientation: Orientation = Orientation.AUTO,
    val margin: Double = 5.0, val gapX: Double = 3.0, val gapY: Double = 3.0, val cut: CutStyle = CutStyle.CORNERS) {
    init {
        require(margin.isFinite() && margin in 2.0..30.0) { "Use 2–30 mm margins." }
        require(listOf(gapX,gapY).all { it.isFinite() && it in 0.0..20.0 }) { "Use 0–20 mm spacing." }
        require(cut!=CutStyle.CORNERS || min(gapX,gapY)>=2) { "Corner marks need at least 2 mm horizontal and vertical spacing." }
    }
}
data class SmartPlacement(val id: String, val rect: Placement, val rotated: Boolean)
data class SmartLayout(val width: Double, val height: Double, val placements: List<SmartPlacement>, val settings: SmartSettings) {
    val counts get()=placements.groupingBy { it.id }.eachCount()
    val usedArea get()=placements.sumOf { it.rect.width*it.rect.height }
    val unusedPercent get()=100*(1-usedArea/(width*height))
}
class SmartOverflow(val missing: Map<String,Int>, val alternative: SmartLayout?) : IllegalArgumentException(
    "Requested copies do not fit this packing. Missing: ${missing.entries.joinToString { "${it.key}: ${it.value}" }}. Try Auto orientation or Best Fit, reduce copies, or adjust sizes. No sheet was generated."
)
/** Bounded multi-start MaxRects packing. Exact and Best Fit never omit requests.
 * Maximum fills complete integer batches of the operator's ratios. This is a practical heuristic,
 * not a proof of global optimality for arbitrary mixed rectangles.
 */
object SmartLayoutEngine {
    private data class Box(val x: Double,val y: Double,val w: Double,val h: Double)
    private const val EPS=1e-7
    fun calculate(requests: List<SmartRequest>, settings: SmartSettings): SmartLayout {
        require(requests.isNotEmpty() && requests.size<=20) { "Select 1–20 images." }
        require(requests.map { it.id }.distinct().size==requests.size)
        // A single image has no ratio: switching from an exact count must still fill capacity.
        if(settings.mode==SmartMode.MAXIMUM && requests.size==1 && requests.single().copies!=1)
            return calculate(listOf(requests.single().copy(copies=1)),settings)
        require(requests.sumOf { it.copies }<=1000) { "Use at most 1000 total copies or ratio units." }
        val orientations=if(settings.orientation==Orientation.AUTO) listOf(Orientation.PORTRAIT,Orientation.LANDSCAPE) else listOf(settings.orientation)
        if(settings.mode!=SmartMode.MAXIMUM) {
            val attempts=orientations.map { pack(requests,settings,it) }
            val full=attempts.filter { it.placements.size==requests.sumOf { r->r.copies } }
            if(full.isNotEmpty()) return full.minBy { occupied(it) }
            val partial=attempts.maxBy { it.placements.size }
            val missing=requests.associate { it.id to (it.copies-(partial.counts[it.id]?:0)) }.filterValues { it>0 }
            val opposite=if(settings.orientation==Orientation.AUTO) null else pack(requests,settings,if(settings.orientation==Orientation.PORTRAIT) Orientation.LANDSCAPE else Orientation.PORTRAIT)
            throw SmartOverflow(missing,opposite?.takeIf { it.placements.size==requests.sumOf { r->r.copies } })
        }
        val batchArea=requests.sumOf { it.size.width*it.size.height*it.copies }
        val perBatch=requests.sumOf { it.copies }
        var best: SmartLayout?=null
        for(o in orientations) {
            val (w,h)=dimensions(o);val inset=settings.margin+if(settings.cut==CutStyle.CORNERS) 1 else 0
            var low=0;var high=min(1000/perBatch,floor((w-2*inset)*(h-2*inset)/batchArea).toInt())
            // All heuristic trials are deterministic. Search bounded batch counts, then verify counts.
            while(low<high) {
                val mid=(low+high+1)/2
                val trial=pack(requests.map { it.copy(copies=it.copies*mid) },settings,o)
                if(trial.placements.size==perBatch*mid) low=mid else high=mid-1
            }
            if(low>0) {
                val trial=pack(requests.map { it.copy(copies=it.copies*low) },settings,o)
                if(best==null || trial.placements.size>best.placements.size || trial.placements.size==best.placements.size && occupied(trial)<occupied(best)) best=trial
            }
        }
        return best ?: throw SmartOverflow(requests.associate { it.id to it.copies },null)
    }
    private fun dimensions(o: Orientation)=if(o==Orientation.LANDSCAPE) 297.0 to 210.0 else 210.0 to 297.0
    private fun occupied(layout: SmartLayout)=(layout.placements.maxOfOrNull { it.rect.y+it.rect.height }?:Double.MAX_VALUE)*layout.width
    private fun pack(requests: List<SmartRequest>, s: SmartSettings, o: Orientation): SmartLayout {
        val expanded=requests.flatMap { r->List(r.copies) { r } }
        val orders=if(s.mode==SmartMode.EXACT) listOf(expanded) else listOf(
            expanded.sortedByDescending { it.size.width*it.size.height },
            expanded.sortedByDescending { max(it.size.width,it.size.height) },
            expanded.sortedByDescending { it.size.height },expanded.sortedByDescending { it.size.width })
        var best: SmartLayout?=null
        for(order in orders) for(scoreMode in 0..2) {
            val (w,h)=dimensions(o);val inset=s.margin+if(s.cut==CutStyle.CORNERS) 1 else 0
            var free=listOf(Box(inset,inset,w-2*inset+s.gapX,h-2*inset+s.gapY))
            val placed=mutableListOf<SmartPlacement>()
            for(item in order) {
                var choice: Box?=null;var rotated=false;var score=Double.MAX_VALUE
                for(box in free) for(turn in 0..if(item.allowRotation) 1 else 0) {
                    val rw=(if(turn==0) item.size.width else item.size.height)+s.gapX
                    val rh=(if(turn==0) item.size.height else item.size.width)+s.gapY
                    if(rw>box.w+EPS || rh>box.h+EPS) continue
                    val value=if(scoreMode==0) min(box.w-rw,box.h-rh)*100000+max(box.w-rw,box.h-rh) else if(scoreMode==1) box.y*100000+box.x else box.x*100000+box.y
                    if(value<score) { score=value;choice=Box(box.x,box.y,rw,rh);rotated=turn==1 }
                }
                val used=choice ?: continue
                placed+=SmartPlacement(item.id,Placement(used.x,used.y,used.w-s.gapX,used.h-s.gapY),rotated)
                val split=free.flatMap { b ->
                    if(used.x>=b.x+b.w-EPS || used.x+used.w<=b.x+EPS || used.y>=b.y+b.h-EPS || used.y+used.h<=b.y+EPS) listOf(b)
                    else buildList {
                        if(used.x>b.x+EPS) add(Box(b.x,b.y,used.x-b.x,b.h))
                        if(used.x+used.w<b.x+b.w-EPS) add(Box(used.x+used.w,b.y,b.x+b.w-used.x-used.w,b.h))
                        if(used.y>b.y+EPS) add(Box(b.x,b.y,b.w,used.y-b.y))
                        if(used.y+used.h<b.y+b.h-EPS) add(Box(b.x,used.y+used.h,b.w,b.y+b.h-used.y-used.h))
                    }
                }.distinct()
                free=split.filterIndexed { i,b -> split.withIndex().none { (j,a)->i!=j && b.x>=a.x-EPS && b.y>=a.y-EPS && b.x+b.w<=a.x+a.w+EPS && b.y+b.h<=a.y+a.h+EPS } }
            }
            val trial=SmartLayout(w,h,placed,s)
            if(best==null || trial.placements.size>best.placements.size || trial.placements.size==best.placements.size && occupied(trial)<occupied(best)) best=trial
        }
        return best!!
    }
}
