package de.qspool.clementineremote.ui.downloads

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import de.qspool.clementineremote.backend.player.MyPlaylist

/** An album of Clementine's library worth downloading. */
data class AlbumSuggestion(
    /** The album artist, or the artist where the album has none. */
    val artist: String,
    val album: String,
    val songs: Int,
    /** How many times its songs were played in Clementine, all together. */
    val plays: Int,
    /** The size of its files on Clementine's computer; 0 when Clementine doesn't know. */
    val bytes: Long,
)

/** A playlist open in Clementine, worth downloading. */
data class PlaylistSuggestion(val id: Int, val name: String, val songs: Int, val playing: Boolean, val favorite: Boolean)

/** What an empty downloads screen suggests downloading. */
data class Suggestions(
    val albums: List<AlbumSuggestion> = emptyList(),
    /** Whether [albums] are the most played; otherwise they're the ones added last. */
    val mostPlayed: Boolean = false,
    val playlists: List<PlaylistSuggestion> = emptyList(),
) {
    val isEmpty: Boolean get() = albums.isEmpty() && playlists.isEmpty()
}

/**
 * Suggests what to download from what's on the phone already: the library synced from
 * Clementine, which is Clementine's own table of songs with their play counts, and the playlists
 * open in Clementine. The albums are those played most (added last, when nothing has been played
 * yet); the playlists are the one playing, then the favourites, then the others.
 */
object DownloadSuggestions {

    const val ALBUMS = 4
    const val PLAYLISTS = 3

    /** Songs, with the artist their album is filed under. */
    private const val SONGS = "(SELECT CASE WHEN albumartist <> '' THEN albumartist ELSE artist END AS album_artist, * FROM songs)"

    /** The library's albums worth downloading, and whether they're the most played. Reads the disk. */
    fun albums(library: SQLiteDatabase, limit: Int = ALBUMS): Pair<List<AlbumSuggestion>, Boolean> = try {
        val played = albums(library, "HAVING plays > 0 ORDER BY plays DESC, MAX(lastplayed) DESC", limit)
        if (played.isNotEmpty()) {
            played to true
        } else {
            albums(library, "ORDER BY MAX(ctime) DESC", limit) to false
        }
    } catch (e: SQLiteException) {
        // A library from a Clementine without play counts; nothing to go on.
        emptyList<AlbumSuggestion>() to false
    }

    private fun albums(library: SQLiteDatabase, order: String, limit: Int): List<AlbumSuggestion> =
        library.rawQuery(
            "SELECT album_artist, album, COUNT(*), SUM(MAX(playcount, 0)) AS plays, SUM(MAX(filesize, 0)) " +
                "FROM $SONGS WHERE album <> '' GROUP BY album_artist, album $order LIMIT $limit",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(AlbumSuggestion(cursor.getString(0).orEmpty(), cursor.getString(1), cursor.getInt(2), cursor.getInt(3), cursor.getLong(4)))
                }
            }
        }

    /** The URLs of [album]'s songs, in order, to download them. Reads the disk. */
    fun urls(library: SQLiteDatabase, album: AlbumSuggestion): List<String> =
        library.rawQuery(
            "SELECT cast(filename AS TEXT) FROM $SONGS WHERE album_artist = ? AND album = ? ORDER BY disc, track",
            arrayOf(album.artist, album.album),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    cursor.getString(0)?.let(::add)
                }
            }
        }

    /** The playlists worth downloading: not empty, the one playing first, then the favourites. */
    fun playlists(playlists: List<MyPlaylist>, limit: Int = PLAYLISTS): List<PlaylistSuggestion> =
        playlists
            .filter { !it.isClosed && it.itemCount > 0 }
            .sortedWith(compareByDescending<MyPlaylist> { it.isActive }.thenByDescending { it.isFavorite })
            .take(limit)
            .map { PlaylistSuggestion(it.id, it.name.orEmpty(), it.itemCount, it.isActive, it.isFavorite) }
}
