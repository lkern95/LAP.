package de.run.app.domain

import de.run.app.domain.model.Workout
import de.run.app.domain.model.WorkoutActivityType
import java.time.Instant
import java.time.Month
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

data class WorkoutHistoryPeriodSelection(val year: Int, val month: Int)

class WorkoutHistoryPeriodFilter(
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {
    fun years(workouts: List<Workout>): List<Int> = workouts
        .map { Instant.ofEpochMilli(it.startedAtMillis).atZone(zoneId).year }
        .distinct()
        .sortedDescending()

    fun months(workouts: List<Workout>, year: Int): List<Int> = workouts
        .map { Instant.ofEpochMilli(it.startedAtMillis).atZone(zoneId) }
        .filter { it.year == year }
        .map { it.monthValue }
        .distinct()
        .sortedDescending()

    fun latestSelection(workouts: List<Workout>): WorkoutHistoryPeriodSelection? {
        val year = years(workouts).firstOrNull() ?: return null
        val month = months(workouts, year).firstOrNull() ?: return null
        return WorkoutHistoryPeriodSelection(year, month)
    }

    fun normalize(
        workouts: List<Workout>,
        selection: WorkoutHistoryPeriodSelection?
    ): WorkoutHistoryPeriodSelection? {
        val availableYears = years(workouts)
        val year = selection?.year?.takeIf(availableYears::contains) ?: availableYears.firstOrNull() ?: return null
        val availableMonths = months(workouts, year)
        val month = selection?.month?.takeIf(availableMonths::contains) ?: availableMonths.firstOrNull() ?: return null
        return WorkoutHistoryPeriodSelection(year, month)
    }

    fun filter(
        workouts: List<Workout>,
        selection: WorkoutHistoryPeriodSelection?,
        activityType: WorkoutActivityType?
    ): List<Workout> {
        val period = normalize(workouts, selection) ?: return emptyList()
        return workouts.filter { workout ->
            val date = Instant.ofEpochMilli(workout.startedAtMillis).atZone(zoneId)
            date.year == period.year && date.monthValue == period.month &&
                (activityType == null || workout.activityType == activityType)
        }.sortedByDescending(Workout::startedAtMillis)
    }

    fun monthName(month: Int): String = Month.of(month)
        .getDisplayName(TextStyle.FULL, Locale.GERMAN)
        .replaceFirstChar { it.titlecase(Locale.GERMAN) }
}
