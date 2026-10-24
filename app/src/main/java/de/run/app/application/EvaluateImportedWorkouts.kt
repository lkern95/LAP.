package de.run.app.application

import de.run.app.domain.PlannedWorkoutMatcher
import de.run.app.domain.WorkoutPlanAssessmentAnalyzer
import de.run.app.domain.model.TrainingGoalStatus
import de.run.app.domain.model.Workout
import de.run.app.domain.model.isRunning
import de.run.app.domain.repository.TrainingGoalRepository
import de.run.app.domain.repository.TrainingProfileRepository
import de.run.app.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Saves a single, stable plan assessment for each newly imported running activity. */
class EvaluateImportedWorkouts(
    private val workouts: WorkoutRepository,
    private val profiles: TrainingProfileRepository,
    private val goals: TrainingGoalRepository,
    private val matcher: PlannedWorkoutMatcher = PlannedWorkoutMatcher(),
    private val assessmentAnalyzer: WorkoutPlanAssessmentAnalyzer = WorkoutPlanAssessmentAnalyzer()
) {
    private val mutex = Mutex()

    suspend operator fun invoke(imported: List<Workout>, prior: List<Workout>): List<Workout> = mutex.withLock {
        val profile = profiles.observe().first() ?: return@withLock emptyList()
        val goal = goals.observeHistory().first().firstOrNull { it.status == TrainingGoalStatus.ACTIVE }
        val knownIds = prior.mapTo(mutableSetOf(), Workout::sourceId)
        val candidates = imported.filter { it.isRunning() && it.sourceId !in knownIds }
        val evaluated = mutableListOf<Workout>()
        var history = prior
        candidates.sortedBy(Workout::startedAtMillis).forEach { workout ->
            val stored = workouts.observeRecent().first().firstOrNull { it.sourceId == workout.sourceId }
            if (stored?.planMatch != null) {
                evaluated += stored
                history = history + stored
                return@forEach
            }
            val match = workout.planMatch ?: matcher.match(workout, history, profile, goal)
            val evaluatedMatch = match.copy(assessment = assessmentAnalyzer.assess(workout, match))
            workouts.savePlanMatch(workout.sourceId, evaluatedMatch)
            val updated = workout.copy(planMatch = evaluatedMatch)
            evaluated += updated
            history = history + updated
        }
        evaluated
    }
}
