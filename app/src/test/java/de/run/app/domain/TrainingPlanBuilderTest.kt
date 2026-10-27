package de.run.app.domain

import de.run.app.domain.model.PlanStatus
import de.run.app.domain.model.AvailabilityMode
import de.run.app.domain.model.AvailabilityStatus
import de.run.app.domain.model.TrainingDayAvailability
import de.run.app.domain.model.TrainingProfile
import de.run.app.domain.model.TrainingGoal
import de.run.app.domain.model.TrainingPlanMode
import de.run.app.domain.model.TrainingGoalStatus
import de.run.app.domain.model.RaceGoal
import de.run.app.domain.model.Workout
import de.run.app.domain.model.WorkoutFeedback
import de.run.app.domain.model.RunningSymptomFeedback
import de.run.app.domain.model.PlannedRunAssessment
import de.run.app.domain.model.PlannedRunMatchType
import de.run.app.domain.model.WorkoutPlanMatch
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingPlanBuilderTest {
    private val builder = TrainingPlanBuilder()
    private val today = LocalDate.of(2026, 10, 5)

    @Test
    fun `optional third run waits for consistent recent training`() {
        val plan = build(profile(optionalRun = true))

        assertEquals(PlanStatus.READY, plan.status)
        assertEquals(2, plan.weeks.first().runs.size)
        assertEquals(1, plan.weeks.first().strengthSessions)
        assertEquals(2, plan.weeks.first().runs.count { !it.optional })
        assertEquals(0, plan.weeks.first().runs.count { it.optional })
    }

    @Test
    fun `race plan initially shows only two conservative weeks without run history`() {
        val plan = build(profile(), goal(today.plusWeeks(23)))

        assertEquals(2, plan.weeks.size)
        assertTrue(plan.weeks.all { it.longRunMinutes <= 45 })
        assertEquals(2, plan.weeks.first().plannedRunCount)
        assertTrue(plan.weeks.first().runs.all { it.distanceSpikeAssessment?.isEstimate != true })
    }

    @Test
    fun `race day is shown as an event and not scheduled as a training run`() {
        val raceDate = today.plusDays(8)
        val plan = build(profile(), goal(raceDate))
        val raceWeek = plan.weeks.first { raceDate in it.startsOn..it.startsOn.plusDays(6) }

        assertEquals(2, raceWeek.plannedRunCount)
        assertTrue(raceWeek.scheduledRuns.none { it.scheduledDate == raceDate })
    }

    @Test
    fun `future preview week cannot compound progression without newly completed runs`() {
        val recentRuns = listOf(1L, 3L, 5L, 7L, 9L, 11L).map { daysAgo ->
            workout(today.minusDays(daysAgo)).copy(
                endedAtMillis = today.minusDays(daysAgo).atStartOfDay().toInstant(ZoneOffset.UTC)
                    .plusSeconds(3_000).toEpochMilli()
            )
        }

        val plan = build(profile(), goal(today.plusWeeks(11)), recentRuns)

        assertEquals(2, plan.weeks.size)
        assertTrue(plan.weeks.first().longRunMinutes <= 60)
        assertTrue(plan.weeks.last().longRunMinutes <= 50)
    }

    @Test
    fun `run through goal starts with a clear thirty minute continuous block`() {
        val plan = build(profile().copy(longestComfortableRunMinutes = 60), goal(today.plusWeeks(23)))
        val longRun = plan.weeks.first().runs.last()
        val easyRun = plan.weeks.first().runs.first()

        assertEquals(30, longRun.steps.first { it.name.contains("am Stück") }.minutes)
        assertEquals(1, longRun.steps.count { it.name == "Locker gehen" && it.minutes == 1 })
        assertEquals(60, longRun.totalMinutes)
        assertEquals(30, easyRun.steps.first { it.name.contains("am Stück") }.minutes)
        assertEquals(45, easyRun.totalMinutes)
    }

    @Test
    fun `one completed easy matched long run advances continuous target by five minutes`() {
        val confirmedLongRun = workout(today.minusDays(5)).copy(
            feedback = WorkoutFeedback(3, RunningSymptomFeedback.NO, today.toEpochDay()),
            planMatch = WorkoutPlanMatch(
                type = PlannedRunMatchType.SCHEDULED,
                plannedTitle = ContinuousRunProgressionAnalyzer.LONG_RUN_TITLE,
                plannedDurationMinutes = 60,
                assessment = PlannedRunAssessment.WELL_MATCHED
            )
        )

        val plan = build(
            profile().copy(longestComfortableRunMinutes = 60),
            goal(today.plusWeeks(23)),
            listOf(confirmedLongRun)
        )

        val easyRun = plan.weeks.first().runs.first()
        val longRun = plan.weeks.first().runs.last()
        assertEquals(35, longRun.steps.first { it.name.contains("am Stück") }.minutes)
        assertEquals(35, easyRun.steps.first { it.name.contains("durchgehend") }.minutes)
    }

    @Test
    fun `continuous target does not progress from a matched run without easy feedback`() {
        val unconfirmedLongRun = workout(today.minusDays(5)).copy(
            planMatch = WorkoutPlanMatch(
                type = PlannedRunMatchType.SCHEDULED,
                plannedTitle = ContinuousRunProgressionAnalyzer.LONG_RUN_TITLE,
                plannedDurationMinutes = 60,
                assessment = PlannedRunAssessment.WELL_MATCHED
            )
        )

        val plan = build(
            profile().copy(longestComfortableRunMinutes = 60),
            goal(today.plusWeeks(23)),
            listOf(unconfirmedLongRun)
        )

        assertEquals(30, plan.weeks.first().runs.last().steps.first { it.name.contains("am Stück") }.minutes)
    }

    @Test
    fun `all seven recurring available days do not create seven training sessions`() {
        val everyDay = profile().copy(availableRunDays = java.time.DayOfWeek.entries.toSet())

        val week = build(everyDay).weeks.first()

        assertEquals(2, week.scheduledRuns.size)
        assertTrue(week.scheduledRuns.zipWithNext().all { (first, second) ->
            val firstDate = week.startsOn.plusDays((first.day.value - 1).toLong())
            val secondDate = week.startsOn.plusDays((second.day.value - 1).toLong())
            java.time.temporal.ChronoUnit.DAYS.between(firstDate, secondDate) >= 2
        })
    }

    @Test
    fun `unknown variable availability creates no dated workouts`() {
        val variable = profile().copy(availabilityMode = AvailabilityMode.VARIABLE, datedAvailability = emptyMap())

        val week = build(variable).weeks.first()

        assertTrue(week.scheduledRuns.isEmpty())
        assertTrue(week.availability.values.all { it == AvailabilityStatus.UNKNOWN })
        assertEquals(2, week.runs.size)
    }

    @Test
    fun `flexible availability uses time limits without assigning calendar days`() {
        val flexible = profile().copy(
            availabilityMode = AvailabilityMode.VARIABLE,
            usualRunTimeLimitMinutes = 45,
            occasionalRunTimeLimitMinutes = 90
        )

        val week = build(flexible).weeks.first()

        assertTrue(week.scheduledRuns.isEmpty())
        assertTrue(week.availability.values.all { it == AvailabilityStatus.UNKNOWN })
        assertTrue(week.runs.first().totalMinutes <= 45)
        assertTrue(week.runs.last().totalMinutes <= 90)
    }

    @Test
    fun `flexible mode does not turn old dated entries into calendar appointments`() {
        val easyDate = today.plusDays(1)
        val longDate = today.plusDays(4)
        val variable = profile().copy(
            availabilityMode = AvailabilityMode.VARIABLE,
            datedAvailability = mapOf(
                easyDate to TrainingDayAvailability(AvailabilityStatus.AVAILABLE, 45),
                today.plusDays(2) to TrainingDayAvailability(AvailabilityStatus.UNAVAILABLE),
                longDate to TrainingDayAvailability(AvailabilityStatus.AVAILABLE, 90)
            )
        )

        val week = build(variable).weeks.first()

        assertTrue(week.scheduledRuns.isEmpty())
        assertTrue(week.availability.values.all { it == AvailabilityStatus.UNKNOWN })
        assertEquals(2, week.runs.size)
    }

    @Test
    fun `old night shift dates are not used as today's flexible availability`() {
        val afterShift = today.plusDays(2)
        val available = today.plusDays(5)
        val variable = profile(optionalRun = false).copy(
            availabilityMode = AvailabilityMode.VARIABLE,
            considerNightShiftRecovery = true,
            datedAvailability = mapOf(
                afterShift to TrainingDayAvailability(AvailabilityStatus.AVAILABLE, 45, afterNightShift = true),
                available to TrainingDayAvailability(AvailabilityStatus.AVAILABLE, 90)
            )
        )

        val week = build(variable).weeks.first()

        assertTrue(week.scheduledRuns.isEmpty())
        assertEquals(2, week.runs.size)
    }

    @Test
    fun `every fourth week has reduced long run`() {
        val goal = goal(today.plusWeeks(23))
        val firstPreview = build(profile(), goal)
        val fourthWeekPreview = builder.build(profile(), goal, today.plusWeeks(3), emptyList())

        assertTrue(fourthWeekPreview.weeks.first().isRecoveryWeek)
        assertTrue(fourthWeekPreview.weeks.first().longRunMinutes < firstPreview.weeks.last().longRunMinutes)
    }

    @Test
    fun `event less than sixteen weeks away is flagged`() {
        val nearRace = profile()

        assertEquals(PlanStatus.EVENT_TOO_CLOSE, build(nearRace, goal(today.plusWeeks(8))).status)
    }

    @Test
    fun `one run in previous week repeats the prior training load`() {
        val raceDate = today.plusWeeks(15)
        val baseline = build(profile(), goal(raceDate))
        val adjusted = build(
            profile(),
            goal(raceDate),
            listOf(workout(today.minusDays(3)).copy(
                endedAtMillis = today.minusDays(3).atStartOfDay().toInstant(ZoneOffset.UTC)
                    .plusSeconds(1_800).toEpochMilli()
            ))
        )

        assertEquals(PlanStatus.READY, adjusted.status)
        assertTrue(adjusted.weeks.first().longRunMinutes < baseline.weeks.first().longRunMinutes)
    }

    @Test
    fun `long run starts from measured recent running duration`() {
        val shortRecentRun = workout(today.minusDays(2)).copy(
            endedAtMillis = today.minusDays(2).atStartOfDay().toInstant(ZoneOffset.UTC)
                .plusSeconds(1_500).toEpochMilli()
        )

        val plan = build(profile(), goal(today.plusWeeks(20)), listOf(shortRecentRun))

        assertEquals(25, plan.weeks.first().longRunMinutes)
        assertEquals(plan.weeks.first().longRunMinutes, plan.weeks.first().runs.last().totalMinutes)
    }

    @Test
    fun `consistent recent training allows only a small individualized long run step`() {
        val recentRuns = listOf(1L, 3L, 5L, 7L, 9L, 11L).map { daysAgo ->
            workout(today.minusDays(daysAgo)).copy(
                endedAtMillis = today.minusDays(daysAgo).atStartOfDay().toInstant(ZoneOffset.UTC)
                    .plusSeconds(3_000).toEpochMilli()
            )
        }

        val plan = build(profile(), goal(today.plusWeeks(11)), recentRuns)

        assertEquals(60, plan.weeks.first().longRunMinutes)
        assertEquals(de.run.app.domain.model.PlanAdjustment.CONTINUE, plan.adjustment)
    }

    @Test
    fun `two runs in previous week allow planned progression`() {
        val runs = listOf(workout(today.minusDays(3)), workout(today.minusDays(5)))
        val plan = build(profile(), goal(today.plusWeeks(15)), runs)

        assertEquals(de.run.app.domain.model.PlanAdjustment.CONTINUE, plan.adjustment)
    }

    @Test
    fun `same source identity in workout input is only one training session`() {
        val imported = workout(today.minusDays(3))
        val plan = build(profile(), goal(today.plusWeeks(20)), listOf(imported, imported.copy()))

        assertEquals(de.run.app.domain.model.PlanAdjustment.REPEAT_WEEK, plan.adjustment)
        assertEquals(1, TrainingProgressAnalyzer().analyze(listOf(imported, imported.copy()), today).runsPreviousWeek)
    }

    @Test
    fun `repeated high effort keeps plan at a prior training phase`() {
        val runs = listOf(3L, 6L).map { daysAgo ->
            workout(today.minusDays(daysAgo)).copy(
                feedback = WorkoutFeedback(8, RunningSymptomFeedback.NO, today.toEpochDay())
            )
        }

        val first = build(profile(), goal(today.plusWeeks(20)), runs)
        val repeated = build(profile(), goal(today.plusWeeks(20)), runs)

        assertEquals(de.run.app.domain.model.PlanAdjustment.REPEAT_AFTER_HIGH_EFFORT, first.adjustment)
        assertEquals(first, repeated)
    }

    @Test
    fun `reported symptoms suppress optional run and a later no answer clears the flag`() {
        val flagged = workout(today.minusDays(2)).copy(
            feedback = WorkoutFeedback(null, RunningSymptomFeedback.UNSURE, today.toEpochDay())
        )
        val beforeClear = build(profile(optionalRun = true), workouts = listOf(flagged))
        val cleared = flagged.copy(
            sourceId = "garmin:later",
            startedAtMillis = today.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            endedAtMillis = today.atStartOfDay().toInstant(ZoneOffset.UTC).plusSeconds(1_800).toEpochMilli(),
            feedback = WorkoutFeedback(null, RunningSymptomFeedback.NO, today.toEpochDay() + 1)
        )

        assertEquals(de.run.app.domain.model.PlanAdjustment.REVIEW_REPORTED_SYMPTOMS, beforeClear.adjustment)
        assertTrue(beforeClear.weeks.first().runs.none { it.optional })
        assertEquals(de.run.app.domain.model.PlanAdjustment.REPEAT_WEEK, build(profile(), workouts = listOf(flagged, cleared)).adjustment)
    }

    @Test
    fun `optional third run is suggested after recent consistent running`() {
        val recentRuns = listOf(1L, 3L, 5L, 8L, 10L, 12L).map { daysAgo -> workout(today.minusDays(daysAgo)) }

        val plan = build(profile(optionalRun = true), workouts = recentRuns)

        assertEquals(3, plan.weeks.first().runs.size)
        assertEquals(1, plan.weeks.first().runs.count { it.optional })
        assertEquals(3, plan.weeks.first().plannedRunCount)
    }

    @Test
    fun `run history after two week break resumes gently`() {
        val plan = build(profile(), workouts = listOf(workout(today.minusDays(15))))

        assertEquals(de.run.app.domain.model.PlanAdjustment.RETURN_GENTLY, plan.adjustment)
        assertEquals(2, plan.weeks.first().plannedRunCount)
        assertTrue(plan.weeks.first().runs.none { it.optional })
    }

    @Test
    fun `maintenance mode produces a repeatable race-free plan`() {
        val plan = build(profile().copy(planMode = TrainingPlanMode.MAINTAIN_FORM))

        assertEquals(PlanStatus.MAINTAINING_FORM, plan.status)
        assertEquals(null, plan.eventDate)
        assertEquals(4, plan.weeks.size)
        assertTrue(plan.weeks.last().isRecoveryWeek)
    }

    @Test
    fun `completed goal remains complete when its date has passed`() {
        val plan = build(profile(), goal(today.minusDays(1)).copy(status = TrainingGoalStatus.COMPLETED))

        assertEquals(PlanStatus.GOAL_COMPLETED, plan.status)
    }

    private fun build(
        profile: TrainingProfile,
        goal: TrainingGoal = goal(today.plusWeeks(23)),
        workouts: List<Workout> = emptyList()
    ) = builder.build(profile, goal, today, workouts)

    private fun profile(optionalRun: Boolean = false) = TrainingProfile(
        longestComfortableRunMinutes = 45,
        optionalRun = optionalRun,
        strengthSessionsPerWeek = 1
    )

    private fun goal(raceDate: LocalDate) = TrainingGoal(
        id = "goal-${raceDate}",
        raceDate = raceDate,
        raceDistanceMeters = 21_100,
        raceGoal = RaceGoal.RUN_THROUGH
    )

    private fun workout(date: LocalDate) = Workout(
        sourceId = "garmin:$date",
        sourceName = "Garmin Connect",
        title = "Lauf",
        startedAtMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        endedAtMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).plusSeconds(3_600).toEpochMilli(),
        distanceMeters = 5_000.0
    )
}
