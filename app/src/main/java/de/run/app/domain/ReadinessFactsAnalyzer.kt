package de.run.app.domain

import de.run.app.domain.model.ReadinessFacts
import de.run.app.domain.model.TrainingProfile
import de.run.app.domain.model.Workout
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

class ReadinessFactsAnalyzer {
    fun analyze(
        workouts: List<Workout>,
        profile: TrainingProfile?,
        now: Instant,
        today: LocalDate = now.atZone(ZoneId.systemDefault()).toLocalDate()
    ): ReadinessFacts {
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val thisWeek = workouts.filter { workout ->
            Instant.ofEpochMilli(workout.startedAtMillis)
                .atZone(ZoneId.systemDefault()).toLocalDate() >= weekStart
        }
        val lastRun = workouts.maxOfOrNull(Workout::endedAtMillis)?.let(Instant::ofEpochMilli)
        val minutesSinceLastRun = lastRun?.let { DurationMinutes.between(it, now) }
        return ReadinessFacts(
            minutesSinceLastRun = minutesSinceLastRun,
            runsThisWeek = thisWeek.size,
            plannedRunsThisWeek = 2 + if (profile?.optionalRun == true) 1 else 0,
            distanceThisWeekMeters = thisWeek.mapNotNull(Workout::distanceMeters).sum()
        )
    }
}

private object DurationMinutes {
    fun between(start: Instant, end: Instant): Long =
        java.time.Duration.between(start, end).toMinutes().coerceAtLeast(0)
}
