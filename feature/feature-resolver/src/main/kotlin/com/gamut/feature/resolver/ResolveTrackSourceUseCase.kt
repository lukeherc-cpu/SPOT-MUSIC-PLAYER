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

    /** YouTube Music's songs for a clean release, split by its explicit badge, plus clean-labelled uploads. */
    suspend fun cleanLookup(track: Track): CleanLookup = CleanLookup()
}

/**
 * [clean]: songs without YouTube Music's explicit badge, best first. [explicitIds]: videos that carry the badge and must
 * never play for a clean release. [ok] is false when the lookup itself failed, so it can be tried again later.
 */
data class CleanLookup(val clean: List<YoutubeCandidate> = emptyList(), val explicitIds: Set<String> = emptySet(), val uploads: List<YoutubeCandidate> = emptyList(), val ok: Boolean = true)

internal val CleanLabel = Regex("\\b(clean(?: version| edit)?|censored|non[ -]?explicit|radio edit|edited(?: version)?)\\b", RegexOption.IGNORE_CASE)
private val ExplicitLabel = Regex("\\b(explicit|dirty|uncensored)\\b", RegexOption.IGNORE_CASE)

class YoutubeCandidateRepositoryImpl @Inject constructor(private val source: YoutubeMusicSearchSource) : YoutubeCandidateRepository {
    override suspend fun songs(track: Track) = source.searchSongs("${track.artist} ${track.title}")
    override suspend fun broad(track: Track) = source.searchBroad("${track.artist} ${track.title}")
    override suspend fun cleanLookup(track: Track): CleanLookup {
        val title = track.title.replace(Regex("\\s*[(\\[][^)\\]]*\\b(clean|radio edit|edited)\\b[^)\\]]*[)\\]]", RegexOption.IGNORE_CASE), "").trim()
        val artist = track.artist.substringBefore(',').trim()
        // The plain search often lists only the explicit master, so the clean one is asked for by name as well.
        var failures = 0
        val songs = listOf("$artist $title", "$artist $title clean").flatMap { query ->
            try { com.gamut.core.network.YoutubeMusicSongs.search(query) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { failures++; emptyList() }
        }.distinctBy { it.videoId }
        val explicitIds = songs.filter { it.explicit }.mapTo(hashSetOf()) { it.videoId }
        val clean = songs.filter { !it.explicit && !ExplicitLabel.containsMatchIn(it.title) }
            .map { YoutubeCandidate(it.videoId, it.title, it.artist, channelIsTopicChannel = true, durationMs = it.durationMs, viewCount = 0, category = "songs", isMusicCatalog = true) }
        // Labels don't always put a clean master on YouTube Music; then a clean-labelled upload is the next best thing.
        val uploads = if (clean.isNotEmpty()) emptyList() else try {
            source.searchBroad("$artist $title clean").filter { CleanLabel.containsMatchIn(it.title) && !ExplicitLabel.containsMatchIn(it.title) && it.videoId !in explicitIds }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { emptyList() }
        return CleanLookup(clean, explicitIds, uploads, ok = failures < 2)
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

    /** The listener's own pick of audio for [track]. It wins over every automatic match from now on. */
    suspend fun replaceSource(track: Track, videoId: String) {
        require(com.gamut.core.network.isValidYoutubeVideoId(videoId))
        sources.saveLocal(ResolvedSource(track.resolutionKey(), videoId, ResolutionMethod.MANUAL, 1.0f, CLEAN_CHECKED))
    }

    private suspend fun resolveOrThrow(track: Track): ResolvedSource {
        val resolutionKey = track.resolutionKey()
        val youtubeVideoId = track.youtubeVideoId
        val wantsClean = track.isExplicit == false && track.source != TrackSource.PODCAST && track.source != TrackSource.YOUTUBE && youtubeVideoId == null
        val cached = sources.getLocal(resolutionKey)
        if (cached != null) {
            if (!wantsClean || cached.verifiedCount == CLEAN_CHECKED || cached.resolutionMethod == ResolutionMethod.MANUAL) return cached
            // Resolved before clean releases were matched to clean audio; check it once.
            val lookup = cleanLookup(track)
            cleanSource(track, resolutionKey, lookup, cached)?.let { return it }
            if (!lookup.ok) return cached
            if (cached.youtubeVideoId !in lookup.explicitIds) return cached.copy(verifiedCount = CLEAN_CHECKED).also { sources.saveLocal(it) }
            // The saved video is the explicit master: resolve again below, without it.
            return searchAndSelect(track, resolutionKey, lookup.explicitIds, checked = true)
        }
        var avoid = emptySet<String>()
        var checked = false
        if (wantsClean) {
            val lookup = cleanLookup(track)
            cleanSource(track, resolutionKey, lookup)?.let { return it }
            avoid = lookup.explicitIds
            checked = lookup.ok
        }
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


        return searchAndSelect(track, resolutionKey, avoid, checked)
    }

    private suspend fun searchAndSelect(track: Track, resolutionKey: String, avoid: Set<String>, checked: Boolean = false): ResolvedSource {
        val songs = (withTimeoutOrNull(SEARCH_TIMEOUT_MS) { candidates.songs(track) }
            ?: throw ResolveFailure.Timeout("songs search")).filter { it.videoId !in avoid }
        var pool = songs
        var selected = scorer.selectBest(pool, track)
        if (selected == null || selected.confidenceScore < CandidateScorer.ACCEPT) {
            val broad = withTimeoutOrNull(BROAD_TIMEOUT_MS) { candidates.broad(track) }
                ?: throw ResolveFailure.Timeout("broad search")
            pool = songs + broad.filter { strictBroadOk(it, track) && it.videoId !in avoid }
            selected = scorer.selectBest(pool, track)
        }
        val result = selected?.copy(isrc = resolutionKey, verifiedCount = if (checked) CLEAN_CHECKED else 0)
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

    private suspend fun cleanLookup(track: Track): CleanLookup = try {
        withTimeoutOrNull(CLEAN_TIMEOUT_MS) { candidates.cleanLookup(track) } ?: CleanLookup(ok = false)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        CleanLookup(ok = false)
    }

    /** A clean release plays YouTube Music's song without the explicit badge, or else a clean-labelled upload. */
    private suspend fun cleanSource(track: Track, resolutionKey: String, lookup: CleanLookup, cached: ResolvedSource? = null): ResolvedSource? {
        if (cached != null && lookup.clean.any { it.videoId == cached.youtubeVideoId }) return cached.copy(verifiedCount = CLEAN_CHECKED).also { sources.saveLocal(it) }
        val best = scorer.selectBest(lookup.clean, track)?.takeIf { it.confidenceScore >= CandidateScorer.ACCEPT }
            ?: scorer.selectBest(lookup.uploads.filter { strictBroadOk(it, track, 15_000L) }, track)
            ?: return null
        return best.copy(isrc = resolutionKey, verifiedCount = CLEAN_CHECKED).also { sources.saveLocal(it) }
    }

    private fun strictBroadOk(candidate: YoutubeCandidate, track: Track, toleranceMs: Long = 8_000L): Boolean {
        val artist = com.gamut.core.common.TextMatch.toks(track.artist)
        val candidateText = com.gamut.core.common.TextMatch.toks(candidate.title + " " + candidate.channelName)
        val artistHit = artist.any { it in candidateText }
        val durationOk = candidate.durationMs <= 0L || track.durationMs <= 0L ||
            kotlin.math.abs(candidate.durationMs - track.durationMs) <= toleranceMs
        return artistHit && durationOk
    }

    private companion object {
        const val SEARCH_TIMEOUT_MS = 8_000L
        const val BROAD_TIMEOUT_MS = 6_000L
        const val REMOTE_LOOKUP_TIMEOUT_MS = 1_500L
        const val CLEAN_CHECKED = 1
        const val CLEAN_TIMEOUT_MS = 20_000L
    }
}

private fun Track.resolutionKey(): String = isrc.takeIf { it.isNotBlank() }
    ?: "fallback:${listOf(artist, title, durationMs.toString()).joinToString("|") { value -> value.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim() }}"
