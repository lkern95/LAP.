package de.run.app.domain

import de.run.app.domain.model.Workout
import de.run.app.domain.model.WorkoutActivityType
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyTrainingComparisonAnalyzerTest {
    private val analyzer = WeeklyTrainingComparisonAnalyzer()
    private val today = LocalDate.parse("2026-10-15")

    @Test
    fun comparesTheLastTwoCompleteWeeksAndOnlyRunningActivities() {
        val result = analyzer.analyze(
            listOf(
                workout("2026-09-30", "old", 5_000.0, 1_800),
                workout("2026-10-03", "old2", 5_000.0, 1_800),
                workout("2026-10-06", "new", 7_500.0, 2_700),
                workout("2026-10-10", "new2", 7_500.0, 2_700),
                workout("2026-10-13", "future-week", 50_000.0, 10_000),
                workout("2026-10-08", "walk", 10_000.0, 3_000, WorkoutActivityType.WALKING)
            ),
            today,
            ZoneOffset.UTC
        )

        assertEquals(LocalDate.parse("2026-10-05"), result.current.startsOn)
        assertEquals(LocalDate.parse("2026-09-28"), result.previous.startsOn)
        assertEquals(2, result.current.runCount)
        assertEquals(15_000.0, result.current.distanceMeters!!, 0.0)
        assertEquals(50, result.distanceChangePercent)
        assertEquals(360L, result.current.comparablePaceSecondsPerKilometer)
        assertTrue(result.feedback.contains("50 %"))
    }

    @Test
    fun doesNotTurnMissingDistancesIntoZeroOrInventPace() {
        val result = analyzer.analyze(
            listOf(workout("2026-10-06", "missing", null, 1_800)),
            today,
            ZoneOffset.UTC
        )
        assertNull(result.current.distanceMeters)
        assertNull(result.current.comparablePaceSecondsPerKilometer)
        assertNull(result.distanceChangePercent)
    }

    @Test
    fun comparesPulseOnlyForRunsWithComparableDistances() {
        val result = analyzer.analyze(
            listOf(
                workout("2026-09-29", "previous-a", 5_000.0, 1_800).copy(averageHeartRateBpm = 140),
                workout("2026-10-01", "previous-b", 5_100.0, 1_836).copy(averageHeartRateBpm = 142),
                workout("2026-10-03", "previous-outlier", 10_000.0, 3_600).copy(averageHeartRateBpm = 170),
                workout("2026-10-06", "current-a", 5_200.0, 1_872).copy(averageHeartRateBpm = 138),
                workout("2026-10-08", "current-b", 5_300.0, 1_908).copy(averageHeartRateBpm = 140),
                workout("2026-10-10", "current-outlier", 10_500.0, 3_780).copy(averageHeartRateBpm = 168)
            ),
            today,
            ZoneOffset.UTC
        )

        assertEquals(141, result.previous.averageHeartRateBpm)
        assertEquals(139, result.current.averageHeartRateBpm)
        assertEquals(2, result.previous.heartRateRunCount)
        assertEquals(2, result.current.heartRateRunCount)
    }

    @Test
    fun comparisonClassifiesMetricChangesByTheirMeaningAndLeavesMissingValuesUnrated() {
        val previous = TrainingWeekStats(LocalDate.parse("2026-10-05"), 3, 18_400.0, 8_340, 434, 144)
        val current = TrainingWeekStats(LocalDate.parse("2026-10-12"), 3, 19_200.0, 8_760, 437, 145)
        val changes = weeklyTrainingChanges(current, previous)

        assertEquals(TrainingWeekTrend.UNCHANGED, changes.runCount.trend)
        assertEquals(TrainingWeekTrend.IMPROVED, changes.distanceMeters.trend)
        assertEquals(800.0, changes.distanceMeters.amount!!, 0.0)
        assertEquals(TrainingWeekTrend.IMPROVED, changes.durationSeconds.trend)
        assertEquals(TrainingWeekTrend.DECLINED, changes.paceSecondsPerKilometer.trend)
        assertEquals(TrainingWeekTrend.DECLINED, changes.averageHeartRateBpm.trend)

        val missing = weeklyTrainingChanges(current.copy(comparablePaceSecondsPerKilometer = null), previous)
        assertNull(missing.paceSecondsPerKilometer.amount)
        assertNull(missing.paceSecondsPerKilometer.trend)
    }

    private fun workout(day: String, id: String, meters: Double?, duration: Long, type: WorkoutActivityType = WorkoutActivityType.RUNNING): Workout {
        val start = LocalDate.parse(day).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        return Workout(id, "Garmin Connect", type.label, start, start + duration * 1_000, meters, activityType = type)
    }
}
