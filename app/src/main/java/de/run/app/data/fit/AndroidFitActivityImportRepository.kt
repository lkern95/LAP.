package de.run.app.data.fit

import android.content.ContentResolver
import android.net.Uri
import androidx.room.withTransaction
import de.run.app.data.local.RunDatabase
import de.run.app.data.local.WorkoutEntity
import de.run.app.domain.repository.FitActivityImportRepository
import de.run.app.domain.repository.FitActivityImportResult
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidFitActivityImportRepository internal constructor(
    private val contentResolver: ContentResolver,
    private val database: RunDatabase,
    private val decoder: GarminFitActivityDecoder = GarminFitActivityDecoder()
) : FitActivityImportRepository {
    override suspend fun importFile(uri: String): FitActivityImportResult = withContext(Dispatchers.IO) {
        val bytes = try {
            readBounded(Uri.parse(uri)) ?: return@withContext FitActivityImportResult.Failed
        } catch (_: FitFileTooLarge) {
            return@withContext FitActivityImportResult.FileTooLarge
        } catch (_: Exception) {
            return@withContext FitActivityImportResult.Failed
        }

        when (val result = runCatching { decoder.decode(bytes) }.getOrElse { FitDecodeResult.Invalid }) {
            is FitDecodeResult.Runs -> {
                database.withTransaction {
                    database.workoutDao().upsertPreservingFeedback(result.workouts.map(WorkoutEntity::fromDomain))
                }
                FitActivityImportResult.Imported(result.workouts)
            }
            FitDecodeResult.NotActivityFile -> FitActivityImportResult.NotActivityFile
            FitDecodeResult.NoRunningSession -> FitActivityImportResult.NoRunningSession
            FitDecodeResult.Invalid -> FitActivityImportResult.InvalidFile
        }
    }

    private fun readBounded(uri: Uri): ByteArray? {
        val stream = contentResolver.openInputStream(uri) ?: return null
        return stream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER_SIZE)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > MAX_FILE_BYTES) throw FitFileTooLarge()
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }

    private class FitFileTooLarge : Exception()

    private companion object {
        const val MAX_FILE_BYTES = 50 * 1024 * 1024
        const val BUFFER_SIZE = 8 * 1024
    }
}
