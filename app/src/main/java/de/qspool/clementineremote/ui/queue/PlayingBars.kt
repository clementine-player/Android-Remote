package de.qspool.clementineremote.ui.queue

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * The now-playing mark: three bars that rise and fall while the song plays, and stand still,
 * at the heights of the equalizer icon, while it's paused.
 */
@Composable
internal fun PlayingBars(playing: Boolean, color: Color, description: String, modifier: Modifier = Modifier) {
    // Out of step with each other, so the bars look like a level meter rather than a wave.
    val heights = if (playing) {
        val transition = rememberInfiniteTransition(label = "playingBars")
        listOf(
            transition.animateFloat(0.3f, 1f, bounce(430), label = "bar1"),
            transition.animateFloat(1f, 0.25f, bounce(520), label = "bar2"),
            transition.animateFloat(0.5f, 0.9f, bounce(370), label = "bar3"),
        )
    } else {
        remember { STILL.map { mutableFloat(it) } }
    }
    Canvas(modifier.semantics { contentDescription = description }) {
        // Three bars of equal width with a bar's gap between them, standing on the bottom.
        val bar = size.width / 5
        heights.forEachIndexed { index, height ->
            val barHeight = size.height * height.value
            drawRoundRect(
                color,
                topLeft = Offset(bar * 2 * index, size.height - barHeight),
                size = Size(bar, barHeight),
                cornerRadius = CornerRadius(bar / 4),
            )
        }
    }
}

/** The equalizer icon's bar heights. */
private val STILL = listOf(0.5f, 1f, 0.7f)

private fun bounce(millis: Int): InfiniteRepeatableSpec<Float> =
    infiniteRepeatable(tween(millis, easing = LinearEasing), RepeatMode.Reverse)

private fun mutableFloat(value: Float): State<Float> = object : State<Float> {
    override val value = value
}
