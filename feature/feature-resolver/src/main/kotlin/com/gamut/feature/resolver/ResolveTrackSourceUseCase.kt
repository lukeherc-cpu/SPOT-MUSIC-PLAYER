package com.gamut.feature.resolver

import com.gamut.core.model.ResolutionMethod
import com.gamut.core.model.ResolvedSource
import com.gamut.core.model.Track
import com.gamut.core.model.TrackSource
import com.gamut.core.network.YoutubeCandidate
import com.gamut.core.network.YoutubeMusicSearchSource
import com.gamut.core.player.PlaybackSettings
import com.gamut.core.player.StreamQuality
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed class ResolveFailure(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NoCandidates(val query: String) : ResolveFailure("No candidates for $query")
    class LowScore(val best: Double, val bestTitle: String) : ResolveFailure("Best candidate score: $best")
    class Timeout(val stage: String) : ResolveFailure("Timed out at $stage")
}

interface YoutubeCandidateRepository {
    suspend fun songs(track: Track): List<YoutubeCandidate>
    suspend fun broad(track: Track): List<YoutubeCandidate>

    /** YouTube Music songs without the explicit badge, for clean releases. */
    suspend fun cleanSongs(track: Track): List<YoutubeCandidate> = emptyList()
}

class YoutubeCandidateRepositoryImpl @Inject constructor(private val source: YoutubeMusicSearchSource) : YoutubeCandidateRepository {
    override suspend fun songs(track: Track) = source.searchSongs("${track.artist} ${track.title}")
    override suspend fun broad(track: Track) = source.searchBroad("${track.artist} ${track.title}")
    override suspend fun cleanSongs(track: Track): List<YoutubeCandidate> {
        val title = track.title.replace(Regex("\\s*[(\\[][^)\\]]*\\b(clean|radio edit|edited)\\b[^)\\]]*[)\\]]", RegexOption.IGNORE_CASE), "").trim()
        return com.gamut.core.network.YoutubeMusicSongs.search("${track.artist} $title")
            .filter { !it.explicit }
            .map { YoutubeCandidate(it.videoId, it.title, it.artist, channelIsTopicChannel = true, durationMs = it.durationMs, viewCount = 0, category = "songs", isMusicCatalog = true) }
    }
}

interface AudioSourceRepository {
    suspend fun audioUrl(videoId: String, forceRefresh: Boolean = false, quality: StreamQuality? = null): Result<String>

    fun clearCache() {}
}

@Singleton
class AudioSourceRepositoryImpl @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context,
    private val engine: AndroidYtDlpEngine,
    private val newPipe: NewPipeYoutubeSource,
    private val settings: PlaybackSettings,
    @ApplicationScope private val appScope: CoroutineScope,
) : AudioSourceRepository {
    private val saved = SavedAudioUrls(java.io.File(context.noBackupFilesDir, "saved-audio-urls.json"))
    private val cache = java.util.concurrent.ConcurrentHashMap<String, CachedAudio>()
    private val inFlight = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Deferred<Result<String>>>()

    override fun clearCache() {
        cache.clear()
        saved.clear()
        engine.forgetAll()
    }

    override suspend fun audioUrl(videoId: String, forceRefresh: Boolean, quality: StreamQuality?): Result<String> {
        val q = quality ?: settings.effectiveQuality()
        val key = "$videoId|${q.name}"
        if (forceRefresh) {
            cache.keys.removeIf { it.startsWith("$videoId|") }
            inFlight.keys.removeIf { it.startsWith("$videoId|") }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { saved.forget(videoId) }
            engine.forget(videoId)
        }
        cache[key]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let {
            return Result.success(it.url)
        }
        if (!forceRefresh) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { saved.get(key) }?.let {
            return Result.success(it)
        }
        val job = inFlight.computeIfAbsent(key) {
            appScope.async {
                try {
                    val result = kotlinx.coroutines.withTimeoutOrNull(AUDIO_PRIMARY_TIMEOUT_MS) { newPipe.audioUrl(videoId, q) }
                        ?.fold(
                            onSuccess = { Result.success(it) },
                            onFailure = { engine.audioUrl(videoId, q) },
                        )
                        ?: engine.audioUrl(videoId, q)
                    result.onSuccess { url ->
                        cache[key] = CachedAudio(url, expiryOf(url))
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { saved.put(key, url) }
                    }
                    result
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure<String>(e)
                }
            }
        }
        job.invokeOnCompletion { inFlight.remove(key, job) }
        return job.await()
    }

    private fun expiryOf(url: String): Long = SavedAudioUrls.expiryOf(url, System.currentTimeMillis())

    private data class CachedAudio(val url: String, val expiresAtMs: Long)

    private companion object {
        const val AUDIO_PRIMARY_TIMEOUT_MS = 8_000L
    }
}

