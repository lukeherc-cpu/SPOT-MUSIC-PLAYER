package com.gamut.speech

import android.content.Context
import com.gamut.core.database.DownloadIndex
import com.gamut.core.model.*
import com.gamut.core.player.MusicPlayer
import com.gamut.podcasts.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

data class PodcastSubtitleState(val trackId: String = "", val cues: List<SubtitleCue> = emptyList(), val timed: Boolean = true, val generated: Boolean = false, val working: Boolean = false, val error: String? = null)

@Singleton
class PodcastSubtitles @Inject constructor(@ApplicationContext private val context: Context, private val podcasts: PodcastRepository, private val player: MusicPlayer, private val downloads: DownloadIndex) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nativeLock = Mutex()
    private val mutable = MutableStateFlow(PodcastSubtitleState())
    val state: StateFlow<PodcastSubtitleState> = mutable.asStateFlow()
    @Volatile private var position = 0L
    @Volatile private var activeId: String? = null
    private var job: Job? = null
    @Volatile private var retryAfter = 0L
    private val directory = File(context.filesDir, "podcast-subtitles").apply { mkdirs() }
    init {
        scope.launch {
            player.playbackState.collect { playback ->
                val track = when (playback) { is PlaybackState.Playing -> playback.track; is PlaybackState.Paused -> playback.track; else -> null }
                position = when (playback) { is PlaybackState.Playing -> playback.positionMs; is PlaybackState.Paused -> playback.positionMs; else -> position }
                if (track != null && (track.isrc != activeId || (track.source == TrackSource.PODCAST && job?.isCompleted == true && mutable.value.error != null && System.currentTimeMillis() >= retryAfter))) {
                    val previous = job
                    previous?.cancel(); WhisperNative.cancelIfLoaded(); activeId = track.isrc
                    if (track.source == TrackSource.PODCAST) {
                        mutable.value = PodcastSubtitleState(track.isrc, working = true)
                        job = scope.launch { prepare(track) }
                    } else {
                        mutable.value = PodcastSubtitleState()
                        scope.launch { previous?.join(); nativeLock.withLock { if (activeId == track.isrc) WhisperNative.releaseIfLoaded() } }
                    }
                }
            }
        }
    }
    private suspend fun publisher(track: Track): PodcastTranscript? {
        var enriched = track
        if (track.transcripts.isEmpty() && !track.album.isNullOrBlank()) {
            val show = podcasts.search(track.album!!).firstOrNull { it.title.equals(track.album, true) }
            val episode = show?.let { show -> podcasts.episodes(show).firstOrNull { it.audioUrl == track.directAudioUrl || it.track(show).isrc == track.isrc } }
            if (episode != null) enriched = track.copy(transcripts = episode.transcripts)
        }
        return podcasts.transcript(enriched)
    }
    private suspend fun prepare(track: Track) {
        try {
            val cache = cacheFile(track)
            val saved = load(cache)
            currentCoroutineContext().ensureActive()
            if (saved.first.isNotEmpty()) mutable.value = PodcastSubtitleState(track.isrc, saved.first, generated = true, working = true)
            val published = try { withTimeoutOrNull(8_000) { publisher(track) } } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            if (published != null) { mutable.value = PodcastSubtitleState(track.isrc, published.cues, published.timed); return }
            val model = model()
            WhisperNative.load()
            val cues = saved.first.toMutableList()
            val windows = saved.second.toMutableSet()
            var language = runCatching { Json.parseToJsonElement(cache.readText()).jsonObject["language"]?.jsonPrimitive?.content }.getOrNull() ?: "auto"
            val source = downloads.pathFor(track.isrc)?.takeIf { File(it).isFile } ?: track.directAudioUrl ?: error("This episode has no audio source")
            var endAt = track.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE
            while (currentCoroutineContext().isActive) {
                val current = position / WINDOW * WINDOW
                val next = (0..2).map { current + it * WINDOW }.firstOrNull { it < endAt && it !in windows }
                if (next == null) { mutable.value = PodcastSubtitleState(track.isrc, cues.toList(), generated = true); delay(750); continue }
                mutable.value = PodcastSubtitleState(track.isrc, cues.toList(), generated = true, working = true)
                val pcm = PodcastPcmDecoder.decode(source, next, WINDOW)
                currentCoroutineContext().ensureActive()
                if (pcm.size < WINDOW * 16 - 16_000) endAt = next + pcm.size / 16
                val energy = if (pcm.isEmpty()) 0.0 else pcm.sumOf { it.toDouble() * it } / pcm.size
                if (pcm.size >= 8_000 && energy > .000004) {
                    val result = nativeLock.withLock {
                        val result = WhisperNative.transcribe(model.absolutePath, pcm, language).toString(Charsets.UTF_8)
                        language = WhisperNative.language()
                        result
                    }
                    currentCoroutineContext().ensureActive()
                    val additions = Json.parseToJsonElement(result).jsonArray.mapNotNull { value ->
                        val o = value.jsonObject
                        val start = o.getValue("start").jsonPrimitive.long.coerceAtLeast(0)
                        val end = o.getValue("end").jsonPrimitive.long.coerceAtMost(pcm.size / 16L)
                        val text = o.getValue("text").jsonPrimitive.content.trim()
                        if (text.isBlank() || end <= start) null else SubtitleCue(next + start, next + end, text)
                    }
                    cues.removeAll { it.startMs >= next && it.startMs < next + WINDOW }
                    cues.addAll(additions); cues.sortBy { it.startMs }
                }
                windows.add(next)
                save(cache, cues, windows, language)
                mutable.value = PodcastSubtitleState(track.isrc, cues.toList(), generated = true)
                yield()
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            retryAfter = System.currentTimeMillis() + 30_000
            if (activeId == track.isrc) mutable.update { it.copy(working = false, error = "Couldn't generate subtitles for this audio. Playback can continue.") }
        }
    }
    /** Transcribes a short 16 kHz mono clip in English for the clean-version check; returns the joined text. */
    suspend fun transcribeForCheck(pcm: FloatArray): String {
        val model = withContext(Dispatchers.IO) { model() }
        val result = nativeLock.withLock { WhisperNative.load(); WhisperNative.transcribe(model.absolutePath, pcm, "en").toString(Charsets.UTF_8) }
        return Json.parseToJsonElement(result).jsonArray.joinToString(" ") { it.jsonObject.getValue("text").jsonPrimitive.content.trim() }
    }
    internal fun model(): File {
        val target = File(context.filesDir, "speech/ggml-tiny-q5_1.bin")
        if (target.isFile && target.length() == 32_152_673L) return target
        target.parentFile!!.mkdirs()
        val part = File(target.parentFile, "model.part")
        context.assets.open("models/ggml-tiny-q5_1.bin").use { input -> part.outputStream().use { input.copyTo(it) } }
        val digest = MessageDigest.getInstance("SHA-256")
        part.inputStream().use { input -> val buffer = ByteArray(65536); while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) } }
        check(digest.digest().joinToString("") { "%02x".format(it) } == "818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7")
        check(part.renameTo(target)); return target
    }
    private fun cacheFile(track: Track) = File(directory, MessageDigest.getInstance("SHA-256").digest(track.isrc.toByteArray()).joinToString("") { "%02x".format(it) } + ".json")
    private fun load(file: File): Pair<List<SubtitleCue>, Set<Long>> = runCatching {
        require(file.length() <= 8_000_000)
        val root = Json.parseToJsonElement(file.readText()).jsonObject
        val cues = root.getValue("cues").jsonArray.map { row -> val a = row.jsonArray; SubtitleCue(a[0].jsonPrimitive.long, a[1].jsonPrimitive.long, a[2].jsonPrimitive.content) }
        cues to root.getValue("windows").jsonArray.map { it.jsonPrimitive.long }.toSet()
    }.getOrDefault(emptyList<SubtitleCue>() to emptySet())
    private fun save(file: File, cues: List<SubtitleCue>, windows: Set<Long>, language: String) {
        val root = buildJsonObject {
            put("version", 1); put("engine", "Whisper Tiny")
            put("language", language)
            put("cues", buildJsonArray { cues.take(50_000).forEach { cue -> add(buildJsonArray { add(cue.startMs); add(cue.endMs); add(cue.text) }) } })
            put("windows", buildJsonArray { windows.sorted().forEach { add(it) } })
        }
        val part = File(file.path + ".part"); part.writeText(root.toString()); check(part.renameTo(file))
        directory.listFiles { candidate -> candidate.extension == "json" }?.sortedByDescending { it.lastModified() }?.drop(100)?.forEach { it.delete() }
    }
    companion object { private const val WINDOW = 20_000L }
}
