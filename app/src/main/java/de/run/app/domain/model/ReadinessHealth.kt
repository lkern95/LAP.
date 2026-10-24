package de.run.app.domain.model

import java.time.LocalDate

data class ReadinessHealthSnapshot(
    val sleepMinutes: HealthMetric<Int>,
    val restingHeartRateBpm: HealthMetric<Int>,
    val isDemo: Boolean = false,
    val sleepHistory: List<DailyHealthValue> = emptyList(),
    val restingHeartRateHistory: List<DailyHealthValue> = emptyList()
)

data class DailyHealthValue(val date: LocalDate, val value: Int)

data class HealthMetric<T>(
    val value: T?,
    val availability: HealthMetricAvailability,
    val sourceName: String? = null
)

enum class HealthMetricAvailability {
    AVAILABLE,
    NO_DATA,
    PERMISSION_NEEDED,
    UNAVAILABLE,
    FAILED
}
