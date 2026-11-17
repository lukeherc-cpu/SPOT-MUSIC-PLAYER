package com.gamut.spot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamut.core.database.DownloadIndex
import com.gamut.core.database.RecentlyPlayedRepository
import com.gamut.core.database.TrackDao
import com.gamut.core.database.toDomain
import com.gamut.core.model.*
import com.gamut.core.network.*
import com.gamut.core.player.*
import com.gamut.feature.library.LibraryRepository
import com.gamut.feature.resolver.PrefetchCoordinator
import com.gamut.feature.resolver.TrackDownloadManager
import com.gamut.feature.search.SearchRepository
import com.gamut.podcasts.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class MusicRail(val title: String, val cards: List<SpotifyCard>)
data class SpotHome(val mixes: List<DailyMix> = emptyList(), val rails: List<MusicRail> = emptyList(), val songs: List<Track> = emptyList(), val shows: List<PodcastShow> = emptyList(), val loading: Boolean = true, val error: String? = null)
data class SpotSearch(val query: String = "", val tracks: List<Track> = emptyList(), val rich: SpotifySearch = SpotifySearch(), val shows: List<PodcastShow> = emptyList(), val musicLoading: Boolean = false, val podcastLoading: Boolean = false, val notice: String? = null, val podcastError: String? = null)
data class SpotDetail(val key: String, val title: String, val subtitle: String, val artwork: String? = null, val description: String = "", val tracks: List<Track> = emptyList(), val loading: Boolean = false, val error: String? = null, val spotify: SpotifyCollection? = null, val podcast: PodcastShow? = null, val episodes: List<PodcastEpisode> = emptyList(), val skipped: Int = 0)

