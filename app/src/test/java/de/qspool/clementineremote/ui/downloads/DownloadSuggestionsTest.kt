package de.qspool.clementineremote.ui.downloads

import android.database.sqlite.SQLiteDatabase
import de.qspool.clementineremote.backend.player.MyPlaylist
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Suggestions come from the play counts in Clementine's library, and from its playlists. */
@RunWith(RobolectricTestRunner::class)
class DownloadSuggestionsTest {

    private val library = SQLiteDatabase.create(null).apply {
        // The columns of Clementine's songs table that suggestions read.
        execSQL(
            "CREATE TABLE songs (artist TEXT, albumartist TEXT, album TEXT, title TEXT, filename TEXT, " +
                "disc INTEGER, track INTEGER, playcount INTEGER NOT NULL DEFAULT 0, " +
                "lastplayed INTEGER NOT NULL DEFAULT -1, ctime INTEGER, filesize INTEGER)",
        )
    }

    @After
    fun close() = library.close()

    private fun song(
        artist: String, album: String, track: Int, plays: Int = 0,
        albumArtist: String = "", lastPlayed: Long = -1, added: Long = 0,
    ) = library.execSQL(
        "INSERT INTO songs VALUES (?, ?, ?, ?, ?, 1, ?, ?, ?, ?, 1000000)",
        arrayOf<Any>(artist, albumArtist, album, "Track $track", "file:///music/$album/$track.flac", track, plays, lastPlayed, added),
    )

    @Test
    fun suggestsTheMostPlayedAlbums() {
        song("Claude Debussy", "Suite bergamasque", 1, plays = 3)
        song("Claude Debussy", "Suite bergamasque", 2, plays = 4)
        song("Erik Satie", "Gymnopédies", 1, plays = 9)
        song("Maurice Ravel", "Boléro", 1)
        // A compilation: filed under its album artist, not each song's.
        song("Chopin", "Piano Favourites", 1, plays = 2, albumArtist = "Various Artists")
        song("Liszt", "Piano Favourites", 2, plays = 2, albumArtist = "Various Artists")

        val (albums, mostPlayed) = DownloadSuggestions.albums(library)

        assertTrue(mostPlayed)
        assertEquals(listOf("Gymnopédies", "Suite bergamasque", "Piano Favourites"), albums.map { it.album })
        assertEquals(AlbumSuggestion("Claude Debussy", "Suite bergamasque", songs = 2, plays = 7, bytes = 2_000_000), albums[1])
        assertEquals("Various Artists", albums[2].artist)
    }

    @Test
    fun equalPlaysGoByWhatWasPlayedLast() {
        song("A", "Earlier", 1, plays = 5, lastPlayed = 100)
        song("B", "Later", 1, plays = 5, lastPlayed = 200)

        assertEquals(listOf("Later", "Earlier"), DownloadSuggestions.albums(library).first.map { it.album })
    }

    @Test
    fun withNothingPlayedSuggestsTheAlbumsAddedLast() {
        song("A", "Old", 1, added = 100)
        song("B", "New", 1, added = 300)
        song("C", "Middle", 1, added = 200)

        val (albums, mostPlayed) = DownloadSuggestions.albums(library, limit = 2)

        assertFalse(mostPlayed)
        assertEquals(listOf("New", "Middle"), albums.map { it.album })
    }

    @Test
    fun anAlbumDownloadsItsSongsInOrder() {
        song("Claude Debussy", "Suite bergamasque", 2)
        song("Claude Debussy", "Suite bergamasque", 1)
        song("Erik Satie", "Gymnopédies", 1)
        val album = DownloadSuggestions.albums(library).first.first { it.album == "Suite bergamasque" }

        assertEquals(
            listOf("file:///music/Suite bergamasque/1.flac", "file:///music/Suite bergamasque/2.flac"),
            DownloadSuggestions.urls(library, album),
        )
    }

    @Test
    fun aLibraryWithoutPlayCountsSuggestsNoAlbums() {
        val old = SQLiteDatabase.create(null)
        old.execSQL("CREATE TABLE songs (artist TEXT, album TEXT, title TEXT, filename TEXT)")

        assertEquals(emptyList<AlbumSuggestion>() to false, DownloadSuggestions.albums(old))
        old.close()
    }

    @Test
    fun suggestsThePlaylistPlayingThenTheFavourites() {
        fun playlist(id: Int, songs: Int, active: Boolean = false, favorite: Boolean = false) = MyPlaylist().apply {
            this.id = id
            name = "Playlist $id"
            itemCount = songs
            isActive = active
            isFavorite = favorite
        }

        val suggested = DownloadSuggestions.playlists(
            listOf(playlist(1, 10), playlist(2, 5, favorite = true), playlist(3, 0, favorite = true), playlist(4, 8, active = true)),
        )

        // Not the empty one.
        assertEquals(listOf(4, 2, 1), suggested.map { it.id })
        assertTrue(suggested[0].playing)
        assertTrue(suggested[1].favorite)
    }
}
