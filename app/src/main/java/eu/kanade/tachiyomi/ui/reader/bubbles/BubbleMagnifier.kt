package eu.kanade.tachiyomi.ui.reader.bubbles

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.widget.FrameLayout
import android.widget.ImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// KMK -->
/** Where the magnified balloon is drawn, in the host view's coordinates. */
data class MagnifierPlacement(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

/**
 * Scales a balloon's on-screen rectangle by [factor], shrinks it if needed so it fits inside the host with
 * [margin] on every side, centres it on the balloon, then moves it back inside the host.
 */
internal fun placeMagnifier(
    balloonLeft: Float,
    balloonTop: Float,
    balloonRight: Float,
    balloonBottom: Float,
    factor: Float,
    hostWidth: Float,
    hostHeight: Float,
    margin: Float,
): MagnifierPlacement {
    val maxWidth = (hostWidth - 2 * margin).coerceAtLeast(1f)
    val maxHeight = (hostHeight - 2 * margin).coerceAtLeast(1f)
    var width = (balloonRight - balloonLeft).coerceAtLeast(1f) * factor
    var height = (balloonBottom - balloonTop).coerceAtLeast(1f) * factor
    val fit = minOf(1f, maxWidth / width, maxHeight / height)
    width *= fit
    height *= fit

    val centerX = (balloonLeft + balloonRight) / 2
    val centerY = (balloonTop + balloonBottom) / 2
    val left = (centerX - width / 2).coerceIn(margin, maxOf(margin, hostWidth - margin - width))
    val top = (centerY - height / 2).coerceIn(margin, maxOf(margin, hostHeight - margin - height))
    return MagnifierPlacement(left, top, width, height)
}

/** The balloon's page fractions grown by [padding] of its size on each side, kept inside the page. */
internal fun paddedRegion(detection: PageDetection, padding: Float): PageDetection {
    val padX = (detection.right - detection.left) * padding
    val padY = (detection.bottom - detection.top) * padding
    return detection.copy(
        left = (detection.left - padX).coerceAtLeast(0f),
        top = (detection.top - padY).coerceAtLeast(0f),
        right = (detection.right + padX).coerceAtMost(1f),
        bottom = (detection.bottom + padY).coerceAtMost(1f),
    )
}

/** Largest power-of-two sample size that keeps a region of [regionWidth] pixels at least [targetWidth] wide. */
internal fun regionSampleSize(regionWidth: Int, targetWidth: Int): Int {
    var sample = 1
    while (targetWidth > 0 && regionWidth / (sample * 2) >= targetWidth) {
        sample *= 2
    }
    return sample
}

/**
 * Shows a magnified copy of one balloon on top of [host], like a magnifying glass held over the page.
 * The crop is decoded from the page file at full resolution, so the text stays sharp.
 */
class BubbleMagnifier(private val host: FrameLayout) {

    private val scope = MainScope()
    private var job: Job? = null
    private var view: ImageView? = null

    /** True while a magnified balloon is shown or being prepared. */
    val isShowing: Boolean
        get() = view != null || job?.isActive == true

    /** Whether ([x], [y]) in host coordinates falls on the magnified balloon. */
    fun contains(x: Float, y: Float): Boolean {
        val shown = view ?: return false
        val params = shown.layoutParams
        return x >= shown.translationX &&
            x <= shown.translationX + params.width &&
            y >= shown.translationY &&
            y <= shown.translationY + params.height
    }

    /** Decodes [region] (page fractions) from [imageBytes] and shows it at [placement]. */
    fun show(imageBytes: ByteArray, region: PageDetection, placement: MagnifierPlacement) {
        dismiss()
        job = scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                decodeRegion(imageBytes, region, placement.width.toInt())
            } ?: return@launch

            val density = host.resources.displayMetrics.density
            val frame = (2 * density).toInt()
            val magnified = ImageView(host.context).apply {
                scaleType = ImageView.ScaleType.FIT_XY
                setImageBitmap(bitmap)
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    setStroke(frame, Color.argb(160, 0, 0, 0))
                    cornerRadius = 6 * density
                }
                setPadding(frame, frame, frame, frame)
                elevation = 8 * density
                translationX = placement.left
                translationY = placement.top
            }
            host.addView(
                magnified,
                FrameLayout.LayoutParams(placement.width.toInt(), placement.height.toInt()),
            )
            view = magnified
        }
    }

    /** Removes the magnified balloon, if any, and frees its bitmap. */
    fun dismiss() {
        job?.cancel()
        job = null
        val shown = view ?: return
        view = null
        host.removeView(shown)
        val bitmap = (shown.drawable as? BitmapDrawable)?.bitmap
        shown.setImageDrawable(null)
        bitmap?.recycle()
    }

    private fun decodeRegion(bytes: ByteArray, region: PageDetection, targetWidth: Int): Bitmap? {
        val decoder = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                BitmapRegionDecoder.newInstance(bytes, 0, bytes.size)
            } else {
                @Suppress("DEPRECATION")
                BitmapRegionDecoder.newInstance(bytes, 0, bytes.size, false)
            }
        } catch (e: Exception) {
            null
        } ?: return null

        return try {
            val width = decoder.width
            val height = decoder.height
            val rect = Rect(
                (region.left * width).toInt(),
                (region.top * height).toInt(),
                (region.right * width).toInt().coerceAtMost(width),
                (region.bottom * height).toInt().coerceAtMost(height),
            )
            if (rect.width() <= 0 || rect.height() <= 0) {
                null
            } else {
                val options = BitmapFactory.Options().apply {
                    inSampleSize = regionSampleSize(rect.width(), targetWidth)
                }
                decoder.decodeRegion(rect, options)
            }
        } catch (e: Exception) {
            null
        } finally {
            decoder.recycle()
        }
    }
}
// KMK <--
