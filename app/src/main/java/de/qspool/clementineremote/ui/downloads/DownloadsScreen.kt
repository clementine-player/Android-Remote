package de.qspool.clementineremote.ui.downloads

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.qspool.clementineremote.R
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.DownloadItem
import de.qspool.clementineremote.ui.settings.ClementineSettings

/**
 * Downloads: those running, with their progress, and those finished, whose songs can be played.
 * Cancelling a running download stops it; on a finished one, it forgets it.
 */
@Composable
fun DownloadsScreen(viewModel: DownloadsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    DownloadsContent(
        state,
        onCancel = { download ->
            if (viewModel.cancel(download.id)) {
                Toast.makeText(context, R.string.download_noti_canceled, Toast.LENGTH_SHORT).show()
            }
        },
        onPlay = { song ->
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(song.uri, "audio/*")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                Toast.makeText(context, R.string.app_not_available, Toast.LENGTH_LONG).show()
            }
        },
        onChangeSettings = { context.startActivity(Intent(context, ClementineSettings::class.java)) },
    )
}

@Composable
internal fun DownloadsContent(
    state: DownloadsState,
    onCancel: (Download) -> Unit,
    onPlay: (DownloadedSong) -> Unit,
    onChangeSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The finished download whose songs are being picked from.
    var picking by remember { mutableStateOf<Download?>(null) }

    LazyColumn(modifier.fillMaxSize().testTag("downloads")) {
        item {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)) {
                Text(
                    stringResource(R.string.downloads_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                if (state.freeSpace.isNotEmpty()) {
                    Text(
                        stringResource(R.string.downloads_free_space, state.freeSpace),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("downloadsFreeSpace"),
                    )
                }
            }
        }

        if (state.running.isEmpty() && state.finished.isEmpty()) {
            item { Empty() }
        }
        section(R.string.downloads_running, state.running, onCancel) {}
        section(R.string.downloads_finished, state.finished, onCancel) { picking = it }

        if (state.wifiOnly) {
            item { WifiOnly(onChangeSettings) }
        }
    }

    picking?.let { download ->
        AlertDialog(
            onDismissRequest = { picking = null },
            title = { Text(stringResource(R.string.downloaded_songs)) },
            text = {
                Column {
                    download.songs.forEach { song ->
                        Text(
                            "${song.artist} - ${song.title}",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    picking = null
                                    onPlay(song)
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { picking = null }) { Text(stringResource(R.string.dialog_close)) }
            },
        )
    }
}

private fun LazyListScope.section(
    title: Int,
    downloads: List<Download>,
    onCancel: (Download) -> Unit,
    onClick: (Download) -> Unit,
) {
    if (downloads.isEmpty()) {
        return
    }
    item {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
        )
    }
    items(downloads, key = { it.id }) { download -> DownloadRow(download, onCancel, onClick) }
}

@Composable
private fun DownloadRow(download: Download, onCancel: (Download) -> Unit, onClick: (Download) -> Unit) {
    Column(Modifier.testTag("download${download.id}")) {
        ListItem(
            headlineContent = { Text(download.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                Column {
                    Text(download.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(download.size, style = MaterialTheme.typography.labelMedium)
                }
            },
            leadingContent = {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(if (download.item == DownloadItem.ItemAlbum) R.drawable.ic_album else R.drawable.ic_music_note),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
            trailingContent = {
                IconButton(onClick = { onCancel(download) }, modifier = Modifier.testTag("cancel${download.id}")) {
                    Icon(painterResource(R.drawable.ic_close), stringResource(R.string.cd_cancel_download))
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable(enabled = !download.running && download.songs.isNotEmpty()) { onClick(download) },
        )
        if (download.running) {
            LinearProgressIndicator(
                progress = { download.progress / 100f },
                modifier = Modifier.fillMaxWidth().padding(start = 72.dp, end = 16.dp, bottom = 12.dp),
            )
        }
    }
}

@Composable
private fun WifiOnly(onChange: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("downloadsWifiOnly"),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.downloads_wifi_only),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onChange) { Text(stringResource(R.string.downloads_change)) }
        }
    }
}

@Composable
private fun Empty() {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_download),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Text(
            stringResource(R.string.downloads_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("downloadsEmpty"),
        )
    }
}
