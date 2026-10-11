package de.run.app.domain

import de.run.app.domain.model.ReadinessFacts
import de.run.app.domain.model.ReadinessGuidanceKind
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadinessGuidanceAnalyzerTest {
    private val analyzer = ReadinessGuidanceAnalyzer()

    @Test
    fun missingPermissionTakesPriorityOverCachedFacts() {
        val guidance = analyzer.analyze(facts(1, 2), false, false, false, false)

        assertEquals(ReadinessGuidanceKind.ACCESS_NEEDED, guidance.kind)
    }

    @Test
    fun missingRunHistoryRecommendsSyncingGarmin() {
        val guidance = analyzer.analyze(facts(0, 2, null), false, true, false, false)

        assertEquals(ReadinessGuidanceKind.NO_RUNS, guidance.kind)
    }

    @Test
    fun completedWeeklyTrainingRecommendsRecovery() {
        val guidance = analyzer.analyze(facts(2, 2), false, true, false, false)

        assertEquals(ReadinessGuidanceKind.WEEK_COMPLETE, guidance.kind)
    }

    @Test
    fun incompleteWeeklyTrainingPointsBackToThePlan() {
        val guidance = analyzer.analyze(facts(1, 2), false, true, false, false)

        assertEquals(ReadinessGuidanceKind.FOLLOW_PLAN, guidance.kind)
        assertEquals(1, guidance.completedRuns)
        assertEquals(2, guidance.plannedRuns)
    }

    private fun facts(runs: Int, planned: Int, minutesSinceLastRun: Long? = 60) = ReadinessFacts(
        minutesSinceLastRun = minutesSinceLastRun,
        runsThisWeek = runs,
        plannedRunsThisWeek = planned,
        distanceThisWeekMeters = 5_000.0
    )
}
