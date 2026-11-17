package com.gamut.spot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** One canvas and center line for the track and thumb, with a full-height touch target. */
@Composable
internal fun SpotSeekBar(value: Float, onChange: (Float) -> Unit, onFinished: () -> Unit, onCancel: () -> Unit) {
    val current by rememberUpdatedState(value.coerceIn(0f, 1f))
    val change by rememberUpdatedState(onChange)
    val finish by rememberUpdatedState(onFinished)
    val cancel by rememberUpdatedState(onCancel)
    val radius = with(LocalDensity.current) { 5.dp.toPx() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    fun fraction(x: Float, width: Int): Float {
        val progress = ((x - radius) / (width - 2 * radius).coerceAtLeast(1f)).coerceIn(0f, 1f)
        return if (rtl) 1f - progress else progress
    }
    Canvas(Modifier.fillMaxWidth().height(48.dp)
        .semantics {
            contentDescription = "Playback position"
            progressBarRangeInfo = ProgressBarRangeInfo(current, 0f..1f)
            setProgress { change(it.coerceIn(0f, 1f)); finish(); true }
        }
        .onKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && event.key in listOf(Key.DirectionLeft, Key.DirectionRight)) {
                val step = if ((event.key == Key.DirectionRight) != rtl) .05f else -.05f
                change((current + step).coerceIn(0f, 1f)); finish(); true
            } else false
        }.focusable()
        .pointerInput(rtl, radius) { detectTapGestures { change(fraction(it.x, size.width)); finish() } }
        .pointerInput(rtl, radius) {
            detectHorizontalDragGestures(
                onDragStart = { change(fraction(it.x, size.width)) },
                onDragEnd = { finish() }, onDragCancel = { cancel() },
                onHorizontalDrag = { event, _ -> event.consume(); change(fraction(event.position.x, size.width)) },
            )
        }) {
        val y = size.height / 2
        val left = Offset(radius, y)
        val right = Offset(size.width - radius, y)
        val position = if (rtl) 1f - current else current
        val thumb = Offset(radius + (size.width - radius * 2) * position, y)
        drawLine(Color.White.copy(alpha = .25f), left, right, 3.dp.toPx(), StrokeCap.Round)
        drawLine(Color.White, if (rtl) right else left, thumb, 3.dp.toPx(), StrokeCap.Round)
        drawCircle(Color.White, radius, thumb)
    }
}
