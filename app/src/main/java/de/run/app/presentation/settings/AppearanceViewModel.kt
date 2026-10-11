package de.run.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.run.app.domain.model.AppTheme
import de.run.app.domain.repository.AppThemeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class AppearanceUiState(
    val theme: AppTheme = AppTheme.OLIVE,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false
)

class AppearanceViewModel(private val themes: AppThemeRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(AppearanceUiState())
    val state: StateFlow<AppearanceUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            themes.observe().collectLatest { theme ->
                mutableState.value = mutableState.value.copy(theme = theme, isSaving = false)
            }
        }
    }

    fun select(theme: AppTheme) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(theme = theme, isSaving = true, saveFailed = false)
            try {
                themes.save(theme)
                mutableState.value = mutableState.value.copy(isSaving = false)
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(isSaving = false, saveFailed = true)
            }
        }
    }
}
