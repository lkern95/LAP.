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
        return saveImportPreservingFeedback(workouts).workouts
    }

    @androidx.room.Transaction
    suspend fun saveImportPreservingFeedback(workouts: List<WorkoutEntity>): WorkoutSaveSummary {
        val saved = getAllForBackup().toMutableList()
        val result = mutableListOf<WorkoutEntity>()
        var inserted = 0
        var alreadyPresent = 0
        workouts.forEach { incoming ->
            val exactIndex = saved.indexOfFirst { it.sourceId == incoming.sourceId }
            val duplicateIndex = if (exactIndex >= 0) exactIndex else saved.indexOfFirst {
                WorkoutDuplicateMatcher.isDuplicate(it, incoming)
            }
            val canonical = when {
                duplicateIndex < 0 -> incoming.also { inserted += 1 }
                else -> WorkoutDuplicateMatcher.merge(saved[duplicateIndex], incoming).also { alreadyPresent += 1 }
            }
            if (duplicateIndex < 0) saved += canonical else saved[duplicateIndex] = canonical
            result += canonical
        }
        val canonicalResult = result.map { value ->
            saved.first { it.sourceId == value.sourceId }
        }
        upsertAll(canonicalResult.distinctBy(WorkoutEntity::sourceId))
        return WorkoutSaveSummary(canonicalResult, inserted, alreadyPresent)
    }
}

data class WorkoutSaveSummary(
    val workouts: List<WorkoutEntity>,
    val insertedCount: Int,
    val alreadyPresentCount: Int
)
