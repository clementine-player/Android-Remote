package de.qspool.clementineremote.ui.hints

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import androidx.core.content.edit
import de.qspool.clementineremote.App
import de.qspool.clementineremote.R

/**
 * A feature that isn't obvious, pointed out once: the first time its button shows, a hint next to
 * it says what it does. Each is its own preference, so a new hint shows to everyone once.
 */
enum class Hint(val key: String) {
    /** The button that chooses where Clementine plays (remote streaming). */
    OUTPUTS("hint_outputs"),
}

/** Which hints have been seen, and the one showing: only one at a time. */
object Hints {

    // Mirrors the preferences, so a hint closes wherever it's marked seen.
    private val seen = mutableStateMapOf<Hint, Boolean>()

    /** The hint showing now, if any. */
    internal var showing by mutableStateOf<Hint?>(null)

    fun isSeen(hint: Hint): Boolean =
        seen.getOrPut(hint) { App.getPreferences().getBoolean(hint.key, false) }

    /** Won't show [hint] again: its feature was used, or the hint was closed. */
    fun seen(hint: Hint) {
        App.getPreferences().edit { putBoolean(hint.key, true) }
        seen[hint] = true
        if (showing == hint) showing = null
    }

    /** Shows every hint again, from the settings. */
    @JvmStatic
    fun reset() {
        App.getPreferences().edit { Hint.entries.forEach { remove(it.key) } }
        seen.clear()
        showing = null
    }

    /** Won't show any hint, for the store screenshots. */
    @JvmStatic
    fun seenAll() = Hint.entries.forEach(::seen)
}

/**
 * [content], with [hint] pointing at it the first time it shows, unless another hint is showing:
 * a title (with [content]'s icon), what it does, and "Got it", in inverted colours and pointing at
 * [content] from above it. The hint closes, for good, on "Got it", on a tap elsewhere,
 * or when the feature is used (which should call [Hints.seen]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HintBox(
    hint: Hint,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    /** The icon of what the hint points at, beside its title. */
    @DrawableRes icon: Int? = null,
    /** False while [content] is covered, by a sheet or a screen over it: the hint waits. */
    enabled: Boolean = true,
    above: Boolean = true,
    content: @Composable () -> Unit,
) {
    val state = rememberTooltipState(isPersistent = true)
    val seen = Hints.isSeen(hint)
    val free = Hints.showing == null || Hints.showing == hint
    val show = enabled && !seen && free
    LaunchedEffect(show) {
        if (show) {
            Hints.showing = hint
            state.show()
        } else {
            state.dismiss()
            if (Hints.showing == hint) Hints.showing = null
        }
    }
    DisposableEffect(hint) {
        onDispose { if (Hints.showing == hint) Hints.showing = null }
    }
    val position = rememberHintPosition(above)
    // Inverted, so the hint stands out from whatever it's over.
    val colors = TooltipDefaults.richTooltipColors(
        containerColor = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        titleContentColor = MaterialTheme.colorScheme.inverseOnSurface,
        actionContentColor = MaterialTheme.colorScheme.inversePrimary,
    )
    TooltipBox(
        positionProvider = position,
        tooltip = {
            Column(Modifier.testTag("hint_" + hint.name.lowercase())) {
                if (position.under) Caret(position, colors.containerColor, up = true)
                RichTooltip(
                    title = {
                        if (icon == null) {
                            Text(title)
                        } else {
                            // The button's icon, to tie the hint to it.
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(painterResource(icon), null, Modifier.size(20.dp))
                                Text(title)
                            }
                        }
                    },
                    action = {
                        TextButton(onClick = { Hints.seen(hint) }, modifier = Modifier.testTag("hintDone")) {
                            Text(stringResource(R.string.hint_done))
                        }
                    },
                    colors = colors,
                ) { Text(text) }
                if (!position.under) Caret(position, colors.containerColor, up = false)
            }
        },
        state = state,
        onDismissRequest = { Hints.seen(hint) },
        // Long-pressing the anchor shouldn't bring the hint back.
        enableUserInput = false,
        modifier = modifier,
        content = content,
    )
}

/**
 * Where a hint goes: above (or below) the anchor, centred on it but kept inside the window.
 * Material's tooltip positions line a wide hint up with the anchor's edge, which can push it off
 * screen, and its caret doesn't follow a position of our own; so this also says where the
 * caret goes.
 */
private class HintPosition(private val above: Boolean, private val spacing: Int, private val margin: Int) :
    PopupPositionProvider {

    /** The middle of the anchor, from the hint's left edge. */
    var caretX by mutableIntStateOf(0)
        private set

    /** Whether the hint is under the anchor: there was no room on the side asked for. */
    var under by mutableStateOf(!above)
        private set

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = (anchorBounds.center.x - popupContentSize.width / 2)
            .coerceAtMost(windowSize.width - margin - popupContentSize.width)
            .coerceAtLeast(margin)
        val over = anchorBounds.top - spacing - popupContentSize.height
        val below = anchorBounds.bottom + spacing
        under = if (above) over < 0 else below + popupContentSize.height <= windowSize.height
        caretX = anchorBounds.center.x - x
        return IntOffset(x, if (under) below else over)
    }
}

@Composable
private fun rememberHintPosition(above: Boolean): HintPosition {
    val density = LocalDensity.current
    val spacing = with(density) { 2.dp.roundToPx() }
    val margin = with(density) { 8.dp.roundToPx() }
    return remember(above, spacing, margin) { HintPosition(above, spacing, margin) }
}

/** The hint's point, at the middle of its anchor. */
@Composable
private fun Caret(position: HintPosition, color: Color, up: Boolean) {
    Canvas(Modifier.fillMaxWidth().height(CARET_HEIGHT)) {
        val half = CARET_WIDTH.toPx() / 2
        // Not past the hint's rounded corners.
        val x = position.caretX.toFloat().coerceIn(half + 12.dp.toPx(), size.width - half - 12.dp.toPx())
        val tip = if (up) 0f else size.height
        val base = if (up) size.height else 0f
        drawPath(
            Path().apply {
                moveTo(x - half, base)
                lineTo(x, tip)
                lineTo(x + half, base)
                close()
            },
            color,
        )
    }
}

private val CARET_WIDTH = 20.dp
private val CARET_HEIGHT = 10.dp
