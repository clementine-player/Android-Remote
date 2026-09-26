package de.qspool.clementineremote.ui.browse

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import de.qspool.clementineremote.backend.database.SongSelectItem

/**
 * The items of a level: tapping one runs [onClick], a long press [onLongClick]. The list is
 * tagged [tag], for tests.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BrowseItems(
    shown: BrowseLevel,
    selection: Set<Int>,
    onClick: (Int, SongSelectItem) -> Unit,
    onLongClick: (Int) -> Unit,
    tag: String,
) {
    LazyColumn(Modifier.fillMaxSize().testTag(tag)) {
        itemsIndexed(shown.items) { index, item ->
            val isSelected = index in selection
            ListItem(
                headlineContent = { Text(item.listTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text(item.listSubtitle.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingContent = { Leading(shown.kind, item) },
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

/** A tile for what an item groups; a search source shows its own icon, if Clementine sent one. */
@Composable
private fun Leading(kind: ItemKind, item: SongSelectItem) {
    val icon = remember(item) { item.icon?.asImageBitmap() }
    val shape = if (kind == ItemKind.SONG) RoundedCornerShape(8.dp) else CircleShape
    val size = if (kind == ItemKind.SONG) 48.dp else 40.dp
    val background = if (kind == ItemKind.SONG) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    Box(Modifier.size(size).clip(shape).background(background), contentAlignment = Alignment.Center) {
        if (icon != null) {
            Image(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        } else {
            val drawable = when (kind) {
                ItemKind.ARTIST -> R.drawable.ic_person
                ItemKind.ALBUM, ItemKind.YEAR -> R.drawable.ic_album
                ItemKind.SOURCE, ItemKind.GENRE, ItemKind.SONG -> R.drawable.ic_music_note
            }
            Icon(painterResource(drawable), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
