package de.run.app.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.run.app.data.local.RunDatabase
import de.run.app.data.local.StrengthCompletionEntity
import de.run.app.data.local.TrainingDecisionEntity
import de.run.app.data.local.WorkoutEntity
import de.run.app.domain.repository.AppBackupException
import de.run.app.data.local.TrainingGoalHistoryCodec
import de.run.app.domain.model.RaceGoal
import de.run.app.domain.model.TrainingGoal
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidAppBackupRepositoryTest {
    private lateinit var database: RunDatabase
    private lateinit var documents: MemoryDocuments
    private lateinit var preferences: MemoryPreferences
    private lateinit var repository: AndroidAppBackupRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, RunDatabase::class.java).build()
        documents = MemoryDocuments()
        preferences = MemoryPreferences(initialSettings())
        repository = AndroidAppBackupRepository(
            context = context,
            database = database,
            cryptography = BackupCryptography(iterations = 100_000),
            documents = documents,
            preferences = preferences
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun exportInspectAndRestorePreservesAllAppDataAndRequiresReplacementConsent() = runBlocking {
        database.workoutDao().upsertAll(listOf(workout("old-run", feedback = true)))
        database.strengthCompletionDao().upsertAll(listOf(StrengthCompletionEntity("strength-1", "ankle", "2026-10-10")))
        database.trainingDecisionDao().upsertAll(listOf(decision("original")))
        repository.create("backup", "mein-langes-passwort".toCharArray())

        database.workoutDao().deleteAll()
        database.workoutDao().upsertAll(listOf(workout("new-run", feedback = false)))
        database.strengthCompletionDao().deleteAll()
        database.trainingDecisionDao().deleteAll()
        database.trainingDecisionDao().upsertAll(listOf(decision("newer")))
        preferences.replace(mapOf("training_settings" to listOf(BackupPreference("theme", "string", "new")), "appearance_settings" to emptyList()))

        val preview = repository.inspect("backup", "mein-langes-passwort".toCharArray())
        assertEquals(1, preview.summary.workoutCount)
        assertEquals(1, preview.summary.feedbackCount)
        assertEquals(true, preview.summary.replacesExistingData)
        assertThrows(AppBackupException::class.java) {
            runBlocking { repository.restore(preview.id, confirmReplacingExistingData = false) }
        }
        assertEquals("new-run", database.workoutDao().getAllForBackup().single().sourceId)

        repository.restore(preview.id, confirmReplacingExistingData = true)

        assertEquals(listOf(workout("old-run", feedback = true)), database.workoutDao().getAllForBackup())
        assertEquals(listOf(StrengthCompletionEntity("strength-1", "ankle", "2026-10-10")), database.strengthCompletionDao().getAllForBackup())
        assertEquals(listOf(decision("original")), database.trainingDecisionDao().getAllForBackup())
        assertEquals(normalizePreferences(initialSettings()), normalizePreferences(preferences.read()))
    }

    @Test
    fun damagedArchiveIsRejectedBeforeAnyStoredDataChanges() = runBlocking {
        database.workoutDao().upsertAll(listOf(workout("kept-run", feedback = false)))
        repository.create("good", "mein-langes-passwort".toCharArray())
        documents.files["bad"] = documents.files.getValue("good").copyOf().also {
            it[it.lastIndex] = (it.last().toInt() xor 0x20).toByte()
        }

        assertThrows(AppBackupException::class.java) {
            runBlocking { repository.inspect("bad", "mein-langes-passwort".toCharArray()) }
        }
        assertEquals("kept-run", database.workoutDao().getAllForBackup().single().sourceId)
        assertEquals(normalizePreferences(initialSettings()), normalizePreferences(preferences.read()))
    }

    private fun workout(id: String, feedback: Boolean) = WorkoutEntity(
        sourceId = id, sourceName = "Garmin Connect", title = "Lauf", startedAtMillis = 1, endedAtMillis = 2,
        distanceMeters = 5_000.0, averageHeartRateBpm = 140, maximumHeartRateBpm = 160,
        perceivedEffort = if (feedback) 3 else null,
        runningAffectedBySymptoms = if (feedback) "NONE" else null,
        feedbackRecordedAtMillis = if (feedback) 3 else null
    )

    private fun normalizePreferences(
        values: Map<String, List<BackupPreference>>
    ): Map<String, List<BackupPreference>> = values.mapValues { (_, preferences) ->
        preferences.sortedBy(BackupPreference::name)
    }

    private fun decision(revision: String) = TrainingDecisionEntity(
        id = "current", ruleVersion = 1, inputRevision = revision, targetRevision = "goal",
        evaluatedOn = "2026-10-10", runCount = 1, latestRunEndedAtMillis = 2,
        recommendationTitle = "Locker laufen", recommendationMinutes = 30, reasonCode = "READY"
    )

    private fun initialSettings() = mapOf(
        "training_settings" to listOf(
            BackupPreference(
                "training_goal_history_v1", "string",
                TrainingGoalHistoryCodec.encode(listOf(TrainingGoal("goal-1", LocalDate.of(2027, 3, 21), 21_100, RaceGoal.ARRIVE)))
            ),
            BackupPreference("usual_run_time_limit", "int", "45"),
            BackupPreference("consider_night_shift", "boolean", "true")
        ),
        "appearance_settings" to listOf(BackupPreference("theme", "string", "OLIVE"))
    )

    private class MemoryPreferences(initial: Map<String, List<BackupPreference>>) : BackupPreferencesAccess {
        private var values = initial
        override suspend fun read(): Map<String, List<BackupPreference>> = values
        override suspend fun replace(stores: Map<String, List<BackupPreference>>) { values = stores }
    }

    private class MemoryDocuments : BackupDocumentAccess {
        val files = mutableMapOf<String, ByteArray>()
        override fun read(uri: String, maximumBytes: Int): ByteArray = files.getValue(uri).also {
            require(it.size <= maximumBytes)
        }
        override fun write(uri: String, bytes: ByteArray) { files[uri] = bytes }
    }
}
