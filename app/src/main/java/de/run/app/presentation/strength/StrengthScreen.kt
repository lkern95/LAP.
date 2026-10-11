package de.run.app.presentation.strength

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.run.app.domain.StrengthExerciseCatalog
import de.run.app.R
import de.run.app.presentation.components.RunIllustration

@Composable
fun StrengthScreen(viewModel: StrengthViewModel) {
    val state by viewModel.state.collectAsState()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Kraft für deinen Lauf", style = MaterialTheme.typography.headlineMedium)
        Text("Ein bis zwei kurze Einheiten pro Woche. Ruhig und kontrolliert üben.")
        if (state.isLoading) {
            CircularProgressIndicator()
            Text("Dein Übungsverlauf wird geladen.")
        } else if (state.loadFailed) {
            Text("Dein Übungsverlauf konnte gerade nicht geladen werden.")
            Button(onClick = viewModel::retryLoading) { Text("Noch einmal versuchen") }
        } else {
            Text("Dein Kraftverlauf der letzten 4 Wochen.")
            StrengthExerciseCatalog.exercises.forEach { exercise ->
                val completed = exercise.id in state.completedToday
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        RunIllustration(R.drawable.run_training_strength, exercise.focus, size = 56.dp)
                        Text(exercise.name, style = MaterialTheme.typography.titleLarge)
                        Text(exercise.focus, style = MaterialTheme.typography.labelLarge)
                        Text("${state.completedInLastFourWeeks[exercise.id] ?: 0}× erledigt")
                        Text(exercise.instruction)
                        Text(exercise.dosage, style = MaterialTheme.typography.bodyMedium)
                        Button(
                            onClick = { viewModel.complete(exercise.id) },
                            enabled = !completed
                        ) { Text(if (completed) "Heute erledigt" else "Als erledigt markieren") }
                    }
                }
            }
        }
        if (state.saveFailed) {
            Text("Dein Fortschritt konnte nicht gespeichert werden. Bitte versuche es erneut.")
        }
    }
}
