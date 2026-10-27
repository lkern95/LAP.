package de.run.app.domain

import de.run.app.domain.model.PlannedRun
import de.run.app.domain.model.PlannedRunAssessment
import de.run.app.domain.model.PlannedRunMatchType
import de.run.app.domain.model.TrainingGoal
import de.run.app.domain.model.TrainingProfile
import de.run.app.domain.model.TrainingPlan
import de.run.app.domain.model.Workout
import de.run.app.domain.model.WorkoutPlanMatch
import de.run.app.domain.model.isRunning
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class WorkoutPlanStatus { WELL_MATCHED, SOME_DEVIATION, HIGHER_LOAD, EXTRA_RUN, NOT_ASSESSABLE }

data class TrainingFeedback(
    val shortSummary: String,
    val status: WorkoutPlanStatus,
    val goalSummary: String,
    val reasons: List<String>,
    val nextAction: String,
    val weeklyFocus: String,
    val weeklyPlanProgress: WeeklyPlanProgress? = null,
    val weeklyComparison: WeeklyTrainingComparison? = null,
    val plannedRun: PlannedRun? = null,
    val planChangeSummary: String? = null,
    val qualityHeadline: String = ""
)

data class WeeklyPlanProgress(
    val startsOn: LocalDate,
    val completedRuns: Int,
    val plannedRuns: Int,
    val extraRuns: Int
)

