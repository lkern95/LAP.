package de.run.app.data.fit

import android.content.ContentResolver
import android.net.Uri
import androidx.room.withTransaction
import de.run.app.data.local.RunDatabase
import de.run.app.data.local.WorkoutEntity
import de.run.app.domain.repository.FitActivityImportRepository
import de.run.app.domain.repository.FitActivityImportResult
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidFitActivityImportRepository internal constructor(
    private val contentResolver: ContentResolver,
    private val database: RunDatabase,
    private val filesDirectory: File,
    private val temporaryDirectory: File = File(filesDirectory, IMPORT_TEMP_DIRECTORY),
    private val decoder: GarminFitActivityDecoder = GarminFitActivityDecoder(),
    private val archiveDecoder: GarminFitArchiveDecoder = GarminFitArchiveDecoder(decoder, temporaryDirectory = temporaryDirectory)
) : FitActivityImportRepository {
    private val textDecoder = GarminTextActivityDecoder()
    private val originalFiles = GarminOriginalActivityFileStore(filesDirectory)

    override suspend fun importFile(uri: String): FitActivityImportResult = withContext(Dispatchers.IO) {
        val stream = try {
            contentResolver.openInputStream(Uri.parse(uri))?.buffered()
                ?: return@withContext FitActivityImportResult.Failed
        } catch (_: Exception) {
            return@withContext FitActivityImportResult.Failed
        }

        val result = try {
            stream.use { input ->
                if (input.isZipArchive()) {
                    when (val archive = archiveDecoder.decode(input, originalFiles::save)) {
                        is FitArchiveDecodeResult.Imported -> FitActivityImportResult.Imported(
                            workouts = archive.workouts,
                            foundActivities = archive.foundActivities,
                            savedOriginalFiles = archive.savedOriginals,
                            skippedFiles = archive.skippedFiles,
                            unsupportedFiles = archive.unsupported
                        ).also { imported ->
                            if (imported.workouts.isNotEmpty()) saveWorkouts(imported.workouts)
                        }
                        FitArchiveDecodeResult.Invalid -> FitActivityImportResult.InvalidFile
                        FitArchiveDecodeResult.TooLarge -> FitActivityImportResult.FileTooLarge
                    }
                } else {
                    importSingleActivity(input.readBounded(), documentExtension(uri))
                }
            }
        } catch (_: FitFileTooLarge) {
            FitActivityImportResult.FileTooLarge
        } catch (_: Exception) {
            FitActivityImportResult.Failed
        }
        result
    }

    private suspend fun importSingleActivity(bytes: ByteArray, extension: String): FitActivityImportResult {
        val parsed = when (extension) {
            "gpx", "tcx" -> textDecoder.decode(extension, bytes)
                ?.takeIf { it.isNotEmpty() }
                ?: return FitActivityImportResult.InvalidFile
            else -> when (val decoded = runCatching { decoder.decode(bytes) }.getOrElse { FitDecodeResult.Invalid }) {
                is FitDecodeResult.Activities -> decoded.workouts
                FitDecodeResult.NoActivitySessions -> return FitActivityImportResult.NoActivitySessions
                FitDecodeResult.NotActivityFile, FitDecodeResult.Invalid -> return FitActivityImportResult.InvalidFile
            }
        }
        if (!originalFiles.save(extension.ifBlank { "fit" }, bytes)) return FitActivityImportResult.Failed
        val origin = if (extension == "fit") "FIT-Datei" else "Garmin-Backup"
        return saveWorkouts(
            parsed.map { it.copy(sourceName = origin) },
            foundActivities = 1,
            savedOriginalFiles = 1
        )
    }

    private suspend fun saveWorkouts(
        workouts: List<de.run.app.domain.model.Workout>,
        foundActivities: Int = workouts.size,
        savedOriginalFiles: Int = 0,
        skippedFiles: Int = 0,
        unsupportedFiles: Map<String, Int> = emptyMap()
    ): FitActivityImportResult.Imported {
        val saved = database.withTransaction {
            database.workoutDao().saveImportPreservingFeedback(workouts.map(WorkoutEntity::fromDomain))
        }
        return FitActivityImportResult.Imported(
            workouts = saved.workouts.map(WorkoutEntity::toDomain),
            foundActivities = foundActivities,
            newlyImportedActivities = saved.insertedCount,
            alreadyPresentActivities = saved.alreadyPresentCount,
            savedOriginalFiles = savedOriginalFiles,
            skippedFiles = skippedFiles,
            unsupportedFiles = unsupportedFiles
        )
    }

    private fun documentExtension(uri: String): String = runCatching {
        contentResolver.query(Uri.parse(uri), arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use "fit"
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex < 0) "fit" else cursor.getString(nameIndex).substringAfterLast('.', "").lowercase()
            }
    }.getOrNull()?.takeIf { it in setOf("fit", "gpx", "tcx") } ?: "fit"

    private fun InputStream.isZipArchive(): Boolean {
        mark(ZIP_SIGNATURE_BYTES)
        val first = read()
        val second = read()
        reset()
        return first == ZIP_SIGNATURE_FIRST && second == ZIP_SIGNATURE_SECOND
    }

    private fun InputStream.readBounded(): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_FIT_BYTES) throw FitFileTooLarge()
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private class FitFileTooLarge : Exception()

    private companion object {
        const val MAX_FIT_BYTES = 64L * 1024 * 1024
        const val BUFFER_SIZE = 8 * 1024
        const val ZIP_SIGNATURE_BYTES = 2
        const val ZIP_SIGNATURE_FIRST = 0x50
        const val ZIP_SIGNATURE_SECOND = 0x4B
        const val IMPORT_TEMP_DIRECTORY = "import-cache"
    }
}
