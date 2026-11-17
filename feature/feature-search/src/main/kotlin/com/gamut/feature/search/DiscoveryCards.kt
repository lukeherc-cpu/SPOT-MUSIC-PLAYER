package com.gamut.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

data class PodcastSearchItem(val id: String, val title: String, val author: String, val artwork: String?)
data class HomeShortcut(val title: String, val artwork: String? = null, val liked: Boolean = false, val podcast: Boolean = false, val open: () -> Unit)

@Composable
internal fun HomeShortcutGrid(items: List<HomeShortcut>) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.take(8).chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { shortcut ->
                    Row(Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.onBackground.copy(alpha = .09f)).clickable(onClick = shortcut.open), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(64.dp).then(if (shortcut.liked) Modifier.background(Brush.linearGradient(listOf(Color(0xFF4C18D6), Color(0xFFBFE6D7)))) else Modifier.background(Color(0xFF292929))), contentAlignment = Alignment.Center) {
                            if (shortcut.artwork != null) AsyncImage(shortcut.artwork, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            else Icon(if (shortcut.liked) Icons.Rounded.Favorite else Icons.Rounded.Podcasts, null, tint = Color.White)
                        }
                        Text(shortcut.title, Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun BrowseCategories(onMusic: () -> Unit, onPodcasts: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Browse all", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(Triple("Music", Color(0xFFE0148C), onMusic), Triple("Podcasts", Color(0xFF006450), onPodcasts)).forEach { (title, color, action) ->
                Box(Modifier.weight(1f).height(106.dp).clip(RoundedCornerShape(6.dp)).background(color).clickable(onClick = action).padding(14.dp)) {
                    Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Icon(if (title == "Music") Icons.Rounded.MusicNote else Icons.Rounded.Podcasts, null, tint = Color.White.copy(alpha = .8f), modifier = Modifier.size(44.dp).align(Alignment.BottomEnd))
                }
            }
        }
    }
}

@Composable
internal fun PodcastSearchRow(item: PodcastSearchItem, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(item.artwork, null, Modifier.size(64.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF282828)), contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Podcast · ${item.author}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
