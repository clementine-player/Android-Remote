package de.qspool.clementineremote.ui.settings

import android.content.Context
import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import de.qspool.clementineremote.R
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Where folders are read: off the main thread, except in tests. */
internal val LocalFolderDispatcher = staticCompositionLocalOf<CoroutineDispatcher> { Dispatchers.IO }

/**
 * Where to save downloads, before Android 10 (which saves them to the Music collection): one of
 * the app's own Music folders or the shared one, or another browsed to. Folders are read off the
 * main thread.
 */
@Composable
internal fun DownloadFolderDialog(current: String, onDismiss: () -> Unit, onChoose: (String) -> Unit) {
    // The folder being browsed; null while picking from the suggestions.
    var browsing by rememberSaveable { mutableStateOf<String?>(null) }
    val folder = browsing
    if (folder == null) {
        Suggestions(onChoose, onBrowse = { browsing = startFolder(current) }, onDismiss)
    } else {
        Browser(folder, onOpen = { browsing = it }, onChoose, onDismiss)
    }
}

@Composable
private fun Suggestions(onChoose: (String) -> Unit, onBrowse: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val io = LocalFolderDispatcher.current
    val suggestions by produceState<Suggested?>(null) {
        value = withContext(io) { suggest(context) }
    }
    FolderDialog(stringResource(R.string.file_dialog_set_dir), onDismiss) {
        suggestions?.let { suggested ->
            suggested.folders.forEach { Row(it, "folder") { onChoose(it) } }
            if (suggested.canBrowse) {
                Row(stringResource(R.string.file_dialog_custom_paths_available), "folderOther", onBrowse)
            }
        }
    }
}

@Composable
private fun Browser(folder: String, onOpen: (String) -> Unit, onChoose: (String) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val io = LocalFolderDispatcher.current
    var notWritable by rememberSaveable(folder) { mutableStateOf(false) }
    val children by produceState<List<String>?>(null, folder) {
        value = withContext(io) { subfolders(File(folder)) }
    }
    FolderDialog(
        folder,
        onDismiss,
        confirm = {
            TextButton(
                onClick = {
                    scope.launch {
                        if (withContext(io) { isWritable(File(folder)) }) {
                            onChoose(folder)
                        } else {
                            notWritable = true
                        }
                    }
                },
                modifier = Modifier.testTag("btnFolderSelect"),
            ) { Text(stringResource(R.string.file_dialog_set_dir)) }
        },
    ) {
        if (notWritable) {
            Text(
                stringResource(R.string.file_dialog_not_writable),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp).testTag("folderNotWritable"),
            )
        }
        File(folder).parentFile?.let { parent -> Row(PARENT, "folderUp") { onOpen(parent.path) } }
        children?.forEach { name -> Row(name, "folder") { onOpen(File(folder, name).path) } }
    }
}

@Composable
private fun FolderDialog(
    title: String,
    onDismiss: () -> Unit,
    confirm: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { content() } },
        confirmButton = confirm,
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_close)) }
        },
    )
}

@Composable
private fun Row(text: String, tag: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp)
            .testTag(tag),
    )
}

private class Suggested(val folders: List<String>, val canBrowse: Boolean)

/** The app's own Music folders, the shared one if it can be written to, and whether to browse. */
private fun suggest(context: Context): Suggested {
    val folders = ContextCompat.getExternalFilesDirs(context, Environment.DIRECTORY_MUSIC).filterNotNull()
        .map { it.path }
        .toMutableList()
    @Suppress("DEPRECATION")
    val shared = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
    if (isWritable(shared)) {
        folders += shared.path
    }
    @Suppress("DEPRECATION")
    val canBrowse = Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED &&
        isWritable(Environment.getExternalStorageDirectory())
    return Suggested(folders, canBrowse)
}

/** Where browsing starts: the folder set, or the storage's root if it's gone. */
private fun startFolder(current: String): String {
    val folder = File(current)
    @Suppress("DEPRECATION")
    return if (folder.isDirectory) folder.path else Environment.getExternalStorageDirectory().path
}

/** The folders in [folder] that can be read, by name. */
internal fun subfolders(folder: File): List<String> =
    folder.listFiles { file -> file.isDirectory && file.canRead() }.orEmpty().map { it.name }.sorted()

/** Whether a file can be made in [folder]; the only sure test on external storage. */
internal fun isWritable(folder: File): Boolean {
    val probe = File(folder, "ClementineTestFile.CheckIfWritable")
    return try {
        if (probe.createNewFile()) {
            probe.delete()
        }
        true
    } catch (_: IOException) {
        false
    }
}

private const val PARENT = ".."
