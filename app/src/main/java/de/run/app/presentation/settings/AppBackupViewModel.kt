package de.run.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.run.app.domain.repository.AppBackupException
import de.run.app.domain.repository.AppBackupFailure
import de.run.app.domain.repository.AppBackupPreview
import de.run.app.domain.repository.AppBackupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppBackupUiState(
    val isWorking: Boolean = false,
    val preview: AppBackupPreview? = null,
    val message: String? = null,
    val isSuccess: Boolean = false
)

class AppBackupViewModel(private val backups: AppBackupRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(AppBackupUiState())
    val state: StateFlow<AppBackupUiState> = mutableState.asStateFlow()

    fun create(uri: String, password: CharArray) = viewModelScope.launch {
        mutableState.value = AppBackupUiState(isWorking = true)
        try {
            withContext(Dispatchers.IO) { backups.create(uri, password) }
            mutableState.value = AppBackupUiState(message = "Deine Sicherung ist fertig.", isSuccess = true)
        } catch (failure: AppBackupException) {
            mutableState.value = AppBackupUiState(message = failure.messageForUser())
        } catch (_: Exception) {
            mutableState.value = AppBackupUiState(message = "Die Sicherung konnte nicht erstellt werden.")
        } finally {
            password.fill('\u0000')
        }
    }

    fun inspect(uri: String, password: CharArray) = viewModelScope.launch {
        mutableState.value = AppBackupUiState(isWorking = true)
        try {
            val preview = withContext(Dispatchers.IO) { backups.inspect(uri, password) }
            mutableState.value = AppBackupUiState(preview = preview)
        } catch (failure: AppBackupException) {
            mutableState.value = AppBackupUiState(message = failure.messageForUser())
        } catch (_: Exception) {
            mutableState.value = AppBackupUiState(message = "Die Sicherung konnte nicht gelesen werden.")
        } finally {
            password.fill('\u0000')
        }
    }

    fun restore(confirmReplacingExistingData: Boolean) = viewModelScope.launch {
        val preview = mutableState.value.preview ?: return@launch
        mutableState.value = mutableState.value.copy(isWorking = true, message = null)
        try {
            withContext(Dispatchers.IO) { backups.restore(preview.id, confirmReplacingExistingData) }
            mutableState.value = AppBackupUiState(message = "Deine Sicherung wurde wiederhergestellt.", isSuccess = true)
        } catch (failure: AppBackupException) {
            mutableState.value = AppBackupUiState(message = failure.messageForUser())
        } catch (_: Exception) {
            mutableState.value = AppBackupUiState(message = "Die Sicherung konnte nicht wiederhergestellt werden.")
        }
    }

    fun clearMessage() {
        mutableState.value = AppBackupUiState()
    }

    private fun AppBackupException.messageForUser(): String = when (failure) {
        AppBackupFailure.INVALID_FILE -> "Diese Sicherungsdatei ist beschädigt oder unvollständig."
        AppBackupFailure.WRONG_PASSWORD_OR_DAMAGED -> "Passwort falsch oder die Datei beschädigt."
        AppBackupFailure.UNSUPPORTED_VERSION -> "Diese Sicherung stammt aus einer nicht unterstützten LAP-Version."
        AppBackupFailure.WRITE_FAILED -> "Die Sicherung konnte nicht gespeichert werden."
        AppBackupFailure.RESTORE_FAILED -> "Die Sicherung konnte nicht wiederhergestellt werden. Deine bisherigen Daten bleiben erhalten."
        AppBackupFailure.RESTORE_RECOVERY_FAILED -> "Die Wiederherstellung wurde unterbrochen. Deine Daten müssen vor der weiteren Nutzung geprüft werden."
    }
}
