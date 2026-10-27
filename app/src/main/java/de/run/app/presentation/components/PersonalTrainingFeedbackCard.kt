package de.run.app.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.run.app.domain.TrainingFeedback
import de.run.app.domain.WorkoutPlanStatus
import de.run.app.domain.model.PlannedRun

@Composable
fun PersonalTrainingFeedbackCard(
    feedback: TrainingFeedback,
    modifier: Modifier = Modifier,
    showNextAction: Boolean = true,
    showPlanDetails: Boolean = true
) {
    var cardExpanded by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val statusLabel = when (feedback.status) {
        WorkoutPlanStatus.WELL_MATCHED -> "Dauer wie geplant"
        WorkoutPlanStatus.SOME_DEVIATION -> "Vorgabe nicht ganz getroffen"
        WorkoutPlanStatus.HIGHER_LOAD -> "Mehr Belastung als geplant"
        WorkoutPlanStatus.EXTRA_RUN -> feedback.qualityHeadline.ifBlank { "Noch nicht bewertbar" }
        WorkoutPlanStatus.NOT_ASSESSABLE -> "Noch nicht bewertbar"
    }
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth()
                    .clickable { cardExpanded = !cardExpanded }
                    .semantics {
                        contentDescription = "War das Training gut? ${if (cardExpanded) "ausblenden" else "anzeigen"}"
                        role = Role.Button
                        stateDescription = if (cardExpanded) "Ausgeklappt" else "Eingeklappt"
                    },
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("War das Training gut?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(statusLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Text(if (cardExpanded) "⌄" else "›", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            AnimatedVisibility(visible = cardExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(feedback.shortSummary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    if (showPlanDetails) PlannedWorkoutSummary(feedback)
                    Text("Dein Wochenplan", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    feedback.weeklyPlanProgress?.let { progress ->
                        val completed = progress.completedRuns.coerceAtMost(progress.plannedRuns)
                        val remaining = (progress.plannedRuns - completed).coerceAtLeast(0)
                        val remainingText = if (remaining == 1) " · 1 noch offen" else if (remaining > 1) " · $remaining noch offen" else ""
                        Text("Diese Woche: $completed von ${progress.plannedRuns} Läufen erledigt$remainingText.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    if (showNextAction) {
                        val nextAction = feedback.nextAction.removePrefix("Als Nächstes: ").trim()
                        val recommendation = feedback.planChangeSummary ?: nextAction
                        Text("Als Nächstes: $recommendation", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    ExpandableSection(
                        "Warum diese Bewertung?",
                        expanded,
                        { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth(),
                        titleStyle = MaterialTheme.typography.labelLarge
                    ) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            feedback.reasons.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer) }
                            feedback.weeklyComparison?.let { comparison ->
                                Text(
                                    "Zwei vollständige Wochen: ${comparison.previous.runCount} → ${comparison.current.runCount} Läufe.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                comparison.distanceChangePercent?.let { change ->
                                    Text("Umfang: ${change}% Veränderung bei vollständigen Streckenwerten.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlannedWorkoutSummary(feedback: TrainingFeedback) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val run = feedback.plannedRun
    if (run == null) {
        if (feedback.status == WorkoutPlanStatus.EXTRA_RUN) {
            Text("Geplant: An diesem Tag war kein Lauf vorgesehen.", style = MaterialTheme.typography.bodySmall)
        }
        return
    }
    Text(
        "Geplant: ${run.title} · ${run.totalMinutes} Min. · ${plannedEffort(run)}",
        style = MaterialTheme.typography.bodySmall
    )
    ExpandableSection("Abschnitte und Vorgaben", expanded, { expanded = !expanded }) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            run.steps.forEach { step ->
                val effort = if (step.effortMinimum != null && step.effortMaximum != null) {
                    " · RPE ${step.effortMinimum}–${step.effortMaximum}"
                } else ""
                Text("${step.minutes} Min. ${step.name}$effort", style = MaterialTheme.typography.bodySmall)
            }
            val paceRange = run.prescription.paceRange
            if (paceRange == null) {
                val observed = run.prescription.observedPaceRange
                Text(
                    observed?.let { "Beobachtete Pace ${formatPace(it.fasterSecondsPerKm)}–${formatPace(it.slowerSecondsPerKm)} min/km; Intensität unbekannt, kein Coach-Ziel." }
                        ?: "Paceziel: noch keine ausreichenden Vergleichsläufe.",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Text(
                    "Persönliche Pace: ${formatPace(paceRange.fasterSecondsPerKm)}–${formatPace(paceRange.slowerSecondsPerKm)} min/km.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            val heartRate = run.prescription.heartRateRangeBpm
            Text(
                heartRate?.let { "Pulsziel: ${it.first}–${it.last} bpm." } ?: "Kein persönliches Pulsziel festgelegt.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun plannedEffort(run: PlannedRun): String =
    "locker · RPE ${run.prescription.effortMinimum}–${run.prescription.effortMaximum}"

private fun formatPace(seconds: Int): String =
    "%d:%02d".format(java.util.Locale.GERMANY, seconds / 60, seconds % 60)
