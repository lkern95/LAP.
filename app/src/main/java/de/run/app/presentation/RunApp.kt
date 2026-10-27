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
import de.run.app.presentation.plan.LiveRunCoachScreen
import de.run.app.data.audio.LiveCoachForegroundService
import de.run.app.domain.LiveRunTargets
import de.run.app.presentation.settings.AppearanceScreen
import de.run.app.presentation.settings.AppearanceViewModel
import de.run.app.presentation.settings.AppBackupViewModel
import de.run.app.presentation.settings.EvaluationReportViewModel
import de.run.app.application.LapEvaluationReportInput
import de.run.app.application.GarminEvaluationSnapshot
import de.run.app.application.LiveCoachEvaluationSnapshot
import de.run.app.presentation.settings.GarminScreen
import de.run.app.presentation.settings.GarminConnectViewModel
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
    PROFILE("profile", "Profil", R.drawable.run_navigation_profile)
}

@Composable
fun RunApp(
    planViewModel: PlanViewModel,
    workoutHistoryViewModel: WorkoutHistoryViewModel,
    appearanceViewModel: AppearanceViewModel,
    garminConnectViewModel: GarminConnectViewModel,
    backupViewModel: AppBackupViewModel,
    evaluationReportViewModel: EvaluationReportViewModel,
    strengthViewModel: StrengthViewModel,
    readinessViewModel: ReadinessViewModel,
    permissionResult: StateFlow<Set<String>?>,
    requestWorkoutAccess: () -> Unit
) {
    val appearance by appearanceViewModel.state.collectAsState()
    val planState by planViewModel.state.collectAsState()
    val workoutHistoryState by workoutHistoryViewModel.state.collectAsState()
    val readinessState by readinessViewModel.state.collectAsState()
    val garminConnectState by garminConnectViewModel.state.collectAsState()
    val strengthState by strengthViewModel.state.collectAsState()
    val liveCoachState by LiveCoachForegroundService.garminState.collectAsState()
    val liveCoachActive by LiveCoachForegroundService.active.collectAsState()
    val activity = LocalContext.current.findActivity()
    val context = LocalContext.current
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
        var isStrengthDetail by rememberSaveable { mutableStateOf(false) }
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
            LaunchedEffect(Unit) {
                workoutHistoryViewModel.refresh()
            }
            LaunchedEffect(workoutHistoryState.trainingHistoryCoverage) {
                planViewModel.updateTrainingHistoryCoverage(workoutHistoryState.trainingHistoryCoverage)
            }
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
                if (currentRoute == READINESS_ROUTE || currentRoute == Destination.PROFILE.route) {
                    readinessViewModel.refresh()
                }
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
                    !(currentRoute == "strength" && isStrengthDetail) &&
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
                        onOnboardingComplete = {
                            navController.navigate(Destination.TODAY.route) {
                                popUpTo(Destination.PLAN.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    )
                }
                composable("strength") {
                    StrengthScreen(
                        viewModel = strengthViewModel,
                        onBack = { navController.popBackStack() },
                        onDetailChanged = { isStrengthDetail = it }
                    )
                }
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
                        if (availableMinutes > 0) planViewModel.runForAvailableTime(run, availableMinutes, workoutHistoryState.workouts) ?: run else run
                    }
                    TrainingDetailsScreen(
                        run = selectedRun,
                        onBack = { navController.popBackStack() },
                        onOpenPlan = { navController.navigate(Destination.PLAN.route) { launchSingleTop = true } },
                        onStartGarminLive = {
                            if (weekStart != null && runIndex >= 0) {
                                navController.navigate(
                                    "$LIVE_COACH_ROUTE/$weekStart/$runIndex?availableMinutes=$availableMinutes&startGarminLive=true"
                                )
                            }
                        }
                    )
                }
                composable(
                    route = "$LIVE_COACH_ROUTE/{weekStart}/{runIndex}?availableMinutes={availableMinutes}&startGarminLive={startGarminLive}",
                    arguments = listOf(
                        navArgument("weekStart") { type = NavType.StringType },
                        navArgument("runIndex") { type = NavType.IntType },
                        navArgument("availableMinutes") { type = NavType.IntType; defaultValue = 0 },
                        navArgument("startGarminLive") { type = NavType.BoolType; defaultValue = false }
                    )
                ) { liveEntry ->
                    val weekStart = liveEntry.arguments?.getString("weekStart")
                        ?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
                    val runIndex = liveEntry.arguments?.getInt("runIndex") ?: -1
                    val availableMinutes = liveEntry.arguments?.getInt("availableMinutes") ?: 0
                    val startGarminLive = liveEntry.arguments?.getBoolean("startGarminLive") ?: false
                    val plannedRun = weekStart?.let { date ->
                        planState.plan?.weeks?.firstOrNull { it.startsOn == date }?.runs?.getOrNull(runIndex)
                    }
                    val runForDisplay = plannedRun?.let { run ->
                        if (availableMinutes > 0) planViewModel.runForAvailableTime(run, availableMinutes, workoutHistoryState.workouts) ?: run else run
                    }
                    if (runForDisplay != null) {
                        LiveRunCoachScreen(
                            run = runForDisplay,
                            onBack = { navController.popBackStack() },
                            startGarminOnOpen = startGarminLive,
                            onStartGarmin = {
                                LiveCoachForegroundService.startGarmin(context, LiveRunTargets.from(runForDisplay))
                            }
                        )
                    }
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
                        onBack = { navController.popBackStack() },
                        trainingFeedback = workoutHistoryState.trainingFeedback
                    )
                }
                composable(Destination.PROGRESS.route) {
                    WorkoutHistoryScreen(
                        workoutHistoryViewModel,
                        requestWorkoutAccess,
                        goalDistanceMeters = planState.goal?.raceDistanceMeters,
                        goalTimeMinutes = planState.goal?.targetTimeMinutes,
                        goalPaceSecondsPerKilometer = planState.goalPaceSecondsPerKilometer,
                        goalPaceUsesForecast = planState.goalPaceUsesForecast,
                        halfMarathonForecast = planState.halfMarathonForecast
                    )
                }
                composable(WORKOUT_ANALYSIS_ROUTE) {
                    WorkoutAnalysisScreen(
                        workout = workoutHistoryState.newlySyncedWorkout,
                        isSavingFeedback = workoutHistoryState.isSavingFeedback,
                        feedbackSaveFailed = workoutHistoryState.feedbackSaveFailed,
                        trainingFeedback = workoutHistoryState.trainingFeedback,
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
                    val evaluationReportInput = LapEvaluationReportInput(
                        generatedAt = java.time.Instant.now(),
                        appVersion = de.run.app.BuildConfig.VERSION_NAME,
                        isDebugBuild = de.run.app.BuildConfig.DEBUG,
                        isDemoData = isDemo || readinessState.isDemo,
                        profile = planState.profile,
                        goal = planState.goal,
                        goals = planState.goals,
                        plan = planState.plan,
                        progress = planState.progress,
                        nextRun = planState.nextScheduledRun?.run,
                        forecast = planState.halfMarathonForecast,
                        feedback = workoutHistoryState.trainingFeedback,
                        workouts = workoutHistoryState.workouts,
                        health = readinessState.health,
                        readiness = readinessState.assessment,
                        readinessFacts = readinessState.facts,
                        readinessGuidance = readinessState.guidance,
                        readinessWeeklyComparison = readinessState.weeklyTrainingComparison,
                        readinessHealthReadFailed = readinessState.healthReadFailed,
                        readinessWorkoutAccess = readinessState.hasWorkoutAccess,
                        readinessWorkoutsUnavailable = readinessState.workoutsUnavailable,
                        readinessWorkoutsFailed = readinessState.workoutsFailed,
                        workoutDashboard = workoutHistoryState.dashboard,
                        workoutSelectedPeriod = workoutHistoryState.selectedPeriod,
                        workoutReadStatus = when {
                            workoutHistoryState.isLoading -> "Lädt"
                            workoutHistoryState.hasError -> "Fehler beim Lesen"
                            workoutHistoryState.isUnavailable -> "Nicht verfügbar"
                            workoutHistoryState.accessMessage != null -> "Freigabe erforderlich"
                            else -> "Verfügbar"
                        },
                        workoutFullHistoryAccess = workoutHistoryState.hasFullHistoryAccess,
                        workoutLastReadAt = workoutHistoryState.lastSuccessfulReadAt,
                        workoutLastRefreshImportedCount = workoutHistoryState.lastRefreshImportedCount,
                        fitImportResult = workoutHistoryState.lastFitImportResult,
                        planCompletedRuns = planState.completedPlannedRunCount,
                        planLoadError = planState.hasLoadError,
                        goalHistoryError = planState.hasGoalHistoryError,
                        goalPaceSecondsPerKilometer = planState.goalPaceSecondsPerKilometer,
                        goalPaceUsesForecast = planState.goalPaceUsesForecast,
                        pendingCompetitionCandidate = planState.pendingCompetitionCandidate != null,
                        decisionSnapshot = planState.decisionSnapshot,
                        trainingHistoryCoverage = workoutHistoryState.trainingHistoryCoverage,
                        planEvidence = planState.planEvidence,
                        liveCoach = LiveCoachEvaluationSnapshot(
                            active = liveCoachActive,
                            connectionStatus = liveCoachState.connectionStatus.name,
                            transport = liveCoachState.diagnostics.transport.name,
                            receiver = liveCoachState.diagnostics.receiver.name,
                            incomingMessages = liveCoachState.diagnostics.incomingMessages,
                            rejectedMessages = liveCoachState.diagnostics.rejectedMessages,
                            validMeasurements = liveCoachState.diagnostics.validMeasurements,
                            targetTransfer = liveCoachState.diagnostics.targetTransfer.name,
                            estimatedLatencyMillis = liveCoachState.estimatedLatencyMillis,
                            averageMessagesPerSecond = liveCoachState.averageMessagesPerSecond,
                            audioAnnouncementsEnabled = LiveCoachForegroundService.audioAnnouncementsEnabled(context),
                            lastCue = liveCoachState.lastCoachCue?.name,
                            activeSeconds = liveCoachState.metrics?.activeSeconds,
                            distanceMeters = liveCoachState.metrics?.distanceMeters,
                            paceSecondsPerKilometer = liveCoachState.metrics?.paceSecondsPerKilometer,
                            heartRateBpm = liveCoachState.metrics?.heartRateBpm
                        ),
                        theme = appearance.theme,
                        garmin = GarminEvaluationSnapshot(
                            isConnected = garminConnectState.isConnected,
                            isSyncing = garminConnectState.isSyncing,
                            hasSyncError = garminConnectState.hasSyncError,
                            lastSyncAt = garminConnectState.lastHistorySyncAt,
                            originalFitSaved = garminConnectState.originalFitSaved,
                            workoutImported = garminConnectState.workoutImported,
                            trainingPlanUpdated = garminConnectState.trainingPlanUpdated,
                            lastHistorySyncSummary = garminConnectState.lastHistorySyncSummary
                        )
                    )
                    AppearanceScreen(
                        viewModel = appearanceViewModel,
                        backupViewModel = backupViewModel,
                        evaluationReportViewModel = evaluationReportViewModel,
                        evaluationReportInput = evaluationReportInput,
                        isEvaluationDataLoading = workoutHistoryState.isLoading || readinessState.isLoading ||
                            garminConnectState.isCheckingConnection || garminConnectState.isSyncing ||
                            strengthState.isLoading || !strengthState.completionHistoryLoaded,
                        strengthViewModel = strengthViewModel,
                        onOpenGarmin = { navController.navigate(GARMIN_ROUTE) { launchSingleTop = true } },
                        onEditTrainingGoal = {
                            planViewModel.editGoal()
                            navController.navigate(Destination.PLAN.route) { launchSingleTop = true }
                        },
                        onEditAvailability = {
                            planViewModel.editAvailability()
                            navController.navigate(Destination.PLAN.route) { launchSingleTop = true }
                        },
                        onOpenStrengthTraining = { navController.navigate("strength") },
                        hasCompetitionToClassify = planState.pendingCompetitionCandidate != null,
                        onClassifyCompetition = {
                            planViewModel.showCompetitionQuestion()
                            navController.navigate(Destination.PLAN.route) { launchSingleTop = true }
                        }
                    )
                }
                composable(GARMIN_ROUTE) {
                    GarminScreen(
                        workoutHistoryViewModel = workoutHistoryViewModel,
                        garminConnectViewModel = garminConnectViewModel,
                        isDemo = isDemo,
                        planViewModel = planViewModel,
                        onOpenProgress = { navController.navigate(Destination.PROGRESS.route) },
                        onOpenHealthData = { navController.navigate(READINESS_ROUTE) { launchSingleTop = true } },
                        onOpenTrainingPlan = { navController.navigate(Destination.PLAN.route) },
                        onRequestWorkoutAccess = requestWorkoutAccess,
                        onInstallGarminConnect = { activity?.openStoreListing(GARMIN_CONNECT_PACKAGE) },
                        onInstallHealthConnect = { activity?.openHealthConnectSettings() },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            }
            BackHandler(enabled = navController.previousBackStackEntry != null && !(currentRoute == "strength" && isStrengthDetail)) {
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
private const val LIVE_COACH_ROUTE = "live-coach"
private const val WORKOUT_ANALYSIS_ROUTE = "workout-analysis"
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
