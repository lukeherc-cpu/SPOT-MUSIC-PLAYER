package com.gamut.spot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamut.core.database.DownloadIndex
import com.gamut.core.database.RecentlyPlayedRepository
import com.gamut.core.database.TrackDao
import com.gamut.core.database.toDomain
import com.gamut.core.database.toEntity
import com.gamut.core.sync.FollowRepository
import com.gamut.core.model.*
import kotlinx.serialization.builtins.serializer
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
data class HomeEpisode(val show: PodcastShow, val episode: PodcastEpisode)
data class SpotHome(val mixes: List<DailyMix> = emptyList(), val personal: List<DailyMix> = emptyList(), val newEpisodes: List<HomeEpisode> = emptyList(), val rails: List<MusicRail> = emptyList(), val songs: List<Track> = emptyList(), val shows: List<PodcastShow> = emptyList(), val podcastReason: String = "", val episodes: List<HomeEpisode> = emptyList(), val videos: List<YoutubeCandidate> = emptyList(), val loading: Boolean = true, val podcastsLoading: Boolean = true, val videosLoading: Boolean = true, val error: String? = null)
data class SpotSearch(val query: String = "", val tracks: List<Track> = emptyList(), val rich: SpotifySearch = SpotifySearch(), val shows: List<PodcastShow> = emptyList(), val youtube: List<Track> = emptyList(), val youtubeLoading: Boolean = false, val youtubeError: String? = null, val musicLoading: Boolean = false, val podcastLoading: Boolean = false, val notice: String? = null, val podcastError: String? = null)
data class SpotDetail(val key: String, val title: String, val subtitle: String, val artwork: String? = null, val description: String = "", val tracks: List<Track> = emptyList(), val loading: Boolean = false, val error: String? = null, val spotify: SpotifyCollection? = null, val podcast: PodcastShow? = null, val episodes: List<PodcastEpisode> = emptyList(), val skipped: Int = 0)

