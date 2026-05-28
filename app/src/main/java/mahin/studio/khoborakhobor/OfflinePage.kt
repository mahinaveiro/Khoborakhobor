package mahin.studio.khoborakhobor

import androidx.compose.runtime.Immutable

@Immutable
data class OfflinePage(
    val id: String,
    val title: String,
    val sourceName: String,
    val sourceId: String,
    val sourceUrl: String,
    val originalUrl: String,
    val savedAt: Long,
    val iconUrl: String,
    val rawHtmlPath: String,
    val cleanHtmlPath: String? = null
) {
    val originalPageUrl: String
        get() = originalUrl

    val localHtmlPath: String
        get() = rawHtmlPath
}

data class PageSnapshot(
    val title: String,
    val url: String,
    val rawHtml: String,
    val savedAt: Long
)
