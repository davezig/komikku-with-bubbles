package eu.kanade.tachiyomi.ui.reader.bubbles

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// KMK -->
/**
 * Pages to analyze around [currentIndex]: the current page first, then one page before and two after.
 * Only indices inside 0 until [pageCount] are returned.
 */
internal fun analysisWindow(currentIndex: Int, pageCount: Int): List<Int> {
    if (pageCount <= 0 || currentIndex !in 0 until pageCount) return emptyList()
    val from = (currentIndex - 1).coerceAtLeast(0)
    val to = (currentIndex + 2).coerceAtMost(pageCount - 1)
    return listOf(currentIndex) + (from..to).filter { it != currentIndex }
}

/**
 * In-memory cache of page detections for the open comic. Keeps only the current page's window and
 * drops everything else. Nothing is written to disk.
 *
 * Results are stored unfiltered, so the caller can change the confidence threshold without re-running
 * the model.
 */
class PageDetectionCache(
    private val detector: PageDetector,
) {
    private val mutex = Mutex()
    private val results = HashMap<Int, List<PageDetection>>()

    /**
     * Returns detections for [pageIndex] with score at least [threshold], analyzing any missing pages
     * in its window first. [loadPage] must return a bitmap for an index; those bitmaps are recycled
     * after analysis, so return a copy rather than a bitmap the reader still shows.
     */
    suspend fun detectionsFor(
        pageIndex: Int,
        pageCount: Int,
        threshold: Float,
        loadPage: suspend (Int) -> Bitmap,
    ): List<PageDetection> = mutex.withLock {
        val window = analysisWindow(pageIndex, pageCount)
        results.keys.retainAll(window.toSet())

        val missing = window.filterNot(results::containsKey)
        for (chunk in missing.chunked(detector.batchSize)) {
            val bitmaps = chunk.map { loadPage(it) }
            val detections = withContext(Dispatchers.Default) { detector.detect(bitmaps) }
            chunk.zip(detections).forEach { (index, found) -> results[index] = found }
            bitmaps.forEach { it.recycle() }
        }

        results[pageIndex].orEmpty().filter { it.score >= threshold }
    }

    /** Drops every cached result, for example when the comic is closed. */
    suspend fun clear() = mutex.withLock { results.clear() }
}
// KMK <--
