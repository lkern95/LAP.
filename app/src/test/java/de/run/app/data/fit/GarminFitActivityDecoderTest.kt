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
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GarminFitActivityDecoderTest {
    private val decoder = GarminFitActivityDecoder()

    @Test
    fun importsRunSummaryValuesFromFitActivity() {
        val result = decoder.decode(activityFile(Sport.RUNNING)) as FitDecodeResult.Activities
        val workout = result.workouts.single()

        assertEquals("Lauf", workout.title)
        assertEquals(5_000.0, workout.distanceMeters!!, 0.01)
        assertEquals(3_600L, workout.durationSeconds)
        assertEquals(148, workout.averageHeartRateBpm)
        assertEquals(172, workout.maximumHeartRateBpm)
        assertEquals(82, workout.averageCadenceRpm)
        assertTrue(workout.sourceId.startsWith("fit:"))
        assertEquals(1_700_000_000_000L, workout.startedAtMillis)
    }

    @Test
    fun importsCyclingActivityWithItsActivityType() {
        val workout = (decoder.decode(activityFile(Sport.CYCLING)) as FitDecodeResult.Activities).workouts.single()
        assertEquals("Radfahren", workout.title)
        assertEquals(de.run.app.domain.model.WorkoutActivityType.CYCLING, workout.activityType)
    }

    @Test
    fun rejectsCorruptedFile() {
        val bytes = activityFile(Sport.RUNNING)
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0x01).toByte()

        assertEquals(FitDecodeResult.Invalid, decoder.decode(bytes))
    }

    @Test
    fun importsActivitiesFromGarminArchiveAndReportsUnsupportedEntries() {
        val archive = archiveOf(
            "DI_CONNECT/DI-Connect-Fitness-Uploaded-Files/run-a.fit" to activityFile(Sport.RUNNING),
            "DI_CONNECT/DI-Connect-Fitness-Uploaded-Files/run-b.fit" to activityFile(Sport.RUNNING, 1_068_934_401L),
            "DI_CONNECT/summary.csv" to "metadata".toByteArray()
        )

        val result = GarminFitArchiveDecoder(decoder).decode(ByteArrayInputStream(archive))

        assertTrue(result is FitArchiveDecodeResult.Imported)
        assertEquals(2, (result as FitArchiveDecodeResult.Imported).foundActivities)
        assertEquals(2, result.workouts.size)
        assertEquals(2, result.savedOriginals)
        assertEquals(1, result.unsupported["Zusatzdaten"])
    }

    @Test
    fun importsNestedZipArchivesWithoutUnpackingToDisk() {
        val inner = archiveOf("activity.fit" to activityFile(Sport.WALKING))
        val outer = archiveOf("DI_CONNECT/Uploaded.zip" to inner)
        val result = GarminFitArchiveDecoder(decoder).decode(ByteArrayInputStream(outer)) as FitArchiveDecodeResult.Imported
        assertEquals(1, result.workouts.size)
        assertEquals(de.run.app.domain.model.WorkoutActivityType.WALKING, result.workouts.single().activityType)
    }

    @Test
    fun keepsValidActivitiesWhenOneArchiveEntryIsCorrupt() {
        val corruptFit = activityFile(Sport.RUNNING).also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        val archive = archiveOf(
            "valid.fit" to activityFile(Sport.RUNNING),
            "corrupt.fit" to corruptFit
        )

        val result = GarminFitArchiveDecoder(decoder).decode(ByteArrayInputStream(archive)) as FitArchiveDecodeResult.Imported
        assertEquals(1, result.workouts.size)
        assertEquals(1, result.skippedFiles)
    }

    @Test
    fun rejectsInvalidZipInsteadOfImportingPartialFiles() {
        assertEquals(
            FitArchiveDecodeResult.Invalid,
            GarminFitArchiveDecoder(decoder).decode(ByteArrayInputStream(byteArrayOf(1, 2, 3)))
        )
    }

    @Test
    fun importsGpxActivityWithDistanceAndTime() {
        val gpx = """<?xml version="1.0"?><gpx><trk><trkseg>
            <trkpt lat="52.0" lon="13.0"><time>2026-10-13T16:12:00Z</time></trkpt>
            <trkpt lat="52.001" lon="13.0"><time>2026-10-13T16:13:00Z</time></trkpt>
            </trkseg></trk></gpx>""".trimIndent().toByteArray()
        val workout = GarminTextActivityDecoder().decode("gpx", gpx)!!.single()
        assertEquals(java.time.Instant.parse("2026-10-13T16:12:00Z").toEpochMilli(), workout.startedAtMillis)
        assertEquals(60L, workout.durationSeconds)
        assertTrue(workout.distanceMeters!! > 100.0)
        assertEquals(de.run.app.domain.model.WorkoutActivityType.OTHER, workout.activityType)
    }

    @Test
    fun rejectsXmlWithExternalEntities() {
        val xml = """<!DOCTYPE gpx [<!ENTITY x SYSTEM "file:///etc/passwd">]><gpx>&x;</gpx>""".toByteArray()
        assertEquals(null, GarminTextActivityDecoder().decode("gpx", xml))
    }

    private fun activityFile(sport: Sport, startTimeSeconds: Long = 1_068_934_400L): ByteArray {
        val file = kotlin.io.path.createTempFile(suffix = ".fit").toFile()
        try {
            val encoder = FileEncoder(file)
            encoder.write(FileIdMesg().apply {
                type = File.ACTIVITY
                manufacturer = 1
                timeCreated = DateTime(startTimeSeconds)
            })
            encoder.write(SessionMesg().apply {
                startTime = DateTime(startTimeSeconds)
                timestamp = DateTime(startTimeSeconds + 3_600L)
                this.sport = sport
                totalElapsedTime = 3_600f
                totalDistance = 5_000f
                avgHeartRate = 148.toShort()
                maxHeartRate = 172.toShort()
                avgRunningCadence = 82.toShort()
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

    private fun archiveOf(vararg entries: Pair<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                entries.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            output.toByteArray()
        }
}
