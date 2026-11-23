package com.gamut.feature.resolver

import com.gamut.core.model.*
import com.gamut.core.network.YoutubeCandidate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ResolveTrackSourceUseCaseTest {
    @Test fun knownYoutubeIdBypassesRemoteLookupAndSearch() = runTest {
        var saved: ResolvedSource? = null
        val sources = object : ResolvedSourceRepository {
            override suspend fun getLocal(isrc: String): ResolvedSource? = null
            override suspend fun getRemote(isrc: String): ResolvedSource? = error("Must not wait for remote lookup")
            override suspend fun saveLocal(source: ResolvedSource) { saved = source }
            override suspend fun submitRemote(source: ResolvedSource) {}
        }
        val candidates = object : YoutubeCandidateRepository {
            override suspend fun songs(track: Track): List<YoutubeCandidate> = error("Must not search again")
            override suspend fun broad(track: Track): List<YoutubeCandidate> = error("Must not search again")
        }
        val track = Track("youtube:dQw4w9WgXcQ", title = "Song", artist = "Artist", durationMs = 1000,
            source = TrackSource.YOUTUBE, youtubeVideoId = "dQw4w9WgXcQ")
        val result = ResolveTrackSourceUseCase(sources, candidates, CandidateScorer(), backgroundScope)(track).getOrThrow()
        assertEquals("dQw4w9WgXcQ", result.youtubeVideoId)
        assertEquals(ResolutionMethod.DIRECT, result.resolutionMethod)
        assertEquals(result, saved)
    }

    private fun candidate(id: String, title: String) = YoutubeCandidate(id, title, "Artist - Topic", true, 200_000, 1000, "songs", isMusicCatalog = true)

    private fun memory(initial: ResolvedSource? = null) = object : ResolvedSourceRepository {
        var stored = initial
        override suspend fun getLocal(isrc: String): ResolvedSource? = stored
        override suspend fun getRemote(isrc: String): ResolvedSource? = null
        override suspend fun saveLocal(source: ResolvedSource) { stored = source }
        override suspend fun submitRemote(source: ResolvedSource) {}
    }

    @Test fun cleanReleasePlaysTheUnbadgedSong() = runTest {
        val candidates = object : YoutubeCandidateRepository {
            override suspend fun songs(track: Track) = listOf(candidate("EEEEEEEEEEE", "Good Song"))
            override suspend fun broad(track: Track) = emptyList<YoutubeCandidate>()
            override suspend fun cleanLookup(track: Track) = CleanLookup(listOf(candidate("CCCCCCCCCCC", "Good Song")), setOf("EEEEEEEEEEE"))
        }
        val track = Track("spotify:clean", title = "Good Song", artist = "Artist", durationMs = 200_000, source = TrackSource.SPOTIFY, isExplicit = false)
        val result = ResolveTrackSourceUseCase(memory(), candidates, CandidateScorer(), backgroundScope)(track).getOrThrow()
        assertEquals("CCCCCCCCCCC", result.youtubeVideoId)
    }

    @Test fun cleanReleaseResolvedEarlierIsRecheckedOnce() = runTest {
        var cleanSearches = 0
        val candidates = object : YoutubeCandidateRepository {
            override suspend fun songs(track: Track) = emptyList<YoutubeCandidate>()
            override suspend fun broad(track: Track) = emptyList<YoutubeCandidate>()
            override suspend fun cleanLookup(track: Track): CleanLookup { cleanSearches++; return CleanLookup(listOf(candidate("CCCCCCCCCCC", "Good Song"))) }
        }
        val sources = memory(ResolvedSource("spotify:clean", "EEEEEEEEEEE", ResolutionMethod.TOPIC_CHANNEL, 0.9f, 0))
        val track = Track("spotify:clean", title = "Good Song", artist = "Artist", durationMs = 200_000, source = TrackSource.SPOTIFY, isExplicit = false)
        val resolve = ResolveTrackSourceUseCase(sources, candidates, CandidateScorer(), backgroundScope)
        assertEquals("CCCCCCCCCCC", resolve(track).getOrThrow().youtubeVideoId)
        assertEquals("CCCCCCCCCCC", resolve(track).getOrThrow().youtubeVideoId)
        assertEquals(1, cleanSearches)
    }

    @Test fun cleanReleaseNeverKeepsTheExplicitMaster() = runTest {
        val candidates = object : YoutubeCandidateRepository {
            override suspend fun songs(track: Track) = listOf(candidate("EEEEEEEEEEE", "Good Song"), candidate("OOOOOOOOOOO", "Good Song"))
            override suspend fun broad(track: Track) = emptyList<YoutubeCandidate>()
            override suspend fun cleanLookup(track: Track) = CleanLookup(explicitIds = setOf("EEEEEEEEEEE"))
        }
        val sources = memory(ResolvedSource("spotify:clean", "EEEEEEEEEEE", ResolutionMethod.TOPIC_CHANNEL, 0.9f, 0))
        val track = Track("spotify:clean", title = "Good Song", artist = "Artist", durationMs = 200_000, source = TrackSource.SPOTIFY, isExplicit = false)
        assertEquals("OOOOOOOOOOO", ResolveTrackSourceUseCase(sources, candidates, CandidateScorer(), backgroundScope)(track).getOrThrow().youtubeVideoId)
    }

    @Test fun replacedSourceWins() = runTest {
        val candidates = object : YoutubeCandidateRepository {
            override suspend fun songs(track: Track): List<YoutubeCandidate> = error("Must not search")
            override suspend fun broad(track: Track): List<YoutubeCandidate> = error("Must not search")
            override suspend fun cleanLookup(track: Track): CleanLookup = error("Must not search")
        }
        val sources = memory()
        val track = Track("spotify:clean", title = "Good Song", artist = "Artist", durationMs = 200_000, source = TrackSource.SPOTIFY, isExplicit = false)
        val resolve = ResolveTrackSourceUseCase(sources, candidates, CandidateScorer(), backgroundScope)
        resolve.replaceSource(track, "MMMMMMMMMMM")
        assertEquals("MMMMMMMMMMM", resolve(track).getOrThrow().youtubeVideoId)
    }
}
