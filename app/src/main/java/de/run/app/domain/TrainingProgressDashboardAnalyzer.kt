package de.run.app.domain

import de.run.app.domain.model.Workout
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class ProgressPeriod(val label: String) {
    WEEK("Woche"),
    MONTH("Monat"),
    TOTAL("Gesamt")
}

data class ProgressDistanceBucket(
    val startsOn: LocalDate,
    val label: String,
    val distanceMeters: Double,
    val runCount: Int
)

data class TrainingProgressDashboard(
    val period: ProgressPeriod,
    val distanceMeters: Double,
    val runCount: Int,
    val previousDistanceMeters: Double?,
    val chartBuckets: List<ProgressDistanceBucket>,
    val longestRecentRunMeters: Double?,
    val averagePaceSecondsPerKilometer: Long?,
    val weeklyDistanceTrendMeters: List<Double>,
    val weeklyLongestRunMeters: List<Double>,
    val weeklyAveragePaceSecondsPerKilometer: List<Long?>
)

class TrainingProgressDashboardAnalyzer {
    fun analyze(
        workouts: List<Workout>,
        period: ProgressPeriod,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): TrainingProgressDashboard {
        val dated = workouts.map { workout ->
            workout to java.time.Instant.ofEpochMilli(workout.startedAtMillis).atZone(zone).toLocalDate()
        }.filter { (_, date) -> !date.isAfter(today) }
        val currentStart = periodStart(period, dated, today)
        val currentRuns = dated.filter { (_, date) -> !date.isBefore(currentStart) }
        val previousDistance = previousRange(period, currentStart, today)?.let { (start, end) ->
            sumDistance(dated.filter { (_, date) -> !date.isBefore(start) && date.isBefore(end) })
        }
        val trend = weeklyTrend(dated, today)
        val recentRuns = dated.filter { (_, date) -> !date.isBefore(today.minusDays(83)) }
        val distanceRuns = currentRuns.mapNotNull { (workout, _) ->
            workout.distanceMeters?.takeIf { it.isFinite() && it > 0.0 }?.let { workout to it }
        }
        val totalMeters = distanceRuns.sumOf { it.second }
        val totalSeconds = distanceRuns.sumOf { (workout, _) -> workout.durationSeconds }

        return TrainingProgressDashboard(
            period = period,
            distanceMeters = totalMeters,
            runCount = currentRuns.size,
            previousDistanceMeters = previousDistance,
            chartBuckets = buildBuckets(dated, period, currentStart, today),
            longestRecentRunMeters = recentRuns.mapNotNull { (workout, _) ->
                workout.distanceMeters?.takeIf { it.isFinite() && it > 0.0 }
            }.maxOrNull(),
            averagePaceSecondsPerKilometer = if (totalMeters > 0.0 && totalSeconds > 0L) {
                (totalSeconds * 1_000.0 / totalMeters).toLong()
            } else null,
            weeklyDistanceTrendMeters = trend.first,
            weeklyLongestRunMeters = trend.second,
            weeklyAveragePaceSecondsPerKilometer = trend.third
        )
    }

    private fun periodStart(
        period: ProgressPeriod,
        dated: List<Pair<Workout, LocalDate>>,
        today: LocalDate
    ): LocalDate = when (period) {
        ProgressPeriod.WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        ProgressPeriod.MONTH -> today.withDayOfMonth(1)
        ProgressPeriod.TOTAL -> dated.minOfOrNull { it.second } ?: today.withDayOfMonth(1)
    }

    private fun previousRange(
        period: ProgressPeriod,
        currentStart: LocalDate,
        today: LocalDate
    ): Pair<LocalDate, LocalDate>? = when (period) {
        ProgressPeriod.WEEK -> {
            val days = ChronoUnit.DAYS.between(currentStart, today).toInt() + 1
            val start = currentStart.minusWeeks(1)
            start to start.plusDays(days.toLong())
        }
        ProgressPeriod.MONTH -> {
            val previousMonth = YearMonth.from(currentStart).minusMonths(1)
            val days = minOf(today.dayOfMonth, previousMonth.lengthOfMonth())
            previousMonth.atDay(1) to previousMonth.atDay(days).plusDays(1)
        }
        ProgressPeriod.TOTAL -> null
    }

    private fun buildBuckets(
        dated: List<Pair<Workout, LocalDate>>,
        period: ProgressPeriod,
        start: LocalDate,
        today: LocalDate
    ): List<ProgressDistanceBucket> = when (period) {
        ProgressPeriod.WEEK -> (0..6).map { offset ->
            val day = start.plusDays(offset.toLong())
            bucket(dated, day, day.plusDays(1), day.format(DAY_LABEL))
        }
        ProgressPeriod.MONTH -> (1..YearMonth.from(today).lengthOfMonth()).map { dayOfMonth ->
            val day = today.withDayOfMonth(1).plusDays((dayOfMonth - 1).toLong())
            bucket(dated, day, day.plusDays(1), day.format(DATE_LABEL))
        }
        ProgressPeriod.TOTAL -> {
            val firstVisibleMonth = YearMonth.from(today).minusMonths(11)
            val monthCount = ChronoUnit.MONTHS.between(firstVisibleMonth, YearMonth.from(today)).toInt()
            (0..monthCount).map { offset ->
                val month = firstVisibleMonth.plusMonths(offset.toLong())
                bucket(
                    dated,
                    month.atDay(1),
                    month.plusMonths(1).atDay(1),
                    month.atDay(1).format(MONTH_LABEL)
                )
            }
        }
    }

    private fun bucket(
        dated: List<Pair<Workout, LocalDate>>,
        start: LocalDate,
        end: LocalDate,
        label: String
    ): ProgressDistanceBucket {
        val runs = dated.filter { (_, date) -> !date.isBefore(start) && date.isBefore(end) }
        return ProgressDistanceBucket(start, label, sumDistance(runs), runs.size)
    }

    private fun weeklyTrend(
        dated: List<Pair<Workout, LocalDate>>,
        today: LocalDate
    ): Triple<List<Double>, List<Double>, List<Long?>> {
        val currentWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weeks = (7 downTo 0).map { currentWeek.minusWeeks(it.toLong()) }
        val runs = weeks.map { start ->
            dated.filter { (_, date) -> !date.isBefore(start) && date.isBefore(start.plusWeeks(1)) }
        }
        val distances = runs.map(::sumDistance)
        val longestRuns = runs.map { week ->
            week.mapNotNull { (workout, _) -> workout.distanceMeters?.takeIf { it.isFinite() && it > 0.0 } }
                .maxOrNull() ?: 0.0
        }
        val paces = runs.map { week ->
            val distance = sumDistance(week)
            val seconds = week.sumOf { (workout, _) ->
                if (workout.distanceMeters?.let { it.isFinite() && it > 0.0 } == true) workout.durationSeconds else 0L
            }
            if (distance > 0.0 && seconds > 0L) (seconds * 1_000.0 / distance).toLong() else null
        }
        return Triple(distances, longestRuns, paces)
    }

    private fun sumDistance(runs: List<Pair<Workout, LocalDate>>): Double =
        runs.sumOf { (workout, _) -> workout.distanceMeters?.takeIf { it.isFinite() && it > 0.0 } ?: 0.0 }

    private companion object {
        val DAY_LABEL = DateTimeFormatter.ofPattern("EE", Locale.GERMAN)
        val DATE_LABEL = DateTimeFormatter.ofPattern("d.", Locale.GERMAN)
        val MONTH_LABEL = DateTimeFormatter.ofPattern("LLL", Locale.GERMAN)
    }
}
