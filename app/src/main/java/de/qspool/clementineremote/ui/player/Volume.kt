package de.qspool.clementineremote.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import de.qspool.clementineremote.R
import kotlin.math.roundToInt

/**
 * Clementine's volume: a button showing it, which pops up a vertical slider to set it. Upright,
 * the slider can't be taken for the seek bar; the phone's volume buttons set it too.
 */
@Composable
internal fun VolumeButton(volume: Int, onVolume: (Int) -> Unit, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    // What was last asked of Clementine, shown until it answers with its volume.
    var asked by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(volume) { asked = null }
    val shown = asked ?: volume
    fun set(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        if (clamped != shown) {
            asked = clamped
            onVolume(clamped)
        }
    }

    val description = stringResource(R.string.player_volume_level, shown)
    Box(modifier) {
        Surface(
            onClick = { open = true },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.height(40.dp)
                .testTag("btnVolume")
                .clearAndSetSemantics { contentDescription = description },
        ) {
            Row(
                Modifier.padding(start = 12.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_volume_up), contentDescription = null, modifier = Modifier.size(20.dp))
                Text("$shown%", style = MaterialTheme.typography.labelLarge)
            }
        }
        if (open) {
            VolumePanel(shown, ::set, onDismiss = { open = false })
        }
    }
}

/** The volume panel, popped up just above the button. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun VolumePanel(volume: Int, onVolume: (Int) -> Unit, onDismiss: () -> Unit) {
    val above = with(LocalDensity.current) { 48.dp.roundToPx() }
    Popup(
        alignment = Alignment.BottomCenter,
        offset = IntOffset(0, -above),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        // The popup is a window of its own: its test tags are resource IDs too, for UI Automator.
        VolumePanelContent(volume, onVolume, Modifier.semantics { testTagsAsResourceId = true })
    }
}

/** The panel itself: the level, and a vertical slider between + and −. */
@Composable
internal fun VolumePanelContent(volume: Int, onVolume: (Int) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        modifier = modifier.testTag("volumePanel"),
    ) {
        Column(
            Modifier.width(72.dp).padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "$volume%",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("volumeLevel"),
            )
            IconButton(onClick = { onVolume(volume + STEP) }, modifier = Modifier.testTag("btnVolumeUp")) {
                Icon(painterResource(R.drawable.ic_add), stringResource(R.string.player_volume_up))
            }
            VerticalSlider(volume, onVolume)
            IconButton(onClick = { onVolume(volume - STEP) }, modifier = Modifier.testTag("btnVolumeDown")) {
                Icon(painterResource(R.drawable.ic_remove), stringResource(R.string.player_volume_down))
            }
        }
    }
}

/** An upright slider from 0 (bottom) to 100 (top), filled up to [volume], as Android's volume panel is. */
@Composable
private fun VerticalSlider(volume: Int, onVolume: (Int) -> Unit) {
    val set by rememberUpdatedState(onVolume)
    val description = stringResource(R.string.connection_volume)
    val filled = volume / 100f
    Box(
        Modifier
            .size(width = 56.dp, height = 200.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .pointerInput(Unit) {
                fun at(y: Float) = ((1 - y / size.height) * 100).roundToInt()
                detectTapGestures { set(at(it.y)) }
            }
            .pointerInput(Unit) {
                fun at(y: Float) = ((1 - y / size.height) * 100).roundToInt()
                detectVerticalDragGestures { change, _ -> set(at(change.position.y)) }
            }
            .semantics {
                contentDescription = description
                stateDescription = "$volume%"
                progressBarRangeInfo = ProgressBarRangeInfo(volume.toFloat(), 0f..100f, steps = 0)
                setProgress { target ->
                    set(target.roundToInt())
                    true
                }
            }
            .testTag("volumeSlider"),
    ) {
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(filled)
                .background(MaterialTheme.colorScheme.primary),
        )
        Icon(
            painterResource(R.drawable.ic_volume_up),
            contentDescription = null,
            tint = if (filled > 0.2f) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
        )
    }
}

/** What + and − change the volume by, in percent. */
private const val STEP = 5
