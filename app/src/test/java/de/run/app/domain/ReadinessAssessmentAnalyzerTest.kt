package de.run.app.domain

import de.run.app.domain.model.DailyHealthValue
import de.run.app.domain.model.HealthMetric
import de.run.app.domain.model.HealthMetricAvailability
import de.run.app.domain.model.ReadinessHealthSnapshot
import de.run.app.domain.model.Workout
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadinessAssessmentAnalyzerTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 10, 24)
    private val now = today.atTime(8, 0).toInstant(java.time.ZoneOffset.UTC)
    private val analyzer = ReadinessAssessmentAnalyzer(zone)

    @Test
    fun displaysHighPersonalizedScoreWhenCurrentValuesMatchSufficientHistory() {
        val result = analyzer.analyze(health(sleep = 420, pulse = 55), baselineWorkouts(), now)

        assertTrue(result.score!! >= 95)
        assertEquals(100, result.sleepScore)
        assertEquals(100, result.autonomicScore)
        assertEquals("Ruhepuls", result.autonomicSignal)
    }

    @Test
    fun lowSleepElevatedPulseAndHeavyRecentLoadReduceScore() {
        val recentHardRuns = (1L..5L).map { daysAgo -> workout(daysAgo, 90) }
        val result = analyzer.analyze(
            health(sleep = 300, pulse = 65),
            baselineWorkouts() + recentHardRuns,
            now
        )

        assertTrue(result.score!! < 65)
        assertEquals(70, result.sleepScore)
        assertEquals(50, result.autonomicScore)
        assertEquals(40, result.trainingLoadScore)
    }

    @Test
    fun usesHrvPersonalTrendInsteadOfAlsoCountingRestingPulse() {
        val health = health(sleep = 420, pulse = 65).copy(
            hrvLastNightAverageMs = HealthMetric(30, HealthMetricAvailability.AVAILABLE),
            hrvHistory = (1L..28L).map { DailyHealthValue(today.minusDays(it), 40) }
        )

        val result = analyzer.analyze(health, baselineWorkouts(), now)

        assertEquals("HRV/HFV", result.autonomicSignal)
        assertEquals(62, result.autonomicScore)
    }

    @Test
    fun partialMetricsDoNotProduceAnUnsupportedScore() {
        val partial = health(sleep = 420, pulse = null)

        assertNull(analyzer.analyze(partial, baselineWorkouts(), now).score)
    }

    @Test
    fun insufficientPersonalHistoryDoesNotProduceAScore() {
        val noHistory = ReadinessHealthSnapshot(
            sleepMinutes = HealthMetric(420, HealthMetricAvailability.AVAILABLE),
            restingHeartRateBpm = HealthMetric(55, HealthMetricAvailability.AVAILABLE)
        )

        assertNull(analyzer.analyze(noHistory, emptyList(), now).score)
    }

    @Test
    fun restingPulseContextUsesPersonalHistoryAndRequiresEnoughSamples() {
        val history = (1L..28L).map { DailyHealthValue(today.minusDays(it), 55) }

        assertEquals(
            RestingHeartRatePersonalBand.WITHIN_PERSONAL_RANGE,
            analyzer.restingHeartRatePersonalBand(56, history, today)
        )
        assertEquals(
            RestingHeartRatePersonalBand.ABOVE_PERSONAL_RANGE,
            analyzer.restingHeartRatePersonalBand(60, history, today)
        )
        assertEquals(
            RestingHeartRatePersonalBand.WELL_ABOVE_PERSONAL_RANGE,
            analyzer.restingHeartRatePersonalBand(64, history, today)
        )
        assertEquals(
            RestingHeartRatePersonalBand.BELOW_PERSONAL_RANGE,
            analyzer.restingHeartRatePersonalBand(50, history, today)
        )
        assertNull(analyzer.restingHeartRatePersonalBand(56, history.take(13), today))
    }

    private fun health(sleep: Int, pulse: Int?) = ReadinessHealthSnapshot(
        sleepMinutes = HealthMetric(sleep, HealthMetricAvailability.AVAILABLE),
        restingHeartRateBpm = pulse?.let { HealthMetric(it, HealthMetricAvailability.AVAILABLE) }
            ?: HealthMetric(null, HealthMetricAvailability.NO_DATA),
        sleepHistory = (1L..28L).map { DailyHealthValue(today.minusDays(it), 420) },
        restingHeartRateHistory = (1L..28L).map { DailyHealthValue(today.minusDays(it), 55) }
    )

    private fun baselineWorkouts() = listOf(9L, 16L, 23L, 34L).map { workout(it, 60) }

    private fun workout(daysAgo: Long, minutes: Int) = Workout(
        sourceId = "run-$daysAgo-$minutes",
        sourceName = "Garmin Connect",
        title = "Lauf",
        startedAtMillis = now.minusSeconds(daysAgo * 86_400).minusSeconds(minutes * 60L).toEpochMilli(),
        endedAtMillis = now.minusSeconds(daysAgo * 86_400).toEpochMilli(),
        distanceMeters = null
    )
}
