package com.xeroxphotopro.core

import org.junit.Assert.*
import org.junit.Test

class CropTest {
    @Test fun `manual crop preserves aspect ratio and remains in image at all extremes`() {
        for ((w,h) in listOf(800 to 1200, 1200 to 800, 200 to 200))
            for (z in listOf(1.0, 2.5, 5.0)) for (x in listOf(0.0,.5,1.0)) for (y in listOf(0.0,.5,1.0)) {
                val c = CropEngine.manual(w, h, PhotoSize(), z, x, y)
                assertEquals(35.0 / 45.0, c.width / c.height, 1e-10)
                assertTrue(c.x >= 0 && c.y >= 0 && c.x + c.width <= w + 1e-9 && c.y + c.height <= h + 1e-9)
            }
    }
    @Test fun `face crop is bounded even near image edge`() {
        val crop = CropEngine.aroundFace(1000, 1500, PhotoSize(), FaceBox(0.0, 0.0, 300.0, 400.0))
        assertEquals(0.0, crop.x, 1e-9); assertEquals(0.0, crop.y, 1e-9)
        assertEquals(35.0 / 45, crop.width / crop.height, 1e-9)
    }
    @Test fun `typical face retains headroom and room for shoulders`() {
        val crop = CropEngine.aroundFace(1200, 1600, PhotoSize(), FaceBox(420.0, 400.0, 780.0, 900.0))
        assertTrue(crop.y < 400); assertTrue(crop.y + crop.height > 900)
        assertTrue(crop.x < 420 && crop.x + crop.width > 780)
    }
}
