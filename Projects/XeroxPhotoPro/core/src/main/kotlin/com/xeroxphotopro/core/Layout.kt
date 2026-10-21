package com.xeroxphotopro.core

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

const val POINTS_PER_MM = 72.0 / 25.4
fun mmToPoints(mm: Double) = mm * POINTS_PER_MM
fun pixelsFor(mm: Double, dpi: Int = 300) = ceil(mm / 25.4 * dpi).toInt()

enum class Orientation { AUTO, PORTRAIT, LANDSCAPE }
data class PhotoSize(val width: Double = 35.0, val height: Double = 45.0) {
    init { require(width.isFinite() && height.isFinite() && width in 10.0..287.0 && height in 10.0..287.0) { "Enter photo dimensions between 10 and 287 mm." } }
}
data class SheetConfig(
    val photo: PhotoSize = PhotoSize(),
    /** Null means use the current maximum, not a fixed remembered quantity. */
    val copies: Int? = null,
    val orientation: Orientation = Orientation.AUTO,
    val margin: Double = 5.0, val gap: Double = 3.0,
    val guides: Boolean = true, val border: Boolean = false
) {
    init {
        require(copies == null || copies in 1..10000) { "Choose at least one copy, or use Maximum." }
        require(margin.isFinite() && margin in 2.0..30.0) { "Use 2–30 mm margins; check your printer's minimum." }
        require(gap.isFinite() && gap in 0.0..20.0) { "Use 0–20 mm spacing." }
        require(!guides || gap >= 2.0) { "External cutting marks require at least 2 mm spacing." }
    }
}
data class Placement(val x: Double, val y: Double, val width: Double, val height: Double)
data class Segment(val x1: Double, val y1: Double, val x2: Double, val y2: Double)
data class Grid(val orientation: Orientation, val width: Double, val height: Double,
    val columns: Int, val rows: Int, val inset: Double, val occupiedWidth: Double, val occupiedHeight: Double) {
    val capacity get() = columns * rows
    val remainingWidth get() = width - 2 * inset - occupiedWidth
    val remainingHeight get() = height - 2 * inset - occupiedHeight
}
class CapacityExceeded(val requested: Int, val maximum: Int) : IllegalArgumentException(
    "Only $maximum photographs can fit on one A4 sheet with the current settings. Please select $maximum or fewer copies (requested $requested)."
)
/** Constructed exclusively by the layout engine: one grid, one page, no overflow. */
class SheetLayout internal constructor(val config: SheetConfig, val grid: Grid, val placements: List<Placement>) {
    val capacity get() = grid.capacity
    val columns get() = grid.columns
    val rows get() = grid.rows
    val width get() = grid.width
    val height get() = grid.height
}
object LayoutEngine {
    fun grid(c: SheetConfig, orientation: Orientation): Grid {
        require(orientation != Orientation.AUTO)
        val width = if (orientation == Orientation.PORTRAIT) 210.0 else 297.0
        val height = if (orientation == Orientation.PORTRAIT) 297.0 else 210.0
        // The entire 0.2 pt external mark stroke stays within the printer-safe margin.
        val inset = c.margin + if (c.guides) 1.0 else 0.0
        val cols = floor((width - 2 * inset + c.gap + 1e-9) / (c.photo.width + c.gap)).toInt().coerceAtLeast(0)
        val rows = floor((height - 2 * inset + c.gap + 1e-9) / (c.photo.height + c.gap)).toInt().coerceAtLeast(0)
        return Grid(orientation, width, height, cols, rows, inset,
            if (cols == 0) 0.0 else cols * c.photo.width + (cols - 1) * c.gap,
            if (rows == 0) 0.0 else rows * c.photo.height + (rows - 1) * c.gap)
    }
    fun recommended(c: SheetConfig): Grid {
        val portrait = grid(c, Orientation.PORTRAIT)
        val landscape = grid(c, Orientation.LANDSCAPE)
        // Same margins/gaps in either direction. On ties prefer portrait for predictable
        // paper feeding and straight, consistent cuts; never rotate individual photos.
        return if (landscape.capacity > portrait.capacity) landscape else portrait
    }
    fun selectedGrid(c: SheetConfig) = if (c.orientation == Orientation.AUTO) recommended(c) else grid(c, c.orientation)
    fun calculate(c: SheetConfig): SheetLayout {
        val grid = selectedGrid(c)
        require(grid.capacity > 0) { "This photo does not fit one A4 sheet. Reduce size, margins or spacing, or choose Auto orientation." }
        val copies = c.copies ?: grid.capacity
        if (copies > grid.capacity) throw CapacityExceeded(copies, grid.capacity)
        // Center the selected grid footprint, without spreading gaps or changing photo size.
        val usedColumns = min(copies, grid.columns)
        val usedRows = (copies + grid.columns - 1) / grid.columns
        val usedWidth = usedColumns * c.photo.width + (usedColumns - 1) * c.gap
        val usedHeight = usedRows * c.photo.height + (usedRows - 1) * c.gap
        val startX = (grid.width - usedWidth) / 2
        val startY = (grid.height - usedHeight) / 2
        val photos = (0 until copies).map { index ->
            Placement(startX + (index % grid.columns) * (c.photo.width + c.gap),
                startY + (index / grid.columns) * (c.photo.height + c.gap), c.photo.width, c.photo.height)
        }
        return SheetLayout(c, grid, photos)
    }
    fun marks(p: Placement): List<Segment> = buildList {
        for (x in listOf(p.x, p.x + p.width)) {
            add(Segment(x, p.y - .9, x, p.y - .25))
            add(Segment(x, p.y + p.height + .25, x, p.y + p.height + .9))
        }
        for (y in listOf(p.y, p.y + p.height)) {
            add(Segment(p.x - .9, y, p.x - .25, y))
            add(Segment(p.x + p.width + .25, y, p.x + p.width + .9, y))
        }
    }
}
