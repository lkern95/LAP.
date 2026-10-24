package de.run.app.domain.model

data class StrengthPrescription(
    val type: Type,
    val reps: Int?,
    val holdSeconds: Int?,
    val perSide: Boolean,
    val restSeconds: Int,
    val tempo: String?,
    val holdPerRepSeconds: Int?
) {
    enum class Type { REPETITIONS, HOLD }
}

data class StrengthMedia(val imageKey: String, val altText: String, val illustrationDirection: String)

data class StrengthCatalogExercise(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val equipment: List<String>,
    val prescription: StrengthPrescription,
    val steps: List<String>,
    val techniqueCues: List<String>,
    val tip: String,
    val commonMistakes: List<String>,
    val cautions: List<String>,
    val regression: String,
    val progression: String,
    val media: StrengthMedia
)

data class StrengthWorkout(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val estimatedMinutes: Int,
    val warmUpMinutes: Int,
    val rounds: Int,
    val restBetweenExercisesSeconds: Int,
    val restBetweenRoundsSeconds: Int,
    val exerciseIds: List<String>,
    val optionalExerciseIds: Set<String>
)

data class StrengthCatalog(
    val schemaVersion: String,
    val safetyAdvice: String,
    val exercises: List<StrengthCatalogExercise>,
    val workouts: List<StrengthWorkout>,
    val sourceJson: String,
    val rawExerciseJson: Map<String, String> = emptyMap(),
    val rawWorkoutJson: Map<String, String> = emptyMap()
) {
    val exercisesById: Map<String, StrengthCatalogExercise> = exercises.associateBy { it.id }
}

data class StrengthSetProgress(val exerciseId: String, val round: Int, val completedAtMillis: Long)

data class StrengthSessionProgress(
    val sessionId: String,
    val workoutId: String,
    val catalogSnapshot: String,
    val status: Status,
    val currentExerciseId: String?,
    val currentRound: Int,
    val completedSets: List<StrengthSetProgress>,
    val isCurrentSetStarted: Boolean,
    val pauseUntilMillis: Long?,
    val startedAtMillis: Long,
    val finishedAtMillis: Long?,
    val schemaVersion: String = "2.0.0"
) {
    enum class Status { IN_PROGRESS, COMPLETED }
}
