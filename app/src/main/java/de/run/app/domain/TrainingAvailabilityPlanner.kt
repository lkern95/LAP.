package de.run.app.domain

import de.run.app.domain.model.AvailabilityMode
import de.run.app.domain.model.AvailabilityStatus
import de.run.app.domain.model.PlannedRun
import de.run.app.domain.model.ScheduledRun
import de.run.app.domain.model.TrainingProfile
import de.run.app.domain.model.TrainingWeek
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal class TrainingAvailabilityPlanner {
    private val durationAdjuster = TrainingRunDurationAdjuster()
    fun buildWeek(
        profile: TrainingProfile,
        startsOn: LocalDate,
        today: LocalDate,
        previousRunDate: LocalDate?,
        weekNumber: Int,
        longRunMinutes: Int,
        isRecoveryWeek: Boolean,
        candidateRuns: List<PlannedRun>
    ): TrainingWeek {
        val dayStatuses = DayOfWeek.entries.associateWith { day ->
            statusFor(profile, startsOn.plusDays((day.value - 1).toLong()))
        }
        val eligibleDates = DayOfWeek.entries.map { startsOn.plusDays((it.value - 1).toLong()) }
            .filter { !it.isBefore(today) && statusFor(profile, it) == AvailabilityStatus.AVAILABLE }
        val selectedDates = chooseDates(
            eligibleDates,
            candidateRuns.size,
            previousRunDate,
            profile.longRunDay
        )
        val selectedRuns = candidateRuns.take(selectedDates.size)
        val scheduled = schedule(profile, selectedDates, selectedRuns)
        val scheduledLongRunMinutes = scheduled.firstOrNull {
            it.run.title.contains("langer", ignoreCase = true)
        }?.run?.totalMinutes ?: longRunMinutes
        return TrainingWeek(
            number = weekNumber,
            startsOn = startsOn,
            longRunMinutes = scheduledLongRunMinutes,
            strengthSessions = profile.strengthSessionsPerWeek,
            runs = scheduled.map(ScheduledRun::run).ifEmpty {
                if (profile.availabilityMode == AvailabilityMode.VARIABLE) {
                    flexibleSuggestions(profile, candidateRuns)
                } else candidateRuns
            },
            isRecoveryWeek = isRecoveryWeek,
            scheduledRuns = scheduled,
            availability = dayStatuses
        )
    }

    private fun statusFor(profile: TrainingProfile, date: LocalDate): AvailabilityStatus = when (profile.availabilityMode) {
        AvailabilityMode.RECURRING -> if (date.dayOfWeek in profile.availableRunDays) {
            AvailabilityStatus.AVAILABLE
        } else {
            AvailabilityStatus.UNAVAILABLE
        }
        AvailabilityMode.VARIABLE -> AvailabilityStatus.UNKNOWN
    }

    private fun flexibleSuggestions(profile: TrainingProfile, runs: List<PlannedRun>): List<PlannedRun> =
        runs.map { run ->
            val maximumMinutes = if (run.title.contains("langer", ignoreCase = true)) {
                profile.occasionalRunTimeLimitMinutes
            } else {
                profile.usualRunTimeLimitMinutes
            }
            maximumMinutes?.let { durationAdjuster.fitToAvailableTime(run, it) } ?: run
        }

    private fun chooseDates(
        dates: List<LocalDate>,
        requestedCount: Int,
        previousRunDate: LocalDate?,
        preferredLongRunDay: DayOfWeek?
    ): List<LocalDate> {
        for (count in requestedCount.coerceAtMost(dates.size) downTo 1) {
            val combinations = combinations(dates.sorted(), count)
                .filter { subset -> spacedFromPrevious(subset, previousRunDate) && spacedWithin(subset) }
            if (combinations.isNotEmpty()) {
                return combinations.maxWithOrNull(
                    compareBy<List<LocalDate>> { set -> preferredLongRunDay != null && set.any { it.dayOfWeek == preferredLongRunDay } }
                        .thenBy { set -> set.lastOrNull()?.toEpochDay() ?: 0L }
                ).orEmpty()
            }
        }
        return emptyList()
    }

    private fun schedule(profile: TrainingProfile, dates: List<LocalDate>, runs: List<PlannedRun>): List<ScheduledRun> {
        if (dates.isEmpty() || runs.isEmpty()) return emptyList()
        val longRun = runs.firstOrNull { it.title.contains("langer", ignoreCase = true) }
        val assigned = mutableMapOf<LocalDate, PlannedRun>()
        val nightShiftDates = dates.filter { date ->
            profile.considerNightShiftRecovery && profile.datedAvailability[date]?.afterNightShift == true
        }
        val remainingRuns = runs.toMutableList()
        val easyRun = remainingRuns.firstOrNull { it.title.contains("lockerer lauf", ignoreCase = true) }
        nightShiftDates.firstOrNull()?.let { date ->
            easyRun?.let { assigned[date] = it; remainingRuns.remove(it) }
        }
        val restedDates = dates.filterNot { it in assigned }
        val preferredLongDate = profile.longRunDay?.let { preferred -> restedDates.firstOrNull { it.dayOfWeek == preferred } }
            ?: restedDates.lastOrNull()
        if (longRun != null && preferredLongDate != null) {
            assigned[preferredLongDate] = longRun
            remainingRuns.remove(longRun)
        }
        restedDates.filterNot { it in assigned }.forEach { date ->
            remainingRuns.removeFirstOrNull()?.let { assigned[date] = it }
        }
        return dates.sorted().mapNotNull { date ->
            val run = assigned[date]
            run?.let {
                val maximumMinutes = when (profile.availabilityMode) {
                    AvailabilityMode.RECURRING -> profile.runTimeLimitsByDay[date.dayOfWeek]
                    AvailabilityMode.VARIABLE -> profile.datedAvailability[date]?.maximumMinutes
                }
                val adjustedRun = maximumMinutes?.let { limit -> durationAdjuster.fitToAvailableTime(it, limit) } ?: it
                val scheduledRun = adjustedRun.copy(
                    trainingId = "lap-run-$date-${adjustedRun.trainingType.name.lowercase()}",
                    scheduledDate = date
                )
                ScheduledRun(date.dayOfWeek, scheduledRun, date)
            }
        }
    }

    private fun spacedFromPrevious(dates: List<LocalDate>, previous: LocalDate?): Boolean =
        previous == null || dates.first().let { ChronoUnit.DAYS.between(previous, it) >= MIN_REST_DAYS }

    private fun spacedWithin(dates: List<LocalDate>): Boolean = dates.zipWithNext().all { (first, second) ->
        ChronoUnit.DAYS.between(first, second) >= MIN_REST_DAYS
    }

    private fun combinations(values: List<LocalDate>, count: Int): List<List<LocalDate>> {
        if (count == 0) return listOf(emptyList())
        if (values.size < count) return emptyList()
        return values.indices.flatMap { index ->
            val remaining = values.drop(index + 1)
            combinations(remaining, count - 1).map { listOf(values[index]) + it }
        }
    }

    private companion object {
        const val MIN_REST_DAYS = 2L
    }
}
