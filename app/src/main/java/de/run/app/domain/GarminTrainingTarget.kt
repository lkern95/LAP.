package de.run.app.domain

import de.run.app.domain.model.PlannedRun

/** Per-run target passed to the Connect IQ field; this plan currently prescribes time, not distance. */
data class GarminTrainingTarget(
    val trainingId: String,
    val durationSeconds: Int
) {
    fun toMessage(): ArrayList<Any> = arrayListOf(MESSAGE_TYPE, VERSION, trainingId, durationSeconds)

    fun acknowledgementMessage(): List<Any> = listOf(ACKNOWLEDGEMENT_TYPE, VERSION, trainingId, durationSeconds)

    companion object {
        private const val MESSAGE_TYPE = "LAP_TARGET"
        private const val ACKNOWLEDGEMENT_TYPE = "LAP_TARGET_ACK"
        private const val VERSION = 1
        private const val MAX_TARGET_SECONDS = 24 * 60 * 60

        fun from(run: PlannedRun): GarminTrainingTarget? {
            val id = run.trainingId?.takeIf(String::isNotBlank) ?: return null
            val duration = run.totalMinutes * 60
            if (duration !in 1..MAX_TARGET_SECONDS) return null
            return GarminTrainingTarget(id, duration)
        }

        fun decodeAcknowledgement(values: List<*>?): GarminTrainingTarget? {
            if (values == null || values.size != 4 || values[0] != ACKNOWLEDGEMENT_TYPE) return null
            if (values[1].toIntOrNull() != VERSION) return null
            val id = values[2] as? String ?: return null
            if (id.isBlank()) return null
            val duration = values[3].toIntOrNull()?.takeIf { it in 1..MAX_TARGET_SECONDS } ?: return null
            return GarminTrainingTarget(id, duration)
        }

        private fun Any?.toIntOrNull(): Int? = when (this) {
            is Number -> toInt().takeIf { toDouble() == it.toDouble() }
            else -> null
        }
    }
}
