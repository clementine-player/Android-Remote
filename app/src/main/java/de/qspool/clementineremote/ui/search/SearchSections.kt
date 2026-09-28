package de.qspool.clementineremote.ui.search

import de.qspool.clementineremote.ui.browse.ItemKind
import java.text.Normalizer
import java.util.Locale

/**
 * Songs are grouped into albums and artists by their album artist, else their artist, as
 * Clementine groups albums, so a compilation is one album. An SQL expression, so a database can
 * be browsed by it.
 */
const val GROUP_ARTIST = "IFNULL(NULLIF(albumartist, ''), artist)"

/**
 * A search result: a song, or an artist or album of the songs found. Its [selection] is the values
 * of the fields browsed by, down to its own: where it came from (for Clementine's global search),
 * then album artist, album and title.
 */
data class SearchItem(
    val kind: ItemKind,
    val selection: List<String>,
    /** The song's URL; for a group, one of its songs'. */
    val url: String,
    /** The song's own artist; for a group, its album artist. */
    val artist: String,
    val album: String,
    /** For an artist, how many albums; for an album, how many songs. */
    val count: Int? = null,
) {
    /** Its own value, such as the artist's name; empty when unknown. */
    val name: String get() = selection.last()

    /** Where it came from, for a global search's result. */
    val source: String get() = selection.first()
}

/** A song a search found, to put in a section. */
data class SearchCandidate(
    /** The song, at the songs' level. */
    val song: SearchItem,
    /** Whether it's a file, not a stream. */
    val isLocal: Boolean = true,
)

/** How well a search matched a field, weakest first. */
enum class MatchQuality {
    /** The words matched between this field and others. */
    SPREAD,

    /** Every word starts a word of the field. */
    WORDS,

    /** The field starts with the search. */
    PREFIX,

    /** The field is the search. */
    EXACT,
}

/**
 * A search's words, matched as Clementine's library search matches them: each word must start a
 * word of some field. Clementine sends the songs it found but not why they matched, so the app
 * works that out. It ignores accents as well as case, so it may explain more than Clementine
 * matched, never less.
 */
class SearchMatcher(query: String) {

    /** A field's text, split into words for matching. */
    class Field(text: String) {
        val words = words(text)

        val isEmpty get() = words.isEmpty()

        fun contains(word: String) = words.any { it.startsWith(word) }
    }

    val words: List<String> = query.trim().split(Regex("\\s+")).flatMap { token ->
        // Clementine takes "artist:name" to search only artists; the field name isn't a word.
        val colon = token.indexOf(':')
        val value = if (colon >= 0 && token.substring(0, colon).all { it.isLetter() }) token.substring(colon + 1) else token
        words(value)
    }

    private val phrase = words.joinToString(" ")

    /** How well [field] alone matches, or null if it doesn't have every word. */
    fun quality(field: Field): MatchQuality? {
        if (words.isEmpty() || !words.all(field::contains)) {
            return null
        }
        val text = field.words.joinToString(" ")
        return when {
            text == phrase -> MatchQuality.EXACT
            text.startsWith(phrase) -> MatchQuality.PREFIX
            else -> MatchQuality.WORDS
        }
    }

    /** Whether each word is in one of [fields]. */
    fun matchesAcross(fields: List<Field>) = words.isNotEmpty() && words.all { word -> fields.any { it.contains(word) } }

    /** Whether any word is in [field]. */
    fun touches(field: Field) = words.any(field::contains)

