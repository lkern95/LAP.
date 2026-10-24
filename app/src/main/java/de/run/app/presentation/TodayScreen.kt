package de.run.app.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.run.app.R
import de.run.app.domain.model.PlanStatus
import de.run.app.domain.model.AvailabilityMode
import de.run.app.domain.model.PlannedRun
import de.run.app.domain.model.TrainingPlan
import de.run.app.domain.model.TrainingWeek
import de.run.app.domain.model.Workout
import de.run.app.domain.model.ScheduledRun
import de.run.app.presentation.history.WorkoutHistoryUiState
import de.run.app.presentation.history.WorkoutHistoryViewModel
import de.run.app.presentation.components.ExpandableSection
import de.run.app.presentation.plan.PlanViewModel
import de.run.app.presentation.plan.raceDistanceName
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@Composable
fun TodayScreen(
    planViewModel: PlanViewModel,
    workoutHistoryViewModel: WorkoutHistoryViewModel,
    onRequestWorkoutAccess: () -> Unit,
    onOpenPlan: () -> Unit,
    onOpenTrainingDetailsForTime: (LocalDate, PlannedRun, Int) -> Unit,
    onOpenReadiness: () -> Unit,
    onOpenAvailability: () -> Unit,
) {
    val planState by planViewModel.state.collectAsState()
    val historyState by workoutHistoryViewModel.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var todayEntry by rememberSaveable { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) todayEntry += 1
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TodayHeader(onOpenPlan)
        when {
            planState.isLoading -> LoadingToday()
            planState.hasLoadError -> PlanErrorToday(planViewModel::retryLoading)
            planState.profile == null || planState.isEditing -> SetupToday(onOpenPlan)
            else -> planState.plan?.let { plan ->
                val week = plan.weeks.firstOrNull()
                when {
                    plan.status == PlanStatus.EVENT_TOO_CLOSE -> EventTooCloseToday(plan, onOpenPlan)
                    plan.status == PlanStatus.PAUSED -> PlanChoiceToday("Dein Training ist pausiert", "Wähle im Plan, wann und wie du weitermachen möchtest.", onOpenPlan)
                    plan.status == PlanStatus.GOAL_COMPLETED -> PlanChoiceToday("Zielwettkampf abgeschlossen", "Dein nächster Schritt: Form halten, ein neues Ziel wählen oder pausieren.", onOpenPlan)
                    plan.status == PlanStatus.GOAL_NEEDED || week == null -> PlanChoiceToday("Lege dein nächstes Ziel fest", "Deine Laufhistorie bleibt erhalten.", onOpenPlan)
                    else -> {
                    requireNotNull(week)
                    val completedRuns = planState.completedPlannedRunCount
                        .coerceAtMost(week.requiredRunCount)
                    val profile = requireNotNull(planState.profile)
                    val nextScheduledRun = if (profile.availabilityMode == AvailabilityMode.RECURRING) {
                        planState.nextScheduledRun
                    } else null
                    when {
                        profile.availabilityMode == AvailabilityMode.VARIABLE -> TodayWorkoutCard(
                            week,
                            onOpenTrainingDetailsForTime,
                            completedRuns,
                            profile.usualRunTimeLimitMinutes,
                            profile.occasionalRunTimeLimitMinutes ?: 180,
                            title = "Dein nächstes Training"
                        )
                        profile.availabilityMode == AvailabilityMode.RECURRING && profile.availableRunDays.isEmpty() ->
                            AvailabilityPromptCard(onOpenAvailability)
                        else -> TodayWorkoutCard(
                            week,
                            onOpenTrainingDetailsForTime,
                            completedRuns,
                            profile.runTimeLimitsByDay[LocalDate.now().dayOfWeek]
                                ?: profile.usualRunTimeLimitMinutes,
                            180,
                            nextScheduledRun = nextScheduledRun,
                            title = "Dein nächstes Training"
                        )
                    }
                    planState.halfMarathonForecast?.let { forecast ->
                        planState.goal?.let {
                            HalfMarathonForecastCard(
                                forecast = forecast
                            )
                        }
                    }
                    ReadinessEntryCard(onOpenReadiness)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        WeeklyRunMetric(
                            week = week,
                            history = historyState,
                            completedRuns = completedRuns,
                            animationKey = todayEntry,
                            modifier = Modifier.weight(1f)
                        )
                        PlanWeekMetric(
                            week = week,
                            plan = plan,
                            raceDistanceMeters = planState.goal?.raceDistanceMeters,
                            animationKey = todayEntry,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    WeeklyActivityCard(
                        week = week,
                        workouts = historyState.workouts,
                        history = historyState,
                        completedRuns = completedRuns,
                        onRequestWorkoutAccess = onRequestWorkoutAccess,
                        onRetry = workoutHistoryViewModel::refresh
                    )
                    }
                }
            } ?: SetupToday(onOpenPlan)
        }
    }
}

