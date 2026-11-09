package com.xeroxphotopro.core

/** The two-photo option is deliberately fixed: top three rows + bottom three rows. */
object PhotoSlots {
    fun validate(layout: SheetLayout, imageCount: Int) {
        require(imageCount in 1..2) { "Select one or two photos." }
        if (imageCount == 2) require(layout.config.photo == PhotoSize() && layout.width == 210.0 &&
            layout.height == 297.0 && layout.placements.size == 30 && layout.columns == 5 && layout.rows == 6) {
            "Two-photo sheets require 30 photos of 35 × 45 mm on portrait A4."
        }
    }
    fun imageIndex(placementIndex: Int, imageCount: Int): Int = if (imageCount == 2 && placementIndex >= 15) 1 else 0
}
