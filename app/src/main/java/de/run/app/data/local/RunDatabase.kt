package de.run.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [WorkoutEntity::class, StrengthCompletionEntity::class, TrainingDecisionEntity::class, StrengthSessionEntity::class, GarminFitFileEntity::class, GarminHealthDayEntity::class], version = 12, exportSchema = true)
abstract class RunDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
    abstract fun strengthCompletionDao(): StrengthCompletionDao
    abstract fun trainingDecisionDao(): TrainingDecisionDao
    abstract fun strengthSessionDao(): StrengthSessionDao
    abstract fun garminFitFileDao(): GarminFitFileDao
    abstract fun garminHealthDayDao(): GarminHealthDayDao

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

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS strength_sessions (
                        sessionId TEXT NOT NULL PRIMARY KEY,
                        workoutId TEXT NOT NULL,
                        catalogSnapshot TEXT NOT NULL,
                        status TEXT NOT NULL,
                        currentExerciseId TEXT,
                        currentRound INTEGER NOT NULL,
                        completedSetsJson TEXT NOT NULL,
                        isCurrentSetStarted INTEGER NOT NULL,
                        pauseUntilMillis INTEGER,
                        startedAtMillis INTEGER NOT NULL,
                        finishedAtMillis INTEGER,
                        schemaVersion TEXT NOT NULL
                    )""".trimIndent()
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS garmin_fit_files (
                        activityId TEXT NOT NULL PRIMARY KEY,
                        sha256 TEXT NOT NULL,
                        fileName TEXT NOT NULL,
                        byteCount INTEGER NOT NULL,
                        savedAtMillis INTEGER NOT NULL
                    )""".trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_garmin_fit_files_sha256 ON garmin_fit_files (sha256)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workouts ADD COLUMN activityType TEXT NOT NULL DEFAULT 'RUNNING'")
                db.execSQL("ALTER TABLE workouts ADD COLUMN averageCadenceRpm INTEGER")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE garmin_fit_files ADD COLUMN canonicalWorkoutSourceId TEXT")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS garmin_health_days (
                        date TEXT NOT NULL PRIMARY KEY,
                        sleepMinutes INTEGER,
                        restingHeartRateBpm INTEGER,
                        updatedAtMillis INTEGER NOT NULL
                    )""".trimIndent()
                )
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workouts ADD COLUMN planMatchType TEXT")
                db.execSQL("ALTER TABLE workouts ADD COLUMN plannedTrainingId TEXT")
                db.execSQL("ALTER TABLE workouts ADD COLUMN plannedDateEpochDay INTEGER")
                db.execSQL("ALTER TABLE workouts ADD COLUMN plannedTitle TEXT")
                db.execSQL("ALTER TABLE workouts ADD COLUMN plannedDurationMinutes INTEGER")
                db.execSQL("ALTER TABLE workouts ADD COLUMN plannedPaceSlowerSecondsPerKm INTEGER")
                db.execSQL("ALTER TABLE workouts ADD COLUMN plannedPaceFasterSecondsPerKm INTEGER")
                db.execSQL("ALTER TABLE workouts ADD COLUMN plannedEffortMinimum INTEGER")
                db.execSQL("ALTER TABLE workouts ADD COLUMN plannedEffortMaximum INTEGER")
                db.execSQL("ALTER TABLE workouts ADD COLUMN plannedAssessment TEXT")
                db.execSQL("ALTER TABLE workouts ADD COLUMN plannedRationale TEXT")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE garmin_health_days ADD COLUMN hrvLastNightAverageMs INTEGER")
                db.execSQL("ALTER TABLE garmin_health_days ADD COLUMN hrvWeeklyAverageMs INTEGER")
                db.execSQL("ALTER TABLE garmin_health_days ADD COLUMN hrvStatus TEXT")
                db.execSQL("ALTER TABLE garmin_health_days ADD COLUMN hrvBaselineLowMs INTEGER")
                db.execSQL("ALTER TABLE garmin_health_days ADD COLUMN hrvBaselineHighMs INTEGER")
                db.execSQL("ALTER TABLE garmin_health_days ADD COLUMN averageStressLevel INTEGER")
                db.execSQL("ALTER TABLE garmin_health_days ADD COLUMN steps INTEGER")
            }
        }
    }
}
