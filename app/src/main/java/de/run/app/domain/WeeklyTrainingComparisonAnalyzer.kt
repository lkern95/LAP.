package de.run.app.domain

import de.run.app.domain.model.Workout
import de.run.app.domain.model.isRunning
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

data class TrainingWeekStats(
    val startsOn: LocalDate,
    val runCount: Int,
    val distanceMeters: Double?,
    val durationSeconds: Long,
    val comparablePaceSecondsPerKilometer: Long?,
    val averageHeartRateBpm: Int?,
    val comparablePaceRunCount: Int = 0,
    val heartRateRunCount: Int = 0
)

data class WeeklyTrainingComparison(
    val current: TrainingWeekStats,
    val previous: TrainingWeekStats,
    val distanceChangePercent: Int?,
    val feedback: String
)

class WeeklyTrainingComparisonAnalyzer {
    fun analyze(
        workouts: List<Workout>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): WeeklyTrainingComparison {
        val thisWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val currentStart = thisWeekStart.minusWeeks(1)
        val previousStart = currentStart.minusWeeks(1)
        val distinctRuns = workouts.filter(Workout::isRunning).distinctBy(Workout::sourceId)
        val previous = summarize(distinctRuns, previousStart, zone)
        val current = summarize(distinctRuns, currentStart, zone)
        val change = distanceChange(current, previous)
        return WeeklyTrainingComparison(current, previous, change, feedback(current, previous, change))
    }

    private fun summarize(workouts: List<Workout>, start: LocalDate, zone: ZoneId): TrainingWeekStats {
        val end = start.plusWeeks(1)
        val runs = workouts.filter { workout ->
            val date = Instant.ofEpochMilli(workout.startedAtMillis).atZone(zone).toLocalDate()
            !date.isBefore(start) && date.isBefore(end)
        }
        val measured = runs.filter { it.distanceMeters?.let { distance -> distance > 0 && distance.isFinite() } == true }
        val comparable = comparableRuns(measured)
        val completeDistance = runs.takeIf { it.isNotEmpty() && it.all { run -> run.distanceMeters?.let { distance -> distance > 0 && distance.isFinite() } == true } }
            ?.sumOf { it.distanceMeters!! }
        val comparablePace = comparable.takeIf { it.isNotEmpty() }
            ?.let { (it.sumOf(Workout::durationSeconds) * 1_000.0 / it.sumOf { run -> run.distanceMeters!! }).toLong() }
        val heartRates = comparable.mapNotNull(Workout::averageHeartRateBpm)
        return TrainingWeekStats(
            startsOn = start,
            runCount = runs.size,
            distanceMeters = completeDistance,
            durationSeconds = runs.sumOf(Workout::durationSeconds),
            comparablePaceSecondsPerKilometer = comparablePace,
            averageHeartRateBpm = heartRates.takeIf { it.isNotEmpty() }?.average()?.toInt(),
            comparablePaceRunCount = comparable.size,
            heartRateRunCount = heartRates.size
        )
    }

    private fun comparableRuns(runs: List<Workout>): List<Workout> {
        if (runs.isEmpty()) return emptyList()
        val distances = runs.mapNotNull(Workout::distanceMeters).sorted()
        val reference = distances[distances.size / 2]
        return runs.filter { run ->
            val distance = run.distanceMeters ?: return@filter false
            abs(distance - reference) / reference <= COMPARABLE_DISTANCE_TOLERANCE
        }
    }

    private fun distanceChange(current: TrainingWeekStats, previous: TrainingWeekStats): Int? {
        val previousDistance = previous.distanceMeters?.takeIf { it > 0.0 } ?: return null
        val currentDistance = current.distanceMeters ?: return null
        return ((currentDistance - previousDistance) / previousDistance * 100).toInt()
    }

    private fun feedback(
        current: TrainingWeekStats,
        previous: TrainingWeekStats,
        distanceChangePercent: Int?
    ): String = when {
        current.runCount == 0 -> "In der letzten vollständigen Woche ist kein Lauf gespeichert. Prüfe die Synchronisierung, bevor du den Umfang erhöhst."
        distanceChangePercent != null && distanceChangePercent > MAX_WEEKLY_DISTANCE_INCREASE_PERCENT ->
            "Der gespeicherte Wochenumfang stieg um $distanceChangePercent %. Halte ihn zunächst stabil und erhöhe nicht gleichzeitig das Tempo."
        current.runCount >= 2 && current.distanceMeters != null -> {
            val kilometers = String.format(java.util.Locale.GERMANY, "%.1f", current.distanceMeters / 1_000.0)
            "In der letzten Woche sind ${current.runCount} Läufe mit $kilometers km gespeichert. Steigere Umfang und Tempo nicht gleichzeitig."
        }
        previous.runCount == 0 -> "Für die vorherige Woche sind keine Läufe gespeichert. Ein Vergleich der Entwicklung ist noch nicht möglich."
        else -> "Die letzten beiden vollständigen Wochen sind erfasst. Orientiere dich für die nächste Einheit an deinem Wochenplan."
    }

    private companion object {
        const val COMPARABLE_DISTANCE_TOLERANCE = 0.20
        const val MAX_WEEKLY_DISTANCE_INCREASE_PERCENT = 20
    }
}