    companion object {
        /** [text]'s words, without case or accents: split where SQLite's full-text index splits them. */
        fun words(text: String): List<String> =
            Normalizer.normalize(text, Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
                .lowercase(Locale.ROOT)
                .split(Regex("[^\\p{L}\\p{N}]+"))
                .filter { it.isNotEmpty() }
    }
}

/**
 * A search's results, in sections by what matched, as music apps show them: songs whose titles
 * matched, artists and albums whose names did, radio stations, and the rest. Both Clementine's
 * global search and the library on the phone are shown this way.
 */
data class SearchSections(
    /** The best match, if one matched well: also the first of its section. */
    val top: SearchItem? = null,
    /** Songs whose titles matched, or whose title, artist and album did between them. */
    val songs: List<SearchItem> = emptyList(),
    val artists: List<SearchItem> = emptyList(),
    /** Albums whose names matched, or whose name and artist did between them. */
    val albums: List<SearchItem> = emptyList(),
    /** Results from internet services that aren't songs: radio streams. */
    val stations: List<SearchItem> = emptyList(),
    /** Songs that matched some other way, such as by genre or composer. */
    val others: List<SearchItem> = emptyList(),
) {
    val isEmpty get() = songs.isEmpty() && artists.isEmpty() && albums.isEmpty() && stations.isEmpty() && others.isEmpty()

    companion object {
        /**
         * The [songs] found by searching for [query], in sections. Artists and albums are items a
         * level and two levels above the songs.
         */
        fun of(query: String, songs: List<SearchCandidate>): SearchSections {
            val matcher = SearchMatcher(query)
            val found = mutableListOf<Pair<SearchItem, MatchQuality>>()
            val stations = mutableListOf<Pair<SearchItem, MatchQuality>>()
            val others = mutableListOf<SearchItem>()
            val artists = LinkedHashMap<List<String>, Group>()
            val albums = LinkedHashMap<List<String>, Group>()

            for (candidate in songs) {
                val song = candidate.song
                val level = song.selection.size - 1
                val groupArtist = song.selection[level - 2]
                val title = SearchMatcher.Field(song.name)
                val artist = SearchMatcher.Field(song.artist)
                val group = SearchMatcher.Field(groupArtist)
                val album = SearchMatcher.Field(song.album)

                // Internet radio: a stream has no album. Radio-Browser.info gives the station's
                // name as its artist too, so that isn't a sign.
                if (!candidate.isLocal && album.isEmpty) {
                    stations += song to (matcher.quality(title) ?: MatchQuality.SPREAD)
                    continue
                }

                var grouped = false
                matcher.quality(group)?.let { quality ->
                    val selection = song.selection.subList(0, level - 1)
                    artists.getOrPut(selection) { Group(song, selection, ItemKind.ARTIST) }.add(quality, song.album)
                    grouped = true
                }
                if (!album.isEmpty && matcher.matchesAcross(listOf(album, group))) {
                    val selection = song.selection.subList(0, level)
                    albums.getOrPut(selection) { Group(song, selection, ItemKind.ALBUM) }
                        .add(matcher.quality(album) ?: MatchQuality.SPREAD, song.url)
                    grouped = true
                }
                val quality = matcher.quality(title)
                when {
                    quality != null -> found += song to quality
                    matcher.matchesAcross(listOf(title, artist, group, album)) && (matcher.touches(title) || !grouped) ->
                        found += song to MatchQuality.SPREAD
                    !grouped -> others += song
                }
            }

            val ranked = listOf(
                ranked(artists.values.map { it.item to it.quality }),
                ranked(albums.values.map { it.item to it.quality }),
                ranked(found),
                ranked(stations),
            )
            // The best match of all, if it matched well: artists first, then albums, songs and
            // stations when as good.
            val firsts = ranked.mapNotNull { it.firstOrNull() }
            val best = firsts.maxOfOrNull { it.second }
            return SearchSections(
                top = if (best != null && best >= MatchQuality.WORDS) firsts.first { it.second == best }.first else null,
                artists = ranked[0].map { it.first },
                albums = ranked[1].map { it.first },
                songs = ranked[2].map { it.first },
                stations = ranked[3].map { it.first },
                others = others.sortedWith(inOrder),
            )
        }

        /** Items by how well they matched, best first, then [inOrder]. */
        private fun ranked(items: List<Pair<SearchItem, MatchQuality>>) =
            items.sortedWith(compareByDescending<Pair<SearchItem, MatchQuality>> { it.second }.thenBy(inOrder) { it.first })

        private val collator = java.text.Collator.getInstance().apply { strength = java.text.Collator.SECONDARY }

        /**
         * By name, artist and album, then the bigger group first, then where they came from, so
         * the order is the same every time.
         */
        private val inOrder: Comparator<SearchItem> = compareBy<SearchItem, String>(collator) { it.name }
            .thenBy(collator) { it.artist }
            .thenBy(collator) { it.album }
            .thenByDescending { it.count ?: 0 }
            .thenBy { it.selection.joinToString("\u0000") }
    }

    /** An artist or album of the results, and how well it matched. */
    private class Group(song: SearchItem, selection: List<String>, kind: ItemKind) {
        var item = SearchItem(
            kind,
            selection.toList(),
            song.url,
            artist = song.selection[song.selection.size - 3],
            album = if (kind == ItemKind.ALBUM) song.album else "",
            count = 0,
        )
        var quality = MatchQuality.SPREAD
        private val below = mutableSetOf<String>()

        fun add(quality: MatchQuality, value: String) {
            this.quality = maxOf(this.quality, quality)
            below += value
            item = item.copy(count = below.size)
        }
    }
}
