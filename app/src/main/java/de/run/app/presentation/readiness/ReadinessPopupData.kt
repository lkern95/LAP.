package de.run.app.presentation.readiness

import de.run.app.domain.model.DailyHealthValue
import de.run.app.domain.model.HealthMetric
import de.run.app.domain.model.HealthMetricAvailability
import de.run.app.domain.model.ReadinessHealthSnapshot
import de.run.app.domain.model.Workout
import de.run.app.domain.model.isRunning
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun readinessSeries(title: String, state: ReadinessUiState): List<ReadinessSeries> = when (title) {
    "HRV / HFV" -> state.health?.let { listOf(healthSeries("", "Heute", it.hrvLastNightAverageMs, it.hrvHistory, ReadinessValueKind.HRV)) }.orEmpty()
    "Ruhepuls" -> state.health?.let { listOf(healthSeries("", "Heute", it.restingHeartRateBpm, it.restingHeartRateHistory, ReadinessValueKind.HEART_RATE)) }.orEmpty()
    "Schlaf & Verlauf" -> state.health?.let { listOf(healthSeries("", "Letzte Nacht", it.sleepMinutes, it.sleepHistory, ReadinessValueKind.SLEEP)) }.orEmpty()
    "Trainingsbelastung" -> listOf(trainingSeries(state.recentWorkouts))
    "Erholungszeit" -> listOf(recoverySeries(state))
    "Stress & Alltagsaktivität" -> state.health?.let { healthContextSeries(it) }.orEmpty()
    else -> emptyList()
}

internal fun popupIntroduction(title: String): String = when (title) {
    "HRV / HFV" -> "Deine nächtliche Herzfrequenzvariabilität zeigt, wie gut sich dein Körper erholt."
    "Ruhepuls" -> "Dein Ruhepuls im Verlauf. Ein einzelner Wert sagt noch nicht, was für dich normal ist."
    "Schlaf & Verlauf" -> "Deine gespeicherte Schlafdauer im Verlauf."
    "Trainingsbelastung" -> "Deine erfassten Laufminuten zeigen, wie viel du zuletzt trainiert hast."
    "Erholungszeit" -> "Zeit seit deinem letzten gespeicherten Lauf."
    else -> "Deine gespeicherten Stress- und Schrittwerte im Verlauf."
}

internal fun lastFourteenDays(values: List<DailyHealthValue>, currentValue: Int?): List<DailyHealthValue> =
    lastDays(values, currentValue, 14)

internal fun lastTwentyEightDays(values: List<DailyHealthValue>, currentValue: Int?): List<DailyHealthValue> =
    lastDays(values, currentValue, 28)

private fun lastDays(values: List<DailyHealthValue>, currentValue: Int?, dayCount: Long): List<DailyHealthValue> {
    val today = LocalDate.now()
    val firstDay = today.minusDays(dayCount - 1)
    val byDate = values.filter { it.value > 0 && !it.date.isBefore(firstDay) && !it.date.isAfter(today) }
        .associate { it.date to it.value }.toMutableMap()
    currentValue?.takeIf { it > 0 }?.let { byDate[today] = it }
    return (dayCount - 1 downTo 0L).mapNotNull { offset ->
        val date = today.minusDays(offset)
        byDate[date]?.let { DailyHealthValue(date, it) }
    }
}

internal fun formatValue(value: Int, kind: ReadinessValueKind): String = when (kind) {
    ReadinessValueKind.HRV -> "$value ms"
    ReadinessValueKind.HEART_RATE -> "$value bpm"
    ReadinessValueKind.SLEEP -> "${value / 60} Std. ${value % 60} Min."
    ReadinessValueKind.STRESS -> "$value von 100"
    ReadinessValueKind.STEPS -> "${"%,d".format(Locale.GERMANY, value)} Schritte"
    ReadinessValueKind.MINUTES -> "$value Min."
}

private fun healthSeries(
    name: String,
    currentLabel: String,
    metric: HealthMetric<Int>,
    history: List<DailyHealthValue>,
    kind: ReadinessValueKind
): ReadinessSeries {
    val availableCurrent = metric.value?.takeIf { metric.availability == HealthMetricAvailability.AVAILABLE }
    val recent = lastFourteenDays(history, availableCurrent)
    val latest = recent.lastOrNull()
    val displayValue = availableCurrent?.let { formatValue(it, kind) }
        ?: latest?.let { formatValue(it.value, kind) }
        ?: "Nicht verfügbar"
    val change = recent.takeLast(2).let { values ->
        if (values.size < 2) null else trendLabel(values.last().value, values.first().value, kind)
    }
    return ReadinessSeries(
        name = name,
        accessibilityName = when (kind) {
            ReadinessValueKind.HRV -> "HRV / HFV"
            ReadinessValueKind.HEART_RATE -> "Ruhepuls"
            ReadinessValueKind.SLEEP -> "Schlaf"
            ReadinessValueKind.STRESS -> "Stress"
            ReadinessValueKind.STEPS -> "Schritte"
            ReadinessValueKind.MINUTES -> "Laufminuten"
        },
        currentLabel = if (availableCurrent != null) currentLabel else latest?.date?.format(READINESS_DATE_FORMAT) ?: currentLabel,
        currentDisplay = displayValue,
        trendLabel = change,
        values = history,
        currentValue = availableCurrent,
        valueKind = kind
    )
}

