package de.qspool.clementineremote.ui.search

import de.qspool.clementineremote.ui.browse.ItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Search results go in sections by what matched, as the iOS remote puts them: these are the same
 * cases as its tests.
 */
class SearchSectionsTest {

    private fun song(
        title: String,
        artist: String = "",
        album: String = "",
        albumArtist: String = "",
        source: String = "Library",
        isLocal: Boolean = true,
    ) = SearchCandidate(
        SearchItem(
            ItemKind.SONG,
            listOf(source, albumArtist.ifEmpty { artist }, album, title),
            url = "file:///$artist/$album/$title",
            artist = artist,
            album = album,
        ),
        isLocal,
    )

    private val okComputer = listOf(
        song("Airbag", "Radiohead", "OK Computer"),
        song("Karma Police", "Radiohead", "OK Computer"),
        song("Creep", "Radiohead", "Pablo Honey"),
    )

    @Test
    fun aSongMatchedByTitleIsASong() {
        val found = SearchSections.of(
            "karma police",
            listOf(okComputer[1], song("Karma Police (Live)", "Radiohead", "I Might Be Wrong")),
        )
        assertEquals(listOf("Karma Police", "Karma Police (Live)"), found.songs.map { it.name })
        assertTrue(found.artists.isEmpty() && found.albums.isEmpty() && found.others.isEmpty())
        assertEquals(found.songs.first(), found.top)
        assertEquals("Radiohead", found.top?.artist)
    }

    @Test
    fun anArtistMatchedIsAnArtistWithItsAlbums() {
        val found = SearchSections.of("radiohead", okComputer)
        assertEquals(listOf("Radiohead"), found.artists.map { it.name })
        assertEquals(2, found.artists.first().count)
        assertEquals(listOf("OK Computer", "Pablo Honey"), found.albums.map { it.name })
        assertEquals(2, found.albums.first().count)
        // Its songs are in it, not listed.
        assertTrue(found.songs.isEmpty() && found.others.isEmpty())
        assertEquals(ItemKind.ARTIST, found.top?.kind)
        // An artist and an album are a level and two levels above the songs.
        assertEquals(listOf("Library", "Radiohead"), found.artists.first().selection)
        assertEquals(listOf("Library", "Radiohead", "OK Computer"), found.albums.first().selection)
    }

    @Test
    fun anAlbumMatchedIsAnAlbum() {
        val found = SearchSections.of("ok comp", okComputer.take(2))
        assertEquals(listOf("OK Computer"), found.albums.map { it.name })
        assertEquals("Radiohead", found.albums.first().artist)
        assertEquals(ItemKind.ALBUM, found.top?.kind)
        assertTrue(found.songs.isEmpty() && found.artists.isEmpty())
    }

    @Test
    fun wordsCanMatchDifferentFields() {
        val found = SearchSections.of("beatles help", listOf(song("Help!", "The Beatles", "Help!")))
        assertEquals(listOf("Help!"), found.songs.map { it.name })
        assertEquals(listOf("Help!"), found.albums.map { it.name })
        assertTrue(found.artists.isEmpty())
        // Nothing matched on its own, so nothing is the best.
        assertNull(found.top)
    }

    @Test
    fun compilationsAreGroupedByAlbumArtist() {
        val found = SearchSections.of(
            "bowie",
            listOf(
                song("Heroes", "David Bowie", "Now 80s", albumArtist = "Various Artists"),
                song("Starman", "David Bowie", "Ziggy Stardust"),
            ),
        )
        assertEquals(listOf("David Bowie"), found.artists.map { it.name })
        assertEquals(listOf("Ziggy Stardust"), found.albums.map { it.name })
        // On someone else's album, the song is listed.
        assertEquals(listOf("Heroes"), found.songs.map { it.name })
    }

    @Test
    fun accentsAndCaseDontMatter() {
        val found = SearchSections.of("GYMNOPEDIE", listOf(song("Gymnopédie No. 1", "Erik Satie", "Gymnopédies")))
        assertEquals(listOf("Gymnopédie No. 1"), found.songs.map { it.name })
        assertEquals(listOf("Gymnopédies"), found.albums.map { it.name })
    }

    @Test
    fun wordsMatchOnlyAtTheirStart() {
        val found = SearchSections.of("head", okComputer)
        assertTrue(found.artists.isEmpty() && found.songs.isEmpty())
        assertEquals(3, found.others.size)
    }

    @Test
    fun otherMatchesAreKept() {
        val found = SearchSections.of("jazz", listOf(song("So What", "Miles Davis")))
        assertEquals(listOf("So What"), found.others.map { it.name })
        assertFalse(found.isEmpty)
        assertNull(found.top)
    }

    @Test
    fun streamsAreStations() {
        val found = SearchSections.of("groove", listOf(song("Groove Salad", source = "SomaFM", isLocal = false)))
        assertEquals(listOf("Groove Salad"), found.stations.map { it.name })
        assertEquals("SomaFM", found.stations.first().source)
        assertEquals("Groove Salad", found.top?.name)
    }

    @Test
    fun radioBrowserStationsAreStations() {
        // Radio-Browser.info gives a station's name as its artist too.
        val found = SearchSections.of(
            "offsp",
            listOf(
                song("Self Esteem", "The Offspring", "Smash"),
                song("The Offspring", "The Offspring", source = "Radio-Browser.info", isLocal = false),
            ),
        )
        assertEquals(listOf("The Offspring"), found.stations.map { it.name })
        assertEquals(listOf(listOf("Library", "The Offspring")), found.artists.map { it.selection })
        assertTrue(found.songs.isEmpty())
        assertEquals(found.artists.first(), found.top)
    }

    @Test
    fun theBiggerOfTwoArtistsNamedAlikeComesFirst() {
        val found = SearchSections.of(
            "offspring",
            listOf(
                song("One", "The Offspring", "A", source = "Jamendo", isLocal = false),
                song("Two", "The Offspring", "B"),
                song("Three", "The Offspring", "C"),
            ),
        )
        assertEquals(
            listOf(listOf("Library", "The Offspring"), listOf("Jamendo", "The Offspring")),
            found.artists.map { it.selection },
        )
        assertEquals(found.artists.first(), found.top)
    }

    @Test
    fun fieldNamesAreNotWords() {
        assertEquals(listOf("Radiohead"), SearchSections.of("artist:radiohead", okComputer).artists.map { it.name })
    }

    @Test
    fun theLibraryHasNoSourceLevel() {
        val found = SearchSections.of(
            "radiohead",
            listOf(SearchCandidate(SearchItem(ItemKind.SONG, listOf("Radiohead", "OK Computer", "Airbag"), "file:///1", "Radiohead", "OK Computer"))),
        )
        assertEquals(listOf("Radiohead"), found.artists.first().selection)
        assertEquals(listOf("Radiohead", "OK Computer"), found.albums.first().selection)
    }
}
