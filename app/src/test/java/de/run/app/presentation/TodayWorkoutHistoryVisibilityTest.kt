package de.run.app.presentation

import de.run.app.domain.model.Workout
import de.run.app.domain.model.WorkoutActivityType
import de.run.app.presentation.history.WorkoutHistoryUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayWorkoutHistoryVisibilityTest {
    @Test
    fun storedFitRunMakesCourseAndWeekDataVisibleWithoutHealthConnectAccess() {
        val history = WorkoutHistoryUiState(
            isLoading = false,
            workouts = listOf(syntheticRun()),
            accessMessage = "Health Connect permission missing"
        )

        assertTrue(hasStoredRunningWorkouts(history))
    }

    @Test
    fun noStoredRunsDoesNotShowCourseWhenAccessIsMissing() {
        val history = WorkoutHistoryUiState(
            isLoading = false,
            accessMessage = "Health Connect permission missing"
        )

        assertFalse(hasStoredRunningWorkouts(history))
    }

    @Test
    fun nonRunningActivityDoesNotCountAsRunHistory() {
        val history = WorkoutHistoryUiState(
            isLoading = false,
            workouts = listOf(syntheticRun().copy(activityType = WorkoutActivityType.CYCLING)),
            accessMessage = "Health Connect permission missing"
        )

        assertFalse(hasStoredRunningWorkouts(history))
    }

    @Test
    fun weeklyCalendarNamesSyntheticFitImportAsItsSource() {
        assertEquals("FIT-Import", weeklyRunSourceLabel(listOf(syntheticRun())))
    }

    @Test
    fun plannedRunCountUsesCorrectSingularLabel() {
        assertEquals("1 Lauf vorgesehen", plannedRunCountLabel(1))
        assertEquals("2 Läufe vorgesehen", plannedRunCountLabel(2))
    }

    private fun syntheticRun() = Workout(
        sourceId = "synthetic-fit-weekdots",
        sourceName = "FIT-Datei",
        title = "Synthetic run",
        startedAtMillis = 1_794_300_000_000,
        endedAtMillis = 1_794_330_000_000,
        distanceMeters = 5_000.0
    )
}
