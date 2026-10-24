package de.run.app.domain

import de.run.app.domain.model.PlannedRun
import de.run.app.domain.model.PlanStatus
import de.run.app.domain.model.RunStep
import de.run.app.domain.model.ScheduledRun
import de.run.app.domain.model.TrainingPlan
import de.run.app.domain.model.TrainingWeek
import de.run.app.domain.model.PlannedRunMatchType
import de.run.app.domain.model.WorkoutPlanMatch
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class UpcomingScheduledRunSelectorTest {
    @Test
    fun selectsTheNextDatedRunInsteadOfSkippingByCompletedRunCount() {
        val monday = LocalDate.parse("2026-10-26")
        val adjustedRecovery = PlannedRun("Lockerer Lauf", false, listOf(RunStep("Locker", 25)))
        val longRun = PlannedRun("Langer Lauf", false, listOf(RunStep("Ruhig", 85)))
        val plan = TrainingPlan(
            eventDate = null,
            status = PlanStatus.READY,
            weeks = listOf(
                TrainingWeek(
                    number = 1,
                    startsOn = monday,
                    longRunMinutes = 85,
                    strengthSessions = 0,
                    runs = listOf(adjustedRecovery, longRun),
                    isRecoveryWeek = false,
                    scheduledRuns = listOf(
                        ScheduledRun(DayOfWeek.THURSDAY, adjustedRecovery, monday.plusDays(3)),
                        ScheduledRun(DayOfWeek.SUNDAY, longRun, monday.plusDays(6))
                    )
                )
            )
        )

        val result = UpcomingScheduledRunSelector().select(plan, monday.plusDays(1))

        assertEquals(adjustedRecovery, result?.run)
        assertEquals(monday.plusDays(3), result?.scheduledDate)
    }

    @Test
    fun skipsACompletedRunScheduledForToday() {
        val monday = LocalDate.parse("2026-10-26")
        val firstRun = PlannedRun("Lockerer Lauf", false, listOf(RunStep("Locker", 45)), trainingId = "run-tuesday")
        val nextRun = PlannedRun("Langer Lauf", false, listOf(RunStep("Ruhig", 90)), trainingId = "run-sunday")
        val plan = TrainingPlan(
            eventDate = null,
            status = PlanStatus.READY,
            weeks = listOf(
                TrainingWeek(
                    1, monday, 90, 0, listOf(firstRun, nextRun), false,
                    scheduledRuns = listOf(
                        ScheduledRun(DayOfWeek.TUESDAY, firstRun, monday.plusDays(1)),
                        ScheduledRun(DayOfWeek.SUNDAY, nextRun, monday.plusDays(6))
                    )
                )
            )
        )

        val result = UpcomingScheduledRunSelector().select(
            plan,
            monday.plusDays(1),
            listOf(WorkoutPlanMatch(PlannedRunMatchType.SCHEDULED, trainingId = "run-tuesday", scheduledDate = monday.plusDays(1)))
        )

        assertEquals(nextRun, result?.run)
        assertEquals(monday.plusDays(6), result?.scheduledDate)
    }
}
