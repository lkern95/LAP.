package de.run.app.data.local

/** Conservative FIT/Health Connect match; never collapses two records from the same source. */
internal object WorkoutDuplicateMatcher {
    private const val FIT_SOURCE = "FIT-Datei"
    private const val START_TOLERANCE_MILLIS = 30_000L
    private const val DURATION_TOLERANCE_SECONDS = 120L
    private const val DISTANCE_TOLERANCE_METERS = 100.0
    private const val DISTANCE_TOLERANCE_RATIO = 0.015

    fun isDuplicate(first: WorkoutEntity, second: WorkoutEntity): Boolean {
        if ((first.sourceName == FIT_SOURCE) == (second.sourceName == FIT_SOURCE)) return false
        if (kotlin.math.abs(first.startedAtMillis - second.startedAtMillis) > START_TOLERANCE_MILLIS) return false
        if (kotlin.math.abs(first.durationSeconds() - second.durationSeconds()) > DURATION_TOLERANCE_SECONDS) return false
        val firstDistance = first.distanceMeters ?: return false
        val secondDistance = second.distanceMeters ?: return false
        val allowedDifference = maxOf(
            DISTANCE_TOLERANCE_METERS,
            maxOf(firstDistance, secondDistance) * DISTANCE_TOLERANCE_RATIO
        )
        return kotlin.math.abs(firstDistance - secondDistance) <= allowedDifference
    }

    fun merge(existing: WorkoutEntity, incoming: WorkoutEntity): WorkoutEntity = existing.copy(
        title = incoming.title,
        endedAtMillis = incoming.endedAtMillis,
        distanceMeters = incoming.distanceMeters ?: existing.distanceMeters,
        averageHeartRateBpm = incoming.averageHeartRateBpm ?: existing.averageHeartRateBpm,
        maximumHeartRateBpm = incoming.maximumHeartRateBpm ?: existing.maximumHeartRateBpm,
        perceivedEffort = existing.perceivedEffort,
        runningAffectedBySymptoms = existing.runningAffectedBySymptoms,
        feedbackRecordedAtMillis = existing.feedbackRecordedAtMillis
    )

    private fun WorkoutEntity.durationSeconds(): Long =
        ((endedAtMillis - startedAtMillis) / 1_000L).coerceAtLeast(0L)
}
