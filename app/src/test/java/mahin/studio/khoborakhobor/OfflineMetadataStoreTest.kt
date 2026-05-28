package mahin.studio.khoborakhobor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OfflineMetadataStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun saveLoadAndDeleteOfflineMetadata() {
        val pagesDir = temporaryFolder.newFolder("offline_pages")
        val rawFile = temporaryFolder.newFile("raw.html")
        rawFile.writeText("<html><body>Original page</body></html>")
        val store = OfflineMetadataStore(pagesDir)
        val page = OfflinePage(
            id = "page_1",
            title = "Original Title",
            sourceName = "Daily Test",
            sourceId = "daily-test",
            sourceUrl = "https://example.com",
            originalUrl = "https://example.com/story",
            savedAt = 10L,
            iconUrl = "",
            rawHtmlPath = rawFile.absolutePath,
            cleanHtmlPath = null
        )

        store.upsertPage(page)

        val loaded = store.loadPages()
        assertEquals(listOf(page), loaded)
        assertTrue(rawFile.exists())

        assertTrue(store.deletePage(page))
        assertFalse(rawFile.exists())
        assertTrue(store.loadPages().isEmpty())
    }

    @Test
    fun readerThemeGenerationDoesNotMutateRawHtmlFile() {
        val rawFile = temporaryFolder.newFile("raw-reader.html")
        val rawHtml = """
            <html><body><article><h1>Title</h1><p>${"Readable text ".repeat(30)}</p><img src="/a.jpg"></article></body></html>
        """.trimIndent()
        rawFile.writeText(rawHtml)

        ReaderHtmlGenerator.generate(
            rawHtml = rawFile.readText(),
            originalUrl = "https://example.com/story",
            title = "Title",
            sourceName = "Example",
            darkMode = true
        )

        assertEquals(rawHtml, rawFile.readText())
        assertFalse(rawFile.readText().contains("#0B0B0B"))
    }
}
