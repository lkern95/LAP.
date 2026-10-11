package de.run.app.presentation.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.run.app.domain.model.PlanStatus
import de.run.app.domain.model.TrainingProfile
import de.run.app.domain.model.PlannedRun
import de.run.app.domain.model.RaceGoal
import de.run.app.domain.model.AvailabilityMode
import de.run.app.domain.model.TrainingGoal
import de.run.app.domain.model.TrainingGoalStatus
import de.run.app.domain.model.TrainingPlanMode
import de.run.app.presentation.history.WorkoutHistoryViewModel
import de.run.app.presentation.settings.GarminWatch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Composable
fun PlanScreen(
    viewModel: PlanViewModel,
    workoutHistoryViewModel: WorkoutHistoryViewModel,
    permissionResult: Set<String>?,
    onOpenStrength: () -> Unit,
    onOpenTrainingDetails: (LocalDate, PlannedRun) -> Unit,
    onConnectGarmin: () -> Unit,
    onSetupHealthSync: () -> Unit,
    onOnboardingComplete: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val history by workoutHistoryViewModel.state.collectAsState()
    val directGarminSupported = de.run.app.domain.GarminConnectHealthSharing.isSupported(android.os.Build.VERSION.SDK_INT)
    val garminAccessReady = if (directGarminSupported) {
        !history.isLoading && history.accessMessage == null && !history.isUnavailable && !history.hasError
    } else {
        history.workouts.any { it.sourceName == "Health Sync" }
    }
    val isFirstSetup = state.profile == null
    when {
        state.isLoading -> LoadingPlan()
        state.hasLoadError -> Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (state.hasGoalHistoryError) {
                    "Deine gespeicherten Ziele konnten nicht sicher gelesen werden. Nichts wurde überschrieben. Bitte versuche es erneut."
                } else "Dein Trainingsplan konnte gerade nicht geladen werden."
            )
            Button(onClick = viewModel::retryLoading) { Text("Noch einmal versuchen") }
        }
        state.isEditingGoal -> TrainingGoalEditor(
            existing = state.goal.takeUnless { state.isCreatingGoal },
            isSaving = state.isSaving,
            saveFailed = state.saveFailed,
            onSave = viewModel::saveGoal,
            onCancel = viewModel::cancelGoalEdit
        )
        state.profile == null || state.isEditing -> TrainingOnboardingScreen(
            initial = state.profile,
            initialGoal = state.goal,
            initialStep = state.editingStep,
            editMode = !isFirstSetup,
            saveFailed = state.saveFailed,
            isSaving = state.isSaving,
            onConnectGarmin = onConnectGarmin,
            onSetupHealthSync = onSetupHealthSync,
            garminLoading = history.isLoading,
            garminConnected = garminAccessReady,
            garminRequestResultArrived = permissionResult != null,
            onSave = { profile, goal ->
                if (isFirstSetup) viewModel.saveInitialSetup(profile, goal, onOnboardingComplete)
                else viewModel.saveProfile(profile)
            },
            onCancel = viewModel::cancelProfileEdit
        )
        else -> TrainingPlanContent(
            state,
            onEditGoal = { viewModel.editGoal() },
            onNewGoal = { viewModel.editGoal(createNew = true) },
            onPlanMode = viewModel::setPlanMode,
            onEditAvailability = viewModel::editAvailability,
            onEditStartPoint = viewModel::editStartPoint,
            onShowCompetitionQuestion = viewModel::showCompetitionQuestion,
            onShowGoalAchievementQuestion = viewModel::showGoalAchievementQuestion,
            onOpenStrength = onOpenStrength,
            onOpenTrainingDetails = onOpenTrainingDetails
        )
    }
    if (!state.isEditing && !state.isEditingGoal) GoalQuestionDialogs(state, viewModel)
}

@Composable
private fun LoadingPlan() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Text("Dein Plan wird geladen.")
    }
}

