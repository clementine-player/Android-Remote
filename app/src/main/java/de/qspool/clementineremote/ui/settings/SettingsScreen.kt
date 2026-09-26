package de.qspool.clementineremote.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import de.qspool.clementineremote.R
import de.qspool.clementineremote.SharedPreferencesKeys
import de.qspool.clementineremote.backend.Clementine
import de.qspool.clementineremote.backend.downloader.MediaStoreDownloadStorage

/** What the settings do outside themselves. */
interface SettingsActions {
    fun onBack()

    fun onOpenUrl(url: String)
}

/** The settings, on one page: a group for each part of the app, then about and licences. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(store: PreferenceStore, actions: SettingsActions, defaultDownloadDir: () -> String) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.menu_settings)) },
                navigationIcon = {
                    IconButton(onClick = actions::onBack, modifier = Modifier.testTag("btnSettingsBack")) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.library_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .testTag("settingsList"),
        ) {
            PlayerSettings(store)
            LibrarySettings(store)
            DownloadSettings(store, defaultDownloadDir)
            ConnectionSettings(store)
            AdvancedSettings(store)
            AboutSettings(actions)
        }
    }
}

@Composable
private fun PlayerSettings(store: PreferenceStore) {
    SettingsHeading(stringResource(R.string.pref_cat_player))
    BooleanSetting(store, SharedPreferencesKeys.SP_KEY_USE_VOLUMEKEYS, true, R.string.pref_volume_title, R.string.pref_volume_summary)
    BooleanSetting(store, SharedPreferencesKeys.SP_SHOW_TRACKNO, true, R.string.pref_trackno_title, R.string.pref_trackno_summary)
    BooleanSetting(store, SharedPreferencesKeys.SP_LASTFM, true, R.string.pref_lastfm_title, R.string.pref_lastfm_summary)
    val lowerVolume = BooleanSetting(
        store, SharedPreferencesKeys.SP_LOWER_VOLUME, true, R.string.pref_lower_volume_title, R.string.pref_lower_volume_summary,
    )

    // The volume during calls, or a pause.
    val callVolumes = stringArrayResource(R.array.pref_volume_values).toList()
    val callVolumeLabels = callVolumes.map { if (it == PAUSE) stringResource(R.string.tasker_pause) else "$it%" }
    StringChoiceSetting(
        store, SharedPreferencesKeys.SP_CALL_VOLUME, DEFAULT_CALL_VOLUME, R.string.pref_call_volume_title,
        R.string.pref_call_volume_summary, callVolumeLabels, callVolumes, enabled = lowerVolume,
    )

    val steps = stringArrayResource(R.array.pref_volume_inc_values).toList()
    StringChoiceSetting(
        store, SharedPreferencesKeys.SP_VOLUME_INC, Clementine.DefaultVolumeInc, R.string.pref_volume_inc_title,
        R.string.pref_volume_inc_summary, steps.map { "$it%" }, steps,
    )
}

@Composable
private fun LibrarySettings(store: PreferenceStore) {
    SettingsHeading(stringResource(R.string.pref_cat_library))
    StringChoiceSetting(
        store, SharedPreferencesKeys.SP_LIBRARY_GROUPING, "artist-album", R.string.pref_library_grouping_title,
        R.string.pref_library_grouping_summary, stringArrayResource(R.array.pref_library_grouping).toList(),
        stringArrayResource(R.array.pref_library_grouping_values).toList(),
    )
    StringChoiceSetting(
        store, SharedPreferencesKeys.SP_LIBRARY_SORTING, "ASC", R.string.pref_library_sorting_title,
        R.string.pref_library_sorting_summary, stringArrayResource(R.array.pref_library_sorting).toList(),
        stringArrayResource(R.array.pref_library_sorting_values).toList(),
    )
}

@Composable
private fun DownloadSettings(store: PreferenceStore, defaultDownloadDir: () -> String) {
    SettingsHeading(stringResource(R.string.pref_cat_downloads))
    BooleanSetting(store, SharedPreferencesKeys.SP_WIFI_ONLY, false, R.string.pref_dl_wifi_only_title, R.string.pref_dl_wifi_only_summary)
    // Android 10 and later save songs to the shared Music collection.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ActionSetting(
            stringResource(R.string.pref_dl_dir), MediaStoreDownloadStorage.BASE_DIR,
            SharedPreferencesKeys.SP_DOWNLOAD_DIR, enabled = false,
        ) {}
    } else {
        val folder = store.string(SharedPreferencesKeys.SP_DOWNLOAD_DIR, defaultDownloadDir())
        var choosing by rememberSaveable { mutableStateOf(false) }
        ActionSetting(stringResource(R.string.pref_dl_dir), folder, SharedPreferencesKeys.SP_DOWNLOAD_DIR) {
            choosing = true
        }
        if (choosing) {
            DownloadFolderDialog(folder, onDismiss = { choosing = false }) {
                choosing = false
                store.set(SharedPreferencesKeys.SP_DOWNLOAD_DIR, it)
            }
        }
    }
    BooleanSetting(store, SharedPreferencesKeys.SP_DOWNLOAD_OVERRIDE, false, R.string.pref_dl_override, null)

    SettingsHeading(stringResource(R.string.pref_dl_cat_folders))
    BooleanSetting(
        store, SharedPreferencesKeys.SP_DOWNLOAD_SAVE_OWN_DIR, false, R.string.pref_dl_pl_save_own_dir_title,
        R.string.pref_dl_pl_save_own_dir_summary,
    )
    val artistDir = BooleanSetting(
        store, SharedPreferencesKeys.SP_DOWNLOAD_PLAYLIST_CRT_ARTIST_DIR, true, R.string.pref_dl_pl_artist_dir_title,
        R.string.pref_dl_pl_artist_dir_summary,
    )
    BooleanSetting(
        store, SharedPreferencesKeys.SP_DOWNLOAD_PLAYLIST_CRT_ALBUM_DIR, true, R.string.pref_dl_pl_album_dir_title,
        R.string.pref_dl_pl_album_dir_summary, enabled = artistDir,
    )
}

@Composable
private fun ConnectionSettings(store: PreferenceStore) {
    SettingsHeading(stringResource(R.string.pref_cat_connection))
    BooleanSetting(store, SharedPreferencesKeys.SP_KEY_AC, false, R.string.pref_autoconnect_title, R.string.pref_autoconnect_summary)

    val port = store.string(SharedPreferencesKeys.SP_KEY_PORT, Clementine.DefaultPort.toString())
    var editing by rememberSaveable { mutableStateOf(false) }
    ActionSetting(
        stringResource(R.string.pref_port_title), stringResource(R.string.pref_port_summary) + " " + port,
        SharedPreferencesKeys.SP_KEY_PORT,
    ) { editing = true }
    if (editing) {
        PortDialog(port, onDismiss = { editing = false }) {
            editing = false
            store.set(SharedPreferencesKeys.SP_KEY_PORT, it)
        }
    }
}

/** Asks for Clementine's port, which has to be one a user's program can listen on. */
@Composable
private fun PortDialog(port: String, onDismiss: () -> Unit, onPort: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(port) }
    val valid = text.toIntOrNull() in 1024..65535
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pref_port_title)) },
        text = {
            OutlinedTextField(
                text,
                onValueChange = { text = it.filter(Char::isDigit).take(5) },
                singleLine = true,
                isError = !valid,
                supportingText = if (valid) null else ({ Text(stringResource(R.string.pref_port_error)) }),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.testTag("portField"),
            )
        },
        confirmButton = {
            TextButton(onClick = { onPort(text) }, enabled = valid, modifier = Modifier.testTag("btnPortOk")) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun AdvancedSettings(store: PreferenceStore) {
    SettingsHeading(stringResource(R.string.pref_cat_advanced))
    // On unless turned off, as the player and settings have always read it.
    BooleanSetting(store, SharedPreferencesKeys.SP_KEEP_SCREEN_ON, true, R.string.pref_keep_screen_on_title, R.string.pref_keep_screen_on_summary)
    BooleanSetting(store, SharedPreferencesKeys.SP_WAKE_LOCK, false, R.string.pref_wake_lock_title, R.string.pref_wake_lock_summary)
}

@Composable
private fun AboutSettings(actions: SettingsActions) {
    SettingsHeading(stringResource(R.string.pref_cat_about))
    val context = LocalContext.current
    val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
    ActionSetting(
        stringResource(R.string.pref_version_title) + " " + version, stringResource(R.string.pref_version_summary),
        "pref_version",
    ) { actions.onOpenUrl(PROJECT_URL) }
    ActionSetting(
        stringResource(R.string.pref_clementine_title), stringResource(R.string.pref_clementine_summary),
        "pref_clementine_website",
    ) { actions.onOpenUrl(CLEMENTINE_URL) }

    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    ActionSetting(stringResource(R.string.pref_about_title), stringResource(R.string.pref_about_summary), ABOUT) {
        dialog = ABOUT
    }
    ActionSetting(stringResource(R.string.pref_license_title), stringResource(R.string.pref_license_summary), LICENSE) {
        dialog = LICENSE
    }
    ActionSetting(stringResource(R.string.pref_opensource_title), stringResource(R.string.pref_opensource_summary), OPEN_SOURCE) {
        dialog = OPEN_SOURCE
    }
    val close = { dialog = null }
    when (dialog) {
        ABOUT -> AboutDialog(close)
        LICENSE -> LicenseDialog(close)
        OPEN_SOURCE -> OpenSourceDialog(close)
    }
}

/** A setting that's on or off; returns whether it's on. */
@Composable
private fun BooleanSetting(
    store: PreferenceStore,
    key: String,
    default: Boolean,
    title: Int,
    summary: Int?,
    enabled: Boolean = true,
): Boolean {
    val checked = store.boolean(key, default)
    SwitchSetting(stringResource(title), summary?.let { stringResource(it) }, key, checked, enabled) {
        store.set(key, it)
    }
    return checked
}

/** A setting picked from [labels]; [summary] shows the label of the one picked. */
@Composable
private fun StringChoiceSetting(
    store: PreferenceStore,
    key: String,
    default: String,
    title: Int,
    summary: Int,
    labels: List<String>,
    values: List<String>,
    enabled: Boolean = true,
) {
    val value = store.string(key, default)
    val label = labels.getOrNull(values.indexOf(value)) ?: value
    ChoiceSetting(
        stringResource(title),
        // The summaries are "%s" or "%1$s" ones, as ListPreference had them.
        stringResource(summary).replace("%1\$s", label).replace("%s", label),
        key, labels, values, value, enabled,
    ) { store.set(key, it) }
}

private const val PAUSE = "-1"
private const val DEFAULT_CALL_VOLUME = "20"
private const val PROJECT_URL = "https://github.com/clementine-player/Android-Remote"
private const val CLEMENTINE_URL = "https://www.clementine-player.org/"
private const val ABOUT = "pref_key_about"
private const val LICENSE = "pref_key_license"
private const val OPEN_SOURCE = "pref_key_opensource"

