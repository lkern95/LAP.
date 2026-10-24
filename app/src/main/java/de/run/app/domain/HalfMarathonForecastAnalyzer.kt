package de.run.app.domain

import de.run.app.domain.model.TrainingGoal
import de.run.app.domain.model.TrainingGoalStatus
import de.run.app.domain.model.RaceGoal
import de.run.app.domain.model.Workout
import de.run.app.domain.model.isRunning
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.pow
import kotlin.math.roundToLong

enum class GoalOutlook { LIKELY, POSSIBLE, UNLIKELY, NOT_ENOUGH_DATA }
enum class ForecastConfidence { TRAINING_ORIENTATION, ROUGH, SUPPORTED }

data class HalfMarathonForecast(
    val estimatedMinutes: IntRange?,
    val evidenceRuns: Int,
    val weeksRemaining: Long,
    val recentRunsPerWeek: Int,
    val longestRunKilometers: Double,
    val targetOutlook: GoalOutlook,
    val explanation: String,
    val recommendation: String,
    val recentDistanceKilometers: Double,
    val previousDistanceKilometers: Double,
    val recentRunCount: Int,
    val previousRunCount: Int,
    val recentLongestRunKilometers: Double,
    val dataQuality: String,
    val confidence: ForecastConfidence? = null,
    val previousLongestRunKilometers: Double = 0.0,
    val outlookSummary: String = ""
)

data class WorkoutGoalProjection(
    val estimatedMinutes: Int,
    val differenceFromTargetMinutes: Int?
)

/** Conservative, offline half-marathon assessment. Ordinary training pace is not performance evidence. */
class HalfMarathonForecastAnalyzer {
    fun projectWorkout(
        workout: Workout,
        goalDistanceMeters: Int?,
        targetTimeMinutes: Int?
    ): WorkoutGoalProjection? {
        val distance = workout.distanceMeters?.takeIf { it.isFinite() && it >= MIN_EVIDENCE_METERS } ?: return null
        val pace = workout.paceSecondsPerKilometer ?: return null
        val goalDistance = goalDistanceMeters?.takeIf { it > 0 } ?: return null
        if (!workout.isRunning()) return null

        val projectedMinutes = pace * (distance / 1_000.0) *
            (goalDistance / distance).pow(RIEGEL_EXPONENT) / 60.0
        val estimate = projectedMinutes.roundToLong().toInt()
        return WorkoutGoalProjection(
            estimatedMinutes = estimate,
            differenceFromTargetMinutes = targetTimeMinutes?.takeIf { it > 0 }?.let { estimate - it }
        )
    }

