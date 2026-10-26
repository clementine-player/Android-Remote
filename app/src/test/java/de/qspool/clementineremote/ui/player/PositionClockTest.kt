package de.qspool.clementineremote.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

/** The position moves smoothly between Clementine's reports, which come once a second. */
class PositionClockTest {

    private fun playingFrom(seconds: Int, now: Double = 0.0) = PositionClock().apply {
        seek(seconds, now)
        setPlaying(true, now)
    }

    @Test
    fun movesBetweenReports() {
        val clock = playingFrom(10)
        assertEquals(10.0, clock.position(0.0), 0.001)
        assertEquals(10.25, clock.position(0.25), 0.001)
        assertEquals(10.9, clock.position(0.9), 0.001)
    }

    @Test
    fun keepsMovingEvenlyThroughReportsThatAgree() {
        val clock = playingFrom(10)
        // Clementine's timer runs a little late, and its rounding skips a second.
        clock.report(11, 1.05)
        clock.report(12, 2.1)
        clock.report(14, 3.6)
        assertEquals(13.6, clock.position(3.6), 0.001)
        assertEquals(14.0, clock.position(4.0), 0.001)
    }

    @Test
    fun aReportPullsTheEstimateWithinItsRounding() {
        val clock = playingFrom(10)
        // The song really started half a second in.
        clock.report(12, 1.0)
        assertEquals(11.5, clock.position(1.0), 0.001)
        assertEquals(12.0, clock.position(1.5), 0.001)
    }

    @Test
    fun aReportFarFromTheEstimateIsASeek() {
        val clock = playingFrom(10)
        clock.report(60, 1.0)
        assertEquals(60.0, clock.position(1.0), 0.001)
        assertEquals(60.5, clock.position(1.5), 0.001)
    }

    @Test
    fun stopsWhenPaused() {
        val clock = playingFrom(10)
        clock.setPlaying(false, 0.6)
        assertEquals(10.6, clock.position(5.0), 0.001)
        clock.setPlaying(true, 5.0)
        assertEquals(10.8, clock.position(5.2), 0.001)
    }

    @Test
    fun waitsWhenReportsStop() {
        val clock = playingFrom(10)
        assertEquals(12.0, clock.position(30.0), 0.001)
    }

    @Test
    fun seeksExactly() {
        val clock = playingFrom(10)
        clock.seek(42, 0.5)
        assertEquals(42.0, clock.position(0.5), 0.001)
    }
}
