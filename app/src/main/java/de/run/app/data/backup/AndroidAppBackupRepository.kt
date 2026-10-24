package de.run.app.data.backup

import android.content.Context
import androidx.room.withTransaction
import de.run.app.data.local.RunDatabase
import de.run.app.data.local.GoalHistoryDecodeResult
import de.run.app.data.local.TrainingGoalHistoryCodec
import de.run.app.domain.repository.AppBackupException
import de.run.app.domain.repository.AppBackupFailure
import de.run.app.domain.repository.AppBackupPreview
import de.run.app.domain.repository.AppBackupRepository
import de.run.app.domain.repository.AppBackupSummary
import java.util.UUID
import kotlinx.coroutines.flow.first

class AndroidAppBackupRepository(
    private val context: Context,
    private val database: RunDatabase,
    private val cryptography: BackupCryptography = BackupCryptography(),
    private val documents: BackupDocumentAccess = SafBackupDocumentAccess(context),
    private val preferences: BackupPreferencesAccess = AndroidBackupPreferencesAccess(context)
) : AppBackupRepository {
    private val candidates = mutableMapOf<String, BackupSnapshot>()

    override suspend fun create(uri: String, password: CharArray) {
        try {
            val payload = BackupPayloadCodec.encode(readSnapshot())
            val archive = cryptography.seal(payload, password, BackupPayloadCodec.CURRENT_VERSION)
            documents.write(uri, archive)
        } catch (failure: AppBackupException) {
            throw failure
        } catch (_: Exception) {
            throw AppBackupException(AppBackupFailure.WRITE_FAILED)
        }
    }

    override suspend fun inspect(uri: String, password: CharArray): AppBackupPreview {
        val bytes = try {
            documents.read(uri, BackupCryptography.MAX_FILE_BYTES)
        } catch (_: Exception) {
            throw AppBackupException(AppBackupFailure.INVALID_FILE)
        }
        val decrypted = try {
            cryptography.open(bytes, password)
        } catch (_: UnsupportedBackupFileVersion) {
            throw AppBackupException(AppBackupFailure.UNSUPPORTED_VERSION)
        } catch (_: WrongPasswordOrDamagedFile) {
            throw AppBackupException(AppBackupFailure.WRONG_PASSWORD_OR_DAMAGED)
        } catch (_: InvalidBackupFile) {
            throw AppBackupException(AppBackupFailure.INVALID_FILE)
        }
        val snapshot = try {
            BackupPayloadCodec.decode(decrypted.payload, decrypted.formatVersion).also(::validateSnapshot)
        } catch (_: BackupPayloadCodec.UnsupportedBackupVersion) {
            throw AppBackupException(AppBackupFailure.UNSUPPORTED_VERSION)
        } catch (_: Exception) {
            throw AppBackupException(AppBackupFailure.INVALID_FILE)
        }
        val id = UUID.randomUUID().toString()
        candidates.clear()
        candidates[id] = snapshot
        val current = readSnapshot()
        return AppBackupPreview(id, snapshot.summary(current.hasUserData()))
    }

    override suspend fun restore(previewId: String, confirmReplacingExistingData: Boolean) {
        val snapshot = candidates[previewId] ?: throw AppBackupException(AppBackupFailure.INVALID_FILE)
        val current = readSnapshot()
        if (current.hasUserData() && !confirmReplacingExistingData) {
            throw AppBackupException(AppBackupFailure.RESTORE_FAILED)
        }
        try {
            replaceData(snapshot)
            candidates.remove(previewId)
        } catch (_: Exception) {
            val rolledBack = runCatching { replaceData(current) }.isSuccess
            throw AppBackupException(
                if (rolledBack) AppBackupFailure.RESTORE_FAILED else AppBackupFailure.RESTORE_RECOVERY_FAILED
            )
        }
    }

    private suspend fun readSnapshot(): BackupSnapshot {
        val workoutDao = database.workoutDao()
        val strengths = database.strengthCompletionDao()
        val decisions = database.trainingDecisionDao()
        return BackupSnapshot(
            workouts = workoutDao.getAllForBackup(),
            strengthCompletions = strengths.getAllForBackup(),
            trainingDecisions = decisions.getAllForBackup(),
            stores = preferences.read(),
            strengthSessions = database.strengthSessionDao().getAllForBackup()
        )
    }

    private suspend fun replaceData(snapshot: BackupSnapshot) {
        database.withTransaction {
            database.workoutDao().deleteAll()
            database.strengthCompletionDao().deleteAll()
            database.trainingDecisionDao().deleteAll()
            database.strengthSessionDao().deleteAll()
            database.workoutDao().upsertAll(snapshot.workouts)
            database.strengthCompletionDao().upsertAll(snapshot.strengthCompletions)
            database.trainingDecisionDao().upsertAll(snapshot.trainingDecisions)
            database.strengthSessionDao().upsertAll(snapshot.strengthSessions)
        }
        preferences.replace(snapshot.stores)
    }

    private fun validateSnapshot(snapshot: BackupSnapshot) {
        if (snapshot.workouts.map { it.sourceId }.toSet().size != snapshot.workouts.size ||
            snapshot.strengthCompletions.map { it.id }.toSet().size != snapshot.strengthCompletions.size ||
            snapshot.trainingDecisions.map { it.id }.toSet().size != snapshot.trainingDecisions.size ||
            snapshot.strengthSessions.map { it.sessionId }.toSet().size != snapshot.strengthSessions.size ||
            snapshot.stores.keys != setOf("training_settings", "appearance_settings") ||
            snapshot.stores.values.any { values -> values.map { it.name }.toSet().size != values.size }
        ) throw InvalidBackupFile()
        snapshot.stores.values.flatten().forEach { preference ->
            when (preference.type) {
                "string" -> Unit
                "int" -> preference.value.toIntOrNull() ?: throw InvalidBackupFile()
                "long" -> preference.value.toLongOrNull() ?: throw InvalidBackupFile()
                "float" -> preference.value.toFloatOrNull()?.takeIf(Float::isFinite) ?: throw InvalidBackupFile()
                "double" -> preference.value.toDoubleOrNull()?.takeIf(Double::isFinite) ?: throw InvalidBackupFile()
                "boolean" -> preference.value.toBooleanStrictOrNull() ?: throw InvalidBackupFile()
                "string_set" -> preference.value.split(',').filter(String::isNotEmpty).forEach {
                    if (!it.startsWith("x")) throw InvalidBackupFile()
                    runCatching { java.util.Base64.getDecoder().decode(it.removePrefix("x")) }
                        .getOrElse { throw InvalidBackupFile() }
                }
                else -> throw InvalidBackupFile()
            }
        }
        snapshot.strengthCompletions.forEach { runCatching { java.time.LocalDate.parse(it.completedOn) }.getOrElse { throw InvalidBackupFile() } }
        snapshot.trainingDecisions.forEach { runCatching { java.time.LocalDate.parse(it.evaluatedOn) }.getOrElse { throw InvalidBackupFile() } }
        snapshot.strengthSessions.forEach { session ->
            if (session.status !in setOf("in_progress", "completed") || session.currentRound < 1 ||
                session.schemaVersion != "2.0.0" || session.startedAtMillis < 0 ||
                session.catalogSnapshot.toByteArray(Charsets.UTF_8).size > de.run.app.data.strength.StrengthCatalogJsonParser.MAX_CATALOG_BYTES
            ) throw InvalidBackupFile()
            try {
                val catalog = de.run.app.data.strength.StrengthCatalogJsonParser().parse(session.catalogSnapshot)
                val progress = session.toDomain()
                val workout = catalog.workouts.first { it.id == progress.workoutId }
                if (progress.currentExerciseId != null && progress.currentExerciseId !in workout.exerciseIds ||
                    progress.completedSets.map { "${it.exerciseId}:${it.round}" }.toSet().size != progress.completedSets.size ||
                    progress.completedSets.any { it.exerciseId !in workout.exerciseIds || it.round !in 1..workout.rounds || it.completedAtMillis < progress.startedAtMillis }
                ) throw InvalidBackupFile()
            } catch (_: Exception) { throw InvalidBackupFile() }
        }
        snapshot.stores["training_settings"].orEmpty()
            .firstOrNull { it.name == "training_goal_history_v1" }
            ?.let { history ->
                if (TrainingGoalHistoryCodec.decode(history.value) !is GoalHistoryDecodeResult.Valid) {
                    throw InvalidBackupFile()
                }
            }
    }

    private fun BackupSnapshot.summary(replacesExisting: Boolean): AppBackupSummary {
        val goalHistory = stores["training_settings"].orEmpty().firstOrNull { it.name == "training_goal_history_v1" }?.value
        val goals = goalHistory?.removePrefix("v1:")?.takeIf { goalHistory.startsWith("v1:") }
            ?.takeIf(String::isNotEmpty)?.count { it == ';' }?.plus(1) ?: 0
        return AppBackupSummary(
            workoutCount = workouts.size,
            strengthSessionCount = strengthCompletions.size + strengthSessions.count { it.status == "completed" },
            goalCount = goals,
            feedbackCount = workouts.count { it.feedbackRecordedAtMillis != null },
            replacesExistingData = replacesExisting
        )
    }

    private fun BackupSnapshot.hasUserData() = workouts.isNotEmpty() || strengthCompletions.isNotEmpty() ||
        trainingDecisions.isNotEmpty() || strengthSessions.isNotEmpty() || stores.values.any { it.isNotEmpty() }
}