    fun analyze(
        goal: TrainingGoal?,
        workouts: List<Workout>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
        nextPlannedRunTitle: String? = null
    ): HalfMarathonForecast? {
        if (goal == null || goal.status != TrainingGoalStatus.ACTIVE || goal.raceDistanceMeters !in HALF_MARATHON_MIN..HALF_MARATHON_MAX) return null

        val weeks = ((goal.raceDate.toEpochDay() - today.toEpochDay()).coerceAtLeast(0) + 6) / 7
        val records = uniqueRunningWorkouts(workouts).mapNotNull { workout ->
            val distance = workout.distanceMeters?.takeIf { it.isFinite() && it > 0.0 } ?: return@mapNotNull null
            val date = Instant.ofEpochMilli(workout.startedAtMillis).atZone(zone).toLocalDate()
            if (date.isAfter(today) || date.isBefore(today.minusDays(83))) null
            else DatedRun(date, distance, workout)
        }
        val recent = records.filter { !it.date.isBefore(today.minusDays(27)) }
        val previous = records.filter { it.date.isBefore(today.minusDays(27)) && !it.date.isBefore(today.minusDays(55)) }
        val recentKm = recent.sumOf { it.distanceMeters } / 1_000.0
        val previousKm = previous.sumOf { it.distanceMeters } / 1_000.0
        val recentLongest = recent.maxOfOrNull { it.distanceMeters }?.div(1_000.0) ?: 0.0
        val previousLongest = previous.maxOfOrNull { it.distanceMeters }?.div(1_000.0) ?: 0.0
        val longestRecent = records.filter { !it.date.isBefore(today.minusDays(55)) }
            .maxOfOrNull { it.distanceMeters }?.div(1_000.0) ?: 0.0
        val performanceEfforts = records.filter(::isPerformanceEvidence)
        val estimate = estimateRange(performanceEfforts, recent)
        val weeklyRuns = (recent.size / 4.0).roundToLong().toInt()
        val outlook = assessTarget(
            goal.targetTimeMinutes, estimate?.minutes, estimate?.confidence, weeklyRuns,
            longestRecent, weeks, recentKm, previousKm
        )
        val outlookSummary = if (goal.raceGoal == RaceGoal.RUN_THROUGH) {
            assessRunThrough(recent.size, recentKm, recentLongest, weeks, previousKm)
        } else targetSummary(outlook)
        val explanation = buildExplanation(
            recent.size, recentKm, previous.size, previousKm, recentLongest,
            longestRecent, previousLongest, estimate?.evidenceRuns ?: performanceEfforts.size,
            estimate?.minutes, estimate?.confidence
        )
        return HalfMarathonForecast(
            estimatedMinutes = estimate?.minutes,
            evidenceRuns = estimate?.evidenceRuns ?: 0,
            weeksRemaining = weeks,
            recentRunsPerWeek = weeklyRuns,
            longestRunKilometers = longestRecent,
            targetOutlook = outlook,
            explanation = explanation,
            recommendation = recommendation(recent.size, recentKm, previousKm, recentLongest, nextPlannedRunTitle),
            recentDistanceKilometers = recentKm,
            previousDistanceKilometers = previousKm,
            recentRunCount = recent.size,
            previousRunCount = previous.size,
            recentLongestRunKilometers = recentLongest,
            dataQuality = when (estimate?.confidence) {
                null -> "Keine aktuelle, aussagekräftige Laufleistung"
                ForecastConfidence.TRAINING_ORIENTATION -> "Breite Trainingsorientierung aus ${estimate.evidenceRuns} längeren Läufen"
                ForecastConfidence.ROUGH -> "Grobe Orientierung; große Unsicherheit"
                ForecastConfidence.SUPPORTED -> "Orientierung aus ${performanceEfforts.size} gut übereinstimmenden Laufleistungen"
            },
            confidence = estimate?.confidence,
            previousLongestRunKilometers = previousLongest,
            outlookSummary = outlookSummary
        )
    }

    private fun uniqueRunningWorkouts(workouts: List<Workout>): List<Workout> = workouts
        .asSequence().filter(Workout::isRunning)
        .groupBy(Workout::sourceId)
        .values.mapNotNull { copies -> copies.maxByOrNull(Workout::endedAtMillis) }

    private fun isPerformanceEvidence(run: DatedRun): Boolean =
        run.distanceMeters >= MIN_EVIDENCE_METERS &&
            run.workout.feedback?.runningAffectedBySymptoms != de.run.app.domain.model.RunningSymptomFeedback.YES &&
            (run.workout.feedback?.perceivedEffort?.let { it >= MIN_PERCEIVED_EFFORT } == true || isExplicitPerformance(run.workout.title))

    private fun isExplicitPerformance(title: String): Boolean {
        val normalized = title.lowercase(java.util.Locale.ROOT)
        return listOf("wettkampf", "race", "zeitlauf", "testlauf", "parkrun").any(normalized::contains)
    }

    private fun estimateRange(evidence: List<DatedRun>, recentRecords: List<DatedRun>): ForecastEstimate? {
        if (evidence.isEmpty()) {
            val longerRuns = recentRecords.filter { it.distanceMeters >= MIN_TRAINING_ORIENTATION_METERS }
            if (longerRuns.size < MIN_TRAINING_ORIENTATION_RUNS) return null
            val projections = projectedTimes(longerRuns)
            if (projections.isEmpty()) return null
            val middle = median(projections)
            return ForecastEstimate(
                (middle * (1.0 - TRAINING_ORIENTATION_MARGIN)).toInt()..
                    (middle * (1.0 + TRAINING_ORIENTATION_MARGIN)).toInt(),
                ForecastConfidence.TRAINING_ORIENTATION,
                longerRuns.size
            )
        }
        val projections = projectedTimes(evidence)
        if (projections.isEmpty()) return null
        val middle = median(projections)
        val spread = if (middle > 0) (projections.last() - projections.first()) / middle else 1.0
        val confidence = if (projections.size >= 2 && spread <= MAX_SUPPORTED_SPREAD) {
            ForecastConfidence.SUPPORTED
        } else ForecastConfidence.ROUGH
        val range = if (confidence == ForecastConfidence.SUPPORTED) {
            (middle * (1.0 - SUPPORTED_MARGIN)).toInt()..(middle * (1.0 + SUPPORTED_MARGIN)).toInt()
        } else if (projections.size == 1) {
            (middle * (1.0 - ROUGH_MARGIN)).toInt()..(middle * (1.0 + ROUGH_MARGIN)).toInt()
        } else {
            (projections.first() * (1.0 - SUPPORTED_MARGIN)).toInt()..(projections.last() * (1.0 + SUPPORTED_MARGIN)).toInt()
        }
        return ForecastEstimate(range, confidence, evidence.size)
    }

