package de.run.app.presentation.readiness

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.run.app.domain.model.DailyHealthValue
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

@Composable
internal fun ReadinessTrendChart(
    seriesName: String,
    points: List<DailyHealthValue>,
    backgroundIcon: Int?,
    normalRange: IntRange? = null,
    chartHeight: Dp = 112.dp,
    backgroundIconSize: Dp = 112.dp,
    themeTintBackground: Boolean = false,
    largeLabels: Boolean = false,
    showTitle: Boolean = true,
    lineWidth: Dp = 3.dp,
    pointRadius: Dp = 3.5.dp,
    highlightToday: Boolean = false,
    autoScale: Boolean = false
) {
    val byDate = points.associate { it.date to it.value }
    val values = (13L downTo 0L).map { offset ->
        byDate[LocalDate.now().minusDays(offset)]?.takeIf { it > 0 }
    }
    val animation = remember { Animatable(0f) }
    LaunchedEffect(values) {
        animation.snapTo(0f)
        animation.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2_200, easing = FastOutSlowInEasing)
        )
    }
    val available = values.filterNotNull()
    val scale = if (autoScale) hrvChartScale(available) else chartScale(available)
    val color = MaterialTheme.colorScheme.primary
    val rangeColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
    val chartSurfaceColor = MaterialTheme.colorScheme.surface
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val todayPulse = if (highlightToday && values.lastOrNull() != null) {
        val transition = rememberInfiniteTransition(label = "todayHrvPointPulse")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1_500), repeatMode = RepeatMode.Reverse),
            label = "todayHrvPointPulseFraction"
        ).value
    } else 0f
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (showTitle) {
            Text("Letzte 14 Tage", style = if (largeLabels) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.width(32.dp).height(chartHeight), verticalArrangement = Arrangement.SpaceBetween) {
                scale?.let { axis ->
                    axis.ticks.forEach { tick ->
                        Text(tick.toString(), style = if (largeLabels) MaterialTheme.typography.bodySmall else MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Box(Modifier.weight(1f).height(chartHeight)) {
                if (backgroundIcon != null) {
                    Image(
                        painter = painterResource(backgroundIcon),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        colorFilter = if (themeTintBackground) ColorFilter.tint(MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier.align(Alignment.Center).width(backgroundIconSize).height(backgroundIconSize)
                            .alpha(if (themeTintBackground) 0.22f else 0.16f)
                    )
                }
                Canvas(
                    Modifier.fillMaxSize().semantics {
                        val todayStyle = if (highlightToday) "; vergangene Punkte offen, heutiger Punkt gefüllt und pulsierend" else ""
                        contentDescription = "$seriesName: ${values.count { it != null }} gespeicherte Messwerte der letzten 14 Tage$todayStyle"
                    }
                ) {
                val horizontalPadding = 4.dp.toPx()
                val verticalPadding = 6.dp.toPx()
                val plotHeight = size.height - verticalPadding * 2
                val plotWidth = size.width - horizontalPadding * 2
                val axis = scale ?: return@Canvas
                val low = axis.low.toFloat()
                val high = axis.high.toFloat()
                val readings = recordedPlotPoints(values)
                fun yFor(value: Float) = verticalPadding + plotHeight * (1f - (value - low) / (high - low))
                fun position(reading: ReadinessPlotPoint) = Offset(
                        x = horizontalPadding + plotWidth * reading.dayIndex / values.lastIndex.coerceAtLeast(1),
                        y = yFor(reading.value.toFloat())
                    )
                val chartPoints = readings.map { it to position(it) }
                val segments = continuousPlotSegments(values).map { (start, end) ->
                    position(start) to position(end)
                }
                normalRange?.let { personalRange ->
                    val bandLow = personalRange.first.toFloat().coerceAtLeast(low)
                    val bandHigh = personalRange.last.toFloat().coerceAtMost(high)
                    if (bandLow < bandHigh) {
                        val top = yFor(bandHigh)
                        val bottom = yFor(bandLow)
                        drawRect(
                            rangeColor,
                            Offset(horizontalPadding, top),
                            Size(plotWidth, bottom - top)
                        )
                    }
                }
                axis.ticks.forEach { tick ->
                    val y = yFor(tick.toFloat())
                    drawLine(gridColor, Offset(horizontalPadding, y), Offset(size.width - horizontalPadding, y), 1.dp.toPx())
                }
                val animatedSegments = animation.value * segments.size
                segments.forEachIndexed { index, (start, end) ->
                    if (animatedSegments > index) {
                        val fraction = (animatedSegments - index).coerceIn(0f, 1f)
                        drawLine(color, start,
                            Offset(
                                start.x + (end.x - start.x) * fraction,
                                start.y + (end.y - start.y) * fraction
                            ),
                            strokeWidth = lineWidth.toPx(), cap = StrokeCap.Round)
                    }
                }
                chartPoints.forEach { (reading, point) ->
                    if (highlightToday && reading.dayIndex == values.lastIndex && values.lastOrNull() != null) {
                        drawCircle(color.copy(alpha = 0.1f), pointRadius.toPx() * 2.5f, point)
                        val pulseRadius = pointRadius.toPx() * (1.8f + todayPulse * 0.8f)
                        drawCircle(color.copy(alpha = 0.22f * (1f - todayPulse)), pulseRadius, point)
                        drawCircle(color, pointRadius.toPx(), point)
                    } else if (highlightToday) {
                        val insideRange = normalRange?.contains(reading.value) == true
                        val holeColor = if (insideRange) rangeColor.compositeOver(chartSurfaceColor) else chartSurfaceColor
                        drawCircle(holeColor, pointRadius.toPx() * 0.72f, point)
                        drawCircle(color, pointRadius.toPx(), point, style = Stroke(width = 1.dp.toPx()))
                    } else {
                        drawCircle(color, pointRadius.toPx(), point)
                    }
                }
                }
        }
    }
}
}

internal fun continuousPlotSegments(values: List<Int?>): List<Pair<ReadinessPlotPoint, ReadinessPlotPoint>> =
    recordedPlotPoints(values).zipWithNext()

internal data class ReadinessPlotPoint(val dayIndex: Int, val value: Int)

private fun recordedPlotPoints(values: List<Int?>): List<ReadinessPlotPoint> =
    values.mapIndexedNotNull { index, value -> value?.takeIf { it > 0 }?.let { ReadinessPlotPoint(index, it) } }

internal data class ChartScale(val low: Int, val high: Int, val ticks: List<Int>)

private fun chartScale(values: List<Int>): ChartScale? {
    val maximum = values.maxOrNull() ?: return null
    val step = niceAxisStep(maxOf(20f, maximum / 4f))
    val roundedHigh = ceil(maximum / step.toFloat()).toInt() * step
    val high = maxOf(80, roundedHigh + if (roundedHigh == maximum) step else 0)
    return ChartScale(0, high, (high downTo 0 step step).toList())
}

internal fun hrvChartScale(values: List<Int>): ChartScale? {
    val minimum = values.minOrNull() ?: return null
    val maximum = values.maxOrNull() ?: return null
    val dataSpan = (maximum - minimum).toFloat()
    val paddedSpan = maxOf(20f, dataSpan * 1.5f)
    val targetIntervals = if (dataSpan < 30f) 3f else 5f
    val step = niceAxisStep(paddedSpan / targetIntervals)
    val padding = (paddedSpan - dataSpan) / 2f
    val low = floor((minimum - padding) / step).toInt() * step
    val high = ceil((maximum + padding) / step).toInt() * step
    return ChartScale(low, high, (high downTo low step step).toList())
}

private fun niceAxisStep(value: Float): Int {
    val magnitude = 10.0.pow(floor(log10(value.coerceAtLeast(1f).toDouble()))).toFloat()
    val normalized = value / magnitude
    val multiplier = when {
        normalized <= 1f -> 1
        normalized <= 2f -> 2
        normalized <= 5f -> 5
        else -> 10
    }
    return (multiplier * magnitude).toInt().coerceAtLeast(1)
}
