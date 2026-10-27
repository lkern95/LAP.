package de.run.app.domain

import de.run.app.domain.model.PlannedRun
import de.run.app.domain.model.TrainingType
import de.run.app.domain.model.Workout
import de.run.app.domain.model.isRunning
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Limits a user-requested extension to the plan's current progression and day limit. */
class ManualRunDurationLimit {
    fun maximumMinutes(
        run: PlannedRun,
        workouts: List<Workout>,
        targetDate: LocalDate,
        dayLimitMinutes: Int,
        zone: ZoneId = ZoneId.systemDefault()
    ): ManualRunDurationLimitResult {
        val dayLimit = dayLimitMinutes.coerceIn(MIN_SESSION_MINUTES, MAX_SESSION_MINUTES)
        if (run.trainingType == TrainingType.RECOVERY) return ManualRunDurationLimitResult(
            minOf(run.totalMinutes, dayLimit),
            "Diese Einheit ist als Erholungslauf geplant und wird nicht verlängert."
        )

        val latestVersions = workouts.filter(Workout::isRunning)
            .groupBy(Workout::sourceId)
            .values.map { versions -> versions.maxBy(Workout::endedAtMillis) }
        val recent = latestVersions.filter { workout ->
            val date = Instant.ofEpochMilli(workout.startedAtMillis).atZone(zone).toLocalDate()
            date >= targetDate.minusDays(30) && date < targetDate && workout.durationSeconds > 0
        }
        if (recent.isEmpty()) return ManualRunDurationLimitResult(
            minOf(run.totalMinutes, dayLimit),
            "Für eine Verlängerung fehlt eine tatsächliche Laufbelastung aus den vorherigen 30 Tagen."
        )

        val longestActualRun = recent.maxWithOrNull(
            compareBy<Workout> { it.distanceMeters?.takeIf(Double::isFinite) ?: 0.0 }
                .thenBy(Workout::durationSeconds)
        ) ?: return ManualRunDurationLimitResult(minOf(run.totalMinutes, dayLimit), null)
        val recentDurationMinutes = (longestActualRun.durationSeconds / 60).toInt()
        val progressionCeiling = maxOf(run.totalMinutes, recentDurationMinutes + TrainingProgressionPolicy.LONG_RUN_STEP_MINUTES)
        val maximum = minOf(progressionCeiling, dayLimit)
        val reason = when {
            maximum < dayLimit && progressionCeiling <= run.totalMinutes ->
                "Dein längster gemessener Lauf der letzten 30 Tage dauerte $recentDurationMinutes Minuten."
            maximum < dayLimit ->
                "Dein längster gemessener Lauf der letzten 30 Tage dauerte $recentDurationMinutes Minuten; LAP erlaubt den bestehenden Aufbau in Zehn-Minuten-Schritten."
            else -> null
        }
        return ManualRunDurationLimitResult(maximum, reason)
    }

    private companion object {
        const val MIN_SESSION_MINUTES = 30
        const val MAX_SESSION_MINUTES = 180
    }
}

data class ManualRunDurationLimitResult(val maximumMinutes: Int, val reason: String?)

internal object TrainingProgressionPolicy {
    const val LONG_RUN_STEP_MINUTES = 10
}
