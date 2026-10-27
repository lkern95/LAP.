package de.run.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadinessSignalBandsTest {
    @Test fun readinessScoreBandsMatchTheRecoveryMeter() {
        assertEquals(ReadinessSignalBand.LOW, readinessSignalBand(59))
        assertEquals(ReadinessSignalBand.CAUTION, readinessSignalBand(60))
        assertEquals(ReadinessSignalBand.CAUTION, readinessSignalBand(79))
        assertEquals(ReadinessSignalBand.FAVORABLE, readinessSignalBand(80))
        assertNull(readinessSignalBand(null))
    }

    @Test fun garminStressBandsCoverGarminsDocumentedBoundaries() {
        assertEquals(GarminStressBand.RESTING, garminStressBand(0))
        assertEquals(GarminStressBand.RESTING, garminStressBand(25))
        assertEquals(GarminStressBand.LOW, garminStressBand(26))
        assertEquals(GarminStressBand.LOW, garminStressBand(50))
        assertEquals(GarminStressBand.MODERATE, garminStressBand(51))
        assertEquals(GarminStressBand.MODERATE, garminStressBand(75))
        assertEquals(GarminStressBand.HIGH, garminStressBand(76))
        assertEquals(GarminStressBand.HIGH, garminStressBand(100))
        assertNull(garminStressBand(null))
        assertNull(garminStressBand(101))
    }

    @Test fun recoveryIntervalBandsUseTheExistingRecencyBoundaries() {
        assertEquals(RecoveryIntervalBand.UNDER_12_HOURS, recoveryIntervalBand(719))
        assertEquals(RecoveryIntervalBand.UNDER_36_HOURS, recoveryIntervalBand(720))
        assertEquals(RecoveryIntervalBand.UNDER_36_HOURS, recoveryIntervalBand(2_159))
        assertEquals(RecoveryIntervalBand.AT_LEAST_36_HOURS, recoveryIntervalBand(2_160))
        assertNull(recoveryIntervalBand(null))
    }
}
