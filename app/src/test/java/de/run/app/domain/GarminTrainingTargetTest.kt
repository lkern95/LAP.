package de.run.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GarminTrainingTargetTest {
    @Test
    fun acceptsOnlyMatchingVersionedTargetAcknowledgementShape() {
        assertEquals(
            GarminTrainingTarget("lap-run-1", 2_700),
            GarminTrainingTarget.decodeAcknowledgement(listOf("LAP_TARGET_ACK", 1, "lap-run-1", 2_700))
        )
        assertNull(GarminTrainingTarget.decodeAcknowledgement(listOf("LAP_TARGET_ACK", 2, "lap-run-1", 2_700)))
        assertNull(GarminTrainingTarget.decodeAcknowledgement(listOf("LAP_TARGET_ACK", 1, "", 2_700)))
        assertNull(GarminTrainingTarget.decodeAcknowledgement(listOf("LAP_TARGET_ACK", 1, "lap-run-1", 0)))
        assertNull(GarminTrainingTarget.decodeAcknowledgement(listOf("LAP_TARGET_ACK", 1, "lap-run-1", 2_700, 5)))
    }
}
