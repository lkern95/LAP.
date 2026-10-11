package de.run.app.domain.model

import kotlin.math.roundToLong

data class Workout(
    val sourceId: String,
    val sourceName: String,
    val title: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val distanceMeters: Double?,
    val averageHeartRateBpm: Int? = null,
    val maximumHeartRateBpm: Int? = null,
    val feedback: WorkoutFeedback? = null
) {
    val durationSeconds: Long
        get() = ((endedAtMillis - startedAtMillis) / 1_000L).coerceAtLeast(0)

    val paceSecondsPerKilometer: Long?
        get() {
            val distance = distanceMeters?.takeIf { it.isFinite() && it > 0.0 } ?: return null
            val duration = durationSeconds.takeIf { it > 0 } ?: return null
            return (duration * 1_000.0 / distance).roundToLong()
        }
}

data class WorkoutFeedback(
    val perceivedEffort: Int?,
    val runningAffectedBySymptoms: RunningSymptomFeedback?,
    val recordedAtMillis: Long
) {
    init {
        require(perceivedEffort == null || perceivedEffort in 0..10)
    }
}

enum class RunningSymptomFeedback {
    NO,
    YES,
    UNSURE
}

sealed interface WorkoutReadResult {
    data class Available(val records: List<Workout>, val hasFullHistoryAccess: Boolean = true) : WorkoutReadResult
    data object PermissionNeeded : WorkoutReadResult
    data object Unavailable : WorkoutReadResult
    data object Failed : WorkoutReadResult
}
