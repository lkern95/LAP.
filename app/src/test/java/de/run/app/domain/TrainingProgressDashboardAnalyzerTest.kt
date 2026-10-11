package de.run.app.domain

import de.run.app.domain.model.Workout
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrainingProgressDashboardAnalyzerTest {
    private val analyzer = TrainingProgressDashboardAnalyzer()
    private val today = LocalDate.parse("2026-10-08")

    @Test
    fun monthShowsActualDistancesAndComparesMatchingDaysOfPreviousMonth() {
        val result = analyzer.analyze(
            listOf(run("2026-10-02", 3_000.0), run("2026-10-07", 4_000.0), run("2026-09-07", 2_000.0)),
            ProgressPeriod.MONTH,
            today,
            ZoneOffset.UTC
        )

        assertEquals(7_000.0, result.distanceMeters, 0.0)
        assertEquals(2_000.0, result.previousDistanceMeters!!, 0.0)
        assertEquals(31, result.chartBuckets.size)
        assertEquals(3_000.0, result.chartBuckets[1].distanceMeters, 0.0)
        assertEquals(4_000.0, result.chartBuckets[6].distanceMeters, 0.0)
    }

    @Test
    fun weekUsesSevenDailyBucketsAndIncludesOnlyTheCurrentWeekInItsSummary() {
        val result = analyzer.analyze(
            listOf(run("2026-10-05", 5_000.0), run("2026-10-04", 8_000.0)),
            ProgressPeriod.WEEK,
            today,
            ZoneOffset.UTC
        )

        assertEquals(5_000.0, result.distanceMeters, 0.0)
        assertEquals(7, result.chartBuckets.size)
        assertEquals(5_000.0, result.chartBuckets.first().distanceMeters, 0.0)
    }

    @Test
    fun totalUsesAllStoredRunsAndCalculatesMeasuredLongestRunAndPace() {
        val result = analyzer.analyze(
            listOf(
                run("2026-10-02", 5_000.0, 1_800),
                run("2026-09-07", 10_000.0, 4_200),
                run("2025-02-01", 3_000.0, 1_200)
            ),
            ProgressPeriod.TOTAL,
            today,
            ZoneOffset.UTC
        )

        assertEquals(18_000.0, result.distanceMeters, 0.0)
        assertEquals(3, result.runCount)
        assertEquals(10_000.0, result.longestRecentRunMeters!!, 0.0)
        assertEquals(400L, result.averagePaceSecondsPerKilometer)
        assertEquals(12, result.chartBuckets.size)
        assertNull(result.previousDistanceMeters)
    }

    @Test
    fun totalWithRecentOnlyRunsStillUsesTwelveMonthlyChartSlots() {
        val result = analyzer.analyze(
            listOf(run("2026-10-02", 5_000.0), run("2026-10-07", 4_000.0)),
            ProgressPeriod.TOTAL,
            today,
            ZoneOffset.UTC
        )

        assertEquals(12, result.chartBuckets.size)
        assertEquals(0.0, result.chartBuckets.first().distanceMeters, 0.0)
        assertEquals(9_000.0, result.chartBuckets.last().distanceMeters, 0.0)
    }

    @Test
    fun missingDistanceDoesNotBecomeAZeroKilometerWorkoutEstimate() {
        val result = analyzer.analyze(
            listOf(run("2026-10-02", null)),
            ProgressPeriod.MONTH,
            today,
            ZoneOffset.UTC
        )

        assertEquals(1, result.runCount)
        assertEquals(0.0, result.distanceMeters, 0.0)
        assertNull(result.averagePaceSecondsPerKilometer)
        assertNull(result.longestRecentRunMeters)
    }

    private fun run(day: String, distance: Double?, seconds: Long = 1_800): Workout {
        val started = LocalDate.parse(day).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        return Workout("$day-$distance-$seconds", "Garmin Connect", "Lauf", started, started + seconds * 1_000, distance)
    }
}
