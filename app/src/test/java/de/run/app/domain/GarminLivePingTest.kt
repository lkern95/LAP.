package de.run.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GarminLivePingTest {
    @Test
    fun decodesMinimalDataFreePing() {
        assertEquals(GarminLivePing(sequence = 12), GarminLivePing.decode(listOf("LAP_PING", 1, 12)))
    }

    @Test
    fun rejectsMalformedOrUnsupportedPing() {
        assertNull(GarminLivePing.decode(null))
        assertNull(GarminLivePing.decode(listOf("LAP_PING", 2, 12)))
        assertNull(GarminLivePing.decode(listOf("LAP_PING", 1, -1)))
        assertNull(GarminLivePing.decode(listOf("LAP_PING", 1, 12, 42)))
    }
}
