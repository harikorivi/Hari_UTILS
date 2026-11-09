package com.xeroxphotopro.core
import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class SmartLayoutTest {
    private fun request(id: String,w: Double=35.0,h: Double=45.0,n: Int=1,rotate: Boolean=false)=SmartRequest(id,PhotoSize(w,h),n,rotate)
    private fun verify(result: SmartLayout,requests: List<SmartRequest>,exact: Boolean=true) {
        val s=result.settings;val inset=s.margin+if(s.cut==CutStyle.CORNERS) 1 else 0
        if(exact) assertEquals(requests.associate { it.id to it.copies },result.counts)
        result.placements.forEachIndexed { i,p ->
            val spec=requests.single { it.id==p.id };val r=p.rect
            assertTrue(!p.rotated || spec.allowRotation)
            assertEquals(if(p.rotated) spec.size.height else spec.size.width,r.width,.00001)
            assertEquals(if(p.rotated) spec.size.width else spec.size.height,r.height,.00001)
            assertTrue(r.x>=inset-.00001 && r.y>=inset-.00001 && r.x+r.width<=result.width-inset+.00001 && r.y+r.height<=result.height-inset+.00001)
            result.placements.drop(i+1).forEach { q->val b=q.rect
                assertTrue("Overlapping or missing gap",r.x+r.width+s.gapX<=b.x+.00001 || b.x+b.width+s.gapX<=r.x+.00001 || r.y+r.height+s.gapY<=b.y+.00001 || b.y+b.height+s.gapY<=r.y+.00001)
            }
        }
    }
    @Test fun `single maximum chooses practical grid and exact never adds copies`() {
        val r=listOf(request("A",40.0,50.0))
        val maximum=SmartLayoutEngine.calculate(r,SmartSettings())
        assertEquals(20,maximum.placements.size);verify(maximum,r,false)
        assertEquals(20,SmartLayoutEngine.calculate(r.map {it.copy(copies=17)},SmartSettings()).placements.size)
        val exact=r.map { it.copy(copies=10) }
        verify(SmartLayoutEngine.calculate(exact,SmartSettings(mode=SmartMode.EXACT)),exact)
    }
    @Test fun `two three and five image mixed size examples keep all copies`() {
        val cases=listOf(
            listOf(request("A",n=10),request("B",n=15)),
            listOf(request("A",n=8),request("B",40.0,50.0,6),request("C",25.0,35.0,10)),
            listOf(request("A",50.0,50.0,4),request("B",30.0,40.0,8),request("C",20.0,30.0,12)),
            (1..5).map { request("$it",20.0+it*3,25.0+it*4,3) })
        for(r in cases) verify(SmartLayoutEngine.calculate(r,SmartSettings(mode=SmartMode.BEST_FIT)),r)
    }
    @Test fun `maximum uses complete ratio batches and includes every image`() {
        val r=listOf(request("A",n=2),request("B",n=1))
        val result=SmartLayoutEngine.calculate(r,SmartSettings())
        assertEquals(30,result.placements.size);assertEquals(2*result.counts.getValue("B"),result.counts.getValue("A"));verify(result,r,false)
    }
    @Test fun `rotation requires consent and alternative orientation is offered`() {
        val r=listOf(request("A",250.0,180.0))
        val s=SmartSettings(mode=SmartMode.BEST_FIT,orientation=Orientation.PORTRAIT)
        try {SmartLayoutEngine.calculate(r,s);fail("Must reject unrotated portrait")} catch(e: SmartOverflow) {assertEquals(1,e.missing["A"]);assertNotNull(e.alternative);assertEquals(297.0,e.alternative!!.width,0.0)}
        val rotated=r.map {it.copy(allowRotation=true)}
        val result=SmartLayoutEngine.calculate(rotated,s);verify(result,rotated);assertTrue(result.placements.single().rotated)
    }
    @Test fun `all cutting styles retain exact dimensions in both orientations`() {
        for(o in listOf(Orientation.PORTRAIT,Orientation.LANDSCAPE)) for(c in CutStyle.values()) {
            val r=listOf(request("A",32.5,41.7,6),request("B",50.0,75.0,2))
            val result=SmartLayoutEngine.calculate(r,SmartSettings(mode=SmartMode.BEST_FIT,orientation=o,cut=c))
            assertEquals(if(o==Orientation.PORTRAIT) 210.0 else 297.0,result.width,0.0);verify(result,r)
        }
    }
    @Test fun `overflow reports missing counts without returning partial sheet`() {
        val r=listOf(request("A",180.0,180.0,5),request("B",100.0,150.0,8))
        try {SmartLayoutEngine.calculate(r,SmartSettings(mode=SmartMode.BEST_FIT));fail("Overflow required")} catch(e: SmartOverflow) {assertTrue(e.missing.isNotEmpty());assertTrue(e.missing.values.sum()>0)}
    }
    @Test fun `random mixed layouts never overlap escape margins or omit requested counts`() {
        val random=Random(53)
        repeat(35) {
            val r=(0..4).map {request("$it",10.0+random.nextInt(75),10.0+random.nextInt(75),1+random.nextInt(4),random.nextBoolean())}
            try {verify(SmartLayoutEngine.calculate(r,SmartSettings(mode=SmartMode.BEST_FIT)),r)} catch(e: SmartOverflow) {assertTrue(e.missing.isNotEmpty())}
        }
    }
}
