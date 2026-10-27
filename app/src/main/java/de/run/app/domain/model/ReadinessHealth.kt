package de.run.app.domain.model

import java.time.LocalDate

data class ReadinessHealthSnapshot(
    val sleepMinutes: HealthMetric<Int>,
    val restingHeartRateBpm: HealthMetric<Int>,
    val isDemo: Boolean = false,
    val sleepHistory: List<DailyHealthValue> = emptyList(),
    val restingHeartRateHistory: List<DailyHealthValue> = emptyList(),
    val hrvLastNightAverageMs: HealthMetric<Int> = unavailableMetric(),
    val hrvStatus: HealthMetric<String> = unavailableMetric(),
    val hrvHistory: List<DailyHealthValue> = emptyList(),
    val averageStressLevel: HealthMetric<Int> = unavailableMetric(),
    val stressHistory: List<DailyHealthValue> = emptyList(),
    val steps: HealthMetric<Int> = unavailableMetric(),
    val stepsHistory: List<DailyHealthValue> = emptyList()
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

private fun <T> unavailableMetric() = HealthMetric<T>(null, HealthMetricAvailability.UNAVAILABLE)
