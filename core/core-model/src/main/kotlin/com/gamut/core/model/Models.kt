package com.gamut.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class TrackSource { MUSICBRAINZ, ITUNES, YOUTUBE, SPOTIFY, PODCAST }

@Serializable
data class Track(
    val isrc: String,
    val musicBrainzId: String? = null,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long,
    val albumArtUrl: String? = null,
    val source: TrackSource = TrackSource.ITUNES,
    val youtubeVideoId: String? = null,
    val directAudioUrl: String? = null,
    val albumUri: String? = null,
    val isExplicit: Boolean? = null,
    val artists: List<TrackArtist> = emptyList(),
    val transcripts: List<TranscriptSource> = emptyList(),
)

@Serializable
data class TrackArtist(val name: String, val uri: String? = null)

@Serializable
data class TranscriptSource(val url: String, val type: String, val language: String? = null)

/** Optional catalogue fields share one backwards-compatible Room column. */
@Serializable
data class TrackMetadata(
    val isExplicit: Boolean? = null,
    val artists: List<TrackArtist> = emptyList(),
    val transcripts: List<TranscriptSource> = emptyList(),
) {
    fun encode(): String = kotlinx.serialization.json.Json.encodeToString(serializer(), this)
    companion object {
        private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        fun decode(raw: String?): TrackMetadata = raw?.takeIf { it.length <= 100_000 }?.let {
            runCatching { json.decodeFromString(serializer(), it) }.getOrNull()
        } ?: TrackMetadata()
    }
}

@Serializable
data class ResolvedSource(
    val isrc: String,
    val youtubeVideoId: String,
    val resolutionMethod: ResolutionMethod,
    val confidenceScore: Float,
    val verifiedCount: Int,
)

@Serializable
enum class ResolutionMethod { TOPIC_CHANNEL, HARD_FILTER, MANUAL, DIRECT }

@Serializable
data class Playlist(
    val id: Long,
    val name: String,
    val createdAtEpochMs: Long,
    val coverUri: String? = null,
    val tags: List<String> = emptyList(),
)

@Serializable
data class DownloadedTrack(
    val isrc: String,
    val localFilePath: String,
    val downloadedAtEpochMs: Long,
    val fileSizeBytes: Long,
)

sealed interface PlaybackState {
    data object Idle : PlaybackState
    data object Loading : PlaybackState
    data class Playing(val positionMs: Long, val track: Track) : PlaybackState
    data class Paused(val positionMs: Long, val track: Track) : PlaybackState
    data class Error(val message: String) : PlaybackState
}

enum class RepeatMode { OFF, ALL, ONE }

data class QueueState(
    val tracks: List<Track> = emptyList(),
    val currentIndex: Int = -1,
)
interface LocalAudioIndex {
    fun pathFor(isrc: String): String?
}
