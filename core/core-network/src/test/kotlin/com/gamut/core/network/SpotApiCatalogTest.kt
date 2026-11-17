package com.gamut.core.network

import com.gamut.core.model.TrackSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class SpotApiCatalogTest {
    @Test fun decodesSpotApiSearchEnvelopes() {
        val json = """{"data":{"searchV2":{"tracksV2":{"items":[{"item":{"data":{
          "uri":"spotify:track:4uLU6hMCjMI75M1A2tKUQC","name":"Never Gonna Give You Up",
          "artists":{"items":[{"profile":{"name":"Rick Astley"}}]},
          "albumOfTrack":{"name":"Whenever You Need Somebody","coverArt":{"sources":[{"url":"https://i.scdn.co/image/test"}]}},
          "duration":{"totalMilliseconds":213573}
        }}}]}}}}"""
        val track = SpotApiCatalog.parseTracks(Json.parseToJsonElement(json).jsonObject).single()
        assertEquals("spotify:4uLU6hMCjMI75M1A2tKUQC", track.isrc)
        assertEquals("Rick Astley", track.artist)
        assertEquals(213573L, track.durationMs)
        assertEquals(TrackSource.SPOTIFY, track.source)
        assertNull(track.directAudioUrl)
    }
    @Test fun rejectsChangedSearchEnvelopesRatherThanClaimingNoMatches() {
        assertThrows(Exception::class.java) { SpotApiCatalog.parseTracks(Json.parseToJsonElement("{}").jsonObject) }
    }
    @Test fun liveSpotifyCatalogSmoke() = runBlocking {
        assumeTrue(System.getenv("SPOT_LIVE_SMOKE") == "1")
        val start = System.nanoTime()
        val tracks = SpotApiCatalog().search("Daft Punk Get Lucky")
        assertTrue(tracks.isNotEmpty())
        assertTrue(tracks.any { "get lucky" in it.title.lowercase() })
        println("Spotify catalog: ${tracks.size} tracks in ${(System.nanoTime() - start) / 1_000_000} ms")
    }
}
