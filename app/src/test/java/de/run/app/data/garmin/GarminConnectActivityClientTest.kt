package de.run.app.data.garmin

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GarminConnectActivityClientTest {
    @Test
    fun readsPaginatedHistoryWithoutFilteringActivityTypes() = runTest {
        val firstPage = activitiesJson(1, 100)
        val finalPage = activitiesJson(101, 2)
        val transport = QueueTransport(listOf(firstPage, finalPage))
        val client = GarminConnectActivityClient(transport, requestPacingMillis = 0)

        val activities = client.listAllActivities(session())

        assertEquals(102, activities.size)
        assertEquals("101", activities.first { it.activityId == "101" }.activityId)
        assertTrue(transport.urls[0].contains("start=0&limit=100"))
        assertTrue(transport.urls[1].contains("start=100&limit=100"))
    }

    @Test
    fun stopsOnEmptyHistoryPage() = runTest {
        val transport = QueueTransport(listOf("[]".toByteArray()))
        val activities = GarminConnectActivityClient(transport, 0).listAllActivities(session())
        assertTrue(activities.isEmpty())
        assertEquals(1, transport.urls.size)
    }

    @Test(expected = GarminApiException::class)
    fun rateLimitIsReturnedWithoutRetryOrBypass() = runTest {
        GarminConnectActivityClient(QueueTransport(status = 429), 0).listAllActivities(session())
    }

    private fun session() = GarminSession("access", "refresh", "client", Long.MAX_VALUE)

    private fun activitiesJson(start: Int, count: Int): ByteArray = buildString {
        append("{[0m\"activityList\":[".replace("\u001b[0m", ""))
        repeat(count) { index ->
            if (index > 0) append(',')
            val id = start + index
            val type = if (id % 2 == 0) "running" else "cycling"
            append("{\"activityId\":$id,\"activityType\":{\"typeKey\":\"$type\"},\"startTimeLocal\":\"2026-10-13T10:00:00\"}")
        }
        append("]}")
    }.toByteArray()

    private class QueueTransport(
        bodies: List<ByteArray> = emptyList(),
        private val status: Int = 200
    ) : GarminConnectHttpTransport {
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
            return GarminHttpResponse(status, if (status == 200) responses.removeFirst() else ByteArray(0))
        }
    }
}
