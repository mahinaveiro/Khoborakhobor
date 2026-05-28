package mahin.studio.khoborakhobor

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class OfflineMetadataStore(
    private val pagesDir: File
) {
    private val metadataFile = File(pagesDir, "metadata.json")

    @Synchronized
    fun loadPages(): List<OfflinePage> {
        if (!metadataFile.exists()) return emptyList()

        val items = JSONArray(metadataFile.readText())
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                val rawHtmlPath = item.optString("rawHtmlPath")
                    .ifBlank { item.optString("localHtmlPath") }
                val originalUrl = item.optString("originalUrl")
                    .ifBlank { item.optString("originalPageUrl") }
                val cleanHtmlPath = item.opt("cleanHtmlPath")
                    ?.takeIf { it != JSONObject.NULL }
                    ?.toString()
                    ?.ifBlank { null }
                val page = OfflinePage(
                    id = item.getString("id"),
                    title = item.getString("title"),
                    sourceName = item.getString("sourceName"),
                    sourceId = item.getString("sourceId"),
                    sourceUrl = item.getString("sourceUrl"),
                    originalUrl = originalUrl,
                    savedAt = item.getLong("savedAt"),
                    iconUrl = item.optString("iconUrl"),
                    rawHtmlPath = rawHtmlPath,
                    cleanHtmlPath = cleanHtmlPath
                )
                if (rawHtmlPath.isNotBlank() && File(rawHtmlPath).exists()) {
                    add(page)
                }
            }
        }.sortedByDescending { it.savedAt }
    }

    @Synchronized
    fun upsertPage(page: OfflinePage): OfflinePage {
        val pages = (loadPages().filterNot { it.id == page.id } + page).sortedByDescending { it.savedAt }
        writePages(pages)
        return page
    }

    @Synchronized
    fun deletePage(page: OfflinePage): Boolean {
        val remaining = loadPages().filterNot { it.id == page.id }
        val rawDeleted = deleteIfExists(page.rawHtmlPath)
        val cleanDeleted = page.cleanHtmlPath?.let { deleteIfExists(it) } ?: true
        writePages(remaining)
        return rawDeleted && cleanDeleted
    }

    @Synchronized
    fun writePages(pages: List<OfflinePage>) {
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
                    .put("originalUrl", page.originalUrl)
                    .put("savedAt", page.savedAt)
                    .put("iconUrl", page.iconUrl)
                    .put("rawHtmlPath", page.rawHtmlPath)
                    .put("cleanHtmlPath", page.cleanHtmlPath ?: JSONObject.NULL)
            )
        }
        metadataFile.writeText(items.toString(), Charsets.UTF_8)
    }

    private fun deleteIfExists(path: String): Boolean {
        val file = File(path)
        return !file.exists() || file.delete()
    }
}
