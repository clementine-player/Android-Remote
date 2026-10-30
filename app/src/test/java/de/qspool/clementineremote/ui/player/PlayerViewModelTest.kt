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

/**
 * Lyrics are asked for once, and found or not when Clementine answers; while songs kept on this
 * phone play, the player follows the phone.
 */
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

    private class FakePhone : PhonePlayback {
        override val nowPlaying = MutableStateFlow<RemoteRepository.NowPlaying?>(null)
        val done = mutableListOf<String>()
        override fun playPause() { done += "playPause" }
        override fun next() { done += "next" }
        override fun previous() { done += "previous" }
        override fun seekTo(seconds: Int) { done += "seek $seconds" }
        override fun toggleShuffle() { done += "shuffle" }
        override fun cycleRepeat() { done += "repeat" }
        override fun stop() { done += "stop" }
    }

    @Test
    fun followsThePhoneWhileItPlays() {
        val phone = FakePhone()
        val player = PlayerViewModel(send = { message: ClementineMessage -> sent += message.messageType }, phone = phone)
        idle()
        assertEquals(song, player.nowPlaying.value.song)
        assertEquals(false, player.onPhone.value)

        val kept = MySong().apply { title = "Gymnopédie No. 1" }
        phone.nowPlaying.value = RemoteRepository.NowPlaying(
            kept, Clementine.State.PLAY, 12, 0, Clementine.ShuffleMode.OFF, Clementine.RepeatMode.OFF,
        )
        idle()
        assertEquals(kept, player.nowPlaying.value.song)
        assertEquals(true, player.onPhone.value)
        assertEquals(null, player.playingFrom())

        player.playPause()
        player.next()
        player.seekTo(30)
        assertEquals(Clementine.ShuffleMode.ALL, player.cycleShuffle())
        player.requestLyrics()
        assertEquals(listOf("playPause", "next", "seek 30", "shuffle"), phone.done)
        assertEquals(emptyList<MsgType>(), sent)

        // Stopped on the phone, the player follows Clementine again.
        phone.nowPlaying.value = null
        idle()
        assertEquals(song, player.nowPlaying.value.song)
        player.playPause()
        assertEquals(listOf(MsgType.PLAYPAUSE), sent)
    }
}
