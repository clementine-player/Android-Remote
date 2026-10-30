package de.qspool.clementineremote.backend.sync

import android.database.sqlite.SQLiteDatabase
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
    fun findsAlbumsByAlbumArtistOrArtist() {
        SQLiteDatabase.create(null).use { db ->
            db.execSQL("CREATE TABLE songs (artist TEXT, albumartist TEXT, album TEXT, genre TEXT)")
            db.execSQL("INSERT INTO songs VALUES ('Pixies', '', 'Doolittle', 'Rock')")
            db.execSQL("INSERT INTO songs VALUES ('Blur', 'Various Artists', 'Now 30', 'Pop')")
            db.execSQL("INSERT INTO songs VALUES ('Oasis', 'Various Artists', 'Now 30', 'Pop')")
            db.execSQL("INSERT INTO songs VALUES ('Blur', '', 'Parklife', 'Pop')")
            db.execSQL("INSERT INTO songs VALUES ('Blur', '', '', 'Pop')")

            assertEquals(
                listOf("Various Artists" to "Now 30"),
                SyncSources.albums(db, listOf("artist", "album"), listOf("Blur", "Now 30")),
            )
            assertEquals(
                listOf("Blur" to "Parklife", "Various Artists" to "Now 30"),
                SyncSources.albums(db, listOf("genre"), listOf("Pop")),
            )
        }
    }
}
