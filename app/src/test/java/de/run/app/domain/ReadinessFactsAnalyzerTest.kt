package de.run.app.domain

import de.run.app.domain.model.TrainingProfile
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

        val facts = analyzer.analyze(workouts, profile(optional = false), now, today)

        assertEquals(1, facts.runsThisWeek)
        assertEquals(2, facts.plannedRunsThisWeek)
        assertEquals(5_000.0, facts.distanceThisWeekMeters, 0.0)
        assertEquals(48L * 60L, facts.minutesSinceLastRun)
    }

    @Test
    fun optionalThirdRunChangesOnlyTheDisplayedPlanCount() {
        val facts = analyzer.analyze(emptyList(), profile(optional = true), now, today)

        assertEquals(0, facts.runsThisWeek)
        assertEquals(3, facts.plannedRunsThisWeek)
        assertEquals(null, facts.minutesSinceLastRun)
    }

    private fun profile(optional: Boolean) = TrainingProfile(
        longestComfortableRunMinutes = 60,
        optionalRun = optional,
        strengthSessionsPerWeek = 1
    )

    private fun workout(id: String, date: LocalDate, distance: Double) = Workout(
        sourceId = id,
        sourceName = "Garmin Connect",
        title = "Lauf",
        startedAtMillis = date.atTime(17, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
        endedAtMillis = date.atTime(18, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
        distanceMeters = distance
    )
}
