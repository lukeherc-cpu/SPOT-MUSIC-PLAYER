package com.gamut.podcasts

import org.junit.Assert.*
import org.junit.Test

class PodcastTranscriptTest {
    @Test fun webVttAndSrtKeepRealTiming() {
        val vtt = "WEBVTT\n\n00:00:01.200 --> 00:00:03.500 align:start\n<v Host>Hello &amp; welcome</v>\n\n00:00:03.500 --> 00:00:06.000\nNext sentence\n"
        val transcript = PodcastTranscriptParser.parse(vtt.toByteArray(), "text/vtt")
        assertTrue(transcript.timed)
        assertEquals(1200L, transcript.cues.first().startMs)
        assertEquals(3500L, transcript.cues.first().endMs)
        assertEquals("Hello & welcome", transcript.cues.first().text)
        val srt = "1\n00:01:02,120 --> 00:01:05,500\nCaption\n"
        assertEquals(62120L, PodcastTranscriptParser.parse(srt.toByteArray(), "application/x-subrip").cues.single().startMs)
    }
    @Test fun podcastingJsonSegmentsUsePublisherTimes() {
        val json = """{"version":"1.0.0","segments":[{"startTime":1.25,"endTime":4.5,"body":"Hello"},{"startTime":4.5,"endTime":8,"body":"World"}]}"""
        val result = PodcastTranscriptParser.parse(json.toByteArray(), "application/json")
        assertEquals(listOf(1250L, 4500L), result.cues.map { it.startMs })
        assertTrue(result.timed)
    }
    @Test fun plainTranscriptsNeverPretendToBeSynchronized() {
        val result = PodcastTranscriptParser.parse("First paragraph\n\nSecond paragraph".toByteArray(), "text/plain")
        assertFalse(result.timed)
        assertEquals(2, result.cues.size)
        assertTrue(result.cues.all { it.startMs == 0L && it.endMs == 0L })
    }
    @Test fun rssTranscriptAndExplicitCreditsReachSavedTracks() {
        val feed = """<rss xmlns:podcast="https://podcastindex.org/namespace/1.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd"><channel><item><guid>episode</guid><title>Episode</title><itunes:explicit>yes</itunes:explicit><enclosure url="https://example.org/audio.mp3" type="audio/mpeg"/><podcast:transcript url="/words.vtt" type="text/vtt" language="en"/></item></channel></rss>"""
        val episode = PodcastFeedParser.parse(feed.toByteArray(), "https://example.org/feed.xml").single()
        val track = episode.track(PodcastShow("show", "Show", "Publisher", "https://example.org/feed.xml"))
        assertEquals("https://example.org/words.vtt", track.transcripts.single().url)
        assertEquals(true, track.isExplicit)
    }
}
