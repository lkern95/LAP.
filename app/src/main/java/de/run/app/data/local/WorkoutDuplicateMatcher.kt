package de.run.app.data.local

/** Conservative file/Health Connect match; manual and app-owned workouts are never inferred as duplicates. */
internal object WorkoutDuplicateMatcher {
    private const val FIT_SOURCE = "FIT-Datei"
    private val HEALTH_CONNECT_PREFIXES = listOf("com.garmin.android.apps.connectmobile:", "nl.appyhapps.healthsync:")
    private const val START_TOLERANCE_MILLIS = 30_000L
    private const val DURATION_TOLERANCE_SECONDS = 120L
    private const val DISTANCE_TOLERANCE_METERS = 100.0
    private const val DISTANCE_TOLERANCE_RATIO = 0.015

    fun isDuplicate(first: WorkoutEntity, second: WorkoutEntity): Boolean {
        if (first.activityType != second.activityType) return false
        val firstFamily = family(first)
        val secondFamily = family(second)
        if (firstFamily == null || secondFamily == null) return false
        if (firstFamily == secondFamily) return false
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

    fun merge(existing: WorkoutEntity, incoming: WorkoutEntity): WorkoutEntity {
        val values = if (existing.sourceId == incoming.sourceId) incoming else existing.copy(
            distanceMeters = existing.distanceMeters ?: incoming.distanceMeters,
            averageHeartRateBpm = existing.averageHeartRateBpm ?: incoming.averageHeartRateBpm,
            maximumHeartRateBpm = existing.maximumHeartRateBpm ?: incoming.maximumHeartRateBpm,
            averageCadenceRpm = existing.averageCadenceRpm ?: incoming.averageCadenceRpm
        )
        return values.copy(
            perceivedEffort = existing.perceivedEffort,
            runningAffectedBySymptoms = existing.runningAffectedBySymptoms,
            feedbackRecordedAtMillis = existing.feedbackRecordedAtMillis,
            planMatchType = values.planMatchType ?: existing.planMatchType,
            plannedTrainingId = values.plannedTrainingId ?: existing.plannedTrainingId,
            plannedDateEpochDay = values.plannedDateEpochDay ?: existing.plannedDateEpochDay,
            plannedTitle = values.plannedTitle ?: existing.plannedTitle,
            plannedDurationMinutes = values.plannedDurationMinutes ?: existing.plannedDurationMinutes,
            plannedPaceSlowerSecondsPerKm = values.plannedPaceSlowerSecondsPerKm ?: existing.plannedPaceSlowerSecondsPerKm,
            plannedPaceFasterSecondsPerKm = values.plannedPaceFasterSecondsPerKm ?: existing.plannedPaceFasterSecondsPerKm,
            plannedEffortMinimum = values.plannedEffortMinimum ?: existing.plannedEffortMinimum,
            plannedEffortMaximum = values.plannedEffortMaximum ?: existing.plannedEffortMaximum,
            plannedAssessment = values.plannedAssessment ?: existing.plannedAssessment,
            plannedRationale = values.plannedRationale ?: existing.plannedRationale
        )
    }

    private fun WorkoutEntity.durationSeconds(): Long =
        ((endedAtMillis - startedAtMillis) / 1_000L).coerceAtLeast(0L)

    private fun family(workout: WorkoutEntity): String? = when {
        HEALTH_CONNECT_PREFIXES.any(workout.sourceId::startsWith) || workout.sourceId.startsWith("health-sync:") -> HEALTH_FAMILY
        workout.sourceId.startsWith("garmin:") || workout.sourceName == "Garmin Connect" -> GARMIN_FILE_FAMILY
        workout.sourceId.startsWith("fit:") || workout.sourceName == FIT_SOURCE -> FIT_FILE_FAMILY
        workout.sourceId.startsWith("gpx:") -> GPX_FILE_FAMILY
        workout.sourceId.startsWith("tcx:") -> TCX_FILE_FAMILY
        workout.sourceName == "Garmin-Backup" -> BACKUP_FILE_FAMILY
        else -> null
    }

    private const val HEALTH_FAMILY = "health"
    private const val GARMIN_FILE_FAMILY = "garmin-file"
    private const val FIT_FILE_FAMILY = "fit-file"
    private const val GPX_FILE_FAMILY = "gpx-file"
    private const val TCX_FILE_FAMILY = "tcx-file"
    private const val BACKUP_FILE_FAMILY = "backup-file"
}
