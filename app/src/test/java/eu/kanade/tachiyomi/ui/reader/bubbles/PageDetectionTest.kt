package eu.kanade.tachiyomi.ui.reader.bubbles

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PageDetectionTest {

    @Test
    fun `window starts with the current page then the neighbours`() {
        assertEquals(listOf(5, 4, 6, 7), analysisWindow(5, 10))
    }

    @Test
    fun `window at the first page has no earlier page`() {
        assertEquals(listOf(0, 1, 2), analysisWindow(0, 10))
    }

    @Test
    fun `window at the last page has no later pages`() {
        assertEquals(listOf(9, 8), analysisWindow(9, 10))
    }

    @Test
    fun `window is empty for an empty comic or invalid index`() {
        assertEquals(emptyList<Int>(), analysisWindow(0, 0))
        assertEquals(emptyList<Int>(), analysisWindow(10, 10))
    }

    @Test
    fun `parse skips slots past the valid count and unknown classes`() {
        val boxes = arrayOf(
            floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f, 0.9f),
            floatArrayOf(0.5f, 0.5f, 0.6f, 0.6f, 0.8f),
            floatArrayOf(0.0f, 0.0f, 1.0f, 1.0f, 0.99f),
        )
        val classes = intArrayOf(0, 7, 1)

        val parsed = parseDetections(boxes, classes, validCount = 2)

        assertEquals(
            listOf(
                PageDetection(DetectionKind.SPEECH_BALLOON, 0.9f, left = 0.2f, top = 0.1f, right = 0.4f, bottom = 0.3f),
            ),
            parsed,
        )
    }

    @Test
    fun `parse maps panel class and keeps coordinates in order`() {
        val boxes = arrayOf(floatArrayOf(0.1f, 0.2f, 0.7f, 0.8f, 0.6f))

        val parsed = parseDetections(boxes, intArrayOf(1), validCount = 1)

        assertEquals(DetectionKind.PANEL, parsed.single().kind)
        assertEquals(0.2f, parsed.single().left)
        assertEquals(0.1f, parsed.single().top)
        assertEquals(0.8f, parsed.single().right)
        assertEquals(0.7f, parsed.single().bottom)
    }
}
