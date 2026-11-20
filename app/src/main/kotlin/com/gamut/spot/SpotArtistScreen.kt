package com.gamut.spot

import androidx.compose.foundation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import kotlinx.coroutines.launch
import com.gamut.core.model.Track
import java.text.NumberFormat

// One continuous page with four sections. The right-hand rail jumps to a section within the same
// page and follows along as the listener scrolls, rather than switching to a separate page.
@Composable
internal fun SpotArtistScreen(detail: SpotDetail, vm: SpotViewModel, favorites: Set<String>, playing: String?, onBack: () -> Unit, onMore: (Track) -> Unit) {
    val follows by vm.followedArtists.collectAsState()
    val info = detail.spotify?.artistInfo
    val albums = detail.spotify?.albums.orEmpty()
    val popular = detail.tracks.take(10)
    var yours by remember(detail.key) { mutableStateOf<List<Track>>(emptyList()) }
    LaunchedEffect(detail.key, detail.title) { yours = vm.mostPlayedBy(detail.title) }
    val rows = albums.chunked(2)
    // Item counts per section: overview 1, songs heading + popular + "your most listened to", albums heading + rows, latest 1.
    val starts = listOf(0, 1, 1 + 1 + popular.size + 1, 1 + 1 + popular.size + 1 + 1 + rows.size)
    val scroll = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val current by remember(starts) { derivedStateOf { if (!scroll.canScrollForward && scroll.firstVisibleItemIndex > 0) starts.lastIndex else starts.indexOfLast { it <= scroll.firstVisibleItemIndex }.coerceAtLeast(0) } }
    Row(Modifier.fillMaxSize().background(Black).statusBarsPadding()) {
        LazyColumn(state = scroll, modifier = Modifier.weight(1f).fillMaxHeight(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "overview") {
                Column {
                    Box(Modifier.fillMaxWidth().height(280.dp)) {
                        if (detail.loading && detail.artwork == null) SkeletonPulse(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize().background(Card)) }
                        else SArt(info?.header ?: detail.artwork, Modifier.fillMaxSize())
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .12f), Color.Transparent, Black))))
                        SIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
                        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                            if (info?.verified == true) Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Verified, null, tint = Color(0xFF3D91F5), modifier = Modifier.size(16.dp)); Text("Verified artist", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 5.dp)) }
                            Text(detail.title, style = MaterialTheme.typography.headlineLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Text(if ((info?.monthlyListeners ?: 0) > 0) "${NumberFormat.getIntegerInstance().format(info!!.monthlyListeners)} monthly listeners" else if (detail.loading) " " else "Monthly listeners unavailable",
                        color = Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { vm.followArtist(detail) }, shape = CircleShape) { Text(if (detail.key in follows) "Following" else "Follow", color = Color.White) }
                        Spacer(Modifier.weight(1f))
                        FilledIconButton(onClick = { vm.playCollection(detail.tracks) }, enabled = detail.tracks.isNotEmpty(), modifier = Modifier.size(54.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Green, contentColor = Color.Black)) { Icon(Icons.Filled.PlayArrow, "Play artist", Modifier.size(32.dp)) }
                    }
                    if (detail.loading) DiscoverySkeleton()
                    detail.error?.let { ErrorRow(it, vm::retryDetail) }
                    val liked = detail.tracks.filter { it.isrc in favorites }
                    if (liked.isNotEmpty()) SLibraryRow("Liked songs", "${liked.size} songs · ${detail.title}", detail.artwork) { vm.openMix(DailyMix("artist-liked-${detail.key}", "Liked songs", detail.title, liked)) }
                    if (!info?.biography.isNullOrBlank() || (info?.followers ?: 0) > 0) {
                        SHeading("About")
                        Column(Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(8.dp)).background(Card).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            if ((info?.followers ?: 0) > 0) Text("${NumberFormat.getIntegerInstance().format(info!!.followers)} followers", style = MaterialTheme.typography.titleLarge)
                            if (!info?.biography.isNullOrBlank()) Text(info!!.biography, style = MaterialTheme.typography.bodyMedium, color = Muted, maxLines = 6, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            item(key = "songs") { SHeading("Popular") }
            itemsIndexed(popular, key = { index, song -> "popular-$index-${song.isrc}" }) { index, song -> STrack(song, song.isrc == playing, song.isrc in favorites, number = index + 1, onPlay = { vm.play(song, detail.tracks) }, onMore = { onMore(song) }) }
            item(key = "yours") {
                Column {
                    SHeading("Your most listened to")
                    if (yours.isEmpty()) Text("Play a few songs by ${detail.title} and your favourites show up here.", color = Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    yours.forEachIndexed { index, song -> STrack(song, song.isrc == playing, song.isrc in favorites, number = index + 1, onPlay = { vm.play(song, yours) }, onMore = { onMore(song) }) }
                }
            }
            item(key = "albums") { SHeading("Albums and singles") }
            itemsIndexed(rows, key = { index, _ -> "albums-$index" }) { _, pair ->
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { album -> Column(Modifier.weight(1f).clickable { vm.openCard(album) }) {
                        SArt(album.artwork, Modifier.fillMaxWidth().aspectRatio(1f))
                        Text(album.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                        Text(album.subtitle, color = Muted, style = MaterialTheme.typography.bodySmall)
                    } }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            item(key = "latest") {
                Column(Modifier.heightIn(min = 420.dp)) {
                    val latest = info?.latest.orEmpty().ifEmpty { albums.take(6) }
                    if (latest.isNotEmpty()) SCardRail("Latest releases", latest, vm::openCard)
                    else if (!detail.loading) { SHeading("Latest releases"); Text("No recent releases available.", color = Muted, modifier = Modifier.padding(horizontal = 16.dp)) }
                    if (!info?.related.isNullOrEmpty()) SCardRail("Fans also like", info!!.related, vm::openCard)
                }
            }
        }
        Box(Modifier.width(48.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            RightArtistTabs(listOf("Overview", "Songs", "Albums", "Latest"), current,
                onDrag = { index -> scope.launch { scroll.scrollToItem(starts[index]) } },
                onSelect = { index -> scope.launch { scroll.animateScrollToItem(starts[index]) } })
        }
    }
}

@Composable
private fun RightArtistTabs(labels: List<String>, selected: Int, onDrag: (Int) -> Unit, onSelect: (Int) -> Unit) {
    var height by remember { mutableIntStateOf(1) }
    val select by rememberUpdatedState(onDrag)
    var dragY by remember { mutableStateOf<Float?>(null) }
    val indicator by animateFloatAsState(dragY ?: ((selected + .5f) * height / labels.size), spring(stiffness = Spring.StiffnessMediumLow), label = "artist thumb")
    Box(Modifier.width(48.dp).fillMaxHeight(.56f).heightIn(max = 360.dp).onSizeChanged { height = it.height.coerceAtLeast(1) }
        .semantics { contentDescription = "Artist sections, drag to browse" }
        .pointerInput(labels) {
            var last = -1
            fun choose(y: Float) { dragY = y.coerceIn(0f, height.toFloat()); val index = (y / height * labels.size).toInt().coerceIn(0, labels.lastIndex); if (index != last) { last = index; select(index) } }
            detectDragGestures(onDragStart = { last = -1; choose(it.y) }, onDragEnd = { dragY = null }, onDragCancel = { dragY = null }) { change, _ -> change.consume(); choose(change.position.y) }
        }) {
        Box(Modifier.align(Alignment.TopEnd).width(3.dp).height(28.dp).offset { IntOffset(0, (indicator - 14.dp.roundToPx()).toInt()) }.background(Green, CircleShape))
        Column(Modifier.fillMaxSize()) {
        labels.forEachIndexed { index, label ->
            val color by animateColorAsState(if (selected == index) Color.White else Muted.copy(alpha = .65f), tween(160), label = "artist section color")
            val distance = kotlin.math.abs((index + .5f) * height / labels.size - (dragY ?: indicator)) / (height.toFloat() / labels.size)
            val wave = if (dragY == null) 0f else kotlin.math.exp(-distance * distance * 1.7f)
            val shift by animateFloatAsState(wave, spring(stiffness = Spring.StiffnessMediumLow), label = "artist rail wave")
            Box(Modifier.weight(1f).fillMaxWidth().selectable(selected = selected == index, role = Role.Tab) { onSelect(index) }.semantics { contentDescription = "$label artist section" }, contentAlignment = Alignment.Center) {
                Text(label, color = color, style = MaterialTheme.typography.titleMedium, fontWeight = if (selected == index) FontWeight.Bold else FontWeight.Normal, maxLines = 1, softWrap = false,
                    modifier = Modifier.graphicsLayer { translationX = -24.dp.toPx() * shift; scaleX = 1 + .12f * shift; scaleY = 1 + .12f * shift }.layout { measurable, _ -> val p = measurable.measure(Constraints()); layout(p.height, p.width) { p.place((p.height - p.width) / 2, (p.width - p.height) / 2) } }.rotate(-90f))
            }
        }
        }
    }
}
