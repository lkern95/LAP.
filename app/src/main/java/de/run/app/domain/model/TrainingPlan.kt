package de.run.app.domain.model

import java.time.LocalDate
import java.time.DayOfWeek

private val DEFAULT_AVAILABLE_RUN_DAYS = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SUNDAY)
private val DEFAULT_RUN_TIME_LIMITS = mapOf(
    DayOfWeek.TUESDAY to 45,
    DayOfWeek.THURSDAY to 45,
    DayOfWeek.SUNDAY to 90
)

enum class RaceGoal {
    ARRIVE,
    RUN_THROUGH,
    TARGET_TIME
}

enum class TrainingGoalStatus {
    ACTIVE,
    COMPLETED,
    REPLACED
}

enum class TrainingPlanMode {
    RACE,
    MAINTAIN_FORM,
    PAUSED
}

data class TrainingGoal(
    val id: String,
    val raceDate: LocalDate,
    val raceDistanceMeters: Int,
    val raceGoal: RaceGoal,
    val targetTimeMinutes: Int? = null,
    val status: TrainingGoalStatus = TrainingGoalStatus.ACTIVE,
    val competitionCompleted: Boolean? = null,
    val goalAchieved: Boolean? = null,
    val competitionWorkoutId: String? = null,
    val pendingCompetitionWorkoutId: String? = null,
    val lastPromptedWorkoutId: String? = null
)

enum class AvailabilityMode {
    RECURRING,
    VARIABLE
}

enum class AvailabilityStatus {
    UNKNOWN,
    AVAILABLE,
    UNAVAILABLE
}

data class TrainingDayAvailability(
    val status: AvailabilityStatus,
    val maximumMinutes: Int? = null,
    val afterNightShift: Boolean = false
)

data class TrainingProfile(
    val longestComfortableRunMinutes: Int,
    val optionalRun: Boolean,
    val strengthSessionsPerWeek: Int,
    val availableRunDays: Set<DayOfWeek> = DEFAULT_AVAILABLE_RUN_DAYS,
    val longRunDay: DayOfWeek? = null,
    val runTimeLimitsByDay: Map<DayOfWeek, Int> = DEFAULT_RUN_TIME_LIMITS,
    val availabilityMode: AvailabilityMode = AvailabilityMode.RECURRING,
    val datedAvailability: Map<LocalDate, TrainingDayAvailability> = emptyMap(),
    val considerNightShiftRecovery: Boolean = false,
    val usualRunTimeLimitMinutes: Int = 45,
    val occasionalRunTimeLimitMinutes: Int? = null,
    val planMode: TrainingPlanMode = TrainingPlanMode.RACE
)

data class RunStep(
    val name: String,
    val minutes: Int
)

data class PlannedRun(
    val title: String,
    val optional: Boolean,
    val steps: List<RunStep>
) {
    val totalMinutes: Int
        get() = steps.sumOf(RunStep::minutes)
}

data class TrainingWeek(
    val number: Int,
    val startsOn: LocalDate,
    val longRunMinutes: Int,
    val strengthSessions: Int,
    val runs: List<PlannedRun>,
    val isRecoveryWeek: Boolean,
    val scheduledRuns: List<ScheduledRun> = emptyList(),
    val availability: Map<DayOfWeek, AvailabilityStatus> = emptyMap()
) {
    val requiredRunCount: Int
        get() = runs.count { !it.optional }
}

data class ScheduledRun(val day: DayOfWeek, val run: PlannedRun)

enum class PlanStatus {
    READY,
    EVENT_TOO_CLOSE,
    MAINTAINING_FORM,
    PAUSED,
    GOAL_NEEDED,
    GOAL_COMPLETED
}

data class TrainingPlan(
    val eventDate: LocalDate?,
    val status: PlanStatus,
    val weeks: List<TrainingWeek>,
    val adjustment: PlanAdjustment = PlanAdjustment.NO_RUN_DATA
)

enum class PlanAdjustment {
    NO_RUN_DATA,
    CONTINUE,
    REPEAT_WEEK,
    RETURN_GENTLY,
    REPEAT_AFTER_HIGH_EFFORT,
    REVIEW_REPORTED_SYMPTOMS
}

data class TrainingProgress(
    val runsLastFourWeeks: Int,
    val minutesLastFourWeeks: Int,
    val distanceMetersLastFourWeeks: Double,
    val longestRunMinutesLastSixteenWeeks: Int,
    val runsPreviousWeek: Int,
    val lastRunDate: LocalDate?,
    val weeklyRunVolumes: List<WeeklyRunVolume>
)

data class WeeklyRunVolume(
    val weekStartsOn: LocalDate,
    val runCount: Int,
    val minutes: Int
)
