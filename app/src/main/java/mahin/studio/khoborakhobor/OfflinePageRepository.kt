package mahin.studio.khoborakhobor

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class OfflinePageRepository(context: Context) {
    private val pagesDir = File(context.filesDir, "offline_pages")
    private val metadataStore = OfflineMetadataStore(pagesDir)
    private val archiver = OfflinePageArchiver(pagesDir)

    @Synchronized
    fun loadPages(): List<OfflinePage> {
        BackgroundThreadGuard.requireBackgroundThread("loadOfflineMetadata")
        return PerformanceLogger.trace("loadOfflineMetadata") {
            metadataStore.loadPages()
        }
    }

    @Synchronized
    fun savePage(source: NewsSource, snapshot: PageSnapshot): Result<OfflinePage> {
        return runCatching {
            BackgroundThreadGuard.requireBackgroundThread("saveOfflinePage")
            PerformanceLogger.trace("writeOfflinePage") {
                pagesDir.mkdirs()

                val savedAt = snapshot.savedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
                val id = "page_${savedAt}_${UUID.randomUUID().toString().take(8)}"
                val archivedPage = archiver.archive(id, source, snapshot.copy(savedAt = savedAt))
                val title = cleanTitle(snapshot.title).ifBlank {
                    "${source.name} ${fallbackDate(savedAt)}"
                }

                val page = OfflinePage(
                    id = id,
                    title = title,
                    sourceName = source.name,
                    sourceId = source.id,
                    sourceUrl = source.url,
                    originalUrl = snapshot.url.ifBlank { source.url },
                    savedAt = savedAt,
                    iconUrl = source.iconUrl,
                    rawHtmlPath = archivedPage.rawHtmlPath,
                    cleanHtmlPath = null,
                    archiveHtmlPath = archivedPage.archiveHtmlPath,
                    archiveDirPath = archivedPage.archiveDirPath
                )

                metadataStore.upsertPage(page)
            }
        }
    }

    @Synchronized
    fun deletePage(page: OfflinePage): Boolean {
        BackgroundThreadGuard.requireBackgroundThread("deleteOfflinePage")
        return metadataStore.deletePage(page)
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
}
