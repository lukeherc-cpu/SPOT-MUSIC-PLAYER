package com.gamut.spot

import com.gamut.core.model.Track
import org.junit.Assert.*
import org.junit.Test

class CleanVersionsTest {
    private fun song(id: String, title: String, explicit: Boolean?) = Track(id, title = title, artist = "Chance the Rapper", durationMs = 200_000, isExplicit = explicit)
    private val wraith = song("wraith", "Wraith", true)
    private val clean = song("wraith-clean", "Wraith (Clean)", false)

    @Test fun cleanVersionSitsRightAfterExplicitInSearch() {
        val results = listOf(song("a", "Other", false), wraith, song("b", "Another", false))
        val shown = CleanVersions.insert(results, mapOf("wraith" to clean))
        assertEquals(listOf("a", "wraith", "wraith-clean", "b"), shown.map { it.isrc })
    }

    @Test fun cleanAlreadyInResultsIsMovedNotDuplicated() {
        val results = listOf(clean, song("a", "Other", false), wraith)
        val shown = CleanVersions.insert(results, mapOf("wraith" to clean))
        assertEquals(listOf("a", "wraith", "wraith-clean"), shown.map { it.isrc })
    }

    @Test fun previewKeepsThePairTogether() {
        val list = (1..5).map { song("s$it", "Song $it", false) } + wraith + clean + song("z", "Last", false)
        assertEquals(7, CleanVersions.preview(list, 6).size)
        assertEquals(6, CleanVersions.preview(list.filter { it.isrc != "wraith-clean" }, 6).size)
    }
}
