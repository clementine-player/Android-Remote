package de.qspool.clementineremote.ui.player

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import de.qspool.clementineremote.R
import de.qspool.clementineremote.backend.RemoteRepository.NowPlaying
import de.qspool.clementineremote.backend.sync.PlayFrom
import de.qspool.clementineremote.ui.shell.isTablet

/**
 * Where the app plays from, in a sheet the player's header and the mini player open: Clementine,
 * or songs kept on this phone. Picking one pauses the other.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun PlayFromSheet(choices: PlayFromChoices, host: String?, onPlayFrom: (PlayFrom) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // The sheet is a window of its own: its test tags are resource IDs too, for UI Automator.
        modifier = Modifier.semantics { testTagsAsResourceId = true },
    ) {
        PlayFromSheetContent(choices, host, onPlayFrom = {
            onPlayFrom(it)
            onDismiss()
        })
    }
}

@Composable
internal fun PlayFromSheetContent(
    choices: PlayFromChoices,
    host: String?,
    onPlayFrom: (PlayFrom) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Text(
            stringResource(R.string.sync_play_from),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp).semantics { heading() },
        )
        PlayFromRow(
            PlayFrom.CLEMENTINE,
            if (host.isNullOrBlank()) {
                stringResource(R.string.fragment_title_connection)
            } else {
                stringResource(R.string.navigation_drawer_clementine_on, host)
            },
            R.drawable.ic_computer,
            choices.clementine,
            choices.playFrom,
            onPlayFrom,
        )
        val tablet = isTablet()
        PlayFromRow(
            PlayFrom.PHONE,
            stringResource(if (tablet) R.string.sync_on_this_tablet else R.string.sync_on_this_phone),
            if (tablet) R.drawable.ic_tablet else R.drawable.ic_smartphone,
            choices.phone,
            choices.playFrom,
            onPlayFrom,
        )
        Text(
            stringResource(R.string.sync_play_from_pauses),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp),
        )
    }
}

/** One place to play from, with whether it plays or is paused, and its song. */
@Composable
private fun PlayFromRow(
    place: PlayFrom,
    name: String,
    @DrawableRes icon: Int,
    nowPlaying: NowPlaying,
    playFrom: PlayFrom,
    onPlayFrom: (PlayFrom) -> Unit,
) {
    val title = nowPlaying.song?.title?.takeIf { it.isNotBlank() }
    ListItem(
        headlineContent = { Text(name) },
        supportingContent = title?.let {
            {
                Text(stringResource(if (nowPlaying.isPlaying) R.string.sync_play_from_playing else R.string.sync_play_from_paused, it))
            }
        },
        leadingContent = { Icon(painterResource(icon), null) },
        // The row is the radio button, so it's read once.
        trailingContent = { RadioButton(selected = place == playFrom, onClick = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .selectable(selected = place == playFrom, role = Role.RadioButton) { onPlayFrom(place) }
            .testTag("playFrom_" + place.name.lowercase()),
    )
}
