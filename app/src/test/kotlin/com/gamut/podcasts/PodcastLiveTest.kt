package com.gamut.podcasts

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class PodcastLiveTest {
    @Test fun livePublisherFeedAndAudio() = runBlocking {
        assumeTrue(System.getenv("SPOT_LIVE_SMOKE") == "1")
        val repository = PodcastRepository()
        val shows = repository.search("NASA Curious Universe")
        assertTrue("Podcast directory returned no shows", shows.isNotEmpty())
        val show = shows.first { "curious universe" in it.title.lowercase() }
        val episode = repository.episodes(show).first()
        val start = System.nanoTime()
        val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
        client.newCall(Request.Builder().url(episode.audioUrl).header("Range", "bytes=0-2047").build()).execute().use { response ->
            assertTrue("Publisher audio HTTP ${response.code}", response.isSuccessful)
            val buffer = ByteArray(2048)
            assertTrue(response.body!!.byteStream().read(buffer) > 0)
        }
        println("Podcast directory, RSS and audio passed; first audio bytes in ${(System.nanoTime() - start) / 1_000_000} ms")
    }
}
