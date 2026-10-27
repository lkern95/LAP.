package de.run.app.domain

import de.run.app.domain.model.PlannedRunAssessment
import de.run.app.domain.model.PlannedRunMatchType
import de.run.app.domain.model.RunningSymptomFeedback
import de.run.app.domain.model.Workout
import de.run.app.domain.model.isRunning
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Sets a planned continuous-running target from recent, matched long runs and explicit easy feedback. */
class ContinuousRunProgressionAnalyzer {
    fun targetMinutes(
        workouts: List<Workout>,
        today: LocalDate,
        availableRunningMinutes: Int,
        zone: ZoneId = ZoneId.systemDefault()
    ): Int {
        val qualifyingRuns = workouts.filter(Workout::isRunning)
            .groupBy(Workout::sourceId)
            .values.map { versions -> versions.maxBy(Workout::endedAtMillis) }
            .count { workout ->
                val date = Instant.ofEpochMilli(workout.startedAtMillis).atZone(zone).toLocalDate()
                date >= today.minusDays(COMPARISON_DAYS) && date <= today &&
                    isConfirmedEasyLongRun(workout)
            }

        return minOf(
            STARTING_CONTINUOUS_MINUTES + qualifyingRuns * CONTINUOUS_STEP_MINUTES,
            availableRunningMinutes.coerceAtLeast(0)
        )
    }

    private fun isConfirmedEasyLongRun(workout: Workout): Boolean =
        workout.planMatch?.let { match ->
            match.type == PlannedRunMatchType.SCHEDULED &&
                match.plannedTitle == LONG_RUN_TITLE &&
                match.assessment == PlannedRunAssessment.WELL_MATCHED
        } == true && workout.feedback?.let { feedback ->
            feedback.perceivedEffort?.let(EASY_EFFORT_RANGE::contains) == true &&
                feedback.runningAffectedBySymptoms == RunningSymptomFeedback.NO
        } == true

    companion object {
        const val LONG_RUN_TITLE = "Langer, ruhiger Lauf"
        const val STARTING_CONTINUOUS_MINUTES = 30
        const val CONTINUOUS_STEP_MINUTES = 5

        private const val COMPARISON_DAYS = 84L
        private val EASY_EFFORT_RANGE = 2..4
    }
}
