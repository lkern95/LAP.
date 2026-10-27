package de.run.app.domain

import de.run.app.domain.model.PlannedRunAssessment
import de.run.app.domain.model.PlannedRunMatchType
import de.run.app.domain.model.RunningSymptomFeedback
import de.run.app.domain.model.Workout
import de.run.app.domain.model.WorkoutFeedback
import de.run.app.domain.model.WorkoutPlanMatch
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class ContinuousRunProgressionAnalyzerTest {
    private val analyzer = ContinuousRunProgressionAnalyzer()
    private val today = LocalDate.of(2026, 10, 25)

    @Test
    fun `starts at thirty minutes without qualifying evidence`() {
        assertEquals(30, analyzer.targetMinutes(emptyList(), today, 50, ZoneOffset.UTC))
    }

    @Test
    fun `one recent well matched easy long run adds five minutes`() {
        assertEquals(35, analyzer.targetMinutes(listOf(confirmedLongRun()), today, 50, ZoneOffset.UTC))
    }

    @Test
    fun `caps target to the running minutes available in the unit`() {
        assertEquals(34, analyzer.targetMinutes(listOf(confirmedLongRun()), today, 34, ZoneOffset.UTC))
    }

    @Test
    fun `does not progress for missing or adverse feedback`() {
        val missingFeedback = confirmedLongRun().copy(feedback = null)
        val adverseFeedback = confirmedLongRun().copy(
            feedback = WorkoutFeedback(6, RunningSymptomFeedback.UNSURE, today.toEpochDay())
        )

        assertEquals(30, analyzer.targetMinutes(listOf(missingFeedback, adverseFeedback), today, 50, ZoneOffset.UTC))
    }

    @Test
    fun `ignores long runs older than twelve weeks`() {
        val oldRun = confirmedLongRun(today.minusDays(85))

        assertEquals(30, analyzer.targetMinutes(listOf(oldRun), today, 50, ZoneOffset.UTC))
    }

    private fun confirmedLongRun(date: LocalDate = today.minusDays(1)) = Workout(
        sourceId = "long-run-$date",
        sourceName = "Garmin Connect",
        title = "Langer Lauf",
        startedAtMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        endedAtMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).plusSeconds(3_600).toEpochMilli(),
        distanceMeters = 8_000.0,
        feedback = WorkoutFeedback(3, RunningSymptomFeedback.NO, date.toEpochDay()),
        planMatch = WorkoutPlanMatch(
            type = PlannedRunMatchType.SCHEDULED,
            plannedTitle = ContinuousRunProgressionAnalyzer.LONG_RUN_TITLE,
            plannedDurationMinutes = 60,
            assessment = PlannedRunAssessment.WELL_MATCHED
        )
    )
}