data class SpotBrowse(val category: BrowseCategory, val content: BrowseContent = BrowseContent(), val shows: List<PodcastShow> = emptyList(), val loading: Boolean = true, val error: String? = null)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SpotViewModel @Inject constructor(
    private val catalog: SpotifyCatalog,
    private val discovery: SpotDiscovery,
    private val albumLookup: SpotifyAlbumLookup,
    private val trackDao: TrackDao,
    private val searchRepository: SearchRepository,
    private val library: LibraryRepository,
    private val podcastRepository: PodcastRepository,
    val podcastStore: PodcastStore,
    val player: MusicPlayer,
    val downloads: TrackDownloadManager,
    val downloadIndex: DownloadIndex,
    val playbackSettings: PlaybackSettings,
    private val recentRepository: RecentlyPlayedRepository,
    private val prefetch: PrefetchCoordinator,
) : ViewModel() {
    val favorites = library.observeFavorites().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val playlists = library.observePlaylists().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val playlistTracks = playlists.flatMapLatest { list ->
        if (list.isEmpty()) flowOf(emptyMap()) else combine(list.map { p -> library.observeTracks(p.id).map { p.id to it } }) { it.toMap() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val downloadedTracks = downloadIndex.downloads.mapLatest { saved -> saved.keys.toList().chunked(400).flatMap { trackDao.getByIsrcs(it) }.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recents = recentRepository.observe().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val followedShows = podcastStore.shows
    private val _home = MutableStateFlow(SpotHome())
    val home = _home.asStateFlow()
    private val _search = MutableStateFlow(SpotSearch())
    val search = _search.asStateFlow()
    private val _detail = MutableStateFlow<SpotDetail?>(null)
    val detail = _detail.asStateFlow()
    private val _browse = MutableStateFlow<SpotBrowse?>(null)
    val browse = _browse.asStateFlow()
    private val detailStack = mutableListOf<SpotDetail>()
    private var browseJob: Job? = null
    private var homeRefreshedAt = 0L
    private var homeNonce = 0L
    private val _opening = MutableStateFlow<Track?>(null)
    val opening = _opening.asStateFlow()
    private val _importing = MutableStateFlow(false)
    val importing = _importing.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()
    private var searchJob: Job? = null
    private var detailJob: Job? = null
    private var homeJob: Job? = null
    private var playJob: Job? = null
    private val imported = mutableSetOf<String>()
    init {
        viewModelScope.launch {
            combine(recents, favorites) { history, saved -> SpotDiscovery.fingerprint(history, saved) }
                .distinctUntilChanged().debounce(800).collect { refreshHome() }
        }
        viewModelScope.launch {
            var lastId: String? = null
            player.playbackState.collect { state ->
                when (state) {
                    is PlaybackState.Playing -> {
                        if (_opening.value?.isrc == state.track.isrc) _opening.value = null
                        if (lastId != state.track.isrc) { recentRepository.record(state.track); lastId = state.track.isrc }
                    }
                    is PlaybackState.Error -> { _opening.value = null; _messages.tryEmit(state.message) }
                    else -> Unit
                }
            }
        }
        viewModelScope.launch { delay(400); player.warmUp() }
    }
    fun refreshHomeIfStale() {
        if (System.currentTimeMillis() - homeRefreshedAt > 30 * 60_000L) refreshHome()
    }
    fun refreshHome() {
        homeJob?.cancel()
        homeRefreshedAt = System.currentTimeMillis()
        val history = recents.value
        val saved = favorites.value
        val rotation = homeRefreshedAt / (30 * 60_000L) + homeNonce++
        homeJob = viewModelScope.launch {
            _home.update { it.copy(loading = true, error = null) }
            supervisorScope {
                launch {
                    try {
                        val result = discovery.recommendations(history, saved, rotation)
                        check(result.mixes.any { it.tracks.isNotEmpty() })
                        _home.update { it.copy(mixes = result.mixes.filter { mix -> mix.tracks.isNotEmpty() }, rails = result.rails, songs = result.songs, loading = false) }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { _home.update { it.copy(loading = false, error = "Couldn't refresh discovery. Your library is still available.") } }
                }
                launch { try { val shows = podcastRepository.search("popular"); _home.update { it.copy(shows = shows) } } catch (e: CancellationException) { throw e } catch (_: Exception) {} }
            }
        }
    }
    fun openBrowse(category: BrowseCategory) {
        closeDetail()
        _browse.value = SpotBrowse(category, SpotDiscovery.starterContent(category))
        browseJob = viewModelScope.launch {
            try {
                if (category == BrowseCategory.Podcasts) {
                    val shows = podcastRepository.search("popular")
                    _browse.value = SpotBrowse(category, shows = shows, loading = false)
                } else {
                    val content = discovery.browse(category)
                    _browse.value = SpotBrowse(category, content, loading = false)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _browse.update { it?.copy(loading = false, error = "Couldn't load ${category.title}. Tap Retry to try again.") } }
        }
    }
    fun openMix(mix: DailyMix) {
        detailJob?.cancel()
        pushDetail()
        _detail.value = SpotDetail(mix.id, mix.title, "Made for you", mix.artwork, "${mix.subtitle}. Refreshed with your listening history.", mix.tracks)
    }
    fun openAlbum(song: Track) {
        detailJob?.cancel()
        pushDetail()
        _detail.value = SpotDetail("album-for:${song.isrc}", song.album ?: song.title, song.artist, song.albumArtUrl, loading = true)
        detailJob = viewModelScope.launch {
            try {
                val card = albumLookup.find(song) ?: error("Couldn't find this song's album on Spotify.")
                val album = catalog.collection(card.uri)
                _detail.value = SpotDetail(album.uri, album.title, album.subtitle, album.artwork, album.description, album.tracks, spotify = album)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _detail.update { it?.copy(loading = false, error = "Couldn't find this song's album on Spotify.") } }
        }
    }
    private fun pushDetail() { _detail.value?.takeUnless { it.loading }?.let { detailStack.add(it) } }
    fun backDetail() {
        detailJob?.cancel()
        if (_detail.value != null) _detail.value = detailStack.removeLastOrNull()
        else { browseJob?.cancel(); _browse.value = null }
    }
    fun search(query: String) {
        searchJob?.cancel()
        val q = query.trim()
        _search.value = SpotSearch(query = query, musicLoading = q.length >= 2, podcastLoading = q.length >= 2)
        if (q.length < 2) return
        searchJob = viewModelScope.launch {
            delay(300)
            supervisorScope {
                launch {
                    try {
                        searchRepository.search(q).collect { result ->
                            _search.update { it.copy(tracks = result.tracks, musicLoading = !result.isFinal, notice = result.catalogNotice) }
                            prefetch.prefetch(result.tracks.take(2))
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { _search.update { it.copy(musicLoading = false, notice = "Music search is unavailable. Try again.") } }
                }
                launch {
                    try { val result = catalog.searchAll(q); _search.update { it.copy(rich = result) } }
                    catch (e: CancellationException) { throw e } catch (_: Exception) {}
                }
                launch {
                    try { val shows = podcastRepository.search(q); _search.update { it.copy(shows = shows, podcastLoading = false) } }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { _search.update { it.copy(podcastLoading = false, podcastError = "Podcast search is unavailable. Try again.") } }
                }
            }
        }
    }
    fun openCard(card: SpotifyCard) {
        pushDetail()
        if (card.type == "show") {
            detailJob?.cancel()
            _detail.value = SpotDetail(card.uri, card.title, "Podcast", card.artwork, loading = true)
            detailJob = viewModelScope.launch {
                try {
                    val candidates = podcastRepository.search(card.title)
                    val match = candidates.firstOrNull { it.title.equals(card.title, ignoreCase = true) } ?: candidates.firstOrNull() ?: error("No publisher feed is available for this show")
                    loadPodcast(match)
                } catch (e: CancellationException) { throw e } catch (_: Exception) { _detail.update { it?.copy(loading = false, error = "This Spotify show has no available publisher feed.") } }
            }
            return
        }
        detailJob?.cancel()
        _detail.value = SpotDetail(card.uri, card.title, card.subtitle, card.artwork, loading = true)
        detailJob = viewModelScope.launch {
            try {
                val collection = catalog.collection(card.uri)
                _detail.value = SpotDetail(card.uri, collection.title, collection.subtitle, collection.artwork ?: card.artwork, collection.description, collection.tracks, spotify = collection, skipped = collection.skipped)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _detail.update { it?.copy(loading = false, error = e.message ?: "Couldn't load this Spotify collection") } }
        }
    }
    fun openPlaylist(id: String) {
        detailJob?.cancel()
        if (id == "liked") {
            _detail.value = SpotDetail("liked", "Liked Songs", "Playlist · ${favorites.value.size} songs", tracks = favorites.value)
            detailJob = viewModelScope.launch { favorites.collect { tracks -> _detail.update { it?.copy(tracks = tracks, subtitle = "Playlist · ${tracks.size} songs") } } }
        } else {
            val playlist = playlists.value.firstOrNull { it.id.toString() == id } ?: return
            _detail.value = SpotDetail(id, playlist.name, "Playlist", playlist.coverUri, tracks = playlistTracks.value[playlist.id].orEmpty())
            detailJob = viewModelScope.launch { library.observeTracks(playlist.id).collect { tracks -> _detail.update { it?.copy(tracks = tracks, subtitle = "Playlist · ${tracks.size} songs", artwork = playlist.coverUri ?: tracks.firstOrNull()?.albumArtUrl) } } }
        }
    }
    fun openShow(show: PodcastShow, refresh: Boolean = false) {
        detailJob?.cancel()
        _detail.value = SpotDetail(show.feedUrl, show.title, show.author, show.artwork, loading = true, podcast = show)
        detailJob = viewModelScope.launch { loadPodcast(show, refresh) }
    }
    private suspend fun loadPodcast(show: PodcastShow, refresh: Boolean = false) {
        try {
            val episodes = podcastRepository.episodes(show, refresh)
            _detail.value = SpotDetail(show.feedUrl, show.title, show.author, show.artwork, tracks = episodes.map { it.track(show) }, podcast = show, episodes = episodes)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { _detail.update { it?.copy(loading = false, podcast = show, error = "Couldn't load this publisher's feed. Tap Retry to try again.") } }
    }
    fun closeDetail() { detailJob?.cancel(); browseJob?.cancel(); detailStack.clear(); _detail.value = null; _browse.value = null }
    fun retryDetail() { _detail.value?.let { d -> if (d.podcast != null) openShow(d.podcast, true) else if (d.key.startsWith("spotify:")) openCard(SpotifyCard(d.key, d.title, d.subtitle, d.artwork, d.key.split(':')[1])) } }
    fun play(track: Track, queue: List<Track>) {
        playJob?.cancel()
        _opening.value = track
        player.setShuffle(false)
        val list = queue.ifEmpty { listOf(track) }
        val index = list.indexOfFirst { it.isrc == track.isrc }.coerceAtLeast(0)
        player.setQueue(list, index)
        player.skipTo(index)
        if (track.source == TrackSource.PODCAST) {
            val resume = podcastStore.position(track.isrc)
            if (resume > 0) playJob = viewModelScope.launch {
                val started = withTimeoutOrNull(30_000) { player.playbackState.first { it is PlaybackState.Playing && it.track.isrc == track.isrc } }
                if (started != null) player.seekTo(resume)
            }
        }
    }
    fun favorite(track: Track) = viewModelScope.launch {
        val adding = favorites.value.none { it.isrc == track.isrc }
        library.toggleFavorite(track)
        if (adding) prefetch.prepareSaved(track)
    }
    fun createPlaylist(name: String) = viewModelScope.launch { if (name.isNotBlank()) { val id = library.createPlaylist(name.trim().take(100)); _messages.emit("Created ${name.trim()}"); delay(100); openPlaylist(id.toString()) } }
    fun addToPlaylist(id: Long, track: Track) = viewModelScope.launch { library.appendTrack(id, track); prefetch.prepareSaved(track); _messages.emit("Added to playlist") }
    fun deletePlaylist(id: Long) = viewModelScope.launch { library.deletePlaylist(id); closeDetail(); _messages.emit("Playlist deleted") }
    fun importSpotify(link: String) {
        if (_importing.value) return
        _importing.value = true
        viewModelScope.launch {
            try {
                val collection = catalog.collection(link)
                require(collection.tracks.isNotEmpty()) { "This collection has no available music to import" }
                // Fetch every page first, then commit the complete local collection atomically.
                val id = library.importPlaylist(collection.title, collection.artwork, collection.tracks)
                imported += collection.uri
                _messages.emit("Imported ${collection.tracks.distinctBy { it.isrc }.size} songs${if (collection.skipped > 0) " · ${collection.skipped} unavailable items skipped" else ""}")
                delay(100); openPlaylist(id.toString())
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _messages.emit(e.message ?: "Spotify import failed. Check that the playlist is public.") }
            finally { _importing.value = false }
        }
    }
    fun isImported(uri: String) = uri in imported
    fun download(track: Track) { downloads.download(listOf(track)); _messages.tryEmit("Downloading ${track.title}") }
}
