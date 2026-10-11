package de.run.app.data.local

import de.run.app.domain.model.ExerciseCompletion
import de.run.app.domain.repository.StrengthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomStrengthRepository(private val dao: StrengthCompletionDao) : StrengthRepository {
    override fun observeCompletions(): Flow<List<ExerciseCompletion>> = dao.observeAll().map { values ->
        values.map(StrengthCompletionEntity::toDomain)
    }

    override suspend fun complete(completion: ExerciseCompletion) {
        dao.upsert(StrengthCompletionEntity.fromDomain(completion))
    }
}
