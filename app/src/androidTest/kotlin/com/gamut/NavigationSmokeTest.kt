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
import com.gamut.core.model.PlaybackState
import com.gamut.feature.library.LibraryRepository
import com.gamut.podcasts.*
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
        val first = Track("ui-reference-one", title = "First UI Song", artist = "Reference Artist", album = "Reference Album", durationMs = 60000, albumArtUrl = art, albumUri = "spotify:album:4m2880jivSbbyEGAKfITCa", isExplicit = true, artists = listOf(com.gamut.core.model.TrackArtist("Daft Punk", "spotify:artist:4tZwfgrHOc3mvqYlEYSvVi"), com.gamut.core.model.TrackArtist("Pharrell Williams", "spotify:artist:2RdwBSPQiwcmiDo9kixcl8")))
        val second = first.copy(isrc = "ui-reference-two", title = "Second UI Song")
        val id = data.testLibraryRepository().importPlaylist("UI Reference Playlist", art, listOf(first, second, first))
        assertEquals(listOf(first.isrc, second.isrc), data.testLibraryRepository().observeTracks(id).first().map { it.isrc })
        data.testLibraryRepository().addFavorite(first, System.currentTimeMillis())
        val saveTarget = data.testLibraryRepository().createPlaylist("Save sheet target")
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
            assertTrue(device.wait(Until.hasObject(By.text("Daily Mixes")), 5000))
            device.wait(Until.hasObject(By.text("Daily Mix 1")), 15000)
            screenshot(device, "home")
            device.findObject(By.text("Podcasts")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Your shows")), 5000))
            screenshot(device, "podcasts")
            bottom(device, "Search")
            assertTrue(device.wait(Until.hasObject(By.text("What do you want to listen to?")), 5000))
            assertNotNull(device.findObject(By.text("Browse all")))
            assertNull(device.findObject(By.text("Catalog")))
            screenshot(device, "search")
            device.findObject(By.text("Charts")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Top charts in the US")), 5000))
            assertNull(device.findObject(By.text("What do you want to listen to?")))
            screenshot(device, "charts")
            device.findObject(By.text("Top 50 - USA")).click()
            assertTrue(device.wait(Until.hasObject(By.desc("Back")), 5000))
            device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.text("Top charts in the US")), 5000))
            device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.text("Browse all")), 5000))
            bottom(device, "Your Library")
            assertTrue(device.wait(Until.hasObject(By.text("UI Reference Playlist")), 5000))
            assertNotNull(device.findObject(By.text("Reference Podcast")))
            screenshot(device, "library")
            device.findObject(By.text("UI Reference Playlist")).click()
            assertTrue(device.wait(Until.hasObject(By.text("First UI Song")), 5000))
            screenshot(device, "playlist")
            val beforeQueue = data.testMusicPlayer().queue.value.tracks.size
            val songRow = device.findObject(By.text("First UI Song")).visibleBounds
            device.swipe((device.displayWidth * .15).toInt(), songRow.centerY(), (device.displayWidth * .82).toInt(), songRow.centerY(), 22)
            withTimeout(5000) { data.testMusicPlayer().queue.first { it.tracks.size > beforeQueue && it.tracks.last().isrc == first.isrc } }
            assertTrue(device.wait(Until.hasObject(By.text("First UI Song")), 5000))
            device.findObject(By.text("First UI Song")).longClick()
            assertTrue(device.wait(Until.hasObject(By.text("Go to queue")), 5000))
            assertNotNull(device.findObject(By.text("Remove from this playlist")))
            screenshot(device, "song-menu")
            device.findObject(By.text("Go to queue")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Next in queue")), 5000))
            screenshot(device, "queue")
            device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.text("First UI Song")), 5000))
            device.findObjects(By.desc("Song options")).first().click()
            assertTrue(device.wait(Until.hasObject(By.text("Go to album")), 5000))
            device.findObject(By.text("Go to album")).click()
            assertTrue(device.wait(Until.hasObject(By.desc("Back")), 5000))
            device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.text("First UI Song")), 5000))
            device.findObject(By.desc("Play collection")).click()
            assertTrue("Local saved song did not start", device.wait(Until.hasObject(By.desc("Pause")), 15000))
            device.findObjects(By.text("First UI Song")).maxBy { it.visibleBounds.bottom }.click()
            assertTrue(device.wait(Until.hasObject(By.text("PLAYING FROM YOUR MUSIC")), 5000))
            assertNotNull(device.findObject(By.desc("Close player")))
            device.findObject(By.desc("Playback position")).click()
            withTimeout(5000) { data.testMusicPlayer().playbackState.first { state -> state is PlaybackState.Playing && state.positionMs in 25000..40000 } }
            screenshot(device, "player")
            device.findObject(By.desc("Save song")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Saved in")), 5000))
            device.findObject(By.text("Save sheet target")).click()
            withTimeout(5000) { data.testLibraryRepository().observeTracks(saveTarget).first { rows -> rows.any { it.isrc == first.isrc } } }
            screenshot(device, "save")
            device.findObject(By.text("Done")).click()
            assertTrue(device.wait(Until.hasObject(By.desc("Cover art, show lyrics")), 5000))
            val coverBounds = device.findObject(By.desc("Cover art, show lyrics")).visibleBounds
            device.findObject(By.desc("Cover art, show lyrics")).click()
            assertTrue(device.wait(Until.hasObject(By.desc("Expand Lyrics")), 5000))
            screenshot(device, "lyrics")
            device.findObject(By.desc("Expand Lyrics")).click()
            assertTrue(device.wait(Until.hasObject(By.desc("Minimize text")), 5000))
            assertNull(device.findObject(By.desc("Playback position")))
            device.findObject(By.desc("Minimize text")).click()
            assertTrue(device.wait(Until.hasObject(By.desc("Show cover art")), 5000))
            device.findObject(By.desc("Show cover art")).click()
            assertTrue(device.wait(Until.hasObject(By.desc("Cover art, show lyrics")), 5000))
            val restored = device.findObject(By.desc("Cover art, show lyrics")).visibleBounds
            assertEquals(coverBounds.width(), restored.width())
            assertEquals(coverBounds.height(), restored.height())
            device.findObject(By.desc("Player options")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Go to artist")), 5000))
            device.findObject(By.text("Go to artist")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Artists on this track")), 5000))
            assertNotNull(device.findObject(By.text("Daft Punk")))
            assertNotNull(device.findObject(By.text("Pharrell Williams")))
            device.pressBack()
            device.findObject(By.desc("Close player")).click()
            device.pressBack()
            bottom(device, "Your Library")
            // The upgrade fixture and save-sheet target can push the footer below the
            // viewport. Use the library's always-visible import menu entry.
            assertTrue(device.wait(Until.hasObject(By.desc("Create or import")), 5000))
            device.findObject(By.desc("Create or import")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Import from Spotify")), 5000))
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
