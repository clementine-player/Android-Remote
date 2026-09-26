package de.qspool.clementineremote.ui.settings

import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.edit

/**
 * The app's preferences, read so that Compose shows their changes, whoever makes them. Keys and
 * defaults are the ones the rest of the app reads, from [de.qspool.clementineremote.SharedPreferencesKeys].
 */
internal class PreferenceStore(private val preferences: SharedPreferences) {

    /** Bumped on every change; reading it makes a reader recompose on the next. */
    private var changes by mutableIntStateOf(0)

    // Held here: SharedPreferences keeps only a weak reference to its listeners.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> changes++ }

    fun boolean(key: String, default: Boolean): Boolean {
        changes
        return preferences.getBoolean(key, default)
    }

    fun string(key: String, default: String): String {
        changes
        return preferences.getString(key, default) ?: default
    }

    fun set(key: String, value: Boolean) = preferences.edit { putBoolean(key, value) }

    fun set(key: String, value: String) = preferences.edit { putString(key, value) }

    fun listen() = preferences.registerOnSharedPreferenceChangeListener(listener)

    fun stopListening() = preferences.unregisterOnSharedPreferenceChangeListener(listener)
}

@Composable
internal fun rememberPreferenceStore(preferences: SharedPreferences): PreferenceStore {
    val store = remember(preferences) { PreferenceStore(preferences) }
    DisposableEffect(store) {
        store.listen()
        onDispose { store.stopListening() }
    }
    return store
}

/** A group's heading. */
@Composable
internal fun SettingsHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

/** A row that does something when tapped. */
@Composable
internal fun ActionSetting(
    title: String,
    summary: String?,
    tag: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    SettingRow(title, summary, Modifier.clickable(enabled = enabled, onClick = onClick).testTag(tag), enabled)
}

/** A row that turns a setting on and off. */
@Composable
internal fun SwitchSetting(
    title: String,
    summary: String?,
    tag: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingRow(
        title,
        summary,
        Modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .testTag(tag),
        enabled,
    ) {
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * A row that picks one of [labels], shown in a dialog; [values] are what's stored for them.
 */
@Composable
internal fun ChoiceSetting(
    title: String,
    summary: String?,
    tag: String,
    labels: List<String>,
    values: List<String>,
    value: String,
    enabled: Boolean = true,
    onChoose: (String) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    ActionSetting(title, summary, tag, enabled) { open = true }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                    labels.zip(values).forEach { (label, choice) ->
                        Row(
                            Modifier.fillMaxWidth()
                                .selectable(choice == value, role = Role.RadioButton) {
                                    open = false
                                    onChoose(choice)
                                }
                                .padding(vertical = 12.dp)
                                .testTag("${tag}_$choice"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = choice == value, onClick = null)
                            Text(label, Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { open = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    summary: String?,
    modifier: Modifier,
    enabled: Boolean,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.alpha(if (enabled) 1f else DISABLED_ALPHA),
    )
}

/** How faint a row is while it can't be changed, as Material's disabled content is. */
private const val DISABLED_ALPHA = 0.38f
