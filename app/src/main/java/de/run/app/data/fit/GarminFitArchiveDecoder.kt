package de.run.app.data.fit

import de.run.app.domain.model.Workout
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.PushbackInputStream
import java.util.zip.ZipInputStream

internal class GarminFitArchiveDecoder(
    private val fitDecoder: GarminFitActivityDecoder = GarminFitActivityDecoder(),
    private val textDecoder: GarminTextActivityDecoder = GarminTextActivityDecoder(),
    private val temporaryDirectory: File = File(System.getProperty("java.io.tmpdir") ?: ".")
) {
    fun decode(
        input: InputStream,
        saveOriginal: (extension: String, bytes: ByteArray) -> Boolean = { _, _ -> true }
    ): FitArchiveDecodeResult {
        val scan = ArchiveScan(saveOriginal)
        return try {
            scan.readArchive(input, 0)
            scan.result()
        } catch (_: ArchiveTooLarge) {
            scan.skippedFiles += 1
            if (scan.validFiles == 0) FitArchiveDecodeResult.TooLarge else scan.result()
        } catch (_: Exception) {
            if (scan.validFiles == 0) FitArchiveDecodeResult.Invalid else scan.result().copy(skippedFiles = scan.skippedFiles + 1)
        }
    }

    private inner class ArchiveScan(private val saveOriginal: (String, ByteArray) -> Boolean) {
        val workouts = mutableListOf<Workout>()
        val unsupported = mutableMapOf<String, Int>()
        var validFiles = 0
        var savedOriginals = 0
        var skippedFiles = 0
        private var totalExpandedBytes = 0L
        private var totalEntries = 0

        fun result() = FitArchiveDecodeResult.Imported(
            workouts = workouts.distinctBy(Workout::sourceId),
            foundActivities = validFiles,
            savedOriginals = savedOriginals,
            skippedFiles = skippedFiles,
            unsupported = unsupported.toMap()
        )

        fun readArchive(input: InputStream, depth: Int) {
            if (depth > MAX_NESTING_DEPTH) throw ArchiveNestingTooDeep()
            val buffered = PushbackInputStream(input, ZIP_HEADER_BYTES)
            if (!buffered.hasZipSignature()) throw ArchiveInvalid()
            ZipInputStream(buffered).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    totalEntries += 1
                    if (totalEntries > MAX_ARCHIVE_ENTRIES) throw ArchiveTooLarge()
                    if (entry.isDirectory) continue
                    val extension = entry.name.substringAfterLast('.', "").lowercase()
                    when (extension) {
                        "zip" -> {
                            val nested = try {
                                zip.writeNestedArchive()
                            } catch (_: EntryTooLarge) {
                                skippedFiles += 1
                                continue
                            }
                            try {
                                FileInputStream(nested).use { readArchive(it, depth + 1) }
                            } catch (error: ArchiveTooLarge) {
                                throw error
                            } catch (_: Exception) {
                                skippedFiles += 1
                            } finally {
                                nested.delete()
                            }
                        }
                        "fit", "gpx", "tcx" -> {
                            val bytes = try {
                                zip.readEntry(MAX_FILE_BYTES)
                            } catch (_: EntryTooLarge) {
                                skippedFiles += 1
                                continue
                            }
                            importActivity(extension, bytes)
                        }
                        else -> {
                            runCatching { zip.readEntry(MAX_FILE_BYTES) }
                                .onFailure { if (it !is EntryTooLarge) throw it }
                            val category = unsupportedCategory(entry.name, extension)
                            unsupported[category] = (unsupported[category] ?: 0) + 1
                        }
                    }
                    zip.closeEntry()
                }
            }
        }

        private fun importActivity(extension: String, bytes: ByteArray) {
            val parsed = when (extension) {
                "fit" -> runCatching { fitDecoder.decode(bytes) }
                    .getOrNull()?.let { (it as? FitDecodeResult.Activities)?.workouts }
                "gpx", "tcx" -> textDecoder.decode(extension, bytes)
                else -> null
            }
            if (parsed.isNullOrEmpty() || !saveOriginal(extension, bytes)) {
                skippedFiles += 1
                return
            }
            validFiles += 1
            savedOriginals += 1
            workouts += parsed
        }

        private fun unsupportedCategory(path: String, extension: String): String {
            val normalized = path.lowercase()
            return when {
                "sleep" in normalized -> "Schlafdaten"
                "wellness" in normalized || "health" in normalized -> "Gesundheitsdaten"
                "weight" in normalized || "bodycomposition" in normalized -> "Körperwerte"
                extension in setOf("csv", "json", "xml") -> "Zusatzdaten"
                else -> "Weitere Dateitypen"
            }
        }

        private fun ZipInputStream.readEntry(maximumBytes: Long): ByteArray {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER_BYTES)
            var currentEntryBytes = 0L
            var entryTooLarge = false
            while (true) {
                val count = read(buffer)
                if (count < 0) break
                currentEntryBytes += count
                totalExpandedBytes += count
                if (totalExpandedBytes > MAX_ARCHIVE_EXPANDED_BYTES) throw ArchiveTooLarge()
                if (currentEntryBytes > maximumBytes) entryTooLarge = true
                if (!entryTooLarge) output.write(buffer, 0, count)
            }
            if (entryTooLarge) throw EntryTooLarge()
            return output.toByteArray()
        }

        private fun ZipInputStream.writeNestedArchive(): File {
            check(temporaryDirectory.isDirectory || temporaryDirectory.mkdirs())
            val file = File.createTempFile("lap-nested-", ".zip", temporaryDirectory)
            var nestedBytes = 0L
            var entryTooLarge = false
            try {
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        val count = read(buffer)
                        if (count < 0) break
                        nestedBytes += count
                        totalExpandedBytes += count
                        if (totalExpandedBytes > MAX_ARCHIVE_EXPANDED_BYTES) throw ArchiveTooLarge()
                        if (nestedBytes > MAX_NESTED_ARCHIVE_BYTES) entryTooLarge = true
                        if (!entryTooLarge) output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
                if (entryTooLarge) throw EntryTooLarge()
                return file
            } catch (error: Exception) {
                file.delete()
                throw error
            }
        }
    }

    private fun PushbackInputStream.hasZipSignature(): Boolean {
        val header = ByteArray(ZIP_HEADER_BYTES)
        var read = 0
        while (read < header.size) {
            val count = read(header, read, header.size - read)
            if (count < 0) break
            read += count
        }
        unread(header, 0, read)
        return read == header.size && header[0] == ZIP_SIGNATURE_FIRST && header[1] == ZIP_SIGNATURE_SECOND &&
            header[2] in ZIP_SIGNATURE_VARIANTS && header[3] in ZIP_SIGNATURE_TRAILERS[header[2]].orEmpty()
    }

    private class ArchiveTooLarge : Exception()
    private class ArchiveInvalid : Exception()
    private class ArchiveNestingTooDeep : Exception()
    private class EntryTooLarge : Exception()

    private companion object {
        const val BUFFER_BYTES = 8 * 1024
        const val ZIP_HEADER_BYTES = 4
        const val ZIP_SIGNATURE_FIRST = 0x50.toByte()
        const val ZIP_SIGNATURE_SECOND = 0x4B.toByte()
        val ZIP_SIGNATURE_VARIANTS = setOf(0x03.toByte(), 0x05.toByte(), 0x07.toByte())
        val ZIP_SIGNATURE_TRAILERS = mapOf(
            0x03.toByte() to setOf(0x04.toByte()),
            0x05.toByte() to setOf(0x06.toByte()),
            0x07.toByte() to setOf(0x08.toByte())
        )
        const val MAX_FILE_BYTES = 64L * 1024 * 1024
        const val MAX_NESTED_ARCHIVE_BYTES = 512L * 1024 * 1024
        const val MAX_ARCHIVE_EXPANDED_BYTES = 2L * 1024 * 1024 * 1024
        const val MAX_ARCHIVE_ENTRIES = 50_000
        const val MAX_NESTING_DEPTH = 4
    }
}

internal sealed interface FitArchiveDecodeResult {
    data class Imported(
        val workouts: List<Workout>,
        val foundActivities: Int,
        val savedOriginals: Int,
        val skippedFiles: Int,
        val unsupported: Map<String, Int>
    ) : FitArchiveDecodeResult

    data object Invalid : FitArchiveDecodeResult
    data object TooLarge : FitArchiveDecodeResult
}
