package de.run.app.data.garmin

import de.run.app.data.local.GarminHealthDayDao
import de.run.app.domain.model.DailyHealthValue
import de.run.app.domain.model.HealthMetric
import de.run.app.domain.model.HealthMetricAvailability
import de.run.app.domain.model.ReadinessHealthSnapshot
import de.run.app.domain.repository.ReadinessHealthRepository
import java.time.Instant
import java.time.ZoneId

class GarminBackedReadinessRepository(
    private val healthConnect: ReadinessHealthRepository,
    private val days: GarminHealthDayDao,
    private val zone: ZoneId = ZoneId.systemDefault()
) : ReadinessHealthRepository {
    override suspend fun read(from: Instant, to: Instant): ReadinessHealthSnapshot {
        val fallback = healthConnect.read(from, to)
        val localToday = to.atZone(zone).toLocalDate()
        val currentFromDate = to.minus(java.time.Duration.ofHours(CURRENT_VALUE_AGE_HOURS)).atZone(zone).toLocalDate()
        val records = days.readBetween(localToday.minusDays(HISTORY_DAYS).toString(), localToday.toString())
        val recent = records.lastOrNull { it.date >= currentFromDate.toString() && it.date <= localToday.toString() }
        return fallback.copy(
            sleepMinutes = recent?.sleepMinutes?.let { HealthMetric(it, HealthMetricAvailability.AVAILABLE, GARMIN_SOURCE) }
                ?: fallback.sleepMinutes,
            restingHeartRateBpm = recent?.restingHeartRateBpm?.let { HealthMetric(it, HealthMetricAvailability.AVAILABLE, GARMIN_SOURCE) }
                ?: fallback.restingHeartRateBpm,
            hrvLastNightAverageMs = recent?.hrvLastNightAverageMs?.let { available(it) } ?: fallback.hrvLastNightAverageMs,
            hrvStatus = recent?.hrvStatus?.let { HealthMetric(it, HealthMetricAvailability.AVAILABLE, GARMIN_SOURCE) }
                ?: fallback.hrvStatus,
            averageStressLevel = recent?.averageStressLevel?.let { available(it) } ?: fallback.averageStressLevel,
            steps = recent?.steps?.let { available(it) } ?: fallback.steps,
            sleepHistory = mergeHistory(
                fallback.sleepHistory,
                records.mapNotNull { row -> row.sleepMinutes?.let { DailyHealthValue(java.time.LocalDate.parse(row.date), it) } }
            ),
            restingHeartRateHistory = mergeHistory(
                fallback.restingHeartRateHistory,
                records.mapNotNull { row -> row.restingHeartRateBpm?.let { DailyHealthValue(java.time.LocalDate.parse(row.date), it) } }
            ),
            hrvHistory = records.mapNotNull { row -> row.hrvLastNightAverageMs?.let { DailyHealthValue(java.time.LocalDate.parse(row.date), it) } },
            stressHistory = records.mapNotNull { row -> row.averageStressLevel?.let { DailyHealthValue(java.time.LocalDate.parse(row.date), it) } },
            stepsHistory = records.mapNotNull { row -> row.steps?.let { DailyHealthValue(java.time.LocalDate.parse(row.date), it) } }
        )
    }

    private fun available(value: Int) = HealthMetric(value, HealthMetricAvailability.AVAILABLE, GARMIN_SOURCE)

    private fun mergeHistory(
        healthConnect: List<DailyHealthValue>,
        garmin: List<DailyHealthValue>
    ): List<DailyHealthValue> = (healthConnect + garmin)
        .associateBy(DailyHealthValue::date)
        .values
        .sortedBy(DailyHealthValue::date)

    private companion object {
        const val HISTORY_DAYS = 55L
        const val CURRENT_VALUE_AGE_HOURS = 36L
        const val GARMIN_SOURCE = "Garmin Connect"
    }
}
