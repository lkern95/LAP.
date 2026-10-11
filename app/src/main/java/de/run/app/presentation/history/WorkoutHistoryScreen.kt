package de.run.app.presentation.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import de.run.app.R
import de.run.app.domain.model.Workout
import de.run.app.presentation.components.RunIllustration
import de.run.app.presentation.components.AppPopup
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun WorkoutHistoryScreen(viewModel: WorkoutHistoryViewModel, requestAccess: () -> Unit) {
    val state by viewModel.state.collectAsState()
    var selectedWorkoutId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedWorkout = state.workouts.firstOrNull { it.sourceId == selectedWorkoutId }
    val fitFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importFit(it.toString()) }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("workout-history-list"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Dein Fortschritt",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (state.isLoading && state.workouts.isNotEmpty()) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
        }
        item {
            PeriodSelector(state.selectedPeriod, viewModel::selectPeriod)
        }
        if (state.accessMessage != null || state.hasError) {
            item {
                DataStatusCard(
                    state = state,
                    onRequestAccess = requestAccess,
                    onRetry = viewModel::refresh
                )
            }
        }
        item {
            ProgressVolumeCard(
                dashboard = state.dashboard,
                loading = state.isLoading && state.workouts.isEmpty()
            )
        }
        item {
            ProgressHighlights(state.dashboard)
        }
        item {
            Text("Deine Läufe", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = { fitFilePicker.launch(arrayOf("application/octet-stream", "application/vnd.ant.fit", "*/*")) },
                    enabled = !state.isImportingFit
                ) {
                    if (state.isImportingFit) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("FIT-Datei importieren")
                }
                state.fitImportMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
        if (state.isLoading && state.workouts.isEmpty()) {
            item { LoadingState() }
        } else if (state.workouts.isEmpty()) {
            item {
                EmptyRunsCard(
                    permissionMissing = state.accessMessage != null,
                    onRetry = viewModel::refresh
                )
            }
        } else {
            items(state.workouts, key = Workout::sourceId) { workout ->
                WorkoutRow(workout) { selectedWorkoutId = workout.sourceId }
            }
        }
        }
        AppPopup(
            visible = selectedWorkout != null,
            title = "Deine Werte",
            onDismiss = { selectedWorkoutId = null }
        ) {
          selectedWorkout?.let { workout ->
              Spacer(Modifier.height(14.dp))
              Text(workout.title, style = MaterialTheme.typography.bodyMedium)
              Spacer(Modifier.height(14.dp))
              WorkoutMetricRow(
                  "Dauer", formatDuration(workout.durationSeconds),
                  "Strecke", workout.distanceMeters?.let(::formatPreciseDistance) ?: "Nicht erfasst"
              )
              Spacer(Modifier.height(10.dp))
              WorkoutMetricRow(
                  "Durchschnittstempo", workout.paceSecondsPerKilometer?.let { "${formatPace(it)} Min./km" } ?: "Nicht erfasst",
                  "Durchschnittspuls", workout.averageHeartRateBpm?.let { "$it Schläge/Min." } ?: "Nicht erfasst"
              )
              Spacer(Modifier.height(14.dp))
              Text("Für diesen Lauf liegt keine Streckenkarte vor.", style = MaterialTheme.typography.bodySmall)
          }
        }
    }
}

@Composable
private fun WorkoutRow(workout: Workout, onToggleDetails: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RunIllustration(R.drawable.run_training_run, "Lauf", size = 42.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(workout.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(formatStart(workout.startedAtMillis), style = MaterialTheme.typography.bodySmall)
                Text(
                    listOf("${workout.durationSeconds / 60} Min.", workout.sourceName)
                        .joinToString(" · ") { it },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                workout.distanceMeters?.let { Text("${formatKilometers(it)} km", style = MaterialTheme.typography.bodyMedium) }
                workout.averageHeartRateBpm?.let { average ->
                    val peak = workout.maximumHeartRateBpm?.let { " · Spitze $it" }.orEmpty()
                    Text("Puls im Lauf: Ø $average$peak Schläge/Min.", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = onToggleDetails) { Text("Laufdetails ansehen") }
            }
        }
    }
}

@Composable
private fun WorkoutMetricRow(firstLabel: String, firstValue: String, secondLabel: String, secondValue: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        WorkoutMetric(firstLabel, firstValue, Modifier.weight(1f))
        WorkoutMetric(secondLabel, secondValue, Modifier.weight(1f))
    }
}

@Composable
private fun WorkoutMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

internal fun formatKilometers(meters: Double): String {
    val km = meters / 1_000.0
    return if (km >= 100.0 && km % 1.0 < 0.05) km.toInt().toString()
    else "%.1f".format(Locale.GERMANY, km)
}

internal fun formatPace(secondsPerKilometer: Long): String =
    "%d:%02d".format(secondsPerKilometer / 60, secondsPerKilometer % 60)

private fun formatPreciseDistance(meters: Double): String =
    "%.2f km".format(Locale.GERMANY, meters / 1_000.0)

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3_600
    val minutes = (seconds % 3_600) / 60
    val remainder = seconds % 60
    return if (hours > 0) "%d:%02d:%02d h".format(hours, minutes, remainder)
    else "%d:%02d Min.".format(minutes, remainder)
}

private fun formatStart(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("EEE, dd.MM.yyyy · HH:mm", Locale.GERMAN))
