package de.run.app.data.garmin

import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GarminConnectHealthClientTest {
    @Test
    fun readsDailySleepAndRestingHeartRateWithoutIncludingOutOfRangeDates() = runTest {
        val today = LocalDate.parse("2026-10-17")
        val heartRates = """[
            {"calendarDate":"2026-10-17","restingHeartRate":54},
            {"calendarDate":"2026-08-22","restingHeartRate":200}
        ]""".trimIndent().toByteArray()
        val sleepReplies = (6 downTo 0).map { offset ->
            if (offset == 0) """{"dailySleepDTO":{"sleepTimeSeconds":25200}}""".toByteArray()
            else "{}".toByteArray()
        }
        val transport = QueueTransport(listOf(heartRates) + sleepReplies)
        val result = GarminConnectHealthClient(transport, 0).readRecent(session(), today)

        assertEquals(1, result.size)
        assertEquals(today, result.single().date)
        assertEquals(420, result.single().sleepMinutes)
        assertEquals(54, result.single().restingHeartRateBpm)
        assertEquals(8, transport.urls.size)
        assert(transport.urls.first().contains("heartRate/daily/"))
        assert(transport.urls.last().contains("date=2026-10-17"))
    }

    @Test
    fun missingGarminMetricsRemainMissing() = runTest {
        val emptyHeartRates = "[]".toByteArray()
        val transport = QueueTransport(listOf(emptyHeartRates) + List(7) { "{}".toByteArray() })

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
