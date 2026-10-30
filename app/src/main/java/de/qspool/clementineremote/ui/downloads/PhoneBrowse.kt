package de.qspool.clementineremote.ui.downloads

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.qspool.clementineremote.R
import de.qspool.clementineremote.ui.browse.BrowseTile

/** A level of the songs on this phone, under the artists at the top, as the library has. */
sealed interface BrowsePage {
    /** An artist's albums, then their songs on no album. */
    data class Artist(val name: String) : BrowsePage

    data class Album(val title: String, val artist: String) : BrowsePage
}

/** An album on this phone: its songs there with its title and artist, by track. */
data class PhoneAlbum(val title: String, val artist: String, val songs: List<DownloadedSong>)

/** The albums [songs] are on, by title. Songs on no album aren't on any. */
internal fun albums(songs: List<DownloadedSong>): List<PhoneAlbum> = songs
    .filter { it.album.isNotEmpty() }
    .groupBy { it.album to it.artist }
    // Songs without a track number keep their order, by file name.
    .map { (key, songs) -> PhoneAlbum(key.first, key.second, songs.sortedBy { it.track }) }
    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })

/** The artists of [songs], by name. */
internal fun artists(songs: List<DownloadedSong>): List<String> = songs
    .map { it.artist }
    .filter { it.isNotEmpty() }
    .distinct()
    .sortedWith(String.CASE_INSENSITIVE_ORDER)

/** How many items an artist has on this phone, as the library counts them: albums, and songs on none. */
private fun itemCount(artist: String, songs: List<DownloadedSong>, albums: List<PhoneAlbum>): Int =
    albums.count { it.artist == artist } + songs.count { it.artist == artist && it.album.isEmpty() }

/**
 * The top level of the songs on this phone, as the library's: its artists to open, then the songs
 * by no artist, to play. They're under a heading when other sections show above them.
 */
internal fun LazyListScope.phoneArtists(
    songs: List<DownloadedSong>,
    playing: String?,
    headed: Boolean,
    onOpen: (BrowsePage) -> Unit,
    onPlay: (List<DownloadedSong>, Int) -> Unit,
) {
    if (songs.isEmpty()) {
        return
    }
    if (headed) {
        item { Heading(R.string.downloads_on_phone) }
    }
    val albums = albums(songs)
    items(artists(songs), key = { "artist/$it" }) { artist ->
        val count = itemCount(artist, songs, albums)
        OpenRow(artist, pluralStringResource(R.plurals.number_items, count, count), R.drawable.ic_person) {
            onOpen(BrowsePage.Artist(artist))
        }
    }
    songRows(songs.filter { it.artist.isEmpty() }, playing, onPlay)
}

/**
 * A level of the songs on this phone, from [songs], laid out as the library's: its name, what's
 * in it, Play and Shuffle, then the albums to open or the songs to play on the phone. The song
 * playing is marked.
 */
@Composable
internal fun PhoneBrowseContent(
    page: BrowsePage,
    songs: List<DownloadedSong>,
    /** The item key of the song playing on this phone. */
    playing: String?,
    onOpen: (BrowsePage) -> Unit,
    onBack: () -> Unit,
    onPlay: (songs: List<DownloadedSong>, index: Int) -> Unit,
    onShuffle: (List<DownloadedSong>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val albums = remember(songs) { albums(songs) }
    Column(modifier.fillMaxSize()) {
        when (page) {
            is BrowsePage.Artist -> {
                val own = albums.filter { it.artist == page.name }
                // The albums' songs in their order, then those on no album.
                val loose = songs.filter { it.artist == page.name && it.album.isEmpty() }
                val inOrder = own.flatMap { it.songs } + loose
                Opened(page.name, own.size + loose.size, inOrder, onBack, onPlay, onShuffle)
                LazyColumn(Modifier.fillMaxSize().testTag("phoneBrowse")) {
                    items(own, key = { "album/${it.title}" }) { album ->
                        val count = pluralStringResource(R.plurals.number_items, album.songs.size, album.songs.size)
                        OpenRow(album.title, count, R.drawable.ic_album) { onOpen(BrowsePage.Album(album.title, album.artist)) }
                    }
                    songRows(loose, playing) { _, index -> onPlay(inOrder, inOrder.size - loose.size + index) }
                }
            }
            is BrowsePage.Album -> {
                val albumSongs = albums.firstOrNull { it.title == page.title && it.artist == page.artist }?.songs.orEmpty()
                Opened(page.title, albumSongs.size, albumSongs, onBack, onPlay, onShuffle)
                LazyColumn(Modifier.fillMaxSize().testTag("phoneBrowse")) {
                    songRows(albumSongs, playing, onPlay)
                }
            }
        }
    }
}

/** The header of what's open, as the library's: its name, how many items, and Play and Shuffle. */
@Composable
private fun Opened(
    title: String,
    count: Int,
    songs: List<DownloadedSong>,
    onBack: () -> Unit,
    onPlay: (List<DownloadedSong>, Int) -> Unit,
    onShuffle: (List<DownloadedSong>) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, end = 16.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("phoneBrowseBack")) {
                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.library_back))
            }
            Column(Modifier.weight(1f).padding(start = 4.dp, top = 8.dp, bottom = 8.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() }.testTag("phoneBrowseTitle"),
                )
                Text(
                    pluralStringResource(R.plurals.number_items, count, count),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (songs.isNotEmpty()) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onPlay(songs, 0) }, modifier = Modifier.testTag("phoneBrowsePlay")) {
                    Icon(painterResource(R.drawable.ic_player_play), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.sync_play), modifier = Modifier.padding(start = 8.dp))
                }
                FilledTonalButton(onClick = { onShuffle(songs) }, modifier = Modifier.testTag("phoneBrowseShuffle")) {
                    Icon(painterResource(R.drawable.ic_player_shuffle), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.sync_shuffle), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/** An artist or album to open, as the library shows them. */
@Composable
private fun OpenRow(title: String, details: String, @DrawableRes icon: Int, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(details, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { BrowseTile(icon, playable = false) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick).testTag("phoneBrowseRow"),
    )
}

/** [songs] to play from the one tapped, on this phone, as the library shows songs. */
private fun LazyListScope.songRows(
    songs: List<DownloadedSong>,
    playing: String?,
    onPlay: (List<DownloadedSong>, Int) -> Unit,
) {
    itemsIndexed(songs, key = { _, song -> "song/${song.uri}" }) { index, song ->
        val isPlaying = song.uri.toString() == playing
        ListItem(
            headlineContent = {
                Text(
                    song.title,
                    color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                val unknown = stringResource(R.string.unknown)
                Text(
                    "${song.artist.ifEmpty { unknown }} / ${song.album.ifEmpty { unknown }}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingContent = { BrowseTile(if (isPlaying) R.drawable.ic_player_play else R.drawable.ic_music_note, playable = true) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier
                .clickable { onPlay(songs, index) }
                .semantics { selected = isPlaying }
                .testTag("phoneBrowseSong"),
        )
    }
}
