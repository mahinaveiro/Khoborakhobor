package mahin.studio.khoborakhobor

import java.net.URI

object SourceIconResolver {
    fun iconUrl(source: NewsSource): String? {
        return source.iconUrl.ifBlank {
            faviconUrl(source.url)
        }.ifBlank {
            null
        }
    }

    fun fallbackInitials(source: NewsSource): String {
        return source.name
            .split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifBlank { source.name.take(1).uppercase() }
    }

    fun faviconUrl(sourceUrl: String): String {
        val host = runCatching { URI(sourceUrl).host }
            .getOrNull()
            ?.lowercase()
            ?.removePrefix("www.")
            ?: return ""
        return "https://www.google.com/s2/favicons?domain=$host&sz=64"
    }
}
