package de.run.app.presentation.readiness

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.run.app.BuildConfig
import de.run.app.R
import de.run.app.data.demo.DebugDemoModeController
import de.run.app.domain.model.HealthMetric
import de.run.app.domain.model.HealthMetricAvailability
import de.run.app.domain.model.ReadinessFacts
import de.run.app.domain.model.Workout
import de.run.app.domain.model.isRunning
import de.run.app.domain.TrainingFeedback
import de.run.app.domain.ReadinessAssessmentAnalyzer
import de.run.app.domain.ReadinessSignalBand
import de.run.app.domain.RestingHeartRatePersonalBand
import de.run.app.domain.readinessSignalBand
import de.run.app.presentation.components.AppPopup
import de.run.app.presentation.components.PersonalTrainingFeedbackCard
import de.run.app.presentation.theme.LocalCautionColor
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

@Composable
fun ReadinessScreen(
    viewModel: ReadinessViewModel,
    debugDemoMode: DebugDemoModeController?,
    onRequestAccess: () -> Unit,
    onDemoModeChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
    trainingFeedback: TrainingFeedback? = null
) {
    val state by viewModel.state.collectAsState()
    var selectedReadinessDetail by remember { mutableStateOf<Pair<String, String>?>(null) }
    val isDemo by (debugDemoMode?.enabled ?: kotlinx.coroutines.flow.MutableStateFlow(false)).collectAsState()
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 8.dp)) {
        ReadinessHeader(onBack, isDemo)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ReadinessMeter(state) { title, detail -> selectedReadinessDetail = title to detail }
            ReadinessFactsCard(state) { title, detail -> selectedReadinessDetail = title to detail }
            if (state.hasWorkoutAccess == true && !state.workoutsUnavailable && !state.workoutsFailed) {
                state.weeklyTrainingComparison?.let { comparison ->
                    WeeklyTrainingComparisonCard(comparison)
                }
            }
            if (trainingFeedback != null) PersonalTrainingFeedbackCard(trainingFeedback)
            else ReadinessGuidanceCard(state)
            ReadinessNotice(state, viewModel::refresh, onRequestAccess)
            if (BuildConfig.DEBUG && debugDemoMode != null) {
                DemoDataControl(isDemo) { onDemoModeChanged(!isDemo) }
            }
        }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Text("Alles passt")
        }
    }
    val selectedDetail = selectedReadinessDetail
    val selectedDataTitle = selectedDetail?.first?.takeIf { it in READINESS_POPUP_TITLES }
    AppPopup(
        visible = selectedDetail != null,
        title = selectedDetail?.first.orEmpty(),
        onDismiss = { selectedReadinessDetail = null },
        bottomSheet = selectedDetail?.first != "So entsteht dein Erholungswert",
        largeTitle = selectedDataTitle == "HRV / HFV",
        bottomSheetHeightFraction = if (selectedDataTitle == "HRV / HFV") 0.82f else 0.92f
    ) {
        when {
            selectedDataTitle != null -> ReadinessDataPopup(selectedDataTitle, state)
            selectedDetail?.first == "So entsteht dein Erholungswert" -> {
                Spacer(Modifier.height(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Dein Schlaf, deine Herzwerte und deine Laufbelastung bestimmen deinen Erholungswert.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Je höher der Wert, desto besser deine geschätzte Erholung.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Der Wert ist eine Einschätzung, keine exakte Messung.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            selectedDetail != null -> {
                Spacer(Modifier.height(14.dp))
                Text(selectedDetail.second, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
internal fun WeeklyTrainingComparisonCard(comparison: de.run.app.domain.WeeklyTrainingComparison) {
    val dateFormat = java.time.format.DateTimeFormatter.ofPattern("d. MMM", java.util.Locale.GERMAN)
    fun period(stats: de.run.app.domain.TrainingWeekStats): String =
        "${stats.startsOn.format(dateFormat)} – ${stats.startsOn.plusDays(6).format(dateFormat)}"
    fun summary(stats: de.run.app.domain.TrainingWeekStats): String {
        val distance = stats.distanceMeters?.let { "${formatReadinessDistance(it)} km" } ?: "Strecke –"
        val pace = stats.comparablePaceSecondsPerKilometer?.let(::formatReadinessPace) ?: "–"
        val pulse = stats.averageHeartRateBpm?.let { "$it bpm" } ?: "–"
        return "$distance · ${stats.durationSeconds / 60} Min. · Pace $pace min/km · Puls $pulse"
    }
    Card(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp),
        shape = RoundedCornerShape(18.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Deine zwei letzten Trainingswochen",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            TrainingWeekSummary(comparison.previous, period(comparison.previous), summary(comparison.previous))
            TrainingWeekSummary(comparison.current, period(comparison.current), summary(comparison.current))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
            val combinedRuns = comparison.previous.runCount + comparison.current.runCount
            val combinedPeriod = "${comparison.previous.startsOn.format(dateFormat)} – ${comparison.current.startsOn.plusDays(6).format(dateFormat)}"
            Text(
                "$combinedPeriod: $combinedRuns absolvierte Läufe",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                TrainingWeekDifference("Strecke", comparison.changes.distanceMeters.amount, comparison.changes.distanceMeters.trend) { formatReadinessDistance(kotlin.math.abs(it)) + " km" }
                TrainingWeekDifference("Laufzeit", comparison.changes.durationSeconds.amount, comparison.changes.durationSeconds.trend) { "${(kotlin.math.abs(it) / 60.0).roundToInt()} Min." }
                TrainingWeekDifference("Pace", comparison.changes.paceSecondsPerKilometer.amount, comparison.changes.paceSecondsPerKilometer.trend) { formatPaceDifference(kotlin.math.abs(it)) }
                TrainingWeekDifference("Puls", comparison.changes.averageHeartRateBpm.amount, comparison.changes.averageHeartRateBpm.trend) { "${kotlin.math.abs(it)} bpm" }
            }
        }
    }
}

@Composable
private fun TrainingWeekSummary(stats: de.run.app.domain.TrainingWeekStats, period: String, summary: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("$period: ${stats.runCount} absolvierte Läufe", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        Text(
            summary,
            Modifier.fillMaxWidth().semantics { contentDescription = "Trainingswerte: $summary" },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun <T> androidx.compose.foundation.layout.RowScope.TrainingWeekDifference(
    label: String,
    amount: T?,
    trend: de.run.app.domain.TrainingWeekTrend?,
    format: (T) -> String
) {
    val color = when (trend) {
        de.run.app.domain.TrainingWeekTrend.IMPROVED -> MaterialTheme.colorScheme.primary
        de.run.app.domain.TrainingWeekTrend.DECLINED -> MaterialTheme.colorScheme.error
        de.run.app.domain.TrainingWeekTrend.UNCHANGED, null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val arrow = when (trend) {
        de.run.app.domain.TrainingWeekTrend.IMPROVED -> "↑"
        de.run.app.domain.TrainingWeekTrend.DECLINED -> "↓"
        de.run.app.domain.TrainingWeekTrend.UNCHANGED -> "→"
        null -> "–"
    }
    val value = amount?.let(format) ?: "–"
    val sign = when {
        amount == null -> ""
        amount is Number && amount.toDouble() > 0.0 -> "+"
        amount is Number && amount.toDouble() < 0.0 -> "−"
        else -> ""
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false
        )
        Text(
            "$arrow $sign$value",
            Modifier.semantics { contentDescription = "$label: $arrow $sign$value" },
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
            softWrap = false
        )
    }
}

private fun formatReadinessDistance(meters: Double): String =
    String.format(java.util.Locale.GERMANY, "%.1f", meters / 1_000.0)

private fun formatPaceDifference(seconds: Long): String {
    val absolute = kotlin.math.abs(seconds)
    return "%d:%02d min/km".format(java.util.Locale.GERMANY, absolute / 60, absolute % 60)
}

private fun formatReadinessPace(secondsPerKilometer: Long): String =
    "%d:%02d".format(java.util.Locale.GERMANY, secondsPerKilometer / 60, secondsPerKilometer % 60)

@Composable
private fun ReadinessHeader(onBack: () -> Unit, isDemo: Boolean) {
    Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart).semantics {
                contentDescription = "Zurück"
                role = Role.Button
            }
        ) { Text("‹", style = MaterialTheme.typography.headlineLarge) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Erholung", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (isDemo) Text("Demodaten", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
internal fun ReadinessMeter(
    state: ReadinessUiState,
    onOpenDetail: (String, String) -> Unit = { _, _ -> }
) {
    val count = state.availableDataAreaCount()
    val score = state.assessment.score
    val targetFraction = if (state.isLoading || score == null) 0f else score / 100f
    val animatedFraction = remember { Animatable(0f) }
    LaunchedEffect(score, state.isLoading) {
        if (state.isLoading || score == null) {
            animatedFraction.snapTo(0f)
        } else {
            animatedFraction.animateTo(
                targetValue = targetFraction,
                animationSpec = tween(durationMillis = 1_800, easing = FastOutSlowInEasing)
            )
        }
    }
    val fraction = animatedFraction.value
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val progressColor = recoveryMeterColor(
        score = score,
        positive = MaterialTheme.colorScheme.primary,
        caution = LocalCautionColor.current,
        negative = MaterialTheme.colorScheme.error
    )
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(Modifier.size(width = 230.dp, height = 124.dp), contentAlignment = Alignment.BottomCenter) {
            Canvas(Modifier.fillMaxSize().semantics {
                contentDescription = when {
                    state.isLoading -> "Erholungsdaten werden geladen"
                    score == null -> "Erholung noch nicht bewertbar"
                    else -> "Geschätzte Erholung: $score von 100"
                }
            }) {
                val width = 12.dp.toPx()
                val radius = (size.width / 2f - width / 2f)
                    .coerceAtMost(size.height - width / 2f)
                val centerX = size.width / 2f
                val centerY = size.height - width / 2f
                val arcSize = radius * 2f
                val topLeft = androidx.compose.ui.geometry.Offset(centerX - radius, centerY - radius)
                val arcDimensions = androidx.compose.ui.geometry.Size(arcSize, arcSize)
                drawArc(
                    color = trackColor,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcDimensions,
                    style = Stroke(width, cap = StrokeCap.Round)
                )
                if (fraction > 0f) drawArc(
                    color = progressColor,
                    startAngle = 180f,
                    sweepAngle = 180f * fraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcDimensions,
                    style = Stroke(width, cap = StrokeCap.Round)
                )
            }
            Column(
                Modifier.padding(bottom = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(Modifier.size(34.dp), strokeWidth = 3.dp)
                } else {
                    Text(
                        score?.toString() ?: "–",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = if (score != null) Modifier
                            .clickable { onOpenDetail("So entsteht dein Erholungswert", READINESS_SCORE_EXPLANATION) }
                            .semantics { role = Role.Button; contentDescription = "Erholungswert erklären: $score" }
                        else Modifier
                    )
                    Text("von 100", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text(
            when {
                state.isLoading -> "Erholung wird eingeschätzt …"
                score == null -> "Noch nicht bewertbar"
                score >= 80 -> "Günstige Erholungssignale"
                score >= 60 -> "Gemischte Erholungssignale"
                else -> "Erholungssignale beachten"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            "Datenbasis: $count von $READINESS_DATA_SOURCE_COUNT Bereichen verfügbar",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

internal fun recoveryMeterColor(
    score: Int?,
    positive: androidx.compose.ui.graphics.Color,
    caution: androidx.compose.ui.graphics.Color,
    negative: androidx.compose.ui.graphics.Color
): androidx.compose.ui.graphics.Color = when (readinessSignalBand(score)) {
    ReadinessSignalBand.FAVORABLE, null -> positive
    ReadinessSignalBand.CAUTION -> caution
    ReadinessSignalBand.LOW -> negative
}

@Composable
internal fun ReadinessFactsCard(
    state: ReadinessUiState,
    onOpenDetail: (String, String) -> Unit = { _, _ -> }
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(horizontal = 15.dp)) {
            FactRow(
                "HRV / HFV",
                hrvDescription(state),
                readinessIconResource("HRV / HFV"),
                state.isLoading || state.health?.hasHrvData() == true,
                state.health?.hrvLastNightAverageMs?.availability,
                state.health?.hrvLastNightAverageMs?.sourceName,
                detail = hrvDetailIfAvailable(state.health),
                onOpenDetail = onOpenDetail
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            FactRow(
                "Ruhepuls",
                restingHeartRateDescription(state),
                readinessIconResource("Ruhepuls"),
                state.isLoading || state.health?.restingHeartRateBpm?.availability == HealthMetricAvailability.AVAILABLE,
                state.health?.restingHeartRateBpm?.availability,
                state.health?.restingHeartRateBpm?.sourceName,
                detail = healthHistoryDetail(state.health?.restingHeartRateHistory.orEmpty(), "bpm"),
                onOpenDetail = onOpenDetail
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            FactRow(
                "Schlaf & Verlauf",
                sleepAndTrendDescription(state),
                readinessIconResource("Schlaf & Verlauf"),
                state.isLoading || state.health?.sleepMinutes?.availability == HealthMetricAvailability.AVAILABLE,
                state.health?.sleepMinutes?.availability,
                state.health?.sleepMinutes?.sourceName,
                detail = healthHistoryDetail(state.health?.sleepHistory.orEmpty(), "Minuten"),
                onOpenDetail = onOpenDetail
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            FactRow(
                "Trainingsbelastung",
                trainingLoadDescription(state),
                readinessIconResource("Trainingsbelastung"),
                state.isLoading || workoutAvailability(state) == HealthMetricAvailability.AVAILABLE,
                workoutAvailability(state),
                detail = workoutHistoryDetail(state.recentWorkouts),
                onOpenDetail = onOpenDetail
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            FactRow(
                "Erholungszeit",
                recoveryDescription(state),
                readinessIconResource("Erholungszeit"),
                state.isLoading || state.facts?.minutesSinceLastRun != null,
                workoutAvailability(state),
                detail = workoutHistoryDetail(state.recentWorkouts),
                onOpenDetail = onOpenDetail
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            FactRow(
                "Stress & Alltagsaktivität",
                stressAndActivityDescription(state),
                readinessIconResource("Stress & Alltagsaktivität"),
                state.isLoading || state.health?.averageStressLevel?.availability == HealthMetricAvailability.AVAILABLE ||
                    state.health?.steps?.availability == HealthMetricAvailability.AVAILABLE,
                state.health?.averageStressLevel?.availability ?: state.health?.steps?.availability,
                state.health?.averageStressLevel?.sourceName ?: state.health?.steps?.sourceName,
                detail = stressAndActivityHistoryDetail(state),
                onOpenDetail = onOpenDetail
            )
        }
    }
}

@Composable
private fun ReadinessGuidanceCard(state: ReadinessUiState) {
    val guidance = state.guidance
    val (title, recommendation) = when (guidance.kind) {
        de.run.app.domain.model.ReadinessGuidanceKind.LOADING ->
            "Deine Werte werden geladen" to "Sobald sie da sind, zeigen wir dir den passenden nächsten Schritt."
        de.run.app.domain.model.ReadinessGuidanceKind.ACCESS_NEEDED ->
            "Laufdaten fehlen noch" to "Gib deine Läufe frei, damit LAP deinen Trainingsverlauf einbeziehen kann."
        de.run.app.domain.model.ReadinessGuidanceKind.UNAVAILABLE ->
            "Laufdaten gerade nicht verfügbar" to "Versuche es später erneut. Bis dahin findest du deine nächste Einheit im Plan."
        de.run.app.domain.model.ReadinessGuidanceKind.FAILED ->
            "Laufdaten konnten nicht aktualisiert werden" to "Versuche es erneut. Dein bisheriger Trainingsplan bleibt verfügbar."
        de.run.app.domain.model.ReadinessGuidanceKind.NO_RUNS ->
            "Noch kein Lauf synchronisiert" to "Starte deinen Lauf auf der Garmin-Uhr und synchronisiere danach Garmin Connect."
        de.run.app.domain.model.ReadinessGuidanceKind.NO_PLAN ->
            "Kein aktueller Wochenplan" to "Öffne deinen Trainingsplan, um die vorgesehenen Laufeinheiten zu sehen."
        de.run.app.domain.model.ReadinessGuidanceKind.WEEK_COMPLETE ->
            "Deine Laufeinheiten für diese Woche sind erfasst" to "Gönn dir Erholung. Verschobene Einheiten musst du nicht nachholen."
        de.run.app.domain.model.ReadinessGuidanceKind.FOLLOW_PLAN ->
            "${guidance.completedRuns} von ${guidance.plannedRuns} Laufeinheiten erfasst" to
                "Orientiere dich an der nächsten Einheit im Plan und lass die vorgesehenen Erholungstage frei."
    }
    Card(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        shape = RoundedCornerShape(18.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_recommendation),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(top = 2.dp).size(24.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Dein Feedback", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(recommendation, style = MaterialTheme.typography.bodySmall)
                if (!state.isLoading && !hasCompleteHealthSnapshot(state)) {
                    Text(
                        "Schlaf oder Ruhepuls fehlen noch und können hier nicht berücksichtigt werden.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

private fun hasCompleteHealthSnapshot(state: ReadinessUiState): Boolean =
    state.health?.sleepMinutes?.availability == HealthMetricAvailability.AVAILABLE &&
        state.health.restingHeartRateBpm.availability == HealthMetricAvailability.AVAILABLE

@Composable
private fun FactRow(
    title: String,
    value: String,
    icon: Int,
    available: Boolean,
    status: HealthMetricAvailability?,
    source: String? = null,
    detail: String? = null,
    onOpenDetail: (String, String) -> Unit = { _, _ -> }
) {
    val marker = when {
        available -> "✓"
        status == HealthMetricAvailability.PERMISSION_NEEDED || status == HealthMetricAvailability.FAILED -> "×"
        else -> "–"
    }
    val markerColor = when {
        available -> MaterialTheme.colorScheme.primary
        status == HealthMetricAvailability.PERMISSION_NEEDED || status == HealthMetricAvailability.FAILED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val rowModifier = Modifier.fillMaxWidth().heightIn(min = 61.dp).padding(vertical = 8.dp).semantics {
        contentDescription = "$title: $value"
        if (detail != null) role = Role.Button
    }
    Row(
        if (detail != null) rowModifier.clickable { onOpenDetail(title, detail) } else rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(27.dp)
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (available && source != null) {
                Text(source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box(
            Modifier.size(22.dp).clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(marker, color = markerColor, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        if (detail != null) {
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ReadinessNotice(state: ReadinessUiState, onRetry: () -> Unit, onRequestAccess: () -> Unit) {
    val healthNeedsPermission = state.health?.let {
        it.sleepMinutes.availability == HealthMetricAvailability.PERMISSION_NEEDED ||
            it.restingHeartRateBpm.availability == HealthMetricAvailability.PERMISSION_NEEDED
    } == true
    val needsPermission = healthNeedsPermission || state.hasWorkoutAccess == false
    val failed = state.workoutsFailed || state.health?.let {
        it.sleepMinutes.availability == HealthMetricAvailability.FAILED ||
            it.restingHeartRateBpm.availability == HealthMetricAvailability.FAILED
    } == true || state.healthReadFailed
    val unavailable = state.workoutsUnavailable || state.health?.let {
        it.sleepMinutes.availability == HealthMetricAvailability.UNAVAILABLE ||
            it.restingHeartRateBpm.availability == HealthMetricAvailability.UNAVAILABLE
    } == true
    when {
        state.isLoading -> Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text("Deine Werte werden aktualisiert …", style = MaterialTheme.typography.bodySmall)
        }
        needsPermission -> Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Gib Schlaf, Ruhepuls und Läufe frei, um sie hier zu sehen.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onRequestAccess) { Text("Freigeben") }
        }
        failed || unavailable -> Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (unavailable) "Gesundheitsdaten sind gerade nicht verfügbar." else "Deine Werte konnten nicht aktualisiert werden.",
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(onClick = onRetry) { Text("Erneut") }
        }
    }
}

@Composable
private fun DemoDataControl(isDemo: Boolean, onToggle: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Emulator-Test", style = MaterialTheme.typography.titleSmall)
            Text(
                if (isDemo) "Beispielwerte und Testläufe werden angezeigt."
                else "Damit kannst du eine verbundene Uhr und synchronisierte Läufe ansehen.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(onClick = onToggle) {
                Text(if (isDemo) "Demodaten ausschalten" else "Demodaten einschalten")
            }
        }
    }
}

private fun sleepDescription(metric: HealthMetric<Int>): String = metric.value?.let { minutes ->
    when (metric.availability) {
        HealthMetricAvailability.AVAILABLE -> "${minutes / 60} Std. ${minutes % 60} Min."
        else -> metricAvailabilityText(metric.availability)
    }
} ?: metricAvailabilityText(metric.availability)

private fun sleepAndTrendDescription(state: ReadinessUiState): String {
    val sleep = state.health?.sleepMinutes ?: return loadingOrMissing(state)
    val value = sleepDescription(sleep)
    if (sleep.availability != HealthMetricAvailability.AVAILABLE) return value
    val baseline = personalMedian(state.health.sleepHistory)
    return if (baseline == null) "$value · persönlicher Verlauf noch zu kurz"
    else "$value · persönlicher Vergleich: ${baseline / 60} Std. ${baseline % 60} Min."
}

private fun restingHeartRateDescription(state: ReadinessUiState): String {
    val metric = state.health?.restingHeartRateBpm ?: return loadingOrMissing(state)
    val value = heartRateDescription(metric)
    if (metric.availability != HealthMetricAvailability.AVAILABLE) return value
    val band = ReadinessAssessmentAnalyzer().restingHeartRatePersonalBand(
        metric.value,
        state.health.restingHeartRateHistory,
        java.time.LocalDate.now()
    )
    val personalContext = when (band) {
        RestingHeartRatePersonalBand.BELOW_PERSONAL_RANGE -> "unter deinem persönlichen Normalbereich"
        RestingHeartRatePersonalBand.WITHIN_PERSONAL_RANGE -> "im persönlichen Normalbereich"
        RestingHeartRatePersonalBand.ABOVE_PERSONAL_RANGE -> "über deinem persönlichen Normalbereich"
        RestingHeartRatePersonalBand.WELL_ABOVE_PERSONAL_RANGE -> "deutlich über deinem persönlichen Normalbereich"
        null -> "persönlicher Vergleich noch nicht möglich"
    }
    return metric.value?.let { "$it bpm · $personalContext" } ?: value
}

private fun hrvDescription(state: ReadinessUiState): String {
    val health = state.health ?: return loadingOrMissing(state)
    val value = health.hrvLastNightAverageMs.value
    val status = health.hrvStatus.value?.let(::hrvStatusText)
    if (value == null && status == null) return readinessMetricAvailabilityText(health.hrvLastNightAverageMs.availability)
    val reading = value?.let { "$it ms letzte Nacht" }
    val baseline = personalMedian(health.hrvHistory)?.let { "persönlicher Schnitt $it ms" }
    return listOfNotNull(status, reading, baseline).joinToString(" · ")
}

private fun hrvDetailIfAvailable(health: de.run.app.domain.model.ReadinessHealthSnapshot?): String? {
    health ?: return null
    return healthHistoryDetail(health.hrvHistory, "ms")
        ?: health.hrvLastNightAverageMs.value
            ?.takeIf { health.hrvLastNightAverageMs.availability == HealthMetricAvailability.AVAILABLE }
            ?.let { "$it ms" }
}

private fun hrvStatusText(status: String): String = when (status.uppercase()) {
    "BALANCED" -> "Ausgeglichen"
    "UNBALANCED" -> "Außerhalb deines Bereichs"
    "LOW" -> "Niedrig"
    "NONE" -> "Noch kein Status"
    else -> "Garmin-Status: $status"
}

private fun stressAndActivityDescription(state: ReadinessUiState): String {
    val health = state.health ?: return loadingOrMissing(state)
    val stress = health.averageStressLevel.value?.let { "Stress Ø $it/100" }
    val stressBaseline = if (stress != null) personalMedian(health.stressHistory)?.let { "dein Schnitt $it" } else null
    val steps = health.steps.value?.let { "${"%,d".format(java.util.Locale.GERMANY, it)} Schritte" }
    val stepBaseline = if (steps != null) personalMedian(health.stepsHistory)?.let {
        "dein Schnitt ${"%,d".format(java.util.Locale.GERMANY, it)}"
    } else null
    val details = listOfNotNull(stress, stressBaseline, steps, stepBaseline)
    return details.takeIf { it.isNotEmpty() }?.joinToString(" · ")
        ?: readinessMetricAvailabilityText(health.averageStressLevel.availability)
}

private fun readinessMetricAvailabilityText(availability: HealthMetricAvailability): String = when (availability) {
    HealthMetricAvailability.UNAVAILABLE -> "Nicht verfügbar"
    else -> metricAvailabilityText(availability)
}

private fun stressAndActivityHistoryDetail(state: ReadinessUiState): String? {
    val health = state.health ?: return null
    val stress = healthHistoryDetail(health.stressHistory, "Stress")
    val steps = healthHistoryDetail(health.stepsHistory, "Schritte")
    return listOfNotNull(stress, steps).joinToString("\n\n").takeIf(String::isNotBlank)
}

private fun heartRateDescription(metric: HealthMetric<Int>): String = metric.value?.let {
    if (metric.availability == HealthMetricAvailability.AVAILABLE) "$it Schläge pro Minute"
    else metricAvailabilityText(metric.availability)
} ?: metricAvailabilityText(metric.availability)

private fun metricAvailabilityText(availability: HealthMetricAvailability): String = when (availability) {
    HealthMetricAvailability.AVAILABLE -> "Wert verfügbar"
    HealthMetricAvailability.NO_DATA -> "Noch keine Daten"
    HealthMetricAvailability.PERMISSION_NEEDED -> "Freigabe fehlt"
    HealthMetricAvailability.UNAVAILABLE -> "Gerade nicht verfügbar"
    HealthMetricAvailability.FAILED -> "Konnte nicht geladen werden"
}

private fun loadingOrMissing(state: ReadinessUiState): String = when {
    state.isLoading -> "Wird geladen …"
    state.healthReadFailed -> "Konnte nicht geladen werden"
    else -> "Noch keine Daten"
}

private fun recoveryDescription(state: ReadinessUiState): String {
    if (state.isLoading) return "Wird geladen …"
    when {
        state.hasWorkoutAccess == false -> return "Freigabe fehlt"
        state.workoutsUnavailable -> return "Gerade nicht verfügbar"
        state.workoutsFailed -> return "Konnte nicht geladen werden"
    }
    val elapsed = state.facts?.minutesSinceLastRun ?: return "Keine Laufeinheit erfasst"
    val days = elapsed / MINUTES_PER_DAY
    val hours = (elapsed % MINUTES_PER_DAY) / 60
    val elapsedText = when {
        days > 0 -> "$days T. $hours Std."
        hours > 0 -> "$hours Std."
        else -> "${elapsed.coerceAtLeast(1)} Min."
    }
    val lastRun = state.recentWorkouts.filter(Workout::isRunning).maxByOrNull(Workout::endedAtMillis)
    val duration = lastRun?.durationSeconds?.takeIf { it > 0 }?.let { " · ${it / 60} Min. Laufbelastung" }.orEmpty()
    return "Letzter Lauf vor $elapsedText$duration"
}

private fun trainingLoadDescription(state: ReadinessUiState): String {
    if (state.isLoading) return "Wird geladen …"
    workoutAvailability(state)?.takeIf { it != HealthMetricAvailability.AVAILABLE }?.let(::metricAvailabilityText)?.let { return it }
    val now = Instant.now().toEpochMilli()
    val recent = state.recentWorkouts.filter(Workout::isRunning).filter {
        it.endedAtMillis >= now - Duration.ofDays(7).toMillis() && it.endedAtMillis <= now
    }
    if (recent.isEmpty()) return "Letzte 7 Tage: keine Läufe erfasst"
    val minutes = recent.sumOf(Workout::durationSeconds) / 60
    val distance = recent.mapNotNull(Workout::distanceMeters).sum().takeIf { it > 0.0 }
        ?.let { " · ${"%.1f".format(java.util.Locale.GERMANY, it / 1_000.0)} km" }.orEmpty()
    return "Letzte 7 Tage: ${recent.size} Läufe · $minutes Min.$distance"
}

internal fun personalMedian(values: List<de.run.app.domain.model.DailyHealthValue>): Int? {
    val yesterday = java.time.LocalDate.now().minusDays(1)
    val cutoff = yesterday.minusDays(27)
    val recent = values.filter { it.value > 0 && !it.date.isAfter(yesterday) && !it.date.isBefore(cutoff) }
    val sorted = recent.map { it.value }.sorted()
    if (sorted.size < 14) return null
    val firstDate = recent.minOfOrNull { it.date } ?: return null
    if (firstDate.isAfter(yesterday.minusDays(20))) return null
    return if (sorted.size % 2 == 1) sorted[sorted.size / 2]
    else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
}

private fun healthHistoryDetail(
    history: List<de.run.app.domain.model.DailyHealthValue>,
    unit: String
): String? = history.takeIf { it.isNotEmpty() }?.sortedByDescending { it.date }?.take(14)?.joinToString("\n") {
    "${it.date}: ${it.value} $unit"
}

private fun workoutHistoryDetail(workouts: List<Workout>): String? = workouts
    .filter(Workout::isRunning)
    .sortedByDescending(Workout::endedAtMillis)
    .take(10)
    .takeIf { it.isNotEmpty() }
    ?.joinToString("\n") { workout ->
        val date = Instant.ofEpochMilli(workout.endedAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        val distance = workout.distanceMeters?.let { " · ${"%.1f".format(java.util.Locale.GERMANY, it / 1_000.0)} km" }.orEmpty()
        "$date · ${workout.durationSeconds / 60} Min.$distance"
    }

private fun weeklyDescription(state: ReadinessUiState): String {
    if (state.isLoading) return "Wird geladen …"
    when {
        state.hasWorkoutAccess == false -> return "Freigabe fehlt"
        state.workoutsUnavailable -> return "Gerade nicht verfügbar"
        state.workoutsFailed -> return "Konnte nicht geladen werden"
    }
    val facts = state.facts ?: return "Noch keine Daten"
    val distanceKm = facts.distanceThisWeekMeters / 1_000.0
    return "${facts.runsThisWeek} Läufe · ${"%.1f".format(java.util.Locale.GERMANY, distanceKm)} km"
}

private fun workoutAvailability(state: ReadinessUiState): HealthMetricAvailability? = when {
    state.hasWorkoutAccess == true -> HealthMetricAvailability.AVAILABLE
    state.hasWorkoutAccess == false -> HealthMetricAvailability.PERMISSION_NEEDED
    state.workoutsUnavailable -> HealthMetricAvailability.UNAVAILABLE
    state.workoutsFailed -> HealthMetricAvailability.FAILED
    else -> null
}

private const val MINUTES_PER_DAY = 1_440L
private const val READINESS_SCORE_EXPLANATION =
    "Dein Schlaf, deine Herzwerte und deine Laufbelastung bestimmen deinen Erholungswert.\n\n" +
        "Je höher der Wert, desto besser deine geschätzte Erholung.\n\n" +
        "Der Wert ist eine Einschätzung, keine exakte Messung."

private val READINESS_POPUP_TITLES = setOf(
    "HRV / HFV",
    "Ruhepuls",
    "Schlaf & Verlauf",
    "Trainingsbelastung",
    "Erholungszeit",
    "Stress & Alltagsaktivität"
)

internal fun readinessIconResource(title: String): Int = when (title) {
    "HRV / HFV" -> R.drawable.readiness_hrv
    "Ruhepuls" -> R.drawable.readiness_resting_heart_rate
    "Schlaf & Verlauf" -> R.drawable.readiness_sleep
    "Trainingsbelastung" -> R.drawable.readiness_training_load
    "Erholungszeit" -> R.drawable.readiness_recovery
    else -> R.drawable.readiness_stress
}
