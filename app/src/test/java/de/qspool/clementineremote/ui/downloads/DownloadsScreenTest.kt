package de.qspool.clementineremote.ui.downloads

import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.viewModelScope
import de.qspool.clementineremote.App
import de.qspool.clementineremote.SharedPreferencesKeys
import de.qspool.clementineremote.backend.downloader.StoredSong
import de.qspool.clementineremote.backend.pb.ClementineMessage
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.DownloadItem
import de.qspool.clementineremote.backend.player.MyPlaylist
import de.qspool.clementineremote.ui.theme.ClementineTheme
import de.qspool.clementineremote.utils.Utilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File

/** Downloads show their progress, let a download be cancelled, and play what was downloaded. */
@RunWith(RobolectricTestRunner::class)
class DownloadsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val done = mutableListOf<String>()

    private val running = Download(
        id = 1, item = DownloadItem.ItemAlbum, title = "Downloading album Suite bergamasque",
        subtitle = "(2/4) Claude Debussy - Menuet", progress = 50, size = "10 MiB / 20 MiB (1 MiB/s)",
        running = true, songs = emptyList(),
    )

    private val finished = Download(
        id = 2, item = DownloadItem.Urls, title = "Downloading songs Erik Satie - Gymnopédie No. 1",
        subtitle = "(1/1) Download finished", progress = 100, size = "3 MiB / 3 MiB",
        running = false, songs = listOf(DownloadedSong("Gymnopédie No. 1", "Erik Satie", Uri.parse("content://media/1"))),
    )

    private fun show(state: DownloadsState, suggestions: Suggestions = Suggestions()) {
        compose.setContent {
            ClementineTheme(dynamicColor = false) {
                DownloadsContent(
                    state,
                    onCancel = { done += "cancel ${it.id}" },
                    onPlay = { done += "play ${it.title}" },
                    onChangeSettings = { done += "settings" },
                    suggestions = suggestions,
                    onDownloadAlbum = { done += "album ${it.album}" },
                    onDownloadPlaylist = { done += "playlist ${it.id}" },
                )
            }
        }
    }

    private val suggestions = Suggestions(
        albums = listOf(AlbumSuggestion("Claude Debussy", "Suite bergamasque", songs = 4, plays = 12, bytes = 90L shl 20)),
        mostPlayed = true,
        playlists = listOf(PlaylistSuggestion(3, "Piano evenings", songs = 13, playing = true, favorite = false)),
    )

    @Test
    fun withoutDownloadsOrSuggestionsSaysWhereDownloadsStart() {
        show(DownloadsState(freeSpace = "1.0 GiB"))

        compose.onNodeWithTag("downloadsEmpty").assertIsDisplayed()
        compose.onNodeWithTag("downloadsHowTo").assertIsDisplayed()
        compose.onNodeWithText("1.0 GiB free on this phone").assertIsDisplayed()
    }

    @Test
    fun withoutDownloadsSuggestsTheMostPlayedAlbumsAndThePlaylists() {
        show(DownloadsState(), suggestions)

        compose.onNodeWithTag("downloadsHowTo").assertDoesNotExist()
        compose.onNodeWithText("Your most played albums").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Claude Debussy · 12 plays · 90.00 MiB").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("13 songs · Playing now").performScrollTo().assertIsDisplayed()

        compose.onNodeWithTag("suggestAlbumDownload").performScrollTo().performClick()
        compose.onNodeWithTag("suggestPlaylist").performScrollTo().performClick()

        assertEquals(listOf("album Suite bergamasque", "playlist 3"), done)
    }

    @Test
    fun withNothingPlayedSuggestsTheAlbumsAddedLast() {
        show(DownloadsState(), suggestions.copy(mostPlayed = false))

        compose.onNodeWithText("Recently added albums").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Claude Debussy · 4 songs · 90.00 MiB").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun suggestionsGoOnceSomethingIsDownloaded() {
        show(DownloadsState(running = listOf(running)), suggestions)

        compose.onNodeWithTag("downloadsEmpty").assertDoesNotExist()
        compose.onNodeWithTag("suggestAlbum").assertDoesNotExist()
    }

    @Test
    fun runningDownloadsCanBeCancelled() {
        show(DownloadsState(running = listOf(running)))

        compose.onNodeWithText("Downloading album Suite bergamasque").assertIsDisplayed()
        compose.onNodeWithText("10 MiB / 20 MiB (1 MiB/s)").assertIsDisplayed()
        compose.onNodeWithTag("cancel1").performClick()

        assertEquals(listOf("cancel 1"), done)
    }

    @Test
    fun finishedDownloadsPlayTheirSongs() {
        show(DownloadsState(finished = listOf(finished)))

        compose.onNodeWithText("Downloading songs Erik Satie - Gymnopédie No. 1").performClick()
        compose.onNodeWithText("Erik Satie - Gymnopédie No. 1").performClick()

        assertEquals(listOf("play Gymnopédie No. 1"), done)
    }

    @Test
    fun songsDownloadedBeforeAreOnThePhoneToPlay() {
        val song = DownloadedSong("Clair de lune", "Claude Debussy", Uri.parse("content://media/2"))
        show(DownloadsState(onPhone = listOf(song)), suggestions)

        // Something is downloaded, so no suggestions.
        compose.onNodeWithTag("downloadsEmpty").assertDoesNotExist()
        compose.onNodeWithTag("suggestAlbum").assertDoesNotExist()
        compose.onNodeWithText("On this phone").assertIsDisplayed()
        compose.onNodeWithText("Claude Debussy").assertIsDisplayed()
        compose.onNodeWithText("Clair de lune").performClick()

        assertEquals(listOf("play Clair de lune"), done)
    }

    @Test
    fun nothingIsSaidToBeDownloadedBeforeThePhoneIsRead() {
        show(DownloadsState(loaded = false))

        compose.onNodeWithTag("downloadsEmpty").assertDoesNotExist()
    }

    @Test
    fun saysWhenDownloadsOnlyRunOnWifi() {
        show(DownloadsState(wifiOnly = true))

        compose.onNodeWithTag("downloadsWifiOnly").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Change").performClick()

        assertEquals(listOf("settings"), done)
    }

    @Test
    fun viewModelReadsTheDownloadsAndSettings() {
        App.getPreferences().edit().putBoolean(SharedPreferencesKeys.SP_WIFI_ONLY, true).commit()
        val downloads = DownloadsViewModel(downloads = { emptyList() }, storedSongs = { emptyList() }, freeSpace = { 1L shl 30 })
        downloads.viewModelScope.launch { downloads.state.collect {} }

        // The state is read off the main thread; wait for the first reading.
        val end = System.currentTimeMillis() + 5_000
        while (downloads.state.value.freeSpace.isEmpty() && System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }

        assertEquals(Utilities.humanReadableBytes(1L shl 30, true), downloads.state.value.freeSpace)
        assertTrue(downloads.state.value.wifiOnly)
        assertTrue(downloads.state.value.running.isEmpty())
    }

    @Test
    fun viewModelListsTheSongsOnThePhoneByFolderAndName() {
        val untagged = StoredSong(Uri.parse("content://media/3"), "Erik Satie/Gymnopédies/", "02 Gymnopédie No. 2.mp3", null, null)
        val tagged = StoredSong(Uri.parse("content://media/4"), "Claude Debussy/", "Clair.ogg", "Clair de lune", "Claude Debussy")
        val downloads = DownloadsViewModel(downloads = { emptyList() }, storedSongs = { listOf(untagged, tagged) }, freeSpace = { 0 })
        downloads.viewModelScope.launch { downloads.state.collect {} }

        // The state is read off the main thread; wait for the first reading.
        val end = System.currentTimeMillis() + 5_000
        while (!downloads.state.value.loaded && System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }

        assertEquals(
            listOf(
                DownloadedSong("Clair de lune", "Claude Debussy", Uri.parse("content://media/4")),
                // Named after its file and folder.
                DownloadedSong("02 Gymnopédie No. 2", "Erik Satie/Gymnopédies", Uri.parse("content://media/3")),
            ),
            downloads.state.value.onPhone,
        )
    }

    @Test
    fun viewModelSuggestsFromTheLibraryAndDownloadsWhatsPicked() {
        val file = File(App.getApp().cacheDir, "library-suggestions.db").apply { delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use {
            it.execSQL(
                "CREATE TABLE songs (artist TEXT, albumartist TEXT, album TEXT, filename TEXT, disc INTEGER, " +
                    "track INTEGER, playcount INTEGER, lastplayed INTEGER, ctime INTEGER, filesize INTEGER)",
            )
            it.execSQL("INSERT INTO songs VALUES ('Erik Satie', '', 'Gymnopédies', 'file:///g1.flac', 1, 1, 3, 0, 0, 0)")
        }
        val playlist = MyPlaylist().apply {
            id = 7
            name = "Mix"
            itemCount = 2
        }
        val started = mutableListOf<ClementineMessage>()
        val downloads = DownloadsViewModel(
            downloads = { emptyList() },
            storedSongs = { emptyList() },
            freeSpace = { 0 },
            library = { SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY) },
            playlists = { listOf(playlist) },
            startDownload = { started += it },
            io = Dispatchers.Unconfined,
        )

        downloads.loadSuggestions()
        shadowOf(Looper.getMainLooper()).idle()
        val suggested = downloads.suggestions.value
        assertEquals(listOf("Gymnopédies"), suggested.albums.map { it.album })
        assertEquals(listOf(7), suggested.playlists.map { it.id })

        downloads.download(suggested.albums.single())
        downloads.download(suggested.playlists.single())
        shadowOf(Looper.getMainLooper()).idle()

        val (album, mix) = started.map { it.message.requestDownloadSongs }
        assertEquals(DownloadItem.Urls, album.downloadItem)
        assertEquals(listOf("file:///g1.flac"), album.urlsList)
        assertEquals(DownloadItem.APlaylist, mix.downloadItem)
        assertEquals(7, mix.playlistId)
    }
}
