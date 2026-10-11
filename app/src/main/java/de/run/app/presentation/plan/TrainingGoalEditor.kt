package de.run.app.presentation.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.run.app.domain.model.RaceGoal
import de.run.app.domain.model.TrainingGoal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.UUID

@Composable
internal fun TrainingGoalEditor(
    existing: TrainingGoal?,
    isSaving: Boolean,
    saveFailed: Boolean,
    onSave: (TrainingGoal) -> Unit,
    onCancel: () -> Unit
) {
    var distance by remember(existing?.id) { mutableStateOf(existing?.raceDistanceMeters?.let { "%.1f".format(Locale.GERMANY, it / 1000.0) } ?: "21,1") }
    var eventDate by remember(existing?.id) { mutableStateOf(existing?.raceDate?.format(DATE_FORMAT) ?: LocalDate.now().plusMonths(5).format(DATE_FORMAT)) }
    var target by remember(existing?.id) { mutableStateOf(existing?.raceGoal?.name ?: RaceGoal.ARRIVE.name) }
    var targetMinutes by remember(existing?.id) { mutableStateOf(existing?.targetTimeMinutes?.toString() ?: "120") }
    val parsedDate = runCatching { LocalDate.parse(eventDate, DATE_FORMAT) }.getOrNull()
    val distanceMeters = distance.replace(',', '.').toDoubleOrNull()
        ?.takeIf { it in 1.0..100.0 }?.let { (it * 1000).toInt() }
    val targetTime = targetMinutes.toIntOrNull()?.takeIf { it in 60..360 }
    val isTooClose = parsedDate?.isBefore(LocalDate.now().plusWeeks(16)) == true

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(if (existing == null) "Neues Ziel" else "Ziel bearbeiten", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Nur dein Trainingsziel wird geändert. Deine Läufe und Garmin-Verbindung bleiben erhalten.")
        OutlinedTextField(
            value = distance,
            onValueChange = { distance = it.filter { char -> char.isDigit() || char == ',' || char == '.' }.take(6) },
            label = { Text("Wettkampfstrecke in km") },
            supportingText = { Text("Zum Beispiel 5, 10, 21,1 oder 42,2 km") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = eventDate,
            onValueChange = { eventDate = it.filter { char -> char.isDigit() || char == '.' }.take(10) },
            label = { Text("Wettkampftag · TT.MM.JJJJ") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Text("Was möchtest du erreichen?", style = MaterialTheme.typography.titleMedium)
        GoalChoice("Ankommen", RaceGoal.ARRIVE.name, target) { target = it }
        GoalChoice("Durchlaufen", RaceGoal.RUN_THROUGH.name, target) { target = it }
        GoalChoice("Bestimmte Zeit", RaceGoal.TARGET_TIME.name, target) { target = it }
        if (target == RaceGoal.TARGET_TIME.name) {
            OutlinedTextField(
                value = targetMinutes,
                onValueChange = { targetMinutes = it.filter(Char::isDigit).take(3) },
                label = { Text("Zielzeit in Minuten") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        if (isTooClose) {
            Text(
                "Bis zu diesem Termin bleibt wenig Vorbereitungszeit. LAP plant keine ausgefallenen Einheiten nach.",
                color = MaterialTheme.colorScheme.error
            )
        }
        if (saveFailed) Text(
            "Deine Änderung konnte nicht vollständig gespeichert werden. Deine Läufe bleiben erhalten. Versuche es noch einmal.",
            color = MaterialTheme.colorScheme.error
        )
        Button(
            onClick = {
                val raceGoal = RaceGoal.entries.firstOrNull { it.name == target } ?: RaceGoal.ARRIVE
                onSave(
                    TrainingGoal(
                        id = existing?.id ?: UUID.randomUUID().toString(),
                        raceDate = requireNotNull(parsedDate),
                        raceDistanceMeters = requireNotNull(distanceMeters),
                        raceGoal = raceGoal,
                        targetTimeMinutes = if (raceGoal == RaceGoal.TARGET_TIME) targetTime else null
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSaving && parsedDate?.isAfter(LocalDate.now()) == true && distanceMeters != null &&
                (target != RaceGoal.TARGET_TIME.name || targetTime != null)
        ) { Text(if (isSaving) "Wird gespeichert …" else "Ziel speichern") }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth(), enabled = !isSaving) { Text("Abbrechen") }
    }
}

@Composable
private fun GoalChoice(label: String, value: String, selectedValue: String, onSelect: (String) -> Unit) {
    val selected = selectedValue == value
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onSelect(value) }.semantics {
            this.selected = selected
            role = Role.RadioButton
        },
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = { onSelect(value) })
            Text(label, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)
