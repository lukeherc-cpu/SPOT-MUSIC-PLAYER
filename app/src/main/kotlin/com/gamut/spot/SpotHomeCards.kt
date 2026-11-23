package com.gamut.spot

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
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

// Music videos play muted inside the card, in the app, instead of opening another site.
// The card most in view starts on its own, muted. A tap stops it, and a tap on another card plays that one.
// Scrolling to another card hands autoplay to it, even after one was stopped or picked by hand.
@Composable
internal fun MusicVideoRail(videos: List<YoutubeCandidate>, title: String = "Music videos for you", onUnmute: () -> Unit = {}) {
    if (videos.isEmpty()) return
    var unmuted by remember { mutableStateOf<String?>(null) }
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    var stopped by remember { mutableStateOf(emptySet<String>()) }
    val rail = androidx.compose.foundation.lazy.rememberLazyListState()
    val inView by remember { derivedStateOf {
        rail.layoutInfo.visibleItemsInfo.firstOrNull { it.offset >= -it.size / 2 }?.index ?: rail.firstVisibleItemIndex
    } }
    // Videos YouTube won't play inside another app (or that never start) are skipped, not shown as playing.
    var failed by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(rail) {
        snapshotFlow { inView }.collect { index ->
            if (chosen != null && videos.getOrNull(index)?.videoId != chosen) { chosen = null; unmuted = null }
        }
    }
    val playing = chosen ?: videos.getOrNull(inView)?.videoId?.takeIf { it !in stopped && it !in failed }
    SHeading(title)
    LazyRow(state = rail, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(videos, key = { it.videoId }) { video ->
            val on = playing == video.videoId
            Column(Modifier.width(272.dp).clip(RoundedCornerShape(8.dp)).background(Card).clickable(onClickLabel = if (on) "Stop video" else "Play video muted") {
                if (on) { stopped = stopped + video.videoId; chosen = null } else { stopped = stopped - video.videoId; failed = failed - video.videoId; chosen = video.videoId }
            }) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                    // The thumbnail stays underneath, so the card never shows an empty black box while the video loads.
                    SArt("https://i.ytimg.com/vi/${video.videoId}/hqdefault.jpg", Modifier.fillMaxSize())
                    if (on) MutedVideo(video.videoId, Modifier.fillMaxSize(), muted = unmuted != video.videoId,
                        onToggleMute = { if (unmuted == video.videoId) unmuted = null else { unmuted = video.videoId; chosen = video.videoId; onUnmute() } },
                        onTap = { stopped = stopped + video.videoId; chosen = null; unmuted = null },
                        onFailed = { failed = failed + video.videoId; if (chosen == video.videoId) chosen = null })
                    else {
                        Box(Modifier.align(Alignment.BottomEnd).padding(10.dp).size(40.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Filled.PlayArrow, null, tint = Color.Black) }
                    }
                }
                Text(video.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp))
                Text((if (on && unmuted == video.videoId) "Playing · tap to stop · " else if (on) "Playing muted · tap to stop · " else if (video.videoId in failed) "Can't play here · tap to try again · " else "Plays muted in Cadence · ") + video.channelName, color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(12.dp))
            }
        }
    }
}

/** The small blue dot on a show that has an episode the listener hasn't seen yet. */
@Composable
internal fun NewDot(modifier: Modifier = Modifier) {
    Box(modifier.size(10.dp).background(NewBlue, CircleShape).semantics { contentDescription = "New episode" })
}
internal val NewBlue = Color(0xFF3D91F4)

@Composable
internal fun NewEpisodeRail(episodes: List<HomeEpisode>, fresh: Set<String>, vm: SpotViewModel) {
    if (episodes.isEmpty()) return
    SHeading("New episodes")
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(episodes, key = { "new-" + it.show.feedUrl }) { item ->
            val song = item.episode.track(item.show)
            Column(Modifier.width(152.dp).clickable { vm.play(song, listOf(song)) }) {
                SArt(spotifyThumb(song.albumArtUrl), Modifier.size(152.dp))
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (item.show.feedUrl in fresh) NewDot(Modifier.padding(end = 6.dp))
                    Text(song.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(item.show.title, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp).clickable { vm.openShow(item.show) })
            }
        }
    }
}

