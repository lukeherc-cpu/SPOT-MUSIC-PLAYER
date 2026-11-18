package com.gamut.spot

import com.gamut.core.model.Track
import com.gamut.feature.playerui.*
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class PlayerBehaviorTest {
    private val tracks = (0..9).map { Track("song-$it", title = "Song $it", artist = "Artist", durationMs = 60_000) }
    @Test fun shuffleCollectionStartsAcrossTheCollectionAndAvoidsRepeatingTheCurrentSong() {
        val picks = (0..40).map { collectionStartIndex(tracks, true, "song-0", Random(it)) }
        assertFalse(0 in picks)
        assertTrue(picks.distinct().size > 5)
        assertEquals(0, collectionStartIndex(tracks, false, null))
    }
    @Test fun cleanLyricsCensorProviderTextAndWordTokensWithoutChangingTheirTiming() {
        val clean = Track("clean", title = "Song (Clean)", artist = "Artist", durationMs = 60_000)
        val loaded = LyricsState.Loaded(listOf(LyricsLine(1234, "This shit is fucking loud", 5678, listOf(LyricsWord(1500, 1800, "shit")))))
        val filtered = CleanLyrics.apply(clean, loaded) as LyricsState.Loaded
        assertEquals("This ••• is ••• loud", filtered.lines.single().text)
        assertEquals(1234L, filtered.lines.single().timestampMs)
        assertEquals(5678L, filtered.lines.single().endMs)
        assertEquals("•••", filtered.lines.single().words.single().text)
        assertEquals(1500L, filtered.lines.single().words.single().startMs)
        assertEquals("A class of bass musicians", CleanLyrics.text("A class of bass musicians"))
        assertSame(loaded, CleanLyrics.apply(clean.copy(title = "Song", isExplicit = true), loaded))
        assertTrue(CleanLyrics.required(clean.copy(title = "Song", isExplicit = false)))
    }
}
