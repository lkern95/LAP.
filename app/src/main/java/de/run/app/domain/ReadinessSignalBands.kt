package de.run.app.domain

enum class ReadinessSignalBand { FAVORABLE, CAUTION, LOW }

enum class GarminStressBand { RESTING, LOW, MODERATE, HIGH }

enum class RecoveryIntervalBand { UNDER_12_HOURS, UNDER_36_HOURS, AT_LEAST_36_HOURS }

fun readinessSignalBand(score: Int?): ReadinessSignalBand? = when {
    score == null -> null
    score >= 80 -> ReadinessSignalBand.FAVORABLE
    score >= 60 -> ReadinessSignalBand.CAUTION
    else -> ReadinessSignalBand.LOW
}

fun garminStressBand(value: Int?): GarminStressBand? = when {
    value == null || value !in 0..100 -> null
    value <= 25 -> GarminStressBand.RESTING
    value <= 50 -> GarminStressBand.LOW
    value <= 75 -> GarminStressBand.MODERATE
    else -> GarminStressBand.HIGH
}

fun recoveryIntervalBand(minutesSinceLastRun: Long?): RecoveryIntervalBand? = when {
    minutesSinceLastRun == null || minutesSinceLastRun < 0 -> null
    minutesSinceLastRun < 12 * 60 -> RecoveryIntervalBand.UNDER_12_HOURS
    minutesSinceLastRun < 36 * 60 -> RecoveryIntervalBand.UNDER_36_HOURS
    else -> RecoveryIntervalBand.AT_LEAST_36_HOURS
}
