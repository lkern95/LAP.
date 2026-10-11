package de.run.app.data.backup

import de.run.app.data.local.StrengthCompletionEntity
import de.run.app.data.local.TrainingDecisionEntity
import de.run.app.data.local.WorkoutEntity

data class BackupPreference(val name: String, val type: String, val value: String)

data class BackupSnapshot(
    val workouts: List<WorkoutEntity>,
    val strengthCompletions: List<StrengthCompletionEntity>,
    val trainingDecisions: List<TrainingDecisionEntity>,
    val stores: Map<String, List<BackupPreference>>
)
