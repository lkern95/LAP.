package de.run.app.domain.repository

interface EvaluationReportRepository {
    suspend fun export(uri: String, archive: ByteArray): Boolean
}
