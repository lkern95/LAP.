package de.run.app.presentation.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.run.app.R
import de.run.app.domain.model.PlannedRun
import de.run.app.domain.model.paceTarget

@Composable
internal fun TrainingDetailsScreen(
    run: PlannedRun?,
    onBack: () -> Unit,
    onOpenPlan: () -> Unit,
    onStartGarminLive: () -> Unit = {}
) {
    Column(Modifier.fillMaxSize()) {
        DetailsHeader(onBack)
        if (run == null) {
            MissingTrainingDetails(onOpenPlan)
        } else {
            var showStartGuide by rememberSaveable(run.title) { mutableStateOf(false) }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                WorkoutSummary(run)
                run.durationAdjustmentExplanation?.let { DistanceAssessmentCard(it) }
                run.distanceSpikeAssessment?.let { assessment ->
                    if (assessment.level != de.run.app.domain.model.DistanceSpikeLevel.NO_WARNING &&
                        assessment.level != de.run.app.domain.model.DistanceSpikeLevel.NOT_ASSESSABLE
                    ) DistanceAssessmentCard(assessment.toUserMessage())
                }
                Text(run.description(), style = MaterialTheme.typography.bodySmall)
                WorkoutFacts(run)
                Text(run.prescription.rationale, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "RPE-Skala: 1 = sehr leicht, 10 = maximal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                RunTimeline(run)
            }
            StartOnGarminAction(showStartGuide, onStartGarminLive) { showStartGuide = true }
        }
    }
}

@Composable
private fun DistanceAssessmentCard(message: String) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Text(
            message,
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

private fun de.run.app.domain.model.DistanceSpikeAssessment.toUserMessage(): String {
    val baselineKm = referenceDistanceMeters?.div(1_000.0)
        ?.let { "%.1f km".format(java.util.Locale.GERMANY, it) } ?: "unbekannt"
    val increase = when {
        increaseMinimumPercent == null || increaseMaximumPercent == null -> null
        increaseMinimumPercent == increaseMaximumPercent -> "$increaseMinimumPercent %"
        else -> "$increaseMinimumPercent % bis $increaseMaximumPercent %"
    }
    val severity = when (level) {
        de.run.app.domain.model.DistanceSpikeLevel.NOTICE -> "Auffälliger Distanzsprung"
        de.run.app.domain.model.DistanceSpikeLevel.WARNING -> "Deutlicher Distanzsprung"
        de.run.app.domain.model.DistanceSpikeLevel.STRONG_WARNING -> "Sehr großer Distanzsprung"
        else -> "Distanzvergleich"
    }
    val distance = if (isEstimate) {
        val minKm = (estimatedDistanceMinimumMeters ?: 0.0) / 1_000.0
        val maxKm = (estimatedDistanceMaximumMeters ?: 0.0) / 1_000.0
        "Die persönliche Pace ergibt für die Laufabschnitte geschätzt ${"%.1f".format(java.util.Locale.GERMANY, minKm)}–${"%.1f".format(java.util.Locale.GERMANY, maxKm)} km; Gehabschnitte sind nicht eingerechnet. "
    } else ""
    return "$severity: Dein längster gemessener Lauf der vorherigen 30 Tage war $baselineKm. " +
        distance + "Die geschätzte Abweichung reicht von ${increase ?: "nicht zuverlässig bestimmbar"}. " +
        (if (isEstimate) "Für die Warnstufe zählt vorsichtig das obere Ende der Schätzung. " else "") +
        "Das ist ein Hinweis, keine Trainingssperre."
}

@Composable
private fun DetailsHeader(onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(48.dp)) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart).semantics { contentDescription = "Zurück" }
        ) { Text("‹", style = MaterialTheme.typography.headlineLarge) }
        Text(
            "Trainingsdetails",
            Modifier.align(Alignment.Center),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun WorkoutSummary(run: PlannedRun) {
    val icon = if (run.title.contains("langer", ignoreCase = true)) {
        R.drawable.run_training_longrun
    } else {
        R.drawable.run_training_relaxed
    }
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.09f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(painterResource(icon), contentDescription = null, Modifier.size(44.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(run.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${if (run.optional) "Optional · " else ""}ca. ${run.totalMinutes} Min.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WorkoutFacts(run: PlannedRun) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WorkoutFact("Dauer", "${run.totalMinutes} Min.", Modifier.weight(1f))
        WorkoutFact("Pace", run.prescription.paceRange?.let {
            "${formatPace(it.fasterSecondsPerKm)}–${formatPace(it.slowerSecondsPerKm)} min/km"
        } ?: "Noch nicht belastbar", Modifier.weight(1f))
        WorkoutFact("Puls", run.prescription.heartRateRangeBpm?.let { "${it.first}–${it.last} bpm" } ?: "Keine persönliche Zone", Modifier.weight(1f))
    }
}

@Composable
private fun WorkoutFact(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun RunTimeline(run: PlannedRun) {
    Text("Ablauf", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        run.steps.forEachIndexed { index, step ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 7.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(
                        Modifier.padding(top = 3.dp).size(10.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary)
                    )
                    if (index < run.steps.lastIndex) {
                        Spacer(Modifier.padding(top = 4.dp).size(width = 2.dp, height = 34.dp).background(MaterialTheme.colorScheme.tertiary.copy(alpha = .45f)))
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(step.name, style = MaterialTheme.typography.bodyMedium)
                    val pace = step.paceTarget(run.prescription)?.let {
                        "Pace ${formatPace(it.fasterSecondsPerKm)}–${formatPace(it.slowerSecondsPerKm)} min/km"
                    }
                    val effort = if (step.effortMinimum != null && step.effortMaximum != null) {
                        "RPE ${step.effortMinimum}–${step.effortMaximum} von 10"
                    } else null
                    listOfNotNull(step.effortCue, effort, pace).takeIf { it.isNotEmpty() }?.let { details ->
                        Text(
                            details.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text("${step.minutes} Min.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StartOnGarminAction(
    showGuide: Boolean,
    onStartGarminLive: () -> Unit,
    onGuidePressed: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(
                    painterResource(R.drawable.run_garmin_devices_watch),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Text("Starte die Aufzeichnung auf deiner Garmin-Uhr. LAP begleitet den Lauf hier.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Button(
            onClick = onStartGarminLive,
            modifier = Modifier.fillMaxWidth().height(52.dp).semantics {
                contentDescription = "Live-Begleitung mit Garmin-Uhr starten"
            },
            shape = CircleShape
        ) { Text("Live-Begleitung starten") }
        androidx.compose.material3.TextButton(onClick = onGuidePressed) { Text("So startest du den Lauf auf der Uhr") }
        if (showGuide) {
            Text(
                "LAP startet keine Aufzeichnung auf der Uhr. Wähle dort Aktivitäten → Laufen und öffne LAP Live als Datenseite.",
                Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MissingTrainingDetails(onOpenPlan: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Diese Trainingseinheit ist nicht mehr verfügbar.", style = MaterialTheme.typography.titleMedium)
        Button(onClick = onOpenPlan, modifier = Modifier.padding(top = 12.dp)) { Text("Zum Wochenplan") }
    }
}

private fun PlannedRun.description(): String =
    "${prescription.effortCue} Gehpausen sind jederzeit möglich."

private fun formatPace(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)
