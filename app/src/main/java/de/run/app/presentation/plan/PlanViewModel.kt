package de.run.app.presentation.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import de.run.app.domain.model.Workout
import de.run.app.domain.repository.TrainingGoalRepository
import de.run.app.domain.repository.TrainingGoalHistoryUnavailableException
import de.run.app.domain.repository.TrainingProfileRepository
import de.run.app.domain.repository.WorkoutRepository
import de.run.app.domain.repository.TrainingDecisionRepository
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

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
    val hasLoadError: Boolean = false,
    val hasGoalHistoryError: Boolean = false,
    val pendingCompetitionCandidate: Workout? = null,
    val dismissedCompetitionWorkoutId: String? = null,
    val dismissedGoalAchievementId: String? = null
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
    private val mutableState = MutableStateFlow(PlanUiState())
    val state: StateFlow<PlanUiState> = mutableState.asStateFlow()

    init {
        observePlan()
    }

    private fun observePlan() {
        viewModelScope.launch {
            combine(profiles.observe(), goals.observeHistory(), workouts.observeRecent()) { profile, history, records ->
                Triple(profile, history, records)
            }.catch { error ->
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    hasLoadError = true,
                    hasGoalHistoryError = error is TrainingGoalHistoryUnavailableException
                )
            }.collectLatest { (profile, history, records) ->
                val activeGoal = history.firstOrNull { it.status == TrainingGoalStatus.ACTIVE }
                val lastCompleted = history.firstOrNull { it.status == TrainingGoalStatus.COMPLETED }
                val planGoal = activeGoal ?: lastCompleted
                val today = LocalDate.now()
                val plan = profile?.let { planBuilder.build(it, planGoal, today, records) }
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
                    progress = profile?.let { progressAnalyzer.analyze(records, today) },
                    pendingCompetitionCandidate = candidate
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

    fun runForAvailableTime(run: PlannedRun, minutes: Int): PlannedRun? =
        runDurationAdjuster.fitToAvailableTime(run, minutes)
}
