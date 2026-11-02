package eu.kanade.tachiyomi.ui.reader.viewer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.annotation.AttrRes
import androidx.annotation.CallSuper
import androidx.annotation.StyleRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.os.postDelayed
import androidx.core.view.isVisible
import coil3.BitmapImage
import coil3.asDrawable
import coil3.dispose
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.size.Precision
import coil3.size.ViewSizeResolver
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.EASE_IN_OUT_QUAD
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.EASE_OUT_QUAD
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
import com.github.chrisbanes.photoview.PhotoView
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.coil.cropBorders
import eu.kanade.tachiyomi.data.coil.customDecoder
import eu.kanade.tachiyomi.ui.reader.bubbles.BubbleMagnifier
import eu.kanade.tachiyomi.ui.reader.bubbles.BubbleOutlineView
import eu.kanade.tachiyomi.ui.reader.bubbles.DetectionKind
import eu.kanade.tachiyomi.ui.reader.bubbles.PageDetection
import eu.kanade.tachiyomi.ui.reader.bubbles.PageRegion
import eu.kanade.tachiyomi.ui.reader.bubbles.PanelMaskView
import eu.kanade.tachiyomi.ui.reader.bubbles.balloonReadingOrder
import eu.kanade.tachiyomi.ui.reader.bubbles.paddedRegion
import eu.kanade.tachiyomi.ui.reader.bubbles.placeMagnifier
import eu.kanade.tachiyomi.ui.reader.bubbles.planPanelView
import eu.kanade.tachiyomi.ui.reader.setting.BubblesPanelsPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.LandscapeZoomScaleType
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonSubsamplingImageView
import eu.kanade.tachiyomi.util.system.animatorDurationScale
import eu.kanade.tachiyomi.util.view.isVisibleOnScreen
import okio.BufferedSource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.abs

/**
 * A wrapper view for showing page image.
 *
 * Animated image will be drawn by [PhotoView] while [SubsamplingScaleImageView] will take non-animated image.
 *
 * @param isWebtoon if true, [WebtoonSubsamplingImageView] will be used instead of [SubsamplingScaleImageView]
 * and [AppCompatImageView] will be used instead of [PhotoView]
 */