@Composable
internal fun HalfMarathonForecastCard(
    forecast: de.run.app.domain.HalfMarathonForecast
) {
    var explanationExpanded by rememberSaveable { mutableStateOf(false) }
    val estimated = forecast.estimatedMinutes?.let {
        val label = when (forecast.confidence) {
            de.run.app.domain.ForecastConfidence.TRAINING_ORIENTATION -> "Grobe Trainingsorientierung"
            de.run.app.domain.ForecastConfidence.ROUGH -> "Grobe Orientierung"
            else -> "Zeitprognose"
        }
        "$label: ${formatHalfMarathonTime(it.first)}–${formatHalfMarathonTime(it.last)} Std."
    } ?: "Für eine Zeitprognose fehlen aktuelle, aussagekräftige Läufe."
    val development = if (forecast.recentRunCount == 0) "Noch keine Läufe in vier Wochen."
    else "${forecast.recentRunCount} Läufe in vier Wochen · längster Lauf ${"%.1f".format(Locale.GERMANY, forecast.recentLongestRunKilometers)} km"
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Bin ich auf Kurs?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(forecast.outlookSummary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(development, style = MaterialTheme.typography.bodySmall)
            Text(estimated, style = MaterialTheme.typography.bodySmall)
            ExpandableSection("Warum diese Einschätzung?", explanationExpanded, { explanationExpanded = !explanationExpanded }) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(forecast.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Noch ${forecast.weeksRemaining} Wochen bis zum Halbmarathon.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun formatHalfMarathonTime(minutes: Int): String = "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}"

@Composable
private fun ReadinessEntryCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = "Trainingscheck öffnen. Schlaf, Ruhepuls und Laufdaten ansehen"
            role = Role.Button
        },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.run_health_check),
                contentDescription = null,
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Deine Trainingswerte", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("Schlaf, Ruhepuls und Laufpause ansehen", style = MaterialTheme.typography.bodySmall)
            }
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun TodayHeader(onOpenPlan: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text("Heute", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, d. MMMM", Locale.GERMANY))
                    .replaceFirstChar { it.titlecase(Locale.GERMANY) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(
            onClick = onOpenPlan,
            modifier = Modifier.semantics {
                contentDescription = "Wochenplan öffnen"
                role = Role.Button
            }
        ) {
            Image(
                painter = painterResource(R.drawable.run_navigation_plan),
                contentDescription = null,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

@Composable
private fun LoadingToday() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator()
        Text("Dein Trainingsplan wird geladen.")
    }
}

@Composable
private fun PlanErrorToday(onRetry: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Dein Plan ist gerade nicht erreichbar.")
            Button(onClick = onRetry) { Text("Noch einmal versuchen") }
        }
    }
}

@Composable
private fun SetupToday(onOpenPlan: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(
                painter = painterResource(R.drawable.run_training_run),
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
            Text("Dein Laufplan wartet auf dich.", style = MaterialTheme.typography.titleLarge)
            Text("Lege im Plan den Wettkampftag und deinen Trainingsrhythmus fest.")
            Button(onClick = onOpenPlan) { Text("Plan einrichten") }
        }
    }
}

