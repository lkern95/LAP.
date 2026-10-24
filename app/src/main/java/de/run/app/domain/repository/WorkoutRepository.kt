package de.run.app.domain.repository

import de.run.app.domain.model.Workout
import de.run.app.domain.model.WorkoutReadResult
import de.run.app.domain.model.WorkoutFeedback
import de.run.app.domain.model.WorkoutPlanMatch
import java.time.Instant
import kotlinx.coroutines.flow.Flow

interface WorkoutRepository {
    fun observeRecent(): Flow<List<Workout>>
    suspend fun refresh(
        from: Instant,
        to: Instant,
        readInBackground: Boolean = false
    ): WorkoutReadResult

    suspend fun saveFeedback(sourceId: String, feedback: WorkoutFeedback): Boolean

    suspend fun savePlanMatch(sourceId: String, match: WorkoutPlanMatch): Boolean = false
}
