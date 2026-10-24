package de.run.app.domain

/** Minimal, data-free connectivity probe used only by the Connect IQ transport test. */
data class GarminLivePing(val sequence: Long) {
    companion object {
        private const val TYPE = "LAP_PING"
        private const val VERSION = 1L

        fun decode(values: List<*>?): GarminLivePing? {
            if (values == null || values.size != 3 || values[0] != TYPE) return null
            val version = values[1].toLongOrNull() ?: return null
            if (version != VERSION) return null
            val sequence = values[2].toLongOrNull()?.takeIf { it >= 0L } ?: return null
            return GarminLivePing(sequence)
        }
    }
}

/** Protocol v1 is an eight-number Connect IQ array; negative sensor values mean unavailable. */
data class GarminLivePacket(
    val sequence: Long,
    val watchTimeSeconds: Long,
    val timerMillis: Long,
    val distanceMeters: Double?,
    val speedMetersPerSecond: Double?,
    val heartRateBpm: Int?,
    val timerState: Int,
    val receivedAtMillis: Long
) {
    fun toMetrics(): LiveRunMetrics {
        val pace = speedMetersPerSecond
            ?.takeIf { it > 0.0 }
            ?.let { (1_000.0 / it).toInt() }
            ?.takeIf { it in 120..1_800 }
        return LiveRunMetrics(
            activeSeconds = timerMillis / 1_000,
            distanceMeters = distanceMeters,
            paceSecondsPerKilometer = pace,
            heartRateBpm = heartRateBpm,
            measuredAtMillis = receivedAtMillis,
            source = LiveMetricSource.GARMIN,
            isPaused = timerState != TIMER_STATE_ON
        )
    }

    companion object {
        const val PROTOCOL_VERSION = 1
        const val TIMER_STATE_ON = 3
        private const val TIMER_STATE_MIN = 0
        private const val TIMER_STATE_MAX = 3
        private const val MAX_PACKET_AGE_MILLIS = 15_000L
        private const val MAX_FUTURE_SKEW_MILLIS = 5_000L
        private const val MAX_TIMER_MILLIS = 24 * 60 * 60 * 1_000L

        fun decode(values: List<*>?, nowMillis: Long): GarminLivePacket? {
            if (values == null || values.size != 8) return null
            val version = values[0].toLongOrNull() ?: return null
            if (version != PROTOCOL_VERSION.toLong()) return null
            val sequence = values[1].toLongOrNull()?.takeIf { it >= 0L } ?: return null
            val watchTimeSeconds = values[2].toLongOrNull()?.takeIf { it > 0L } ?: return null
            val watchTimeMillis = watchTimeSeconds * 1_000L
            val ageMillis = nowMillis - watchTimeMillis
            if (ageMillis > MAX_PACKET_AGE_MILLIS || ageMillis < -MAX_FUTURE_SKEW_MILLIS) return null

            val timerMillis = values[3].toLongOrNull()
                ?.takeIf { it in 0..MAX_TIMER_MILLIS } ?: return null
            val distance = values[4].toDoubleOrNull()
                ?.takeIf { it == MISSING_VALUE || it >= 0.0 }
                ?.takeIf { it <= MAX_DISTANCE_METERS }
                ?.takeUnless { it == MISSING_VALUE }
            val speed = values[5].toDoubleOrNull()
                ?.takeIf { it == MISSING_VALUE || it >= 0.0 }
                ?.takeUnless { it == MISSING_VALUE }
            val heartRate = values[6].toLongOrNull()
                ?.takeIf { it == MISSING_VALUE.toLong() || it in MIN_HEART_RATE..MAX_HEART_RATE }
                ?.takeUnless { it == MISSING_VALUE.toLong() }
                ?.toInt()
            val timerState = values[7].toLongOrNull()
                ?.takeIf { it in TIMER_STATE_MIN..TIMER_STATE_MAX }
                ?.toInt() ?: return null
            return GarminLivePacket(
                sequence,
                watchTimeSeconds,
                timerMillis,
                distance,
                speed,
                heartRate,
                timerState,
                nowMillis
            )
        }

        private const val MISSING_VALUE = -1.0
        private const val MIN_HEART_RATE = 30L
        private const val MAX_HEART_RATE = 240L
        private const val MAX_DISTANCE_METERS = 1_000_000.0
    }
}

/** Drops repeated/reordered samples and accepts a new activity after its timer restarts. */
class GarminLivePacketSequenceGate {
    private var lastPacket: GarminLivePacket? = null

    fun reset() {
        lastPacket = null
    }

    fun accept(packet: GarminLivePacket): Boolean {
        val previous = lastPacket
        if (previous != null) {
            if (packet.watchTimeSeconds <= previous.watchTimeSeconds) return false
            val timerRestarted = packet.timerMillis + TIMER_RESTART_TOLERANCE_MILLIS < previous.timerMillis
            val sequenceRestarted = packet.sequence == 0L &&
                (packet.timerMillis >= previous.timerMillis || timerRestarted)
            if (packet.sequence <= previous.sequence && !sequenceRestarted) return false
        }
        lastPacket = packet
        return true
    }

    companion object {
        private const val TIMER_RESTART_TOLERANCE_MILLIS = 5_000L
    }
}

private fun Any?.toLongOrNull(): Long? = when (this) {
    is Number -> toLong().takeIf { toDouble() == it.toDouble() }
    else -> null
}

private fun Any?.toDoubleOrNull(): Double? = (this as? Number)?.toDouble()
