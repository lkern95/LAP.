package de.run.app.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.run.app.R
import de.run.app.domain.model.PlanStatus
import de.run.app.domain.model.PlannedRunMatchType
import de.run.app.domain.model.AvailabilityMode
import de.run.app.domain.model.PlannedRun
import de.run.app.domain.model.TrainingPlan
import de.run.app.domain.model.TrainingWeek
import de.run.app.domain.model.Workout
import de.run.app.domain.model.isRunning
import de.run.app.domain.model.ScheduledRun
import de.run.app.presentation.history.WorkoutHistoryUiState
import de.run.app.presentation.history.WorkoutHistoryViewModel
import de.run.app.presentation.plan.PlanViewModel
import de.run.app.presentation.plan.TrainingPlanEvidenceNotice
import de.run.app.presentation.plan.raceDistanceName
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

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
                if (plan.weeks.isNotEmpty()) TrainingPlanEvidenceNotice(planState.planEvidence)
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
                    val scheduledRun = planState.nextScheduledRun
                    val scheduledDate = scheduledRun?.scheduledDate ?: LocalDate.now()
                    val selectedRun = scheduledRun?.run
                    val scheduledDayLimit = profile.runTimeLimitsByDay[scheduledDate.dayOfWeek]
                        ?: profile.usualRunTimeLimitMinutes
                    val flexibleLimit = if (selectedRun?.title?.contains("langer", ignoreCase = true) == true) {
                        profile.occasionalRunTimeLimitMinutes ?: 180
                    } else profile.usualRunTimeLimitMinutes
                    val variableDayLimit = profile.datedAvailability[scheduledDate]?.maximumMinutes
                        ?.let { minOf(it, flexibleLimit) } ?: flexibleLimit
                    val recurringInitialMinutes = (selectedRun?.totalMinutes ?: scheduledDayLimit)
                        .coerceIn(30, scheduledDayLimit.coerceIn(30, 180))
                    val variableInitialMinutes = (selectedRun?.totalMinutes ?: profile.usualRunTimeLimitMinutes)
                        .coerceIn(30, variableDayLimit.coerceIn(30, 180))
                    val hasWorkoutAccess = hasUsableWorkoutHistory(historyState)
                    TodayWorkoutStatusCard(
                        history = historyState,
                        onRequestWorkoutAccess = onRequestWorkoutAccess,
                        onOpenReadiness = onOpenReadiness,
                        onRetry = workoutHistoryViewModel::refresh
                    )
                    val nextScheduledRun = planState.nextScheduledRun
                    when {
                        profile.availabilityMode == AvailabilityMode.VARIABLE -> TodayWorkoutCard(
                            week,
                            onOpenTrainingDetailsForTime,
                            completedRuns,
                            variableInitialMinutes,
                            variableDayLimit,
                            canStartTraining = hasWorkoutAccess,
                            nextScheduledRun = nextScheduledRun,
                            title = "Dein nächstes Training"
                        )
                        profile.availabilityMode == AvailabilityMode.RECURRING && profile.availableRunDays.isEmpty() ->
                            AvailabilityPromptCard(onOpenAvailability)
                        else -> TodayWorkoutCard(
                            week,
                            onOpenTrainingDetailsForTime,
                            completedRuns,
                            recurringInitialMinutes,
                            scheduledDayLimit,
                            canStartTraining = hasWorkoutAccess,
                            nextScheduledRun = nextScheduledRun,
                            title = "Dein nächstes Training"
                        )
                    }
                    if (hasStoredRunningWorkouts(historyState)) {
                        planState.halfMarathonForecast?.let { forecast ->
                        HalfMarathonForecastCard(
                            forecast = forecast
                        )
                        }
                    }
                    ReadinessEntryCard(onOpenReadiness)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        WeeklyRunVolumeMetric(
                            weeklyVolume = planState.progress?.weeklyRunVolumes?.lastOrNull(),
                            history = historyState,
                            workouts = historyState.workouts,
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
    forecast: de.run.app.domain.HalfMarathonForecast,
) {
    var cardExpanded by rememberSaveable { mutableStateOf(false) }
    var explanationExpanded by rememberSaveable { mutableStateOf(false) }
    val estimated = forecast.estimatedMinutes?.let {
        val label = when (forecast.confidence) {
            de.run.app.domain.ForecastConfidence.ROUGH -> "Grobe Zeitspanne"
            else -> "Geschätzte Zeitspanne"
        }
        "$label: ${formatHalfMarathonTime(it.first)}–${formatHalfMarathonTime(it.last)} Std."
    }
    val development = if (forecast.recentRunCount == 0) "In den letzten 4 Wochen: keine Läufe erfasst."
    else {
        val earlier = if (forecast.previousRunCount == 0) "Davor keine Vergleichswerte" else
            "Davor ${forecast.previousRunCount} Läufe, ${"%.1f".format(Locale.GERMANY, forecast.previousDistanceKilometers)} km"
        "${forecast.recentRunCount} Läufe, ${"%.1f".format(Locale.GERMANY, forecast.recentDistanceKilometers)} km in 4 Wochen; " +
            "längster Lauf ${"%.1f".format(Locale.GERMANY, forecast.recentLongestRunKilometers)} km. $earlier."
    }
    val nextStep = forecast.recommendation
        .substringBefore(". ")
        .removePrefix("Als Nächstes: ")
        .removePrefix("Als Nächstes ")
        .trimEnd()
        .removeSuffix(".")
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(
                Modifier.fillMaxWidth()
                    .clickable { cardExpanded = !cardExpanded }
                    .semantics {
                        contentDescription = "Bin ich auf Kurs? ${if (cardExpanded) "ausblenden" else "anzeigen"}"
                        role = Role.Button
                        stateDescription = if (cardExpanded) "Ausgeklappt" else "Eingeklappt"
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Bin ich auf Kurs?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(forecast.outlookSummary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Text(if (cardExpanded) "⌄" else "›", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            androidx.compose.animation.AnimatedVisibility(visible = cardExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(development, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Nächster Schritt: $nextStep.", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    when {
                        forecast.confidence == de.run.app.domain.ForecastConfidence.TRAINING_ORIENTATION -> {
                            Text("${forecast.evidenceRuns} lange Läufe zeigen deine Ausdauer. Für eine Zielzeit fehlt noch ein Test- oder Wettkampflauf.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        estimated != null -> Text("$estimated · aus ${forecast.evidenceRuns} aktuellen Test-, Wettkampf- oder anstrengenden Läufen", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        else -> Text("Für eine Zielzeit fehlt ein Lauf über 5 km, den du als sehr anstrengend oder als Test/Wettkampf erfasst hast.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    if (forecast.estimatedMinutes != null) {
                        Text(
                            "Das ist eine grobe Schätzung, keine genaue Vorhersage.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                        )
                    }
                    ForecastExplanationSection(
                        expanded = explanationExpanded,
                        onToggle = { explanationExpanded = !explanationExpanded }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(forecast.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("Noch ${forecast.weeksRemaining} Wochen bis zum Halbmarathon.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
        }
    }
}

private fun formatHalfMarathonTime(minutes: Int): String = "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}"

@Composable
private fun ForecastExplanationSection(
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).semantics {
                contentDescription = "Warum diese Einschätzung? ${if (expanded) "ausblenden" else "anzeigen"}"
                role = Role.Button
                stateDescription = if (expanded) "Ausgeklappt" else "Eingeklappt"
            }.padding(vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Warum diese Einschätzung?", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Text(if (expanded) "⌄" else "›", style = MaterialTheme.typography.titleSmall)
        }
        androidx.compose.animation.AnimatedVisibility(visible = expanded) { content() }
    }
}

private fun hasUsableWorkoutHistory(history: WorkoutHistoryUiState): Boolean =
    !history.isLoading && history.accessMessage == null && !history.isUnavailable && !history.hasError

internal fun hasStoredRunningWorkouts(history: WorkoutHistoryUiState): Boolean =
    !history.isLoading && history.workouts.any(Workout::isRunning)

private fun hasVisibleWorkoutHistory(history: WorkoutHistoryUiState): Boolean =
    hasUsableWorkoutHistory(history) || hasStoredRunningWorkouts(history)

@Composable
private fun TodayWorkoutStatusCard(
    history: WorkoutHistoryUiState,
    onRequestWorkoutAccess: () -> Unit,
    onOpenReadiness: () -> Unit,
    onRetry: () -> Unit
) {
    if (history.isLoading) return
    if (hasStoredRunningWorkouts(history)) return
    val message = when {
        history.accessMessage != null -> "LAP benötigt Zugriff auf deine Laufdaten, um dein Training auszuwerten."
        history.isUnavailable -> "Laufdaten sind auf diesem Gerät gerade nicht verfügbar."
        history.hasError -> "Deine Laufdaten konnten gerade nicht geladen werden."
        history.workouts.isEmpty() -> "Für eine aktuelle Einschätzung fehlt noch ein Lauf."
        else -> return
    }
    val action = when {
        history.accessMessage != null -> "Laufdaten freigeben" to onRequestWorkoutAccess
        history.hasError -> "Erneut versuchen" to onRetry
        else -> "Grund ansehen" to onOpenReadiness
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                if (history.accessMessage != null || history.isUnavailable || history.hasError) {
                    "Laufdaten noch nicht verfügbar"
                } else {
                    "Noch keine aktuellen Laufdaten"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = action.second) { Text(action.first) }
        }
    }
}

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
                Text("Deine Erholungsdaten", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
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
internal fun TodayWorkoutCard(
    week: TrainingWeek,
    onOpenTrainingDetailsForTime: (LocalDate, PlannedRun, Int) -> Unit,
    completedRuns: Int,
    defaultAvailableMinutes: Int,
    maximumAvailableMinutes: Int,
    canStartTraining: Boolean,
    nextScheduledRun: ScheduledRun? = null,
    title: String = "Dein Training"
) {
    val run = nextScheduledRun?.run
    val scheduledDate = nextScheduledRun?.scheduledDate ?: LocalDate.now()
    val usualMaximumMinutes = maxOf(defaultAvailableMinutes, maximumAvailableMinutes).coerceIn(20, 240)
    var showTimeChoice by rememberSaveable(scheduledDate, run?.title) { mutableStateOf(false) }
    var showRecoveryChoice by rememberSaveable(week.startsOn, completedRuns) { mutableStateOf(false) }
    var showRecoveryAlternative by rememberSaveable(week.startsOn, completedRuns) { mutableStateOf(false) }
    var selectedMinutes by rememberSaveable(week.startsOn, run?.title, usualMaximumMinutes) {
        mutableIntStateOf(snapAvailableMinutes(defaultAvailableMinutes))
    }
    val workoutTitle = run?.title ?: "Heute ist Erholung sinnvoll"
    val detail = run?.let {
        val dateLabel = if (scheduledDate == LocalDate.now()) "Heute" else scheduledDate
            .format(DateTimeFormatter.ofPattern("EEE, d. MMM", Locale.GERMANY))
            .replaceFirstChar { character -> character.titlecase(Locale.GERMANY) }
        "$dateLabel · Locker · ca. ${it.totalMinutes} Min."
    }
        ?: if (completedRuns >= week.requiredRunCount) {
            "Die geplanten Laufeinheiten für diese Woche sind erledigt."
        } else {
            "Für diese Woche ist kein weiterer Lauftag eingeplant."
        }

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
        Box {
            Image(
                painter = painterResource(R.drawable.today_training_start_background),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.align(Alignment.Center).size(164.dp).alpha(0.07f)
            )
            Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(title, style = MaterialTheme.typography.labelMedium)
                        Text(workoutTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (run != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val iconColor = MaterialTheme.colorScheme.onTertiary
                            Box(
                                Modifier.size(46.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.tertiary)
                                    .clickable { onOpenTrainingDetailsForTime(week.startsOn, run, defaultAvailableMinutes) }
                                    .semantics {
                                        contentDescription = "Trainingsdetails ansehen"
                                        role = Role.Button
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(Modifier.size(20.dp)) {
                                    val stroke = 2.dp.toPx()
                                    val start = Offset(size.width * 0.12f, size.height * 0.5f)
                                    val tip = Offset(size.width * 0.88f, size.height * 0.5f)
                                    drawLine(iconColor, start, tip, stroke, StrokeCap.Round)
                                    drawLine(
                                        iconColor,
                                        tip,
                                        Offset(size.width * 0.62f, size.height * 0.24f),
                                        stroke,
                                        StrokeCap.Round
                                    )
                                    drawLine(
                                        iconColor,
                                        tip,
                                        Offset(size.width * 0.62f, size.height * 0.76f),
                                        stroke,
                                        StrokeCap.Round
                                    )
                                }
                            }
                            if (canStartTraining) {
                                Box(
                                    Modifier.size(46.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.tertiary)
                                        .clickable { showTimeChoice = true }
                                        .semantics {
                                            contentDescription = "Weniger oder mehr Zeit?"
                                            role = Role.Button
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(Modifier.size(20.dp)) {
                                        val stroke = 2.dp.toPx()
                                        drawCircle(iconColor, radius = size.minDimension * 0.39f, style = Stroke(stroke))
                                        drawLine(
                                            iconColor,
                                            Offset(size.width * 0.5f, size.height * 0.5f),
                                            Offset(size.width * 0.5f, size.height * 0.25f),
                                            stroke,
                                            StrokeCap.Round
                                        )
                                        drawLine(
                                            iconColor,
                                            Offset(size.width * 0.5f, size.height * 0.5f),
                                            Offset(size.width * 0.7f, size.height * 0.58f),
                                            stroke,
                                            StrokeCap.Round
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (run != null && canStartTraining) {
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
                } else if (run == null && canStartTraining) {
                    OutlinedButton(onClick = { showRecoveryChoice = true }) { Text("Trotzdem laufen") }
                }
            }
        }
    }
    if (showTimeChoice && run != null) {
        AvailableTimeDialog(
            initialMinutes = defaultAvailableMinutes,
            usualMaximumMinutes = usualMaximumMinutes,
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
    usualMaximumMinutes: Int,
    selectedMinutes: Int,
    onMinutesChanged: (Int) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var sliderPosition by remember(initialMinutes) {
        mutableFloatStateOf(selectedMinutes.toFloat())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wie viel Zeit hast du heute?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("LAP passt die nächste Einheit an. Diese Angabe gilt nur für heute.")
                Text("$selectedMinutes Minuten", style = MaterialTheme.typography.titleMedium)
                Slider(
                    modifier = Modifier.testTag("available-time-slider"),
                    value = sliderPosition,
                    onValueChange = { value ->
                        val minutes = snapAvailableMinutes(value.roundToInt())
                        sliderPosition = minutes.toFloat()
                        onMinutesChanged(minutes)
                    },
                    valueRange = MIN_AVAILABLE_TIME_MINUTES.toFloat()..MAX_AVAILABLE_TIME_MINUTES.toFloat(),
                    steps = AVAILABLE_TIME_STEPS
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("20 Min.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("4 Std.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (initialMinutes != selectedMinutes || selectedMinutes > usualMaximumMinutes) {
                    Text("Üblich: bis zu $usualMaximumMinutes Minuten", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Einheit ansehen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zurück") } }
    )
}

private fun snapAvailableMinutes(minutes: Int): Int =
    (MIN_AVAILABLE_TIME_MINUTES +
        ((minutes - MIN_AVAILABLE_TIME_MINUTES) / AVAILABLE_TIME_STEP_MINUTES.toFloat()).roundToInt() * AVAILABLE_TIME_STEP_MINUTES)
        .coerceIn(MIN_AVAILABLE_TIME_MINUTES, MAX_AVAILABLE_TIME_MINUTES)

private const val MIN_AVAILABLE_TIME_MINUTES = 20
private const val MAX_AVAILABLE_TIME_MINUTES = 240
private const val AVAILABLE_TIME_STEP_MINUTES = 10
private const val AVAILABLE_TIME_STEPS = (MAX_AVAILABLE_TIME_MINUTES - MIN_AVAILABLE_TIME_MINUTES) / AVAILABLE_TIME_STEP_MINUTES - 1

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
private fun WeeklyRunVolumeMetric(
    weeklyVolume: de.run.app.domain.model.WeeklyRunVolume?,
    history: WorkoutHistoryUiState,
    workouts: List<Workout>,
    animationKey: Int,
    modifier: Modifier
) {
    val hasAccess = hasVisibleWorkoutHistory(history)
    val weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val thisWeekRuns = workouts.filter { it.isRunning() && it.localDate() in weekStart..weekStart.plusDays(6) }
    val distanceMeters = weeklyVolume?.distanceMeters
    val animatedDistance = remember { Animatable(0f) }
    val targetKilometers = distanceMeters?.div(1_000.0)?.toFloat() ?: 0f
    LaunchedEffect(animationKey, targetKilometers, hasAccess) {
        animatedDistance.snapTo(0f)
        if (hasAccess && distanceMeters != null) {
            animatedDistance.animateTo(
                targetValue = targetKilometers,
                animationSpec = tween(durationMillis = 1_800, easing = LinearOutSlowInEasing)
            )
        }
    }
    val value = when {
        !hasAccess -> "—"
        thisWeekRuns.isEmpty() -> "0,0 km"
        distanceMeters != null -> "%.1f km".format(Locale.GERMANY, animatedDistance.value)
        else -> "—"
    }
    val detail = when {
        !hasAccess -> "Laufdaten nicht verfügbar"
        thisWeekRuns.isEmpty() -> "Noch keine Läufe erfasst"
        distanceMeters == null && thisWeekRuns.isNotEmpty() -> "Distanz nicht vollständig erfasst"
        thisWeekRuns.isNotEmpty() -> "Per ${weeklyRunSourceLabel(thisWeekRuns)} erfasst"
        else -> "Laufdaten werden aktualisiert"
    }

    Card(
        modifier = modifier.semantics {
            contentDescription = "Laufumfang diese Woche: $value. $detail"
        },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Box {
            Image(
                painter = painterResource(R.drawable.today_weekly_volume_background),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.align(Alignment.Center).size(92.dp).blur(3.dp).alpha(0.12f)
            )
            Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Laufumfang diese Woche", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
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
        Box {
            Image(
                painter = painterResource(R.drawable.today_half_marathon_background),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.align(Alignment.Center).size(92.dp).blur(3.dp).alpha(0.12f)
            )
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
    onRequestWorkoutAccess: () -> Unit,
    onRetry: () -> Unit
) {
    val hasAccess = hasVisibleWorkoutHistory(history)
    val weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weekEnd = weekStart.plusDays(6)
    val weekRuns = workouts.filter { it.isRunning() && it.localDate() in weekStart..weekEnd }
        .distinctBy(Workout::sourceId)
    val runsByDay = weekRuns.groupBy(Workout::localDate)
    val dayLabels = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
    val latestRunDate = weekRuns.maxByOrNull(Workout::startedAtMillis)?.localDate()
    val activitySummary = if (hasAccess && weekRuns.isNotEmpty() && latestRunDate != null) {
        val source = weeklyRunSourceLabel(weekRuns)
        "${weekRuns.size} ${if (weekRuns.size == 1) "Lauf" else "Läufe"} erfasst\nPer $source · zuletzt ${latestRunDate.format(DateTimeFormatter.ofPattern("d. MMM", Locale.GERMANY))}"
    } else null

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
                    plannedRunCountLabel(week.requiredRunCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                dayLabels.forEachIndexed { index, label ->
                    val date = weekStart.plusDays(index.toLong())
                    val dayRuns = runsByDay[date].orEmpty().takeIf { hasAccess }.orEmpty()
                    val hasRun = dayRuns.isNotEmpty()
                    val isExtraRun = hasRun && dayRuns.all { it.planMatch?.type == PlannedRunMatchType.EXTRA }
                    val hasPlannedRun = hasRun && !isExtraRun
                    val fill = remember(date) { Animatable(0f) }
                    LaunchedEffect(hasRun) {
                        fill.animateTo(
                            targetValue = if (hasRun) 1f else 0f,
                            animationSpec = tween(durationMillis = 1_800, easing = LinearOutSlowInEasing)
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.semantics {
                            contentDescription = "$label: ${when {
                                isExtraRun -> "Zusatzlauf erfasst"
                                hasPlannedRun -> "Lauf erfasst"
                                hasAccess -> "kein Lauf erfasst"
                                else -> "Laufdaten nicht verfügbar"
                            }}"
                        }
                    ) {
                        Box(
                            Modifier.size(17.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center
                        ) {
                            if (hasPlannedRun) Box(
                                Modifier.fillMaxSize()
                                    .graphicsLayer { scaleX = fill.value; scaleY = fill.value }
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            if (isExtraRun) Box(
                                Modifier.fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = fill.value
                                        scaleY = fill.value
                                        alpha = fill.value
                                    }
                                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            )
                            if (hasRun) Text(
                                "✓",
                                modifier = Modifier.graphicsLayer { alpha = fill.value },
                                color = if (isExtraRun) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            activitySummary?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (hasAccess) SyncMessage(history, onRequestWorkoutAccess, onRetry)
        }
    }
}

internal fun weeklyRunSourceLabel(workouts: List<Workout>): String {
    val sources = workouts.filter(Workout::isRunning).map(Workout::sourceName).toSet()
    return when {
        sources == setOf("FIT-Datei") -> "FIT-Import"
        sources == setOf("Garmin Connect") -> "Garmin Connect"
        sources == setOf("Health Sync") -> "Health Connect"
        sources.size == 1 && "Garmin-Backup" in sources -> "Garmin-Backup"
        sources.isEmpty() -> "Laufdaten"
        else -> "mehrere Quellen"
    }
}

internal fun plannedRunCountLabel(count: Int): String =
    if (count == 1) "1 Lauf vorgesehen" else "$count Läufe vorgesehen"

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
            Text("Automatischer Laufabgleich nicht freigegeben.", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
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
