package com.gamut.spot

import com.gamut.core.model.Track
import com.gamut.core.model.TrackSource
import com.gamut.core.network.SpotifyCard
import com.gamut.core.network.SpotifyCatalog
import com.gamut.core.network.SpotifySearch
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlin.random.Random

enum class BrowseCategory(val title: String, val color: Long, val query: String, val genre: String? = null) {
    Music("Music", 0xFFE0148C, "discover new music"),
    Podcasts("Podcasts", 0xFF006450, "popular"),
    NewReleases("New releases", 0xFFE66A20, "new music friday"),
    Charts("Charts", 0xFF8D67AB, ""),
    Indie("Indie", 0xFF608108, "indie", "indie"),
    Alternative("Alternative", 0xFFBC5900, "alternative", "alternative"),
    Pop("Pop", 0xFF8D67AB, "pop", "pop"),
    HipHop("Hip-Hop", 0xFFBA5D07, "hip hop", "hip-hop"),
    Ambient("Ambient", 0xFF477D95, "ambient", "ambient"),
    Folk("Folk & Acoustic", 0xFFB49A6D, "folk acoustic", "folk"),
}

data class DailyMix(val id: String, val title: String, val subtitle: String, val tracks: List<Track>) {
    val artwork: String? get() = tracks.firstOrNull { it.albumArtUrl != null }?.albumArtUrl
}
data class BrowseContent(val rails: List<MusicRail> = emptyList(), val songs: List<Track> = emptyList())
data class HomeRecommendations(val mixes: List<DailyMix>, val rails: List<MusicRail>, val songs: List<Track>)

/** Public Spotify discovery plus local listening-history ranking; no private Spotify account feeds. */
class SpotDiscovery @Inject constructor(private val catalog: SpotifyCatalog) {
    suspend fun browse(category: BrowseCategory): BrowseContent {
        if (category == BrowseCategory.Charts) {
            // These are Spotify chart collections, not search results for the word "charts".
            val us = catalog.collection(US_TOP_50.uri)
            return BrowseContent(listOf(MusicRail("Top charts in the US", listOf(US_TOP_50.copy(artwork = us.artwork ?: US_TOP_50.artwork))), MusicRail("Around the world", listOf(GLOBAL_TOP_50))), us.tracks.take(10))
        }
        val year = LocalDate.now().year
        val primary = catalog.searchAll(category.query)
        val releases = catalog.searchAll(category.genre?.let { "genre:$it year:$year" } ?: "year:$year")
        val featured = when (category) {
            BrowseCategory.Music -> listOf(NEW_MUSIC_FRIDAY, TODAYS_TOP_HITS)
            BrowseCategory.NewReleases -> listOf(NEW_MUSIC_FRIDAY)
            BrowseCategory.Pop -> listOf(TODAYS_TOP_HITS)
            BrowseCategory.HipHop -> listOf(RAP_CAVIAR)
            BrowseCategory.Ambient -> listOf(PEACEFUL_PIANO)
            else -> emptyList()
        }
        return BrowseContent(buildList {
            if (featured.isNotEmpty()) add(MusicRail(if (category == BrowseCategory.NewReleases) "Discover new music" else "Start here", featured))
            if (primary.playlists.isNotEmpty()) add(MusicRail(if (category.genre != null) "${category.title} playlists" else "Discover something new", primary.playlists.take(12)))
            if (releases.albums.isNotEmpty()) add(MusicRail(if (category.genre != null) "New in ${category.title}" else "New albums and singles", releases.albums.take(12)))
            if (primary.albums.isNotEmpty()) add(MusicRail("Albums to explore", primary.albums.take(12)))
        }, (if (category.genre != null) catalog.searchAll("genre:${category.genre}").tracks else primary.tracks).take(20))
    }

    suspend fun recommendations(recent: List<Track>, favorites: List<Track>, rotation: Long): HomeRecommendations {
        val seeds = seedArtists(recent, favorites)
        val music = (recent + favorites).filter { it.source != TrackSource.PODCAST }.distinctBy { it.isrc }
        val results = seeds.associateWith { seed ->
            try { catalog.searchAll(seed) } catch (e: CancellationException) { throw e } catch (_: Exception) { SpotifySearch() }
        }
        val random = Random((rotation xor fingerprint(recent, favorites).hashCode().toLong()).toInt())
        val mixes = (0 until minOf(3, seeds.size)).map { index ->
            val names = listOf(seeds[index % seeds.size], seeds[(index + 3) % seeds.size]).distinct()
            val known = music.filter { song -> names.any { song.artist.contains(it, true) } }
            val pool = (names.flatMap { results[it]?.tracks.orEmpty() } + known).distinctBy { it.isrc }
            val shuffled = pool.shuffled(random)
            val justPlayed = recent.take(3).mapTo(hashSetOf()) { it.isrc }
            val tracks = (shuffled.filterNot { it.isrc in justPlayed } + shuffled.filter { it.isrc in justPlayed }).take(40)
            DailyMix("daily-mix-${index + 1}", "Daily Mix ${index + 1}", names.joinToString(", ") + " and more", tracks)
        }
        val picked = results.values.flatMap { it.playlists + it.albums }.distinctBy { it.uri }.shuffled(random).take(12)
        val rails = buildList {
            if (picked.isNotEmpty()) add(MusicRail("Picked for you", picked))
            seeds.take(2).forEach { seed ->
                val cards = results[seed]?.let { it.albums + it.playlists }.orEmpty().distinctBy { it.uri }.shuffled(random).take(12)
                if (cards.isNotEmpty()) add(MusicRail("More like $seed", cards))
            }
        }
        return HomeRecommendations(mixes, rails, music.take(10))
    }

