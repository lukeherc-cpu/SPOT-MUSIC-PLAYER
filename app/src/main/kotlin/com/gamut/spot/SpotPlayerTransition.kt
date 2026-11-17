/* SPDX-License-Identifier: GPL-3.0-only
 * Mini-player container transform and cover-flip timing adapted from ReadAlong (GPL-3.0).
 * https://github.com/readalongapp/ReadAlong (PlayerSheet.kt and PlayerScreen.kt)
 */
package com.gamut.spot

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.gamut.core.model.Track

private class PlayerTransformShape(val progress: Float, val start: Rect?, val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val from = start ?: Rect(8f, size.height * .84f, size.width - 8f, size.height * .94f)
        val p = progress.coerceIn(0f, 1f)
        fun value(a: Float, b: Float) = a + (b - a) * p
        return Outline.Rounded(RoundRect(value(from.left, 0f), value(from.top, 0f), value(from.right, size.width), value(from.bottom, size.height), CornerRadius(radius * (1 - p))))
    }
}

@Composable
internal fun SpotPlayerTransition(visible: Boolean, bounds: Rect?, track: Track?, content: @Composable () -> Unit) {
    val progress by animateFloatAsState(if (visible && track != null) 1f else 0f, tween(420, easing = FastOutSlowInEasing), label = "mini player container transform")
    if ((visible || progress > 0f) && track != null) {
        val density = LocalDensity.current
        val shape = remember(progress, bounds, density) { PlayerTransformShape(progress, bounds, with(density) { 8.dp.toPx() }) }
        Box(Modifier.fillMaxSize().graphicsLayer { this.shape = shape; clip = true }.background(Black).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
            Box(Modifier.fillMaxSize().graphicsLayer {
                translationY = (bounds?.top ?: 0f) * (1 - progress)
                alpha = ((progress - .15f) / .5f).coerceIn(0f, 1f)
            }) { content() }
            if (progress < .35f && bounds != null) with(density) {
                Row(Modifier.offset { IntOffset((bounds.left * (1 - progress)).toInt(), (bounds.top * (1 - progress)).toInt()) }.width(bounds.width.toDp()).height(bounds.height.toDp()).graphicsLayer { alpha = (1 - progress / .35f).coerceIn(0f, 1f) }.background(Card).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SArt(track.albumArtUrl, Modifier.size(44.dp))
                    Text(track.title, modifier = Modifier.padding(start = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
