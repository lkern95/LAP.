package de.run.app.presentation.strength

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.run.app.domain.model.StrengthCatalog
import de.run.app.domain.model.StrengthSessionProgress
import de.run.app.domain.StrengthSessionProgressor
import de.run.app.domain.StrengthProgressAnalyzer
import de.run.app.domain.repository.StrengthRepository
import java.time.LocalDate
import de.run.app.domain.repository.StrengthCatalogRepository
import de.run.app.domain.repository.StrengthSessionRepository
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class StrengthUiState(
    val isLoading: Boolean = true,
    val catalog: StrengthCatalog? = null,
    val sessions: List<StrengthSessionProgress> = emptyList(),
    val selectedSessionId: String? = null,
    val remainingSeconds: Int = 0,
    val errorMessage: String? = null,
    val importMessage: String? = null,
    val completionCountsLastFourWeeks: Map<String, Int> = emptyMap(),
    val completionHistoryLoaded: Boolean = true
) {
    val selectedSession: StrengthSessionProgress? get() = sessions.firstOrNull { it.sessionId == selectedSessionId }
}

class StrengthViewModel(
    private val sessionRepository: StrengthSessionRepository,
    private val catalogRepository: StrengthCatalogRepository,
    private val progressor: StrengthSessionProgressor = StrengthSessionProgressor(),
    strengthRepository: StrengthRepository? = null
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        StrengthUiState(completionHistoryLoaded = strengthRepository == null)
    )
    val state: StateFlow<StrengthUiState> = mutableState.asStateFlow()
    private val parser = de.run.app.data.strength.StrengthCatalogJsonParser()
    private val progressAnalyzer = StrengthProgressAnalyzer()
    private var timerJob: Job? = null

    init {
        strengthRepository?.let { repository ->
            viewModelScope.launch {
                repository.observeCompletions().collectLatest { values ->
                    mutableState.value = mutableState.value.copy(
                        completionCountsLastFourWeeks = progressAnalyzer.completionsInLastFourWeeks(values, LocalDate.now()),
                        completionHistoryLoaded = true
                    )
                }
            }
        }
        viewModelScope.launch {
            try {
                val catalog = catalogRepository.load()
                mutableState.value = mutableState.value.copy(isLoading = false, catalog = catalog)
                observeSessions()
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(isLoading = false, errorMessage = "Der Übungskatalog konnte nicht geladen werden.")
            }
        }
    }

    private suspend fun observeSessions() {
        sessionRepository.observeSessions().collectLatest { sessions ->
            val selected = mutableState.value.selectedSessionId
            mutableState.value = mutableState.value.copy(
                sessions = sessions,
                selectedSessionId = selected?.takeIf { id -> sessions.any { it.sessionId == id } }
                    ?: sessions.firstOrNull { it.status == StrengthSessionProgress.Status.IN_PROGRESS }?.sessionId
            )
            selectedSession?.let(::resumeTimer)
        }
    }

    private val selectedSession: StrengthSessionProgress? get() = mutableState.value.selectedSession

    fun catalogFor(session: StrengthSessionProgress): StrengthCatalog = parser.parse(session.catalogSnapshot)

    fun selectSession(sessionId: String) {
        mutableState.value = mutableState.value.copy(selectedSessionId = sessionId)
        selectedSession?.let(::resumeTimer)
    }

    fun startOrResume(workoutId: String, exerciseId: String? = null) {
        viewModelScope.launch {
            try {
                val existing = mutableState.value.sessions.firstOrNull {
                    it.workoutId == workoutId && it.status == StrengthSessionProgress.Status.IN_PROGRESS
                }
                val catalog = mutableState.value.catalog ?: return@launch
                val workout = catalog.workouts.firstOrNull { it.id == workoutId } ?: return@launch
                val exerciseIndex = exerciseId?.let(workout.exerciseIds::indexOf)?.takeIf { it >= 0 } ?: 0
                val session = existing ?: StrengthSessionProgress(
                    sessionId = UUID.randomUUID().toString(),
                    workoutId = workoutId,
                    catalogSnapshot = catalog.sourceJson,
                    status = StrengthSessionProgress.Status.IN_PROGRESS,
                    currentExerciseId = workout.exerciseIds[exerciseIndex],
                    currentRound = 1,
                    completedSets = emptyList(),
                    isCurrentSetStarted = false,
                    pauseUntilMillis = null,
                    startedAtMillis = System.currentTimeMillis(),
                    finishedAtMillis = null
                ).also { sessionRepository.save(it) }
                mutableState.value = mutableState.value.copy(selectedSessionId = session.sessionId, errorMessage = null)
                resumeTimer(session)
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(errorMessage = "Dein Trainingsfortschritt konnte nicht gespeichert werden.")
            }
        }
    }

    fun startWorkoutAndStartSet(workoutId: String, exerciseId: String) {
        viewModelScope.launch {
            try {
                val catalog = mutableState.value.catalog ?: return@launch
                val workout = catalog.workouts.first { it.id == workoutId }
                val index = workout.exerciseIds.indexOf(exerciseId).takeIf { it >= 0 } ?: 0
                val previous = mutableState.value.sessions.firstOrNull {
                    it.workoutId == workoutId && it.status == StrengthSessionProgress.Status.IN_PROGRESS
                }
                val session = previous ?: StrengthSessionProgress(
                    UUID.randomUUID().toString(), workoutId, catalog.sourceJson,
                    StrengthSessionProgress.Status.IN_PROGRESS, workout.exerciseIds[index], 1, emptyList(), false, null,
                    System.currentTimeMillis(), null
                )
                val started = progressor.startSet(session, catalogFor(session), System.currentTimeMillis())
                sessionRepository.save(started)
                mutableState.value = mutableState.value.copy(selectedSessionId = started.sessionId, errorMessage = null)
                if (started.pauseUntilMillis != null) resumeTimer(started)
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(errorMessage = "Der Satz konnte nicht gestartet werden.")
            }
        }
    }

    fun startCurrentSet() {
        updateSelected { session ->
            val updated = progressor.startSet(session, catalogFor(session), System.currentTimeMillis())
            val hold = updated.pauseUntilMillis
            if (hold != null) resumeTimer(updated)
            updated
        }
    }

    fun completeCurrentSet() {
        updateSelected { session ->
            progressor.completeSet(session, catalogFor(session), System.currentTimeMillis())
        }
    }

    fun finishRest() = updateSelected { it.copy(pauseUntilMillis = null) }

    fun skipOptionalExercise() {
        updateSelected { session ->
            progressor.skipOptionalExercise(session, catalogFor(session), System.currentTimeMillis())
        }
    }

    fun importCatalog(json: String) {
        viewModelScope.launch {
            try {
                val result = catalogRepository.import(json)
                mutableState.value = mutableState.value.copy(catalog = catalogRepository.load(), importMessage =
                    "Katalog übernommen: ${result.addedExerciseCount} neue Übungen und ${result.addedWorkoutCount} neue Programme.", errorMessage = null)
            } catch (error: CancellationException) { throw error } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(importMessage = "Der Katalog wurde nicht übernommen. Prüfe Version, IDs und Pflichtfelder.")
            }
        }
    }

    fun clearImportMessage() { mutableState.value = mutableState.value.copy(importMessage = null) }

    private fun updateSelected(transform: (StrengthSessionProgress) -> StrengthSessionProgress) {
        val current = selectedSession ?: return
        viewModelScope.launch {
            try {
                val updated = transform(current)
                sessionRepository.save(updated)
                mutableState.value = mutableState.value.copy(errorMessage = null)
                resumeTimer(updated)
            } catch (error: CancellationException) { throw error } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(errorMessage = "Dein Trainingsfortschritt konnte nicht gespeichert werden.")
            }
        }
    }

    private fun resumeTimer(session: StrengthSessionProgress) {
        timerJob?.cancel()
        val until = session.pauseUntilMillis ?: run {
            mutableState.value = mutableState.value.copy(remainingSeconds = 0)
            return
        }
        timerJob = viewModelScope.launch {
            while (true) {
                val seconds = ((until - System.currentTimeMillis()).coerceAtLeast(0) + 999) / 1_000
                mutableState.value = mutableState.value.copy(remainingSeconds = seconds.toInt())
                if (seconds <= 0) break
                delay(250)
            }
            val current = sessionRepository.getSession(session.sessionId) ?: return@launch
            if (current.pauseUntilMillis == until && current.isCurrentSetStarted) completeCurrentSetFor(session.sessionId, current)
        }
    }

    private suspend fun completeCurrentSetFor(id: String, session: StrengthSessionProgress) {
        if (mutableState.value.selectedSessionId != id) return
        completeCurrentSet()
    }

}

class StrengthViewModelFactory(
    private val sessionRepository: StrengthSessionRepository,
    private val catalogRepository: StrengthCatalogRepository,
    private val strengthRepository: StrengthRepository? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        StrengthViewModel(sessionRepository, catalogRepository, strengthRepository = strengthRepository) as T
}
