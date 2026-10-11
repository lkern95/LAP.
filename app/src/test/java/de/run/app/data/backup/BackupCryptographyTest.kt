package de.run.app.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCryptographyTest {
    private val cryptography = BackupCryptography(iterations = 100_000)

    @Test
    fun encryptedArchiveRoundTripsAndAuthenticatesItsHeader() {
        val plain = "LAP-local-data".toByteArray()
        val archive = cryptography.seal(plain, "correct horse".toCharArray(), 2)

        assertArrayEquals(plain, cryptography.open(archive, "correct horse".toCharArray()).payload)
        val changed = archive.copyOf().also { it[12] = (it[12].toInt() xor 1).toByte() }
        assertThrows(Exception::class.java) { cryptography.open(changed, "correct horse".toCharArray()) }
    }

    @Test
    fun wrongPasswordAndDamagedContentsAreRejected() {
        val archive = cryptography.seal(byteArrayOf(1, 2, 3), "good-password".toCharArray(), 2)

        assertThrows(WrongPasswordOrDamagedFile::class.java) {
            cryptography.open(archive, "bad-password".toCharArray())
        }
        val damaged = archive.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 0x10).toByte() }
        assertThrows(WrongPasswordOrDamagedFile::class.java) {
            cryptography.open(damaged, "good-password".toCharArray())
        }
    }

    @Test
    fun rejectsUnknownFileVersionsAndInvalidPasswordLength() {
        val archive = cryptography.seal(byteArrayOf(1), "good-password".toCharArray(), 2)
        val future = archive.copyOf().also { it[15] = 99 }

        assertThrows(UnsupportedBackupFileVersion::class.java) {
            cryptography.open(future, "good-password".toCharArray())
        }
        assertThrows(IllegalArgumentException::class.java) {
            cryptography.seal(byteArrayOf(1), "short".toCharArray(), 2)
        }
        assertEquals(2, cryptography.open(archive, "good-password".toCharArray()).formatVersion)
    }
}
