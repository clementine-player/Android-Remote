package de.qspool.clementineremote.ui.library

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.qspool.clementineremote.R
import de.qspool.clementineremote.backend.database.SongSelectItem

/**
 * The library: Clementine's library, browsed level by level (artists, their albums, their songs).
 * Tapping a song adds it to the playlist; a long press starts selecting items to add or download.
 * Pulling down downloads the library from Clementine again.
 */
@Composable
fun LibraryScreen(viewModel: LibraryViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val text = when (message) {
                is LibraryViewModel.Message.Added ->
                    resources.getQuantityString(R.plurals.songs_added, message.count, message.count)
                is LibraryViewModel.Message.DownloadFailed ->
                    resources.getString(R.string.library_download_error) + ": " +
                        resources.getString(message.reason)
            }
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        }
    }
    LibraryContent(
        state,
        onOpen = viewModel::open,
        onBack = { viewModel.back() },
        onDownloadLibrary = viewModel::download,
        onAdd = viewModel::addToPlaylist,
        onDownload = viewModel::downloadSongs,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryContent(
    state: LibraryState,
    onOpen: (SongSelectItem) -> Unit,
    onBack: () -> Unit,
    onDownloadLibrary: () -> Unit,
    onAdd: (List<SongSelectItem>) -> Unit,
    onDownload: (List<SongSelectItem>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = state.shown
    // Selected items, by their position in the level shown; cleared when another level shows.
    var selection by remember(state.levels.size, shown?.opened) { mutableStateOf(emptySet<Int>()) }
    val selected = shown?.items?.filterIndexed { index, _ -> index in selection }.orEmpty()

    Column(modifier.fillMaxSize()) {
        Progress(state.status)
        when {
            selection.isNotEmpty() -> SelectionBar(
                count = selected.size,
                onClear = { selection = emptySet() },
                onAdd = {
                    onAdd(selected)
                    selection = emptySet()
                },
                onDownload = {
                    onDownload(selected)
                    selection = emptySet()
                },
            )
            shown?.opened != null -> Opened(shown, onBack, onAdd, onDownload)
            else -> Top(shown)
        }

        PullToRefreshBox(
            // Progress shows above, so the pull only starts the download.
            isRefreshing = false,
            onRefresh = onDownloadLibrary,
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("libraryRefresh"),
        ) {
            if (state.status == LibraryStatus.Missing) {
                Missing(onDownloadLibrary)
            } else if (shown != null && shown.items.isEmpty() && state.filter.isNotBlank()) {
                NoResults()
            } else if (shown != null) {
                Items(
                    shown,
                    selection,
                    onClick = { index, item ->
                        if (selection.isEmpty()) {
                            onOpen(item)
                        } else {
                            selection = selection.toggle(index)
                        }
                    },
                    onLongClick = { index -> selection = selection.toggle(index) },
                )
            }
        }
    }
}

private fun Set<Int>.toggle(index: Int) = if (index in this) this - index else this + index

@Composable
private fun Progress(status: LibraryStatus) {
    val text = when (status) {
        is LibraryStatus.Downloading -> R.string.library_download
        LibraryStatus.Optimizing -> R.string.library_optimize
        else -> return
    }
    Column(Modifier.fillMaxWidth().testTag("libraryProgress")) {
        if (status is LibraryStatus.Downloading && status.total > 0) {
            LinearProgressIndicator(
                progress = { (status.bytes.toFloat() / status.total).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Text(
            stringResource(text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun Top(shown: LibraryLevel?) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)) {
        Text(
            stringResource(R.string.library_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        if (shown != null) {
            Text(
                pluralStringResource(R.plurals.number_items, shown.items.size, shown.items.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The header of an opened item, such as an album: its name, what's in it, and what to do with it all. */
@Composable
private fun Opened(
    shown: LibraryLevel,
    onBack: () -> Unit,
    onAdd: (List<SongSelectItem>) -> Unit,
    onDownload: (List<SongSelectItem>) -> Unit,
) {
    val opened = shown.opened ?: return
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, end = 16.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("libraryBack")) {
                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.library_back))
            }
            Column(Modifier.weight(1f).padding(start = 4.dp, top = 8.dp, bottom = 8.dp)) {
                Text(
                    opened.listTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() }.testTag("libraryTitle"),
                )
                Text(
                    pluralStringResource(R.plurals.number_items, shown.items.size, shown.items.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onAdd(listOf(opened)) }, modifier = Modifier.testTag("libraryAddAll")) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.library_add_to_playlist), modifier = Modifier.padding(start = 8.dp))
            }
            FilledTonalButton(onClick = { onDownload(listOf(opened)) }, modifier = Modifier.testTag("libraryDownloadAll")) {
                Icon(painterResource(R.drawable.ic_download), contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.menu_download), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun SelectionBar(count: Int, onClear: () -> Unit, onAdd: () -> Unit, onDownload: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().testTag("librarySelection")) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClear) {
                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.queue_clear_selection))
            }
            Text(
                pluralStringResource(R.plurals.queue_selected, count, count),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            IconButton(onClick = onAdd, modifier = Modifier.testTag("libraryAdd")) {
                Icon(painterResource(R.drawable.ic_add), stringResource(R.string.library_add_to_playlist))
            }
            IconButton(onClick = onDownload, modifier = Modifier.testTag("libraryDownload")) {
                Icon(painterResource(R.drawable.ic_download), stringResource(R.string.menu_download))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Items(
    shown: LibraryLevel,
    selection: Set<Int>,
    onClick: (Int, SongSelectItem) -> Unit,
    onLongClick: (Int) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().testTag("library")) {
        itemsIndexed(shown.items) { index, item ->
            val isSelected = index in selection
            ListItem(
                headlineContent = { Text(item.listTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text(item.listSubtitle.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingContent = { Leading(shown.kind) },
                colors = ListItemDefaults.colors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                ),
                modifier = Modifier
                    .combinedClickable(onClick = { onClick(index, item) }, onLongClick = { onLongClick(index) })
                    .semantics { selected = isSelected },
            )
        }
    }
}

@Composable
private fun Leading(kind: ItemKind) {
    val icon = when (kind) {
        ItemKind.ARTIST -> R.drawable.ic_person
        ItemKind.ALBUM, ItemKind.YEAR -> R.drawable.ic_album
        ItemKind.GENRE, ItemKind.SONG -> R.drawable.ic_music_note
    }
    val shape = if (kind == ItemKind.SONG) RoundedCornerShape(8.dp) else CircleShape
    val size = if (kind == ItemKind.SONG) 48.dp else 40.dp
    val background = if (kind == ItemKind.SONG) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    Box(Modifier.size(size).clip(shape).background(background), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** No library on this phone yet. Scrollable, so pulling down downloads it too. */
@Composable
private fun Missing(onDownload: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_album),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Text(
            stringResource(R.string.library_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onDownload, modifier = Modifier.testTag("btnDownloadLibrary")) {
            Text(stringResource(R.string.library_download_action))
        }
    }
}

/** Nothing at this level matches the search. Scrollable, so pulling down still works. */
@Composable
private fun NoResults() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp)) {
        Text(
            stringResource(R.string.library_no_search_results),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("libraryNoResults"),
        )
    }
}
