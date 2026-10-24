package de.run.app.domain.model

import java.time.LocalDate

data class ExerciseCompletion(
    val id: String,
    val exerciseId: String,
    val completedOn: LocalDate
)
