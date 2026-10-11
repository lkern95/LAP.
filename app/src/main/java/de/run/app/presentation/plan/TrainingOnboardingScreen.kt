package de.run.app.presentation.plan

import android.app.DatePickerDialog
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.run.app.domain.model.RaceGoal
import de.run.app.domain.GarminConnectHealthSharing
import de.run.app.domain.model.TrainingProfile
import de.run.app.domain.model.AvailabilityMode
import de.run.app.domain.model.AvailabilityStatus
import de.run.app.domain.model.TrainingDayAvailability
import de.run.app.domain.model.TrainingGoal
import de.run.app.presentation.settings.GarminWatch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.UUID

@Composable
internal fun TrainingOnboardingScreen(
    initial: TrainingProfile?,
    initialGoal: TrainingGoal?,
    initialStep: Int = 0,
    editMode: Boolean = false,
    isSaving: Boolean,
    saveFailed: Boolean,
    onSave: (TrainingProfile, TrainingGoal) -> Unit,
    onCancel: () -> Unit,
    onConnectGarmin: () -> Unit,
    onSetupHealthSync: () -> Unit = {},
    garminLoading: Boolean = false,
    garminConnected: Boolean = false,
    garminRequestResultArrived: Boolean = true,
    garminSharingSupported: Boolean = GarminConnectHealthSharing.isSupported(Build.VERSION.SDK_INT)
) {
    var step by rememberSaveable(initial, initialStep) { mutableIntStateOf(initialStep) }
    var eventDate by rememberSaveable { mutableStateOf(initialGoal?.raceDate?.format(DATE_FORMAT) ?: "21.03.2027") }
    var goal by rememberSaveable { mutableStateOf(initialGoal?.raceGoal?.name ?: RaceGoal.RUN_THROUGH.name) }
    var targetTime by rememberSaveable { mutableStateOf(initialGoal?.targetTimeMinutes?.toString() ?: "120") }
    var raceDistanceKm by rememberSaveable {
        mutableStateOf(initialGoal?.raceDistanceMeters?.let(::formatRaceDistance) ?: "21,1")
    }
    var connectionRequested by rememberSaveable { mutableStateOf(false) }
    var longestRun by rememberSaveable { mutableStateOf(initial?.longestComfortableRunMinutes?.toString() ?: "60") }
    var availabilityMode by rememberSaveable { mutableStateOf((initial?.availabilityMode ?: AvailabilityMode.VARIABLE).name) }
    var selectedDays by rememberSaveable { mutableStateOf((initial?.availableRunDays ?: DEFAULT_DAYS).joinToString(",") { it.name }) }
    var longRunDay by rememberSaveable { mutableStateOf(initial?.longRunDay?.name ?: AUTO_LONG_RUN_DAY) }
    var considerNightShiftRecovery by rememberSaveable { mutableStateOf(initial?.considerNightShiftRecovery ?: false) }
    var usualRunTimeLimit by rememberSaveable { mutableIntStateOf(initial?.usualRunTimeLimitMinutes ?: DEFAULT_USUAL_LIMIT) }
    var occasionalRunTimeLimit by rememberSaveable { mutableStateOf(initial?.occasionalRunTimeLimitMinutes?.toString()) }
    var occasionalTimeDraft by rememberSaveable {
        mutableIntStateOf(initial?.occasionalRunTimeLimitMinutes ?: maxOf(90, (initial?.usualRunTimeLimitMinutes ?: DEFAULT_USUAL_LIMIT) + 30))
    }
    var datedAvailabilityValue by rememberSaveable {
        mutableStateOf(serializeAvailability(initial?.datedAvailability.orEmpty()))
    }
    var timeLimits by rememberSaveable {
        mutableStateOf((initial?.runTimeLimitsByDay ?: DEFAULT_LIMITS).entries.joinToString(",") { "${it.key.name}:${it.value}" })
    }

    val selected = selectedDays.split(',').mapNotNull { value -> DayOfWeek.entries.firstOrNull { it.name == value } }.toSet()
    val limits = timeLimits.split(',').mapNotNull { entry ->
        val parts = entry.split(':')
        val day = parts.getOrNull(0)?.let { name -> DayOfWeek.entries.firstOrNull { it.name == name } }
        val value = parts.getOrNull(1)?.toIntOrNull()
        if (day != null && value != null) day to value else null
    }.toMap()
    val datedAvailability = parseAvailability(datedAvailabilityValue)
    val parsedDate = try { LocalDate.parse(eventDate, DATE_FORMAT) } catch (_: DateTimeParseException) { null }
    val targetMinutes = targetTime.toIntOrNull()?.takeIf { it in 60..360 }
    val distanceMeters = parseRaceDistanceMeters(raceDistanceKm)
    val baseline = longestRun.toIntOrNull()?.takeIf { it in 5..300 }

    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 14.dp)) {
        OnboardingHeader(step, editMode)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            when (step) {
                0 -> GoalStep(
                    eventDate = eventDate,
                    goal = goal,
                    raceDistanceKm = raceDistanceKm,
                    targetTime = targetTime,
                    onDate = { eventDate = it.filter { char -> char.isDigit() || char == '.' }.take(10) },
                    onGoal = { goal = it },
                    onRaceDistance = { raceDistanceKm = it.filter { char -> char.isDigit() || char == ',' || char == '.' }.take(6) },
                    onTargetTime = { targetTime = it.filter(Char::isDigit).take(3) }
                )
                1 -> GarminStep(
                    garminSharingSupported = garminSharingSupported,
                    connectionState = resolveGarminConnectionUiState(
                        requested = connectionRequested,
                        resultArrived = garminRequestResultArrived,
                        loading = garminLoading,
                        connected = garminConnected
                    ),
                    connectionRequested = connectionRequested,
                    onSetupHealthSync = onSetupHealthSync,
                    onConnect = {
                        if (!connectionRequested) {
                            connectionRequested = true
                            onConnectGarmin()
                        }
                    }
                )
                2 -> WeekStep(
                    mode = AvailabilityMode.entries.firstOrNull { it.name == availabilityMode } ?: AvailabilityMode.VARIABLE,
                    onMode = { availabilityMode = it.name },
                    selected = selected,
                    preferredLongRunDay = DayOfWeek.entries.firstOrNull { it.name == longRunDay },
                    limits = limits,
                    usualRunTimeLimit = usualRunTimeLimit,
                    occasionalRunTimeLimit = occasionalRunTimeLimit?.toIntOrNull(),
                    onUsualRunTimeLimit = { usualRunTimeLimit = it },
                    onOccasionalRunTimeLimit = { occasionalRunTimeLimit = it?.toString() },
                    occasionalTimeDraft = occasionalTimeDraft,
                    onOccasionalTimeDraft = { occasionalTimeDraft = it },
                    onToggleDay = { day ->
                        selectedDays = (if (day in selected) selected - day else selected + day)
                            .sortedBy(DayOfWeek::getValue).joinToString(",") { it.name }
                    },
                    onLongRunDay = { longRunDay = it?.name ?: AUTO_LONG_RUN_DAY },
                    onLimit = { day, minutes ->
                        timeLimits = (limits + (day to minutes)).entries.joinToString(",") { "${it.key.name}:${it.value}" }
                    }
                )
                else -> StartPointStep(longestRun, onLongestRun = { longestRun = it.filter(Char::isDigit).take(3) })
            }
        }
        if (saveFailed) Text("Deine Angaben konnten nicht gespeichert werden. Bitte versuche es noch einmal.", color = MaterialTheme.colorScheme.error)
        val valid = when (step) {
            0 -> parsedDate != null && parsedDate.isAfter(LocalDate.now()) && distanceMeters != null &&
                (goal != RaceGoal.TARGET_TIME.name || targetMinutes != null)
            1 -> true
            2 -> true
            else -> baseline != null
        }
        val continueLabel = when {
            editMode && step == 3 -> "Speichern  →"
            step == 3 -> "Plan erstellen  →"
            else -> "Weiter  →"
        }
        Button(
            onClick = {
                if (step < 3) step++ else if (parsedDate != null && baseline != null && distanceMeters != null) {
                    val chosenGoal = RaceGoal.entries.firstOrNull { it.name == goal } ?: RaceGoal.ARRIVE
                    onSave(
                        TrainingProfile(
                            longestComfortableRunMinutes = baseline,
                            optionalRun = initial?.optionalRun ?: true,
                            strengthSessionsPerWeek = initial?.strengthSessionsPerWeek ?: 1,
                            availableRunDays = selected,
                            longRunDay = DayOfWeek.entries.firstOrNull { it.name == longRunDay },
                            runTimeLimitsByDay = limits,
                            availabilityMode = AvailabilityMode.entries.firstOrNull { it.name == availabilityMode } ?: AvailabilityMode.VARIABLE,
                            datedAvailability = datedAvailability,
                            considerNightShiftRecovery = considerNightShiftRecovery,
                            usualRunTimeLimitMinutes = usualRunTimeLimit,
                            occasionalRunTimeLimitMinutes = occasionalRunTimeLimit?.toIntOrNull(),
                            planMode = initial?.planMode ?: de.run.app.domain.model.TrainingPlanMode.RACE
                        ),
                        TrainingGoal(
                            id = initialGoal?.id ?: UUID.randomUUID().toString(),
                            raceDate = parsedDate,
                            raceDistanceMeters = distanceMeters,
                            raceGoal = chosenGoal,
                            targetTimeMinutes = if (chosenGoal == RaceGoal.TARGET_TIME) targetMinutes else null
                        )
                    )
                }
            },
            enabled = valid && !isSaving,
            modifier = Modifier.fillMaxWidth().height(60.dp).semantics {
                contentDescription = when {
                    editMode && step == 3 -> "Angaben speichern"
                    step == 3 -> "Plan erstellen"
                    else -> "Einrichtung fortsetzen"
                }
            },
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(if (isSaving) "Wird gespeichert …" else continueLabel, style = MaterialTheme.typography.titleLarge)
        }
        if (editMode && step == 2 && !isSaving) {
            Text("Abbrechen", Modifier.align(Alignment.CenterHorizontally).clickable(onClick = onCancel).padding(12.dp), color = MaterialTheme.colorScheme.primary)
        } else if (step > (if (editMode) 2 else 0) && !isSaving) {
            Text("Zurück", Modifier.align(Alignment.CenterHorizontally).clickable { step-- }.padding(12.dp), color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun OnboardingHeader(step: Int, editMode: Boolean) {
    val visibleStep = if (editMode) step - 2 else step
    val totalSteps = if (editMode) 2 else 4
    val title = if (editMode) {
        if (step == 2) "Deine Woche" else "Dein Startpunkt"
    } else if (step == 0) "Dein Ziel" else listOf("Dein Ziel", "Garmin", "Deine Woche", "Startpunkt")[step]
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("${visibleStep + 1} von $totalSteps", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(totalSteps) { index ->
                Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(if (index <= visibleStep) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant))
            }
        }
    }
}

