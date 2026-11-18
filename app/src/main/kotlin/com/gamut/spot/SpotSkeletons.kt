package com.gamut.spot

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

@Composable internal fun SkeletonPulse(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val alpha by rememberInfiniteTransition(label = "loading skeleton").animateFloat(.45f, .85f, infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "pulse")
    Box(modifier.graphicsLayer { this.alpha = alpha }.semantics { contentDescription = "Loading content" }) { content() }
}
@Composable internal fun WordsSkeleton() = SkeletonPulse {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(5) { index -> Box(Modifier.fillMaxWidth(if (index % 2 == 0) .9f else .65f).height(20.dp).background(Color.White.copy(alpha = .2f), RoundedCornerShape(4.dp))) }
    }
}
@Composable internal fun RailSkeleton() = SkeletonPulse(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(3) { Column(Modifier.width(152.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(152.dp).background(Card, RoundedCornerShape(4.dp)))
            Box(Modifier.width(116.dp).height(14.dp).background(Card, RoundedCornerShape(3.dp)))
        } }
    }
}
