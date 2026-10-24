package de.run.app.domain

import de.run.app.data.strength.StrengthCatalogJsonParser
import de.run.app.domain.model.StrengthSessionProgress
import de.run.app.domain.model.StrengthSetProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StrengthSessionProgressorTest {
    private val catalog = StrengthCatalogJsonParser().parse(
        File("src/main/assets/strength/runner_training_catalog_v2.json").readText()
    )
    private val progressor = StrengthSessionProgressor()

    @Test
    fun openingSessionDoesNotCompleteSetsAndRoundsAdvanceOneAtATime() {
        val workout = catalog.workouts.first()
        val firstId = workout.exerciseIds.first()
        var session = session(workout.id, catalog.sourceJson)
        assertTrue(session.completedSets.isEmpty())

        session = progressor.startSet(session, catalog, 1_000L)
        assertTrue(session.isCurrentSetStarted)
        session = progressor.completeSet(session, catalog, 2_000L)

        assertEquals(listOf(1), session.completedSets.map { it.round })
        assertEquals(firstId, session.completedSets.single().exerciseId)
        assertEquals(2, session.currentRound)
        assertEquals(firstId, session.currentExerciseId)
        assertNotNull(session.pauseUntilMillis)

        session = session.copy(pauseUntilMillis = null)
        session = progressor.startSet(session, catalog, 3_000L)
        session = progressor.completeSet(session, catalog, 4_000L)
        assertEquals(1, session.currentRound)
        assertEquals(2, session.completedSets.size)
        assertEquals(workout.exerciseIds[1], session.currentExerciseId)
    }

    @Test
    fun heldSetStartsLocalTimerAndCompletesOnlyAfterExplicitTimerResult() {
        val workout = catalog.workouts.first()
        val holdId = "wandsitz_90_90"
        val session = session(workout.id, catalog.sourceJson).copy(currentExerciseId = holdId)
        val started = progressor.startSet(session, catalog, 10_000L)

        assertEquals(40_000L, started.pauseUntilMillis)
        assertTrue(started.isCurrentSetStarted)
        assertTrue(started.completedSets.isEmpty())
        val completed = progressor.completeSet(started, catalog, 40_000L)
        assertEquals(holdId, completed.completedSets.single().exerciseId)
    }

    @Test
    fun optionalLastExerciseCanBeSkippedWithoutBlockingCompletion() {
        val workout = catalog.workouts.last()
        val completedRequiredSets = workout.exerciseIds.filterNot(workout.optionalExerciseIds::contains).flatMap { id ->
            (1..workout.rounds).map { round -> StrengthSetProgress(id, round, 10_000L) }
        }
        var session = session(workout.id, catalog.sourceJson).copy(
            currentExerciseId = workout.exerciseIds.last(), completedSets = completedRequiredSets
        )
        assertTrue(workout.exerciseIds.last() in workout.optionalExerciseIds)

        session = progressor.skipOptionalExercise(session, catalog, 20_000L)

        assertEquals(StrengthSessionProgress.Status.COMPLETED, session.status)
        assertFalse(session.completedSets.any { it.exerciseId in workout.optionalExerciseIds })
        assertEquals(20_000L, session.finishedAtMillis)
    }

    @Test
    fun completedSessionCannotBeAccidentallyStartedAgain() {
        val workout = catalog.workouts.first()
        val complete = session(workout.id, catalog.sourceJson).copy(status = StrengthSessionProgress.Status.COMPLETED)
        assertTrue(runCatching { progressor.startSet(complete, catalog, 1L) }.isFailure)
    }

    @Test
    fun finishingEveryConfiguredSetCompletesExactlyOneWorkoutSession() {
        val workout = catalog.workouts.first()
        var session = session(workout.id, catalog.sourceJson)
        var nowMillis = 1_000L
        workout.exerciseIds.forEach { exerciseId ->
            repeat(workout.rounds) {
                session = session.copy(currentExerciseId = exerciseId, pauseUntilMillis = null)
                session = progressor.startSet(session, catalog, nowMillis)
                nowMillis = maxOf(nowMillis + 1_000L, session.pauseUntilMillis ?: nowMillis)
                session = progressor.completeSet(session, catalog, nowMillis)
                nowMillis += 1_000L
                session = session.copy(pauseUntilMillis = null)
            }
        }
        assertEquals(StrengthSessionProgress.Status.COMPLETED, session.status)
        assertEquals(workout.exerciseIds.size * workout.rounds, session.completedSets.size)
        assertEquals(null, session.currentExerciseId)
    }

    private fun session(workoutId: String, snapshot: String) = StrengthSessionProgress(
        sessionId = "session-1",
        workoutId = workoutId,
        catalogSnapshot = snapshot,
        status = StrengthSessionProgress.Status.IN_PROGRESS,
        currentExerciseId = catalog.workouts.first { it.id == workoutId }.exerciseIds.first(),
        currentRound = 1,
        completedSets = emptyList(),
        isCurrentSetStarted = false,
        pauseUntilMillis = null,
        startedAtMillis = 0L,
        finishedAtMillis = null
    )
}
