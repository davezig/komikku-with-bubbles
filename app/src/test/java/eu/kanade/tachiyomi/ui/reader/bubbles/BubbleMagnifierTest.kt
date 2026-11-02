package eu.kanade.tachiyomi.ui.reader.bubbles

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BubbleMagnifierTest {

    @Test
    fun `magnifier doubles the balloon and stays centred on it`() {
        val placement = placeMagnifier(
            balloonLeft = 400f,
            balloonTop = 400f,
            balloonRight = 500f,
            balloonBottom = 450f,
            factor = 2f,
            hostWidth = 1000f,
            hostHeight = 1000f,
            margin = 10f,
        )

        assertEquals(MagnifierPlacement(left = 350f, top = 375f, width = 200f, height = 100f), placement)
    }

    @Test
    fun `magnifier near the edge is moved back inside the screen`() {
        val placement = placeMagnifier(
            balloonLeft = 0f,
            balloonTop = 0f,
            balloonRight = 100f,
            balloonBottom = 50f,
            factor = 2f,
            hostWidth = 1000f,
            hostHeight = 1000f,
            margin = 10f,
        )

        assertEquals(10f, placement.left)
        assertEquals(10f, placement.top)
        assertEquals(200f, placement.width)
        assertEquals(100f, placement.height)
    }

    @Test
    fun `magnifier larger than the screen is shrunk to fit and keeps its shape`() {
        val placement = placeMagnifier(
            balloonLeft = 100f,
            balloonTop = 100f,
            balloonRight = 600f,
            balloonBottom = 350f,
            factor = 2f,
            hostWidth = 520f,
            hostHeight = 2000f,
            margin = 10f,
        )

        assertEquals(500f, placement.width)
        assertEquals(250f, placement.height)
        assertEquals(10f, placement.left)
    }

    @Test
    fun `padded region grows on each side but stays inside the page`() {
        val balloon = PageDetection(
            kind = DetectionKind.SPEECH_BALLOON,
            score = 0.9f,
            left = 0f,
            top = 0.5f,
            right = 0.2f,
            bottom = 0.6f,
        )

        val region = paddedRegion(balloon, padding = 0.5f)

        assertEquals(0f, region.left)
        assertEquals(0.45f, region.top, 1e-6f)
        assertEquals(0.3f, region.right, 1e-6f)
        assertEquals(0.65f, region.bottom, 1e-6f)
    }

    @Test
    fun `sample size halves the region while it stays at least the target size`() {
        assertEquals(1, regionSampleSize(regionWidth = 500, targetWidth = 400))
        assertEquals(2, regionSampleSize(regionWidth = 900, targetWidth = 400))
        assertEquals(4, regionSampleSize(regionWidth = 1700, targetWidth = 400))
        assertEquals(1, regionSampleSize(regionWidth = 200, targetWidth = 400))
    }
}
