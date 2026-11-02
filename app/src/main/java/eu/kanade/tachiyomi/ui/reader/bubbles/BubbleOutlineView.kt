package eu.kanade.tachiyomi.ui.reader.bubbles

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView

// KMK -->
/** Draws detected balloon and panel outlines over a zoomable page, following its zoom and pan. */
class BubbleOutlineView(context: Context) : View(context) {

    /** The page image the outlines belong to. Nothing is drawn until it is ready. */
    var source: SubsamplingScaleImageView? = null

    var detections: List<PageDetection> = emptyList()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val image = source ?: return
        if (!image.isReady) return
        val width = image.sWidth.toFloat()
        val height = image.sHeight.toFloat()
        for (detection in detections) {
            val topLeft = image.sourceToViewCoord(detection.left * width, detection.top * height) ?: continue
            val bottomRight = image.sourceToViewCoord(detection.right * width, detection.bottom * height) ?: continue
            paint.color = when (detection.kind) {
                DetectionKind.SPEECH_BALLOON -> Color.argb(220, 255, 64, 64)
                DetectionKind.PANEL -> Color.argb(200, 64, 160, 255)
            }
            canvas.drawRect(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y, paint)
        }
    }
}
// KMK <--
