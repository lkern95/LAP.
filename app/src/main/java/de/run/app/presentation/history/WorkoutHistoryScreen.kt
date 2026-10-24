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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import de.run.app.domain.WorkoutHistoryPeriodFilter
import de.run.app.domain.WorkoutHistoryPeriodSelection
import de.run.app.domain.HalfMarathonForecast
import de.run.app.domain.ForecastConfidence
import de.run.app.presentation.components.AppDropdown
import de.run.app.presentation.components.AppDropdownOption
import de.run.app.domain.model.WorkoutActivityType
import de.run.app.R
import de.run.app.domain.model.Workout
import de.run.app.presentation.components.RunIllustration
import de.run.app.presentation.components.AppPopup
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun WorkoutHistoryScreen(
    viewModel: WorkoutHistoryViewModel,
    requestAccess: () -> Unit,
    goalDistanceMeters: Int? = null,
    goalTimeMinutes: Int? = null,
    goalPaceSecondsPerKilometer: Long? = null,
    goalPaceUsesForecast: Boolean = false,
    halfMarathonForecast: HalfMarathonForecast? = null
) {
    val state by viewModel.state.collectAsState()
    var selectedWorkoutId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedYear by rememberSaveable { mutableIntStateOf(0) }
    var selectedMonth by rememberSaveable { mutableIntStateOf(0) }
    val periodFilter = remember { WorkoutHistoryPeriodFilter() }
    val selection = periodFilter.normalize(
        state.workouts,
        if (selectedYear > 0 && selectedMonth > 0) WorkoutHistoryPeriodSelection(selectedYear, selectedMonth) else null
    )
    LaunchedEffect(selection) {
        if (selection != null && (selection.year != selectedYear || selection.month != selectedMonth)) {
            selectedYear = selection.year
            selectedMonth = selection.month
        }
    }
    val years = periodFilter.years(state.workouts)
    val months = selection?.let { periodFilter.months(state.workouts, it.year) }.orEmpty()
    val filteredWorkouts = periodFilter.filter(
        state.workouts,
        selection,
        null
    )
    val selectedWorkout = state.workouts.firstOrNull { it.sourceId == selectedWorkoutId }
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
            ProgressHighlights(
                state.dashboard,
                goalDistanceMeters?.toDouble() ?: 21_097.5,
                goalPaceSecondsPerKilometer,
                goalPaceUsesForecast
            )
        }
        item {
            Text("Deine Aktivitäten", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        if (years.isNotEmpty()) {
            item {
                ActivityHistoryFilters(
                    years = years,
                    months = months,
                    selectedYear = selection?.year ?: years.first(),
                    selectedMonth = selection?.month ?: months.first(),
                    monthName = periodFilter::monthName,
                    onYearSelected = { year ->
                        selectedYear = year
                        selectedMonth = periodFilter.months(state.workouts, year).firstOrNull() ?: 0
                    },
                    onMonthSelected = { selectedMonth = it }
                )
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
            items(filteredWorkouts, key = Workout::sourceId) { workout ->
                WorkoutRow(workout) { selectedWorkoutId = workout.sourceId }
            }
            if (filteredWorkouts.isEmpty() && state.workouts.isNotEmpty()) {
                item { Text("Für diese Auswahl sind keine Aktivitäten vorhanden.", style = MaterialTheme.typography.bodyMedium) }
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
                  "Datum", formatStart(workout.startedAtMillis),
                  "Dauer", formatDuration(workout.durationSeconds),
              )
              Spacer(Modifier.height(10.dp))
              WorkoutMetricRow(
                  "Strecke", workout.distanceMeters?.let(::formatPreciseDistance) ?: "Nicht erfasst",
                  if (workout.activityType == de.run.app.domain.model.WorkoutActivityType.RUNNING) "Durchschnittstempo" else "Kadenz",
                  if (workout.activityType == de.run.app.domain.model.WorkoutActivityType.RUNNING) {
                      workout.paceSecondsPerKilometer?.let { "${formatPace(it)} Min./km" } ?: "Nicht erfasst"
                  } else workout.averageCadenceRpm?.let { "$it U/Min." } ?: "Nicht erfasst"
              )
              Spacer(Modifier.height(10.dp))
              WorkoutMetricRow(
                  "Ø Puls", workout.averageHeartRateBpm?.let { "$it Schläge/Min." } ?: "Nicht erfasst",
                  "Maximalpuls", workout.maximumHeartRateBpm?.let { "$it Schläge/Min." } ?: "Nicht erfasst"
              )
              Spacer(Modifier.height(10.dp))
              WorkoutGoalProjectionCard(halfMarathonForecast)
              Spacer(Modifier.height(14.dp))
              Text("Quelle: ${workout.sourceName}", style = MaterialTheme.typography.bodySmall)
          }
        }
    }
}

