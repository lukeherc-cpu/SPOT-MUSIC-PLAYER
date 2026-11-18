package com.gamut.spot

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.SubcomposeAsyncImage
import com.gamut.core.model.Track
import com.gamut.core.network.SpotifyCard

internal val Green = Color(0xFF1ED760)
internal val Black = Color(0xFF121212)
internal val Card = Color(0xFF282828)
internal val Muted = Color(0xFFB3B3B3)
internal val LikedBrush = Brush.linearGradient(listOf(Color(0xFF4C18D6), Color(0xFFBFE6D7)))
internal val LocalArtistTap = staticCompositionLocalOf<(Track) -> Unit> { {} }
internal val LocalSaveTrack = staticCompositionLocalOf<(Track) -> Unit> { {} }
internal val LocalQueueTrack = staticCompositionLocalOf<(Track) -> Unit> { {} }

@Composable
internal fun SpotTheme(content: @Composable () -> Unit) {
    val fonts = FontFamily(Font(com.gamut.core.designsystem.R.font.inter_regular), Font(com.gamut.core.designsystem.R.font.inter_medium, FontWeight.Medium), Font(com.gamut.core.designsystem.R.font.inter_semibold, FontWeight.Bold))
    val base = Typography()
    val typography = base.copy(
        headlineLarge = base.headlineLarge.copy(fontFamily = fonts, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontFamily = fonts, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontFamily = fonts, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontFamily = fonts, fontSize = 16.sp, fontWeight = FontWeight.Bold),
        bodyLarge = base.bodyLarge.copy(fontFamily = fonts, fontSize = 16.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = fonts, fontSize = 14.sp),
        bodySmall = base.bodySmall.copy(fontFamily = fonts, fontSize = 12.sp),
        labelLarge = base.labelLarge.copy(fontFamily = fonts, fontSize = 14.sp, fontWeight = FontWeight.Medium),
        labelSmall = base.labelSmall.copy(fontFamily = fonts, fontSize = 11.sp),
    )
    MaterialTheme(colorScheme = darkColorScheme(primary = Green, onPrimary = Color.Black, background = Black, onBackground = Color.White, surface = Black, onSurface = Color.White, surfaceVariant = Card, onSurfaceVariant = Muted, secondaryContainer = Card, onSecondaryContainer = Color.White, surfaceContainer = Card, outline = Color(0xFF727272), error = Color(0xFFFF8A9A)), typography = typography) {
        CompositionLocalProvider(LocalContentColor provides Color.White, content = content)
    }
}

@Composable
internal fun SIcon(icon: ImageVector, description: String?, tint: Color = Color.White, size: Dp = 24.dp, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) { Icon(icon, description, tint = tint, modifier = Modifier.size(size)) }
}

@Composable
internal fun SAvatar(onClick: () -> Unit) {
    Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0xFFD694B1)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.Person, "Profile and settings", tint = Color(0xFF25151E), modifier = Modifier.size(24.dp))
    }
}

@Composable
internal fun SChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(text, color = if (selected) Color.Black else Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1,
        modifier = Modifier.clip(CircleShape).background(if (selected) Green else Card).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp))
}

@Composable
internal fun SArt(url: String?, modifier: Modifier = Modifier, liked: Boolean = false, circle: Boolean = false) {
    Box(modifier.clip(if (circle) CircleShape else RoundedCornerShape(3.dp)).then(if (liked) Modifier.background(LikedBrush) else Modifier.background(Card)), contentAlignment = Alignment.Center) {
        if (url != null && !liked) SubcomposeAsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.High,
            loading = { SkeletonPulse(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = .08f))) } },
            error = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Filled.MusicNote, null, tint = Muted, modifier = Modifier.fillMaxSize(.38f)) } })
        else Icon(if (liked) Icons.Filled.Favorite else Icons.Filled.MusicNote, null, tint = Color.White, modifier = Modifier.fillMaxSize(.38f))
    }
}

@Composable
internal fun SHeading(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 14.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

@Composable
internal fun SCardRail(title: String, cards: List<SpotifyCard>, open: (SpotifyCard) -> Unit) {
    if (cards.isEmpty()) return
    SHeading(title)
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(cards, key = { it.uri }) { item ->
            Column(Modifier.width(152.dp).clickable { open(item) }) {
                SArt(item.artwork, Modifier.size(152.dp), circle = item.type == "artist")
                Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun STrack(track: Track, playing: Boolean = false, saved: Boolean = false, number: Int? = null, artwork: Boolean = true, onPlay: () -> Unit, onMore: () -> Unit) {
    val artistTap = LocalArtistTap.current
    val save = LocalSaveTrack.current
    val enqueue by rememberUpdatedState(LocalQueueTrack.current)
    val haptics = LocalHapticFeedback.current
    val swipe = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
        if (value == SwipeToDismissBoxValue.EndToStart) { enqueue(track); haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
        false
    })
    SwipeToDismissBox(state = swipe, enableDismissFromStartToEnd = false, backgroundContent = {
        Box(Modifier.fillMaxSize().background(Green).padding(end = 24.dp), contentAlignment = Alignment.CenterEnd) { Icon(Icons.Outlined.PlaylistAdd, "Add to queue", tint = Color.Black) }
    }) {
    Row(Modifier.fillMaxWidth().background(Black).combinedClickable(onClick = onPlay, onLongClickLabel = "Song options", onLongClick = onMore)
        .semantics { customActions = listOf(CustomAccessibilityAction("Add to queue") { enqueue(track); true }) }
        .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (number != null) Text(number.toString(), Modifier.width(28.dp), color = if (playing) Green else Muted, style = MaterialTheme.typography.bodyMedium)
        if (artwork) SArt(track.albumArtUrl, Modifier.size(52.dp))
        Column(Modifier.weight(1f).padding(start = if (artwork) 12.dp else 0.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(track.title, style = MaterialTheme.typography.bodyLarge, color = if (playing) Green else Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (track.isExplicit == true) ExplicitBadge()
                Text(track.artist, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { artistTap(track) })
            }
        }
        SIcon(if (saved) Icons.Filled.CheckCircle else Icons.Outlined.AddCircleOutline, if (saved) "Saved in playlists" else "Save song", tint = if (saved) Green else Muted, size = 22.dp, onClick = { save(track) })
        SIcon(Icons.Outlined.MoreVert, "Song options", tint = Muted, onClick = onMore)
    }
    }
}

@Composable
internal fun ExplicitBadge() {
    Text("E", color = Black, fontSize = 9.sp, fontWeight = FontWeight.Bold, lineHeight = 12.sp,
        modifier = Modifier.clip(RoundedCornerShape(2.dp)).background(Muted).padding(horizontal = 3.dp))
}

@Composable
internal fun SLibraryRow(title: String, subtitle: String, image: String?, liked: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        SArt(image, Modifier.size(64.dp), liked = liked)
        Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

internal fun timeLabel(ms: Long): String = "${ms.coerceAtLeast(0) / 60000}:${(ms.coerceAtLeast(0) / 1000 % 60).toString().padStart(2, '0')}"
