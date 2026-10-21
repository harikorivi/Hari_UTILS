package com.xeroxphotopro.core

import org.junit.Assert.*
import org.junit.Test

class LayoutTest {
    @Test fun `default selects portrait maximum 30 instead of landscape 28`() {
        val c = SheetConfig()
        assertEquals(30, LayoutEngine.grid(c, Orientation.PORTRAIT).capacity)
        assertEquals(28, LayoutEngine.grid(c, Orientation.LANDSCAPE).capacity)
        val sheet = LayoutEngine.calculate(c)
        assertEquals(Orientation.PORTRAIT, sheet.grid.orientation)
        assertEquals(30, sheet.placements.size)
        assertEquals(5, sheet.columns); assertEquals(6, sheet.rows)
    }
    @Test fun `auto selects landscape when better and portrait on ties`() {
        val landscape = LayoutEngine.calculate(SheetConfig(photo = PhotoSize(25.0, 35.0)))
        assertEquals(Orientation.LANDSCAPE, landscape.grid.orientation)
        assertEquals(50, landscape.capacity)
        val tie = LayoutEngine.calculate(SheetConfig(photo = PhotoSize(50.0, 50.0)))
        assertEquals(15, tie.capacity); assertEquals(Orientation.PORTRAIT, tie.grid.orientation)
    }
    @Test fun `real feedback 24 square photos is rejected at 15 not paginated`() {
        val c = SheetConfig(PhotoSize(50.0, 50.0), copies = 24, orientation = Orientation.LANDSCAPE)
        val error = assertThrows(CapacityExceeded::class.java) { LayoutEngine.calculate(c) }
        assertEquals(15, error.maximum); assertEquals(24, error.requested)
        assertTrue(error.message!!.contains("15 or fewer"))
        assertEquals(15, LayoutEngine.calculate(c.copy(copies = null)).placements.size)
    }
    @Test fun `reduced counts retain dimensions and overflow never silently shrinks`() {
        for (count in listOf(1, 2, 12, 29, 30)) {
            val sheet = LayoutEngine.calculate(SheetConfig(copies = count))
            assertEquals(count, sheet.placements.size)
            sheet.placements.forEach { assertEquals(35.0, it.width, 0.0); assertEquals(45.0, it.height, 0.0) }
        }
        listOf(31, 40, 500, 10000).forEach { count -> assertThrows(CapacityExceeded::class.java) { LayoutEngine.calculate(SheetConfig(copies = count)) } }
    }
    @Test fun `all combinations fit inside margins with nonoverlapping external marks`() {
        for (o in Orientation.values()) for (guides in listOf(false, true)) for (border in listOf(false, true))
            for (size in listOf(PhotoSize(), PhotoSize(25.0, 35.0), PhotoSize(50.0, 50.0), PhotoSize(32.5, 41.7)))
                for (margin in listOf(2.0, 5.0, 20.0)) for (gap in listOf(2.0, 3.0, 10.0)) {
                    val c = SheetConfig(size, orientation = o, margin = margin, gap = gap, guides = guides, border = border)
                    val full = LayoutEngine.calculate(c)
                    for (count in listOf(1, full.capacity / 2, full.capacity).filter { it > 0 }) {
                        val layout = LayoutEngine.calculate(c.copy(copies = count))
                        layout.placements.forEachIndexed { i, p ->
                            assertTrue(p.x >= margin && p.y >= margin)
                            assertTrue(p.x + p.width <= layout.width - margin + 1e-8)
                            assertTrue(p.y + p.height <= layout.height - margin + 1e-8)
                            layout.placements.drop(i + 1).forEach { q ->
                                assertTrue(p.x + p.width <= q.x + 1e-8 || q.x + q.width <= p.x + 1e-8 || p.y + p.height <= q.y + 1e-8 || q.y + q.height <= p.y + 1e-8)
                            }
                            if (guides) LayoutEngine.marks(p).forEach { s ->
                                assertTrue(listOf(s.x1, s.x2).all { it - .04 >= margin && it + .04 <= layout.width - margin })
                                assertTrue(listOf(s.y1, s.y2).all { it - .04 >= margin && it + .04 <= layout.height - margin })
                            }
                        }
                    }
                }
    }
    @Test fun `custom one photo edge and no fit are handled`() {
        assertEquals(1, LayoutEngine.calculate(SheetConfig(PhotoSize(190.0, 280.0))).capacity)
        val wide = LayoutEngine.calculate(SheetConfig(PhotoSize(250.0, 180.0)))
        assertEquals(1, wide.capacity); assertEquals(Orientation.LANDSCAPE, wide.grid.orientation)
        assertThrows(IllegalArgumentException::class.java) { LayoutEngine.calculate(SheetConfig(PhotoSize(287.0, 287.0))) }
        // Exact printable-area fit, no guides: no lost row due to floating-point math.
        assertEquals(1, LayoutEngine.calculate(SheetConfig(PhotoSize(200.0, 287.0), guides = false)).capacity)
    }
    @Test fun `invalid values and guide gaps fail clearly`() {
        listOf<() -> Unit>({ SheetConfig(copies = 0) }, { SheetConfig(copies = -1) },
            { SheetConfig(margin = Double.NaN) }, { SheetConfig(gap = -1.0) },
            { SheetConfig(guides = true, gap = 0.0) }, { PhotoSize(Double.POSITIVE_INFINITY, 45.0) },
            { PhotoSize(0.0, 45.0) }, { PhotoSize(288.0, 35.0) }
        ).forEach { assertThrows(IllegalArgumentException::class.java) { it() } }
    }
    @Test fun `geometry reports occupied and remaining space deterministically`() {
        val g = LayoutEngine.grid(SheetConfig(), Orientation.PORTRAIT)
        assertEquals(187.0, g.occupiedWidth, 1e-9); assertEquals(285.0, g.occupiedHeight, 1e-9)
        assertEquals(11.0, g.remainingWidth, 1e-9); assertEquals(0.0, g.remainingHeight, 1e-9)
        assertEquals(72.0, mmToPoints(25.4), 1e-10)
        assertEquals(414, pixelsFor(35.0)); assertEquals(532, pixelsFor(45.0))
    }
}
