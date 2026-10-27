package de.run.app.presentation.readiness

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.run.app.domain.ReadinessFactsAnalyzer
import de.run.app.domain.ReadinessAssessmentAnalyzer
import de.run.app.domain.ReadinessGuidanceAnalyzer
import de.run.app.domain.WeeklyTrainingComparison
import de.run.app.domain.WeeklyTrainingComparisonAnalyzer
import de.run.app.domain.TrainingPlanBuilder
import de.run.app.domain.model.TrainingGoal
import de.run.app.domain.model.TrainingGoalStatus
import de.run.app.domain.model.HealthMetricAvailability
import de.run.app.domain.model.ReadinessFacts
import de.run.app.domain.model.ReadinessAssessment
import de.run.app.domain.model.ReadinessGuidance
import de.run.app.domain.model.ReadinessHealthSnapshot
import de.run.app.domain.model.TrainingProfile
import de.run.app.domain.model.Workout
import de.run.app.domain.model.WorkoutReadResult
import de.run.app.domain.model.isRunning
import de.run.app.domain.repository.ReadinessHealthRepository
import de.run.app.domain.repository.TrainingProfileRepository
import de.run.app.domain.repository.TrainingGoalRepository
import de.run.app.domain.repository.WorkoutRepository
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class ReadinessUiState(
    val isLoading: Boolean = true,
    val health: ReadinessHealthSnapshot? = null,
    val healthReadFailed: Boolean = false,
    val facts: ReadinessFacts? = null,
    val assessment: ReadinessAssessment = ReadinessAssessment(score = null),
    val recentWorkouts: List<Workout> = emptyList(),
    val hasWorkoutAccess: Boolean? = null,
    val weeklyTrainingComparison: WeeklyTrainingComparison? = null,
    val workoutsUnavailable: Boolean = false,
    val workoutsFailed: Boolean = false,
    val isDemo: Boolean = false,
    val guidance: ReadinessGuidance =
        ReadinessGuidanceAnalyzer().analyze(null, true, null, false, false)
)

class ReadinessViewModel(
    private val workouts: WorkoutRepository,
    private val health: ReadinessHealthRepository,
    profiles: TrainingProfileRepository,
    goals: TrainingGoalRepository,
    private val planBuilder: TrainingPlanBuilder,
    private val factsAnalyzer: ReadinessFactsAnalyzer = ReadinessFactsAnalyzer(),
    private val guidanceAnalyzer: ReadinessGuidanceAnalyzer = ReadinessGuidanceAnalyzer(),
    private val weeklyComparisonAnalyzer: WeeklyTrainingComparisonAnalyzer = WeeklyTrainingComparisonAnalyzer(),
    private val assessmentAnalyzer: ReadinessAssessmentAnalyzer = ReadinessAssessmentAnalyzer()
) : ViewModel() {
    private val mutableState = MutableStateFlow(ReadinessUiState())
    val state: StateFlow<ReadinessUiState> = mutableState.asStateFlow()
    private var recentWorkouts: List<Workout> = emptyList()
    private var profile: TrainingProfile? = null
    private var goalHistory: List<TrainingGoal> = emptyList()

    init {
        viewModelScope.launch {
            combine(workouts.observeRecent(), profiles.observe(), goals.observeHistory()) { records, savedProfile, history ->
                ReadinessInputs(records, savedProfile, history)
            }
                .catch { mutableState.value = mutableState.value.copy(workoutsFailed = true, isLoading = false) }
                .collect { inputs ->
                    recentWorkouts = inputs.workouts
                    profile = inputs.profile
                    goalHistory = inputs.goalHistory
                    updateFacts()
                }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isLoading = true)
            val now = Instant.now()
            val workoutResult = try {
                workouts.refresh(now.minus(Duration.ofDays(14)), now)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                WorkoutReadResult.Failed
            }
            val healthSnapshot = try {
                health.read(now.minus(Duration.ofDays(28)), now)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
            mutableState.value = mutableState.value.copy(
                health = healthSnapshot,
                healthReadFailed = healthSnapshot == null,
                isLoading = false,
                hasWorkoutAccess = when (workoutResult) {
                    is WorkoutReadResult.Available -> true
                    WorkoutReadResult.PermissionNeeded -> false
                    WorkoutReadResult.Unavailable,
                    WorkoutReadResult.Failed -> null
                },
                workoutsUnavailable = workoutResult == WorkoutReadResult.Unavailable,
                workoutsFailed = workoutResult == WorkoutReadResult.Failed,
                isDemo = healthSnapshot?.isDemo == true
            )
            updateFacts()
        }
    }

    private fun updateFacts() {
        val now = Instant.now()
        val today = now.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val currentPlan = profile?.let { savedProfile ->
            val goal = goalHistory.firstOrNull { it.status == TrainingGoalStatus.ACTIVE }
                ?: goalHistory.firstOrNull { it.status == TrainingGoalStatus.COMPLETED }
            planBuilder.build(savedProfile, goal, today, recentWorkouts)
        }
        val currentWeek = currentPlan?.weeks?.firstOrNull { it.startsOn == today.with(
            java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)
        ) }
        mutableState.value = mutableState.value.copy(
            facts = factsAnalyzer.analyze(recentWorkouts, currentWeek?.plannedRunCount, now, today),
            weeklyTrainingComparison = weeklyComparisonAnalyzer.analyze(recentWorkouts, today),
            recentWorkouts = recentWorkouts,
            assessment = assessmentAnalyzer.analyze(mutableState.value.health, recentWorkouts, Instant.now())
        )
        mutableState.value = mutableState.value.copy(guidance = guidanceAnalyzer.analyze(
            facts = mutableState.value.facts,
            isLoading = mutableState.value.isLoading,
            hasWorkoutAccess = mutableState.value.hasWorkoutAccess,
            workoutsUnavailable = mutableState.value.workoutsUnavailable,
            workoutsFailed = mutableState.value.workoutsFailed
        ))
    }
}

class ReadinessViewModelFactory(
    private val workouts: WorkoutRepository,
    private val health: ReadinessHealthRepository,
    private val profiles: TrainingProfileRepository,
    private val goals: TrainingGoalRepository,
    private val planBuilder: TrainingPlanBuilder
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ReadinessViewModel::class.java))
        return ReadinessViewModel(workouts, health, profiles, goals, planBuilder) as T
    }
}

private data class ReadinessInputs(
    val workouts: List<Workout>,
    val profile: TrainingProfile?,
    val goalHistory: List<TrainingGoal>
)

internal fun ReadinessUiState.availableDataAreaCount(): Int = listOf(
    health?.hasHrvData() == true,
    health?.restingHeartRateBpm?.availability == HealthMetricAvailability.AVAILABLE,
    health?.sleepMinutes?.availability == HealthMetricAvailability.AVAILABLE,
    recentWorkouts.any(Workout::isRunning),
    hasWorkoutAccess == true && facts?.minutesSinceLastRun != null,
    health?.averageStressLevel?.availability == HealthMetricAvailability.AVAILABLE ||
        health?.steps?.availability == HealthMetricAvailability.AVAILABLE
).count { it }

internal fun ReadinessHealthSnapshot.hasHrvData(): Boolean =
    hrvLastNightAverageMs.availability == HealthMetricAvailability.AVAILABLE ||
        (hrvStatus.availability == HealthMetricAvailability.AVAILABLE &&
            hrvStatus.value?.uppercase() !in setOf(null, "NONE", "UNKNOWN"))

internal const val READINESS_DATA_SOURCE_COUNT = 6
