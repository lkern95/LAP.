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
        val records = days.readBetween(localToday.minusDays(HISTORY_DAYS).toString(), localToday.toString())
        val recent = records.filter { record ->
            val date = java.time.LocalDate.parse(record.date).atStartOfDay(zone).toInstant()
            !date.isBefore(from) && !date.isAfter(to)
        }.maxByOrNull { it.date }
        return fallback.copy(
            sleepMinutes = recent?.sleepMinutes?.let { HealthMetric(it, HealthMetricAvailability.AVAILABLE, GARMIN_SOURCE) }
                ?: fallback.sleepMinutes,
            restingHeartRateBpm = recent?.restingHeartRateBpm?.let { HealthMetric(it, HealthMetricAvailability.AVAILABLE, GARMIN_SOURCE) }
                ?: fallback.restingHeartRateBpm,
            sleepHistory = records.mapNotNull { row -> row.sleepMinutes?.let { DailyHealthValue(java.time.LocalDate.parse(row.date), it) } },
            restingHeartRateHistory = records.mapNotNull { row -> row.restingHeartRateBpm?.let { DailyHealthValue(java.time.LocalDate.parse(row.date), it) } }
        )
    }

    private companion object {
        const val HISTORY_DAYS = 55L
        const val GARMIN_SOURCE = "Garmin Connect"
    }
}
