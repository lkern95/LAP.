package de.run.app.data.garmin

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

internal data class GarminDailyHealth(
    val date: LocalDate,
    val sleepMinutes: Int? = null,
    val restingHeartRateBpm: Int? = null,
    val hrvLastNightAverageMs: Int? = null,
    val hrvWeeklyAverageMs: Int? = null,
    val hrvStatus: String? = null,
    val hrvBaselineLowMs: Int? = null,
    val hrvBaselineHighMs: Int? = null,
    val averageStressLevel: Int? = null,
    val steps: Int? = null
)

internal class GarminConnectHealthClient(
    private val transport: GarminConnectHttpTransport,
    private val requestPacingMillis: Long = REQUEST_PACING_MILLIS
) {
    suspend fun readRecent(session: GarminSession, today: LocalDate): List<GarminDailyHealth> {
        val from = today.minusDays(HISTORY_DAYS)
        val days = linkedMapOf<LocalDate, GarminDailyHealth>()
        readHeartRate(session, from, today).forEach { days.merge(it) }
        pause()
        readSleepSummaries(session, from, today).forEach { days.merge(it) }
        pause()
        readHrv(session, from, today).forEach { days.merge(it) }
        pause()
        readStress(session, from, today).forEach { days.merge(it) }
        pause()
        readSteps(session, from, today).forEach { days.merge(it) }
        return days.values.sortedBy(GarminDailyHealth::date)
    }

    private suspend fun pause() {
        if (requestPacingMillis > 0) kotlinx.coroutines.delay(requestPacingMillis)
    }

    private fun readSleepSummaries(session: GarminSession, from: LocalDate, to: LocalDate): List<GarminDailyHealth> {
        val response = get(session, "$API_BASE/sleep-service/sleep/dailySleepSummariesByDate?startDate=$from&endDate=$to")
        val root = response.jsonOrNull() ?: return emptyList()
        return root.healthObjects().mapNotNull { item ->
            val date = item.dateInRange(from, to) ?: return@mapNotNull null
            val minutes = item.number(SLEEP_SECONDS_KEYS)?.takeIf { it > 0.0 }?.div(60.0)?.toInt()
                ?: item.number(SLEEP_MINUTES_KEYS)?.toInt()
            minutes?.takeIf { it in MIN_SLEEP_MINUTES..MAX_SLEEP_MINUTES }
                ?.let { GarminDailyHealth(date, sleepMinutes = it) }
        }
    }

    private fun readHeartRate(session: GarminSession, from: LocalDate, to: LocalDate): List<GarminDailyHealth> {
        val response = get(session, "$API_BASE/usersummary-service/stats/heartRate/daily/$from/$to")
        val root = response.jsonOrNull() ?: return emptyList()
        return root.healthObjects().mapNotNull { item ->
            val date = item.dateInRange(from, to) ?: return@mapNotNull null
            val bpm = item.number(RESTING_HEART_RATE_KEYS)?.toInt()
                ?.takeIf { it in MIN_RESTING_BPM..MAX_RESTING_BPM } ?: return@mapNotNull null
            GarminDailyHealth(date, restingHeartRateBpm = bpm)
        }
    }

    private fun readHrv(session: GarminSession, from: LocalDate, to: LocalDate): List<GarminDailyHealth> {
        val response = get(session, "$API_BASE/hrv-service/hrv/daily/$from/$to")
        val root = response.jsonOrNull() ?: return emptyList()
        return root.healthObjects().mapNotNull { item ->
            val date = item.dateInRange(from, to) ?: return@mapNotNull null
            val summary = item.optJSONObject("hrvSummary") ?: item
            val lastNight = summary.number(HRV_LAST_NIGHT_KEYS)?.toInt()?.takeIf { it in MIN_HRV_MS..MAX_HRV_MS }
            val weekly = summary.number(HRV_WEEKLY_KEYS)?.toInt()?.takeIf { it in MIN_HRV_MS..MAX_HRV_MS }
            val baseline = summary.optJSONObject("baseline")
            val baselineLow = baseline?.number(HRV_BASELINE_LOW_KEYS)?.toInt()?.takeIf { it in MIN_HRV_MS..MAX_HRV_MS }
            val baselineHigh = baseline?.number(HRV_BASELINE_HIGH_KEYS)?.toInt()?.takeIf { it in MIN_HRV_MS..MAX_HRV_MS }
            val status = summary.optString("status").takeIf(String::isNotBlank)
            if (lastNight == null && weekly == null && status == null) return@mapNotNull null
            GarminDailyHealth(
                date = date,
                hrvLastNightAverageMs = lastNight,
                hrvWeeklyAverageMs = weekly,
                hrvStatus = status,
                hrvBaselineLowMs = baselineLow,
                hrvBaselineHighMs = baselineHigh
            )
        }
    }

    private fun readStress(session: GarminSession, from: LocalDate, to: LocalDate): List<GarminDailyHealth> {
        val response = get(session, "$API_BASE/usersummary-service/stats/stress/daily/$from/$to")
        val root = response.jsonOrNull() ?: return emptyList()
        return root.healthObjects().mapNotNull { item ->
            val date = item.dateInRange(from, to) ?: return@mapNotNull null
            val stress = item.number(STRESS_AVERAGE_KEYS)?.toInt()?.takeIf { it in MIN_STRESS..MAX_STRESS }
                ?: return@mapNotNull null
            GarminDailyHealth(date, averageStressLevel = stress)
        }
    }

    private fun readSteps(session: GarminSession, from: LocalDate, to: LocalDate): List<GarminDailyHealth> {
        val response = get(session, "$API_BASE/usersummary-service/stats/steps/daily/$from/$to")
        val root = response.jsonOrNull() ?: return emptyList()
        return root.healthObjects().mapNotNull { item ->
            val date = item.dateInRange(from, to) ?: return@mapNotNull null
            val steps = item.number(STEP_KEYS)?.toInt()?.takeIf { it >= 0 } ?: return@mapNotNull null
            GarminDailyHealth(date, steps = steps)
        }
    }

    private fun get(session: GarminSession, url: String) = transport.execute(
        url,
        "GET",
        headers = mapOf(
            "Authorization" to "Bearer ${session.accessToken}",
            "Accept" to "application/json",
            "User-Agent" to "LAP/0.3.0 (Android)"
        ),
        maximumBytes = MAX_RESPONSE_BYTES
    ).also(::requireSuccess)

    private fun requireSuccess(response: GarminHttpResponse) {
        if (response.statusCode !in 200..299 && response.statusCode != HTTP_NOT_FOUND) {
            throw GarminApiException(response.statusCode)
        }
    }

    private fun GarminHttpResponse.jsonOrNull(): Any? {
        if (statusCode == HTTP_NOT_FOUND || body.isEmpty()) return null
        val text = String(body, Charsets.UTF_8).trim()
        if (text.isEmpty() || text == "null") return null
        return if (text.startsWith("[")) JSONArray(text) else JSONObject(text)
    }

    private fun Any.healthObjects(): List<JSONObject> = buildList {
        fun collect(value: Any?) {
            when (value) {
                is JSONArray -> for (index in 0 until value.length()) collect(value.opt(index))
                is JSONObject -> {
                    if (value.date() != null) add(value)
                    val iterator = value.keys()
                    while (iterator.hasNext()) collect(value.opt(iterator.next()))
                }
            }
        }
        collect(this@healthObjects)
    }

    private fun JSONObject.date(): LocalDate? = optString("calendarDate").takeIf(String::isNotBlank)
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun JSONObject.dateInRange(from: LocalDate, to: LocalDate): LocalDate? = date()?.takeIf {
        !it.isBefore(from) && !it.isAfter(to)
    }

    private fun JSONObject.number(keys: Set<String>): Double? {
        keys.forEach { key ->
            val value = opt(key)
            if (value is Number) return value.toDouble().takeIf(Double::isFinite)
        }
        val iterator = keys()
        while (iterator.hasNext()) findNumber(opt(iterator.next()), keys)?.let { return it }
        return null
    }

    private fun findNumber(value: Any?, keys: Set<String>): Double? = when (value) {
        is JSONObject -> value.number(keys)
        is JSONArray -> (0 until value.length()).firstNotNullOfOrNull { findNumber(value.opt(it), keys) }
        else -> null
    }

    private fun MutableMap<LocalDate, GarminDailyHealth>.merge(fresh: GarminDailyHealth) {
        val old = get(fresh.date)
        put(fresh.date, GarminDailyHealth(
            date = fresh.date,
            sleepMinutes = fresh.sleepMinutes ?: old?.sleepMinutes,
            restingHeartRateBpm = fresh.restingHeartRateBpm ?: old?.restingHeartRateBpm,
            hrvLastNightAverageMs = fresh.hrvLastNightAverageMs ?: old?.hrvLastNightAverageMs,
            hrvWeeklyAverageMs = fresh.hrvWeeklyAverageMs ?: old?.hrvWeeklyAverageMs,
            hrvStatus = fresh.hrvStatus ?: old?.hrvStatus,
            hrvBaselineLowMs = fresh.hrvBaselineLowMs ?: old?.hrvBaselineLowMs,
            hrvBaselineHighMs = fresh.hrvBaselineHighMs ?: old?.hrvBaselineHighMs,
            averageStressLevel = fresh.averageStressLevel ?: old?.averageStressLevel,
            steps = fresh.steps ?: old?.steps
        ))
    }

    private companion object {
        const val API_BASE = "https://connectapi.garmin.com"
        const val REQUEST_PACING_MILLIS = 1_000L
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
        const val HTTP_NOT_FOUND = 404
        const val HISTORY_DAYS = 55L
        const val MIN_SLEEP_MINUTES = 60
        const val MAX_SLEEP_MINUTES = 24 * 60
        const val MIN_RESTING_BPM = 25
        const val MAX_RESTING_BPM = 250
        const val MIN_HRV_MS = 1
        const val MAX_HRV_MS = 500
        const val MIN_STRESS = 0
        const val MAX_STRESS = 100
        val SLEEP_SECONDS_KEYS = setOf("sleepTimeSeconds", "sleepDurationSeconds", "totalSleepSeconds")
        val SLEEP_MINUTES_KEYS = setOf("sleepTimeMinutes", "sleepDurationMinutes", "totalSleepMinutes")
        val RESTING_HEART_RATE_KEYS = setOf("restingHeartRate", "restingHeartRateBpm", "restingHr")
        val HRV_LAST_NIGHT_KEYS = setOf("lastNightAvg", "lastNightAverage")
        val HRV_WEEKLY_KEYS = setOf("weeklyAvg", "weeklyAverage")
        val HRV_BASELINE_LOW_KEYS = setOf("balancedLow", "lowUpper")
        val HRV_BASELINE_HIGH_KEYS = setOf("balancedUpper")
        val STRESS_AVERAGE_KEYS = setOf("avgStressLevel", "averageStressLevel")
        val STEP_KEYS = setOf("totalSteps", "steps")
    }
}
