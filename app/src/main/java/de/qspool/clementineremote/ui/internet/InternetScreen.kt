package de.qspool.clementineremote.ui.internet

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.qspool.clementineremote.R
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.BrowseAddAction
import de.qspool.clementineremote.ui.browse.BrowseTile
import de.qspool.clementineremote.ui.browse.rememberLevelListState

/**
 * Internet: Clementine's internet services (SomaFM, Radio Browser, Subsonic, saved radio
 * streams...) and what's below them, as its Internet sidebar shows them. Tapping a node opens it,
 * or plays a track or stream (adds it, while Clementine is playing); a long press puts it on the
 * playlist in other ways. Pulling down asks for the level again.
 */
@Composable
fun InternetScreen(viewModel: InternetViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.added.collect { message ->
            val text = when (message) {
                AddMessage.ADDED -> R.string.internet_added
                AddMessage.PLAYING_NEXT -> R.string.internet_playing_next
                AddMessage.NOT_PLAYABLE -> R.string.internet_not_playable
                AddMessage.GONE -> R.string.internet_gone
            }
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }
    InternetContent(
        state,
        onTap = viewModel::tap,
        onAdd = viewModel::add,
        onBack = { viewModel.back() },
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InternetContent(
    state: InternetState,
    onTap: (InternetNode) -> Unit,
    onAdd: (InternetNode, BrowseAddAction) -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = state.shown
    // Held here, so a level keeps its place while it's loading or empty.
    val listState = rememberLevelListState(state.levels.size)

    Column(modifier.fillMaxSize()) {
        // Loading more of what's shown.
        if (shown.status == LevelStatus.LOADING && shown.nodes.isNotEmpty()) {
            LinearProgressIndicator(Modifier.fillMaxWidth().testTag("internetProgress"))
        }
        val opened = shown.opened
        // How many items, once Clementine says.
        val count = shown.totalCount.takeUnless { shown.status == LevelStatus.LOADING && shown.nodes.isEmpty() }
        if (opened == null) Top() else Opened(opened, count, onBack, onAdd)

        PullToRefreshBox(
            // Clementine's answer replaces what's shown; the pull only asks for it.
            isRefreshing = false,
            onRefresh = onRefresh,
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("internetRefresh"),
        ) {
            when {
                shown.status == LevelStatus.NEEDS_SETUP -> Empty(
                    stringResource(R.string.internet_needs_setup),
                    shown.message,
                    "internetNeedsSetup",
                )
                shown.nodes.isNotEmpty() -> LazyColumn(Modifier.fillMaxSize().testTag("internetNodes"), state = listState) {
                    itemsIndexed(shown.nodes) { index, node ->
                        NodeRow(node, onTap = { onTap(node) }, onAdd = { onAdd(node, it) })
                        if (index == shown.nodes.lastIndex && shown.hasMore) {
                            LaunchedEffect(shown.nodes.size) { onLoadMore() }
                        }
                    }
                }
                shown.status == LevelStatus.LOADING -> Loading()
                else -> Empty(stringResource(R.string.internet_empty), null, "internetEmpty")
            }
        }
    }
}

@Composable
private fun Top() {
    Text(
        stringResource(R.string.internet_title),
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
            .semantics { heading() },
    )
}

/**
 * The header of an opened node: back, and its title. One that can go on the playlist, such as an
 * album or a playlist, also says how many items it has ([count], once known), and plays or adds
 * them all.
 */
@Composable
private fun Opened(
    opened: InternetNode,
    count: Int?,
    onBack: () -> Unit,
    onAdd: (InternetNode, BrowseAddAction) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, end = 16.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("internetBack")) {
                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.library_back))
            }
            Column(Modifier.weight(1f).padding(start = 4.dp, top = 8.dp, bottom = 8.dp)) {
                Text(
                    opened.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() }.testTag("internetTitle"),
                )
                if (opened.addable && count != null) {
                    Text(
                        pluralStringResource(R.plurals.number_items, count, count),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("internetCount"),
                    )
                }
            }
        }
        if (opened.addable) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onAdd(opened, BrowseAddAction.BROWSE_ADD_ACTION_PLAY_NOW) },
                    modifier = Modifier.testTag("internetPlayAll"),
                ) {
                    Icon(painterResource(R.drawable.ic_player_play), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.internet_play), modifier = Modifier.padding(start = 8.dp))
                }
                FilledTonalButton(
                    onClick = { onAdd(opened, BrowseAddAction.BROWSE_ADD_ACTION_APPEND) },
                    modifier = Modifier.testTag("internetAddAll"),
                ) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.library_add_to_playlist), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/**
 * A node: tapping it opens it or puts it on the playlist; a long press on one that can go on
 * the playlist offers the ways it can. One that does neither is shown, but does nothing.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NodeRow(node: InternetNode, onTap: () -> Unit, onAdd: (BrowseAddAction) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val actions = stringResource(R.string.shell_more)
    Box {
        ListItem(
            headlineContent = { Text(node.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = if (node.subtitle != null) {
                { Text(node.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            } else {
                null
            },
            leadingContent = { NodeTile(node) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier
                .then(
                    if (node.hasChildren || node.addable) {
                        Modifier.combinedClickable(
                            onClick = onTap,
                            onLongClickLabel = if (node.addable) actions else null,
                            onLongClick = if (node.addable) ({ menu = true }) else null,
                        )
                    } else {
                        Modifier
                    },
                )
                .testTag("internetNode"),
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            listOf(
                BrowseAddAction.BROWSE_ADD_ACTION_PLAY_NOW to R.string.internet_play_now,
                BrowseAddAction.BROWSE_ADD_ACTION_PLAY_NEXT to R.string.internet_play_next,
                BrowseAddAction.BROWSE_ADD_ACTION_APPEND to R.string.library_add_to_playlist,
                BrowseAddAction.BROWSE_ADD_ACTION_REPLACE to R.string.internet_replace_playlist,
            ).forEach { (action, label) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        menu = false
                        onAdd(action)
                    },
                    modifier = Modifier.testTag("internetMenu_${action.name}"),
                )
            }
        }
    }
}

/** A node's tile: a service's own icon (or a globe), or an icon for what kind of node it is. */
@Composable
private fun NodeTile(node: InternetNode) {
    val image = remember(node.icon) {
        node.icon?.toByteArray()?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }?.asImageBitmap()
    }
    val icon = when (node.kind) {
        NodeKind.SERVICE -> R.drawable.ic_public
        NodeKind.FOLDER -> R.drawable.ic_folder
        NodeKind.TRACK -> R.drawable.ic_music_note
        NodeKind.STREAM -> R.drawable.ic_sensors
        NodeKind.SMART_PLAYLIST -> R.drawable.ic_auto_awesome
    }
    BrowseTile(icon, playable = node.kind == NodeKind.TRACK || node.kind == NodeKind.STREAM, image = image)
}

/** Nothing from Clementine yet. Scrollable, so pulling down still works. */
@Composable
private fun Loading() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp).testTag("internetLoading"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator()
        Text(
            stringResource(R.string.internet_loading),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A level with nothing to show: [title], and [message] below it. Scrollable, so pulling down still works. */
@Composable
private fun Empty(title: String, message: String?, tag: String) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp).testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (!message.isNullOrBlank()) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
