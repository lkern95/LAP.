package de.run.app.presentation.strength

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.run.app.domain.StrengthProgressAnalyzer
import de.run.app.domain.model.ExerciseCompletion
import de.run.app.domain.repository.StrengthRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class StrengthUiState(
    val isLoading: Boolean = true,
    val completedInLastFourWeeks: Map<String, Int> = emptyMap(),
    val completedToday: Set<String> = emptySet(),
    val saveFailed: Boolean = false,
    val loadFailed: Boolean = false
)

class StrengthViewModel(
    private val repository: StrengthRepository,
    private val progressAnalyzer: StrengthProgressAnalyzer = StrengthProgressAnalyzer()
) : ViewModel() {
    private val mutableState = MutableStateFlow(StrengthUiState())
    val state: StateFlow<StrengthUiState> = mutableState.asStateFlow()

    init {
        observeCompletions()
    }

    private fun observeCompletions() {
        viewModelScope.launch {
            repository.observeCompletions().catch {
                mutableState.value = mutableState.value.copy(isLoading = false, loadFailed = true)
            }.collectLatest { records ->
                val today = LocalDate.now()
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    completedToday = records.filter { it.completedOn == today }.map { it.exerciseId }.toSet(),
                    completedInLastFourWeeks = progressAnalyzer.completionsInLastFourWeeks(records, today)
                )
            }
        }
    }

    fun retryLoading() {
        mutableState.value = mutableState.value.copy(isLoading = true, loadFailed = false)
        observeCompletions()
    }

    fun complete(exerciseId: String) {
        if (exerciseId in mutableState.value.completedToday) return
        val today = LocalDate.now()
        mutableState.value = mutableState.value.copy(
            completedToday = mutableState.value.completedToday + exerciseId,
            saveFailed = false
        )
        viewModelScope.launch {
            try {
                repository.complete(ExerciseCompletion("$exerciseId:$today", exerciseId, today))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(
                    completedToday = mutableState.value.completedToday - exerciseId,
                    saveFailed = true
                )
            }
        }
    }
}

class StrengthViewModelFactory(private val repository: StrengthRepository) :
    androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        StrengthViewModel(repository) as T
}
