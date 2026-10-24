package de.run.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

@Entity(
    tableName = "garmin_fit_files",
    indices = [Index(value = ["sha256"])]
)
data class GarminFitFileEntity(
    @PrimaryKey val activityId: String,
    val sha256: String,
    val fileName: String,
    val byteCount: Long,
    val savedAtMillis: Long,
    val canonicalWorkoutSourceId: String? = null
)

@Dao
interface GarminFitFileDao {
    @Query("SELECT * FROM garmin_fit_files WHERE activityId = :activityId LIMIT 1")
    suspend fun byActivityId(activityId: String): GarminFitFileEntity?

    @Query("SELECT * FROM garmin_fit_files WHERE sha256 = :sha256 LIMIT 1")
    suspend fun bySha256(sha256: String): GarminFitFileEntity?

    @Upsert
    suspend fun save(file: GarminFitFileEntity)
}
