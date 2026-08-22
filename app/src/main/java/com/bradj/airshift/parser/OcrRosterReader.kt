package com.bradj.airshift.parser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import com.bradj.airshift.BuildConfig
import com.paddle.ocr.EngineConfig
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.PaddleOCRConfig
import com.paddle.ocr.util.OpenCVUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import kotlin.math.ceil
import kotlin.math.floor

object OcrRosterReader {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val initializationMutex = Mutex()
    private val inferenceMutex = Mutex()

    @Volatile
    private var paddleOcr: PaddleOCR? = null

    fun read(
        context: Context,
        uri: Uri,
        userName: String,
        onResult: (Result<RosterParseResult>) -> Unit,
    ) {
        val appContext = context.applicationContext
        scope.launch {
            val result = runCatching {
                val bitmap = decodeBitmap(appContext, uri)
                try {
                    readBitmap(appContext, bitmap, userName)
                } finally {
                    bitmap.recycle()
                }
            }
            onResult(result)
        }
    }

    internal suspend fun readBitmap(
        context: Context,
        bitmap: Bitmap,
        userName: String,
        clock: Clock = Clock.systemDefaultZone(),
    ): RosterParseResult {
        val ocr = getOrCreateEngine(context.applicationContext)
        val ocrResult = inferenceMutex.withLock { ocr.recognize(bitmap) }
        val tokens = ocrResult.results.mapNotNull { line ->
            val text = line.text.trim()
            if (text.isEmpty()) return@mapNotNull null
            val left = floor(line.box.points.minOf { it.x }.toDouble()).toInt().coerceIn(0, bitmap.width)
            val top = floor(line.box.points.minOf { it.y }.toDouble()).toInt().coerceIn(0, bitmap.height)
            val right = ceil(line.box.points.maxOf { it.x }.toDouble()).toInt().coerceIn(0, bitmap.width)
            val bottom = ceil(line.box.points.maxOf { it.y }.toDouble()).toInt().coerceIn(0, bitmap.height)
            OcrToken(text, left, top, right, bottom).takeIf { right > left && bottom > top }
        }
        if (BuildConfig.DEBUG) {
            Log.d(
                "AirShiftOCR",
                "engine=PP-OCRv6-tiny lines=${ocrResult.lineCount} tokens=${tokens.size} totalMs=${ocrResult.totalTimeMs}",
            )
        }
        return RosterTableParser.parse(
            tokens = tokens,
            imageWidth = bitmap.width,
            userName = userName,
            clock = clock,
        )
    }

    private suspend fun decodeBitmap(context: Context, uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val decoded = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        if (decoded.config == Bitmap.Config.ARGB_8888) {
            decoded
        } else {
            decoded.copy(Bitmap.Config.ARGB_8888, false).also { decoded.recycle() }
        }
    }

    private suspend fun getOrCreateEngine(context: Context): PaddleOCR {
        paddleOcr?.let { return it }
        return initializationMutex.withLock {
            paddleOcr ?: run {
                check(OpenCVUtils.init(context)) { "OpenCV 初始化失败" }
                PaddleOCR.create(
                    context = context,
                    config = PaddleOCRConfig(
                        detThresh = 0.2f,
                        detBoxThresh = 0.4f,
                        detUnclipRatio = 1.4f,
                        recScoreThresh = 0.35f,
                        recBatchSize = 4,
                    ),
                    engineConfig = EngineConfig(
                        numThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4),
                    ),
                ).also { paddleOcr = it }
            }
        }
    }
}
