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
import de.run.app.domain.model.RunPrescription
import de.run.app.domain.model.TrainingType
import de.run.app.domain.model.Workout
import de.run.app.domain.model.isRunning
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import java.time.temporal.ChronoUnit

class TrainingPlanBuilder(
    private val progressAnalyzer: TrainingProgressAnalyzer = TrainingProgressAnalyzer(),
    private val responseAnalyzer: TrainingResponseAnalyzer = TrainingResponseAnalyzer(),
    private val distanceSpikeAnalyzer: RunDistanceSpikeAnalyzer = RunDistanceSpikeAnalyzer()
) {
    private val availabilityPlanner = TrainingAvailabilityPlanner()
    private val prescriptionAnalyzer = RunPrescriptionAnalyzer()
    private val continuousRunProgressionAnalyzer = ContinuousRunProgressionAnalyzer()
    fun build(
        profile: TrainingProfile,
        goal: TrainingGoal?,
        today: LocalDate,
        workouts: List<Workout> = emptyList()
    ): TrainingPlan {
        val runs = workouts.filter(Workout::isRunning)
        return when (profile.planMode) {
            TrainingPlanMode.PAUSED -> TrainingPlan(goal?.raceDate, PlanStatus.PAUSED, emptyList())
            TrainingPlanMode.MAINTAIN_FORM -> buildMaintenancePlan(profile, today, runs)
            TrainingPlanMode.RACE -> when {
            goal?.status == TrainingGoalStatus.COMPLETED ->
                TrainingPlan(goal.raceDate, PlanStatus.GOAL_COMPLETED, emptyList())
            goal?.status != TrainingGoalStatus.ACTIVE ->
                TrainingPlan(goal?.raceDate, PlanStatus.GOAL_NEEDED, emptyList())
            else -> buildRacePlan(profile, goal, today, runs)
            }
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
            PlanAdjustment.RECOVER_AFTER_HIGH_LOAD -> 1
            PlanAdjustment.HOLD_AFTER_SLOWING -> 1
            PlanAdjustment.REVIEW_REPORTED_SYMPTOMS -> 2
            else -> 0
        }
        val firstWeekIndex = (raceWeekIndex - repeatOffset).coerceAtLeast(0)
        val count = weeksRemaining
            .coerceAtMost(LONG_RUN_MINUTES.size - firstWeekIndex)
            .coerceAtMost(RACE_PLAN_PREVIEW_WEEKS)
        var previousPlannedRunDate = progress.lastRunDate
        val weeks = buildList {
            (0 until count).forEach { offset ->
                val week = buildWeek(
                    profile,
                    today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(offset.toLong()),
                    firstWeekIndex + offset,
                    offset + 1,
                    count,
                    today,
                    previousPlannedRunDate,
                    progress,
                    response,
                    workouts,
                    goal.raceGoal == de.run.app.domain.model.RaceGoal.RUN_THROUGH,
                    goal.raceDate
                )
                add(week)
                week.scheduledRuns.maxByOrNull { scheduled ->
                    scheduled.day.value
                }?.let { previousPlannedRunDate = week.startsOn.plusDays((it.day.value - 1).toLong()) }
            }
        }
        return TrainingPlan(
            goal.raceDate,
            status,
            assessPlannedDistances(weeks, workouts),
            adjustment,
            TrainingPlanAdjustmentExplainer().explain(adjustment, progress, workouts, today)
        )
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
            val responseRecovery = index == 0 && response in setOf(
                TrainingResponse.RECOVER_AFTER_HIGH_LOAD,
                TrainingResponse.HOLD_AFTER_SLOWING
            )
            val longRunMinutes = if (isRecoveryWeek || responseRecovery) {
                (steadyLongRun - RECOVERY_REDUCTION_MINUTES).coerceAtLeast(MIN_MAINTENANCE_LONG_RUN)
            } else steadyLongRun
            val runs = maintenanceRuns(profile, progress, today, longRunMinutes, isRecoveryWeek, response, workouts)
            val week = availabilityPlanner.buildWeek(
                profile = profile,
                startsOn = startsOn,
                today = today,
                previousRunDate = previousRunDate,
                weekNumber = index + 1,
                longRunMinutes = longRunMinutes,
                isRecoveryWeek = isRecoveryWeek || responseRecovery,
                candidateRuns = runs
            )
            week.scheduledRuns.maxByOrNull { it.day.value }?.let {
                previousRunDate = week.startsOn.plusDays((it.day.value - 1).toLong())
            }
            week
        }
        val adjustment = adjustmentFor(progress, today, response)
        return TrainingPlan(
            null,
            PlanStatus.MAINTAINING_FORM,
            assessPlannedDistances(weeks, workouts),
            adjustment,
            TrainingPlanAdjustmentExplainer().explain(adjustment, progress, workouts, today)
        )
    }

    private fun assessPlannedDistances(weeks: List<TrainingWeek>, workouts: List<Workout>): List<TrainingWeek> =
        weeks.map { week ->
            val scheduledRuns = week.scheduledRuns.map { scheduled ->
                    val date = scheduled.scheduledDate ?: week.startsOn.plusDays((scheduled.day.value - 1).toLong())
                    val assessed = assessRun(scheduled.run, date, workouts)
                    scheduled.copy(scheduledDate = date, run = assessed)
                }
            val assessedById = scheduledRuns.mapNotNull { item ->
                item.run.trainingId?.let { it to item.run }
            }.toMap()
            week.copy(
                runs = week.runs.map { run ->
                    run.trainingId?.let(assessedById::get)
                        ?: assessRun(run, run.scheduledDate ?: week.startsOn, workouts)
                },
                scheduledRuns = scheduledRuns
            )
        }

    private fun assessRun(run: PlannedRun, date: LocalDate, workouts: List<Workout>): PlannedRun =
        run.copy(distanceSpikeAssessment = distanceSpikeAnalyzer.assessPlanned(run, workouts, date))

    private fun maintenanceRuns(
        profile: TrainingProfile,
        progress: TrainingProgress,
        today: LocalDate,
        longRunMinutes: Int,
        isRecoveryWeek: Boolean,
        response: TrainingResponse,
        workouts: List<Workout>
    ): List<PlannedRun> {
        val easyMinutes = (profile.usualRunTimeLimitMinutes - 10).coerceIn(20, 35)
        val runs = mutableListOf(
            if (response in setOf(TrainingResponse.RECOVER_AFTER_HIGH_LOAD, TrainingResponse.HOLD_AFTER_SLOWING)) recoveryRun() else easyRun(easyMinutes),
            longRun(longRunMinutes)
        )
        val consistent = progress.runsLastFourWeeks >= MIN_RUNS_FOR_OPTIONAL &&
            progress.lastRunDate?.isAfter(today.minusDays(8)) == true &&
            progress.runsPreviousWeek >= REQUIRED_WEEKLY_RUNS
        if (profile.optionalRun && consistent && !isRecoveryWeek && response == TrainingResponse.NO_CHANGE) {
            runs.add(optionalRun(0))
        }
        return runs.map { withPrescription(it, workouts, today) }
    }

    private fun adjustmentFor(
        progress: TrainingProgress,
        today: LocalDate,
        response: TrainingResponse
    ): PlanAdjustment = when (response) {
        TrainingResponse.REVIEW_REPORTED_SYMPTOMS -> PlanAdjustment.REVIEW_REPORTED_SYMPTOMS
        TrainingResponse.REPEAT_AFTER_HIGH_EFFORT -> PlanAdjustment.REPEAT_AFTER_HIGH_EFFORT
        TrainingResponse.RECOVER_AFTER_HIGH_LOAD -> PlanAdjustment.RECOVER_AFTER_HIGH_LOAD
        TrainingResponse.HOLD_AFTER_SLOWING -> PlanAdjustment.HOLD_AFTER_SLOWING
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
        weekNumber: Int,
        weekCount: Int,
        today: LocalDate,
        previousPlannedRunDate: LocalDate?,
        progress: TrainingProgress,
        response: TrainingResponse,
        workouts: List<Workout>,
        progressContinuousRunning: Boolean,
        raceDate: LocalDate
    ): TrainingWeek {
        val plannedLongRun = LONG_RUN_MINUTES[index]
        val measuredLongRun = (progress.longestRunMinutesLastFourWeeks
            .takeIf { it > 0 } ?: profile.longestComfortableRunMinutes)
            .coerceIn(MIN_LONG_RUN_MINUTES, MAX_LONG_RUN_MINUTES)
        val consistentRecentTraining = progress.runsLastFourWeeks >= MIN_RUNS_FOR_OPTIONAL &&
            progress.runsPreviousWeek >= REQUIRED_WEEKLY_RUNS &&
            progress.lastRunDate?.isAfter(today.minusDays(8)) == true
        val progressionAllowance = if (
            weekNumber == 1 && consistentRecentTraining && response == TrainingResponse.NO_CHANGE
        ) {
            TrainingProgressionPolicy.LONG_RUN_STEP_MINUTES
        } else 0
        val longRun = minOf(
            maxOf(plannedLongRun, measuredLongRun),
            measuredLongRun + progressionAllowance,
            MAX_LONG_RUN_MINUTES
        )
        val recovery = (index + 1) % RECOVERY_WEEK_INTERVAL == 0
        val responseRecovery = response in setOf(
            TrainingResponse.RECOVER_AFTER_HIGH_LOAD,
            TrainingResponse.HOLD_AFTER_SLOWING
        ) && weekNumber == 1
        val adjustedLongRun = if (recovery || responseRecovery) {
            (longRun - RECOVERY_REDUCTION_MINUTES).coerceAtLeast(maxOf(MIN_LONG_RUN_MINUTES, longRun / 2))
        } else longRun
        val recentAndConsistent = progress.runsLastFourWeeks >= MIN_RUNS_FOR_OPTIONAL &&
            progress.lastRunDate?.isAfter(today.minusDays(8)) == true &&
            progress.lastRunDate?.isAfter(today.minusDays(14)) == true
        val addOptional = profile.optionalRun && recentAndConsistent && !recovery && !responseRecovery &&
            adjustmentFor(progress, today, response) == PlanAdjustment.CONTINUE
        val continuousRunningTarget = if (progressContinuousRunning) {
            val evidenceBasedTarget = continuousRunProgressionAnalyzer.targetMinutes(
                workouts,
                today,
                (adjustedLongRun - WARM_UP_MINUTES - COOL_DOWN_MINUTES).coerceAtLeast(0)
            )
            if (recovery || responseRecovery) {
                (evidenceBasedTarget - CONTINUOUS_RECOVERY_REDUCTION_MINUTES)
                    .coerceAtLeast(ContinuousRunProgressionAnalyzer.STARTING_CONTINUOUS_MINUTES)
            } else evidenceBasedTarget
        } else null
        val long = longRun(adjustedLongRun, continuousRunningTarget)
        val baseEasy = easyRun(index, weekCount)
        val easy = when {
            responseRecovery -> recoveryRun()
            continuousRunningTarget != null -> {
                val easyMinutes = minOf(
                    baseEasy.totalMinutes,
                    profile.usualRunTimeLimitMinutes.coerceIn(MIN_SESSION_MINUTES, MAX_LONG_RUN_MINUTES)
                )
                val easyContinuousTarget = continuousRunProgressionAnalyzer.targetMinutes(
                    workouts,
                    today,
                    (easyMinutes - WARM_UP_MINUTES - COOL_DOWN_MINUTES).coerceAtLeast(0)
                )
                easyRunWithContinuousTarget(easyMinutes, easyContinuousTarget)
            }
            else -> baseEasy
        }
        val candidates = buildList {
            add(withPrescription(easy, workouts, today))
            add(withPrescription(long, workouts, today))
            if (addOptional) add(withPrescription(optionalRun(index), workouts, today))
        }
        return availabilityPlanner.buildWeek(
            profile = profile,
            startsOn = startsOn,
            today = today,
            previousRunDate = previousPlannedRunDate,
            weekNumber = index + 1,
            longRunMinutes = long.totalMinutes,
            isRecoveryWeek = recovery,
            candidateRuns = candidates,
            raceDate = raceDate.takeIf { it in startsOn..startsOn.plusDays(6) }
        )
    }

    private fun withPrescription(run: PlannedRun, workouts: List<Workout>, today: LocalDate): PlannedRun =
        if (run.trainingType == TrainingType.RECOVERY) run
        else run.copy(prescription = prescriptionAnalyzer.analyze(workouts, today, run.totalMinutes))

    private fun easyRun(index: Int, weekCount: Int): PlannedRun {
        val secondRunMinutes = (30 + index).coerceAtMost(45).coerceAtMost(weekCount + 29)
        return PlannedRun(
            title = "Lockerer Lauf",
            optional = false,
            steps = listOf(
                RunStep("Zügig gehen", WARM_UP_MINUTES, 1, 2, "Bequem gehen"),
                RunStep("Locker laufen", 30, 2, 4, "Ganze Sätze sprechen können"),
                RunStep("Locker gehen", 1, 1, 2, "Erholen"),
                RunStep("Locker laufen", secondRunMinutes, 2, 4, "Ganze Sätze sprechen können"),
                RunStep("Locker gehen", COOL_DOWN_MINUTES, 1, 2, "Bequem ausgehen")
            ),
            trainingType = TrainingType.EASY
        )
    }

    private fun easyRun(minutes: Int): PlannedRun = PlannedRun(
        title = "Lockerer Lauf",
        optional = false,
        steps = listOf(
            RunStep("Zügig gehen", WARM_UP_MINUTES, 1, 2, "Bequem gehen"),
            RunStep("Locker laufen", minutes, 2, 4, "Ganze Sätze sprechen können"),
            RunStep("Locker gehen", COOL_DOWN_MINUTES, 1, 2, "Bequem ausgehen")
        ),
        trainingType = TrainingType.EASY
    )

    private fun recoveryRun(): PlannedRun = PlannedRun(
        title = "Erholungslauf",
        optional = false,
        steps = listOf(
            RunStep("Zügig gehen", WARM_UP_MINUTES, 1, 2, "Bequem gehen"),
            RunStep("Sehr locker laufen oder gehen", RECOVERY_RUN_MINUTES, 1, 3, "Nur wenn es sich gut anfühlt"),
            RunStep("Locker ausgehen", COOL_DOWN_MINUTES, 1, 2, "Bequem gehen")
        ),
        trainingType = TrainingType.RECOVERY,
        prescription = RunPrescription(
            effortMinimum = 1,
            effortMaximum = 3,
            effortCue = "Sehr leicht; jederzeit gehen oder abbrechen.",
            rationale = "Verkürzte Einheit nach einer deutlich höheren Belastung als geplant."
        )
    )

    private fun longRun(minutes: Int, continuousRunningTargetMinutes: Int? = null): PlannedRun {
        if (continuousRunningTargetMinutes != null) {
            return PlannedRun(
                ContinuousRunProgressionAnalyzer.LONG_RUN_TITLE,
                false,
                continuousRunningSteps(minutes, continuousRunningTargetMinutes),
                trainingType = TrainingType.LONG_EASY
            )
        }
        val runningMinutes = (minutes - WARM_UP_MINUTES - COOL_DOWN_MINUTES).coerceAtLeast(0)
        val steps = if (runningMinutes <= RUN_BLOCK_MINUTES) {
            listOf(
                RunStep("Zügig gehen", WARM_UP_MINUTES, 1, 2, "Bequem gehen"),
                RunStep("Locker laufen; Gehpausen sind jederzeit möglich", runningMinutes, 2, 4, "Ganze Sätze sprechen können"),
                RunStep("Locker gehen", COOL_DOWN_MINUTES, 1, 2, "Bequem ausgehen")
            )
        } else {
            listOf(
                RunStep("Zügig gehen", WARM_UP_MINUTES, 1, 2, "Bequem gehen"),
                RunStep("Locker laufen", RUN_BLOCK_MINUTES, 2, 4, "Ganze Sätze sprechen können"),
                RunStep("Locker gehen", 1, 1, 2, "Erholen"),
                RunStep("Locker laufen; weitere Gehpausen sind jederzeit möglich", runningMinutes - RUN_BLOCK_MINUTES - 1, 2, 4, "Ganze Sätze sprechen können"),
                RunStep("Locker gehen", COOL_DOWN_MINUTES, 1, 2, "Bequem ausgehen")
            )
        }
        return PlannedRun(ContinuousRunProgressionAnalyzer.LONG_RUN_TITLE, false, steps, trainingType = TrainingType.LONG_EASY)
    }

    private fun easyRunWithContinuousTarget(totalMinutes: Int, targetMinutes: Int): PlannedRun = PlannedRun(
        title = "Lockerer Lauf",
        optional = false,
        steps = continuousRunningSteps(totalMinutes, targetMinutes),
        trainingType = TrainingType.EASY
    )

    private fun continuousRunningSteps(totalMinutes: Int, requestedContinuousMinutes: Int): List<RunStep> {
        val runningMinutes = (totalMinutes - WARM_UP_MINUTES - COOL_DOWN_MINUTES).coerceAtLeast(0)
        val continuousMinutes = requestedContinuousMinutes.coerceIn(0, runningMinutes)
        return buildList {
            add(RunStep("Zügig gehen", WARM_UP_MINUTES, 1, 2, "Bequem gehen"))
            if (continuousMinutes >= runningMinutes - 1) {
                add(RunStep("Locker laufen; Ziel: möglichst durchgehend", runningMinutes, 2, 4, "Ganze Sätze sprechen können"))
            } else {
                add(RunStep("Locker laufen; Ziel: $continuousMinutes Min. am Stück", continuousMinutes, 2, 4, "Ganze Sätze sprechen können"))
                add(RunStep("Locker gehen", 1, 1, 2, "Erholen"))
                val remainingRunningMinutes = runningMinutes - continuousMinutes - 1
                if (remainingRunningMinutes > 0) {
                    add(RunStep("Locker weiterlaufen; Gehpause jederzeit möglich", remainingRunningMinutes, 2, 4, "Ganze Sätze sprechen können"))
                }
            }
            add(RunStep("Locker gehen", COOL_DOWN_MINUTES, 1, 2, "Bequem ausgehen"))
        }
    }

    private fun optionalRun(index: Int) = PlannedRun(
        title = "Sehr lockerer Zusatzlauf",
        optional = true,
        steps = listOf(
            RunStep("Zügig gehen", WARM_UP_MINUTES, 1, 2, "Bequem gehen"),
            RunStep("Laufen und bei Bedarf gehen", (20 + index / 3).coerceAtMost(30), 1, 3, "Sprechtempo; jederzeit gehen"),
            RunStep("Locker gehen", COOL_DOWN_MINUTES, 1, 2, "Bequem ausgehen")
        ),
        trainingType = TrainingType.RECOVERY
    )

    private companion object {
        const val REQUIRED_WEEKLY_RUNS = 2
        const val MINIMUM_PREPARATION_WEEKS = 16
        const val WARM_UP_MINUTES = 5
        const val COOL_DOWN_MINUTES = 5
        const val RECOVERY_WEEK_INTERVAL = 4
        const val MIN_SESSION_MINUTES = 30
        const val MIN_RUNS_FOR_OPTIONAL = 6
        const val RECOVERY_REDUCTION_MINUTES = 15
        const val CONTINUOUS_RECOVERY_REDUCTION_MINUTES = 5
        const val MIN_LONG_RUN_MINUTES = 15
        const val MAX_LONG_RUN_MINUTES = 150
        const val MIN_MAINTENANCE_LONG_RUN = 45
        const val MAINTENANCE_CYCLE_WEEKS = 4
        const val RUN_BLOCK_MINUTES = 30
        const val RECOVERY_RUN_MINUTES = 15
        const val RACE_PLAN_PREVIEW_WEEKS = 2
        val LONG_RUN_MINUTES = listOf(
            50, 55, 60, 50, 65, 70, 75, 60, 80, 85, 90, 70,
            100, 105, 110, 85, 120, 130, 140, 105, 120, 90, 60, 45
        )
    }
}
