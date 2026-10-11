package de.run.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import de.run.app.data.healthconnect.HealthConnectPermissionCoordinator
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import de.run.app.presentation.RunApp
import de.run.app.presentation.history.WorkoutHistoryViewModel
import de.run.app.presentation.plan.PlanViewModel
import de.run.app.presentation.settings.AppearanceViewModel
import de.run.app.presentation.settings.AppBackupViewModel
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
    private val backupViewModel: AppBackupViewModel by viewModels {
        AppBackupViewModelFactory(appContainer.backupRepository)
    }
    private val strengthViewModel: StrengthViewModel by viewModels {
        StrengthViewModelFactory(appContainer.strengthRepository)
    }
    private val readinessViewModel: ReadinessViewModel by viewModels {
        ReadinessViewModelFactory(
            appContainer.workoutRepository,
            appContainer.readinessHealthRepository,
            appContainer.profileRepository
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
                backupViewModel = backupViewModel,
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
            importFitActivity = de.run.app.application.ImportFitActivity(container.fitActivityImportRepository)
        ) as T
}

private class AppearanceViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AppearanceViewModel(container.themeRepository) as T
}

private class AppBackupViewModelFactory(
    private val repository: de.run.app.domain.repository.AppBackupRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = AppBackupViewModel(repository) as T
}
