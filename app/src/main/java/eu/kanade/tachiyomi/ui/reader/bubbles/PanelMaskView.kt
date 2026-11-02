package eu.kanade.tachiyomi.ui.reader.bubbles

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView

// KMK -->
/**
 * Panel view overlay. Covers everything outside the current panel so only the panel is visible, and can show a
 * short note when panel view fell back to the whole page.
 */
class PanelMaskView(context: Context) : View(context) {

    /** The page image the panel belongs to. */
    var source: SubsamplingScaleImageView? = null

    /** The panel to keep visible, or null to cover nothing. */
    var region: PageRegion? = null
        set(value) {
            field = value
            invalidate()
        }

    /** A short note drawn at the top, or null for none. */
    var note: String? = null
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density

    private val maskPaint = Paint().apply { color = Color.BLACK }

    private val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 13 * density
    }

    private val noteBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(170, 0, 0, 0) }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawMask(canvas)
        drawNote(canvas)
    }

    private fun drawMask(canvas: Canvas) {
        val image = source ?: return
        val panel = region ?: return
        if (panel.isFullPage || !image.isReady) return
        val width = image.sWidth.toFloat()
        val height = image.sHeight.toFloat()
        val topLeft = image.sourceToViewCoord(panel.left * width, panel.top * height) ?: return
        val bottomRight = image.sourceToViewCoord(panel.right * width, panel.bottom * height) ?: return
        val viewWidth = this.width.toFloat()
        val viewHeight = this.height.toFloat()
        canvas.drawRect(0f, 0f, viewWidth, topLeft.y, maskPaint)
        canvas.drawRect(0f, bottomRight.y, viewWidth, viewHeight, maskPaint)
        canvas.drawRect(0f, topLeft.y, topLeft.x, bottomRight.y, maskPaint)
        canvas.drawRect(bottomRight.x, topLeft.y, viewWidth, bottomRight.y, maskPaint)
    }

    private fun drawNote(canvas: Canvas) {
        val text = note ?: return
        val padding = 8 * density
        val textWidth = notePaint.measureText(text)
        val metrics = notePaint.fontMetrics
        val boxHeight = metrics.descent - metrics.ascent + 2 * padding
        val left = (width - textWidth) / 2 - padding
        val top = 16 * density
        canvas.drawRoundRect(
            RectF(left, top, left + textWidth + 2 * padding, top + boxHeight),
            boxHeight / 2,
            boxHeight / 2,
            noteBackground,
        )
        canvas.drawText(text, left + padding, top + padding - metrics.ascent, notePaint)
    }
}
// KMK <--
