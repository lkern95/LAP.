package de.run.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [WorkoutEntity::class, StrengthCompletionEntity::class, TrainingDecisionEntity::class], version = 5, exportSchema = true)
abstract class RunDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
    abstract fun strengthCompletionDao(): StrengthCompletionDao
    abstract fun trainingDecisionDao(): TrainingDecisionDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workouts ADD COLUMN averageHeartRateBpm INTEGER")
                db.execSQL("ALTER TABLE workouts ADD COLUMN maximumHeartRateBpm INTEGER")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS strength_completions (
                        id TEXT NOT NULL PRIMARY KEY,
                        exerciseId TEXT NOT NULL,
                        completedOn TEXT NOT NULL
                    )""".trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workouts ADD COLUMN perceivedEffort INTEGER")
                db.execSQL("ALTER TABLE workouts ADD COLUMN runningAffectedBySymptoms TEXT")
                db.execSQL("ALTER TABLE workouts ADD COLUMN feedbackRecordedAtMillis INTEGER")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS training_decisions (
                        id TEXT NOT NULL PRIMARY KEY,
                        ruleVersion INTEGER NOT NULL,
                        inputRevision TEXT NOT NULL,
                        targetRevision TEXT,
                        evaluatedOn TEXT NOT NULL,
                        runCount INTEGER NOT NULL,
                        latestRunEndedAtMillis INTEGER,
                        recommendationTitle TEXT,
                        recommendationMinutes INTEGER,
                        reasonCode TEXT NOT NULL
                    )""".trimIndent()
                )
            }
        }
    }
}
