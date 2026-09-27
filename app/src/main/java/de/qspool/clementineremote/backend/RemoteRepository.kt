package de.qspool.clementineremote.backend

import android.os.Message
import androidx.annotation.VisibleForTesting
import de.qspool.clementineremote.App
import de.qspool.clementineremote.backend.ClementinePlayerConnection.ConnectionStatus
import de.qspool.clementineremote.backend.listener.PlayerConnectionListener
import de.qspool.clementineremote.backend.pb.ClementineMessage
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.OutputState
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ServerFeature
import de.qspool.clementineremote.backend.streaming.ThisRenderer
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

    /** Somewhere Clementine can play: its own computer, or a renderer such as this phone. */
    data class Output(
        /** [LOCAL_OUTPUT] for Clementine's computer. */
        val id: String,
        val name: String,
        val active: Boolean,
        /** Playback is moving to it. */
        val activating: Boolean,
    ) {
        val isThisPhone: Boolean get() = id == ThisRenderer.id()
    }

    /** Where Clementine can play. */
    data class Outputs(
        /** Whether this Clementine can play elsewhere than on its own computer. */
        val supported: Boolean = false,
        val outputs: List<Output> = emptyList(),
    ) {
        val active: Output? get() = outputs.firstOrNull { it.active }
    }

    /** The output id of Clementine's own computer. */
    const val LOCAL_OUTPUT = "local"

    private val _connection = MutableStateFlow(ConnectionStatus.IDLE)

    /** The connection to Clementine. */
    @JvmStatic
    val connection: StateFlow<ConnectionStatus> = _connection.asStateFlow()

    private val _nowPlaying = MutableStateFlow(snapshot())

    @JvmStatic
    val nowPlaying: StateFlow<NowPlaying> = _nowPlaying.asStateFlow()

    private val _lyricsAnswers = MutableStateFlow(0)

    /**
     * How many times Clementine has answered a request for lyrics. The lyrics themselves are
     * added to the song they're for ([MySong.getLyricsProvider]); none may have been found.
     */
    @JvmStatic
    val lyricsAnswers: StateFlow<Int> = _lyricsAnswers.asStateFlow()

    private val _outputs = MutableStateFlow(Outputs())

    /** Where Clementine can play (remote streaming), and where it plays now. */
    @JvmStatic
    val outputs: StateFlow<Outputs> = _outputs.asStateFlow()

    /** Whether Clementine plays on this phone (remote streaming). */
    @JvmStatic
    fun isPlayingHere(): Boolean = _outputs.value.active?.isThisPhone == true

    /** Follows a new connection: its status, and the messages that change what's playing. */
    @JvmStatic
    fun attach(connection: ClementinePlayerConnection) {
        connection.addPlayerConnectionListener(object : PlayerConnectionListener {
            override fun onConnectionStatusChanged(status: ConnectionStatus) {
                _connection.value = status
                if (status == ConnectionStatus.DISCONNECTED) {
                    _outputs.value = Outputs()
                }
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
            MsgType.INFO -> {
                val supported = ServerFeature.SERVER_FEATURE_RENDERING in
                    message.message.responseClementineInfo.featuresList
                _outputs.value = _outputs.value.copy(supported = supported)
                if (supported) {
                    send(ClementineMessage.getMessage(MsgType.REQUEST_OUTPUTS))
                }
                refresh()
            }
            MsgType.OUTPUTS -> _outputs.value = _outputs.value.copy(
                outputs = message.message.responseOutputs.outputsList.map {
                    Output(
                        id = it.outputId,
                        name = it.displayName,
                        active = it.state == OutputState.OUTPUT_STATE_ACTIVE,
                        activating = it.state == OutputState.OUTPUT_STATE_ACTIVATING,
                    )
                },
            )
            MsgType.CURRENT_METAINFO,
            MsgType.PLAY,
            MsgType.PAUSE,
            MsgType.STOP,
            MsgType.UPDATE_TRACK_POSITION,
            MsgType.SET_VOLUME,
            MsgType.SHUFFLE,
            MsgType.REPEAT,
            MsgType.FIRST_DATA_SENT_COMPLETE -> refresh()
            MsgType.LYRICS -> _lyricsAnswers.value++
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
