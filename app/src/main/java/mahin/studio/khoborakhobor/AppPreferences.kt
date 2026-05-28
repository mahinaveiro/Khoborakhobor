package mahin.studio.khoborakhobor

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class AppPreferences(context: Context) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences("khoborakhobor_preferences", Context.MODE_PRIVATE)

    fun loadFavoriteIds(): Set<String> {
        return preferences.getStringSet(KEY_FAVORITES, emptySet()).orEmpty().toSet()
    }

    fun saveFavoriteIds(ids: Set<String>) {
        preferences.edit().putStringSet(KEY_FAVORITES, ids.toSet()).apply()
    }

    fun loadThemePreference(): ThemePreference {
        val rawValue = preferences.getString(KEY_THEME, ThemePreference.System.name)
        return ThemePreference.entries.firstOrNull { it.name == rawValue } ?: ThemePreference.System
    }

    fun saveThemePreference(themePreference: ThemePreference) {
        preferences.edit().putString(KEY_THEME, themePreference.name).apply()
    }

    fun loadDisableAds(): Boolean {
        return preferences.getBoolean(KEY_DISABLE_ADS, false)
    }

    fun saveDisableAds(disabled: Boolean) {
        preferences.edit().putBoolean(KEY_DISABLE_ADS, disabled).apply()
    }

    fun loadWebsiteDarkMode(): Boolean {
        return preferences.getBoolean(KEY_WEBSITE_DARK_MODE, false)
    }

    fun saveWebsiteDarkMode(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_WEBSITE_DARK_MODE, enabled).apply()
    }

    fun loadCustomSources(): List<NewsSource> {
        val jsonStr = preferences.getString(KEY_CUSTOM_SOURCES, null) ?: return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<NewsSource>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    NewsSource(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        category = obj.getString("category"),
                        language = "en",
                        url = obj.getString("url"),
                        country = "US",
                        type = "general",
                        iconUrl = SourceIconResolver.faviconUrl(obj.getString("url")),
                        isCustom = obj.optBoolean("isCustom", true),
                        createdAt = obj.optLong("createdAt", 0L)
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveCustomSources(sources: List<NewsSource>) {
        try {
            val array = JSONArray()
            for (source in sources) {
                val obj = JSONObject().apply {
                    put("id", source.id)
                    put("name", source.name)
                    put("url", source.url)
                    put("category", source.category)
                    put("isCustom", source.isCustom)
                    put("createdAt", source.createdAt)
                }
                array.put(obj)
            }
            preferences.edit().putString(KEY_CUSTOM_SOURCES, array.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isCommunityDialogDismissed(): Boolean {
        return preferences.getBoolean(KEY_COMMUNITY_DISMISSED, false)
    }

    fun setCommunityDialogDismissed(dismissed: Boolean) {
        preferences.edit().putBoolean(KEY_COMMUNITY_DISMISSED, dismissed).apply()
    }

    private companion object {
        const val KEY_FAVORITES = "favorite_source_ids"
        const val KEY_THEME = "theme_preference"
        const val KEY_DISABLE_ADS = "disable_ads"
        const val KEY_WEBSITE_DARK_MODE = "website_dark_mode"
        const val KEY_CUSTOM_SOURCES = "custom_sources_json"
        const val KEY_COMMUNITY_DISMISSED = "community_dialog_dismissed"
    }
}