@Composable
private fun TrainingPlanContent(
    state: PlanUiState,
    onEditGoal: () -> Unit,
    onNewGoal: () -> Unit,
    onPlanMode: (TrainingPlanMode) -> Unit,
    onEditAvailability: () -> Unit,
    onEditStartPoint: () -> Unit,
    onShowCompetitionQuestion: () -> Unit,
    onShowGoalAchievementQuestion: () -> Unit,
    onOpenStrength: () -> Unit,
    onOpenTrainingDetails: (LocalDate, PlannedRun) -> Unit
) {
    val plan = state.plan ?: return
    var selectedWeekIndex by remember(plan.eventDate) { mutableIntStateOf(0) }
    var showGoalHistory by remember { mutableStateOf(false) }
    val weekIndex = selectedWeekIndex.coerceIn(0, plan.weeks.lastIndex.coerceAtLeast(0))
    val selectedWeek = plan.weeks.getOrNull(weekIndex)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(if (plan.status == PlanStatus.MAINTAINING_FORM) "Form halten" else "Wochenplan", style = MaterialTheme.typography.headlineMedium)
        TrainingAdjustmentNotice(plan.adjustment)
        state.goal?.takeIf { it.status == TrainingGoalStatus.ACTIVE }?.let { goal ->
            Text("${raceDistanceName(goal.raceDistanceMeters)} am ${goal.raceDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}")
            Text("Dein Ziel: ${goalDescription(goal)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onEditGoal) { Text("Ziel bearbeiten") }
            if (state.pendingCompetitionCandidate?.sourceId == state.dismissedCompetitionWorkoutId) {
                Button(onClick = onShowCompetitionQuestion) { Text("Wettkampfabschluss einordnen") }
            }
        }
        when (plan.status) {
            PlanStatus.EVENT_TOO_CLOSE -> {
                Text(
                    if (state.goal?.raceDate?.isBefore(LocalDate.now()) == true) {
                        "Der Zieltermin liegt zurück. Das bedeutet nicht automatisch, dass du am Wettkampf teilgenommen hast."
                    } else "Bis zum Wettkampf bleibt wenig Vorbereitungszeit. LAP plant keine ausgefallenen Einheiten nach."
                )
                Button(onClick = onEditGoal) { Text("Ziel bearbeiten") }
                Button(onClick = onNewGoal) { Text("Neues Ziel") }
                Button(onClick = { onPlanMode(TrainingPlanMode.MAINTAIN_FORM) }) { Text("Form halten") }
                Button(onClick = { onPlanMode(TrainingPlanMode.PAUSED) }) { Text("Pause") }
            }
            PlanStatus.GOAL_COMPLETED -> {
                Text("Der Wettkampfabschluss bleibt getrennt davon, ob du dein persönliches Ziel erreicht hast.")
                when (state.goal?.goalAchieved) {
                    true -> Text("Dein persönliches Ziel hast du erreicht.")
                    false -> Text("Dein persönliches Ziel hast du diesmal nicht erreicht.")
                    null -> Text("Dein persönlicher Zielerfolg ist noch offen.")
                }
                if (state.goal?.goalAchieved == null) {
                    Button(onClick = onShowGoalAchievementQuestion) { Text("Zielerreichung festhalten") }
                }
                Button(onClick = { onPlanMode(TrainingPlanMode.MAINTAIN_FORM) }) { Text("Form halten") }
                Button(onClick = onNewGoal) { Text("Neues Ziel") }
                Button(onClick = { onPlanMode(TrainingPlanMode.PAUSED) }) { Text("Pause") }
            }
            PlanStatus.GOAL_NEEDED -> {
                Text("Lege ein neues Ziel fest oder trainiere ohne Wettkampftermin weiter.")
                Button(onClick = onNewGoal) { Text("Neues Ziel") }
                Button(onClick = { onPlanMode(TrainingPlanMode.MAINTAIN_FORM) }) { Text("Form halten") }
                Button(onClick = { onPlanMode(TrainingPlanMode.PAUSED) }) { Text("Pause") }
            }
            PlanStatus.PAUSED -> {
                Text("Dein Training ist pausiert. Deine bisherigen Läufe und Ziele bleiben erhalten.")
                Button(onClick = { onPlanMode(if (state.goal?.status == TrainingGoalStatus.ACTIVE) TrainingPlanMode.RACE else TrainingPlanMode.MAINTAIN_FORM) }) {
                    Text(if (state.goal?.status == TrainingGoalStatus.ACTIVE) "Zieltraining fortsetzen" else "Form halten")
                }
                if (state.goal?.status == TrainingGoalStatus.ACTIVE) Button(onClick = onEditGoal) { Text("Ziel bearbeiten") }
                Button(onClick = onNewGoal) { Text("Neues Ziel") }
            }
            PlanStatus.READY, PlanStatus.MAINTAINING_FORM -> if (plan.weeks.isNotEmpty()) {
                selectedWeek?.let { currentWeek ->
                    WeekSelector(
                        weekNumber = currentWeek.number,
                        startsOn = currentWeek.startsOn,
                        totalWeeks = plan.weeks.size,
                        canGoBack = weekIndex > 0,
                        canGoForward = weekIndex < plan.weeks.lastIndex,
                        onPrevious = { selectedWeekIndex = (weekIndex - 1).coerceAtLeast(0) },
                        onNext = { selectedWeekIndex = (weekIndex + 1).coerceAtMost(plan.weeks.lastIndex) }
                    )
                    if (currentWeek.isRecoveryWeek) {
                        Text("Erholungswoche: Der lange Lauf ist bewusst kürzer.")
                    }
                    PlanWeekSchedule(
                        currentWeek,
                        state.profile?.availabilityMode ?: AvailabilityMode.RECURRING,
                        onOpenStrength
                    ) { run ->
                        onOpenTrainingDetails(currentWeek.startsOn, run)
                    }
                    Text("Die Tage sind ein Vorschlag und lassen sich an deinen Alltag anpassen. Zwischen den Läufen bleibt mindestens ein Ruhetag.")
                }
            } else {
                Text("Für diesen Wettkampftag ist keine weitere Trainingswoche geplant.")
            }
        }
        if (state.goals.any { it.status != TrainingGoalStatus.ACTIVE }) {
            Text(
                if (showGoalHistory) "Zielverlauf ausblenden" else "Frühere Ziele ansehen",
                Modifier.clickable { showGoalHistory = !showGoalHistory }.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary
            )
            if (showGoalHistory) state.goals.filter { it.status != TrainingGoalStatus.ACTIVE }.forEach { oldGoal ->
                Text("${raceDistanceName(oldGoal.raceDistanceMeters)} · ${oldGoal.raceDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))} · ${goalStatusLabel(oldGoal)}")
            }
        }
        Button(onClick = onOpenStrength) { Text("Kraftübungen ansehen") }
        Button(onClick = onEditAvailability) { Text("Verfügbarkeit ändern") }
        Button(onClick = onEditStartPoint) { Text("Startpunkt ändern") }
    }
}

