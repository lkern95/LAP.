package de.run.app.domain

import de.run.app.domain.model.Workout
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrainingProgressAnalyzerTest {
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 11, 11)

    @Test
    fun `current week volume sums measured running distances`() {
        val progress = TrainingProgressAnalyzer().analyze(
            listOf(run("tuesday", today.minusDays(1), 12_000.0), run("wednesday", today, 2_200.0)),
            today,
            zone
        )

        val thisWeek = progress.weeklyRunVolumes.last()
        assertEquals(2, thisWeek.runCount)
        assertEquals(14_200.0, thisWeek.distanceMeters!!, 0.0)
    }

    @Test
    fun `current week volume stays unavailable if a run has no measured distance`() {
        val progress = TrainingProgressAnalyzer().analyze(
            listOf(run("measured", today.minusDays(1), 12_000.0), run("unknown", today, null)),
            today,
            zone
        )

        assertNull(progress.weeklyRunVolumes.last().distanceMeters)
    }

    private fun run(id: String, date: LocalDate, distanceMeters: Double?) = Workout(
        sourceId = id,
        sourceName = "FIT-Datei",
        title = "synthetic",
        startedAtMillis = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
        endedAtMillis = date.atTime(12, 30).atZone(zone).toInstant().toEpochMilli(),
        distanceMeters = distanceMeters
    )
}
