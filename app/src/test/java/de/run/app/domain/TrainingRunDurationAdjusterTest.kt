package de.run.app.domain

import de.run.app.domain.model.PlannedRun
import de.run.app.domain.model.RunStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingRunDurationAdjusterTest {
    private val adjuster = TrainingRunDurationAdjuster()
    private val run = PlannedRun(
        title = "Lockerer Lauf",
        optional = false,
        steps = listOf(
            RunStep("Zügig gehen", 5),
            RunStep("Locker laufen", 30),
            RunStep("Locker gehen", 1),
            RunStep("Locker laufen", 30),
            RunStep("Locker gehen", 5)
        )
    )

    @Test
    fun `fits running portions to the confirmed time and keeps recovery steps`() {
        val adjusted = adjuster.fitToAvailableTime(run, 45)

        assertEquals(45, adjusted?.totalMinutes)
        assertEquals(5, adjusted?.steps?.first()?.minutes)
        assertEquals(5, adjusted?.steps?.last()?.minutes)
        assertTrue(adjusted?.steps?.filter { it.name.contains("gehen") }?.all { it.minutes > 0 } == true)
    }

    @Test
    fun `leaves a shorter session unchanged when more time is available`() {
        val shorter = run.copy(steps = listOf(RunStep("Locker laufen", 30)))

        assertEquals(shorter, adjuster.fitToAvailableTime(shorter, 60))
    }

    @Test
    fun `rejects a time outside supported limits`() {
        assertNull(adjuster.fitToAvailableTime(run, 20))
        assertNull(adjuster.fitToAvailableTime(run, 181))
    }
}
