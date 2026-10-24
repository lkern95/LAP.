package de.run.app.data.fit

import androidx.room.withTransaction
import de.run.app.data.local.GarminFitFileEntity
import de.run.app.data.local.RunDatabase
import de.run.app.data.local.WorkoutEntity
import de.run.app.domain.model.Workout
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class AndroidGarminFitImportRepository(
    private val database: RunDatabase,
    private val filesDirectory: File,
    private val decoder: GarminFitActivityDecoder = GarminFitActivityDecoder()
) {
    suspend fun import(activityId: String, downloaded: ByteArray): GarminFitImportResult = withContext(Dispatchers.IO) {
        if (activityId.isBlank() || activityId.any { !it.isDigit() }) return@withContext GarminFitImportResult.Failed
        val fitBytes = GarminOriginalFitFile.extract(downloaded) ?: return@withContext GarminFitImportResult.InvalidFile
        val decoded = decoder.decode(fitBytes)
        if (decoded !is FitDecodeResult.Activities) return@withContext GarminFitImportResult.InvalidFile
        val sha256 = GarminOriginalFitFile.sha256(fitBytes)
        val originalFile = persistOriginal(sha256, fitBytes) ?: return@withContext GarminFitImportResult.Failed
        val fileDao = database.garminFitFileDao()
        val previousId = fileDao.byActivityId(activityId)
        if (previousId != null) {
            if (previousId.sha256 != sha256) return@withContext GarminFitImportResult.Conflict
            val existing = storedWorkout(previousId.canonicalWorkoutSourceId, activityId, sha256)
            return@withContext existing?.let { GarminFitImportResult.AlreadyImported(it.toDomain(), sha256) }
                ?: GarminFitImportResult.Failed
        }

        val knownFile = fileDao.bySha256(sha256)
        val workouts = decoded.workouts.mapIndexed { index, workout ->
            val fitImport = database.workoutDao().getBySourceId("fit:$sha256:$index")
            val knownGarminImport = knownFile?.let {
                database.workoutDao().getBySourceId("garmin:${it.activityId}:$index")
            }
            (fitImport ?: knownGarminImport)?.toDomain() ?: workout.copy(
                sourceId = "garmin:$activityId:$index",
                sourceName = GARMIN_SOURCE
            )
        }
        val saved = runCatching {
            database.withTransaction {
                val saveSummary = database.workoutDao().saveImportPreservingFeedback(workouts.map(WorkoutEntity::fromDomain))
                database.garminFitFileDao().save(
                    GarminFitFileEntity(
                        activityId = activityId,
                        sha256 = sha256,
                        fileName = originalFile.name,
                        byteCount = fitBytes.size.toLong(),
                        savedAtMillis = System.currentTimeMillis(),
                        canonicalWorkoutSourceId = saveSummary.workouts.firstOrNull()?.sourceId
                    )
                )
                (saveSummary.workouts.firstOrNull()?.toDomain() ?: error("missing_imported_workout")) to saveSummary.insertedCount
            }
        }.getOrNull() ?: return@withContext GarminFitImportResult.Failed
        val (workout, insertedCount) = saved
        if (knownFile != null || insertedCount == 0) GarminFitImportResult.AlreadyImported(workout, sha256)
        else GarminFitImportResult.Imported(workout, sha256)
    }

    private suspend fun storedWorkout(canonicalSourceId: String?, activityId: String, sha256: String): WorkoutEntity? {
        canonicalSourceId?.let { database.workoutDao().getBySourceId(it)?.let { record -> return record } }
        database.workoutDao().getBySourceId("garmin:$activityId:0")?.let { return it }
        return database.workoutDao().getBySourceId("fit:$sha256:0")
    }

    private fun persistOriginal(sha256: String, bytes: ByteArray): File? = runCatching {
        val directory = File(filesDirectory, ORIGINAL_DIRECTORY)
        check(directory.isDirectory || directory.mkdirs())
        val destination = File(directory, "$sha256.fit")
        if (destination.exists()) {
            check(GarminOriginalFitFile.sha256(destination.readBytes()) == sha256)
            return destination
        }
        val temporary = File.createTempFile("lap-fit-", ".tmp", directory)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            check(temporary.renameTo(destination) || (destination.exists() && GarminOriginalFitFile.sha256(destination.readBytes()) == sha256))
            destination
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }.getOrNull()

    private companion object {
        const val GARMIN_SOURCE = "Garmin Connect"
        const val ORIGINAL_DIRECTORY = "garmin-original-fit"
    }
}

internal object GarminOriginalFitFile {
    fun extract(download: ByteArray): ByteArray? {
        if (download.size > MAX_DOWNLOAD_BYTES) return null
        if (download.hasFitHeader()) return download
        if (!download.hasZipHeader()) return null
        return runCatching {
            var extracted: ByteArray? = null
            var expandedBytes = 0L
            var entries = 0
            ZipInputStream(download.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries += 1
                    check(entries <= MAX_ZIP_ENTRIES)
                    val isFit = !entry.isDirectory && entry.name.substringAfterLast('/').endsWith(".fit", ignoreCase = true)
                    val output = if (isFit) ByteArrayOutputStream() else null
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        expandedBytes += count
                        check(expandedBytes <= MAX_EXPANDED_BYTES)
                        output?.write(buffer, 0, count)
                    }
                    if (isFit) {
                        check(extracted == null)
                        extracted = output!!.toByteArray()
                    }
                    zip.closeEntry()
                }
            }
            extracted
        }.getOrNull()?.takeIf { it.hasFitHeader() }
    }

    private fun ByteArray.hasFitHeader(): Boolean =
        size >= MIN_FIT_BYTES && (this[0].toInt() and 0xff) in setOf(12, 14) &&
            this[8] == FIT_SIGNATURE_FIRST && this[9] == FIT_SIGNATURE_SECOND

    private fun ByteArray.hasZipHeader(): Boolean =
        size >= 4 && this[0] == ZIP_SIGNATURE_FIRST && this[1] == ZIP_SIGNATURE_SECOND

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private const val MAX_DOWNLOAD_BYTES = 64 * 1024 * 1024
    private const val MAX_EXPANDED_BYTES = 64L * 1024 * 1024
    private const val MAX_ZIP_ENTRIES = 512
    private const val BUFFER_BYTES = 8 * 1024
    private const val MIN_FIT_BYTES = 14
    private val FIT_SIGNATURE_FIRST = 0x2E.toByte()
    private val FIT_SIGNATURE_SECOND = 0x46.toByte()
    private val ZIP_SIGNATURE_FIRST = 0x50.toByte()
    private val ZIP_SIGNATURE_SECOND = 0x4B.toByte()
}

internal sealed interface GarminFitImportResult {
    data class Imported(val workout: Workout, val sha256: String) : GarminFitImportResult
    data class AlreadyImported(val workout: Workout, val sha256: String) : GarminFitImportResult
    data object InvalidFile : GarminFitImportResult
    data object Conflict : GarminFitImportResult
    data object Failed : GarminFitImportResult
}
