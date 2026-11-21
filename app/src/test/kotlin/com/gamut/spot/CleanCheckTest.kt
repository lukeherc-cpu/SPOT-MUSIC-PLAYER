package com.gamut.spot

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CleanCheckTest {
    @Test fun swearWordsAreFoundButNotInsideOtherWords() {
        assertEquals(listOf("fuckin", "shit"), CleanWords.swears("Fuckin' with my shit, damn"))
        assertTrue(CleanWords.swears("Class assignment, shiitake").isEmpty())
    }

    @Test fun swearLinesBecomePaddedMergedWindows() {
        val windows = CleanWords.windows(listOf(Triple(10_000L, 14_000L, "what the fuck"), Triple(14_000L, 18_000L, "nice day"), Triple(15_000L, 19_000L, "bitch please")), 200_000)
        assertEquals(1, windows.size)
        assertEquals(8_500L, windows[0].startMs)
    }

    @Test fun verdictNeedsTheExplicitVersionToSwearAndTheCandidateToSingWithout() {
        assertEquals(CleanVerdict.CLEAN, CleanWords.judge(listOf("what the fuck man"), listOf("what the man")).verdict)
        assertEquals(CleanVerdict.EXPLICIT, CleanWords.judge(listOf("what the fuck man"), listOf("what the fuck man")).verdict)
        assertEquals(CleanVerdict.NO_VOCALS, CleanWords.judge(listOf("what the fuck man here"), listOf("")).verdict)
        assertEquals(CleanVerdict.UNVERIFIED, CleanWords.judge(listOf("nothing to see here"), listOf("nothing to see here")).verdict)
    }

    @Test fun youtubeMusicResultsKeepTheExplicitBadge() {
        fun row(id: String, duration: String, explicit: Boolean) = """{"musicResponsiveListItemRenderer":{"playlistItemData":{"videoId":"$id"},
            "flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Wraith"}]}}},
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Chance the Rapper","navigationEndpoint":{"browseEndpoint":{"browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_ARTIST"}}}}},{"text":" • "},{"text":"$duration"}]}}}]
            ${if (explicit) ""","badges":[{"musicInlineBadgeRenderer":{"icon":{"iconType":"MUSIC_EXPLICIT_BADGE"}}}]""" else ""}}}"""
        val songs = YoutubeMusicSongs.parse(Json.parseToJsonElement("""{"contents":[${row("EEEEEEEEEEE", "3:20", true)},${row("CCCCCCCCCCC", "3:19", false)}]}"""))
        assertEquals(listOf("EEEEEEEEEEE" to true, "CCCCCCCCCCC" to false), songs.map { it.videoId to it.explicit })
        assertEquals(199_000L, songs[1].durationMs)
        assertEquals("Chance the Rapper", songs[1].artist)
    }
}
