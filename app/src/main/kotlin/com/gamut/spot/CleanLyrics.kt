package com.gamut.spot

import com.gamut.core.model.Track
import com.gamut.feature.playerui.LyricsState

/** Preserve provider timing, but never display uncensored words for a clean recording. */
internal object CleanLyrics {
    private val cleanLabel = Regex("\\b(clean(?: version| edit)?|censored|non[ -]?explicit|no swearing|radio edit|edited version)\\b", RegexOption.IGNORE_CASE)
    private val profanity = Regex("(?i)(?<![\\p{L}\\p{N}])(?:motherf(?:uck|ucking|ucker|uckers)|fuck(?:s|ed|er|ers|in|ing)?|bullshit|horseshit|shit(?:s|ty|ting|head)?|bitch(?:es|y|ing)?|ass(?:es|hole|holes)?|bastard(?:s)?|cunt(?:s)?|dick(?:s|head)?|cock(?:s|sucker|suckers)?|puss(?:y|ies)|whore(?:s)?|slut(?:s)?|nigg(?:a|as|er|ers)|fagg?(?:ot|ots)?|damn(?:ed)?|goddamn(?:ed)?|piss(?:ed|ing)?|tits?|twat(?:s)?)(?![\\p{L}\\p{N}])")
    fun required(track: Track) = track.isExplicit == false || cleanLabel.containsMatchIn(track.title)
    fun text(value: String): String = profanity.replace(value, "•••")
    fun apply(track: Track, state: LyricsState): LyricsState = if (!required(track) || state !is LyricsState.Loaded) state else state.copy(
        lines = state.lines.map { line -> line.copy(text = text(line.text), words = line.words.map { it.copy(text = text(it.text)) }) }
    )
}
