package de.run.app.domain

import de.run.app.domain.model.ExerciseCompletion
import java.time.LocalDate

class StrengthProgressAnalyzer {
    fun completionsInLastFourWeeks(
        completions: List<ExerciseCompletion>,
        today: LocalDate
    ): Map<String, Int> = completions
        .filter { !it.completedOn.isBefore(today.minusDays(27)) && !it.completedOn.isAfter(today) }
        .groupingBy { it.exerciseId }
        .eachCount()
}
