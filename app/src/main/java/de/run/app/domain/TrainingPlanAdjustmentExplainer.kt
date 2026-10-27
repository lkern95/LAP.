package de.run.app.domain

import de.run.app.domain.model.PlanAdjustment
import de.run.app.domain.model.RunningSymptomFeedback
import de.run.app.domain.model.TrainingProgress
import de.run.app.domain.model.Workout
import de.run.app.domain.model.isRunning
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Builds short explanations from the same values used by the plan decision. */
class TrainingPlanAdjustmentExplainer {
    fun explain(
        adjustment: PlanAdjustment,
        progress: TrainingProgress,
        workouts: List<Workout>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): String? = when (adjustment) {
        PlanAdjustment.CONTINUE -> null
        PlanAdjustment.NO_RUN_DATA -> "Es sind noch keine abgeschlossenen Läufe gespeichert. Deshalb baut LAP vorsichtig auf."
        PlanAdjustment.RETURN_GENTLY -> {
            val days = progress.lastRunDate?.let { java.time.temporal.ChronoUnit.DAYS.between(it, today) }
            "Dein letzter gespeicherter Lauf liegt ${days ?: "unbekannt viele"} Tage zurück. LAP setzt den Aufbau deshalb vorsichtig fort."
        }
        PlanAdjustment.REPEAT_WEEK -> "In deiner letzten vollständigen Woche sind ${progress.runsPreviousWeek} Läufe gespeichert. LAP wiederholt den aktuellen Aufbau, statt ihn zu steigern."
        PlanAdjustment.REVIEW_REPORTED_SYMPTOMS -> "Du hast Beschwerden beim Laufen gemeldet. LAP setzt den Belastungsaufbau aus und plant vorsichtiger."
        PlanAdjustment.REPEAT_AFTER_HIGH_EFFORT -> highEffortExplanation(workouts, today, zone)
        PlanAdjustment.RECOVER_AFTER_HIGH_LOAD -> highLoadExplanation(workouts)
        PlanAdjustment.HOLD_AFTER_SLOWING -> slowdownExplanation(workouts, today, zone)
    }

    private fun highEffortExplanation(workouts: List<Workout>, today: LocalDate, zone: ZoneId): String {
        val matchingPair = recentRuns(workouts, today, zone).filter {
            it.feedback?.perceivedEffort?.let { effort -> effort >= HIGH_EFFORT } == true
        }.sortedBy(Workout::durationSeconds).zipWithNext().firstOrNull { (first, second) ->
            first.durationSeconds > 0 &&
                second.durationSeconds <= first.durationSeconds * (1.0 + DURATION_TOLERANCE) &&
                first.durationSeconds >= second.durationSeconds * (1.0 - DURATION_TOLERANCE)
        }
        return matchingPair?.let { (first, second) ->
            val firstEffort = first.feedback?.perceivedEffort
            val secondEffort = second.feedback?.perceivedEffort
            "Bei zwei ähnlich langen Läufen wurde Anstrengung $firstEffort und $secondEffort von 10 eingetragen (ab 7 gilt als hoch). LAP steigert den Umfang deshalb nicht."
        } ?: "Mehrere ähnlich lange Läufe waren mit RPE 7 oder höher anstrengend. LAP steigert den Umfang deshalb nicht."
    }

    private fun highLoadExplanation(workouts: List<Workout>): String {
        val workout = workouts.filter(Workout::isRunning)
            .filter { it.planMatch?.assessment == de.run.app.domain.model.PlannedRunAssessment.HIGHER_LOAD }
            .maxByOrNull(Workout::endedAtMillis)
        val match = workout?.planMatch
        val planned = match?.plannedDurationMinutes
        val actual = workout?.durationSeconds?.let { (it / 60).toInt() }
        return if (planned != null && actual != null && planned > 0) {
            "Dein Lauf dauerte $actual Min. statt geplanter $planned Min. (mehr als 25 % länger). LAP plant die nächste Einheit leichter."
        } else "Ein gespeicherter Lauf wurde als deutlich höher belastend als geplant bewertet. LAP plant die nächste Einheit leichter."
    }

    private fun slowdownExplanation(workouts: List<Workout>, today: LocalDate, zone: ZoneId): String {
        val weekStart = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val previousStart = weekStart.minusWeeks(2)
        val comparable = recentRuns(workouts, today, zone).filter { workout ->
            val date = Instant.ofEpochMilli(workout.startedAtMillis).atZone(zone).toLocalDate()
            date >= previousStart && workout.distanceMeters?.let { it >= MIN_COMPARABLE_DISTANCE_METERS } == true &&
                workout.paceSecondsPerKilometer != null &&
                workout.feedback?.perceivedEffort?.let(EASY_TO_MODERATE_EFFORT::contains) == true &&
                workout.feedback?.runningAffectedBySymptoms == RunningSymptomFeedback.NO
        }
        val previous = comparable.filter {
            Instant.ofEpochMilli(it.startedAtMillis).atZone(zone).toLocalDate() < weekStart.minusWeeks(1)
        }
        val current = comparable.filter {
            Instant.ofEpochMilli(it.startedAtMillis).atZone(zone).toLocalDate() >= weekStart.minusWeeks(1)
        }
        val previousPace = previous.mapNotNull(Workout::paceSecondsPerKilometer).average()
        val currentPace = current.mapNotNull(Workout::paceSecondsPerKilometer).average()
        val comparableLoad = previous.size >= MIN_WEEKLY_RUNS && current.size >= MIN_WEEKLY_RUNS &&
            previous.mapNotNull(Workout::distanceMeters).average().let { old ->
                val new = current.mapNotNull(Workout::distanceMeters).average()
                old > 0 && new > 0 && kotlin.math.abs(new - old) / old <= DISTANCE_SIMILARITY
            } &&
            kotlin.math.abs(
                current.mapNotNull { it.feedback?.perceivedEffort }.average() -
                    previous.mapNotNull { it.feedback?.perceivedEffort }.average()
            ) <= EFFORT_SIMILARITY
        return if (comparableLoad && previousPace > 0 && currentPace >= previousPace * SLOWDOWN_THRESHOLD) {
            "Bei vergleichbaren Läufen wurde deine Durchschnittspace langsamer (${formatPace(previousPace)} auf ${formatPace(currentPace)} min/km). LAP hält den Aufbau zurück."
        } else "Bei vergleichbaren Läufen wurde eine langsamere Pace erkannt. LAP hält den Aufbau zurück und plant leichter."
    }

    private fun recentRuns(workouts: List<Workout>, today: LocalDate, zone: ZoneId): List<Workout> =
        workouts.filter(Workout::isRunning).groupBy(Workout::sourceId).values
            .map { versions -> versions.maxBy(Workout::endedAtMillis) }.filter { workout ->
            val date = Instant.ofEpochMilli(workout.startedAtMillis).atZone(zone).toLocalDate()
            !date.isAfter(today)
        }.sortedBy(Workout::endedAtMillis)

    private fun formatPace(seconds: Double): String =
        "%d:%02d".format(java.util.Locale.GERMANY, seconds.toInt() / 60, seconds.toInt() % 60)

    private companion object {
        const val HIGH_EFFORT = 7
        const val DURATION_TOLERANCE = 0.20
        const val MIN_COMPARABLE_DISTANCE_METERS = 3_000.0
        const val MIN_WEEKLY_RUNS = 2
        const val DISTANCE_SIMILARITY = 0.20
        const val EFFORT_SIMILARITY = 1.0
        const val SLOWDOWN_THRESHOLD = 1.07
        val EASY_TO_MODERATE_EFFORT = 2..6
    }
}
