package de.run.app.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.run.app.application.ImportFitActivity
import de.run.app.application.EvaluateImportedWorkouts
import de.run.app.application.EvaluateWorkoutTrainingFeedback
import de.run.app.domain.NewlySyncedGarminWorkoutSelector
import de.run.app.domain.ProgressPeriod
import de.run.app.domain.TrainingProgressDashboardAnalyzer
import de.run.app.domain.TrainingProgressAnalyzer
import de.run.app.domain.TrainingFeedback
import de.run.app.domain.model.Workout
import de.run.app.domain.model.WorkoutReadResult
import de.run.app.domain.model.WorkoutFeedback
import de.run.app.domain.model.isRunning
import de.run.app.domain.model.RunningSymptomFeedback
import de.run.app.domain.model.TrainingProgress
import de.run.app.domain.model.TrainingProfile
import de.run.app.domain.repository.TrainingGoalRepository
import de.run.app.domain.repository.TrainingProfileRepository
import de.run.app.domain.repository.WorkoutRepository
import de.run.app.domain.repository.FitActivityImportRepository
import de.run.app.domain.repository.FitActivityImportResult
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class WorkoutHistoryUiState(
    val isLoading: Boolean = true,
    val workouts: List<Workout> = emptyList(),
    val accessMessage: String? = null,
    val isUnavailable: Boolean = false,
    val hasError: Boolean = false,
    val progress: TrainingProgress? = null,
    val selectedPeriod: ProgressPeriod = ProgressPeriod.MONTH,
    val dashboard: de.run.app.domain.TrainingProgressDashboard? = null,
    val newlySyncedWorkout: Workout? = null,
    val lastSuccessfulReadAt: Instant? = null,
    val lastRefreshMessage: String? = null,
    val lastRefreshImportedCount: Int? = null,
    val hasFullHistoryAccess: Boolean? = null,
    val isSavingFeedback: Boolean = false,
    val feedbackSaveFailed: Boolean = false,
    val isImportingFit: Boolean = false,
    val fitImportMessage: String? = null,
    val trainingFeedback: TrainingFeedback? = null
)

