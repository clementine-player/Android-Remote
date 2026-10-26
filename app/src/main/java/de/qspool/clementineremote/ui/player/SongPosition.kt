package de.qspool.clementineremote.ui.player

import android.os.SystemClock
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import de.qspool.clementineremote.backend.Clementine
import de.qspool.clementineremote.backend.RemoteRepository.NowPlaying
import de.qspool.clementineremote.backend.player.MySong

/**
 * The song's position, which moves on every frame while Clementine plays, rather than once a
 * second when Clementine reports it.
 */
@Stable
class SongPosition internal constructor() {
    private val clock = PositionClock()
    private var song: MySong? = null
    private var reported = 0

    /** What Clementine last said. */
    internal var nowPlaying: NowPlaying? by mutableStateOf(null)

    /** Changes on every frame while playing, so that reading [seconds] follows them. */
    internal var frame by mutableLongStateOf(0L)

    /** In seconds. */
    val seconds: Float
        get() {
            frame
            nowPlaying?.let { update(it) }
            return clock.position(now()).toFloat()
        }

    /** The user seeked to [seconds]. */
    fun seek(seconds: Int) {
        clock.seek(seconds, now())
        frame++
    }

    /** Takes in what Clementine last said; does nothing when that hasn't changed. */
    private fun update(nowPlaying: NowPlaying) {
        val stopped = nowPlaying.state == Clementine.State.STOP
        val seconds = if (stopped) 0 else nowPlaying.positionSeconds
        val now = now()
        if (nowPlaying.song !== song || stopped) {
            clock.seek(seconds, now)
        } else if (seconds != reported) {
            clock.report(seconds, now)
        }
        clock.setPlaying(nowPlaying.isPlaying, now)
        song = nowPlaying.song
        reported = seconds
    }

    private fun now() = SystemClock.elapsedRealtimeNanos() / 1e9
}

/** The position of the song [nowPlaying] is playing, moving smoothly while it plays. */
@Composable
fun rememberSongPosition(nowPlaying: NowPlaying): SongPosition {
    val position = remember { SongPosition() }
    position.nowPlaying = nowPlaying
    if (nowPlaying.isPlaying) {
        LaunchedEffect(position) {
            while (true) {
                withInfiniteAnimationFrameNanos { position.frame = it }
            }
        }
    }
    return position
}
