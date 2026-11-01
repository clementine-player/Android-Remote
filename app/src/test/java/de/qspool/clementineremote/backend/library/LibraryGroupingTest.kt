package de.qspool.clementineremote.backend.library

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import de.qspool.clementineremote.backend.database.DynamicSongQuery
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * The library groups artists as Clementine does: by album artist (the artist where there's none),
 * with the albums Clementine finds are compilations under Various artists.
 */
@RunWith(RobolectricTestRunner::class)
class LibraryGroupingTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val database = File(context.cacheDir, "grouping-test.db")

    /** The fields grouped by. Read by the query's constructor, so not a constructor argument of its own. */
    private var fields = emptyArray<String>()

    /** The library grouped by [fields], as [LibraryQuery] groups it. */
    private inner class Query : DynamicSongQuery(context) {
        override fun getSelectedFields() = fields
        override fun getSorting() = "ASC"
        override fun getTable() = "songs"
        override fun getExpression(field: String): String = LibraryQuery.expression(field)
        override fun getReadableDatabase(): SQLiteDatabase =
            SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    @Before
    fun setUp() {
        database.delete()
        SQLiteDatabase.openOrCreateDatabase(database, null).use { db ->
            db.execSQL(
                "CREATE TABLE songs (artist TEXT, albumartist TEXT, album TEXT, title TEXT, filename TEXT, " +
                    "disc INTEGER, track INTEGER, effective_compilation INTEGER)",
            )
            db.execSQL("INSERT INTO songs VALUES ('Blur', '', 'Parklife', 'Girls & Boys', 'file:///1', 1, 1, 0)")
            db.execSQL("INSERT INTO songs VALUES ('David Bowie', 'Queen', 'Hot Space', 'Under Pressure', 'file:///2', 1, 11, 0)")
            db.execSQL("INSERT INTO songs VALUES ('Queen', 'Queen', 'Hot Space', 'Staying Power', 'file:///3', 1, 1, 0)")
            // A compilation, with an album artist, that Clementine finds is one.
            db.execSQL("INSERT INTO songs VALUES ('Blur', 'Various', 'Now 30', 'Country House', 'file:///4', 1, 1, 1)")
            // Songs whose tags Clementine took from their file names ("01 - Title.mp3"), so each has
            // an artist of its own, and Clementine finds their album is a compilation.
            db.execSQL("INSERT INTO songs VALUES ('01', '', 'Powerage', 'Rock n Roll Damnation', 'file:///5', 1, 1, 1)")
            db.execSQL("INSERT INTO songs VALUES ('02', '', 'Powerage', 'Down Payment Blues', 'file:///6', 1, 2, 1)")
        }
    }

    @After
    fun tearDown() {
        database.delete()
    }

    private fun names(fields: Array<String>, level: Int = 0, vararg selection: String): List<String> {
        this.fields = fields
        val query = Query()
        query.level = level
        query.selection = arrayOf(*selection)
        query.openDatabase()
        try {
            return query.selectData().map { it.listTitle }
        } finally {
            query.closeDatabase()
        }
    }

    @Test
    fun groupsByAlbumArtistWithCompilationsUnderVariousArtistsFirst() {
        val fields = arrayOf("albumartist", "album", "title")
        assertEquals(listOf("Various artists", "Blur", "Queen"), names(fields))
        assertEquals(listOf("Now 30", "Powerage"), names(fields, 1, DynamicSongQuery.VARIOUS_ARTISTS))
        assertEquals(listOf("Parklife"), names(fields, 1, "Blur"))
        assertEquals(
            listOf("Rock n Roll Damnation", "Down Payment Blues"),
            names(fields, 2, DynamicSongQuery.VARIOUS_ARTISTS, "Powerage"),
        )
    }

    @Test
    fun groupsByArtistWithCompilationsUnderVariousArtists() {
        val fields = arrayOf("artist", "album", "title")
        assertEquals(listOf("Various artists", "Blur", "David Bowie", "Queen"), names(fields))
        assertEquals(listOf("Parklife"), names(fields, 1, "Blur"))
    }
}
