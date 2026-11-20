package com.gamut.spot

import com.gamut.core.model.Track
import com.gamut.core.network.SpotifyCard
import com.gamut.podcasts.PodcastShow
import com.gamut.spot.SearchRanking.Section
import org.junit.Assert.*
import org.junit.Test

class SearchRankingTest {
    private fun song(title: String, artist: String = "Someone") = Track("spotify:$title$artist".replace(" ", ""), title = title, artist = artist, durationMs = 200_000)
    private fun card(title: String, type: String) = SpotifyCard("spotify:$type:${title.hashCode()}", title, type, null, type)
    private fun show(title: String) = PodcastShow(title, title, "Publisher", "https://feeds.example/${title.hashCode()}.xml")

    @Test fun showNamePutsPodcastsFirst() {
        val songs = listOf(song("The Journal"), song("The Journal", "Band"), song("Journal", "Other"))
        val order = SearchRanking.order("the journal", songs, listOf(show("The Journal.")), listOf(card("Journal Vibes", "playlist")), emptyList(), emptyList(), listOf("podcast"))
        assertEquals(Section.Podcasts, order.first())
    }

    @Test fun moodsAndErasPutPlaylistsFirst() {
        for (query in listOf("chill", "2000s", "90s hits", "workout", "late night")) {
            val order = SearchRanking.order(query, listOf(song(query.replaceFirstChar { it.uppercase() })), emptyList(), listOf(card("$query mix", "playlist")), emptyList(), emptyList(), listOf("song"))
            assertEquals(query, Section.Playlists, order.first())
        }
    }

    @Test fun spotifyPlaylistTopResultPutsPlaylistsFirst() {
        val order = SearchRanking.order("rap caviar", listOf(song("Caviar")), emptyList(), listOf(card("RapCaviar", "playlist")), emptyList(), emptyList(), listOf("playlist"))
        assertEquals(Section.Playlists, order.first())
    }

    @Test fun songAndArtistSearchesStayMusicFirst() {
        assertEquals(Section.Songs, SearchRanking.order("blinding lights", listOf(song("Blinding Lights", "The Weeknd")), emptyList(), listOf(card("Chill Hits", "playlist")), listOf(card("The Weeknd", "artist")), emptyList(), listOf("song")).first())
        assertEquals(Section.Artists, SearchRanking.order("the weeknd", listOf(song("Starboy", "The Weeknd")), emptyList(), emptyList(), listOf(card("The Weeknd", "artist")), emptyList(), listOf("artist")).first())
        assertEquals(Section.Songs, SearchRanking.order("zzqx", emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()).first())
    }
}
