package de.run.app.domain.model

data class ReadinessFacts(
    val minutesSinceLastRun: Long?,
    val runsThisWeek: Int,
    val plannedRunsThisWeek: Int,
    val distanceThisWeekMeters: Double
)
