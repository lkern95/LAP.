package de.run.app.data.garmin

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

internal data class GarminDailyHealth(
    val date: LocalDate,
    val sleepMinutes: Int? = null,
    val restingHeartRateBpm: Int? = null
)

internal class GarminConnectHealthClient(
    private val transport: GarminConnectHttpTransport,
    private val requestPacingMillis: Long = REQUEST_PACING_MILLIS
) {
    suspend fun readRecent(session: GarminSession, today: LocalDate): List<GarminDailyHealth> {
        val days = linkedMapOf<LocalDate, GarminDailyHealth>()
        val heartRateStart = today.minusDays(55)
        readHeartRate(session, heartRateStart, today).forEach { days[it.date] = it }
        for (offset in 6 downTo 0) {
            val date = today.minusDays(offset.toLong())
            if (offset != 6 && requestPacingMillis > 0) kotlinx.coroutines.delay(requestPacingMillis)
            val minutes = readSleep(session, date)
            if (minutes != null) {
                val old = days[date]
                days[date] = GarminDailyHealth(date, minutes, old?.restingHeartRateBpm)
            }
        }
        return days.values.sortedBy(GarminDailyHealth::date)
    }

    private fun readSleep(session: GarminSession, date: LocalDate): Int? {
        val response = get(session, "$API_BASE/sleep-service/sleep/dailySleepData?date=$date&nonSleepBufferMinutes=60")
        if (response.statusCode == HTTP_NOT_FOUND) return null
        requireSuccess(response)
        val root = JSONObject(String(response.body, Charsets.UTF_8))
        val sleep = root.optJSONObject("dailySleepDTO") ?: root
        val seconds = sleep.number(SLEEP_SECONDS_KEYS)?.takeIf { it > 0.0 }
        val minutes = seconds?.div(60.0)?.toInt()
            ?: sleep.number(SLEEP_MINUTES_KEYS)?.toInt()
        return minutes?.takeIf { it in MIN_SLEEP_MINUTES..MAX_SLEEP_MINUTES }
    }

    private fun readHeartRate(session: GarminSession, from: LocalDate, to: LocalDate): List<GarminDailyHealth> {
        val response = get(session, "$API_BASE/usersummary-service/stats/heartRate/daily/$from/$to")
        if (response.statusCode == HTTP_NOT_FOUND) return emptyList()
        requireSuccess(response)
        val root = String(response.body, Charsets.UTF_8).let { text ->
            if (text.trimStart().startsWith("[")) JSONArray(text) else JSONObject(text)
        }
        return buildList {
            root.forEachObject { item ->
                val date = item.optString("calendarDate").takeIf(String::isNotBlank)
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@forEachObject
                if (date.isBefore(from) || date.isAfter(to)) return@forEachObject
                val bpm = item.number(RESTING_HEART_RATE_KEYS)?.toInt()
                    ?.takeIf { it in MIN_RESTING_BPM..MAX_RESTING_BPM } ?: return@forEachObject
                add(GarminDailyHealth(date, restingHeartRateBpm = bpm))
            }
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
    )

    private fun requireSuccess(response: GarminHttpResponse) {
        if (response.statusCode !in 200..299) throw GarminApiException(response.statusCode)
    }

    private fun JSONObject.number(keys: Set<String>): Double? {
        keys.forEach { key ->
            val value = opt(key)
            if (value is Number) return value.toDouble().takeIf(Double::isFinite)
        }
        val iterator = keys()
        while (iterator.hasNext()) {
            findNumber(opt(iterator.next()), keys)?.let { return it }
        }
        return null
    }

    private fun findNumber(value: Any?, keys: Set<String>): Double? = when (value) {
        is JSONObject -> value.number(keys)
        is JSONArray -> {
            var found: Double? = null
            for (index in 0 until value.length()) {
                found = findNumber(value.opt(index), keys)
                if (found != null) break
            }
            found
        }
        else -> null
    }

    private fun Any.forEachObject(action: (JSONObject) -> Unit) {
        when (this) {
            is JSONArray -> for (index in 0 until length()) optJSONObject(index)?.let(action)
            is JSONObject -> {
                val preferred = listOf("dailyHeartRateStats", "heartRateStats", "stats", "data")
                preferred.forEach { key -> optJSONArray(key)?.let { it.forEachObject(action) } }
                if (optString("calendarDate").isNotBlank()) action(this)
            }
        }
    }

    private companion object {
        const val API_BASE = "https://connectapi.garmin.com"
        const val REQUEST_PACING_MILLIS = 1_000L
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
        const val HTTP_NOT_FOUND = 404
        const val MIN_SLEEP_MINUTES = 60
        const val MAX_SLEEP_MINUTES = 24 * 60
        const val MIN_RESTING_BPM = 25
        const val MAX_RESTING_BPM = 250
        val SLEEP_SECONDS_KEYS = setOf("sleepTimeSeconds", "sleepDurationSeconds", "totalSleepSeconds")
        val SLEEP_MINUTES_KEYS = setOf("sleepTimeMinutes", "sleepDurationMinutes", "totalSleepMinutes")
        val RESTING_HEART_RATE_KEYS = setOf("restingHeartRate", "restingHeartRateBpm", "restingHr")
    }
}
