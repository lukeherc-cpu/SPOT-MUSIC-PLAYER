package com.gamut.spot

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gamut.core.model.Track

@Composable
internal fun SpotBrowseScreen(state: SpotBrowse, home: SpotHome, vm: SpotViewModel, favorites: Set<String>, playing: String?, onMore: (Track) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(state.category.color), Black))).statusBarsPadding().padding(bottom = 20.dp)) {
                SIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = vm::backDetail)
                Text(state.category.title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp))
                Text(when (state.category) {
                    BrowseCategory.Charts -> "Top charts in the US and around the world. Updated by Spotify."
                    BrowseCategory.Music -> "Discover new music, explore your favorites and find your next obsession."
                    BrowseCategory.NewReleases -> "Fresh albums, singles and New Music Friday."
                    BrowseCategory.Podcasts -> "Find your next favorite show."
                    else -> "Explore ${state.category.title.lowercase()} playlists, albums and songs."
                }, color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
        if (state.category == BrowseCategory.Music) item { DailyMixRail(home.mixes, home.loading, vm::openMix) }
        if (state.loading) item { DiscoverySkeleton() }
        state.error?.let { error -> item { ErrorRow(error) { vm.openBrowse(state.category) } } }
        state.content.rails.forEach { rail -> item { SCardRail(rail.title, rail.cards, vm::openCard) } }
        if (state.content.songs.isNotEmpty()) item { SHeading(if (state.category == BrowseCategory.Charts) "Leading the US chart" else "Songs to discover") }
        itemsIndexed(state.content.songs, key = { _, song -> song.isrc }) { index, song -> STrack(song, playing == song.isrc, song.isrc in favorites, number = if (state.category == BrowseCategory.Charts) index + 1 else null, onPlay = { vm.play(song, state.content.songs) }, onMore = { onMore(song) }) }
        items(state.shows, key = { it.feedUrl }) { show -> SLibraryRow(show.title, "Podcast · ${show.author}", show.artwork) { vm.openShow(show) } }
    }
}

@Composable
internal fun DailyMixRail(mixes: List<DailyMix>, loading: Boolean, open: (DailyMix) -> Unit) {
    SHeading("Daily Mixes")
    if (loading) RailSkeleton()
    else if (mixes.isEmpty()) {
        Text("Listen to music to shape your mixes.", color = Muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    } else LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        itemsIndexed(mixes, key = { _, mix -> mix.id }) { index, mix ->
            Column(Modifier.width(152.dp).clickable { open(mix) }) {
                Box(Modifier.size(152.dp).clip(RoundedCornerShape(4.dp))) {
                    SArt(mix.artwork, Modifier.fillMaxSize())
                    Text(mix.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Color.Black,
                        modifier = Modifier.fillMaxWidth().background(listOf(Green, Color(0xFFB49ADC), Color(0xFFF4BB6C))[index % 3]).padding(8.dp).align(androidx.compose.ui.Alignment.BottomCenter))
                }
                Text(mix.subtitle, color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}
