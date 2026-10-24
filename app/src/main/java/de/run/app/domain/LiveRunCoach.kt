package de.run.app.domain

import java.util.ArrayDeque
import de.run.app.domain.model.PlannedRun
import de.run.app.domain.model.RunStep
import de.run.app.domain.model.paceTarget

/** A point received from a live source. Pace is seconds per kilometre. */
data class LiveRunMetrics(
    val activeSeconds: Long,
    val distanceMeters: Double?,
    val paceSecondsPerKilometer: Int?,
    val heartRateBpm: Int?,
    val measuredAtMillis: Long,
    val source: LiveMetricSource,
    val isPaused: Boolean
)

enum class LiveMetricSource { GARMIN, EMULATOR_TEST }

data class LiveRunTargets(
    val minPaceSecondsPerKilometer: Int? = null,
    val maxPaceSecondsPerKilometer: Int? = null,
    val maxHeartRateBpm: Int? = null,
    val sections: List<LiveRunSectionTarget> = emptyList(),
    val trainingTarget: GarminTrainingTarget? = null
) {
    fun atElapsedSeconds(activeSeconds: Long): LiveRunTargets {
        if (sections.isEmpty()) return this
        val target = sectionAt(activeSeconds)?.target ?: return LiveRunTargets()
        return target
    }

    fun sectionIndexAt(activeSeconds: Long): Int? {
        if (activeSeconds < 0) return null
        var elapsed = 0L
        sections.forEachIndexed { index, section ->
            elapsed += section.durationSeconds
            if (activeSeconds < elapsed) return index
        }
        return null
    }

    fun sectionAt(activeSeconds: Long): LiveRunSectionTarget? = sectionIndexAt(activeSeconds)?.let(sections::get)

    fun toTransportValues(): ArrayList<Int> = ArrayList(sections.flatMap { section ->
        listOf(
            section.durationSeconds,
            section.target.minPaceSecondsPerKilometer ?: NO_TARGET,
            section.target.maxPaceSecondsPerKilometer ?: NO_TARGET,
            section.target.maxHeartRateBpm ?: NO_TARGET,
            section.transitionCue.ordinal
        )
    })

    companion object {
        fun from(run: PlannedRun): LiveRunTargets {
            return LiveRunTargets(
                sections = run.steps.mapIndexed { index, step ->
                    val stepPace = step.paceTarget(run.prescription)
                    LiveRunSectionTarget(
                        durationSeconds = step.minutes.coerceAtLeast(0) * 60,
                        target = LiveRunTargets(
                            minPaceSecondsPerKilometer = stepPace?.fasterSecondsPerKm,
                            maxPaceSecondsPerKilometer = stepPace?.slowerSecondsPerKm,
                            maxHeartRateBpm = run.prescription.heartRateRangeBpm?.last
                                ?.takeIf { stepPace != null }
                        ),
                        transitionCue = step.transitionCue(index, run.steps.lastIndex)
                    )
                },
                trainingTarget = GarminTrainingTarget.from(run)
            )
        }

        fun fromTransportValues(values: List<Int>?): LiveRunTargets {
            if (values.isNullOrEmpty() || values.size % TRANSPORT_FIELDS_PER_SECTION != 0) return LiveRunTargets()
            val sections = values.chunked(TRANSPORT_FIELDS_PER_SECTION).map { fields ->
                val duration = fields[0].takeIf { it > 0 } ?: return LiveRunTargets()
                val cue = CoachAudioCue.entries.getOrNull(fields[4]) ?: return LiveRunTargets()
                LiveRunSectionTarget(
                    durationSeconds = duration,
                    target = LiveRunTargets(
                        minPaceSecondsPerKilometer = fields[1].takeUnless { it == NO_TARGET },
                        maxPaceSecondsPerKilometer = fields[2].takeUnless { it == NO_TARGET },
                        maxHeartRateBpm = fields[3].takeUnless { it == NO_TARGET }
                    ),
                    transitionCue = cue
                )
            }
            return LiveRunTargets(sections = sections)
        }

        private const val NO_TARGET = -1
        private const val TRANSPORT_FIELDS_PER_SECTION = 5
    }
}

data class LiveRunSectionTarget(
    val durationSeconds: Int,
    val target: LiveRunTargets,
    val transitionCue: CoachAudioCue
)

private fun RunStep.transitionCue(index: Int, lastIndex: Int): CoachAudioCue = when {
    index == 0 -> CoachAudioCue.WARMUP
    index == lastIndex -> CoachAudioCue.COOL_DOWN
    name.contains("gehen", ignoreCase = true) -> CoachAudioCue.RECOVERY
    else -> CoachAudioCue.MAIN_PART
}

enum class LiveCoachCue { TOO_FAST, TOO_SLOW, HEART_RATE_HIGH }

enum class CoachAudioCue {
    READY,
    RUN_STARTED,
    RUN_PAUSED,
    RUN_RESUMED,
    RUN_FINISHED,
    TOO_FAST,
    TOO_SLOW,
    HEART_RATE_HIGH,
    PACE_ON_TARGET,
    HEART_RATE_ON_TARGET,
    HIGH_LOAD,
    WARMUP,
    MAIN_PART,
    HALFWAY,
    RECOVERY,
    INTERVAL_START,
    COOL_DOWN,
    LIVE_CONNECTED,
    CONNECTION_LOST,
    CONNECTION_RESTORED,
    LIVE_UNAVAILABLE,
    METRICS_UNRELIABLE,
    REST_DAY,
    FIVE_MINUTES,
    TRAINING_COMPLETED
}

