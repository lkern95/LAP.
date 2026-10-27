package de.run.app.domain

import de.run.app.domain.model.ReadinessFacts
import de.run.app.domain.model.ReadinessGuidance
import de.run.app.domain.model.ReadinessGuidanceKind

class ReadinessGuidanceAnalyzer {
    fun analyze(
        facts: ReadinessFacts?,
        isLoading: Boolean,
        hasWorkoutAccess: Boolean?,
        workoutsUnavailable: Boolean,
        workoutsFailed: Boolean
    ): ReadinessGuidance {
        val kind = when {
            isLoading -> ReadinessGuidanceKind.LOADING
            hasWorkoutAccess == false -> ReadinessGuidanceKind.ACCESS_NEEDED
            workoutsUnavailable -> ReadinessGuidanceKind.UNAVAILABLE
            workoutsFailed -> ReadinessGuidanceKind.FAILED
            facts?.minutesSinceLastRun == null -> ReadinessGuidanceKind.NO_RUNS
            facts.plannedRunsThisWeek <= 0 -> ReadinessGuidanceKind.NO_PLAN
            facts.runsThisWeek >= facts.plannedRunsThisWeek -> ReadinessGuidanceKind.WEEK_COMPLETE
            else -> ReadinessGuidanceKind.FOLLOW_PLAN
        }
        return ReadinessGuidance(
            kind = kind,
            completedRuns = facts?.runsThisWeek ?: 0,
            plannedRuns = facts?.plannedRunsThisWeek ?: 0
        )
    }
}
