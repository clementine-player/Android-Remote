package de.qspool.clementineremote.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.qspool.clementineremote.R
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.SyncSourceKind
import de.qspool.clementineremote.backend.sync.KeptRule
import de.qspool.clementineremote.backend.sync.RefreshPolicy
import de.qspool.clementineremote.backend.sync.RuleResult
import de.qspool.clementineremote.utils.Utilities

/** What's kept on this phone, for the downloads screen. */
data class Kept(
    val rules: List<KeptRule> = emptyList(),
    /** Whether a sync is running. */
    val running: Boolean = false,
    /** Whether the Clementine connected syncs, so Sync now can. */
    val canSync: Boolean = false,
)

/**
 * The rules kept on this phone, each saying how its last sync went, with a menu to sync now and to
 * stop keeping it, which asks first. Tapping one opens its songs ([onOpen]). Rows are tagged
 * "kept" and the rule's id.
 */
internal fun LazyListScope.keptOnPhone(
    kept: Kept,
    onSyncNow: () -> Unit,
    onRemove: (KeptRule) -> Unit,
    onOpen: (KeptRule) -> Unit = {},
) {
    if (kept.rules.isEmpty()) {
        return
    }
    item {
        Text(
            stringResource(R.string.sync_kept),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
        )
    }
    items(kept.rules, key = { "kept/${it.rule.id}" }) { rule ->
        KeptRow(rule, kept.running, kept.canSync, onSyncNow, onRemove, onOpen)
    }
}

@Composable
private fun KeptRow(
    kept: KeptRule,
    running: Boolean,
    canSync: Boolean,
    onSyncNow: () -> Unit,
    onRemove: (KeptRule) -> Unit,
    onOpen: (KeptRule) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    val title = kept.rule.title
    ListItem(
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                keptDetails(kept, running),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (kept.result is RuleResult.Synced || kept.result == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        },
        leadingContent = {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(kindIcon(kept.rule.kind)), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.testTag("keptMenu${kept.rule.id}")) {
                    Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.shell_more))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (canSync) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sync_now)) },
                            onClick = {
                                menu = false
                                onSyncNow()
                            },
                            modifier = Modifier.testTag("keptSyncNow"),
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sync_remove)) },
                        onClick = {
                            menu = false
                            confirming = true
                        },
                        modifier = Modifier.testTag("keptRemove"),
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onOpen(kept) }.testTag("kept${kept.rule.id}"),
    )

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            text = { Text(stringResource(R.string.sync_remove_confirm, title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        onRemove(kept)
                    },
                    modifier = Modifier.testTag("keptRemoveConfirm"),
                ) { Text(stringResource(R.string.sync_remove_button)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.dialog_cancel)) }
            },
        )
    }
}

/** How a rule stands: why it didn't sync, or what it keeps. */
@Composable
internal fun keptDetails(kept: KeptRule, running: Boolean): String = when (val result = kept.result) {
    is RuleResult.NeedsSetup -> result.message
    RuleResult.Gone -> stringResource(R.string.sync_gone)
    is RuleResult.Failed -> stringResource(R.string.sync_failed)
    else -> when {
        kept.songs == 0 && result == null && running -> stringResource(R.string.sync_preparing)
        kept.songs == 0 && result == null -> stringResource(R.string.sync_waiting)
        else -> listOf(
            stringResource(
                if (kept.rule.refreshPolicy == RefreshPolicy.NEVER) R.string.sync_kept_as_it_is else R.string.sync_every_connect
            ),
            pluralStringResource(R.plurals.sync_songs_size, kept.songs, kept.songs, Utilities.humanReadableBytes(kept.bytes, true)),
        ).joinToString(" · ")
    }
}

private fun kindIcon(kind: SyncSourceKind): Int = when (kind) {
    SyncSourceKind.SYNC_SOURCE_KIND_ALBUM -> R.drawable.ic_album
    SyncSourceKind.SYNC_SOURCE_KIND_ARTIST -> R.drawable.ic_person
    SyncSourceKind.SYNC_SOURCE_KIND_PLAYLIST, SyncSourceKind.SYNC_SOURCE_KIND_SMART_PLAYLIST -> R.drawable.ic_queue_music
    SyncSourceKind.SYNC_SOURCE_KIND_UNSPECIFIED, SyncSourceKind.SYNC_SOURCE_KIND_SONG,
    SyncSourceKind.SYNC_SOURCE_KIND_INTERNET_NODE -> R.drawable.ic_music_note
}
