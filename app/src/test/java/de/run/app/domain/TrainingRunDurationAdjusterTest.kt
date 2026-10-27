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
    fun `extends running duration when more time is available`() {
        val shorter = run.copy(steps = listOf(RunStep("Locker laufen", 30)))

        val adjusted = adjuster.fitToAvailableTime(shorter, 60)

        assertEquals(60, adjusted?.totalMinutes)
        assertEquals(60, adjusted?.steps?.single()?.minutes)
    }

    @Test
    fun `updates continuous target label when available time shortens its run segment`() {
        val targetedRun = run.copy(steps = listOf(
            RunStep("Zügig gehen", 5),
            RunStep("Locker laufen; Ziel: 30 Min. am Stück", 30),
            RunStep("Locker gehen", 1),
            RunStep("Locker gehen", 5)
        ))

        val adjusted = adjuster.fitToAvailableTime(targetedRun, 30)

        assertEquals(19, adjusted?.steps?.get(1)?.minutes)
        assertEquals("Locker laufen; Ziel: 19 Min. am Stück", adjusted?.steps?.get(1)?.name)
    }

    @Test
    fun `supports the available time minimum of twenty minutes`() {
        assertEquals(20, adjuster.fitToAvailableTime(run, 20)?.totalMinutes)
    }

    @Test
    fun `rejects a time above the supported training limit`() {
        assertNull(adjuster.fitToAvailableTime(run, 181))
    }
}
