package de.run.app.presentation.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import de.run.app.presentation.components.ExpandableSection
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.run.app.domain.model.GarminConnectMobileState
import de.run.app.domain.model.GarminDataFieldState
import de.run.app.domain.model.GarminLiveDiagnostics
import de.run.app.domain.model.GarminReceiverState
import de.run.app.domain.model.GarminSdkState
import de.run.app.domain.model.GarminTransportMode
import de.run.app.domain.model.GarminWatchState
import de.run.app.domain.model.GarminTargetTransferState

@Composable
internal fun GarminLiveDiagnosticsPanel(
    diagnostics: GarminLiveDiagnostics,
    expanded: Boolean = false,
    onToggle: () -> Unit = {}
) {
    ExpandableSection("Verbindungsdetails", expanded, onToggle) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DiagnosticRow("Transportmodus", diagnostics.transport.label())
                DiagnosticRow("Garmin Connect Mobile", diagnostics.connectMobile.label())
                DiagnosticRow("Connect-IQ-SDK", diagnostics.sdk.label())
                DiagnosticRow("Erkanntes Garmin-Gerät", diagnostics.knownDeviceName ?: "Keins")
                DiagnosticRow("Garmin-Uhr", diagnostics.watch.label())
                DiagnosticRow("LAP-Live-Datenfeld", diagnostics.dataField.label())
                diagnostics.dataFieldAppId?.let { DiagnosticRow("Connect-IQ-App-ID", it) }
                DiagnosticRow("Nachrichtenempfänger", diagnostics.receiver.label())
                DiagnosticRow("Eingegangene Garmin-Nachrichten", diagnostics.incomingMessages.toString())
                DiagnosticRow("Gültige PINGs", diagnostics.validPings.toString())
                DiagnosticRow("Gültige Messpakete", diagnostics.validMeasurements.toString())
                if (diagnostics.targetTrainingId != null) {
                    DiagnosticRow("LAP-Trainingsziel", diagnostics.targetTransfer.label())
                }
                if (diagnostics.rejectedMessages > 0L) {
                    DiagnosticRow("Nicht verwendbare Nachrichten", diagnostics.rejectedMessages.toString())
                }
                Text(
                    diagnostics.nextStep(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun GarminTargetTransferState.label(): String = when (this) {
    GarminTargetTransferState.NOT_SENT -> "Noch nicht gesendet"
    GarminTargetTransferState.SENT -> "An die Uhr gesendet"
    GarminTargetTransferState.ACKNOWLEDGED -> "Von der Uhr übernommen"
    GarminTargetTransferState.FAILED -> "Nicht übertragen"
}

private fun GarminLiveDiagnostics.nextStep(): String = when {
    transport != GarminTransportMode.WIRELESS -> "PING-Test noch nicht starten: Für die echte Uhr muss WIRELESS aktiv sein."
    connectMobile != GarminConnectMobileState.READY -> "PING-Test noch nicht starten: Garmin Connect Mobile ist nicht bereit."
    sdk != GarminSdkState.READY -> "PING-Test noch nicht starten: Das Connect-IQ-SDK ist nicht bereit."
    watch != GarminWatchState.CONNECTED -> "PING-Test noch nicht starten: Warte, bis die Garmin-Uhr als verbunden angezeigt wird."
    dataField != GarminDataFieldState.FOUND -> "PING-Test noch nicht starten: LAP Live wurde auf der Uhr noch nicht erkannt."
    receiver != GarminReceiverState.REGISTERED -> "PING-Test noch nicht starten: Der Nachrichtenempfänger ist noch nicht registriert."
    else -> "Bereit für genau einen PING. Erst jetzt LAP Live auf der Uhr aktivieren. Empfang gilt erst mit PING-Zähler 1 als bestätigt."
}

private fun GarminTransportMode.label(): String = when (this) {
    GarminTransportMode.UNKNOWN -> "Noch unbekannt"
    GarminTransportMode.WIRELESS -> "WIRELESS · Garmin Connect"
    GarminTransportMode.TETHERED -> "TETHERED · Simulator"
}

private fun GarminConnectMobileState.label(): String = when (this) {
    GarminConnectMobileState.NOT_CHECKED -> "Noch nicht geprüft"
    GarminConnectMobileState.CHECKING -> "Wird geprüft"
    GarminConnectMobileState.READY -> "Erreichbar"
    GarminConnectMobileState.NOT_INSTALLED -> "Nicht installiert"
    GarminConnectMobileState.UPGRADE_REQUIRED -> "Update erforderlich"
    GarminConnectMobileState.UNAVAILABLE -> "Nicht erreichbar"
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

private fun GarminSdkState.label(): String = when (this) {
    GarminSdkState.NOT_STARTED -> "Nicht gestartet"
    GarminSdkState.INITIALIZING -> "Wird gestartet"
    GarminSdkState.READY -> "Bereit"
    GarminSdkState.UNAVAILABLE -> "Nicht verfügbar"
}

private fun GarminWatchState.label(): String = when (this) {
    GarminWatchState.UNKNOWN -> "Noch unbekannt"
    GarminWatchState.NOT_PAIRED -> "Nicht gekoppelt"
    GarminWatchState.DISCONNECTED -> "Nicht verbunden"
    GarminWatchState.CONNECTED -> "Verbunden"
}

private fun GarminDataFieldState.label(): String = when (this) {
    GarminDataFieldState.NOT_CHECKED -> "Noch nicht geprüft"
    GarminDataFieldState.CHECKING -> "Wird gesucht"
    GarminDataFieldState.FOUND -> "Erkannt"
    GarminDataFieldState.NOT_INSTALLED -> "Nicht erkannt"
    GarminDataFieldState.ERROR -> "Prüfung fehlgeschlagen"
}

private fun GarminReceiverState.label(): String = when (this) {
    GarminReceiverState.NOT_REGISTERED -> "Nicht registriert"
    GarminReceiverState.REGISTERING -> "Wird registriert"
    GarminReceiverState.REGISTERED -> "Aufruf erfolgreich"
    GarminReceiverState.FAILED -> "Registrierung fehlgeschlagen"
}

