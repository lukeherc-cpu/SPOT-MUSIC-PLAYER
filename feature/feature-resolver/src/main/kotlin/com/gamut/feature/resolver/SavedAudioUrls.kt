package com.gamut.feature.resolver

import java.io.File
import java.net.URI
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Private, bounded cache. URLs expire and must never be included in library exports. */
internal class SavedAudioUrls(private val file: File, private val now: () -> Long = System::currentTimeMillis) {
    @Serializable private data class Entry(val url: String, val expiresAt: Long)
    private val entries: LinkedHashMap<String, Entry> by lazy {
        runCatching { Json.decodeFromString<LinkedHashMap<String, Entry>>(file.readText()) }.getOrDefault(linkedMapOf())
    }
    @Synchronized fun get(key: String): String? = entries[key]?.takeIf { it.expiresAt > now() }?.url
    @Synchronized fun put(key: String, url: String) {
        if (runCatching { URI(url).scheme != "https" }.getOrDefault(true)) return
        val expiry = expiryOf(url, now())
        if (expiry <= now()) return
        entries.entries.removeAll { it.value.expiresAt <= now() }
        entries.remove(key)
        entries[key] = Entry(url, expiry)
        while (entries.size > 500) entries.remove(entries.keys.first())
        persist()
    }
    @Synchronized fun forget(videoId: String) {
        entries.keys.removeAll { it.startsWith("$videoId|") }
        persist()
    }
    @Synchronized fun clear() { entries.clear(); file.delete() }
    private fun persist() {
        runCatching {
            file.parentFile?.mkdirs()
            val temporary = File(file.parentFile, "${file.name}.tmp")
            temporary.writeText(Json.encodeToString(entries))
            check(temporary.renameTo(file))
        }
    }
    companion object {
        internal fun expiryOf(url: String, now: Long): Long =
            Regex("[?&]expire=(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull()
                ?.takeIf { it < Long.MAX_VALUE / 1000 }?.times(1000)?.minus(120_000)
                ?.coerceAtMost(now + 6 * 60 * 60_000L) ?: (now + 20 * 60_000L)
    }
}
