package com.xeroxphotopro.core

import kotlin.math.abs

/** Operator guidance, not an official passport or visa specification. Ratios are of output height. */
data class FramingTarget(val eyeLine: Double = .40, val eyeToChin: Double = .32) {
    init { require(eyeLine.isFinite() && eyeLine in .32.. .48 && eyeToChin.isFinite() && eyeToChin in .24.. .40) }
}
data class FaceAnchor(val eyeX: Double, val eyeY: Double, val chinY: Double)
data class FramingResult(val crop: Crop, val constrained: Boolean, val usedLandmarks: Boolean)

object FaceFraming {
    fun calculate(w: Int, h: Int, size: PhotoSize, face: FaceBox, anchor: FaceAnchor?, target: FramingTarget): FramingResult {
        require(listOf(face.left,face.top,face.right,face.bottom).all { it.isFinite() })
        require(face.right>face.left && face.bottom>face.top)
        val reliable = anchor?.takeIf {
            listOf(it.eyeX,it.eyeY,it.chinY).all { n -> n.isFinite() } &&
                it.eyeX in face.left..face.right && it.eyeY in face.top..face.bottom &&
                it.chinY > it.eyeY && (it.chinY-it.eyeY)/(face.bottom-face.top) in .25.. .8
        }
        val base=CropEngine.manual(w,h,size)
        // Eyes-to-chin uses landmarks instead of camera distance or bounding-box width.
        val desiredHeight=if(reliable!=null) (reliable.chinY-reliable.eyeY)/target.eyeToChin else (face.bottom-face.top)/.60
        val desiredWidth=desiredHeight*size.width/size.height
        val height=desiredHeight.coerceIn(base.height/5,base.height)
        val width=height*size.width/size.height
        val center=reliable?.eyeX ?: (face.left+face.right)/2
        val idealX=center-width/2
        val idealY=if(reliable!=null) reliable.eyeY-height*target.eyeLine else face.top-height*.13
        val x=idealX.coerceIn(0.0,(w-width).coerceAtLeast(0.0))
        val y=idealY.coerceIn(0.0,(h-height).coerceAtLeast(0.0))
        // Crown is not a reliable ML Kit landmark. Never claim measured head-height compliance.
        val estimatedCrown=face.top-(face.bottom-face.top)*.12
        val constrained=abs(height-desiredHeight)>1 || abs(x-idealX)>1 || abs(y-idealY)>1 ||
            face.left<x+width*.03 || face.right>x+width*.97 || estimatedCrown<y || face.bottom>y+height*.90
        check(desiredWidth.isFinite())
        return FramingResult(Crop(x,y,width,height),constrained,reliable!=null)
    }
}
