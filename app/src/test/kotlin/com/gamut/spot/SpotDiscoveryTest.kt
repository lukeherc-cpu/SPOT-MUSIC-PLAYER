package com.gamut.spot

import com.gamut.core.model.Track
import com.gamut.core.model.TrackSource
import com.gamut.core.network.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SpotDiscoveryTest {
    private class Catalog : SpotifyCatalog {
        val queries = mutableListOf<String>()
        val collections = mutableListOf<String>()
        var response: (String) -> SpotifySearch = { seed -> SpotifySearch(tracks = (1..25).map { song("$seed-$it", seed) }) }
        override suspend fun search(query: String) = searchAll(query).tracks
        override suspend fun searchAll(query: String): SpotifySearch { queries += query; return response(query) }
        override suspend fun collection(uri: String): SpotifyCollection {
            collections += uri
            return SpotifyCollection(uri, "Top 50 - USA", "Spotify", null, "", listOf(song("chart", "Chart Artist")), 1)
        }
    }
    @Test fun chartsUseTheActualUsCollectionAndGenresStayDistinct() = runBlocking {
        val catalog = Catalog()
        val discovery = SpotDiscovery(catalog)
        val charts = discovery.browse(BrowseCategory.Charts)
        assertEquals(listOf(SpotDiscovery.US_TOP_50.uri), catalog.collections)
        assertTrue(catalog.queries.isEmpty())
        assertEquals("chart", charts.songs.single().isrc)
        assertEquals(SpotDiscovery.US_TOP_50.uri, charts.rails.first().cards.single().uri)
        discovery.browse(BrowseCategory.Pop)
        assertTrue(catalog.queries.contains("genre:pop"))
        catalog.queries.clear()
        discovery.browse(BrowseCategory.HipHop)
        assertTrue(catalog.queries.contains("genre:hip-hop"))
        assertFalse(catalog.queries.any { "chart" in it || it == "pop" })
    }
    @Test fun historyChangesMixSeedsAndRotationChangesTrackOrder() = runBlocking {
        val catalog = Catalog()
        val discovery = SpotDiscovery(catalog)
        val a = song("old", "Artist A")
        val b = song("new", "Artist B")
        val first = discovery.recommendations(listOf(a), emptyList(), 1)
        val changed = discovery.recommendations(listOf(b, a), emptyList(), 1)
        assertEquals(1, first.mixes.size)
        assertTrue(first.mixes.first().tracks.all { it.artist == "Artist A" })
        assertTrue(changed.mixes.first().subtitle.startsWith("Artist B"))
        assertTrue(changed.mixes.first().tracks.any { it.artist == "Artist B" })
        val rotated = discovery.recommendations(listOf(a), emptyList(), 2)
        assertNotEquals(first.mixes.first().tracks.map { it.isrc }, rotated.mixes.first().tracks.map { it.isrc })
        assertEquals(SpotDiscovery.fingerprint(listOf(a), emptyList()), SpotDiscovery.fingerprint(listOf(a.copy(source = TrackSource.PODCAST), a), emptyList()))
    }
    @Test fun musicAlreadyInTheLibraryRemainsUsableWhenSpotifyFails() = runBlocking {
        val catalog = Catalog().apply { response = { error("offline") } }
        val saved = song("saved", "Artist A")
        assertEquals(listOf(saved), SpotDiscovery(catalog).recommendations(listOf(saved), emptyList(), 1).mixes.single().tracks)
    }
    @Test fun albumLookupUsesStoredIdentityAndRejectsAnotherArtistsAlbum() = runBlocking {
        val catalog = Catalog()
        val uri = "spotify:album:4m2880jivSbbyEGAKfITCa"
        val track = song("one", "Daft Punk").copy(album = "Random Access Memories", albumUri = uri)
        assertEquals(uri, SpotifyAlbumLookup(catalog).find(track)?.uri)
        assertTrue(catalog.queries.isEmpty())
        catalog.response = { SpotifySearch(albums = listOf(SpotifyCard(uri, "Random Access Memories", "Wrong Artist", null, "album"))) }
        assertNull(SpotifyAlbumLookup(catalog).find(track.copy(albumUri = null)))
        catalog.response = { SpotifySearch(albums = listOf(SpotifyCard(uri, "Random Access Memories", "Daft Punk", null, "album"))) }
        assertEquals(uri, SpotifyAlbumLookup(catalog).find(track.copy(albumUri = null))?.uri)
    }
    companion object {
        private fun song(id: String, artist: String) = Track(id, title = id, artist = artist, durationMs = 60000)
    }
}
