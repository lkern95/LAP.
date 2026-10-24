package de.run.app.presentation.readiness

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.run.app.domain.ReadinessFactsAnalyzer
import de.run.app.domain.ReadinessGuidanceAnalyzer
import de.run.app.domain.WeeklyTrainingComparison
import de.run.app.domain.WeeklyTrainingComparisonAnalyzer
import de.run.app.domain.model.HealthMetricAvailability
import de.run.app.domain.model.ReadinessFacts
import de.run.app.domain.model.ReadinessGuidance
import de.run.app.domain.model.ReadinessHealthSnapshot
import de.run.app.domain.model.TrainingProfile
import de.run.app.domain.model.Workout
import de.run.app.domain.model.WorkoutReadResult
import de.run.app.domain.repository.ReadinessHealthRepository
import de.run.app.domain.repository.TrainingProfileRepository
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
    private val factsAnalyzer: ReadinessFactsAnalyzer = ReadinessFactsAnalyzer(),
    private val guidanceAnalyzer: ReadinessGuidanceAnalyzer = ReadinessGuidanceAnalyzer(),
    private val weeklyComparisonAnalyzer: WeeklyTrainingComparisonAnalyzer = WeeklyTrainingComparisonAnalyzer()
) : ViewModel() {
    private val mutableState = MutableStateFlow(ReadinessUiState())
    val state: StateFlow<ReadinessUiState> = mutableState.asStateFlow()
    private var recentWorkouts: List<Workout> = emptyList()
    private var profile: TrainingProfile? = null

    init {
        viewModelScope.launch {
            combine(workouts.observeRecent(), profiles.observe()) { records, savedProfile -> records to savedProfile }
                .catch { mutableState.value = mutableState.value.copy(workoutsFailed = true, isLoading = false) }
                .collect { (records, savedProfile) ->
                    recentWorkouts = records
                    profile = savedProfile
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
                health.read(now.minus(Duration.ofHours(36)), now)
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
        mutableState.value = mutableState.value.copy(
            facts = factsAnalyzer.analyze(recentWorkouts, profile, Instant.now()),
            weeklyTrainingComparison = weeklyComparisonAnalyzer.analyze(recentWorkouts, java.time.LocalDate.now())
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
    private val profiles: TrainingProfileRepository
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ReadinessViewModel::class.java))
        return ReadinessViewModel(workouts, health, profiles) as T
    }
}

internal fun ReadinessUiState.availableFactCount(): Int = listOf(
    health?.sleepMinutes?.availability == HealthMetricAvailability.AVAILABLE,
    health?.restingHeartRateBpm?.availability == HealthMetricAvailability.AVAILABLE,
    hasWorkoutAccess == true && facts?.minutesSinceLastRun != null,
    hasWorkoutAccess == true
).count { it }
