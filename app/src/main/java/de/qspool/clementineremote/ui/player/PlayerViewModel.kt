package de.qspool.clementineremote.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.qspool.clementineremote.App
import de.qspool.clementineremote.SharedPreferencesKeys
import de.qspool.clementineremote.backend.Clementine
import de.qspool.clementineremote.backend.ClementinePlayerConnection.ConnectionStatus
import de.qspool.clementineremote.backend.RemoteRepository
import de.qspool.clementineremote.backend.RemoteRepository.NowPlaying
import de.qspool.clementineremote.backend.pb.ClementineMessage
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType
import de.qspool.clementineremote.backend.player.MySong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** The current song's lyrics, which Clementine looks up when asked. */
sealed interface Lyrics {
    /** Not asked for yet. */
    data object NotAsked : Lyrics

    /** Clementine is looking for them. */
    data object Loading : Lyrics

    /** Clementine found none. */
    data object None : Lyrics

    data class Found(val title: String, val content: String) : Lyrics
}

/**
 * The player's state and actions: what Clementine is playing, and the commands the player
 * sends it. Commands are sent to Clementine, whose answer then updates [nowPlaying].
 */
class PlayerViewModel(
    private val send: (ClementineMessage) -> Unit = RemoteRepository::send,
    private val lyricsAnswers: StateFlow<Int> = RemoteRepository.lyricsAnswers,
) : ViewModel() {

    val nowPlaying: StateFlow<NowPlaying> = RemoteRepository.nowPlaying

    val connection: StateFlow<ConnectionStatus> = RemoteRepository.connection

    /** The song lyrics were asked for, and how many answers Clementine had given then. */
    private data class Asked(val song: MySong, val answersBefore: Int)

    private val asked = MutableStateFlow<Asked?>(null)

    /** The current song's lyrics: the longest Clementine found. */
    val lyrics: StateFlow<Lyrics> = combine(nowPlaying, lyricsAnswers, asked) { playing, answers, asked ->
        val song = playing.song
        val best = song?.lyricsProvider?.maxByOrNull { it.content.length }
        when {
            song == null -> Lyrics.None
            best != null -> Lyrics.Found(best.title, best.content)
            asked == null || asked.song !== song -> Lyrics.NotAsked
            answers > asked.answersBefore -> Lyrics.None
            else -> Lyrics.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Lyrics.NotAsked)

    /** Asks Clementine for the current song's lyrics, unless it already sent them. */
    fun requestLyrics() {
        val song = App.Clementine.currentSong ?: return
        if (song.lyricsProvider.isEmpty()) {
            asked.value = Asked(song, lyricsAnswers.value)
            send(ClementineMessage.getMessage(MsgType.GET_LYRICS))
        }
    }

    /** The playlist Clementine plays from. */
    fun playingFrom(): String? = App.Clementine.playlistManager.activePlaylist?.name

    /** Whether to offer loving and banning songs on Last.fm. */
    fun lastFm(): Boolean = App.getPreferences().getBoolean(SharedPreferencesKeys.SP_LASTFM, true)

    fun playPause() = send(ClementineMessage.getMessage(MsgType.PLAYPAUSE))

    fun next() = send(ClementineMessage.getMessage(MsgType.NEXT))

    fun previous() = send(ClementineMessage.getMessage(MsgType.PREVIOUS))

    fun stop() = send(ClementineMessage.getMessage(MsgType.STOP))

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
