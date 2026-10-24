package de.run.app.domain

import de.run.app.domain.model.TrainingGoal
import de.run.app.domain.model.TrainingGoalStatus
import de.run.app.domain.model.Workout
import de.run.app.domain.model.isRunning
import java.time.Instant
import java.time.ZoneId

class TrainingGoalCandidateSelector {
    fun select(goal: TrainingGoal?, workouts: List<Workout>): Workout? {
        goal ?: return null
        if (goal.status != TrainingGoalStatus.ACTIVE) return null

        goal.pendingCompetitionWorkoutId?.let { pendingId ->
            workouts.firstOrNull { it.sourceId == pendingId }?.let { return it }
        }

        return workouts.asSequence()
            .filter(Workout::isRunning)
            .filter { it.sourceId != goal.lastPromptedWorkoutId }
            .filter { workout ->
                Instant.ofEpochMilli(workout.startedAtMillis).atZone(ZoneId.systemDefault()).toLocalDate() == goal.raceDate
            }
            .filter { workout ->
                val distance = workout.distanceMeters?.takeIf { it.isFinite() && it > 0.0 } ?: return@filter false
                distance >= goal.raceDistanceMeters * MINIMUM_RACE_DISTANCE_RATIO
            }
            .maxByOrNull(Workout::startedAtMillis)
    }

    private companion object {
        const val MINIMUM_RACE_DISTANCE_RATIO = 0.9
    }
}
