package de.run.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

@Entity(tableName = "garmin_health_days")
data class GarminHealthDayEntity(
    @PrimaryKey val date: String,
    val sleepMinutes: Int?,
    val restingHeartRateBpm: Int?,
    val hrvLastNightAverageMs: Int?,
    val hrvWeeklyAverageMs: Int?,
    val hrvStatus: String?,
    val hrvBaselineLowMs: Int?,
    val hrvBaselineHighMs: Int?,
    val averageStressLevel: Int?,
    val steps: Int?,
    val updatedAtMillis: Long
)

@Dao
interface GarminHealthDayDao {
    @Query("SELECT * FROM garmin_health_days WHERE date BETWEEN :fromDate AND :toDate ORDER BY date")
    suspend fun readBetween(fromDate: String, toDate: String): List<GarminHealthDayEntity>

    @Query("SELECT * FROM garmin_health_days WHERE date = :date LIMIT 1")
    suspend fun byDate(date: String): GarminHealthDayEntity?

    @Upsert
    suspend fun save(day: GarminHealthDayEntity)

    @Upsert
    suspend fun saveAll(days: List<GarminHealthDayEntity>)
}