@Composable
private fun TodayWorkoutCard(
    week: TrainingWeek,
    onOpenTrainingDetailsForTime: (LocalDate, PlannedRun, Int) -> Unit,
    completedRuns: Int,
    defaultAvailableMinutes: Int,
    maximumAvailableMinutes: Int,
    nextScheduledRun: ScheduledRun? = null,
    title: String = "Dein Training"
) {
    val requiredRuns = week.runs.filterNot { it.optional }
    val run = nextScheduledRun?.run ?: requiredRuns.getOrNull(completedRuns) ?: if (completedRuns >= week.requiredRunCount) {
        week.runs.firstOrNull { it.optional }
    } else {
        requiredRuns.firstOrNull()
    }
    val scheduledDate = nextScheduledRun?.scheduledDate ?: week.startsOn
    val allowedMaximumMinutes = maxOf(defaultAvailableMinutes, maximumAvailableMinutes).coerceIn(30, 180)
    var showTimeChoice by rememberSaveable(scheduledDate, run?.title) { mutableStateOf(false) }
    var showRecoveryChoice by rememberSaveable(week.startsOn, completedRuns) { mutableStateOf(false) }
    var showRecoveryAlternative by rememberSaveable(week.startsOn, completedRuns) { mutableStateOf(false) }
    var selectedMinutes by rememberSaveable(week.startsOn, run?.title, allowedMaximumMinutes) {
        mutableIntStateOf(defaultAvailableMinutes.coerceIn(30, allowedMaximumMinutes))
    }
    val workoutTitle = run?.title ?: "Heute ist Erholung sinnvoll"
    val detail = run?.let {
        val dateLabel = if (scheduledDate == LocalDate.now()) "Heute" else scheduledDate
            .format(DateTimeFormatter.ofPattern("EEE, d. MMM", Locale.GERMANY))
            .replaceFirstChar { character -> character.titlecase(Locale.GERMANY) }
        "$dateLabel · Locker · ca. ${it.totalMinutes} Min."
    }
        ?: "Die geplanten Laufeinheiten für diese Woche sind erledigt."

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "$title: $workoutTitle"
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.09f)
        )
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.labelMedium)
                    Text(workoutTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (run != null) {
                    Box(
                        Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary)
                        .clickable { onOpenTrainingDetailsForTime(scheduledDate, run, defaultAvailableMinutes) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("→", color = MaterialTheme.colorScheme.onTertiary, style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.run_training_run),
                    contentDescription = null,
                    modifier = Modifier.size(25.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Lauf auf der Garmin-Uhr beginnen · danach hier ausgewertet",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (run != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {
                        onOpenTrainingDetailsForTime(scheduledDate, run, defaultAvailableMinutes)
                    }) { Text("Training starten") }
                    TextButton(onClick = { showTimeChoice = true }) { Text("Weniger oder mehr Zeit?") }
                }
            } else {
                OutlinedButton(onClick = { showRecoveryChoice = true }) { Text("Trotzdem laufen") }
            }
        }
    }
    if (showTimeChoice && run != null) {
        AvailableTimeDialog(
            initialMinutes = defaultAvailableMinutes,
            maximumMinutes = allowedMaximumMinutes,
            selectedMinutes = selectedMinutes,
            onMinutesChanged = { selectedMinutes = it },
            onDismiss = { showTimeChoice = false },
            onConfirm = {
                showTimeChoice = false
                onOpenTrainingDetailsForTime(week.startsOn, run, selectedMinutes)
            }
        )
    }
    if (showRecoveryChoice) {
        AlertDialog(
            onDismissRequest = { showRecoveryChoice = false },
            title = { Text("Erholung ist heute eingeplant") },
            text = { Text("Deine vorgesehenen Laufeinheiten für diese Woche sind erledigt. Wenn du dich gut fühlst, wähle eine kurze, sehr leichte Alternative oder zeichne nur auf der Garmin-Uhr auf. Ein zusätzlicher Lauf ersetzt keine Planeinheit und wird erst nach dem späteren Import berücksichtigt.") },
            confirmButton = {
                TextButton(onClick = {
                    showRecoveryChoice = false
                    showRecoveryAlternative = true
                }) { Text("Leichte Alternative ansehen") }
            },
            dismissButton = {
                TextButton(onClick = { showRecoveryChoice = false }) { Text("Nur Garmin-Aufzeichnung") }
            }
        )
    }
    if (showRecoveryAlternative) {
        AlertDialog(
            onDismissRequest = { showRecoveryAlternative = false },
            title = { Text("20 Minuten sehr leicht") },
            text = { Text("5 Min. zügig gehen, 10 Min. im Sprechtempo laufen und bei Bedarf gehen, danach 5 Min. locker ausgehen. Keine Pace erzwingen. Starte die Aufzeichnung auf der Uhr unter Aktivitäten → Laufen. Der Zusatzlauf zählt erst nach der Synchronisierung und nicht als geplante Einheit.") },
            confirmButton = { TextButton(onClick = { showRecoveryAlternative = false }) { Text("Verstanden") } }
        )
    }
}

