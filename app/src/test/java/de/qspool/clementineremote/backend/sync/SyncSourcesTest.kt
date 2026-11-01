package de.qspool.clementineremote.backend.sync

import android.database.sqlite.SQLiteDatabase
import de.qspool.clementineremote.backend.database.DynamicSongQuery
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Sources are named as Clementine names them, and albums are found as Clementine files them. */
@RunWith(RobolectricTestRunner::class)
class SyncSourcesTest {

    @Test
    fun encodesAsClementineDoes() {
        assertEquals("library/album/AC%2FDC/Back%20in%20Black", SyncSources.album("AC/DC", "Back in Black"))
        assertEquals("library/artist/Bj%C3%B6rk", SyncSources.artist("Björk"))
        assertEquals("library/artist/a-b.c_d~e%2B%25%3F", SyncSources.artist("a-b.c_d~e+%?"))
        assertEquals("playlist/12", SyncSources.playlist(12))
    }

    @Test
    fun playlistsAreKeptUpToDateAndAlbumsAsTheyAre() {
        val playlist = SyncSources.playlistRule("studio", 7, "Road trip")
        assertEquals("playlist/7", playlist.sourceId)
        assertEquals("Road trip", playlist.title)
        assertEquals(RefreshPolicy.ON_CONNECT, playlist.refreshPolicy)
        assertEquals(RefreshPolicy.NEVER, SyncSources.albumRule("studio", "Pixies", "Doolittle").refreshPolicy)
        assertEquals(
            RefreshPolicy.ON_CONNECT,
            SyncSources.artistRule("studio", "Pixies", RefreshPolicy.ON_CONNECT).refreshPolicy,
        )
    }

    @Test
    fun findsAlbumsByAlbumArtistOrArtist() {
        SQLiteDatabase.create(null).use { db ->
            db.execSQL(
                "CREATE TABLE songs (artist TEXT, albumartist TEXT, album TEXT, genre TEXT, effective_compilation INTEGER)",
            )
            db.execSQL("INSERT INTO songs VALUES ('Pixies', '', 'Doolittle', 'Rock', 0)")
            db.execSQL("INSERT INTO songs VALUES ('Blur', 'Various Artists', 'Now 30', 'Pop', 0)")
            db.execSQL("INSERT INTO songs VALUES ('Oasis', 'Various Artists', 'Now 30', 'Pop', 0)")
            db.execSQL("INSERT INTO songs VALUES ('Blur', '', 'Parklife', 'Pop', 0)")
            db.execSQL("INSERT INTO songs VALUES ('Blur', '', '', 'Pop', 0)")
            // A compilation Clementine found, with no album artist: filed under each song's artist.
            db.execSQL("INSERT INTO songs VALUES ('Pulp', '', 'Britpop', 'Pop', 1)")
            db.execSQL("INSERT INTO songs VALUES ('Suede', '', 'Britpop', 'Pop', 1)")

            assertEquals(
                listOf("Various Artists" to "Now 30"),
                SyncSources.albums(db, listOf("artist", "album"), listOf("Blur", "Now 30")),
            )
            assertEquals(
                listOf("Blur" to "Parklife", "Pulp" to "Britpop", "Suede" to "Britpop", "Various Artists" to "Now 30"),
                SyncSources.albums(db, listOf("genre"), listOf("Pop")),
            )
            assertEquals(
                listOf("Blur" to "Parklife", "Various Artists" to "Now 30"),
                SyncSources.albums(db, listOf("albumartist"), listOf("Blur")).plus(
                    SyncSources.albums(db, listOf("albumartist"), listOf("Various Artists")),
                ),
            )
            assertEquals(
                listOf("Pulp" to "Britpop", "Suede" to "Britpop"),
                SyncSources.albums(db, listOf("albumartist"), listOf(DynamicSongQuery.VARIOUS_ARTISTS)),
            )
        }
    }
}
