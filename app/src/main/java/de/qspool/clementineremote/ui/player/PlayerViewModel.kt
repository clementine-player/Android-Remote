package de.qspool.clementineremote.ui.player

import androidx.lifecycle.ViewModel
import de.qspool.clementineremote.App
import de.qspool.clementineremote.backend.Clementine
import de.qspool.clementineremote.backend.ClementinePlayerConnection.ConnectionStatus
import de.qspool.clementineremote.backend.RemoteRepository
import de.qspool.clementineremote.backend.RemoteRepository.NowPlaying
import de.qspool.clementineremote.backend.pb.ClementineMessage
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType
import kotlinx.coroutines.flow.StateFlow

/**
 * The player screen's state and actions: what Clementine is playing, and the commands the
 * screen sends it. Commands are sent to Clementine, whose answer then updates [nowPlaying].
 */
class PlayerViewModel(
    private val send: (ClementineMessage) -> Unit = RemoteRepository::send,
) : ViewModel() {

    val nowPlaying: StateFlow<NowPlaying> = RemoteRepository.nowPlaying

    val connection: StateFlow<ConnectionStatus> = RemoteRepository.connection

    fun playPause() = send(ClementineMessage.getMessage(MsgType.PLAYPAUSE))

    fun next() = send(ClementineMessage.getMessage(MsgType.NEXT))

    fun previous() = send(ClementineMessage.getMessage(MsgType.PREVIOUS))

    /** Toggles stopping once the current song ends. */
    fun stopAfterCurrent() = send(ClementineMessage.getMessage(MsgType.STOP_AFTER))

    /** Seeks, and shows the new position at once rather than when Clementine confirms it. */
    fun seekTo(seconds: Int) {
        App.Clementine.songPosition = seconds
        RemoteRepository.refresh()
        send(ClementineMessageFactory.buildTrackPosition(seconds))
    }

    /** Moves to the next shuffle mode, and returns it. */
    fun cycleShuffle(): Clementine.ShuffleMode {
        App.Clementine.nextShuffleMode()
        RemoteRepository.refresh()
        send(ClementineMessageFactory.buildShuffle())
        return App.Clementine.shuffleMode
    }

    /** Moves to the next repeat mode, and returns it. */
    fun cycleRepeat(): Clementine.RepeatMode {
        App.Clementine.nextRepeatMode()
        RemoteRepository.refresh()
        send(ClementineMessageFactory.buildRepeat())
        return App.Clementine.repeatMode
    }

    /** Rates the current song, from 0 to 5 stars, and shows the rating at once. */
    fun rate(stars: Float) {
        val song = App.Clementine.currentSong ?: return
        song.rating = stars / 5
        RemoteRepository.refresh()
        send(ClementineMessageFactory.buildRateTrack(stars / 5))
    }

    /** Loves the current song on Last.fm; a song can be loved only once. */
    fun love() {
        val song = App.Clementine.currentSong ?: return
        if (!song.isLoved) {
            send(ClementineMessage.getMessage(MsgType.LOVE))
            song.isLoved = true
        }
    }

    /** Bans the current song on Last.fm. */
    fun ban() = send(ClementineMessage.getMessage(MsgType.BAN))

    /** Sets Clementine's volume, from 0 to 100. */
    fun setVolume(percent: Int) = send(ClementineMessageFactory.buildVolumeMessage(percent))
}
