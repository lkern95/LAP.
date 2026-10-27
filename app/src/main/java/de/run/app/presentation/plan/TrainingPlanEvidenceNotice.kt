package de.run.app.presentation.plan

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.run.app.domain.TrainingPlanEvidence
import de.run.app.domain.TrainingPlanEvidenceStatus

@Composable
fun TrainingPlanEvidenceNotice(evidence: TrainingPlanEvidence) {
    if (!evidence.requiresStarterPlan) return

    val detail = when (evidence.status) {
        TrainingPlanEvidenceStatus.CHECK_REQUIRED ->
            "LAP prüft deine Laufdaten. Bis dahin ist dieser Plan ein vorläufiger Einstieg."
        TrainingPlanEvidenceStatus.ACCESS_REQUIRED ->
            "LAP kann deine Laufhistorie gerade nicht lesen. Das heißt nicht, dass du nicht gelaufen bist. " +
                localRunsNote(evidence.locallySavedRunCount)
        TrainingPlanEvidenceStatus.SOURCE_UNAVAILABLE ->
            "Die Trainingsdatenquelle ist gerade nicht verfügbar. LAP kann deshalb nicht sicher einschätzen, " +
                "wie viel du bisher gelaufen bist. ${localRunsNote(evidence.locallySavedRunCount)}"
        TrainingPlanEvidenceStatus.READ_FAILED ->
            "LAP konnte die Trainingshistorie nicht auslesen. Vorhandene lokale Läufe werden berücksichtigt; " +
                "der Plan bleibt vorläufig."
        TrainingPlanEvidenceStatus.HISTORY_LIMITED ->
            "Nur ein Teil der Trainingshistorie ist verfügbar. LAP berücksichtigt die gespeicherten Läufe, " +
                "kann deinen bisherigen Umfang aber nicht vollständig beurteilen."
        TrainingPlanEvidenceStatus.NO_RUNS_FOUND ->
            "Im abgerufenen Zeitraum wurden keine aufgezeichneten Läufe gefunden. Das beweist nicht, " +
                "dass du in dieser Zeit nicht gelaufen bist."
        TrainingPlanEvidenceStatus.INSUFFICIENT_PERSONAL_BASELINE ->
            "Für persönliche Tempovorgaben fehlen noch vergleichbare Läufe."
        TrainingPlanEvidenceStatus.PERSONAL_BASELINE_AVAILABLE -> return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Einordnung deines Plans", style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun localRunsNote(count: Int): String = when (count) {
    0 -> "Es sind hier keine Läufe gespeichert."
    1 -> "Ein lokal gespeicherter Lauf wird trotzdem berücksichtigt."
    else -> "$count lokal gespeicherte Läufe werden trotzdem berücksichtigt."
}
