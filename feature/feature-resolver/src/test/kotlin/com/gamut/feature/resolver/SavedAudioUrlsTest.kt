package com.gamut.feature.resolver

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class SavedAudioUrlsTest {
    @Test fun survivesRestartExpiresAndInvalidatesFailedSource() {
        val directory = Files.createTempDirectory("saved-audio").toFile()
        try {
            val file = java.io.File(directory, "urls.json")
            var now = 1_000_000L
            val url = "https://audio.googlevideo.com/videoplayback?expire=4000"
            SavedAudioUrls(file) { now }.put("video|NORMAL", url)
            assertEquals(url, SavedAudioUrls(file) { now }.get("video|NORMAL"))
            val cache = SavedAudioUrls(file) { now }
            cache.forget("video")
            assertNull(SavedAudioUrls(file) { now }.get("video|NORMAL"))
            cache.put("video|NORMAL", url)
            now = 4_000_000L
            assertNull(SavedAudioUrls(file) { now }.get("video|NORMAL"))
        } finally { directory.deleteRecursively() }
    }
}
