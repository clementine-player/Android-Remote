package de.qspool.clementineremote.ui.search

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.qspool.clementineremote.R
import de.qspool.clementineremote.backend.database.SongSelectItem
import de.qspool.clementineremote.ui.browse.BrowseItems
import de.qspool.clementineremote.ui.browse.BrowseSelectionBar

/**
 * Search: a search bar that asks Clementine to search its library and internet services, and the
 * results, browsed level by level. Tapping a song adds it to the playlist; a long press starts
 * selecting items to add.
 */
@Composable
fun SearchScreen(viewModel: SearchViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.added.collect { count ->
            Toast.makeText(context, resources.getQuantityString(R.plurals.songs_added, count, count), Toast.LENGTH_SHORT)
                .show()
        }
    }
    SearchContent(
        state,
        onSearch = viewModel::search,
        onOpen = viewModel::open,
        onBack = { viewModel.back() },
        onAdd = viewModel::addToPlaylist,
    )
}

@Composable
internal fun SearchContent(
    state: SearchState,
    onSearch: (String) -> Unit,
    onOpen: (SongSelectItem) -> Unit,
    onBack: () -> Unit,
    onAdd: (List<SongSelectItem>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = state.shown
    var selection by remember(state.levels.size, shown?.opened) { mutableStateOf(emptySet<Int>()) }
    val selected = shown?.items?.filterIndexed { index, _ -> index in selection }.orEmpty()
    val opened = shown?.opened

    Column(modifier.fillMaxSize()) {
        SearchField(state.searchedFor.orEmpty(), onSearch, Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp))

        if (state.searching) {
            Column(Modifier.fillMaxWidth().testTag("searchProgress")) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    stringResource(R.string.search_searching, state.searchedFor.orEmpty()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        if (selection.isNotEmpty()) {
            BrowseSelectionBar(
                count = selected.size,
                onClear = { selection = emptySet() },
                onAdd = {
                    onAdd(selected)
                    selection = emptySet()
                },
                onDownload = null,
                tag = "search",
            )
        } else if (opened != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, end = 16.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("searchBack")) {
                    Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.library_back))
                }
                Column(Modifier.weight(1f).padding(start = 4.dp, top = 4.dp, bottom = 4.dp)) {
                    Text(
                        opened.listTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() }.testTag("searchTitle"),
                    )
                    Text(
                        pluralStringResource(R.plurals.number_items, shown.items.size, shown.items.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = { onAdd(listOf(opened)) }, modifier = Modifier.testTag("searchAddAll")) {
                    Text(stringResource(R.string.library_add_to_playlist))
                }
            }
        }

        when {
            shown != null && shown.items.isNotEmpty() -> BrowseItems(
                shown,
                selection,
                onClick = { index, item ->
                    if (selection.isEmpty()) {
                        onOpen(item)
                    } else {
                        selection = if (index in selection) selection - index else selection + index
                    }
                },
                onLongClick = { index -> selection = if (index in selection) selection - index else selection + index },
                tag = "global_search",
            )
            shown != null -> Message(R.string.library_no_search_results, "searchNoResults")
            !state.searching -> Message(R.string.global_search_empty, "searchEmpty")
        }
    }
}

/** A search bar: a pill with the query, which searches when the keyboard's search key is pressed. */
@Composable
private fun SearchField(searchedFor: String, onSearch: (String) -> Unit, modifier: Modifier) {
    var text by rememberSaveable(searchedFor) { mutableStateOf(searchedFor) }
    val keyboard = LocalSoftwareKeyboardController.current
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().height(56.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboard?.hide()
                    onSearch(text)
                }),
                decorationBox = { field ->
                    if (text.isEmpty()) {
                        Text(
                            stringResource(R.string.global_search_search),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    field()
                },
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp).testTag("searchField"),
            )
            if (text.isNotEmpty()) {
                IconButton(onClick = { text = "" }) {
                    Icon(painterResource(R.drawable.ic_close), stringResource(R.string.search_clear))
                }
            }
        }
    }
}

@Composable
private fun Message(text: Int, tag: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_search),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Text(
            stringResource(text),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(tag),
        )
    }
}
