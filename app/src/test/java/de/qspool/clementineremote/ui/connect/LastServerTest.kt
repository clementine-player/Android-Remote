package de.qspool.clementineremote.ui.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Auto-connect finds the last Clementine by its name, else by its address. */
class LastServerTest {

    private val studio = Server("studio-pc", "192.168.1.30", 5500)
    private val kitchen = Server("kitchen", "192.168.1.20", 5500)

    @Test
    fun findsItByNameAtANewAddress() {
        assertEquals(studio, lastServer(listOf(kitchen, studio), "studio-pc", "192.168.1.20"))
    }

    @Test
    fun findsItByAddressWithoutAName() {
        assertEquals(kitchen, lastServer(listOf(studio, kitchen), "", "192.168.1.20"))
        assertEquals(kitchen, lastServer(listOf(studio, kitchen), null, "192.168.1.20"))
    }

    @Test
    fun findsItByAddressWhenTheNameIsGone() {
        assertEquals(kitchen, lastServer(listOf(studio, kitchen), "old-laptop", "192.168.1.20"))
    }

    @Test
    fun findsNothingWhenNeitherIsThere() {
        assertNull(lastServer(listOf(studio, kitchen), "old-laptop", "10.0.0.5"))
        assertNull(lastServer(listOf(studio, kitchen), null, null))
        assertNull(lastServer(emptyList(), "studio-pc", "192.168.1.30"))
    }
}
