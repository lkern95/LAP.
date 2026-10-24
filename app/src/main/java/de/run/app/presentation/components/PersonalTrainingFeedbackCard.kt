package de.run.app.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
    var expanded by rememberSaveable { mutableStateOf(false) }
    val statusLabel = when (feedback.status) {
        WorkoutPlanStatus.WELL_MATCHED -> "Dauer passend · Intensität nur mit RPE beurteilbar"
        WorkoutPlanStatus.SOME_DEVIATION -> "Vorgabe teilweise verfehlt"
        WorkoutPlanStatus.HIGHER_LOAD -> "Belastung höher als geplant"
        WorkoutPlanStatus.EXTRA_RUN -> feedback.qualityHeadline.ifBlank { "Belastung nicht beurteilbar" }
        WorkoutPlanStatus.NOT_ASSESSABLE -> "Noch nicht bewertbar"
    }
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .68f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("War das Training gut?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(statusLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(feedback.shortSummary, style = MaterialTheme.typography.bodyLarge)
            if (showPlanDetails) PlannedWorkoutSummary(feedback)
            Text("Dein Wochenplan", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            feedback.weeklyPlanProgress?.let { progress ->
                val completed = progress.completedRuns.coerceAtMost(progress.plannedRuns)
                val remaining = (progress.plannedRuns - completed).coerceAtLeast(0)
                val remainingText = if (remaining == 1) " · 1 noch offen" else if (remaining > 1) " · $remaining noch offen" else ""
                Text("Diese Woche: $completed von ${progress.plannedRuns} Läufen erledigt$remainingText.", style = MaterialTheme.typography.bodyMedium)
            }
            if (showNextAction) {
                val nextAction = feedback.nextAction.removePrefix("Als Nächstes: ").trim()
                val recommendation = feedback.planChangeSummary ?: nextAction
                Text("Als Nächstes: $recommendation", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            ExpandableSection("Warum diese Bewertung?", expanded, { expanded = !expanded }) {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    feedback.reasons.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    feedback.weeklyComparison?.let { comparison ->
                        Text(
                            "Zwei vollständige Wochen: ${comparison.previous.runCount} → ${comparison.current.runCount} Läufe.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        comparison.distanceChangePercent?.let { change ->
                            Text("Umfang: ${change}% Veränderung bei vollständigen Streckenwerten.", style = MaterialTheme.typography.bodySmall)
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
