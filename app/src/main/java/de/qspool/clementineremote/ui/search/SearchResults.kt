package de.qspool.clementineremote.ui.search

import android.content.res.Resources
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.qspool.clementineremote.R
import de.qspool.clementineremote.backend.database.SongSelectItem
import de.qspool.clementineremote.ui.browse.BrowseItems
import de.qspool.clementineremote.ui.browse.BrowseLevel
import de.qspool.clementineremote.ui.browse.BrowseRow
import de.qspool.clementineremote.ui.browse.BrowseSelectionBar
import de.qspool.clementineremote.ui.browse.ItemKind

/** A section of search results. */
enum class SearchSection(@StringRes val title: Int, val kind: ItemKind) {
    SONGS(R.string.search_songs, ItemKind.SONG),
    ARTISTS(R.string.search_artists, ItemKind.ARTIST),
    ALBUMS(R.string.search_albums, ItemKind.ALBUM),
    STATIONS(R.string.search_stations, ItemKind.SONG),
    OTHERS(R.string.search_other_matches, ItemKind.SONG);

    fun items(sections: SearchSections) = when (this) {
        SONGS -> sections.songs
        ARTISTS -> sections.artists
        ALBUMS -> sections.albums
        STATIONS -> sections.stations
        OTHERS -> sections.others
    }
}

/** A page opened from search results: all of a section, or what's in an artist or album. */
sealed interface SearchPage {
    data class All(val section: SearchSection) : SearchPage

    data class Opened(val level: BrowseLevel) : SearchPage
}

/** Search results in sections, and the pages opened from them; the last is shown. */
data class SearchResults(
    val sections: SearchSections = SearchSections(),
    val pages: List<SearchPage> = emptyList(),
) {
    fun seeAll(section: SearchSection) = copy(pages = pages + SearchPage.All(section))

    fun opened(level: BrowseLevel) = copy(pages = pages + SearchPage.Opened(level))

    /** Closes the page shown; null at the sections. */
    fun back(): SearchResults? = if (pages.isEmpty()) null else copy(pages = pages.dropLast(1))
}

/** How many of each section show before See all. */
private const val SHOWN = 4

/**
 * Search results: their sections, or the page opened from them. Tapping a song or station adds it
 * to the playlist ([onOpen]); an artist or album opens ([onOpen] too); See all shows all of a
 * section. On a page, a long press starts selecting items to add, or with [onDownload], download.
 * The sections are tagged "[tag]Sections", a page's list "[tag]Results", for tests.
 *
 * @param icon a provider's icon, by its name
 */
@Composable
internal fun SearchResultsContent(
    results: SearchResults,
    icon: (String) -> Bitmap?,
    onOpen: (SongSelectItem) -> Unit,
    onSeeAll: (SearchSection) -> Unit,
    onBack: () -> Unit,
    onAdd: (List<SongSelectItem>) -> Unit,
    onDownload: ((List<SongSelectItem>) -> Unit)?,
    tag: String,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    BackHandler(results.pages.isNotEmpty(), onBack)
    when (val page = results.pages.lastOrNull()) {
        null -> Sections(results.sections, icon, onOpen, onSeeAll, tag, modifier)
        is SearchPage.All -> {
            val level = BrowseLevel(
                null,
                page.section.kind,
                page.section.items(results.sections).map { it.toSongSelectItem(resources, page.section, icon) },
            )
            Page(level, resources.getString(page.section.title), opened = null, onOpen, onBack, onAdd, onDownload, tag, modifier)
        }
        is SearchPage.Opened ->
            Page(page.level, page.level.opened?.listTitle.orEmpty(), page.level.opened, onOpen, onBack, onAdd, onDownload, tag, modifier)
    }
}

/** The best match, then the first few of each section. */
@Composable
private fun Sections(
    sections: SearchSections,
    icon: (String) -> Bitmap?,
    onOpen: (SongSelectItem) -> Unit,
    onSeeAll: (SearchSection) -> Unit,
    tag: String,
    modifier: Modifier,
) {
    val resources = LocalResources.current
    val top = sections.top
    val topSection = SearchSection.entries.firstOrNull { it.items(sections).firstOrNull() == top }
    LazyColumn(modifier.fillMaxSize().testTag("${tag}Sections")) {
        if (top != null && topSection != null) {
            item(key = "top") { Header(stringResource(R.string.search_top_result)) }
            item(key = "topItem") {
                val item = remember(top) { top.toSongSelectItem(resources, topSection, icon, topMeta = true) }
                BrowseRow(topSection.kind, item, onClick = { onOpen(item) })
            }
        }
        for (section in SearchSection.entries) {
            val all = section.items(sections)
            // The top result shows once, above.
            val shown = if (top != null && all.firstOrNull() == top) all.drop(1) else all
            if (shown.isEmpty()) {
                continue
            }
            item(key = section) {
                Header(stringResource(section.title), seeAll = if (shown.size > SHOWN) ({ onSeeAll(section) }) else null)
            }
            items(shown.take(SHOWN), key = { section to it.selection }) { result ->
                val item = remember(result) { result.toSongSelectItem(resources, section, icon) }
                BrowseRow(section.kind, item, onClick = { onOpen(item) })
            }
        }
    }
}

