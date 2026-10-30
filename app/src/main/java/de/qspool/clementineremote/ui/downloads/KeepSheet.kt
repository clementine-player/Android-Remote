package de.qspool.clementineremote.ui.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.qspool.clementineremote.R
import de.qspool.clementineremote.backend.sync.RefreshPolicy

/** The ways to keep something up to date that the Keep sheet offers, for now. */
private val CHOICES = listOf(RefreshPolicy.NEVER, RefreshPolicy.ON_CONNECT)

/**
 * Keep on this phone, in a sheet: how to keep [title] up to date, starting at [refreshPolicy],
 * then the button that keeps it ([onKeep]).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun KeepSheet(
    title: String,
    refreshPolicy: RefreshPolicy,
    onKeep: (RefreshPolicy) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // The sheet is a window of its own: its test tags are resource IDs too, for UI Automator.
        modifier = Modifier.semantics { testTagsAsResourceId = true },
    ) {
        KeepSheetContent(title, refreshPolicy, onKeep = {
            onKeep(it)
            onDismiss()
        })
    }
}

/** What the Keep sheet shows. The choices are tagged "keepRefresh" and the policy's key. */
@Composable
internal fun KeepSheetContent(
    title: String,
    refreshPolicy: RefreshPolicy,
    onKeep: (RefreshPolicy) -> Unit,
    modifier: Modifier = Modifier,
) {
    var chosen by remember(refreshPolicy) { mutableStateOf(refreshPolicy) }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Column(
            Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(R.string.sync_keep),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            stringResource(R.string.sync_update_it),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp).semantics { heading() },
        )
        Column(Modifier.selectableGroup()) {
            for (policy in CHOICES) {
                ListItem(
                    headlineContent = { Text(stringResource(choiceName(policy))) },
                    supportingContent = if (policy == RefreshPolicy.NEVER) {
                        { Text(stringResource(R.string.sync_refresh_never_detail)) }
                    } else {
                        null
                    },
                    // The row is the radio button, so it's read once.
                    leadingContent = { RadioButton(selected = chosen == policy, onClick = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .selectable(chosen == policy, role = Role.RadioButton) { chosen = policy }
                        .padding(horizontal = 8.dp)
                        .testTag("keepRefresh${policy.key}"),
                )
            }
        }
        Button(
            onClick = { onKeep(chosen) },
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp).testTag("keepConfirm"),
        ) { Text(stringResource(R.string.sync_keep)) }
    }
}

private fun choiceName(policy: RefreshPolicy): Int = when (policy) {
    RefreshPolicy.NEVER -> R.string.sync_refresh_never
    else -> R.string.sync_refresh_on_connect
}
