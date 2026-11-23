package com.gamut.spot

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class YoutubeMusicSongsTest {
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
