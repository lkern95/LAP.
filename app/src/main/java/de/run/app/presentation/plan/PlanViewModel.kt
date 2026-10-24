package de.run.app.presentation.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.run.app.domain.HalfMarathonForecast
import de.run.app.domain.HalfMarathonForecastAnalyzer
import de.run.app.domain.TrainingGoalCandidateSelector
import de.run.app.domain.TrainingProgressAnalyzer
import de.run.app.domain.TrainingDecisionSnapshotFactory
import de.run.app.domain.TrainingPlanBuilder
import de.run.app.domain.TrainingRunDurationAdjuster
import de.run.app.domain.model.PlannedRun
import de.run.app.domain.model.TrainingGoal
import de.run.app.domain.model.TrainingGoalStatus
import de.run.app.domain.model.TrainingPlan
import de.run.app.domain.model.TrainingPlanMode
import de.run.app.domain.model.TrainingProfile
import de.run.app.domain.model.TrainingProgress
import de.run.app.domain.model.ScheduledRun
import de.run.app.domain.model.Workout
import de.run.app.domain.repository.TrainingGoalRepository
import de.run.app.domain.repository.TrainingGoalHistoryUnavailableException
import de.run.app.domain.repository.TrainingProfileRepository
import de.run.app.domain.repository.WorkoutRepository
import de.run.app.domain.repository.TrainingDecisionRepository
import java.time.LocalDate
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

private val HALF_MARATHON_DISTANCE_RANGE = 20_000..22_000

data class PlanUiState(
    val isLoading: Boolean = true,
    val profile: TrainingProfile? = null,
    val goal: TrainingGoal? = null,
    val goals: List<TrainingGoal> = emptyList(),
    val plan: TrainingPlan? = null,
    val saveFailed: Boolean = false,
    val isSaving: Boolean = false,
    val isEditing: Boolean = false,
    val isEditingGoal: Boolean = false,
    val isCreatingGoal: Boolean = false,
    val editingStep: Int = 0,
    val progress: TrainingProgress? = null,
    val completedPlannedRunCount: Int = 0,
    val nextScheduledRun: ScheduledRun? = null,
    val halfMarathonForecast: HalfMarathonForecast? = null,
    val hasLoadError: Boolean = false,
    val hasGoalHistoryError: Boolean = false,
    val pendingCompetitionCandidate: Workout? = null,
    val dismissedCompetitionWorkoutId: String? = null,
    val dismissedGoalAchievementId: String? = null,
    val goalPaceSecondsPerKilometer: Long? = null,
    val goalPaceUsesForecast: Boolean = false
)

