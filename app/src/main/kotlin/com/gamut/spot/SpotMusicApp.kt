package com.gamut.spot

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.gamut.BuildConfig
import com.gamut.core.model.*
import com.gamut.core.network.SpotifyCard
import com.gamut.core.player.StreamQuality
import com.gamut.podcasts.PodcastShow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private enum class Page { Home, Search, Library, Settings, Import }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotMusicApp(sharedLink: StateFlow<String?>, consumeLink: () -> Unit, vm: SpotViewModel = hiltViewModel()) {
    val home by vm.home.collectAsState()
    val search by vm.search.collectAsState()
    val detail by vm.detail.collectAsState()
    val browse by vm.browse.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val playlistTracks by vm.playlistTracks.collectAsState()
    val follows by vm.followedShows.collectAsState()
    val recent by vm.recents.collectAsState()
    val playback by vm.player.playbackState.collectAsState()
    val opening by vm.opening.collectAsState()
    val importing by vm.importing.collectAsState()
    val downloadedTracks by vm.downloadedTracks.collectAsState()
    val newShows by vm.newShows.collectAsState()
    val cleanChoices by vm.cleanChoices.collectAsState()
    val shared by sharedLink.collectAsState()
    val loaded = when (val state = playback) { is PlaybackState.Playing -> state.track; is PlaybackState.Paused -> state.track; else -> null }
    val track = opening ?: loaded
    val isPlaying = playback is PlaybackState.Playing
    var page by rememberSaveable { mutableStateOf(Page.Home) }
    var fullPlayer by rememberSaveable { mutableStateOf(false) }
    var actions by remember { mutableStateOf<Track?>(null) }
    var saveTrack by remember { mutableStateOf<Track?>(null) }
    var artistTrack by remember { mutableStateOf<Track?>(null) }
    var queueSheet by remember { mutableStateOf(false) }
    val queue by vm.player.queue.collectAsState()
    var miniBounds by remember { mutableStateOf<Rect?>(null) }
    var pullRequested by remember { mutableStateOf(false) }
    var addToPlaylist by remember { mutableStateOf<Track?>(null) }
    var createSheet by remember { mutableStateOf(false) }
    var createName by remember { mutableStateOf<String?>(null) }
    var importLink by rememberSaveable { mutableStateOf("") }
    var libraryFilter by rememberSaveable { mutableStateOf("All") }
    var homeFilter by rememberSaveable { mutableStateOf("All") }
    var searchFilter by rememberSaveable { mutableStateOf("All") }
    var libraryGrid by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val favoriteIds = remember(favorites) { favorites.mapTo(hashSetOf()) { it.isrc } }
    val currentId = loaded?.isrc
    fun navigate(next: Page) { keyboard?.hide(); vm.closeDetail(); page = next; fullPlayer = false }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(shared) { if (shared != null) { importLink = shared!!; navigate(Page.Import); consumeLink() } }
    LaunchedEffect(detail?.key) { if (page == Page.Import && detail?.key?.toLongOrNull() != null) page = Page.Library }
    LaunchedEffect(home.loading) { if (!home.loading) pullRequested = false }
    LaunchedEffect(searchFilter, search.query) { if (searchFilter == "YouTube") vm.searchYoutube() }
    LaunchedEffect(page) {
        if (page == Page.Home) while (isActive) { vm.refreshHomeIfStale(); delay(60_000) }
    }
    BackHandler(fullPlayer || detail != null || browse != null || page != Page.Home) {
        when { fullPlayer -> fullPlayer = false; detail != null || browse != null -> vm.backDetail(); else -> navigate(Page.Home) }
    }
    SpotTheme {
        CompositionLocalProvider(LocalArtistTap provides { song -> artistTrack = song }, LocalSaveTrack provides { song -> vm.saveToLiked(song); saveTrack = song }, LocalQueueTrack provides vm::enqueue) {
        Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = if (fullPlayer) Modifier.clearAndSetSemantics { } else Modifier,
            containerColor = Black,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                Column(Modifier.background(Brush.verticalGradient(listOf(Black.copy(alpha = .94f), Color.Black)))) {
                    if (track != null) SpotMiniPlayer(track, vm.player, isPlaying, opening != null, track.isrc in favoriteIds,
                        onOpen = { fullPlayer = true }, onSave = { vm.saveToLiked(track); saveTrack = track }, onBounds = { miniBounds = it })
                    Row(Modifier.fillMaxWidth().navigationBarsPadding().height(62.dp), verticalAlignment = Alignment.CenterVertically) {
                        // Account sits where Create used to be. Create is now the + at the top of each page.
                        val entries = listOf(Triple(Page.Home, "Home", Icons.Outlined.Home), Triple(Page.Search, "Search", Icons.Outlined.Search), Triple(Page.Library, "Your Library", Icons.Outlined.LibraryMusic), Triple(Page.Settings, "Account", Icons.Outlined.Person))
                        entries.forEach { (target, title, icon) ->
                            val selected = page == target
                            Column(Modifier.weight(1f).fillMaxHeight().clickable { navigate(target) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                if (target == Page.Settings) Box(Modifier.size(27.dp).then(if (selected) Modifier.border(1.5.dp, Color.White, CircleShape) else Modifier).padding(if (selected) 2.dp else 0.dp), contentAlignment = Alignment.Center) { SAvatar(null, if (selected) 23.dp else 26.dp) }
                                else Icon(if (selected && target == Page.Home) Icons.Filled.Home else icon, null, tint = if (selected) Color.White else Muted, modifier = Modifier.size(27.dp))
                                Text(title, style = MaterialTheme.typography.labelSmall, color = if (selected) Color.White else Muted, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (detail?.key?.startsWith("spotify:artist:") == true || detail?.key?.startsWith("artist-for:") == true) SpotArtistScreen(detail!!, vm, favoriteIds, currentId, vm::backDetail) { actions = it }
                else if (detail != null) SpotCollectionScreen(detail!!, vm, favoriteIds, currentId, onBack = vm::backDetail, onMore = { actions = it }, onImport = { importLink = detail!!.key; navigate(Page.Import) })
                else if (browse != null) SpotBrowseScreen(browse!!, home, vm, favoriteIds, currentId, onMore = { actions = it })
                else when (page) {
                    Page.Home -> PullToRefreshBox(isRefreshing = pullRequested && home.loading, onRefresh = { pullRequested = true; vm.refreshHome() }, indicator = {}) { LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
                        item {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                CreateButton { createSheet = true }
                                LazyRow(Modifier.padding(start = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(listOf("All", "Music", "Podcasts")) { filter -> SChip(filter, homeFilter == filter) { homeFilter = filter } }
                                }
                            }
                        }
                        item {
                            // Always eight tiles: your collections first, then mixes, recent albums and editorial picks to fill the grid.
                            val shortcuts = remember(homeFilter, playlists, playlistTracks, follows, home.mixes, home.personal, home.shows, recent, newShows) {
                                buildList<HomeTile> {
                                    if (homeFilter != "Podcasts") {
                                        add(HomeTile("Liked Songs", null, liked = true) { vm.openPlaylist("liked") })
                                        playlists.take(3).forEach { p -> add(HomeTile(p.name, p.coverUri ?: playlistTracks[p.id]?.firstOrNull()?.albumArtUrl) { vm.openPlaylist(p.id.toString()) }) }
                                    }
                                    if (homeFilter != "Music") follows.sortedByDescending { it.feedUrl in newShows }.take(4).forEach { show -> add(HomeTile(show.title, show.artwork, fresh = show.feedUrl in newShows) { vm.openShow(show) }) }
                                    if (homeFilter != "Podcasts") {
                                        (home.mixes.take(2) + home.personal.take(2)).forEach { mix -> add(HomeTile(mix.title, mix.artwork) { vm.openMix(mix) }) }
                                        recent.filter { it.source != TrackSource.PODCAST && !it.album.isNullOrBlank() }.distinctBy { it.album }.take(4).forEach { song -> add(HomeTile(song.album!!, song.albumArtUrl) { vm.openAlbum(song) }) }
                                        listOf(SpotDiscovery.NEW_MUSIC_FRIDAY, SpotDiscovery.TODAYS_TOP_HITS, SpotDiscovery.US_TOP_50, SpotDiscovery.RAP_CAVIAR, SpotDiscovery.PEACEFUL_PIANO, SpotDiscovery.GLOBAL_TOP_50).forEach { card -> add(HomeTile(card.title, card.artwork) { vm.openCard(card) }) }
                                    }
                                    if (homeFilter == "Podcasts") {
                                        home.shows.take(8).forEach { show -> add(HomeTile(show.title, show.artwork) { vm.openShow(show) }) }
                                        BrowseCategory.entries.filter { it.group == BrowseGroup.Podcasts }.forEach { category -> add(HomeTile(category.title, null, color = category.color) { vm.openBrowse(category) }) }
                                    }
                                }.distinctBy { it.title.lowercase() }.take(8)
                            }
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                shortcuts.chunked(2).forEach { pair -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    pair.forEach { tile -> Row(Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(4.dp)).background(Card).clickable(onClick = tile.open), verticalAlignment = Alignment.CenterVertically) {
                                        if (tile.color != null) Box(Modifier.size(56.dp).background(Color(tile.color)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Podcasts, null, tint = Color.White) }
                                        else SArt(spotifyThumb(tile.art), Modifier.size(56.dp), liked = tile.liked)
                                        Text(tile.title, Modifier.weight(1f).padding(horizontal = 8.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        if (tile.fresh) NewDot(Modifier.padding(end = 10.dp))
                                    } }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                } }
                            }
                        }
                        if (homeFilter != "Podcasts") {
                            item { DailyMixRail(home.mixes, home.loading, vm::openMix) }
                            if (home.personal.isNotEmpty()) item { DailyMixRail(home.personal, false, vm::openMix, title = "Made for you") }
                            home.rails.flatMap { it.cards }.firstOrNull { it.type == "album" }?.let { card -> item { FeaturedRelease(card) { vm.openCard(card) } } }
                        }
                        if (homeFilter != "Music") {
                            if (follows.isNotEmpty()) item { PodcastRail("Your shows", follows.sortedByDescending { it.feedUrl in newShows }.take(12), newShows, vm::openShow) }
                            item { NewEpisodeRail(home.newEpisodes, newShows, vm) }
                        }
                        if (homeFilter == "All") item { if (home.podcastsLoading) DiscoverySkeleton() else PodcastEpisodeCards(home.episodes.take(1), vm) }
                        if (homeFilter != "Podcasts" && !home.loading) {
                            home.rails.take(1).forEach { rail -> item("home-${rail.title}") { SCardRail(rail.title, rail.cards, vm::openCard) } }
                            item { CategoryRail("Explore your genres", BrowseCategory.entries.filter { it.genre != null }.shuffled(kotlin.random.Random(home.mixes.firstOrNull()?.id.hashCode() + recent.size)).take(6), home.rails.flatMap { it.cards }.mapNotNull { it.artwork }.distinct(), vm::openBrowse) }
                            home.rails.drop(1).forEach { rail -> item("home-${rail.title}") { SCardRail(rail.title.replace("More like", "Popular with listeners of"), rail.cards, vm::openCard) } }
                            item { if (home.videosLoading) RailSkeleton() else MusicVideoRail(home.videos) }
                        }
                        if (homeFilter != "Music") {
                            if (home.podcastsLoading) item { RailSkeleton() }
                            else if (home.shows.isNotEmpty()) item {
                                PodcastRail("Find your next podcast", home.shows, emptySet(), vm::openShow)
                                if (home.podcastReason.isNotBlank()) Text(home.podcastReason, color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                            }
                            if (homeFilter == "Podcasts") item { if (home.podcastsLoading) DiscoverySkeleton() else PodcastEpisodeCards(home.episodes, vm) }
                        }
                        if (recent.isNotEmpty()) {
                            item { SHeading("Recently played") }
                            items(recent.take(5), key = { "recent-${it.isrc}" }) { song -> STrack(song, currentId == song.isrc, song.isrc in favoriteIds, onPlay = { vm.play(song, recent) }, onMore = { actions = song }) }
                        }
                        if (home.loading && homeFilter != "Podcasts") item { HomeSkeleton() }
                        home.error?.let { error -> item { ErrorRow(error, vm::refreshHome) } }
                    } }
                    Page.Search -> Column(Modifier.fillMaxSize().statusBarsPadding()) {
                        PageTitle("Search", { createSheet = true })
                        SearchInput(search.query, vm::search)
                        if (search.query.isBlank()) {
                            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                                // Browse page: shortcuts, things to watch and hear, then genres, podcast topics and moods. No audiobooks.
                                val art = home.rails.flatMap { it.cards }.mapNotNull { it.artwork }.distinct()
                                fun group(value: BrowseGroup) = BrowseCategory.entries.filter { it.group == value }
                                item { SHeading("Browse all") }
                                item { CategoryGrid(group(BrowseGroup.Top), artwork = art, podcastArtwork = home.shows.firstOrNull()?.artwork, onCategory = vm::openBrowse, compact = true) }
                                item { if (home.videosLoading) RailSkeleton() else MusicVideoRail(home.videos, "Videos you can watch") }
                                if (home.newEpisodes.isNotEmpty()) item { NewEpisodeRail(home.newEpisodes, newShows, vm) }
                                else if (home.episodes.isNotEmpty()) item { NewEpisodeRail(home.episodes, emptySet(), vm) }
                                item { CategoryRail("More for you", group(BrowseGroup.More) + BrowseCategory.NewReleases + BrowseCategory.Charts, art.drop(3), vm::openBrowse) }
                                item { SHeading("Genres") }
                                item { CategoryGrid(BrowseCategory.entries.filter { it.genre != null }, artwork = art.drop(1), podcastArtwork = null, onCategory = vm::openBrowse) }
                                item { SHeading("Podcasts") }
                                item { CategoryGrid(group(BrowseGroup.Podcasts), artwork = home.shows.mapNotNull { it.artwork }, podcastArtwork = null, onCategory = vm::openBrowse) }
                                item { SHeading("Moods & activities") }
                                item { CategoryGrid(group(BrowseGroup.Moods), artwork = art.drop(2), podcastArtwork = null, onCategory = vm::openBrowse) }
                                home.rails.take(2).forEach { rail -> item { SCardRail(rail.title, rail.cards, vm::openCard) } }
                            }
                        } else {
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(listOf("All", "Music", "Podcasts", "Playlists", "Albums", "Artists", "YouTube")) { filter -> SChip(filter, searchFilter == filter) { searchFilter = filter } }
                            }
                            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                                if (searchFilter == "YouTube") {
                                    item { Text("YouTube", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp)) }
                                    item { Text("Search for clean versions, live performances and more. Try adding ‘clean’ to your search.", color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
                                    items(search.youtube, key = { "youtube-${it.isrc}" }) { song -> STrack(song, currentId == song.isrc, song.isrc in favoriteIds, onPlay = { keyboard?.hide(); vm.play(song, search.youtube) }, onMore = { actions = song }) }
                                    if (search.youtubeLoading) item { DiscoverySkeleton() }
                                    search.youtubeError?.let { message -> item { ErrorRow(message, vm::searchYoutube) } }
                                    if (!search.youtubeLoading && search.youtubeError == null && search.youtube.isEmpty()) item { Text("No YouTube results yet. Try a song title and artist.", color = Muted, modifier = Modifier.padding(16.dp)) }
                                }
                                // Sections follow the query: a show name puts Podcasts first, a mood or era puts Playlists first.
                                val order = SearchRanking.order(search.query, search.tracks, search.shows, search.rich.playlists, search.rich.artists, search.rich.albums, search.rich.top)
                                order.forEach { section ->
                                    when (section) {
                                SearchRanking.Section.Songs -> {
                                if (searchFilter in listOf("All", "Music")) {
                                    if (search.tracks.isNotEmpty()) item { SHeading("Songs") }
                                    val songs = if (searchFilter == "All") search.tracks.take(6) else search.tracks
                                    items(songs, key = { "search-${it.isrc}" }) { song -> STrack(song, currentId == song.isrc, song.isrc in favoriteIds, onPlay = { keyboard?.hide(); vm.play(song, search.tracks) }, onMore = { actions = song }) }
                                    if (search.musicLoading) item { DiscoverySkeleton() }
                                    search.notice?.let { item { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(16.dp)) } }
                                }
                                }
                                SearchRanking.Section.Podcasts -> {
                                if (searchFilter in listOf("All", "Podcasts")) {
                                    if (search.shows.isNotEmpty() || search.rich.podcasts.isNotEmpty()) item { SHeading("Podcasts") }
                                    val shows = if (searchFilter == "All") search.shows.take(3) else search.shows
                                    items(shows, key = { "podcast-${it.feedUrl}" }) { show -> SLibraryRow(show.title, "Podcast · ${show.author}", show.artwork) { keyboard?.hide(); vm.openShow(show) } }
                                    if (search.rich.podcasts.isNotEmpty()) item { SCardRail("From Spotify", search.rich.podcasts.take(if (searchFilter == "All") 4 else 25), vm::openCard) }
                                    if (search.podcastLoading) item { DiscoverySkeleton() }
                                    search.podcastError?.let { item { ErrorRow(it) { vm.search(search.query) } } }
                                }
                                }
                                SearchRanking.Section.Playlists -> {
                                if (searchFilter in listOf("All", "Playlists")) item { SCardRail("Playlists", search.rich.playlists, vm::openCard) }
                                }
                                SearchRanking.Section.Albums -> {
                                if (searchFilter in listOf("All", "Albums")) item { SCardRail("Albums", search.rich.albums, vm::openCard) }
                                }
                                SearchRanking.Section.Artists -> {
                                if (searchFilter in listOf("All", "Artists")) item { SCardRail("Artists", search.rich.artists, vm::openCard) }
                                }
                                    }
                                }
                                if (searchFilter != "YouTube" && !search.musicLoading && !search.podcastLoading && search.tracks.isEmpty() && search.shows.isEmpty() && search.rich.albums.isEmpty() && search.rich.playlists.isEmpty()) item { Text("No results. Try another song, artist or show.", color = Muted, modifier = Modifier.padding(24.dp)) }
                            }
                        }
                    }
                    Page.Library -> Column(Modifier.fillMaxSize().statusBarsPadding()) {
                        PageTitle("Your Library", { createSheet = true }, trailing = {
                            SIcon(Icons.Outlined.Search, "Search", onClick = { navigate(Page.Search) })
                        })
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(listOf("All", "Playlists", "Podcasts", "Downloads")) { filter -> SChip(filter, libraryFilter == filter) { libraryFilter = filter } }
                        }
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Sort, null, Modifier.size(20.dp)); Text("Your collection", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f).padding(start = 8.dp))
                            SIcon(if (libraryGrid) Icons.Outlined.ViewList else Icons.Outlined.GridView, "Change library layout", onClick = { libraryGrid = !libraryGrid })
                        }
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                            if (libraryFilter in listOf("All", "Playlists")) {
                                item { SLibraryRow("Liked Songs", "Playlist · ${favorites.size} songs", null, liked = true) { vm.openPlaylist("liked") } }
                                if (libraryGrid) {
                                    items(playlists.chunked(2)) { pair -> Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        pair.forEach { playlist -> Column(Modifier.weight(1f).clickable { vm.openPlaylist(playlist.id.toString()) }) {
                                            SArt(playlist.coverUri ?: playlistTracks[playlist.id]?.firstOrNull()?.albumArtUrl, Modifier.fillMaxWidth().aspectRatio(1f))
                                            Text(playlist.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        } }
                                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                                    } }
                                } else items(playlists, key = { "library-${it.id}" }) { playlist -> SLibraryRow(playlist.name, "Playlist · ${playlistTracks[playlist.id]?.size ?: 0} songs", playlist.coverUri ?: playlistTracks[playlist.id]?.firstOrNull()?.albumArtUrl) { vm.openPlaylist(playlist.id.toString()) } }
                            }
                            if (libraryFilter in listOf("All", "Podcasts")) {
                                if (follows.isNotEmpty()) items(follows, key = { "follow-${it.feedUrl}" }) { show -> Box {
                                    SLibraryRow(show.title, if (show.feedUrl in newShows) "New episode · ${show.author}" else "Podcast · ${show.author}", show.artwork) { vm.openShow(show) }
                                    if (show.feedUrl in newShows) NewDot(Modifier.align(Alignment.CenterEnd).padding(end = 20.dp))
                                } }
                                else item { TextButton(onClick = { searchFilter = "Podcasts"; navigate(Page.Search); vm.search("popular podcasts") }, modifier = Modifier.padding(8.dp)) { Text("Find podcasts to follow", color = Color.White) } }
                            }
                            if (libraryFilter == "Downloads") {
                                val saved = downloadedTracks
                                if (saved.isEmpty()) item { Text("Songs you download will appear here.", color = Muted, modifier = Modifier.padding(24.dp)) }
                                items(saved, key = { it.isrc }) { song -> STrack(song, currentId == song.isrc, song.isrc in favoriteIds, onPlay = { vm.play(song, saved) }, onMore = { actions = song }) }
                            }
                            item { TextButton(onClick = { navigate(Page.Import) }, modifier = Modifier.padding(8.dp)) { Icon(Icons.Outlined.Download, null); Spacer(Modifier.width(8.dp)); Text("Import from Spotify", color = Color.White) } }
                        }
                    }
                    Page.Import -> SpotImportScreen(vm, importLink, { importLink = it }, importing, onBack = { navigate(Page.Library) }, onImportLink = { keyboard?.hide(); vm.importSpotify(importLink) })
                    Page.Settings -> SpotSettings(vm, onBack = { navigate(Page.Home) }, onImport = { navigate(Page.Import) })
                }
            }
        }
        SpotPlayerTransition(fullPlayer, miniBounds, track) {
            if (track != null) SpotPlayerScreen(vm, track, isPlaying, opening != null, favoriteIds, onBack = { fullPlayer = false }, onMore = { actions = it }, onSave = { vm.saveToLiked(track); saveTrack = track })
        }
        }
        saveTrack?.let { song -> SpotSaveSheet(song, vm) { saveTrack = null } }
        artistTrack?.let { song -> SpotArtistSheet(song, vm, { artistTrack = null }, { artistTrack = null; fullPlayer = false }, { artistTrack = null; fullPlayer = false }) }
        if (queueSheet) ModalBottomSheet(onDismissRequest = { queueSheet = false }, containerColor = Card) {
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                item { SHeading("Next in queue") }
                itemsIndexed(queue.tracks, key = { index, song -> "$index-${song.isrc}" }) { index, song -> STrack(song, index == queue.currentIndex, song.isrc in favoriteIds, onPlay = { vm.player.skipTo(index); queueSheet = false }, onMore = { queueSheet = false; actions = song }) }
            }
        }
        if (createSheet) ModalBottomSheet(onDismissRequest = { createSheet = false }, containerColor = Color(0xFF222222)) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
                ActionTile(Icons.Outlined.QueueMusic, "Create playlist", "Start with a name, then add music") { createSheet = false; createName = "" }
                ActionTile(Icons.Outlined.Download, "Import from Spotify", "Paste a playlist or album link") { createSheet = false; navigate(Page.Import) }
            }
        }
        createName?.let { name -> AlertDialog(onDismissRequest = { createName = null }, containerColor = Card, title = { Text("Give your playlist a name") }, text = { OutlinedTextField(name, { createName = it }, singleLine = true) }, confirmButton = { TextButton(onClick = { vm.createPlaylist(name); createName = null }) { Text("Create") } }, dismissButton = { TextButton(onClick = { createName = null }) { Text("Cancel", color = Color.White) } }) }
        actions?.let { song -> ModalBottomSheet(onDismissRequest = { actions = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color(0xFF222222)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
                SLibraryRow(song.title, song.artist, song.albumArtUrl) {}
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = .1f))
                MenuRow(Icons.Outlined.Share, "Share") {
                    val text = if (song.source == TrackSource.SPOTIFY) "https://open.spotify.com/track/${song.isrc.removePrefix("spotify:")}" else "${song.title} — ${song.artist}"
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share song")); actions = null
                }
                MenuRow(Icons.Outlined.PlaylistAdd, "Add to playlist") { saveTrack = song; actions = null }
                if (detail?.key == "liked" || detail?.key?.toLongOrNull() != null) MenuRow(Icons.Outlined.RemoveCircleOutline, "Remove from this playlist") { vm.removeFromCollection(song, detail!!.key); actions = null }
                MenuRow(Icons.Outlined.QueueMusic, "Add to queue") { vm.enqueue(song); actions = null }
                MenuRow(Icons.Outlined.QueueMusic, "Go to queue") { actions = null; queueSheet = true }
                if (song.source != TrackSource.PODCAST) MenuRow(Icons.Outlined.Album, "Go to album") { actions = null; fullPlayer = false; vm.openAlbum(song) }
                if (song.source != TrackSource.PODCAST) MenuRow(Icons.Outlined.Person, "Go to artist") { artistTrack = song; actions = null }
                if (song.source != TrackSource.PODCAST) MenuRow(Icons.Outlined.Download, "Download") { vm.download(song); actions = null }
                if (song.isExplicit == true && song.source != TrackSource.PODCAST) MenuRow(Icons.Outlined.SwapHoriz, "Make a clean version") { vm.makeCleanVersion(song, detail?.key); actions = null }
            }
        } }
        cleanChoices?.let { choice -> ModalBottomSheet(onDismissRequest = vm::dismissCleanChoices, containerColor = Color(0xFF222222)) {
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                item { SHeading("Pick a clean version") }
                item { Text("Cadence couldn't find a clean version of ${choice.song.title} on its own. Pick one from YouTube. Tap the play button to hear it first.", color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)) }
                if (choice.loading) item { DiscoverySkeleton() }
                else if (choice.videos.isEmpty()) item { Text("No YouTube uploads found. Try the YouTube filter in Search.", color = Muted, modifier = Modifier.padding(16.dp)) }
                items(choice.videos, key = { "clean-${it.videoId}" }) { video ->
                    Row(Modifier.fillMaxWidth().clickable { vm.pickCleanVideo(video) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        SArt("https://i.ytimg.com/vi/${video.videoId}/mqdefault.jpg", Modifier.width(96.dp).height(54.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(video.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(video.channelName.takeIf { it.isNotBlank() }, video.durationMs.takeIf { it > 0 }?.let(::timeLabel), "Clean".takeIf { CleanMatch.cleanLabel.containsMatchIn(video.title) }).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        SIcon(Icons.Filled.PlayArrow, "Preview ${video.title}", onClick = {
                            val preview = Track("youtube:${video.videoId}", title = video.title, artist = video.channelName, durationMs = video.durationMs, albumArtUrl = "https://i.ytimg.com/vi/${video.videoId}/hqdefault.jpg", source = TrackSource.YOUTUBE, youtubeVideoId = video.videoId)
                            vm.play(preview, listOf(preview))
                        })
                    }
                }
            }
        } }
        addToPlaylist?.let { song -> ModalBottomSheet(onDismissRequest = { addToPlaylist = null }, containerColor = Color(0xFF222222)) {
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                item { SHeading("Add to playlist") }
                items(playlists, key = { it.id }) { playlist -> SLibraryRow(playlist.name, "Playlist", playlist.coverUri ?: playlistTracks[playlist.id]?.firstOrNull()?.albumArtUrl) { vm.addToPlaylist(playlist.id, song); addToPlaylist = null } }
                if (playlists.isEmpty()) item { TextButton(onClick = { addToPlaylist = null; createName = "" }) { Text("Create a playlist first") } }
            }
        } }
        }
    }
}

