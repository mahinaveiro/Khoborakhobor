package mahin.studio.khoborakhobor

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
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

    fun buildDarkReaderPage(url: String, source: NewsSource): Result<ReaderPage> {
        return runCatching {
            val article = fetchArticle(url)
            readerCacheDir.mkdirs()
            val outputFile = File(readerCacheDir, "current_dark_reader.html")
            outputFile.writeText(
                cleanReaderHtml(
                    title = article.title.ifBlank { source.name },
                    originalUrl = article.url,
                    sourceName = source.name,
                    contentHtml = article.contentHtml,
                    darkOnly = true,
                    savedAt = null
                ),
                Charsets.UTF_8
            )
            ReaderPage(
                title = article.title.ifBlank { source.name },
                originalUrl = article.url,
                localHtmlPath = outputFile.absolutePath
            )
        }.onFailure { error ->
            Log.e(TAG, "Could not build dark reader", error)
        }
    }

    fun buildOfflineSnapshot(url: String, source: NewsSource): Result<PageSnapshot> {
        return runCatching {
            val article = fetchArticle(url)
            val savedAt = System.currentTimeMillis()
            PageSnapshot(
                title = article.title.ifBlank { "${source.name} ${fallbackDate(savedAt)}" },
                url = article.url.ifBlank { url },
                html = cleanReaderHtml(
                    title = article.title.ifBlank { source.name },
                    originalUrl = article.url.ifBlank { url },
                    sourceName = source.name,
                    contentHtml = article.contentHtml,
                    darkOnly = false,
                    savedAt = savedAt
                ),
                savedAt = savedAt
            )
        }.onFailure { error ->
            Log.e(TAG, "Could not build offline snapshot", error)
        }
    }

    private fun fetchArticle(url: String): ExtractedArticle {
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
            return extractArticle(html, response.request.url.toString())
        }
    }

    private fun extractArticle(html: String, baseUrl: String): ExtractedArticle {
        val doc = Jsoup.parse(html, baseUrl)
        doc.outputSettings().prettyPrint(false)
        cleanDocument(doc)

        val title = cleanTitle(
            doc.selectFirst("meta[property=og:title]")?.attr("content")
                ?: doc.selectFirst("meta[name=twitter:title]")?.attr("content")
                ?: doc.title().takeIf { it.isNotBlank() }
                ?: doc.selectFirst("h1")?.text()
                ?: ""
        )
        val content = selectArticleElement(doc)
        absolutizeLinks(content ?: doc)

        val contentHtml = content?.html()?.ifBlank { null }
            ?: doc.body().html().ifBlank { null }
            ?: "<p>${escapeHtml(doc.text())}</p>"

        return ExtractedArticle(
            title = title.ifBlank { cleanTitle(doc.selectFirst("h1")?.text().orEmpty()) },
            url = baseUrl,
            contentHtml = contentHtml
        )
    }

    private fun cleanDocument(doc: Document) {
        doc.select("script,noscript,style,template,iframe,form,nav,footer").remove()
        doc.allElements
            .filter { element ->
                val marker = "${element.id()} ${element.className()}".lowercase()
                AD_MARKERS.any { marker.contains(it) }
            }
            .forEach { it.remove() }
    }

    private fun selectArticleElement(doc: Document): Element? {
        val selectors = listOf(
            "article",
            "main article",
            "main",
            ".article",
            ".post",
            ".news-details",
            ".content",
            "[class*=article]",
            "[class*=post-content]",
            "[class*=story]"
        )
        return selectors.asSequence()
            .mapNotNull { selector -> doc.selectFirst(selector) }
            .firstOrNull { element -> element.text().length > MIN_ARTICLE_TEXT_LENGTH }
    }

    private fun absolutizeLinks(root: Element) {
        root.select("a[href]").forEach { element ->
            val href = element.absUrl("href")
            if (href.isNotBlank()) {
                element.attr("href", href)
            }
        }
        root.select("img[src]").forEach { element ->
            val src = element.absUrl("src")
            if (src.isNotBlank()) {
                element.attr("src", src)
            }
            element.removeAttr("srcset")
            element.removeAttr("sizes")
            element.removeAttr("loading")
        }
    }

    private fun cleanReaderHtml(
        title: String,
        originalUrl: String,
        sourceName: String,
        contentHtml: String,
        darkOnly: Boolean,
        savedAt: Long?
    ): String {
        val savedLine = savedAt?.let { "<p class=\"meta\">Saved ${escapeHtml(fullDate(it))}</p>" }.orEmpty()
        val themeCss = if (darkOnly) {
            DARK_READER_CSS
        } else {
            OFFLINE_READER_CSS
        }
        return """
            <!doctype html>
            <html lang="bn">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <base href="${escapeHtmlAttribute(originalUrl)}">
              <title>${escapeHtml(title)}</title>
              <style>$themeCss</style>
            </head>
            <body>
              <article class="reader-shell">
                <header>
                  <p class="meta">${escapeHtml(sourceName)}</p>
                  <h1>${escapeHtml(title)}</h1>
                  <p class="meta"><a href="${escapeHtmlAttribute(originalUrl)}">${escapeHtml(originalUrl)}</a></p>
                  $savedLine
                </header>
                <main>
                  $contentHtml
                </main>
              </article>
            </body>
            </html>
        """.trimIndent()
    }

    private fun cleanTitle(title: String): String {
        return title
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(180)
    }

    private fun fallbackDate(savedAt: Long): String {
        return SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(savedAt))
    }

    private fun fullDate(savedAt: Long): String {
        return SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date(savedAt))
    }

    private fun escapeHtml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    private fun escapeHtmlAttribute(value: String): String {
        return escapeHtml(value).replace("\"", "&quot;")
    }

    private data class ExtractedArticle(
        val title: String,
        val url: String,
        val contentHtml: String
    )

    private companion object {
        const val TAG = "ReaderPageRepository"
        const val MIN_ARTICLE_TEXT_LENGTH = 160
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Khoborakhobor/1.0 Mobile Safari/537.36"
        val AD_MARKERS = listOf(
            "advert",
            "advertisement",
            " ad-",
            "-ad",
            " ads",
            "promo",
            "sponsor",
            "share",
            "social",
            "related"
        )

        val DARK_READER_CSS = """
            :root{color-scheme:dark}
            *{box-sizing:border-box}
            html,body{margin:0;background:#080808;color:#f1f1f1}
            body{font-family:system-ui,-apple-system,BlinkMacSystemFont,"Noto Sans Bengali","Noto Serif Bengali",sans-serif;line-height:1.72}
            .reader-shell{max-width:760px;margin:0 auto;min-height:100vh;padding:28px 18px;background:#111111}
            h1{font-size:1.85rem;line-height:1.28;margin:8px 0 14px}
            p,li{font-size:1.05rem}
            .meta{color:#b8b8b8;font-size:.9rem;margin:4px 0}
            a{color:#ffffff;text-decoration:underline;text-underline-offset:3px}
            img,video{max-width:100%;height:auto}
            figure{margin:18px 0}
            figcaption{color:#b8b8b8;font-size:.9rem}
            blockquote{border-left:3px solid rgba(255,255,255,.28);margin:18px 0;padding-left:14px;color:#e7e7e7}
            table{max-width:100%;border-collapse:collapse}
            td,th{border:1px solid rgba(255,255,255,.16);padding:8px}
        """.trimIndent()

        val OFFLINE_READER_CSS = """
            :root{color-scheme:light dark;--bg:#f7f6f2;--surface:#ffffff;--text:#111111;--muted:#5f5f5f;--border:rgba(0,0,0,.14);--link:#111111}
            @media (prefers-color-scheme:dark){:root{--bg:#080808;--surface:#111111;--text:#f1f1f1;--muted:#b8b8b8;--border:rgba(255,255,255,.16);--link:#ffffff}}
            *{box-sizing:border-box}
            html,body{margin:0;background:var(--bg);color:var(--text)}
            body{font-family:system-ui,-apple-system,BlinkMacSystemFont,"Noto Sans Bengali","Noto Serif Bengali",sans-serif;line-height:1.72}
            .reader-shell{max-width:760px;margin:0 auto;min-height:100vh;padding:28px 18px;background:var(--surface)}
            h1{font-size:1.85rem;line-height:1.28;margin:8px 0 14px}
            p,li{font-size:1.05rem}
            .meta{color:var(--muted);font-size:.9rem;margin:4px 0}
            a{color:var(--link);text-decoration:underline;text-underline-offset:3px}
            img,video{max-width:100%;height:auto}
            figure{margin:18px 0}
            figcaption{color:var(--muted);font-size:.9rem}
            blockquote{border-left:3px solid var(--border);margin:18px 0;padding-left:14px}
            table{max-width:100%;border-collapse:collapse}
            td,th{border:1px solid var(--border);padding:8px}
        """.trimIndent()
    }
}

data class ReaderPage(
    val title: String,
    val originalUrl: String,
    val localHtmlPath: String
)
