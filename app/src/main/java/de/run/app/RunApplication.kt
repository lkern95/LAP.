package de.run.app

import android.app.Application
import androidx.room.Room
import de.run.app.data.healthconnect.AndroidHealthConnectReader
import de.run.app.data.healthconnect.HealthConnectReadinessRepository
import de.run.app.data.healthconnect.HealthConnectPermissionCoordinator
import de.run.app.data.healthconnect.WorkoutBackgroundSyncScheduler
import de.run.app.data.demo.DebugDemoDataSource
import de.run.app.data.demo.DebugDemoModeController
import de.run.app.data.demo.DemoAwareReadinessHealthRepository
import de.run.app.data.demo.DemoAwareWorkoutRepository
import de.run.app.data.local.RoomWorkoutRepository
import de.run.app.data.local.RunDatabase
import de.run.app.data.local.RoomStrengthRepository
import de.run.app.data.local.RoomTrainingDecisionRepository
import de.run.app.data.local.RoomStrengthSessionRepository
import de.run.app.data.strength.AndroidStrengthCatalogRepository
import de.run.app.data.local.AppThemeDataStore
import de.run.app.data.local.TrainingProfileDataStore
import de.run.app.data.local.TrainingGoalDataStore
import de.run.app.data.backup.AndroidAppBackupRepository
import de.run.app.data.fit.AndroidFitActivityImportRepository
import de.run.app.data.garmin.AndroidGarminConnectRepository
import de.run.app.domain.TrainingPlanBuilder
import de.run.app.domain.repository.AppThemeRepository
import de.run.app.domain.repository.StrengthRepository
import de.run.app.domain.repository.TrainingProfileRepository
import de.run.app.domain.repository.TrainingGoalRepository
import de.run.app.domain.repository.WorkoutRepository
import de.run.app.domain.repository.ReadinessHealthRepository

class RunApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val database = Room.databaseBuilder(this, RunDatabase::class.java, "run-local.db")
            .addMigrations(RunDatabase.MIGRATION_1_2)
            .addMigrations(RunDatabase.MIGRATION_2_3)
            .addMigrations(RunDatabase.MIGRATION_3_4)
            .addMigrations(RunDatabase.MIGRATION_4_5)
            .addMigrations(RunDatabase.MIGRATION_5_6)
            .addMigrations(RunDatabase.MIGRATION_6_7)
            .addMigrations(RunDatabase.MIGRATION_7_8)
            .addMigrations(RunDatabase.MIGRATION_8_9)
            .addMigrations(RunDatabase.MIGRATION_9_10, RunDatabase.MIGRATION_10_11)
            .build()
        val backupRepository = AndroidAppBackupRepository(this, database)
        val fitActivityImportRepository = AndroidFitActivityImportRepository(contentResolver, database, filesDir, cacheDir)
        val demoMode = DebugDemoModeController().takeIf { BuildConfig.DEBUG }
        val demoData = demoMode?.let { DebugDemoDataSource(this) }
        val roomWorkoutRepository = RoomWorkoutRepository(
            database.workoutDao(),
            AndroidHealthConnectReader(this)
        )
        val healthReadinessRepository = de.run.app.data.garmin.GarminBackedReadinessRepository(
            HealthConnectReadinessRepository(this),
            database.garminHealthDayDao()
        )
        container = AppContainer(
            profileRepository = TrainingProfileDataStore(this),
            goalRepository = TrainingGoalDataStore(this),
            themeRepository = AppThemeDataStore(this),
            strengthRepository = RoomStrengthRepository(database.strengthCompletionDao()),
            strengthSessionRepository = RoomStrengthSessionRepository(database.strengthSessionDao()),
            strengthCatalogRepository = AndroidStrengthCatalogRepository(this),
            trainingDecisionRepository = RoomTrainingDecisionRepository(database.trainingDecisionDao()),
            workoutRepository = if (demoMode != null && demoData != null) {
                DemoAwareWorkoutRepository(roomWorkoutRepository, demoMode, demoData)
            } else roomWorkoutRepository,
            readinessHealthRepository = if (demoMode != null && demoData != null) {
                DemoAwareReadinessHealthRepository(healthReadinessRepository, demoMode, demoData)
            } else healthReadinessRepository,
            debugDemoMode = demoMode,
            planBuilder = TrainingPlanBuilder(),
            healthPermissionCoordinator = HealthConnectPermissionCoordinator(),
            backgroundSyncScheduler = WorkoutBackgroundSyncScheduler(this),
            backupRepository = backupRepository,
            fitActivityImportRepository = fitActivityImportRepository,
            garminConnectRepository = AndroidGarminConnectRepository(this, database)
        )
    }
}

data class AppContainer(
    val profileRepository: TrainingProfileRepository,
    val goalRepository: TrainingGoalRepository,
    val themeRepository: AppThemeRepository,
    val strengthRepository: StrengthRepository,
    val strengthSessionRepository: de.run.app.domain.repository.StrengthSessionRepository,
    val strengthCatalogRepository: de.run.app.domain.repository.StrengthCatalogRepository,
    val trainingDecisionRepository: de.run.app.domain.repository.TrainingDecisionRepository,
    val workoutRepository: WorkoutRepository,
    val readinessHealthRepository: ReadinessHealthRepository,
    val debugDemoMode: DebugDemoModeController?,
    val planBuilder: TrainingPlanBuilder,
    val healthPermissionCoordinator: HealthConnectPermissionCoordinator,
    val backgroundSyncScheduler: WorkoutBackgroundSyncScheduler,
    val backupRepository: de.run.app.domain.repository.AppBackupRepository,
    val fitActivityImportRepository: de.run.app.domain.repository.FitActivityImportRepository,
    val garminConnectRepository: de.run.app.domain.repository.GarminConnectRepository
)
