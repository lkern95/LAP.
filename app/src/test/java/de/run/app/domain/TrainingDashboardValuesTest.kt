package de.run.app.domain

import de.run.app.domain.model.PlannedRun
import de.run.app.domain.model.RunStep
import de.run.app.domain.model.TrainingWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class TrainingDashboardValuesTest {
    @Test
    fun plannedRunDurationIncludesEachDisplayedStep() {
        val run = PlannedRun(
            title = "Lockerer Lauf",
            optional = false,
            steps = listOf(
                RunStep("Gehen", 5),
                RunStep("Laufen", 30),
                RunStep("Gehen", 1),
                RunStep("Laufen", 30),
                RunStep("Gehen", 5)
            )
        )

        assertEquals(71, run.totalMinutes)
    }

    @Test
    fun weeklyRequiredRunCountExcludesOptionalRun() {
        val week = TrainingWeek(
            number = 1,
            startsOn = LocalDate.parse("2026-10-05"),
            longRunMinutes = 60,
            strengthSessions = 1,
            runs = listOf(
                run("Lockerer Lauf", optional = false),
                run("Langer Lauf", optional = false),
                run("Zusatzlauf", optional = true)
            ),
            isRecoveryWeek = false
        )

        assertEquals(2, week.requiredRunCount)
    }

    private fun run(title: String, optional: Boolean) = PlannedRun(
        title = title,
        optional = optional,
        steps = listOf(RunStep("Laufen", 30))
    )
}
