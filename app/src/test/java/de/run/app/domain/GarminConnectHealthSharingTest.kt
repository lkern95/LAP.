package de.run.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GarminConnectHealthSharingTest {
    @Test
    fun garminSharingRequiresAndroid14() {
        assertFalse(GarminConnectHealthSharing.isSupported(29))
        assertFalse(GarminConnectHealthSharing.isSupported(33))
        assertTrue(GarminConnectHealthSharing.isSupported(34))
        assertTrue(GarminConnectHealthSharing.isSupported(36))
    }
}
