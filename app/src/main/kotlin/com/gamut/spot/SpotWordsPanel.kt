package com.gamut.spot

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.*
import com.gamut.feature.playerui.LyricsState

@Composable
internal fun SpotWordsPanel(title: String, words: LyricsState, position: Long, background: Color, expanded: Boolean, onExpand: () -> Unit, onCover: () -> Unit, onSeek: (Long) -> Unit, modifier: Modifier = Modifier, caption: String? = null) {
    val loaded = words as? LyricsState.Loaded
    val list = rememberLazyListState()
    val active = remember(loaded, position / 100) {
        if (loaded?.isSynced == true) loaded.lines.indexOfLast { it.timestampMs <= position }.coerceAtLeast(0) else -1
    }
    LaunchedEffect(active, expanded) {
        if (active >= 0 && !list.isScrollInProgress) list.animateScrollToItem((active - if (expanded) 2 else 1).coerceAtLeast(0))
    }
    Column(modifier.fillMaxSize().clip(RoundedCornerShape(if (expanded) 0.dp else 7.dp)).background(background)
        .clickable(onClickLabel = "Return to cover art", onClick = onCover)
        .semantics { contentDescription = "$title, tap to return to cover art" }) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            SIcon(Icons.Outlined.Image, "Show cover art", size = 21.dp, onClick = onCover)
            SIcon(if (expanded) Icons.Outlined.CloseFullscreen else Icons.Outlined.OpenInFull, if (expanded) "Minimize text" else "Expand $title", size = 21.dp, onClick = onExpand)
        }
        if (caption != null) Text(caption, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .7f), modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp))
        when (words) {
            is LyricsState.Loaded -> LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = if (expanded) 24.dp else 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(if (expanded) 18.dp else 12.dp)) {
                if (!words.isSynced) item { Text("Transcript · timing unavailable", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .65f)) }
                itemsIndexed(words.lines) { index, line ->
                    Text(line.text.ifBlank { "♪" }, fontSize = if (expanded) 30.sp else 22.sp, lineHeight = if (expanded) 41.sp else 31.sp, fontWeight = FontWeight.Bold,
                        color = if (!words.isSynced || index == active) Color.White else Color.White.copy(alpha = if (index < active) .78f else .4f),
                        modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Return to cover art", onClick = onCover).padding(vertical = 3.dp))
                }
                item { Spacer(Modifier.height(if (expanded) 140.dp else 50.dp)) }
            }
            else -> Box(Modifier.weight(1f).fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (words is LyricsState.Loading) WordsSkeleton()
                    else Text(when (words) { is LyricsState.NotAvailable -> words.reason; is LyricsState.Error -> words.message; else -> "Text unavailable" }, color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
internal fun FullScreenWords(title: String, words: LyricsState, position: Long, color: Color, onClose: () -> Unit, onCover: () -> Unit, onSeek: (Long) -> Unit, caption: String? = null) {
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(color).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}.statusBarsPadding().navigationBarsPadding()) {
        SpotWordsPanel(title, words, position, color, true, onClose, onCover, onSeek, caption = caption)
    }
}
