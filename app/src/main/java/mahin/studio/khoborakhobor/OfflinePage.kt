package mahin.studio.khoborakhobor

import androidx.compose.runtime.Immutable

@Immutable
data class OfflinePage(
    val id: String,
    val title: String,
    val sourceName: String,
    val sourceId: String,
    val sourceUrl: String,
    val originalPageUrl: String,
    val iconUrl: String,
    val savedAt: Long,
    val localHtmlPath: String
)

data class PageSnapshot(
    val title: String,
    val url: String,
    val html: String,
    val savedAt: Long
)
