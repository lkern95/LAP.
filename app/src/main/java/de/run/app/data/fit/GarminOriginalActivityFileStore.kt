package de.run.app.data.fit

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

internal class GarminOriginalActivityFileStore(private val filesDirectory: File) {
    fun save(extension: String, bytes: ByteArray): Boolean {
        if (extension !in SUPPORTED_EXTENSIONS || bytes.isEmpty()) return false
        return runCatching {
            val directory = File(filesDirectory, DIRECTORY)
            check(directory.isDirectory || directory.mkdirs())
            val digest = sha256(bytes)
            val destination = File(directory, "$digest.$extension")
            if (destination.exists()) return sha256(destination.readBytes()) == digest
            val temporary = File.createTempFile("lap-original-", ".tmp", directory)
            try {
                FileOutputStream(temporary).use { output ->
                    output.write(bytes)
                    output.fd.sync()
                }
                temporary.renameTo(destination) || (destination.exists() && sha256(destination.readBytes()) == digest)
            } finally {
                if (temporary.exists()) temporary.delete()
            }
        }.getOrDefault(false)
    }

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val DIRECTORY = "garmin-original-activities"
        val SUPPORTED_EXTENSIONS = setOf("fit", "gpx", "tcx")
    }
}
