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
import de.qspool.clementineremote.backend.sync.PhonePlayer
import de.qspool.clementineremote.backend.sync.PlayFrom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Where Play from can switch to, and what each one plays. */
data class PlayFromChoices(val clementine: NowPlaying, val phone: NowPlaying, val playFrom: PlayFrom)

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

/** The phone's own player, which the player follows while the app plays from this phone. */
interface PhonePlayback {
    /** What plays on this phone, or is paused there; null when nothing is. */
    val nowPlaying: StateFlow<NowPlaying?>

    /** Where the app plays from. */
    val playFrom: StateFlow<PlayFrom>

    /** Switches where the app plays from, pausing the one it leaves. */
    fun playFrom(place: PlayFrom)
    fun playPause()
    fun next()
    fun previous()
    fun seekTo(seconds: Int)
    fun toggleShuffle()
    fun cycleRepeat()
    fun stop()
}

private object PhonePlayerPlayback : PhonePlayback {
    override val nowPlaying get() = PhonePlayer.nowPlaying
    override val playFrom get() = PhonePlayer.playFrom
    override fun playFrom(place: PlayFrom) = PhonePlayer.playFrom(App.getApp(), place)
    override fun playPause() = PhonePlayer.playPause(App.getApp())
    override fun next() = PhonePlayer.next(App.getApp())
    override fun previous() = PhonePlayer.previous(App.getApp())
    override fun seekTo(seconds: Int) = PhonePlayer.seekTo(App.getApp(), seconds)
    override fun toggleShuffle() = PhonePlayer.toggleShuffle(App.getApp())
    override fun cycleRepeat() = PhonePlayer.cycleRepeat(App.getApp())
    override fun stop() = PhonePlayer.stop(App.getApp())
}

/**
 * The player's state and actions: what Clementine is playing, and the commands the player
 * sends it. Commands are sent to Clementine, whose answer then updates [nowPlaying].
 *
 * While the app plays from this phone, the player follows the phone's own player instead, and its
 * commands go there.
 */
