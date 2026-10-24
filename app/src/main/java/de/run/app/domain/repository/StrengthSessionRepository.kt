package de.run.app.domain.repository

import de.run.app.domain.model.StrengthSessionProgress
import kotlinx.coroutines.flow.Flow

interface StrengthSessionRepository {
    fun observeSessions(): Flow<List<StrengthSessionProgress>>
    suspend fun getSession(sessionId: String): StrengthSessionProgress?
    suspend fun save(session: StrengthSessionProgress)
}
