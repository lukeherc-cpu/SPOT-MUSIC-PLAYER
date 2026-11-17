package com.gamut

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import com.gamut.spot.SpotMusicApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

/** The app has one screen system; the former Gamut navigation tree is removed. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val spotifyLink = MutableStateFlow<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.BLACK
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        readSpotifyLink(intent)
        setContent { SpotMusicApp(spotifyLink, consumeLink = { spotifyLink.value = null }) }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); readSpotifyLink(intent) }
    private fun readSpotifyLink(intent: Intent?) {
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT) ?: intent?.dataString ?: return
        Regex("https://open\\.spotify\\.com/(?:intl-[a-z]+/)?(?:playlist|album|artist)/[A-Za-z0-9]{22}(?:\\?[^\\s]*)?").find(text)?.value?.let { spotifyLink.value = it }
    }
}
