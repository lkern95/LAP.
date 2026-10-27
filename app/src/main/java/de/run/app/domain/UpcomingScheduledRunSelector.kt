package de.run.app.domain

import de.run.app.domain.model.ScheduledRun
import de.run.app.domain.model.TrainingPlan
import de.run.app.domain.model.TrainingWeek
import de.run.app.domain.model.WorkoutPlanMatch
import de.run.app.domain.model.PlannedRunMatchType
import java.time.LocalDate
import java.time.DayOfWeek

class UpcomingScheduledRunSelector {
    fun selectFlexible(week: TrainingWeek, today: LocalDate, completedRunCount: Int): ScheduledRun? {
        val requiredRuns = week.runs.filterNot { it.optional }
        val nextRun = requiredRuns.getOrNull(completedRunCount)
            ?: (if (completedRunCount >= week.requiredRunCount) week.runs.firstOrNull { it.optional } else null)
            ?: return null
        return ScheduledRun(today.dayOfWeek, nextRun, today)
    }

    fun select(
        plan: TrainingPlan,
        today: LocalDate,
        completedMatches: List<WorkoutPlanMatch> = emptyList()
    ): ScheduledRun? {
        val completedIds = completedMatches.filter { it.type == PlannedRunMatchType.SCHEDULED }
            .mapNotNull { it.trainingId }
            .toSet()
        val completedDates = completedMatches.filter { it.type == PlannedRunMatchType.SCHEDULED }
            .mapNotNull { it.scheduledDate }
            .toSet()
        return plan.weeks
        .flatMap { week ->
            week.scheduledRuns.map { scheduled ->
                val date = scheduled.scheduledDate ?: week.startsOn.plusDays((scheduled.day.value - 1).toLong())
                scheduled.copy(scheduledDate = date)
            }
        }
        .filter { scheduled ->
            val date = scheduled.scheduledDate ?: return@filter false
            !date.isBefore(today) &&
                scheduled.run.trainingId !in completedIds && date !in completedDates
        }
        .minByOrNull { scheduled -> scheduled.scheduledDate?.toEpochDay() ?: Long.MAX_VALUE }
    }
}
