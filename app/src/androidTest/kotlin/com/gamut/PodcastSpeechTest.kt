package com.gamut

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gamut.core.database.DownloadEntity
import com.gamut.core.model.Track
import com.gamut.core.model.TrackSource
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PodcastSpeechTest {
    @Test fun missingPublisherTranscriptGeneratesTimedSubtitlesFromRealSpeech() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val data = EntryPointAccessors.fromApplication(context, UiTestData::class.java)
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets.open("speech-jfk.mp3.base64").use { Base64.decode(it.readBytes(), Base64.DEFAULT) }
        val file = File(context.cacheDir, "speech-fixture.mp3").apply { writeBytes(bytes) }
        val track = Track("podcast:speech-fixture", title = "Public speech fixture", artist = "Fixture", durationMs = 11_000, source = TrackSource.PODCAST)
        data.testDownloadIndex().save(DownloadEntity(track.isrc, file.path, System.currentTimeMillis(), file.length()))
        withTimeout(5000) { data.testDownloadIndex().downloads.first { track.isrc in it } }
        val subtitles = data.testPodcastSubtitles()
        withContext(Dispatchers.Main.immediate) {
            data.testMusicPlayer().setQueue(listOf(track), 0)
            data.testMusicPlayer().play(track, file.path)
        }
        val result = try {
            withTimeout(120_000) { subtitles.state.first { it.trackId == track.isrc && (it.cues.isNotEmpty() || it.error != null) } }
        } finally {
            withContext(Dispatchers.Main.immediate) { data.testMusicPlayer().pause() }
        }
        assertNull(result.error)
        assertTrue(result.generated)
        assertTrue(result.timed)
        val text = result.cues.joinToString(" ") { it.text }.lowercase()
        assertTrue("Actual recognizer result: $text", "fellow americans" in text)
        assertTrue(result.cues.all { it.startMs >= 0 && it.endMs > it.startMs && it.endMs <= 14_000 })
        assertTrue(File(context.filesDir, "podcast-subtitles").listFiles().orEmpty().any { it.extension == "json" && "fellow" in it.readText().lowercase() })
    }
}
