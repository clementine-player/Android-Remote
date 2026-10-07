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
import de.qspool.clementineremote.backend.AddAction
import de.qspool.clementineremote.backend.database.SongSelectItem
import de.qspool.clementineremote.ui.browse.BrowseItems
import de.qspool.clementineremote.ui.browse.BrowseLevel
import de.qspool.clementineremote.ui.browse.BrowseSelectionBar
import de.qspool.clementineremote.ui.browse.ItemKind
import de.qspool.clementineremote.ui.browse.rememberLevelListState
import de.qspool.clementineremote.ui.search.SearchResultsContent
import de.qspool.clementineremote.ui.search.SearchSection

/**
 * The library: Clementine's library, browsed level by level (artists, their albums, their songs).
 * Tapping a song adds it to the playlist; a long press offers the other ways to put an item on the
 * playlist, and selecting items to add or download.
 * Searching it shows what matched in sections, as the Search screen does. Pulling down downloads
 * the library from Clementine again.
 */
@Composable
fun LibraryScreen(viewModel: LibraryViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val canPlayNext by viewModel.canPlayNext.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val text = when (message) {
                is LibraryViewModel.Message.Added ->
                    resources.getQuantityString(R.plurals.songs_added, message.count, message.count)
                is LibraryViewModel.Message.SyncFailed ->
                    resources.getString(R.string.library_sync_error) + ": " +
                        resources.getString(message.reason)
            }
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        }
    }
    LibraryContent(
        state,
        onOpen = viewModel::open,
        onBack = { viewModel.back() },
        onSyncLibrary = viewModel::sync,
        onAdd = viewModel::addToPlaylist,
        onDownload = viewModel::downloadSongs,
        results = ResultActions(
            onOpen = viewModel::openResult,
            onSeeAll = viewModel::seeAll,
            onBack = { viewModel.backInResults() },
            onAdd = viewModel::addResults,
            onDownload = viewModel::downloadResults,
        ),
        canPlayNext = canPlayNext,
    )
}

/** What's done with the library's search results. */
internal class ResultActions(
    val onOpen: (SongSelectItem) -> Unit = {},
    val onSeeAll: (SearchSection) -> Unit = {},
    val onBack: () -> Unit = {},
    val onAdd: (List<SongSelectItem>, AddAction) -> Unit = { _, _ -> },
    val onDownload: (List<SongSelectItem>) -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryContent(
    state: LibraryState,
    onOpen: (SongSelectItem) -> Unit,
    onBack: () -> Unit,
    onSyncLibrary: () -> Unit,
    onAdd: (List<SongSelectItem>, AddAction) -> Unit,
    onDownload: (List<SongSelectItem>) -> Unit,
    modifier: Modifier = Modifier,
    results: ResultActions = ResultActions(),
    canPlayNext: Boolean = false,
) {
    val shown = state.shown
    val search = state.search
    // Selected items, by their position in the level shown; cleared when another level shows.
    var selection by remember(state.levels.size, shown?.opened) { mutableStateOf(emptySet<Int>()) }
    // Held here, so a level keeps its place while another branch shows (no results, say).
    val listState = rememberLevelListState(state.levels.size)
    val selected = shown?.items?.filterIndexed { index, _ -> index in selection }.orEmpty()

    Column(modifier.fillMaxSize()) {
        Progress(state.status)
        when {
            search != null -> if (search.pages.isEmpty()) Top(null)
            selection.isNotEmpty() -> BrowseSelectionBar(
                count = selected.size,
                onClear = { selection = emptySet() },
                onAdd = {
                    onAdd(selected, AddAction.APPEND)
                    selection = emptySet()
                },
                onDownload = {
                    onDownload(selected)
                    selection = emptySet()
                },
                tag = "library",
            )
            shown?.opened != null -> Opened(shown, onBack, onAdd, onDownload)
            else -> Top(shown)
        }

        PullToRefreshBox(
            // Progress shows above, so the pull only starts the sync.
            isRefreshing = false,
            onRefresh = onSyncLibrary,
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("libraryRefresh"),
        ) {
            if (state.status == LibraryStatus.Missing) {
                Missing(onSyncLibrary)
            } else if (search != null && search.sections.isEmpty && search.pages.isEmpty()) {
                NoResults()
            } else if (search != null) {
                SearchResultsContent(
                    search,
                    icon = { null },
                    onOpen = results.onOpen,
                    onSeeAll = results.onSeeAll,
                    onBack = results.onBack,
                    onAdd = results.onAdd,
                    onDownload = results.onDownload,
                    tag = "library",
                    canPlayNext = canPlayNext,
                )
            } else if (shown != null) {
                BrowseItems(
                    shown,
                    selection,
                    onClick = { index, item ->
                        if (selection.isEmpty()) {
                            onOpen(item)
                        } else {
                            selection = selection.toggle(index)
                        }
                    },
                    onToggle = { index -> selection = selection.toggle(index) },
                    onAdd = { item, action -> onAdd(listOf(item), action) },
                    canPlayNext = canPlayNext,
                    tag = "library",
                    listState = listState,
                )
            }
        }
    }
}

private fun Set<Int>.toggle(index: Int) = if (index in this) this - index else this + index

@Composable
private fun Progress(status: LibraryStatus) {
    val text = when (status) {
        is LibraryStatus.Syncing -> R.string.library_syncing
        LibraryStatus.Optimizing -> R.string.library_optimize
        else -> return
    }
    Column(Modifier.fillMaxWidth().testTag("libraryProgress")) {
        if (status is LibraryStatus.Syncing && status.total > 0) {
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
private fun Top(shown: BrowseLevel?) {
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
    shown: BrowseLevel,
    onBack: () -> Unit,
    onAdd: (List<SongSelectItem>, AddAction) -> Unit,
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
            Button(onClick = { onAdd(listOf(opened), AddAction.APPEND) }, modifier = Modifier.testTag("libraryAddAll")) {
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

/** No library on this phone yet. Scrollable, so pulling down downloads it too. */
@Composable
private fun Missing(onSync: () -> Unit) {
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
        Button(onClick = onSync, modifier = Modifier.testTag("btnSyncLibrary")) {
            Text(stringResource(R.string.library_sync_action))
        }
    }
}

/** Nothing in the library matches the search. Scrollable, so pulling down still works. */
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