@Composable
private fun AvailableTimeDialog(
    initialMinutes: Int,
    maximumMinutes: Int,
    selectedMinutes: Int,
    onMinutesChanged: (Int) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wie viel Zeit hast du heute?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("LAP passt die nächste Einheit an. Diese Angabe gilt nur für heute.")
                Text("$selectedMinutes Minuten", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = selectedMinutes.toFloat(),
                    onValueChange = { value ->
                        val rounded = 30 + (((value - 30) / 15).toInt() * 15)
                        onMinutesChanged(rounded.coerceIn(30, maximumMinutes))
                    },
                    valueRange = 30f..maximumMinutes.toFloat(),
                    steps = ((maximumMinutes - 30) / 15 - 1).coerceAtLeast(0)
                )
                if (initialMinutes != selectedMinutes) {
                    Text("Üblich: bis zu $initialMinutes Minuten", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Einheit ansehen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zurück") } }
    )
}

@Composable
private fun AvailabilityPromptCard(onEdit: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.09f))
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Heute Zeit zum Trainieren?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Verfügbare Zeit angeben", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onEdit) { Text("Verfügbarkeit angeben") }
        }
    }
}

@Composable
private fun WeeklyRunMetric(
    week: TrainingWeek,
    history: WorkoutHistoryUiState,
    completedRuns: Int,
    animationKey: Int,
    modifier: Modifier
) {
    val hasAccess = !history.isLoading && history.accessMessage == null &&
        !history.isUnavailable && !history.hasError
    val permissionMissing = history.accessMessage != null
    val value = if (hasAccess) "$completedRuns / ${week.requiredRunCount}" else "— / ${week.requiredRunCount}"
    val fraction = if (hasAccess && week.requiredRunCount > 0) {
        (completedRuns.toFloat() / week.requiredRunCount).coerceIn(0f, 1f)
    } else {
        0f
    }

    Card(
        modifier = modifier.semantics {
            contentDescription = if (hasAccess) {
                "$completedRuns von ${week.requiredRunCount} Läufen diese Woche"
            } else {
                "Laufdaten: ${if (history.isLoading) "werden geladen" else "nicht verfügbar"}"
            }
        },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Läufe diese Woche", style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        when {
                            hasAccess -> "erledigt"
                            history.isLoading -> "wird geladen"
                            permissionMissing -> "Freigabe fehlt"
                            history.isUnavailable -> "nicht verfügbar"
                            else -> "nicht geladen"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (permissionMissing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                MetricRing(
                    progress = fraction,
                    color = if (permissionMissing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    centerText = if (permissionMissing) "!" else null,
                    isLoading = history.isLoading,
                    animationKey = animationKey
                )
            }
        }
    }
}

@Composable
private fun PlanWeekMetric(
    week: TrainingWeek,
    plan: TrainingPlan,
    raceDistanceMeters: Int?,
    animationKey: Int,
    modifier: Modifier
) {
    val totalWeeks = plan.weeks.lastOrNull()?.number?.coerceAtLeast(week.number) ?: week.number
    val fraction = if (totalWeeks > 0) (week.number.toFloat() / totalWeeks).coerceIn(0f, 1f) else 0f

    Card(
        modifier = modifier.semantics {
            contentDescription = "Trainingsplan, Woche ${week.number} von $totalWeeks"
        },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (plan.status == PlanStatus.MAINTAINING_FORM) "Form halten"
                else raceDistanceMeters?.let(::raceDistanceName) ?: "Trainingsplan",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("${week.number} / $totalWeeks", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Planwoche", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                MetricRing(progress = fraction, color = MaterialTheme.colorScheme.tertiary, animationKey = animationKey)
            }
        }
    }
}

