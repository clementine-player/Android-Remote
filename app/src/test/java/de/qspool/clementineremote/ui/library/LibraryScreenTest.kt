package de.qspool.clementineremote.ui.library

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import de.qspool.clementineremote.App
import de.qspool.clementineremote.backend.Clementine
import de.qspool.clementineremote.backend.database.DynamicSongQuery
import de.qspool.clementineremote.backend.database.SongSelectItem
import de.qspool.clementineremote.backend.elements.DownloaderResult
import de.qspool.clementineremote.backend.listener.OnLibraryDownloadListener
import de.qspool.clementineremote.backend.pb.ClementineMessage
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType
import de.qspool.clementineremote.ui.browse.BrowseLevel
import de.qspool.clementineremote.ui.browse.ItemKind
import de.qspool.clementineremote.ui.theme.ClementineTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext
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

/** The library browses Clementine's library level by level and acts on what's picked. */
@RunWith(RobolectricTestRunner::class)
class LibraryScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var database: File

    /** A library grouped by artist, then album, over a database of three songs. */
    inner class TestQuery : DynamicSongQuery(context) {
        override fun getSelectedFields() = arrayOf("artist", "album", "title")
        override fun getSorting() = "ASC"
        override fun getTable() = "songs"
        override fun getReadableDatabase(): SQLiteDatabase = SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READONLY)
        override fun getMatchesSubQuery(match: String) =
            "(SELECT * FROM songs WHERE title LIKE '%$match%' OR artist LIKE '%$match%' OR album LIKE '%$match%')"
    }

    @Before
    fun setUp() {
        App.Clementine = Clementine()
        database = File(context.cacheDir, "library-test.db").apply { delete() }
        SQLiteDatabase.openOrCreateDatabase(database, null).use { db ->
            db.execSQL("CREATE TABLE songs (artist TEXT, album TEXT, title TEXT, filename TEXT, disc INTEGER, track INTEGER)")
            db.execSQL("INSERT INTO songs VALUES ('Frédéric Chopin', 'Nocturnes, Op. 9', 'Nocturne in B-flat minor', 'file:///chopin/1.ogg', 1, 1)")
            db.execSQL("INSERT INTO songs VALUES ('Frédéric Chopin', 'Nocturnes, Op. 9', 'Nocturne in E-flat major', 'file:///chopin/2.ogg', 1, 2)")
            db.execSQL("INSERT INTO songs VALUES ('Erik Satie', 'Gymnopédies', 'Gymnopédie No. 1', 'file:///satie/1.ogg', 1, 1)")
        }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun viewModel(sent: MutableList<ClementineMessage>, exists: Boolean = true) = LibraryViewModel(
        send = { sent += it },
        newQuery = { TestQuery() },
        io = Dispatchers.Unconfined,
        libraryExists = { exists },
    ).also { idle() }

    @Test
    fun browsesArtistsAlbumsAndSongs() {
        val sent = mutableListOf<ClementineMessage>()
        val library = viewModel(sent)

        var shown = library.state.value.shown!!
        assertEquals(LibraryStatus.Ready, library.state.value.status)
        assertEquals(ItemKind.ARTIST, shown.kind)
        assertEquals(listOf("Erik Satie", "Frédéric Chopin"), shown.items.map { it.listTitle })

        library.open(shown.items[1])
        idle()
        shown = library.state.value.shown!!
        assertEquals(ItemKind.ALBUM, shown.kind)
        assertEquals(listOf("Nocturnes, Op. 9"), shown.items.map { it.listTitle })

        library.open(shown.items[0])
        idle()
        shown = library.state.value.shown!!
        assertEquals(ItemKind.SONG, shown.kind)
        assertEquals(2, shown.items.size)

        // Opening a song adds it to the playlist.
        library.open(shown.items[0])
        idle()
        assertEquals(listOf(MsgType.INSERT_URLS), sent.map { it.messageType })
        assertEquals(listOf("file:///chopin/1.ogg"), sent[0].message.requestInsertUrls.urlsList)

        assertTrue(library.back())
        assertTrue(library.back())
        assertFalse(library.back())
    }

    @Test
    fun addsEverySongOfAnArtist() {
        val sent = mutableListOf<ClementineMessage>()
        val library = viewModel(sent)

        val chopin = library.state.value.shown!!.items.first { it.listTitle == "Frédéric Chopin" }
        library.addToPlaylist(listOf(chopin))
        idle()

        assertEquals(
            listOf("file:///chopin/1.ogg", "file:///chopin/2.ogg"),
            sent.single().message.requestInsertUrls.urlsList)
    }

    @Test
    fun filtersTheLevelShown() {
        val library = viewModel(mutableListOf())

        library.setFilter("Satie")
        idle()

        assertEquals(listOf("Erik Satie"), library.state.value.shown!!.items.map { it.listTitle })
    }

    @Test
    fun withoutALibraryOffersToDownloadIt() {
        val library = viewModel(mutableListOf(), exists = false)
        assertEquals(LibraryStatus.Missing, library.state.value.status)

        var downloads = 0
        val state = library.state.value
        compose.setContent {
            ClementineTheme(dynamicColor = false) {
                LibraryContent(state, {}, {}, { downloads++ }, {}, {})
            }
        }
        compose.onNodeWithTag("btnSyncLibrary").performClick()

        assertEquals(1, downloads)
    }

    private fun item(title: String, level: Int) = SongSelectItem().apply {
        listTitle = title
        listSubtitle = "1 item"
        this.level = level
        selection = arrayOf(title)
    }

    @Test
    fun anOpenedItemOffersToAddOrDownloadAllOfIt() {
        val album = item("Nocturnes, Op. 9", 1)
        val songs = listOf(item("Nocturne in B-flat minor", 2), item("Nocturne in E-flat major", 2))
        val done = mutableListOf<String>()
        compose.setContent {
            ClementineTheme(dynamicColor = false) {
                LibraryContent(
                    LibraryState(LibraryStatus.Ready, listOf(BrowseLevel(album, ItemKind.SONG, songs))),
                    onOpen = { done += "open ${it.listTitle}" },
                    onBack = { done += "back" },
                    onSyncLibrary = {},
                    onAdd = { items -> done += "add " + items.joinToString { it.listTitle } },
                    onDownload = { items -> done += "download " + items.joinToString { it.listTitle } },
                )
            }
        }

        compose.onNodeWithTag("libraryTitle").assertTextEquals("Nocturnes, Op. 9")
        compose.onNodeWithTag("libraryAddAll").performClick()
        compose.onNodeWithText("Nocturne in E-flat major").performClick()
        compose.onNodeWithText("Nocturne in B-flat minor").performTouchInput { longClick() }
        compose.onNodeWithTag("librarySelection").assertIsDisplayed()
        compose.onNodeWithTag("libraryDownload").performClick()
        compose.onNodeWithTag("libraryBack").performClick()

        assertEquals(
            listOf(
                "add Nocturnes, Op. 9",
                "open Nocturne in E-flat major",
                "download Nocturne in B-flat minor",
                "back",
            ),
            done)
    }

    @Test
    fun syncsTheLibraryWhenConnectedKeepingTheOldOneMeanwhile() {
        var listener: OnLibraryDownloadListener? = null
        val library = LibraryViewModel(
            send = {},
            newQuery = { TestQuery() },
            io = Dispatchers.Unconfined,
            startSync = { listener = it; null },
            connected = { true },
            libraryExists = { true },
        )
        idle()

        // Syncing straight away, with the library on the phone still there to browse.
        assertTrue(library.state.value.status is LibraryStatus.Syncing)
        assertEquals(listOf("Erik Satie", "Frédéric Chopin"), library.state.value.shown!!.items.map { it.listTitle })

        listener!!.OnLibraryDownloadFinished(DownloaderResult(0, DownloaderResult.DownloadResult.SUCCESSFUL))
        idle()
        assertEquals(LibraryStatus.Ready, library.state.value.status)
        assertEquals(2, library.state.value.shown!!.items.size)
    }

    @Test
    fun whatsOpenedWhileASyncFinishesStaysOpen() {
        var listener: OnLibraryDownloadListener? = null
        // Reads of the library run when this says, to interleave them as they can on a phone.
        val io = QueuedDispatcher()
        val library = LibraryViewModel(
            send = {},
            newQuery = { TestQuery() },
            io = io,
            startSync = { listener = it; null },
            connected = { true },
            libraryExists = { true },
        )
        fun settle() {
            repeat(3) {
                io.runAll()
                idle()
            }
        }
        settle()

        // Chopin is tapped just as the sync finishes: the refresh starts reading before Chopin's
        // albums are shown, and finishes after.
        library.open(library.state.value.shown!!.items.single { it.listTitle == "Frédéric Chopin" })
        listener!!.OnLibraryDownloadFinished(DownloaderResult(0, DownloaderResult.DownloadResult.SUCCESSFUL))
        settle()

        assertEquals(LibraryStatus.Ready, library.state.value.status)
        assertEquals("Frédéric Chopin", library.state.value.shown!!.opened?.listTitle)
    }

    @Test
    fun aFailedFirstSyncLeavesTheLibraryMissing() {
        var listener: OnLibraryDownloadListener? = null
        val library = LibraryViewModel(
            send = {},
            newQuery = { TestQuery() },
            io = Dispatchers.Unconfined,
            startSync = { listener = it; null },
            connected = { true },
            libraryExists = { false },
        )
        idle()
        assertTrue(library.state.value.status is LibraryStatus.Syncing)

        listener!!.OnLibraryDownloadFinished(DownloaderResult(0, DownloaderResult.DownloadResult.CONNECTION_ERROR))
        idle()
        assertEquals(LibraryStatus.Missing, library.state.value.status)
    }

    @Test
    fun doesntSyncWhileNotConnected() {
        var started = false
        LibraryViewModel(
            send = {},
            newQuery = { TestQuery() },
            io = Dispatchers.Unconfined,
            startSync = { started = true; null },
            connected = { false },
            libraryExists = { true },
        )
        idle()

        assertFalse(started)
    }

    @Test
    fun goingBackShowsTheListWhereItWasLeft() {
        val artists = (1..60).map { item("Artist $it", 0) }
        val albums = listOf(item("Album", 1))
        var state by mutableStateOf(LibraryState(LibraryStatus.Ready, listOf(BrowseLevel(null, ItemKind.ARTIST, artists))))
        compose.setContent {
            ClementineTheme(dynamicColor = false) {
                LibraryContent(state, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithTag("library").performScrollToIndex(45)
        compose.onNodeWithText("Artist 46").assertIsDisplayed()

        // An artist opened starts at the top of its albums...
        state = state.copy(levels = state.levels + BrowseLevel(artists[45], ItemKind.ALBUM, albums))
        compose.onNodeWithText("Album").assertIsDisplayed()

        // ...and going back returns to the artists where they were left.
        state = state.copy(levels = state.levels.dropLast(1))
        compose.onNodeWithText("Artist 46").assertIsDisplayed()
        compose.onNodeWithText("Artist 1").assertDoesNotExist()
    }
}

/** Runs what's dispatched to it only when told, in order. */
private class QueuedDispatcher : CoroutineDispatcher() {
    private val queue = ArrayDeque<Runnable>()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        queue += block
    }

    fun runAll() {
        while (queue.isNotEmpty()) queue.removeFirst().run()
    }
}
