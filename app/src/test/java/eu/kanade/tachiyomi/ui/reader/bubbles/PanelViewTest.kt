package eu.kanade.tachiyomi.ui.reader.bubbles

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PanelViewTest {

    private fun panel(left: Float, top: Float, right: Float, bottom: Float, score: Float = 0.95f) =
        PageDetection(DetectionKind.PANEL, score, left, top, right, bottom)

    private fun balloon(left: Float, top: Float, right: Float, bottom: Float) =
        PageDetection(DetectionKind.SPEECH_BALLOON, 0.95f, left, top, right, bottom)

    private val left = panel(0.05f, 0.05f, 0.45f, 0.45f)
    private val right = panel(0.55f, 0.05f, 0.95f, 0.45f)
    private val bottom = panel(0.05f, 0.55f, 0.95f, 0.95f)
    private val page = listOf(bottom, right, left, balloon(0.1f, 0.1f, 0.2f, 0.2f))

    private fun plan(
        detections: List<PageDetection> = page,
        ink: Float = 0f,
        rightToLeft: Boolean = false,
        fullPageAfterLast: Boolean = false,
    ) = planPanelView(detections, ink, threshold = 0.5f, rightToLeft, fullPageAfterLast)

    @Test
    fun `clear panels are shown one at a time in reading order with padding`() {
        val result = plan()

        assertFalse(result.usedFullPage)
        assertEquals(
            listOf(
                PageRegion(0.04f, 0.04f, 0.46f, 0.46f),
                PageRegion(0.54f, 0.04f, 0.96f, 0.46f),
                PageRegion(0.04f, 0.54f, 0.96f, 0.96f),
            ).map { it.rounded() },
            result.steps.map { it.rounded() },
        )
    }

    @Test
    fun `right to left starts with the right panel`() {
        assertEquals(0.54f, plan(rightToLeft = true).steps.first().left, 1e-6f)
    }

    @Test
    fun `full page can be added after the last panel`() {
        val result = plan(fullPageAfterLast = true)

        assertEquals(4, result.steps.size)
        assertTrue(result.steps.last().isFullPage)
    }

    @Test
    fun `no panels shows the whole page`() {
        assertTrue(plan(detections = listOf(balloon(0.1f, 0.1f, 0.2f, 0.2f))).usedFullPage)
    }

    @Test
    fun `a balloon outside every panel shows the whole page`() {
        assertTrue(plan(detections = page + balloon(0.46f, 0.47f, 0.54f, 0.53f)).usedFullPage)
    }

    @Test
    fun `an unsure panel shows the whole page`() {
        assertTrue(plan(detections = page + panel(0.0f, 0.97f, 0.2f, 1.0f, score = 0.55f)).usedFullPage)
    }

    @Test
    fun `heavily overlapping panels show the whole page`() {
        assertTrue(plan(detections = page + panel(0.1f, 0.1f, 0.4f, 0.4f)).usedFullPage)
    }

    @Test
    fun `artwork outside the panels shows the whole page`() {
        assertTrue(plan(ink = 0.05f).usedFullPage)
        assertFalse(plan(ink = 0.01f).usedFullPage)
    }

    @Test
    fun `ink outside panels counts only artwork not covered by a panel`() {
        val width = 100
        val height = 100
        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF000000.toInt()
        val pixels = IntArray(width * height) { white }
        // A 20 x 20 black square inside the panel and a 10 x 10 black square outside it.
        fun fill(from: Int, to: Int) {
            for (y in from until to) {
                for (x in from until to) {
                    pixels[y * width + x] = black
                }
            }
        }
        fill(20, 40)
        fill(70, 80)

        val ink = inkOutsidePanels(pixels, width, height, listOf(panel(0.1f, 0.1f, 0.5f, 0.5f)))

        assertEquals(0.01f, ink, 1e-6f)
    }

    @Test
    fun `overlap is measured against the smaller panel`() {
        val big = panel(0f, 0f, 1f, 1f)
        val small = panel(0.5f, 0.5f, 0.7f, 0.7f)

        assertEquals(1f, maxPanelOverlap(listOf(big, small)), 1e-6f)
        assertEquals(0f, maxPanelOverlap(listOf(left, right)), 1e-6f)
    }

    private fun PageRegion.rounded() = PageRegion(left.r(), top.r(), right.r(), bottom.r())

    private fun Float.r() = Math.round(this * 1000) / 1000f
}