@Composable
private fun PageTitle(title: String, create: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        CreateButton(create); Text(title, Modifier.weight(1f).padding(start = 14.dp), style = MaterialTheme.typography.headlineMedium, maxLines = 1); trailing()
    }
}

/** Create lives at the top left, where the profile picture used to be: just a +, no label. */
@Composable
private fun CreateButton(onClick: () -> Unit) {
    Box(Modifier.size(34.dp).clip(CircleShape).background(Card).clickable(onClickLabel = "Create or import", onClick = onClick).semantics { contentDescription = "Create or import" }, contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

private class HomeTile(val title: String, val art: String?, val liked: Boolean = false, val fresh: Boolean = false, val color: Long? = null, val open: () -> Unit)

@Composable
internal fun SearchInput(query: String, onChange: (String) -> Unit) {
    TextField(query, onChange, singleLine = true, leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text("What do you want to listen to?", style = MaterialTheme.typography.bodyMedium) },
        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Close, "Clear search") } },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(5.dp),
        colors = TextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, focusedTextColor = Color.Black, unfocusedTextColor = Color.Black, focusedPlaceholderColor = Color(0xFF535353), unfocusedPlaceholderColor = Color(0xFF535353), focusedLeadingIconColor = Color.Black, unfocusedLeadingIconColor = Color.Black, focusedTrailingIconColor = Color.Black, unfocusedTrailingIconColor = Color.Black, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
}

