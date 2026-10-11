package de.run.app.domain

import de.run.app.domain.model.Workout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutMetricsTest {
    @Test
    fun paceUsesRecordedDurationAndDistance() {
        val workout = workout(distanceMeters = 5_000.0, durationSeconds = 2_220)

        assertEquals(444L, workout.paceSecondsPerKilometer)
    }

    @Test
    fun paceIsMissingWhenDistanceOrDurationIsUnavailable() {
        assertNull(workout(distanceMeters = null, durationSeconds = 2_220).paceSecondsPerKilometer)
        assertNull(workout(distanceMeters = 0.0, durationSeconds = 2_220).paceSecondsPerKilometer)
        assertNull(workout(distanceMeters = 5_000.0, durationSeconds = 0).paceSecondsPerKilometer)
    }

    private fun workout(distanceMeters: Double?, durationSeconds: Long) = Workout(
        sourceId = "test-run",
        sourceName = "Test",
        title = "Lauf",
        startedAtMillis = 0,
        endedAtMillis = durationSeconds * 1_000,
        distanceMeters = distanceMeters
    )
}
