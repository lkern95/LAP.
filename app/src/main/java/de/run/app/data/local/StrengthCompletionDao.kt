package de.run.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface StrengthCompletionDao {
    @Query("SELECT * FROM strength_completions ORDER BY completedOn DESC")
    fun observeAll(): Flow<List<StrengthCompletionEntity>>

    @Query("SELECT * FROM strength_completions ORDER BY completedOn DESC")
    suspend fun getAllForBackup(): List<StrengthCompletionEntity>

    @Upsert
    suspend fun upsert(completion: StrengthCompletionEntity)

    @Upsert
    suspend fun upsertAll(completions: List<StrengthCompletionEntity>)

    @Query("DELETE FROM strength_completions")
    suspend fun deleteAll()
}
