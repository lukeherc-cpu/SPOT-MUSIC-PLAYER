package com.gamut.spot

import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Rect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.*
import com.gamut.feature.playerui.LyricsLine
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamut.core.designsystem.rememberCoverPalette
import com.gamut.core.model.*
import com.gamut.core.player.MusicPlayer
import com.gamut.feature.playerui.LyricsRepository
import com.gamut.feature.playerui.LyricsState

@Composable
internal fun SpotMiniPlayer(track: Track, player: MusicPlayer, playing: Boolean, opening: Boolean, saved: Boolean, onOpen: () -> Unit, onSave: () -> Unit, onBounds: (Rect) -> Unit = {}) {
    val palette = rememberCoverPalette(track.albumArtUrl)
    val background = lerp(palette.primary, Color(0xFF171717), .68f)
    val state by player.playbackState.collectAsState()
    val duration by player.streamDurationMs.collectAsState()
    val awaiting by player.awaitingAudio.collectAsState()
    val position = when (val value = state) { is PlaybackState.Playing -> value.positionMs; is PlaybackState.Paused -> value.positionMs; else -> 0L }
    val total = duration.takeIf { it > 0 } ?: track.durationMs
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).onGloballyPositioned { onBounds(it.boundsInRoot()) }.clip(RoundedCornerShape(6.dp)).background(background).clickable(onClick = onOpen)) {
        Row(Modifier.fillMaxWidth().height(60.dp).padding(start = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            SArt(track.albumArtUrl, Modifier.size(44.dp))
            Column(Modifier.weight(1f).padding(start = 10.dp, end = 4.dp)) {
                Text(track.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (opening || awaiting) "Connecting…" else (if (track.isExplicit == true) "E · " else "") + track.artist, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            SIcon(if (saved) Icons.Filled.CheckCircle else Icons.Outlined.AddCircleOutline, "Save song", tint = if (saved) Green else Color.White, size = 24.dp, onClick = onSave)
            if (opening || awaiting) Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { SkeletonPulse { Box(Modifier.size(24.dp).background(Color.White.copy(alpha = .25f), CircleShape)) } }
            else SIcon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pause" else "Play", size = 32.dp, onClick = { if (playing) player.pause() else player.resume() })
        }
        LinearProgressIndicator(progress = { if (total > 0) (position.toFloat() / total).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth().height(2.dp).padding(horizontal = 8.dp), color = Color.White, trackColor = Color.White.copy(alpha = .2f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpotPlayerScreen(vm: SpotViewModel, track: Track, playing: Boolean, opening: Boolean, favorites: Set<String>, onBack: () -> Unit, onMore: (Track) -> Unit, onSave: () -> Unit) {
    val state by vm.player.playbackState.collectAsState()
    val queue by vm.player.queue.collectAsState()
    val shuffle by vm.player.shuffleEnabled.collectAsState()
    val repeat by vm.player.repeatMode.collectAsState()
    val actualDuration by vm.player.streamDurationMs.collectAsState()
    val awaiting by vm.player.awaitingAudio.collectAsState()
    val subtitles by vm.podcastSubtitles.state.collectAsState()
    val position = when (val value = state) { is PlaybackState.Playing -> value.positionMs; is PlaybackState.Paused -> value.positionMs; else -> 0L }
    val duration = (actualDuration.takeIf { it > 0 } ?: track.durationMs).coerceAtLeast(1)
    val podcast = track.source == TrackSource.PODCAST
    val palette = rememberCoverPalette(track.albumArtUrl)
    val top = lerp(palette.primary, Color.Black, .55f)
    val wordsColor = lerp(palette.primary, Color(0xFF343D39), .72f)
    val context = LocalContext.current
    val artistTap = LocalArtistTap.current
    var queueSheet by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf(false) }
    var expanded by rememberSaveable(track.isrc) { mutableStateOf(false) }
    var showWords by rememberSaveable(track.isrc) { mutableStateOf(false) }
    val flip by animateFloatAsState(if (showWords) 180f else 0f, tween(380), label = "cover text flip")
    var words by remember(track.isrc) { mutableStateOf<LyricsState>(LyricsState.Loading) }
    var cleanText by remember(track.isrc) { mutableStateOf(CleanLyrics.required(track)) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(track.isrc, showWords, subtitles) {
        if (podcast && subtitles.trackId == track.isrc) {
            words = when {
                subtitles.cues.isNotEmpty() -> LyricsState.Loaded(subtitles.cues.map { LyricsLine(it.startMs, it.text, it.endMs) }, subtitles.timed)
                subtitles.error != null -> LyricsState.Error(subtitles.error!!)
                else -> LyricsState.Loading
            }
        }
        if (showWords && words is LyricsState.Loading) {
            if (!podcast) {
                val lyricTrack = vm.lyricTrack(track)
                cleanText = CleanLyrics.required(lyricTrack)
                words = CleanLyrics.apply(lyricTrack, LyricsRepository.fetchLyrics(lyricTrack, duration))
            }
        }
    }
    val displayWords = remember(track.isrc, track.title, track.isExplicit, words) { if (podcast) words else CleanLyrics.apply(track, words) }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(top, Black)))) {
        // The player itself never scrolls. The square artwork gives its space to the text.
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().then(if (expanded) Modifier.clearAndSetSemantics { } else Modifier)) {
            Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                SIcon(Icons.Outlined.KeyboardArrowDown, "Close player", onClick = onBack)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (podcast) "PLAYING EPISODE" else "PLAYING FROM YOUR MUSIC", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                    Text(track.album ?: track.artist, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                SIcon(Icons.Outlined.MoreVert, "Player options", onClick = { onMore(track) })
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                val side = minOf(maxWidth, maxHeight)
                Box(Modifier.size(side).graphicsLayer { rotationY = flip; cameraDistance = 16 * density }) {
                    if (flip > 90f) Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) {
                        SpotWordsPanel(if (podcast) "Subtitles" else "Lyrics", displayWords, position, wordsColor, false, { expanded = true }, { showWords = false }, vm.player::seekTo,
                            caption = if (podcast && subtitles.generated) "Auto-generated subtitles" else if (!podcast && cleanText) "Clean lyrics" else null)
                    } else Box(Modifier.fillMaxSize().clickable(onClickLabel = if (podcast) "Show subtitles" else "Show lyrics") { showWords = true }.semantics { contentDescription = if (podcast) "Cover art, show subtitles" else "Cover art, show lyrics" }) {
                        SArt(track.albumArtUrl, Modifier.fillMaxSize())
                        Surface(Modifier.align(Alignment.BottomCenter).padding(10.dp), shape = CircleShape, color = Color.Black.copy(alpha = .58f), contentColor = Color.White) {
                            Text(if (podcast) "Tap for subtitles" else "Tap for lyrics", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp))
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (track.isExplicit == true) ExplicitBadge()
                        Text(track.artist, color = Color.White.copy(alpha = .7f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { artistTap(track) })
                    }
                }
                SIcon(if (track.isrc in favorites) Icons.Filled.CheckCircle else Icons.Outlined.AddCircleOutline, "Save song", tint = if (track.isrc in favorites) Green else Color.White, size = 28.dp, onClick = onSave)
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                SpotSeekBar(value = dragging ?: (position.toFloat() / duration).coerceIn(0f, 1f), onChange = { dragging = it }, onFinished = { dragging?.let { vm.player.seekTo((it * duration).toLong()) }; dragging = null }, onCancel = { dragging = null })
                Row(Modifier.fillMaxWidth()) {
                    Text(timeLabel(dragging?.let { (it * duration).toLong() } ?: position), color = Muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.weight(1f)); Text("−${timeLabel((duration - position).coerceAtLeast(0))}", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(Modifier.fillMaxWidth().height(82.dp).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                if (podcast) SIcon(Icons.Outlined.SkipPrevious, "Previous episode", tint = Muted, onClick = vm.player::skipPrevious)
                else SIcon(Icons.Outlined.Shuffle, "Shuffle", tint = if (shuffle) Green else Muted, onClick = { vm.player.setShuffle(!shuffle) })
                if (podcast) SIcon(Icons.Outlined.Replay10, "Back 15 seconds", onClick = { vm.player.seekTo((position - 15000).coerceAtLeast(0)) })
                else SIcon(Icons.Filled.SkipPrevious, "Previous song", size = 36.dp, onClick = vm.player::skipPrevious)
                FilledIconButton(onClick = { if (playing) vm.player.pause() else vm.player.resume() }, enabled = !opening, modifier = Modifier.size(64.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black, disabledContainerColor = Color.White, disabledContentColor = Color.Black)) {
                    if (opening || awaiting) SkeletonPulse { Box(Modifier.size(28.dp).background(Color.Black.copy(alpha = .2f), CircleShape)) }
                    else Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pause" else "Play", Modifier.size(40.dp))
                }
                if (podcast) SIcon(Icons.Outlined.Forward10, "Forward 15 seconds", onClick = { vm.player.seekTo((position + 15000).coerceAtMost(duration)) })
                else SIcon(Icons.Filled.SkipNext, "Next song", size = 36.dp, onClick = vm.player::skipNext)
                if (podcast) SIcon(Icons.Outlined.SkipNext, "Next episode", tint = Muted, onClick = vm.player::skipNext)
                else SIcon(if (repeat == RepeatMode.ONE) Icons.Outlined.RepeatOne else Icons.Outlined.Repeat, "Repeat", tint = if (repeat != RepeatMode.OFF) Green else Muted, onClick = { vm.player.setRepeatMode(when (repeat) { RepeatMode.OFF -> RepeatMode.ALL; RepeatMode.ALL -> RepeatMode.ONE; RepeatMode.ONE -> RepeatMode.OFF }) })
            }
            Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                SIcon(Icons.Outlined.SpeakerGroup, "Listening device", size = 22.dp, onClick = { devices = true })
                Spacer(Modifier.weight(1f))
                SIcon(Icons.Outlined.Subtitles, if (podcast) "Show subtitles" else "Show lyrics", tint = if (showWords) Green else Color.White, size = 22.dp, onClick = { showWords = !showWords })
                SIcon(Icons.Outlined.Share, "Share", size = 22.dp, onClick = {
                    val text = if (track.source == TrackSource.SPOTIFY) "https://open.spotify.com/track/${track.isrc.removePrefix("spotify:")}" else "${track.title} — ${track.artist}"
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share song"))
                })
                SIcon(Icons.Outlined.QueueMusic, "Queue", size = 22.dp, onClick = { queueSheet = true })
            }
        }
        if (expanded) FullScreenWords(if (podcast) "Subtitles" else "Lyrics", displayWords, position, wordsColor, { expanded = false }, { expanded = false; showWords = false }, vm.player::seekTo,
            caption = if (podcast && subtitles.generated) "Auto-generated subtitles" else if (!podcast && cleanText) "Clean lyrics" else null)
    }
    if (queueSheet) ModalBottomSheet(onDismissRequest = { queueSheet = false }, containerColor = Color(0xFF222222)) {
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            item { SHeading("Next in queue") }
            itemsIndexed(queue.tracks, key = { index, song -> "$index-${song.isrc}" }) { index, song -> STrack(song, index == queue.currentIndex, song.isrc in favorites, onPlay = { vm.player.skipTo(index); queueSheet = false }, onMore = { queueSheet = false; onMore(song) }) }
        }
    }
    if (devices) ModalBottomSheet(onDismissRequest = { devices = false }, containerColor = Color(0xFF222222)) {
        Column(Modifier.padding(24.dp).padding(bottom = 24.dp)) {
            Text("Listening on this device", style = MaterialTheme.typography.titleLarge)
            Text("Switch between your phone, headphones and connected Bluetooth speakers.", color = Muted, modifier = Modifier.padding(vertical = 20.dp))
            Button(onClick = { context.startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)); devices = false }) { Text("Open Bluetooth settings") }
        }
    }
}
