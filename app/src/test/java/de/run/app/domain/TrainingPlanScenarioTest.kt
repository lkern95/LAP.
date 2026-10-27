package de.run.app.domain

import de.run.app.domain.model.PlannedRunAssessment
import de.run.app.domain.model.PlannedRunMatchType
import de.run.app.domain.model.PlanAdjustment
import de.run.app.domain.model.RaceGoal
import de.run.app.domain.model.RunningSymptomFeedback
import de.run.app.domain.model.TrainingGoal
import de.run.app.domain.model.TrainingHistoryAccess
import de.run.app.domain.model.TrainingHistoryCoverage
import de.run.app.domain.model.TrainingProfile
import de.run.app.domain.model.Workout
import de.run.app.domain.model.WorkoutFeedback
import de.run.app.domain.model.WorkoutPlanMatch
import de.run.app.domain.model.WorkoutReadResult
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingPlanScenarioTest {
    private val today = LocalDate.of(2026, 10, 26)
    private val builder = TrainingPlanBuilder()

    @Test
    fun `missing history access shows only provisional weeks without claiming inactivity`() {
        val plan = createPlan(today, emptyList())
        val coverage = TrainingHistoryCoverage.from(WorkoutReadResult.PermissionNeeded, emptyList())
        val evidence = TrainingPlanEvidenceAnalyzer().analyze(coverage, plan)
        val visiblePlan = TrainingPlanEvidenceAnalyzer().visiblePlan(plan, evidence)

        assertEquals(TrainingPlanEvidenceStatus.ACCESS_REQUIRED, evidence.status)
        assertEquals(TrainingHistoryAccess.PERMISSION_NEEDED, coverage.access)
        assertEquals(2, visiblePlan?.weeks?.size)
        assertTrue(evidence.status != TrainingPlanEvidenceStatus.NO_RUNS_FOUND)
    }

    @Test
    fun `confirmed easy long run advances continuous duration by one controlled step`() {
        val plan = createPlan(today, listOf(confirmedLongRun(today.minusDays(1))))

        assertEquals(35, continuousTarget(plan))
    }

    @Test
    fun `symptoms or high effort prevent continuous duration progression`() {
        val symptomaticRun = confirmedLongRun(today.minusDays(1)).copy(
            feedback = WorkoutFeedback(3, RunningSymptomFeedback.YES, today.toEpochDay())
        )
        val strenuousRuns = listOf(today.minusDays(2), today.minusDays(1)).map { date ->
            confirmedLongRun(date).copy(
                feedback = WorkoutFeedback(8, RunningSymptomFeedback.NO, today.toEpochDay())
            )
        }
        val additionalWalkingBreaks = confirmedLongRun(today.minusDays(1)).copy(
            planMatch = confirmedLongRun(today.minusDays(1)).planMatch?.copy(
                assessment = PlannedRunAssessment.SOME_DEVIATION
            )
        )
        val symptomPlan = createPlan(today, listOf(symptomaticRun))
        val highEffortPlan = createPlan(today, strenuousRuns)

        assertEquals(30, continuousTarget(symptomPlan))
        assertEquals(PlanAdjustment.REVIEW_REPORTED_SYMPTOMS, symptomPlan.adjustment)
        assertEquals(30, continuousTarget(highEffortPlan))
        assertEquals(PlanAdjustment.REPEAT_AFTER_HIGH_EFFORT, highEffortPlan.adjustment)
        assertEquals(30, continuousTarget(createPlan(today, listOf(additionalWalkingBreaks))))
    }

    @Test
    fun `continuous duration does not increase when a week passes without a new completed run`() {
        val completedRun = confirmedLongRun(today.minusDays(1))
        val firstPlan = createPlan(today, listOf(completedRun))
        val laterPlan = createPlan(today.plusWeeks(1), listOf(completedRun))

        assertEquals(35, continuousTarget(firstPlan))
        assertEquals(continuousTarget(firstPlan), continuousTarget(laterPlan))
    }

    @Test
    fun `new scheduled import updates plan once while extra or duplicate imports do not overstate progression`() {
        val imported = confirmedLongRun(today.minusDays(1))
        val baseline = continuousTarget(createPlan(today, emptyList()))
        val updated = continuousTarget(createPlan(today, listOf(imported)))
        val duplicate = continuousTarget(createPlan(today, listOf(imported, imported.copy())))
        val extraRun = imported.copy(
            planMatch = imported.planMatch?.copy(type = PlannedRunMatchType.EXTRA)
        )

        assertEquals(30, baseline)
        assertEquals(35, updated)
        assertEquals(updated, duplicate)
        assertEquals(baseline, continuousTarget(createPlan(today, listOf(extraRun))))
    }

    private fun createPlan(date: LocalDate, workouts: List<Workout>) = builder.build(
        profile = TrainingProfile(
            longestComfortableRunMinutes = 60,
            optionalRun = false,
            strengthSessionsPerWeek = 0
        ),
        goal = TrainingGoal(
            id = "scenario-goal",
            raceDate = date.plusWeeks(23),
            raceDistanceMeters = 21_100,
            raceGoal = RaceGoal.RUN_THROUGH
        ),
        today = date,
        workouts = workouts
    )

    private fun continuousTarget(plan: de.run.app.domain.model.TrainingPlan): Int =
        plan.weeks.first().runs.last().steps
            .first { it.name.contains("am Stück") }
            .minutes

    private fun confirmedLongRun(date: LocalDate) = Workout(
        sourceId = "garmin:$date",
        sourceName = "Garmin Connect",
        title = ContinuousRunProgressionAnalyzer.LONG_RUN_TITLE,
        startedAtMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        endedAtMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).plusSeconds(3_600).toEpochMilli(),
        distanceMeters = 8_000.0,
        feedback = WorkoutFeedback(3, RunningSymptomFeedback.NO, date.toEpochDay()),
        planMatch = WorkoutPlanMatch(
            type = PlannedRunMatchType.SCHEDULED,
            plannedTitle = ContinuousRunProgressionAnalyzer.LONG_RUN_TITLE,
            plannedDurationMinutes = 60,
            assessment = PlannedRunAssessment.WELL_MATCHED
        )
    )
}
