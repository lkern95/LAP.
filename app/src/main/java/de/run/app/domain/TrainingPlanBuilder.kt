package de.run.app.domain

import de.run.app.domain.model.PlanStatus
import de.run.app.domain.model.PlanAdjustment
import de.run.app.domain.model.PlannedRun
import de.run.app.domain.model.RunStep
import de.run.app.domain.model.TrainingPlan
import de.run.app.domain.model.TrainingProgress
import de.run.app.domain.model.TrainingProfile
import de.run.app.domain.model.TrainingGoal
import de.run.app.domain.model.TrainingGoalStatus
import de.run.app.domain.model.TrainingPlanMode
import de.run.app.domain.model.TrainingWeek
import de.run.app.domain.model.Workout
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import java.time.temporal.ChronoUnit

class TrainingPlanBuilder(
    private val progressAnalyzer: TrainingProgressAnalyzer = TrainingProgressAnalyzer(),
    private val responseAnalyzer: TrainingResponseAnalyzer = TrainingResponseAnalyzer()
) {
    private val availabilityPlanner = TrainingAvailabilityPlanner()
    fun build(
        profile: TrainingProfile,
        goal: TrainingGoal?,
        today: LocalDate,
        workouts: List<Workout> = emptyList()
    ): TrainingPlan = when (profile.planMode) {
        TrainingPlanMode.PAUSED -> TrainingPlan(goal?.raceDate, PlanStatus.PAUSED, emptyList())
        TrainingPlanMode.MAINTAIN_FORM -> buildMaintenancePlan(profile, today, workouts)
        TrainingPlanMode.RACE -> when {
            goal?.status == TrainingGoalStatus.COMPLETED ->
                TrainingPlan(goal.raceDate, PlanStatus.GOAL_COMPLETED, emptyList())
            goal?.status != TrainingGoalStatus.ACTIVE ->
                TrainingPlan(goal?.raceDate, PlanStatus.GOAL_NEEDED, emptyList())
            else -> buildRacePlan(profile, goal, today, workouts)
        }
    }

    private fun buildRacePlan(
        profile: TrainingProfile,
        goal: TrainingGoal,
        today: LocalDate,
        workouts: List<Workout>
    ): TrainingPlan {
        val weeksRemaining = ChronoUnit.WEEKS.between(today, goal.raceDate).toInt() + 1
        if (weeksRemaining <= 0) return TrainingPlan(goal.raceDate, PlanStatus.EVENT_TOO_CLOSE, emptyList())

        val progress = progressAnalyzer.analyze(workouts, today)
        val status = if (weeksRemaining < MINIMUM_PREPARATION_WEEKS) {
            PlanStatus.EVENT_TOO_CLOSE
        } else {
            PlanStatus.READY
        }
        val response = responseAnalyzer.analyze(workouts, today)
        val adjustment = adjustmentFor(progress, today, response)
        val raceWeekIndex = (LONG_RUN_MINUTES.size - weeksRemaining).coerceAtLeast(0)
        val repeatOffset = when (adjustment) {
            PlanAdjustment.REPEAT_WEEK -> 1
            PlanAdjustment.RETURN_GENTLY -> 2
            PlanAdjustment.REPEAT_AFTER_HIGH_EFFORT -> 1
            PlanAdjustment.REVIEW_REPORTED_SYMPTOMS -> 2
            else -> 0
        }
        val firstWeekIndex = (raceWeekIndex - repeatOffset).coerceAtLeast(0)
        val count = weeksRemaining.coerceAtMost(LONG_RUN_MINUTES.size - firstWeekIndex)
        var previousPlannedRunDate = progress.lastRunDate
        val weeks = buildList {
            (0 until count).forEach { offset ->
                val week = buildWeek(
                    profile,
                    today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(offset.toLong()),
                    firstWeekIndex + offset,
                    count,
                    today,
                    previousPlannedRunDate,
                    progress,
                    response
                )
                add(week)
                week.scheduledRuns.maxByOrNull { scheduled ->
                    scheduled.day.value
                }?.let { previousPlannedRunDate = week.startsOn.plusDays((it.day.value - 1).toLong()) }
            }
        }
        return TrainingPlan(goal.raceDate, status, weeks, adjustment)
    }

    private fun buildMaintenancePlan(
        profile: TrainingProfile,
        today: LocalDate,
        workouts: List<Workout>
    ): TrainingPlan {
        val progress = progressAnalyzer.analyze(workouts, today)
        val response = responseAnalyzer.analyze(workouts, today)
        val steadyLongRun = (progress.longestRunMinutesLastSixteenWeeks.takeIf { it > 0 }
            ?: profile.longestComfortableRunMinutes).coerceIn(MIN_MAINTENANCE_LONG_RUN, MAX_LONG_RUN_MINUTES)
        var previousRunDate = progress.lastRunDate
        val weeks = (0 until MAINTENANCE_CYCLE_WEEKS).map { index ->
            val startsOn = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(index.toLong())
            val isRecoveryWeek = index == MAINTENANCE_CYCLE_WEEKS - 1
            val longRunMinutes = if (isRecoveryWeek) {
                (steadyLongRun - RECOVERY_REDUCTION_MINUTES).coerceAtLeast(MIN_MAINTENANCE_LONG_RUN)
            } else steadyLongRun
            val runs = maintenanceRuns(profile, progress, today, longRunMinutes, isRecoveryWeek, response)
            val week = availabilityPlanner.buildWeek(
                profile = profile,
                startsOn = startsOn,
                today = today,
                previousRunDate = previousRunDate,
                weekNumber = index + 1,
                longRunMinutes = longRunMinutes,
                isRecoveryWeek = isRecoveryWeek,
                candidateRuns = runs
            )
            week.scheduledRuns.maxByOrNull { it.day.value }?.let {
                previousRunDate = week.startsOn.plusDays((it.day.value - 1).toLong())
            }
            week
        }
        return TrainingPlan(null, PlanStatus.MAINTAINING_FORM, weeks, adjustmentFor(progress, today, response))
    }

    private fun maintenanceRuns(
        profile: TrainingProfile,
        progress: TrainingProgress,
        today: LocalDate,
        longRunMinutes: Int,
        isRecoveryWeek: Boolean,
        response: TrainingResponse
    ): List<PlannedRun> {
        val easyMinutes = (profile.usualRunTimeLimitMinutes - 10).coerceIn(20, 35)
        val runs = mutableListOf(
            PlannedRun("Lockerer Lauf", false, listOf(
                RunStep("Zügig gehen", WARM_UP_MINUTES),
                RunStep("Locker laufen", easyMinutes),
                RunStep("Locker gehen", COOL_DOWN_MINUTES)
            )),
            longRun(longRunMinutes)
        )
        val consistent = progress.runsLastFourWeeks >= MIN_RUNS_FOR_OPTIONAL &&
            progress.lastRunDate?.isAfter(today.minusDays(8)) == true &&
            progress.runsPreviousWeek >= REQUIRED_WEEKLY_RUNS
        if (profile.optionalRun && consistent && !isRecoveryWeek && response == TrainingResponse.NO_CHANGE) {
            runs.add(optionalRun(0))
        }
        return runs
    }

    private fun adjustmentFor(
        progress: TrainingProgress,
        today: LocalDate,
        response: TrainingResponse
    ): PlanAdjustment = when (response) {
        TrainingResponse.REVIEW_REPORTED_SYMPTOMS -> PlanAdjustment.REVIEW_REPORTED_SYMPTOMS
        TrainingResponse.REPEAT_AFTER_HIGH_EFFORT -> PlanAdjustment.REPEAT_AFTER_HIGH_EFFORT
        TrainingResponse.NO_CHANGE -> when {
            progress.lastRunDate == null -> PlanAdjustment.NO_RUN_DATA
            !progress.lastRunDate.isAfter(today.minusDays(14)) -> PlanAdjustment.RETURN_GENTLY
            progress.runsPreviousWeek < REQUIRED_WEEKLY_RUNS -> PlanAdjustment.REPEAT_WEEK
            else -> PlanAdjustment.CONTINUE
        }
    }

    private fun buildWeek(
        profile: TrainingProfile,
        startsOn: LocalDate,
        index: Int,
        weekCount: Int,
        today: LocalDate,
        previousPlannedRunDate: LocalDate?,
        progress: TrainingProgress,
        response: TrainingResponse
    ): TrainingWeek {
        val plannedLongRun = LONG_RUN_MINUTES[index]
        val longRun = maxOf(plannedLongRun, profile.longestComfortableRunMinutes.coerceAtMost(MAX_LONG_RUN_MINUTES))
        val recovery = (index + 1) % RECOVERY_WEEK_INTERVAL == 0
        val adjustedLongRun = if (recovery) (longRun - RECOVERY_REDUCTION_MINUTES).coerceAtLeast(longRun / 2) else longRun
        val recentAndConsistent = progress.runsLastFourWeeks >= MIN_RUNS_FOR_OPTIONAL &&
            progress.lastRunDate?.isAfter(today.minusDays(8)) == true &&
            progress.lastRunDate?.isAfter(today.minusDays(14)) == true
        val addOptional = profile.optionalRun && recentAndConsistent && !recovery &&
            adjustmentFor(progress, today, response) == PlanAdjustment.CONTINUE
        val easy = easyRun(index, weekCount)
        val long = longRun(adjustedLongRun)
        val candidates = buildList {
            add(easy)
            add(long)
            if (addOptional) add(optionalRun(index))
        }
        return availabilityPlanner.buildWeek(
            profile = profile,
            startsOn = startsOn,
            today = today,
            previousRunDate = previousPlannedRunDate,
            weekNumber = index + 1,
            longRunMinutes = long.totalMinutes,
            isRecoveryWeek = recovery,
            candidateRuns = candidates
        )
    }

    private fun easyRun(index: Int, weekCount: Int): PlannedRun {
        val secondRunMinutes = (30 + index).coerceAtMost(45).coerceAtMost(weekCount + 29)
        return PlannedRun(
            title = "Lockerer Lauf",
            optional = false,
            steps = listOf(
                RunStep("Zügig gehen", WARM_UP_MINUTES),
                RunStep("Locker laufen", 30),
                RunStep("Locker gehen", 1),
                RunStep("Locker laufen", secondRunMinutes),
                RunStep("Locker gehen", COOL_DOWN_MINUTES)
            )
        )
    }

    private fun longRun(minutes: Int) = PlannedRun(
        title = "Langer, ruhiger Lauf",
        optional = false,
        steps = listOf(
            RunStep("Zügig gehen", WARM_UP_MINUTES),
            RunStep("Locker laufen", RUN_BLOCK_MINUTES),
            RunStep("Locker gehen", 1),
            RunStep("Locker laufen; weitere Gehpausen sind jederzeit möglich", minutes - RUN_BLOCK_MINUTES - WARM_UP_MINUTES - COOL_DOWN_MINUTES - 1),
            RunStep("Locker gehen", COOL_DOWN_MINUTES)
        )
    )

    private fun optionalRun(index: Int) = PlannedRun(
        title = "Sehr lockerer Zusatzlauf",
        optional = true,
        steps = listOf(
            RunStep("Zügig gehen", WARM_UP_MINUTES),
            RunStep("Laufen und bei Bedarf gehen", (20 + index / 3).coerceAtMost(30)),
            RunStep("Locker gehen", COOL_DOWN_MINUTES)
        )
    )

    private companion object {
        const val REQUIRED_WEEKLY_RUNS = 2
        const val MINIMUM_PREPARATION_WEEKS = 16
        const val WARM_UP_MINUTES = 5
        const val COOL_DOWN_MINUTES = 5
        const val RECOVERY_WEEK_INTERVAL = 4
        const val MIN_RUNS_FOR_OPTIONAL = 6
        const val RECOVERY_REDUCTION_MINUTES = 15
        const val MAX_LONG_RUN_MINUTES = 150
        const val MIN_MAINTENANCE_LONG_RUN = 45
        const val MAINTENANCE_CYCLE_WEEKS = 4
        const val RUN_BLOCK_MINUTES = 30
        val LONG_RUN_MINUTES = listOf(
            50, 55, 60, 50, 65, 70, 75, 60, 80, 85, 90, 70,
            100, 105, 110, 85, 120, 130, 140, 105, 120, 90, 60, 45
        )
    }
}