@Composable
private fun GoalQuestionDialogs(state: PlanUiState, viewModel: PlanViewModel) {
    val candidate = state.pendingCompetitionCandidate?.takeUnless {
        it.sourceId == state.dismissedCompetitionWorkoutId
    }
    val unresolvedGoal = state.goals.firstOrNull {
        it.status == TrainingGoalStatus.COMPLETED && it.competitionCompleted == true && it.goalAchieved == null
    }
    val visibleUnresolvedGoal = unresolvedGoal?.takeUnless { it.id == state.dismissedGoalAchievementId }
    when {
        candidate != null -> AlertDialog(
            onDismissRequest = viewModel::dismissCompetitionQuestion,
            title = { Text("War das dein Zielwettkampf?") },
            text = { Text("LAP hat am geplanten Tag einen Lauf mit passender Strecke gefunden.") },
            confirmButton = { Button(onClick = { viewModel.answerCompetitionQuestion(true) }) { Text("Ja, das war er") } },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.answerCompetitionQuestion(false) }) { Text("Nein") }
                    Button(onClick = viewModel::dismissCompetitionQuestion) { Text("Später") }
                }
            }
        )
        visibleUnresolvedGoal != null -> AlertDialog(
            onDismissRequest = viewModel::dismissGoalAchievementQuestion,
            title = { Text("Hast du dein persönliches Ziel erreicht?") },
            text = { Text("Der Wettkampf wurde als absolviert erfasst. Der Erfolg deines persönlichen Ziels wird getrennt gespeichert.") },
            confirmButton = { Button(onClick = { viewModel.saveGoalAchievement(true) }) { Text("Ja") } },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.saveGoalAchievement(false) }) { Text("Nein") }
                    Button(onClick = viewModel::dismissGoalAchievementQuestion) { Text("Später") }
                }
            }
        )
    }
}

private fun goalDescription(goal: TrainingGoal): String = when (goal.raceGoal) {
    RaceGoal.ARRIVE -> "Ankommen"
    RaceGoal.RUN_THROUGH -> "Durchlaufen"
    RaceGoal.TARGET_TIME -> goal.targetTimeMinutes?.let(::formatTargetTime) ?: "Bestimmte Zeit"
}

private fun goalStatusLabel(goal: TrainingGoal): String = when (goal.status) {
    TrainingGoalStatus.ACTIVE -> "Aktuell"
    TrainingGoalStatus.REPLACED -> "Ersetzt"
    TrainingGoalStatus.COMPLETED -> when (goal.goalAchieved) {
        true -> "Abgeschlossen · Ziel erreicht"
        false -> "Abgeschlossen · Ziel nicht erreicht"
        null -> "Abgeschlossen · Zielerfolg offen"
    }
}

private fun formatTargetTime(minutes: Int): String =
    "%02d:%02d h".format(minutes / 60, minutes % 60)

@Composable
private fun WeekSelector(
    weekNumber: Int,
    startsOn: LocalDate,
    totalWeeks: Int,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onPrevious,
            enabled = canGoBack,
            modifier = Modifier.semantics { contentDescription = "Vorherige Woche" }
        ) { Text("‹", style = MaterialTheme.typography.headlineMedium) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(weekRange(startsOn), style = MaterialTheme.typography.titleMedium)
            Text("Woche $weekNumber von $totalWeeks", style = MaterialTheme.typography.bodyMedium)
        }
        IconButton(
            onClick = onNext,
            enabled = canGoForward,
            modifier = Modifier.semantics { contentDescription = "Nächste Woche" }
        ) { Text("›", style = MaterialTheme.typography.headlineMedium) }
    }
}

private fun weekRange(start: LocalDate): String {
    val end = start.plusDays(6)
    val locale = java.util.Locale.GERMANY
    return if (start.month == end.month) {
        "${start.dayOfMonth}. – ${end.dayOfMonth}. ${end.format(DateTimeFormatter.ofPattern("MMMM", locale))}"
    } else {
        "${start.format(DateTimeFormatter.ofPattern("d. MMM", locale))} – ${end.format(DateTimeFormatter.ofPattern("d. MMM", locale))}"
    }
}
