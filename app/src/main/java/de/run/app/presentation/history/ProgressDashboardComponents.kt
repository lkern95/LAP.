package de.run.app.presentation.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.run.app.domain.ProgressDistanceBucket
import de.run.app.domain.ProgressPeriod
import de.run.app.domain.TrainingProgressDashboard
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

@Composable
internal fun PeriodSelector(selected: ProgressPeriod, onSelect: (ProgressPeriod) -> Unit) {
    val periods = ProgressPeriod.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        periods.forEachIndexed { index, period ->
            SegmentedButton(
                selected = selected == period,
                onClick = { onSelect(period) },
                shape = SegmentedButtonDefaults.itemShape(index, periods.size),
                label = { Text(period.label) }
            )
        }
    }
}

@Composable
internal fun ProgressVolumeCard(dashboard: TrainingProgressDashboard?, loading: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Trainingsumfang", style = MaterialTheme.typography.labelLarge)
            when {
                loading -> LoadingVolume()
                dashboard == null || dashboard.runCount == 0 -> {
                    Text("Noch keine Läufe in diesem Zeitraum.", style = MaterialTheme.typography.titleMedium)
                    Text("Synchronisiere deine Garmin-Uhr in Garmin Connect.", style = MaterialTheme.typography.bodySmall)
                    DistanceBarChart(emptyList())
                }
                dashboard.distanceMeters <= 0.0 -> {
                    Text("Für diese Läufe liegt keine Streckenangabe vor.", style = MaterialTheme.typography.titleMedium)
                    Text("Sobald Garmin Connect die Strecke teilt, erscheint sie hier.", style = MaterialTheme.typography.bodySmall)
                    DistanceBarChart(emptyList())
                }
                else -> {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "${formatKilometers(dashboard.distanceMeters)} km",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        ComparisonText(dashboard)
                    }
                    Text(
                        periodDescription(dashboard),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    DistanceBarChart(dashboard.chartBuckets, dashboard.period)
                }
            }
        }
    }
}