    private fun projectedTimes(runs: List<DatedRun>): List<Double> = runs.mapNotNull { run ->
        val pace = run.workout.paceSecondsPerKilometer ?: return@mapNotNull null
        (pace * run.distanceMeters / 1_000.0 / 60.0) *
            (HALF_MARATHON_METERS / run.distanceMeters).pow(RIEGEL_EXPONENT)
    }.sorted()

    private fun median(sorted: List<Double>): Double = if (sorted.size % 2 == 0) {
        (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
    } else sorted[sorted.size / 2]

    private fun assessTarget(
        target: Int?, estimate: IntRange?, confidence: ForecastConfidence?, runsPerWeek: Int, longest: Double, weeks: Long,
        recentKm: Double, previousKm: Double
    ): GoalOutlook {
        if (target == null || estimate == null || confidence == ForecastConfidence.TRAINING_ORIENTATION) return GoalOutlook.NOT_ENOUGH_DATA
        if (runsPerWeek < 2 || longest < 12.0 || weeks < 4) return GoalOutlook.UNLIKELY
        return when {
            target >= estimate.last && weeks >= 12 && (previousKm <= 0.0 || recentKm >= previousKm * 0.75) ->
                if (confidence == ForecastConfidence.SUPPORTED) GoalOutlook.LIKELY else GoalOutlook.POSSIBLE
            target >= estimate.last && weeks < 8 -> GoalOutlook.UNLIKELY
            target >= estimate.first -> GoalOutlook.POSSIBLE
            target < estimate.first * 0.90 && weeks < 12 -> GoalOutlook.UNLIKELY
            else -> GoalOutlook.POSSIBLE
        }
    }

    private fun assessRunThrough(
        recentCount: Int, recentKm: Double, longest: Double, weeks: Long, previousKm: Double
    ): String = when {
        recentCount == 0 -> "Für den Ausdaueraufbau fehlen aktuelle Läufe."
        weeks < 4 -> "Für den Ausdaueraufbau bleibt nur wenig Zeit."
        recentCount < 4 -> "Noch nicht auf Kurs: Baue die Laufregelmäßigkeit weiter auf."
        longest < 8.0 -> "Noch nicht auf Kurs: Der längste Lauf liegt bei ${formatKm(longest)} km."
        previousKm > 0.0 && recentKm < previousKm * 0.65 -> "Der Umfang ist zuletzt gesunken; stabilisiere die Laufhäufigkeit."
        recentCount >= 6 && longest >= 12.0 && weeks >= 8 &&
            (previousKm <= 0.0 || recentKm >= previousKm * 0.75) -> "Der Ausdaueraufbau fürs Durchlaufen ist auf Kurs."
        else -> "Du baust die Ausdauer weiter auf; verlängere den langen Lauf schrittweise."
    }

    private fun targetSummary(outlook: GoalOutlook): String = when (outlook) {
        GoalOutlook.LIKELY -> "Dein Zeitziel ist mit den vorhandenen Daten erreichbar."
        GoalOutlook.POSSIBLE -> "Dein Zeitziel ist ambitioniert; bleib beim Trainingsplan."
        GoalOutlook.UNLIKELY -> "Dein Zeitziel ist mit dem aktuellen Stand schwer erreichbar."
        GoalOutlook.NOT_ENOUGH_DATA -> "Für eine Einschätzung des Zeitziels fehlen aussagekräftige Leistungen."
    }

    private fun buildExplanation(
        recentCount: Int, recentKm: Double, previousCount: Int, previousKm: Double,
        recentLongest: Double, longestRecent: Double, previousLongest: Double, effortCount: Int,
        estimate: IntRange?, confidence: ForecastConfidence?
    ): String {
        val volume = when {
            previousKm == 0.0 && recentKm > 0.0 -> "In den letzten vier Wochen kamen ${formatKm(recentKm)} km zusammen; davor liegen keine vergleichbaren Läufe."
            previousKm > 0.0 -> "Der Umfang lag bei ${formatKm(previousKm)} km in den vorherigen und ${formatKm(recentKm)} km in den letzten vier Wochen (${recentCount} statt $previousCount Läufe)."
            else -> "In den letzten acht Wochen sind keine ausreichenden Laufdaten erfasst."
        }
        val longRun = if (recentLongest > 0.0) "Der längste Lauf der letzten vier Wochen: ${formatKm(recentLongest)} km." else "In den letzten vier Wochen ist kein Lauf mit Distanz erfasst."
        val progression = when {
            previousLongest <= 0.0 && longestRecent > 0.0 -> "Ein Vergleich mit früheren langen Läufen fehlt noch."
            previousLongest > 0.0 && recentLongest > previousLongest -> "Der längste Lauf stieg von ${formatKm(previousLongest)} auf ${formatKm(recentLongest)} km."
            previousLongest > 0.0 && recentLongest < previousLongest -> "Der längste Lauf lag zuletzt bei ${formatKm(recentLongest)} statt ${formatKm(previousLongest)} km."
            recentLongest <= 0.0 -> "Noch kein Lauf mit Distanz zum Vergleich."
            else -> "Der längste Lauf blieb bei ${formatKm(recentLongest)} km."
        }
        val estimateText = when {
            estimate == null -> "Für eine Zeitprognose fehlen aktuelle, aussagekräftige Läufe. Lockere Trainingsläufe allein reichen dafür nicht."
            confidence == ForecastConfidence.TRAINING_ORIENTATION -> "Die breite Trainingsorientierung nutzt $effortCount längere Läufe. Trainingspace ist keine Wettkampfpace; die Spanne bleibt deshalb groß."
            confidence == ForecastConfidence.ROUGH && effortCount == 1 -> "Eine grobe Zeit-Orientierung stützt sich auf eine ausdrücklich angestrengte oder als Wettkampf/Testlauf bezeichnete Aktivität; die Unsicherheit ist groß."
            confidence == ForecastConfidence.ROUGH -> "Die aussagekräftigen Aktivitäten unterscheiden sich deutlich; deshalb ist die Zeitspanne besonders breit."
            else -> "Die Zeit-Orientierung stützt sich auf $effortCount aktuelle angestrengte oder als Wettkampf/Testlauf bezeichnete Aktivitäten."
        }
        return "$volume $longRun $progression $estimateText"
    }

    private fun recommendation(
        recentCount: Int, recentKm: Double, previousKm: Double, longestKm: Double, nextRunTitle: String?
    ): String {
        val nextStep = nextRunTitle?.let { "Als Nächstes: $it laut Wochenplan." }
            ?: "Als Nächstes die nächste verfügbare Einheit aus dem Wochenplan wählen."
        return when {
            recentCount < 4 -> "$nextStep Der letzte Monat enthält $recentCount Läufe (${formatKm(recentKm)} km); baue danach gemäß Wochenplan Regelmäßigkeit auf, zunächst zwei Läufe pro Woche."
            longestKm < 12.0 -> "$nextStep Verlängere den langen Lauf im Wochenplan schrittweise; zuletzt waren es ${formatKm(longestKm)} km."
            previousKm > 0.0 && recentKm < previousKm -> "$nextStep Stabilisiere den Umfang; er sank von ${formatKm(previousKm)} auf ${formatKm(recentKm)} km pro Vier-Wochen-Zeitraum."
            else -> "$nextStep Halte den Umfang gleichmäßig; zuletzt ${formatKm(recentKm)} km in vier Wochen."
        }
    }

    private data class DatedRun(val date: LocalDate, val distanceMeters: Double, val workout: Workout)
    private data class ForecastEstimate(val minutes: IntRange, val confidence: ForecastConfidence, val evidenceRuns: Int)

    private companion object {
        const val HALF_MARATHON_MIN = 20_000
        const val HALF_MARATHON_MAX = 22_000
        const val HALF_MARATHON_METERS = 21_097.5
        const val MIN_EVIDENCE_METERS = 5_000.0
        const val MIN_TRAINING_ORIENTATION_METERS = 8_000.0
        const val MIN_TRAINING_ORIENTATION_RUNS = 3
        const val MIN_PERCEIVED_EFFORT = 6
        const val ROUGH_MARGIN = 0.15
        const val TRAINING_ORIENTATION_MARGIN = 0.30
        const val SUPPORTED_MARGIN = 0.08
        const val MAX_SUPPORTED_SPREAD = 0.18
        const val RIEGEL_EXPONENT = 1.06
        fun formatKm(value: Double) = "%.1f".format(java.util.Locale.GERMANY, value)
    }
}