class PlanViewModel(
    private val profiles: TrainingProfileRepository,
    private val goals: TrainingGoalRepository,
    private val planBuilder: TrainingPlanBuilder,
    private val workouts: WorkoutRepository,
    private val decisions: TrainingDecisionRepository,
    private val progressAnalyzer: TrainingProgressAnalyzer = TrainingProgressAnalyzer()
) : ViewModel() {
    private val decisionFactory = TrainingDecisionSnapshotFactory()
    private val runDurationAdjuster = TrainingRunDurationAdjuster()
    private val candidateSelector = TrainingGoalCandidateSelector()
    private val completionAnalyzer = de.run.app.domain.WorkoutPlanCompletionAnalyzer()
    private val upcomingRunSelector = de.run.app.domain.UpcomingScheduledRunSelector()
    private val forecastAnalyzer = HalfMarathonForecastAnalyzer()
    private val mutableState = MutableStateFlow(PlanUiState())
    val state: StateFlow<PlanUiState> = mutableState.asStateFlow()

    init {
        observePlan()
    }

    private fun observePlan() {
        viewModelScope.launch {
            combine(profiles.observe(), goals.observeHistory(), workouts.observeRecent(), observeLocalDate()) { profile, history, records, today ->
                PlanInputs(profile, history, records, today)
            }.catch { error ->
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    hasLoadError = true,
                    hasGoalHistoryError = error is TrainingGoalHistoryUnavailableException
                )
            }.collectLatest { (profile, history, records, today) ->
                val activeGoal = history.firstOrNull { it.status == TrainingGoalStatus.ACTIVE }
                val lastCompleted = history.firstOrNull { it.status == TrainingGoalStatus.COMPLETED }
                val planGoal = activeGoal ?: lastCompleted
                val plan = profile?.let { planBuilder.build(it, planGoal, today, records) }
                val progress = profile?.let { progressAnalyzer.analyze(records, today) }
                val currentWeek = plan?.weeks?.firstOrNull()
                val completedPlannedRuns = if (profile != null && currentWeek != null) {
                    completionAnalyzer.completedRuns(records, currentWeek, profile, activeGoal)
                } else 0
                val completedMatches = records.mapNotNull(Workout::planMatch)
                val nextScheduledRun = plan?.let { upcomingRunSelector.select(it, today, completedMatches) }
                val nextRunTitle = nextScheduledRun?.run?.title ?: nextPlannedRunTitle(plan, completedPlannedRuns)
                val forecast = forecastAnalyzer.analyze(planGoal, records, today, nextPlannedRunTitle = nextRunTitle)
                val explicitGoalTime = planGoal?.targetTimeMinutes?.takeIf { it > 0 }
                val forecastMinutes = forecast?.takeIf {
                    it.confidence == de.run.app.domain.ForecastConfidence.SUPPORTED
                }?.estimatedMinutes?.let { (it.first + it.last) / 2.0 }
                val paceReferenceMinutes = explicitGoalTime?.toDouble()
                    ?: forecastMinutes.takeIf { planGoal?.raceDistanceMeters in HALF_MARATHON_DISTANCE_RANGE }
                val goalPace = planGoal?.raceDistanceMeters?.takeIf { it > 0 }?.let { distance ->
                    paceReferenceMinutes?.let { (it * 60_000.0 / distance).toLong() }
                }
                val candidate = candidateSelector.select(activeGoal, records)
                if (profile != null && plan != null) {
                    try {
                        decisions.recordCurrent(decisionFactory.create(profile, planGoal, records, plan, today))
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        // The current plan remains usable if its local explanation cannot be refreshed.
                    }
                }
                mutableState.value = PlanUiState(
                    isLoading = false,
                    profile = profile,
                    goal = planGoal,
                    goals = history,
                    plan = plan,
                    isEditing = mutableState.value.isEditing,
                    editingStep = mutableState.value.editingStep,
                    isEditingGoal = mutableState.value.isEditingGoal,
                    isCreatingGoal = mutableState.value.isCreatingGoal,
                    dismissedCompetitionWorkoutId = mutableState.value.dismissedCompetitionWorkoutId,
                    dismissedGoalAchievementId = mutableState.value.dismissedGoalAchievementId,
                    progress = progress,
                    completedPlannedRunCount = completedPlannedRuns,
                    nextScheduledRun = nextScheduledRun,
                    halfMarathonForecast = forecast,
                    pendingCompetitionCandidate = candidate,
                    goalPaceSecondsPerKilometer = goalPace,
                    goalPaceUsesForecast = explicitGoalTime == null && forecastMinutes != null &&
                        planGoal?.raceDistanceMeters in HALF_MARATHON_DISTANCE_RANGE
                )
                if (candidate != null && activeGoal?.pendingCompetitionWorkoutId == null) {
                    goals.update(activeGoal!!.copy(pendingCompetitionWorkoutId = candidate.sourceId))
                }
            }
        }
    }

    fun retryLoading() {
        mutableState.value = mutableState.value.copy(isLoading = true, hasLoadError = false)
        observePlan()
    }

    fun saveInitialSetup(profile: TrainingProfile, goal: TrainingGoal, onSaved: () -> Unit = {}) {
        mutableState.value = mutableState.value.copy(isEditing = true)
        saveWith {
            profiles.save(profile.copy(planMode = TrainingPlanMode.RACE))
            goals.saveAsCurrent(goal)
            onSaved()
        }
    }

    fun saveProfile(profile: TrainingProfile) {
        saveWith { profiles.save(profile) }
    }

    fun saveGoal(goal: TrainingGoal) {
        saveWith {
            goals.saveAsCurrent(goal.copy(id = UUID.randomUUID().toString()))
            mutableState.value.profile?.let { profiles.save(it.copy(planMode = TrainingPlanMode.RACE)) }
            mutableState.value = mutableState.value.copy(isEditingGoal = false, isCreatingGoal = false)
        }
    }

    private fun saveWith(action: suspend () -> Unit) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, saveFailed = false)
            try {
                action()
                mutableState.value = mutableState.value.copy(isSaving = false, saveFailed = false, isEditing = false)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(isSaving = false, saveFailed = true)
            }
        }
    }

    fun editProfile(step: Int) {
        mutableState.value = mutableState.value.copy(isEditing = true, editingStep = step, saveFailed = false)
    }

    fun cancelProfileEdit() {
        mutableState.value = mutableState.value.copy(isEditing = false, saveFailed = false)
    }

    fun editAvailability() = editProfile(2)
    fun editStartPoint() = editProfile(3)

    fun editGoal(createNew: Boolean = false) {
        mutableState.value = mutableState.value.copy(
            isEditingGoal = true,
            isCreatingGoal = createNew,
            saveFailed = false
        )
    }

    fun cancelGoalEdit() {
        mutableState.value = mutableState.value.copy(isEditingGoal = false, isCreatingGoal = false, saveFailed = false)
    }

    fun setPlanMode(mode: TrainingPlanMode) {
        val profile = mutableState.value.profile ?: return
        saveWith { profiles.save(profile.copy(planMode = mode)) }
    }

    fun answerCompetitionQuestion(wasGoalEvent: Boolean) {
        val goal = mutableState.value.goals.firstOrNull { it.status == TrainingGoalStatus.ACTIVE } ?: return
        val workout = mutableState.value.pendingCompetitionCandidate ?: return
        viewModelScope.launch {
            goals.update(
                goal.copy(
                    status = if (wasGoalEvent) TrainingGoalStatus.COMPLETED else goal.status,
                    competitionCompleted = if (wasGoalEvent) true else null,
                    competitionWorkoutId = if (wasGoalEvent) workout.sourceId else null,
                    pendingCompetitionWorkoutId = null,
                    lastPromptedWorkoutId = workout.sourceId
                )
            )
        }
    }

    fun dismissCompetitionQuestion() {
        val candidateId = mutableState.value.pendingCompetitionCandidate?.sourceId ?: return
        mutableState.value = mutableState.value.copy(dismissedCompetitionWorkoutId = candidateId)
    }

    fun showCompetitionQuestion() {
        mutableState.value = mutableState.value.copy(dismissedCompetitionWorkoutId = null)
    }

    fun dismissGoalAchievementQuestion() {
        val unresolvedId = mutableState.value.goals.firstOrNull {
            it.status == TrainingGoalStatus.COMPLETED && it.competitionCompleted == true && it.goalAchieved == null
        }?.id ?: return
        mutableState.value = mutableState.value.copy(dismissedGoalAchievementId = unresolvedId)
    }

    fun showGoalAchievementQuestion() {
        mutableState.value = mutableState.value.copy(dismissedGoalAchievementId = null)
    }

    fun saveGoalAchievement(achieved: Boolean) {
        val goal = mutableState.value.goals.firstOrNull {
            it.status == TrainingGoalStatus.COMPLETED && it.competitionCompleted == true && it.goalAchieved == null
        } ?: return
        viewModelScope.launch {
            goals.update(goal.copy(goalAchieved = achieved))
            mutableState.value = mutableState.value.copy(dismissedGoalAchievementId = null)
        }
    }

    private fun nextPlannedRunTitle(plan: TrainingPlan?, completedRuns: Int): String? {
        val week = plan?.weeks?.firstOrNull() ?: return null
        if (completedRuns >= week.requiredRunCount) return week.runs.firstOrNull(PlannedRun::optional)?.title
        val next = week.scheduledRuns.minByOrNull { it.scheduledDate?.toEpochDay() ?: it.day.value.toLong() }?.run
            ?: week.runs.firstOrNull { !it.optional }
        return next?.title
    }

    private fun observeLocalDate(): Flow<LocalDate> = flow {
        while (true) {
            val today = LocalDate.now()
            emit(today)
            val nextDay = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
            val waitMillis = Duration.between(Instant.now(), nextDay).toMillis().coerceAtLeast(1_000L)
            delay(waitMillis)
        }
    }

    fun runForAvailableTime(run: PlannedRun, minutes: Int): PlannedRun? =
        runDurationAdjuster.fitToAvailableTime(run, minutes)
}

private data class PlanInputs(
    val profile: TrainingProfile?,
    val history: List<TrainingGoal>,
    val workouts: List<Workout>,
    val today: LocalDate
)
