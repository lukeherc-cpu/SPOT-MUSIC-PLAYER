package com.gamut.podcasts

import com.gamut.core.model.Track
import com.gamut.core.model.TrackSource
import kotlinx.coroutines.CancellationException
import kotlin.random.Random

data class RecommendedPodcasts(val shows: List<PodcastShow>, val reason: String)

object PodcastRecommendations {
    fun seeds(history: List<Track>, followed: List<PodcastShow>): List<String> {
        val listened = history.filter { it.source == TrackSource.PODCAST }
        val related = listened.mapNotNull { song -> followed.firstOrNull { it.title.equals(song.album, true) } }
        val categories = (related + followed).flatMap { it.categories }.filter { it.isNotBlank() }.distinct()
        return (categories + listened.mapNotNull { it.album } + followed.map { it.title }).distinct().take(5).ifEmpty { listOf("technology", "society culture", "comedy", "science") }
    }
    suspend fun discover(repository: PodcastRepository, history: List<Track>, followed: List<PodcastShow>, rotation: Long): RecommendedPodcasts {
        val last = history.firstOrNull { it.source == TrackSource.PODCAST }
        // Look up the listened show's categories even when it has not been followed.
        val known = if (last?.album != null && followed.none { it.title.equals(last.album, true) }) {
            try { repository.search(last.album!!).firstOrNull { it.title.equals(last.album, true) }?.let { followed + it } ?: followed }
            catch (e: CancellationException) { throw e } catch (_: Exception) { followed }
        } else followed
        val seeds = seeds(history, known)
        val random = Random(rotation.toInt())
        val selected = seeds.shuffled(random).take(2)
        val excluded = (known.map { it.title.lowercase() } + history.filter { it.source == TrackSource.PODCAST }.mapNotNull { it.album?.lowercase() }).toSet()
        val shows = selected.flatMap { query ->
            try { repository.search(query) } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
        }.distinctBy { it.feedUrl }.filterNot { it.title.lowercase() in excluded }.shuffled(random).take(12)
        return RecommendedPodcasts(shows, last?.album?.let { "Because you listened to $it" } ?: known.firstOrNull()?.title?.let { "More like $it" } ?: "A fresh selection for you")
    }
}
