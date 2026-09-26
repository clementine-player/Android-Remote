package de.qspool.clementineremote.ui.browse

import de.qspool.clementineremote.backend.database.DynamicSongQuery
import de.qspool.clementineremote.backend.database.SongSelectItem

/** What the items of a level group: their field decides their icon. */
enum class ItemKind { SOURCE, ARTIST, ALBUM, GENRE, YEAR, SONG }

/** One level of songs browsed: the items under what was opened (nothing at the top). */
data class BrowseLevel(
    /** What was opened to get here; null at the top. */
    val opened: SongSelectItem?,
    val kind: ItemKind,
    val items: List<SongSelectItem>,
)

/**
 * Songs in a database, browsed level by level as a [DynamicSongQuery] groups them: the library,
 * or a global search's results. Its methods query the database, so call them off the main thread.
 */
class SongBrowser(private val newQuery: () -> DynamicSongQuery) {

    /** The items under [opened] (the top level for null), matching [filter]. */
    fun level(opened: SongSelectItem?, filter: String = ""): BrowseLevel {
        val depth = opened?.let { it.level + 1 } ?: 0
        return query { query ->
            query.level = depth
            query.selection = opened?.selection ?: emptyArray()
            BrowseLevel(opened, kind(query, depth), query.selectData(filter))
        }
    }

    /** The songs [items] are or group. */
    fun songs(items: List<SongSelectItem>): List<SongSelectItem> = query { query ->
        val songLevel = query.maxLevels - 1
        items.flatMap { item ->
            if (item.level == songLevel) {
                listOf(item)
            } else {
                query.level = songLevel
                query.selection = item.selection
                query.selectData()
            }
        }
    }

    private fun <T> query(block: (DynamicSongQuery) -> T): T {
        val query = newQuery()
        query.openDatabase()
        try {
            return block(query)
        } finally {
            query.closeDatabase()
        }
    }

    private fun kind(query: DynamicSongQuery, level: Int): ItemKind = when {
        level == query.maxLevels - 1 -> ItemKind.SONG
        else -> when (query.getField(level)) {
            "search_provider" -> ItemKind.SOURCE
            "album" -> ItemKind.ALBUM
            "genre" -> ItemKind.GENRE
            "year" -> ItemKind.YEAR
            else -> ItemKind.ARTIST
        }
    }
}
