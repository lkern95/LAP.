package de.run.app.presentation.readiness

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import de.run.app.R
import de.run.app.domain.GarminStressBand
import de.run.app.domain.ReadinessSignalBand
import de.run.app.domain.RecoveryIntervalBand
import de.run.app.domain.garminStressBand
import de.run.app.domain.readinessSignalBand
import de.run.app.domain.recoveryIntervalBand
import de.run.app.domain.model.HealthMetricAvailability
import de.run.app.presentation.theme.LocalCautionColor

@Composable
internal fun ReadinessDataPopup(title: String, state: ReadinessUiState) {
    val series = remember(title, state.health, state.recentWorkouts, state.facts) {
        readinessSeries(title, state)
    }
    if (title == "HRV / HFV") {
        HrvDetailPopup(series.singleOrNull(), state)
        return
    }
    StandardReadinessPopup(title, series, state)
}

@Composable
private fun StandardReadinessPopup(title: String, series: List<ReadinessSeries>, state: ReadinessUiState) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(popupIntroduction(title), style = MaterialTheme.typography.bodyMedium)
        series.forEachIndexed { index, item ->
            ReadinessSeriesSection(
                item,
                readinessIconResource(title).takeIf { index == 0 },
                readinessMetricBadge(item, state)
            )
        }
        InformationCard(popupCaution(title), compact = true)
    }
}

@Composable
private fun HrvDetailPopup(series: ReadinessSeries?, state: ReadinessUiState) {
    val points = remember(series) { series?.let { lastFourteenDays(it.values, it.currentValue) }.orEmpty() }
    val statistics = remember(series) { series?.let { lastTwentyEightDays(it.values, it.currentValue) }.orEmpty() }
    val personalRange = remember(state.health?.hrvHistory) {
        hrvPersonalRange(state.health?.hrvHistory.orEmpty())
    }
    val measurements = points.filter { it.value > 0 }
    val comparison = remember(state.health?.hrvHistory, series?.currentValue) {
        hrvAverageComparison(state.health?.hrvHistory.orEmpty(), series?.currentValue)
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(popupIntroduction("HRV / HFV"), style = MaterialTheme.typography.bodyMedium)
        ReadinessValueCard(
            currentLabel = series?.currentLabel ?: "Heute",
            currentDisplay = series?.currentDisplay ?: "Nicht verfügbar",
            backgroundIcon = R.drawable.readiness_hrv,
            comparison = comparison,
            status = hrvPopupStatus(state)?.let { ReadinessBadge(it, hrvStatusTone(it)) }
        )
        if (measurements.isNotEmpty()) {
            ReadinessTrendChart(
                seriesName = "HRV / HFV",
                points = points,
                backgroundIcon = null,
                normalRange = personalRange,
                chartHeight = 112.dp,
                showTitle = false,
                lineWidth = 1.5.dp,
                pointRadius = 2.5.dp,
                highlightToday = true,
                autoScale = true
            )
            DateAxisLabels()
            Legend("Deine HRV", personalRange != null)
            if (series?.showStatistics == true && statistics.size >= 3) {
                StatisticsCard(series, statistics, periodDays = 28, denseRows = true)
            }
        } else {
            HrvEmptyChart()
        }
        InformationCard(
            if (personalRange != null) {
                "Je höher deine HRV im Vergleich zu deinem persönlichen Bereich, desto besser ist meist deine Erholung."
            } else {
                "Einzelne HRV-Werte sagen nicht, was für dich normal ist."
            },
            compact = true
        )
        if (measurements.isNotEmpty()) series?.let { HrvDailyValuesButton(it, measurements) }
    }
}

@Composable
private fun ReadinessValueCard(
    currentLabel: String,
    currentDisplay: String,
    backgroundIcon: Int,
    comparison: HrvAverageComparison? = null,
    status: ReadinessBadge? = null,
    supporting: (@Composable () -> Unit)? = null
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Box(Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(backgroundIcon),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.align(Alignment.Center).size(112.dp).alpha(0.16f)
            )
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(currentLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    status?.let { ReadinessStatusBadge(it) }
                }
                Text(
                    currentDisplay,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (comparison != null) HrvAverageComparisonRow(comparison)
                else supporting?.invoke()
            }
        }
    }
}

