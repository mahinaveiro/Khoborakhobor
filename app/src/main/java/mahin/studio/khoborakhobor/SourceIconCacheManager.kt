package mahin.studio.khoborakhobor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

sealed interface SourceIconState {
    data object Missing : SourceIconState
    data object Failed : SourceIconState
    data class Ready(val file: File, val bitmap: Bitmap? = null) : SourceIconState
}

class SourceIconCacheManager(
    context: Context,
    private val client: OkHttpClient = OkHttpClient()
) {
    private val appContext = context.applicationContext
    private val cacheRoot: File by lazy { File(appContext.cacheDir, "source_icons") }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<NewsSource>(Channel.UNLIMITED)
    private val states = ConcurrentHashMap<String, MutableStateFlow<SourceIconState>>()
    private val queuedIds = ConcurrentHashMap.newKeySet<String>()
    private val failedIds = ConcurrentHashMap.newKeySet<String>()
    private val diskHits = AtomicInteger(0)
    private val networkMisses = AtomicInteger(0)
    private val downloads = AtomicInteger(0)
    private val failures = AtomicInteger(0)
    private val decodeLogs = AtomicInteger(0)
    private val activeJobs = AtomicInteger(0)
    private val _iconUpdates = MutableSharedFlow<String>(extraBufferCapacity = 64)
    private val _isLoading = MutableStateFlow(false)
    private val summaryLock = Any()
    private var summaryJob: Job? = null

    val iconUpdates: SharedFlow<String> = _iconUpdates.asSharedFlow()
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        repeat(MAX_CONCURRENT_DOWNLOADS) {
            scope.launch {
                for (source in queue) {
                    activeJobs.incrementAndGet()
                    _isLoading.value = true
                    try {
                        loadIcon(source)
                    } finally {
                        if (activeJobs.decrementAndGet() == 0) {
                            _isLoading.value = false
                        }
                    }
                }
            }
        }
    }

    fun stateFor(source: NewsSource): StateFlow<SourceIconState> {
        return states.getOrPut(source.id) {
            MutableStateFlow(SourceIconState.Missing)
        }
    }

    fun request(source: NewsSource) {
        val currentState = stateFor(source).value
        if (
            currentState is SourceIconState.Ready ||
            currentState == SourceIconState.Failed ||
            failedIds.contains(source.id) ||
            !queuedIds.add(source.id)
        ) {
            return
        }
        queue.trySend(source)
    }

    private fun loadIcon(source: NewsSource) {
        try {
            val flow = states.getOrPut(source.id) {
                MutableStateFlow(SourceIconState.Missing)
            }
            cachedIconFile(source.id)?.let { cachedFile ->
                diskHits.incrementAndGet()
                flow.value = SourceIconState.Ready(cachedFile, decodeBitmap(cachedFile))
                _iconUpdates.tryEmit(source.id)
                return
            }

            val iconUrl = SourceIconResolver.iconUrl(source)
                ?: throw IOException("Missing icon URL")
            networkMisses.incrementAndGet()

            val bytes = downloadIcon(iconUrl)
            cacheRoot.mkdirs()
            val extension = iconExtension(iconUrl, bytes)
            val targetFile = File(cacheRoot, "${safeFileName(source.id)}.$extension")
            deleteOtherFormats(source.id, keepExtension = extension)
            targetFile.writeBytes(bytes)
            downloads.incrementAndGet()
            flow.value = SourceIconState.Ready(targetFile, decodeBitmap(bytes))
            _iconUpdates.tryEmit(source.id)
        } catch (_: Throwable) {
            failedIds.add(source.id)
            failures.incrementAndGet()
            states.getOrPut(source.id) {
                MutableStateFlow(SourceIconState.Missing)
            }.value = SourceIconState.Failed
            _iconUpdates.tryEmit(source.id)
        } finally {
            queuedIds.remove(source.id)
            scheduleSummaryLog()
        }
    }

    private fun downloadIcon(iconUrl: String): ByteArray {
        val request = Request.Builder()
            .url(iconUrl)
            .header("User-Agent", "Khoborakhobor/1.0")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Icon request failed ${response.code}")
            }
            val body = response.body ?: throw IOException("Empty icon body")
            val bytes = body.bytes()
            if (bytes.isEmpty() || bytes.size > MAX_ICON_BYTES) {
                throw IOException("Invalid icon size ${bytes.size}")
            }
            return bytes
        }
    }

    private fun cachedIconFile(sourceId: String): File? {
        val baseName = safeFileName(sourceId)
        return ICON_EXTENSIONS
            .map { extension -> File(cacheRoot, "$baseName.$extension") }
            .firstOrNull { file -> file.exists() && file.length() > 0L }
    }

    private fun deleteOtherFormats(sourceId: String, keepExtension: String) {
        val baseName = safeFileName(sourceId)
        ICON_EXTENSIONS
            .filterNot { it == keepExtension }
            .map { extension -> File(cacheRoot, "$baseName.$extension") }
            .forEach { file -> if (file.exists()) file.delete() }
    }

    private fun iconExtension(iconUrl: String, bytes: ByteArray): String {
        return when {
            iconUrl.endsWith(".webp", ignoreCase = true) -> "webp"
            bytes.size >= 12 &&
                bytes[0] == 'R'.code.toByte() &&
                bytes[1] == 'I'.code.toByte() &&
                bytes[2] == 'F'.code.toByte() &&
                bytes[3] == 'F'.code.toByte() &&
                bytes[8] == 'W'.code.toByte() &&
                bytes[9] == 'E'.code.toByte() &&
                bytes[10] == 'B'.code.toByte() &&
                bytes[11] == 'P'.code.toByte() -> "webp"
            else -> "png"
        }
    }

    private fun decodeBitmap(file: File): Bitmap? {
        val start = SystemClock.elapsedRealtime()
        return BitmapFactory.decodeFile(file.absolutePath).also {
            logDecodeDuration(start)
        }
    }

    private fun decodeBitmap(bytes: ByteArray): Bitmap? {
        val start = SystemClock.elapsedRealtime()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size).also {
            logDecodeDuration(start)
        }
    }

    private fun logDecodeDuration(start: Long) {
        val duration = SystemClock.elapsedRealtime() - start
        if (decodeLogs.getAndIncrement() < MAX_DECODE_LOGS || duration >= 16L) {
            PerformanceLogger.mark("sourceIconDecode ${duration}ms")
        }
    }

    private fun scheduleSummaryLog() {
        synchronized(summaryLock) {
            summaryJob?.cancel()
            summaryJob = scope.launch {
                delay(SUMMARY_DEBOUNCE_MS)
                PerformanceLogger.mark(
                    "Icon cache summary diskHits=${diskHits.get()} " +
                        "networkMisses=${networkMisses.get()} " +
                        "downloaded=${downloads.get()} failed=${failures.get()}"
                )
            }
        }
    }

    private fun safeFileName(sourceId: String): String {
        return sourceId.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    private companion object {
        const val MAX_CONCURRENT_DOWNLOADS = 1
        const val MAX_ICON_BYTES = 512 * 1024
        const val MAX_DECODE_LOGS = 8
        const val SUMMARY_DEBOUNCE_MS = 900L
        val ICON_EXTENSIONS = listOf("png", "webp")
    }
}
