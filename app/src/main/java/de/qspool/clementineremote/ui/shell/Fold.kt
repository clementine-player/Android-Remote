package de.qspool.clementineremote.ui.shell

import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.separatingHorizontalHingeBounds
import androidx.compose.material3.adaptive.separatingVerticalHingeBounds
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/**
 * A fold that splits a layout in two: a foldable half-open like a book, or set down like a laptop.
 * Screens that lay out around it put things on either side of it rather than across it.
 */
internal sealed interface Fold {
    /** The space before the fold, the fold itself, then the rest. */
    val before: Dp
    val width: Dp

    /** Half-open like a book: [before] is the width on the layout's leading side. */
    data class Vertical(override val before: Dp, override val width: Dp) : Fold

    /** Set down like a laptop: [before] is the height above the fold. */
    data class Horizontal(override val before: Dp, override val width: Dp) : Fold
}

/**
 * The fold that crosses a layout at [bounds], in window pixels, if there is one. A foldable open
 * flat has no fold that splits anything, so it lays out like any other screen.
 */
internal fun foldAcross(posture: Posture, bounds: Rect, density: Density, direction: LayoutDirection): Fold? {
    val vertical = posture.separatingVerticalHingeBounds
        .firstOrNull { it.left > bounds.left && it.right < bounds.right }
    if (vertical != null) {
        val before = if (direction == LayoutDirection.Ltr) {
            vertical.left - bounds.left
        } else {
            bounds.right - vertical.right
        }
        return with(density) { Fold.Vertical(before.toDp(), vertical.width.toDp()) }
    }
    val horizontal = posture.separatingHorizontalHingeBounds
        .firstOrNull { it.top > bounds.top && it.bottom < bounds.bottom }
        ?: return null
    return with(density) { Fold.Horizontal((horizontal.top - bounds.top).toDp(), horizontal.height.toDp()) }
}

/**
 * The fold that crosses a layout at [bounds] (from `Modifier.onGloballyPositioned`), if there is
 * one. Folding doesn't move the layout, so this follows the posture rather than the layout.
 */
@Composable
internal fun foldAcross(bounds: Rect?): Fold? {
    val posture = currentWindowAdaptiveInfoV2().windowPosture
    return bounds?.let { foldAcross(posture, it, LocalDensity.current, LocalLayoutDirection.current) }
}
