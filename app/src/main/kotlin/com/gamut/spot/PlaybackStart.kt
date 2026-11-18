package com.gamut.spot

import com.gamut.core.model.Track
import kotlin.random.Random

internal fun collectionStartIndex(tracks: List<Track>, shuffle: Boolean, currentId: String?, random: Random = Random.Default): Int {
    require(tracks.isNotEmpty())
    if (!shuffle || tracks.size == 1) return 0
    val choices = tracks.indices.filter { tracks[it].isrc != currentId }.ifEmpty { tracks.indices.toList() }
    return choices[random.nextInt(choices.size)]
}