/** Tall colored cards that open a genre or mood page. */
@Composable
internal fun CategoryRail(title: String, categories: List<BrowseCategory>, artwork: List<String>, open: (BrowseCategory) -> Unit) {
    if (categories.isEmpty()) return
    SHeading(title)
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(categories, key = { it.name }) { category ->
            val index = categories.indexOf(category)
            Box(Modifier.width(140.dp).height(190.dp).clip(RoundedCornerShape(8.dp)).background(Brush.verticalGradient(listOf(Color(category.color), Color(category.color).copy(alpha = .55f)))).clickable { open(category) }) {
                artwork.getOrNull(index % artwork.size.coerceAtLeast(1))?.let { SArt(spotifyThumb(it), Modifier.size(104.dp).align(Alignment.Center).graphicsLayer { rotationZ = if (index % 2 == 0) -8f else 8f }) }
                Text("#" + category.title.lowercase(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomStart).padding(12.dp))
            }
        }
    }
}

@android.annotation.SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
private fun MutedVideo(videoId: String, modifier: Modifier, muted: Boolean, onToggleMute: () -> Unit, onTap: () -> Unit, onFailed: () -> Unit) {
    val id = videoId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(11)
    val origin = "https://www.youtube-nocookie.com"
    // The video stays hidden behind its thumbnail until it is actually playing, so YouTube's poster,
    // red play button and loading spinner never show. Controls, captions and annotations are off, and
    // the player is taller than the card: the video fills the card exactly while YouTube's title bar
    // and logo sit in the cropped-off strips above and below it.
    var playing by remember(id) { mutableStateOf(false) }
    var web by remember(id) { mutableStateOf<android.webkit.WebView?>(null) }
    val fail by rememberUpdatedState(onFailed)
    // A video that hasn't started after a while is treated as unplayable, so the card doesn't claim it's playing.
    LaunchedEffect(id, playing) { if (!playing) { kotlinx.coroutines.delay(12_000); fail() } }
    // The video starts muted; the speaker button turns its sound on and off.
    LaunchedEffect(web, muted, playing) {
        web?.evaluateJavascript(if (muted) "window.pl&&pl.mute()" else "window.pl&&(pl.unMute(),pl.setVolume(100))", null)
    }
    val shown by animateFloatAsState(if (playing) 1f else 0f, label = "video")
    val page = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><style>
html,body{margin:0;width:100%;height:100%;background:#000;overflow:hidden;position:relative}
#p,iframe{position:absolute;left:0;top:-90px;width:100%;height:calc(100% + 180px);border:0;pointer-events:none}
</style></head><body><div id="p"></div><script>
function onYouTubeIframeAPIReady(){window.pl=new YT.Player('p',{host:'$origin',videoId:'$id',playerVars:{autoplay:1,mute:1,playsinline:1,controls:0,disablekb:1,fs:0,iv_load_policy:3,cc_load_policy:0,rel:0,modestbranding:1,loop:1,playlist:'$id'},
events:{onReady:function(e){e.target.mute();e.target.playVideo()},onError:function(){Cadence.failed()},onStateChange:function(e){if(e.data==1)Cadence.started();else if(e.data==0||e.data==2)e.target.playVideo()}}})}
</script><script src="https://www.youtube.com/iframe_api"></script></body></html>"""
    Box(modifier) {
        androidx.compose.ui.viewinterop.AndroidView(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = shown }, factory = { context ->
            android.webkit.WebView(context).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(android.graphics.Color.BLACK)
                isVerticalScrollBarEnabled = false; isHorizontalScrollBarEnabled = false
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                addJavascriptInterface(object {
                    @android.webkit.JavascriptInterface fun started() { post { playing = true } }
                    @android.webkit.JavascriptInterface fun failed() { post { fail() } }
                }, "Cadence")
                // Stay in the app: links out of the player are ignored.
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: android.webkit.WebView, request: android.webkit.WebResourceRequest) = request.isForMainFrame
                }
                loadDataWithBaseURL("$origin/", page, "text/html", "utf-8", null)
                web = this
            }
        }, onRelease = { it.stopLoading(); it.loadUrl("about:blank"); it.destroy() })
        // Taps land on the card, never on YouTube's player, so its menus and overlays never open.
        Box(Modifier.matchParentSize().clickable(onClickLabel = "Stop video", onClick = onTap))
        Box(Modifier.align(Alignment.BottomEnd).padding(10.dp).size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = .6f)).clickable(onClickLabel = if (muted) "Turn sound on" else "Mute", onClick = onToggleMute), contentAlignment = Alignment.Center) {
            Icon(if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, if (muted) "Sound off" else "Sound on", tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

