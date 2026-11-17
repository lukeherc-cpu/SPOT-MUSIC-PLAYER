package com.gamut.spot

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.gamut.core.model.Track
import java.text.NumberFormat

@Composable
internal fun SpotArtistScreen(detail: SpotDetail, vm: SpotViewModel, favorites: Set<String>, playing: String?, onBack: () -> Unit, onMore: (Track) -> Unit) {
    var selected by rememberSaveable(detail.key) { mutableIntStateOf(0) }
    val follows by vm.followedArtists.collectAsState()
    val info = detail.spotify?.artistInfo
    val albums = detail.spotify?.albums.orEmpty()
    Row(Modifier.fillMaxSize().background(Black).statusBarsPadding()) {
        val scroll = rememberLazyListState()
        LaunchedEffect(selected) { scroll.scrollToItem(0) }
        LazyColumn(state = scroll, modifier = Modifier.weight(1f).fillMaxHeight(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Box(Modifier.fillMaxWidth().height(if (selected == 0) 280.dp else 190.dp)) {
                    SArt(info?.header ?: detail.artwork, Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .12f), Color.Transparent, Black))))
                    SIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
                    Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                        if (info?.verified == true) Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Verified, null, tint = Color(0xFF3D91F5), modifier = Modifier.size(16.dp)); Text("Verified artist", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 5.dp)) }
                        Text(detail.title, style = MaterialTheme.typography.headlineLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if ((info?.monthlyListeners ?: 0) > 0) Text("${NumberFormat.getIntegerInstance().format(info!!.monthlyListeners)} monthly listeners", color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { vm.followArtist(detail) }, shape = CircleShape) { Text(if (detail.key in follows) "Following" else "Follow", color = Color.White) }
                    Spacer(Modifier.weight(1f))
                    FilledIconButton(onClick = { detail.tracks.firstOrNull()?.let { vm.play(it, detail.tracks) } }, enabled = detail.tracks.isNotEmpty(), modifier = Modifier.size(54.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Green, contentColor = Color.Black)) { Icon(Icons.Filled.PlayArrow, "Play artist", Modifier.size(32.dp)) }
                }
            }
            if (detail.loading) item { DiscoverySkeleton() }
            detail.error?.let { item { ErrorRow(it, vm::retryDetail) } }
            when (selected) {
                0 -> {
                    val liked = detail.tracks.filter { it.isrc in favorites }
                    if (liked.isNotEmpty()) item { SLibraryRow("Liked songs", "${liked.size} songs · ${detail.title}", detail.artwork) { vm.openMix(DailyMix("artist-liked-${detail.key}", "Liked songs", detail.title, liked)) } }
                    if (detail.tracks.isNotEmpty()) item { SHeading("Popular") }
                    itemsIndexed(detail.tracks.take(5), key = { _, song -> song.isrc }) { index, song -> STrack(song, song.isrc == playing, song.isrc in favorites, number = index + 1, onPlay = { vm.play(song, detail.tracks) }, onMore = { onMore(song) }) }
                    if (detail.tracks.size > 5) item { TextButton(onClick = { selected = 1 }, modifier = Modifier.padding(horizontal = 12.dp)) { Text("See all songs", color = Color.White) } }
                    if (albums.isNotEmpty()) item { SCardRail("Discography", albums, vm::openCard) }
                    if (!info?.related.isNullOrEmpty()) item { SCardRail("Fans also like", info!!.related, vm::openCard) }
                    if (!info?.biography.isNullOrBlank() || (info?.followers ?: 0) > 0) item {
                        SHeading("About")
                        Column(Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(8.dp)).background(Card).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            if ((info?.followers ?: 0) > 0) Text("${NumberFormat.getIntegerInstance().format(info!!.followers)} followers", style = MaterialTheme.typography.titleLarge)
                            if (!info?.biography.isNullOrBlank()) Text(info!!.biography, style = MaterialTheme.typography.bodyMedium, color = Muted)
                        }
                    }
                }
                1 -> {
                    item { SHeading("Songs") }
                    itemsIndexed(detail.tracks, key = { _, song -> song.isrc }) { index, song -> STrack(song, song.isrc == playing, song.isrc in favorites, number = index + 1, onPlay = { vm.play(song, detail.tracks) }, onMore = { onMore(song) }) }
                }
                2 -> {
                    item { SHeading("Albums and singles") }
                    items(albums.chunked(2)) { pair ->
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            pair.forEach { album -> Column(Modifier.weight(1f).clickable { vm.openCard(album) }) {
                                SArt(album.artwork, Modifier.fillMaxWidth().aspectRatio(1f))
                                Text(album.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                                Text(album.subtitle, color = Muted, style = MaterialTheme.typography.bodySmall)
                            } }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                else -> {
                    val latest = info?.latest.orEmpty().ifEmpty { albums.take(6) }
                    if (latest.isNotEmpty()) item { SCardRail("Latest releases", latest, vm::openCard) }
                    if (latest.isEmpty() && !detail.loading) item { Text("No recent releases available.", color = Muted, modifier = Modifier.padding(20.dp)) }
                }
            }
        }
        RightArtistTabs(listOf("Overview", "Songs", "Albums", "Latest"), selected) { selected = it }
    }
}

@Composable
private fun RightArtistTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    var height by remember { mutableIntStateOf(1) }
    val select by rememberUpdatedState(onSelect)
    Column(Modifier.width(48.dp).fillMaxHeight().onSizeChanged { height = it.height.coerceAtLeast(1) }
        .semantics { contentDescription = "Artist sections, drag to browse" }
        .pointerInput(labels) {
            fun choose(y: Float) { select((y / height * labels.size).toInt().coerceIn(0, labels.lastIndex)) }
            detectDragGestures(onDragStart = { choose(it.y) }) { change, _ -> change.consume(); choose(change.position.y) }
        }) {
        labels.forEachIndexed { index, label ->
            Box(Modifier.weight(1f).fillMaxWidth().clickable { onSelect(index) }.semantics { this.selected = selected == index; contentDescription = "$label artist section" }, contentAlignment = Alignment.Center) {
                if (selected == index) Box(Modifier.align(Alignment.CenterEnd).width(3.dp).fillMaxHeight(.55f).background(Green, CircleShape))
                Text(label, color = if (selected == index) Color.White else Muted.copy(alpha = .65f), style = MaterialTheme.typography.titleMedium, fontWeight = if (selected == index) FontWeight.Bold else FontWeight.Normal, maxLines = 1, softWrap = false,
                    modifier = Modifier.layout { measurable, _ -> val p = measurable.measure(Constraints()); layout(p.height, p.width) { p.place((p.height - p.width) / 2, (p.width - p.height) / 2) } }.rotate(-90f))
            }
        }
    }
}
