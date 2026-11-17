package com.gamut

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.gamut.podcasts.PodcastFeedParser
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationSmokeTest {
    @Test fun podcastFeedParsesOnAndroidRuntime() {
        val feed = """<rss xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd"><channel><item>
          <guid>one</guid><title>Episode</title><itunes:duration>1:30</itunes:duration>
          <enclosure url="https://example.org/audio.mp3" type="audio/mpeg"/>
        </item></channel></rss>"""
        val episode = PodcastFeedParser.parse(feed.toByteArray(), "https://example.org/feed.xml").single()
        assertEquals(90_000L, episode.durationMs)
        assertEquals("Episode", episode.title)
    }

    @Test fun homeSearchLibraryAndPodcastsAreReachable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("gamut_onboarding", Context.MODE_PRIVATE).edit().putBoolean("local_mode", true).commit()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertTrue("Home did not become available", device.wait(Until.hasObject(By.text("Home")), 30_000))
            device.waitForIdle()
            android.os.SystemClock.sleep(1000)
            device.takeScreenshot(File(context.cacheDir, "ui-home.png"))
            device.findObject(By.text("Podcasts")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Your shows")), 10_000))
            assertNotNull(device.findObject(By.text("Search podcasts")))
            device.takeScreenshot(File(context.cacheDir, "ui-podcasts.png"))
            device.findObjects(By.text("Search")).maxBy { node -> node.visibleBounds.bottom }.click()
            assertTrue(device.wait(Until.hasObject(By.text("Artists, songs, albums")), 10_000))
            scenario.onActivity { activity -> activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java).hideSoftInputFromWindow(activity.window.decorView.windowToken, 0) }
            device.waitForIdle()
            device.takeScreenshot(File(context.cacheDir, "ui-search.png"))
            device.findObject(By.text("Your Library")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Your podcasts")), 10_000))
            device.findObject(By.text("Your podcasts")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Your shows")), 10_000))
        }
    }
}
