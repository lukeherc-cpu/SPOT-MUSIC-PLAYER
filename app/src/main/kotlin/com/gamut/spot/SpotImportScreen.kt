package com.gamut.spot

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gamut.core.network.SpotifyCard

/** Import: sign in to Spotify and pick playlists and mixes, or paste a public link. */
@Composable
internal fun SpotImportScreen(vm: SpotViewModel, link: String, onLink: (String) -> Unit, importing: Boolean, onBack: () -> Unit, onImportLink: () -> Unit) {
    val account by vm.account.collectAsState()
    var signingIn by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(account.signedIn) { if (account.signedIn && account.playlists.isEmpty() && account.mixes.isEmpty()) vm.loadSpotifyLibrary() }
    val all = remember(account.playlists, account.mixes) { (account.mixes + account.playlists).distinctBy { it.uri } }
    val busy = account.progress != null
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { SIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack); Text("Import from Spotify", style = MaterialTheme.typography.titleLarge) } }
        item { Text("Bring your music with you", style = MaterialTheme.typography.headlineLarge) }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Card).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!account.signedIn) {
                    Text("Log in with Spotify", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Choose some or all of your playlists, and bring over your mixes like Daily Mix and Discover Weekly. Your sign-in stays encrypted on this phone.", color = Muted, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { signingIn = true }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.Black)) { Text("Log in with Spotify", fontWeight = FontWeight.Bold) }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(account.name?.let { "Signed in as $it" } ?: "Signed in to Spotify", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("${account.mixes.size} mixes · ${account.playlists.size} playlists", color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { CookieManager.getInstance().removeAllCookies(null); chosen = emptySet(); vm.signOutSpotify() }, enabled = !busy) { Text("Sign out", color = Color.White) }
                    }
                    if (all.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { chosen = if (chosen.size == all.size) emptySet() else all.mapTo(hashSetOf()) { it.uri } }, enabled = !busy) { Text(if (chosen.size == all.size) "Clear all" else "Select all", color = Green) }
                        Spacer(Modifier.weight(1f))
                        Button(onClick = { vm.importSpotifyCards(all.filter { it.uri in chosen }); chosen = emptySet() }, enabled = chosen.isNotEmpty() && !busy, shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.Black)) {
                            Text(if (chosen.isEmpty()) "Import" else "Import ${chosen.size}", fontWeight = FontWeight.Bold)
                        }
                    }
                    account.progress?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White); LinearProgressIndicator(Modifier.fillMaxWidth(), color = Green) }
                    account.error?.let { error -> Text(error, color = Muted, style = MaterialTheme.typography.bodySmall); TextButton(onClick = { if ("expired" in error) signingIn = true else vm.loadSpotifyLibrary() }) { Text(if ("expired" in error) "Sign in again" else "Retry", color = Green) } }
                }
            }
        }
        if (account.signedIn) {
            if (account.loading && all.isEmpty()) item { DiscoverySkeleton() }
            if (account.mixes.isNotEmpty()) {
                item { Text("Your mixes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                items(account.mixes, key = { "mix-" + it.uri }) { card -> PickRow(card, card.uri in chosen, !busy) { chosen = if (it) chosen + card.uri else chosen - card.uri } }
            } else if (!account.loading && account.error == null) item { Text("No mixes found yet. Spotify makes them after some listening.", color = Muted, style = MaterialTheme.typography.bodySmall) }
            if (account.playlists.isNotEmpty()) {
                item { Text("Your playlists", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                items(account.playlists.filter { p -> account.mixes.none { it.uri == p.uri } }, key = { "playlist-" + it.uri }) { card -> PickRow(card, card.uri in chosen, !busy) { chosen = if (it) chosen + card.uri else chosen - card.uri } }
            }
        }
        item { Text("Or paste a link", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
        item { Text("Paste a public Spotify playlist or album link. We'll keep its songs, order and artwork in your library.", color = Muted) }
        item { OutlinedTextField(link, onLink, label = { Text("Spotify link") }, placeholder = { Text("https://open.spotify.com/playlist/…") }, modifier = Modifier.fillMaxWidth(), maxLines = 3, enabled = !importing) }
        item { Button(onClick = onImportLink, enabled = link.isNotBlank() && !importing, modifier = Modifier.fillMaxWidth().height(50.dp), shape = CircleShape) { Text(if (importing) "Importing…" else "Import songs", fontWeight = FontWeight.Bold) } }
        if (importing) item { WordsSkeleton() }
        item { Text("Cadence only reads your Spotify library. It never changes it.", style = MaterialTheme.typography.bodySmall, color = Muted) }
    }
    if (signingIn) SpotifySignIn(onDismiss = { signingIn = false }) { cookie -> signingIn = false; vm.signInSpotify(cookie) }
}

@Composable
private fun PickRow(card: SpotifyCard, picked: Boolean, enabled: Boolean, onPick: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { onPick(!picked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        SArt(spotifyThumb(card.artwork), Modifier.size(56.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(card.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(card.subtitle, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Checkbox(picked, onPick, enabled = enabled, colors = CheckboxDefaults.colors(checkedColor = Green, checkmarkColor = Color.Black))
    }
}

/** Spotify's own sign-in page. Once it sets the web session cookie, that cookie is handed back and the page closes. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SpotifySignIn(onDismiss: () -> Unit, onSignedIn: (String) -> Unit) {
    val signedIn by rememberUpdatedState(onSignedIn)
    var progress by remember { mutableStateOf(0) }
    var failure by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Black).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                SIcon(Icons.Filled.Close, "Cancel sign-in", onClick = onDismiss)
                Text("Log in with Spotify", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
            }
            if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth(), color = Green)
            failure?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp)) }
            AndroidView(modifier = Modifier.fillMaxWidth().weight(1f), factory = { context ->
                val cookies = CookieManager.getInstance()
                cookies.setAcceptCookie(true)
                var handled = false
                fun check() {
                    if (handled) return
                    val value = cookies.getCookie("https://open.spotify.com")?.split(';')?.map { it.trim() }
                        ?.firstOrNull { it.startsWith("sp_dc=") }?.removePrefix("sp_dc=")?.takeIf { it.isNotBlank() } ?: return
                    handled = true
                    // Only the session value is kept (encrypted). The page's own cookies are cleared.
                    cookies.removeAllCookies(null)
                    signedIn(value)
                }
                WebView(context).apply {
                    // Without explicit size the WebView lays out at zero height inside a dialog and stays blank.
                    layoutParams = android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(android.graphics.Color.WHITE)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    // Spotify's sign-in page treats the embedded-browser marker as unsupported, so present as mobile Chrome.
                    settings.userAgentString = settings.userAgentString.replace("; wv", "").replace(Regex("Version/\\S+ "), "")
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress }
                    }
                    cookies.setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) { check() }
                        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) { check() }
                        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) { failure = null }
                        override fun onReceivedError(view: WebView, request: android.webkit.WebResourceRequest, error: android.webkit.WebResourceError) {
                            if (request.isForMainFrame) failure = "Spotify's sign-in page didn't load (${error.description}). Check your connection and try again."
                        }
                    }
                    loadUrl("https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F")
                }
            }, onRelease = { it.stopLoading(); it.destroy() })
        }
    }
}