@Composable
private fun MetricRing(
    progress: Float,
    color: androidx.compose.ui.graphics.Color,
    centerText: String? = null,
    isLoading: Boolean = false,
    animationKey: Int = 0
) {
    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(animationKey, progress, isLoading) {
        animatedProgress.snapTo(0f)
        if (!isLoading) {
            animatedProgress.animateTo(
                targetValue = progress.coerceIn(0f, 1f),
                animationSpec = tween(durationMillis = 1_800, easing = LinearOutSlowInEasing)
            )
        }
    }

    Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { if (isLoading) 0f else animatedProgress.value },
            modifier = Modifier.fillMaxSize(),
            color = color,
            trackColor = color.copy(alpha = 0.16f),
            strokeWidth = 3.5.dp
        )
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = color)
        } else if (centerText != null) {
            Text(centerText, color = color, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun WeeklyActivityCard(
    week: TrainingWeek,
    workouts: List<Workout>,
    history: WorkoutHistoryUiState,
    completedRuns: Int,
    onRequestWorkoutAccess: () -> Unit,
    onRetry: () -> Unit
) {
    val hasAccess = !history.isLoading && history.accessMessage == null &&
        !history.isUnavailable && !history.hasError
    val runDays = workouts.map { it.localDate() }.toSet()
    val weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val dayLabels = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Diese Woche", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(
                    if (hasAccess) "$completedRuns / ${week.requiredRunCount} Läufe" else "Läufe nicht freigegeben",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                dayLabels.forEachIndexed { index, label ->
                    val date = weekStart.plusDays(index.toLong())
                    val hasRun = hasAccess && date in runDays
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.semantics {
                            contentDescription = "$label: ${if (hasRun) "Lauf erledigt" else if (hasAccess) "kein Lauf eingetragen" else "Laufdaten nicht freigegeben"}"
                        }
                    ) {
                        Box(
                            Modifier.size(17.dp)
                                .clip(CircleShape)
                                .background(
                                    if (hasRun) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainerHighest
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (hasRun) Text("✓", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall)
                        }
                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            SyncMessage(history, onRequestWorkoutAccess, onRetry)
        }
    }
}

@Composable
private fun SyncMessage(
    history: WorkoutHistoryUiState,
    onRequestAccess: () -> Unit,
    onRetry: () -> Unit
) {
    when {
        history.isLoading -> Text("Läufe werden aktualisiert …", style = MaterialTheme.typography.bodySmall)
        history.isUnavailable -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Laufdaten sind auf diesem Gerät gerade nicht verfügbar.", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onRetry) { Text("Erneut") }
        }
        history.accessMessage != null -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Freigabe für Laufdaten fehlt.", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onRequestAccess) { Text("Freigeben") }
        }
        history.hasError -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Läufe konnten nicht aktualisiert werden.", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onRetry) { Text("Erneut") }
        }
        history.workouts.isEmpty() -> Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.run_training_run),
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(7.dp))
            Text("Starte auf der Uhr unter Aktivitäten → Laufen.", style = MaterialTheme.typography.bodySmall)
        }
        else -> Text("Deine synchronisierten Läufe sind eingerechnet.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EventTooCloseToday(plan: TrainingPlan, onOpenPlan: () -> Unit) {
    Card(
        onClick = onOpenPlan,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.09f))
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Dein Training", style = MaterialTheme.typography.labelMedium)
            Text("Wettkampftag rückt nah", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Ein neuer Trainingsaufbau braucht mehr Zeit. Öffne den Plan für den Hinweis.")
            plan.eventDate?.let { Text(it.format(DateTimeFormatter.ofPattern("dd. MMMM yyyy", Locale.GERMANY))) }
        }
    }
}

@Composable
private fun PlanChoiceToday(title: String, message: String, onOpenPlan: () -> Unit) {
    Card(
        onClick = onOpenPlan,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onOpenPlan) { Text("Plan ansehen") }
        }
    }
}

private fun Workout.localDate(): LocalDate =
    Instant.ofEpochMilli(startedAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()
