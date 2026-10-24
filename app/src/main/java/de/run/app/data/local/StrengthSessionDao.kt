package de.run.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface StrengthSessionDao {
    @Query("SELECT * FROM strength_sessions ORDER BY startedAtMillis DESC")
    fun observeAll(): Flow<List<StrengthSessionEntity>>

    @Query("SELECT * FROM strength_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun get(sessionId: String): StrengthSessionEntity?

    @Query("SELECT * FROM strength_sessions ORDER BY startedAtMillis DESC")
    suspend fun getAllForBackup(): List<StrengthSessionEntity>

    @Query("SELECT * FROM strength_sessions WHERE status = 'in_progress' ORDER BY startedAtMillis DESC LIMIT 1")
    suspend fun getActive(): StrengthSessionEntity?

    @Upsert
    suspend fun upsert(session: StrengthSessionEntity)

    @Upsert
    suspend fun upsertAll(sessions: List<StrengthSessionEntity>)

    @Query("DELETE FROM strength_sessions")
    suspend fun deleteAll()
}
