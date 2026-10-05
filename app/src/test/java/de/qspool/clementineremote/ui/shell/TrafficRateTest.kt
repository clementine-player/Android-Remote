package de.qspool.clementineremote.ui.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The traffic rate is over the last few seconds, so it falls soon after traffic stops. */
class TrafficRateTest {
    @Test
    fun needsTwoReadings() {
        assertNull(TrafficRate().add(0, 1_000))
    }

    @Test
    fun isTheRateOverTheWindow() {
        val rate = TrafficRate(windowMillis = 5_000)
        var last: Long? = null
        // 100 KB a second for 10 seconds, read twice a second.
        for (i in 0..20) last = rate.add(i * 500L, i * 50_000L)
        assertEquals(100_000L, last)
    }

    @Test
    fun fallsToNothingOnceTrafficStops() {
        val rate = TrafficRate(windowMillis = 5_000)
        // A minute of streaming at 100 KB a second, then nothing.
        for (i in 0..120) rate.add(i * 500L, i * 50_000L)
        val total = 120 * 50_000L

        assertEquals(50_000L, rate.add(62_500, total))
        var last: Long? = null
        for (i in 126..130) last = rate.add(i * 500L, total)
        assertEquals(0L, last)
    }

    @Test
    fun aTotalThatGoesBackIsNoTraffic() {
        val rate = TrafficRate()
        rate.add(0, 1_000)
        assertEquals(0L, rate.add(500, 0))
    }
}
