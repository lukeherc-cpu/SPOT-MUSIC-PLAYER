package com.gamut.spot

import com.gamut.core.model.Track
import com.gamut.core.network.SpotifyCard
import com.gamut.podcasts.PodcastShow
import java.text.Normalizer

/**
 * Orders the sections of a search the way a person reads the query, using Spotify's own top result
 * (from SpotAPI's searchDesktop) plus title matches. Same rules as the desktop app's search.mjs.
 * "the journal" lands on the podcast called The Journal; "chill" or "2000s" lands on playlists first.
 */
internal object SearchRanking {
    enum class Section { Podcasts, Playlists, Artists, Songs, Albums }

    private val moods = Regex(
        "^(?:(?:19|20)?\\d0'?s|chill|chillout|lofi|lo-fi|vibes?|mood|workout|gym|running|party|sleep|sleepy|focus|study|studying|relax|relaxing|calm|happy|sad|love songs?|romantic|throwback|throwbacks|hits|top hits|summer|winter|road trip|rainy day|morning|night|late night|dinner|coffee|cozy|feel good|good vibes|motivation|hype|dance|acoustic|instrumental|piano|ambient|meditation|christmas|holiday|halloween|country|rock|pop|rap|hip hop|r&b|rnb|jazz|classical|indie|edm|latin|k-?pop|metal|punk|soul|funk|gospel|reggae|reggaeton|blues|emo|grunge|disco|oldies|classic rock)(?:\\s+(?:music|songs|hits|mix|playlist|vibes))?$",
        RegexOption.IGNORE_CASE)

    fun fold(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "").replace("&", "and").replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    private fun bare(value: String) = fold(value).removePrefix("the ")
    private fun plain(title: String) = title.replace(Regex("\\s*[(\\[][^)\\]]*[)\\]]"), "").replace(Regex("\\s+-\\s+.*$"), "").trim()
    fun match(query: String, title: String): Int {
        val q = bare(query); val t = bare(title)
        if (q.isEmpty() || t.isEmpty()) return 0
        return when { t == q -> 100; t.startsWith("$q ") || q.startsWith("$t ") -> 70; q in t -> 40; else -> 0 }
    }
    private fun distance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1); cur[0] = i
            for (j in 1..b.length) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            prev = cur
        }
        return prev[b.length]
    }
    /**
     * How well a query names an artist, allowing a typo or two ("chappel roan" is Chappell Roan) and a
     * name still being typed ("chappell r"). Exact names score 100.
     */
    fun artistMatch(query: String, name: String): Int {
        val q = bare(query); val n = bare(name)
        if (q.length < 3 || n.isEmpty()) return if (q == n && q.isNotEmpty()) 100 else 0
        if (q == n) return 100
        val q0 = q.replace(" ", ""); val n0 = n.replace(" ", "")
        if (q0 == n0) return 98
        if (distance(q0, n0) <= maxOf(1, n0.length / 6)) return 92
        if (q.length >= 4 && n.startsWith(q)) return 85
        return match(query, name)
    }
    /** A mood, activity, genre or era, where people want playlists before single songs. */
    fun isMood(query: String) = moods.matches(query.trim().replace(Regex("\\s+"), " "))

    fun order(query: String, tracks: List<Track>, shows: List<PodcastShow>, playlists: List<SpotifyCard>, artists: List<SpotifyCard>, albums: List<SpotifyCard>, spotifyTop: List<String>): List<Section> {
        val top = spotifyTop.firstOrNull()
        val mood = isMood(query)
        val sameTitleSongs = tracks.count { bare(plain(it.title)) == bare(query) }
        fun best(titles: List<String>, bonus: Int) = titles.take(5).mapIndexed { i, title -> match(query, title) + bonus - i }.maxOrNull() ?: 0
        val scores = mutableMapOf(
            Section.Podcasts to best(shows.map { it.title }, 12),
            Section.Artists to artists.take(3).mapIndexed { i, artist ->
                // The artist outranks their own songs: a search that names them shows them first.
                val owns = tracks.take(6).count { fold(it.artist.substringBefore(',')) == fold(artist.title) } >= 2
                val named = artistMatch(query, artist.title)
                named + 10 - i + if (owns && named >= 85) 25 else 0
            }.maxOrNull() ?: 0,
            Section.Songs to maxOf(best(tracks.map { plain(it.title) }, 8),
                tracks.take(5).mapIndexed { i, t -> val a = t.artist.substringBefore(','); val p = plain(t.title); if (bare("$a $p") == bare(query) || bare("$p $a") == bare(query)) 108 - i else 0 }.maxOrNull() ?: 0) - if (sameTitleSongs >= 3) 20 else 0,
            Section.Albums to best(albums.map { it.title }, 4),
            Section.Playlists to best(playlists.map { it.title }, 2),
        )
        // Spotify's own top result counts once the title also matches.
        val spotifySection = when (top) { "podcast" -> Section.Podcasts; "artist" -> Section.Artists; "song" -> Section.Songs; "album" -> Section.Albums; "playlist" -> Section.Playlists; else -> null }
        spotifySection?.let { if ((scores[it] ?: 0) >= 40) scores[it] = scores.getValue(it) + 25 }
        if ((mood || top == "playlist") && playlists.isNotEmpty()) {
            scores[Section.Playlists] = maxOf(scores.getValue(Section.Playlists), 40) + 60
            scores[Section.Songs] = scores.getValue(Section.Songs) - 40
        }
        val lead = scores.filterValues { it >= 40 }.maxByOrNull { it.value }?.key ?: Section.Songs
        val default = listOf(Section.Songs, Section.Podcasts, Section.Playlists, Section.Albums, Section.Artists)
        return listOf(lead) + default.filter { it != lead }
    }
}
