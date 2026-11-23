package de.qspool.clementineremote.ui.player

import android.os.SystemClock
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import de.qspool.clementineremote.backend.Clementine
import de.qspool.clementineremote.backend.RemoteRepository.NowPlaying
import de.qspool.clementineremote.backend.player.MySong
import kotlin.math.abs

/**
 * The song's position, which moves on every frame while Clementine plays, rather than once a
 * second when Clementine reports it.
 */
@Stable
class SongPosition internal constructor() {
    private val clock = PositionClock()
    private var song: MySong? = null
    private var reported = 0
    private var synced: NowPlaying? = null
    private var seekTarget = 0
    private var seekGraceUntil = 0.0

    /** Changes on every frame while playing (and on sync), so reading [seconds] observes them. */
    internal var frame by mutableLongStateOf(0L)

    /** In seconds. */
    val seconds: Float
        get() {
            frame
            return clock.position(now()).toFloat()
        }

    /** The user seeked to [seconds]. */
    fun seek(seconds: Int) {
        val now = now()
        // Hold a short grace window so an in-flight 1s tick from before the seek doesn't snap back.
        seekTarget = seconds
        seekGraceUntil = now + SEEK_GRACE_SECONDS
        clock.seek(seconds, now)
        frame++
    }

    /** Synchronizes with [nowPlaying] and wakes snapshot readers if anything changed. */
    internal fun sync(nowPlaying: NowPlaying) {
        if (synced === nowPlaying) return
        update(nowPlaying)
        frame++
    }

    /** Takes in what Clementine last said. */
    internal fun update(nowPlaying: NowPlaying) {
        synced = nowPlaying
        val stopped = nowPlaying.state == Clementine.State.STOP
        val seconds = if (stopped) 0 else nowPlaying.positionSeconds
        val now = now()
        if (nowPlaying.song !== song || stopped) {
            seekGraceUntil = 0.0
            clock.seek(seconds, now)
        } else if (seconds != reported) {
            // Apply server ticks only once the grace window ends or Clementine confirms near seekTarget.
            if (now >= seekGraceUntil || abs(seconds - seekTarget) <= 2) {
                seekGraceUntil = 0.0
                clock.report(seconds, now)
            }
        }
        clock.setPlaying(nowPlaying.isPlaying, now)
        song = nowPlaying.song
        reported = seconds
    }

    private fun now() = SystemClock.elapsedRealtimeNanos() / 1e9

    private companion object {
        const val SEEK_GRACE_SECONDS = 1.5
    }
}

/** The position of the song [nowPlaying] is playing, moving smoothly while it plays. */
@Composable
fun rememberSongPosition(nowPlaying: NowPlaying): SongPosition {
    val position = remember { SongPosition().apply { update(nowPlaying) } }
    // Sync inside LaunchedEffect rather than during composition to avoid Snapshot writes in compose.
    LaunchedEffect(position, nowPlaying) {
        position.sync(nowPlaying)
        if (nowPlaying.isPlaying) {
            while (true) {
                withInfiniteAnimationFrameNanos { position.frame = it }
            }
        }
    }
    return position
}
