package de.run.app.domain.repository

import de.run.app.domain.model.TrainingGoal
import kotlinx.coroutines.flow.Flow

interface TrainingGoalRepository {
    fun observeHistory(): Flow<List<TrainingGoal>>
    suspend fun saveAsCurrent(goal: TrainingGoal)
    suspend fun update(goal: TrainingGoal)
}
