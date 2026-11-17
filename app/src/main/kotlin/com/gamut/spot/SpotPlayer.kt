package com.gamut.spot

import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
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
internal fun SpotMiniPlayer(track: Track, player: MusicPlayer, playing: Boolean, opening: Boolean, saved: Boolean, onOpen: () -> Unit, onSave: () -> Unit) {
    val palette = rememberCoverPalette(track.albumArtUrl)
    val background = lerp(palette.primary, Color(0xFF171717), .68f)
    val state by player.playbackState.collectAsState()
    val duration by player.streamDurationMs.collectAsState()
    val awaiting by player.awaitingAudio.collectAsState()
    val position = when (val value = state) { is PlaybackState.Playing -> value.positionMs; is PlaybackState.Paused -> value.positionMs; else -> 0L }
    val total = duration.takeIf { it > 0 } ?: track.durationMs
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).clip(RoundedCornerShape(6.dp)).background(background).clickable(onClick = onOpen)) {
        Row(Modifier.fillMaxWidth().height(60.dp).padding(start = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            SArt(track.albumArtUrl, Modifier.size(44.dp))
            Column(Modifier.weight(1f).padding(start = 10.dp, end = 4.dp)) {
                Text(track.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (opening || awaiting) "Connecting…" else track.artist, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            SIcon(if (saved) Icons.Filled.CheckCircle else Icons.Outlined.AddCircleOutline, "Save song", tint = if (saved) Green else Color.White, size = 24.dp, onClick = onSave)
            if (opening || awaiting) Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp) }
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
    val position = when (val value = state) { is PlaybackState.Playing -> value.positionMs; is PlaybackState.Paused -> value.positionMs; else -> 0L }
    val duration = (actualDuration.takeIf { it > 0 } ?: track.durationMs).coerceAtLeast(1)
    val podcast = track.source == TrackSource.PODCAST
    val palette = rememberCoverPalette(track.albumArtUrl)
    val top = lerp(palette.primary, Color.Black, .55f)
    val context = LocalContext.current
    var queueSheet by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf(false) }
    var lyricsExpanded by remember { mutableStateOf(false) }
    var lyrics by remember(track.isrc) { mutableStateOf<LyricsState>(LyricsState.Loading) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(track.isrc) { if (!podcast) lyrics = LyricsRepository.fetchLyrics(track) }
    LazyColumn(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(top, Black))).statusBarsPadding(), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 30.dp), verticalAlignment = Alignment.CenterVertically) {
                SIcon(Icons.Outlined.KeyboardArrowDown, "Close player", onClick = onBack)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (podcast) "PLAYING EPISODE" else "PLAYING FROM YOUR MUSIC", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                    Text(track.album ?: track.artist, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                SIcon(Icons.Outlined.MoreVert, "Player options", onClick = { onMore(track) })
            }
        }
        item { SArt(track.albumArtUrl, Modifier.fillMaxWidth().aspectRatio(1f)) }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 30.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, color = Color.White.copy(alpha = .7f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
                }
                SIcon(if (track.isrc in favorites) Icons.Filled.CheckCircle else Icons.Outlined.AddCircleOutline, "Save to Liked Songs", tint = if (track.isrc in favorites) Green else Color.White, size = 29.dp, onClick = onSave)
            }
        }
        item {
            Slider(
                value = dragging ?: (position.toFloat() / duration).coerceIn(0f, 1f),
                onValueChange = { dragging = it },
                onValueChangeFinished = { dragging?.let { vm.player.seekTo((it * duration).toLong()) }; dragging = null },
                modifier = Modifier.fillMaxWidth().height(34.dp).padding(top = 10.dp),
                thumb = { Box(Modifier.size(10.dp).clip(CircleShape).background(Color.White)) },
                track = { slider ->
                    Canvas(Modifier.fillMaxWidth().height(3.dp)) {
                        val start = Offset(0f, size.height / 2)
                        val end = Offset(size.width, size.height / 2)
                        drawLine(Color.White.copy(alpha = .25f), start, end, size.height, StrokeCap.Round)
                        drawLine(Color.White, start, Offset(size.width * slider.value.coerceIn(0f, 1f), end.y), size.height, StrokeCap.Round)
                    }
                },
            )
            Row(Modifier.fillMaxWidth()) {
                Text(timeLabel(dragging?.let { (it * duration).toLong() } ?: position), color = Muted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.weight(1f))
                Text("−${timeLabel((duration - position).coerceAtLeast(0))}", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                if (podcast) SIcon(Icons.Outlined.SkipPrevious, "Previous episode", tint = Muted, onClick = vm.player::skipPrevious)
                else SIcon(Icons.Outlined.Shuffle, "Shuffle", tint = if (shuffle) Green else Muted, onClick = { vm.player.setShuffle(!shuffle) })
                if (podcast) Text("↶ 15", color = Color.White, fontSize = 20.sp, modifier = Modifier.clickable { vm.player.seekTo((position - 15000).coerceAtLeast(0)) }.padding(12.dp))
                else SIcon(Icons.Filled.SkipPrevious, "Previous song", size = 36.dp, onClick = vm.player::skipPrevious)
                FilledIconButton(onClick = { if (playing) vm.player.pause() else vm.player.resume() }, enabled = !opening, modifier = Modifier.size(68.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black, disabledContainerColor = Color.White, disabledContentColor = Color.Black)) {
                    if (opening || awaiting) CircularProgressIndicator(Modifier.size(30.dp), color = Color.Black, strokeWidth = 2.dp)
                    else Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pause" else "Play", Modifier.size(42.dp))
                }
                if (podcast) Text("15 ↷", color = Color.White, fontSize = 20.sp, modifier = Modifier.clickable { vm.player.seekTo((position + 15000).coerceAtMost(duration)) }.padding(12.dp))
                else SIcon(Icons.Filled.SkipNext, "Next song", size = 36.dp, onClick = vm.player::skipNext)
                if (podcast) SIcon(Icons.Outlined.SkipNext, "Next episode", tint = Muted, onClick = vm.player::skipNext)
                else SIcon(if (repeat == RepeatMode.ONE) Icons.Outlined.RepeatOne else Icons.Outlined.Repeat, "Repeat", tint = if (repeat != RepeatMode.OFF) Green else Muted, onClick = { vm.player.setRepeatMode(when (repeat) { RepeatMode.OFF -> RepeatMode.ALL; RepeatMode.ALL -> RepeatMode.ONE; RepeatMode.ONE -> RepeatMode.OFF }) })
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                SIcon(Icons.Outlined.SpeakerGroup, "Listening device", size = 22.dp, onClick = { devices = true })
                Spacer(Modifier.weight(1f))
                SIcon(Icons.Outlined.Share, "Share", size = 22.dp, onClick = {
                    val text = if (track.source == TrackSource.SPOTIFY) "https://open.spotify.com/track/${track.isrc.removePrefix("spotify:")}" else "${track.title} — ${track.artist}"
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share song"))
                })
                SIcon(Icons.Outlined.QueueMusic, "Queue", size = 22.dp, onClick = { queueSheet = true })
            }
        }
        if (!podcast) item {
            val loaded = lyrics as? LyricsState.Loaded
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(lerp(palette.primary, Color(0xFF4A3D13), .8f)).padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Lyrics", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); SIcon(Icons.Outlined.OpenInFull, "Expand lyrics", size = 20.dp, onClick = { lyricsExpanded = !lyricsExpanded }) }
                when {
                    loaded != null -> {
                        val lines = if (lyricsExpanded) loaded.lines else loaded.lines.take(8)
                        lines.forEach { line -> Text(line.text.ifBlank { "♪" }, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 31.sp, color = if (!loaded.isSynced || line.timestampMs <= position) Color.White else Color.White.copy(alpha = .55f), modifier = Modifier.fillMaxWidth().clickable(enabled = loaded.isSynced) { vm.player.seekTo(line.timestampMs) }.padding(vertical = 4.dp)) }
                        if (!lyricsExpanded && loaded.lines.size > 8) TextButton(onClick = { lyricsExpanded = true }) { Text("Show lyrics", color = Color.White) }
                    }
                    lyrics is LyricsState.Loading -> Text("Finding lyrics…", style = MaterialTheme.typography.bodyMedium)
                    else -> Text("Lyrics aren't available for this song.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
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
            Text("Use Android's media output selector to switch between your phone, headphones and connected Bluetooth speakers.", color = Muted, modifier = Modifier.padding(vertical = 20.dp))
            Button(onClick = { context.startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)); devices = false }) { Text("Open Bluetooth settings") }
        }
    }
}