@Composable
private fun GoalStep(
    eventDate: String,
    goal: String,
    raceDistanceKm: String,
    targetTime: String,
    onDate: (String) -> Unit,
    onGoal: (String) -> Unit,
    onRaceDistance: (String) -> Unit,
    onTargetTime: (String) -> Unit
) {
    val context = LocalContext.current
    val initialDate = runCatching { LocalDate.parse(eventDate, DATE_FORMAT) }.getOrElse { LocalDate.now().plusMonths(5) }
    Text("Wofür trainierst du?", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("👟", style = MaterialTheme.typography.headlineLarge)
            Column {
                Text(parseRaceDistanceMeters(raceDistanceKm)?.let(::raceDistanceName) ?: "Wettkampf", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Deine Wettkampfstrecke", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    OutlinedTextField(
        value = raceDistanceKm,
        onValueChange = onRaceDistance,
        label = { Text("Wettkampfstrecke in km") },
        supportingText = { Text("Zum Beispiel 5, 10, 21,1 oder 42,2 km") },
        suffix = { Text("km") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    if (parseRaceDistanceMeters(raceDistanceKm) == null) {
        Text("Gib eine Strecke zwischen 1 und 100 km ein.", color = MaterialTheme.colorScheme.error)
    }
    Text("Wann möchtest du laufen?", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        eventDate,
        onDate,
        label = { Text("Wettkampftag · TT.MM.JJJJ") },
        trailingIcon = {
            IconButton(onClick = {
                DatePickerDialog(
                    context,
                    { _, year, month, day -> onDate(LocalDate.of(year, month + 1, day).format(DATE_FORMAT)) },
                    initialDate.year,
                    initialDate.monthValue - 1,
                    initialDate.dayOfMonth
                ).show()
            }, modifier = Modifier.semantics { contentDescription = "Wettkampftag wählen" }) { Text("▦") }
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Text("Was möchtest du erreichen?", style = MaterialTheme.typography.titleMedium)
    GoalOption("Ankommen", RaceGoal.ARRIVE.name, goal, onGoal)
    GoalOption("Durchlaufen", RaceGoal.RUN_THROUGH.name, goal, onGoal)
    GoalOption("Bestimmte Zeit", RaceGoal.TARGET_TIME.name, goal, onGoal)
    if (goal == RaceGoal.TARGET_TIME.name) {
        OutlinedTextField(targetTime, onTargetTime, label = { Text("Zielzeit in Minuten") }, supportingText = { Text("Du kannst die Zeit später anpassen.") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    }
}

@Composable
private fun GoalOption(label: String, value: String, selected: String, onSelect: (String) -> Unit) {
    val active = selected == value
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = .09f) else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            .clickable { onSelect(value) }.padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Text(if (active) "✓" else "○", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun GarminStep(
    garminSharingSupported: Boolean,
    connectionState: GarminConnectionUiState,
    connectionRequested: Boolean,
    onSetupHealthSync: () -> Unit,
    onConnect: () -> Unit
) {
    val loading = connectionState == GarminConnectionUiState.CONNECTING
    Text("Dein Training als Ausgangspunkt", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
    Text(
        if (garminSharingSupported) {
            "Übernimm bisherige Läufe aus Garmin Connect. LAP nutzt dafür die Gesundheitsfreigabe deines Geräts."
        } else {
            "Garmin Connect gibt Läufe auf diesem Android nicht direkt an Health Connect weiter. Du kannst Health Sync als lokale Brücke zu Health Connect nutzen oder FIT-Dateien später unter Fortschritt importieren."
        },
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))
    GarminWatch(
        loading = loading,
        connected = connectionState == GarminConnectionUiState.CONNECTED,
        failed = connectionState == GarminConnectionUiState.FAILED,
        showIndicator = connectionRequested,
        modifier = Modifier.fillMaxWidth().height(280.dp)
    )
    Text("LAP speichert deine Trainingsangaben und Läufe nur auf diesem Gerät.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Button(onClick = onConnect, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
        Text(if (loading) "Freigabe wird geöffnet …" else if (garminSharingSupported) "Garmin verbinden" else "Health Connect freigeben")
    }
    if (!garminSharingSupported) {
        TextButton(onClick = onSetupHealthSync, modifier = Modifier.fillMaxWidth()) {
            Text("Health Sync einrichten")
        }
        Text(
            "Health Sync liest Garmin Connect und gibt Läufe an Health Connect weiter. LAP liest freigegebene Läufe von dort ein. Alternativ kannst du später FIT-Dateien unter Fortschritt importieren.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Text("Du kannst die Verbindung später einrichten.", Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
}

@Composable
private fun WeekStep(
    mode: AvailabilityMode,
    onMode: (AvailabilityMode) -> Unit,
    selected: Set<DayOfWeek>,
    preferredLongRunDay: DayOfWeek?,
    limits: Map<DayOfWeek, Int>,
    usualRunTimeLimit: Int,
    occasionalRunTimeLimit: Int?,
    onUsualRunTimeLimit: (Int) -> Unit,
    onOccasionalRunTimeLimit: (Int?) -> Unit,
    occasionalTimeDraft: Int,
    onOccasionalTimeDraft: (Int) -> Unit,
    onToggleDay: (DayOfWeek) -> Unit,
    onLongRunDay: (DayOfWeek?) -> Unit,
    onLimit: (DayOfWeek, Int) -> Unit
) {
    Text("Wie passt Training in deinen Alltag?", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AvailabilityMode.entries.forEach { option ->
            val label = if (option == AvailabilityMode.RECURRING) "Meist feste Tage" else "Flexibel"
            val description = if (option == AvailabilityMode.RECURRING) {
                "Ich kann meine Trainingszeiten ungefähr planen."
            } else {
                "Meine verfügbaren Zeiten wechseln oder ergeben sich kurzfristig."
            }
            val chosen = mode == option
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                    .background(if (chosen) MaterialTheme.colorScheme.primary.copy(alpha = .12f) else MaterialTheme.colorScheme.surface)
                    .border(1.dp, if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    .clickable { onMode(option) }
                    .semantics { role = Role.RadioButton; this.selected = chosen; contentDescription = label }
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(label, color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (mode == AvailabilityMode.RECURRING) {
        Text("Ich kann ungefähr an diesen Tagen trainieren:", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        DayOfWeek.entries.forEach { day ->
            val checked = day in selected
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onToggleDay(day) }.semantics {
                    role = Role.Checkbox
                    this.selected = checked
                    contentDescription = "${DAY_LABELS.getValue(day)}: ${if (checked) "Verfügbar" else "Nicht ausgewählt"}"
                },
                colors = CardDefaults.cardColors(containerColor = if (checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("${if (checked) "✓" else "○"}  ${DAY_LABELS.getValue(day)}", style = MaterialTheme.typography.titleMedium)
                        if (checked) Text("Verfügbar", color = MaterialTheme.colorScheme.primary)
                    }
                    if (checked) {
                        DurationLimitRow(limits[day] ?: DEFAULT_LIMITS[day] ?: DEFAULT_LIMIT_MINUTES, { onLimit(day, it) })
                        Text(
                            if (day == preferredLongRunDay) "Langer Lauf: bevorzugt" else "Langen Lauf bevorzugen",
                            Modifier.clickable { onLongRunDay(if (day == preferredLongRunDay) null else day) }.padding(vertical = 6.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        Text("Ausgewählte Tage sind nur eine Vorlage. Wie viele Einheiten passen, entscheidet LAP passend zu deinem Ziel und deiner Erholung.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Text("Ich kann normalerweise einplanen:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        DurationLimitRow(usualRunTimeLimit, onUsualRunTimeLimit)
        Text("Ist gelegentlich mehr Zeit möglich?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        DurationLimitRow(occasionalRunTimeLimit ?: occasionalTimeDraft) { minutes ->
            val adjusted = maxOf(minutes, usualRunTimeLimit)
            onOccasionalTimeDraft(adjusted)
            onOccasionalRunTimeLimit(adjusted)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = occasionalRunTimeLimit == null,
                onCheckedChange = { isUnknown -> onOccasionalRunTimeLimit(if (isUnknown) null else occasionalTimeDraft) }
            )
            Text("Noch unklar", style = MaterialTheme.typography.bodyMedium)
        }
        Text("LAP wählt dein Training passend zu deiner verfügbaren Zeit und deinem bisherigen Verlauf. Zeiten sind Obergrenzen, keine Trainingsvorgaben. Längere Zeiten bestätigst du bei Bedarf erst am Trainingstag.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DurationLimitRow(minutes: Int, onMinutes: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Bis zu $minutes Min.")
        Row {
            Text("−", Modifier.clickable { onMinutes((minutes - 15).coerceAtLeast(30)) }
                .semantics { contentDescription = "Laufzeit verkürzen" }.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge)
            Text("+", Modifier.clickable { onMinutes((minutes + 15).coerceAtMost(180)) }
                .semantics { contentDescription = "Laufzeit verlängern" }.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun StartPointStep(longestRun: String, onLongestRun: (String) -> Unit) {
    Text("Dein Startpunkt", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
    Text("Ein kurzer Blick auf deinen bisherigen Laufumfang.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("Damit der Plan zu deiner Erfahrung passt, brauchen wir nur deine längste lockere Laufeinheit.", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(longestRun, onLongestRun, label = { Text("Längste lockere Laufdauer (Minuten)") }, supportingText = { Text("Zum Beispiel 60 Minuten") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    Text("Bisherige Läufe werden nach der Freigabe automatisch übernommen. Fehlende Angaben kannst du später ergänzen.", color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.uuuu")
private val DEFAULT_DAYS = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SUNDAY)
private val DEFAULT_LIMITS = mapOf(DayOfWeek.TUESDAY to 45, DayOfWeek.THURSDAY to 45, DayOfWeek.SUNDAY to 90)
private const val DEFAULT_LIMIT_MINUTES = 45
private const val DEFAULT_USUAL_LIMIT = 45
private const val AUTO_LONG_RUN_DAY = "AUTO"
private val DAY_LABELS = mapOf(
    DayOfWeek.MONDAY to "Montag", DayOfWeek.TUESDAY to "Dienstag", DayOfWeek.WEDNESDAY to "Mittwoch",
    DayOfWeek.THURSDAY to "Donnerstag", DayOfWeek.FRIDAY to "Freitag", DayOfWeek.SATURDAY to "Samstag", DayOfWeek.SUNDAY to "Sonntag"
)

private fun serializeAvailability(values: Map<LocalDate, TrainingDayAvailability>): String =
    values.entries.sortedBy { it.key }.joinToString(";") { (date, availability) ->
        "${date}=${availability.status.name}:${availability.maximumMinutes ?: 0}:${if (availability.afterNightShift) 1 else 0}"
    }

private fun parseAvailability(value: String): Map<LocalDate, TrainingDayAvailability> = value
    .split(';').mapNotNull { item ->
        val parts = item.split('=', limit = 2)
        val date = parts.getOrNull(0)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
        val fields = parts.getOrNull(1).orEmpty().split(':')
        val status = fields.getOrNull(0)?.let { saved -> AvailabilityStatus.entries.firstOrNull { it.name == saved } }
            ?.takeIf { it != AvailabilityStatus.UNKNOWN } ?: return@mapNotNull null
        val minutes = fields.getOrNull(1)?.toIntOrNull()?.takeIf { it in 30..180 }
        val afterNightShift = fields.getOrNull(2) == "1"
        date to TrainingDayAvailability(status, minutes, afterNightShift)
    }.toMap()
