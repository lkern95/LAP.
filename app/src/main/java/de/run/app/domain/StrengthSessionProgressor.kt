package de.run.app.domain

import de.run.app.domain.model.StrengthCatalog
import de.run.app.domain.model.StrengthSessionProgress
import de.run.app.domain.model.StrengthSetProgress

class StrengthSessionProgressor {
    fun startSet(session: StrengthSessionProgress, catalog: StrengthCatalog, nowMillis: Long): StrengthSessionProgress {
        require(session.status == StrengthSessionProgress.Status.IN_PROGRESS)
        val exercise = currentExercise(session, catalog)
        require(!session.isCurrentSetStarted && session.pauseUntilMillis == null)
        return session.copy(
            isCurrentSetStarted = true,
            pauseUntilMillis = exercise.prescription.holdSeconds?.let { nowMillis + it * 1_000L }
        )
    }

    fun completeSet(session: StrengthSessionProgress, catalog: StrengthCatalog, nowMillis: Long): StrengthSessionProgress {
        require(session.status == StrengthSessionProgress.Status.IN_PROGRESS && session.isCurrentSetStarted)
        val workout = catalog.workouts.first { it.id == session.workoutId }
        val exercise = currentExercise(session, catalog)
        exercise.prescription.holdSeconds?.let { holdSeconds ->
            require(session.pauseUntilMillis != null && session.pauseUntilMillis <= nowMillis) {
                "Die Haltezeit ist noch nicht abgeschlossen."
            }
        }
        val set = StrengthSetProgress(exercise.id, session.currentRound, nowMillis)
        require(session.completedSets.none { it.exerciseId == set.exerciseId && it.round == set.round })
        val completed = session.completedSets + set
        val nextRound = session.currentRound < workout.rounds
        val currentIndex = workout.exerciseIds.indexOf(session.currentExerciseId)
        require(currentIndex in workout.exerciseIds.indices)
        val nextExercise = !nextRound && currentIndex < workout.exerciseIds.lastIndex
        if (!nextRound && !nextExercise) {
            return session.copy(
                completedSets = completed,
                currentExerciseId = null,
                isCurrentSetStarted = false,
                pauseUntilMillis = null,
                status = StrengthSessionProgress.Status.COMPLETED,
                finishedAtMillis = nowMillis
            )
        }
        val restSeconds = if (nextRound) {
            maxOf(exercise.prescription.restSeconds, workout.restBetweenRoundsSeconds)
        } else maxOf(exercise.prescription.restSeconds, workout.restBetweenExercisesSeconds)
        return session.copy(
            completedSets = completed,
            currentExerciseId = if (nextRound) session.currentExerciseId else workout.exerciseIds[currentIndex + 1],
            currentRound = if (nextRound) session.currentRound + 1 else 1,
            isCurrentSetStarted = false,
            pauseUntilMillis = nowMillis.takeIf { restSeconds > 0 }?.plus(restSeconds * 1_000L)
        )
    }

    fun skipOptionalExercise(session: StrengthSessionProgress, catalog: StrengthCatalog, nowMillis: Long): StrengthSessionProgress {
        val workout = catalog.workouts.first { it.id == session.workoutId }
        val id = session.currentExerciseId ?: return session
        require(id in workout.optionalExerciseIds)
        val nextIndex = workout.exerciseIds.indexOf(id) + 1
        return if (nextIndex >= workout.exerciseIds.size) {
            val required = workout.exerciseIds.filterNot(workout.optionalExerciseIds::contains)
            require(required.all { requiredId -> session.completedSets.count { it.exerciseId == requiredId } >= workout.rounds })
            session.copy(currentExerciseId = null, status = StrengthSessionProgress.Status.COMPLETED, finishedAtMillis = nowMillis, pauseUntilMillis = null)
        } else session.copy(currentExerciseId = workout.exerciseIds[nextIndex], currentRound = 1, isCurrentSetStarted = false, pauseUntilMillis = null)
    }

    private fun currentExercise(session: StrengthSessionProgress, catalog: StrengthCatalog) =
        catalog.exercisesById.getValue(requireNotNull(session.currentExerciseId))
}
