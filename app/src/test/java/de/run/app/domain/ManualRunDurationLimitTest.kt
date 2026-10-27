package de.run.app.domain

import de.run.app.domain.model.PlannedRun
import de.run.app.domain.model.RunStep
import de.run.app.domain.model.TrainingType
import de.run.app.domain.model.Workout
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class ManualRunDurationLimitTest {
    private val limiter = ManualRunDurationLimit()
    private val date = LocalDate.of(2026, 10, 25)
    private val zone = ZoneOffset.UTC
    private val run = PlannedRun("Locker Lauf", false, listOf(RunStep("Locker laufen", 40)))

    @Test
    fun `requires recent actual workload before allowing manual extension`() {
        val result = limiter.maximumMinutes(run, emptyList(), date, 90, zone)

        assertEquals(40, result.maximumMinutes)
        assertEquals(true, result.reason?.contains("fehlt") == true)
    }

    @Test
    fun `allows only current plan step beyond longest measured recent run`() {
        val history = listOf(workout("recent", date.minusDays(10), 8_000.0, 50))

        val result = limiter.maximumMinutes(run, history, date, 90, zone)

        assertEquals(60, result.maximumMinutes)
        assertEquals(true, result.reason?.contains("50 Minuten") == true)
    }

    @Test
    fun `keeps day ceiling and ignores runs older than thirty days`() {
        val old = workout("old", date.minusDays(31), 9_000.0, 80)

        val result = limiter.maximumMinutes(run, listOf(old), date, 45, zone)

        assertEquals(40, result.maximumMinutes)
        assertEquals(true, result.reason?.contains("fehlt") == true)
    }

    @Test
    fun `does not extend recovery runs`() {
        val recovery = run.copy(trainingType = TrainingType.RECOVERY)
        val history = listOf(workout("recent", date.minusDays(1), 8_000.0, 55))

        val result = limiter.maximumMinutes(recovery, history, date, 90, zone)

        assertEquals(40, result.maximumMinutes)
        assertEquals(true, result.reason?.contains("Erholungslauf") == true)
    }

    private fun workout(id: String, day: LocalDate, meters: Double, minutes: Int): Workout {
        val start = day.atTime(12, 0).toInstant(zone).toEpochMilli()
        return Workout(id, "test", "Lauf", start, start + minutes * 60_000L, meters)
    }
}
