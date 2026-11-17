package com.gamut.podcasts

import kotlinx.serialization.json.*

data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String)
data class PodcastTranscript(val cues: List<SubtitleCue>, val timed: Boolean)

/** Publisher VTT, SRT, Podcasting 2.0 JSON and plain text. Never invent timing for plain text. */
object PodcastTranscriptParser {
    const val MAX_BYTES = 6_000_000
    fun parse(bytes: ByteArray, type: String): PodcastTranscript {
        require(bytes.size <= MAX_BYTES) { "Transcript is too large" }
        val raw = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val mime = type.substringBefore(';').lowercase()
        if (mime.contains("json") || raw.trimStart().startsWith('{') || raw.trimStart().startsWith('[')) return json(raw)
        val stamp = Regex("(?m)^\\s*((?:\\d{1,3}:)?\\d{2}:\\d{2}[.,]\\d{1,3})\\s*-->\\s*((?:\\d{1,3}:)?\\d{2}:\\d{2}[.,]\\d{1,3})[^\\n]*\\n")
        val matches = stamp.findAll(raw).toList().take(50_000)
        if (matches.isNotEmpty()) {
            val cues = matches.mapIndexedNotNull { index, match ->
                val end = raw.indexOf("\n\n", match.range.last + 1).takeIf { it >= 0 } ?: (matches.getOrNull(index + 1)?.range?.first ?: raw.length)
                val text = clean(raw.substring(match.range.last + 1, end))
                val startMs = timestamp(match.groupValues[1]); val endMs = timestamp(match.groupValues[2])
                text.takeIf { it.isNotBlank() && endMs > startMs }?.let { SubtitleCue(startMs, endMs, it) }
            }.sortedBy { it.startMs }
            require(cues.isNotEmpty()) { "No subtitle cues in transcript" }
            return PodcastTranscript(cues, true)
        }
        require(mime == "text/plain") { "Publisher transcript has an unsupported format" }
        val cues = raw.split(Regex("\\n\\s*\\n")).map { clean(it) }.filter(String::isNotBlank).take(50_000).map { SubtitleCue(0, 0, it) }
        require(cues.isNotEmpty()) { "Publisher transcript is empty" }
        return PodcastTranscript(cues, false)
    }
    private fun json(raw: String): PodcastTranscript {
        val root = Json.parseToJsonElement(raw)
        val entries = when (root) { is JsonArray -> root; is JsonObject -> root["segments"] as? JsonArray ?: root["results"] as? JsonArray; else -> null } ?: error("No transcript segments")
        val cues = entries.take(50_000).mapNotNull { entry ->
            val item = entry as? JsonObject ?: return@mapNotNull null
            fun number(vararg keys: String) = keys.firstNotNullOfOrNull { (item[it] as? JsonPrimitive)?.doubleOrNull?.takeIf(Double::isFinite) }
            val start = number("startTime", "start") ?: return@mapNotNull null
            val end = number("endTime", "end") ?: start
            val text = listOf("body", "text").firstNotNullOfOrNull { (item[it] as? JsonPrimitive)?.contentOrNull }?.let(::clean).orEmpty()
            if (start < 0 || end <= start || end > 7 * 24 * 3600 || text.isBlank()) null else SubtitleCue((start * 1000).toLong(), (end * 1000).toLong(), text)
        }.sortedBy { it.startMs }
        require(cues.isNotEmpty()) { "No timed transcript segments" }
        return PodcastTranscript(cues, true)
    }
    private fun timestamp(raw: String): Long = raw.replace(',', '.').split(':').fold(0.0) { sum, part -> sum * 60 + (part.toDoubleOrNull() ?: 0.0) }.times(1000).toLong().coerceIn(0, 7 * 24 * 60 * 60_000L)
    private fun clean(raw: String) = raw.replace(Regex("<[^>]*>"), "").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace(Regex("[ \\t]+"), " ").trim().take(12_000)
}
