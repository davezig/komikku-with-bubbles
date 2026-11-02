package eu.kanade.tachiyomi.ui.reader.bubbles

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BalloonReadingOrderTest {

    private fun panel(left: Float, top: Float, right: Float, bottom: Float) =
        PageDetection(DetectionKind.PANEL, 0.9f, left, top, right, bottom)

    private fun balloon(left: Float, top: Float, right: Float, bottom: Float) =
        PageDetection(DetectionKind.SPEECH_BALLOON, 0.9f, left, top, right, bottom)

    // Two panels on the top row, one wide panel below.
    private val topLeft = panel(0.0f, 0.0f, 0.5f, 0.4f)
    private val topRight = panel(0.5f, 0.0f, 1.0f, 0.4f)
    private val bottom = panel(0.0f, 0.5f, 1.0f, 1.0f)

    private val inTopLeft = balloon(0.1f, 0.1f, 0.3f, 0.2f)
    private val inTopRight = balloon(0.6f, 0.1f, 0.8f, 0.2f)
    private val inBottomFirst = balloon(0.1f, 0.6f, 0.3f, 0.7f)
    private val inBottomSecond = balloon(0.6f, 0.6f, 0.8f, 0.7f)

    private val page = listOf(bottom, inBottomSecond, topRight, inTopRight, inBottomFirst, topLeft, inTopLeft)

    @Test
    fun `left to right reads the top row left to right then the next row`() {
        assertEquals(
            listOf(inTopLeft, inTopRight, inBottomFirst, inBottomSecond),
            balloonReadingOrder(page, rightToLeft = false),
        )
    }

    @Test
    fun `right to left reads each row from the right`() {
        assertEquals(
            listOf(inTopRight, inTopLeft, inBottomSecond, inBottomFirst),
            balloonReadingOrder(page, rightToLeft = true),
        )
    }

    @Test
    fun `balloons in one panel read top to bottom before the next panel`() {
        val upper = balloon(0.3f, 0.05f, 0.45f, 0.15f)
        val lower = balloon(0.05f, 0.25f, 0.2f, 0.35f)

        assertEquals(
            listOf(upper, lower, inTopRight),
            balloonReadingOrder(listOf(topLeft, topRight, lower, inTopRight, upper), rightToLeft = false),
        )
    }

    @Test
    fun `balloons without panels are still read by rows`() {
        val first = balloon(0.1f, 0.1f, 0.3f, 0.2f)
        val second = balloon(0.6f, 0.12f, 0.8f, 0.22f)
        val third = balloon(0.2f, 0.6f, 0.4f, 0.7f)

        assertEquals(
            listOf(first, second, third),
            balloonReadingOrder(listOf(third, second, first), rightToLeft = false),
        )
    }

    @Test
    fun `balloon goes to the smallest panel containing its centre`() {
        // Two overlapping panels side by side; the right one is smaller.
        val left = panel(0.0f, 0.0f, 0.6f, 0.4f)
        val right = panel(0.4f, 0.0f, 0.9f, 0.4f)
        val onlyLeft = balloon(0.05f, 0.1f, 0.2f, 0.2f)
        val onlyRight = balloon(0.8f, 0.1f, 0.88f, 0.2f)
        // Centre is inside both panels, so it belongs to the smaller right panel and is read after onlyRight.
        val inOverlap = balloon(0.45f, 0.25f, 0.55f, 0.35f)

        assertEquals(
            listOf(onlyLeft, onlyRight, inOverlap),
            balloonReadingOrder(listOf(left, right, inOverlap, onlyRight, onlyLeft), rightToLeft = false),
        )
    }
}
