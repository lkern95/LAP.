package de.run.app.domain

import de.run.app.domain.model.DistanceSpikeAssessment
import de.run.app.domain.model.DistanceSpikeLevel
import de.run.app.domain.model.PlannedRun
import de.run.app.domain.model.Workout
import de.run.app.domain.model.isRunning
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.floor

/** Compares a run only with completed, measured runs in the preceding 30 days. */
class RunDistanceSpikeAnalyzer {
    fun assessPlanned(
        run: PlannedRun,
        workouts: List<Workout>,
        targetDate: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): DistanceSpikeAssessment {
        val reference = longestRecentRun(workouts, targetDate, zone)
            ?: return DistanceSpikeAssessment(
                DistanceSpikeLevel.NOT_ASSESSABLE,
                unavailableReason = "Kein gemessener Lauf aus den vorherigen 30 Tagen vorhanden."
            )
        val pace = run.prescription.paceRange
            ?: return DistanceSpikeAssessment(
                DistanceSpikeLevel.NOT_ASSESSABLE,
                referenceDistanceMeters = reference.distanceMeters,
                referenceDate = reference.date,
                unavailableReason = "Für diesen Lauf liegt keine belastbare persönliche Pace-Spanne vor."
            )
        val runningSeconds = run.steps
            .filter { it.name.contains("laufen", ignoreCase = true) }
            .sumOf { it.minutes.toLong() * 60L }
        if (runningSeconds <= 0) return DistanceSpikeAssessment(
            DistanceSpikeLevel.NOT_ASSESSABLE,
            referenceDistanceMeters = reference.distanceMeters,
            referenceDate = reference.date,
            unavailableReason = "Die Laufdauer dieser Einheit lässt sich nicht bestimmen."
        )
        val minimum = runningSeconds * 1_000.0 / pace.slowerSecondsPerKm
        val maximum = runningSeconds * 1_000.0 / pace.fasterSecondsPerKm
        return assessment(reference, minimum, maximum, isEstimate = true)
    }

    fun assessCompleted(
        workout: Workout,
        workouts: List<Workout>,
        zone: ZoneId = ZoneId.systemDefault()
    ): DistanceSpikeAssessment {
        val date = Instant.ofEpochMilli(workout.startedAtMillis).atZone(zone).toLocalDate()
        val distance = workout.distanceMeters?.takeIf { it.isFinite() && it > 0.0 }
            ?: return DistanceSpikeAssessment(
                DistanceSpikeLevel.NOT_ASSESSABLE,
                unavailableReason = "Für diesen Lauf wurde keine verlässliche Distanz gespeichert."
            )
        val history = workouts.filter { it.sourceId != workout.sourceId }
        val reference = longestRecentRun(history, date, zone)
            ?: return DistanceSpikeAssessment(
                DistanceSpikeLevel.NOT_ASSESSABLE,
                runDistanceMeters = distance,
                unavailableReason = "Kein gemessener Lauf aus den vorherigen 30 Tagen vorhanden."
            )
        return assessment(reference, distance, distance, isEstimate = false)
    }

    private fun longestRecentRun(
        workouts: List<Workout>,
        targetDate: LocalDate,
        zone: ZoneId
    ): DatedDistance? = workouts.asSequence()
        .filter(Workout::isRunning)
        .groupBy(Workout::sourceId)
        .values.asSequence()
        .map { versions -> versions.maxBy { it.endedAtMillis } }
        .mapNotNull { workout ->
            val date = Instant.ofEpochMilli(workout.startedAtMillis).atZone(zone).toLocalDate()
            val distance = workout.distanceMeters?.takeIf { it.isFinite() && it > 0.0 } ?: return@mapNotNull null
            if (date.isBefore(targetDate.minusDays(30)) || !date.isBefore(targetDate)) return@mapNotNull null
            DatedDistance(date, distance)
        }
        .maxByOrNull(DatedDistance::distanceMeters)

    private fun assessment(
        reference: DatedDistance,
        minimumDistance: Double,
        maximumDistance: Double,
        isEstimate: Boolean
    ): DistanceSpikeAssessment {
        val minimumPercentExact = exactPercentIncrease(minimumDistance, reference.distanceMeters)
        val maximumPercentExact = exactPercentIncrease(maximumDistance, reference.distanceMeters)
        val minimumPercent = floor(minimumPercentExact).toInt()
        val maximumPercent = ceil(maximumPercentExact).toInt()
        return DistanceSpikeAssessment(
            level = levelFor(maximumPercentExact),
            referenceDistanceMeters = reference.distanceMeters,
            referenceDate = reference.date,
            runDistanceMeters = if (isEstimate) null else minimumDistance,
            estimatedDistanceMinimumMeters = minimumDistance.takeIf { isEstimate },
            estimatedDistanceMaximumMeters = maximumDistance.takeIf { isEstimate },
            increaseMinimumPercent = minimumPercent,
            increaseMaximumPercent = maximumPercent,
            isEstimate = isEstimate
        )
    }

    private fun exactPercentIncrease(distance: Double, reference: Double): Double =
        ((distance - reference) / reference) * 100.0

    private fun levelFor(percent: Double): DistanceSpikeLevel = when {
        percent <= 10 -> DistanceSpikeLevel.NO_WARNING
        percent <= 30 -> DistanceSpikeLevel.NOTICE
        percent <= 100 -> DistanceSpikeLevel.WARNING
        else -> DistanceSpikeLevel.STRONG_WARNING
    }

    private data class DatedDistance(val date: LocalDate, val distanceMeters: Double)
}
