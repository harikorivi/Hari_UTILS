package com.xeroxphotopro.core
import org.junit.Assert.*
import org.junit.Test
class FramingTest {
    @Test fun `camera distance and resolution preserve eye and chin ratios`() {
        for((w,h) in listOf(1200 to 1400,1600 to 1200)) for(scale in listOf(.5,1.0,2.0)) {
            val f=FaceBox(500-100*scale,500-120*scale,500+100*scale,500+200*scale)
            val a=FaceAnchor(500.0,500.0,500+180*scale)
            val result=FaceFraming.calculate(w,h,PhotoSize(),f,a,FramingTarget())
            assertFalse(result.constrained);assertTrue(result.usedLandmarks)
            assertEquals(.40,(a.eyeY-result.crop.y)/result.crop.height,1e-9)
            assertEquals(.32,(a.chinY-a.eyeY)/result.crop.height,1e-9)
            assertEquals(.5,(a.eyeX-result.crop.x)/result.crop.width,1e-9)
            assertEquals(35.0/45,result.crop.width/result.crop.height,1e-9)
        }
    }
    @Test fun `missing space is flagged and no pixels invented`() {
        val r=FaceFraming.calculate(400,500,PhotoSize(),FaceBox(0.0,0.0,300.0,400.0),FaceAnchor(140.0,100.0,380.0),FramingTarget())
        assertTrue(r.constrained);assertTrue(r.crop.x>=0 && r.crop.y>=0)
        assertTrue(r.crop.x+r.crop.width<=400.001 && r.crop.y+r.crop.height<=500.001)
    }
    @Test fun `custom targets and missing landmark fallback are explicit`() {
        val f=FaceBox(300.0,300.0,600.0,700.0)
        val a=FaceAnchor(450.0,450.0,680.0)
        val r=FaceFraming.calculate(1200,1600,PhotoSize(),f,a,FramingTarget(.42,.30))
        assertEquals(.42,(a.eyeY-r.crop.y)/r.crop.height,1e-9)
        assertFalse(FaceFraming.calculate(1200,1600,PhotoSize(),f,null,FramingTarget()).usedLandmarks)
    }
}