class ResolveTrackSourceUseCase @Inject constructor(
    private val sources: ResolvedSourceRepository,
    private val candidates: YoutubeCandidateRepository,
    private val scorer: CandidateScorer,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    suspend operator fun invoke(track: Track): Result<ResolvedSource> = try {
        Result.success(resolveOrThrow(track))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private suspend fun resolveOrThrow(track: Track): ResolvedSource {
        val resolutionKey = track.resolutionKey()
        val youtubeVideoId = track.youtubeVideoId
        val wantsClean = track.isExplicit == false && track.source != TrackSource.PODCAST && track.source != TrackSource.YOUTUBE && youtubeVideoId == null
        val cached = sources.getLocal(resolutionKey)
        if (cached != null) {
            if (!wantsClean || cached.verifiedCount == CLEAN_CHECKED || cached.resolutionMethod == ResolutionMethod.MANUAL) return cached
            // Resolved before clean releases were matched to YouTube Music's unbadged song; check once.
            val clean = cleanSource(track, resolutionKey, cached) ?: cached.copy(verifiedCount = CLEAN_CHECKED).also { sources.saveLocal(it) }
            return clean
        }
        if (wantsClean) cleanSource(track, resolutionKey)?.let { return it }
        if (track.source != TrackSource.PODCAST && youtubeVideoId != null && com.gamut.core.network.isValidYoutubeVideoId(youtubeVideoId)) {
            val direct = ResolvedSource(resolutionKey, youtubeVideoId, if (track.source == TrackSource.YOUTUBE) ResolutionMethod.DIRECT else ResolutionMethod.HARD_FILTER, 1.0f, 0)
            sources.saveLocal(direct)
            appScope.launch { sources.submitRemote(direct) }
            return direct
        }

        val remote = try {
            withTimeoutOrNull(REMOTE_LOOKUP_TIMEOUT_MS) { sources.getRemote(resolutionKey) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        remote?.let {
            sources.saveLocal(it)
            return it
        }


        val songs = withTimeoutOrNull(SEARCH_TIMEOUT_MS) { candidates.songs(track) }
            ?: throw ResolveFailure.Timeout("songs search")
        var pool = songs
        var selected = scorer.selectBest(pool, track)
        if (selected == null || selected.confidenceScore < CandidateScorer.ACCEPT) {
            val broad = withTimeoutOrNull(BROAD_TIMEOUT_MS) { candidates.broad(track) }
                ?: throw ResolveFailure.Timeout("broad search")
            pool = songs + broad.filter { strictBroadOk(it, track) }
            selected = scorer.selectBest(pool, track)
        }
        val result = selected?.copy(isrc = resolutionKey)
            ?: if (pool.isEmpty()) {
                throw ResolveFailure.NoCandidates(track.title)
            } else {
                throw ResolveFailure.LowScore(scorer.rank(pool, track).first().score, pool.first().title)
            }
        if (result.confidenceScore >= CandidateScorer.ACCEPT) {
            sources.saveLocal(result)
            appScope.launch { sources.submitRemote(result) }
        }
        return result
    }

    /** A clean release plays YouTube Music's song without the explicit badge, so the audio is clean. */
    private suspend fun cleanSource(track: Track, resolutionKey: String, cached: ResolvedSource? = null): ResolvedSource? {
        val pool = try {
            withTimeoutOrNull(SEARCH_TIMEOUT_MS) { candidates.cleanSongs(track) }.orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        if (cached != null && pool.any { it.videoId == cached.youtubeVideoId }) return cached.copy(verifiedCount = CLEAN_CHECKED).also { sources.saveLocal(it) }
        val best = scorer.selectBest(pool, track)?.takeIf { it.confidenceScore >= CandidateScorer.ACCEPT } ?: return null
        return best.copy(isrc = resolutionKey, verifiedCount = CLEAN_CHECKED).also { sources.saveLocal(it) }
    }

    private fun strictBroadOk(candidate: YoutubeCandidate, track: Track): Boolean {
        val artist = com.gamut.core.common.TextMatch.toks(track.artist)
        val candidateText = com.gamut.core.common.TextMatch.toks(candidate.title + " " + candidate.channelName)
        val artistHit = artist.any { it in candidateText }
        val durationOk = candidate.durationMs <= 0L || track.durationMs <= 0L ||
            kotlin.math.abs(candidate.durationMs - track.durationMs) <= 8_000L
        return artistHit && durationOk
    }

    private companion object {
        const val SEARCH_TIMEOUT_MS = 8_000L
        const val BROAD_TIMEOUT_MS = 6_000L
        const val REMOTE_LOOKUP_TIMEOUT_MS = 1_500L
        const val CLEAN_CHECKED = 1
    }
}

private fun Track.resolutionKey(): String = isrc.takeIf { it.isNotBlank() }
    ?: "fallback:${listOf(artist, title, durationMs.toString()).joinToString("|") { value -> value.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim() }}"
