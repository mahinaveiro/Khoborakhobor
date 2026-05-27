package mahin.studio.khoborakhobor

import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONArray

object NewsSourceRepository {
    @Volatile
    private var cachedSources: List<NewsSource>? = null

    fun load(context: Context): List<NewsSource> {
        cachedSources?.let { return it }

        return synchronized(this) {
            cachedSources ?: parseSources(context).also { sources ->
                cachedSources = sources
                Log.i(TAG, "Sources JSON loaded")
            }
        }
    }

    private fun parseSources(context: Context): List<NewsSource> {
        val json = context.assets.open("news_sources.json").bufferedReader().use { it.readText() }
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
                            faviconUrl(item.getString("url"))
                        }
                    )
                )
            }
        }
    }

    private fun faviconUrl(sourceUrl: String): String {
        val uri = Uri.parse(sourceUrl)
        val scheme = uri.scheme ?: "https"
        val host = uri.host ?: return "$sourceUrl/favicon.ico"
        return "$scheme://$host/favicon.ico"
    }

    private const val TAG = "NewsSourceRepository"
}
