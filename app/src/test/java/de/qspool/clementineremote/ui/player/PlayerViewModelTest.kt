package de.qspool.clementineremote.ui.player

import android.os.Looper
import androidx.lifecycle.viewModelScope
import de.qspool.clementineremote.App
import de.qspool.clementineremote.backend.Clementine
import de.qspool.clementineremote.backend.RemoteRepository
import de.qspool.clementineremote.backend.pb.ClementineMessage
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType
import de.qspool.clementineremote.backend.player.LyricsProvider
import de.qspool.clementineremote.backend.player.MySong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Lyrics are asked for once, and found or not when Clementine answers. */
@RunWith(RobolectricTestRunner::class)
class PlayerViewModelTest {

    private val sent = mutableListOf<MsgType>()

    private val answers = MutableStateFlow(0)

    private val song = MySong().apply { title = "Clair de lune" }

    private lateinit var player: PlayerViewModel

    @Before
    fun setUp() {
        App.Clementine = Clementine()
        App.Clementine.currentSong = song
        RemoteRepository.refresh()
        player = PlayerViewModel(send = { message: ClementineMessage -> sent += message.messageType }, lyricsAnswers = answers)
        player.viewModelScope.launch { player.lyrics.collect {} }
        idle()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun asksForLyricsAndSaysWhenNoneWereFound() {
        assertEquals(Lyrics.NotAsked, player.lyrics.value)

        player.requestLyrics()
        idle()
        assertEquals(listOf(MsgType.GET_LYRICS), sent)
        assertEquals(Lyrics.Loading, player.lyrics.value)

        // Clementine answers without any.
        answers.value++
        idle()
        assertEquals(Lyrics.None, player.lyrics.value)
    }

    @Test
    fun showsTheLongestLyricsFound() {
        player.requestLyrics()
        song.lyricsProvider += LyricsProvider().apply { title = "short"; content = "La" }
        song.lyricsProvider += LyricsProvider().apply { title = "long"; content = "La la la" }
        answers.value++
        idle()

        assertEquals(Lyrics.Found("long", "La la la"), player.lyrics.value)
        // Already there, so not asked for again.
        player.requestLyrics()
        assertEquals(listOf(MsgType.GET_LYRICS), sent)
    }
}
