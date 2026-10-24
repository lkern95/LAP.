package de.run.app.data.local

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import de.run.app.data.strength.StrengthCatalogJsonParser
import de.run.app.domain.model.StrengthSessionProgress
import de.run.app.domain.model.StrengthSetProgress
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrengthSessionPersistenceTest {
    @Test
    fun separateSessionsAndTheirCatalogSnapshotsSurviveDatabaseReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "strength-session-persistence-${UUID.randomUUID()}.db"
        val catalogJson = context.assets.open("strength/runner_training_catalog_v2.json")
            .bufferedReader().use { it.readText() }
        val catalog = StrengthCatalogJsonParser().parse(catalogJson)
        val workout = catalog.workouts.first()
        val first = newSession(workout.id, catalogJson, workout.exerciseIds.first())
        val second = newSession(workout.id, catalogJson, workout.exerciseIds.first())

        try {
            val db = database(context, databaseName)
            try {
                db.strengthCompletionDao().upsert(StrengthCompletionEntity("legacy", "old-exercise", "2026-10-01"))
                db.strengthSessionDao().upsert(StrengthSessionEntity.fromDomain(first))
                db.strengthSessionDao().upsert(StrengthSessionEntity.fromDomain(second))
            } finally {
                db.close()
            }
            val reopened = database(context, databaseName)
            try {
                val sessions = reopened.strengthSessionDao().getAllForBackup()
                assertEquals(2, sessions.size)
                assertEquals(setOf(first.sessionId, second.sessionId), sessions.map { it.sessionId }.toSet())
                val restored = sessions.first { it.sessionId == first.sessionId }.toDomain()
                assertEquals(first, restored)
                assertEquals(1, reopened.strengthCompletionDao().getAllForBackup().size)
                assertTrue(restored.catalogSnapshot.contains("${workout.id}"))
            } finally {
                reopened.close()
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    private fun newSession(workoutId: String, snapshot: String, exerciseId: String) = StrengthSessionProgress(
        sessionId = UUID.randomUUID().toString(), workoutId = workoutId, catalogSnapshot = snapshot,
        status = StrengthSessionProgress.Status.IN_PROGRESS, currentExerciseId = exerciseId, currentRound = 2,
        completedSets = listOf(StrengthSetProgress(exerciseId, 1, 1791715200000L)),
        isCurrentSetStarted = false, pauseUntilMillis = 1791715260000L,
        startedAtMillis = 1791715000000L, finishedAtMillis = null
    )

    private fun database(context: android.content.Context, name: String) =
        Room.databaseBuilder(context, RunDatabase::class.java, name).build()
}
