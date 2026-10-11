package de.run.app.data.healthconnect

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.RestingHeartRateRecord

class HealthConnectPermissionCoordinator {
    fun register(
        activity: ComponentActivity,
        onResult: (Set<String>) -> Unit
    ): ActivityResultLauncher<Set<String>> = activity.registerForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
        onResult
    )

    companion object {
        val requestedPermissions = setOf(
            HealthConnectWorkoutPermissions.exerciseSessions,
            HealthConnectWorkoutPermissions.distances,
            HealthConnectWorkoutPermissions.heartRates,
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            BACKGROUND_READ_PERMISSION,
            HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY
        )

        const val BACKGROUND_READ_PERMISSION =
            HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
    }
}
