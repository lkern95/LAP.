package de.run.app.domain

import de.run.app.domain.model.PlannedRun
import de.run.app.domain.model.RunStep

class TrainingRunDurationAdjuster {
    fun fitToAvailableTime(run: PlannedRun, availableMinutes: Int): PlannedRun? {
        if (availableMinutes !in MINIMUM_SESSION_MINUTES..MAXIMUM_SESSION_MINUTES) return null
        if (run.totalMinutes == availableMinutes) return run

        if (availableMinutes > run.totalMinutes) {
            val lastRunningStep = run.steps.indexOfLast(::isRunningStep)
            if (lastRunningStep < 0) return null
            val steps = run.steps.toMutableList()
            val step = steps[lastRunningStep]
            steps[lastRunningStep] = step.copy(minutes = step.minutes + availableMinutes - run.totalMinutes)
            return run.copy(steps = steps)
        }

        val fixedMinutes = run.steps.filterNot(::isRunningStep).sumOf(RunStep::minutes)
        if (fixedMinutes >= availableMinutes) return null
        var runningMinutesLeft = availableMinutes - fixedMinutes
        var foundRunningStep = false
        val fittedSteps = run.steps.mapNotNull { step ->
            if (!isRunningStep(step)) return@mapNotNull step
            foundRunningStep = true
            val minutes = step.minutes.coerceAtMost(runningMinutesLeft)
            runningMinutesLeft -= minutes
            step.copy(
                minutes = minutes,
                name = updateContinuousTarget(step.name, minutes)
            ).takeIf { minutes > 0 }
        }
        return run.copy(steps = fittedSteps).takeIf { foundRunningStep && it.totalMinutes <= availableMinutes }
    }

    private fun updateContinuousTarget(name: String, minutes: Int): String =
        CONTINUOUS_TARGET.replace(name, "Ziel: $minutes Min. am Stück")

    private fun isRunningStep(step: RunStep): Boolean = step.name.contains("laufen", ignoreCase = true)

    private companion object {
        const val MINIMUM_SESSION_MINUTES = 20
        const val MAXIMUM_SESSION_MINUTES = 180
        val CONTINUOUS_TARGET = Regex("Ziel: \\d+ Min\\. am Stück")
    }
}
