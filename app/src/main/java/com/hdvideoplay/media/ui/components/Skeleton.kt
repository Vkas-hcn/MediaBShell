package com.hdvideoplay.media.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hdvideoplay.media.ui.theme.VideoPlayTokens

/** Shared animation durations for skeleton placeholders and content swaps. */
object SkeletonMotion {
    /** Fade used when a skeleton is replaced by real content (and vice versa). */
    const val FadeMs = 220

    /** One full shimmer sweep. */
    const val ShimmerMs = 1200

    /** Coil crossfade duration for posters / avatars. */
    const val ImageFadeMs = 220
}

/**
 * Draws the animated skeleton shimmer inside this modifier's bounds.
 *
 * The animated phase is read inside drawWithCache so only the draw pass is
 * invalidated each frame — it does not recompose the surrounding screen.
 */
@Composable
fun Modifier.shimmer(shape: Shape = RectangleShape): Modifier {
    val transition = rememberInfiniteTransition(label = "skeletonShimmer")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = SkeletonMotion.ShimmerMs,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "skeletonShimmerPhase",
    )
    return this
        .clip(shape)
        .clearAndSetSemantics { }
        .drawWithCache {
            val travel = size.width * 2f
            val startX = -size.width + phase.value * travel
            val brush = Brush.linearGradient(
                colors = listOf(
                    VideoPlayTokens.SkeletonBase,
                    VideoPlayTokens.SkeletonHighlight,
                    VideoPlayTokens.SkeletonBase,
                ),
                start = Offset(startX, 0f),
                end = Offset(startX + size.width, 0f),
            )
            onDrawBehind {
                drawRect(brush = brush)
            }
        }
}

/** Single shimmering bar. Width is either fixed or a fraction of the parent. */
@Composable
fun SkeletonBar(
    height: Dp,
    modifier: Modifier = Modifier,
    width: Dp? = null,
    widthFraction: Float = 1f,
    shape: Shape = RoundedCornerShape(4.dp),
) {
    val widthModifier = if (width != null) {
        Modifier.width(width)
    } else {
        Modifier.fillMaxWidth(widthFraction)
    }
    Box(
        modifier = modifier
            .then(widthModifier)
            .height(height)
            .shimmer(shape),
    )
}

/** Multi-line text skeleton (the last line is shorter by default). */
@Composable
fun SkeletonTextLines(
    lineCount: Int,
    modifier: Modifier = Modifier,
    lineHeight: Dp = 12.dp,
    lineSpacing: Dp = 6.dp,
    widths: List<Float> = List(lineCount) { if (it == lineCount - 1) 0.62f else 1f },
    shape: Shape = RoundedCornerShape(4.dp),
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(lineSpacing),
    ) {
        repeat(lineCount.coerceAtLeast(0)) { index ->
            SkeletonBar(
                height = lineHeight,
                widthFraction = widths.getOrElse(index) { 1f },
                shape = shape,
            )
        }
    }
}

/** Circular avatar skeleton. */
@Composable
fun SkeletonCircle(
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .shimmer(CircleShape),
    )
}

/**
 * Crossfades a skeleton placeholder into real content without changing the
 * slot's external size. Callers normally pass a fixed size in [modifier].
 *
 * Both layers are measured by the Box; when the outer modifier fixes the
 * height, the slot is fully stable. Without a fixed height the Box uses the
 * larger of the two layers, which keeps the default overview slot stable while
 * still allowing a user-expanded overview to grow.
 */
@Composable
fun SkeletonSlot(
    loading: Boolean,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.TopStart,
    skeleton: @Composable BoxScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val contentAlpha by animateFloatAsState(
        targetValue = if (loading) 0f else 1f,
        animationSpec = tween(
            durationMillis = SkeletonMotion.FadeMs,
            easing = FastOutSlowInEasing,
        ),
        label = "skeletonSlotAlpha",
    )
    Box(
        modifier = modifier,
        contentAlignment = alignment,
    ) {
        if (contentAlpha < 1f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = 1f - contentAlpha },
                contentAlignment = alignment,
                content = skeleton,
            )
        }
        if (contentAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = contentAlpha },
                contentAlignment = alignment,
                content = content,
            )
        }
    }
}
