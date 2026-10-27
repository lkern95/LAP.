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
import de.run.app.domain.model.PlanAdjustment

@Composable
fun TrainingAdjustmentNotice(adjustment: PlanAdjustment, explanation: String? = null) {
    val message = when (adjustment) {
        PlanAdjustment.REPEAT_AFTER_HIGH_EFFORT ->
            "Mehrere ähnlich lange Läufe waren anstrengend. LAP behält den Aufbau zunächst bei."
        PlanAdjustment.REVIEW_REPORTED_SYMPTOMS ->
            "Du hast Beschwerden beim Laufen gemeldet. LAP setzt den Belastungsaufbau aus. Eine spätere Rückmeldung kann den Hinweis aktualisieren."
        PlanAdjustment.RECOVER_AFTER_HIGH_LOAD ->
            "Dein Trainingsplan reagiert auf eine deutlich höhere Belastung. Die nächste Laufeinheit ist verkürzt und sehr leicht."
        PlanAdjustment.HOLD_AFTER_SLOWING ->
            "Dein Tempo wurde bei ähnlichem Umfang und ähnlicher Anstrengung langsamer. LAP hält den Aufbau zurück und plant eine leichtere Einheit."
        PlanAdjustment.REPEAT_WEEK -> "LAP wiederholt den aktuellen Aufbau, statt ihn zu steigern."
        PlanAdjustment.RETURN_GENTLY -> "Nach einer Laufpause setzt LAP den Aufbau vorsichtig fort."
        PlanAdjustment.NO_RUN_DATA -> "Ohne gespeicherte Laufhistorie baut LAP vorsichtig auf."
        PlanAdjustment.CONTINUE -> return
    }
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Warum dein Plan angepasst ist", style = MaterialTheme.typography.titleSmall)
            Text(explanation ?: message, style = MaterialTheme.typography.bodySmall)
        }
    }
}
