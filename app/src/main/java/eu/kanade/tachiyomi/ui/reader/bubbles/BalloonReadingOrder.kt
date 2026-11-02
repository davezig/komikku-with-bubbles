package eu.kanade.tachiyomi.ui.reader.bubbles

// KMK -->
/** Two boxes count as one row when they share at least this much of the shorter one's height. */
private const val ROW_OVERLAP = 0.5f

/**
 * Speech balloons in the order they are read.
 *
 * Panels are read row by row, top to bottom, and within a row left to right (or right to left when
 * [rightToLeft]). Each balloon belongs to the smallest panel that contains its centre, and the balloons of a
 * panel are read the same way. A balloon outside every detected panel is treated as its own panel, so missed
 * panels still give a sensible order.
 */
internal fun balloonReadingOrder(detections: List<PageDetection>, rightToLeft: Boolean): List<PageDetection> {
    val panels = detections.filter { it.kind == DetectionKind.PANEL }
    val balloons = detections.filter { it.kind == DetectionKind.SPEECH_BALLOON }

    val byPanel = LinkedHashMap<PageDetection, MutableList<PageDetection>>()
    for (balloon in balloons) {
        val centerX = (balloon.left + balloon.right) / 2
        val centerY = (balloon.top + balloon.bottom) / 2
        val owner = panels
            .filter { centerX in it.left..it.right && centerY in it.top..it.bottom }
            .minByOrNull { area(it) }
        // A balloon without a panel stands in for its own panel.
        byPanel.getOrPut(owner ?: balloon) { mutableListOf() } += balloon
    }

    return orderByRows(byPanel.keys.toList(), rightToLeft)
        .flatMap { unit -> orderByRows(byPanel.getValue(unit), rightToLeft) }
}

/** Orders boxes row by row from the top, and within a row by horizontal position in reading direction. */
internal fun orderByRows(boxes: List<PageDetection>, rightToLeft: Boolean): List<PageDetection> {
    val rows = mutableListOf<Row>()
    for (box in boxes.sortedBy { it.top }) {
        val row = rows.firstOrNull { it.overlapRatio(box) >= ROW_OVERLAP }
        if (row != null) {
            row.add(box)
        } else {
            rows += Row(box)
        }
    }
    return rows.flatMap { row ->
        if (rightToLeft) {
            row.boxes.sortedByDescending { it.left + it.right }
        } else {
            row.boxes.sortedBy { it.left + it.right }
        }
    }
}

private fun area(box: PageDetection) = (box.right - box.left) * (box.bottom - box.top)

private class Row(first: PageDetection) {
    val boxes = mutableListOf(first)
    private var top = first.top
    private var bottom = first.bottom

    fun add(box: PageDetection) {
        boxes += box
        top = minOf(top, box.top)
        bottom = maxOf(bottom, box.bottom)
    }

    /** Shared height of this row and [box], as a fraction of the shorter of the two. */
    fun overlapRatio(box: PageDetection): Float {
        val shared = minOf(bottom, box.bottom) - maxOf(top, box.top)
        val shorter = minOf(bottom - top, box.bottom - box.top)
        if (shared <= 0f || shorter <= 0f) return 0f
        return shared / shorter
    }
}
// KMK <--
