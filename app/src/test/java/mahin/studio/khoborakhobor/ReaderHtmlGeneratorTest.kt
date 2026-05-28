package mahin.studio.khoborakhobor

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderHtmlGeneratorTest {
    @Test
    fun preservesImagesAddsBaseAndPromotesLazySource() {
        val rawHtml = """
            <html>
            <head><meta property="og:title" content="Story Title"></head>
            <body>
              <article>
                <h1>Story Title</h1>
                <p>${longText()}</p>
                <figure>
                  <img data-src="/images/photo.jpg" alt="Photo">
                  <figcaption>Caption</figcaption>
                </figure>
              </article>
            </body>
            </html>
        """.trimIndent()

        val readerHtml = ReaderHtmlGenerator.generate(
            rawHtml = rawHtml,
            originalUrl = "https://example.com/news/story",
            title = "",
            sourceName = "Example",
            darkMode = true
        )
        val doc = Jsoup.parse(readerHtml, "https://example.com/news/story")
        val image = doc.selectFirst("img")

        assertEquals("https://example.com/news/story", doc.selectFirst("base")?.attr("href"))
        assertEquals("https://example.com/images/photo.jpg", image?.attr("src"))
        assertTrue(image?.attr("style").orEmpty().contains("max-width:100%"))
        assertFalse(readerHtml.contains("filter:", ignoreCase = true))
        assertFalse(readerHtml.contains("display:none", ignoreCase = true))
    }

    @Test
    fun usesBodyFallbackWhenArticleExtractionIsWeak() {
        val rawHtml = """
            <html>
            <body>
              <article><p>Short</p></article>
              <section class="content">
                <h1>Fallback Headline</h1>
                <p>${longText()}</p>
                <img src="/fallback.jpg">
              </section>
            </body>
            </html>
        """.trimIndent()

        val readerHtml = ReaderHtmlGenerator.generate(
            rawHtml = rawHtml,
            originalUrl = "https://example.com/story",
            title = "Fallback Headline",
            sourceName = "Example",
            darkMode = false
        )

        assertTrue(readerHtml.contains("Fallback Headline"))
        assertTrue(readerHtml.contains("https://example.com/fallback.jpg"))
    }

    @Test
    fun themeChangesGeneratedOutputWithoutMutatingRawHtml() {
        val rawHtml = """
            <html><body><article><h1>Title</h1><p>${longText()}</p><img src="/a.jpg"></article></body></html>
        """.trimIndent()

        val light = ReaderHtmlGenerator.generate(
            rawHtml = rawHtml,
            originalUrl = "https://example.com/a",
            title = "Title",
            sourceName = "Example",
            darkMode = false
        )
        val dark = ReaderHtmlGenerator.generate(
            rawHtml = rawHtml,
            originalUrl = "https://example.com/a",
            title = "Title",
            sourceName = "Example",
            darkMode = true
        )

        assertNotEquals(light, dark)
        assertTrue(light.contains("#FAF9F5"))
        assertTrue(dark.contains("#0B0B0B"))
        assertFalse(rawHtml.contains("#0B0B0B"))
    }

    private fun longText(): String {
        return "This story has enough readable article text for the reader selection fallback checks. ".repeat(8)
    }
}
