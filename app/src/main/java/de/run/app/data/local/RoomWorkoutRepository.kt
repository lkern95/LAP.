package de.run.app.data.local

import de.run.app.domain.model.Workout
import de.run.app.domain.model.WorkoutReadResult
import de.run.app.domain.model.WorkoutFeedback
import de.run.app.domain.model.WorkoutPlanMatch
import de.run.app.domain.repository.WorkoutRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomWorkoutRepository(
    private val dao: WorkoutDao,
    private val healthConnectReader: HealthConnectReader
) : WorkoutRepository {
    override fun observeRecent(): Flow<List<Workout>> = dao.observeRecent().map { records ->
        records.map(WorkoutEntity::toDomain)
    }

    override suspend fun refresh(
        from: Instant,
        to: Instant,
        readInBackground: Boolean
    ): WorkoutReadResult {
        return when (val result = healthConnectReader.readWorkouts(from, to, readInBackground)) {
            is WorkoutReadResult.Available -> {
                val saved = dao.upsertPreservingFeedback(result.records.map(WorkoutEntity::fromDomain))
                result.copy(records = saved.map(WorkoutEntity::toDomain))
            }
            else -> result
        }
    }

    override suspend fun saveFeedback(sourceId: String, feedback: WorkoutFeedback): Boolean =
        dao.saveFeedback(
            sourceId,
            feedback.perceivedEffort,
            feedback.runningAffectedBySymptoms?.name,
            feedback.recordedAtMillis
        ) > 0

    override suspend fun savePlanMatch(sourceId: String, match: WorkoutPlanMatch): Boolean {
        val existing = dao.getBySourceId(sourceId) ?: return false
        dao.upsertAll(listOf(WorkoutEntity.fromDomain(existing.toDomain().copy(planMatch = match))))
        return true
    }
}

interface HealthConnectReader {
    suspend fun readWorkouts(
        from: Instant,
        to: Instant,
        readInBackground: Boolean = false
    ): WorkoutReadResult
}
