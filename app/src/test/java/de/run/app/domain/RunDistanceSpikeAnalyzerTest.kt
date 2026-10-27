package de.run.app.domain

import de.run.app.domain.model.DistanceSpikeLevel
import de.run.app.domain.model.PaceTargetRange
import de.run.app.domain.model.PlannedRun
import de.run.app.domain.model.RunPrescription
import de.run.app.domain.model.RunStep
import de.run.app.domain.model.Workout
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RunDistanceSpikeAnalyzerTest {
    private val analyzer = RunDistanceSpikeAnalyzer()
    private val targetDate = LocalDate.of(2026, 10, 25)
    private val zone = ZoneOffset.UTC

    @Test
    fun `completed run at or below ten percent has no warning`() {
        assertEquals(DistanceSpikeLevel.NO_WARNING, assessCompleted(11_000.0).level)
        assertEquals(DistanceSpikeLevel.NO_WARNING, assessCompleted(10_000.0).level)
    }

    @Test
    fun `completed run gets three warning tiers above ten thirty and one hundred percent`() {
        assertEquals(DistanceSpikeLevel.NOTICE, assessCompleted(11_001.0).level)
        assertEquals(DistanceSpikeLevel.WARNING, assessCompleted(13_001.0).level)
        assertEquals(DistanceSpikeLevel.STRONG_WARNING, assessCompleted(20_001.0).level)
    }

    @Test
    fun `uses longest measured completed run from preceding thirty days`() {
        val history = listOf(
            workout("old", targetDate.minusDays(31), 14_000.0),
            workout("near", targetDate.minusDays(30), 10_000.0),
            workout("recent", targetDate.minusDays(5), 9_000.0)
        )

        val result = analyzer.assessCompleted(workout("today", targetDate, 11_000.0), history + workout("today", targetDate, 11_000.0), zone)

        assertEquals(10_000.0, result.referenceDistanceMeters!!, 0.0)
        assertEquals(DistanceSpikeLevel.NO_WARNING, result.level)
    }

    @Test
    fun `does not invent baseline or completed distance`() {
        val noBaseline = analyzer.assessCompleted(workout("today", targetDate, 10_000.0), emptyList(), zone)
        val noDistance = analyzer.assessCompleted(workout("today", targetDate, null), emptyList(), zone)

        assertEquals(DistanceSpikeLevel.NOT_ASSESSABLE, noBaseline.level)
        assertNotNull(noBaseline.runDistanceMeters)
        assertEquals(DistanceSpikeLevel.NOT_ASSESSABLE, noDistance.level)
        assertNull(noDistance.runDistanceMeters)
    }

    @Test
    fun `planned distance requires a measured baseline and personal pace range`() {
        val noBaseline = analyzer.assessPlanned(plannedRun(), emptyList(), targetDate, zone)
        val noPace = analyzer.assessPlanned(
            plannedRun().copy(prescription = RunPrescription()),
            listOf(workout("base", targetDate.minusDays(2), 8_000.0)),
            targetDate,
            zone
        )
        val estimated = analyzer.assessPlanned(
            plannedRun(),
            listOf(workout("base", targetDate.minusDays(2), 8_000.0)),
            targetDate,
            zone
        )

        assertEquals(DistanceSpikeLevel.NOT_ASSESSABLE, noBaseline.level)
        assertEquals(DistanceSpikeLevel.NOT_ASSESSABLE, noPace.level)
        assertEquals(DistanceSpikeLevel.NOTICE, estimated.level)
        assertEquals(true, estimated.isEstimate)
        assertNull(estimated.runDistanceMeters)
        assertEquals(10_000.0, estimated.estimatedDistanceMaximumMeters!!, 0.01)
    }

    private fun assessCompleted(distance: Double) = analyzer.assessCompleted(
        workout("today", targetDate, distance),
        listOf(workout("base", targetDate.minusDays(2), 10_000.0)),
        zone
    )

    private fun plannedRun(): PlannedRun = PlannedRun(
        title = "Testlauf",
        optional = false,
        steps = listOf(RunStep("Locker laufen", 60)),
        prescription = RunPrescription(paceRange = PaceTargetRange(slowerSecondsPerKm = 450, fasterSecondsPerKm = 360))
    )

    private fun workout(id: String, date: LocalDate, distance: Double?): Workout {
        val start = date.atTime(12, 0).toInstant(zone).toEpochMilli()
        return Workout(id, "test", "Lauf", start, start + 3_600_000, distance)
    }
}
