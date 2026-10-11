package de.run.app.domain

import de.run.app.domain.model.ExerciseCompletion
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class StrengthProgressAnalyzerTest {
    @Test
    fun `counts only completions in the last four weeks`() {
        val today = LocalDate.of(2026, 10, 5)
        val completions = listOf(
            completion(today),
            completion(today.minusDays(27)),
            completion(today.minusDays(28)),
            completion(today.plusDays(1))
        )

        assertEquals(2, StrengthProgressAnalyzer().completionsInLastFourWeeks(completions, today)["calf-raise"])
    }

    private fun completion(date: LocalDate) = ExerciseCompletion("id-$date", "calf-raise", date)
}
