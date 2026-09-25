package de.qspool.clementineremote.backend

import android.os.Message
import androidx.annotation.VisibleForTesting
import de.qspool.clementineremote.App
import de.qspool.clementineremote.backend.ClementinePlayerConnection.ConnectionStatus
import de.qspool.clementineremote.backend.listener.PlayerConnectionListener
import de.qspool.clementineremote.backend.pb.ClementineMessage
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType
import de.qspool.clementineremote.backend.player.MySong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Clementine's state as flows that screens observe, instead of each screen receiving
 * Clementine's messages through a Handler. It follows the connection the service opens
 * ([attach]) and re-reads [App.Clementine] after each message that changes it; the messages are
 * parsed into [App.Clementine] before listeners hear of them.
 *
 * Commands still go to the connection's thread as messages ([send]).
 */
object RemoteRepository {

    /** What Clementine is playing, and how. */
    data class NowPlaying(
        val song: MySong?,
        val state: Clementine.State,
        /** In seconds, as Clementine reports it. */
        val positionSeconds: Int,
        /** From 0 to 100. */
        val volume: Int,
        val shuffle: Clementine.ShuffleMode,
        val repeat: Clementine.RepeatMode,
    ) {
        val isPlaying: Boolean get() = state == Clementine.State.PLAY
    }

    private val _connection = MutableStateFlow(ConnectionStatus.IDLE)

    /** The connection to Clementine. */
    @JvmStatic
    val connection: StateFlow<ConnectionStatus> = _connection.asStateFlow()

    private val _nowPlaying = MutableStateFlow(snapshot())

    @JvmStatic
    val nowPlaying: StateFlow<NowPlaying> = _nowPlaying.asStateFlow()

    /** Follows a new connection: its status, and the messages that change what's playing. */
    @JvmStatic
    fun attach(connection: ClementinePlayerConnection) {
        connection.addPlayerConnectionListener(object : PlayerConnectionListener {
            override fun onConnectionStatusChanged(status: ConnectionStatus) {
                _connection.value = status
            }

            override fun onClementineMessageReceived(message: ClementineMessage) {
                onMessage(message)
            }
        })
    }

    @VisibleForTesting
    internal fun onMessage(message: ClementineMessage) {
        if (message.isErrorMessage) {
            return
        }
        when (message.messageType) {
            MsgType.INFO,
            MsgType.CURRENT_METAINFO,
            MsgType.PLAY,
            MsgType.PAUSE,
            MsgType.STOP,
            MsgType.UPDATE_TRACK_POSITION,
            MsgType.SET_VOLUME,
            MsgType.SHUFFLE,
            MsgType.REPEAT,
            MsgType.FIRST_DATA_SENT_COMPLETE -> refresh()
            else -> {}
        }
    }

    /** Re-reads [App.Clementine], for changes made here rather than by Clementine's messages. */
    @JvmStatic
    fun refresh() {
        _nowPlaying.value = snapshot()
    }

    private fun snapshot(): NowPlaying = App.Clementine.run {
        NowPlaying(
            song = currentSong,
            state = state,
            positionSeconds = songPosition,
            volume = volume,
            shuffle = shuffleMode,
            repeat = repeatMode,
        )
    }

    /** Sends a command to Clementine, if connected. */
    @JvmStatic
    fun send(message: ClementineMessage) {
        val connection = App.ClementineConnection ?: return
        // The handler exists once the connection thread has started.
        connection.mHandler?.sendMessage(Message.obtain().apply { obj = message })
    }
}
