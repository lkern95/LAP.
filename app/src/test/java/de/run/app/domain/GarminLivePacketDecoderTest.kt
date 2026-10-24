package de.run.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GarminLivePacketDecoderTest {
    private val now = 1_800_000_000_000L

    @Test
    fun decodesTimerDistancePaceHeartRateAndPausedState() {
        val packet = GarminLivePacket.decode(sample(), now)!!
        val metrics = packet.toMetrics()

        assertEquals(123L, packet.sequence)
        assertEquals(5_400_000L, packet.timerMillis)
        assertEquals(3_210.5, metrics.distanceMeters!!, 0.001)
        assertEquals(360, metrics.paceSecondsPerKilometer)
        assertEquals(148, metrics.heartRateBpm)
        assertEquals(LiveMetricSource.GARMIN, metrics.source)
        assertFalse(metrics.isPaused)
    }

    @Test
    fun missingSensorValuesRemainUnavailableAndPausedRunCannotCue() {
        val values = sample().toMutableList().apply {
            this[4] = -1.0
            this[5] = -1.0
            this[6] = -1
            this[7] = 2
        }
        val metrics = GarminLivePacket.decode(values, now)!!.toMetrics()

        assertNull(metrics.distanceMeters)
        assertNull(metrics.paceSecondsPerKilometer)
        assertNull(metrics.heartRateBpm)
        assertTrue(metrics.isPaused)
        assertNull(LiveRunCoach().nextCue(metrics, LiveRunTargets(360, 390, 150), now))
    }

    @Test
    fun rejectsUnknownVersionMalformedAndStalePackets() {
        val unknownVersion = sample().toMutableList().apply { this[0] = 2 }
        val malformed = sample().dropLast(1)
        val staleNow = now + 16_000

        assertNull(GarminLivePacket.decode(unknownVersion, now))
        assertNull(GarminLivePacket.decode(malformed, now))
        assertNull(GarminLivePacket.decode(sample(), staleNow))
    }

    @Test
    fun sequenceGateDropsDuplicatesAndOutOfOrderSamplesButAcceptsNewActivity() {
        val gate = GarminLivePacketSequenceGate()
        val first = GarminLivePacket.decode(sample(), now)!!
        val duplicate = first.copy(receivedAtMillis = now + 1)
        val newer = first.copy(sequence = first.sequence + 1, watchTimeSeconds = first.watchTimeSeconds + 1)
        val newActivity = newer.copy(
            sequence = 0,
            watchTimeSeconds = newer.watchTimeSeconds + 1,
            timerMillis = 0
        )

        assertTrue(gate.accept(first))
        assertFalse(gate.accept(duplicate))
        assertTrue(gate.accept(newer))
        assertTrue(gate.accept(newActivity))
    }

    private fun sample(): List<Any> = listOf(
        GarminLivePacket.PROTOCOL_VERSION,
        123,
        now / 1_000,
        5_400_000,
        3_210.5,
        1000.0 / 360.0,
        148,
        GarminLivePacket.TIMER_STATE_ON
    )
}
