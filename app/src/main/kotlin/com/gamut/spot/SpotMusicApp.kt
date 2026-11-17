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

private enum class Page { Home, Search, Library, Create, Settings, Import }

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
    val shared by sharedLink.collectAsState()
    val loaded = when (val state = playback) { is PlaybackState.Playing -> state.track; is PlaybackState.Paused -> state.track; else -> null }
    val track = opening ?: loaded
    val isPlaying = playback is PlaybackState.Playing
    var page by rememberSaveable { mutableStateOf(Page.Home) }
    var fullPlayer by rememberSaveable { mutableStateOf(false) }
    var actions by remember { mutableStateOf<Track?>(null) }
    var saveTrack by remember { mutableStateOf<Track?>(null) }
    var artistTrack by remember { mutableStateOf<Track?>(null) }
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
        CompositionLocalProvider(LocalArtistTap provides { song -> artistTrack = song }, LocalSaveTrack provides { song -> vm.saveToLiked(song); saveTrack = song }) {
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
                        val entries = listOf(Triple(Page.Home, "Home", Icons.Outlined.Home), Triple(Page.Search, "Search", Icons.Outlined.Search), Triple(Page.Library, "Your Library", Icons.Outlined.LibraryMusic), Triple(Page.Create, "Create", Icons.Outlined.Add))
                        entries.forEach { (target, title, icon) ->
                            val selected = page == target
                            Column(Modifier.weight(1f).fillMaxHeight().clickable { navigate(target) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Icon(if (selected && target == Page.Home) Icons.Filled.Home else icon, null, tint = if (selected) Color.White else Muted, modifier = Modifier.size(27.dp))
                                Text(title, style = MaterialTheme.typography.labelSmall, color = if (selected) Color.White else Muted, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (detail?.key?.startsWith("spotify:artist:") == true) SpotArtistScreen(detail!!, vm, favoriteIds, currentId, vm::backDetail) { actions = it }
                else if (detail != null) SpotCollectionScreen(detail!!, vm, favoriteIds, currentId, onBack = vm::backDetail, onMore = { actions = it }, onImport = { importLink = detail!!.key; navigate(Page.Import) })
                else if (browse != null) SpotBrowseScreen(browse!!, home, vm, favoriteIds, currentId, onMore = { actions = it })
                else when (page) {
                    Page.Home -> PullToRefreshBox(isRefreshing = pullRequested && home.loading, onRefresh = { pullRequested = true; vm.refreshHome() }) { LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
                        item {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                SAvatar { navigate(Page.Settings) }
                                LazyRow(Modifier.padding(start = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(listOf("All", "Music", "Podcasts")) { filter -> SChip(filter, homeFilter == filter) { homeFilter = filter } }
                                }
                            }
                        }
                        item {
                            val shortcuts = buildList {
                                if (homeFilter != "Podcasts") {
                                    add(Triple("Liked Songs", null as String?, { vm.openPlaylist("liked") }))
                                    playlists.take(3).forEach { p -> add(Triple(p.name, p.coverUri ?: playlistTracks[p.id]?.firstOrNull()?.albumArtUrl, { vm.openPlaylist(p.id.toString()) })) }
                                }
                                if (homeFilter != "Music") follows.take(4).forEach { show -> add(Triple(show.title, show.artwork, { vm.openShow(show) })) }
                            }.take(8)
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                shortcuts.chunked(2).forEach { pair -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    pair.forEach { (title, art, open) -> Row(Modifier.weight(1f).height(60.dp).clip(RoundedCornerShape(4.dp)).background(Card).clickable(onClick = open), verticalAlignment = Alignment.CenterVertically) {
                                        SArt(art, Modifier.size(60.dp), liked = title == "Liked Songs")
                                        Text(title, Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    } }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                } }
                            }
                        }
                        if (homeFilter != "Podcasts") item { DailyMixRail(home.mixes, home.loading, vm::openMix) }
                        if (homeFilter != "Music") {
                            if (follows.isNotEmpty()) item { PodcastRail("Your shows", follows.take(12), vm::openShow) }
                            if (home.shows.isNotEmpty()) item {
                                PodcastRail("Find your next podcast", home.shows, vm::openShow)
                                if (home.podcastReason.isNotBlank()) Text(home.podcastReason, color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                            }
                        }
                        if (homeFilter != "Podcasts") {
                            home.rails.forEach { rail -> item("home-${rail.title}") { SCardRail(rail.title, rail.cards, vm::openCard) } }
                            home.rails.flatMap { it.cards }.firstOrNull { it.type == "album" }?.let { card -> item { FeaturedRelease(card) { vm.openCard(card) } } }
                            item { MusicVideoRail(home.videos) }
                        }
                        if (homeFilter != "Music") item { PodcastEpisodeCards(home.episodes, vm) }
                        if (recent.isNotEmpty()) {
                            item { SHeading("Recently played") }
                            items(recent.take(5), key = { "recent-${it.isrc}" }) { song -> STrack(song, currentId == song.isrc, song.isrc in favoriteIds, onPlay = { vm.play(song, recent) }, onMore = { actions = song }) }
                        }
                        if (home.loading) item { HomeSkeleton() }
                        home.error?.let { error -> item { ErrorRow(error, vm::refreshHome) } }
                    } }
                    Page.Search -> Column(Modifier.fillMaxSize().statusBarsPadding()) {
                        PageTitle("Search", { navigate(Page.Settings) })
                        SearchInput(search.query, vm::search)
                        if (search.query.isBlank()) {
                            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                                item { SHeading("Browse all") }
                                item { CategoryGrid(artwork = home.rails.flatMap { it.cards }.mapNotNull { it.artwork }.distinct(), podcastArtwork = home.shows.firstOrNull()?.artwork, onCategory = vm::openBrowse) }
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
                                if (searchFilter in listOf("All", "Music")) {
                                    if (search.tracks.isNotEmpty()) item { SHeading("Songs") }
                                    val songs = if (searchFilter == "All") search.tracks.take(6) else search.tracks
                                    items(songs, key = { "search-${it.isrc}" }) { song -> STrack(song, currentId == song.isrc, song.isrc in favoriteIds, onPlay = { keyboard?.hide(); vm.play(song, search.tracks) }, onMore = { actions = song }) }
                                    if (search.musicLoading) item { DiscoverySkeleton() }
                                    search.notice?.let { item { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(16.dp)) } }
                                }
                                if (searchFilter in listOf("All", "Podcasts")) {
                                    if (search.shows.isNotEmpty() || search.rich.podcasts.isNotEmpty()) item { SHeading("Podcasts") }
                                    val shows = if (searchFilter == "All") search.shows.take(3) else search.shows
                                    items(shows, key = { "podcast-${it.feedUrl}" }) { show -> SLibraryRow(show.title, "Podcast · ${show.author}", show.artwork) { keyboard?.hide(); vm.openShow(show) } }
                                    if (search.rich.podcasts.isNotEmpty()) item { SCardRail("From Spotify", search.rich.podcasts.take(if (searchFilter == "All") 4 else 25), vm::openCard) }
                                    if (search.podcastLoading) item { DiscoverySkeleton() }
                                    search.podcastError?.let { item { ErrorRow(it) { vm.search(search.query) } } }
                                }
                                if (searchFilter in listOf("All", "Playlists")) item { SCardRail("Playlists", search.rich.playlists, vm::openCard) }
                                if (searchFilter in listOf("All", "Albums")) item { SCardRail("Albums", search.rich.albums, vm::openCard) }
                                if (searchFilter in listOf("All", "Artists")) item { SCardRail("Artists", search.rich.artists, vm::openCard) }
                                if (searchFilter != "YouTube" && !search.musicLoading && !search.podcastLoading && search.tracks.isEmpty() && search.shows.isEmpty() && search.rich.albums.isEmpty() && search.rich.playlists.isEmpty()) item { Text("No results. Try another song, artist or show.", color = Muted, modifier = Modifier.padding(24.dp)) }
                            }
                        }
                    }
                    Page.Library -> Column(Modifier.fillMaxSize().statusBarsPadding()) {
                        PageTitle("Your Library", { navigate(Page.Settings) }, trailing = {
                            SIcon(Icons.Outlined.Search, "Search", onClick = { navigate(Page.Search) })
                            SIcon(Icons.Outlined.Add, "Create or import", onClick = { createSheet = true })
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
                                if (follows.isNotEmpty()) items(follows, key = { "follow-${it.feedUrl}" }) { show -> SLibraryRow(show.title, "Podcast · ${show.author}", show.artwork) { vm.openShow(show) } }
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
                    Page.Create -> Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
                        Text("Create", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(vertical = 24.dp))
                        ActionTile(Icons.Outlined.QueueMusic, "Playlist", "Build a collection of your favorites") { createName = "" }
                        ActionTile(Icons.Outlined.Download, "Import from Spotify", "Bring a playlist or album into your library") { navigate(Page.Import) }
                    }
                    Page.Import -> Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { SIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = { navigate(Page.Library) }); Text("Import from Spotify", style = MaterialTheme.typography.titleLarge) }
                        Text("Bring your music with you", style = MaterialTheme.typography.headlineLarge)
                        Text("Paste a public Spotify playlist or album link. We'll keep its songs, order and artwork in your library.", color = Muted)
                        OutlinedTextField(importLink, { importLink = it }, label = { Text("Spotify link") }, placeholder = { Text("https://open.spotify.com/playlist/…") }, modifier = Modifier.fillMaxWidth(), maxLines = 3, enabled = !importing)
                        Button(onClick = { keyboard?.hide(); vm.importSpotify(importLink) }, enabled = importLink.isNotBlank() && !importing, modifier = Modifier.fillMaxWidth().height(50.dp), shape = CircleShape) { Text(if (importing) "Importing…" else "Import songs", fontWeight = FontWeight.Bold) }
                        if (importing) LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Private playlists and Liked Songs need account access. You can make a playlist public and import its link. Your Spotify library is never modified.", style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
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
                MenuRow(if (song.isrc in favoriteIds) Icons.Filled.CheckCircle else Icons.Outlined.AddCircleOutline, if (song.isrc in favoriteIds) "Remove from Liked Songs" else "Save to Liked Songs") { vm.favorite(song); actions = null }
                if (song.source != TrackSource.PODCAST) MenuRow(Icons.Outlined.Album, "Go to album") { actions = null; fullPlayer = false; vm.openAlbum(song) }
                MenuRow(Icons.Outlined.PlaylistAdd, "Add to playlist") { saveTrack = song; actions = null }
                MenuRow(Icons.Outlined.QueueMusic, "Add to queue") { vm.player.addToQueue(song); actions = null }
                if (song.source != TrackSource.PODCAST) MenuRow(Icons.Outlined.Download, "Download") { vm.download(song); actions = null }
                if (song.source != TrackSource.PODCAST) MenuRow(Icons.Outlined.Person, "Go to artist") { artistTrack = song; actions = null }
                MenuRow(Icons.Outlined.Share, "Share") {
                    val text = if (song.source == TrackSource.SPOTIFY) "https://open.spotify.com/track/${song.isrc.removePrefix("spotify:")}" else "${song.title} — ${song.artist}"
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share song")); actions = null
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
private fun PageTitle(title: String, profile: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        SAvatar(profile); Text(title, Modifier.weight(1f).padding(start = 14.dp), style = MaterialTheme.typography.headlineMedium, maxLines = 1); trailing()
    }
}

@Composable
internal fun SearchInput(query: String, onChange: (String) -> Unit) {
    TextField(query, onChange, singleLine = true, leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text("What do you want to listen to?", style = MaterialTheme.typography.bodyMedium) },
        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Close, "Clear search") } },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(5.dp),
        colors = TextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, focusedTextColor = Color.Black, unfocusedTextColor = Color.Black, focusedPlaceholderColor = Color(0xFF535353), unfocusedPlaceholderColor = Color(0xFF535353), focusedLeadingIconColor = Color.Black, unfocusedLeadingIconColor = Color.Black, focusedTrailingIconColor = Color.Black, unfocusedTrailingIconColor = Color.Black, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
}

@Composable
private fun CategoryGrid(artwork: List<String>, podcastArtwork: String?, onCategory: (BrowseCategory) -> Unit) {
    val categories = BrowseCategory.entries
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        categories.chunked(2).forEachIndexed { rowIndex, pair -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            pair.forEachIndexed { columnIndex, category ->
                val name = category.title
                Box(Modifier.weight(1f).height(98.dp).clip(RoundedCornerShape(5.dp)).background(Color(category.color)).clickable { onCategory(category) }.padding(12.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(.75f))
                val image = if (name == "Podcasts") podcastArtwork else artwork.getOrNull((rowIndex * 2 + columnIndex) % artwork.size.coerceAtLeast(1))
                if (image != null) SArt(image, Modifier.size(68.dp).align(Alignment.BottomEnd).offset(x = 20.dp, y = 20.dp).rotate(22f))
                else Icon(if (name == "Podcasts") Icons.Filled.Podcasts else Icons.Filled.MusicNote, null, tint = Color.White.copy(alpha = .6f), modifier = Modifier.size(42.dp).align(Alignment.BottomEnd))
            } }
        } }
    }
}

@Composable
private fun PodcastRail(title: String, shows: List<PodcastShow>, open: (PodcastShow) -> Unit) {
    if (shows.isEmpty()) return
    SHeading(title)
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(shows, key = { it.feedUrl }) { show -> Column(Modifier.width(152.dp).clickable { open(show) }) {
            SArt(show.artwork, Modifier.size(152.dp)); Text(show.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp)); Text(show.author, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        } }
    }
}

@Composable
internal fun ErrorRow(message: String, retry: () -> Unit) {
    Column(Modifier.padding(16.dp)) { Text(message, color = Muted); TextButton(onClick = retry) { Text("Retry") } }
}
@Composable private fun HomeSkeleton() {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { repeat(3) { Box(Modifier.fillMaxWidth().height(70.dp).background(Card, RoundedCornerShape(4.dp))) } }
}
@Composable private fun ActionTile(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = action).padding(vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(36.dp)); Column(Modifier.weight(1f).padding(start = 18.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 6.dp)) }
    }
}
@Composable internal fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = action).padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null); Text(title, Modifier.padding(start = 20.dp), style = MaterialTheme.typography.bodyLarge) }
}
