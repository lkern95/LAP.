package de.run.app.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.input.PasswordVisualTransformation
import de.run.app.domain.repository.AppBackupRepository
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.run.app.domain.model.AppTheme
import de.run.app.R

@Composable
fun AppearanceScreen(
    viewModel: AppearanceViewModel,
    backupViewModel: AppBackupViewModel,
    onOpenProgress: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val backupState by backupViewModel.state.collectAsState()
    var passwordDialog by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var restoreUri by remember { mutableStateOf<String?>(null) }
    var pendingPassword by remember { mutableStateOf<CharArray?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }
    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.lap.sicherung")
    ) { uri ->
        val savedPassword = pendingPassword
        pendingPassword = null
        if (uri != null && savedPassword != null) backupViewModel.create(uri.toString(), savedPassword)
        else savedPassword?.fill('\u0000')
    }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        restoreUri = uri?.toString()
        if (uri != null) passwordDialog = true
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Ich", style = MaterialTheme.typography.headlineMedium)
        Text("Deine App", style = MaterialTheme.typography.titleLarge)
        Card(Modifier.fillMaxWidth().clickable(onClick = onOpenProgress)) {
            Row(
                Modifier.padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.run_garmin_devices_watch),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    contentScale = ContentScale.Fit
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Läufe und Garmin Connect", style = MaterialTheme.typography.titleMedium)
                    Text("Synchronisierte Läufe findest du unter Fortschritt.")
                }
            }
        }
        Text("Farbstil", style = MaterialTheme.typography.titleLarge)
        AppTheme.entries.forEach { theme ->
            ThemeOption(
                theme = theme,
                selected = state.theme == theme,
                onSelect = { viewModel.select(theme) }
            )
        }
        if (state.isSaving) Text("Wird gespeichert …")
        if (state.saveFailed) Text("Deine Auswahl konnte nicht gespeichert werden. Bitte versuche es erneut.")
        Text("Sicherung", style = MaterialTheme.typography.titleLarge)
        Text("Übertrage deine LAP-Daten geschützt auf ein neues Handy.")
        Text("Die Freigabe für Gesundheitsdaten richtest du auf dem neuen Handy erneut ein.")
        Button(
            onClick = {
                restoreUri = null
                password = ""
                passwordDialog = true
            },
            enabled = !backupState.isWorking
        ) { Text("Sicherung erstellen") }
        OutlinedButton(
            onClick = { openDocument.launch(arrayOf("application/octet-stream", "application/vnd.lap.sicherung")) },
            enabled = !backupState.isWorking
        ) { Text("Sicherung wiederherstellen") }
        backupState.message?.let { message ->
            Text(message, color = if (backupState.isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        }
        if (backupState.isWorking) CircularProgressIndicator()
        backupState.preview?.let { preview ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sicherung geprüft", style = MaterialTheme.typography.titleMedium)
                    Text("${preview.summary.workoutCount} Läufe · ${preview.summary.strengthSessionCount} Krafteinheiten · ${preview.summary.goalCount} Ziele")
                    Text("Deine Rückmeldungen und Einstellungen sind ebenfalls enthalten.")
                    Button(onClick = { confirmRestore = true }, enabled = !backupState.isWorking) {
                        Text("Wiederherstellen")
                    }
                    TextButton(onClick = backupViewModel::clearMessage) { Text("Schließen") }
                }
            }
        }
    }

    if (passwordDialog) {
        AlertDialog(
            onDismissRequest = {
                passwordDialog = false
                password = ""
                restoreUri = null
            },
            title = { Text(if (restoreUri == null) "Sicherung schützen" else "Sicherung öffnen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (restoreUri == null) "Lege ein Passwort mit mindestens 8 Zeichen fest. Merke es dir gut." else "Gib das Passwort deiner Sicherung ein.")
                    TextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Passwort") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = password.length >= AppBackupRepository.MIN_PASSWORD_LENGTH,
                    onClick = {
                        val secret = password.toCharArray()
                        password = ""
                        passwordDialog = false
                        val uri = restoreUri
                        if (uri == null) {
                            pendingPassword = secret
                            createDocument.launch("lap-sicherung.lap")
                        } else {
                            restoreUri = null
                            backupViewModel.inspect(uri, secret)
                        }
                    }
                ) { Text("Weiter") }
            },
            dismissButton = { TextButton(onClick = { passwordDialog = false; password = ""; restoreUri = null }) { Text("Abbrechen") } }
        )
    }

    if (confirmRestore) {
        val replacesData = backupState.preview?.summary?.replacesExistingData == true
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text(if (replacesData) "Vorhandene Daten ersetzen?" else "Sicherung wiederherstellen?") },
            text = { Text(if (replacesData) "Diese Sicherung ersetzt die vorhandenen LAP-Daten auf diesem Handy." else "Die geprüften LAP-Daten werden auf diesem Handy wiederhergestellt.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    backupViewModel.restore(confirmReplacingExistingData = replacesData)
                }) { Text("Wiederherstellen") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Abbrechen") } }
        )
    }
}

@Composable
private fun ThemeOption(theme: AppTheme, selected: Boolean, onSelect: () -> Unit) {
    val (title, detail) = when (theme) {
        AppTheme.OLIVE -> "Olive" to "Warmes Weiß mit Dunkelgrün und Rosé"
        AppTheme.DARK -> "Dunkel" to "Dunkle Flächen mit gut lesbarer Schrift"
        AppTheme.LIGHT -> "Hell" to "Helle, weiße Flächen"
    }
    Row(
        Modifier.fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .semantics(mergeDescendants = true) { }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