class WorkoutHistoryViewModel(
    private val workouts: WorkoutRepository,
    private val progressAnalyzer: TrainingProgressAnalyzer = TrainingProgressAnalyzer(),
    private val dashboardAnalyzer: TrainingProgressDashboardAnalyzer = TrainingProgressDashboardAnalyzer(),
    private val newWorkoutSelector: NewlySyncedGarminWorkoutSelector = NewlySyncedGarminWorkoutSelector(),
    private val importFitActivity: ImportFitActivity? = null,
    private val profiles: TrainingProfileRepository? = null,
    private val goals: TrainingGoalRepository? = null,
    private val evaluateImportedWorkouts: EvaluateImportedWorkouts? = null,
    private val evaluateTrainingFeedback: EvaluateWorkoutTrainingFeedback? = null
) : ViewModel() {
    private val mutableState = MutableStateFlow(WorkoutHistoryUiState())
    private var refreshInProgress = false
    private var latestProfile: TrainingProfile? = null
    private var latestGoals = emptyList<de.run.app.domain.model.TrainingGoal>()
    private var observedWorkoutIds: Set<String>? = null
    val state: StateFlow<WorkoutHistoryUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            workouts.observeRecent().catch {
                mutableState.value = mutableState.value.copy(isLoading = false, hasError = true)
            }.collectLatest { records ->
                val previousIds = observedWorkoutIds
                observedWorkoutIds = records.mapTo(mutableSetOf(), Workout::sourceId)
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    hasError = false,
                    workouts = records,
                    newlySyncedWorkout = mutableState.value.newlySyncedWorkout?.let { pending ->
                        records.firstOrNull { it.sourceId == pending.sourceId } ?: pending
                    },
                    progress = progressAnalyzer.analyze(records, java.time.LocalDate.now()),
                    dashboard = dashboardAnalyzer.analyze(
                        records,
                        mutableState.value.selectedPeriod,
                        java.time.LocalDate.now()
                    )
                )
                updateTrainingFeedback(records)
                val newlyObserved = previousIds?.let { known ->
                    records.filter { it.sourceId !in known && !it.sourceId.startsWith("demo:") }
                }.orEmpty()
                if (newlyObserved.isNotEmpty()) {
                    viewModelScope.launch {
                        val evaluated = evaluateImportedWorkouts?.invoke(newlyObserved, records - newlyObserved.toSet()).orEmpty()
                        val newest = evaluated.maxByOrNull(Workout::endedAtMillis) ?: return@launch
                        val latestRecords = runCatching { workouts.observeRecent().first() }.getOrDefault(records)
                        mutableState.value = mutableState.value.copy(
                            workouts = latestRecords,
                            newlySyncedWorkout = newest
                        )
                        updateTrainingFeedback(latestRecords)
                    }
                }
            }
        }
        profiles?.let { repository ->
            viewModelScope.launch {
                repository.observe().collect { latestProfile = it; updateTrainingFeedback(mutableState.value.workouts) }
            }
        }
        goals?.let { repository ->
            viewModelScope.launch {
                repository.observeHistory().collect { latestGoals = it; updateTrainingFeedback(mutableState.value.workouts) }
            }
        }
    }

    private fun updateTrainingFeedback(records: List<Workout>) {
        val workout = mutableState.value.newlySyncedWorkout
            ?: records.filter(Workout::isRunning).maxByOrNull(Workout::startedAtMillis)
            ?: run { mutableState.value = mutableState.value.copy(trainingFeedback = null); return }
        val profile = latestProfile ?: return
        val evaluator = evaluateTrainingFeedback ?: return
        val goal = latestGoals.firstOrNull { it.status == de.run.app.domain.model.TrainingGoalStatus.ACTIVE }
        val today = java.time.LocalDate.now()
        mutableState.value = mutableState.value.copy(
            trainingFeedback = evaluator(workout, records, profile, goal, today)
        )
    }

    fun selectPeriod(period: ProgressPeriod) {
        mutableState.value = mutableState.value.copy(
            selectedPeriod = period,
            dashboard = dashboardAnalyzer.analyze(
                mutableState.value.workouts,
                period,
                java.time.LocalDate.now()
            )
        )
    }

    fun importFit(uri: String) {
        val importer = importFitActivity ?: return
        viewModelScope.launch {
            val priorWorkouts = runCatching { workouts.observeRecent().first() }
                .getOrDefault(mutableState.value.workouts)
            mutableState.value = mutableState.value.copy(isImportingFit = true, fitImportMessage = null)
            val result = runCatching { importer(uri) }.getOrNull()
            val newlyEvaluated = (result as? FitActivityImportResult.Imported)?.let { imported ->
                if (imported.newlyImportedActivities == 0) emptyList()
                else evaluateImportedWorkouts?.invoke(imported.workouts, priorWorkouts).orEmpty()
            }.orEmpty()
            if (newlyEvaluated.isNotEmpty()) {
                val currentRecords = runCatching { workouts.observeRecent().first() }.getOrDefault(priorWorkouts + newlyEvaluated)
                mutableState.value = mutableState.value.copy(
                    workouts = currentRecords,
                    newlySyncedWorkout = newlyEvaluated.maxByOrNull(Workout::endedAtMillis)
                )
                updateTrainingFeedback(currentRecords)
            }
            val message = when (result) {
                is FitActivityImportResult.Imported -> {
                    val categories = result.unsupportedFiles.entries.joinToString(" · ") { (label, count) ->
                        "$label: $count"
                    }
                    buildList {
                        add("${result.workouts.size} Aktivitäten gefunden: ${result.newlyImportedActivities} neu übernommen, ${result.alreadyPresentActivities} bereits vorhanden.")
                        if (result.savedOriginalFiles > 0) add("${result.savedOriginalFiles} Originaldateien gesichert.")
                        if (result.skippedFiles > 0) add("${result.skippedFiles} Dateien konnten nicht übernommen werden.")
                        if (categories.isNotBlank()) add("Nicht importiert: $categories.")
                    }.joinToString(" ")
                }
                FitActivityImportResult.NotActivityFile -> "Diese FIT-Datei enthält keine Aktivität."
                FitActivityImportResult.NoActivitySessions -> "In der FIT-Datei wurde keine Aktivität gefunden."
                FitActivityImportResult.InvalidFile -> "Die FIT-Datei ist beschädigt oder kann nicht gelesen werden."
                FitActivityImportResult.FileTooLarge -> "Die Datei ist zu groß für den sicheren Import."
                FitActivityImportResult.Failed, null -> "Die FIT-Datei konnte nicht übernommen werden."
            }
            mutableState.value = mutableState.value.copy(isImportingFit = false, fitImportMessage = message)
        }
    }

    fun refresh(surfaceNewGarminRun: Boolean = false) {
        if (refreshInProgress) return
        refreshInProgress = true
        viewModelScope.launch {
            try {
                mutableState.value = mutableState.value.copy(
                    isLoading = true,
                    accessMessage = null,
                    isUnavailable = false,
                    hasError = false,
                    lastRefreshMessage = null
                )
                val previouslySaved = try {
                    workouts.observeRecent().first()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    mutableState.value.workouts
                }
                val end = Instant.now()
                val result = try {
                    workouts.refresh(end.minus(Duration.ofDays(365)), end)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    WorkoutReadResult.Failed
                }
                when (result) {
                    is WorkoutReadResult.Available -> {
                        val savedIds = previouslySaved.mapTo(mutableSetOf()) { it.sourceId }
                        val importedCount = result.records.count { it.sourceId !in savedIds }
                        val newRecords = result.records.filter { it.sourceId !in savedIds && it.isRunning() }
                        val evaluatedNewRuns = evaluateImportedWorkouts?.invoke(newRecords, previouslySaved).orEmpty()
                        val latestNew = if (surfaceNewGarminRun) {
                            newWorkoutSelector.select(previouslySaved, result.records)
                        } else null
                        val newlySynced = latestNew?.let { candidate ->
                            evaluatedNewRuns.firstOrNull { it.sourceId == candidate.sourceId } ?: candidate
                        }
                        val currentRecords = runCatching { workouts.observeRecent().first() }
                            .getOrDefault(result.records + evaluatedNewRuns)
                        mutableState.value = mutableState.value.copy(
                            isLoading = false,
                            accessMessage = null,
                            isUnavailable = false,
                            hasError = false,
                            lastSuccessfulReadAt = Instant.now(),
                            lastRefreshImportedCount = importedCount,
                            lastRefreshMessage = if (importedCount > 0) {
                                "$importedCount neue Läufe übernommen"
                            } else "Keine neuen Läufe gefunden",
                            hasFullHistoryAccess = result.hasFullHistoryAccess,
                            newlySyncedWorkout = newlySynced ?: mutableState.value.newlySyncedWorkout,
                            workouts = currentRecords
                        )
                        updateTrainingFeedback(currentRecords)
                    }
                    WorkoutReadResult.PermissionNeeded -> mutableState.value = mutableState.value.copy(
                        isLoading = false,
                        accessMessage = "Gib Läufe frei, damit sie hier erscheinen. Strecke und Puls sind freiwillig; ältere Läufe kannst du zusätzlich freigeben.",
                        isUnavailable = false,
                        hasFullHistoryAccess = null,
                        lastRefreshMessage = null
                    )
                    WorkoutReadResult.Unavailable -> mutableState.value = mutableState.value.copy(
                        isLoading = false,
                        accessMessage = "Die Gesundheitsdaten sind auf diesem Gerät gerade nicht verfügbar.",
                        isUnavailable = true,
                        hasFullHistoryAccess = null,
                        lastRefreshMessage = null
                    )
                    WorkoutReadResult.Failed -> mutableState.value = mutableState.value.copy(
                        isLoading = false,
                        hasError = true,
                        lastRefreshMessage = null
                    )
                }
            } finally {
                refreshInProgress = false
            }
        }
    }

    fun onPermissionResult() {
        refresh(surfaceNewGarminRun = true)
    }

    fun dismissNewlySyncedWorkout() {
        mutableState.value = mutableState.value.copy(newlySyncedWorkout = null)
    }

    fun saveFeedback(perceivedEffort: Int?, symptoms: RunningSymptomFeedback?) {
        val workout = mutableState.value.newlySyncedWorkout ?: return
        saveFeedback(workout, perceivedEffort, symptoms)
    }

    fun skipFeedback() = saveFeedback(null, null)

    private fun saveFeedback(workout: Workout, perceivedEffort: Int?, symptoms: RunningSymptomFeedback?) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSavingFeedback = true, feedbackSaveFailed = false)
            try {
                val feedback = WorkoutFeedback(perceivedEffort, symptoms, System.currentTimeMillis())
                val saved = workouts.saveFeedback(workout.sourceId, feedback)
                mutableState.value = mutableState.value.copy(
                    isSavingFeedback = false,
                    feedbackSaveFailed = !saved,
                    newlySyncedWorkout = if (saved) workout.copy(feedback = feedback) else workout
                )
                if (saved) updateTrainingFeedback(mutableState.value.workouts + workout.copy(feedback = feedback))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(isSavingFeedback = false, feedbackSaveFailed = true)
            }
        }
    }

}

class WorkoutHistoryViewModelFactory(
    private val workouts: WorkoutRepository,
    private val fitImports: FitActivityImportRepository,
    private val profiles: TrainingProfileRepository? = null,
    private val goals: TrainingGoalRepository? = null,
    private val evaluateImportedWorkouts: EvaluateImportedWorkouts? = null,
    private val evaluateTrainingFeedback: EvaluateWorkoutTrainingFeedback? = null
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(WorkoutHistoryViewModel::class.java))
        return WorkoutHistoryViewModel(
            workouts,
            importFitActivity = ImportFitActivity(fitImports),
            profiles = profiles,
            goals = goals,
            evaluateImportedWorkouts = evaluateImportedWorkouts,
            evaluateTrainingFeedback = evaluateTrainingFeedback
        ) as T
    }
}
