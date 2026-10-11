package de.run.app.data.fit

import com.garmin.fit.DateTime
import com.garmin.fit.Decode
import com.garmin.fit.File
import com.garmin.fit.FileEncoder
import com.garmin.fit.FileIdMesg
import com.garmin.fit.Mesg
import com.garmin.fit.MesgListener
import com.garmin.fit.MesgNum
import com.garmin.fit.SessionMesg
import com.garmin.fit.Sport
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GarminFitActivityDecoderTest {
    private val decoder = GarminFitActivityDecoder()

    @Test
    fun importsRunSummaryValuesFromFitActivity() {
        val result = decoder.decode(activityFile(Sport.RUNNING)) as FitDecodeResult.Runs
        val workout = result.workouts.single()

        assertEquals("Lauf", workout.title)
        assertEquals(5_000.0, workout.distanceMeters!!, 0.01)
        assertEquals(3_600L, workout.durationSeconds)
        assertEquals(148, workout.averageHeartRateBpm)
        assertEquals(172, workout.maximumHeartRateBpm)
        assertTrue(workout.sourceId.startsWith("fit:"))
        assertEquals(1_700_000_000_000L, workout.startedAtMillis)
    }

    @Test
    fun rejectsNonRunningActivity() {
        assertEquals(FitDecodeResult.NoRunningSession, decoder.decode(activityFile(Sport.CYCLING)))
    }

    @Test
    fun rejectsCorruptedFile() {
        val bytes = activityFile(Sport.RUNNING)
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0x01).toByte()

        assertEquals(FitDecodeResult.Invalid, decoder.decode(bytes))
    }

    private fun activityFile(sport: Sport): ByteArray {
        val file = kotlin.io.path.createTempFile(suffix = ".fit").toFile()
        try {
            val encoder = FileEncoder(file)
            encoder.write(FileIdMesg().apply {
                type = File.ACTIVITY
                manufacturer = 1
                timeCreated = DateTime(1_068_934_400L)
            })
            encoder.write(SessionMesg().apply {
                startTime = DateTime(1_068_934_400L)
                timestamp = DateTime(1_068_938_000L)
                this.sport = sport
                totalElapsedTime = 3_600f
                totalDistance = 5_000f
                avgHeartRate = 148.toShort()
                maxHeartRate = 172.toShort()
            })
            encoder.close()
            val bytes = file.readBytes()
            assertTrue(Decode().isFileFit(ByteArrayInputStream(bytes)))
            assertTrue(Decode().checkFileIntegrity(ByteArrayInputStream(bytes)))
            val messages = mutableListOf<Mesg>()
            Decode().read(ByteArrayInputStream(bytes), MesgListener { messages += it })
            assertTrue("FIT messages: ${messages.map { it.num }}", messages.any { it.num == MesgNum.FILE_ID })
            assertTrue("FIT messages: ${messages.map { it.num }}", messages.any { it.num == MesgNum.SESSION })
            val session = SessionMesg(messages.first { it.num == MesgNum.SESSION })
            assertEquals(sport, session.sport)
            assertEquals(3_600f, session.totalElapsedTime)
            assertNotNull(session.startTime?.date)
            assertEquals(5_000f, session.totalDistance)
            return bytes
        } finally {
            file.delete()
        }
    }
}
