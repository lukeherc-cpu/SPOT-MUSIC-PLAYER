package com.gamut

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.gamut.core.database.DownloadEntity
import com.gamut.core.database.DownloadIndex
import com.gamut.core.model.Track
import com.gamut.feature.library.LibraryRepository
import com.gamut.podcasts.*
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

    @Test fun replacementScreensLibraryPlaybackAndImportAreReachable() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val data = EntryPointAccessors.fromApplication(context, UiTestData::class.java)
        val art = "https://image-cdn-fa.spotifycdn.com/image/ab67616d00001e029b9b36b0e22870b9f542d937"
        val first = Track("ui-reference-one", title = "First UI Song", artist = "Reference Artist", album = "Reference Album", durationMs = 60000, albumArtUrl = art)
        val second = first.copy(isrc = "ui-reference-two", title = "Second UI Song")
        val id = data.testLibraryRepository().importPlaylist("UI Reference Playlist", art, listOf(first, second, first))
        assertEquals(listOf(first.isrc, second.isrc), data.testLibraryRepository().observeTracks(id).first().map { it.isrc })
        data.testLibraryRepository().addFavorite(first, System.currentTimeMillis())
        val show = PodcastShow("ui-reference-show", "Reference Podcast", "Reference Host", "https://example.org/reference.xml", art)
        if (data.testPodcastStore().shows.value.none { it.feedUrl == show.feedUrl }) data.testPodcastStore().toggle(show)
        val audio = File(context.filesDir, "ui-reference.wav")
        val bytes = 8000 * 60 * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(bytes + 36).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(8000).putInt(16000).putShort(2).putShort(16).put("data".toByteArray()).putInt(bytes)
        audio.outputStream().use { it.write(header.array()); it.write(ByteArray(bytes)) }
        for (track in listOf(first, second)) data.testDownloadIndex().save(DownloadEntity(track.isrc, audio.absolutePath, System.currentTimeMillis(), audio.length()))
        data.testDownloadIndex().downloads.first { first.isrc in it && second.isrc in it }
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue("Home did not become available", device.wait(Until.hasObject(By.text("Liked Songs")), 30000))
            device.wait(Until.hasObject(By.text("Picked for you")), 12000)
            screenshot(device, "home")
            device.findObject(By.text("Podcasts")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Your shows")), 5000))
            screenshot(device, "podcasts")
            bottom(device, "Search")
            assertTrue(device.wait(Until.hasObject(By.text("What do you want to listen to?")), 5000))
            assertNotNull(device.findObject(By.text("Browse all")))
            assertNull(device.findObject(By.text("Catalog")))
            screenshot(device, "search")
            bottom(device, "Your Library")
            assertTrue(device.wait(Until.hasObject(By.text("UI Reference Playlist")), 5000))
            assertNotNull(device.findObject(By.text("Reference Podcast")))
            screenshot(device, "library")
            device.findObject(By.text("UI Reference Playlist")).click()
            assertTrue(device.wait(Until.hasObject(By.text("First UI Song")), 5000))
            screenshot(device, "playlist")
            device.findObject(By.desc("Play collection")).click()
            assertTrue("Local saved song did not start", device.wait(Until.hasObject(By.desc("Pause")), 15000))
            device.findObjects(By.text("First UI Song")).maxBy { it.visibleBounds.bottom }.click()
            assertTrue(device.wait(Until.hasObject(By.text("PLAYING FROM YOUR MUSIC")), 5000))
            assertNotNull(device.findObject(By.desc("Close player")))
            screenshot(device, "player")
            device.findObject(By.desc("Close player")).click()
            device.pressBack()
            bottom(device, "Your Library")
            device.findObject(By.text("Import from Spotify")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Bring your music with you")), 5000))
            assertNotNull(device.findObject(By.text("Spotify link")))
            screenshot(device, "import")
        }
    }
    private fun bottom(device: UiDevice, label: String) = device.findObjects(By.text(label)).maxBy { it.visibleBounds.bottom }.click()
    private fun screenshot(device: UiDevice, name: String) {
        device.waitForIdle()
        // Accessibility can update before Compose submits the corresponding display frame.
        SystemClock.sleep(1500)
        device.waitForIdle()
        // connectedAndroidTest uninstalls the target app at completion, deleting its cache.
        // The automation shell writes previews outside app data so CI can export them afterward.
        val path = "/sdcard/spot-ui-$name.png"
        device.executeShellCommand("screencap -p $path")
        val bytes = device.executeShellCommand("stat -c %s $path").trim().toLongOrNull() ?: 0
        assertTrue("Screenshot $name was not written", bytes > 1000)
    }
}
