package de.run.app.domain

import de.run.app.domain.model.PaceTargetRange
import de.run.app.domain.model.PlanStatus
import de.run.app.domain.model.PlannedRun
import de.run.app.domain.model.RunPrescription
import de.run.app.domain.model.RunStep
import de.run.app.domain.model.TrainingHistoryAccess
import de.run.app.domain.model.TrainingHistoryCoverage
import de.run.app.domain.model.TrainingType
import de.run.app.domain.model.TrainingWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingPlanEvidenceAnalyzerTest {
    private val analyzer = TrainingPlanEvidenceAnalyzer()

    @Test
    fun missingAccessIsNotInterpretedAsNoRunningHistory() {
        val evidence = analyzer.analyze(
            TrainingHistoryCoverage(
                access = TrainingHistoryAccess.PERMISSION_NEEDED,
                locallySavedRunCount = 0
            ),
            planWithWeeks()
        )

        assertEquals(TrainingPlanEvidenceStatus.ACCESS_REQUIRED, evidence.status)
        assertTrue(evidence.requiresStarterPlan)
    }

    @Test
    fun availableCompleteHistoryWithNoRunsIsItsOwnState() {
        val evidence = analyzer.analyze(
            TrainingHistoryCoverage(
                access = TrainingHistoryAccess.AVAILABLE,
                hasFullHistoryAccess = true,
                accessibleRunCount = 0,
                locallySavedRunCount = 0
            ),
            planWithWeeks()
        )

        assertEquals(TrainingPlanEvidenceStatus.NO_RUNS_FOUND, evidence.status)
    }

    @Test
    fun onlyTwoWeeksAreVisibleWhilePlanEvidenceIsProvisional() {
        val visible = analyzer.visiblePlan(
            planWithWeeks(weeks = 5),
            TrainingPlanEvidence(TrainingPlanEvidenceStatus.ACCESS_REQUIRED, 0)
        )

        assertEquals(2, visible?.weeks?.size)
    }

    @Test
    fun personalPaceBaselineRequiresPlanPrescriptionAndCompleteAccess() {
        val coverage = TrainingHistoryCoverage(
            access = TrainingHistoryAccess.AVAILABLE,
            hasFullHistoryAccess = true,
            accessibleRunCount = 5,
            locallySavedRunCount = 5
        )
        val evidence = analyzer.analyze(coverage, planWithWeeks(hasPaceRange = true))

        assertEquals(TrainingPlanEvidenceStatus.PERSONAL_BASELINE_AVAILABLE, evidence.status)
        assertFalse(evidence.requiresStarterPlan)
    }

    private fun planWithWeeks(weeks: Int = 3, hasPaceRange: Boolean = false) =
        de.run.app.domain.model.TrainingPlan(
            eventDate = LocalDate.of(2027, 5, 1),
            status = PlanStatus.READY,
            weeks = (1..weeks).map { number ->
                val run = PlannedRun(
                    title = "Lockerer Lauf",
                    optional = false,
                    steps = listOf(RunStep("Laufen", 30)),
                    trainingType = TrainingType.EASY,
                    prescription = RunPrescription(
                        paceRange = if (hasPaceRange) PaceTargetRange(400, 360) else null
                    )
                )
                TrainingWeek(number, LocalDate.of(2026, 10, 26).plusWeeks(number - 1L), 30, 0, listOf(run), false)
            }
        )
}
