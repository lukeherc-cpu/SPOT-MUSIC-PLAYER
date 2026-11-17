package com.gamut.feature.search

import com.gamut.core.model.*
import com.gamut.core.network.*
import java.lang.reflect.Proxy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ProgressiveSearchTest {
    @Test fun playableResultsArriveBeforeSlowSpotifyCatalog() = runTest {
        val itunes = Proxy.newProxyInstance(ItunesApi::class.java.classLoader, arrayOf(ItunesApi::class.java)) { _, method, _ ->
            check(method.name == "search")
            Response.success(ItunesSearchResponse())
        } as ItunesApi
        val deezer = Proxy.newProxyInstance(DeezerApi::class.java.classLoader, arrayOf(DeezerApi::class.java)) { _, method, _ ->
            when (method.name) {
                "searchArtists" -> DeezerArtistSearchResponse()
                "searchTracks" -> DeezerTrackResponse()
                else -> error("Unexpected call: ${method.name}")
            }
        } as DeezerApi
        val youtube = object : YoutubeMusicSearchSource {
            override suspend fun searchSongs(query: String): List<YoutubeCandidate> {
                delay(100)
                return listOf(YoutubeCandidate("dQw4w9WgXcQ", "Never Gonna Give You Up", "Rick Astley - Topic", true, 213_000, 100_000_000, "Music", true))
            }
            override suspend fun searchBroad(query: String) = emptyList<YoutubeCandidate>()
        }
        val spotify = object : SpotifyCatalog {
            override suspend fun search(query: String): List<Track> { delay(10_000); return listOf(Track("spotify:4uLU6hMCjMI75M1A2tKUQC", title = "Never Gonna Give You Up", artist = "Rick Astley", durationMs = 213000, source = TrackSource.SPOTIFY)) }
        }
        val results = mutableListOf<SearchResult>()
        val job = launch { SearchRepositoryImpl(itunes, deezer, youtube, spotify).search("Rick Astley Never Gonna Give You Up").collect { results += it } }
        advanceTimeBy(101); runCurrent()
        assertTrue(results.any { result -> result.tracks.any { it.youtubeVideoId == "dQw4w9WgXcQ" } })
        assertFalse(results.any { it.isFinal })
        advanceUntilIdle()
        assertTrue(results.last().isFinal)
        assertEquals(TrackSource.SPOTIFY, results.last().tracks.single().source)
        assertEquals("dQw4w9WgXcQ", results.last().tracks.single().youtubeVideoId)
        job.join()
    }
}
