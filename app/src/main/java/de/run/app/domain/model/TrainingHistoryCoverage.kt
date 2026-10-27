package de.run.app.domain.model

import de.run.app.domain.model.WorkoutReadResult.Available
import de.run.app.domain.model.WorkoutReadResult.Failed
import de.run.app.domain.model.WorkoutReadResult.PermissionNeeded
import de.run.app.domain.model.WorkoutReadResult.Unavailable

enum class TrainingHistoryAccess {
    NOT_CHECKED,
    AVAILABLE,
    PERMISSION_NEEDED,
    UNAVAILABLE,
    FAILED
}

data class TrainingHistoryCoverage(
    val access: TrainingHistoryAccess = TrainingHistoryAccess.NOT_CHECKED,
    val hasFullHistoryAccess: Boolean? = null,
    val accessibleRunCount: Int? = null,
    val locallySavedRunCount: Int = 0
) {
    val confirmsNoRunsInAvailablePeriod: Boolean
        get() = access == TrainingHistoryAccess.AVAILABLE &&
            hasFullHistoryAccess == true && accessibleRunCount == 0 && locallySavedRunCount == 0

    companion object {
        fun from(result: WorkoutReadResult, locallySavedWorkouts: List<Workout>): TrainingHistoryCoverage {
            val savedRunCount = locallySavedWorkouts.count(Workout::isRunning)
            return when (result) {
                is Available -> TrainingHistoryCoverage(
                    access = TrainingHistoryAccess.AVAILABLE,
                    hasFullHistoryAccess = result.hasFullHistoryAccess,
                    accessibleRunCount = result.records.count(Workout::isRunning),
                    locallySavedRunCount = savedRunCount
                )
                PermissionNeeded -> TrainingHistoryCoverage(
                    access = TrainingHistoryAccess.PERMISSION_NEEDED,
                    locallySavedRunCount = savedRunCount
                )
                Unavailable -> TrainingHistoryCoverage(
                    access = TrainingHistoryAccess.UNAVAILABLE,
                    locallySavedRunCount = savedRunCount
                )
                Failed -> TrainingHistoryCoverage(
                    access = TrainingHistoryAccess.FAILED,
                    locallySavedRunCount = savedRunCount
                )
            }
        }
    }
}
