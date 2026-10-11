package de.run.app.data.healthconnect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthConnectWorkoutPermissionsTest {
    @Test
    fun `only exercise session permission is required to import runs`() {
        assertEquals(
            setOf(HealthConnectWorkoutPermissions.exerciseSessions),
            HealthConnectWorkoutPermissions.requiredForImport
        )
    }

    @Test
    fun `distance and heart rate permissions are optional measurements`() {
        assertEquals(
            setOf(
                HealthConnectWorkoutPermissions.distances,
                HealthConnectWorkoutPermissions.heartRates
            ),
            HealthConnectWorkoutPermissions.optionalMeasurements
        )
        assertTrue(HealthConnectWorkoutPermissions.optionalMeasurements.none {
            it in HealthConnectWorkoutPermissions.requiredForImport
        })
    }
}
