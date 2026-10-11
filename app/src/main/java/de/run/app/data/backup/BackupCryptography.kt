package de.run.app.data.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import de.run.app.domain.repository.AppBackupRepository
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupCryptography(
    private val random: SecureRandom = SecureRandom(),
    private val iterations: Int = DEFAULT_ITERATIONS
) {
    init {
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS)
    }

    internal fun seal(payload: ByteArray, password: CharArray, formatVersion: Int): ByteArray {
        require(password.size >= AppBackupRepository.MIN_PASSWORD_LENGTH)
        require(payload.size <= MAX_PAYLOAD_BYTES)
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val encryptedSize = payload.size + GCM_TAG_BYTES
        val header = header(formatVersion, iterations, salt, iv, encryptedSize)
        val encrypted = cipher(Cipher.ENCRYPT_MODE, password, salt, iv, iterations, header).doFinal(payload)
        return ByteArrayOutputStream(header.size + encrypted.size).use { output ->
            output.write(header)
            output.write(encrypted)
            output.toByteArray()
        }
    }

    internal fun open(file: ByteArray, password: CharArray): DecryptedBackup {
        if (file.size !in MIN_FILE_BYTES..MAX_FILE_BYTES) throw InvalidBackupFile()
        val input = DataInputStream(ByteArrayInputStream(file))
        val magic = ByteArray(MAGIC.size).also(input::readFully)
        if (!magic.contentEquals(MAGIC)) throw InvalidBackupFile()
        val envelopeVersion = input.readInt()
        if (envelopeVersion != ENVELOPE_VERSION) throw UnsupportedBackupFileVersion()
        val formatVersion = input.readInt()
        if (formatVersion !in MIN_FORMAT_VERSION..BackupPayloadCodec.CURRENT_VERSION) {
            throw UnsupportedBackupFileVersion()
        }
        val rounds = input.readInt()
        if (rounds !in MIN_ITERATIONS..MAX_ITERATIONS) throw InvalidBackupFile()
        val salt = ByteArray(SALT_BYTES).also(input::readFully)
        val iv = ByteArray(IV_BYTES).also(input::readFully)
        val cipherSize = input.readInt()
        if (cipherSize !in GCM_TAG_BYTES..MAX_PAYLOAD_BYTES + GCM_TAG_BYTES || cipherSize != input.available()) {
            throw InvalidBackupFile()
        }
        val header = header(formatVersion, rounds, salt, iv, cipherSize)
        val encrypted = ByteArray(cipherSize).also(input::readFully)
        val payload = try {
            cipher(Cipher.DECRYPT_MODE, password, salt, iv, rounds, header).doFinal(encrypted)
        } catch (_: AEADBadTagException) {
            throw WrongPasswordOrDamagedFile()
        } catch (_: Exception) {
            throw WrongPasswordOrDamagedFile()
        }
        return DecryptedBackup(formatVersion, payload)
    }

    private fun cipher(mode: Int, password: CharArray, salt: ByteArray, iv: ByteArray, rounds: Int, aad: ByteArray): Cipher {
        val spec = PBEKeySpec(password, salt, rounds, KEY_BITS)
        val keyBytes = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(TAG_BITS, iv))
            updateAAD(aad)
            keyBytes.fill(0)
        }
    }

    private fun header(formatVersion: Int, rounds: Int, salt: ByteArray, iv: ByteArray, cipherSize: Int) =
        ByteArrayOutputStream(HEADER_BYTES).use { bytes ->
            DataOutputStream(bytes).use { out ->
                out.write(MAGIC); out.writeInt(ENVELOPE_VERSION); out.writeInt(formatVersion)
                out.writeInt(rounds); out.write(salt); out.write(iv); out.writeInt(cipherSize)
            }
            bytes.toByteArray()
        }

    companion object {
        private const val KEY_BITS = 256
        private const val TAG_BITS = 128
        private const val GCM_TAG_BYTES = TAG_BITS / 8
        private const val SALT_BYTES = 16
        private const val IV_BYTES = 12
        private const val ENVELOPE_VERSION = 1
        private const val MIN_FORMAT_VERSION = 1
        private const val DEFAULT_ITERATIONS = 310_000
        private const val MIN_ITERATIONS = 100_000
        private const val MAX_ITERATIONS = 1_000_000
        const val MAX_PAYLOAD_BYTES = 32 * 1024 * 1024
        private const val HEADER_BYTES = 8 + 4 + 4 + 4 + SALT_BYTES + IV_BYTES + 4
        const val MAX_FILE_BYTES = MAX_PAYLOAD_BYTES + HEADER_BYTES + GCM_TAG_BYTES
        private const val MIN_FILE_BYTES = HEADER_BYTES + GCM_TAG_BYTES
        private val MAGIC = "LAPSAFE1".toByteArray(Charsets.US_ASCII)
    }
}

internal data class DecryptedBackup(val formatVersion: Int, val payload: ByteArray)
internal class InvalidBackupFile : Exception()
internal class UnsupportedBackupFileVersion : Exception()
internal class WrongPasswordOrDamagedFile : Exception()
