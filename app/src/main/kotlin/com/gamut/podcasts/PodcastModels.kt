package com.gamut.podcasts

import com.gamut.core.model.Track
import com.gamut.core.model.TrackSource
import java.security.MessageDigest
import kotlinx.serialization.Serializable

@Serializable
data class PodcastShow(val id: String, val title: String, val author: String, val feedUrl: String, val artwork: String? = null, val categories: List<String> = emptyList())

@Serializable
data class PodcastEpisode(
    val id: String, val title: String, val audioUrl: String, val description: String = "",
    val published: String = "", val durationMs: Long = 0L, val artwork: String? = null,
    val transcripts: List<com.gamut.core.model.TranscriptSource> = emptyList(),
    val isExplicit: Boolean? = null,
) {
    fun track(show: PodcastShow) = Track(
        isrc = "podcast:" + MessageDigest.getInstance("SHA-256").digest((show.feedUrl + "|" + id).toByteArray()).joinToString("") { "%02x".format(it) },
        title = title, artist = show.author, album = show.title, durationMs = durationMs,
        albumArtUrl = artwork ?: show.artwork, source = TrackSource.PODCAST, directAudioUrl = audioUrl,
        transcripts = transcripts, isExplicit = isExplicit,
    )
}
