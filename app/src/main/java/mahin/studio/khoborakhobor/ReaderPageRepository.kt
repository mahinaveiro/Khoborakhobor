package mahin.studio.khoborakhobor

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
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
            val archiveFile = File(page.offlineDisplayPath)
            if (!archiveFile.exists()) {
                throw IOException("Missing offline HTML: ${page.offlineDisplayPath}")
            }
            if (!darkMode) {
                return@runCatching ReaderPage(
                    title = page.title,
                    originalUrl = page.originalUrl,
                    localHtmlPath = archiveFile.absolutePath
                )
            }
            val cacheFile = offlineOverlayCacheFile(page, archiveFile)
            if (cacheFile.exists() && cacheFile.length() > 0L) {
                PerformanceLogger.mark("generateReaderHtml cacheHit page=${page.id} darkMode=$darkMode")
                return@runCatching ReaderPage(
                    title = page.title,
                    originalUrl = page.originalUrl,
                    localHtmlPath = cacheFile.absolutePath
                )
            }
            PerformanceLogger.mark("generateReaderHtml cacheMiss page=${page.id} darkMode=$darkMode")
            cacheFile.writeText(
                appendOfflineDarkOverlay(
                    html = archiveFile.readText(Charsets.UTF_8),
                    archiveFile = archiveFile
                ),
                Charsets.UTF_8
            )
            ReaderPage(
                title = page.title,
                originalUrl = page.originalUrl,
                localHtmlPath = cacheFile.absolutePath
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

    private fun offlineOverlayCacheFile(
        page: OfflinePage,
        archiveFile: File
    ): File {
        readerCacheDir.mkdirs()
        val modified = archiveFile.lastModified().coerceAtLeast(0L)
        val safePageId = page.id.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(readerCacheDir, "offline_archive_${safePageId}_dark_$modified.html")
    }

    private fun appendOfflineDarkOverlay(html: String, archiveFile: File): String {
        val doc = Jsoup.parse(html, archiveFile.toURI().toString())
        doc.outputSettings().prettyPrint(false)
        doc.head().select("style[data-khobor-offline-dark]").remove()
        doc.head().select("base").remove()
        archiveFile.parentFile?.let { parent ->
            doc.head().prependElement("base").attr("href", parent.toURI().toString())
        }
        doc.head().appendElement("style")
            .attr("data-khobor-offline-dark", "true")
            .appendText(OFFLINE_DARK_OVERLAY_CSS)
        return doc.outerHtml()
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
        val OFFLINE_DARK_OVERLAY_CSS = """
            html,body{background:#080808!important;color:#F5F2EC!important;color-scheme:dark!important}
            body,main,article,section,header,footer,nav,aside,div,p,span,li,ul,ol,table,tbody,thead,tfoot,tr,td,th,form,label,figure,figcaption,blockquote{border-color:#2A2A2A!important;color:inherit!important}
            main,article,section,header,footer,nav,aside,div,table,tbody,thead,tfoot,tr,td,th,form{background-color:transparent!important}
            h1,h2,h3,h4,h5,h6,p,span,li,blockquote,figcaption,strong,b,em,small,label,time{color:#F5F2EC!important}
            a,a span{color:#F5F2EC!important}
            input,textarea,select,button{background:#151515!important;color:#F5F2EC!important;border-color:#333333!important}
            *[style*="background:#fff"],*[style*="background: #fff"],*[style*="background-color:#fff"],*[style*="background-color: #fff"],*[style*="background:white"],*[style*="background: white"],*[style*="background-color:white"],*[style*="background-color: white"]{background-color:#151515!important}
            img,picture,video,svg,canvas,iframe{filter:none!important;opacity:1!important}
        """.trimIndent()
    }
}

data class ReaderPage(
    val title: String,
    val originalUrl: String,
    val localHtmlPath: String
)