@Composable
private fun HrvAverageComparisonRow(comparison: HrvAverageComparison) {
    val difference = comparison.differenceMs
    if (difference == 0) {
        Text("Unverändert", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        return
    }
    val isPositive = difference > 0
    val accent = if (isPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val content = if (isPositive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onError
    val formattedDifference = if (isPositive) "+$difference ms" else "−${kotlin.math.abs(difference)} ms"
    Row(
        Modifier.fillMaxWidth().semantics {
            contentDescription = if (isPositive) "Trend nach oben: $formattedDifference im Vergleich zu deinem Durchschnitt"
            else "Trend nach unten: $formattedDifference im Vergleich zu deinem Durchschnitt"
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(color = accent, contentColor = content, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.size(22.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text(if (isPositive) "↗" else "↘", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Text(formattedDifference, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = accent)
            Text("im Vergleich zu deinem Durchschnitt", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

internal data class HrvAverageComparison(val differenceMs: Int)

internal fun hrvAverageComparison(history: List<de.run.app.domain.model.DailyHealthValue>, currentValue: Int?): HrvAverageComparison? {
    val current = currentValue?.takeIf { it > 0 } ?: return null
    if (personalMedian(history) == null) return null
    val today = java.time.LocalDate.now()
    val baseline = history.asSequence()
        .filter { it.value > 0 && !it.date.isBefore(today.minusDays(28)) && it.date.isBefore(today) }
        .map { it.value }
        .toList()
    if (baseline.isEmpty()) return null
    return HrvAverageComparison(current - baseline.average().roundToInt())
}

@Composable
private fun ReadinessStatusBadge(badge: ReadinessBadge) {
    val cautionColor = LocalCautionColor.current
    val (containerColor, contentColor) = when (badge.tone) {
        HrvStatusTone.GOOD -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        HrvStatusTone.CAUTION -> cautionColor to MaterialTheme.colorScheme.onPrimary
        HrvStatusTone.LOW -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
        HrvStatusTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("●", style = MaterialTheme.typography.labelLarge)
            Text(badge.label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

internal enum class HrvStatusTone { GOOD, CAUTION, LOW, NEUTRAL }

private data class ReadinessBadge(val label: String, val tone: HrvStatusTone)

internal fun hrvStatusTone(status: String): HrvStatusTone = when (status) {
    "Im Normalbereich" -> HrvStatusTone.GOOD
    "Abweichend" -> HrvStatusTone.CAUTION
    "Niedrig" -> HrvStatusTone.LOW
    else -> HrvStatusTone.NEUTRAL
}

private fun hrvPopupStatus(state: ReadinessUiState): String? {
    val health = state.health ?: return null
    if (personalMedian(health.hrvHistory) == null) return null
    val metric = health.hrvStatus
    if (metric.availability != de.run.app.domain.model.HealthMetricAvailability.AVAILABLE) return null
    return when (metric.value?.uppercase()) {
        "BALANCED" -> "Im Normalbereich"
        "UNBALANCED" -> "Abweichend"
        "LOW" -> "Niedrig"
        else -> null
    }
}

private fun hrvPersonalRange(history: List<de.run.app.domain.model.DailyHealthValue>): IntRange? {
    if (personalMedian(history) == null) return null
    val today = java.time.LocalDate.now()
    val firstDay = today.minusDays(28)
    val values = history.asSequence()
        .filter { it.value > 0 && !it.date.isBefore(firstDay) && it.date.isBefore(today) }
        .map { it.value }
        .sorted()
        .toList()
    if (values.size < 14) return null
    val lastIndex = values.lastIndex
    val lower = values[(lastIndex * 10) / 100]
    val upper = values[(lastIndex * 90 + 99) / 100]
    return if (lower < upper) lower..upper else null
}

@Composable
private fun HrvEmptyChart() {
    Box(Modifier.fillMaxWidth().height(164.dp), contentAlignment = Alignment.Center) {
        BackgroundReadinessIcon(readinessIconResource("HRV / HFV"))
        Text("Noch keine Messwerte für den Verlauf gespeichert.",
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HrvDailyValuesButton(series: ReadinessSeries, measurements: List<de.run.app.domain.model.DailyHealthValue>) {
    var showAllValues by remember { mutableStateOf(false) }
    TextButton(onClick = { showAllValues = !showAllValues }, modifier = Modifier.fillMaxWidth()) {
        Text(if (showAllValues) "Werte ausblenden" else "Alle Werte anzeigen")
    }
    if (showAllValues) StoredValues(series, measurements)
}

@Composable
private fun ReadinessSeriesSection(series: ReadinessSeries, backgroundIcon: Int?, badge: ReadinessBadge?) {
    var showAllValues by remember(series.name) { mutableStateOf(false) }
    val points = remember(series) { lastFourteenDays(series.values, series.currentValue) }
    val measurements = points.filter { it.value > 0 }
    val latest = measurements.lastOrNull()
    val previous = measurements.dropLast(1).lastOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ReadinessValueCard(
            currentLabel = series.currentLabel,
            currentDisplay = series.currentDisplay,
            backgroundIcon = backgroundIcon ?: readinessIconResource(series.accessibilityName),
            status = badge,
            supporting = {
                val trend = series.trendLabel ?: if (series.showTrend && latest != null && previous != null) {
                    trendLabel(latest.value, previous.value, series.valueKind)
                } else null
                trend?.let { TrendSummary(it) }
            }
        )
        if (measurements.isNotEmpty()) {
            ReadinessTrendChart(
                seriesName = series.name.ifBlank { series.accessibilityName },
                points = points,
                backgroundIcon = null,
                chartHeight = 112.dp,
                showTitle = false,
                lineWidth = 1.5.dp,
                pointRadius = 2.5.dp,
                highlightToday = true,
                autoScale = true
            )
            DateAxisLabels()
            Legend(series.name.ifBlank { series.accessibilityName })
            if (series.showStatistics && measurements.size >= 3) {
                StatisticsCard(series, measurements, periodDays = 14, denseRows = true)
            }
            TextButton(onClick = { showAllValues = !showAllValues }, modifier = Modifier.fillMaxWidth()) {
                Text(if (showAllValues) "Werte ausblenden" else "Alle Werte anzeigen")
            }
            if (showAllValues) StoredValues(series, measurements)
        } else {
            Box(Modifier.fillMaxWidth().height(112.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Noch keine Messwerte für den Verlauf gespeichert.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun readinessMetricBadge(series: ReadinessSeries, state: ReadinessUiState): ReadinessBadge? = when (series.valueKind) {
    ReadinessValueKind.HRV -> hrvPopupStatus(state)?.let { ReadinessBadge(it, hrvStatusTone(it)) }
    ReadinessValueKind.HEART_RATE -> {
        if (state.assessment.autonomicSignal != "Ruhepuls") null
        else state.assessment.autonomicScore?.let { scoreBadge(it, "Im persönlichen Bereich", "Erhöht", "Deutlich erhöht") }
    }
    ReadinessValueKind.SLEEP -> state.assessment.sleepScore?.let {
        scoreBadge(it, "Nicht verkürzt", "Kürzer als üblich", "Deutlich kürzer")
    }
    ReadinessValueKind.STRESS -> stressBadge(state)
    ReadinessValueKind.MINUTES -> if (series.currentLabel == "Seit dem letzten Lauf") {
        recoveryIntervalBadge(state.facts?.minutesSinceLastRun)
    } else if (series.values.isEmpty()) null else state.assessment.trainingLoadScore?.let {
        scoreBadge(it, "Kein deutlicher Anstieg", "Erhöht", "Stark erhöht")
    }
    ReadinessValueKind.STEPS -> null
}

private fun scoreBadge(score: Int, favorable: String, caution: String, low: String): ReadinessBadge? {
    val band = readinessSignalBand(score) ?: return null
    val label = when (band) {
        ReadinessSignalBand.FAVORABLE -> favorable
        ReadinessSignalBand.CAUTION -> caution
        ReadinessSignalBand.LOW -> low
    }
    return ReadinessBadge(label, band.toBadgeTone())
}

private fun stressBadge(state: ReadinessUiState): ReadinessBadge? {
    val metric = state.health?.averageStressLevel ?: return null
    if (metric.availability != HealthMetricAvailability.AVAILABLE) return null
    val band = garminStressBand(metric.value) ?: return null
    val label = when (band) {
        GarminStressBand.RESTING -> "Ruhe"
        GarminStressBand.LOW -> "Niedrig"
        GarminStressBand.MODERATE -> "Mittel"
        GarminStressBand.HIGH -> "Hoch"
    }
    val tone = when (band) {
        GarminStressBand.RESTING, GarminStressBand.LOW -> HrvStatusTone.GOOD
        GarminStressBand.MODERATE -> HrvStatusTone.CAUTION
        GarminStressBand.HIGH -> HrvStatusTone.LOW
    }
    return ReadinessBadge(label, tone)
}

private fun recoveryIntervalBadge(minutesSinceLastRun: Long?): ReadinessBadge? {
    val band = recoveryIntervalBand(minutesSinceLastRun) ?: return null
    val label = when (band) {
        RecoveryIntervalBand.UNDER_12_HOURS -> "Unter 12 Std."
        RecoveryIntervalBand.UNDER_36_HOURS -> "12–36 Std."
        RecoveryIntervalBand.AT_LEAST_36_HOURS -> "Mind. 36 Std."
    }
    val tone = when (band) {
        RecoveryIntervalBand.UNDER_12_HOURS -> HrvStatusTone.LOW
        RecoveryIntervalBand.UNDER_36_HOURS -> HrvStatusTone.CAUTION
        RecoveryIntervalBand.AT_LEAST_36_HOURS -> HrvStatusTone.GOOD
    }
    return ReadinessBadge(label, tone)
}

private fun ReadinessSignalBand.toBadgeTone(): HrvStatusTone = when (this) {
    ReadinessSignalBand.FAVORABLE -> HrvStatusTone.GOOD
    ReadinessSignalBand.CAUTION -> HrvStatusTone.CAUTION
    ReadinessSignalBand.LOW -> HrvStatusTone.LOW
}

@Composable
private fun TrendSummary(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun BackgroundReadinessIcon(icon: Int?) {
    if (icon != null) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(112.dp).alpha(0.16f)
        )
    }
}

@Composable
private fun StoredValues(series: ReadinessSeries, measurements: List<de.run.app.domain.model.DailyHealthValue>) {
    val today = java.time.LocalDate.now()
    val newestFirst = measurements.asReversed()
    Column {
        newestFirst.forEachIndexed { index, value ->
            val isToday = value.date == today
            val fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal
            Row(
                Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(value.date.format(READINESS_DATE_FORMAT), style = MaterialTheme.typography.bodySmall, fontWeight = fontWeight)
                Text(formatValue(value.value, series.valueKind), style = MaterialTheme.typography.bodySmall, fontWeight = fontWeight)
            }
            if (index < newestFirst.lastIndex) {
                HorizontalDivider(
                    Modifier.padding(horizontal = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                )
            }
        }
    }
}

@Composable
private fun DateAxisLabels(largeLabels: Boolean = false) {
    val today = java.time.LocalDate.now()
    Row(Modifier.fillMaxWidth().padding(start = 40.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(today.minusDays(13).format(READINESS_DATE_FORMAT), style = if (largeLabels) MaterialTheme.typography.bodySmall else MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(today.minusDays(7).format(READINESS_DATE_FORMAT), style = if (largeLabels) MaterialTheme.typography.bodySmall else MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Heute", style = if (largeLabels) MaterialTheme.typography.bodySmall else MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Legend(label: String, hasPersonalRange: Boolean = false, largeLabel: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        if (hasPersonalRange) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.width(12.dp).height(12.dp)) {}
            Spacer(Modifier.width(8.dp))
            Text("Dein Normalbereich", style = if (largeLabel) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(16.dp))
        }
        Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.width(12.dp).height(12.dp)) {}
        Spacer(Modifier.width(8.dp))
        Text(label, style = if (largeLabel) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun StatisticsCard(
    series: ReadinessSeries,
    measurements: List<de.run.app.domain.model.DailyHealthValue>,
    spacious: Boolean = false,
    periodDays: Int = 14,
    denseRows: Boolean = false
) {
    val values = measurements.map { it.value }
    val average = values.average().roundToInt()
    Card(
        shape = if (spacious) MaterialTheme.shapes.large else MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = if (spacious) 18.dp else 16.dp, vertical = if (spacious) 12.dp else if (denseRows) 6.dp else 8.dp)) {
            Text("Übersicht · letzte $periodDays Tage", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            StatisticRow("Durchschnitt", formatValue(average, series.valueKind), spacious, denseRows,
                StatisticIconType.AVERAGE)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            StatisticRow("Höchster Wert", formatValue(values.maxOrNull() ?: average, series.valueKind), spacious, denseRows,
                StatisticIconType.HIGH)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            StatisticRow("Niedrigster Wert", formatValue(values.minOrNull() ?: average, series.valueKind), spacious, denseRows,
                StatisticIconType.LOW)
        }
    }
}

@Composable
private fun StatisticRow(
    label: String,
    value: String,
    spacious: Boolean = false,
    dense: Boolean = false,
    icon: StatisticIconType? = null
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = if (spacious) 11.dp else if (dense) 6.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let { StatisticIcon(it) }
        if (icon != null) Spacer(Modifier.width(14.dp))
        Text(label, Modifier.weight(1f), style = if (spacious) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium)
        Text(value, style = if (spacious) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

private enum class StatisticIconType { AVERAGE, HIGH, LOW }

@Composable
private fun StatisticIcon(type: StatisticIconType) {
    val color = when (type) {
        StatisticIconType.AVERAGE -> MaterialTheme.colorScheme.onSurface
        StatisticIconType.HIGH -> MaterialTheme.colorScheme.primary
        StatisticIconType.LOW -> MaterialTheme.colorScheme.error
    }
    androidx.compose.foundation.Canvas(Modifier.size(18.dp).semantics {
        contentDescription = when (type) {
            StatisticIconType.AVERAGE -> "Durchschnittsdiagramm"
            StatisticIconType.HIGH -> "Pfeil nach oben"
            StatisticIconType.LOW -> "Pfeil nach unten"
        }
    }) {
        val stroke = 1.7.dp.toPx()
        when (type) {
            StatisticIconType.AVERAGE -> {
                val baseline = size.height * 0.88f
                listOf(0.28f to 0.5f, 0.5f to 0.25f, 0.72f to 0.08f, 0.92f to 0.36f).forEach { (x, top) ->
                    drawLine(color, Offset(size.width * x, size.height * top), Offset(size.width * x, baseline), stroke, StrokeCap.Round)
                }
            }
            StatisticIconType.HIGH -> drawDirectionArrow(color, pointsUp = true, strokeWidth = stroke)
            StatisticIconType.LOW -> drawDirectionArrow(color, pointsUp = false, strokeWidth = stroke)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDirectionArrow(
    color: androidx.compose.ui.graphics.Color,
    pointsUp: Boolean,
    strokeWidth: Float
) {
    val startY = if (pointsUp) size.height * 0.82f else size.height * 0.18f
    val endY = if (pointsUp) size.height * 0.18f else size.height * 0.82f
    val start = Offset(size.width * 0.18f, startY)
    val end = Offset(size.width * 0.82f, endY)
    drawLine(color, start, end, strokeWidth, StrokeCap.Round)
    val head = size.minDimension * 0.3f
    val verticalArmY = end.y + if (pointsUp) head else -head
    drawLine(color, end, Offset(end.x - head, end.y), strokeWidth, StrokeCap.Round)
    drawLine(color, end, Offset(end.x, verticalArmY), strokeWidth, StrokeCap.Round)
}

@Composable
private fun InformationCard(message: String, compact: Boolean = false) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(if (compact) 12.dp else 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("ⓘ", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun popupCaution(title: String): String =
    "Die Kurve verbindet gespeicherte Messpunkte. Fehlende Tageswerte werden nicht ergänzt.\n\n" + when (title) {
    "HRV / HFV" -> "Ob dein Wert für dich normal ist, lässt sich erst mit ausreichend persönlichen Vergleichswerten einordnen."
    "Ruhepuls" -> "Dein persönlicher Verlauf hilft bei der Einordnung. Einzelne Werte sind keine pauschale Bewertung."
    "Schlaf & Verlauf" -> "Die Anzeige beschreibt gespeicherte Schlafdauer. Sie misst nicht allein, wie erholt du bist."
    "Trainingsbelastung" -> "Gezeigt werden erfasste Laufminuten. Tage ohne Eintrag werden nicht als Ruhetage gewertet."
    "Erholungszeit" -> "Die Zeit wird aus deinem letzten gespeicherten Lauf berechnet. Sie verändert deinen Trainingsplan nicht."
    else -> "Stress und Schritte sind zusätzlicher Tageskontext. Fehlende Messwerte werden nicht ergänzt."
}

private val READINESS_DATE_FORMAT = DateTimeFormatter.ofPattern("d. MMM", Locale.GERMAN)
