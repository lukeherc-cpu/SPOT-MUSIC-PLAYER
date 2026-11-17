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
}
