package de.run.app.domain.repository

import de.run.app.domain.model.TrainingProfile
import kotlinx.coroutines.flow.Flow

interface TrainingProfileRepository {
    fun observe(): Flow<TrainingProfile?>
    suspend fun save(profile: TrainingProfile)
}
