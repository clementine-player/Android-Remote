package de.qspool.clementineremote.ui.search

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import de.qspool.clementineremote.App
import de.qspool.clementineremote.backend.Clementine
import de.qspool.clementineremote.backend.database.DynamicSongQuery
import de.qspool.clementineremote.backend.pb.ClementineMessage
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.SongMetadata
import de.qspool.clementineremote.ui.browse.ItemKind
import de.qspool.clementineremote.ui.theme.ClementineTheme
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File

/** Search asks Clementine, then browses its results by source, artist and song. */
@RunWith(RobolectricTestRunner::class)
class SearchScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var database: File

    /** Results grouped by source, then artist, over a database of three results of search 7. */
    inner class TestQuery(private val id: Int) : DynamicSongQuery(context) {
        override fun getSelectedFields() = arrayOf("search_provider", "artist", "title")
        override fun getSorting() = "ASC"
        override fun getTable() = "results"
        override fun getHiddenWhere() = " global_search_id = $id"
        override fun getReadableDatabase(): SQLiteDatabase = SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READONLY)
        override fun getMatchesSubQuery(match: String) = ""
    }

    @Before
    fun setUp() {
        App.Clementine = Clementine()
        database = File(context.cacheDir, "search-test.db").apply { delete() }
        SQLiteDatabase.openOrCreateDatabase(database, null).use { db ->
            db.execSQL("CREATE TABLE results (global_search_id INTEGER, search_provider TEXT, artist TEXT, album TEXT, title TEXT, filename TEXT, disc INTEGER, track INTEGER)")
            db.execSQL("INSERT INTO results VALUES (7, 'Library', 'Erik Satie', 'Gymnopédies', 'Gymnopédie No. 1', 'satie-1', 1, 1)")
            db.execSQL("INSERT INTO results VALUES (7, 'Library', 'Erik Satie', 'Gymnopédies', 'Gymnopédie No. 2', 'satie-2', 1, 2)")
            db.execSQL("INSERT INTO results VALUES (7, 'Jamendo', 'Someone', 'Covers', 'Gymnopédie No. 1', 'jamendo-1', 1, 1)")
            db.execSQL("INSERT INTO results VALUES (6, 'Library', 'Old search', 'Old', 'Old song', 'old', 1, 1)")
        }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun viewModel(sent: MutableList<ClementineMessage>) = SearchViewModel(
        send = { sent += it },
        newQuery = { id -> TestQuery(id) },
        songFromUrl = { url -> SongMetadata.newBuilder().setFilename(url).setTitle(url).build() },
        io = Dispatchers.Unconfined,
        listenToClementine = false,
    )

    @Test
    fun searchesAndBrowsesTheResults() {
        val sent = mutableListOf<ClementineMessage>()
        val search = viewModel(sent)

        search.search(" Gymnopédie ")
        assertEquals(MsgType.GLOBAL_SEARCH, sent.single().messageType)
        assertEquals("Gymnopédie", sent.single().message.requestGlobalSearch.query)
        assertTrue(search.state.value.searching)

        search.finished(7)
        idle()
        var shown = search.state.value.shown!!
        assertFalse(search.state.value.searching)
        assertEquals(ItemKind.SOURCE, shown.kind)
        assertEquals(listOf("Jamendo", "Library"), shown.items.map { it.listTitle })

        search.open(shown.items[1])
        idle()
        shown = search.state.value.shown!!
        assertEquals(ItemKind.ARTIST, shown.kind)
        assertEquals(listOf("Erik Satie"), shown.items.map { it.listTitle })

        search.open(shown.items[0])
        idle()
        shown = search.state.value.shown!!
        assertEquals(ItemKind.SONG, shown.kind)
        assertEquals(2, shown.items.size)

        // A song is added to the playlist, as Clementine described it.
        search.open(shown.items[1])
        idle()
        assertEquals(MsgType.INSERT_URLS, sent.last().messageType)
        assertEquals(listOf("satie-2"), sent.last().message.requestInsertUrls.songsList.map { it.filename })

        assertTrue(search.back())
        assertTrue(search.back())
        assertFalse(search.back())
    }

    @Test
    fun addsEverySongOfASource() {
        val sent = mutableListOf<ClementineMessage>()
        val search = viewModel(sent)
        search.finished(7)
        idle()

        val library = search.state.value.shown!!.items.first { it.listTitle == "Library" }
        search.addToPlaylist(listOf(library))
        idle()

        assertEquals(listOf("satie-1", "satie-2"), sent.single().message.requestInsertUrls.songsList.map { it.filename })
    }

    @Test
    fun theSearchBarSearches() {
        val searched = mutableListOf<String>()
        compose.setContent {
            ClementineTheme(dynamicColor = false) {
                SearchContent(SearchState(), onSearch = { searched += it }, onOpen = {}, onBack = {}, onAdd = {})
            }
        }

        compose.onNodeWithTag("searchEmpty").assertIsDisplayed()
        compose.onNodeWithTag("searchField").performTextInput("nocturne")
        compose.onNodeWithTag("searchField").performImeAction()

        assertEquals(listOf("nocturne"), searched)
    }

    @Test
    fun showsSearchingAndThenTheResults() {
        val sent = mutableListOf<ClementineMessage>()
        val search = viewModel(sent)
        search.search("Gymnopédie")
        compose.setContent {
            ClementineTheme(dynamicColor = false) {
                SearchScreen(search)
            }
        }
        compose.onNodeWithTag("searchProgress").assertIsDisplayed()

        search.finished(7)
        compose.waitForIdle()
        compose.onNodeWithText("Library").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("searchTitle").assertIsDisplayed()
        compose.onNodeWithText("Erik Satie").assertIsDisplayed()
    }
}
