package de.run.app.data.healthconnect

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord

/** Permissions needed to read a run and its optional measurements from Health Connect. */
internal object HealthConnectWorkoutPermissions {
    val exerciseSessions: String = HealthPermission.getReadPermission(ExerciseSessionRecord::class)
    val distances: String = HealthPermission.getReadPermission(DistanceRecord::class)
    val heartRates: String = HealthPermission.getReadPermission(HeartRateRecord::class)

    val requiredForImport: Set<String> = setOf(exerciseSessions)
    val optionalMeasurements: Set<String> = setOf(distances, heartRates)
}
