package de.qspool.clementineremote.ui.queue

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.qspool.clementineremote.R
import de.qspool.clementineremote.backend.player.MySong

/**
 * The queue: the songs of one of Clementine's playlists, with the one playing marked. Tapping a
 * song plays it; a long press starts selecting songs to play, download or remove.
 */
@Composable
fun QueueScreen(viewModel: QueueViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    QueueContent(
        state,
        onShow = viewModel::show,
        onPlay = viewModel::play,
        onDownload = viewModel::download,
        onRemove = viewModel::remove,
    )
}

@Composable
internal fun QueueContent(
    state: QueueState,
    onShow: (PlaylistTab) -> Unit,
    onPlay: (MySong) -> Unit,
    onDownload: (List<MySong>) -> Unit,
    onRemove: (List<MySong>) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Selected songs, by their index in the playlist; cleared when another playlist shows.
    var selection by remember(state.shown?.id) { mutableStateOf(emptySet<Int>()) }
    val selected = state.songs.filter { it.index in selection }

    Column(modifier.fillMaxSize()) {
        state.loading?.let { (done, total) ->
            LinearProgressIndicator(
                progress = { if (total > 0) done.toFloat() / total else 0f },
                modifier = Modifier.fillMaxWidth().testTag("queueLoading"),
            )
        }
        if (selection.isEmpty()) {
            Header(state, onShow)
        } else {
            SelectionBar(
                count = selected.size,
                onClear = { selection = emptySet() },
                onPlay = {
                    selected.firstOrNull()?.let(onPlay)
                    selection = emptySet()
                },
                onDownload = {
                    onDownload(selected)
                    selection = emptySet()
                },
                onRemove = {
                    onRemove(selected)
                    selection = emptySet()
                },
            )
        }

        if (state.songs.isEmpty() && state.loading == null) {
            Empty(Modifier.weight(1f))
        } else {
            Songs(
                state,
                selection,
                onClick = { song ->
                    if (selection.isEmpty()) {
                        onPlay(song)
                    } else {
                        selection = selection.toggle(song.index)
                    }
                },
                onLongClick = { song -> selection = selection.toggle(song.index) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun Set<Int>.toggle(index: Int) = if (index in this) this - index else this + index

@Composable
private fun Header(state: QueueState, onShow: (PlaylistTab) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp)) {
        Text(
            state.shown?.name.orEmpty(),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() }.testTag("queueTitle"),
        )
        if (state.shown != null) {
            Text(
                summary(state.songCount, state.lengthSeconds),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (state.playlists.size > 1) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.playlists.forEach { playlist ->
                    FilterChip(
                        selected = playlist == state.shown,
                        onClick = { onShow(playlist) },
                        label = { Text(playlist.name) },
                        modifier = Modifier.testTag("playlist${playlist.id}"),
                    )
                }
            }
        }
    }
}

/** "13 songs · 50 min" */
@Composable
private fun summary(songs: Int, seconds: Int): String {
    val minutes = (seconds + 30) / 60
    val length = if (minutes >= 60) {
        stringResource(R.string.queue_hours_minutes, minutes / 60, minutes % 60)
    } else {
        stringResource(R.string.queue_minutes, minutes)
    }
    return pluralStringResource(R.plurals.queue_songs, songs, songs) + " · " + length
}

@Composable
private fun SelectionBar(
    count: Int,
    onClear: () -> Unit,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().testTag("queueSelection")) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClear) {
                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.queue_clear_selection))
            }
            Text(
                pluralStringResource(R.plurals.queue_selected, count, count),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            IconButton(onClick = onPlay, modifier = Modifier.testTag("queuePlay")) {
                Icon(painterResource(R.drawable.ic_player_play), stringResource(R.string.playlist_context_play))
            }
            IconButton(onClick = onDownload, modifier = Modifier.testTag("queueDownload")) {
                Icon(painterResource(R.drawable.ic_download), stringResource(R.string.menu_download))
            }
            IconButton(onClick = onRemove, modifier = Modifier.testTag("queueRemove")) {
                Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.playlist_context_remove))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Songs(
    state: QueueState,
    selection: Set<Int>,
    onClick: (MySong) -> Unit,
    onLongClick: (MySong) -> Unit,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    // Show the song playing, a few rows down, when it changes or another playlist shows.
    LaunchedEffect(state.shown?.id, state.playingIndex) {
        val row = state.songs.indexOfFirst { it.index == state.playingIndex }
        if (row >= 0) {
            listState.scrollToItem((row - 3).coerceAtLeast(0))
        }
    }
    LazyColumn(modifier.fillMaxWidth().testTag("queueSongs"), state = listState) {
        items(state.songs, key = { it.index }) { song ->
            val playing = song.index == state.playingIndex
            val isSelected = song.index in selection
            ListItem(
                headlineContent = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (playing) {
                            Icon(
                                painterResource(R.drawable.ic_equalizer),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Text(
                            song.title,
                            color = if (playing) MaterialTheme.colorScheme.primary else Color.Unspecified,
                            fontWeight = if (playing) FontWeight.Medium else null,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                supportingContent = {
                    Text(
                        listOf(song.artist, song.album).filter { it.isNotBlank() }.joinToString(" · "),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingContent = {
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_music_note),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                trailingContent = {
                    Text(song.prettyLength.orEmpty(), style = MaterialTheme.typography.labelMedium)
                },
                colors = ListItemDefaults.colors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                ),
                modifier = Modifier
                    .combinedClickable(onClick = { onClick(song) }, onLongClick = { onLongClick(song) })
                    .semantics { selected = isSelected }
                    .testTag("song${song.index}"),
            )
        }
    }
}

@Composable
private fun Empty(modifier: Modifier) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(
            painterResource(R.drawable.ic_music_note),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Text(
            stringResource(R.string.playlist_empty),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
