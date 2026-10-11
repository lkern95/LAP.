package de.run.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workouts ORDER BY startedAtMillis DESC")
    fun observeRecent(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts ORDER BY startedAtMillis DESC")
    suspend fun getAllForBackup(): List<WorkoutEntity>

    @Upsert
    suspend fun upsertAll(workouts: List<WorkoutEntity>)

    @Query("DELETE FROM workouts")
    suspend fun deleteAll()

    @Query("SELECT * FROM workouts WHERE sourceId = :sourceId LIMIT 1")
    suspend fun getBySourceId(sourceId: String): WorkoutEntity?

    @Query("UPDATE workouts SET perceivedEffort = :perceivedEffort, runningAffectedBySymptoms = :symptoms, feedbackRecordedAtMillis = :recordedAtMillis WHERE sourceId = :sourceId")
    suspend fun saveFeedback(
        sourceId: String,
        perceivedEffort: Int?,
        symptoms: String?,
        recordedAtMillis: Long
    ): Int

    @androidx.room.Transaction
    suspend fun upsertPreservingFeedback(workouts: List<WorkoutEntity>): List<WorkoutEntity> {
        val saved = getAllForBackup().toMutableList()
        val result = mutableListOf<WorkoutEntity>()
        workouts.forEach { incoming ->
            val exactIndex = saved.indexOfFirst { it.sourceId == incoming.sourceId }
            val duplicateIndex = if (exactIndex >= 0) exactIndex else saved.indexOfFirst {
                WorkoutDuplicateMatcher.isDuplicate(it, incoming)
            }
            val canonical = when {
                duplicateIndex < 0 -> incoming
                else -> WorkoutDuplicateMatcher.merge(saved[duplicateIndex], incoming)
            }
            if (duplicateIndex < 0) saved += canonical else saved[duplicateIndex] = canonical
            result += canonical
        }
        val canonicalResult = result.map { value ->
            saved.first { it.sourceId == value.sourceId }
        }
        upsertAll(canonicalResult.distinctBy(WorkoutEntity::sourceId))
        return canonicalResult
    }
}
