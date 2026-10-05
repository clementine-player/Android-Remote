package de.qspool.clementineremote.ui.shell

/**
 * How fast data has moved lately: bytes a second over the last [windowMillis], from running totals
 * read now and then. Unlike the average since connecting, it falls to nothing soon after traffic
 * stops.
 */
internal class TrafficRate(private val windowMillis: Long = 5_000) {
    private val samples = ArrayDeque<Pair<Long, Long>>()

    /**
     * Adds the running total [bytes] read at [timeMillis], and returns the rate since the oldest
     * reading in the window; null until there are two readings to compare.
     */
    fun add(timeMillis: Long, bytes: Long): Long? {
        samples.addLast(timeMillis to bytes)
        // Keep one reading at or beyond the window's start, so the rate spans the whole window.
        while (samples.size > 2 && timeMillis - samples[1].first >= windowMillis) {
            samples.removeFirst()
        }
        val (since, before) = samples.first()
        val elapsed = timeMillis - since
        if (elapsed <= 0) return null
        return (bytes - before).coerceAtLeast(0) * 1000 / elapsed
    }
}
