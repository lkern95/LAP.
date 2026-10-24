package de.run.app.data.local

import de.run.app.domain.model.StrengthSessionProgress
import de.run.app.domain.repository.StrengthSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomStrengthSessionRepository(private val dao: StrengthSessionDao) : StrengthSessionRepository {
    override fun observeSessions(): Flow<List<StrengthSessionProgress>> = dao.observeAll().map { rows ->
        rows.map(StrengthSessionEntity::toDomain)
    }

    override suspend fun getSession(sessionId: String): StrengthSessionProgress? = dao.get(sessionId)?.toDomain()

    override suspend fun save(session: StrengthSessionProgress) {
        dao.upsert(StrengthSessionEntity.fromDomain(session))
    }
}
