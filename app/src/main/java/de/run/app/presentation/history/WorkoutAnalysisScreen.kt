package de.run.app.presentation.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.run.app.R
import de.run.app.presentation.components.ExpandableSection
import de.run.app.presentation.components.PersonalTrainingFeedbackCard
import de.run.app.domain.model.Workout
import de.run.app.domain.TrainingFeedback
import de.run.app.domain.model.RunningSymptomFeedback
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun WorkoutAnalysisScreen(
    workout: Workout?,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    isSavingFeedback: Boolean = false,
    feedbackSaveFailed: Boolean = false,
    trainingFeedback: TrainingFeedback? = null,
    onSaveFeedback: (Int?, RunningSymptomFeedback?) -> Unit = { _, _ -> },
    onSkipFeedback: () -> Unit = {}
) {
    if (workout == null) {
        MissingWorkoutAnalysis(onBack)
        return
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("workout-analysis-list"),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (trainingFeedback != null) item { PersonalTrainingFeedbackCard(trainingFeedback) }
            item { Text("Deine Laufwerte", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            item { WorkoutMetricGrid(workout) }
            item { SyncConfirmation(workout) }
            item { SyncDetails(workout) }
            item {
                WorkoutFeedbackCard(
                    workout = workout,
                    isSaving = isSavingFeedback,
                    saveFailed = feedbackSaveFailed,
                    onSave = onSaveFeedback,
                    onSkip = onSkipFeedback
                )
            }
        }
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp).height(56.dp),
            contentPadding = PaddingValues(horizontal = 22.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Text("Weiter", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("→", style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

@Composable
private fun WorkoutFeedbackCard(
    workout: Workout,
    isSaving: Boolean,
    saveFailed: Boolean,
    onSave: (Int?, RunningSymptomFeedback?) -> Unit,
    onSkip: () -> Unit
) {
    var effort by rememberSaveable(workout.sourceId) { mutableStateOf<Int?>(workout.feedback?.perceivedEffort) }
    var symptoms by rememberSaveable(workout.sourceId) {
        mutableStateOf(workout.feedback?.runningAffectedBySymptoms)
    }
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Wie war der Lauf für dich?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (workout.feedback != null) {
                Text("Deine Rückmeldung ist gespeichert.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("Wie anstrengend war er insgesamt? 0 = sehr leicht · 10 = sehr anstrengend")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0..10).forEach { value ->
                        if (value == effort) Button(onClick = { effort = value }, enabled = !isSaving) {
                            Text(value.toString())
                        } else OutlinedButton(onClick = { effort = value }, enabled = !isSaving) {
                            Text(value.toString())
                        }
                    }
                }
                Text("Haben Beschwerden dein Laufen beeinträchtigt?")
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SymptomChoice("Nein", RunningSymptomFeedback.NO, symptoms, !isSaving) { symptoms = it }
                    SymptomChoice("Ja", RunningSymptomFeedback.YES, symptoms, !isSaving) { symptoms = it }
                    SymptomChoice("Unsicher", RunningSymptomFeedback.UNSURE, symptoms, !isSaving) { symptoms = it }
                }
                if (saveFailed) Text("Deine Rückmeldung konnte nicht gespeichert werden. Versuch es noch einmal.", color = MaterialTheme.colorScheme.error)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onSave(effort, symptoms) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSaving && (effort != null || symptoms != null)
                    ) {
                        Text(if (isSaving) "Wird gespeichert …" else "Rückmeldung speichern")
                    }
                    OutlinedButton(onClick = onSkip, modifier = Modifier.fillMaxWidth(), enabled = !isSaving) {
                        Text("Überspringen")
                    }
                }
            }
        }
    }
}

@Composable
private fun SymptomChoice(
    label: String,
    value: RunningSymptomFeedback,
    selected: RunningSymptomFeedback?,
    enabled: Boolean,
    onSelect: (RunningSymptomFeedback) -> Unit
) {
    if (selected == value) Button(onClick = { onSelect(value) }, enabled = enabled) {
        Text(label)
    } else OutlinedButton(onClick = { onSelect(value) }, enabled = enabled) {
        Text(label)
    }
}

@Composable
private fun WorkoutCelebration(workout: Workout) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("Deine Laufanalyse", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Image(
            painter = painterResource(R.drawable.run_status_feedback_success),
            contentDescription = null,
            modifier = Modifier.size(178.dp)
        )
        Text(
            "Lauf ausgewertet",
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            formatWorkoutDate(workout.startedAtMillis),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun WorkoutMetricGrid(workout: Workout) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WorkoutMetric(
                "Distanz",
                workout.distanceMeters?.takeIf { it > 0.0 }?.let { "${formatKilometers(it)} km" },
                R.drawable.ic_workout_location,
                Modifier.weight(1f)
            )
            WorkoutMetric(
                "Dauer",
                workout.durationSeconds.takeIf { it > 0L }?.let(::formatWorkoutDuration),
                R.drawable.ic_workout_timer,
                Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WorkoutMetric(
                "Ø Pace",
                workout.paceSecondsPerKilometer?.let(::formatPace),
                R.drawable.ic_workout_pace,
                Modifier.weight(1f),
                unit = "min/km"
            )
            WorkoutMetric(
                "Ø Puls",
                workout.averageHeartRateBpm?.toString(),
                R.drawable.ic_workout_heart,
                Modifier.weight(1f),
                unit = "bpm",
                iconTint = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
private fun WorkoutMetric(
    label: String,
    value: String?,
    iconResource: Int,
    modifier: Modifier = Modifier,
    unit: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    val shape = RoundedCornerShape(20.dp)
    Card(
        modifier = modifier.heightIn(min = 120.dp).border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
            shape = shape
        ),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                painter = painterResource(iconResource),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(30.dp)
            )
            Text(
                value ?: "Nicht erfasst",
                style = if (value == null) MaterialTheme.typography.titleMedium
                else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            if (unit != null && value != null) {
                Text(unit, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
            }
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SyncConfirmation(workout: Workout) {
    val message = when (workout.sourceName) {
        "Garmin Connect" -> "Mit Garmin Connect synchronisiert"
        "Garmin-Backup" -> "Aus Garmin-Backup importiert"
        "FIT-Datei" -> "FIT-Aktivität importiert"
        else -> "Lauf gespeichert"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("✓", color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.headlineMedium)
            Text(message, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun SyncDetails(workout: Workout) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ExpandableSection(
        title = "Details zur Synchronisierung",
        expanded = expanded,
        onToggle = { expanded = !expanded },
        modifier = Modifier.padding(horizontal = 10.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (workout.sourceName.contains("garmin", ignoreCase = true)) "Quelle: Garmin Connect" else "Quelle: verbundene Lauf-App",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Eine Streckenkarte wurde für diesen Lauf nicht mitgesendet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MissingWorkoutAnalysis(onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Die Auswertung ist gerade nicht verfügbar.", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text("Öffne deinen Laufverlauf, um die synchronisierten Läufe anzusehen.", textAlign = TextAlign.Center)
        Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) { Text("Zurück") }
    }
}

private fun formatWorkoutDuration(seconds: Long): String {
    val hours = seconds / 3_600
    val minutes = (seconds % 3_600) / 60
    val remainder = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, remainder)
    else "%d:%02d".format(minutes, remainder)
}

private fun formatWorkoutDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("EEEE, d. MMMM · HH:mm", Locale.GERMAN))
