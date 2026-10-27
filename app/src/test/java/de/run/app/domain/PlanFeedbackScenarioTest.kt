package de.run.app.domain

import de.run.app.domain.model.RaceGoal
import de.run.app.domain.model.TrainingGoal
import de.run.app.domain.model.TrainingProfile
import de.run.app.domain.model.Workout
import de.run.app.domain.model.ReadinessGuidanceKind
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanFeedbackScenarioTest {
    private val today = LocalDate.of(2026, 10, 5)
    private val planBuilder = TrainingPlanBuilder()
    private val factsAnalyzer = ReadinessFactsAnalyzer()
    private val guidanceAnalyzer = ReadinessGuidanceAnalyzer()

    @Test
    fun beginnerWithoutHistoryGetsTwoVisibleSessionsAndNoFabricatedDistanceAssessment() {
        val plan = planBuilder.build(profile(), goal(), today, emptyList())
        val facts = factsAnalyzer.analyze(emptyList(), plan.weeks.first().plannedRunCount, today.atStartOfDay().toInstant(ZoneOffset.UTC), today)
        val guidance = guidanceAnalyzer.analyze(facts, false, true, false, false)

        assertEquals(2, plan.weeks.first().plannedRunCount)
        assertTrue(plan.weeks.first().runs.all {
            it.distanceSpikeAssessment?.level == de.run.app.domain.model.DistanceSpikeLevel.NOT_ASSESSABLE
        })
        assertEquals(2, facts.plannedRunsThisWeek)
        assertEquals(ReadinessGuidanceKind.NO_RUNS, guidance.kind)
    }

    @Test
    fun consistentRunnerGetsThreeOnlyWhenTheGeneratedPlanActuallyIncludesAnOptionalRun() {
        val workouts = listOf(1L, 3L, 5L, 8L, 10L, 12L).map { daysAgo -> workout(today.minusDays(daysAgo)) }
        val plan = planBuilder.build(profile(optionalRun = true), goal(), today, workouts)
        val facts = factsAnalyzer.analyze(workouts, plan.weeks.first().plannedRunCount, today.atTime(18, 0).toInstant(ZoneOffset.UTC), today)

        assertEquals(3, plan.weeks.first().plannedRunCount)
        assertEquals(3, facts.plannedRunsThisWeek)
        assertTrue(plan.weeks.first().runs.any { it.optional })
    }

    @Test
    fun multiWeekTrainingBreakUsesGentleReturnAndDoesNotCountAnOptionalRun() {
        val workouts = listOf(workout(today.minusDays(21)))
        val plan = planBuilder.build(profile(optionalRun = true), goal(), today, workouts)
        val facts = factsAnalyzer.analyze(
            workouts,
            plan.weeks.first().plannedRunCount,
            today.atTime(18, 0).toInstant(ZoneOffset.UTC),
            today
        )
        val guidance = guidanceAnalyzer.analyze(facts, false, true, false, false)

        assertEquals(de.run.app.domain.model.PlanAdjustment.RETURN_GENTLY, plan.adjustment)
        assertEquals(2, plan.weeks.first().plannedRunCount)
        assertTrue(plan.weeks.first().runs.none { it.optional })
        assertEquals(2, facts.plannedRunsThisWeek)
        assertEquals(ReadinessGuidanceKind.FOLLOW_PLAN, guidance.kind)
    }

    private fun profile(optionalRun: Boolean = false) = TrainingProfile(
        longestComfortableRunMinutes = 45,
        optionalRun = optionalRun,
        strengthSessionsPerWeek = 1
    )

    private fun goal() = TrainingGoal(
        id = "scenario-goal",
        raceDate = today.plusWeeks(23),
        raceDistanceMeters = 21_100,
        raceGoal = RaceGoal.RUN_THROUGH
    )

    private fun workout(date: LocalDate) = Workout(
        sourceId = "scenario-${date}",
        sourceName = "Test",
        title = "Lauf",
        startedAtMillis = date.atTime(9, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
        endedAtMillis = date.atTime(10, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
        distanceMeters = 5_000.0
    )
}
