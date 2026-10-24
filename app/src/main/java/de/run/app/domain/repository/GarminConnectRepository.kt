package de.run.app.domain.repository

import de.run.app.domain.model.Workout
import java.time.LocalDateTime

interface GarminConnectRepository {
    suspend fun connect(email: String, password: CharArray): GarminConnectLoginResult
    suspend fun submitMfaCode(code: CharArray): GarminConnectLoginResult
    suspend fun isConnected(): Boolean
    suspend fun syncActivityNear(target: LocalDateTime): GarminConnectSyncResult
    suspend fun syncAllActivities(): GarminConnectHistorySyncResult
    suspend fun disconnect()
}

data class GarminConnectHistorySyncSummary(
    val listingCompleted: Boolean,
    val found: Int,
    val newlyImported: Int,
    val alreadyPresent: Int,
    val failed: Int,
    val originalsSaved: Int,
    val stoppedByRateLimit: Boolean,
    val stoppedByGarminAccess: Boolean,
    val completedAtMillis: Long,
    val healthDaysUpdated: Int = 0,
    val healthSyncFailed: Boolean = false,
    val healthSyncBlocked: Boolean = false,
    val healthAuthenticationExpired: Boolean = false
)

sealed interface GarminConnectHistorySyncResult {
    data class Completed(val summary: GarminConnectHistorySyncSummary) : GarminConnectHistorySyncResult
    data class Partial(val summary: GarminConnectHistorySyncSummary, val authenticationExpired: Boolean) : GarminConnectHistorySyncResult
    data object AuthenticationExpired : GarminConnectHistorySyncResult
    data object Failed : GarminConnectHistorySyncResult
}

sealed interface GarminConnectLoginResult {
    data object Connected : GarminConnectLoginResult
    data object NeedsMfa : GarminConnectLoginResult
    data object InvalidCredentials : GarminConnectLoginResult
    data object AccessBlocked : GarminConnectLoginResult
    data object Failed : GarminConnectLoginResult
}

sealed interface GarminConnectSyncResult {
    data class Imported(val workout: Workout, val sha256: String) : GarminConnectSyncResult
    data class AlreadyImported(val workout: Workout) : GarminConnectSyncResult
    data object NoMatchingActivity : GarminConnectSyncResult
    data object InvalidOriginalFile : GarminConnectSyncResult
    data object AuthenticationExpired : GarminConnectSyncResult
    data object AccessBlocked : GarminConnectSyncResult
    data object Failed : GarminConnectSyncResult
}
