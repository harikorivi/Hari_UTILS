package com.xeroxphotopro.core
import org.junit.Assert.*
import org.junit.Test
class HouseLayoutTest {
    @Test fun `one and two copies are true six by four inches and centered`() {
        for(n in 1..2) {
            val page=HouseLayout.calculate(n)
            assertEquals(210.0,page.width,0.0);assertEquals(297.0,page.height,0.0)
            assertEquals(n,page.placements.size)
            page.placements.forEach {p->assertEquals(152.4,p.rect.width,1e-9);assertEquals(101.6,p.rect.height,1e-9);assertEquals(105.0,p.rect.x+p.rect.width/2,1e-9)}
            assertEquals(297.0,page.placements.first().rect.y+page.placements.last().rect.let {it.y+it.height},1e-9)
        }
    }
    @Test fun `three equal frames maximize available height and preserve aspect and spacing`() {
        val p=HouseLayout.calculate(3,CutStyle.GRAY).placements.map {it.rect}
        assertEquals(140.5,p.first().width,1e-9);assertEquals(281.0/3,p.first().height,1e-9)
        p.forEach {assertEquals(1.5,it.width/it.height,1e-9);assertEquals(105.0,it.x+it.width/2,1e-9);assertTrue(it.x>=5 && it.y>=5-1e-9 && it.y+it.height<=292+1e-9)}
        p.zipWithNext().forEach {(a,b)->assertEquals(3.0,b.y-a.y-a.height,1e-9)}
        assertEquals(5.0,p.first().y,1e-9);assertEquals(292.0,p.last().y+p.last().height,1e-9)
        assertTrue(3*(p.first().height+.01)+2*HouseLayout.GAP>297-2*HouseLayout.MARGIN)
    }
    @Test fun `only one to three sheets and supported outlines are accepted`() {
        assertEquals(CutStyle.BLACK,HouseLayout.calculate(1).settings.cut)
        assertEquals(CutStyle.GRAY,HouseLayout.calculate(2,CutStyle.GRAY).settings.cut)
        assertThrows(IllegalArgumentException::class.java) {HouseLayout.calculate(4)}
        assertThrows(IllegalArgumentException::class.java) {HouseLayout.calculate(0)}
    }
}