class PlayerViewModel(
    private val send: (ClementineMessage) -> Unit = RemoteRepository::send,
    private val lyricsAnswers: StateFlow<Int> = RemoteRepository.lyricsAnswers,
    private val phone: PhonePlayback = PhonePlayerPlayback,
) : ViewModel() {

    /** What the phone plays while the app plays from it; null otherwise. */
    private fun followed(nowPlaying: NowPlaying?, from: PlayFrom) = nowPlaying.takeIf { from == PlayFrom.PHONE }

    val nowPlaying: StateFlow<NowPlaying> =
        combine(RemoteRepository.nowPlaying, phone.nowPlaying, phone.playFrom) { clementine, phone, from ->
            followed(phone, from) ?: clementine
        }.stateIn(viewModelScope, SharingStarted.Eagerly, followedNow ?: RemoteRepository.nowPlaying.value)

    /** Whether the player follows the phone's own player. */
    val onPhone: StateFlow<Boolean> = combine(phone.nowPlaying, phone.playFrom) { phone, from ->
        followed(phone, from) != null
    }.stateIn(viewModelScope, SharingStarted.Eagerly, followedNow != null)

    /**
     * What Play from offers: Clementine (while connected) and the phone's own player (while
     * something plays or is paused there), with what each plays; null when there's no choice.
     */
    val choices: StateFlow<PlayFromChoices?> =
        combine(RemoteRepository.nowPlaying, RemoteRepository.connection, phone.nowPlaying, phone.playFrom) { clementine, connection, onPhone, from ->
            if (connection == ConnectionStatus.CONNECTED && onPhone != null) PlayFromChoices(clementine, onPhone, from) else null
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Switches where the app plays from, pausing the one it leaves. */
    fun playFrom(place: PlayFrom) = phone.playFrom(place)

    private val followedNow: NowPlaying? get() = followed(phone.nowPlaying.value, phone.playFrom.value)

    private val playingOnPhone: Boolean get() = followedNow != null

    val connection: StateFlow<ConnectionStatus> = RemoteRepository.connection

    /** The song lyrics were asked for, and how many answers Clementine had given then. */
    private data class Asked(val song: MySong, val answersBefore: Int)

    private val asked = MutableStateFlow<Asked?>(null)

    /** The current song's lyrics: the longest Clementine found. */
    val lyrics: StateFlow<Lyrics> = combine(nowPlaying, lyricsAnswers, asked) { playing, answers, asked ->
        val song = playing.song
        val best = song?.lyricsProvider?.maxByOrNull { it.content.length }
        when {
            song == null || playingOnPhone -> Lyrics.None
            best != null -> Lyrics.Found(best.title, best.content)
            asked == null || asked.song !== song -> Lyrics.NotAsked
            answers > asked.answersBefore -> Lyrics.None
            else -> Lyrics.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Lyrics.NotAsked)

    /** Asks Clementine for the current song's lyrics, unless it already sent them. */
    fun requestLyrics() {
        if (playingOnPhone) return
        val song = App.Clementine.currentSong ?: return
        if (song.lyricsProvider.isEmpty()) {
            asked.value = Asked(song, lyricsAnswers.value)
            send(ClementineMessage.getMessage(MsgType.GET_LYRICS))
        }
    }

    /** The playlist Clementine plays from; null while the phone plays. */
    fun playingFrom(): String? = if (playingOnPhone) null else App.Clementine.playlistManager.activePlaylist?.name

    /** Whether to offer loving and banning songs on Last.fm. */
    fun lastFm(): Boolean = App.getPreferences().getBoolean(SharedPreferencesKeys.SP_LASTFM, true)

    fun playPause() = if (playingOnPhone) phone.playPause() else send(ClementineMessage.getMessage(MsgType.PLAYPAUSE))

    fun next() = if (playingOnPhone) phone.next() else send(ClementineMessage.getMessage(MsgType.NEXT))

    fun previous() = if (playingOnPhone) phone.previous() else send(ClementineMessage.getMessage(MsgType.PREVIOUS))

    fun stop() = if (playingOnPhone) phone.stop() else send(ClementineMessage.getMessage(MsgType.STOP))

    /** Toggles stopping once the current song ends. */
    fun stopAfterCurrent() = send(ClementineMessage.getMessage(MsgType.STOP_AFTER))

    /** Seeks, and shows the new position at once rather than when Clementine confirms it. */
    fun seekTo(seconds: Int) {
        if (playingOnPhone) {
            phone.seekTo(seconds)
            return
        }
        App.Clementine.songPosition = seconds
        RemoteRepository.refresh()
        send(ClementineMessageFactory.buildTrackPosition(seconds))
    }

    /** Moves to the next shuffle mode, and returns it. */
    fun cycleShuffle(): Clementine.ShuffleMode {
        if (playingOnPhone) {
            val shuffle = phone.nowPlaying.value?.shuffle == Clementine.ShuffleMode.OFF
            phone.toggleShuffle()
            return if (shuffle) Clementine.ShuffleMode.ALL else Clementine.ShuffleMode.OFF
        }
        App.Clementine.nextShuffleMode()
        RemoteRepository.refresh()
        send(ClementineMessageFactory.buildShuffle())
        return App.Clementine.shuffleMode
    }

    /** Moves to the next repeat mode, and returns it. */
    fun cycleRepeat(): Clementine.RepeatMode {
        if (playingOnPhone) {
            phone.cycleRepeat()
            return when (phone.nowPlaying.value?.repeat) {
                Clementine.RepeatMode.OFF -> Clementine.RepeatMode.PLAYLIST
                Clementine.RepeatMode.PLAYLIST -> Clementine.RepeatMode.TRACK
                else -> Clementine.RepeatMode.OFF
            }
        }
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
