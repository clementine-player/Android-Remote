package de.qspool.clementineremote.ui.search

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import de.qspool.clementineremote.App
import de.qspool.clementineremote.backend.Clementine
import de.qspool.clementineremote.backend.database.DynamicSongQuery
import de.qspool.clementineremote.backend.globalsearch.GlobalSearchDatabaseHelper
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

/** Search asks Clementine, then shows its results in sections by what matched. */
@RunWith(RobolectricTestRunner::class)
class SearchScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var database: File

    /** Results of search [id] opened by source, album artist, album and song, as the app opens them. */
    inner class TestQuery(private val id: Int) : DynamicSongQuery(context) {
        override fun getSelectedFields() = arrayOf("search_provider", GROUP_ARTIST, "album", "title")
        override fun getSorting() = "ASC"
        override fun getTable() = GlobalSearchDatabaseHelper.TABLE_NAME
        override fun getHiddenWhere() = " global_search_id = $id"
        override fun getReadableDatabase(): SQLiteDatabase = open()
    }

    private fun open() = SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READONLY)

    @Before
    fun setUp() {
        App.Clementine = Clementine()
        database = File(context.cacheDir, "search-test.db").apply { delete() }
        SQLiteDatabase.openOrCreateDatabase(database, null).use { db ->
            db.execSQL("CREATE TABLE ${GlobalSearchDatabaseHelper.TABLE_NAME} (global_search_id INTEGER, search_provider TEXT, artist TEXT, albumartist TEXT, album TEXT, title TEXT, filename TEXT, is_local INTEGER, disc INTEGER, track INTEGER)")
            db.execSQL("INSERT INTO ${GlobalSearchDatabaseHelper.TABLE_NAME} VALUES (7, 'Library', 'Erik Satie', '', 'Gymnopédies', 'Gymnopédie No. 1', 'satie-1', 1, 1, 1)")
            db.execSQL("INSERT INTO ${GlobalSearchDatabaseHelper.TABLE_NAME} VALUES (7, 'Library', 'Erik Satie', '', 'Gymnopédies', 'Gymnopédie No. 2', 'satie-2', 1, 1, 2)")
            db.execSQL("INSERT INTO ${GlobalSearchDatabaseHelper.TABLE_NAME} VALUES (7, 'Radio-Browser.info', 'Gymnopédie FM', '', '', 'Gymnopédie FM', 'http://radio', 0, 0, 0)")
            db.execSQL("INSERT INTO ${GlobalSearchDatabaseHelper.TABLE_NAME} VALUES (6, 'Library', 'Old search', '', 'Old', 'Old song', 'old', 1, 1, 1)")
        }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun viewModel(sent: MutableList<ClementineMessage>) = SearchViewModel(
        send = { sent += it },
        newQuery = { id -> TestQuery(id) },
        candidates = { id -> open().use { globalSearchCandidates(it, id) } },
        songFromUrl = { url -> SongMetadata.newBuilder().setFilename(url).setTitle(url).build() },
        icon = { null },
        io = Dispatchers.Unconfined,
        listenToClementine = false,
    )

    @Test
    fun searchesAndShowsTheResultsInSections() {
        val sent = mutableListOf<ClementineMessage>()
        val search = viewModel(sent)

        search.search(" Gymnopédie ")
        assertEquals(MsgType.GLOBAL_SEARCH, sent.single().messageType)
        assertEquals("Gymnopédie", sent.single().message.requestGlobalSearch.query)
        assertTrue(search.state.value.searching)

        // Results show as they come, while Clementine still searches.
        search.update(7, finished = false)
        idle()
        var sections = search.state.value.results!!.sections
        assertTrue(search.state.value.searching)
        assertEquals(listOf("Gymnopédie No. 1", "Gymnopédie No. 2"), sections.songs.map { it.name })
        assertEquals(listOf("Gymnopédies"), sections.albums.map { it.name })
        assertEquals(listOf("Gymnopédie FM"), sections.stations.map { it.name })
        assertTrue(sections.others.isEmpty())

        search.update(7, finished = true)
        idle()
        assertFalse(search.state.value.searching)
    }

    @Test
    fun opensAnAlbumAndAddsItsSongs() {
        val sent = mutableListOf<ClementineMessage>()
        val search = viewModel(sent)
        search.search("Gymnopédies")
        search.update(7, finished = true)
        idle()

        val album = search.state.value.results!!.sections.albums.single()
        search.open(album.toSongSelectItem(context.resources, SearchSection.ALBUMS, icon = { null }))
        idle()
        val page = search.state.value.results!!.pages.single() as SearchPage.Opened
        assertEquals(ItemKind.SONG, page.level.kind)
        assertEquals(listOf("Gymnopédie No. 1", "Gymnopédie No. 2"), page.level.items.map { it.listTitle })

        // A song is added to the playlist, as Clementine described it.
        search.open(page.level.items[1])
        idle()
        assertEquals(MsgType.INSERT_URLS, sent.last().messageType)
        assertEquals(listOf("satie-2"), sent.last().message.requestInsertUrls.songsList.map { it.filename })

        // So is all of the album.
        search.addToPlaylist(listOf(page.level.opened!!))
        idle()
        assertEquals(listOf("satie-1", "satie-2"), sent.last().message.requestInsertUrls.songsList.map { it.filename })

        assertTrue(search.back())
        assertFalse(search.back())
    }

    @Test
    fun aSongOpenedPlaysUnlessClementineIsPlaying() {
        val sent = mutableListOf<ClementineMessage>()
        val search = viewModel(sent)
        search.search("Gymnopédie")
        search.update(7, finished = true)
        idle()
        val song = search.state.value.results!!.sections.songs.first()
            .toSongSelectItem(context.resources, SearchSection.SONGS, icon = { null })
        sent.clear()

        search.open(song)
        App.Clementine.state = Clementine.State.PAUSE
        search.open(song)
        App.Clementine.state = Clementine.State.PLAY
        search.open(song)
        // Adding without opening never plays.
        App.Clementine.state = Clementine.State.STOP
        search.addToPlaylist(listOf(song))
        idle()

        assertEquals(listOf(true, true, false, false), sent.map { it.message.requestInsertUrls.playNow })
    }

    @Test
    fun seesAllOfASection() {
        val search = viewModel(mutableListOf())
        search.search("Gymnopédie")
        search.update(7, finished = true)
        idle()

        search.seeAll(SearchSection.SONGS)
        assertEquals(SearchPage.All(SearchSection.SONGS), search.state.value.results!!.pages.single())
        assertTrue(search.back())
    }

    @Test
    fun theSearchBarSearches() {
        val searched = mutableListOf<String>()
        compose.setContent {
            ClementineTheme(dynamicColor = false) {
                SearchContent(SearchState(), onSearch = { searched += it }, onOpen = {}, onSeeAll = {}, onBack = {}, onAdd = { _, _ -> })
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
        search.search("Gymnopédies")
        compose.setContent {
            ClementineTheme(dynamicColor = false) {
                SearchScreen(search)
            }
        }
        compose.onNodeWithTag("searchProgress").assertIsDisplayed()

        search.update(7, finished = true)
        compose.waitForIdle()
        // The album matched best.
        compose.onNodeWithText("Top result").assertIsDisplayed()
        compose.onNodeWithText("Album • Erik Satie").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("searchTitle").assertTextEquals("Gymnopédies")
        compose.onNodeWithText("Gymnopédie No. 2").assertIsDisplayed()
    }

    @Test
    fun saysWhenNothingWasFound() {
        compose.setContent {
            ClementineTheme(dynamicColor = false) {
                SearchContent(
                    SearchState(searchedFor = "nothing", results = SearchResults()),
                    onSearch = {}, onOpen = {}, onSeeAll = {}, onBack = {}, onAdd = { _, _ -> },
                )
            }
        }
        compose.onNodeWithTag("searchNoResults").assertIsDisplayed()
    }
}
