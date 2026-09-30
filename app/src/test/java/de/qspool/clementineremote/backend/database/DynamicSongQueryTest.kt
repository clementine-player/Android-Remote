package de.qspool.clementineremote.backend.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** The library lists names in order for the phone's language, whatever their case or accents. */
@RunWith(RobolectricTestRunner::class)
class DynamicSongQueryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val database = File(context.cacheDir, "sorting-test.db")

    /** ASC or DESC. Read by the query's constructor, so not a constructor argument of its own. */
    private var order = "ASC"

    private inner class Query : DynamicSongQuery(context) {
        override fun getSelectedFields() = arrayOf("artist", "album", "title")
        override fun getSorting(): String = order
        override fun getTable() = "songs"
        override fun getReadableDatabase(): SQLiteDatabase =
            SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    @Before
    fun setUp() {
        database.delete()
        SQLiteDatabase.openOrCreateDatabase(database, null).use { db ->
            db.execSQL("CREATE TABLE songs (artist TEXT, album TEXT, title TEXT, filename TEXT, disc INTEGER, track INTEGER)")
            for ((artist, album) in listOf(
                "ZZ Top" to "Eliminator", "abba" to "arrival", "Érik Satie" to "Gymnopédies", "Blondie" to "Parallel Lines",
            )) {
                db.execSQL("INSERT INTO songs VALUES (?, ?, 'Song', 'file:///song', 1, 1)", arrayOf(artist, album))
            }
            db.execSQL("INSERT INTO songs VALUES ('Various', 'zebra', 'Song', 'file:///song', 1, 1)")
            db.execSQL("INSERT INTO songs VALUES ('Various', 'Apple', 'Song', 'file:///song', 1, 1)")
            db.execSQL("INSERT INTO songs VALUES ('Various', 'Été', 'Song', 'file:///song', 1, 3)")
            db.execSQL("INSERT INTO songs VALUES ('Various', 'apple', 'Song', 'file:///song', 1, 1)")
            // An album's songs, in disc and track order whatever their names.
            db.execSQL("INSERT INTO songs VALUES ('Various', 'Été', 'b second', 'file:///song', 1, 2)")
            db.execSQL("INSERT INTO songs VALUES ('Various', 'Été', 'A first', 'file:///song', 1, 1)")
        }
    }

    @After
    fun tearDown() {
        database.delete()
    }

    /** The names at the query's current level, in the order it lists them. */
    private fun names(query: DynamicSongQuery): List<String> {
        query.openDatabase()
        try {
            return query.selectData().map { it.listTitle }
        } finally {
            query.closeDatabase()
        }
    }

    @Test
    fun sortsIgnoringCaseAndAccents() {
        assertEquals(listOf("abba", "Blondie", "Érik Satie", "Various", "ZZ Top"), names(Query()))
    }

    @Test
    fun sortsAnArtistsAlbumsIgnoringCaseAndAccents() {
        val query = Query().apply {
            level = 1
            selection = arrayOf("Various")
        }
        assertEquals(listOf("Apple", "apple", "Été", "zebra"), names(query))
    }

    @Test
    fun keepsAnAlbumsSongsInTrackOrder() {
        val query = Query().apply {
            level = 2
            selection = arrayOf("Various", "Été")
        }
        assertEquals(listOf("A first", "b second", "Song"), names(query))
    }

    @Test
    fun sortsBackwardsIgnoringCase() {
        order = "DESC"
        assertEquals(listOf("ZZ Top", "Various", "Érik Satie", "Blondie", "abba"), names(Query()))
    }
}
