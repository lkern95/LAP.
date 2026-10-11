package de.run.app.domain

import de.run.app.domain.model.PlannedRun
import de.run.app.domain.model.RunStep

class TrainingRunDurationAdjuster {
    fun fitToAvailableTime(run: PlannedRun, availableMinutes: Int): PlannedRun? {
        if (availableMinutes !in MINIMUM_SESSION_MINUTES..MAXIMUM_SESSION_MINUTES) return null
        if (run.totalMinutes <= availableMinutes) return run

        val fixedMinutes = run.steps.filterNot(::isRunningStep).sumOf(RunStep::minutes)
        if (fixedMinutes >= availableMinutes) return null
        var runningMinutesLeft = availableMinutes - fixedMinutes
        val fittedSteps = run.steps.mapNotNull { step ->
            if (!isRunningStep(step)) return@mapNotNull step
            val minutes = step.minutes.coerceAtMost(runningMinutesLeft)
            runningMinutesLeft -= minutes
            step.copy(minutes = minutes).takeIf { minutes > 0 }
        }
        return run.copy(steps = fittedSteps).takeIf { it.totalMinutes <= availableMinutes }
    }

    private fun isRunningStep(step: RunStep): Boolean = step.name.contains("laufen", ignoreCase = true)

    private companion object {
        const val MINIMUM_SESSION_MINUTES = 30
        const val MAXIMUM_SESSION_MINUTES = 180
    }
}
