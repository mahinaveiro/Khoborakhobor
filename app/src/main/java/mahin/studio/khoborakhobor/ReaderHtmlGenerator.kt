package mahin.studio.khoborakhobor

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReaderHtmlGenerator {
    fun extractTitle(rawHtml: String, originalUrl: String): String {
        val doc = Jsoup.parse(rawHtml, originalUrl)
        return cleanTitle(
            listOf(
                doc.selectFirst("meta[property=og:title]")?.attr("content"),
                doc.selectFirst("title")?.text(),
                doc.selectFirst("h1")?.text(),
                doc.title()
            ).firstOrNull { !it.isNullOrBlank() }.orEmpty()
        )
    }

    fun generate(
        rawHtml: String,
        originalUrl: String,
        title: String,
        sourceName: String,
        darkMode: Boolean,
        savedAt: Long? = null
    ): String {
        val doc = Jsoup.parse(rawHtml, originalUrl)
        doc.outputSettings().prettyPrint(false)
        removeNoisyElements(doc)
        prepareMedia(doc)

        val resolvedTitle = cleanTitle(title).ifBlank { extractTitle(rawHtml, originalUrl) }
        val contentRoot = selectContentRoot(doc)
        val content = contentRoot.clone()
        content.select("script,noscript,template,style").remove()
        absolutizeUrls(content)

        return renderReaderPage(
            title = resolvedTitle.ifBlank { sourceName },
            originalUrl = originalUrl,
            sourceName = sourceName,
            contentHtml = content.html().ifBlank { "<p>${escapeHtml(doc.text())}</p>" },
            darkMode = darkMode,
            savedAt = savedAt
        )
    }

    private fun removeNoisyElements(doc: Document) {
        doc.select("script,noscript,template").remove()
        doc.select("iframe").forEach { iframe ->
            val marker = "${iframe.id()} ${iframe.className()} ${iframe.attr("src")}".lowercase()
            if (AD_MARKERS.any { marker.contains(it) }) {
                iframe.remove()
            }
        }
    }

    private fun selectContentRoot(doc: Document): Element {
        val body = doc.body()
        val selected = CONTENT_SELECTORS
            .asSequence()
            .mapNotNull { selector -> doc.selectFirst(selector) }
            .firstOrNull { element -> hasEnoughContent(element) }
        return selected ?: body
    }

    private fun hasEnoughContent(element: Element): Boolean {
        return element.text().trim().length >= MIN_CONTENT_TEXT_LENGTH &&
            element.select("img,picture,figure,video").isNotEmpty()
    }

    private fun prepareMedia(root: Element) {
        root.select("img").forEach { image ->
            val currentSrc = image.attr("src").trim()
            val promoted = listOf("data-src", "data-original", "data-lazy-src", "data-actualsrc")
                .firstNotNullOfOrNull { attr ->
                    image.attr(attr).takeIf { it.isNotBlank() }
                }
            if ((currentSrc.isBlank() || currentSrc.startsWith("data:image/", ignoreCase = true)) && promoted != null) {
                image.attr("src", promoted)
            }
            image.attr("style", appendMediaStyle(image.attr("style")))
        }
        root.select("video").forEach { video ->
            video.attr("style", appendMediaStyle(video.attr("style")))
        }
    }

    private fun absolutizeUrls(root: Element) {
        root.select("a[href]").forEach { element ->
            element.absUrl("href").takeIf { it.isNotBlank() }?.let { element.attr("href", it) }
        }
        root.select("img[src],video[src],source[src]").forEach { element ->
            element.absUrl("src").takeIf { it.isNotBlank() }?.let { element.attr("src", it) }
        }
        root.select("img[srcset],source[srcset]").forEach { element ->
            absolutizeSrcSet(element)?.let { element.attr("srcset", it) }
        }
        root.select("video[poster]").forEach { element ->
            element.absUrl("poster").takeIf { it.isNotBlank() }?.let { element.attr("poster", it) }
        }
    }

    private fun absolutizeSrcSet(element: Element): String? {
        val srcSet = element.attr("srcset").trim()
        if (srcSet.isBlank()) return null
        return srcSet.split(",")
            .joinToString(", ") { candidate ->
                val parts = candidate.trim().split(Regex("\\s+"), limit = 2)
                val rawUrl = parts.firstOrNull().orEmpty()
                if (rawUrl.isBlank()) {
                    candidate.trim()
                } else {
                    element.attr("srcset_tmp", rawUrl)
                    val absolute = element.absUrl("srcset_tmp").ifBlank { rawUrl }
                    element.removeAttr("srcset_tmp")
                    if (parts.size == 2) "$absolute ${parts[1]}" else absolute
                }
            }
    }

    private fun appendMediaStyle(existing: String): String {
        val trimmed = existing.trim().trimEnd(';')
        val parts = buildList {
            if (trimmed.isNotBlank()) add(trimmed)
            if (!trimmed.contains(Regex("(^|;)\\s*max-width\\s*:", RegexOption.IGNORE_CASE))) add("max-width:100%")
            if (!trimmed.contains(Regex("(^|;)\\s*height\\s*:", RegexOption.IGNORE_CASE))) add("height:auto")
        }
        return parts.joinToString(";").trimEnd(';') + ";"
    }

    private fun renderReaderPage(
        title: String,
        originalUrl: String,
        sourceName: String,
        contentHtml: String,
        darkMode: Boolean,
        savedAt: Long?
    ): String {
        val savedLine = savedAt?.let {
            "<p class=\"meta\">Saved ${escapeHtml(fullDate(it))}</p>"
        }.orEmpty()
        val css = if (darkMode) DARK_READER_CSS else LIGHT_READER_CSS

        return """
            <!doctype html>
            <html lang="bn">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <base href="${escapeHtmlAttribute(originalUrl)}">
              <title>${escapeHtml(title)}</title>
              <style>$css</style>
            </head>
            <body>
              <article class="reader-shell">
                <header class="reader-header">
                  <p class="meta source">${escapeHtml(sourceName)}</p>
                  <h1>${escapeHtml(title)}</h1>
                  <p class="meta"><a href="${escapeHtmlAttribute(originalUrl)}">${escapeHtml(originalUrl)}</a></p>
                  $savedLine
                </header>
                <main class="reader-content">
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

    private val CONTENT_SELECTORS = listOf(
        "article",
        "main",
        "[role=main]",
        ".article",
        ".post",
        ".news-details",
        ".entry-content",
        ".content",
        "body"
    )

    private val AD_MARKERS = listOf(
        "advert",
        "advertisement",
        " ad-",
        "-ad",
        " ads",
        "sponsor",
        "promo"
    )

    private const val MIN_CONTENT_TEXT_LENGTH = 160

    private val LIGHT_READER_CSS = """
        :root{color-scheme:light}
        *{box-sizing:border-box}
        html,body{margin:0;background:#FAF9F5;color:#111111}
        body{font-family:system-ui,-apple-system,BlinkMacSystemFont,"Noto Sans Bengali","Noto Serif Bengali","SolaimanLipi",sans-serif;line-height:1.72}
        .reader-shell{max-width:760px;margin:0 auto;min-height:100vh;padding:20px;background:#FFFFFF}
        .reader-header{border-bottom:1px solid rgba(0,0,0,.12);margin-bottom:18px;padding-bottom:14px}
        h1{font-size:1.85rem;line-height:1.28;margin:8px 0 14px;color:#111111}
        h2,h3,h4,h5,h6{line-height:1.35;color:#111111}
        p,li{font-size:1.05rem}
        .meta{color:#616161;font-size:.9rem;margin:4px 0;word-break:break-word}
        a{color:#111111;text-decoration:underline;text-underline-offset:3px}
        img,picture,video{max-width:100%;height:auto}
        figure{margin:18px 0}
        figcaption{color:#616161;font-size:.9rem}
        blockquote{border-left:3px solid rgba(0,0,0,.2);margin:18px 0;padding-left:14px}
        table{max-width:100%;border-collapse:collapse;display:block;overflow-x:auto}
        td,th{border:1px solid rgba(0,0,0,.14);padding:8px}
    """.trimIndent()

    private val DARK_READER_CSS = """
        :root{color-scheme:dark}
        *{box-sizing:border-box}
        html,body{margin:0;background:#0B0B0B;color:#F2F2F2}
        body{font-family:system-ui,-apple-system,BlinkMacSystemFont,"Noto Sans Bengali","Noto Serif Bengali","SolaimanLipi",sans-serif;line-height:1.72}
        .reader-shell{max-width:760px;margin:0 auto;min-height:100vh;padding:20px;background:#111111}
        .reader-header{border-bottom:1px solid rgba(255,255,255,.14);margin-bottom:18px;padding-bottom:14px}
        h1{font-size:1.85rem;line-height:1.28;margin:8px 0 14px;color:#F2F2F2}
        h2,h3,h4,h5,h6{line-height:1.35;color:#F2F2F2}
        p,li{font-size:1.05rem}
        .meta{color:#B8B8B8;font-size:.9rem;margin:4px 0;word-break:break-word}
        a{color:#F5F5F5;text-decoration:underline;text-underline-offset:3px}
        img,picture,video{max-width:100%;height:auto}
        figure{margin:18px 0}
        figcaption{color:#B8B8B8;font-size:.9rem}
        blockquote{border-left:3px solid rgba(255,255,255,.22);margin:18px 0;padding-left:14px;color:#F2F2F2}
        table{max-width:100%;border-collapse:collapse;display:block;overflow-x:auto}
        td,th{border:1px solid rgba(255,255,255,.14);padding:8px}
    """.trimIndent()
}