@Composable
private fun Header(title: String, seeAll: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (seeAll != null) {
            val description = stringResource(R.string.search_see_all_of, title)
            TextButton(onClick = seeAll, modifier = Modifier.semantics { contentDescription = description }) {
                Text(stringResource(R.string.search_see_all))
            }
        }
    }
}

/**
 * A page of results: its title, how many, and for an [opened] artist or album, what to do with
 * all of it; then its items, which can be selected.
 */
@Composable
private fun Page(
    level: BrowseLevel,
    title: String,
    opened: SongSelectItem?,
    onOpen: (SongSelectItem) -> Unit,
    onBack: () -> Unit,
    onAdd: (List<SongSelectItem>) -> Unit,
    onDownload: ((List<SongSelectItem>) -> Unit)?,
    tag: String,
    modifier: Modifier,
) {
    var selection by remember(level) { mutableStateOf(emptySet<Int>()) }
    val selected = level.items.filterIndexed { index, _ -> index in selection }
    Column(modifier.fillMaxSize()) {
        if (selection.isNotEmpty()) {
            BrowseSelectionBar(
                count = selected.size,
                onClear = { selection = emptySet() },
                onAdd = {
                    onAdd(selected)
                    selection = emptySet()
                },
                onDownload = onDownload?.let { download ->
                    {
                        download(selected)
                        selection = emptySet()
                    }
                },
                tag = tag,
            )
        } else {
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, end = 16.dp)) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("${tag}Back")) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.library_back))
                    }
                    Column(Modifier.weight(1f).padding(start = 4.dp, top = 8.dp, bottom = 8.dp)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.semantics { heading() }.testTag("${tag}Title"),
                        )
                        Text(
                            pluralStringResource(R.plurals.number_items, level.items.size, level.items.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (opened != null) {
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onAdd(listOf(opened)) }, modifier = Modifier.testTag("${tag}AddAll")) {
                            Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.library_add_to_playlist), modifier = Modifier.padding(start = 8.dp))
                        }
                        if (onDownload != null) {
                            FilledTonalButton(onClick = { onDownload(listOf(opened)) }, modifier = Modifier.testTag("${tag}DownloadAll")) {
                                Icon(painterResource(R.drawable.ic_download), contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(stringResource(R.string.menu_download), modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }
        }
        BrowseItems(
            level,
            selection,
            onClick = { index, item ->
                if (selection.isEmpty()) {
                    onOpen(item)
                } else {
                    selection = if (index in selection) selection - index else selection + index
                }
            },
            onLongClick = { index -> selection = if (index in selection) selection - index else selection + index },
            tag = "${tag}Results",
        )
    }
}

/**
 * [this] as an item to show and open, in [section]: its second line says whose it is, or for the
 * [topMeta] top result, what it is too.
 */
internal fun SearchItem.toSongSelectItem(
    resources: Resources,
    section: SearchSection,
    icon: (String) -> Bitmap?,
    topMeta: Boolean = false,
): SongSelectItem {
    val unknown = resources.getString(R.string.unknown)
    val artist = artist.ifEmpty { unknown }
    val subtitle = when {
        topMeta -> when (section) {
            SearchSection.SONGS, SearchSection.OTHERS -> resources.getString(R.string.search_top_song, artist)
            SearchSection.ARTISTS -> resources.getString(R.string.search_top_artist)
            SearchSection.ALBUMS -> resources.getString(R.string.search_top_album, artist)
            SearchSection.STATIONS -> resources.getString(R.string.search_top_station, source)
        }
        else -> when (section) {
            SearchSection.SONGS, SearchSection.OTHERS -> "$artist / ${album.ifEmpty { unknown }}"
            SearchSection.ARTISTS -> resources.getQuantityString(R.plurals.search_number_albums, count ?: 0, count ?: 0)
            SearchSection.ALBUMS -> artist
            SearchSection.STATIONS -> source
        }
    }
    val result = this
    return SongSelectItem().apply {
        level = result.selection.size - 1
        selection = result.selection.toTypedArray()
        url = result.url
        listTitle = result.name.ifEmpty { unknown }
        listSubtitle = subtitle
        if (section == SearchSection.STATIONS) {
            this.icon = icon(result.source)
        }
    }
}
