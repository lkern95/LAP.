package de.run.app.data.fit

import com.garmin.fit.DateTime
import com.garmin.fit.File
import com.garmin.fit.FileEncoder
import com.garmin.fit.FileIdMesg
import com.garmin.fit.SessionMesg
import com.garmin.fit.Sport
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GarminOriginalFitFileTest {
    @Test
    fun preservesRawFitBytesExactly() {
        val original = createFit()

        assertArrayEquals(original, GarminOriginalFitFile.extract(original))
    }

    @Test
    fun extractsSingleFitFromGarminZipWithoutChangingBytes() {
        val original = createFit()
        val archive = zip("DI_CONNECT/activity.fit" to original, "summary.txt" to byteArrayOf(1, 2, 3))

        assertArrayEquals(original, GarminOriginalFitFile.extract(archive))
    }

    @Test
    fun rejectsMultipleFitEntriesAndInvalidArchives() {
        val original = createFit()

        assertNull(GarminOriginalFitFile.extract(zip("a.fit" to original, "b.fit" to original)))
        assertNull(GarminOriginalFitFile.extract(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun calculatesStableSha256ForOriginalBytes() {
        val original = createFit()

        assertEquals(GarminOriginalFitFile.sha256(original), GarminOriginalFitFile.sha256(original.copyOf()))
    }

    private fun createFit(): ByteArray {
        val file = kotlin.io.path.createTempFile(suffix = ".fit").toFile()
        try {
            val encoder = FileEncoder(file)
            encoder.write(FileIdMesg().apply {
                type = File.ACTIVITY
                manufacturer = 1
                timeCreated = DateTime(1_700_000_000L)
            })
            encoder.write(SessionMesg().apply {
                startTime = DateTime(1_700_000_000L)
                timestamp = DateTime(1_700_003_600L)
                sport = Sport.RUNNING
                totalElapsedTime = 3_600f
                totalDistance = 5_000f
            })
            encoder.close()
            return file.readBytes()
        } finally {
            file.delete()
        }
    }

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
