package com.gamut

import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.gamut.core.model.Track
import com.gamut.core.network.*
import com.gamut.spot.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArtistNavigationTest {
    @Test fun rightThumbRailScrollsToArtistSectionsOnOnePage() {
        val art = "https://image-cdn-fa.spotifycdn.com/image/ab67616d00001e029b9b36b0e22870b9f542d937"
        val uri = "spotify:artist:4tZwfgrHOc3mvqYlEYSvVi"
        val album = SpotifyCard("spotify:album:4m2880jivSbbyEGAKfITCa", "Reference release", "Album", art, "album")
        val tracks = (1..8).map { Track("artist-ui-$it", title = "Popular song $it", artist = "Reference Artist", durationMs = 60000, albumArtUrl = art) }
        val collection = SpotifyCollection(uri, "Reference Artist", "Artist", art, "", tracks, tracks.size, albums = listOf(album), artistInfo = SpotifyArtistInfo(monthlyListeners = 1_200_000, verified = true, biography = "Artist biography from the catalogue.", latest = listOf(album)))
        val detail = SpotDetail(uri, collection.title, "Artist", art, tracks = tracks, spotify = collection)
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent { SpotTheme { SpotArtistScreen(detail, hiltViewModel(), emptySet(), null, {}, {}) } } }
            assertTrue(device.wait(Until.hasObject(By.textContains("monthly listeners")), 10000))
            val overview = device.findObject(By.desc("Overview artist section")).visibleBounds
            val latest = device.findObject(By.desc("Latest artist section")).visibleBounds
            assertTrue(overview.centerX() > device.displayWidth * .8)
            assertTrue("Rail should occupy the middle of the screen", overview.top > device.displayHeight * .15)
            assertTrue("Rail should leave space below", latest.bottom < device.displayHeight * .9)
            // The rail scrolls within one page: the header leaves the screen and the section arrives.
            device.swipe(overview.centerX(), overview.centerY(), latest.centerX(), latest.centerY(), 24)
            assertTrue(device.wait(Until.hasObject(By.text("Latest releases")), 5000))
            assertTrue(device.wait(Until.gone(By.textContains("monthly listeners")), 5000))
            device.findObject(By.desc("Songs artist section")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Popular")), 5000))
            assertTrue(device.wait(Until.hasObject(By.desc("Songs artist section").selected(true)), 5000))
            device.findObject(By.desc("Albums artist section")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Albums and singles")), 5000))
            device.findObject(By.desc("Overview artist section")).click()
            assertTrue(device.wait(Until.hasObject(By.textContains("monthly listeners")), 5000))
            SystemClock.sleep(1200)
            device.executeShellCommand("screencap -p /sdcard/spot-ui-artist.png")
        }
    }
}
