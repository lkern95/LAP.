package de.run.app.presentation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.run.app.presentation.history.WorkoutHistoryScreen
import de.run.app.presentation.history.WorkoutHistoryViewModel
import de.run.app.presentation.history.WorkoutAnalysisScreen
import de.run.app.presentation.plan.PlanScreen
import de.run.app.presentation.plan.PlanViewModel
import de.run.app.presentation.plan.TrainingDetailsScreen
import de.run.app.presentation.settings.AppearanceScreen
import de.run.app.presentation.settings.AppearanceViewModel
import de.run.app.presentation.settings.AppBackupViewModel
import de.run.app.presentation.settings.GarminScreen
import de.run.app.presentation.theme.RunTheme
import de.run.app.presentation.strength.StrengthScreen
import de.run.app.presentation.strength.StrengthViewModel
import de.run.app.presentation.readiness.ReadinessScreen
import de.run.app.presentation.readiness.ReadinessViewModel
import de.run.app.R
import kotlinx.coroutines.flow.StateFlow

private enum class Destination(val route: String, val label: String, val iconResource: Int) {
    TODAY("today", "Heute", R.drawable.run_navigation_home),
    PLAN("plan", "Plan", R.drawable.run_navigation_plan),
    PROGRESS("progress", "Fortschritt", R.drawable.run_navigation_progress),
    PROFILE("profile", "Ich", R.drawable.run_navigation_profile)
}

