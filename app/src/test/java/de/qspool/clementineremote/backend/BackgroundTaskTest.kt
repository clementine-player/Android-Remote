package de.qspool.clementineremote.backend

import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A background task runs off the main thread, and reports its progress and result, or that it was
 * cancelled, on the main thread.
 */
@RunWith(RobolectricTestRunner::class)
class BackgroundTaskTest {

    private val events = mutableListOf<String>()

    /** Counts to what it's given, in the background, until it's cancelled. */
    private inner class Counter(private val release: CountDownLatch = CountDownLatch(0)) :
        BackgroundTask<Int, Int, String>() {

        val mainThread = Looper.getMainLooper().thread

        /** Whether its wait was interrupted, by cancelling. */
        @Volatile
        var interrupted = false

        fun start(to: Int) = execute(to)

        override fun doInBackground(params: Int): String {
            assertFalse("Ran on the main thread", Thread.currentThread() == mainThread)
            // Held until the test says, so it can be cancelled midway.
            try {
                release.await(5, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                interrupted = true
            }
            var count = 0
            while (count < params && !isCancelled) {
                count++
                publishProgress(count)
            }
            return "counted $count"
        }

        override fun onProgressUpdate(progress: Int) {
            assertTrue(Thread.currentThread() == mainThread)
            events += "progress $progress"
        }

        override fun onPostExecute(result: String) {
            assertTrue(Thread.currentThread() == mainThread)
            events += result
        }

        override fun onCancelled(result: String) {
            events += "cancelled after $result"
        }
    }

    /** Idles the main thread until [done], as results arrive from the background. */
    private fun idleUntil(done: () -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (!done() && System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }

    @Test
    fun reportsProgressThenTheResultOnTheMainThread() {
        val task = Counter()
        assertEquals(BackgroundTask.Status.PENDING, task.status)

        task.start(3)
        assertEquals(BackgroundTask.Status.RUNNING, task.status)
        idleUntil { task.status == BackgroundTask.Status.FINISHED }

        assertEquals(listOf("progress 1", "progress 2", "progress 3", "counted 3"), events)
        assertFalse(task.isCancelled)
        // Finished: there's nothing left to cancel.
        assertFalse(task.cancel(true))
    }

    @Test
    fun aCancelledTaskReportsThatInsteadOfItsResult() {
        val release = CountDownLatch(1)
        val task = Counter(release)
        task.start(1_000_000)

        assertTrue(task.cancel(false))
        release.countDown()
        idleUntil { task.status == BackgroundTask.Status.FINISHED }
        assertFalse("Cancelling without interrupting interrupted it", task.interrupted)

        assertTrue(task.isCancelled)
        assertEquals(listOf("cancelled after counted 0"), events)
    }

    @Test
    fun cancellingCanInterruptTheWork() {
        // Held until interrupted: the latch is never released.
        val task = Counter(CountDownLatch(1))
        task.start(1)

        // Let it get as far as waiting, then cancel it.
        Thread.sleep(100)
        task.cancel(true)
        idleUntil { task.status == BackgroundTask.Status.FINISHED }

        assertTrue("The work wasn't interrupted", task.interrupted)
        assertEquals(listOf("cancelled after counted 0"), events)
    }

    @Test(expected = IllegalStateException::class)
    fun runsOnlyOnce() {
        val task = Counter()
        task.start(1)
        task.start(1)
    }
}
