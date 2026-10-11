package de.run.app.domain.repository

data class AppBackupSummary(
    val workoutCount: Int,
    val strengthSessionCount: Int,
    val goalCount: Int,
    val feedbackCount: Int,
    val replacesExistingData: Boolean
)

data class AppBackupPreview(val id: String, val summary: AppBackupSummary)

enum class AppBackupFailure {
    INVALID_FILE,
    WRONG_PASSWORD_OR_DAMAGED,
    UNSUPPORTED_VERSION,
    WRITE_FAILED,
    RESTORE_FAILED,
    RESTORE_RECOVERY_FAILED
}

class AppBackupException(val failure: AppBackupFailure) : Exception()

interface AppBackupRepository {
    companion object {
        const val MIN_PASSWORD_LENGTH = 8
    }

    suspend fun create(uri: String, password: CharArray)
    suspend fun inspect(uri: String, password: CharArray): AppBackupPreview
    suspend fun restore(previewId: String, confirmReplacingExistingData: Boolean)
}