internal object CleanMatch {
    val cleanLabel = Regex("\\bclean\\b|\\bedited\\b|radio edit|censored|clean version", RegexOption.IGNORE_CASE)
    /** A title without version labels: "Song (feat. X) - Radio Edit" becomes "song". */
    fun core(title: String): String = title.lowercase()
        .replace(Regex("\\(.*?\\)|\\[.*?]"), " ")
        .replace(Regex("\\s+-\\s+.*(edit|clean|version|remaster|explicit|mix).*$"), " ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    fun tokens(value: String): Set<String> = value.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 1 || it.any(Char::isDigit) }.toSet()
}

/** YouTube uploads the listener can pick from when no clean version is found on its own. */
data class CleanChoices(val song: Track, val collectionKey: String?, val videos: List<YoutubeCandidate> = emptyList(), val loading: Boolean = true, val listened: Int = 0, val swore: Int = 0, val checking: String? = null, val notes: Map<String, CleanVerdict> = emptyMap(), val controlVideoId: String? = null)

data class SpotifyAccountState(val signedIn: Boolean = false, val name: String? = null, val loading: Boolean = false, val playlists: List<SpotifyCard> = emptyList(), val mixes: List<SpotifyCard> = emptyList(), val error: String? = null, val progress: String? = null)

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
    private val youtube: YoutubeMusicSearchSource,
    private val artistFollows: FollowRepository,
    val podcastSubtitles: com.gamut.speech.PodcastSubtitles,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val avatars: com.gamut.account.AvatarRepository,
    private val spotifyAccount: SpotifyAccountStore,
    private val cleanVerifier: CleanVerifier,
) : ViewModel() {
    // Plays per song, kept on this device. Used for "Your most listened to" on artist pages.
    private val plays by lazy { appContext.getSharedPreferences("cadence_play_counts", android.content.Context.MODE_PRIVATE) }
    val favorites = library.observeFavorites().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val playlists = library.observePlaylists().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val playlistTracks = playlists.flatMapLatest { list ->
        if (list.isEmpty()) flowOf(emptyMap()) else combine(list.map { p -> library.observeTracks(p.id).map { p.id to it } }) { it.toMap() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val downloadedTracks = downloadIndex.downloads.mapLatest { saved -> saved.keys.toList().chunked(400).flatMap { trackDao.getByIsrcs(it) }.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recents = recentRepository.observe().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val followedShows = podcastStore.shows
    val followedArtists = artistFollows.followedKeys.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    // The last mixes are kept on the device, so home shows them (and their artwork) at once while fresh ones load.
    private val homeCache by lazy { appContext.getSharedPreferences("cadence_home", android.content.Context.MODE_PRIVATE) }
    private val homeJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    private val mixList = kotlinx.serialization.builtins.ListSerializer(DailyMix.serializer())
    private fun cachedMixes(key: String): List<DailyMix> {
        val raw = homeCache.getString(key, null) ?: return emptyList()
        return runCatching { homeJson.decodeFromString(mixList, raw) }.getOrDefault(emptyList())
    }
    // Clean versions the person made, keyed by the explicit song, so search shows both versions together.
    private val cleanPrefs by lazy { appContext.getSharedPreferences("cadence_clean", android.content.Context.MODE_PRIVATE) }
    private val cleanMap = kotlinx.serialization.builtins.MapSerializer(String.serializer(), Track.serializer())
    private val cleanVersions: MutableMap<String, Track> by lazy {
        runCatching { homeJson.decodeFromString(cleanMap, cleanPrefs.getString("versions", null) ?: "{}") }.getOrDefault(emptyMap()).toMutableMap()
    }
    private fun withCleanVersions(tracks: List<Track>): List<Track> = CleanVersions.insert(tracks, cleanVersions)
    private val _home = MutableStateFlow(SpotHome(mixes = cachedMixes("mixes"), personal = cachedMixes("personal")))
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
    private val _newShows = MutableStateFlow<Set<String>>(emptySet())
    /** Followed shows whose latest episode came out since the listener last opened the show. */
    val newShows = _newShows.asStateFlow()
    private val _playContext = MutableStateFlow<String?>(null)
    /** The collection the queue was started from, so its play button can show pause. */
    val playContext = _playContext.asStateFlow()
    private var lyricsJob: Job? = null
    private val _account = MutableStateFlow(SpotifyAccountState())
    val account = _account.asStateFlow()
    private var accountJob: Job? = null
    private val _cleanChoices = MutableStateFlow<CleanChoices?>(null)
    val cleanChoices = _cleanChoices.asStateFlow()
    private val _cleanSearching = MutableStateFlow<String?>(null)
    /** The song whose clean version is being looked for, so the menu can show progress. */
    val cleanSearching = _cleanSearching.asStateFlow()
    private val _opening = MutableStateFlow<Track?>(null)
    val opening = _opening.asStateFlow()
    private val _importing = MutableStateFlow(false)
    val importing = _importing.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()
    private var youtubeJob: Job? = null
    private var searchJob: Job? = null
    private var detailJob: Job? = null
    private var homeJob: Job? = null
    private var playJob: Job? = null
    private val imported = mutableSetOf<String>()
    init {
        viewModelScope.launch {
            combine(recents, favorites, followedShows) { history, saved, shows -> SpotDiscovery.fingerprint(history, saved) + history.filter { it.source == TrackSource.PODCAST }.take(15).joinToString { it.isrc } + shows.joinToString { it.feedUrl } }
                .distinctUntilChanged().debounce(800).collect { refreshHome() }
        }
        viewModelScope.launch {
            var lastId: String? = null
            player.playbackState.collect { state ->
                when (state) {
                    is PlaybackState.Playing -> {
                        if (_opening.value?.isrc == state.track.isrc) _opening.value = null
                        if (lastId != state.track.isrc) { recentRepository.record(state.track); countPlay(state.track); lastId = state.track.isrc; preloadWords(state.track) }
                    }
                    is PlaybackState.Error -> { _opening.value = null; _messages.tryEmit(state.message) }
                    else -> Unit
                }
            }
        }
        viewModelScope.launch { delay(400); player.warmUp() }
        // Songs played before get their stream links ready in the background, so they start faster.
        viewModelScope.launch { delay(3_000); prefetch.prepareRecent(recents.first { it.isNotEmpty() }.take(6)) }
        viewModelScope.launch { avatars.model(null).collect { SpotProfile.photo = it } }
        viewModelScope.launch(Dispatchers.IO) {
            spotifyAccount.cookie()?.let { cookie -> catalog.setAccount(cookie); _account.value = SpotifyAccountState(signedIn = true) }
        }
    }
    // Lyrics start loading as soon as a song plays, not when the cover is flipped. Podcast subtitles already start on play.
    private fun preloadWords(track: Track) {
        if (track.source == TrackSource.PODCAST) { markEpisodePlayed(track); return }
        lyricsJob?.cancel()
        lyricsJob = viewModelScope.launch {
            try { com.gamut.feature.playerui.LyricsRepository.fetchLyrics(lyricTrack(track), track.durationMs) } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        }
    }
    private fun markEpisodePlayed(track: Track) {
        val show = followedShows.value.firstOrNull { it.title.equals(track.album, true) } ?: return
        _home.value.newEpisodes.firstOrNull { it.show.feedUrl == show.feedUrl && it.episode.track(it.show).isrc == track.isrc }?.let { podcastStore.markSeen(show.feedUrl, it.episode.id); _newShows.update { set -> set - show.feedUrl } }
    }
    private fun countPlay(track: Track) {
        if (track.source == TrackSource.PODCAST) return
        val all = plays.all
        val editor = plays.edit().putInt(track.isrc, ((all[track.isrc] as? Int) ?: 0) + 1)
        // Keep the store small: drop the least played songs past 2,000 entries.
        if (all.size > 2000) all.entries.sortedBy { (it.value as? Int) ?: 0 }.take(all.size - 2000).forEach { editor.remove(it.key) }
        editor.apply()
    }
    fun setProfilePhoto(uri: android.net.Uri) {
        viewModelScope.launch { avatars.set(null, appContext.contentResolver, uri).onFailure { _messages.tryEmit("Could not use that photo. Try another image.") } }
    }
    fun removeProfilePhoto() { viewModelScope.launch { avatars.remove(null) } }
    suspend fun mostPlayedBy(artist: String, limit: Int = 5): List<Track> = withContext(Dispatchers.IO) {
        val needle = artist.trim().lowercase()
        if (needle.isEmpty()) return@withContext emptyList()
        val counts = plays.all.mapNotNull { (k, v) -> (v as? Int)?.let { k to it } }.sortedByDescending { it.second }.take(400)
        val tracks = counts.map { it.first }.chunked(400).flatMap { trackDao.getByIsrcs(it) }.map { it.toDomain() }.associateBy { it.isrc }
        counts.mapNotNull { tracks[it.first] }.filter { t -> t.artist.split(',').any { it.trim().lowercase() == needle } }.take(limit)
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
            _home.update { it.copy(loading = it.mixes.isEmpty(), podcastsLoading = true, videosLoading = true, error = null) }
            supervisorScope {
                launch {
                    try {
                        val result = discovery.recommendations(history, saved, rotation)
                        check(result.mixes.any { it.tracks.isNotEmpty() })
                        val mixes = result.mixes.filter { mix -> mix.tracks.isNotEmpty() }
                        _home.update { it.copy(mixes = mixes, personal = result.personal, rails = result.rails, songs = result.songs, loading = false) }
                        withContext(Dispatchers.IO) { homeCache.edit().putString("mixes", homeJson.encodeToString(mixList, mixes.map { it.copy(tracks = it.tracks.take(40)) })).putString("personal", homeJson.encodeToString(mixList, result.personal)).apply() }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { _home.update { it.copy(loading = false, error = if (it.mixes.isEmpty()) "Couldn't refresh discovery. Your library is still available." else null) } }
                }
                launch {
                    try {
                        val result = PodcastRecommendations.discover(podcastRepository, history, followedShows.value, rotation)
                        _home.update { it.copy(shows = result.shows, podcastReason = result.reason) }
                        val previews = (followedShows.value.take(1) + result.shows.take(2)).distinctBy { it.feedUrl }.mapNotNull { show ->
                            try { podcastRepository.episodes(show).firstOrNull()?.let { HomeEpisode(show, it) } } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                        }
                        _home.update { it.copy(episodes = previews) }
                    } catch (e: CancellationException) { throw e } catch (_: Exception) {}
                    finally { if (currentCoroutineContext().isActive) _home.update { it.copy(podcastsLoading = false) } }
                }
                launch { refreshNewEpisodes() }
                launch {
                    try {
                        val artist = SpotDiscovery.seedArtists(history, saved).first()
                        val videos = youtube.searchBroad("$artist official music video").filterNot { it.channelIsTopicChannel }.take(6)
                        _home.update { it.copy(videos = videos) }
                    } catch (e: CancellationException) { throw e } catch (_: Exception) {}
                    finally { if (currentCoroutineContext().isActive) _home.update { it.copy(videosLoading = false) } }
                }
            }
        }
    }
    private suspend fun refreshNewEpisodes() {
        val shows = followedShows.value.take(16)
        if (shows.isEmpty()) { _home.update { it.copy(newEpisodes = emptyList()) }; _newShows.value = emptySet(); return }
        val latest = coroutineScope {
            shows.map { show -> async { try { podcastRepository.episodes(show).firstOrNull()?.let { HomeEpisode(show, it) } } catch (e: CancellationException) { throw e } catch (_: Exception) { null } } }.awaitAll().filterNotNull()
        }
        val fresh = latest.filter { item ->
            when (val seen = podcastStore.seenEpisode(item.show.feedUrl)) {
                null -> { podcastStore.markSeen(item.show.feedUrl, item.episode.id); false } // First check after following: nothing is new yet.
                else -> seen != item.episode.id && podcastStore.position(item.episode.track(item.show).isrc) == 0L
            }
        }
        _newShows.value = fresh.mapTo(hashSetOf()) { it.show.feedUrl }
        _home.update { it.copy(newEpisodes = latest.sortedByDescending { item -> publishedAt(item.episode.published) }) }
    }
    private fun publishedAt(value: String): Long = runCatching { java.time.ZonedDateTime.parse(value.trim(), java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
        .recoverCatching { java.time.OffsetDateTime.parse(value.trim()).toInstant().toEpochMilli() }.getOrDefault(0L)
    fun openBrowse(category: BrowseCategory) {
        closeDetail()
        _browse.value = SpotBrowse(category, SpotDiscovery.starterContent(category))
        browseJob = viewModelScope.launch {
            try {
                if (category == BrowseCategory.Podcasts) {
                    val shows = PodcastRecommendations.discover(podcastRepository, recents.value, followedShows.value, System.currentTimeMillis() / 1800000).shows
                    _browse.value = SpotBrowse(category, shows = shows, loading = false)
                } else if (category.podcast) {
                    _browse.value = SpotBrowse(category, shows = podcastRepository.search(category.query), loading = false)
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
        youtubeJob?.cancel()
        val q = query.trim()
        _search.value = SpotSearch(query = query, musicLoading = q.length >= 2, podcastLoading = q.length >= 2)
        if (q.length < 2) return
        searchJob = viewModelScope.launch {
            delay(300)
            supervisorScope {
                launch {
                    try {
                        searchRepository.search(q).collect { result ->
                            _search.update { it.copy(tracks = withCleanVersions(result.tracks), musicLoading = !result.isFinal, notice = result.catalogNotice) }
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
    fun searchYoutube() {
        val query = _search.value.query.trim()
        if (query.length < 2 || _search.value.youtubeLoading || _search.value.youtube.isNotEmpty()) return
        youtubeJob?.cancel()
        _search.update { it.copy(youtubeLoading = true, youtubeError = null) }
        youtubeJob = viewModelScope.launch {
            try {
                val tracks = youtube.searchBroad(query).filter { isValidYoutubeVideoId(it.videoId) }.map {
                    Track("youtube:${it.videoId}", title = it.title, artist = it.channelName, durationMs = it.durationMs, albumArtUrl = "https://i.ytimg.com/vi/${it.videoId}/hqdefault.jpg", source = TrackSource.YOUTUBE, youtubeVideoId = it.videoId)
                }
                _search.update { it.copy(youtube = tracks, youtubeLoading = false) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _search.update { it.copy(youtubeLoading = false, youtubeError = "YouTube search is unavailable. Try again.") } }
        }
    }
    fun artistChoices(song: Track): List<TrackArtist> = song.artists.ifEmpty {
        song.artist.split(Regex("\\s*(?:,|;| feat\\.? | ft\\.? | featuring )\\s*", RegexOption.IGNORE_CASE)).filter { it.isNotBlank() }.map { TrackArtist(it.trim()) }
    }
    private val artistProfiles = linkedMapOf<String, Deferred<SpotifyCollection>>()
    private suspend fun artistProfile(artist: TrackArtist): SpotifyCollection {
        val key = artist.uri ?: artist.name.lowercase()
        val pending = artistProfiles.getOrPut(key) { viewModelScope.async {
            val uri = artist.uri ?: catalog.searchAll(artist.name).artists.firstOrNull { it.title.equals(artist.name, true) }?.uri ?: error("Artist unavailable")
            catalog.collection(uri)
        } }
        if (artistProfiles.size > 32) artistProfiles.entries.firstOrNull { it.key != key && it.value.isCompleted }?.let { artistProfiles.remove(it.key) }
        return try { pending.await() } catch (e: CancellationException) { throw e } catch (e: Exception) { artistProfiles.remove(key); throw e }
    }
    suspend fun artistPortrait(artist: TrackArtist): String? = try { artistProfile(artist).artwork } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
    suspend fun lyricTrack(track: Track): Track {
        if (track.isExplicit != null || track.source != TrackSource.SPOTIFY) return track
        return try {
            val match = catalog.searchAll("${track.artist} ${track.title}").tracks.firstOrNull { it.isrc == track.isrc }
            if (match == null) track else track.copy(isExplicit = match.isExplicit, artists = match.artists.ifEmpty { track.artists }).also { trackDao.upsert(it.toEntity()) }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { track }
    }
    fun openArtist(artist: TrackArtist) {
        detailJob?.cancel(); pushDetail()
        _detail.value = SpotDetail("artist-for:${artist.name}", artist.name, "Artist", loading = true)
        detailJob = viewModelScope.launch {
            try {
                val value = artistProfile(artist)
                _detail.value = SpotDetail(value.uri, value.title, value.subtitle, value.artwork, value.description, value.tracks, spotify = value)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _detail.update { it?.copy(loading = false, error = "Couldn't load this artist.") } }
        }
    }
    fun followArtist(detail: SpotDetail) = viewModelScope.launch { artistFollows.toggle(detail.key, detail.title, detail.artwork) }
    suspend fun podcastTranscript(song: Track): PodcastTranscript? {
        var track = song
        if (track.transcripts.isEmpty()) {
            val show = followedShows.value.firstOrNull { it.title.equals(song.album, true) }
                ?: song.album?.let { title -> podcastRepository.search(title).firstOrNull { it.title.equals(title, true) } }
            val episode = show?.let { podcastRepository.episodes(it).firstOrNull { episode -> episode.audioUrl == song.directAudioUrl || episode.track(it).isrc == song.isrc } }
            if (episode != null && episode.transcripts.isNotEmpty()) {
                track = song.copy(transcripts = episode.transcripts)
                trackDao.upsert(track.toEntity())
            }
        }
        return podcastRepository.transcript(track)
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
            episodes.firstOrNull()?.let { latest -> if (followedShows.value.any { it.feedUrl == show.feedUrl }) { podcastStore.markSeen(show.feedUrl, latest.id); _newShows.update { it - show.feedUrl } } }
            _detail.value = SpotDetail(show.feedUrl, show.title, show.author, show.artwork, tracks = episodes.map { it.track(show) }, podcast = show, episodes = episodes)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { _detail.update { it?.copy(loading = false, podcast = show, error = "Couldn't load this publisher's feed. Tap Retry to try again.") } }
    }
    fun closeDetail() { detailJob?.cancel(); browseJob?.cancel(); detailStack.clear(); _detail.value = null; _browse.value = null }
    fun retryDetail() { _detail.value?.let { d -> if (d.podcast != null) openShow(d.podcast, true) else if (d.key.startsWith("artist-for:")) openArtist(TrackArtist(d.title)) else if (d.key.startsWith("spotify:")) openCard(SpotifyCard(d.key, d.title, d.subtitle, d.artwork, d.key.split(':')[1])) } }
    fun play(track: Track, queue: List<Track>, context: String? = null) {
        playJob?.cancel()
        _playContext.value = context
        _opening.value = track
        val list = queue.ifEmpty { listOf(track) }
        val index = list.indexOfFirst { it.isrc == track.isrc }.coerceAtLeast(0)
        player.setQueue(list, index)
        // A shuffled queue pins the explicitly selected item at position zero.
        player.skipTo(if (player.shuffleEnabled.value) 0 else index)
        if (track.source == TrackSource.PODCAST) {
            val resume = podcastStore.position(track.isrc)
            if (resume > 0) playJob = viewModelScope.launch {
                val started = withTimeoutOrNull(30_000) { player.playbackState.first { it is PlaybackState.Playing && it.track.isrc == track.isrc } }
                if (started != null) player.seekTo(resume)
            }
        }
    }
    fun playCollection(tracks: List<Track>, context: String? = null) {
        if (tracks.isEmpty()) return
        val current = when (val state = player.playbackState.value) {
            is PlaybackState.Playing -> state.track.isrc
            is PlaybackState.Paused -> state.track.isrc
            else -> null
        }
        play(tracks[collectionStartIndex(tracks, player.shuffleEnabled.value, current)], tracks, context)
    }
    fun favorite(track: Track) = viewModelScope.launch {
        val adding = favorites.value.none { it.isrc == track.isrc }
        library.toggleFavorite(track)
        if (adding) prefetch.prepareSaved(track)
    }
    fun createPlaylist(name: String) = viewModelScope.launch { if (name.isNotBlank()) { val id = library.createPlaylist(name.trim().take(100)); _messages.emit("Created ${name.trim()}"); delay(100); openPlaylist(id.toString()) } }
    fun saveToLiked(track: Track) = viewModelScope.launch {
        library.addFavorite(track, System.currentTimeMillis()); prefetch.prepareSaved(track)
    }
    fun enqueue(track: Track) { player.addToQueue(track); _messages.tryEmit("Added to queue") }
    fun removeFromCollection(track: Track, key: String) = viewModelScope.launch {
        if (key == "liked") { if (favorites.value.any { it.isrc == track.isrc }) library.toggleFavorite(track) }
        else key.toLongOrNull()?.let { library.removeTrack(it, track.isrc) }
    }
    fun setPlaylistMembership(id: Long, track: Track, included: Boolean) = viewModelScope.launch {
        if (included) { library.appendTrack(id, track); prefetch.prepareSaved(track) } else library.removeTrack(id, track.isrc)
    }
    fun createPlaylistWithTrack(name: String, track: Track) = viewModelScope.launch {
        if (name.isNotBlank()) { val id = library.createPlaylist(name.trim().take(100)); library.appendTrack(id, track); prefetch.prepareSaved(track); _messages.emit("Added to ${name.trim()}") }
    }
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

    /** Called by the sign-in page once Spotify has signed the listener in. */
    fun signInSpotify(cookie: String) {
        viewModelScope.launch(Dispatchers.IO) { spotifyAccount.save(cookie) }
        catalog.setAccount(cookie)
        _account.value = SpotifyAccountState(signedIn = true)
        loadSpotifyLibrary()
    }
    fun signOutSpotify() {
        accountJob?.cancel()
        catalog.setAccount(null)
        viewModelScope.launch(Dispatchers.IO) { spotifyAccount.clear() }
        _account.value = SpotifyAccountState()
    }
    fun loadSpotifyLibrary() {
        if (!_account.value.signedIn || _account.value.progress != null) return
        accountJob?.cancel()
        _account.update { it.copy(loading = true, error = null) }
        accountJob = viewModelScope.launch {
            // Each part loads on its own, so one failing source never hides the others.
            val name = async { try { catalog.accountName() } catch (e: CancellationException) { throw e } catch (_: Exception) { null } }
            val playlists = async { try { Result.success(catalog.libraryPlaylists()) } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) } }
            val mixes = async { try { Result.success(catalog.personalMixes()) } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) } }
            val lists = playlists.await(); val made = mixes.await()
            _account.update { it.copy(name = name.await() ?: it.name, playlists = lists.getOrDefault(emptyList()), mixes = made.getOrDefault(emptyList()), loading = false) }
            val failure = lists.exceptionOrNull() ?: made.exceptionOrNull()
            val nothing = lists.getOrDefault(emptyList()).isEmpty() && made.getOrDefault(emptyList()).isEmpty()
            if (nothing) {
                val detail = failure?.message.orEmpty()
                val expired = detail.contains("sign", true) || detail.contains("HTTP 401")
                _account.update { it.copy(error = when {
                    expired -> "Your Spotify sign-in has expired. Sign in again."
                    failure != null -> "Couldn't load your Spotify library. Tap Retry." + detail.substringAfter("(", "").substringBefore(")").takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                    else -> "Spotify didn't return any playlists or mixes for this account. Tap Retry."
                }) }
            }
        }
    }
    /** Imports the chosen playlists and mixes one at a time, each saved complete or not at all. */
    fun importSpotifyCards(cards: List<SpotifyCard>) {
        if (cards.isEmpty() || _account.value.progress != null) return
        accountJob?.cancel()
        accountJob = viewModelScope.launch {
            var done = 0; var failed = 0; var songs = 0
            try {
                cards.forEachIndexed { index, card ->
                    _account.update { it.copy(progress = "Importing ${index + 1} of ${cards.size}: ${card.title}") }
                    try {
                        val collection = catalog.collection(card.uri)
                        if (collection.tracks.isEmpty()) { failed++; return@forEachIndexed }
                        library.importPlaylist(collection.title, collection.artwork ?: card.artwork, collection.tracks)
                        imported += card.uri; done++; songs += collection.tracks.distinctBy { it.isrc }.size
                    } catch (e: CancellationException) { throw e } catch (_: Exception) { failed++ }
                }
            } finally {
                _account.update { it.copy(progress = null) }
                if (done > 0 || failed > 0) _messages.tryEmit("Imported $done ${if (done == 1) "collection" else "collections"} · $songs songs" + if (failed > 0) " · $failed couldn't be imported" else "")
            }
        }
    }
    /** Makes a clean version of an explicit song and keeps both: the clean one sits right after the explicit one. */
    fun makeCleanVersion(song: Track, collectionKey: String?) = viewModelScope.launch {
        _cleanSearching.value = song.isrc
        _messages.emit("Finding a clean version of ${song.title} and listening to it for swearing. This can take a minute.")
        val outcome = try { findClean(song) } catch (e: CancellationException) { throw e } catch (_: Exception) { null } finally { _cleanSearching.value = null }
        val clean = outcome?.track
        if (clean == null) { chooseCleanVideo(song, collectionKey, outcome); return@launch }
        applyClean(song, collectionKey, clean, verified = true)
    }
    /** No verified clean version: show YouTube uploads (minus ones with swearing heard) and let the listener pick one to check. */
    private suspend fun chooseCleanVideo(song: Track, collectionKey: String?, outcome: CleanOutcome?) {
        val explicit = outcome?.checked.orEmpty().filterValues { it == CleanVerdict.EXPLICIT }.keys
        _cleanChoices.value = CleanChoices(song, collectionKey, listened = outcome?.checked?.size ?: 0, swore = explicit.size, notes = outcome?.checked.orEmpty(), controlVideoId = outcome?.controlVideoId)
        val artist = song.artist.substringBefore(',').trim()
        val base = song.title.substringBefore(" (").substringBefore(" - ")
        val found = coroutineScope {
            listOf("$artist $base clean", "$artist $base clean version", "$artist $base radio edit", "$base clean").map { q ->
                async { try { youtube.searchBroad(q) } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() } }
            }.awaitAll().flatten()
        }.filter { isValidYoutubeVideoId(it.videoId) && it.videoId !in explicit }.distinctBy { it.videoId }
            .sortedWith(compareByDescending<YoutubeCandidate> { CleanMatch.cleanLabel.containsMatchIn(it.title) }.thenByDescending { CleanMatch.tokens(it.title).containsAll(CleanMatch.tokens(base)) }.thenByDescending { it.viewCount })
            .take(15)
        _cleanChoices.update { if (it?.song?.isrc == song.isrc) it.copy(videos = found, loading = false) else it }
    }
    /** Checks a picked upload for swearing before adding it. */
    fun pickCleanVideo(video: YoutubeCandidate) {
        val choice = _cleanChoices.value ?: return
        if (choice.checking != null) return
        _cleanChoices.value = choice.copy(checking = video.title)
        viewModelScope.launch {
            val song = choice.song
            val result = try {
                val control = choice.controlVideoId ?: controlVideo(song) ?: error("No explicit recording to compare with")
                cleanVerifier.check(song, control, video.videoId, video.durationMs)
            } catch (e: CancellationException) { throw e } catch (_: Exception) { CleanResult(CleanVerdict.ERROR) }
            when (result.verdict) {
                CleanVerdict.EXPLICIT -> {
                    _cleanChoices.update { if (it?.song?.isrc == song.isrc) it.copy(checking = null, videos = it.videos.filter { v -> v.videoId != video.videoId }, listened = it.listened + 1, swore = it.swore + 1) else it }
                    _messages.emit("Heard swearing in that upload, so it wasn't added.")
                }
                CleanVerdict.NO_VOCALS -> {
                    _cleanChoices.update { if (it?.song?.isrc == song.isrc) it.copy(checking = null, notes = it.notes + (video.videoId to CleanVerdict.NO_VOCALS)) else it }
                    _messages.emit("That upload sounds instrumental, so it wasn't added.")
                }
                else -> {
                    _cleanChoices.value = null
                    val clean = Track("youtube:${video.videoId}", title = song.title.substringBefore(" (").substringBefore(" - ").ifBlank { song.title } + " (Clean)",
                        artist = song.artist, album = song.album, durationMs = video.durationMs.takeIf { it > 0 } ?: song.durationMs, albumArtUrl = song.albumArtUrl,
                        source = TrackSource.YOUTUBE, youtubeVideoId = video.videoId, isExplicit = false, artists = song.artists)
                    applyClean(song, choice.collectionKey, clean, verified = result.verdict == CleanVerdict.CLEAN)
                }
            }
        }
    }
    fun dismissCleanChoices() { _cleanChoices.value = null }
    private suspend fun applyClean(song: Track, collectionKey: String?, clean: Track, verified: Boolean) {
        trackDao.upsert(clean.toEntity())
        cleanVersions[song.isrc] = clean
        withContext(Dispatchers.IO) { cleanPrefs.edit().putString("versions", homeJson.encodeToString(cleanMap, cleanVersions.toMap())).apply() }
        _search.update { it.copy(tracks = withCleanVersions(it.tracks)) }
        // Both versions stay: the clean one goes right after the explicit one wherever that is saved.
        fun List<Track>.withClean(): List<Track> {
            if (none { it.isrc == song.isrc }) return this
            val rest = filter { it.isrc != clean.isrc }
            val at = rest.indexOfFirst { it.isrc == song.isrc }
            return rest.take(at + 1) + clean + rest.drop(at + 1)
        }
        val playlistId = collectionKey?.toLongOrNull()
        if (playlistId != null) library.replaceTracks(playlistId, playlistTracks.value[playlistId].orEmpty().withClean())
        if (favorites.value.any { it.isrc == song.isrc } && favorites.value.none { it.isrc == clean.isrc }) {
            val order = favorites.value.withClean()
            library.addFavorite(clean, System.currentTimeMillis()); library.setFavoriteOrder(order)
        }
        val queue = player.queue.value
        val at = queue.tracks.indexOfFirst { it.isrc == song.isrc }
        if (at >= 0 && queue.tracks.none { it.isrc == clean.isrc }) player.addToQueue(clean, playNext = at == queue.currentIndex)
        val name = song.title.substringBefore(" (")
        _messages.emit(if (verified) "Made a clean version of $name. Cadence listened to it and heard no swearing. Both versions are saved." else "Added a clean version of $name, but Cadence couldn't confirm it's clean. Both versions are saved.")
    }
    /** The explicit recording the player would use, to compare candidates against. */
    private suspend fun controlVideo(song: Track, music: List<MusicSong> = emptyList()): String? {
        song.youtubeVideoId?.let { return it }
        val artistWords = CleanMatch.tokens(song.artist.substringBefore(',').trim())
        fun matches(title: String, artist: String) = CleanMatch.core(title) == CleanMatch.core(song.title) && (artistWords.isEmpty() || artistWords.any { it in CleanMatch.tokens(artist) })
        music.firstOrNull { it.explicit && matches(it.title, it.artist) && (song.durationMs <= 0 || it.durationMs <= 0 || kotlin.math.abs(it.durationMs - song.durationMs) <= 15_000) }?.let { return it.videoId }
        val found = try { youtube.searchSongs("${song.artist.substringBefore(',').trim()} ${song.title}") } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
        return found.firstOrNull { isValidYoutubeVideoId(it.videoId) && matches(it.title, it.channelName + " " + it.title) }?.videoId ?: found.firstOrNull { isValidYoutubeVideoId(it.videoId) }?.videoId
    }
    data class CleanOutcome(val track: Track?, val checked: Map<String, CleanVerdict>, val controlVideoId: String?)
    /**
     * Finds a clean version of an explicit song and listens to it before trusting it. Candidates are YouTube Music
     * songs without the explicit badge (the label's clean master, the same release Spotify lists as clean) and
     * clean-labeled uploads. Spotify's clean track supplies the metadata when it has one.
     */
    private suspend fun findClean(song: Track): CleanOutcome = coroutineScope {
        val artist = song.artist.substringBefore(',').trim()
        val base = song.title.substringBefore(" (").substringBefore(" - ").ifBlank { song.title }
        val words = CleanMatch.tokens(CleanMatch.core(song.title))
        val artistWords = CleanMatch.tokens(artist)
        suspend fun spotify(query: String) = try { catalog.searchAll(query).tracks } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
        suspend fun videos(query: String) = try { youtube.searchBroad(query) } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
        suspend fun music(query: String) = try { YoutubeMusicSongs.search(query) } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
        val spotifyJobs = listOf("${song.title} $artist", "${song.title} $artist clean", "${CleanMatch.core(song.title)} $artist edited").map { async { spotify(it) } }
        val videoJobs = listOf("$artist $base clean", "$artist $base radio edit").map { async { videos(it) } }
        val musicJobs = listOf("$base $artist", "$base $artist clean").map { async { music(it) } }
        fun near(a: Long, b: Long, limit: Long) = a <= 0 || b <= 0 || kotlin.math.abs(a - b) <= limit
        val spotifyClean = spotifyJobs.awaitAll().flatten().distinctBy { it.isrc }.filter { candidate ->
            candidate.isrc != song.isrc && candidate.isExplicit == false && CleanMatch.core(candidate.title) == CleanMatch.core(song.title) &&
                CleanMatch.tokens(candidate.artist).containsAll(artistWords) && near(candidate.durationMs, song.durationMs, 15_000)
        }.minByOrNull { kotlin.math.abs(it.durationMs - song.durationMs) }
        val dirty = Regex("explicit|dirty|uncensored", RegexOption.IGNORE_CASE)
        val musicSongs = musicJobs.awaitAll().flatten().distinctBy { it.videoId }
        val target = spotifyClean?.durationMs ?: song.durationMs
        val fromMusic = musicSongs.filter { !it.explicit && CleanMatch.core(it.title) == CleanMatch.core(song.title) && !dirty.containsMatchIn(it.title) &&
                (artistWords.isEmpty() || artistWords.any { w -> w in CleanMatch.tokens(it.artist) }) && near(it.durationMs, song.durationMs, 25_000) }
            .sortedBy { kotlin.math.abs(it.durationMs - target) }.take(3).map { Triple(it.videoId, it.title, it.durationMs) }
        val fromVideos = videoJobs.awaitAll().flatten().distinctBy { it.videoId }.filter { video ->
            val text = CleanMatch.tokens(video.title + " " + video.channelName)
            isValidYoutubeVideoId(video.videoId) && CleanMatch.cleanLabel.containsMatchIn(video.title) && !dirty.containsMatchIn(video.title) &&
                text.containsAll(words) && (artistWords.isEmpty() || artistWords.any { it in text }) && near(video.durationMs, song.durationMs, 25_000)
        }.sortedWith(compareByDescending<YoutubeCandidate> { it.isMusicCatalog || it.channelIsTopicChannel }.thenByDescending { it.viewCount }).take(2).map { Triple(it.videoId, it.title, it.durationMs) }
        val candidates = (fromMusic + fromVideos).distinctBy { it.first }
        val control = controlVideo(song, musicSongs) ?: return@coroutineScope CleanOutcome(null, emptyMap(), null)
        val checked = linkedMapOf<String, CleanVerdict>()
        for ((videoId, title, durationMs) in candidates) {
            if (videoId == control) continue
            val result = cleanVerifier.check(song, control, videoId, durationMs)
            checked[videoId] = result.verdict
            if (result.verdict == CleanVerdict.CLEAN) {
                val track = spotifyClean?.copy(isExplicit = false, youtubeVideoId = videoId) ?: Track("youtube:$videoId", title = "$base (Clean)",
                    artist = song.artist, album = song.album, durationMs = durationMs.takeIf { it > 0 } ?: song.durationMs, albumArtUrl = song.albumArtUrl,
                    source = TrackSource.YOUTUBE, youtubeVideoId = videoId, isExplicit = false, artists = song.artists)
                return@coroutineScope CleanOutcome(track, checked, control)
            }
        }
        CleanOutcome(null, checked, control)
    }
    fun download(track: Track) { downloads.download(listOf(track)); _messages.tryEmit("Downloading ${track.title}") }
}
