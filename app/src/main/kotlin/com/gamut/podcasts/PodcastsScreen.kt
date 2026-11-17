package com.gamut.podcasts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gamut.core.model.Track

@Composable
fun PodcastsScreen(viewModel: PodcastsViewModel, barHeight: Dp, active: Boolean = true, onPlay: (Track, List<Track>) -> Unit) {
    val state by viewModel.state.collectAsState()
    val follows by viewModel.store.shows.collectAsState()
    val show = state.selected
    BackHandler(active && show != null) { viewModel.back() }
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = barHeight + 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (show != null) IconButton(onClick = viewModel::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to podcasts") }
                Text(if (show == null) "Podcasts" else "Episodes", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                if (show != null) TextButton(onClick = { viewModel.open(show, refresh = true) }) { Text("Refresh") }
            }
        }
        if (show == null) {
            item { OutlinedTextField(value = state.query, onValueChange = viewModel::search, singleLine = true, label = { Text("Search podcasts") }, placeholder = { Text("Shows, creators, topics") }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) }
            if (state.query.isBlank()) {
                item { Text("Your shows", style = MaterialTheme.typography.titleLarge) }
                if (follows.isEmpty()) item { Text("Find a show and follow it to keep it here.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(follows, key = { it.feedUrl }) { PodcastShowRow(it) { viewModel.open(it) } }
                item { Text("Find your next listen", style = MaterialTheme.typography.titleLarge) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Technology", "Comedy", "Science").forEach { topic -> AssistChip(onClick = { viewModel.search(topic) }, label = { Text(topic) }) }
                    }
                }
            } else {
                items(state.shows, key = { it.feedUrl }) { PodcastShowRow(it) { viewModel.open(it) } }
                if (!state.loading && state.error == null && state.shows.isEmpty() && state.query.trim().length >= 2) item { Text("No shows found. Try another name or topic.") }
                if (state.shows.isNotEmpty()) item { Text("Podcast directory: Apple Podcasts", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AsyncImage(show.artwork, contentDescription = "${show.title} cover", contentScale = ContentScale.Crop, modifier = Modifier.size(180.dp).clip(RoundedCornerShape(8.dp)))
                    Text(show.title, style = MaterialTheme.typography.headlineLarge)
                    Text(show.author, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilledTonalButton(onClick = { viewModel.store.toggle(show) }) { Text(if (follows.any { it.feedUrl == show.feedUrl }) "Following" else "Follow") }
                    Text("All episodes", style = MaterialTheme.typography.titleLarge)
                }
            }
            items(state.episodes, key = { it.id }) { episode ->
                val track = remember(show, episode) { episode.track(show) }
                val saved = viewModel.store.position(track.isrc)
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(episode.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (episode.published.isNotBlank()) Text(episode.published.take(25), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (episode.description.isNotBlank()) Text(episode.description, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (episode.durationMs > 0) "${episode.durationMs / 60_000} min" else "Audio episode", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        Button(onClick = { onPlay(track, state.episodes.map { it.track(show) }) }) {
                            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp))
                            Text(if (saved > 0) "Resume · ${saved / 60_000} min" else "Play")
                        }
                    }
                }
            }
            if (!state.loading && state.error == null && state.episodes.isEmpty()) item { Text("No supported HTTPS audio episodes in this feed.") }
        }
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
    }
}

@Composable
private fun PodcastShowRow(show: PodcastShow, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        AsyncImage(show.artwork, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(72.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(show.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Podcast · ${show.author}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