@Composable
private fun LoadingVolume() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Text("Deine Läufe werden geladen.", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ComparisonText(dashboard: TrainingProgressDashboard) {
    val previous = dashboard.previousDistanceMeters
    val label = when {
        previous == null -> "Gesamter Verlauf"
        previous <= 0.0 && dashboard.distanceMeters > 0.0 -> "Erste Läufe"
        previous <= 0.0 -> "Noch kein Vergleich"
        else -> {
            val change = ((dashboard.distanceMeters - previous) / previous * 100).toInt()
            "%+d%%".format(Locale.GERMANY, change)
        }
    }
    val color = if (previous != null && previous > 0.0 && dashboard.distanceMeters < previous) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else MaterialTheme.colorScheme.primary
    Text(label, color = color, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun DistanceBarChart(buckets: List<ProgressDistanceBucket>, period: ProgressPeriod? = null) {
    val values = buckets.map(ProgressDistanceBucket::distanceMeters)
    val maximum = values.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
    val axisMaximum = max(ceil(maximum / 10_000.0) * 10_000.0, 10_000.0)
    val labels = axisLabels(buckets, period)
    val chartDescription = if (buckets.isEmpty()) "Noch keine Laufdaten im Diagramm" else {
        "Kilometer je Zeitraum: ${buckets.joinToString { "${it.label} ${formatKilometers(it.distanceMeters)} Kilometer" }}"
    }
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val barColor = MaterialTheme.colorScheme.primary

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier.width(26.dp).height(106.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
        ) {
            Text(formatAxisKilometers(axisMaximum), style = MaterialTheme.typography.labelSmall)
            Text(formatAxisKilometers(axisMaximum / 2.0), style = MaterialTheme.typography.labelSmall)
            Text("0", style = MaterialTheme.typography.labelSmall)
        }
        Column(Modifier.weight(1f)) {
            Canvas(Modifier.fillMaxWidth().height(90.dp).semantics { contentDescription = chartDescription }) {
                val slotWidth = size.width / max(values.size, 1)
                val barWidth = minOf(slotWidth * 0.8f, BAR_WIDTH.toPx())
                val chartHeight = size.height
                for (line in 0..2) {
                    val y = chartHeight * line / 2f
                    drawLine(
                        gridColor,
                        androidx.compose.ui.geometry.Offset(0f, y),
                        androidx.compose.ui.geometry.Offset(size.width, y),
                        1.dp.toPx()
                    )
                }
                values.forEachIndexed { index, value ->
                    if (value > 0.0) {
                        val height = (value / axisMaximum * chartHeight).toFloat().coerceAtLeast(2.dp.toPx())
                        val left = index * slotWidth + (slotWidth - barWidth) / 2f
                        drawRoundRect(
                            color = barColor,
                            topLeft = androidx.compose.ui.geometry.Offset(left, chartHeight - height),
                            size = androidx.compose.ui.geometry.Size(barWidth, height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
                        )
                    }
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth().height(18.dp)) {
                labels.forEach { (index, label) ->
                    Text(
                        label,
                        modifier = Modifier
                            .width(AXIS_LABEL_WIDTH)
                            .offset(x = maxWidth * ((index + 0.5f) / max(buckets.size, 1)) - AXIS_LABEL_WIDTH / 2),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun axisLabels(
    buckets: List<ProgressDistanceBucket>,
    period: ProgressPeriod?
): List<Pair<Int, String>> {
    if (buckets.isEmpty()) return emptyList()
    if (buckets.size <= 7) return buckets.mapIndexed { index, bucket ->
        index to bucket.label.removeSuffix(".")
    }
    if (period == ProgressPeriod.MONTH) {
        val indexes = (0 until buckets.size step MONTH_LABEL_INTERVAL_DAYS)
            .toMutableList()
            .apply { if (buckets.lastIndex - last() > 1) add(buckets.lastIndex) }
        return indexes.map { index -> index to buckets[index].label }
    }
    val indexes = listOf(0, buckets.lastIndex / 4, buckets.lastIndex / 2, buckets.lastIndex * 3 / 4, buckets.lastIndex).distinct()
    return indexes.map { index -> index to buckets[index].label }
}

private val BAR_WIDTH = 4.dp
private val AXIS_LABEL_WIDTH = 28.dp
private const val MONTH_LABEL_INTERVAL_DAYS = 2

@Composable
internal fun ProgressHighlights(dashboard: TrainingProgressDashboard?) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricHighlight(
            title = "HM-Vorbereitung",
            value = dashboard?.longestRecentRunMeters?.let { "${formatKilometers(it)} km" } ?: "–",
            detail = "längster Lauf · letzte 12 Wochen",
            trend = dashboard?.weeklyLongestRunMeters.orEmpty(),
            modifier = Modifier.weight(1f)
        )
        MetricHighlight(
            title = "Lauftempo",
            value = dashboard?.averagePaceSecondsPerKilometer?.let(::formatPace) ?: "–",
            detail = "Minuten pro Kilometer",
            trend = dashboard?.weeklyAveragePaceSecondsPerKilometer.orEmpty().filterNotNull().map(Long::toDouble),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MetricHighlight(
    title: String,
    value: String,
    detail: String,
    trend: List<Double>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MiniTrend(trend)
        }
    }
}

@Composable
private fun MiniTrend(values: List<Double>) {
    val color = MaterialTheme.colorScheme.tertiary
    Canvas(Modifier.fillMaxWidth().height(30.dp)) {
        if (values.size < 2 || values.maxOrNull() == 0.0) return@Canvas
        val minimum = values.minOrNull() ?: 0.0
        val maximum = values.maxOrNull() ?: 1.0
        val spread = (maximum - minimum).takeIf { it > 0.0 } ?: 1.0
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = size.width * index / values.lastIndex.toFloat()
            val y = size.height - ((value - minimum) / spread * (size.height - 4.dp.toPx())).toFloat() - 2.dp.toPx()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
internal fun DataStatusCard(state: WorkoutHistoryUiState, onRequestAccess: () -> Unit, onRetry: () -> Unit) {
    val permissionMissing = state.accessMessage != null && !state.isUnavailable
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                when {
                    permissionMissing -> "Freigabe fehlt"
                    state.isUnavailable -> "Laufdaten gerade nicht verfügbar"
                    else -> "Läufe konnten nicht aktualisiert werden"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                when {
                    permissionMissing -> "Erlaube den Zugriff auf deine Läufe, damit der Fortschritt aktuell bleibt."
                    state.isUnavailable -> "Deine gespeicherten Läufe und der Trainingsplan bleiben erhalten."
                    else -> "Deine gespeicherten Läufe bleiben sichtbar. Versuche es später noch einmal."
                },
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (permissionMissing) Button(onClick = onRequestAccess) { Text("Freigabe einrichten") }
                OutlinedButton(onClick = onRetry) { Text("Erneut versuchen") }
            }
        }
    }
}

@Composable
internal fun EmptyRunsCard(permissionMissing: Boolean, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Noch keine Läufe gefunden.", style = MaterialTheme.typography.titleMedium)
            Text("Starte deinen Lauf auf der Garmin-Uhr und synchronisiere danach Garmin Connect.")
            if (!permissionMissing) OutlinedButton(onClick = onRetry) { Text("Läufe aktualisieren") }
        }
    }
}

@Composable
internal fun LoadingState() {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text("Deine Läufe werden geladen.")
    }
}

private fun periodDescription(dashboard: TrainingProgressDashboard): String = when (dashboard.period) {
    ProgressPeriod.WEEK -> "${dateShort(dashboard.chartBuckets.firstOrNull()?.startsOn)} – ${dateShort(dashboard.chartBuckets.lastOrNull()?.startsOn)} · ${dashboard.runCount} Läufe"
    ProgressPeriod.MONTH -> "${dashboard.runCount} Läufe in diesem Monat"
    ProgressPeriod.TOTAL -> "${dashboard.runCount} Läufe · letzte 12 Monate im Diagramm"
}

private fun dateShort(date: java.time.LocalDate?): String =
    date?.format(DateTimeFormatter.ofPattern("d. MMM", Locale.GERMAN)).orEmpty()

private fun formatAxisKilometers(meters: Double): String = (meters / 1_000.0).toInt().toString()
