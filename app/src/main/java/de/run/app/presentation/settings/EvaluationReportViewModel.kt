package de.run.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.run.app.application.ExportLapEvaluationReport
import de.run.app.application.LapEvaluationReportBuilder
import de.run.app.application.LapEvaluationReportInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EvaluationReportUiState(
    val report: String? = null,
    val isExporting: Boolean = false,
    val message: String? = null,
    val succeeded: Boolean = false
)

class EvaluationReportViewModel(
    private val builder: LapEvaluationReportBuilder,
    private val exportReport: ExportLapEvaluationReport
) : ViewModel() {
    private val mutableState = MutableStateFlow(EvaluationReportUiState())
    val state: StateFlow<EvaluationReportUiState> = mutableState.asStateFlow()

    fun prepare(input: LapEvaluationReportInput) {
        val report = runCatching { builder.build(input) }.getOrNull()
        mutableState.value = if (report == null) {
            EvaluationReportUiState(message = "Das Auswertungsprotokoll konnte nicht erstellt werden.")
        } else {
            EvaluationReportUiState(report = report)
        }
    }

    fun export(uri: String) {
        val report = mutableState.value.report ?: return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isExporting = true, message = null)
            val succeeded = try {
                exportReport(uri, report)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                false
            }
            mutableState.value = mutableState.value.copy(
                isExporting = false,
                succeeded = succeeded,
                message = if (succeeded) "Das LAP-Auswertungsprotokoll wurde exportiert." else "Das Auswertungsprotokoll konnte nicht gespeichert werden."
            )
        }
    }

    fun clearMessage() {
        mutableState.value = mutableState.value.copy(message = null, succeeded = false)
    }
}

class EvaluationReportViewModelFactory(
    private val builder: LapEvaluationReportBuilder,
    private val exportReport: ExportLapEvaluationReport
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(EvaluationReportViewModel::class.java))
        return EvaluationReportViewModel(builder, exportReport) as T
    }
}
