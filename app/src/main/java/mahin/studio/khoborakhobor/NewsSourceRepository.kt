package mahin.studio.khoborakhobor

import android.content.Context
import org.json.JSONArray

object NewsSourceRepository {
    @Volatile
    private var cachedDefaultSources: List<NewsSource>? = null

    fun load(context: Context, customSources: List<NewsSource> = emptyList()): List<NewsSource> {
        val defaults = getDefaultSources(context)
        return defaults + customSources
    }

    private fun getDefaultSources(context: Context): List<NewsSource> {
        cachedDefaultSources?.let { return it }

        return synchronized(this) {
            cachedDefaultSources ?: PerformanceLogger.trace("loadDefaultSources") {
                BackgroundThreadGuard.requireBackgroundThread("loadDefaultSources")
                parseSources(context.assets.open("news_sources.json").bufferedReader().use { it.readText() })
            }.also { sources ->
                cachedDefaultSources = sources
            }
        }
    }

    internal fun loadForTest(readJson: () -> String): List<NewsSource> {
        cachedDefaultSources?.let { return it }

        return synchronized(this) {
            cachedDefaultSources ?: parseSources(readJson()).also { sources ->
                cachedDefaultSources = sources
            }
        }
    }

    internal fun clearCacheForTest() {
        cachedDefaultSources = null
    }

    fun normalizeUrl(urlStr: String): String {
        var url = urlStr.trim()
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            url = "https://$url"
        }
        return try {
            val uri = java.net.URI(url)
            val scheme = uri.scheme?.lowercase() ?: "https"
            val host = uri.host?.lowercase() ?: ""
            var path = uri.path ?: ""
            if (path.endsWith("/")) {
                path = path.substring(0, path.length - 1)
            }
            val query = uri.query?.let { "?$it" } ?: ""
            "$scheme://$host$path$query"
        } catch (e: Exception) {
            var normalized = url.lowercase().trim()
            if (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length - 1)
            }
            normalized
        }
    }

    fun extractDomain(urlStr: String): String {
        val normalized = normalizeUrl(urlStr)
        return try {
            java.net.URI(normalized).host?.lowercase()?.removePrefix("www.") ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    fun isDuplicateSource(url: String, existingSources: List<NewsSource>): NewsSource? {
        val normUrl = normalizeUrl(url)
        val dom = extractDomain(url)
        return existingSources.firstOrNull { existing ->
            val existingNorm = normalizeUrl(existing.url)
            val existingDom = extractDomain(existing.url)
            (normUrl == existingNorm) || (dom.isNotEmpty() && dom == existingDom)
        }
    }

    private fun parseSources(json: String): List<NewsSource> {
        val items = JSONArray(json)
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                add(
                    NewsSource(
                        id = item.getString("id"),
                        name = item.getString("name"),
                        category = item.getString("category"),
                        language = item.getString("language"),
                        url = item.getString("url"),
                        country = item.getString("country"),
                        type = item.getString("type"),
                        iconUrl = item.optString("iconUrl").ifBlank {
                            SourceIconResolver.faviconUrl(item.getString("url"))
                        }
                    )
                )
            }
        }
    }
}
