package de.run.app.data.local

import android.content.ContentResolver
import android.net.Uri
import de.run.app.domain.repository.EvaluationReportRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Writes a user-requested report through Android's selected document destination. */
class AndroidEvaluationReportRepository(
    private val resolver: ContentResolver
) : EvaluationReportRepository {
    override suspend fun export(uri: String, archive: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            val output = resolver.openOutputStream(Uri.parse(uri), "wt") ?: return@withContext false
            output.use { it.write(archive) }
            true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
    }
}
