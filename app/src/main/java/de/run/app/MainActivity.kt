package de.run.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import de.run.app.data.healthconnect.HealthConnectPermissionCoordinator
import de.run.app.application.EvaluateImportedWorkouts
import de.run.app.application.EvaluateWorkoutTrainingFeedback
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import de.run.app.presentation.RunApp
import de.run.app.presentation.history.WorkoutHistoryViewModel
import de.run.app.presentation.plan.PlanViewModel
import de.run.app.presentation.settings.AppearanceViewModel
import de.run.app.presentation.settings.GarminConnectViewModel
import de.run.app.presentation.settings.AppBackupViewModel
import de.run.app.presentation.settings.EvaluationReportViewModel
import de.run.app.presentation.settings.EvaluationReportViewModelFactory
import de.run.app.application.ExportLapEvaluationReport
import de.run.app.application.LapEvaluationReportBuilder
import de.run.app.presentation.strength.StrengthViewModel
import de.run.app.presentation.strength.StrengthViewModelFactory
import de.run.app.presentation.readiness.ReadinessViewModel
import de.run.app.presentation.readiness.ReadinessViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : androidx.activity.ComponentActivity() {
    private val appContainer get() = (application as RunApplication).container
    private val permissionResult = MutableStateFlow<Set<String>?>(null)
    private val planViewModel: PlanViewModel by viewModels {
        PlanViewModelFactory(appContainer)
    }
    private val workoutHistoryViewModel: WorkoutHistoryViewModel by viewModels {
        WorkoutHistoryViewModelFactory(appContainer)
    }
    private val appearanceViewModel: AppearanceViewModel by viewModels {
        AppearanceViewModelFactory(appContainer)
    }
    private val garminConnectViewModel: GarminConnectViewModel by viewModels {
        GarminConnectViewModelFactory(appContainer)
    }
    private val backupViewModel: AppBackupViewModel by viewModels {
        AppBackupViewModelFactory(appContainer.backupRepository)
    }
    private val evaluationReportViewModel: EvaluationReportViewModel by viewModels {
        EvaluationReportViewModelFactory(
            LapEvaluationReportBuilder(),
            ExportLapEvaluationReport(appContainer.evaluationReportRepository)
        )
    }
    private val strengthViewModel: StrengthViewModel by viewModels {
        StrengthViewModelFactory(
            appContainer.strengthSessionRepository,
            appContainer.strengthCatalogRepository,
            appContainer.strengthRepository
        )
    }
    private val readinessViewModel: ReadinessViewModel by viewModels {
        ReadinessViewModelFactory(
            appContainer.workoutRepository,
            appContainer.readinessHealthRepository,
            appContainer.profileRepository,
            appContainer.goalRepository,
            appContainer.planBuilder
        )
    }
    private val permissionLauncher by lazy {
        appContainer.healthPermissionCoordinator.register(this) { granted ->
            permissionResult.value = granted
            appContainer.backgroundSyncScheduler.setEnabled(
                HealthConnectPermissionCoordinator.BACKGROUND_READ_PERMISSION in granted
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionLauncher
        setContent {
            RunApp(
                planViewModel = planViewModel,
                workoutHistoryViewModel = workoutHistoryViewModel,
                appearanceViewModel = appearanceViewModel,
                garminConnectViewModel = garminConnectViewModel,
                backupViewModel = backupViewModel,
                evaluationReportViewModel = evaluationReportViewModel,
                strengthViewModel = strengthViewModel,
                readinessViewModel = readinessViewModel,
                permissionResult = permissionResult,
                requestWorkoutAccess = {
                    permissionResult.value = null
                    permissionLauncher.launch(HealthConnectPermissionCoordinator.requestedPermissions)
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        workoutHistoryViewModel.refresh(surfaceNewGarminRun = true)
    }
}

private class PlanViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        PlanViewModel(
            container.profileRepository,
            container.goalRepository,
            container.planBuilder,
            container.workoutRepository,
            container.trainingDecisionRepository
        ) as T
}

private class WorkoutHistoryViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        WorkoutHistoryViewModel(
            container.workoutRepository,
            importFitActivity = de.run.app.application.ImportFitActivity(container.fitActivityImportRepository),
            profiles = container.profileRepository,
            goals = container.goalRepository,
            evaluateImportedWorkouts = EvaluateImportedWorkouts(
                container.workoutRepository,
                container.profileRepository,
                container.goalRepository
            ),
            evaluateTrainingFeedback = EvaluateWorkoutTrainingFeedback(container.planBuilder)
        ) as T
}

private class AppearanceViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AppearanceViewModel(container.themeRepository) as T
}

private class GarminConnectViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        GarminConnectViewModel(container.garminConnectRepository) as T
}

private class AppBackupViewModelFactory(
    private val repository: de.run.app.domain.repository.AppBackupRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = AppBackupViewModel(repository) as T
}
