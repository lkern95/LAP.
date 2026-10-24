package de.run.app.data.fit

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GarminOriginalActivityFileStoreTest {
    @Test
    fun storesOriginalBytesOnceUsingTheirSha256() {
        val root = Files.createTempDirectory("lap-original-test").toFile()
        try {
            val bytes = byteArrayOf(0, 1, 2, 3, 0x2e, 0x46)
            val store = GarminOriginalActivityFileStore(root)
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

            assertTrue(store.save("fit", bytes))
            assertTrue(store.save("fit", bytes))
            assertFalse(store.save("exe", bytes))

            val saved = File(root, "garmin-original-activities/$hash.fit")
            assertTrue(saved.isFile)
            assertArrayEquals(bytes, saved.readBytes())
            assertEquals(1, saved.parentFile!!.listFiles()!!.size)
        } finally {
            root.deleteRecursively()
        }
    }
}
