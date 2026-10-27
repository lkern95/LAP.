package de.run.app.domain

import de.run.app.domain.model.Workout
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadinessFactsAnalyzerTest {
    private val analyzer = ReadinessFactsAnalyzer()
    private val today = LocalDate.of(2026, 10, 9)
    private val now = today.atTime(18, 0).atZone(java.time.ZoneId.systemDefault()).toInstant()

    @Test
    fun countsOnlyThisWeekAndReportsTimeSinceLatestRun() {
        val workouts = listOf(
            workout("this-week", today.minusDays(2), 5_000.0),
            workout("last-week", today.minusDays(7), 8_000.0)
        )

        val facts = analyzer.analyze(workouts, plannedRunCount = 2, now, today)

        assertEquals(1, facts.runsThisWeek)
        assertEquals(2, facts.plannedRunsThisWeek)
        assertEquals(5_000.0, facts.distanceThisWeekMeters, 0.0)
        assertEquals(48L * 60L, facts.minutesSinceLastRun)
    }

    @Test
    fun displayedPlanCountComesFromTheGeneratedPlanInsteadOfTheOptionalPreference() {
        val facts = analyzer.analyze(emptyList(), plannedRunCount = 2, now, today)

        assertEquals(0, facts.runsThisWeek)
        assertEquals(2, facts.plannedRunsThisWeek)
        assertEquals(null, facts.minutesSinceLastRun)
    }

    @Test
    fun missingPlanDoesNotInventPlannedRuns() {
        val facts = analyzer.analyze(emptyList(), plannedRunCount = null, now, today)

        assertEquals(0, facts.plannedRunsThisWeek)
    }

    private fun workout(id: String, date: LocalDate, distance: Double) = Workout(
        sourceId = id,
        sourceName = "Garmin Connect",
        title = "Lauf",
        startedAtMillis = date.atTime(17, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
        endedAtMillis = date.atTime(18, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
        distanceMeters = distance
    )
}
