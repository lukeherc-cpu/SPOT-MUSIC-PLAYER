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
import androidx.compose.ui.graphics.graphicsLayer
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

// The accent is purple by default. Listeners can pick any hue in Settings; it is kept on this device.
internal object SpotAccent {
    const val DEFAULT_HUE = 268f
    var hue by mutableFloatStateOf(DEFAULT_HUE)
    private fun store(context: android.content.Context) = context.getSharedPreferences("cadence_ui", android.content.Context.MODE_PRIVATE)
    fun load(context: android.content.Context) { hue = store(context).getFloat("accent_hue", DEFAULT_HUE) }
    fun save(context: android.content.Context, value: Float) { hue = value; store(context).edit().putFloat("accent_hue", value).apply() }
}
internal val Green: Color get() = Color.hsl(SpotAccent.hue, .70f, .72f)
internal val Black = Color(0xFF121212)
internal val Card = Color(0xFF282828)
internal val Muted = Color(0xFFB3B3B3)
internal val LikedBrush = Brush.linearGradient(listOf(Color(0xFF4C18D6), Color(0xFFBFE6D7)))
internal val LocalArtistTap = staticCompositionLocalOf<(Track) -> Unit> { {} }
internal val LocalSaveTrack = staticCompositionLocalOf<(Track) -> Unit> { {} }
internal val LocalQueueTrack = staticCompositionLocalOf<(Track) -> Unit> { {} }

@Composable
internal fun SpotTheme(content: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    remember(context) { SpotAccent.load(context) }
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

// Save button with a quick bounce whenever the saved state changes, wherever the song is saved from.
@Composable
internal fun SaveToggle(saved: Boolean, description: String, idleTint: Color = Color.White, size: Dp = 24.dp, onClick: () -> Unit) {
    val scale = remember { androidx.compose.animation.core.Animatable(1f) }
    var seen by remember { mutableStateOf(saved) }
    LaunchedEffect(saved) {
        if (seen == saved) return@LaunchedEffect
        seen = saved
        scale.snapTo(.6f)
        scale.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = .32f, stiffness = 520f))
    }
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        Icon(if (saved) Icons.Filled.CheckCircle else Icons.Outlined.AddCircleOutline, description, tint = if (saved) Green else idleTint,
            modifier = Modifier.size(size).graphicsLayer { scaleX = scale.value; scaleY = scale.value })
    }
}

// The listener's profile photo, set in Settings and kept on this device.
internal object SpotProfile { var photo by mutableStateOf<Any?>(null) }

@Composable
internal fun SAvatar(onClick: (() -> Unit)?, size: Dp = 34.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(Green).then(if (onClick != null) Modifier.clickable(onClick = onClick).semantics { contentDescription = "Profile and settings" } else Modifier), contentAlignment = Alignment.Center) {
        val photo = SpotProfile.photo
        if (photo != null) coil.compose.AsyncImage(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Filled.Person, null, tint = Color(0xFF1B1226), modifier = Modifier.size(size * .7f))
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
        if (value == SwipeToDismissBoxValue.StartToEnd) { enqueue(track); haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
        false
    })
    SwipeToDismissBox(state = swipe, enableDismissFromEndToStart = false, backgroundContent = {
        Box(Modifier.fillMaxSize().background(Green).padding(start = 24.dp), contentAlignment = Alignment.CenterStart) { Icon(Icons.Outlined.PlaylistAdd, "Add to queue", tint = Color.Black) }
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
        SaveToggle(saved, if (saved) "Saved in playlists" else "Save song", idleTint = Muted, size = 22.dp, onClick = { save(track) })
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

/** Spotify's 300px cover instead of the 640px one: tiles and rails load faster and look the same. */
internal fun spotifyThumb(url: String?): String? = url?.replace("ab67616d0000b273", "ab67616d00001e02")

internal fun timeLabel(ms: Long): String = "${ms.coerceAtLeast(0) / 60000}:${(ms.coerceAtLeast(0) / 1000 % 60).toString().padStart(2, '0')}"
