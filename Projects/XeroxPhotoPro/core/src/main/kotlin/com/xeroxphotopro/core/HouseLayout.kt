package com.xeroxphotopro.core

/** Fixed portrait A4 photographic frames. Three true 6x4-inch frames cannot fit vertically. */
object HouseLayout {
    const val MARGIN=5.0
    const val GAP=3.0
    fun calculate(copies: Int,cut: CutStyle=CutStyle.BLACK): SmartLayout {
        require(copies in 1..3) { "Choose 1, 2 or 3 copies." }
        require(cut==CutStyle.BLACK || cut==CutStyle.GRAY)
        val height=if(copies<3) 101.6 else (297.0-2*MARGIN-(copies-1)*GAP)/copies
        val width=height*1.5
        val x=(210.0-width)/2
        val top=(297.0-copies*height-(copies-1)*GAP)/2
        return SmartLayout(210.0,297.0,List(copies) { i->
            SmartPlacement("house-photo",Placement(x,top+i*(height+GAP),width,height),false)
        },SmartSettings(mode=SmartMode.EXACT,orientation=Orientation.PORTRAIT,margin=MARGIN,gapX=GAP,gapY=GAP,cut=cut))
    }
}
