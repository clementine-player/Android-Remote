package de.qspool.clementineremote.ui.queue

import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.viewModelScope
import de.qspool.clementineremote.App
import de.qspool.clementineremote.backend.Clementine
import de.qspool.clementineremote.backend.RemoteRepository
import de.qspool.clementineremote.backend.pb.ClementineMessage
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType
import de.qspool.clementineremote.backend.player.MyPlaylist
import de.qspool.clementineremote.backend.player.MySong
import de.qspool.clementineremote.ui.theme.ClementineTheme
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** The queue shows Clementine's playlists and hands on what the user does with their songs. */
@RunWith(RobolectricTestRunner::class)
class QueueScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun song(index: Int, title: String, length: Int, artist: String = "Claude Debussy") = MySong().apply {
        this.index = index
        this.title = title
        this.artist = artist
        this.album = "Suite bergamasque"
        this.length = length
        this.prettyLength = "%d:%02d".format(length / 60, length % 60)
    }

    private val songs = listOf(
        song(0, "Prélude", 250),
        song(1, "Menuet", 290),
        song(2, "Clair de lune", 300),
    )

    private val playlist1 = PlaylistTab(1, "Playlist 1")

    private val playlist2 = PlaylistTab(2, "Satie")

    private val done = mutableListOf<String>()

    private fun show(state: QueueState) {
        compose.setContent {
            ClementineTheme(dynamicColor = false) {
                QueueContent(
                    state,
                    onShow = { done += "show ${it.name}" },
                    onPlay = { done += "play ${it.title}" },
                    onDownload = { list -> done += "download " + list.joinToString { it.title } },
                    onRemove = { list -> done += "remove " + list.joinToString { it.title } },
                )
            }
        }
    }

    private val state = QueueState(
        playlists = listOf(playlist1, playlist2),
        shown = playlist1,
        songs = songs,
        songCount = 3,
        lengthSeconds = 840,
        playingIndex = 2,
    )

    @Before
    fun setUp() {
        App.Clementine = Clementine()
        App.ClementineConnection = null
        RemoteRepository.refresh()
    }

    @After
    fun tearDown() {
        App.Clementine = Clementine()
        RemoteRepository.refresh()
    }

    @Test
    fun showsThePlaylistAndItsSongs() {
        show(state)

        compose.onNodeWithTag("queueTitle").assertTextEquals("Playlist 1")
        compose.onNodeWithText("3 songs · 14 min").assertIsDisplayed()
        compose.onNodeWithText("Menuet").assertIsDisplayed()
        compose.onNodeWithText("4:50").assertIsDisplayed()

        compose.onNodeWithTag("playlist2").performClick()
        assertEquals(listOf("show Satie"), done)
    }

    @Test
    fun tappingASongPlaysIt() {
        show(state)

        compose.onNodeWithText("Menuet").performClick()

        assertEquals(listOf("play Menuet"), done)
    }

    @Test
    fun aLongPressSelectsSongsToDownloadOrRemove() {
        show(state)

        compose.onNodeWithTag("song0").performTouchInput { longClick() }
        compose.onNodeWithTag("song1").performClick()
        compose.onNodeWithTag("song1").assertIsSelected()
        compose.onNodeWithText("2 selected").assertIsDisplayed()
        compose.onNodeWithTag("queueDownload").performClick()

        compose.onNodeWithTag("song2").performTouchInput { longClick() }
        compose.onNodeWithTag("queueRemove").performClick()

        assertEquals(listOf("download Prélude, Menuet", "remove Clair de lune"), done)
        compose.onNodeWithTag("queueTitle").assertIsDisplayed()
    }

    @Test
    fun anEmptyPlaylistSaysSo() {
        show(QueueState(playlists = listOf(playlist1), shown = playlist1))

        compose.onNodeWithText("Playlist is empty!").assertIsDisplayed()
    }

    @Test
    fun viewModelFollowsThePlaylistManagerAndSendsCommands() {
        val manager = App.Clementine.playlistManager
        manager.addPlaylist(MyPlaylist().apply { id = 1; name = "Playlist 1"; isActive = true })
        manager.addPlaylist(MyPlaylist().apply { id = 2; name = "Satie" })
        manager.playlistSongsDownloaded(1, songs)
        App.Clementine.currentSong = songs[2]
        RemoteRepository.refresh()

        val sent = mutableListOf<ClementineMessage>()
        val viewModel = QueueViewModel(send = { sent += it })
        viewModel.viewModelScope.launch { viewModel.state.collect {} }
        shadowOf(Looper.getMainLooper()).idle()

        var state = viewModel.state.value
        assertEquals(listOf("Playlist 1", "Satie"), state.playlists.map { it.name })
        assertEquals("Playlist 1", state.shown?.name)
        assertEquals(3, state.songCount)
        assertEquals(840, state.lengthSeconds)
        assertEquals(2, state.playingIndex)

        viewModel.setFilter("men")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("Menuet"), viewModel.state.value.songs.map { it.title })

        viewModel.play(songs[1])
        viewModel.remove(listOf(songs[0]))
        assertEquals(listOf(MsgType.CHANGE_SONG, MsgType.REMOVE_SONGS), sent.map { it.messageType })

        // Another playlist has nothing playing in it.
        viewModel.show(PlaylistTab(2, "Satie"))
        shadowOf(Looper.getMainLooper()).idle()
        state = viewModel.state.value
        assertEquals("Satie", state.shown?.name)
        assertNull(state.playingIndex)
    }
}
