package com.gamut.podcasts

import org.junit.Assert.*
import org.junit.Test

class PodcastFeedParserTest {
    private val base = "https://example.org/show/feed.xml"
    @Test fun parsesPublisherEnclosuresAndStableEpisodeIds() {
        val xml = """<rss xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd"><channel>
          <item><guid>episode-1</guid><title>A &amp; B</title><description><![CDATA[<p>Hello world</p>]]></description>
          <itunes:duration>1:02:03</itunes:duration><itunes:image href="/art.jpg"/>
          <enclosure url="../ep.mp3?sig=1&amp;x=2" type="audio/mpeg"/></item>
          <item><guid>episode-1</guid><enclosure url="https://cdn.example.org/duplicate.mp3" type="audio/mpeg"/></item>
          <item><enclosure url="http://insecure.example.org/audio.mp3" type="audio/mpeg"/></item>
          <item><enclosure url="https://example.org/movie.mp4" type="video/mp4"/></item>
        </channel></rss>"""
        val episodes = PodcastFeedParser.parse(xml.toByteArray(), base)
        assertEquals(1, episodes.size)
        val ep = episodes.single()
        assertEquals("A & B", ep.title)
        assertEquals("Hello world", ep.description)
        assertEquals(3_723_000L, ep.durationMs)
        assertEquals("https://example.org/ep.mp3?sig=1&x=2", ep.audioUrl)
        assertEquals("https://example.org/art.jpg", ep.artwork)
        val show = PodcastShow("1", "Show", "Host", base)
        assertEquals(ep.track(show).isrc, ep.copy(title = "Renamed").track(show).isrc)
        assertEquals(ep.audioUrl, ep.track(show).directAudioUrl)
    }
    @Test fun rejectsExternalEntitiesAndOversizedFeeds() {
        val xml = """<!DOCTYPE rss [<!ENTITY x SYSTEM "file:///etc/passwd">]><rss><channel><item><title>&x;</title></item></channel></rss>"""
        assertThrows(IllegalArgumentException::class.java) { PodcastFeedParser.parse(xml.toByteArray(), base) }
        assertThrows(IllegalArgumentException::class.java) { PodcastFeedParser.parse(xml.toByteArray(Charsets.UTF_16), base) }
        assertThrows(IllegalArgumentException::class.java) { PodcastFeedParser.parse(ByteArray(PodcastFeedParser.MAX_BYTES + 1), base) }
    }
    @Test fun handlesMissingAndInvalidDurations() {
        assertEquals(0L, PodcastFeedParser.durationMs(""))
        assertEquals(93_000L, PodcastFeedParser.durationMs("93"))
        assertEquals(93_000L, PodcastFeedParser.durationMs("1:33"))
        assertEquals(0L, PodcastFeedParser.durationMs("-1:10"))
        assertNull(PodcastFeedParser.httpsUrl("javascript:alert(1)", base))
        assertNull(PodcastFeedParser.httpsUrl("https://user:pass@example.org/file", base))
    }
}
