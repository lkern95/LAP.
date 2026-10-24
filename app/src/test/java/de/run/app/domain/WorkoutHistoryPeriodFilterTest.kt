package de.run.app.domain

import de.run.app.domain.model.Workout
import de.run.app.domain.model.WorkoutActivityType
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutHistoryPeriodFilterTest {
    private val filter = WorkoutHistoryPeriodFilter(ZoneOffset.UTC)
    private val workouts = listOf(
        workout("2025-12-30", "run", WorkoutActivityType.RUNNING),
        workout("2026-01-03", "walk", WorkoutActivityType.WALKING),
        workout("2026-01-06", "run", WorkoutActivityType.RUNNING),
        workout("2026-03-02", "ride", WorkoutActivityType.CYCLING)
    )

    @Test
    fun listsAvailableYearsAndMonthsNewestFirst() {
        assertEquals(listOf(2026, 2025), filter.years(workouts))
        assertEquals(listOf(3, 1), filter.months(workouts, 2026))
        assertEquals(WorkoutHistoryPeriodSelection(2026, 3), filter.latestSelection(workouts))
    }

    @Test
    fun normalizesMissingPeriodAndFiltersByMonthAndSportNewestFirst() {
        val selection = filter.normalize(workouts, WorkoutHistoryPeriodSelection(2026, 2))
        assertEquals(WorkoutHistoryPeriodSelection(2026, 3), selection)
        assertEquals(
            listOf("2026-01-06-run"),
            filter.filter(workouts, WorkoutHistoryPeriodSelection(2026, 1), WorkoutActivityType.RUNNING)
                .map(Workout::sourceId)
        )
        assertEquals("Januar", filter.monthName(1))
    }

    private fun workout(day: String, id: String, type: WorkoutActivityType): Workout {
        val start = LocalDate.parse(day).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        return Workout("$day-$id", "Garmin Connect", type.label, start, start + 1_000, 1_000.0, activityType = type)
    }
}