@Composable
internal fun WorkoutGoalProjectionCard(forecast: HalfMarathonForecast?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Halbmarathon-Prognose", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val estimate = forecast?.estimatedMinutes
            if (estimate != null) {
                val label = when (forecast.confidence) {
                    ForecastConfidence.TRAINING_ORIENTATION -> "Grobe Trainingsorientierung"
                    ForecastConfidence.ROUGH -> "Grobe Orientierung"
                    else -> "Zeitprognose"
                }
                Text("$label: ${formatRaceTime(estimate.first)}–${formatRaceTime(estimate.last)}", style = MaterialTheme.typography.bodyMedium)
                if (forecast.confidence != ForecastConfidence.SUPPORTED) {
                    Text("Große Unsicherheit; Trainingspace ist keine Wettkampfpace.", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Text("Für eine Zeitprognose fehlen noch aktuelle, aussagekräftige Läufe.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
internal fun ActivityHistoryFilters(
    years: List<Int>,
    months: List<Int>,
    selectedYear: Int,
    selectedMonth: Int,
    monthName: (Int) -> String,
    onYearSelected: (Int) -> Unit,
    onMonthSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        FilterDropdown("Jahr", "history-year", selectedYear.toString(), years.map(Int::toString), Modifier.weight(1f)) { value ->
            value.toIntOrNull()?.let(onYearSelected)
        }
        FilterDropdown("Monat", "history-month", monthName(selectedMonth), months.map(monthName), Modifier.weight(1.35f)) { value ->
            months.firstOrNull { monthName(it) == value }?.let(onMonthSelected)
        }
    }
}

@Composable
private fun FilterDropdown(
    label: String,
    tag: String,
    selected: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    onSelected: (String) -> Unit
) {
    var expanded by rememberSaveable(label) { mutableStateOf(false) }
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        AppDropdown(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            options = options.map { AppDropdownOption(it, it) },
            onOptionSelected = onSelected,
            modifier = Modifier.fillMaxWidth(),
            menuModifier = Modifier.testTag("$tag-popup")
        ) { anchorModifier ->
            OutlinedButton(
                onClick = { expanded = !expanded },
                modifier = anchorModifier.fillMaxWidth().heightIn(min = 52.dp).testTag("$tag-anchor"),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(selected, maxLines = 1, modifier = Modifier.weight(1f))
                Text("⌄", style = MaterialTheme.typography.titleMedium)
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
            RunIllustration(R.drawable.run_training_run, workout.activityType.label, size = 42.dp)
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
                if (workout.activityType == WorkoutActivityType.RUNNING) {
                    workout.paceSecondsPerKilometer?.let {
                        Text("Ø ${formatPace(it)} Min./km", style = MaterialTheme.typography.bodySmall)
                    }
                }
                workout.averageHeartRateBpm?.let { average ->
                    val peak = workout.maximumHeartRateBpm?.let { " · Spitze $it" }.orEmpty()
                    Text("Puls im Lauf: Ø $average$peak Schläge/Min.", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = onToggleDetails) { Text("Details ansehen") }
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

private fun formatRaceTime(minutes: Int): String = "%d:%02d h".format(minutes / 60, minutes % 60)

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