@Composable
fun RunApp(
    planViewModel: PlanViewModel,
    workoutHistoryViewModel: WorkoutHistoryViewModel,
    appearanceViewModel: AppearanceViewModel,
    backupViewModel: AppBackupViewModel,
    strengthViewModel: StrengthViewModel,
    readinessViewModel: ReadinessViewModel,
    permissionResult: StateFlow<Set<String>?>,
    requestWorkoutAccess: () -> Unit
) {
    val appearance by appearanceViewModel.state.collectAsState()
    val planState by planViewModel.state.collectAsState()
    val workoutHistoryState by workoutHistoryViewModel.state.collectAsState()
    val activity = LocalContext.current.findActivity()
    val debugDemoMode = (LocalContext.current.applicationContext as? de.run.app.RunApplication)
        ?.container?.debugDemoMode
    val isDemo by (debugDemoMode?.enabled ?: kotlinx.coroutines.flow.MutableStateFlow(false)).collectAsState()
    RunTheme(appearance.theme) {
        SideEffect {
            activity?.window?.let { window ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    window.insetsController?.setSystemBarsAppearance(
                        if (appearance.theme == de.run.app.domain.model.AppTheme.DARK) 0 else mask,
                        mask
                    )
                } else {
                    @Suppress("DEPRECATION")
                    window.decorView.systemUiVisibility = if (
                        appearance.theme == de.run.app.domain.model.AppTheme.DARK
                    ) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                }
            }
        }
        var showWelcome by rememberSaveable { mutableStateOf(true) }
        val navController = rememberNavController()
        if (showWelcome) {
            WelcomeScreen(onContinue = { showWelcome = false })
        } else if (planState.isLoading) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                androidx.compose.material3.CircularProgressIndicator()
                Text("Dein Start wird vorbereitet.")
            }
        } else {
            val entry by navController.currentBackStackEntryAsState()
            val currentRoute = entry?.destination?.route
            val detailSource = entry?.arguments?.getString("source")
            val result by permissionResult.collectAsState()
            LaunchedEffect(workoutHistoryState.newlySyncedWorkout?.sourceId, currentRoute) {
                if (
                    workoutHistoryState.newlySyncedWorkout != null &&
                    currentRoute in setOf(Destination.TODAY.route, Destination.PROGRESS.route, GARMIN_ROUTE)
                ) {
                    navController.navigate(WORKOUT_ANALYSIS_ROUTE) { launchSingleTop = true }
                }
            }
            LaunchedEffect(currentRoute) {
                if (currentRoute == Destination.PROGRESS.route || currentRoute == GARMIN_ROUTE) {
                    workoutHistoryViewModel.refresh(surfaceNewGarminRun = true)
                }
                if (currentRoute == READINESS_ROUTE) readinessViewModel.refresh()
            }
            LaunchedEffect(result) {
                result?.let {
                    workoutHistoryViewModel.onPermissionResult()
                    readinessViewModel.refresh()
                }
            }

            Scaffold(
            bottomBar = {
                if (currentRoute != READINESS_ROUTE && currentRoute != WORKOUT_ANALYSIS_ROUTE &&
                    !(planState.profile == null && currentRoute == Destination.PLAN.route)
                ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    Destination.entries.forEach { destination ->
                        val selected = currentRoute == destination.route ||
                            (currentRoute == TRAINING_DETAILS_ROUTE && detailSource == "today" && destination == Destination.TODAY) ||
                            (currentRoute == TRAINING_DETAILS_ROUTE && detailSource == "plan" && destination == Destination.PLAN) ||
                            (currentRoute == WORKOUT_ANALYSIS_ROUTE && destination == Destination.TODAY) ||
                            (destination == Destination.PLAN && currentRoute == "strength") ||
                            (destination == Destination.TODAY && currentRoute == READINESS_ROUTE) ||
                            (destination == Destination.PROFILE && currentRoute == GARMIN_ROUTE)
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTopLevel(destination.route) },
                            icon = {
                                Box(
                                    Modifier.size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (selected) {
                                                MaterialTheme.colorScheme.secondaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surface
                                            }
                                        ),
                                    contentAlignment = androidx.compose.ui.Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(destination.iconResource),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            },
                            label = { Text(destination.label) }
                        )
                    }
                }
                }
            }
        ) { contentPadding ->
            NavHost(
                navController = navController,
                startDestination = if (planState.profile == null) Destination.PLAN.route else Destination.TODAY.route,
                modifier = Modifier.padding(contentPadding),
                enterTransition = {
                    fadeIn(tween(180)) + slideInHorizontally(tween(180)) { it / 16 }
                },
                exitTransition = {
                    fadeOut(tween(120)) + slideOutHorizontally(tween(120)) { -it / 24 }
                },
                popEnterTransition = {
                    fadeIn(tween(180)) + slideInHorizontally(tween(180)) { -it / 16 }
                },
                popExitTransition = {
                    fadeOut(tween(120)) + slideOutHorizontally(tween(120)) { it / 24 }
                }
            ) {
                composable(Destination.TODAY.route) {
                    TodayScreen(
                        planViewModel = planViewModel,
                        workoutHistoryViewModel = workoutHistoryViewModel,
                        onRequestWorkoutAccess = requestWorkoutAccess,
                        onOpenPlan = { navController.navigate(Destination.PLAN.route) },
                        onOpenReadiness = { navController.navigate(READINESS_ROUTE) },
                        onOpenAvailability = {
                            planViewModel.editAvailability()
                            navController.navigate(Destination.PLAN.route)
                        },
                        onOpenTrainingDetailsForTime = { weekStart, run, availableMinutes ->
                            val week = planState.plan?.weeks?.firstOrNull { it.startsOn == weekStart }
                            val runIndex = week?.runs?.indexOf(run) ?: -1
                            if (runIndex >= 0) navController.navigate(
                                "$TRAINING_DETAILS_ROUTE/$weekStart/$runIndex/today?availableMinutes=$availableMinutes"
                            )
                        }
                    )
                }
                composable(Destination.PLAN.route) {
                    PlanScreen(
                        viewModel = planViewModel,
                        workoutHistoryViewModel = workoutHistoryViewModel,
                        permissionResult = result,
                        onOpenStrength = { navController.navigate("strength") },
                        onOpenTrainingDetails = { weekStart, run ->
                            val week = planState.plan?.weeks?.firstOrNull { it.startsOn == weekStart }
                            val runIndex = week?.runs?.indexOf(run) ?: -1
                            if (runIndex >= 0) navController.navigate("$TRAINING_DETAILS_ROUTE/$weekStart/$runIndex/plan")
                        },
                        onConnectGarmin = requestWorkoutAccess,
                        onSetupHealthSync = { activity?.openHealthSyncSetup() },
                        onOnboardingComplete = {
                            navController.navigate(Destination.TODAY.route) {
                                popUpTo(Destination.PLAN.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    )
                }
                composable("strength") { StrengthScreen(strengthViewModel) }
                composable(
                    route = "$TRAINING_DETAILS_ROUTE/{weekStart}/{runIndex}/{source}?availableMinutes={availableMinutes}",
                    arguments = listOf(
                        navArgument("weekStart") { type = NavType.StringType },
                        navArgument("runIndex") { type = NavType.IntType },
                        navArgument("source") { type = NavType.StringType },
                        navArgument("availableMinutes") { type = NavType.IntType; defaultValue = 0 }
                    )
                ) { detailEntry ->
                    val weekStart = detailEntry.arguments?.getString("weekStart")
                        ?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
                    val runIndex = detailEntry.arguments?.getInt("runIndex") ?: -1
                    val storedRun = weekStart?.let { date ->
                        planState.plan?.weeks?.firstOrNull { it.startsOn == date }?.runs?.getOrNull(runIndex)
                    }
                    val availableMinutes = detailEntry.arguments?.getInt("availableMinutes") ?: 0
                    val selectedRun = storedRun?.let { run ->
                        if (availableMinutes > 0) planViewModel.runForAvailableTime(run, availableMinutes) ?: run else run
                    }
                    TrainingDetailsScreen(
                        run = selectedRun,
                        onBack = { navController.popBackStack() },
                        onOpenPlan = { navController.navigate(Destination.PLAN.route) { launchSingleTop = true } }
                    )
                }
                composable(READINESS_ROUTE) {
                    ReadinessScreen(
                        viewModel = readinessViewModel,
                        debugDemoMode = debugDemoMode,
                        onRequestAccess = requestWorkoutAccess,
                        onDemoModeChanged = { enabled ->
                            debugDemoMode?.setEnabled(enabled)
                            readinessViewModel.refresh()
                            workoutHistoryViewModel.refresh()
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Destination.PROGRESS.route) {
                    WorkoutHistoryScreen(workoutHistoryViewModel, requestWorkoutAccess)
                }
                composable(WORKOUT_ANALYSIS_ROUTE) {
                    WorkoutAnalysisScreen(
                        workout = workoutHistoryState.newlySyncedWorkout,
                        isSavingFeedback = workoutHistoryState.isSavingFeedback,
                        feedbackSaveFailed = workoutHistoryState.feedbackSaveFailed,
                        onSaveFeedback = workoutHistoryViewModel::saveFeedback,
                        onSkipFeedback = workoutHistoryViewModel::skipFeedback,
                        onContinue = {
                            workoutHistoryViewModel.dismissNewlySyncedWorkout()
                            navController.navigate(Destination.TODAY.route) {
                                popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        onBack = {
                            workoutHistoryViewModel.dismissNewlySyncedWorkout()
                            navController.popBackStack()
                        }
                    )
                }
                composable(Destination.PROFILE.route) {
                    AppearanceScreen(appearanceViewModel, backupViewModel) {
                        navController.navigate(GARMIN_ROUTE) { launchSingleTop = true }
                    }
                }
                composable(GARMIN_ROUTE) {
                    GarminScreen(
                        workoutHistoryViewModel = workoutHistoryViewModel,
                        isDemo = isDemo,
                        planViewModel = planViewModel,
                        onOpenProgress = { navController.navigate(Destination.PROGRESS.route) },
                        onOpenTrainingPlan = { navController.navigate(Destination.PLAN.route) },
                        onRequestWorkoutAccess = requestWorkoutAccess,
                        onSetupHealthSync = { activity?.openHealthSyncSetup() },
                        onInstallGarminConnect = { activity?.openStoreListing(GARMIN_CONNECT_PACKAGE) },
                        onInstallHealthConnect = { activity?.openHealthConnectSettings() },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            }
            BackHandler(enabled = navController.previousBackStackEntry != null) {
                if (currentRoute == WORKOUT_ANALYSIS_ROUTE) {
                    workoutHistoryViewModel.dismissNewlySyncedWorkout()
                }
                navController.popBackStack()
            }
    }
}
}

private const val GARMIN_ROUTE = "garmin"
private const val READINESS_ROUTE = "readiness"
private const val TRAINING_DETAILS_ROUTE = "training-details"
private const val WORKOUT_ANALYSIS_ROUTE = "workout-analysis"
private const val HEALTH_SYNC_PACKAGE = "nl.appyhapps.healthsync"
private const val GARMIN_CONNECT_PACKAGE = "com.garmin.android.apps.connectmobile"
private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"

private fun NavController.navigateToTopLevel(route: String) {
    if (currentDestination?.route == route) return
    if (popBackStack(route, inclusive = false)) return

    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Activity.openHealthSyncSetup() {
    openStoreListing(HEALTH_SYNC_PACKAGE)
}

private fun Activity.openHealthConnectSettings() {
    val settingsIntent = Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
        .setPackage(HEALTH_CONNECT_PACKAGE)
    if (settingsIntent.resolveActivity(packageManager) != null) {
        startActivity(settingsIntent)
    } else {
        openStoreListing(HEALTH_CONNECT_PACKAGE)
    }
}

private fun Activity.openStoreListing(packageName: String) {
    val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
    val webIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
    )
    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        ?: if (marketIntent.resolveActivity(packageManager) != null) marketIntent else webIntent
    startActivity(launchIntent)
}
