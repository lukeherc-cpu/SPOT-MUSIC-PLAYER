package com.gamut.spot

import com.gamut.core.model.Track
import com.gamut.core.model.TrackSource
import com.gamut.core.network.SpotifyCard
import com.gamut.core.network.SpotifyCatalog
import com.gamut.core.network.SpotifySearch
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlin.random.Random

enum class BrowseGroup { Top, More, Genres, Podcasts, Moods }

enum class BrowseCategory(val title: String, val color: Long, val query: String, val genre: String? = null, val group: BrowseGroup = BrowseGroup.Genres, val podcast: Boolean = false) {
    Music("Music", 0xFFE0148C, "discover new music", group = BrowseGroup.Top),
    Podcasts("Podcasts", 0xFF006450, "popular", group = BrowseGroup.Top, podcast = true),
    MadeForYou("Made For You", 0xFF1E3264, "", group = BrowseGroup.Top),
    NewReleases("New releases", 0xFFE66A20, "new music friday", group = BrowseGroup.Top),
    Charts("Charts", 0xFF8D67AB, "", group = BrowseGroup.Top),
    Fitness("Fitness", 0xFF777777, "workout", group = BrowseGroup.Top),
    Discover("Discover", 0xFF8D67AB, "fresh finds", group = BrowseGroup.More),
    Radio("Radio", 0xFF509BF5, "radio", group = BrowseGroup.More),
    MusicVideos("Music videos", 0xFFE13300, "music video playlist", group = BrowseGroup.More),
    Throwback("Throwback", 0xFFBA5D07, "throwback hits", group = BrowseGroup.More),
    Indie("Indie", 0xFF608108, "indie", "indie"),
    Alternative("Alternative", 0xFFBC5900, "alternative", "alternative"),
    Pop("Pop", 0xFF8D67AB, "pop", "pop"),
    HipHop("Hip-Hop", 0xFFBA5D07, "hip hop", "hip-hop"),
    Rock("Rock", 0xFFE91429, "rock", "rock"),
    RnB("R&B", 0xFFDC148C, "r&b", "r-n-b"),
    Electronic("Dance/Electronic", 0xFF0D73EC, "dance electronic", "edm"),
    Country("Country", 0xFFD84000, "country", "country"),
    Latin("Latin", 0xFFE1118C, "latin", "latin"),
    Ambient("Ambient", 0xFF477D95, "ambient", "ambient"),
    Folk("Folk & Acoustic", 0xFFB49A6D, "folk acoustic", "folk"),
    Jazz("Jazz", 0xFF1E3264, "jazz", "jazz"),
    Classical("Classical", 0xFF7D4B32, "classical", "classical"),
    Comedy("Comedy", 0xFFAF2896, "comedy", group = BrowseGroup.Podcasts, podcast = true),
    TrueCrime("True crime", 0xFF503750, "true crime", group = BrowseGroup.Podcasts, podcast = true),
    News("News & politics", 0xFF477D95, "news politics", group = BrowseGroup.Podcasts, podcast = true),
    Sports("Sports", 0xFF27856A, "sports", group = BrowseGroup.Podcasts, podcast = true),
    Learning("Educational", 0xFF0D73EC, "educational", group = BrowseGroup.Podcasts, podcast = true),
    Business("Business & technology", 0xFF1E3264, "business technology", group = BrowseGroup.Podcasts, podcast = true),
    Chill("Chill", 0xFFD84000, "chill", group = BrowseGroup.Moods),
    Mood("Mood", 0xFFE1118C, "mood", group = BrowseGroup.Moods),
    Party("Party", 0xFF537AA1, "party", group = BrowseGroup.Moods),
    Focus("Focus", 0xFF503750, "focus", group = BrowseGroup.Moods),
    Sleep("Sleep", 0xFF1E3264, "sleep", group = BrowseGroup.Moods),
    Workout("Workout", 0xFF777777, "workout", group = BrowseGroup.Moods),
    Romance("Romance", 0xFF8C1932, "love songs", group = BrowseGroup.Moods),
    Travel("Travel", 0xFF148A08, "road trip", group = BrowseGroup.Moods),
}

@Serializable
data class DailyMix(val id: String, val title: String, val subtitle: String, val tracks: List<Track>) {
    val artwork: String? get() = tracks.firstOrNull { it.albumArtUrl != null }?.albumArtUrl
}
data class BrowseContent(val rails: List<MusicRail> = emptyList(), val songs: List<Track> = emptyList())
data class HomeRecommendations(val mixes: List<DailyMix>, val rails: List<MusicRail>, val songs: List<Track>, val personal: List<DailyMix> = emptyList())

/** Public Spotify discovery plus local listening-history ranking; no private Spotify account feeds. */
class SpotDiscovery @Inject constructor(private val catalog: SpotifyCatalog) {
    suspend fun browse(category: BrowseCategory): BrowseContent {
        if (category == BrowseCategory.MadeForYou) return BrowseContent()
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
        // Every search runs at the same time, so the mixes (and their artwork) arrive after one round trip.
        suspend fun safe(query: String) = try { catalog.searchAll(query) } catch (e: CancellationException) { throw e } catch (_: Exception) { SpotifySearch() }
        val moodQueries = MOODS.mapIndexed { index, mood -> "${mood.query} ${seeds[index % seeds.size]}" }
        val (results, moodResults) = coroutineScope {
            val seedJobs = seeds.map { seed -> async { seed to safe(seed) } }
            val moodJobs = moodQueries.map { query -> async { query to safe(query) } }
            seedJobs.awaitAll().toMap() to moodJobs.awaitAll().toMap()
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
        val personal = buildList {
            // "<Artist> Mix" for the artists heard most, then mood mixes built from searches near those artists.
            seeds.take(3).forEach { seed ->
                val tracks = (music.filter { it.artist.contains(seed, true) } + results[seed]?.tracks.orEmpty()).distinctBy { it.isrc }.shuffled(random).take(30)
                if (tracks.size >= 3) add(DailyMix("artist-mix-${seed.lowercase()}", "$seed Mix", "${tracks.map { it.artist.substringBefore(',') }.distinct().take(3).joinToString(", ")} and more", tracks))
            }
            MOODS.forEachIndexed { index, mood ->
                val seed = seeds[index % seeds.size]
                val tracks = moodResults[moodQueries[index]]?.tracks.orEmpty().distinctBy { it.isrc }.shuffled(random).take(30)
                if (tracks.size >= 3) add(DailyMix("mood-mix-${mood.title.lowercase()}", "${mood.title} Mix", "${mood.blurb} with $seed and more", tracks))
            }
        }
        return HomeRecommendations(mixes, rails, music.take(10), personal)
    }

    private class MoodMix(val title: String, val query: String, val blurb: String)

    companion object {
        private val MOODS = listOf(MoodMix("Chill", "chill", "Laid-back picks"), MoodMix("Upbeat", "upbeat", "Feel-good energy"), MoodMix("Late Night", "late night", "After-hours listening"))
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
