package de.run.app.presentation.readiness

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.run.app.BuildConfig
import de.run.app.R
import de.run.app.data.demo.DebugDemoModeController
import de.run.app.domain.model.HealthMetric
import de.run.app.domain.model.HealthMetricAvailability
import de.run.app.domain.model.ReadinessFacts
import de.run.app.domain.model.Workout
import java.time.Duration

@Composable
fun ReadinessScreen(
    viewModel: ReadinessViewModel,
    debugDemoMode: DebugDemoModeController?,
    onRequestAccess: () -> Unit,
    onDemoModeChanged: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val isDemo by (debugDemoMode?.enabled ?: kotlinx.coroutines.flow.MutableStateFlow(false)).collectAsState()
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 8.dp)) {
        ReadinessHeader(onBack, isDemo)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ReadinessMeter(state)
            ReadinessFactsCard(state)
            ReadinessGuidanceCard(state)
            ReadinessNotice(state, viewModel::refresh, onRequestAccess)
            if (BuildConfig.DEBUG && debugDemoMode != null) {
                DemoDataControl(isDemo) { onDemoModeChanged(!isDemo) }
            }
        }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Text("Alles passt")
        }
    }
}

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
private fun ReadinessMeter(state: ReadinessUiState) {
    val count = state.availableFactCount()
    val targetFraction = if (state.isLoading) 0f else count / 4f
    val animatedFraction = remember { Animatable(0f) }
    LaunchedEffect(count, state.isLoading) {
        if (state.isLoading) {
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
    val progressColor = MaterialTheme.colorScheme.primary
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(Modifier.size(width = 230.dp, height = 124.dp), contentAlignment = Alignment.BottomCenter) {
            Canvas(Modifier.fillMaxSize().semantics {
                contentDescription = if (state.isLoading) "Trainingswerte werden geladen" else "$count von 4 Werten verfügbar"
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
                    Text("$count", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                    Text("von 4", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text(
            when {
                state.isLoading -> "Deine Werte werden geladen"
                count == 0 -> "Noch keine Angaben verfügbar"
                count == 4 -> "Alle Angaben verfügbar"
                else -> "$count von 4 Angaben verfügbar"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            "Trainingswerte für heute",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ReadinessFactsCard(state: ReadinessUiState) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(horizontal = 15.dp)) {
            FactRow(
                "Schlaf",
                state.health?.sleepMinutes?.let(::sleepDescription) ?: loadingOrMissing(state),
                R.drawable.run_training_regeneration,
                state.isLoading || state.health?.sleepMinutes?.availability == HealthMetricAvailability.AVAILABLE,
                state.health?.sleepMinutes?.availability,
                state.health?.sleepMinutes?.sourceName
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            FactRow(
                "Ruhepuls",
                state.health?.restingHeartRateBpm?.let(::heartRateDescription) ?: loadingOrMissing(state),
                R.drawable.run_health_bpm,
                state.isLoading || state.health?.restingHeartRateBpm?.availability == HealthMetricAvailability.AVAILABLE,
                state.health?.restingHeartRateBpm?.availability,
                state.health?.restingHeartRateBpm?.sourceName
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            FactRow(
                "Zeit seit dem letzten Lauf",
                recoveryDescription(state),
                R.drawable.run_training_relaxed,
                state.isLoading || state.facts?.minutesSinceLastRun != null,
                workoutAvailability(state)
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            FactRow(
                "Läufe diese Woche",
                weeklyDescription(state),
                R.drawable.run_training_run,
                state.isLoading || state.hasWorkoutAccess == true,
                workoutAvailability(state)
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
    source: String? = null
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
    Row(
        Modifier.fillMaxWidth().height(61.dp).semantics {
            contentDescription = "$title: $value"
        },
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
    val elapsed = state.facts?.minutesSinceLastRun ?: return "Noch kein Lauf angekommen"
    val days = elapsed / MINUTES_PER_DAY
    val hours = (elapsed % MINUTES_PER_DAY) / 60
    return when {
        days > 0 -> "Vor $days T. $hours Std."
        hours > 0 -> "Vor $hours Std."
        else -> "Vor ${elapsed.coerceAtLeast(1)} Min."
    }
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
