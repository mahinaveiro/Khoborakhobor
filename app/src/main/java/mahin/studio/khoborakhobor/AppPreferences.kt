package mahin.studio.khoborakhobor

import android.content.Context
import android.content.SharedPreferences

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

    private companion object {
        const val KEY_FAVORITES = "favorite_source_ids"
        const val KEY_THEME = "theme_preference"
        const val KEY_DISABLE_ADS = "disable_ads"
        const val KEY_WEBSITE_DARK_MODE = "website_dark_mode"
    }
}
