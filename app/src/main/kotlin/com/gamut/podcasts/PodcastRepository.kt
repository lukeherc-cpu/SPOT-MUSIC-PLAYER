package com.gamut.podcasts

import java.net.InetAddress
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import com.gamut.core.model.Track
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class PodcastRepository @Inject constructor() {
    // Feeds are untrusted input. Apply DNS restrictions to each feed request and redirect.
    private val client = OkHttpClient.Builder().callTimeout(35, TimeUnit.SECONDS)
        .followRedirects(false).dns(PublicPodcastDns).build()
    private val cache = object : LinkedHashMap<String, Pair<Long, List<PodcastEpisode>>>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, List<PodcastEpisode>>>) = size > 20
    }
    suspend fun search(query: String): List<PodcastShow> = withContext(Dispatchers.IO) {
        val url = "https://itunes.apple.com/search".toHttpUrl().newBuilder()
            .addQueryParameter("term", query.trim().take(200)).addQueryParameter("media", "podcast")
            .addQueryParameter("entity", "podcast").addQueryParameter("country", "US").addQueryParameter("limit", "30").build()
        val root = Json.parseToJsonElement(fetch(url.toString(), 2_000_000).toString(Charsets.UTF_8)).jsonObject
        (root["results"] as? JsonArray).orEmpty().mapNotNull { result ->
            val item = result.jsonObject
            fun value(key: String) = (item[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val feed = PodcastFeedParser.httpsUrl(value("feedUrl"), "https://itunes.apple.com/") ?: return@mapNotNull null
            if (value("feedUrl").isBlank()) return@mapNotNull null
            PodcastShow(value("collectionId").ifBlank { feed }, value("collectionName"), value("artistName"), feed,
                value("artworkUrl600").ifBlank { value("artworkUrl100") }.takeIf { it.startsWith("https://") },
                (item["genres"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.filterNot { it == "Podcasts" })
        }.distinctBy { it.feedUrl }
    }
    suspend fun episodes(show: PodcastShow, refresh: Boolean = false): List<PodcastEpisode> = withContext(Dispatchers.IO) {
        if (!refresh) synchronized(cache) { cache[show.feedUrl]?.takeIf { System.currentTimeMillis() - it.first < 15 * 60_000L } }?.let { return@withContext it.second }
        val bytes = fetch(show.feedUrl, PodcastFeedParser.MAX_BYTES)
        PodcastFeedParser.parse(bytes, show.feedUrl).also { synchronized(cache) { cache[show.feedUrl] = System.currentTimeMillis() to it } }
    }
    private val transcripts = object : LinkedHashMap<String, PodcastTranscript>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PodcastTranscript>) = size > 12
    }
    suspend fun transcript(track: Track): PodcastTranscript? = withContext(Dispatchers.IO) {
        val sources = track.transcripts.filter { it.type.substringBefore(';') in listOf("text/vtt", "application/x-subrip", "application/srt", "text/srt", "application/json", "text/plain") }
            .sortedWith(compareBy({ if (it.language.isNullOrBlank() || it.language.orEmpty().startsWith("en")) 0 else 1 }, { if (it.type == "text/plain") 1 else 0 }))
        var failure: Exception? = null
        for (source in sources) {
            synchronized(transcripts) { transcripts[source.url] }?.let { return@withContext it }
            try {
                val parsed = PodcastTranscriptParser.parse(fetch(source.url, PodcastTranscriptParser.MAX_BYTES), source.type)
                synchronized(transcripts) { transcripts[source.url] = parsed }
                return@withContext parsed
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { failure = e }
        }
        if (failure != null) throw failure
        null
    }
    private suspend fun fetch(initial: String, limit: Int): ByteArray {
        var url = initial.toHttpUrl()
        repeat(6) {
            require(url.isHttps && url.username.isEmpty() && url.password.isEmpty()) { "This podcast requires an unsupported connection" }
            val result = request(Request.Builder().url(url).header("User-Agent", "SpotMusic/1.0 (+https://github.com/lukeherc-cpu/SPOT-MUSIC-PLAYER)").build(), limit)
            if (result.code in listOf(301, 302, 303, 307, 308)) {
                url = url.resolve(result.location.orEmpty()) ?: error("Invalid podcast redirect")
            } else {
                check(result.code in 200..299) { "Podcast publisher is unavailable (${result.code})" }
                return result.body
            }
        }
        error("Too many podcast redirects")
    }
    private data class HttpResult(val code: Int, val location: String?, val body: ByteArray)
    private suspend fun request(request: Request, limit: Int): HttpResult = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.use {
                        val bytes = if (it.isSuccessful) {
                            val source = it.body?.source() ?: error("Empty podcast feed")
                            check(!source.request(limit + 1L)) { "Podcast feed is too large" }
                            source.readByteArray()
                        } else ByteArray(0)
                        HttpResult(it.code, it.header("Location"), bytes)
                    }
                    if (continuation.isActive) continuation.resume(result)
                } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
            }
        })
    }

}

internal object PublicPodcastDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> = Dns.SYSTEM.lookup(hostname).also { addresses ->
        require(addresses.isNotEmpty() && addresses.none { it.isAnyLocalAddress || it.isLoopbackAddress || it.isLinkLocalAddress || it.isSiteLocalAddress || it.isMulticastAddress || (it.address.size == 16 && (it.address[0].toInt() and 0xfe) == 0xfc) }) { "Private podcast addresses are unsupported" }
    }
}