/** Turns imported activity, the saved goal and the current plan into brief, evidence-based feedback. */
class TrainingFeedbackEngine(
    private val forecastAnalyzer: HalfMarathonForecastAnalyzer = HalfMarathonForecastAnalyzer(),
    private val weeklyAnalyzer: WeeklyTrainingComparisonAnalyzer = WeeklyTrainingComparisonAnalyzer()
) {
    private val assessmentAnalyzer = WorkoutPlanAssessmentAnalyzer()
    private val plannedWorkoutMatcher = PlannedWorkoutMatcher()
    private val planBuilder = TrainingPlanBuilder()

    fun analyze(
        workout: Workout,
        workouts: List<Workout>,
        goal: TrainingGoal?,
        plan: TrainingPlan?,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
        profile: TrainingProfile? = null
    ): TrainingFeedback {
        val workoutDate = Instant.ofEpochMilli(workout.startedAtMillis).atZone(zone).toLocalDate()
        val planned = plannedRunOn(plan, workoutDate)
        val savedMatch = workout.planMatch
        val originalPlannedRun = originalPlannedRun(workout, workouts, profile, goal, plan, zone)
        val status = status(workout, planned, savedMatch)
        val analyzedWorkouts = (workouts + workout).distinctBy(Workout::sourceId)
        val weeklyComparison = weeklyAnalyzer.analyze(analyzedWorkouts, today, zone)
        val weeklyPlanProgress = currentWeekProgress(analyzedWorkouts, plan, profile, goal, today, zone)
        val nextDate = if (today.isAfter(workoutDate.plusDays(1))) today else workoutDate.plusDays(1)
        val completedRuns = weeklyPlanProgress?.completedRuns ?: 0
        val next = nextPlannedRun(plan, nextDate, completedRuns)
        val forecast = forecastAnalyzer.analyze(goal, analyzedWorkouts, today, zone, next?.title)
        val reasons = buildList {
            val kilometers = workout.distanceMeters?.takeIf { it.isFinite() && it > 0 }?.div(1_000.0)
            if (kilometers != null) add("Du bist ${"%.1f".format(java.util.Locale.GERMANY, kilometers)} km gelaufen.")
            addActualValues(workout, this)
            val distanceAssessment = RunDistanceSpikeAnalyzer().assessCompleted(workout, workouts + workout, zone)
            if (distanceAssessment.level != de.run.app.domain.model.DistanceSpikeLevel.NO_WARNING &&
                distanceAssessment.level != de.run.app.domain.model.DistanceSpikeLevel.NOT_ASSESSABLE
            ) {
                val referenceKm = (distanceAssessment.referenceDistanceMeters ?: 0.0) / 1_000.0
                val actualKm = (distanceAssessment.runDistanceMeters ?: 0.0) / 1_000.0
                val increase = distanceAssessment.increaseMaximumPercent ?: 0
                val levelText = when (distanceAssessment.level) {
                    de.run.app.domain.model.DistanceSpikeLevel.NOTICE -> "auffällig"
                    de.run.app.domain.model.DistanceSpikeLevel.WARNING -> "deutlich"
                    de.run.app.domain.model.DistanceSpikeLevel.STRONG_WARNING -> "sehr groß"
                    else -> ""
                }
                add("Distanzvergleich der letzten 30 Tage: ${"%.1f".format(java.util.Locale.GERMANY, actualKm)} km gegenüber deinem längsten Lauf von ${"%.1f".format(java.util.Locale.GERMANY, referenceKm)} km ($increase % mehr, $levelText). Das ist ein Hinweis, keine Trainingssperre.")
            }
            when (savedMatch?.type) {
                PlannedRunMatchType.SCHEDULED -> add("Geplant: ${savedMatch.plannedTitle} · ${savedMatch.plannedDurationMinutes} Min. Die Zuordnung wurde vor der Planneuberechnung gesichert.")
                PlannedRunMatchType.EXTRA -> add("Dieser Lauf ist als Zusatzlauf gespeichert und erfüllt keine Planeinheit.")
                PlannedRunMatchType.AMBIGUOUS -> add("Für diesen Lauf ließ sich keine sichere Planeinheit auswählen; er erfüllt keine Planeinheit.")
                null -> if (planned != null) add("Der Lauf war für heute mit ${planned.totalMinutes} Minuten geplant.")
                    else add("Der Lauf passt zu keiner für heute gespeicherten Planeinheit und zählt daher als Zusatzlauf.")
            }
            if (savedMatch?.paceFasterSecondsPerKm != null && savedMatch.paceSlowerSecondsPerKm != null) {
                add("Vergleichs-Pace: ${formatPace(savedMatch.paceFasterSecondsPerKm)}–${formatPace(savedMatch.paceSlowerSecondsPerKm)} min/km; nur der Gesamtdurchschnitt liegt vor.")
            } else {
                add("Für eine persönliche Pacevorgabe fehlen genügend vergleichbare lockere Läufe.")
            }
            if (workout.averageHeartRateBpm != null) {
                add("Durchschnittspuls: ${workout.averageHeartRateBpm} bpm. Eine persönliche Pulszone wird mangels belastbarer Schwellenwerte nicht vorgegeben.")
            } else add("Kein Herzfrequenzwert erfasst; der Puls wurde nicht bewertet.")
            forecast?.let {
                if (it.recentRunCount > 0) add("In den letzten vier Wochen sind ${it.recentRunCount} Läufe mit ${"%.1f".format(java.util.Locale.GERMANY, it.recentDistanceKilometers)} km gespeichert.")
                if (it.evidenceRuns == 0) add("Für eine belastbare Halbmarathon-Leistung fehlen geeignete intensive Leistungsnachweise.")
            }
            add("Laufhäufigkeit in den letzten vollständigen Wochen: ${weeklyComparison.previous.runCount} → ${weeklyComparison.current.runCount} Läufe.")
            weeklyComparison.distanceChangePercent?.let { change -> add("Wochenumfang: ${"%.1f".format(java.util.Locale.GERMANY, (weeklyComparison.previous.distanceMeters ?: 0.0) / 1_000.0)} → ${"%.1f".format(java.util.Locale.GERMANY, (weeklyComparison.current.distanceMeters ?: 0.0) / 1_000.0)} km ($change %).") }
        }
        val nextAction = when {
            workout.feedback?.runningAffectedBySymptoms == de.run.app.domain.model.RunningSymptomFeedback.YES -> "Wegen deiner gemeldeten Beschwerden: heute keine Tempoeinheit; bei anhaltenden Beschwerden pausieren."
            status == WorkoutPlanStatus.HIGHER_LOAD && next != null -> "Als Nächstes: ${next.title} für ${next.totalMinutes} Minuten. Wegen der höheren Belastung bleibt die Einheit sehr leicht."
            status == WorkoutPlanStatus.HIGHER_LOAD -> "Als Nächstes Erholung einplanen; eine Tempoeinheit wird nicht empfohlen."
            status == WorkoutPlanStatus.EXTRA_RUN && weeklyPlanProgress != null &&
                weeklyPlanProgress.completedRuns >= weeklyPlanProgress.plannedRuns ->
                "Als Nächstes: Erholung. Die geplanten Läufe dieser Woche sind erledigt."
            next != null -> "Als Nächstes: ${next.title} für ${next.totalMinutes} Minuten laut Wochenplan."
            else -> forecast?.recommendation ?: "Orientiere dich an der nächsten Einheit in deinem Wochenplan."
        }
        return TrainingFeedback(
            shortSummary = summary(status, workout, savedMatch, originalPlannedRun),
            status = status,
            goalSummary = goalSummary(goal, forecast),
            reasons = reasons,
            nextAction = nextAction,
            weeklyFocus = weeklyFocus(analyzedWorkouts, today, zone, plan),
            weeklyPlanProgress = weeklyPlanProgress,
            weeklyComparison = weeklyComparison,
            plannedRun = originalPlannedRun,
            planChangeSummary = planChangeSummary(status, workout, savedMatch, next, plan),
            qualityHeadline = qualityHeadline(status, workout)
        )
    }

    private fun originalPlannedRun(
        workout: Workout,
        workouts: List<Workout>,
        profile: TrainingProfile?,
        goal: TrainingGoal?,
        currentPlan: TrainingPlan?,
        zone: ZoneId
    ): PlannedRun? {
        val match = workout.planMatch ?: return plannedRunOn(
            currentPlan,
            Instant.ofEpochMilli(workout.startedAtMillis).atZone(zone).toLocalDate()
        )
        if (match.type != PlannedRunMatchType.SCHEDULED) return null
        val date = match.scheduledDate ?: Instant.ofEpochMilli(workout.startedAtMillis).atZone(zone).toLocalDate()
        val plan = if (profile == null) currentPlan else {
            val prior = workouts.filter { it.sourceId != workout.sourceId && it.endedAtMillis <= workout.startedAtMillis }
            planBuilder.build(profile, goal, date, prior)
        }
        return plan?.weeks
            ?.firstOrNull { it.startsOn == date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
            ?.scheduledRuns
            ?.firstOrNull { it.scheduledDate == date && it.run.trainingId == match.trainingId }
            ?.run
    }

    private fun currentWeekProgress(
        workouts: List<Workout>,
        plan: TrainingPlan?,
        profile: TrainingProfile?,
        goal: TrainingGoal?,
        today: LocalDate,
        zone: ZoneId
    ): WeeklyPlanProgress? {
        val week = plan?.weeks?.firstOrNull { it.startsOn == today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
            ?: return null
        val trainingProfile = profile ?: return null
        val completed = WorkoutPlanCompletionAnalyzer().completedRuns(workouts, week, trainingProfile, goal, zone)
        val weekWorkouts = workouts.filter { workout ->
            if (!workout.isRunning()) return@filter false
            val date = Instant.ofEpochMilli(workout.startedAtMillis).atZone(zone).toLocalDate()
            !date.isBefore(week.startsOn) && date.isBefore(week.startsOn.plusWeeks(1))
        }
        val extraRuns = weekWorkouts.count { workout ->
            val match = workout.planMatch ?: plannedWorkoutMatcher.match(
                workout,
                workouts.filter { it.sourceId != workout.sourceId && it.endedAtMillis <= workout.startedAtMillis },
                trainingProfile,
                goal,
                zone
            )
            match.type == PlannedRunMatchType.EXTRA
        }
        return WeeklyPlanProgress(week.startsOn, completed, week.requiredRunCount, extraRuns)
    }

    private fun status(workout: Workout, planned: PlannedRun?, savedMatch: WorkoutPlanMatch?): WorkoutPlanStatus {
        if (!workout.isRunning()) return WorkoutPlanStatus.NOT_ASSESSABLE
        savedMatch?.let { match ->
            return when (match.type) {
                PlannedRunMatchType.EXTRA -> WorkoutPlanStatus.EXTRA_RUN
                PlannedRunMatchType.AMBIGUOUS -> WorkoutPlanStatus.NOT_ASSESSABLE
                PlannedRunMatchType.SCHEDULED -> when (match.assessment ?: assessmentAnalyzer.assess(workout, match)) {
                    PlannedRunAssessment.WELL_MATCHED -> WorkoutPlanStatus.WELL_MATCHED
                    PlannedRunAssessment.SOME_DEVIATION -> WorkoutPlanStatus.SOME_DEVIATION
                    PlannedRunAssessment.HIGHER_LOAD -> WorkoutPlanStatus.HIGHER_LOAD
                    PlannedRunAssessment.NOT_ASSESSABLE -> WorkoutPlanStatus.NOT_ASSESSABLE
                }
            }
        }
        if (planned == null) return WorkoutPlanStatus.EXTRA_RUN
        val match = WorkoutPlanMatch(
            type = PlannedRunMatchType.SCHEDULED,
            plannedDurationMinutes = planned.totalMinutes,
            paceSlowerSecondsPerKm = planned.prescription.paceRange?.slowerSecondsPerKm,
            paceFasterSecondsPerKm = planned.prescription.paceRange?.fasterSecondsPerKm,
            effortMinimum = planned.prescription.effortMinimum,
            effortMaximum = planned.prescription.effortMaximum
        )
        return when (assessmentAnalyzer.assess(workout, match)) {
            PlannedRunAssessment.WELL_MATCHED -> WorkoutPlanStatus.WELL_MATCHED
            PlannedRunAssessment.SOME_DEVIATION -> WorkoutPlanStatus.SOME_DEVIATION
            PlannedRunAssessment.HIGHER_LOAD -> WorkoutPlanStatus.HIGHER_LOAD
            PlannedRunAssessment.NOT_ASSESSABLE -> WorkoutPlanStatus.NOT_ASSESSABLE
        }
    }

    private fun plannedRunOn(plan: TrainingPlan?, date: LocalDate): PlannedRun? = plan?.weeks
        ?.firstOrNull { it.startsOn == date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
        ?.scheduledRuns?.firstOrNull { it.day == date.dayOfWeek }?.run

    private fun nextPlannedRun(
        plan: TrainingPlan?,
        fromDate: LocalDate,
        completedRunsInCurrentWeek: Int
    ): PlannedRun? {
        val weekStart = fromDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val currentWeek = plan?.weeks?.firstOrNull { it.startsOn == weekStart }
        val nextScheduled = currentWeek?.scheduledRuns
            ?.map { run -> (run.scheduledDate ?: weekStart.with(TemporalAdjusters.nextOrSame(run.day))) to run.run }
            ?.filter { (date, _) -> !date.isBefore(fromDate) }
            ?.minByOrNull { it.first.toEpochDay() }
            ?.second
        if (nextScheduled != null) return nextScheduled

        currentWeek?.runs?.getOrNull(completedRunsInCurrentWeek)?.let { return it }
        return plan?.weeks
            ?.filter { it.startsOn.isAfter(weekStart) }
            ?.sortedBy { it.startsOn }
            ?.firstNotNullOfOrNull { week ->
                week.scheduledRuns.minByOrNull { run ->
                    run.scheduledDate?.toEpochDay() ?: run.day.value.toLong()
                }?.run ?: week.runs.firstOrNull()
            }
    }

    private fun weeklyFocus(workouts: List<Workout>, today: LocalDate, zone: ZoneId, plan: TrainingPlan?): String {
        val comparison = weeklyAnalyzer.analyze(workouts, today, zone)
        val current = comparison.current
        val previous = comparison.previous
        val next = nextPlannedRun(plan, today.plusWeeks(1), completedRunsInCurrentWeek = 0)
        return when {
            current.runCount == 0 -> "Für die letzte vollständige Woche liegt kein Lauf vor; nächste Woche die geplante Einheit${next?.let { " ${it.title}" } ?: ""} umsetzen."
            comparison.distanceChangePercent != null && comparison.distanceChangePercent > 20 ->
                "Der Wochenumfang stieg um ${comparison.distanceChangePercent} %; halte ihn nächste Woche stabil."
            previous.runCount > current.runCount ->
                "Die Laufhäufigkeit sank von ${previous.runCount} auf ${current.runCount}; setze mit ${next?.title ?: "der nächsten Planeinheit"} fort."
            next != null -> "Nächste Woche ${next.title} für ${next.totalMinutes} Minuten laut Plan absolvieren."
            else -> comparison.feedback
        }
    }

    private fun summary(
        status: WorkoutPlanStatus,
        workout: Workout,
        match: WorkoutPlanMatch?,
        plannedRun: PlannedRun?
    ): String = when (status) {
        WorkoutPlanStatus.WELL_MATCHED -> when {
            (match?.plannedDurationMinutes ?: plannedRun?.totalMinutes) != null ->
                "Geplant ${match?.plannedDurationMinutes ?: plannedRun?.totalMinutes} Min., gelaufen ${workout.durationSeconds / 60} Min. Die Dauer passt; " +
                    (workout.feedback?.perceivedEffort?.let { "Anstrengung: $it von 10." } ?: "deine Anstrengung ist noch offen.")
            else -> "Die Dauer passt. Wie anstrengend es war, ist noch offen."
        }
        WorkoutPlanStatus.SOME_DEVIATION -> when {
            durationDeviation(workout, match) -> durationSummary(workout, match)
            paceDeviation(workout, match) -> "Das Tempo lag außerhalb deiner persönlichen Vorgabe."
            else -> "Der Lauf wich von deiner Planung ab."
        }
        WorkoutPlanStatus.HIGHER_LOAD -> when {
            durationDeviation(workout, match, highLoad = true) -> durationSummary(workout, match) + " Das war deutlich länger als geplant."
            workout.feedback?.perceivedEffort?.let { it >= 8 } == true -> "Du hast die Belastung als sehr hoch eingeschätzt."
            else -> "Der Lauf war anstrengender als geplant."
        }
        WorkoutPlanStatus.EXTRA_RUN -> extraRunSummary(workout)
        WorkoutPlanStatus.NOT_ASSESSABLE -> "Der Lauf lässt sich keiner geplanten Einheit zuordnen."
    }

    private fun qualityHeadline(status: WorkoutPlanStatus, workout: Workout): String = when (status) {
        WorkoutPlanStatus.EXTRA_RUN -> when {
            workout.feedback?.runningAffectedBySymptoms == de.run.app.domain.model.RunningSymptomFeedback.YES -> "Beschwerden gemeldet"
            workout.feedback?.perceivedEffort == null -> "Anstrengung offen"
            workout.feedback.perceivedEffort <= 4 -> "Locker empfunden"
            workout.feedback.perceivedEffort >= 8 -> "Sehr anstrengend"
            else -> "Belastung eingeschätzt"
        }
        else -> ""
    }

    private fun extraRunSummary(workout: Workout): String {
        val minutes = workout.durationSeconds / 60
        val effort = workout.feedback?.perceivedEffort?.let { "Anstrengung: $it von 10" }
        val symptoms = when (workout.feedback?.runningAffectedBySymptoms) {
            de.run.app.domain.model.RunningSymptomFeedback.NO -> "keine Beschwerden gemeldet"
            de.run.app.domain.model.RunningSymptomFeedback.YES -> "Beschwerden gemeldet"
            de.run.app.domain.model.RunningSymptomFeedback.UNSURE -> "Beschwerden unklar"
            null -> "Beschwerden nicht erfasst"
        }
        val assessment = listOfNotNull(effort, symptoms).joinToString(" · ")
        return if (effort == null && workout.feedback?.runningAffectedBySymptoms == null) {
            "$minutes Min. zusätzlich gelaufen. Anstrengung und Beschwerden sind noch offen."
        } else "$minutes Min. zusätzlich gelaufen. $assessment."
    }

    private fun durationSummary(workout: Workout, match: WorkoutPlanMatch?): String {
        val planned = match?.plannedDurationMinutes ?: return "Dauer gemessen ${workout.durationSeconds / 60} Min."
        val actual = (workout.durationSeconds / 60).toInt()
        val difference = actual - planned
        return "Geplant $planned Min., gemessen $actual Min.: ${if (difference >= 0) "+" else ""}$difference Min."
    }

    private fun planChangeSummary(
        status: WorkoutPlanStatus,
        workout: Workout,
        match: WorkoutPlanMatch?,
        next: PlannedRun?,
        plan: TrainingPlan?
    ): String? {
        if (status != WorkoutPlanStatus.HIGHER_LOAD || plan?.adjustment != de.run.app.domain.model.PlanAdjustment.RECOVER_AFTER_HIGH_LOAD) return null
        val planned = match?.plannedDurationMinutes ?: return null
        val extra = (workout.durationSeconds / 60).toInt() - planned
        if (extra <= 0 || next == null) return null
        return "Dein Lauf dauerte $extra Min. länger als geplant. Darum wurde die nächste Einheit „${next.title}“ auf ${next.totalMinutes} Min. verkürzt."
    }

    private fun durationDeviation(workout: Workout, match: WorkoutPlanMatch?, highLoad: Boolean = false): Boolean {
        val planned = match?.plannedDurationMinutes?.takeIf { it > 0 }?.times(60L) ?: return false
        val delta = (workout.durationSeconds - planned).toDouble() / planned
        return if (highLoad) delta > 0.25 else delta < -0.25 || delta > 0.10
    }

    private fun paceDeviation(workout: Workout, match: WorkoutPlanMatch?): Boolean {
        val pace = workout.paceSecondsPerKilometer?.takeIf { it > 0 } ?: return false
        val faster = match?.paceFasterSecondsPerKm ?: return false
        val slower = match.paceSlowerSecondsPerKm ?: return false
        return pace < faster - 15 || pace > slower + 15
    }

    private fun goalSummary(goal: TrainingGoal?, forecast: HalfMarathonForecast?): String {
        if (goal?.raceGoal == de.run.app.domain.model.RaceGoal.RUN_THROUGH) {
            if (forecast == null || forecast.recentRunCount == 0) return "Noch nicht einschätzbar: Es fehlen regelmäßige Laufdaten."
            val longest = "%.1f".format(java.util.Locale.GERMANY, forecast.recentLongestRunKilometers)
            val runLabel = if (forecast.recentRunCount == 1) "Lauf" else "Läufe"
            return if (forecast.recentRunCount >= 6 && forecast.recentLongestRunKilometers >= 12.0 && forecast.weeksRemaining >= 8) {
                "Die Regelmäßigkeit stimmt: ${forecast.recentRunCount} $runLabel in vier Wochen, längster Lauf $longest km; noch ${forecast.weeksRemaining} Wochen zum Ausdaueraufbau."
            } else {
                "Noch nicht sicher auf Kurs: ${forecast.recentRunCount} $runLabel in vier Wochen, längster Lauf $longest km; ${forecast.weeksRemaining} Wochen bleiben."
            }
        }
        if (goal?.targetTimeMinutes == null) return "Ohne Zeitziel keine Zeitbewertung. Regelmäßigkeit und Ausdauer entscheiden über das Durchlaufen."
        return when (forecast?.targetOutlook) {
        GoalOutlook.LIKELY -> "Dein Halbmarathonziel ist mit den vorhandenen Daten erreichbar."
        GoalOutlook.POSSIBLE -> "Dein Ziel ist ambitioniert; halte dich an den Plan."
        GoalOutlook.UNLIKELY -> "Dein Ziel wirkt derzeit schwer erreichbar."
        GoalOutlook.NOT_ENOUGH_DATA, null -> "Für eine verlässliche Einschätzung fehlen Vergleichsläufe."
        }
    }

    private fun addActualValues(workout: Workout, reasons: MutableList<String>) {
        reasons += "Ist-Dauer: ${workout.durationSeconds / 60} Min."
        workout.paceSecondsPerKilometer?.let { reasons += "Durchschnittspace: ${formatPace(it.toInt())} min/km; einzelne Abschnitte sind nicht verfügbar." }
            ?: run { reasons += "Keine Pace erfasst; ein Pacevergleich ist nicht möglich." }
        workout.averageHeartRateBpm?.let { reasons += "Durchschnittspuls: $it bpm." }
        if (workout.feedback?.perceivedEffort != null) reasons += "Deine Anstrengungseinschätzung: ${workout.feedback.perceivedEffort} von 10."
        if (workout.feedback?.runningAffectedBySymptoms != null) reasons += "Deine Rückmeldung zu Beschwerden wurde berücksichtigt."
    }

    private fun formatPace(seconds: Int): String =
        "%d:%02d".format(java.util.Locale.GERMANY, seconds / 60, seconds % 60)
}
