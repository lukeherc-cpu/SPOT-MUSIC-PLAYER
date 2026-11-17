package com.gamut.podcasts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PodcastsState(
    val query: String = "", val shows: List<PodcastShow> = emptyList(), val selected: PodcastShow? = null,
    val episodes: List<PodcastEpisode> = emptyList(), val loading: Boolean = false, val error: String? = null,
)

@HiltViewModel
class PodcastsViewModel @Inject constructor(private val repository: PodcastRepository, val store: PodcastStore) : ViewModel() {
    private val _state = MutableStateFlow(PodcastsState())
    val state = _state.asStateFlow()
    private var searchJob: Job? = null
    private var feedJob: Job? = null
    fun search(query: String) {
        searchJob?.cancel()
        _state.value = _state.value.copy(query = query, shows = emptyList(), error = null, loading = query.trim().length >= 2)
        if (query.trim().length < 2) return
        searchJob = viewModelScope.launch {
            delay(350)
            try { _state.value = _state.value.copy(shows = repository.search(query), loading = false) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { _state.value = _state.value.copy(loading = false, error = "Podcast search is unavailable. Try again.") }
        }
    }
    fun open(show: PodcastShow, refresh: Boolean = false) {
        searchJob?.cancel(); feedJob?.cancel()
        _state.value = _state.value.copy(selected = show, episodes = emptyList(), loading = true, error = null)
        feedJob = viewModelScope.launch {
            try { _state.value = _state.value.copy(episodes = repository.episodes(show, refresh), loading = false) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { _state.value = _state.value.copy(loading = false, error = "Could not load this show's audio feed. Try refreshing.") }
        }
    }
    fun back() { feedJob?.cancel(); _state.value = _state.value.copy(selected = null, episodes = emptyList(), error = null, loading = false) }
}
