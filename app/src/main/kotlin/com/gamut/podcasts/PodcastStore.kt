package com.gamut.podcasts

import android.content.Context
import com.gamut.core.model.PlaybackState
import com.gamut.core.model.TrackSource
import com.gamut.core.player.MusicPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class PodcastStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("spot_podcasts", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val _shows = MutableStateFlow(runCatching { json.decodeFromString<List<PodcastShow>>(prefs.getString("shows", "[]")!!) }.getOrDefault(emptyList()))
    val shows = _shows.asStateFlow()
    fun toggle(show: PodcastShow) {
        val next = if (_shows.value.any { it.feedUrl == show.feedUrl }) _shows.value.filterNot { it.feedUrl == show.feedUrl } else (_shows.value + show).takeLast(200)
        prefs.edit().putString("shows", json.encodeToString(next)).apply()
        _shows.value = next
    }
    fun position(id: String) = prefs.getLong("position:$id", 0L).coerceAtLeast(0L)
    /** The newest episode the listener has seen for a show. A newer one shows a blue dot. */
    fun seenEpisode(feedUrl: String): String? = prefs.getString("seen:$feedUrl", null)
    fun markSeen(feedUrl: String, episodeId: String) { prefs.edit().putString("seen:$feedUrl", episodeId).apply() }
    fun observe(player: MusicPlayer, scope: CoroutineScope) {
        scope.launch {
            var lastId: String? = null
            var lastSaved = -1L
            player.playbackState.collect { state ->
                val track = when (state) { is PlaybackState.Playing -> state.track; is PlaybackState.Paused -> state.track; else -> null }
                if (track?.source != TrackSource.PODCAST) return@collect
                val pos = when (state) { is PlaybackState.Playing -> state.positionMs; is PlaybackState.Paused -> state.positionMs; else -> 0L }
                val finished = track.durationMs > 0 && pos >= track.durationMs - 5_000
                if (lastId != track.isrc || kotlin.math.abs(pos - lastSaved) >= 5_000 || state is PlaybackState.Paused) {
                    prefs.edit().putLong("position:${track.isrc}", if (finished) 0 else pos).apply()
                    lastId = track.isrc; lastSaved = pos
                }
            }
        }
    }
}
