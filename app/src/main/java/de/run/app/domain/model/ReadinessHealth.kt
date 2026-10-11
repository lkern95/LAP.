package de.run.app.domain.model

data class ReadinessHealthSnapshot(
    val sleepMinutes: HealthMetric<Int>,
    val restingHeartRateBpm: HealthMetric<Int>,
    val isDemo: Boolean = false
)

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
