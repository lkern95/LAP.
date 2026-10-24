package de.run.app.domain

import de.run.app.domain.model.PaceTargetRange
import de.run.app.domain.model.RunPrescription
import de.run.app.domain.model.RunningSymptomFeedback
import de.run.app.domain.model.Workout
import de.run.app.domain.model.isRunning
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Uses only repeated, explicitly easy runs to provide an observed pace reference. */
class RunPrescriptionAnalyzer {
    fun analyze(
        workouts: List<Workout>,
        today: LocalDate,
        plannedDurationMinutes: Int,
        zone: ZoneId = ZoneId.systemDefault()
    ): RunPrescription {
        val cutoff = today.minusDays(COMPARISON_DAYS)
        val plannedDurationSeconds = plannedDurationMinutes.coerceAtLeast(1) * 60L
        val recentRuns = workouts.filter { workout ->
            if (!workout.isRunning()) return@filter false
            val date = Instant.ofEpochMilli(workout.startedAtMillis).atZone(zone).toLocalDate()
            !date.isBefore(cutoff) && !date.isAfter(today)
        }
        val comparableRuns = recentRuns.filter { workout ->
            (workout.distanceMeters ?: 0.0) >= MIN_COMPARABLE_DISTANCE_METERS &&
                workout.durationSeconds in
                (plannedDurationSeconds * MIN_DURATION_RATIO).toLong()..
                (plannedDurationSeconds * MAX_DURATION_RATIO).toLong()
        }
        val observedPaces = comparableRuns.mapNotNull(Workout::paceSecondsPerKilometer)
            .filter { it in MIN_REASONABLE_PACE_SECONDS..MAX_REASONABLE_PACE_SECONDS }
            .map(Long::toInt)
            .sorted()
        val observedRange = observedPaces.takeIf { it.size >= MIN_COMPARABLE_RUNS }?.let {
            PaceTargetRange(slowerSecondsPerKm = percentile(it, 0.75), fasterSecondsPerKm = percentile(it, 0.25))
        }
        val easyRatedRuns = comparableRuns.filter { workout ->
            workout.feedback?.perceivedEffort?.let(EASY_EFFORT_RANGE::contains) == true
        }
        val symptomFreeRuns = easyRatedRuns.filter {
            it.feedback?.runningAffectedBySymptoms == RunningSymptomFeedback.NO
        }
        val prescribedPaces = symptomFreeRuns.mapNotNull(Workout::paceSecondsPerKilometer)
            .filter { it in MIN_REASONABLE_PACE_SECONDS..MAX_REASONABLE_PACE_SECONDS }
            .map(Long::toInt)
            .sorted()
        if (prescribedPaces.size < MIN_COMPARABLE_RUNS) return RunPrescription(
            observedPaceRange = observedRange,
            rationale = "Noch keine persönliche Pacevorgabe: ${missingPaceEvidenceReason(
                recentRuns.size, comparableRuns.size, easyRatedRuns.size, symptomFreeRuns.size, prescribedPaces.size
            )} Für den Vergleich zählen die letzten zwölf Wochen, mindestens 3 km, ähnliche Dauer (±25 %), " +
                "dokumentiertes RPE 2–4 und keine gemeldeten Beschwerden. Ein persönlicher Pulsbereich lässt sich " +
                "aus Aktivitäts-Durchschnittswerten nicht sicher ableiten."
        )

        val faster = percentile(prescribedPaces, 0.25)
        val slower = percentile(prescribedPaces, 0.75)
        return RunPrescription(
            paceRange = PaceTargetRange(slowerSecondsPerKm = slower, fasterSecondsPerKm = faster),
            observedPaceRange = observedRange,
            rationale = "Pacebereich aus ${prescribedPaces.size} lockeren Vergleichsläufen der letzten zwölf Wochen " +
                "mit ähnlicher Dauer (RPE 2–4, ohne gemeldete Beschwerden). Ein persönlicher Pulsbereich " +
                "lässt sich aus Aktivitäts-Durchschnittswerten nicht sicher ableiten."
        )
    }

    private fun missingPaceEvidenceReason(
        recent: Int,
        comparable: Int,
        easyRated: Int,
        symptomFree: Int,
        validPaces: Int
    ): String = when {
        recent == 0 -> "Es liegen keine aktuellen Laufdaten vor."
        comparable < MIN_COMPARABLE_RUNS -> "Es liegen $comparable Läufe mit mindestens 3 km und passender Dauer vor; benötigt werden fünf."
        easyRated < MIN_COMPARABLE_RUNS -> "Bei nur $easyRated von $comparable vergleichbaren Läufen ist ein leichtes RPE von 2–4 dokumentiert; benötigt werden fünf."
        symptomFree < MIN_COMPARABLE_RUNS -> "Nur $symptomFree von $easyRated leicht bewerteten Läufen wurden ohne Beschwerden bestätigt; benötigt werden fünf."
        else -> "Nur $validPaces von $symptomFree passenden Läufen enthalten eine auswertbare Pace; benötigt werden fünf."
    }

    private fun percentile(values: List<Int>, fraction: Double): Int =
        values[((values.size - 1) * fraction).toInt().coerceIn(values.indices)]

    private companion object {
        const val COMPARISON_DAYS = 84L
        const val MIN_COMPARABLE_RUNS = 5
        const val MIN_COMPARABLE_DISTANCE_METERS = 3_000.0
        const val MIN_DURATION_RATIO = 0.75
        const val MAX_DURATION_RATIO = 1.25
        const val MIN_REASONABLE_PACE_SECONDS = 150L
        const val MAX_REASONABLE_PACE_SECONDS = 1_200L
        val EASY_EFFORT_RANGE = 2..4
    }
}
