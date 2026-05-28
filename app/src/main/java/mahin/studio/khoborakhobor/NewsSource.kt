package mahin.studio.khoborakhobor

import androidx.compose.runtime.Immutable

@Immutable
data class NewsSource(
    val id: String,
    val name: String,
    val category: String,
    val language: String,
    val url: String,
    val country: String,
    val type: String,
    val iconUrl: String,
    val isCustom: Boolean = false,
    val createdAt: Long = 0L
)

enum class ThemePreference {
    System,
    Light,
    Dark
}
