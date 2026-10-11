package de.run.app.domain.model

import java.time.LocalDate

data class StrengthExercise(
    val id: String,
    val name: String,
    val focus: String,
    val instruction: String,
    val dosage: String
)

data class ExerciseCompletion(
    val id: String,
    val exerciseId: String,
    val completedOn: LocalDate
)
