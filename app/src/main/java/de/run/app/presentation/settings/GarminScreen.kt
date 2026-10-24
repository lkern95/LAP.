package de.run.app.presentation.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.DpSize
import de.run.app.R
import de.run.app.domain.model.PlanStatus
import de.run.app.domain.GarminConnectHealthSharing
import de.run.app.domain.model.Workout
import de.run.app.presentation.history.WorkoutHistoryUiState
import de.run.app.presentation.history.WorkoutHistoryViewModel
import de.run.app.presentation.settings.GarminConnectViewModel
import de.run.app.presentation.plan.PlanUiState
import de.run.app.presentation.plan.PlanViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun GarminScreen(
    workoutHistoryViewModel: WorkoutHistoryViewModel,
    garminConnectViewModel: GarminConnectViewModel,
    isDemo: Boolean = false,
    planViewModel: PlanViewModel,
    onOpenProgress: () -> Unit,
    onOpenHealthData: () -> Unit,
    onOpenTrainingPlan: () -> Unit,
    onRequestWorkoutAccess: () -> Unit,
    onInstallGarminConnect: () -> Unit,
    onInstallHealthConnect: () -> Unit,
    onBack: () -> Unit,
    garminSharingSupported: Boolean = GarminConnectHealthSharing.isSupported(Build.VERSION.SDK_INT)
) {
    val context = LocalContext.current
    val garminConnectInstalled = remember(context) { context.packageManager.getLaunchIntentForPackage(GARMIN_CONNECT_PACKAGE) != null }
    val healthConnectInstalled = remember(context) { context.packageManager.getLaunchIntentForPackage(HEALTH_CONNECT_PACKAGE) != null }
    val isHuaweiDevice = remember { Build.MANUFACTURER.equals("HUAWEI", ignoreCase = true) }
    val history by workoutHistoryViewModel.state.collectAsState()
    val directConnect by garminConnectViewModel.state.collectAsState()
    val plan by planViewModel.state.collectAsState()
    var showConnectionCheck by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(CONNECTION_CHECK_MINIMUM_MILLIS)
        showConnectionCheck = false
    }
    val importedWorkouts = history.workouts.filter {
        it.sourceName in setOf(GARMIN_SOURCE_NAME, HEALTH_SYNC_SOURCE_NAME, FIT_SOURCE_NAME, GARMIN_BACKUP_SOURCE_NAME)
    }
    val hasGarminActivities = history.isConnectedToGarmin(importedWorkouts)
    val failed = history.accessMessage != null || history.isUnavailable || history.hasError
    val connected = history.lastSuccessfulReadAt != null && !failed && !history.isLoading
    val displayHistory = history.copy(
        isLoading = !connected && (history.isLoading || showConnectionCheck)
    )
    val fitFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { workoutHistoryViewModel.importFit(it.toString()) }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GarminHeader(
            if (garminSharingSupported) displayHistory.connectionLabel(hasGarminActivities)
            else if (displayHistory.isLoading) "Verbindung wird geprüft"
            else if (connected) "Laufdaten verfügbar"
            else "Verbindung prüfen",
            onBack
        )
        if (isDemo) {
            Text(
                "Demoverbindung mit Testdaten",
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.labelMedium
            )
        }
        GarminWatch(
            loading = displayHistory.isLoading || directConnect.isSyncing,
            connected = directConnect.isConnected && !directConnect.isSyncing,
            failed = failed || directConnect.hasSyncError,
            onRetrySync = garminConnectViewModel::syncAllActivities
        )
        GarminStatuses(
            activities = displayHistory.activityStatus(importedWorkouts, garminSharingSupported),
            health = displayHistory.healthStatus(hasGarminActivities, importedWorkouts, garminSharingSupported),
            plan = plan.trainingPlanStatus(),
            onOpenActivities = onOpenProgress,
            onOpenHealthData = onOpenHealthData,
            onOpenTrainingPlan = onOpenTrainingPlan
        )
        Text(
            if (garminSharingSupported) {
                "Starte deinen Lauf auf der Uhr unter Aktivitäten → Laufen und synchronisiere danach Garmin Connect."
            } else {
                "Auf diesem Android werden Garmin-Läufe nicht direkt über Health Connect geteilt. Du kannst Garmin Connect abgleichen oder weiter unten ein Garmin-Backup beziehungsweise eine Aktivitätsdatei importieren."
            },
            style = MaterialTheme.typography.bodyMedium
        )
        history.lastSuccessfulReadAt?.let { checkedAt ->
            Text(
                "Zuletzt erfolgreich geprüft: ${checkedAt.atZone(ZoneId.systemDefault()).format(LAST_CHECK_FORMAT)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        directConnect.lastHistorySyncAt?.let { syncedAt ->
            Text(
                "Garmin-Verlauf zuletzt abgeglichen: ${syncedAt.atZone(ZoneId.systemDefault()).format(LAST_CHECK_FORMAT)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        directConnect.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        history.lastRefreshMessage?.let { resultMessage ->
            val displayMessage = if (resultMessage == NO_NEW_HEALTH_CONNECT_RUNS && importedWorkouts.isNotEmpty()) {
                "Health Connect: keine neuen Läufe gefunden."
            } else resultMessage
            Text(displayMessage, style = MaterialTheme.typography.bodyMedium)
        }
        if (!garminSharingSupported && importedWorkouts.isEmpty() && history.lastRefreshMessage == "Keine neuen Läufe gefunden") {
            Text(
                "Es wurden noch keine Läufe gefunden. Prüfe die Verbindung oder importiere weiter unten eine Aktivitätsdatei.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (history.hasError && isHuaweiDevice) {
            Text(
                "Huawei hat Health Connect im Hintergrund blockiert. Öffne Health Connect einmal und kehre dann hierher zurück.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (importedWorkouts.isNotEmpty()) {
            Text(
                "${importedWorkouts.size} Aktivität${if (importedWorkouts.size == 1) "" else "en"} auf diesem Gerät · zuletzt ${importedWorkouts.first().startedAtMillis.formattedWorkoutDate()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (history.hasFullHistoryAccess == false) {
            Text(
                "Ältere Läufe sind noch nicht freigegeben. Zurzeit kann LAP nur Läufe der letzten 30 Tage anzeigen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!garminSharingSupported) {
            Text(
                "Garmin Connect: ${if (garminConnectInstalled) "installiert" else "nicht gefunden"} · Health Connect: ${if (healthConnectInstalled) "installiert" else "nicht gefunden"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!garminConnectInstalled) {
                OutlinedButton(onClick = onInstallGarminConnect) { Text("Garmin Connect installieren") }
            }
            if (!healthConnectInstalled || history.isUnavailable || history.hasError) {
                OutlinedButton(onClick = onInstallHealthConnect) { Text("Health Connect öffnen") }
            }
        }
        if (garminSharingSupported) {
            GarminAction(history, onOpenProgress, onRequestWorkoutAccess) { workoutHistoryViewModel.refresh() }
        } else if (history.accessMessage != null) {
            Button(onClick = onRequestWorkoutAccess) { Text("Health-Connect-Freigabe einrichten") }
        } else {
            OutlinedButton(onClick = { workoutHistoryViewModel.refresh() }, enabled = !history.isLoading) {
                Text(if (history.isLoading) "Laufdaten werden geprüft …" else "Jetzt prüfen")
            }
        }
        GarminConnectAccountSection(directConnect, garminConnectViewModel)
        OutlinedButton(
            onClick = { fitFilePicker.launch(arrayOf("application/zip", "application/octet-stream", "application/vnd.ant.fit", "*/*")) },
            enabled = !history.isImportingFit,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (history.isImportingFit) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("Garmin-Backup oder Aktivitätsdatei importieren")
        }
        history.fitImportMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun GarminConnectAccountSection(
    state: GarminConnectUiState,
    viewModel: GarminConnectViewModel
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var mfaCode by remember { mutableStateOf("") }

    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Garmin Connect direkt verbinden", style = MaterialTheme.typography.titleMedium)
            Text(
                "LAP liest Aktivitäten und Originaldateien. Dein Passwort wird nicht gespeichert. Garmin kann diesen privaten Zugriffsweg ändern oder ablehnen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when {
                state.isConnected -> {
                    Button(onClick = viewModel::syncAllActivities, enabled = !state.isSyncing) {
                        Text(if (state.isSyncing) "Aktivitäten werden abgeglichen …" else "Synchronisieren")
                    }
                    OutlinedButton(onClick = viewModel::disconnect, enabled = !state.isSyncing) {
                        Text("Verbindung auf diesem Gerät trennen")
                    }
                }
                state.needsMfa -> {
                    OutlinedTextField(
                        value = mfaCode,
                        onValueChange = { mfaCode = it },
                        label = { Text("Garmin-Sicherheitscode") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = {
                        val code = mfaCode.toCharArray()
                        mfaCode = ""
                        viewModel.submitMfa(code)
                    }, enabled = !state.isConnecting && mfaCode.isNotBlank()) {
                        Text(if (state.isConnecting) "Wird geprüft …" else "Code bestätigen")
                    }
                }
                else -> {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Garmin-E-Mail") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Passwort") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = {
                        val secret = password.toCharArray()
                        password = ""
                        viewModel.connect(email, secret)
                    }, enabled = !state.isConnecting && email.isNotBlank() && password.isNotEmpty()) {
                        Text(if (state.isConnecting) "Garmin prüft die Anmeldung …" else "Garmin Connect verbinden")
                    }
                }
            }
            state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (state.originalFitSaved || state.workoutImported || state.trainingPlanUpdated) {
                Text(if (state.originalFitSaved) "✓ Originaldateien gesichert" else "○ Originaldateien gesichert")
                Text(if (state.workoutImported) "✓ Aktivitäten importiert" else "○ Aktivitäten importiert")
                Text(if (state.trainingPlanUpdated) "✓ Trainingsplan aktualisiert" else "○ Trainingsplan aktualisiert")
            }
        }
    }
}

@Composable
private fun GarminHeader(
    subtitle: String,
    onBack: () -> Unit
) {
    Box(Modifier.fillMaxWidth().height(58.dp), contentAlignment = Alignment.Center) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart)
                .semantics { contentDescription = "Zurück zum Profil" }
        ) {
            Text("‹", style = MaterialTheme.typography.headlineLarge)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Garmin", style = MaterialTheme.typography.titleLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun GarminWatch(
    loading: Boolean,
    connected: Boolean,
    failed: Boolean,
    modifier: Modifier = Modifier,
    showIndicator: Boolean = true,
    onRetrySync: (() -> Unit)? = null
) {
    val watchModifier = if (onRetrySync == null) modifier else modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        enabled = !loading,
        role = Role.Button,
        onClick = onRetrySync
    ).semantics { contentDescription = "Garmin-Laufdaten jetzt prüfen" }
    BoxWithConstraints(watchModifier.fillMaxWidth().height(250.dp)) {
        val watchImage = painterResource(R.drawable.garmin)
        val imageRatio = watchImage.intrinsicSize.width / watchImage.intrinsicSize.height
        val geometry = remember(maxWidth, maxHeight, imageRatio) {
            calculateWatchOverlayGeometry(DpSize(maxWidth, maxHeight), imageRatio)
        }
        Image(
            painter = watchImage,
            contentDescription = "Schwarze Garmin-Uhr",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
        if (showIndicator) {
            Box(
                Modifier
                    .offset(geometry.ringTopLeft.x, geometry.ringTopLeft.y)
                    .size(geometry.ringSize)
            ) {
                ConnectionIndicator(loading, connected, failed, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
internal fun ConnectionIndicator(
    loading: Boolean,
    connected: Boolean,
    failed: Boolean,
    modifier: Modifier = Modifier
) {
    val rotation = androidx.compose.runtime.remember { Animatable(0f) }
    val ringProgress = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (connected) 1f else 0f,
        animationSpec = tween(650),
        label = "Garmin-Verbindungsring"
    ).value
    val checkProgress = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (connected) 1f else 0f,
        animationSpec = tween(300, delayMillis = 400),
        label = "Garmin-Verbindungshaken"
    ).value
    val failureProgress = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (failed && !loading) 1f else 0f,
        animationSpec = tween(300, delayMillis = 100),
        label = "Garmin-Verbindungsfehler"
    ).value

    LaunchedEffect(loading, connected) {
        rotation.snapTo(0f)
        if (loading) {
            while (isActive) rotation.animateTo(rotation.value + 360f, tween(900, easing = LinearEasing))
        } else if (connected) {
            rotation.animateTo(360f, tween(650, easing = LinearEasing))
        }
    }

    val color = when {
        connected || (loading && !failed) -> GARMIN_SUCCESS_GREEN
        failed -> GARMIN_FAILURE_RED
        else -> GARMIN_IDLE_GRAY
    }
    val description = when {
        connected -> "Health-Connect-Prüfung erfolgreich"
        failed && !loading -> "Import der Laufdaten fehlgeschlagen"
        loading -> "Laufdaten werden gesucht"
        else -> "Noch keine Laufdaten angekommen"
    }
    Canvas(modifier.semantics { contentDescription = description }) {
        val strokeWidth = 3.5.dp.toPx()
        if (loading) {
            drawArc(color, rotation.value % 360f, 260f, false, style = Stroke(strokeWidth, cap = StrokeCap.Round))
        } else {
            drawArc(
                color,
                -90f,
                if (connected) 360f * ringProgress else 360f,
                false,
                style = Stroke(strokeWidth, cap = StrokeCap.Round)
            )
        }
        if (connected) {
            val check = Path().apply {
                moveTo(size.width * 0.26f, size.height * 0.51f)
                lineTo(size.width * 0.43f, size.height * 0.68f)
                lineTo(size.width * 0.75f, size.height * 0.34f)
            }
            drawPath(check, color.copy(alpha = checkProgress), style = Stroke(strokeWidth, cap = StrokeCap.Round))
        } else if (failed && !loading) {
            val cross = Path().apply {
                moveTo(size.width * 0.34f, size.height * 0.34f)
                lineTo(size.width * 0.66f, size.height * 0.66f)
                moveTo(size.width * 0.66f, size.height * 0.34f)
                lineTo(size.width * 0.34f, size.height * 0.66f)
            }
            drawPath(cross, color.copy(alpha = failureProgress), style = Stroke(strokeWidth, cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun GarminStatuses(
    activities: String,
    health: String,
    plan: String,
    onOpenActivities: () -> Unit,
    onOpenHealthData: () -> Unit,
    onOpenTrainingPlan: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp)) {
            GarminStatusRow("Aktivitäten", activities, R.drawable.run_training_run, onOpenActivities)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            GarminStatusRow("Gesundheitsdaten", health, R.drawable.run_health_bpm, onOpenHealthData)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            GarminStatusRow("Trainingspläne", plan, R.drawable.run_navigation_plan, onOpenTrainingPlan)
        }
    }
}

@Composable
private fun GarminStatusRow(title: String, status: String, iconResource: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(72.dp).clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(iconResource),
            contentDescription = null,
            modifier = Modifier.size(46.dp),
            contentScale = ContentScale.Fit
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(status, style = MaterialTheme.typography.bodySmall)
        }
        Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun GarminAction(
    history: WorkoutHistoryUiState,
    onOpenProgress: () -> Unit,
    onRequestAccess: () -> Unit,
    onRetry: () -> Unit
) {
    when {
        history.accessMessage != null && !history.isUnavailable -> Button(onClick = onRequestAccess) {
            Text("Freigabe einrichten")
        }
        history.isUnavailable || history.hasError -> OutlinedButton(onClick = onRetry) {
            Text("Erneut prüfen")
        }
        else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRetry, enabled = !history.isLoading) {
                Text(if (history.isLoading) "Laufdaten werden geprüft …" else "Jetzt prüfen")
            }
            OutlinedButton(onClick = onOpenProgress) { Text("Läufe ansehen") }
        }
    }
}

private fun WorkoutHistoryUiState.isConnectedToGarmin(garminWorkouts: List<Workout>): Boolean =
    !isLoading && accessMessage == null && !isUnavailable && !hasError && garminWorkouts.isNotEmpty()

private fun WorkoutHistoryUiState.connectionLabel(connected: Boolean): String = when {
    connected && workouts.any { it.sourceName == GARMIN_SOURCE_NAME } -> "Garmin-Laufdaten verfügbar"
    connected -> "Health Connect geprüft"
    isLoading -> "Verbindung wird geprüft"
    accessMessage != null -> "Freigabe fehlt"
    isUnavailable -> "Gerade nicht verfügbar"
    hasError -> "Verbindung konnte nicht geprüft werden"
    else -> "Noch kein Lauf angekommen"
}

private fun WorkoutHistoryUiState.activityStatus(
    workouts: List<Workout>,
    garminSharingSupported: Boolean
): String = when {
    !garminSharingSupported && workouts.any { it.sourceName == GARMIN_BACKUP_SOURCE_NAME } -> "Garmin-Backup importiert"
    !garminSharingSupported && workouts.any { it.sourceName == FIT_SOURCE_NAME } -> "FIT-Dateien importiert"
    !garminSharingSupported && workouts.any { it.sourceName == HEALTH_SYNC_SOURCE_NAME } -> "Frühere importierte Läufe vorhanden"
    !garminSharingSupported -> "Garmin-Verbindung prüfen oder Datei importieren"
    isLoading -> "Wird geprüft"
    accessMessage != null -> "Freigabe nötig"
    isUnavailable -> "Gerade nicht verfügbar"
    hasError -> "Konnte nicht geprüft werden"
    hasFullHistoryAccess == false -> "Import über Health Connect · nur letzte 30 Tage"
    workouts.any { it.sourceName == GARMIN_SOURCE_NAME } -> "Über Health Connect eingelesen"
    else -> "Wartet auf Laufdaten aus Garmin Connect"
}

private fun WorkoutHistoryUiState.healthStatus(
    connected: Boolean,
    workouts: List<Workout>,
    garminSharingSupported: Boolean
): String = when {
    !garminSharingSupported && workouts.any { it.averageHeartRateBpm != null } -> "Laufpuls angekommen"
    !garminSharingSupported && workouts.any { it.sourceName == HEALTH_SYNC_SOURCE_NAME } -> "Importierte Laufdaten vorhanden"
    !garminSharingSupported && workouts.any { it.sourceName == FIT_SOURCE_NAME } -> "Pulsdaten aus FIT-Datei"
    !garminSharingSupported -> "Dein Trainingsplan bleibt nutzbar"
    isLoading -> "Wird geprüft"
    accessMessage != null -> "Freigabe nötig"
    isUnavailable -> "Gerade nicht verfügbar"
    hasError -> "Konnte nicht geprüft werden"
    workouts.any { it.averageHeartRateBpm != null } -> "Laufpuls angekommen"
    connected -> "Freigabe aktiv"
    else -> "Wartet auf Laufdaten"
}

private fun PlanUiState.trainingPlanStatus(): String = when {
    isLoading -> "Wird geladen"
    hasLoadError -> "Konnte nicht geladen werden"
    profile == null -> "Noch nicht eingerichtet"
    plan?.status == PlanStatus.EVENT_TOO_CLOSE -> "Nicht aktiv"
    else -> "Aktiv"
}

private const val GARMIN_SOURCE_NAME = "Garmin Connect"
private const val HEALTH_SYNC_SOURCE_NAME = "Health Sync"
private const val HEALTH_SYNC_SOURCE_PACKAGE = "nl.appyhapps.healthsync"
private const val GARMIN_CONNECT_PACKAGE = "com.garmin.android.apps.connectmobile"
private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
private const val FIT_SOURCE_NAME = "FIT-Datei"
private const val GARMIN_BACKUP_SOURCE_NAME = "Garmin-Backup"
private const val NO_NEW_HEALTH_CONNECT_RUNS = "Keine neuen Läufe gefunden"
private const val CONNECTION_CHECK_MINIMUM_MILLIS = 2_200L
private val LAST_CHECK_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy · HH:mm", Locale.GERMANY)
private val GARMIN_SUCCESS_GREEN = Color(0xFF2E7D32)
private val GARMIN_FAILURE_RED = Color(0xFFC62828)
private val GARMIN_IDLE_GRAY = Color(0xFF9E9E9E)

private fun Long.formattedWorkoutDate(): String =
    java.time.Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY))
