package de.run.app.domain

import de.run.app.domain.model.RunningSymptomFeedback
import de.run.app.domain.model.TrainingGoal
import de.run.app.domain.model.TrainingProfile
import de.run.app.domain.model.Workout
import de.run.app.domain.model.WorkoutFeedback
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunPrescriptionAnalyzerTest {
    private val analyzer = RunPrescriptionAnalyzer()
    private val today = LocalDate.of(2026, 10, 19)

    @Test
    fun `five explicitly easy comparable runs provide an observed pace range`() {
        val runs = listOf(360, 365, 370, 375, 380).mapIndexed { index, pace ->
            workout(today.minusDays(7L + index * 3), pace, effort = 3)
        }

        val prescription = analyzer.analyze(runs, today, 30, ZoneOffset.UTC)

        assertEquals(365, prescription.paceRange?.fasterSecondsPerKm)
        assertEquals(375, prescription.paceRange?.slowerSecondsPerKm)
        assertTrue(prescription.rationale.contains("5 lockeren Vergleichsläufen"))
        assertNull(prescription.heartRateRangeBpm)
    }

    @Test
    fun `easy runs without explicit effort never become personal pace targets`() {
        val runs = (0 until 8).map { index -> workout(today.minusDays((index + 1).toLong()), 360, effort = null) }

        val prescription = analyzer.analyze(runs, today, 30, ZoneOffset.UTC)

        assertNull(prescription.paceRange)
        assertEquals(360, prescription.observedPaceRange?.fasterSecondsPerKm)
        assertTrue(prescription.rationale.contains("RPE von 2–4 dokumentiert"))
    }

    @Test
    fun `unknown Garmin intensity provides orientation but never a coach target`() {
        val runs = (0 until 5).map { index -> workout(today.minusDays((index + 1).toLong()), 360 + index, effort = null) }

        val prescription = analyzer.analyze(runs, today, 30, ZoneOffset.UTC)

        assertNull(prescription.paceRange)
        assertTrue(prescription.observedPaceRange != null)
        assertTrue(prescription.rationale.contains("dokumentiertes RPE"))
    }

    @Test
    fun `tempo or symptomatic runs are not used for easy pace prescription`() {
        val runs = (0 until 6).map { index ->
            workout(
                today.minusDays((index + 1).toLong()),
                350 + index * 5,
                effort = if (index == 0) 8 else 3,
                symptoms = if (index == 1) RunningSymptomFeedback.YES else RunningSymptomFeedback.NO
            )
        }

        assertNull(analyzer.analyze(runs, today, 30, ZoneOffset.UTC).paceRange)
    }

    @Test
    fun `five easy runs from a different duration are not considered comparable`() {
        val runs = (0 until 5).map { index ->
            workout(today.minusDays((index + 1).toLong()), 360, effort = 3).copy(
                endedAtMillis = workout(today.minusDays((index + 1).toLong()), 360, effort = 3)
                    .startedAtMillis + 60 * 60 * 1_000L
            )
        }

        val prescription = analyzer.analyze(runs, today, 30, ZoneOffset.UTC)

        assertNull(prescription.paceRange)
        assertTrue(prescription.rationale.contains("0 Läufe mit mindestens 3 km und passender Dauer"))
        assertTrue(prescription.rationale.contains("ähnliche Dauer"))
    }

    private fun workout(
        date: LocalDate,
        pace: Int,
        effort: Int?,
        symptoms: RunningSymptomFeedback = RunningSymptomFeedback.NO
    ): Workout {
        val start = date.atTime(8, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val duration = pace * 5L
        return Workout(
            sourceId = "synthetic:$date:$pace",
            sourceName = "Testdaten",
            title = "Lauf",
            startedAtMillis = start,
            endedAtMillis = start + duration * 1_000,
            distanceMeters = 5_000.0,
            feedback = effort?.let { WorkoutFeedback(it, symptoms, start + duration * 1_000) }
        )
    }
}
