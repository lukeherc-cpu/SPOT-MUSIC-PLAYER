package com.gamut.spot

import android.content.Intent
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gamut.core.designsystem.rememberCoverPalette
import com.gamut.core.model.Track

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpotCollectionScreen(detail: SpotDetail, vm: SpotViewModel, favorites: Set<String>, playing: String?, onBack: () -> Unit, onMore: (Track) -> Unit, onImport: () -> Unit) {
    val palette = rememberCoverPalette(detail.artwork)
    val tint = if (detail.key == "liked") Color(0xFF483193) else lerp(palette.primary, Black, .48f)
    val follows by vm.followedShows.collectAsState()
    var menu by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }
    var showFilter by remember { mutableStateOf(false) }
    val shuffle by vm.player.shuffleEnabled.collectAsState()
    val context = LocalContext.current
    val songs = remember(detail.tracks, filter) { detail.tracks.filter { filter.isBlank() || it.title.contains(filter, true) || it.artist.contains(filter, true) } }
    LazyColumn(Modifier.fillMaxSize().background(Black), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(tint, Black))).statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    SIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
                    Spacer(Modifier.weight(1f))
                    SIcon(Icons.Outlined.Search, "Find in this collection", onClick = { showFilter = !showFilter })
                }
                SArt(detail.artwork, Modifier.size(228.dp).align(Alignment.CenterHorizontally).padding(4.dp), liked = detail.key == "liked", circle = detail.key.startsWith("spotify:artist:"))
                Text(detail.title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 10.dp))
                if (detail.description.isNotBlank()) Text(detail.description, color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                Text(detail.subtitle, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                val length = detail.tracks.sumOf { it.durationMs } / 60000
                Text(if (detail.podcast != null) "${detail.tracks.size} episodes" else "${detail.tracks.size} songs${if (length > 0) " · ${length / 60} hr ${length % 60} min" else ""}", color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (detail.podcast != null) {
                        val following = follows.any { it.feedUrl == detail.podcast.feedUrl }
                        OutlinedButton(onClick = { vm.podcastStore.toggle(detail.podcast) }, shape = CircleShape, modifier = Modifier.padding(start = 8.dp)) { Text(if (following) "Following" else "Follow", color = Color.White) }
                    } else if (detail.spotify != null) SIcon(if (vm.isImported(detail.key)) Icons.Filled.CheckCircle else Icons.Outlined.AddCircleOutline, "Save collection to library", tint = if (vm.isImported(detail.key)) Green else Muted, onClick = onImport)
                    if (detail.podcast == null) SIcon(Icons.Outlined.DownloadForOffline, "Download collection", tint = Muted, onClick = { vm.downloads.download(detail.tracks, detail.title) })
                    SIcon(Icons.Outlined.MoreVert, "Collection options", tint = Muted, onClick = { menu = true })
                    Spacer(Modifier.weight(1f))
                    if (detail.podcast == null) SIcon(Icons.Outlined.Shuffle, "Shuffle", tint = if (shuffle) Green else Muted, onClick = { vm.player.setShuffle(!shuffle) })
                    FilledIconButton(onClick = { detail.tracks.firstOrNull()?.let { val wasShuffle = shuffle; vm.play(it, detail.tracks); vm.player.setShuffle(wasShuffle) } }, enabled = detail.tracks.isNotEmpty(), modifier = Modifier.size(56.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Green, contentColor = Color.Black)) { Icon(Icons.Filled.PlayArrow, "Play collection", Modifier.size(34.dp)) }
                }
            }
        }
        if (showFilter) item { SearchInput(filter) { filter = it } }
        if (detail.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp)) }
        detail.error?.let { item { ErrorRow(it, vm::retryDetail) } }
        if (detail.skipped > 0) item { Text("${detail.skipped} unavailable items aren't included.", style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(16.dp)) }
        if (detail.podcast != null) {
            item { SHeading("All episodes") }
            val byId = detail.episodes.associateBy { it.track(detail.podcast).isrc }
            items(songs, key = { it.isrc }) { song ->
                val episode = byId[song.isrc]
                val resume = vm.podcastStore.position(song.isrc)
                Column(Modifier.fillMaxWidth().clickable { vm.play(song, detail.tracks) }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SArt(song.albumArtUrl, Modifier.size(64.dp))
                        Text(song.title, Modifier.weight(1f).padding(start = 14.dp), style = MaterialTheme.typography.titleMedium, color = if (song.isrc == playing) Green else Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    if (!episode?.description.isNullOrBlank()) Text(episode!!.description, style = MaterialTheme.typography.bodyMedium, color = Muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(episode?.published?.take(16)?.takeIf { it.isNotBlank() }, (song.durationMs / 60000).takeIf { it > 0 }?.let { "$it min" }).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Muted)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SIcon(Icons.Outlined.AddCircleOutline, "Save episode", onClick = { vm.favorite(song) })
                        SIcon(Icons.Outlined.MoreHoriz, "Episode options", tint = Muted, onClick = { onMore(song) })
                        Spacer(Modifier.weight(1f))
                        Text(if (resume > 0) "Resume · ${timeLabel(resume)}" else "Play", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 8.dp))
                        FilledIconButton(onClick = { vm.play(song, detail.tracks) }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black)) { Icon(Icons.Filled.PlayArrow, "Play episode") }
                    }
                }
                HorizontalDivider(color = Color.White.copy(alpha = .08f), modifier = Modifier.padding(horizontal = 16.dp))
            }
        } else itemsIndexed(songs, key = { _, song -> song.isrc }) { index, song -> STrack(song, song.isrc == playing, song.isrc in favorites, number = if (detail.key.startsWith("spotify:artist:")) index + 1 else null, onPlay = { vm.play(song, detail.tracks) }, onMore = { onMore(song) }) }
        if (!detail.spotify?.albums.isNullOrEmpty()) item { SCardRail("Albums and singles", detail.spotify!!.albums, vm::openCard) }
        if (!detail.loading && detail.error == null && detail.tracks.isEmpty()) item { Text("No available audio in this collection.", color = Muted, modifier = Modifier.padding(24.dp)) }
    }
    if (menu) ModalBottomSheet(onDismissRequest = { menu = false }, containerColor = Color(0xFF222222)) {
        Column(Modifier.padding(bottom = 32.dp)) {
            SLibraryRow(detail.title, detail.subtitle, detail.artwork, liked = detail.key == "liked") {}
            if (detail.spotify != null) MenuRow(Icons.Outlined.AddCircleOutline, "Import to your library") { menu = false; onImport() }
            if (detail.podcast == null) MenuRow(Icons.Outlined.Download, "Download") { vm.downloads.download(detail.tracks, detail.title); menu = false }
            MenuRow(Icons.Outlined.Share, "Share") {
                val link = if (detail.key.startsWith("spotify:")) "https://open.spotify.com/${detail.key.removePrefix("spotify:").replace(':', '/')}" else detail.podcast?.feedUrl ?: detail.title
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link), "Share collection")); menu = false
            }
            detail.key.toLongOrNull()?.let { id -> MenuRow(Icons.Outlined.DeleteOutline, "Delete playlist") { vm.deletePlaylist(id); menu = false } }
        }
    }
}
