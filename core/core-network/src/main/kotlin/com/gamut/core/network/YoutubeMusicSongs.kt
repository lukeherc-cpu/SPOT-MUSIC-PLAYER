package com.gamut.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL

/** A YouTube Music song result. [explicit] is YouTube Music's own explicit badge. */
data class MusicSong(val videoId: String, val title: String, val artist: String, val durationMs: Long, val explicit: Boolean)

/**
 * YouTube Music song search. Labels upload both the explicit and the clean master of a release, and
 * YouTube Music badges the explicit one, so a result without the badge is the label's clean version.
 */
object YoutubeMusicSongs {
    private const val SONGS = "EgWKAQIIAWoMEA4QChADEAQQCRAF"
    private fun JsonElement?.obj(key: String): JsonElement? = (this as? JsonObject)?.get(key)
    private fun runs(text: JsonElement?): List<JsonObject> = (text.obj("runs") as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
    private fun JsonObject.text() = (this["text"] as? JsonPrimitive)?.contentOrNull.orEmpty()
    private fun seconds(value: String): Long {
        val parts = value.trim().split(':').map { it.toLongOrNull() ?: return 0 }
        return if (parts.size in 2..3) parts.fold(0L) { a, n -> a * 60 + n } else 0
    }
    private fun renderers(node: JsonElement, out: MutableList<JsonObject>) {
        when (node) {
            is JsonObject -> { (node["musicResponsiveListItemRenderer"] as? JsonObject)?.let(out::add); node.values.forEach { renderers(it, out) } }
            is JsonArray -> node.forEach { renderers(it, out) }
            else -> {}
        }
    }
    fun parse(root: JsonElement): List<MusicSong> {
        val found = mutableListOf<JsonObject>(); renderers(root, found)
        return found.mapNotNull { r ->
            val columns = (r["flexColumns"] as? JsonArray).orEmpty().map { it.obj("musicResponsiveListItemFlexColumnRenderer").obj("text") }
            val title = runs(columns.getOrNull(0)).joinToString("") { it.text() }
            val second = runs(columns.getOrNull(1))
            val videoId = (r.obj("playlistItemData").obj("videoId") as? JsonPrimitive)?.contentOrNull
                ?: (runs(columns.getOrNull(0)).firstOrNull().obj("navigationEndpoint").obj("watchEndpoint").obj("videoId") as? JsonPrimitive)?.contentOrNull
            if (title.isBlank() || videoId == null || !Regex("^[A-Za-z0-9_-]{11}$").matches(videoId)) return@mapNotNull null
            fun page(run: JsonObject) = (run.obj("navigationEndpoint").obj("browseEndpoint").obj("browseEndpointContextSupportedConfigs").obj("browseEndpointContextMusicConfig").obj("pageType") as? JsonPrimitive)?.contentOrNull.orEmpty()
            val artists = second.filter { page(it) == "MUSIC_PAGE_TYPE_ARTIST" }.map { it.text() }
            val duration = second.reversed().map { it.text().trim() }.firstOrNull { Regex("^\\d+:\\d{2}(:\\d{2})?$").matches(it) }.orEmpty()
            MusicSong(videoId, title, artists.joinToString(", ").ifBlank { second.firstOrNull()?.text().orEmpty() }, seconds(duration) * 1000, r["badges"].toString().contains("MUSIC_EXPLICIT_BADGE"))
        }.distinctBy { it.videoId }
    }
    suspend fun search(query: String): List<MusicSong> = withContext(Dispatchers.IO) {
        val connection = URL("https://music.youtube.com/youtubei/v1/search?prettyPrint=false").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"; connection.doOutput = true; connection.connectTimeout = 15_000; connection.readTimeout = 20_000
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Origin", "https://music.youtube.com"); connection.setRequestProperty("Referer", "https://music.youtube.com/")
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36")
            val body = buildJsonObject {
                putJsonObject("context") { putJsonObject("client") { put("clientName", "WEB_REMIX"); put("clientVersion", "1.20241111.01.00"); put("hl", "en"); put("gl", "US") } }
                put("query", query.take(200)); put("params", SONGS)
            }
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            check(connection.responseCode == 200) { "YouTube Music search failed (${connection.responseCode})" }
            val text = connection.inputStream.use { input -> String(input.readNBytesCompat(6_000_000)) }
            parse(Json.parseToJsonElement(text))
        } finally { connection.disconnect() }
    }
}

private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(65536)
    while (true) { val n = read(buffer); if (n < 0) break; out.write(buffer, 0, n); check(out.size() <= limit) { "Response too large" } }
    return out.toByteArray()
}