open class ReaderPageImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttrs: Int = 0,
    @StyleRes defStyleRes: Int = 0,
    private val isWebtoon: Boolean = false,
) : FrameLayout(context, attrs, defStyleAttrs, defStyleRes) {

    private val alwaysDecodeLongStripWithSSIV by lazy {
        Injekt.get<BasePreferences>().alwaysDecodeLongStripWithSSIV().get()
    }

    private var pageView: View? = null

    // KMK -->
    private var outlineView: BubbleOutlineView? = null
    private val bubblesPreferences by lazy { Injekt.get<BubblesPanelsPreferences>() }
    private val bubbleMagnifier by lazy { BubbleMagnifier(this) }
    private var bubbleDetections: List<PageDetection> = emptyList()
    private var bubbleImageBytes: ByteArray? = null
    private var magnifiedBalloon: PageDetection? = null
    private val touchStarts = HashMap<Int, PointF>()
    private var isTwoFingerTap = false
    private var panelMaskView: PanelMaskView? = null
    private var bubbleInkOutsidePanels = 0f
    private var panelSteps: List<PageRegion> = emptyList()
    private var panelIndex = 0
    private var isPanelViewActive = false

    // Panel view was asked for before the page or its detections were ready. True means start at the last panel.
    private var pendingPanelStartAtEnd: Boolean? = null
    // KMK <--

    private var config: Config? = null

    var onImageLoaded: (() -> Unit)? = null
    var onImageLoadError: ((Throwable?) -> Unit)? = null
    var onScaleChanged: ((newScale: Float) -> Unit)? = null
    var onViewClicked: (() -> Unit)? = null

    /**
     * For automatic background. Will be set as background color when [onImageLoaded] is called.
     */
    var pageBackground: Drawable? = null

    @CallSuper
    open fun onImageLoaded() {
        onImageLoaded?.invoke()
        background = pageBackground
        // KMK -->
        outlineView?.invalidate()
        tryStartPendingPanelView()
        // KMK <--
    }

    @CallSuper
    open fun onImageLoadError(error: Throwable?) {
        onImageLoadError?.invoke(error)
    }

    @CallSuper
    open fun onScaleChanged(newScale: Float) {
        onScaleChanged?.invoke(newScale)
    }

    @CallSuper
    open fun onViewClicked() {
        onViewClicked?.invoke()
    }

    open fun onPageSelected(forward: Boolean) {
        // KMK -->
        dismissBubbleZoom()
        if (bubblesPreferences.panelViewEnabled().get()) {
            startPanelView(atEnd = !forward)
        } else {
            stopPanelView()
        }
        // KMK <--
        with(pageView as? SubsamplingScaleImageView) {
            if (this == null) return
            if (isReady) {
                landscapeZoom(forward)
            } else {
                setOnImageEventListener(
                    object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                        override fun onReady() {
                            setupZoom(config)
                            landscapeZoom(forward)
                            this@ReaderPageImageView.onImageLoaded()
                        }

                        override fun onImageLoadError(e: Exception) {
                            onImageLoadError(e)
                        }
                    },
                )
            }
        }
    }

    private fun SubsamplingScaleImageView.landscapeZoom(forward: Boolean) {
        val config = config
        if (config != null &&
            config.landscapeZoom &&
            config.minimumScaleType == SCALE_TYPE_CENTER_INSIDE &&
            sWidth > sHeight &&
            scale == minScale
        ) {
            handler?.postDelayed(500) {
                val point = when (config.zoomStartPosition) {
                    ZoomStartPosition.LEFT -> if (forward) PointF(0F, 0F) else PointF(sWidth.toFloat(), 0F)
                    ZoomStartPosition.RIGHT -> if (forward) PointF(sWidth.toFloat(), 0F) else PointF(0F, 0F)
                    ZoomStartPosition.CENTER -> center
                }

                val targetScale = /* KMK --> */ when (config.landscapeZoomScaleType) {
                    LandscapeZoomScaleType.DOUBLE -> scale * 2
                    // KMK <--
                    else -> height.toFloat() / sHeight.toFloat()
                }
                (animateScaleAndCenter(targetScale, point) ?: return@postDelayed)
                    .withDuration(500)
                    .withEasing(EASE_IN_OUT_QUAD)
                    .withInterruptible(true)
                    .start()
            }
        }
    }

    fun setImage(drawable: Drawable, config: Config) {
        this.config = config
        if (drawable is Animatable) {
            prepareAnimatedImageView()
            setAnimatedImage(drawable, config)
        } else {
            prepareNonAnimatedImageView()
            setNonAnimatedImage(drawable, config)
        }
    }

    fun setImage(source: BufferedSource, isAnimated: Boolean, config: Config) {
        this.config = config
        if (isAnimated) {
            prepareAnimatedImageView()
            setAnimatedImage(source, config)
        } else {
            prepareNonAnimatedImageView()
            setNonAnimatedImage(source, config)
        }
    }

    // KMK -->
    /** Whether balloons on this page are read right to left. */
    var bubbleReadRightToLeft: Boolean = false

    /** Called when a two-finger tap is made while the page's last balloon is magnified. */
    var onBubbleNextPage: (() -> Unit)? = null

    /**
     * Sets the balloons and panels found on this page and the page's image file, used to magnify a tapped
     * balloon. Outlines are drawn only when the outline setting is on.
     */
    fun setBubbleData(detections: List<PageDetection>, imageBytes: ByteArray?, inkOutsidePanels: Float = 0f) {
        dismissBubbleZoom()
        bubbleDetections = detections
        bubbleImageBytes = imageBytes
        bubbleInkOutsidePanels = inkOutsidePanels
        outlineView?.let { overlay ->
            overlay.detections = detections
            overlay.isVisible = detections.isNotEmpty() && bubblesPreferences.showDetectionOutlines().get()
            overlay.invalidate()
        }
        if (imageBytes == null) {
            // The page is being reloaded; its panels are no longer known.
            isPanelViewActive = false
            panelMaskView?.region = null
            panelMaskView?.note = null
        } else {
            tryStartPendingPanelView()
        }
    }

    /** Starts panel view on this page, at the first panel or, when [atEnd], at the last one. */
    fun startPanelView(atEnd: Boolean) {
        pendingPanelStartAtEnd = atEnd
        tryStartPendingPanelView()
    }

    /** Leaves panel view and shows the whole page again. */
    fun stopPanelView() {
        pendingPanelStartAtEnd = null
        if (!isPanelViewActive) return
        isPanelViewActive = false
        panelSteps = emptyList()
        panelMaskView?.region = null
        panelMaskView?.note = null
        (pageView as? SubsamplingScaleImageView)?.let(::showWholePage)
    }

    /** Shows the next panel. Returns false when there is none, so the reader can turn the page. */
    fun nextPanel(): Boolean {
        val image = pageView as? SubsamplingScaleImageView
        if (!isPanelViewActive || image == null || panelIndex >= panelSteps.lastIndex) return false
        panelIndex++
        showPanelStep(image)
        return true
    }

    /** Shows the previous panel. Returns false when there is none, so the reader can turn the page. */
    fun previousPanel(): Boolean {
        val image = pageView as? SubsamplingScaleImageView
        if (!isPanelViewActive || image == null || panelIndex <= 0) return false
        panelIndex--
        showPanelStep(image)
        return true
    }

    private fun tryStartPendingPanelView() {
        val atEnd = pendingPanelStartAtEnd ?: return
        val image = readyBubbleImage() ?: return
        pendingPanelStartAtEnd = null
        val plan = planPanelView(
            detections = bubbleDetections,
            inkOutsidePanels = bubbleInkOutsidePanels,
            threshold = bubblesPreferences.detectionConfidence().get(),
            rightToLeft = bubbleReadRightToLeft,
            fullPageAfterLast = bubblesPreferences.panelViewFullPageAfterLast().get(),
        )
        panelSteps = plan.steps
        panelIndex = if (atEnd) plan.steps.lastIndex else 0
        isPanelViewActive = true
        panelMaskView?.note = if (plan.usedFullPage) context.stringResource(KMR.strings.panel_view_full_page) else null
        showPanelStep(image)
    }

    /** Zooms to the current panel and covers the rest of the page. */
    private fun showPanelStep(image: SubsamplingScaleImageView) {
        dismissBubbleZoom()
        val step = panelSteps.getOrNull(panelIndex) ?: return
        panelMaskView?.region = step
        if (step.isFullPage) {
            showWholePage(image)
            return
        }
        val sourceWidth = image.sWidth.toFloat()
        val sourceHeight = image.sHeight.toFloat()
        val scale = minOf(
            width / ((step.right - step.left) * sourceWidth),
            height / ((step.bottom - step.top) * sourceHeight),
        )
        image.maxScale = maxOf(image.maxScale, scale)
        val center = PointF((step.left + step.right) / 2 * sourceWidth, (step.top + step.bottom) / 2 * sourceHeight)
        image.animateScaleAndCenter(scale, center)
            ?.withDuration(PANEL_ANIMATION_MS)
            ?.withInterruptible(false)
            ?.start()
    }

    private fun showWholePage(image: SubsamplingScaleImageView) {
        if (!image.isReady) return
        val center = PointF(image.sWidth / 2f, image.sHeight / 2f)
        image.animateScaleAndCenter(image.minScale, center)
            ?.withDuration(PANEL_ANIMATION_MS)
            ?.withInterruptible(false)
            ?.start()
    }

    /**
     * Handles a one-finger tap at ([x], [y]) in this view's coordinates. Returns true when the tap was used: it
     * closed the magnified balloon, or it opened one. Returns false when the reader should handle the tap as usual.
     */
    fun handleBubbleTap(x: Float, y: Float): Boolean {
        if (bubbleMagnifier.isShowing) {
            dismissBubbleZoom()
            return true
        }
        val image = readyBubbleImage() ?: return false
        val source = image.viewToSourceCoord(x, y) ?: return false
        val fractionX = source.x / image.sWidth
        val fractionY = source.y / image.sHeight
        val balloon = bubbleDetections
            .filter { it.kind == DetectionKind.SPEECH_BALLOON }
            .filter { fractionX in it.left..it.right && fractionY in it.top..it.bottom }
            .minByOrNull { (it.right - it.left) * (it.bottom - it.top) }
            ?: return false
        return showBubbleZoom(image, balloon)
    }

    /** Closes the magnified balloon, if one is shown. */
    fun dismissBubbleZoom() {
        bubbleMagnifier.dismiss()
        magnifiedBalloon = null
    }

    /** Moves the magnifier to the next balloon in reading order, or to the next page after the last balloon. */
    private fun showNextBubble() {
        val image = readyBubbleImage()
        val current = magnifiedBalloon
        val order = balloonReadingOrder(bubbleDetections, bubbleReadRightToLeft)
        val next = current?.let { order.getOrNull(order.indexOf(it) + 1) }
        if (image != null && next != null && showBubbleZoom(image, next)) return
        dismissBubbleZoom()
        onBubbleNextPage?.invoke()
    }

    private fun readyBubbleImage(): SubsamplingScaleImageView? {
        val image = pageView as? SubsamplingScaleImageView ?: return null
        if (bubbleImageBytes == null || !image.isReady || image.sWidth <= 0 || image.sHeight <= 0) return null
        return image
    }

    private fun showBubbleZoom(image: SubsamplingScaleImageView, balloon: PageDetection): Boolean {
        val bytes = bubbleImageBytes ?: return false
        val region = paddedRegion(balloon, BUBBLE_ZOOM_PADDING)
        val topLeft = image.sourceToViewCoord(region.left * image.sWidth, region.top * image.sHeight)
            ?: return false
        val bottomRight = image.sourceToViewCoord(region.right * image.sWidth, region.bottom * image.sHeight)
            ?: return false
        val placement = placeMagnifier(
            balloonLeft = topLeft.x,
            balloonTop = topLeft.y,
            balloonRight = bottomRight.x,
            balloonBottom = bottomRight.y,
            factor = bubblesPreferences.balloonZoomFactor().get(),
            hostWidth = width.toFloat(),
            hostHeight = height.toFloat(),
            margin = 8 * resources.displayMetrics.density,
        )
        bubbleMagnifier.show(bytes, region, placement)
        magnifiedBalloon = balloon
        return true
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // While a balloon is magnified, keep touches from the page so a two-finger tap doesn't zoom it.
        // In panel view, keep them from the page so swipes step through panels instead of panning.
        if (bubbleMagnifier.isShowing || isPanelViewActive) return true
        return super.onInterceptTouchEvent(ev)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (bubbleMagnifier.isShowing) {
            trackTwoFingerTap(event)
            return true
        }
        if (isPanelViewActive) return true
        return super.onTouchEvent(event)
    }

    /** Detects a quick two-finger tap that barely moves, and then shows the next balloon. */
    private fun trackTwoFingerTap(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStarts.clear()
                touchStarts[event.getPointerId(0)] = PointF(event.x, event.y)
                isTwoFingerTap = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                touchStarts[event.getPointerId(index)] = PointF(event.getX(index), event.getY(index))
                isTwoFingerTap = event.pointerCount == 2
            }
            MotionEvent.ACTION_MOVE -> {
                val slop = ViewConfiguration.get(context).scaledTouchSlop
                for (index in 0 until event.pointerCount) {
                    val start = touchStarts[event.getPointerId(index)] ?: continue
                    if (abs(event.getX(index) - start.x) > slop || abs(event.getY(index) - start.y) > slop) {
                        isTwoFingerTap = false
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                val quick = event.eventTime - event.downTime <= ViewConfiguration.getLongPressTimeout()
                if (isTwoFingerTap && quick) showNextBubble()
                isTwoFingerTap = false
                touchStarts.clear()
            }
            MotionEvent.ACTION_CANCEL -> {
                isTwoFingerTap = false
                touchStarts.clear()
            }
        }
    }

    override fun onDetachedFromWindow() {
        dismissBubbleZoom()
        super.onDetachedFromWindow()
    }
    // KMK <--

    fun recycle() = pageView?.let {
        when (it) {
            is SubsamplingScaleImageView -> it.recycle()
            is AppCompatImageView -> it.dispose()
        }
        it.isVisible = false
    }

    /**
     * Check if the image can be panned to the left
     */
    fun canPanLeft(): Boolean = canPan { it.left }

    /**
     * Check if the image can be panned to the right
     */
    fun canPanRight(): Boolean = canPan { it.right }

    // KMK -->
    /**
     * Check if the image can be panned up
     */
    fun canPanUp(): Boolean = canPan { it.top }

    /**
     * Check if the image can be panned down
     */
    fun canPanDown(): Boolean = canPan { it.bottom }
    // KMK <--

    /**
     * Check whether the image can be panned.
     * @param fn a function that returns the direction to check for
     */
    private fun canPan(fn: (RectF) -> Float): Boolean {
        (pageView as? SubsamplingScaleImageView)?.let { view ->
            RectF().let {
                view.getPanRemaining(it)
                return fn(it) > 1
            }
        }
        return false
    }

    /**
     * Pans the image to the left by a screen's width worth.
     */
    fun panLeft() {
        pan { center, view -> center.also { it.x -= view.width / view.scale } }
    }

    /**
     * Pans the image to the right by a screen's width worth.
     */
    fun panRight() {
        pan { center, view -> center.also { it.x += view.width / view.scale } }
    }

    // KMK -->
    /**
     * Pans the image down by a screen's height worth.
     */
    fun panDown() {
        pan { center, view -> center.also { it.y += view.height / view.scale } }
    }

    /**
     * Pans the image up by a screen's height worth.
     */
    fun panUp() {
        pan { center, view -> center.also { it.y -= view.height / view.scale } }
    }
    // KMK <--

    /**
     * Pans the image.
     * @param fn a function that computes the new center of the image
     */
    private fun pan(fn: (PointF, SubsamplingScaleImageView) -> PointF) {
        (pageView as? SubsamplingScaleImageView)?.let { view ->

            val target = fn(view.center ?: return, view)
            view.animateCenter(target)!!
                .withEasing(EASE_OUT_QUAD)
                .withDuration(250)
                .withInterruptible(true)
                .start()
        }
    }

    private fun prepareNonAnimatedImageView() {
        if (pageView is SubsamplingScaleImageView) return
        // KMK -->
        outlineView?.let { removeView(it) }
        outlineView = null
        panelMaskView?.let { removeView(it) }
        panelMaskView = null
        // KMK <--
        removeView(pageView)

        pageView = if (isWebtoon) {
            WebtoonSubsamplingImageView(context)
        } else {
            SubsamplingScaleImageView(context)
        }.apply {
            setMaxTileSize(ImageUtil.hardwareBitmapThreshold)
            setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_CENTER)
            setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE)
            setMinimumTileDpi(180)
            setOnStateChangedListener(
                object : SubsamplingScaleImageView.OnStateChangedListener {
                    override fun onScaleChanged(newScale: Float, origin: Int) {
                        this@ReaderPageImageView.onScaleChanged(newScale)
                        // KMK -->
                        outlineView?.invalidate()
                        panelMaskView?.invalidate()
                        dismissBubbleZoom()
                        // KMK <--
                    }

                    override fun onCenterChanged(newCenter: PointF?, origin: Int) {
                        // KMK -->
                        outlineView?.invalidate()
                        panelMaskView?.invalidate()
                        dismissBubbleZoom()
                        // KMK <--
                    }
                },
            )
            setOnClickListener { this@ReaderPageImageView.onViewClicked() }
        }
        addView(pageView, MATCH_PARENT, MATCH_PARENT)
        // KMK -->
        outlineView = BubbleOutlineView(context).also { overlay ->
            overlay.source = pageView as? SubsamplingScaleImageView
            overlay.isVisible = false
            addView(overlay, MATCH_PARENT, MATCH_PARENT)
        }
        panelMaskView = PanelMaskView(context).also { mask ->
            mask.source = pageView as? SubsamplingScaleImageView
            addView(mask, MATCH_PARENT, MATCH_PARENT)
        }
        // KMK <--
    }

    private fun SubsamplingScaleImageView.setupZoom(config: Config?) {
        // 5x zoom
        maxScale = scale * MAX_ZOOM_SCALE
        // KMK -->
        if (config?.disableZoomIn == true) {
            isZoomEnabled = false
        } else {
            if (config?.doubleTapZoom == false) {
                setDoubleTapZoomScale(scale)
            } else {
                // KMK <--
                setDoubleTapZoomScale(scale * 2)
            }
        }

        when (config?.zoomStartPosition) {
            ZoomStartPosition.LEFT -> setScaleAndCenter(scale, PointF(0F, 0F))
            ZoomStartPosition.RIGHT -> setScaleAndCenter(scale, PointF(sWidth.toFloat(), 0F))
            ZoomStartPosition.CENTER -> setScaleAndCenter(scale, center)
            null -> {}
        }
    }

    private fun setNonAnimatedImage(
        data: Any,
        config: Config,
    ) = (pageView as? SubsamplingScaleImageView)?.apply {
        setDoubleTapZoomDuration(config.zoomDuration.getSystemScaledDuration())
        setMinimumScaleType(config.minimumScaleType)
        setMinimumDpi(1) // Just so that very small image will be fit for initial load
        setCropBorders(config.cropBorders)
        setOnImageEventListener(
            object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                override fun onReady() {
                    setupZoom(config)
                    if (isVisibleOnScreen()) landscapeZoom(true)
                    this@ReaderPageImageView.onImageLoaded()
                }

                override fun onImageLoadError(e: Exception) {
                    this@ReaderPageImageView.onImageLoadError(e)
                }
            },
        )

        when (data) {
            is BitmapDrawable -> {
                setImage(ImageSource.bitmap(data.bitmap))
                isVisible = true
            }
            is BufferedSource -> {
                if (!isWebtoon || alwaysDecodeLongStripWithSSIV) {
                    setHardwareConfig(ImageUtil.canUseHardwareBitmap(data))
                    setImage(ImageSource.inputStream(data.inputStream()))
                    isVisible = true
                    return@apply
                }

                ImageRequest.Builder(context)
                    .data(data)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .target(
                        onSuccess = { result ->
                            val image = result as BitmapImage
                            setImage(ImageSource.bitmap(image.bitmap))
                            isVisible = true
                        },
                    )
                    .listener(
                        onError = { _, result ->
                            onImageLoadError(result.throwable)
                        },
                    )
                    .size(ViewSizeResolver(this@ReaderPageImageView))
                    .precision(Precision.INEXACT)
                    .cropBorders(config.cropBorders)
                    .customDecoder(true)
                    .crossfade(false)
                    .build()
                    .let(context.imageLoader::enqueue)
            }
            else -> {
                throw IllegalArgumentException("Not implemented for class ${data::class.simpleName}")
            }
        }
    }

    private fun prepareAnimatedImageView() {
        if (pageView is AppCompatImageView) return
        removeView(pageView)

        pageView = if (isWebtoon) {
            AppCompatImageView(context)
        } else {
            PhotoView(context)
        }.apply {
            adjustViewBounds = true

            if (this is PhotoView) {
                setScaleLevels(1F, 2F, MAX_ZOOM_SCALE)
                // Force 2 scale levels on double tap
                setOnDoubleTapListener(
                    object : GestureDetector.SimpleOnGestureListener() {
                        override fun onDoubleTap(e: MotionEvent): Boolean {
                            if (scale > 1F) {
                                setScale(1F, e.x, e.y, true)
                            } else {
                                setScale(2F, e.x, e.y, true)
                            }
                            return true
                        }

                        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                            this@ReaderPageImageView.onViewClicked()
                            return super.onSingleTapConfirmed(e)
                        }
                    },
                )
                setOnScaleChangeListener { _, _, _ ->
                    this@ReaderPageImageView.onScaleChanged(scale)
                }
            }
        }
        addView(pageView, MATCH_PARENT, MATCH_PARENT)
    }

    private fun setAnimatedImage(
        data: Any,
        config: Config,
    ) = (pageView as? AppCompatImageView)?.apply {
        if (this is PhotoView) {
            setZoomTransitionDuration(config.zoomDuration.getSystemScaledDuration())
        }

        val request = ImageRequest.Builder(context)
            .data(data)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .target(
                onSuccess = { result ->
                    val drawable = result.asDrawable(context.resources)
                    setImageDrawable(drawable)
                    (drawable as? Animatable)?.start()
                    isVisible = true
                    this@ReaderPageImageView.onImageLoaded()
                },
            )
            .listener(
                onError = { _, result ->
                    onImageLoadError(result.throwable)
                },
            )
            .crossfade(false)
            // KMK -->
            .allowHardware(false) // Disable hardware bitmaps for GIFs
            // KMK <--
            .build()
        context.imageLoader.enqueue(request)
    }

    private fun Int.getSystemScaledDuration(): Int {
        return (this * context.animatorDurationScale).toInt().coerceAtLeast(1)
    }

    /**
     * All of the config except [zoomDuration] will only be used for non-animated image.
     */
    data class Config(
        val zoomDuration: Int,
        val minimumScaleType: Int = SCALE_TYPE_CENTER_INSIDE,
        val cropBorders: Boolean = false,
        val zoomStartPosition: ZoomStartPosition = ZoomStartPosition.CENTER,
        val landscapeZoom: Boolean = false,
        // KMK -->
        val disableZoomIn: Boolean = false,
        val doubleTapZoom: Boolean = true,
        val landscapeZoomScaleType: LandscapeZoomScaleType = LandscapeZoomScaleType.FIT,
        // KMK <--
    )

    enum class ZoomStartPosition {
        LEFT,
        CENTER,
        RIGHT,
    }
}

private const val MAX_ZOOM_SCALE = 5F

// KMK -->
// Extra space around a magnified balloon, as a fraction of its size on each side.
private const val BUBBLE_ZOOM_PADDING = 0.06f

// How long moving between panels takes, in milliseconds.
private const val PANEL_ANIMATION_MS = 250L
// KMK <--
