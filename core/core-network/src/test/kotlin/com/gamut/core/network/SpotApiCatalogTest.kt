package com.gamut.core.network

import com.gamut.core.model.TrackSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class SpotApiCatalogTest {
    @Test fun artworkChoosesTheLargestSafeSource() {
        val item = Json.parseToJsonElement("""{"coverArt":{"sources":[{"url":"https://i.scdn.co/small","width":64},{"url":"https://i.scdn.co/large","width":640},{"url":"https://evil.example/huge","width":9999},{"url":"https://i.scdn.co/medium","width":300}]}}""").jsonObject
        assertEquals("https://i.scdn.co/large", SpotApiCatalog.artwork(item))
    }
    @Test fun explicitRatingsAndAllArtistIdentitiesSurviveDecode() {
        val item = Json.parseToJsonElement("""{"data":{"searchV2":{"tracksV2":{"items":[{"uri":"spotify:track:4uLU6hMCjMI75M1A2tKUQC","name":"Song","contentRating":{"label":"EXPLICIT"},"artists":{"items":[{"uri":"spotify:artist:4tZwfgrHOc3mvqYlEYSvVi","profile":{"name":"Daft Punk"}},{"uri":"spotify:artist:2RdwBSPQiwcmiDo9kixcl8","profile":{"name":"Pharrell Williams"}}]},"duration":{"totalMilliseconds":60000}}]}}}}""").jsonObject
        val track = SpotApiCatalog.parseTracks(item).single()
        assertEquals(true, track.isExplicit)
        assertEquals(listOf("Daft Punk", "Pharrell Williams"), track.artists.map { it.name })
        assertEquals("spotify:artist:2RdwBSPQiwcmiDo9kixcl8", track.artists.last().uri)
    }
    @Test fun decodesSpotApiSearchEnvelopes() {
        val json = """{"data":{"searchV2":{"tracksV2":{"items":[{"item":{"data":{
          "uri":"spotify:track:4uLU6hMCjMI75M1A2tKUQC","name":"Never Gonna Give You Up",
          "artists":{"items":[{"profile":{"name":"Rick Astley"}}]},
          "albumOfTrack":{"uri":"spotify:album:4m2880jivSbbyEGAKfITCa","name":"Whenever You Need Somebody","coverArt":{"sources":[{"url":"https://i.scdn.co/image/test"}]}},
          "duration":{"totalMilliseconds":213573}
        }}}]}}}}"""
        val track = SpotApiCatalog.parseTracks(Json.parseToJsonElement(json).jsonObject).single()
        assertEquals("spotify:4uLU6hMCjMI75M1A2tKUQC", track.isrc)
        assertEquals("Rick Astley", track.artist)
        assertEquals(213573L, track.durationMs)
        assertEquals(TrackSource.SPOTIFY, track.source)
        assertEquals("spotify:album:4m2880jivSbbyEGAKfITCa", track.albumUri)
        assertNull(track.directAudioUrl)
    }
    @Test fun rejectsChangedSearchEnvelopesRatherThanClaimingNoMatches() {
        assertThrows(Exception::class.java) { SpotApiCatalog.parseTracks(Json.parseToJsonElement("{}").jsonObject) }
    }
    @Test fun validatesSpotifyImportLinks() {
        assertEquals("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M", SpotApiCatalog.spotifyResource("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=example"))
        assertThrows(IllegalArgumentException::class.java) { SpotApiCatalog.spotifyResource("https://example.org/playlist/37i9dQZF1DXcBWIGoYBM5M") }
        assertThrows(IllegalArgumentException::class.java) { SpotApiCatalog.spotifyResource("https://open.spotify.com/playlist/../../secret") }
    }
    @Test fun liveCollectionsAndRichSearch() = runBlocking {
        assumeTrue(System.getenv("SPOT_LIVE_SMOKE") == "1")
        val catalog = SpotApiCatalog()
        val search = catalog.searchAll("Daft Punk")
        println("Rich Spotify search: ${search.tracks.size} songs, ${search.albums.size} albums, ${search.artists.size} artists, ${search.playlists.size} playlists")
        assertTrue(search.albums.isNotEmpty())
        assertTrue(search.artists.isNotEmpty())
        for (uri in listOf("spotify:album:4m2880jivSbbyEGAKfITCa", "spotify:playlist:37i9dQZF1DXcBWIGoYBM5M", "spotify:playlist:37i9dQZF1DX4UtSsGT1Sbe", "spotify:artist:4tZwfgrHOc3mvqYlEYSvVi")) {
            val collection = catalog.collection(uri)
            assertTrue("No songs for $uri", collection.tracks.isNotEmpty())
            assertEquals(collection.total, collection.tracks.size + collection.skipped)
            println("Spotify collection ${collection.title}: ${collection.tracks.size} songs, ${collection.total} items")
        }
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
