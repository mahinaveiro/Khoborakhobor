package mahin.studio.khoborakhobor

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class ReaderPageRepository(context: Context) {
    private val appContext = context.applicationContext
    private val readerCacheDir = File(appContext.filesDir, "reader_cache")
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(16, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun buildReaderPage(
        url: String,
        source: NewsSource,
        darkMode: Boolean
    ): Result<ReaderPage> {
        return runCatching {
            BackgroundThreadGuard.requireBackgroundThread("generateReaderHtml")
            val fetched = fetchRawHtml(url)
            writeReaderFile(
                fileName = "browser_reader_${source.id}.html",
                rawHtml = fetched.html,
                originalUrl = fetched.url,
                title = ReaderHtmlGenerator.extractTitle(fetched.html, fetched.url).ifBlank { source.name },
                sourceName = source.name,
                darkMode = darkMode,
                savedAt = null
            )
        }.onFailure { error ->
            Log.e(TAG, "Could not build reader page", error)
        }
    }

    fun buildOfflineReaderPage(
        page: OfflinePage,
        darkMode: Boolean
    ): Result<ReaderPage> {
        return runCatching {
            BackgroundThreadGuard.requireBackgroundThread("generateReaderHtml")
            val rawHtmlFile = File(page.rawHtmlPath)
            if (!rawHtmlFile.exists()) {
                throw IOException("Missing raw offline HTML: ${page.rawHtmlPath}")
            }
            writeReaderFile(
                fileName = "offline_reader_${page.id}.html",
                rawHtml = rawHtmlFile.readText(Charsets.UTF_8),
                originalUrl = page.originalUrl,
                title = page.title,
                sourceName = page.sourceName,
                darkMode = darkMode,
                savedAt = page.savedAt
            )
        }.onFailure { error ->
            Log.e(TAG, "Could not build offline reader page", error)
        }
    }

    fun fetchPageSnapshot(url: String, source: NewsSource): Result<PageSnapshot> {
        return runCatching {
            BackgroundThreadGuard.requireBackgroundThread("saveOfflinePage")
            val fetched = fetchRawHtml(url)
            val savedAt = System.currentTimeMillis()
            val title = ReaderHtmlGenerator.extractTitle(fetched.html, fetched.url)
                .ifBlank { "${source.name} ${fallbackDate(savedAt)}" }
            PageSnapshot(
                title = title,
                url = fetched.url.ifBlank { url },
                rawHtml = fetched.html,
                savedAt = savedAt
            )
        }.onFailure { error ->
            Log.e(TAG, "Could not fetch offline page", error)
        }
    }

    private fun writeReaderFile(
        fileName: String,
        rawHtml: String,
        originalUrl: String,
        title: String,
        sourceName: String,
        darkMode: Boolean,
        savedAt: Long?
    ): ReaderPage {
        readerCacheDir.mkdirs()
        val outputFile = File(readerCacheDir, fileName)
        val readerHtml = PerformanceLogger.trace("generateReaderHtml") {
            ReaderHtmlGenerator.generate(
                rawHtml = rawHtml,
                originalUrl = originalUrl,
                title = title,
                sourceName = sourceName,
                darkMode = darkMode,
                savedAt = savedAt
            )
        }
        outputFile.writeText(readerHtml, Charsets.UTF_8)
        return ReaderPage(
            title = title,
            originalUrl = originalUrl,
            localHtmlPath = outputFile.absolutePath
        )
    }

    private fun fetchRawHtml(url: String): FetchedPage {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} while fetching $url")
            }
            val html = response.body?.string().orEmpty()
            if (html.isBlank()) {
                throw IOException("Empty response while fetching $url")
            }
            return FetchedPage(
                url = response.request.url.toString(),
                html = html
            )
        }
    }

    private fun fallbackDate(savedAt: Long): String {
        return SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(savedAt))
    }

    private data class FetchedPage(
        val url: String,
        val html: String
    )

    private companion object {
        const val TAG = "ReaderPageRepository"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Khoborakhobor/1.0 Mobile Safari/537.36"
    }
}

data class ReaderPage(
    val title: String,
    val originalUrl: String,
    val localHtmlPath: String
)
