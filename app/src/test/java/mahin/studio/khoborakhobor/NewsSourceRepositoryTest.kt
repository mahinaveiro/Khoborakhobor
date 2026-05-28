package mahin.studio.khoborakhobor

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class NewsSourceRepositoryTest {
    @After
    fun tearDown() {
        NewsSourceRepository.clearCacheForTest()
    }

    @Test
    fun sourceJsonLoadsOnceThroughRepositoryCache() {
        var loadCount = 0
        val first = NewsSourceRepository.loadForTest {
            loadCount += 1
            sourceJson()
        }
        val second = NewsSourceRepository.loadForTest {
            loadCount += 1
            sourceJson()
        }

        assertEquals(1, loadCount)
        assertEquals(first, second)
        assertEquals("https://www.google.com/s2/favicons?domain=example.com&sz=64", first.single().iconUrl)
    }

    private fun sourceJson(): String {
        return """
            [
              {
                "id": "example",
                "name": "Example News",
                "category": "Bangla",
                "language": "bn",
                "url": "https://www.example.com",
                "country": "BD",
                "type": "newspaper"
              }
            ]
        """.trimIndent()
    }
}
