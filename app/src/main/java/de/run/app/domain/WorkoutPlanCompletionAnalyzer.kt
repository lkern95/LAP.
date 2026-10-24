package de.run.app.domain

import de.run.app.domain.model.PlannedRunMatchType
import de.run.app.domain.model.TrainingGoal
import de.run.app.domain.model.TrainingProfile
import de.run.app.domain.model.TrainingWeek
import de.run.app.domain.model.Workout
import de.run.app.domain.model.isRunning
import java.time.Instant
import java.time.ZoneId

/** Counts scheduled matches only; an extra run can add load but cannot complete a planned run. */
class WorkoutPlanCompletionAnalyzer(
    private val matcher: PlannedWorkoutMatcher = PlannedWorkoutMatcher()
) {
    fun completedRuns(
        workouts: List<Workout>,
        week: TrainingWeek,
        profile: TrainingProfile,
        goal: TrainingGoal?,
        zone: ZoneId = ZoneId.systemDefault()
    ): Int {
        val endDate = week.startsOn.plusDays(6)
        val inWeek = workouts.filter { workout ->
            if (!workout.isRunning()) return@filter false
            val date = Instant.ofEpochMilli(workout.startedAtMillis).atZone(zone).toLocalDate()
            !date.isBefore(week.startsOn) && !date.isAfter(endDate)
        }.sortedBy(Workout::startedAtMillis)
        val usedTrainingIds = mutableSetOf<String>()
        val processed = mutableListOf<Workout>()
        inWeek.forEach { workout ->
            val match = workout.planMatch ?: matcher.match(workout, workoutsBefore(workouts, workout), profile, goal, zone)
            val id = match.trainingId
            if (match.type == PlannedRunMatchType.SCHEDULED && match.scheduledDate?.let { it >= week.startsOn && it <= endDate } == true &&
                id != null && usedTrainingIds.add(id)
            ) processed += workout
        }
        return processed.size.coerceAtMost(week.requiredRunCount)
    }

    private fun workoutsBefore(workouts: List<Workout>, workout: Workout): List<Workout> = workouts.filter {
        it.sourceId != workout.sourceId && it.endedAtMillis <= workout.startedAtMillis
    }
}
