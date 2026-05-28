package mahin.studio.khoborakhobor

import android.content.Context
import org.json.JSONArray

object NewsSourceRepository {
    @Volatile
    private var cachedSources: List<NewsSource>? = null

    fun load(context: Context): List<NewsSource> {
        cachedSources?.let { return it }

        return synchronized(this) {
            cachedSources ?: PerformanceLogger.trace("loadSources") {
                BackgroundThreadGuard.requireBackgroundThread("loadSources")
                parseSources(context.assets.open("news_sources.json").bufferedReader().use { it.readText() })
            }.also { sources ->
                cachedSources = sources
            }
        }
    }

    internal fun loadForTest(readJson: () -> String): List<NewsSource> {
        cachedSources?.let { return it }

        return synchronized(this) {
            cachedSources ?: parseSources(readJson()).also { sources ->
                cachedSources = sources
            }
        }
    }

    internal fun clearCacheForTest() {
        cachedSources = null
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
