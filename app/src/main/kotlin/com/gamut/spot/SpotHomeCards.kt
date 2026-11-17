package com.gamut.spot

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gamut.core.network.SpotifyCard
import com.gamut.core.network.YoutubeCandidate

@Composable
internal fun DiscoverySkeleton() {
    val pulse by rememberInfiniteTransition(label = "discovery skeleton").animateFloat(.45f, .85f, infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "skeleton pulse")
    Column(Modifier.fillMaxWidth().padding(16.dp).graphicsLayer { alpha = pulse }, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Box(Modifier.fillMaxWidth(.55f).height(24.dp).background(Card, RoundedCornerShape(4.dp)))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(2) { Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Card, RoundedCornerShape(5.dp)))
                Box(Modifier.fillMaxWidth(.8f).height(14.dp).background(Card, RoundedCornerShape(3.dp)))
                Box(Modifier.fillMaxWidth(.55f).height(10.dp).background(Card, RoundedCornerShape(3.dp)))
            } }
        }
        repeat(3) { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(52.dp).background(Card, RoundedCornerShape(4.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.fillMaxWidth(.8f).height(15.dp).background(Card, RoundedCornerShape(3.dp)))
                Box(Modifier.fillMaxWidth(.5f).height(10.dp).background(Card, RoundedCornerShape(3.dp)))
            }
        } }
    }
}

@Composable
internal fun FeaturedRelease(card: SpotifyCard, open: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(Color(0xFF413C70), Color(0xFF23212F)))).clickable(onClick = open).padding(16.dp)) {
        Text("Picked for your rotation", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = .75f))
        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            SArt(card.artwork, Modifier.size(124.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(card.type.replaceFirstChar(Char::titlecase), color = Muted, style = MaterialTheme.typography.bodySmall)
                Text(card.title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(card.subtitle, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Explore this release", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Box(Modifier.size(44.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Filled.ArrowForward, null, tint = Color.Black) }
        }
    }
}

@Composable
internal fun PodcastEpisodeCards(episodes: List<HomeEpisode>, vm: SpotViewModel) {
    if (episodes.isEmpty()) return
    SHeading("Episodes for you")
    episodes.take(3).forEachIndexed { index, item ->
        val song = item.episode.track(item.show)
        val save = LocalSaveTrack.current
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(9.dp)).background(listOf(Color(0xFF343A33), Color(0xFF642D40), Color(0xFF69432D))[index % 3]).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SArt(song.albumArtUrl, Modifier.size(if (index == 0) 100.dp else 72.dp).clickable { vm.openShow(item.show) })
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(item.show.title, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .7f), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { vm.openShow(item.show) })
                }
            }
            if (item.episode.description.isNotBlank()) Text(item.episode.description, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = .7f), maxLines = if (index == 0) 3 else 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 16.dp))
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (song.durationMs > 0) "${song.durationMs / 60000} min" else "Episode", color = Color.White.copy(alpha = .7f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                SIcon(Icons.Outlined.AddCircleOutline, "Save episode", onClick = { save(song) })
                FilledIconButton(onClick = { vm.play(song, listOf(song)) }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black)) { Icon(Icons.Filled.PlayArrow, "Play ${song.title}") }
            }
        }
    }
}

@Composable
internal fun MusicVideoRail(videos: List<YoutubeCandidate>) {
    if (videos.isEmpty()) return
    val context = LocalContext.current
    SHeading("Music videos for you")
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(videos, key = { it.videoId }) { video ->
            Column(Modifier.width(272.dp).clip(RoundedCornerShape(8.dp)).background(Card).clickable {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=${video.videoId}")))
            }) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                    SArt("https://i.ytimg.com/vi/${video.videoId}/hqdefault.jpg", Modifier.fillMaxSize())
                    Box(Modifier.align(Alignment.BottomEnd).padding(10.dp).size(40.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Filled.PlayArrow, null, tint = Color.Black) }
                }
                Text(video.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp))
                Text("Watch on YouTube · ${video.channelName}", color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(12.dp))
            }
        }
    }
}
