package mahin.studio.khoborakhobor

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit

class OfflinePageArchiver(
    private val pagesDir: File,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {
    private val assetPaths = linkedMapOf<String, String>()
    private var assetIndex = 0
    private var totalAssetBytes = 0L
    private lateinit var assetsDir: File

    fun archive(pageId: String, source: NewsSource, snapshot: PageSnapshot): ArchivedOfflinePage {
        BackgroundThreadGuard.requireBackgroundThread("archiveOfflinePage")
        val pageDir = File(pagesDir, pageId)
        assetsDir = File(pageDir, "assets")
        pageDir.mkdirs()
        assetsDir.mkdirs()

        val rawHtmlFile = File(pageDir, "raw.html")
        rawHtmlFile.writeText(snapshot.rawHtml, Charsets.UTF_8)

        val doc = Jsoup.parse(snapshot.rawHtml, snapshot.url)
        doc.outputSettings().prettyPrint(false)
        prepareDocument(doc)
        rewriteStylesheets(doc)
        rewriteImages(doc)
        rewriteMediaPosters(doc)
        rewriteAnchors(doc)

        val indexFile = File(pageDir, "index.html")
        indexFile.writeText(doc.outerHtml(), Charsets.UTF_8)
        File(pageDir, "metadata.json").writeText(
            JSONObject()
                .put("id", pageId)
                .put("title", snapshot.title)
                .put("sourceName", source.name)
                .put("sourceId", source.id)
                .put("sourceUrl", source.url)
                .put("originalUrl", snapshot.url)
                .put("savedAt", snapshot.savedAt)
                .put("rawHtmlPath", rawHtmlFile.absolutePath)
                .put("archiveHtmlPath", indexFile.absolutePath)
                .toString(),
            Charsets.UTF_8
        )

        return ArchivedOfflinePage(
            rawHtmlPath = rawHtmlFile.absolutePath,
            archiveHtmlPath = indexFile.absolutePath,
            archiveDirPath = pageDir.absolutePath
        )
    }

    private fun prepareDocument(doc: Document) {
        doc.select("base").remove()
        if (doc.head().selectFirst("meta[charset]") == null) {
            doc.head().prependElement("meta").attr("charset", "utf-8")
        }
        if (doc.head().selectFirst("meta[name=viewport]") == null) {
            doc.head().appendElement("meta")
                .attr("name", "viewport")
                .attr("content", "width=device-width, initial-scale=1")
        }
        doc.select("script").forEach { script ->
            val marker = "${script.attr("src")} ${script.className()} ${script.id()} ${script.data()}".lowercase()
            if (SCRIPT_SKIP_MARKERS.any { marker.contains(it) }) {
                script.remove()
            }
        }
    }

    private fun rewriteStylesheets(doc: Document) {
        doc.select("link[href]").forEach { link ->
            val rel = link.attr("rel").lowercase(Locale.US)
            val asValue = link.attr("as").lowercase(Locale.US)
            val href = link.absUrl("href")
            when {
                href.isBlank() -> Unit
                rel.contains("stylesheet") || asValue == "style" -> {
                    downloadCssAsset(href)?.let { localPath ->
                        link.attr("href", localPath)
                        link.attr("rel", "stylesheet")
                        link.removeAttr("integrity")
                        link.removeAttr("crossorigin")
                    }
                }
                rel.contains("icon") || rel.contains("apple-touch-icon") -> {
                    downloadBinaryAsset(href, "icon", MAX_IMAGE_BYTES)?.let { localPath ->
                        link.attr("href", localPath)
                    }
                }
            }
        }
        doc.select("style").forEach { style ->
            style.text(rewriteCssUrls(style.data(), doc.location()))
        }
    }

    private fun rewriteImages(doc: Document) {
        doc.select("img").forEach { image ->
            promoteLazyImageSource(image)
            rewriteUrlAttr(image, "src", "image", MAX_IMAGE_BYTES)
            rewriteSrcSet(image, "srcset")
        }
        doc.select("source").forEach { source ->
            rewriteUrlAttr(source, "src", "image", MAX_IMAGE_BYTES)
            rewriteSrcSet(source, "srcset")
        }
    }

    private fun rewriteMediaPosters(doc: Document) {
        doc.select("video[poster]").forEach { video ->
            rewriteUrlAttr(video, "poster", "image", MAX_IMAGE_BYTES)
        }
    }

    private fun rewriteAnchors(doc: Document) {
        doc.select("a[href]").forEach { link ->
            link.absUrl("href").takeIf { it.isNotBlank() }?.let { link.attr("href", it) }
        }
    }

    private fun promoteLazyImageSource(image: Element) {
        val currentSrc = image.attr("src").trim()
        if (currentSrc.isNotBlank() && !currentSrc.startsWith("data:image/", ignoreCase = true)) return
        listOf("data-src", "data-original", "data-lazy-src", "data-actualsrc")
            .firstNotNullOfOrNull { attr -> image.attr(attr).takeIf { it.isNotBlank() } }
            ?.let { image.attr("src", it) }
    }

    private fun rewriteUrlAttr(element: Element, attr: String, kind: String, maxBytes: Long) {
        val absoluteUrl = element.absUrl(attr).ifBlank { resolveUrl(element.baseUri(), element.attr(attr)).orEmpty() }
        if (absoluteUrl.isBlank()) return
        downloadBinaryAsset(absoluteUrl, kind, maxBytes)?.let { element.attr(attr, it) }
    }

    private fun rewriteSrcSet(element: Element, attr: String) {
        val srcSet = element.attr(attr).trim()
        if (srcSet.isBlank()) return
        val rewritten = srcSet.split(",")
            .joinToString(", ") { candidate ->
                val parts = candidate.trim().split(Regex("\\s+"), limit = 2)
                val rawUrl = parts.firstOrNull().orEmpty()
                val absoluteUrl = resolveUrl(element.baseUri(), rawUrl)
                val localPath = absoluteUrl?.let { downloadBinaryAsset(it, "image", MAX_IMAGE_BYTES) }
                val url = localPath ?: rawUrl
                if (parts.size == 2) "$url ${parts[1]}" else url
            }
        element.attr(attr, rewritten)
    }

    private fun downloadCssAsset(url: String): String? {
        assetPaths[url]?.let { return it }
        if (!canFetch(url)) return null
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/css,*/*;q=0.8")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body ?: return@runCatching null
                val bytes = readBytesLimited(body, MAX_CSS_BYTES) ?: return@runCatching null
                val relativePath = nextAssetPath(url, response.header("Content-Type"), "style", "css")
                assetPaths[url] = relativePath
                val charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
                val css = bytes.toString(charset)
                val rewrittenCss = rewriteCssUrls(css, url)
                File(assetsDir, relativePath.removePrefix("assets/")).writeText(rewrittenCss, Charsets.UTF_8)
                totalAssetBytes += bytes.size
                relativePath
            }
        }.getOrNull()
    }

    private fun rewriteCssUrls(css: String, baseUrl: String): String {
        return CSS_URL_REGEX.replace(css) { match ->
            val rawUrl = match.groups[2]?.value?.trim().orEmpty()
            val absoluteUrl = resolveUrl(baseUrl, rawUrl) ?: return@replace match.value
            val localPath = downloadBinaryAsset(absoluteUrl, "asset", MAX_FONT_BYTES) ?: return@replace match.value
            "url(\"$localPath\")"
        }
    }

    private fun downloadBinaryAsset(url: String, kind: String, maxBytes: Long): String? {
        assetPaths[url]?.let { return it }
        if (!canFetch(url) || totalAssetBytes >= MAX_TOTAL_ASSET_BYTES) return null
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "*/*")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body ?: return@runCatching null
                val bytes = readBytesLimited(body, maxBytes) ?: return@runCatching null
                val relativePath = nextAssetPath(url, response.header("Content-Type"), kind, "bin")
                val outputFile = File(assetsDir, relativePath.removePrefix("assets/"))
                outputFile.writeBytes(bytes)
                totalAssetBytes += bytes.size
                assetPaths[url] = relativePath
                relativePath
            }
        }.getOrNull()
    }

    private fun readBytesLimited(body: ResponseBody, maxBytes: Long): ByteArray? {
        val contentLength = body.contentLength()
        if (contentLength > maxBytes || contentLength > remainingAssetBudget()) return null
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        body.byteStream().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                total += read
                if (total > maxBytes || total > remainingAssetBudget()) return null
                output.write(buffer, 0, read)
            }
        }
        return output.toByteArray()
    }

    private fun remainingAssetBudget(): Long {
        return (MAX_TOTAL_ASSET_BYTES - totalAssetBytes).coerceAtLeast(0L)
    }

    private fun nextAssetPath(url: String, contentType: String?, kind: String, fallbackExtension: String): String {
        val extension = extensionFor(url, contentType, fallbackExtension)
        val safeKind = kind.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val name = "${safeKind}_${assetIndex++}.$extension"
        return "assets/$name"
    }

    private fun extensionFor(url: String, contentType: String?, fallback: String): String {
        val normalizedType = contentType?.substringBefore(";")?.trim()?.lowercase(Locale.US).orEmpty()
        val fromType = when (normalizedType) {
            "text/css" -> "css"
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/svg+xml" -> "svg"
            "font/woff2", "application/font-woff2" -> "woff2"
            "font/woff", "application/font-woff" -> "woff"
            "font/ttf", "application/x-font-ttf" -> "ttf"
            "font/otf", "application/x-font-otf" -> "otf"
            else -> null
        }
        if (fromType != null) return fromType
        val path = runCatching { URI(url).path }.getOrNull().orEmpty()
        val extension = path.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase(Locale.US)
            .takeIf { it.length in 2..5 && it.all { char -> char.isLetterOrDigit() } }
        return extension ?: fallback
    }

    private fun resolveUrl(baseUrl: String, rawUrl: String): String? {
        val trimmed = rawUrl.trim().trim('"', '\'')
        if (trimmed.isBlank()) return null
        val lowered = trimmed.lowercase(Locale.US)
        if (
            lowered.startsWith("data:") ||
            lowered.startsWith("blob:") ||
            lowered.startsWith("javascript:") ||
            lowered.startsWith("mailto:") ||
            lowered.startsWith("tel:") ||
            lowered.startsWith("#")
        ) {
            return null
        }
        return runCatching {
            if (trimmed.startsWith("//")) {
                val scheme = URI(baseUrl).scheme ?: "https"
                "$scheme:$trimmed"
            } else {
                URI(baseUrl).resolve(trimmed).toString()
            }
        }.getOrNull()
    }

    private fun canFetch(url: String): Boolean {
        val scheme = runCatching { URI(url).scheme?.lowercase(Locale.US) }.getOrNull()
        return scheme == "http" || scheme == "https"
    }

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Khoborakhobor/1.0 Mobile Safari/537.36"
        const val MAX_IMAGE_BYTES = 5L * 1024L * 1024L
        const val MAX_CSS_BYTES = 1024L * 1024L
        const val MAX_FONT_BYTES = 2L * 1024L * 1024L
        const val MAX_TOTAL_ASSET_BYTES = 32L * 1024L * 1024L
        val CSS_URL_REGEX = Regex("""url\(\s*(['"]?)([^'")]+)\1\s*\)""", RegexOption.IGNORE_CASE)
        val SCRIPT_SKIP_MARKERS = listOf(
            "googletagmanager",
            "google-analytics",
            "doubleclick",
            "adservice",
            "adsbygoogle",
            "facebook.net",
            "scorecardresearch",
            "analytics",
            "advert"
        )
    }
}

data class ArchivedOfflinePage(
    val rawHtmlPath: String,
    val archiveHtmlPath: String,
    val archiveDirPath: String
)
