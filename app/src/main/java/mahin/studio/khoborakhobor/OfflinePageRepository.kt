package mahin.studio.khoborakhobor

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class OfflinePageRepository(context: Context) {
    private val pagesDir = File(context.filesDir, "offline_pages")
    private val metadataFile = File(pagesDir, "metadata.json")

    @Synchronized
    fun loadPages(): List<OfflinePage> {
        if (!metadataFile.exists()) {
            Log.i(TAG, "Offline metadata loaded")
            return emptyList()
        }

        return runCatching {
            val items = JSONArray(metadataFile.readText())
            buildList {
                for (index in 0 until items.length()) {
                    val item = items.getJSONObject(index)
                    val page = OfflinePage(
                        id = item.getString("id"),
                        title = item.getString("title"),
                        sourceName = item.getString("sourceName"),
                        sourceId = item.getString("sourceId"),
                        sourceUrl = item.getString("sourceUrl"),
                        originalPageUrl = item.getString("originalPageUrl"),
                        iconUrl = item.optString("iconUrl"),
                        savedAt = item.getLong("savedAt"),
                        localHtmlPath = item.getString("localHtmlPath")
                    )
                    if (File(page.localHtmlPath).exists()) {
                        add(page)
                    }
                }
            }.sortedByDescending { it.savedAt }
        }.onSuccess {
            Log.i(TAG, "Offline metadata loaded")
        }.onFailure { error ->
            Log.e(TAG, "Offline metadata load failed", error)
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun savePage(source: NewsSource, snapshot: PageSnapshot): Result<OfflinePage> {
        return runCatching {
            pagesDir.mkdirs()

            val savedAt = snapshot.savedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
            val id = "page_${savedAt}_${UUID.randomUUID().toString().take(8)}"
            val title = cleanTitle(snapshot.title).ifBlank {
                "${source.name} ${fallbackDate(savedAt)}"
            }
            val htmlFile = File(pagesDir, "$id.html")
            htmlFile.writeText(normalizeHtml(snapshot.html, snapshot.url), Charsets.UTF_8)

            val page = OfflinePage(
                id = id,
                title = title,
                sourceName = source.name,
                sourceId = source.id,
                sourceUrl = source.url,
                originalPageUrl = snapshot.url.ifBlank { source.url },
                iconUrl = source.iconUrl,
                savedAt = savedAt,
                localHtmlPath = htmlFile.absolutePath
            )

            writeMetadata((loadPages().filterNot { it.id == id } + page).sortedByDescending { it.savedAt })
            page
        }
    }

    @Synchronized
    fun deletePage(page: OfflinePage): Boolean {
        val remaining = loadPages().filterNot { it.id == page.id }
        val htmlDeleted = runCatching {
            val file = File(page.localHtmlPath)
            !file.exists() || file.delete()
        }.getOrDefault(false)

        writeMetadata(remaining)
        return htmlDeleted
    }

    private fun writeMetadata(pages: List<OfflinePage>) {
        pagesDir.mkdirs()
        val items = JSONArray()
        pages.forEach { page ->
            items.put(
                JSONObject()
                    .put("id", page.id)
                    .put("title", page.title)
                    .put("sourceName", page.sourceName)
                    .put("sourceId", page.sourceId)
                    .put("sourceUrl", page.sourceUrl)
                    .put("originalPageUrl", page.originalPageUrl)
                    .put("iconUrl", page.iconUrl)
                    .put("savedAt", page.savedAt)
                    .put("localHtmlPath", page.localHtmlPath)
            )
        }
        metadataFile.writeText(items.toString(), Charsets.UTF_8)
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

    private fun htmlWithBase(html: String, originalUrl: String): String {
        val base = """<base href="${escapeHtmlAttribute(originalUrl)}">"""
        val headIndex = Regex("<head[^>]*>", RegexOption.IGNORE_CASE).find(html)?.range?.last
        return if (headIndex != null) {
            html.substring(0, headIndex + 1) + base + html.substring(headIndex + 1)
        } else {
            "<!doctype html><html><head>$base<meta charset=\"utf-8\"></head><body>$html</body></html>"
        }
    }

    private fun normalizeHtml(html: String, originalUrl: String): String {
        return if (html.contains("<base ", ignoreCase = true)) {
            html
        } else {
            htmlWithBase(html, originalUrl)
        }
    }

    private fun escapeHtmlAttribute(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    private companion object {
        const val TAG = "OfflinePageRepository"
    }
}
