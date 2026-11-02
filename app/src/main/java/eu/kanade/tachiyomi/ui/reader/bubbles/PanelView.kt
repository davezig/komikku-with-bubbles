package eu.kanade.tachiyomi.ui.reader.bubbles

import kotlin.math.abs

// KMK -->
/** A part of the page to show, as fractions of the page size from 0 to 1. */
data class PageRegion(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val isFullPage: Boolean
        get() = this == FULL_PAGE

    companion object {
        val FULL_PAGE = PageRegion(0f, 0f, 1f, 1f)
    }
}

/**
 * The steps panel view shows for one page.
 * [usedFullPage] is true when the panels looked unreliable and the whole page is shown instead.
 */
data class PanelPlan(
    val steps: List<PageRegion>,
    val usedFullPage: Boolean,
)

/** A panel is "sure" when its score is at least this, or at least the user's threshold plus [UNSURE_MARGIN]. */
internal const val SURE_PANEL_SCORE = 0.7f
internal const val UNSURE_MARGIN = 0.1f

/** Two panels overlapping by more than this share of the smaller one suggest the layout was misread. */
internal const val MAX_PANEL_OVERLAP = 0.3f

/** More than this share of the page covered by artwork outside every panel suggests a missed panel. */
internal const val MAX_INK_OUTSIDE_PANELS = 0.025f

/** Extra space kept around each panel, as a share of the page, so panel borders are not cut off. */
internal const val PANEL_PADDING = 0.01f

/**
 * Plans panel view for a page. Panels are shown one at a time in reading order. The whole page is shown
 * instead when any check suggests a panel was missed: no panels, a balloon outside every panel, a panel the
 * model was unsure of, heavily overlapping panels, or artwork outside every panel ([inkOutsidePanels]).
 * When [fullPageAfterLast] is set, the whole page is added after the last panel.
 */
internal fun planPanelView(
    detections: List<PageDetection>,
    inkOutsidePanels: Float,
    threshold: Float,
    rightToLeft: Boolean,
    fullPageAfterLast: Boolean,
): PanelPlan {
    val panels = detections.filter { it.kind == DetectionKind.PANEL }
    val balloons = detections.filter { it.kind == DetectionKind.SPEECH_BALLOON }

    val reliable = panels.isNotEmpty() &&
        balloons.all { balloon -> panels.any { it.containsCenterOf(balloon) } } &&
        panels.all { it.score >= maxOf(SURE_PANEL_SCORE, threshold + UNSURE_MARGIN) } &&
        maxPanelOverlap(panels) <= MAX_PANEL_OVERLAP &&
        inkOutsidePanels <= MAX_INK_OUTSIDE_PANELS
    if (!reliable) return PanelPlan(listOf(PageRegion.FULL_PAGE), usedFullPage = true)

    val steps = orderByRows(panels, rightToLeft).map { panel ->
        PageRegion(
            left = (panel.left - PANEL_PADDING).coerceAtLeast(0f),
            top = (panel.top - PANEL_PADDING).coerceAtLeast(0f),
            right = (panel.right + PANEL_PADDING).coerceAtMost(1f),
            bottom = (panel.bottom + PANEL_PADDING).coerceAtMost(1f),
        )
    }
    return PanelPlan(if (fullPageAfterLast) steps + PageRegion.FULL_PAGE else steps, usedFullPage = false)
}

private fun PageDetection.containsCenterOf(other: PageDetection): Boolean {
    val centerX = (other.left + other.right) / 2
    val centerY = (other.top + other.bottom) / 2
    return centerX in left..right && centerY in top..bottom
}

/** Largest overlap between two panels, as a share of the smaller panel's area. */
internal fun maxPanelOverlap(panels: List<PageDetection>): Float {
    var worst = 0f
    for (i in panels.indices) {
        for (j in i + 1 until panels.size) {
            val a = panels[i]
            val b = panels[j]
            val width = minOf(a.right, b.right) - maxOf(a.left, b.left)
            val height = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
            if (width <= 0f || height <= 0f) continue
            val smaller = minOf((a.right - a.left) * (a.bottom - a.top), (b.right - b.left) * (b.bottom - b.top))
            if (smaller > 0f) worst = maxOf(worst, width * height / smaller)
        }
    }
    return worst
}

/** How different from the background a pixel must be, in any colour channel, to count as artwork. */
private const val INK_DIFFERENCE = 48

/** Width of the page edge, as a share of the page, used to find the background colour. */
private const val BORDER_SHARE = 0.02f

/** Extra space around each panel, as a share of the page, so a panel's own border lines are not counted. */
private const val INK_PANEL_PADDING = 0.015f

/**
 * Share of the page covered by artwork that lies outside every region in [panels].
 * [pixels] are ARGB values of a small copy of the page, row by row, [width] by [height].
 * The background colour is the most common colour along the page edge.
 */
internal fun inkOutsidePanels(pixels: IntArray, width: Int, height: Int, panels: List<PageDetection>): Float {
    if (width <= 0 || height <= 0 || pixels.size < width * height) return 0f

    val covered = BooleanArray(width * height)
    for (panel in panels) {
        val left = ((panel.left - INK_PANEL_PADDING) * width).toInt().coerceIn(0, width)
        val right = ((panel.right + INK_PANEL_PADDING) * width).toInt().coerceIn(0, width)
        val top = ((panel.top - INK_PANEL_PADDING) * height).toInt().coerceIn(0, height)
        val bottom = ((panel.bottom + INK_PANEL_PADDING) * height).toInt().coerceIn(0, height)
        for (y in top until bottom) {
            covered.fill(true, y * width + left, y * width + right)
        }
    }

    val background = borderColor(pixels, width, height)
    var ink = 0
    for (index in 0 until width * height) {
        if (!covered[index] && isInk(pixels[index], background)) ink++
    }
    return ink.toFloat() / (width * height)
}

/** Most common colour along the page edge, with each channel rounded to 16 levels. */
private fun borderColor(pixels: IntArray, width: Int, height: Int): Int {
    val border = maxOf(1, (minOf(width, height) * BORDER_SHARE).toInt())
    val counts = HashMap<Int, Int>()
    for (y in 0 until height) {
        for (x in 0 until width) {
            if (x >= border && x < width - border && y >= border && y < height - border) continue
            val key = pixels[y * width + x] and 0xF0F0F0
            counts[key] = (counts[key] ?: 0) + 1
        }
    }
    // Use the middle of each rounded channel.
    return (counts.maxByOrNull { it.value }?.key ?: 0xF0F0F0) or 0x080808
}

private fun isInk(pixel: Int, background: Int): Boolean {
    return abs(((pixel shr 16) and 0xFF) - ((background shr 16) and 0xFF)) > INK_DIFFERENCE ||
        abs(((pixel shr 8) and 0xFF) - ((background shr 8) and 0xFF)) > INK_DIFFERENCE ||
        abs((pixel and 0xFF) - (background and 0xFF)) > INK_DIFFERENCE
}
// KMK <--
