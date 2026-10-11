package de.run.app.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.run.app.domain.model.Workout
import de.run.app.domain.model.WorkoutReadResult
import de.run.app.domain.model.WorkoutFeedback
import de.run.app.domain.model.RunningSymptomFeedback
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutRepositoryTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun cleanUp() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun refreshUpdatesMatchingSourceIdentityInsteadOfAddingDuplicate() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, RunDatabase::class.java).build()
        val repository = RoomWorkoutRepository(database.workoutDao(), object : HealthConnectReader {
            var refreshNumber = 0
            override suspend fun readWorkouts(
                from: Instant,
                to: Instant,
                readInBackground: Boolean
            ): WorkoutReadResult {
                refreshNumber += 1
                return WorkoutReadResult.Available(listOf(workout(durationMinutes = 40 + refreshNumber)))
            }
        })

        repository.refresh(Instant.EPOCH, Instant.now())
        repository.refresh(Instant.EPOCH, Instant.now())

        val records = repository.observeRecent().first()
        assertEquals(1, records.size)
        assertEquals(42L * 60, records.single().durationSeconds)
        database.close()
    }

    @Test
    fun repositoryForwardsBackgroundReadModeToHealthConnectReader() = runBlocking {
        var requestedBackgroundRead = false
        val database = Room.inMemoryDatabaseBuilder(context, RunDatabase::class.java).build()
        val repository = RoomWorkoutRepository(database.workoutDao(), object : HealthConnectReader {
            override suspend fun readWorkouts(
                from: Instant,
                to: Instant,
                readInBackground: Boolean
            ): WorkoutReadResult {
                requestedBackgroundRead = readInBackground
                return WorkoutReadResult.Available(emptyList())
            }
        })

        repository.refresh(Instant.EPOCH, Instant.now(), readInBackground = true)

        assertTrue(requestedBackgroundRead)
        database.close()
    }

    @Test
    fun migrationKeepsExistingWorkoutAndLeavesNewMetricsEmpty() = runBlocking {
        createVersionOneDatabase()
        val database = Room.databaseBuilder(context, RunDatabase::class.java, DATABASE_NAME)
            .addMigrations(
                RunDatabase.MIGRATION_1_2,
                RunDatabase.MIGRATION_2_3,
                RunDatabase.MIGRATION_3_4,
                RunDatabase.MIGRATION_4_5
            )
            .build()

        val migrated = database.workoutDao().observeRecent().first().single()

        assertEquals("Garmin Connect", migrated.sourceName)
        assertEquals(5_000.0, migrated.distanceMeters!!, 0.0)
        assertNull(migrated.averageHeartRateBpm)
        assertNull(migrated.maximumHeartRateBpm)
        assertNull(migrated.toDomain().feedback)
        assertEquals(0, database.strengthCompletionDao().observeAll().first().size)
        assertNull(database.trainingDecisionDao().getCurrent())
        database.close()
    }

    @Test
    fun decisionRepositoryReplacesOnlyItsDerivedCurrentSnapshot() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, RunDatabase::class.java).build()
        val repository = RoomTrainingDecisionRepository(database.trainingDecisionDao())
        val first = decisionSnapshot("revision-a")
        val second = decisionSnapshot("revision-b")

        repository.recordCurrent(first)
        repository.recordCurrent(first)
        repository.recordCurrent(second)

        assertEquals(second, database.trainingDecisionDao().getCurrent()?.toDomain())
        assertEquals(1, database.trainingDecisionDao().count())
        database.close()
    }

    @Test
    fun refreshKeepsFeedbackWhenSourceUpdatesTheSameRun() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, RunDatabase::class.java).build()
        val repository = RoomWorkoutRepository(database.workoutDao(), object : HealthConnectReader {
            override suspend fun readWorkouts(from: Instant, to: Instant, readInBackground: Boolean) =
                WorkoutReadResult.Available(listOf(workout(42)))
        })

        repository.refresh(Instant.EPOCH, Instant.now())
        repository.saveFeedback("garmin:stable-id", WorkoutFeedback(6, RunningSymptomFeedback.NO, 99))
        repository.refresh(Instant.EPOCH, Instant.now())

        val updated = repository.observeRecent().first().single()
        assertEquals(42L * 60, updated.durationSeconds)
        assertEquals(6, updated.feedback?.perceivedEffort)
        assertEquals(RunningSymptomFeedback.NO, updated.feedback?.runningAffectedBySymptoms)
        repository.saveFeedback("garmin:stable-id", WorkoutFeedback(null, null, 100))
        repository.refresh(Instant.EPOCH, Instant.now())
        val skipped = repository.observeRecent().first().single().feedback
        assertEquals(null, skipped?.perceivedEffort)
        assertEquals(null, skipped?.runningAffectedBySymptoms)
        assertEquals(100L, skipped?.recordedAtMillis)
        database.close()
    }

    @Test
    fun refreshMergesMatchingFitAndHealthConnectRunsWithoutLosingFeedback() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, RunDatabase::class.java).build()
        val repository = RoomWorkoutRepository(database.workoutDao(), object : HealthConnectReader {
            override suspend fun readWorkouts(from: Instant, to: Instant, readInBackground: Boolean) =
                WorkoutReadResult.Available(listOf(workout(30).copy(
                    startedAtMillis = 10_000L,
                    endedAtMillis = 1_810_000L,
                    averageHeartRateBpm = 145
                )))
        })
        val fit = workout(30).copy(
            sourceId = "fit:historical",
            sourceName = "FIT-Datei",
            startedAtMillis = 20_000L,
            endedAtMillis = 1_820_000L,
            averageHeartRateBpm = 145
        )
        database.workoutDao().upsertPreservingFeedback(listOf(WorkoutEntity.fromDomain(fit)))
        database.workoutDao().saveFeedback("fit:historical", 5, null, 77L)

        repository.refresh(Instant.EPOCH, Instant.now())

        val stored = repository.observeRecent().first()
        assertEquals(1, stored.size)
        assertEquals("fit:historical", stored.single().sourceId)
        assertEquals("FIT-Datei", stored.single().sourceName)
        assertEquals(145, stored.single().averageHeartRateBpm)
        assertEquals(5, stored.single().feedback?.perceivedEffort)
        database.close()
    }

    private fun createVersionOneDatabase() {
        context.deleteDatabase(DATABASE_NAME)
        val file = context.getDatabasePath(DATABASE_NAME)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { legacy ->
            legacy.execSQL(
                """CREATE TABLE workouts (
                    sourceId TEXT NOT NULL PRIMARY KEY,
                    sourceName TEXT NOT NULL,
                    title TEXT NOT NULL,
                    startedAtMillis INTEGER NOT NULL,
                    endedAtMillis INTEGER NOT NULL,
                    distanceMeters REAL
                )""".trimIndent()
            )
            legacy.execSQL(
                "INSERT INTO workouts VALUES ('garmin:old', 'Garmin Connect', 'Lauf', 1000, 601000, 5000.0)"
            )
            legacy.version = 1
        }
    }

    private fun workout(durationMinutes: Int) = Workout(
        sourceId = "garmin:stable-id",
        sourceName = "Garmin Connect",
        title = "Lauf",
        startedAtMillis = 1_000,
        endedAtMillis = 1_000 + durationMinutes * 60_000L,
        distanceMeters = 5_000.0,
        averageHeartRateBpm = 140
    )

    private fun decisionSnapshot(revision: String) = de.run.app.domain.model.TrainingDecisionSnapshot(
        ruleVersion = 1,
        inputRevision = revision,
        targetRevision = "target-revision",
        evaluatedOn = java.time.LocalDate.of(2026, 10, 5),
        runCount = 1,
        latestRunEndedAtMillis = 20,
        recommendationTitle = "Lockerer Lauf",
        recommendationMinutes = 30,
        reasonCode = "CONTINUE"
    )

    private companion object {
        const val DATABASE_NAME = "workout-test.db"
    }
}
