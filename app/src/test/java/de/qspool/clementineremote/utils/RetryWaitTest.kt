package de.qspool.clementineremote.utils

import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseDisconnect
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** How long Clementine said to wait, in words. */
@RunWith(RobolectricTestRunner::class)
class RetryWaitTest {

    private fun describe(seconds: Int?): String {
        val disconnect = ResponseDisconnect.newBuilder()
        seconds?.let { disconnect.setRetryAfterSeconds(it) }
        return RetryWait.describe(RuntimeEnvironment.getApplication().resources, disconnect.build())
    }

    @Test
    fun secondsUnderAMinute() {
        assertEquals("10 seconds", describe(10))
        assertEquals("1 second", describe(1))
    }

    @Test
    fun roundedUpToMinutesOrHours() {
        assertEquals("1 minute", describe(60))
        assertEquals("2 minutes", describe(61))
        assertEquals("43 minutes", describe(2560))
        assertEquals("60 minutes", describe(3600))
        assertEquals("2 hours", describe(3601))
    }

    @Test
    fun aWhileWhenClementineDoesntSay() {
        assertEquals("a while", describe(null))
    }
}
