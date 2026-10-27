package de.run.app.domain

import de.run.app.domain.model.DailyHealthValue
import de.run.app.domain.model.ReadinessAssessment
import de.run.app.domain.model.ReadinessHealthSnapshot
import de.run.app.domain.model.Workout
import de.run.app.domain.model.isRunning
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

enum class RestingHeartRatePersonalBand {
    BELOW_PERSONAL_RANGE,
    WITHIN_PERSONAL_RANGE,
    ABOVE_PERSONAL_RANGE,
    WELL_ABOVE_PERSONAL_RANGE
}

/** A conservative personal-trend index. It is not a measurement of physiological recovery. */
class ReadinessAssessmentAnalyzer(
    private val zone: ZoneId = ZoneId.systemDefault()
) {
    fun analyze(health: ReadinessHealthSnapshot?, workouts: List<Workout>, now: Instant): ReadinessAssessment {
        if (health == null) return ReadinessAssessment(score = null)
        val today = now.atZone(zone).toLocalDate()
        val sleepValue = health.sleepMinutes.value.takeIf {
            health.sleepMinutes.availability == de.run.app.domain.model.HealthMetricAvailability.AVAILABLE
        }
        val hrvValue = health.hrvLastNightAverageMs.value.takeIf {
            health.hrvLastNightAverageMs.availability == de.run.app.domain.model.HealthMetricAvailability.AVAILABLE
        }
        val pulseValue = health.restingHeartRateBpm.value.takeIf {
            health.restingHeartRateBpm.availability == de.run.app.domain.model.HealthMetricAvailability.AVAILABLE
        }
        val sleep = currentAndBaseline(sleepValue, health.sleepHistory, today) ?: return ReadinessAssessment(null)
        val hrv = currentAndBaseline(hrvValue, health.hrvHistory, today)
        val restingHeartRate = if (hrv == null) currentAndBaseline(pulseValue, health.restingHeartRateHistory, today) else null
        val autonomic = hrv ?: restingHeartRate ?: return ReadinessAssessment(null)
        val training = trainingScore(workouts.filter(Workout::isRunning), now) ?: return ReadinessAssessment(null)

        val sleepScore = (100 + (sleep.first - sleep.second).coerceAtMost(0) / SLEEP_MINUTES_PER_POINT).coerceIn(40, 100)
        val autonomicScore = if (hrv != null) {
            val dropPercent = ((hrv.second - hrv.first).coerceAtLeast(0) * 100.0 / hrv.second)
            (100 - (dropPercent * HRV_POINTS_PER_PERCENT_DROP).roundToInt()).coerceIn(40, 100)
        } else {
            (100 - (autonomic.first - autonomic.second).coerceAtLeast(0) * PULSE_POINTS_PER_BPM).coerceIn(40, 100)
        }
        val score = (
            sleepScore * SLEEP_WEIGHT +
                autonomicScore * RESTING_HEART_RATE_WEIGHT +
                training * TRAINING_LOAD_WEIGHT
            ).roundToInt().coerceIn(0, 100)
        return ReadinessAssessment(
            score = score,
            sleepScore = sleepScore,
            autonomicScore = autonomicScore,
            autonomicSignal = if (hrv != null) "HRV/HFV" else "Ruhepuls",
            trainingLoadScore = training
        )
    }

    fun restingHeartRatePersonalBand(
        current: Int?,
        history: List<DailyHealthValue>,
        today: LocalDate
    ): RestingHeartRatePersonalBand? {
        val comparison = currentAndBaseline(current, history, today) ?: return null
        val difference = comparison.first - comparison.second
        return when {
            difference < -PERSONAL_PULSE_TOLERANCE_BPM -> RestingHeartRatePersonalBand.BELOW_PERSONAL_RANGE
            difference <= PERSONAL_PULSE_TOLERANCE_BPM -> RestingHeartRatePersonalBand.WITHIN_PERSONAL_RANGE
            difference <= PERSONAL_PULSE_CAUTION_BPM -> RestingHeartRatePersonalBand.ABOVE_PERSONAL_RANGE
            else -> RestingHeartRatePersonalBand.WELL_ABOVE_PERSONAL_RANGE
        }
    }

    private fun currentAndBaseline(
        current: Int?,
        history: List<DailyHealthValue>,
        today: LocalDate
    ): Pair<Int, Int>? {
        val currentValue = current?.takeIf { it > 0 } ?: return null
        val baselineValues = history.filter { !it.date.isAfter(today.minusDays(1)) && !it.date.isBefore(today.minusDays(28)) }
            .map(DailyHealthValue::value)
            .filter { it > 0 }
            .sorted()
        if (baselineValues.size < MIN_HEALTH_SAMPLES) return null
        val firstDate = history.filter { it.value > 0 && !it.date.isAfter(today.minusDays(1)) }
            .minOfOrNull(DailyHealthValue::date) ?: return null
        if (firstDate.isAfter(today.minusDays(MIN_HEALTH_SPAN_DAYS))) return null
        return currentValue to median(baselineValues)
    }

    private fun trainingScore(workouts: List<Workout>, now: Instant): Int? {
        val baselineStart = now.minusSeconds(TRAINING_BASELINE_DAYS * SECONDS_PER_DAY)
        val recentStart = now.minusSeconds(RECENT_LOAD_DAYS * SECONDS_PER_DAY)
        val baselineRuns = workouts.filter {
            val end = Instant.ofEpochMilli(it.endedAtMillis)
            !end.isBefore(baselineStart) && end.isBefore(recentStart) && it.durationSeconds > 0
        }
        if (baselineRuns.size < MIN_BASELINE_RUNS) return null
        val earliest = baselineRuns.minOf { Instant.ofEpochMilli(it.endedAtMillis) }
        if (earliest.isAfter(baselineStart.plusSeconds(SECONDS_PER_DAY))) return null

        val averageWeeklyMinutes = baselineRuns.sumOf(Workout::durationSeconds) / 60.0 / 4.0
        if (averageWeeklyMinutes <= 0.0) return null
        val recentMinutes = workouts.filter {
            val end = Instant.ofEpochMilli(it.endedAtMillis)
            !end.isBefore(recentStart) && !end.isAfter(now) && it.durationSeconds > 0
        }.sumOf { it.durationSeconds / 60.0 }
        val ratio = recentMinutes / averageWeeklyMinutes
        val loadPenalty = ((ratio - 1.0).coerceAtLeast(0.0) * LOAD_POINTS_PER_BASELINE_RATIO).roundToInt()
        val latestRun = workouts.maxOfOrNull { it.endedAtMillis }?.let(Instant::ofEpochMilli)
        val elapsedHours = latestRun?.let { (now.epochSecond - it.epochSecond).coerceAtLeast(0) / 3_600.0 }
        val recencyPenalty = when {
            elapsedHours == null -> 0
            elapsedHours < 12 -> 15
            elapsedHours < 24 -> 10
            elapsedHours < 36 -> 5
            else -> 0
        }
        return (100 - loadPenalty - recencyPenalty).coerceIn(40, 100)
    }

    private fun median(values: List<Int>): Int =
        if (values.size % 2 == 1) values[values.size / 2]
        else (values[values.size / 2 - 1] + values[values.size / 2]) / 2

    private companion object {
        const val MIN_HEALTH_SAMPLES = 14
        const val MIN_HEALTH_SPAN_DAYS = 20L
        const val MIN_BASELINE_RUNS = 4
        const val TRAINING_BASELINE_DAYS = 35L
        const val RECENT_LOAD_DAYS = 7L
        const val SECONDS_PER_DAY = 86_400L
        const val SLEEP_MINUTES_PER_POINT = 4
        const val PULSE_POINTS_PER_BPM = 5
        const val PERSONAL_PULSE_TOLERANCE_BPM = 4
        const val PERSONAL_PULSE_CAUTION_BPM = 8
        const val HRV_POINTS_PER_PERCENT_DROP = 1.5
        const val LOAD_POINTS_PER_BASELINE_RATIO = 35.0
        const val SLEEP_WEIGHT = 0.4
        const val RESTING_HEART_RATE_WEIGHT = 0.3
        const val TRAINING_LOAD_WEIGHT = 0.3
    }
}