fun CoachAudioCue.deviationMessage(): String? = when (this) {
    CoachAudioCue.TOO_FAST -> "Du bist schneller als der geplante Tempobereich. Laufe etwas ruhiger."
    CoachAudioCue.TOO_SLOW -> "Du bist langsamer als der geplante Tempobereich. Prüfe dein Tempo."
    CoachAudioCue.HEART_RATE_HIGH -> "Dein Puls liegt über dem geplanten Bereich. Laufe etwas lockerer."
    else -> null
}

fun shouldShowCoachDeviationNotice(
    cue: CoachAudioCue,
    appResumed: Boolean,
    screenInteractive: Boolean,
    deviceLocked: Boolean
): Boolean = cue.deviationMessage() != null && appResumed && screenInteractive && !deviceLocked

enum class CoachAudioPriority(val rank: Int) { ROUTINE(1), INFORMATION(2), WARNING(3), SAFETY(4) }

fun CoachAudioCue.priority(): CoachAudioPriority = when (this) {
    CoachAudioCue.CONNECTION_LOST,
    CoachAudioCue.LIVE_UNAVAILABLE,
    CoachAudioCue.METRICS_UNRELIABLE,
    CoachAudioCue.HIGH_LOAD -> CoachAudioPriority.SAFETY
    CoachAudioCue.TOO_FAST,
    CoachAudioCue.TOO_SLOW,
    CoachAudioCue.HEART_RATE_HIGH -> CoachAudioPriority.WARNING
    CoachAudioCue.LIVE_CONNECTED,
    CoachAudioCue.CONNECTION_RESTORED,
    CoachAudioCue.RUN_STARTED,
    CoachAudioCue.RUN_PAUSED,
    CoachAudioCue.RUN_RESUMED,
    CoachAudioCue.RUN_FINISHED,
    CoachAudioCue.TRAINING_COMPLETED -> CoachAudioPriority.INFORMATION
    else -> CoachAudioPriority.ROUTINE
}

fun CoachAudioCue.mayInterrupt(active: CoachAudioCue): Boolean = priority().rank > active.priority().rank

fun LiveCoachCue.toAudioCue(): CoachAudioCue = when (this) {
    LiveCoachCue.TOO_FAST -> CoachAudioCue.TOO_FAST
    LiveCoachCue.TOO_SLOW -> CoachAudioCue.TOO_SLOW
    LiveCoachCue.HEART_RATE_HIGH -> CoachAudioCue.HEART_RATE_HIGH
}

/** Median filter for transient GPS pace spikes; retains at most five recent samples. */
class LivePaceSmoother(private val capacity: Int = 5) {
    private val samples = ArrayDeque<Int>()

    fun reset() = samples.clear()

    fun add(paceSecondsPerKilometer: Int?): Int? {
        paceSecondsPerKilometer?.takeIf { it > 0 }?.let { sample ->
            samples.addLast(sample)
            while (samples.size > capacity) samples.removeFirst()
        }
        return samples.toList().sorted().getOrNull(samples.size / 2)
    }
}

/** Stateful hysteresis and cooldown prevent audio prompts from reacting to GPS noise. */
class LiveRunCoach(
    private val deviationSeconds: Int = 15,
    private val persistenceMillis: Long = 15_000,
    private val cooldownMillis: Long = 45_000,
    private val staleAfterMillis: Long = 15_000
) {
    private var candidate: LiveCoachCue? = null
    private var candidateSince = 0L
    private var lastCueAt = Long.MIN_VALUE

    fun nextCue(metrics: LiveRunMetrics, targets: LiveRunTargets, nowMillis: Long): LiveCoachCue? {
        if (metrics.isPaused || nowMillis - metrics.measuredAtMillis > staleAfterMillis) {
            candidate = null
            return null
        }
        val cue = when {
            targets.maxHeartRateBpm != null && metrics.heartRateBpm != null &&
                metrics.heartRateBpm > targets.maxHeartRateBpm -> LiveCoachCue.HEART_RATE_HIGH
            targets.minPaceSecondsPerKilometer != null && metrics.paceSecondsPerKilometer != null &&
                metrics.paceSecondsPerKilometer < targets.minPaceSecondsPerKilometer - deviationSeconds -> LiveCoachCue.TOO_FAST
            targets.maxPaceSecondsPerKilometer != null && metrics.paceSecondsPerKilometer != null &&
                metrics.paceSecondsPerKilometer > targets.maxPaceSecondsPerKilometer + deviationSeconds -> LiveCoachCue.TOO_SLOW
            else -> null
        }
        if (cue == null) {
            candidate = null
            return null
        }
        if (cue != candidate) {
            candidate = cue
            candidateSince = nowMillis
            return null
        }
        val coolingDown = lastCueAt != Long.MIN_VALUE && nowMillis - lastCueAt < cooldownMillis
        if (nowMillis - candidateSince < persistenceMillis || coolingDown) return null
        lastCueAt = nowMillis
        candidate = null
        return cue
    }
}
