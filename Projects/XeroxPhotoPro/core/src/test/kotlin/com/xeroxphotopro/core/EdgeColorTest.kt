package com.xeroxphotopro.core
import org.junit.Assert.*
import org.junit.Test
class EdgeColorTest {
    @Test fun `known white contamination is reduced without changing opaque hair`() {
        val corrected=EdgeColor.recover(0xff555555.toInt(),0xffffffff.toInt(),0xff1e1e1e.toInt(),.75f)
        assertTrue((corrected and 255)<40)
        assertEquals(0xff1e1e1e.toInt(),EdgeColor.recover(0xff1e1e1e.toInt(),-1,0xff1e1e1e.toInt(),1f))
    }
    @Test fun `ambiguous color and incompatible skin anchor remain untouched`() {
        assertEquals(0xff999999.toInt(),EdgeColor.recover(0xff999999.toInt(),0xff999999.toInt(),0xff999999.toInt(),.5f))
        val skin=0xffbf8060.toInt()
        assertEquals(skin,EdgeColor.recover(skin,-1,0xff000000.toInt(),.5f))
    }
}
