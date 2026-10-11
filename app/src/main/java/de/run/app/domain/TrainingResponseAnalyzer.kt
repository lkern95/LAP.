package de.run.app.domain

import de.run.app.domain.model.RunningSymptomFeedback
import de.run.app.domain.model.Workout
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Conservative response flags based only on feedback the runner chose to record. */
class TrainingResponseAnalyzer {
    fun analyze(
        workouts: List<Workout>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): TrainingResponse {
        val distinct = workouts.groupBy(Workout::sourceId).values.map { versions ->
            versions.maxWith(compareBy<Workout> { it.endedAtMillis }.thenBy { it.feedback?.recordedAtMillis ?: 0L })
        }
        val latestSymptoms = distinct.mapNotNull { workout ->
            workout.feedback?.takeIf { it.runningAffectedBySymptoms != null }?.let { workout to it }
        }.maxByOrNull { (_, feedback) -> feedback.recordedAtMillis }
        if (latestSymptoms?.second?.runningAffectedBySymptoms in setOf(
                RunningSymptomFeedback.YES,
                RunningSymptomFeedback.UNSURE
            )
        ) return TrainingResponse.REVIEW_REPORTED_SYMPTOMS

        val recentCutoff = today.minusDays(RESPONSE_WINDOW_DAYS)
        val highEffort = distinct.filter { workout ->
            val feedback = workout.feedback ?: return@filter false
            val runDate = Instant.ofEpochMilli(workout.startedAtMillis).atZone(zone).toLocalDate()
            !runDate.isBefore(recentCutoff) && !runDate.isAfter(today) &&
                feedback.perceivedEffort?.let { it >= HIGH_EFFORT_THRESHOLD } == true
        }
        return if (hasComparableRepeatedHighEffort(highEffort)) {
            TrainingResponse.REPEAT_AFTER_HIGH_EFFORT
        } else TrainingResponse.NO_CHANGE
    }

    private fun hasComparableRepeatedHighEffort(workouts: List<Workout>): Boolean =
        workouts.sortedBy(Workout::durationSeconds).zipWithNext().any { (first, second) ->
            first.durationSeconds > 0 &&
                second.durationSeconds <= first.durationSeconds * (1.0 + DURATION_TOLERANCE) &&
                first.durationSeconds >= second.durationSeconds * (1.0 - DURATION_TOLERANCE)
        }

    private companion object {
        const val RESPONSE_WINDOW_DAYS = 28L
        const val HIGH_EFFORT_THRESHOLD = 7
        const val DURATION_TOLERANCE = 0.20
    }
}

enum class TrainingResponse {
    NO_CHANGE,
    REPEAT_AFTER_HIGH_EFFORT,
    REVIEW_REPORTED_SYMPTOMS
}
