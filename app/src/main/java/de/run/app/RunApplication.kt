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
import de.run.app.data.local.AppThemeDataStore
import de.run.app.data.local.TrainingProfileDataStore
import de.run.app.data.local.TrainingGoalDataStore
import de.run.app.data.backup.AndroidAppBackupRepository
import de.run.app.data.fit.AndroidFitActivityImportRepository
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
            .build()
        val backupRepository = AndroidAppBackupRepository(this, database)
        val fitActivityImportRepository = AndroidFitActivityImportRepository(contentResolver, database)
        val demoMode = DebugDemoModeController().takeIf { BuildConfig.DEBUG }
        val demoData = demoMode?.let { DebugDemoDataSource(this) }
        val roomWorkoutRepository = RoomWorkoutRepository(
            database.workoutDao(),
            AndroidHealthConnectReader(this)
        )
        val healthReadinessRepository = HealthConnectReadinessRepository(this)
        container = AppContainer(
            profileRepository = TrainingProfileDataStore(this),
            goalRepository = TrainingGoalDataStore(this),
            themeRepository = AppThemeDataStore(this),
            strengthRepository = RoomStrengthRepository(database.strengthCompletionDao()),
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
            fitActivityImportRepository = fitActivityImportRepository
        )
    }
}

data class AppContainer(
    val profileRepository: TrainingProfileRepository,
    val goalRepository: TrainingGoalRepository,
    val themeRepository: AppThemeRepository,
    val strengthRepository: StrengthRepository,
    val trainingDecisionRepository: de.run.app.domain.repository.TrainingDecisionRepository,
    val workoutRepository: WorkoutRepository,
    val readinessHealthRepository: ReadinessHealthRepository,
    val debugDemoMode: DebugDemoModeController?,
    val planBuilder: TrainingPlanBuilder,
    val healthPermissionCoordinator: HealthConnectPermissionCoordinator,
    val backgroundSyncScheduler: WorkoutBackgroundSyncScheduler,
    val backupRepository: de.run.app.domain.repository.AppBackupRepository,
    val fitActivityImportRepository: de.run.app.domain.repository.FitActivityImportRepository
)
