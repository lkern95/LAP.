package de.run.app.domain

import de.run.app.domain.model.PlannedRun
import de.run.app.domain.model.PlannedRunMatchType
import de.run.app.domain.model.PlannedRunAssessment
import de.run.app.domain.model.TrainingGoal
import de.run.app.domain.model.TrainingProfile
import de.run.app.domain.model.Workout
import de.run.app.domain.model.WorkoutPlanMatch
import de.run.app.domain.model.isRunning
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

/** Matches once against the schedule that applied on the workout date. */
class PlannedWorkoutMatcher(private val planBuilder: TrainingPlanBuilder = TrainingPlanBuilder()) {
    fun match(
        workout: Workout,
        priorWorkouts: List<Workout>,
        profile: TrainingProfile,
        goal: TrainingGoal?,
        zone: ZoneId = ZoneId.systemDefault()
    ): WorkoutPlanMatch {
        if (!workout.isRunning()) return WorkoutPlanMatch(PlannedRunMatchType.AMBIGUOUS, rationale = "Aktivität ist kein Lauf.")
        val date = Instant.ofEpochMilli(workout.startedAtMillis).atZone(zone).toLocalDate()
        val before = priorWorkouts.filter { it.sourceId != workout.sourceId && it.endedAtMillis <= workout.startedAtMillis }
        val plan = planBuilder.build(profile, goal, date, before)
        val weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val candidates = plan.weeks.firstOrNull { it.startsOn == weekStart }
            ?.scheduledRuns.orEmpty()
            .filter { it.scheduledDate == date || (it.scheduledDate == null && it.day == date.dayOfWeek) }
            .map { it.run }
        return when (candidates.size) {
            1 -> candidates.single().toMatch(date)
            0 -> WorkoutPlanMatch(
                type = PlannedRunMatchType.EXTRA,
                scheduledDate = date,
                rationale = "Am Aktivitätstag ist keine Planeinheit gespeichert."
            )
            else -> WorkoutPlanMatch(
                type = PlannedRunMatchType.AMBIGUOUS,
                scheduledDate = date,
                rationale = "Für den Aktivitätstag kommen mehrere Planeinheiten infrage."
            )
        }
    }

    fun resolveForDate(
        workout: Workout,
        priorWorkouts: List<Workout>,
        profile: TrainingProfile,
        goal: TrainingGoal?,
        zone: ZoneId = ZoneId.systemDefault()
    ): WorkoutPlanMatch = workout.planMatch ?: match(workout, priorWorkouts, profile, goal, zone)

    private fun PlannedRun.toMatch(date: LocalDate) = WorkoutPlanMatch(
        type = PlannedRunMatchType.SCHEDULED,
        trainingId = trainingId ?: "lap-run-$date-${trainingType.name.lowercase()}",
        scheduledDate = scheduledDate ?: date,
        plannedTitle = title,
        plannedDurationMinutes = totalMinutes,
        paceSlowerSecondsPerKm = prescription.paceRange?.slowerSecondsPerKm,
        paceFasterSecondsPerKm = prescription.paceRange?.fasterSecondsPerKm,
        effortMinimum = prescription.effortMinimum,
        effortMaximum = prescription.effortMaximum,
        rationale = prescription.rationale
    )
}

class WorkoutPlanAssessmentAnalyzer {
    fun assess(workout: Workout, match: WorkoutPlanMatch): PlannedRunAssessment {
        if (!workout.isRunning() || match.type != PlannedRunMatchType.SCHEDULED) {
            return PlannedRunAssessment.NOT_ASSESSABLE
        }
        val plannedSeconds = match.plannedDurationMinutes?.takeIf { it > 0 }?.times(60L)
            ?: return PlannedRunAssessment.NOT_ASSESSABLE
        val durationDelta = (workout.durationSeconds - plannedSeconds).toDouble() / plannedSeconds
        if (durationDelta > HIGH_DURATION_DEVIATION || workout.feedback?.perceivedEffort?.let { it >= HIGH_EFFORT } == true) {
            return PlannedRunAssessment.HIGHER_LOAD
        }
        val paceDeviation = paceDeviation(workout.paceSecondsPerKilometer, match)
        return when {
            durationDelta < -LARGE_SHORTFALL || durationDelta > MODERATE_DURATION_DEVIATION -> PlannedRunAssessment.SOME_DEVIATION
            paceDeviation -> PlannedRunAssessment.SOME_DEVIATION
            else -> PlannedRunAssessment.WELL_MATCHED
        }
    }

    private fun paceDeviation(pace: Long?, match: WorkoutPlanMatch): Boolean {
        val value = pace?.takeIf { it > 0 }?.toInt() ?: return false
        val faster = match.paceFasterSecondsPerKm ?: return false
        val slower = match.paceSlowerSecondsPerKm ?: return false
        return value < faster - PACE_TOLERANCE_SECONDS || value > slower + PACE_TOLERANCE_SECONDS
    }

    private companion object {
        const val HIGH_DURATION_DEVIATION = 0.25
        const val MODERATE_DURATION_DEVIATION = 0.10
        const val LARGE_SHORTFALL = 0.25
        const val PACE_TOLERANCE_SECONDS = 15
        const val HIGH_EFFORT = 8
    }
}
