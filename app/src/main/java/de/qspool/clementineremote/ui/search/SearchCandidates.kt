package de.qspool.clementineremote.ui.search

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import de.qspool.clementineremote.backend.globalsearch.GlobalSearchDatabaseHelper
import de.qspool.clementineremote.backend.library.LibraryDatabaseHelper
import de.qspool.clementineremote.ui.browse.ItemKind

/** The songs of global search [id], in the order they came, from Clementine's results in [db]. */
fun globalSearchCandidates(db: SQLiteDatabase, id: Int): List<SearchCandidate> =
    db.rawQuery(
        "SELECT search_provider, $GROUP_ARTIST, album, title, artist, filename, is_local " +
            "FROM ${GlobalSearchDatabaseHelper.TABLE_NAME} WHERE global_search_id = ? ORDER BY rowid",
        arrayOf(id.toString()),
    ).use { cursor ->
        cursor.map { SearchCandidate(song(it, fields = 4), isLocal = it.getInt(6) != 0) }
    }

/**
 * The songs of the library in [db] matching [text], as Clementine's global search matches them:
 * each word starts a word of some field.
 */
fun librarySearchCandidates(db: SQLiteDatabase, text: String): List<SearchCandidate> {
    val columns = db.rawQuery("PRAGMA table_info(${LibraryDatabaseHelper.SONGS_FTS})", null).use { cursor ->
        cursor.map { it.getString(1).lowercase() }.toSet()
    }
    val match = fullTextQuery(text, columns)
    if (match.isEmpty()) {
        return emptyList()
    }
    return db.rawQuery(
        "SELECT $GROUP_ARTIST, album, title, artist, CAST(filename AS TEXT) " +
            "FROM ${LibraryDatabaseHelper.SONGS_FTS} WHERE ${LibraryDatabaseHelper.SONGS_FTS} MATCH ?",
        arrayOf(match),
    ).use { cursor ->
        cursor.map { SearchCandidate(song(it, fields = 3)) }
    }
}

/**
 * [text] as a full-text query, as Clementine makes one: every word must start a word of some
 * field, or of the field it's prefixed with ("artist:satie", for one of [columns]). Words are
 * quoted, so none is an operator such as OR.
 */
internal fun fullTextQuery(text: String, columns: Set<String>): String =
    text.trim().split(Regex("\\s+")).flatMap { token ->
        var value = token
        var column = ""
        val colon = token.indexOf(':')
        if (colon >= 0) {
            val name = token.substring(0, colon).lowercase()
            if (name in columns) {
                column = "$name:"
            }
            value = token.substring(colon + 1)
        }
        // A field's word can't be quoted, but after it, isn't an operator either.
        value.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
            .map { if (column.isEmpty()) "\"$it*\"" else "$column$it*" }
    }.joinToString(" ")

/**
 * A song from a row of [fields] browsed by (the last its title), then its artist and URL. Its
 * URL is kept as Clementine sent it; browsing decodes it.
 */
private fun song(cursor: Cursor, fields: Int): SearchItem {
    val values = (0 until fields).map { cursor.getString(it).orEmpty() }
    return SearchItem(
        ItemKind.SONG,
        values,
        url = cursor.getString(fields + 1).orEmpty(),
        artist = cursor.getString(fields).orEmpty(),
        album = values[fields - 2],
    )
}

private fun <T> Cursor.map(transform: (Cursor) -> T): List<T> {
    val items = mutableListOf<T>()
    while (moveToNext()) {
        items += transform(this)
    }
    return items
}
