package com.gamut.spot

import com.gamut.core.model.Track

/** Keeps a clean version the person made right next to its explicit original in a list of songs. */
object CleanVersions {
    fun insert(tracks: List<Track>, versions: Map<String, Track>): List<Track> {
        if (versions.isEmpty() || tracks.none { it.isrc in versions }) return tracks
        val cleanIds = tracks.mapNotNull { versions[it.isrc]?.isrc }.toSet()
        val out = ArrayList<Track>(tracks.size + cleanIds.size)
        for (track in tracks) {
            if (track.isrc in cleanIds) continue
            out += track
            versions[track.isrc]?.let { clean -> if (out.none { it.isrc == clean.isrc }) out += clean }
        }
        return out
    }

    /** The first [count] songs, plus the clean version when the last one shown has it right after. */
    fun preview(tracks: List<Track>, count: Int): List<Track> {
        if (tracks.size <= count) return tracks
        val last = tracks[count - 1]; val next = tracks[count]
        val pair = last.isExplicit == true && next.isExplicit != true && core(next.title) == core(last.title)
        return tracks.take(if (pair) count + 1 else count)
    }

    private fun core(title: String) = title.substringBefore(" (").substringBefore(" - ").trim().lowercase()
}
