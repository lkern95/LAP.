package de.run.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Transaction

@Dao
interface TrainingDecisionDao {
    @Query("SELECT * FROM training_decisions")
    suspend fun getAllForBackup(): List<TrainingDecisionEntity>

    @Upsert
    suspend fun upsertAll(decisions: List<TrainingDecisionEntity>)

    @Query("DELETE FROM training_decisions")
    suspend fun deleteAll()

    @Query("SELECT * FROM training_decisions WHERE id = :id LIMIT 1")
    suspend fun getCurrent(id: String = TrainingDecisionEntity.CURRENT_ID): TrainingDecisionEntity?

    @Upsert
    suspend fun save(snapshot: TrainingDecisionEntity)

    @Query("SELECT COUNT(*) FROM training_decisions")
    suspend fun count(): Int

    @Transaction
    suspend fun saveIfChanged(snapshot: TrainingDecisionEntity) {
        if (getCurrent()?.inputRevision != snapshot.inputRevision) save(snapshot)
    }
}
