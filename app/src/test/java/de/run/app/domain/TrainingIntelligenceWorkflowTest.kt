package de.run.app.domain

import de.run.app.domain.model.AvailabilityMode
import de.run.app.domain.model.PlannedRunAssessment
import de.run.app.domain.model.PlannedRunMatchType
import de.run.app.domain.model.RaceGoal
import de.run.app.domain.model.RunningSymptomFeedback
import de.run.app.domain.model.TrainingGoal
import de.run.app.domain.model.TrainingProfile
import de.run.app.domain.model.TrainingType
import de.run.app.domain.model.Workout
import de.run.app.domain.model.WorkoutFeedback
import de.run.app.domain.model.WorkoutPlanMatch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingIntelligenceWorkflowTest {
    private val today = LocalDate.of(2026, 10, 19)
    private val profile = TrainingProfile(
        longestComfortableRunMinutes = 35,
        optionalRun = true,
        strengthSessionsPerWeek = 1,
        availableRunDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SUNDAY),
        longRunDay = DayOfWeek.SUNDAY,
        runTimeLimitsByDay = DayOfWeek.entries.associateWith { 180 },
        availabilityMode = AvailabilityMode.RECURRING
    )
    private val goal = TrainingGoal("hm", today.plusWeeks(22), 21_100, RaceGoal.TARGET_TIME, 150)
    private val planBuilder = TrainingPlanBuilder()
    private val matcher = PlannedWorkoutMatcher(planBuilder)
    private val assessment = WorkoutPlanAssessmentAnalyzer()

    @Test
    fun `01 planned easy run has concrete duration and is assessed as matched`() {
        val plan = planBuilder.build(profile, goal, today)
        val planned = plan.weeks.first().scheduledRuns.first().run
        val date = planned.scheduledDate!!
        val workout = workout(date, planned.totalMinutes * 60L, 10_000.0)
        val match = planned.toMatch(date)

        assertEquals(TrainingType.EASY, planned.trainingType)
        assertEquals(planned.totalMinutes.toLong(), workout.durationSeconds / 60)
        assertTrue(planned.steps.any { it.effortCue?.contains("Sätze") == true })
        val assessed = match.copy(assessment = assessment.assess(workout, match))
        assertEquals(PlannedRunAssessment.WELL_MATCHED, assessed.assessment)

        val nextRun = planBuilder.build(profile, goal, date.plusDays(1), listOf(workout.copy(planMatch = assessed)))
            .weeks.first().scheduledRuns.first()
        assertEquals("Lockerer Lauf", nextRun.run.title)
        assertEquals(date.plusDays(2), nextRun.scheduledDate)
    }

    @Test
    fun `02 faster than the observed easy pace range is a deviation`() {
        val plannedMinutes = planBuilder.build(profile, goal, today).weeks.first().scheduledRuns
            .first { it.run.trainingType == TrainingType.EASY }.run.totalMinutes
        val history = easyHistory(plannedMinutes)
        val plan = planBuilder.build(profile, goal, today, history)
        val planned = plan.weeks.first().scheduledRuns.first { it.run.trainingType == TrainingType.EASY }.run
        val date = planned.scheduledDate!!
        val match = planned.toMatch(date)
        val pace = match.paceFasterSecondsPerKm!! - 30
        val durationSeconds = planned.totalMinutes * 60L
        val distanceMeters = durationSeconds * 1_000.0 / pace
        val workout = workout(date, durationSeconds, distanceMeters)

        assertNotNull(match.paceFasterSecondsPerKm)
        assertEquals(PlannedRunAssessment.SOME_DEVIATION, assessment.assess(workout, match))
    }

    @Test
    fun `03 excessive duration changes the next plan to a recovery run`() {
        val baseline = planBuilder.build(profile, goal, today)
        val planned = baseline.weeks.first().scheduledRuns.first().run
        val match = planned.toMatch(today.minusDays(1))
        val actual = workout(today.minusDays(1), planned.totalMinutes * 60L * 140 / 100, 8_000.0)
        val assessed = match.copy(assessment = assessment.assess(actual, match))
        assertEquals(PlannedRunAssessment.HIGHER_LOAD, assessed.assessment)

        val adjusted = planBuilder.build(profile, goal, today, listOf(actual.copy(planMatch = assessed)))

        assertEquals(de.run.app.domain.model.PlanAdjustment.RECOVER_AFTER_HIGH_LOAD, adjusted.adjustment)
        assertTrue(adjusted.weeks.first().runs.any { it.title == "Erholungslauf" })
        assertFalse(adjusted.weeks.first().runs.any { it.optional })
    }

    @Test
    fun `04 a missed earlier date is not backfilled into a new dated session`() {
        val afterMissedDate = today.plusDays(2)
        val plan = planBuilder.build(profile, goal, afterMissedDate)

        assertTrue(plan.weeks.first().scheduledRuns.all { it.scheduledDate!! >= afterMissedDate })
        assertFalse(plan.weeks.first().scheduledRuns.any { it.scheduledDate == today.plusDays(1) })
    }

    @Test
    fun `05 a rest day extra run does not complete a planned session`() {
        val extra = workout(today, 30 * 60L, 3_000.0)
        val match = matcher.match(extra, emptyList(), profile, goal, ZoneOffset.UTC)
        val week = planBuilder.build(profile, goal, today).weeks.first()
        val completed = WorkoutPlanCompletionAnalyzer(matcher)
            .completedRuns(listOf(extra.copy(planMatch = match)), week, profile, goal, ZoneOffset.UTC)

        assertEquals(PlannedRunMatchType.EXTRA, match.type)
        assertEquals(0, completed)
        val nextPlan = planBuilder.build(profile, goal, today, listOf(extra.copy(planMatch = match)))
        assertEquals("Lockerer Lauf", nextPlan.weeks.first().scheduledRuns.first().run.title)
        assertTrue(nextPlan.weeks.first().scheduledRuns.first().scheduledDate!! >= today.plusDays(2))
    }

    @Test
    fun `06 consistent positive training history safely increases the long run`() {
        val history = (1L..6L).map { daysAgo ->
            workout(today.minusDays(daysAgo), 40 * 60L, 5_000.0, pace = 480)
                .copy(feedback = WorkoutFeedback(3, RunningSymptomFeedback.NO, today.toEpochDay()))
        }
        val baseline = planBuilder.build(profile.copy(optionalRun = false), goal, today)
        val progressed = planBuilder.build(profile.copy(optionalRun = false), goal, today, history)
        val baselineLong = baseline.weeks.first().runs.first { it.trainingType == TrainingType.LONG_EASY }.totalMinutes
        val progressedLong = progressed.weeks.first().runs.first { it.trainingType == TrainingType.LONG_EASY }.totalMinutes

        assertTrue(progressedLong > baselineLong)
    }

    @Test
    fun `07 slower comparable completed weeks hold progression and shorten the next unit`() {
        val history = buildList {
            listOf(today.minusDays(13), today.minusDays(11)).forEachIndexed { index, date ->
                add(workout(date, 40 * 60L, 5_000.0, pace = 360).withEasyEffort(index))
            }
            listOf(today.minusDays(6), today.minusDays(4)).forEachIndexed { index, date ->
                add(workout(date, 40 * 60L, 5_000.0, pace = 400).withEasyEffort(index + 2))
            }
        }
        val response = TrainingResponseAnalyzer().analyze(history, today, ZoneOffset.UTC)
        val plan = planBuilder.build(profile, goal, today, history)

        assertEquals(TrainingResponse.HOLD_AFTER_SLOWING, response)
        assertEquals(de.run.app.domain.model.PlanAdjustment.HOLD_AFTER_SLOWING, plan.adjustment)
        assertEquals("Erholungslauf", plan.weeks.first().runs.first().title)
        assertFalse(plan.weeks.first().runs.any { it.optional })
    }

    @Test
    fun `08 missing pace and heart rate remain unavailable without false warnings`() {
        val planned = planBuilder.build(profile, goal, today).weeks.first().scheduledRuns.first().run
        val date = planned.scheduledDate!!
        val workout = workout(date, planned.totalMinutes * 60L, null)
        val match = planned.toMatch(date)
        val feedback = TrainingFeedbackEngine().analyze(
            workout.copy(planMatch = match.copy(assessment = assessment.assess(workout, match))),
            listOf(workout), goal, null, today, ZoneOffset.UTC
        )

        assertNull(planned.prescription.paceRange)
        assertNull(planned.prescription.heartRateRangeBpm)
        assertEquals(WorkoutPlanStatus.WELL_MATCHED, feedback.status)
        assertTrue(feedback.reasons.any { it.contains("Keine Pace erfasst") })
        assertTrue(feedback.reasons.any { it.contains("Kein Herzfrequenzwert") })
    }

    @Test
    fun `09 run through goal uses endurance assessment without a target time`() {
        val noTarget = goal.copy(raceGoal = RaceGoal.RUN_THROUGH, targetTimeMinutes = null)
        val workout = workout(today, 40 * 60L, 5_000.0)
        val feedback = TrainingFeedbackEngine().analyze(workout, listOf(workout), noTarget, null, today, ZoneOffset.UTC)

        assertTrue(feedback.goalSummary.contains("in vier Wochen"))
        assertFalse(feedback.goalSummary.contains("Ohne Zielzeit"))
    }

    @Test
    fun `10 live coach uses the planned personal pace and leaves unsupported heart rate unset`() {
        val plannedMinutes = planBuilder.build(profile, goal, today).weeks.first().runs.first().totalMinutes
        val plan = planBuilder.build(profile, goal, today, easyHistory(plannedMinutes))
        val planned = plan.weeks.first().runs.first { it.trainingType == TrainingType.EASY }
        val targets = LiveRunTargets.from(planned)
        val pace = planned.prescription.paceRange

        assertNotNull(pace)
        assertTrue(targets.sections.any {
            it.target.minPaceSecondsPerKilometer == pace?.fasterSecondsPerKm &&
                it.target.maxPaceSecondsPerKilometer == pace?.slowerSecondsPerKm
        })
        assertTrue(targets.sections.all { it.target.maxHeartRateBpm == null })
    }

    private fun easyHistory(plannedMinutes: Int) = listOf(8L, 10L, 12L, 14L, 16L).mapIndexed { index, daysAgo ->
        val pace = 365 + index * 5
        val duration = plannedMinutes * 60L
        val distance = duration * 1_000.0 / pace
        workout(today.minusDays(daysAgo), duration, distance)
            .copy(feedback = WorkoutFeedback(3, RunningSymptomFeedback.NO, today.toEpochDay()))
    }

    private fun workout(date: LocalDate, durationSeconds: Long, distance: Double?, pace: Int? = null): Workout {
        val started = date.atTime(9, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val actualDuration = pace?.let { it * 5L } ?: durationSeconds
        return Workout(
            sourceId = "synthetic:$date:$started",
            sourceName = "Testdaten",
            title = "Lauf",
            startedAtMillis = started,
            endedAtMillis = started + actualDuration * 1_000,
            distanceMeters = distance
        )
    }

    private fun Workout.withEasyEffort(index: Int) = copy(
        sourceId = "week-run-$index",
        feedback = WorkoutFeedback(3, RunningSymptomFeedback.NO, endedAtMillis)
    )

    private fun de.run.app.domain.model.PlannedRun.toMatch(date: LocalDate) = WorkoutPlanMatch(
        type = PlannedRunMatchType.SCHEDULED,
        trainingId = trainingId ?: "lap-run-$date-${trainingType.name.lowercase()}",
        scheduledDate = date,
        plannedTitle = title,
        plannedDurationMinutes = totalMinutes,
        paceSlowerSecondsPerKm = prescription.paceRange?.slowerSecondsPerKm,
        paceFasterSecondsPerKm = prescription.paceRange?.fasterSecondsPerKm,
        effortMinimum = prescription.effortMinimum,
        effortMaximum = prescription.effortMaximum
    )
}
