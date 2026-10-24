package de.run.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.run.app.application.ConnectGarminConnect
import de.run.app.application.DisconnectGarminConnect
import de.run.app.application.SubmitGarminConnectMfa
import de.run.app.application.SyncAllGarminConnectActivities
import de.run.app.domain.repository.GarminConnectLoginResult
import de.run.app.domain.repository.GarminConnectRepository
import de.run.app.domain.repository.GarminConnectHistorySyncResult
import de.run.app.domain.repository.GarminConnectHistorySyncSummary
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GarminConnectUiState(
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val needsMfa: Boolean = false,
    val isSyncing: Boolean = false,
    val hasSyncError: Boolean = false,
    val message: String? = null,
    val originalFitSaved: Boolean = false,
    val workoutImported: Boolean = false,
    val trainingPlanUpdated: Boolean = false,
    val lastHistorySyncAt: Instant? = null
)

class GarminConnectViewModel(
    private val repository: GarminConnectRepository
) : ViewModel() {
    private val connectAccount = ConnectGarminConnect(repository)
    private val verifyMfa = SubmitGarminConnectMfa(repository)
    private val syncActivities = SyncAllGarminConnectActivities(repository)
    private val disconnect = DisconnectGarminConnect(repository)
    private val mutableState = MutableStateFlow(GarminConnectUiState())
    val state: StateFlow<GarminConnectUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isConnected = repository.isConnected())
        }
    }

    fun connect(email: String, password: CharArray) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isConnecting = true, message = null, needsMfa = false)
            val result = runCatching { connectAccount(email.trim(), password) }.getOrNull()
            mutableState.value = mutableState.value.copy(
                isConnecting = false,
                isConnected = result == GarminConnectLoginResult.Connected,
                needsMfa = result == GarminConnectLoginResult.NeedsMfa,
                message = loginMessage(result)
            )
        }
    }

    fun submitMfa(code: CharArray) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isConnecting = true, message = null)
            val result = runCatching { verifyMfa(code) }.getOrNull()
            mutableState.value = mutableState.value.copy(
                isConnecting = false,
                isConnected = result == GarminConnectLoginResult.Connected,
                needsMfa = result == GarminConnectLoginResult.NeedsMfa,
                message = loginMessage(result)
            )
        }
    }

    fun syncTargetActivity() = syncAllActivities()

    fun syncAllActivities() {
        if (mutableState.value.isSyncing) return
        if (!mutableState.value.isConnected) {
            mutableState.value = mutableState.value.copy(
                message = "Verbinde zuerst Garmin Connect, um deine Aktivitäten abzugleichen.",
                hasSyncError = false
            )
            return
        }
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(
                isSyncing = true,
                message = "Synchronisierung gestartet … Aktivitäten werden geprüft …",
                hasSyncError = false,
                originalFitSaved = false,
                workoutImported = false,
                trainingPlanUpdated = false
            )
            val result = runCatching { syncActivities() }.getOrNull()
            mutableState.value = when (result) {
                is GarminConnectHistorySyncResult.Completed -> {
                    val summary = result.summary
                    mutableState.value.copy(
                        isSyncing = false,
                        hasSyncError = false,
                        message = listOf(
                            historyMessage(summary.listingCompleted, summary.found, summary.newlyImported, summary.alreadyPresent,
                                summary.failed, summary.originalsSaved, false, false),
                            healthHistoryMessage(summary)
                        ).joinToString(" · "),
                        originalFitSaved = summary.originalsSaved > 0,
                        workoutImported = summary.newlyImported > 0 || summary.alreadyPresent > 0,
                        trainingPlanUpdated = summary.newlyImported > 0 || summary.alreadyPresent > 0,
                        lastHistorySyncAt = Instant.ofEpochMilli(summary.completedAtMillis)
                    )
                }
                is GarminConnectHistorySyncResult.Partial -> {
                    val summary = result.summary
                    mutableState.value.copy(
                        isSyncing = false,
                        hasSyncError = !summary.listingCompleted || summary.failed > 0 ||
                            summary.stoppedByRateLimit || summary.stoppedByGarminAccess,
                        isConnected = !result.authenticationExpired,
                        message = listOf(
                            historyMessage(summary.listingCompleted, summary.found, summary.newlyImported, summary.alreadyPresent,
                                summary.failed, summary.originalsSaved, summary.stoppedByRateLimit, summary.stoppedByGarminAccess),
                            healthHistoryMessage(summary)
                        ).joinToString(" · "),
                        originalFitSaved = summary.originalsSaved > 0,
                        workoutImported = summary.newlyImported > 0 || summary.alreadyPresent > 0,
                        trainingPlanUpdated = summary.newlyImported > 0 || summary.alreadyPresent > 0,
                        lastHistorySyncAt = Instant.ofEpochMilli(summary.completedAtMillis)
                    )
                }
                GarminConnectHistorySyncResult.AuthenticationExpired -> mutableState.value.copy(
                    isSyncing = false, isConnected = false, hasSyncError = true,
                    message = "Die Garmin-Anmeldung ist abgelaufen. Bitte verbinde dich erneut."
                )
                GarminConnectHistorySyncResult.Failed -> mutableState.value.copy(
                    isSyncing = false,
                    hasSyncError = true,
                    message = "Die Garmin-Aktivitäten konnten nicht abgerufen werden. Bereits übernommene Läufe bleiben erhalten."
                )
                null -> mutableState.value.copy(
                    isSyncing = false,
                    hasSyncError = true,
                    message = "Die Garmin-Aktivitäten konnten nicht abgerufen werden. Garmin Connect und deine gespeicherten Läufe blieben unverändert."
                )
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            disconnect()
            mutableState.value = GarminConnectUiState(message = "Garmin Connect wurde auf diesem Gerät getrennt.")
        }
    }

    private fun loginMessage(result: GarminConnectLoginResult?): String? = when (result) {
        GarminConnectLoginResult.Connected -> "Garmin Connect ist auf diesem Gerät verbunden."
        GarminConnectLoginResult.NeedsMfa -> "Gib den Sicherheitscode von Garmin ein."
        GarminConnectLoginResult.InvalidCredentials -> "Anmeldung nicht möglich. Prüfe deine Angaben."
        GarminConnectLoginResult.AccessBlocked -> "Garmin hat die Anmeldung abgelehnt oder begrenzt. LAP versucht keine Umgehung."
        GarminConnectLoginResult.Failed, null -> "Anmeldung fehlgeschlagen. Garmin-Daten wurden nicht verändert."
    }

    private fun historyMessage(
        listingCompleted: Boolean,
        found: Int,
        imported: Int,
        alreadyPresent: Int,
        failed: Int,
        originals: Int,
        rateLimited: Boolean,
        accessDenied: Boolean
    ): String = buildList {
        if (listingCompleted) add("$found Aktivitäten gefunden · $imported neu importiert · $alreadyPresent bereits vorhanden")
        else add("Garmin konnte die Aktivitätsliste nicht vollständig liefern.")
        add("$originals Originaldateien dauerhaft gesichert")
        if (failed > 0) add("$failed Aktivitäten konnten nicht übernommen werden")
        if (rateLimited) add("Garmin hat die Anfrage begrenzt. Später erneut versuchen.")
        if (accessDenied) add("Garmin hat weitere Abrufe abgelehnt. Später erneut versuchen.")
        if (listingCompleted && found == 0) add("Es wurden keine Aktivitäten geliefert.")
        else if (imported > 0 || alreadyPresent > 0) add("Der Trainingsplan wurde anhand der gespeicherten Daten neu berechnet.")
    }.joinToString(" · ")

    private fun healthHistoryMessage(summary: GarminConnectHistorySyncSummary): String = when {
        summary.healthAuthenticationExpired -> "Die Garmin-Anmeldung ist abgelaufen; Gesundheitswerte konnten nicht aktualisiert werden."
        summary.healthSyncBlocked -> "Garmin hat den Abruf der Gesundheitswerte begrenzt. Bitte später erneut versuchen."
        summary.healthSyncFailed -> "Schlaf und Ruhepuls konnten diesmal nicht abgerufen werden."
        summary.healthDaysUpdated > 0 -> "Schlaf und Ruhepuls für ${summary.healthDaysUpdated} Tage gespeichert."
        else -> "Garmin hat für die letzten Tage keine Schlaf- oder Ruhepulswerte bereitgestellt."
    }

}
