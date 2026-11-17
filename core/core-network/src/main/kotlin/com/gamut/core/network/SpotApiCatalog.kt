/* SPDX-License-Identifier: GPL-3.0-only
 * Native Kotlin adaptation of the public catalogue protocol in Aran404/SpotAPI (GPL-3.0).
 * Baseline: 13cb48ddc221aa6f7d2bf1b8229bc4b158247916.
 * See docs/SPOTAPI_INTEGRATION.md. No Spotify audio, user account cookies or private account APIs.
 */
package com.gamut.core.network

import com.gamut.core.model.Track
import com.gamut.core.model.TrackSource
import com.gamut.core.model.TrackArtist
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

interface SpotifyCatalog {
    suspend fun search(query: String): List<Track>
    suspend fun searchAll(query: String): SpotifySearch = SpotifySearch(tracks = search(query))
    suspend fun collection(uri: String): SpotifyCollection = error("Spotify collections are unavailable")
}

data class SpotifyCard(val uri: String, val title: String, val subtitle: String, val artwork: String?, val type: String)
data class SpotifySearch(val tracks: List<Track> = emptyList(), val albums: List<SpotifyCard> = emptyList(), val artists: List<SpotifyCard> = emptyList(), val playlists: List<SpotifyCard> = emptyList(), val podcasts: List<SpotifyCard> = emptyList())
data class SpotifyArtistInfo(val monthlyListeners: Long = 0, val followers: Long = 0, val verified: Boolean = false, val header: String? = null, val biography: String = "", val latest: List<SpotifyCard> = emptyList(), val related: List<SpotifyCard> = emptyList())
data class SpotifyCollection(val uri: String, val title: String, val subtitle: String, val artwork: String?, val description: String, val tracks: List<Track>, val total: Int, val skipped: Int = 0, val albums: List<SpotifyCard> = emptyList(), val artistInfo: SpotifyArtistInfo? = null)


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
    private val cache = mutableMapOf<String, Pair<Long, SpotifySearch>>()
    private val hashes = mutableMapOf<String, String>()
    private var bundleUrls = emptyList<String>()
    private val visitedBundles = mutableSetOf<String>()
    private val cdn = "https://open.spotifycdn.com/cdn/build/web-player/"

    override suspend fun search(query: String): List<Track> = searchAll(query).tracks

    override suspend fun searchAll(query: String): SpotifySearch {
        val q = query.trim().take(300)
        if (q.length < 2) return SpotifySearch()
        synchronized(cache) { cache[q]?.takeIf { nowMillis() - it.first < 600_000 } }?.let { return it.second }
        val variables = buildJsonObject {
            put("searchTerm", q); put("offset", 0); put("limit", 25); put("numberOfTopResults", 5)
            for (key in listOf("includeAudiobooks", "includeArtistHasConcertsField", "includePreReleases", "includeAlbumPreReleases", "includeAuthors", "includeEpisodeContentRatingsV2")) put(key, false)
            put("isPrefix", JsonNull); put("sectionFilters", buildJsonArray { add("GENERIC") })
        }
        val body = execute("searchDesktop", variables)
        val root = body.o("data").let { it.o("searchV2").ifEmpty { it.o("search") } }
        val result = SpotifySearch(parseTracks(body), cards(root, "albums", "album"), cards(root, "artists", "artist"), cards(root, "playlists", "playlist"), cards(root, "podcasts", "show"))
        synchronized(cache) { if (cache.size >= 50) cache.remove(cache.keys.first()); cache[q] = nowMillis() to result }
        return result
    }

    override suspend fun collection(uri: String): SpotifyCollection {
        val resource = spotifyResource(uri)
        val type = resource.split(':')[1]
        val vars = buildJsonObject { put("uri", resource); put("locale", ""); put("offset", 0); put("limit", 100); put("enableWatchFeedEntrypoint", false); put("includeEpisodeContentRatingsV2", true) }
        val operation = when (type) { "playlist" -> "fetchPlaylistMetadata"; "album" -> "getAlbum"; "artist" -> "queryArtistOverview"; else -> error("Import a Spotify playlist, album or artist link") }
        val data = execute(operation, vars).o("data")
        val root = data.values.filterIsInstance<JsonObject>().firstOrNull { it.s("uri") == resource }
            ?: data.values.filterIsInstance<JsonObject>().firstOrNull() ?: changed()
        if (root.s("name").isBlank() && root.o("profile").s("name").isBlank()) error("This Spotify collection is private or unavailable")
        val tracks = mutableListOf<Track>()
        var skipped = 0
        var total = 0
        if (type == "artist") {
            val items = root.o("discography").o("topTracks").a("items").ifEmpty { root.o("topTracks").a("items") }
            tracks += items.mapNotNull { decodeTrack(unwrap(it)) }
            total = tracks.size
        } else {
            var offset = 0
            do {
                val pageVars = buildJsonObject { vars.forEach { (k,v) -> put(k,v) }; put("offset", offset); put("limit", if (type == "album") 50 else 100) }
                val pageData = execute(if (type == "playlist") "fetchPlaylistContents" else "queryAlbumTracks", pageVars).o("data")
                val page = if (type == "playlist") pageData.o("playlistV2").o("content") else pageData.o("albumUnion").o("tracksV2")
                val entries = page["items"] as? JsonArray ?: error("Spotify did not return this collection's songs")
                total = page.n("totalCount").toInt().coerceAtLeast(entries.size + offset)
                for (entry in entries) {
                    val track = decodeTrack(unwrap(entry), root.takeIf { type == "album" })
                    if (track != null) tracks += track else skipped++
                }
                val next = page.o("pagingInfo").n("nextOffset").toInt().takeIf { it > offset } ?: (offset + entries.size)
                if (entries.isEmpty() && offset < total) error("Spotify returned an incomplete collection. Try importing again.")
                offset = next
                check(offset <= 10_000) { "Import collections with fewer than 10,000 items" }
            } while (offset < total)
        }
        return SpotifyCollection(resource, root.s("name").ifBlank { root.o("profile").s("name") },
            if (type == "artist") "Artist" else if (type == "album") artistNames(root).ifBlank { "Album" } else root.o("ownerV2").o("data").s("name").ifBlank { "Spotify playlist" },
            artwork(root), root.s("description").ifBlank { root.o("profile").o("biography").s("text") }.replace(Regex("<[^>]*>"), ""), tracks, total, skipped, if (type == "artist") artistAlbums(root.o("discography")) else emptyList(),
            if (type == "artist") SpotifyArtistInfo(
                monthlyListeners = root.o("stats").n("monthlyListeners"), followers = root.o("stats").n("followers"),
                verified = root.o("profile").o("verified").b("verified") == true || root.o("profile").b("verified") == true,
                header = bestImage(root.o("visuals").o("headerImage").a("sources")),
                biography = root.o("profile").o("biography").s("text").replace(Regex("<[^>]*>"), "").take(12_000),
                latest = artistAlbums(root.o("discography").o("latest")).ifEmpty { artistAlbums(root.o("discography"), listOf("latest")) },
                related = root.o("relatedContent").o("relatedArtists").a("items").mapNotNull { value ->
                    val artist = unwrap(value); val uri = artist.s("uri"); val name = artist.o("profile").s("name")
                    if (uri.startsWith("spotify:artist:") && name.isNotBlank()) SpotifyCard(uri, name, "Artist", artwork(artist), "artist") else null
                },
            ) else null)
    }

    private suspend fun execute(operation: String, variables: JsonObject): JsonObject {
        gate.lock()
        try {
            repeat(2) { attempt ->
                val active = session?.takeIf { it.expires > nowMillis() + 60_000 } ?: authorize().also { session = it }
                val hash = if (operation == "searchDesktop") active.hash else resolveHash(operation)
                val payload = buildJsonObject {
                    put("operationName", operation); put("variables", variables)
                    put("extensions", buildJsonObject { put("persistedQuery", buildJsonObject { put("version", 1); put("sha256Hash", hash) }) })
                }
                try {
                    val result = json("https://api-partner.spotify.com/pathfinder/v2/query", payload, headers = mapOf(
                        "Authorization" to "Bearer ${active.access}", "client-token" to active.clientToken,
                        "spotify-app-version" to active.version, "app-platform" to "WebPlayer", "Origin" to "https://open.spotify.com", "Referer" to "https://open.spotify.com/"))
                    if (result.a("errors").isNotEmpty()) throw CatalogException(502, "Spotify could not load this collection. It may be private or unavailable.")
                    return result
                } catch (e: CatalogException) { if (attempt == 0 && e.code == 401) session = null else throw e }
            }
            changed()
        } finally { gate.unlock() }
    }
    private fun discoverHashes(source: String) {
        Regex("""["']([A-Za-z][A-Za-z0-9_]+)["']\s*,\s*["'](?:query|mutation)["']\s*,\s*["']([a-f0-9]{64})["']""").findAll(source).forEach { hashes[it.groupValues[1]] = it.groupValues[2] }
    }
    private suspend fun resolveHash(operation: String): String {
        hashes[operation]?.let { return it }
        val kind = when { "Playlist" in operation -> "playlist"; "Album" in operation -> "album"; "Artist" in operation -> "artist"; else -> "search" }
        val candidates = bundleUrls.filter { it.contains("xpui-routes-$kind") || it.contains("$kind-page", true) }
        for (url in candidates.take(10)) {
            if (visitedBundles.add(url)) discoverHashes(request(url))
            hashes[operation]?.let { return it }
        }
        return when (operation) {
            "getAlbum" -> "0b6ce3511e1b02e4a40fa95440f992acbe85080eb308af9194322d708fc1914f"
            "queryArtistOverview" -> "eafb1620d783ece118e72fbfede2b0064483e029d3c1aafafa1c2de0e25a89e7"
            else -> error("Spotify changed its collection API. Try again after updating Spot Music.")
        }
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
        hashes.clear(); visitedBundles.clear()
        discoverHashes(main)
        bundleUrls = (links + bundleChunks(main)).distinct()
        val totp = spotifyTotp(secret, nowMillis())
        var hash = queryHash(main)
        if (hash == null) {
            val searchLinks = (links.filter { "xpui-routes-search." in it } + bundleChunks(main).filter { "xpui-routes-search" in it }).distinct().take(4)
            for (url in searchLinks) { val source = request(url); discoverHashes(source); hash = queryHash(source); if (hash != null) break }
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
    private fun bundleChunks(main: String): List<String> {
        val start = Regex("""\.u\s*=\s*(?:[\w$]+\s*=>|function\s*\()""").find(main)?.range?.last ?: 0
        val maps = Regex("""\{\d+:"[^"]+"(?:,\d+:"[^"]+")*\}""").findAll(main, start).take(2).map { match ->
            Regex("""(\d+):"([^"]+)""").findAll(match.value).associate { it.groupValues[1] to it.groupValues[2] }
        }.toList()
        if (maps.size < 2) return emptyList()
        val hashes = maps.maxBy { map -> map.values.count { it.matches(Regex("[a-f0-9]{8,}")) } }
        val names = maps.firstOrNull { it !== hashes } ?: return emptyList()
        return names.filterValues { it.matches(Regex("[A-Za-z0-9._-]+")) }.mapNotNull { (id, name) ->
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
            return section.a("items").mapNotNull { decodeTrack(unwrap(it)) }.distinctBy { it.isrc }
        }
        internal fun spotifyResource(raw: String): String {
            val trimmed = raw.trim()
            val match = Regex("^spotify:(playlist|album|artist):([A-Za-z0-9]{22})$").matchEntire(trimmed)
            if (match != null) return trimmed
            val url = runCatching { trimmed.toHttpUrl() }.getOrNull()
            require(url != null && url.isHttps && url.host == "open.spotify.com" && url.username.isEmpty() && url.password.isEmpty()) { "Paste an open.spotify.com playlist or album link" }
            val segments = url.pathSegments.filter { it.isNotBlank() && !it.startsWith("intl-") }
            require(segments.size == 2 && segments[0] in listOf("playlist", "album", "artist") && segments[1].matches(Regex("[A-Za-z0-9]{22}"))) { "Paste a Spotify playlist, album or artist link" }
            return "spotify:${segments[0]}:${segments[1]}"
        }
        private fun unwrap(value: JsonElement): JsonObject {
            var item = value.obj()
            repeat(5) { item = listOf("itemV2", "item", "data", "track").firstNotNullOfOrNull { item[it] as? JsonObject } ?: item }
            return item
        }
        private fun artistNames(track: JsonObject): String = track.o("artists").a("items").map { it.obj().let { artist -> artist.o("profile").s("name").ifBlank { artist.s("name") } } }.filter(String::isNotBlank).joinToString(", ")
        internal fun artwork(item: JsonObject): String? {
            val sources = item.o("coverArt").a("sources").ifEmpty { item.o("visuals").o("avatarImage").a("sources") }.ifEmpty { item.o("images").a("items").firstOrNull()?.obj()?.a("sources").orEmpty() }
            return bestImage(sources)
        }
        private fun bestImage(sources: List<JsonElement>): String? = sources.map { it.obj() }.filter { image ->
            runCatching { image.s("url").toHttpUrl().let { it.isHttps && (it.host.endsWith(".scdn.co") || it.host.endsWith(".spotifycdn.com") || it.host == "i.scdn.co") } }.getOrDefault(false)
        }.maxByOrNull { maxOf(it.n("width"), it.n("height")) }?.s("url")
        private fun trackArtists(track: JsonObject): List<TrackArtist> = track.o("artists").a("items").mapNotNull { value ->
            val artist = unwrap(value); val name = artist.o("profile").s("name").ifBlank { artist.s("name") }
            if (name.isBlank()) null else TrackArtist(name, artist.s("uri").takeIf { it.matches(Regex("spotify:artist:[A-Za-z0-9]{22}")) })
        }.distinctBy { it.uri ?: it.name }
        private fun decodeTrack(track: JsonObject, fallbackAlbum: JsonObject? = null): Track? {
            val id = track.s("id").ifBlank { track.s("uri").removePrefix("spotify:track:") }
            if (!id.matches(Regex("[A-Za-z0-9]{22}")) || track.s("name").isBlank()) return null
            val album = track.o("albumOfTrack").ifEmpty { fallbackAlbum ?: JsonObject(emptyMap()) }
            return Track("spotify:$id", title = track.s("name"), artist = artistNames(track).ifBlank { artistNames(album) }.ifBlank { "Unknown artist" },
                album = album.s("name"), durationMs = track.o("duration").n("totalMilliseconds"), albumArtUrl = artwork(album), source = TrackSource.SPOTIFY,
                albumUri = album.s("uri").takeIf { it.matches(Regex("spotify:album:[A-Za-z0-9]{22}")) }
                    ?: album.s("id").takeIf { it.matches(Regex("[A-Za-z0-9]{22}")) }?.let { "spotify:album:$it" },
                isExplicit = track.o("contentRating").s("label").takeIf { it.isNotBlank() }?.let { it.equals("EXPLICIT", true) } ?: track.b("explicit"),
                artists = trackArtists(track).ifEmpty { trackArtists(album) })
        }
        private fun artistAlbums(root: JsonObject, sections: List<String> = listOf("albums", "singles", "popularReleasesAlbums")): List<SpotifyCard> {
            val result = mutableListOf<SpotifyCard>()
            fun visit(value: JsonElement, depth: Int) {
                if (depth > 10 || result.size >= 40) return
                if (value is JsonObject) {
                    val uri = value.s("uri")
                    if (uri.startsWith("spotify:album:") && value.s("name").isNotBlank()) result += SpotifyCard(uri, value.s("name"), "Album", artwork(value), "album")
                    else value.values.forEach { visit(it, depth + 1) }
                } else if (value is JsonArray) value.forEach { visit(it, depth + 1) }
            }
            sections.forEach { root[it]?.let { value -> visit(value, 0) } }
            return result.distinctBy { it.uri }
        }
        private fun cards(root: JsonObject, section: String, type: String): List<SpotifyCard> {
            val items = root.o(section).a("items").ifEmpty { root.o("${section}V2").a("items") }
            return items.mapNotNull { entry ->
                val item = unwrap(entry)
                val uri = item.s("uri"); val title = item.s("name").ifBlank { item.o("profile").s("name") }
                if (!uri.startsWith("spotify:$type:") || title.isBlank()) null else SpotifyCard(uri, title,
                    artistNames(item).ifBlank { item.o("ownerV2").o("data").s("name") }.ifBlank { type.replaceFirstChar(Char::titlecase) }, artwork(item), type)
            }.distinctBy { it.uri }
        }

    }
}

private class CatalogException(val code: Int, message: String) : Exception(message)
private fun nowMillis() = System.currentTimeMillis()
private fun JsonElement.obj() = this as? JsonObject ?: JsonObject(emptyMap())
private fun JsonObject.o(key: String) = this[key]?.obj() ?: JsonObject(emptyMap())
private fun JsonObject.s(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
private fun JsonObject.n(key: String) = (this[key] as? JsonPrimitive)?.longOrNull ?: 0L
private fun JsonObject.b(key: String) = (this[key] as? JsonPrimitive)?.booleanOrNull
private fun JsonObject.a(key: String) = this[key] as? JsonArray ?: JsonArray(emptyList())
private fun spotifyTotp(key: ByteArray, timestampMs: Long): String {
    val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(key, "HmacSHA1")) }
    val hash = mac.doFinal(ByteBuffer.allocate(8).putLong(timestampMs / 30_000L).array())
    val offset = hash.last().toInt() and 0x0f
    val value = ByteBuffer.wrap(hash, offset, 4).int and 0x7fffffff
    return (value % 1_000_000).toString().padStart(6, '0')
}
