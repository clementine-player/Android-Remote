package de.qspool.clementineremote.ui.shell

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.qspool.clementineremote.R

/** What to download from the player, and what the choice is called. */
enum class DownloadWhat(val tag: String, @StringRes val label: Int) {
    SONG("downloadSong", R.string.player_download_song),
    ALBUM("downloadAlbum", R.string.player_download_album),
    PLAYLIST("downloadPlaylist", R.string.player_download_playlist),
}

/** Asks whether to download the song playing, its album or its playlist. */
@Composable
internal fun DownloadChooser(onChoose: (DownloadWhat) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_download_what)) },
        text = {
            Column {
                DownloadWhat.entries.forEach { what ->
                    Text(
                        stringResource(what.label),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth()
                            .clickable(role = Role.Button) { onChoose(what) }
                            .padding(vertical = 14.dp)
                            .testTag(what.tag),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_close)) }
        },
    )
}
