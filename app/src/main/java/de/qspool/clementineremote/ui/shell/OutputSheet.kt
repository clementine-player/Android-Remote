package de.qspool.clementineremote.ui.shell

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import de.qspool.clementineremote.R
import de.qspool.clementineremote.backend.RemoteRepository
import de.qspool.clementineremote.ui.hints.Hint
import de.qspool.clementineremote.ui.hints.Hints

/** Whether there's anywhere to play but Clementine's computer, so the output button shows. */
internal val RemoteRepository.Outputs.switchable: Boolean
    get() = supported && outputs.size > 1

/**
 * Chooses where Clementine plays: shows where it plays now, the device's icon when it isn't
 * Clementine's own computer, and opens the output sheet.
 */
@Composable
internal fun OutputButton(outputs: RemoteRepository.Outputs, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val active = outputs.active?.takeIf { it.id != RemoteRepository.LOCAL_OUTPUT }
    IconButton(
        onClick = {
            // Found it: its hint has done its job.
            Hints.seen(Hint.OUTPUTS)
            onClick()
        },
        modifier = modifier.testTag("btnOutputs"),
    ) {
        Icon(
            painterResource(if (active == null) R.drawable.ic_devices else outputIcon(active)),
            if (active == null) {
                stringResource(R.string.output_choose)
            } else {
                stringResource(R.string.output_playing_on, active.name)
            },
            tint = if (active == null) LocalContentColor.current else MaterialTheme.colorScheme.primary,
        )
    }
}

/** Where Clementine can play, in a sheet the output button opens; picking one plays there. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun OutputSheet(outputs: RemoteRepository.Outputs, onOutput: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // The sheet is a window of its own: its test tags are resource IDs too, for UI Automator.
        modifier = Modifier.semantics { testTagsAsResourceId = true },
    ) {
        OutputSheetContent(outputs, onOutput = {
            onOutput(it)
            onDismiss()
        })
    }
}

/**
 * Where Clementine plays: its own computer, by the name Clementine gives it, this phone, or another
 * device (remote streaming).
 */
@Composable
internal fun OutputSheetContent(outputs: RemoteRepository.Outputs, onOutput: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Text(
            stringResource(R.string.output_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp).semantics { heading() },
        )
        for (output in outputs.outputs) {
            val name = when {
                output.id == RemoteRepository.LOCAL_OUTPUT ->
                    output.name.ifBlank { stringResource(R.string.fragment_title_connection) }
                output.isThisPhone -> stringResource(
                    if (isTablet()) R.string.output_this_tablet else R.string.output_this_phone,
                    output.name,
                )
                else -> output.name
            }
            ListItem(
                headlineContent = { Text(name) },
                supportingContent = if (output.activating) {
                    { Text(stringResource(R.string.output_switching)) }
                } else {
                    null
                },
                leadingContent = { Icon(painterResource(outputIcon(output)), null) },
                // The row is the radio button, so it's read once.
                trailingContent = { RadioButton(selected = output.active, onClick = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier
                    .selectable(selected = output.active, role = Role.RadioButton) { onOutput(output.id) }
                    .testTag("output_" + output.id),
            )
        }
    }
}

/**
 * What an output is, as an icon: Clementine's computer, this phone or tablet, or another device.
 * Clementine doesn't say what kind of device the others are, so they're shown as speakers.
 */
@DrawableRes
@Composable
internal fun outputIcon(output: RemoteRepository.Output): Int = when {
    output.id == RemoteRepository.LOCAL_OUTPUT -> R.drawable.ic_computer
    output.isThisPhone -> if (isTablet()) R.drawable.ic_tablet else R.drawable.ic_smartphone
    else -> R.drawable.ic_speaker
}

/** Whether this device is a tablet: at least 600dp wide whichever way it's held. */
@Composable
private fun isTablet(): Boolean = LocalConfiguration.current.smallestScreenWidthDp >= 600
