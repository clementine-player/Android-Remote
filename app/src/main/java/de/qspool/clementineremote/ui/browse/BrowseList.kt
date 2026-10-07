package de.qspool.clementineremote.ui.browse

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.qspool.clementineremote.R
import de.qspool.clementineremote.backend.AddAction
import de.qspool.clementineremote.backend.database.SongSelectItem

/**
 * Where each open level is scrolled to, the top level first, so going back shows the level where
 * it was left. A level just opened starts at the top.
 *
 * @param depth how many levels are open
 */
@Composable
internal fun rememberLevelListState(depth: Int): LazyListState {
    val states = rememberSaveable(saver = LevelListStatesSaver) { mutableListOf<LazyListState>() }
    // Levels closed since are forgotten; levels opened start afresh.
    while (states.size > maxOf(depth, 1)) states.removeAt(states.lastIndex)
    while (states.size < maxOf(depth, 1)) states.add(LazyListState())
    return states.last()
}

/** Keeps each level's first visible item and its offset, across a configuration change. */
private val LevelListStatesSaver = listSaver<MutableList<LazyListState>, Int>(
    save = { states -> states.flatMap { listOf(it.firstVisibleItemIndex, it.firstVisibleItemScrollOffset) } },
    restore = { saved -> saved.chunked(2).map { LazyListState(it[0], it[1]) }.toMutableList() },
)

/**
 * The items of a level: tapping one runs [onClick]. A long press offers the ways to put it on the
 * playlist ([onAdd]) and selecting it ([onToggle]); while items are selected, it selects or
 * deselects it instead. The list is tagged [tag], for tests.
 */
@Composable
internal fun BrowseItems(
    shown: BrowseLevel,
    selection: Set<Int>,
    onClick: (Int, SongSelectItem) -> Unit,
    onToggle: (Int) -> Unit,
    onAdd: (SongSelectItem, AddAction) -> Unit,
    canPlayNext: Boolean,
    tag: String,
    listState: LazyListState = rememberLazyListState(),
) {
    LazyColumn(Modifier.fillMaxSize().testTag(tag), state = listState) {
        itemsIndexed(shown.items) { index, item ->
            BrowseRow(
                shown.kind, item, index in selection,
                onClick = { onClick(index, item) },
                onAdd = { onAdd(item, it) },
                canPlayNext = canPlayNext,
                onSelect = { onToggle(index) },
                selecting = selection.isNotEmpty(),
            )
        }
    }
}

/**
 * An item of a level, of [kind]: tapping it runs [onClick]. A long press offers the ways to put
 * it on the playlist ([onAdd]) and, with [onSelect], selecting it; while [selecting], it runs
 * [onSelect] straight away.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BrowseRow(
    kind: ItemKind,
    item: SongSelectItem,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onAdd: (AddAction) -> Unit,
    canPlayNext: Boolean,
    onSelect: (() -> Unit)? = null,
    selecting: Boolean = false,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        ListItem(
            headlineContent = { Text(item.listTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = { Text(item.listSubtitle.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingContent = { Leading(kind, item) },
            colors = ListItemDefaults.colors(
                containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            ),
            modifier = Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClickLabel = stringResource(R.string.shell_more),
                    onLongClick = { if (selecting && onSelect != null) onSelect() else menu = true },
                )
                .semantics { selected = isSelected },
        )
        AddMenu(menu, onDismiss = { menu = false }, canPlayNext = canPlayNext, onAdd = onAdd, onSelect = onSelect)
    }
}

/** A tile for what an item groups; a search source shows its own icon, if Clementine sent one. */
@Composable
private fun Leading(kind: ItemKind, item: SongSelectItem) {
    val icon = remember(item) { item.icon?.asImageBitmap() }
    val drawable = when (kind) {
        ItemKind.ARTIST -> R.drawable.ic_person
        ItemKind.ALBUM, ItemKind.YEAR -> R.drawable.ic_album
        ItemKind.SOURCE, ItemKind.GENRE, ItemKind.SONG -> R.drawable.ic_music_note
    }
    BrowseTile(drawable, playable = kind == ItemKind.SONG, image = icon)
}

/**
 * A row's tile: a rounded square for something that plays (a song), a circle for what groups
 * others. It shows [image], if there is one, or else [icon].
 */
@Composable
internal fun BrowseTile(@DrawableRes icon: Int, playable: Boolean, image: ImageBitmap? = null) {
    val shape = if (playable) RoundedCornerShape(8.dp) else CircleShape
    val size = if (playable) 48.dp else 40.dp
    val background = if (playable) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    Box(Modifier.size(size).clip(shape).background(background), contentAlignment = Alignment.Center) {
        if (image != null) {
            Image(image, contentDescription = null, modifier = Modifier.size(24.dp))
        } else {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The bar shown while items are selected: how many, and adding them to the playlist or, with
 * [onDownload], downloading them. Its parts are tagged "[tag]Selection", "[tag]Add" and
 * "[tag]Download".
 */
@Composable
internal fun BrowseSelectionBar(
    count: Int,
    onClear: () -> Unit,
    onAdd: () -> Unit,
    onDownload: (() -> Unit)?,
    tag: String,
) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().testTag("${tag}Selection")) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClear) {
                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.queue_clear_selection))
            }
            Text(
                pluralStringResource(R.plurals.queue_selected, count, count),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            IconButton(onClick = onAdd, modifier = Modifier.testTag("${tag}Add")) {
                Icon(painterResource(R.drawable.ic_add), stringResource(R.string.library_add_to_playlist))
            }
            if (onDownload != null) {
                IconButton(onClick = onDownload, modifier = Modifier.testTag("${tag}Download")) {
                    Icon(painterResource(R.drawable.ic_download), stringResource(R.string.menu_download))
                }
            }
        }
    }
}
