package de.run.app.domain

import de.run.app.domain.model.TrainingHistoryAccess
import de.run.app.domain.model.TrainingHistoryCoverage
import de.run.app.domain.model.TrainingPlan

enum class TrainingPlanEvidenceStatus {
    CHECK_REQUIRED,
    ACCESS_REQUIRED,
    SOURCE_UNAVAILABLE,
    READ_FAILED,
    HISTORY_LIMITED,
    NO_RUNS_FOUND,
    INSUFFICIENT_PERSONAL_BASELINE,
    PERSONAL_BASELINE_AVAILABLE
}

data class TrainingPlanEvidence(
    val status: TrainingPlanEvidenceStatus,
    val locallySavedRunCount: Int
) {
    val requiresStarterPlan: Boolean
        get() = status != TrainingPlanEvidenceStatus.PERSONAL_BASELINE_AVAILABLE
}

class TrainingPlanEvidenceAnalyzer {
    fun analyze(coverage: TrainingHistoryCoverage, plan: TrainingPlan?): TrainingPlanEvidence {
        val status = when (coverage.access) {
            TrainingHistoryAccess.NOT_CHECKED -> TrainingPlanEvidenceStatus.CHECK_REQUIRED
            TrainingHistoryAccess.PERMISSION_NEEDED -> TrainingPlanEvidenceStatus.ACCESS_REQUIRED
            TrainingHistoryAccess.UNAVAILABLE -> TrainingPlanEvidenceStatus.SOURCE_UNAVAILABLE
            TrainingHistoryAccess.FAILED -> TrainingPlanEvidenceStatus.READ_FAILED
            TrainingHistoryAccess.AVAILABLE -> when {
                coverage.hasFullHistoryAccess != true -> TrainingPlanEvidenceStatus.HISTORY_LIMITED
                coverage.confirmsNoRunsInAvailablePeriod -> TrainingPlanEvidenceStatus.NO_RUNS_FOUND
                plan.hasPersonalPaceBaseline() -> TrainingPlanEvidenceStatus.PERSONAL_BASELINE_AVAILABLE
                else -> TrainingPlanEvidenceStatus.INSUFFICIENT_PERSONAL_BASELINE
            }
        }
        return TrainingPlanEvidence(status, coverage.locallySavedRunCount)
    }

    fun visiblePlan(plan: TrainingPlan?, evidence: TrainingPlanEvidence): TrainingPlan? =
        if (plan == null || !evidence.requiresStarterPlan) plan
        else plan.copy(weeks = plan.weeks.take(PROVISIONAL_PLAN_WEEKS))

    private fun TrainingPlan?.hasPersonalPaceBaseline(): Boolean =
        this?.weeks.orEmpty().any { week -> week.runs.any { it.prescription.paceRange != null } }

    private companion object {
        const val PROVISIONAL_PLAN_WEEKS = 2
    }
}
