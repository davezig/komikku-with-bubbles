package eu.kanade.tachiyomi.ui.reader.bubbles

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PageDetectionTest {

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
