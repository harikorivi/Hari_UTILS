package com.xeroxphotopro.core
import org.junit.Assert.*
import org.junit.Test

class QualityTest {
    @Test fun `dark bright and flat images are flagged for review`() {
        for (color in listOf(0x080808,0xfafafa)) {
            val results=QualityMetrics.inspect(64,64,IntArray(4096) { color })
            assertEquals(CheckStatus.REVIEW,results.first { it.label=="Lighting" }.status)
            assertEquals(CheckStatus.REVIEW,results.first { it.label=="Sharpness" }.status)
        }
    }
    @Test fun `uneven subject and patterned corners trigger relevant hints`() {
        val pixels=IntArray(4096) { if (it % 64 < 32) 0x444444 else 0xcccccc }
        val results=QualityMetrics.inspect(64,64,pixels)
        assertEquals(CheckStatus.REVIEW,results.first { it.label=="Shadows" }.status)
        assertEquals(CheckStatus.REVIEW,results.first { it.label=="Background" }.status)
    }
}
