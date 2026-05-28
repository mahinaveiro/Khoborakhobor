package mahin.studio.khoborakhobor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceIconResolverTest {
    @Test
    fun usesExplicitIconUrlWhenPresent() {
        val source = source(iconUrl = "https://example.com/icon.png")

        assertEquals("https://example.com/icon.png", SourceIconResolver.iconUrl(source))
    }

    @Test
    fun derivesSmallGoogleFaviconWhenIconUrlMissing() {
        val source = source(iconUrl = "", url = "https://www.example.com/news")

        assertEquals(
            "https://www.google.com/s2/favicons?domain=example.com&sz=64",
            SourceIconResolver.iconUrl(source)
        )
    }

    @Test
    fun fallsBackToInitialsWhenIconCannotBeResolved() {
        val source = source(iconUrl = "", url = "not a url", name = "Daily Test")

        assertNull(SourceIconResolver.iconUrl(source))
        assertEquals("DT", SourceIconResolver.fallbackInitials(source))
    }

    private fun source(
        iconUrl: String,
        url: String = "https://example.com",
        name: String = "Example News"
    ): NewsSource {
        return NewsSource(
            id = "example",
            name = name,
            category = "Bangla",
            language = "bn",
            url = url,
            country = "BD",
            type = "newspaper",
            iconUrl = iconUrl
        )
    }
}
