package de.run.app.data.garmin.live

import com.garmin.android.connectiq.IQApp
import de.run.app.domain.model.GarminTransportMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GarminConnectIqCompanionTest {
    @Test
    fun tetheredSimulatorAcceptsUnknownApplicationStatus() {
        assertTrue(
            GarminConnectIqCompanion.isApplicationAvailable(
                IQApp.IQAppStatus.UNKNOWN,
                useSimulatorTransport = true
            )
        )
    }

    @Test
    fun wirelessTransportRequiresInstalledApplication() {
        assertFalse(
            GarminConnectIqCompanion.isApplicationAvailable(
                IQApp.IQAppStatus.UNKNOWN,
                useSimulatorTransport = false
            )
        )
        assertTrue(
            GarminConnectIqCompanion.isApplicationAvailable(
                IQApp.IQAppStatus.INSTALLED,
                useSimulatorTransport = false
            )
        )
    }

    @Test
    fun tetheredSimulatorListensForRealAndEmptyApplicationIds() {
        assertEquals(
            listOf(GarminConnectIqCompanion.LAP_LIVE_DATA_FIELD_ID, ""),
            GarminConnectIqCompanion.listenerAppIds(
                GarminConnectIqCompanion.LAP_LIVE_DATA_FIELD_ID,
                useSimulatorTransport = true
            )
        )
    }

    @Test
    fun wirelessTransportListensForTheDataFieldApplicationId() {
        assertEquals(
            listOf(GarminConnectIqCompanion.LAP_LIVE_DATA_FIELD_ID),
            GarminConnectIqCompanion.listenerAppIds(
                GarminConnectIqCompanion.LAP_LIVE_DATA_FIELD_ID,
                useSimulatorTransport = false
            )
        )
    }

    @Test
    fun realDeviceUsesWirelessTransportWhileSimulatorUsesTethered() {
        assertEquals(
            GarminTransportMode.WIRELESS,
            GarminConnectIqCompanion.transportMode(useSimulatorTransport = false)
        )
        assertEquals(
            GarminTransportMode.TETHERED,
            GarminConnectIqCompanion.transportMode(useSimulatorTransport = true)
        )
    }

    @Test
    fun duplicateSimulatorCallbackIsDeliveredOnlyOnce() {
        val recent = linkedSetOf<List<Any?>>()
        val message = listOf<Any?>("LAP_PING", 1, 7)

        assertTrue(GarminConnectIqCompanion.rememberNewMessage(message, recent))
        assertFalse(GarminConnectIqCompanion.rememberNewMessage(message, recent))
        assertEquals(1, recent.size)
    }

    @Test
    fun simulatorMessageDeduplicationIsBounded() {
        val recent = linkedSetOf<List<Any?>>()
        repeat(40) { sequence ->
            GarminConnectIqCompanion.rememberNewMessage(listOf<Any?>("LAP_PING", 1, sequence), recent)
        }

        assertEquals(32, recent.size)
        assertFalse(GarminConnectIqCompanion.rememberNewMessage(listOf<Any?>("LAP_PING", 1, 39), recent))
    }
}
