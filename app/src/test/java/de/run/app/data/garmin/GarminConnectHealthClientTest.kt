package de.run.app.data.garmin

import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GarminConnectHealthClientTest {
    @Test
    fun readsGarminReadinessMetricsFromDateRanges() = runTest {
        val today = LocalDate.parse("2026-10-17")
        val heartRates = """[
            {"calendarDate":"2026-10-17","restingHeartRate":54},
            {"calendarDate":"2026-08-22","restingHeartRate":200}
        ]""".trimIndent().toByteArray()
        val sleep = """{"dailySleepSummaries":[{"calendarDate":"2026-10-17","sleepTimeSeconds":25200}]}""".toByteArray()
        val hrv = """{"hrvSummaries":[{"calendarDate":"2026-10-17","lastNightAvg":42,"weeklyAvg":44,"status":"BALANCED","baseline":{"balancedLow":38,"balancedUpper":50}}]}""".toByteArray()
        val stress = """[{"calendarDate":"2026-10-17","avgStressLevel":28}]""".toByteArray()
        val steps = """[{"calendarDate":"2026-10-17","totalSteps":6432}]""".toByteArray()
        val transport = QueueTransport(listOf(heartRates, sleep, hrv, stress, steps))
        val result = GarminConnectHealthClient(transport, 0).readRecent(session(), today)

        assertEquals(1, result.size)
        assertEquals(today, result.single().date)
        assertEquals(420, result.single().sleepMinutes)
        assertEquals(54, result.single().restingHeartRateBpm)
        assertEquals(42, result.single().hrvLastNightAverageMs)
        assertEquals("BALANCED", result.single().hrvStatus)
        assertEquals(28, result.single().averageStressLevel)
        assertEquals(6432, result.single().steps)
        assertEquals(5, transport.urls.size)
        assert(transport.urls.first().contains("heartRate/daily/"))
        assert(transport.urls[1].contains("startDate=2026-08-23"))
        assert(transport.urls[2].contains("hrv/daily/2026-08-23/2026-10-17"))
        assert(transport.urls[3].contains("stress/daily/2026-08-23/2026-10-17"))
        assert(transport.urls[4].contains("steps/daily/2026-08-23/2026-10-17"))
    }

    @Test
    fun missingGarminMetricsRemainMissing() = runTest {
        val emptyHeartRates = "[]".toByteArray()
        val transport = QueueTransport(listOf(emptyHeartRates, "{}".toByteArray(), "{}".toByteArray(), "[]".toByteArray(), "[]".toByteArray()))

        val result = GarminConnectHealthClient(transport, 0)
            .readRecent(session(), LocalDate.parse("2026-10-17"))

        assertNull(result.firstOrNull()?.sleepMinutes)
        assertNull(result.firstOrNull()?.restingHeartRateBpm)
        assertEquals(0, result.size)
    }

    private fun session() = GarminSession("access", "refresh", "client", Long.MAX_VALUE)

    private class QueueTransport(private val bodies: List<ByteArray>) : GarminConnectHttpTransport {
        private val responses = ArrayDeque(bodies)
        val urls = mutableListOf<String>()

        override fun execute(
            urlText: String,
            method: String,
            body: ByteArray?,
            headers: Map<String, String>,
            maximumBytes: Int
        ): GarminHttpResponse {
            urls += urlText
            return GarminHttpResponse(200, responses.removeFirst())
        }
    }
}
