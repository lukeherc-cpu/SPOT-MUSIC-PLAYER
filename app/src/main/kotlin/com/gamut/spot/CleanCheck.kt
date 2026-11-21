package com.gamut.spot

import com.gamut.core.model.Track
import com.gamut.feature.resolver.AudioSourceRepository
import com.gamut.feature.playerui.LyricsRepository
import com.gamut.feature.playerui.LyricsState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** A moment of a song to listen to, in ms. */
data class CleanWindow(val startMs: Long, val endMs: Long)

/** What listening to a candidate found. */
enum class CleanVerdict { CLEAN, EXPLICIT, UNVERIFIED, NO_VOCALS, ERROR }
data class CleanResult(val verdict: CleanVerdict, val heard: List<String> = emptyList())

/** Pure rules for the swearing check, shared with the desktop app's clean-verify.mjs. */
object CleanWords {
    // Words a clean edit removes. "damn" and "hell" are left out: many official clean edits keep them.
    private val swears = Regex("(?<![\\p{L}\\p{N}])(?:motherf\\w*|f+u+c+k\\w*|fuk\\w*|bullshit|horseshit|shit\\w*|bitch\\w*|ass|asses|asshole\\w*|bastard\\w*|cunt\\w*|dick|dicks|dickhead\\w*|cock|cocks|cocksucker\\w*|puss(?:y|ies)|whore\\w*|slut\\w*|nigg\\w*|fag|fags|faggot\\w*|goddamn\\w*|tits?|twat\\w*)(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
    fun swears(text: String): List<String> = swears.findAll(text).map { it.value.lowercase() }.toList()

    /** Where the explicit song swears, from synced lyric lines (start, end, text): up to [max] padded windows. */
    fun windows(lines: List<Triple<Long, Long, String>>, durationMs: Long, max: Int = 8): List<CleanWindow> {
        val limit = if (durationMs > 0) durationMs else Long.MAX_VALUE
        val raw = lines.filter { it.second > it.first && swears(it.third).isNotEmpty() }
            .map { CleanWindow((it.first - 1500).coerceAtLeast(0), minOf(it.first + minOf(it.second - it.first, 9000) + 1500, limit)) }
            .sortedBy { it.startMs }
        val merged = mutableListOf<CleanWindow>()
        for (w in raw) {
            val last = merged.lastOrNull()
            if (last != null && w.startMs <= last.endMs && w.endMs - last.startMs <= 14_000) merged[merged.size - 1] = last.copy(endMs = maxOf(last.endMs, w.endMs))
            else merged += w
        }
        if (merged.size <= max) return merged
        val step = merged.size.toDouble() / max
        return List(max) { merged[(it * step).toInt()] }
    }

    /** Without synced lyrics: evenly spread windows over the body of the song. */
    fun spread(durationMs: Long, count: Int = 6, length: Long = 15_000): List<CleanWindow> {
        val d = if (durationMs > 0) durationMs else 180_000
        return List(count) { val start = (d * (.08 + .84 * it / count)).toLong(); CleanWindow(start, minOf(d, start + length)) }
    }

    /** The verdict from per-window transcripts of the explicit recording (control) and the candidate. */
    fun judge(control: List<String>, candidate: List<String>): CleanResult {
        val heard = candidate.flatMap { swears(it) }
        fun words(list: List<String>) = list.joinToString(" ").split(Regex("\\s+")).count { w -> w.any { it.isLetter() } }
        if (heard.isNotEmpty()) return CleanResult(CleanVerdict.EXPLICIT, heard.distinct())
        if (words(candidate) < maxOf(3.0, words(control) * .3)) return CleanResult(CleanVerdict.NO_VOCALS)
        if (control.none { swears(it).isNotEmpty() }) return CleanResult(CleanVerdict.UNVERIFIED)
        return CleanResult(CleanVerdict.CLEAN)
    }
}

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

/**
 * Listens to a clean-version candidate before it is trusted. The explicit song's synced lyrics say where
 * it swears; those moments are transcribed with the bundled offline Whisper model in the explicit
 * recording (the control) and in the candidate. Only a candidate where the control swears, the candidate
 * doesn't, and vocals are still present counts as clean. Audio stays on the device.
 */
@Singleton
class CleanVerifier @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val audio: AudioSourceRepository,
    private val speech: com.gamut.speech.PodcastSubtitles,
) {
    private class Control(val windows: List<CleanWindow>, val texts: List<String>)
    private val controls = mutableMapOf<String, Deferred<Control>>()
    private val directory get() = File(context.cacheDir, "clean-check").apply { mkdirs() }

    private suspend fun download(videoId: String): File = withContext(Dispatchers.IO) {
        val url = audio.audioUrl(videoId).getOrThrow()
        val file = File(directory, "$videoId.audio")
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000; connection.readTimeout = 30_000
            connection.setRequestProperty("Accept-Encoding", "identity")
            check(connection.responseCode in 200..299) { "Audio download failed (${connection.responseCode})" }
            connection.inputStream.use { input -> file.outputStream().use { output ->
                val buffer = ByteArray(65536); var total = 0L
                while (true) { val n = input.read(buffer); if (n < 0) break; output.write(buffer, 0, n); total += n; check(total <= 40_000_000) { "Audio too large" } }
            } }
            file
        } finally { connection.disconnect() }
    }
    private suspend fun transcribe(file: File, windows: List<CleanWindow>): List<String> = windows.map { w ->
        try {
            val pcm = com.gamut.speech.PodcastPcmDecoder.decode(file.absolutePath, w.startMs, w.endMs - w.startMs)
            if (pcm.size < 8_000) "" else speech.transcribeForCheck(pcm)
        } catch (e: CancellationException) { throw e } catch (_: Exception) { "" }
    }
    private suspend fun control(song: Track, controlVideoId: String): Control = coroutineScope {
        val task = synchronized(controls) { controls.getOrPut(song.isrc) { async(Dispatchers.IO) {
            val lyric = try { LyricsRepository.fetchLyrics(song, song.durationMs) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            val lines = (lyric as? LyricsState.Loaded)?.takeIf { it.isSynced }?.lines.orEmpty().map { Triple(it.timestampMs, it.endMs, it.text) }
            val windows = CleanWords.windows(lines, song.durationMs).ifEmpty { CleanWords.spread(song.durationMs) }
            val file = download(controlVideoId)
            try { Control(windows, transcribe(file, windows)) } finally { file.delete() }
        } } }
        try { task.await() } catch (e: Exception) { synchronized(controls) { controls.remove(song.isrc) }; throw e }
    }
    /** Listens to [videoId] as a clean version of the explicit [song], whose own recording is [controlVideoId]. */
    suspend fun check(song: Track, controlVideoId: String, videoId: String, durationMs: Long): CleanResult = try {
        val control = control(song, controlVideoId)
        val scale = if (durationMs > 0 && song.durationMs > 0 && kotlin.math.abs(durationMs - song.durationMs) < 30_000) durationMs.toDouble() / song.durationMs else 1.0
        val windows = control.windows.map { CleanWindow((it.startMs * scale).toLong(), (it.endMs * scale).toLong()) }
        val file = download(videoId)
        try { CleanWords.judge(control.texts, transcribe(file, windows)) } finally { file.delete() }
    } catch (e: CancellationException) { throw e } catch (_: Exception) { CleanResult(CleanVerdict.ERROR) }
}