@Composable
private fun CategoryGrid(categories: List<BrowseCategory>, artwork: List<String>, podcastArtwork: String?, onCategory: (BrowseCategory) -> Unit, compact: Boolean = false) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        categories.chunked(2).forEachIndexed { rowIndex, pair -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            pair.forEachIndexed { columnIndex, category ->
                val name = category.title
                Box(Modifier.weight(1f).height(if (compact) 64.dp else 98.dp).clip(RoundedCornerShape(5.dp)).background(Color(category.color)).clickable { onCategory(category) }.padding(12.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(.75f))
                val image = if (name == "Podcasts") podcastArtwork else artwork.getOrNull((rowIndex * 2 + columnIndex) % artwork.size.coerceAtLeast(1))
                if (image != null) SArt(spotifyThumb(image), Modifier.size(if (compact) 48.dp else 68.dp).align(Alignment.BottomEnd).offset(x = 20.dp, y = 20.dp).rotate(22f))
                else Icon(if (category.podcast) Icons.Filled.Podcasts else Icons.Filled.MusicNote, null, tint = Color.White.copy(alpha = .6f), modifier = Modifier.size(if (compact) 30.dp else 42.dp).align(Alignment.BottomEnd))
            } }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        } }
    }
}

@Composable
private fun PodcastRail(title: String, shows: List<PodcastShow>, fresh: Set<String>, open: (PodcastShow) -> Unit) {
    if (shows.isEmpty()) return
    SHeading(title)
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(shows, key = { it.feedUrl }) { show -> Column(Modifier.width(152.dp).clickable { open(show) }) {
            SArt(show.artwork, Modifier.size(152.dp))
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (show.feedUrl in fresh) NewDot(Modifier.padding(end = 6.dp))
                Text(show.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }; Text(show.author, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        } }
    }
}

@Composable
internal fun ErrorRow(message: String, retry: () -> Unit) {
    Column(Modifier.padding(16.dp)) { Text(message, color = Muted); TextButton(onClick = retry) { Text("Retry") } }
}
@Composable private fun HomeSkeleton() {
    DiscoverySkeleton()
}
@Composable private fun ActionTile(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = action).padding(vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(36.dp)); Column(Modifier.weight(1f).padding(start = 18.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 6.dp)) }
    }
}
@Composable internal fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = action).padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null); Text(title, Modifier.padding(start = 20.dp), style = MaterialTheme.typography.bodyLarge) }
}
