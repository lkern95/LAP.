package de.run.app.domain

import de.run.app.domain.model.PlannedRun
import de.run.app.domain.model.RunStep
import de.run.app.domain.model.ScheduledRun
import de.run.app.domain.model.TrainingPlan
import de.run.app.domain.model.TrainingProfile
import de.run.app.domain.model.TrainingGoal
import de.run.app.domain.model.RaceGoal
import de.run.app.domain.model.TrainingWeek
import de.run.app.domain.model.PlanStatus
import de.run.app.domain.model.Workout
import de.run.app.domain.model.WorkoutPlanMatch
import de.run.app.domain.model.PlannedRunMatchType
import de.run.app.domain.model.AvailabilityMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingFeedbackEngineTest {
    private val today = LocalDate.of(2026, 10, 19) // Monday
    private val run = PlannedRun("Lockerer Dauerlauf", false, listOf(RunStep("Locker", 40)))
    private val engine = TrainingFeedbackEngine()

    @Test
    fun `run matching plan duration is positive and describes next step`() {
        val workout = workout(today, 40 * 60L)
        val feedback = engine.analyze(workout, listOf(workout), null, plan(), today, ZoneOffset.UTC)

        assertEquals(WorkoutPlanStatus.WELL_MATCHED, feedback.status)
        assertTrue(feedback.shortSummary.contains("Dauer passt"))
        assertTrue(feedback.shortSummary.contains("deine Anstrengung ist noch offen"))
        assertTrue(feedback.nextAction.contains("Langer Lauf"))
        assertEquals(run, feedback.plannedRun)
    }

    @Test
    fun `pace deviation names pace instead of claiming a duration deviation`() {
        val match = WorkoutPlanMatch(
            type = PlannedRunMatchType.SCHEDULED,
            scheduledDate = today,
            plannedDurationMinutes = 40,
            paceFasterSecondsPerKm = 360,
            paceSlowerSecondsPerKm = 390
        )
        val workout = workout(today, 40 * 60L).copy(planMatch = match)

        val feedback = engine.analyze(workout, listOf(workout), null, plan(), today, ZoneOffset.UTC)

        assertEquals(WorkoutPlanStatus.SOME_DEVIATION, feedback.status)
        assertEquals("Das Tempo lag außerhalb deiner persönlichen Vorgabe.", feedback.shortSummary)
    }

    @Test
    fun `extra run after all planned runs recommends recovery once`() {
        val extraDate = today.plusDays(3)
        val plannedRuns = listOf(today, today.plusDays(2)).mapIndexed { index, date ->
            workout(date, 40 * 60L).copy(
                sourceId = "planned-$index",
                planMatch = WorkoutPlanMatch(
                    type = PlannedRunMatchType.SCHEDULED,
                    trainingId = "planned-$index",
                    scheduledDate = date,
                    plannedDurationMinutes = 40
                )
            )
        }
        val extra = workout(extraDate, 25 * 60L).copy(
            sourceId = "extra",
            planMatch = WorkoutPlanMatch(type = PlannedRunMatchType.EXTRA, scheduledDate = extraDate)
        )
        val twoRunPlan = plan().copy(weeks = listOf(plan().weeks.first().copy(
            runs = listOf(run, run.copy(title = "Langer Lauf")),
            scheduledRuns = listOf(
                ScheduledRun(DayOfWeek.MONDAY, run, today),
                ScheduledRun(DayOfWeek.WEDNESDAY, run.copy(title = "Langer Lauf"), today.plusDays(2))
            )
        )))

        val feedback = engine.analyze(extra, plannedRuns + extra, null, twoRunPlan, extraDate, ZoneOffset.UTC,
            TrainingProfile(60, false, 0, availableRunDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)))

        assertEquals(WorkoutPlanStatus.EXTRA_RUN, feedback.status)
        assertTrue(feedback.nextAction.contains("Erholung"))
        assertFalse(feedback.nextAction.contains("Langer Lauf"))
    }

    @Test
    fun `rest day activity remains an extra run and does not complete planned run`() {
        val restDay = today.plusDays(1)
        val workout = workout(restDay, 30 * 60L)
        val profile = TrainingProfile(60, false, 0, availabilityMode = AvailabilityMode.VARIABLE)
        val feedback = engine.analyze(workout, listOf(workout), null, plan(), today, ZoneOffset.UTC, profile)

        assertEquals(WorkoutPlanStatus.EXTRA_RUN, feedback.status)
        assertTrue(feedback.shortSummary.contains("30 Min. zusätzlich gelaufen"))
        assertTrue(feedback.shortSummary.contains("Anstrengung und Beschwerden sind noch offen"))
        assertEquals(0, feedback.weeklyPlanProgress?.completedRuns)
        assertEquals(1, feedback.weeklyPlanProgress?.extraRuns)
    }

    @Test
    fun `longer than planned run names minute difference and changed next workout`() {
        val highLoad = PlannedRun("Erholungslauf", false, listOf(RunStep("Sehr locker laufen", 15)))
        val planned = WorkoutPlanMatch(
            type = PlannedRunMatchType.SCHEDULED,
            scheduledDate = today,
            plannedDurationMinutes = 45,
            assessment = de.run.app.domain.model.PlannedRunAssessment.HIGHER_LOAD
        )
        val workout = workout(today, 100 * 60L).copy(planMatch = planned)
        val updatedPlan = TrainingPlan(
            null,
            PlanStatus.READY,
            listOf(TrainingWeek(1, today, 60, 0, listOf(run, highLoad), false,
                scheduledRuns = listOf(ScheduledRun(DayOfWeek.THURSDAY, highLoad, today.plusDays(3))))) ,
            de.run.app.domain.model.PlanAdjustment.RECOVER_AFTER_HIGH_LOAD
        )

        val feedback = engine.analyze(workout, listOf(workout), null, updatedPlan, today, ZoneOffset.UTC)

        assertTrue(feedback.shortSummary.contains("55 Min."))
        assertEquals("Dein Lauf dauerte 55 Min. länger als geplant. Darum wurde die nächste Einheit „Erholungslauf“ auf 15 Min. verkürzt.", feedback.planChangeSummary)
        assertTrue(feedback.nextAction.contains("Erholungslauf für 15 Minuten"))
    }

    @Test
    fun `run through goal reports consistency and endurance without race time`() {
        val goal = TrainingGoal("goal", today.plusWeeks(16), 21_097, RaceGoal.RUN_THROUGH)
        val recentRuns = (1..6).map { daysAgo ->
            workout(today.minusDays(daysAgo.toLong()), 40 * 60L).copy(
                sourceId = "run-through-$daysAgo",
                distanceMeters = if (daysAgo == 1) 12_000.0 else 6_000.0
            )
        }

        val feedback = engine.analyze(recentRuns.first(), recentRuns, goal, plan(), today, ZoneOffset.UTC)

        assertTrue(feedback.goalSummary.contains("6 Läufe in vier Wochen"))
        assertTrue(feedback.goalSummary.contains("längster Lauf 12,0 km"))
        assertFalse(feedback.goalSummary.contains("Std."))
    }

    @Test
    fun `weekly plan count excludes extras and shows only actually planned runs`() {
        val profile = TrainingProfile(60, false, 0, availableRunDays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))
        val scheduled = workout(today, 40 * 60L).copy(
            planMatch = WorkoutPlanMatch(
                type = PlannedRunMatchType.SCHEDULED,
                trainingId = "monday",
                scheduledDate = today,
                plannedTitle = run.title,
                plannedDurationMinutes = run.totalMinutes
            )
        )
        val extraDate = today.plusDays(1)
        val extra = workout(extraDate, 30 * 60L).copy(
            planMatch = WorkoutPlanMatch(type = PlannedRunMatchType.EXTRA, scheduledDate = extraDate)
        )
        val plan = plan().copy(weeks = listOf(plan().weeks.first().copy(runs = listOf(run, run.copy(title = "Langer Lauf")))))

        val feedback = engine.analyze(scheduled, listOf(scheduled, extra), null, plan, today, ZoneOffset.UTC, profile)

        assertEquals(1, feedback.weeklyPlanProgress?.completedRuns)
        assertEquals(2, feedback.weeklyPlanProgress?.plannedRuns)
        assertEquals(1, feedback.weeklyPlanProgress?.extraRuns)
        assertTrue(feedback.weeklyComparison != null)
    }

    @Test
    fun `variable availability feedback names an unfinished unit and never says the week is done`() {
        val profile = TrainingProfile(60, false, 0, availabilityMode = AvailabilityMode.VARIABLE)
        val longRun = PlannedRun("Langer Lauf", false, listOf(RunStep("Dauerlauf", 60)))
        val variablePlan = TrainingPlan(
            null,
            PlanStatus.READY,
            listOf(TrainingWeek(1, today, 60, 0, listOf(run, longRun), false))
        )
        val extras = listOf(today, today.plusDays(1), today.plusDays(2)).mapIndexed { index, date ->
            workout(date, 30 * 60L).copy(
                sourceId = "extra-$index",
                planMatch = WorkoutPlanMatch(type = PlannedRunMatchType.EXTRA, scheduledDate = date)
            )
        }

        val feedback = engine.analyze(extras.last(), extras, null, variablePlan, today.plusDays(2), ZoneOffset.UTC, profile)

        assertEquals(0, feedback.weeklyPlanProgress?.completedRuns)
        assertEquals(2, feedback.weeklyPlanProgress?.plannedRuns)
        assertEquals(3, feedback.weeklyPlanProgress?.extraRuns)
        assertTrue(feedback.nextAction.contains("Lockerer Dauerlauf"))
        assertFalse(feedback.nextAction.contains("Laufeinheiten dieser Woche sind erledigt"))
    }

    @Test
    fun `variable availability feedback advances to the second unit after one planned run`() {
        val profile = TrainingProfile(60, false, 0, availabilityMode = AvailabilityMode.VARIABLE)
        val longRun = PlannedRun("Langer Lauf", false, listOf(RunStep("Dauerlauf", 60)))
        val variablePlan = TrainingPlan(
            null,
            PlanStatus.READY,
            listOf(TrainingWeek(1, today, 60, 0, listOf(run, longRun), false))
        )
        val plannedDate = today.plusDays(1)
        val planned = workout(plannedDate, 40 * 60L).copy(
            sourceId = "scheduled",
            planMatch = WorkoutPlanMatch(
                type = PlannedRunMatchType.SCHEDULED,
                trainingId = "easy-run",
                scheduledDate = plannedDate,
                plannedTitle = run.title,
                plannedDurationMinutes = run.totalMinutes
            )
        )
        val extraDate = today.plusDays(2)
        val extra = workout(extraDate, 30 * 60L).copy(
            sourceId = "extra",
            planMatch = WorkoutPlanMatch(type = PlannedRunMatchType.EXTRA, scheduledDate = extraDate)
        )

        val feedback = engine.analyze(extra, listOf(planned, extra), null, variablePlan, extraDate, ZoneOffset.UTC, profile)

        assertEquals(1, feedback.weeklyPlanProgress?.completedRuns)
        assertEquals(1, feedback.weeklyPlanProgress?.extraRuns)
        assertTrue(feedback.nextAction.contains("Langer Lauf"))
    }

    private fun plan() = TrainingPlan(
        eventDate = null,
        status = PlanStatus.READY,
        weeks = listOf(
            TrainingWeek(
                number = 1,
                startsOn = today,
                longRunMinutes = 60,
                strengthSessions = 0,
                runs = listOf(run),
                isRecoveryWeek = false,
                scheduledRuns = listOf(
                    ScheduledRun(DayOfWeek.MONDAY, run),
                    ScheduledRun(DayOfWeek.THURSDAY, PlannedRun("Langer Lauf", false, listOf(RunStep("Dauerlauf", 60))))
                )
            )
        )
    )

    private fun workout(date: LocalDate, durationSeconds: Long) = Workout(
        sourceId = date.toString(),
        sourceName = "Garmin Connect",
        title = "Lauf",
        startedAtMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        endedAtMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() + durationSeconds * 1_000,
        distanceMeters = 5_000.0
    )
}