    companion object {
        fun starterContent(category: BrowseCategory): BrowseContent = if (category == BrowseCategory.Charts)
            BrowseContent(listOf(MusicRail("Top charts in the US", listOf(US_TOP_50)), MusicRail("Around the world", listOf(GLOBAL_TOP_50)))) else BrowseContent()
        val US_TOP_50 = SpotifyCard("spotify:playlist:37i9dQZEVXbLRQDuF5jeBp", "Top 50 - USA", "The most played tracks in the United States", "https://charts-images.scdn.co/assets/locale_en/regional/daily/region_us_default.jpg", "playlist")
        val GLOBAL_TOP_50 = SpotifyCard("spotify:playlist:37i9dQZEVXbMDoHDwVN2tF", "Top 50 - Global", "The most played tracks worldwide", "https://charts-images.scdn.co/assets/locale_en/regional/daily/region_global_default.jpg", "playlist")
        val NEW_MUSIC_FRIDAY = SpotifyCard("spotify:playlist:37i9dQZF1DX4JAvHpjipBk", "New Music Friday", "New music, updated by Spotify", null, "playlist")
        val TODAYS_TOP_HITS = SpotifyCard("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M", "Today's Top Hits", "Today's biggest hits", null, "playlist")
        val RAP_CAVIAR = SpotifyCard("spotify:playlist:37i9dQZF1DX0XUsuxWHRQd", "RapCaviar", "New and essential hip-hop", null, "playlist")
        val PEACEFUL_PIANO = SpotifyCard("spotify:playlist:37i9dQZF1DX4sWSpwq3LiO", "Peaceful Piano", "Instrumental listening", null, "playlist")

        fun fingerprint(recent: List<Track>, favorites: List<Track>): String =
            recent.filter { it.source != TrackSource.PODCAST }.take(30).joinToString { it.isrc } + "|" + favorites.filter { it.source != TrackSource.PODCAST }.take(30).joinToString { it.isrc }

        fun seedArtists(recent: List<Track>, favorites: List<Track>): List<String> {
            val weights = linkedMapOf<String, Pair<String, Int>>()
            fun add(track: Track, weight: Int) {
                val name = track.artist.substringBefore(',').trim()
                if (track.source == TrackSource.PODCAST || name.isBlank() || name.equals("Unknown artist", true)) return
                val key = name.lowercase()
                weights[key] = name to (weights[key]?.second.orZero() + weight)
            }
            recent.take(30).forEachIndexed { index, song -> add(song, (40 - index).coerceAtLeast(1)) }
            favorites.take(50).forEach { add(it, 8) }
            return weights.values.sortedByDescending { it.second }.take(6).map { it.first }.ifEmpty { listOf("indie pop", "hip hop", "chill") }
        }
        private fun Int?.orZero() = this ?: 0
    }
}

class SpotifyAlbumLookup @Inject constructor(private val catalog: SpotifyCatalog) {
    suspend fun find(song: Track): SpotifyCard? {
        fun linked(track: Track) = track.albumUri?.takeIf { it.matches(Regex("spotify:album:[A-Za-z0-9]{22}")) }
            ?.let { SpotifyCard(it, track.album ?: "Album", track.artist, track.albumArtUrl, "album") }
        linked(song)?.let { return it }
        val match = if (song.album.isNullOrBlank()) catalog.searchAll("${song.title} ${song.artist}").tracks.firstOrNull {
            normalized(it.title) == normalized(song.title) && normalized(it.artist) == normalized(song.artist)
        } ?: return null else song
        linked(match)?.let { return it }
        val album = match.album?.takeIf { it.isNotBlank() } ?: return null
        return catalog.searchAll("$album ${match.artist}").albums.firstOrNull {
            normalized(it.title) == normalized(album) && normalized(it.subtitle).contains(normalized(match.artist.substringBefore(',')))
        }
    }
    private fun normalized(value: String) = value.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
}
