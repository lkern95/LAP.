package de.run.app.domain

import de.run.app.domain.model.TrainingProgress
import de.run.app.domain.model.WeeklyRunVolume
import de.run.app.domain.model.Workout
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

class TrainingProgressAnalyzer {
    fun analyze(workouts: List<Workout>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): TrainingProgress {
        val datedRuns = workouts.groupBy(Workout::sourceId).values.map { versions ->
            versions.maxBy { it.endedAtMillis }
        }.map { workout ->
            val date = Instant.ofEpochMilli(workout.startedAtMillis).atZone(zone).toLocalDate()
            date to workout
        }.filter { (date, _) -> !date.isAfter(today) }
        val fourWeekStart = today.minusDays(27)
        val fourWeekRuns = datedRuns.filter { (date, _) -> !date.isBefore(fourWeekStart) }
        val sixteenWeekStart = today.minusWeeks(16)
        val sixteenWeekRuns = datedRuns.filter { (date, _) -> !date.isBefore(sixteenWeekStart) }
        val previousWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)
        val currentWeekStart = previousWeekStart.plusWeeks(1)
        val previousWeekRuns = datedRuns.count { (date, _) ->
            !date.isBefore(previousWeekStart) && date.isBefore(currentWeekStart)
        }
        val weeklyVolumes = (3 downTo 0).map { weeksAgo ->
            val weekStart = currentWeekStart.minusWeeks(weeksAgo.toLong())
            val runs = datedRuns.filter { (date, _) ->
                !date.isBefore(weekStart) && date.isBefore(weekStart.plusWeeks(1))
            }
            WeeklyRunVolume(
                weekStartsOn = weekStart,
                runCount = runs.size,
                minutes = runs.sumOf { (_, workout) -> (workout.durationSeconds / 60).toInt() }
            )
        }
        return TrainingProgress(
            runsLastFourWeeks = fourWeekRuns.size,
            minutesLastFourWeeks = fourWeekRuns.sumOf { (_, workout) -> (workout.durationSeconds / 60).toInt() },
            distanceMetersLastFourWeeks = fourWeekRuns.sumOf { (_, workout) -> workout.distanceMeters ?: 0.0 },
            longestRunMinutesLastSixteenWeeks = sixteenWeekRuns.maxOfOrNull { (_, workout) ->
                (workout.durationSeconds / 60).toInt()
            } ?: 0,
            runsPreviousWeek = previousWeekRuns,
            lastRunDate = datedRuns.maxOfOrNull { (date, _) -> date },
            weeklyRunVolumes = weeklyVolumes
        )
    }
}
