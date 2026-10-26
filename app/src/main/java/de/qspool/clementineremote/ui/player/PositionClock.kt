package de.qspool.clementineremote.ui.player

import kotlin.math.abs

/**
 * Estimates the song's position between Clementine's reports of it, so it can move smoothly.
 *
 * While playing, Clementine reports the position once a second, rounded to whole seconds, and
 * while paused not at all. Between reports the position moves with this phone's clock. Each
 * report pulls it back within the half second the rounding allows, and one far from it (Clementine
 * seeked) moves it there.
 *
 * Times are in seconds, on any clock that only moves forward.
 */
class PositionClock {
    private var anchor = 0.0
    private var anchorTime = 0.0
    private var playing = false

    /** The position at [now], in seconds. */
    fun position(now: Double): Double {
        if (!playing) return anchor
        // If Clementine's reports stop coming, the position stops soon after, rather than run on.
        return anchor + (now - anchorTime).coerceIn(0.0, MAX_UNREPORTED)
    }

    /** Clementine reported the position, in whole seconds. */
    fun report(seconds: Int, now: Double) {
        val estimate = position(now)
        anchor = if (abs(estimate - seconds) > JUMP) {
            seconds.toDouble()
        } else {
            estimate.coerceIn(seconds - ROUNDING, seconds + ROUNDING + LATENCY)
        }
        anchorTime = now
    }

    /** Clementine started or stopped playing. */
    fun setPlaying(playing: Boolean, now: Double) {
        if (playing == this.playing) return
        anchor = position(now)
        anchorTime = now
        this.playing = playing
    }

    /** The position is now exactly [seconds]: a new song started, or the user seeked. */
    fun seek(seconds: Int, now: Double) {
        anchor = seconds.toDouble()
        anchorTime = now
    }

    private companion object {
        /** Clementine rounds the position to the nearest second. */
        const val ROUNDING = 0.5
        /** How late a report can arrive, and still only correct the estimate. */
        const val LATENCY = 0.25
        /** Further than this from the estimate, a report is a seek. */
        const val JUMP = 1.5
        /** Reports come every second; without one for longer, the position waits for the next. */
        const val MAX_UNREPORTED = 2.0
    }
}
