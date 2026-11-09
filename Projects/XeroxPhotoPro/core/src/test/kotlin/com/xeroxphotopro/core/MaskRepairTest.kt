package com.xeroxphotopro.core

import org.junit.Assert.*
import org.junit.Test

class MaskRepairTest {
    @Test fun `restore rescues zero confidence ear without changing remote background`() {
        val mask=FloatArray(100*100)
        val restored=MaskRepair.apply(mask,100,100,1000,1000,listOf(MaskStroke(listOf(BrushPoint(.2,.5)),.05,true)))
        assertEquals(1f,restored[50*100+20],0f)
        assertEquals(0f,restored[50*100+50],0f)
        assertEquals(0f,mask[50*100+20],0f)
        assertTrue(restored.any { it>0f && it<1f })
    }
    @Test fun `continuous strokes ordered erase and rotation retain source geometry`() {
        val stroke=MaskStroke(listOf(BrushPoint(.1,.5),BrushPoint(.4,.5)),.025,true)
        val erase=MaskStroke(listOf(BrushPoint(.25,.5)),.04,false)
        val repaired=MaskRepair.apply(FloatArray(200*100),200,100,2000,1000,listOf(stroke,erase))
        assertEquals(1f,repaired[50*200+30],0f)
        assertEquals(0f,repaired[50*200+50],0f)
        assertEquals(0f,repaired[20*200+30],0f)
        assertEquals(BrushPoint(.5,.1),stroke.clockwise().points.first())
        assertEquals(stroke.radius,stroke.clockwise().radius,0.0)
        assertThrows(IllegalArgumentException::class.java) { MaskStroke(listOf(BrushPoint(Double.NaN,.5)),.02,true) }
    }
}
