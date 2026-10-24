package de.run.app.data.garmin

import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal data class GarminActivitySummary(
    val activityId: String,
    val startedAt: LocalDateTime?
)

private data class GarminActivityPage(val itemCount: Int, val activities: List<GarminActivitySummary>)

internal class GarminConnectActivityClient(
    private val transport: GarminConnectHttpTransport,
    private val requestPacingMillis: Long = PACING_DELAY_MILLIS
) {
    fun findActivityNear(session: GarminSession, target: LocalDateTime): GarminActivitySummary? {
        val date = target.toLocalDate().toString()
        val query = "start=0&limit=100&startDate=$date&endDate=$date&activityType=running"
        val response = transport.execute(
            "$API_BASE/activitylist-service/activities/search/activities?$query",
            "GET",
            headers = headers(session)
        )
        if (response.statusCode !in 200..299) throw GarminApiException(response.statusCode)
        return GarminTargetActivitySelector.select(parseActivityPage(response.body).activities, target)
    }

    suspend fun listAllActivities(session: GarminSession): List<GarminActivitySummary> {
        val seen = linkedMapOf<String, GarminActivitySummary>()
        var offset = 0
        while (true) {
            val response = transport.execute(
                "$API_BASE/activitylist-service/activities/search/activities?start=$offset&limit=$PAGE_SIZE",
                "GET",
                headers = headers(session)
            )
            if (response.statusCode !in 200..299) throw GarminApiException(response.statusCode)
            val page = parseActivityPage(response.body)
            if (page.itemCount == 0) return seen.values.toList()
            check(offset.toLong() + page.itemCount <= MAX_HISTORY_ENTRIES) { "activity_history_limit_reached" }
            val before = seen.size
            page.activities.forEach { seen.putIfAbsent(it.activityId, it) }
            if (page.activities.isNotEmpty() && seen.size == before) throw IllegalStateException("repeated_activity_page")
            offset += page.itemCount
            if (page.itemCount < PAGE_SIZE) return seen.values.toList()
            if (requestPacingMillis > 0) kotlinx.coroutines.delay(requestPacingMillis)
        }
    }

    fun downloadOriginal(session: GarminSession, activityId: String): ByteArray {
        require(activityId.all(Char::isDigit))
        val response = transport.execute(
            "$API_BASE/download-service/files/activity/$activityId",
            "GET",
            headers = headers(session),
            maximumBytes = MAX_DOWNLOAD_BYTES
        )
        if (response.statusCode !in 200..299) throw GarminApiException(response.statusCode)
        if (response.body.isEmpty()) throw IllegalStateException("empty_download")
        return response.body
    }

    private fun parseActivityPage(bytes: ByteArray): GarminActivityPage {
        val json = String(bytes, Charsets.UTF_8)
        val array = when {
            json.trimStart().startsWith("[") -> JSONArray(json)
            else -> JSONObject(json).getJSONArray("activityList")
        }
        val activities = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val activityId = item.optString("activityId").takeIf { it.isNotBlank() && it.all(Char::isDigit) && it.any { digit -> digit != '0' } } ?: continue
                val startedAt = parseLocalDateTime(item.optString("startTimeLocal"))
                add(GarminActivitySummary(activityId, startedAt))
            }
        }
        return GarminActivityPage(array.length(), activities)
    }

    private fun parseLocalDateTime(value: String): LocalDateTime? = runCatching {
        LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    }.recoverCatching {
        LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    }.getOrNull()

    private fun headers(session: GarminSession) = mapOf(
        "Authorization" to "Bearer ${session.accessToken}",
        "Accept" to "application/json, application/octet-stream, */*",
        "User-Agent" to "LAP/${BuildMetadata.VERSION} (Android)"
    )

    private object BuildMetadata {
        const val VERSION = "0.3.0"
    }

    private companion object {
        const val API_BASE = "https://connectapi.garmin.com"
        const val MAX_DOWNLOAD_BYTES = 64 * 1024 * 1024
        const val PAGE_SIZE = 100
        const val MAX_HISTORY_ENTRIES = 100_000
        const val PACING_DELAY_MILLIS = 1_000L
    }
}

internal object GarminTargetActivitySelector {
    fun select(
        activities: List<GarminActivitySummary>,
        target: LocalDateTime,
        toleranceMinutes: Long = TARGET_WINDOW_MINUTES
    ): GarminActivitySummary? {
        require(toleranceMinutes >= 0)
        return activities
            .asSequence()
            .filter { it.startedAt?.toLocalDate() == target.toLocalDate() }
            .mapNotNull { activity -> activity.startedAt?.let { activity to Duration.between(target, it).abs() } }
            .filter { (_, difference) -> difference <= Duration.ofMinutes(toleranceMinutes) }
            .minByOrNull { (_, difference) -> difference }
            ?.first
    }

    private const val TARGET_WINDOW_MINUTES = 30L
}

internal class GarminApiException(val statusCode: Int) : Exception()
