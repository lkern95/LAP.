package de.run.app.domain

import de.run.app.domain.model.RunningSymptomFeedback
import de.run.app.domain.model.Workout
import de.run.app.domain.model.isRunning
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
        val distinct = workouts.filter(Workout::isRunning).groupBy(Workout::sourceId).values.map { versions ->
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

        val recentHighLoad = distinct
            .filter { workout -> workout.planMatch?.assessment == de.run.app.domain.model.PlannedRunAssessment.HIGHER_LOAD }
            .filter { workout ->
                val runDate = Instant.ofEpochMilli(workout.startedAtMillis).atZone(zone).toLocalDate()
                !runDate.isBefore(today.minusDays(HIGH_LOAD_RESPONSE_DAYS)) && !runDate.isAfter(today)
            }
            .maxByOrNull(Workout::endedAtMillis)
        if (recentHighLoad != null) return TrainingResponse.RECOVER_AFTER_HIGH_LOAD

        val recentCutoff = today.minusDays(RESPONSE_WINDOW_DAYS)
        val highEffort = distinct.filter { workout ->
            val feedback = workout.feedback ?: return@filter false
            val runDate = Instant.ofEpochMilli(workout.startedAtMillis).atZone(zone).toLocalDate()
            !runDate.isBefore(recentCutoff) && !runDate.isAfter(today) &&
                feedback.perceivedEffort?.let { it >= HIGH_EFFORT_THRESHOLD } == true
        }
        return when {
            hasComparableRepeatedHighEffort(highEffort) -> TrainingResponse.REPEAT_AFTER_HIGH_EFFORT
            hasComparableSlowdown(distinct, today, zone) -> TrainingResponse.HOLD_AFTER_SLOWING
            else -> TrainingResponse.NO_CHANGE
        }
    }

    private fun hasComparableRepeatedHighEffort(workouts: List<Workout>): Boolean =
        workouts.sortedBy(Workout::durationSeconds).zipWithNext().any { (first, second) ->
            first.durationSeconds > 0 &&
                second.durationSeconds <= first.durationSeconds * (1.0 + DURATION_TOLERANCE) &&
                first.durationSeconds >= second.durationSeconds * (1.0 - DURATION_TOLERANCE)
        }

    private fun hasComparableSlowdown(workouts: List<Workout>, today: LocalDate, zone: ZoneId): Boolean {
        val weekStart = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val currentStart = weekStart.minusWeeks(1)
        val previousStart = currentStart.minusWeeks(1)
        val comparable = workouts.filter { workout ->
            val feedback = workout.feedback ?: return@filter false
            val date = Instant.ofEpochMilli(workout.startedAtMillis).atZone(zone).toLocalDate()
            date >= previousStart && date < weekStart &&
                (workout.distanceMeters ?: 0.0) >= MIN_COMPARABLE_DISTANCE_METERS &&
                workout.paceSecondsPerKilometer != null &&
                feedback.perceivedEffort?.let(EASY_TO_MODERATE_EFFORT::contains) == true &&
                feedback.runningAffectedBySymptoms == RunningSymptomFeedback.NO
        }
        val previous = comparable.filter { workout ->
            Instant.ofEpochMilli(workout.startedAtMillis).atZone(zone).toLocalDate() < currentStart
        }
        val current = comparable.filter { workout ->
            Instant.ofEpochMilli(workout.startedAtMillis).atZone(zone).toLocalDate() >= currentStart
        }
        if (previous.size < MIN_WEEKLY_COMPARABLE_RUNS || current.size < MIN_WEEKLY_COMPARABLE_RUNS) return false
        val previousDistance = previous.mapNotNull(Workout::distanceMeters).average()
        val currentDistance = current.mapNotNull(Workout::distanceMeters).average()
        if (previousDistance <= 0.0 || kotlin.math.abs(currentDistance - previousDistance) / previousDistance > DISTANCE_SIMILARITY) return false
        val previousEffort = previous.mapNotNull { it.feedback?.perceivedEffort }.average()
        val currentEffort = current.mapNotNull { it.feedback?.perceivedEffort }.average()
        if (kotlin.math.abs(currentEffort - previousEffort) > EFFORT_SIMILARITY) return false
        val previousPace = previous.mapNotNull { it.paceSecondsPerKilometer?.toDouble() }.average()
        val currentPace = current.mapNotNull { it.paceSecondsPerKilometer?.toDouble() }.average()
        return previousPace > 0.0 && currentPace >= previousPace * SLOWDOWN_THRESHOLD
    }

    private companion object {
        const val RESPONSE_WINDOW_DAYS = 28L
        const val HIGH_EFFORT_THRESHOLD = 7
        const val DURATION_TOLERANCE = 0.20
        const val HIGH_LOAD_RESPONSE_DAYS = 4L
        const val MIN_COMPARABLE_DISTANCE_METERS = 3_000.0
        const val MIN_WEEKLY_COMPARABLE_RUNS = 2
        const val DISTANCE_SIMILARITY = 0.20
        const val EFFORT_SIMILARITY = 1.0
        const val SLOWDOWN_THRESHOLD = 1.07
        val EASY_TO_MODERATE_EFFORT = 2..6
    }
}

enum class TrainingResponse {
    NO_CHANGE,
    REPEAT_AFTER_HIGH_EFFORT,
    RECOVER_AFTER_HIGH_LOAD,
    HOLD_AFTER_SLOWING,
    REVIEW_REPORTED_SYMPTOMS
}
