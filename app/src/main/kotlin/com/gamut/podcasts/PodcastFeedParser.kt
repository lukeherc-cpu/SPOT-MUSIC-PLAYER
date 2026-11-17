package com.gamut.podcasts

import java.io.ByteArrayInputStream
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** RSS enclosures only: no embedded players, scripts, external XML entities or page scraping. */
object PodcastFeedParser {
    const val MAX_BYTES = 4_000_000
    fun parse(bytes: ByteArray, feedUrl: String): List<PodcastEpisode> {
        require(bytes.size <= MAX_BYTES) { "Podcast feed is too large" }
        require(!bytes.toString(Charsets.UTF_8).replace("\u0000", "").contains("<!DOCTYPE", ignoreCase = true)) { "Unsupported podcast feed" }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // Android's Harmony DOM does not expose these SAX switches. The mandatory
            // DOCTYPE rejection above and throwing EntityResolver below apply on every runtime.
            for (feature in listOf("http://xml.org/sax/features/external-general-entities", "http://xml.org/sax/features/external-parameter-entities")) {
                try { setFeature(feature, false) } catch (_: javax.xml.parsers.ParserConfigurationException) { }
            }
            isExpandEntityReferences = false
        }
        val doc = factory.newDocumentBuilder().apply {
            setEntityResolver { _, _ -> throw IllegalArgumentException("External XML entities are unsupported") }
        }.parse(ByteArrayInputStream(bytes))
        val nodes = doc.getElementsByTagName("item")
        return (0 until minOf(nodes.length, 500)).mapNotNull { i ->
            val item = nodes.item(i) as? Element ?: return@mapNotNull null
            val enclosure = item.getElementsByTagName("enclosure").item(0) as? Element ?: return@mapNotNull null
            val type = enclosure.getAttribute("type").lowercase()
            if (type.isNotBlank() && !type.startsWith("audio/") && type != "application/ogg") return@mapNotNull null
            val url = httpsUrl(enclosure.getAttribute("url"), feedUrl) ?: return@mapNotNull null
            val image = item.getElementsByTagNameNS("*", "image").item(0) as? Element
            PodcastEpisode(
                id = item.text("guid").ifBlank { url }, title = item.text("title").ifBlank { "Untitled episode" },
                audioUrl = url, description = item.text("description").replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim().take(2000),
                published = item.text("pubDate"), durationMs = durationMs(item.text("duration")),
                artwork = image?.getAttribute("href")?.let { httpsUrl(it, feedUrl) },
            )
        }.distinctBy { it.id }
    }
    private fun Element.text(name: String): String = (getElementsByTagNameNS("*", name).item(0)?.textContent ?: getElementsByTagName(name).item(0)?.textContent).orEmpty().trim()
    internal fun durationMs(raw: String): Long {
        val parts = raw.trim().split(':').map { it.toLongOrNull() ?: return 0L }
        if (parts.size !in 1..3 || parts.any { it < 0 || it > 604800 }) return 0L
        return parts.fold(0L) { total, n -> total * 60 + n }.times(1000).coerceAtMost(7 * 24 * 60 * 60_000L)
    }
    internal fun httpsUrl(raw: String, base: String): String? = runCatching {
        URI(base).resolve(raw.trim()).takeIf { it.scheme == "https" && !it.host.isNullOrBlank() && it.userInfo == null }?.toASCIIString()
    }.getOrNull()
}
