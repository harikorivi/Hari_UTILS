package com.xeroxphotopro.core

import kotlin.math.abs
import kotlin.math.roundToInt

/** Conservative color unmixing; alpha and confident interior colors are never changed. */
object EdgeColor {
    fun recover(observed: Int, background: Int, foregroundAnchor: Int, alpha: Float): Int {
        if(alpha !in .12f.. .94f) return observed
        var contrast=0.0;var error=0.0
        for(shift in 16 downTo 0 step 8) {
            val b=(background ushr shift) and 255; val f=(foregroundAnchor ushr shift) and 255
            val c=(observed ushr shift) and 255
            contrast+=(f-b)*(f-b)
            val residual=c-(alpha*f+(1-alpha)*b);error+=residual*residual
        }
        // Reject ambiguous colors and boundaries that do not fit the local compositing model.
        if(contrast<3600 || error>1875) return observed
        var result=-0x1000000
        for(shift in 16 downTo 0 step 8) {
            val c=(observed ushr shift) and 255;val b=(background ushr shift) and 255
            val recovered=(c-(1-alpha)*b)/alpha
            if(recovered < -12 || recovered > 267) return observed
            val delta=(recovered-c).coerceIn(-64f,64f)
            result=result or ((c+delta).roundToInt().coerceIn(0,255) shl shift)
        }
        return result
    }
}
