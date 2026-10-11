package de.run.app.data.local

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import de.run.app.domain.model.RunningSymptomFeedback
import de.run.app.domain.model.Workout
import de.run.app.domain.model.WorkoutFeedback
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutProcessPersistenceTest {
    @Test
    fun importedWorkoutAndFeedbackSurviveAppProcessRestart() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val externalMode = InstrumentationRegistry.getArguments().getString(PROCESS_PERSISTENCE_MODE)
        val sourceId = if (externalMode == null) "test:process-persistence:${UUID.randomUUID()}"
            else EXTERNAL_PROCESS_TEST_SOURCE_ID
        val workout = sampleWorkout(sourceId)
        val database = openAppDatabase(context)
        try {
            val dao = database.workoutDao()
            if (externalMode == VERIFY_MODE) {
                assertPersistedTestRun(dao.getBySourceId(sourceId))
            } else {
                dao.upsertAll(listOf(WorkoutEntity.fromDomain(workout)))
                assertPersistedTestRun(dao.getBySourceId(sourceId))
            }
        } finally {
            database.close()
        }

        if (externalMode == SEED_MODE) return@runBlocking

        val reopenedDatabase = openAppDatabase(context)
        try {
            if (externalMode == VERIFY_MODE) {
                assertPersistedTestRun(reopenedDatabase.workoutDao().getBySourceId(sourceId))
            } else {
                assertPersistedTestRun(reopenedDatabase.workoutDao().getBySourceId(sourceId))
            }
        } finally {
            reopenedDatabase.openHelper.writableDatabase.execSQL(
                "DELETE FROM workouts WHERE sourceId = ?",
                arrayOf(sourceId)
            )
            reopenedDatabase.close()
        }
    }

    private fun assertPersistedTestRun(persisted: WorkoutEntity?) {
        assertNotNull("Der importierte Testlauf fehlt nach dem Neustart.", persisted)
        assertEquals(5_000.0, persisted?.distanceMeters ?: 0.0, 0.0)
        assertEquals(7, persisted?.perceivedEffort)
        assertEquals(RunningSymptomFeedback.UNSURE.name, persisted?.runningAffectedBySymptoms)
        assertEquals(FEEDBACK_RECORDED_AT, persisted?.feedbackRecordedAtMillis)
    }

    private fun sampleWorkout(sourceId: String) = Workout(
        sourceId = sourceId,
        sourceName = "Testquelle",
        title = "Testlauf",
        startedAtMillis = WORKOUT_STARTED_AT,
        endedAtMillis = WORKOUT_ENDED_AT,
        distanceMeters = 5_000.0,
        averageHeartRateBpm = 140,
        feedback = WorkoutFeedback(7, RunningSymptomFeedback.UNSURE, FEEDBACK_RECORDED_AT)
    )

    private fun openAppDatabase(context: android.content.Context) =
        Room.databaseBuilder(context, RunDatabase::class.java, "run-local.db")
            .addMigrations(
                RunDatabase.MIGRATION_1_2,
                RunDatabase.MIGRATION_2_3,
                RunDatabase.MIGRATION_3_4,
                RunDatabase.MIGRATION_4_5
            )
            .build()

    private companion object {
        const val PROCESS_PERSISTENCE_MODE = "processPersistenceMode"
        const val SEED_MODE = "seed"
        const val VERIFY_MODE = "verify"
        const val EXTERNAL_PROCESS_TEST_SOURCE_ID = "test:process-persistence:lap"
        const val WORKOUT_STARTED_AT = 1_700_000_000_000L
        const val WORKOUT_ENDED_AT = 1_700_001_800_000L
        const val FEEDBACK_RECORDED_AT = 1_700_001_900_000L
    }
}
