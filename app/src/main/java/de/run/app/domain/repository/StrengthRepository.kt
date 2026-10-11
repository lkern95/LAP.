package de.run.app.domain.repository

import de.run.app.domain.model.ExerciseCompletion
import kotlinx.coroutines.flow.Flow

interface StrengthRepository {
    fun observeCompletions(): Flow<List<ExerciseCompletion>>
    suspend fun complete(completion: ExerciseCompletion)
}
