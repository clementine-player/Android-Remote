package de.qspool.clementineremote.backend

import de.qspool.clementineremote.App
import de.qspool.clementineremote.backend.pb.ClementineMessage
import de.qspool.clementineremote.backend.pb.ClementinePbParser
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.Message
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseCurrentMetadata
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseUpdateTrackPosition
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.Shuffle
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ShuffleMode
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.SongMetadata
import de.qspool.clementineremote.ui.player.PlayerViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Clementine's messages, parsed as the connection does, show up in [RemoteRepository.nowPlaying];
 * the player's commands go out as Clementine's messages.
 */
@RunWith(RobolectricTestRunner::class)
class RemoteRepositoryTest {

    private val parser = ClementinePbParser()

    @Before
    fun setUp() {
        App.Clementine = Clementine()
        App.ClementineConnection = null
        RemoteRepository.refresh()
    }

    /** Parses a message into [App.Clementine] and hands it on, as the connection thread does. */
    private fun receive(message: Message.Builder) {
        RemoteRepository.onMessage(parser.parse(message.build().toByteArray()))
    }

    private fun message(type: MsgType) = ClementineMessage.getMessageBuilder(type)

    @Test
    fun followsTheCurrentSong() {
        assertNull(RemoteRepository.nowPlaying.value.song)

        receive(message(MsgType.CURRENT_METAINFO).setResponseCurrentMetadata(
            ResponseCurrentMetadata.newBuilder().setSongMetadata(
                SongMetadata.newBuilder()
                    .setId(3)
                    .setTitle("Clair de lune")
                    .setArtist("Claude Debussy")
                    .setLength(300))))

        val song = RemoteRepository.nowPlaying.value.song
        assertEquals("Clair de lune", song?.title)
        assertEquals(300, song?.length)
    }

    @Test
    fun followsPlaybackAndPosition() {
        receive(message(MsgType.PLAY))
        assertTrue(RemoteRepository.nowPlaying.value.isPlaying)

        receive(message(MsgType.UPDATE_TRACK_POSITION).setResponseUpdateTrackPosition(
            ResponseUpdateTrackPosition.newBuilder().setPosition(42)))
        assertEquals(42, RemoteRepository.nowPlaying.value.positionSeconds)

        receive(message(MsgType.PAUSE))
        assertFalse(RemoteRepository.nowPlaying.value.isPlaying)
    }

    @Test
    fun followsShuffle() {
        receive(message(MsgType.SHUFFLE).setShuffle(
            Shuffle.newBuilder().setShuffleMode(ShuffleMode.Shuffle_All)))

        assertEquals(Clementine.ShuffleMode.ALL, RemoteRepository.nowPlaying.value.shuffle)
    }

    @Test
    fun ignoresErrors() {
        val before = RemoteRepository.nowPlaying.value
        RemoteRepository.onMessage(ClementineMessage(ClementineMessage.ErrorMessage.IO_EXCEPTION))
        assertEquals(before, RemoteRepository.nowPlaying.value)
    }

    @Test
    fun playerSendsCommands() {
        val sent = mutableListOf<ClementineMessage>()
        val player = PlayerViewModel(send = { sent += it })

        player.playPause()
        player.next()
        player.previous()
        player.seekTo(61)
        player.rate(4f)

        assertEquals(
            listOf(MsgType.PLAYPAUSE, MsgType.NEXT, MsgType.PREVIOUS, MsgType.SET_TRACK_POSITION,
                MsgType.RATE_SONG),
            sent.map { it.messageType })
        assertEquals(61, sent[3].message.requestSetTrackPosition.position)
        assertEquals(0.8f, sent[4].message.requestRateSong.rating, 0.001f)
    }

    @Test
    fun cyclingShuffleShowsTheNewModeAtOnce() {
        val sent = mutableListOf<ClementineMessage>()
        val player = PlayerViewModel(send = { sent += it })

        player.cycleShuffle()

        assertEquals(Clementine.ShuffleMode.ALL, player.nowPlaying.value.shuffle)
        assertEquals(MsgType.SHUFFLE, sent.single().messageType)
    }
}
