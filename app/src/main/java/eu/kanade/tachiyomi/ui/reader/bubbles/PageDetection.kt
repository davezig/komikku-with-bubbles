package eu.kanade.tachiyomi.ui.reader.bubbles

// KMK -->
/** Object classes the page model can detect. [modelClassId] matches the model's output class ids. */
enum class DetectionKind(val modelClassId: Int) {
    SPEECH_BALLOON(0),
    PANEL(1),
    ;

    companion object {
        fun fromModelClassId(id: Int): DetectionKind? = entries.firstOrNull { it.modelClassId == id }
    }
}

/**
 * One detected object on a page. Coordinates are fractions of the page size, from 0 to 1,
 * so the same detection can be drawn at any display size.
 */
data class PageDetection(
    val kind: DetectionKind,
    val score: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)
// KMK <--