private fun healthContextSeries(health: ReadinessHealthSnapshot): List<ReadinessSeries> = buildList {
    if (health.averageStressLevel.value != null || health.stressHistory.isNotEmpty()) {
        add(healthSeries("Stress", "Heute", health.averageStressLevel, health.stressHistory, ReadinessValueKind.STRESS))
    }
    if (health.steps.value != null || health.stepsHistory.isNotEmpty()) {
        add(healthSeries("Alltagsaktivität · Schritte", "Heute", health.steps, health.stepsHistory, ReadinessValueKind.STEPS))
    }
}

private fun trainingSeries(workouts: List<Workout>): ReadinessSeries {
    val today = LocalDate.now()
    val firstDate = today.minusDays(13)
    val dailyMinutes = workouts.filter(Workout::isRunning)
        .mapNotNull { workout ->
            val date = Instant.ofEpochMilli(workout.endedAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            date.takeUnless { it.isBefore(firstDate) || it.isAfter(today) }
                ?.let { it to (workout.durationSeconds / 60).toInt() }
        }
        .groupBy({ it.first }, { it.second })
        .map { (date, minutes) -> DailyHealthValue(date, minutes.sum()) }
    val lastWeek = dailyMinutes.filter { !it.date.isBefore(today.minusDays(6)) }.sumOf(DailyHealthValue::value)
    val runsLastWeek = workouts.count {
        val date = Instant.ofEpochMilli(it.endedAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        it.isRunning() && !date.isBefore(today.minusDays(6)) && !date.isAfter(today)
    }
    val display = if (dailyMinutes.isEmpty()) "Keine Läufe erfasst" else "$runsLastWeek Läufe · $lastWeek Min."
    return ReadinessSeries(
        name = "Laufminuten",
        accessibilityName = "Laufminuten",
        currentLabel = "Letzte 7 Tage",
        currentDisplay = display,
        trendLabel = "Läufe der letzten 14 Tage",
        values = dailyMinutes,
        currentValue = null,
        valueKind = ReadinessValueKind.MINUTES,
        showTrend = false,
        showStatistics = false
    )
}

private fun recoverySeries(state: ReadinessUiState): ReadinessSeries {
    val elapsed = state.facts?.minutesSinceLastRun
    val value = elapsed?.let(::formatElapsed)
        ?: if (state.recentWorkouts.none(Workout::isRunning)) "Kein Lauf erfasst" else "Nicht verfügbar"
    return trainingSeries(state.recentWorkouts).copy(
        name = "Laufminuten als Verlauf",
        currentLabel = "Seit dem letzten Lauf",
        currentDisplay = value,
        trendLabel = null,
        showTrend = false
    )
}

private fun formatElapsed(minutes: Long): String {
    val days = minutes / 1_440
    val hours = (minutes % 1_440) / 60
    return when {
        days > 0 -> "$days Tage, $hours Std."
        hours > 0 -> "$hours Std."
        else -> "${minutes.coerceAtLeast(1)} Min."
    }
}

internal fun trendLabel(current: Int, previous: Int, kind: ReadinessValueKind): String {
    val difference = current - previous
    if (difference == 0) return "Unverändert"
    val direction = if (difference > 0) "Gestiegen" else "Gesunken"
    return "$direction um ${formatValue(kotlin.math.abs(difference), kind)}"
}

internal data class ReadinessSeries(
    val name: String,
    val accessibilityName: String,
    val currentLabel: String,
    val currentDisplay: String,
    val trendLabel: String?,
    val values: List<DailyHealthValue>,
    val currentValue: Int?,
    val valueKind: ReadinessValueKind,
    val showTrend: Boolean = true,
    val showStatistics: Boolean = true
)

internal enum class ReadinessValueKind { HRV, HEART_RATE, SLEEP, STRESS, STEPS, MINUTES }

private val READINESS_DATE_FORMAT = DateTimeFormatter.ofPattern("d. MMM", Locale.GERMAN)
