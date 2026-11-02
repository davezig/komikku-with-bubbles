package eu.kanade.tachiyomi.ui.reader.bubbles

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

// KMK -->
/**
 * Runs the on-device page model that finds speech balloons and panels.
 * Not thread-safe on its own: [detect] is synchronized, so callers can share one instance.
 */
class PageDetector(context: Context) : Closeable {

    private val interpreter: Interpreter

    /** How many pages the model processes in one call. */
    val batchSize: Int

    private val inputHeight: Int
    private val inputWidth: Int
    private val maxDetections: Int

    init {
        interpreter = Interpreter(
            loadModel(context, MODEL_ASSET),
            Interpreter.Options().setNumThreads(NUM_THREADS),
        )
        val inputShape = interpreter.getInputTensor(0).shape() // [batch, height, width, channels]
        batchSize = inputShape[0]
        inputHeight = inputShape[1]
        inputWidth = inputShape[2]
        maxDetections = interpreter.getOutputTensor(0).shape()[1]
    }

    /**
     * Detects objects on up to [batchSize] pages. Returns one list per input page, with every
     * detection the model reports (no score filtering).
     */
    @Synchronized
    fun detect(pages: List<Bitmap>): List<List<PageDetection>> {
        require(pages.size in 1..batchSize) { "Expected 1..$batchSize pages, got ${pages.size}" }

        val input = ByteBuffer.allocateDirect(batchSize * inputHeight * inputWidth * CHANNELS * FLOAT_BYTES)
            .order(ByteOrder.nativeOrder())
        for (page in pages) {
            writePage(page, input)
        }
        // The model always expects a full batch, so pad unused slots with black.
        repeat((batchSize - pages.size) * inputHeight * inputWidth * CHANNELS) { input.putFloat(0f) }
        input.rewind()

        val boxes = Array(batchSize) { Array(maxDetections) { FloatArray(BOX_VALUES) } }
        val classes = Array(batchSize) { IntArray(maxDetections) }
        val valid = IntArray(batchSize)
        val outputs = HashMap<Int, Any>()
        outputs[0] = boxes
        outputs[1] = classes
        outputs[2] = valid
        interpreter.runForMultipleInputsOutputs(arrayOf(input), outputs)

        return pages.indices.map { i -> parseDetections(boxes[i], classes[i], valid[i]) }
    }

    private fun writePage(page: Bitmap, into: ByteBuffer) {
        val scaled = Bitmap.createScaledBitmap(page, inputWidth, inputHeight, true)
        val pixels = IntArray(inputWidth * inputHeight)
        scaled.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)
        if (scaled !== page) scaled.recycle()
        for (pixel in pixels) {
            into.putFloat(((pixel shr 16) and 0xFF) / 255f)
            into.putFloat(((pixel shr 8) and 0xFF) / 255f)
            into.putFloat((pixel and 0xFF) / 255f)
        }
    }

    override fun close() {
        interpreter.close()
    }

    private companion object {
        const val MODEL_ASSET = "bubbles/yolo_seeneva.tflite"
        const val NUM_THREADS = 4
        const val CHANNELS = 3
        const val FLOAT_BYTES = 4
        // Each box is (y_min, x_min, y_max, x_max, score).
        const val BOX_VALUES = 5

        fun loadModel(context: Context, asset: String): MappedByteBuffer {
            context.assets.openFd(asset).use { fd ->
                FileInputStream(fd.fileDescriptor).channel.use { channel: FileChannel ->
                    return channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                }
            }
        }
    }
}

/** Turns one page's raw model output into detections, skipping invalid slots and unknown classes. */
internal fun parseDetections(
    boxes: Array<FloatArray>,
    classes: IntArray,
    validCount: Int,
): List<PageDetection> {
    val result = ArrayList<PageDetection>()
    for (i in 0 until minOf(validCount, boxes.size)) {
        val box = boxes[i]
        val kind = DetectionKind.fromModelClassId(classes[i]) ?: continue
        result += PageDetection(
            kind = kind,
            score = box[4],
            left = box[1],
            top = box[0],
            right = box[3],
            bottom = box[2],
        )
    }
    return result
}
// KMK <--
