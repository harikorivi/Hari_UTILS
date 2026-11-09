package com.xeroxphotopro.core

import kotlin.math.*

data class BrushPoint(val u: Double, val v: Double)
data class MaskStroke(val points: List<BrushPoint>, val radius: Double, val restore: Boolean) {
    init {
        require(points.size in 1..256 && points.all { it.u.isFinite() && it.v.isFinite() && it.u in 0.0..1.0 && it.v in 0.0..1.0 })
        require(radius.isFinite() && radius in .002.. .1)
    }
    fun clockwise() = copy(points = points.map { BrushPoint(1-it.v,it.u) })
}

object MaskRepair {
    /** Coordinates and brush radius belong to the original image, so crop changes cannot move repairs. */
    fun apply(alpha: FloatArray, width: Int, height: Int, sourceWidth: Int, sourceHeight: Int, strokes: List<MaskStroke>): FloatArray {
        require(width > 0 && height > 0 && alpha.size == width*height && sourceWidth > 0 && sourceHeight > 0)
        require(strokes.size <= 64)
        val result=alpha.copyOf()
        for (stroke in strokes) {
            val radius=stroke.radius*min(sourceWidth,sourceHeight)
            val pairs=if(stroke.points.size==1) listOf(stroke.points[0] to stroke.points[0]) else stroke.points.zipWithNext()
            for ((a,b) in pairs) {
                val ax=a.u*sourceWidth; val ay=a.v*sourceHeight
                val bx=b.u*sourceWidth; val by=b.v*sourceHeight
                val dx=bx-ax; val dy=by-ay; val length=dx*dx+dy*dy
                val left=floor((min(ax,bx)-radius)*width/sourceWidth).toInt().coerceIn(0,width-1)
                val right=ceil((max(ax,bx)+radius)*width/sourceWidth).toInt().coerceIn(0,width-1)
                val top=floor((min(ay,by)-radius)*height/sourceHeight).toInt().coerceIn(0,height-1)
                val bottom=ceil((max(ay,by)+radius)*height/sourceHeight).toInt().coerceIn(0,height-1)
                for(y in top..bottom) for(x in left..right) {
                    val px=(x+.5)*sourceWidth/width; val py=(y+.5)*sourceHeight/height
                    val t=if(length==0.0) 0.0 else (((px-ax)*dx+(py-ay)*dy)/length).coerceIn(0.0,1.0)
                    val distance=hypot(px-ax-t*dx,py-ay-t*dy)
                    // Solid interior restores real pixels, with a narrow feather to avoid a hard seam.
                    val brush=((radius-distance)/(radius*.2)).coerceIn(0.0,1.0).toFloat()
                    val i=y*width+x
                    result[i]=if(stroke.restore) max(result[i],brush) else min(result[i],1-brush)
                }
            }
        }
        return result
    }
}
