package de.run.app.domain

import de.run.app.domain.model.TrainingDecisionSnapshot
import de.run.app.domain.model.TrainingGoal
import de.run.app.domain.model.TrainingPlan
import de.run.app.domain.model.TrainingProfile
import de.run.app.domain.model.Workout
import java.security.MessageDigest
import java.time.LocalDate

class TrainingDecisionSnapshotFactory {
    fun create(
        profile: TrainingProfile,
        goal: TrainingGoal?,
        workouts: List<Workout>,
        plan: TrainingPlan,
        today: LocalDate
    ): TrainingDecisionSnapshot {
        val distinctRuns = workouts.groupBy(Workout::sourceId).values.map { versions ->
            versions.maxWith(compareBy<Workout> { it.endedAtMillis }.thenBy { it.feedback?.recordedAtMillis ?: 0L })
        }.sortedBy(Workout::sourceId)
        val firstRun = plan.weeks.firstOrNull()?.runs?.firstOrNull()
        return TrainingDecisionSnapshot(
            ruleVersion = RULE_VERSION,
            inputRevision = digest(inputDescription(profile, goal, distinctRuns, today)),
            targetRevision = goal?.let { digest(goalDescription(it)) },
            evaluatedOn = today,
            runCount = distinctRuns.size,
            latestRunEndedAtMillis = distinctRuns.maxOfOrNull(Workout::endedAtMillis),
            recommendationTitle = firstRun?.title,
            recommendationMinutes = firstRun?.totalMinutes,
            reasonCode = plan.adjustment.name
        )
    }

    private fun inputDescription(
        profile: TrainingProfile,
        goal: TrainingGoal?,
        workouts: List<Workout>,
        today: LocalDate
    ): String = buildString {
        append("rule=").append(RULE_VERSION).append('|').append(today).append('|')
        append(profile.longestComfortableRunMinutes).append('|').append(profile.optionalRun).append('|')
        append(profile.strengthSessionsPerWeek).append('|').append(profile.availableRunDays.sortedBy { it.value })
        append('|').append(profile.longRunDay?.value).append('|').append(profile.availabilityMode)
        append('|').append(profile.runTimeLimitsByDay.toSortedMap(compareBy { it.value }))
        append('|').append(profile.usualRunTimeLimitMinutes).append('|').append(profile.occasionalRunTimeLimitMinutes)
        append('|').append(profile.considerNightShiftRecovery).append('|').append(profile.planMode)
        profile.datedAvailability.toSortedMap().forEach { (date, availability) ->
            append('|').append(date).append(':').append(availability.status).append(':')
            append(availability.maximumMinutes).append(':').append(availability.afterNightShift)
        }
        append('|').append(goal?.let(::goalDescription))
        workouts.forEach { workout ->
            append('\n').append(workout.sourceId).append('|').append(workout.startedAtMillis).append('|')
            append(workout.endedAtMillis).append('|').append(workout.distanceMeters).append('|')
            append(workout.averageHeartRateBpm).append('|').append(workout.maximumHeartRateBpm).append('|')
            append(workout.feedback?.perceivedEffort).append('|')
            append(workout.feedback?.runningAffectedBySymptoms).append('|')
            append(workout.feedback?.recordedAtMillis)
        }
    }

    private fun goalDescription(goal: TrainingGoal): String = listOf(
        goal.id,
        goal.raceDate,
        goal.raceDistanceMeters,
        goal.raceGoal,
        goal.targetTimeMinutes,
        goal.status,
        goal.competitionCompleted,
        goal.goalAchieved,
        goal.competitionWorkoutId,
        goal.pendingCompetitionWorkoutId,
        goal.lastPromptedWorkoutId
    ).joinToString("|")

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val RULE_VERSION = 1
    }
}
