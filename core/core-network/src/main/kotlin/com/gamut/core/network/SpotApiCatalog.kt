/* SPDX-License-Identifier: GPL-3.0-only
 * Native Kotlin adaptation of the public catalogue protocol in Aran404/SpotAPI (GPL-3.0).
 * Baseline: 13cb48ddc221aa6f7d2bf1b8229bc4b158247916.
 * See docs/SPOTAPI_INTEGRATION.md. No Spotify audio, user account cookies or private account APIs.
 */
package com.gamut.core.network

import com.gamut.core.model.Track
import com.gamut.core.model.TrackSource
import javax.inject.Inject
import javax.inject.Singleton
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.nio.ByteBuffer
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

interface SpotifyCatalog { suspend fun search(query: String): List<Track> }

/** Native Android adaptation of Aran404/SpotAPI's anonymous public catalogue protocol. */
@Singleton
class SpotApiCatalog @Inject constructor() : SpotifyCatalog {
    private val cookies = mutableMapOf<String, List<Cookie>>()
    private val client = OkHttpClient.Builder().protocols(listOf(Protocol.HTTP_1_1)).callTimeout(25, TimeUnit.SECONDS)
        .followRedirects(false).cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, values: List<Cookie>) { synchronized(cookies) { cookies[url.host] = (cookies[url.host].orEmpty() + values).associateBy { it.name + "|" + it.domain + "|" + it.path }.values.toList() } }
            override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(cookies) { cookies.values.flatten().filter { it.matches(url) && it.expiresAt > nowMillis() } }
        }).build()
    private val gate = Mutex()
    private var session: Session? = null
    private class Session(val access: String, val clientToken: String, val version: String, val hash: String, val expires: Long)
    private val cache = mutableMapOf<String, Pair<Long, List<Track>>>()
    private val cdn = "https://open.spotifycdn.com/cdn/build/web-player/"

    override suspend fun search(query: String): List<Track> {
        val q = query.trim().take(300)
        if (q.length < 2) return emptyList()
        gate.lock()
        try {
            cache[q]?.takeIf { nowMillis() - it.first < 600_000 }?.let { return it.second }
            repeat(2) { attempt ->
                val s = session?.takeIf { it.expires > nowMillis() + 60_000 } ?: authorize().also { session = it }
                val variables = buildJsonObject {
                    put("searchTerm", q); put("offset", 0); put("limit", 25); put("numberOfTopResults", 5)
                    for (key in listOf("includeAudiobooks", "includeArtistHasConcertsField", "includePreReleases", "includeAlbumPreReleases", "includeAuthors", "includeEpisodeContentRatingsV2")) put(key, false)
                    put("isPrefix", JsonNull); put("sectionFilters", buildJsonArray { add("GENERIC") })
                }
                val payload = buildJsonObject {
                    put("operationName", "searchDesktop"); put("variables", variables)
                    put("extensions", buildJsonObject { put("persistedQuery", buildJsonObject { put("version", 1); put("sha256Hash", s.hash) }) })
                }
                try {
                    val body = json("https://api-partner.spotify.com/pathfinder/v2/query", payload, headers = mapOf(
                        "Authorization" to "Bearer ${s.access}", "client-token" to s.clientToken,
                        "spotify-app-version" to s.version, "app-platform" to "WebPlayer", "Origin" to "https://open.spotify.com", "Referer" to "https://open.spotify.com/"))
                    val tracks = parseTracks(body)
                    if (cache.size >= 50) cache.remove(cache.keys.first())
                    cache[q] = nowMillis() to tracks
                    return tracks
                } catch (e: CatalogException) {
                    if (attempt == 0 && e.code == 401) session = null else throw e
                }
            }
            return emptyList()
        } finally { gate.unlock() }
    }

    private suspend fun authorize(): Session {
        val html = request("https://open.spotify.com/")
        val encoded = Regex("""<script\b[^>]*\bid=["']appServerConfig["'][^>]*>([^<]+)</script>""").find(html)?.groupValues?.get(1)
            ?: changed()
        val config = try { Json.parseToJsonElement(Base64.getDecoder().decode(encoded).decodeToString()).obj() } catch (_: Exception) { changed() }
        val version = config.s("clientVersion").ifBlank { changed() }
        val links = Regex("""["'](https://open\.spotifycdn\.com/cdn/build/web-player/[^"']+\.js)["']""").findAll(html).map { it.groupValues[1] }.distinct().take(12).toList()
        val mainUrl = links.firstOrNull { "/web-player." in it } ?: changed()
        val main = request(mainUrl)
        val secretMatch = Regex("""secret\s*:\s*(["'])(.*?)\1\s*,?\s*version\s*:\s*(\d+)""", RegexOption.DOT_MATCHES_ALL)
            .findAll(main).maxByOrNull { it.groupValues[3].toIntOrNull() ?: 0 } ?: changed()
        val secret = secretMatch.groupValues[2].mapIndexed { i, char -> (char.code xor (i % 33 + 9)).toString() }.joinToString("").encodeToByteArray()
        val totp = spotifyTotp(secret, nowMillis())
        var hash = queryHash(main)
        if (hash == null) {
            val searchLinks = (links.filter { "xpui-routes-search." in it } + searchChunks(main)).distinct().take(4)
            for (url in searchLinks) { hash = queryHash(request(url)); if (hash != null) break }
        }
        // SpotAPI's explicit fallback, used only when the current build doesn't expose this query.
        val searchHash = hash ?: "db61238974d27839a136c9dc02bfdbe3fab7635f21cf85976ebff9a1ee281345"
        val access = json("https://open.spotify.com/api/token", params = listOf("reason" to "init", "productType" to "web-player", "totp" to totp, "totpServer" to totp, "totpVer" to secretMatch.groupValues[3]))
        val accessToken = access.s("accessToken").ifBlank { changed() }; val clientId = access.s("clientId").ifBlank { changed() }
        val deviceId = client.cookieJar.loadForRequest("https://open.spotify.com/".toHttpUrl()).firstOrNull { it.name == "sp_t" }?.value ?: UUID.randomUUID().toString()
        val device = buildJsonObject {
            put("device_brand", "unknown"); put("device_model", "unknown"); put("os", "windows"); put("os_version", "NT 10.0")
            put("device_id", deviceId); put("device_type", "computer")
        }
        val token = json("https://clienttoken.spotify.com/v1/clienttoken", buildJsonObject { put("client_data", buildJsonObject {
            put("client_version", version); put("client_id", clientId); put("js_sdk_data", device)
        }) }).o("granted_token").s("token").ifBlank { changed() }
        return Session(accessToken, token, version, searchHash, access.n("accessTokenExpirationTimestampMs"))
    }
    private fun queryHash(source: String) = Regex("""["']searchDesktop["']\s*,\s*["']query["']\s*,\s*["']([a-f0-9]{64})["']""").find(source)?.groupValues?.get(1)
    private fun searchChunks(main: String): List<String> {
        val start = Regex("""\.u\s*=\s*(?:[\w$]+\s*=>|function\s*\()""").find(main)?.range?.last ?: 0
        val maps = Regex("""\{\d+:"[^"]+"(?:,\d+:"[^"]+")*\}""").findAll(main, start).take(2).map { match ->
            Regex("""(\d+):"([^"]+)""").findAll(match.value).associate { it.groupValues[1] to it.groupValues[2] }
        }.toList()
        if (maps.size < 2) return emptyList()
        val hashes = maps.maxBy { map -> map.values.count { it.matches(Regex("[a-f0-9]{8,}")) } }
        val names = maps.firstOrNull { it !== hashes } ?: return emptyList()
        return names.filterValues { it.startsWith("xpui-routes-search") && it.matches(Regex("[A-Za-z0-9._-]+")) }.mapNotNull { (id, name) ->
            hashes[id]?.takeIf { it.matches(Regex("[A-Za-z0-9]+")) }?.let { "$cdn$name.$it.js" }
        }
    }
    private suspend fun json(url: String, body: JsonObject? = null, params: List<Pair<String, String>> = emptyList(), headers: Map<String, String> = emptyMap()): JsonObject =
        try { Json.parseToJsonElement(request(url, body, params, headers)).obj() } catch (e: CatalogException) { throw e } catch (_: Exception) { changed() }

    private suspend fun request(url: String, body: JsonObject? = null, params: List<Pair<String, String>> = emptyList(), headers: Map<String, String> = emptyMap()): String {
        val target = url.toHttpUrl()
        require(target.isHttps && target.host in setOf("open.spotify.com", "open.spotifycdn.com", "api-partner.spotify.com", "clienttoken.spotify.com"))
        val builder = target.newBuilder()
        params.forEach { builder.addQueryParameter(it.first, it.second) }
        val req = Request.Builder().url(builder.build())
            .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
            .header("Accept", if (body != null || target.encodedPath == "/api/token") "application/json" else "text/html, */*")
            .header("Accept-Language", "en")
        headers.forEach { (name, value) -> req.header(name, value) }
        if (body != null) req.post(body.toString().encodeToByteArray().toRequestBody("application/json".toMediaType()))
        return withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                val call = client.newCall(req.build())
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: java.io.IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
                    override fun onResponse(call: Call, response: Response) {
                        try {
                            val text = response.use {
                                if (!it.isSuccessful) throw CatalogException(it.code, "Spotify catalog request failed (HTTP ${it.code}, ${target.host}${target.encodedPath}).")
                                val limit = if (target.host == "open.spotifycdn.com") 16_000_000L else 4_000_000L
                                val source = it.body?.source() ?: error("Empty catalog response")
                                if (source.request(limit + 1)) error("Catalog response too large")
                                source.readUtf8()
                            }
                            if (continuation.isActive) continuation.resume(text)
                        } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
                    }
                })
            }
        }
    }
    private fun changed(): Nothing = throw CatalogException(502, "Spotify's catalog response has changed. Check for a Spot Music update.")

    companion object {
        internal fun parseTracks(body: JsonObject): List<Track> {
            val data = body.o("data"); val root = (data["searchV2"] ?: data["search"]) as? JsonObject
                ?: throw CatalogException(502, "Spotify returned no catalog results. Try again later.")
            val section = (root["tracksV2"] ?: root["tracks"]) as? JsonObject ?: throw CatalogException(502, "Spotify returned an incomplete track search.")
            return section.a("items").mapNotNull { entry ->
                var track = entry.obj()
                repeat(4) { if (track["item"] is JsonObject) track = track.o("item") else if (track["data"] is JsonObject) track = track.o("data") }
                val id = track.s("id").ifBlank { track.s("uri").removePrefix("spotify:track:") }
                if (!id.matches(Regex("[A-Za-z0-9]{22}")) || track.s("name").isBlank()) return@mapNotNull null
                val album = track.o("albumOfTrack")
                val artwork = (album.o("coverArt")["sources"] as? JsonArray)?.firstOrNull()?.obj()?.s("url")?.takeIf { url -> runCatching { url.toHttpUrl().let { it.isHttps && it.host.endsWith(".scdn.co") } }.getOrDefault(false) }
                val artists = (track.o("artists")["items"] as? JsonArray)?.map { it.obj().o("profile").s("name") }?.filter(String::isNotBlank).orEmpty()
                Track(isrc = "spotify:$id", title = track.s("name"), artist = artists.joinToString(", ").ifBlank { "Unknown artist" },
                    album = album.s("name"), durationMs = track.o("duration").n("totalMilliseconds"),
                    albumArtUrl = artwork, source = TrackSource.SPOTIFY)
            }.distinctBy { it.isrc }
        }
    }
}

private class CatalogException(val code: Int, message: String) : Exception(message)
private fun nowMillis() = System.currentTimeMillis()
private fun JsonElement.obj() = this as? JsonObject ?: JsonObject(emptyMap())
private fun JsonObject.o(key: String) = this[key]?.obj() ?: JsonObject(emptyMap())
private fun JsonObject.s(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
private fun JsonObject.n(key: String) = (this[key] as? JsonPrimitive)?.longOrNull ?: 0L
private fun JsonObject.a(key: String) = this[key] as? JsonArray ?: JsonArray(emptyList())
private fun spotifyTotp(key: ByteArray, timestampMs: Long): String {
    val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(key, "HmacSHA1")) }
    val hash = mac.doFinal(ByteBuffer.allocate(8).putLong(timestampMs / 30_000L).array())
    val offset = hash.last().toInt() and 0x0f
    val value = ByteBuffer.wrap(hash, offset, 4).int and 0x7fffffff
    return (value % 1_000_000).toString().padStart(6, '0')
}
