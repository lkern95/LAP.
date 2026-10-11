package de.run.app.data.healthconnect

import org.junit.Assert.assertEquals
import org.junit.Test

class HealthConnectSourceLabelTest {
    @Test
    fun labelsOnlyKnownWriterPackages() {
        assertEquals("Garmin Connect", HealthConnectSourceLabel.forPackage("com.garmin.android.apps.connectmobile"))
        assertEquals("Health Sync", HealthConnectSourceLabel.forPackage("nl.appyhapps.healthsync"))
        assertEquals("Unbekannte Quelle", HealthConnectSourceLabel.forPackage("example.unknown"))
    }
}
