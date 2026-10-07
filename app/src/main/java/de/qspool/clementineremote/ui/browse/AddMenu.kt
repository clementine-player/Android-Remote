package de.qspool.clementineremote.ui.browse

import androidx.annotation.StringRes
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import de.qspool.clementineremote.R
import de.qspool.clementineremote.backend.AddAction

/**
 * What a long press on an item of the library or search results offers, as Clementine's own
 * library does: the ways to put its songs on the playlist, and with [onSelect], selecting it to
 * act on several items at once. Play next shows only if Clementine [canPlayNext]. Its items are
 * tagged "addMenu_" and the action's name, or "addMenu_SELECT".
 */
@Composable
internal fun AddMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    canPlayNext: Boolean,
    onAdd: (AddAction) -> Unit,
    onSelect: (() -> Unit)? = null,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        @Composable
        fun item(@StringRes label: Int, tag: String, onClick: () -> Unit) = DropdownMenuItem(
            text = { Text(stringResource(label)) },
            onClick = {
                onDismiss()
                onClick()
            },
            modifier = Modifier.testTag("addMenu_$tag"),
        )

        @Composable
        fun add(@StringRes label: Int, action: AddAction) = item(label, action.name) { onAdd(action) }

        add(R.string.internet_play_now, AddAction.PLAY_NOW)
        if (canPlayNext) {
            add(R.string.internet_play_next, AddAction.PLAY_NEXT)
        }
        add(R.string.library_add_to_queue, AddAction.QUEUE)
        HorizontalDivider()
        add(R.string.library_add_to_playlist, AddAction.APPEND)
        add(R.string.internet_replace_playlist, AddAction.REPLACE)
        add(R.string.library_open_in_new_playlist, AddAction.NEW_PLAYLIST)
        if (onSelect != null) {
            HorizontalDivider()
            item(R.string.library_select, "SELECT", onSelect)
        }
    }
}
