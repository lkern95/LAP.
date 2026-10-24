package de.run.app.presentation.plan

import android.Manifest
import android.app.KeyguardManager
import android.speech.tts.TextToSpeech
import android.media.AudioAttributes
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import de.run.app.BuildConfig
import de.run.app.R
import de.run.app.domain.LiveCoachCue
import de.run.app.domain.CoachAudioCue
import de.run.app.domain.LiveMetricSource
import de.run.app.domain.LiveRunCoach
import de.run.app.domain.LiveRunMetrics
import de.run.app.domain.LivePaceSmoother
import de.run.app.domain.LiveRunTargets
import de.run.app.domain.toAudioCue
import de.run.app.domain.deviationMessage
import de.run.app.domain.shouldShowCoachDeviationNotice
import de.run.app.domain.model.PlannedRun
import de.run.app.data.audio.AndroidCoachAudioPlayer
import de.run.app.data.audio.LiveCoachForegroundService
import de.run.app.data.garmin.live.GarminLiveConnectionStatus
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
internal fun LiveRunCoachScreen(
    run: PlannedRun,
    onBack: () -> Unit,
    startGarminOnOpen: Boolean = false,
    onStartGarmin: () -> Unit = {}
) {
    val context = LocalContext.current
    val releaseLiveMode = !BuildConfig.DEBUG
    val initialGarminMode = startGarminOnOpen || releaseLiveMode
    var running by remember { mutableStateOf(initialGarminMode) }
    var connected by remember { mutableStateOf(true) }
    var seconds by remember { mutableIntStateOf(0) }
    var pace by remember { mutableIntStateOf(375) }
    var smoothedPace by remember { mutableIntStateOf(375) }
    var heartRate by remember { mutableIntStateOf(142) }
    var audioEnabled by remember(context) {
        mutableStateOf(LiveCoachForegroundService.audioAnnouncementsEnabled(context))
    }
    var audioReady by remember { mutableStateOf(false) }
    var testFinished by remember { mutableStateOf(false) }
    var testToolsExpanded by remember { mutableStateOf(false) }
    var diagnosticsExpanded by remember { mutableStateOf(false) }
    var coachNotice by remember { mutableStateOf<CoachDeviationNotice?>(null) }
    var nextNoticeId by remember { mutableLongStateOf(0L) }
    var announcedSectionIndex by remember(run.trainingId, run.title) { mutableIntStateOf(-1) }
    var lastMessage by remember {
        mutableStateOf(if (initialGarminMode) "Warte auf Garmin-Uhr." else "Starte den Emulator-Lauf, um die Testwerte zu sehen.")
    }
    var garminMode by remember { mutableStateOf(initialGarminMode) }
    val serviceActive by LiveCoachForegroundService.active.collectAsState()
    val garminState by LiveCoachForegroundService.garminState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var appResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            appResumed = event == Lifecycle.Event.ON_RESUME ||
                (event != Lifecycle.Event.ON_PAUSE && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val screenInteractive = context.getSystemService(PowerManager::class.java)?.isInteractive == true
    val deviceLocked = context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
    fun showDeviationNotice(cue: CoachAudioCue, message: String) {
        if (shouldShowCoachDeviationNotice(cue, appResumed, screenInteractive, deviceLocked)) {
            nextNoticeId += 1
            coachNotice = CoachDeviationNotice(nextNoticeId, message)
        }
    }
    var lastHandledGarminCueSequence by remember { mutableLongStateOf(garminState.lastCoachCueSequence) }
    LaunchedEffect(garminState.lastCoachCueSequence) {
        val sequence = garminState.lastCoachCueSequence
        if (garminMode && sequence > lastHandledGarminCueSequence) {
            lastHandledGarminCueSequence = sequence
            garminState.lastCoachCue?.let { cue ->
                cue.deviationMessage()?.let { message ->
                    lastMessage = message
                    if (audioEnabled) showDeviationNotice(cue, message)
                }
            }
        } else if (sequence < lastHandledGarminCueSequence) {
            lastHandledGarminCueSequence = sequence
        }
    }
    var serviceWasActive by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(serviceActive) {
        if (serviceActive) serviceWasActive = true
        else if (serviceWasActive) running = false
    }
    LaunchedEffect(initialGarminMode) {
        if (initialGarminMode) onStartGarmin()
    }
    val coach = remember { LiveRunCoach() }
    val paceSmoother = remember { LivePaceSmoother() }
    val audioPlayer = remember(context) {
        AndroidCoachAudioPlayer(context)
    }
    val targets = remember(run.trainingId, run.prescription) { LiveRunTargets.from(run) }
    val speech = remember { mutableStateOf<TextToSpeech?>(null) }
    val speak: (CoachAudioCue, String) -> Unit = { cue, message ->
        val messageText = cue.deviationMessage() ?: message
        if (audioEnabled) {
            audioPlayer?.play(cue, onError = {
                if (LiveCoachForegroundService.audioAnnouncementsEnabled(context)) {
                    speech.value?.speak(messageText, TextToSpeech.QUEUE_FLUSH, null, "live-coach")
                }
            })
            showDeviationNotice(cue, messageText)
        }
        lastMessage = messageText
    }
    LaunchedEffect(audioEnabled) {
        if (!audioEnabled) {
            coachNotice = null
            audioPlayer.stop()
            speech.value?.stop()
        }
    }
    DisposableEffect(context) {
        val tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val localGermanVoice = speech.value?.voices?.firstOrNull {
                    !it.isNetworkConnectionRequired && it.locale.language == Locale.GERMAN.language
                }
                audioReady = localGermanVoice != null && speech.value?.setVoice(localGermanVoice) == TextToSpeech.SUCCESS
            }
        }
        tts?.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        speech.value = tts
        onDispose {
            LiveCoachForegroundService.stop(context)
            audioPlayer?.close()
            speech.value?.stop()
            speech.value?.shutdown()
            speech.value = null
        }
    }
    LaunchedEffect(running, connected, garminMode) {
        while (running && connected && !garminMode) {
            delay(1_000)
            seconds += 1
            val metrics = LiveRunMetrics(
                activeSeconds = seconds.toLong(),
                distanceMeters = seconds * 2.65,
                paceSecondsPerKilometer = paceSmoother.add(pace).also { it?.let { value -> smoothedPace = value } },
                heartRateBpm = heartRate,
                measuredAtMillis = System.currentTimeMillis(),
                source = LiveMetricSource.EMULATOR_TEST,
                isPaused = false
            )
            val sectionIndex = targets.sectionIndexAt(seconds.toLong())
            if (sectionIndex != null && sectionIndex != announcedSectionIndex) {
                if (announcedSectionIndex >= 0) {
                    targets.sectionAt(seconds.toLong())?.let { section ->
                        speak(section.transitionCue, transitionMessage(section.transitionCue))
                    }
                }
                announcedSectionIndex = sectionIndex
            }
            when (coach.nextCue(metrics, targets.atElapsedSeconds(seconds.toLong()), System.currentTimeMillis())) {
                LiveCoachCue.TOO_FAST -> speak(LiveCoachCue.TOO_FAST.toAudioCue(), "Du bist etwas zu schnell. Reduziere dein Tempo.")
                LiveCoachCue.TOO_SLOW -> speak(LiveCoachCue.TOO_SLOW.toAudioCue(), "Du bist unter deinem geplanten Tempobereich.")
                LiveCoachCue.HEART_RATE_HIGH -> speak(LiveCoachCue.HEART_RATE_HIGH.toAudioCue(), "Dein Puls liegt über dem Testbereich. Laufe etwas lockerer.")
                null -> Unit
            }
        }
    }

    val garminMetrics = garminState.metrics
    val waitingForGarmin = garminMode && garminMetrics == null
    val completedTest = BuildConfig.DEBUG && !garminMode && testFinished
    val activeRun = !waitingForGarmin && !completedTest && (garminMode && garminMetrics != null || running)
    val readyForTest = !garminMode && !running && !testFinished
    val displayedSeconds = if (garminMode) garminMetrics?.activeSeconds?.toInt() else seconds
    val displayedDistance = if (garminMode) garminMetrics?.distanceMeters else seconds * 2.7
    val displayedPace = if (garminMode) garminMetrics?.paceSecondsPerKilometer else smoothedPace
    val displayedHeartRate = if (garminMode) garminMetrics?.heartRateBpm else heartRate
    val currentStepIndex = displayedSeconds?.toLong()?.let(targets::sectionIndexAt)
    val currentStep = currentStepIndex?.let(run.steps::getOrNull)
    val targetSeconds = (run.totalMinutes * 60).coerceAtLeast(1)
    val goalProgress = ((displayedSeconds ?: 0) / targetSeconds.toFloat()).coerceIn(0f, 1f)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp).testTag("live-run-coach-screen"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            when { completedTest -> "Lauf beendet"; activeRun -> run.title; else -> "Live-Laufcoach" },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )

        when {
            waitingForGarmin -> StatusCard(
                title = if (garminState.connectionStatus == GarminLiveConnectionStatus.WATCH_CONNECTED) "Uhr verbunden" else "Uhr nicht verbunden",
                body = "Lauf auf der Garmin-Uhr starten. LAP wartet auf die Werte.",
                iconResource = R.drawable.run_garmin_devices_watch
            )
            readyForTest -> StatusCard(
                title = run.title,
                body = run.prescription.paceRange?.let {
                    "${run.totalMinutes} Min. · ${formatPace(it.fasterSecondsPerKm)}–${formatPace(it.slowerSecondsPerKm)} min/km"
                } ?: "${run.totalMinutes} Min. · Sprechtempo · RPE ${run.prescription.effortMinimum}–${run.prescription.effortMaximum}/10"
            )
            activeRun -> RunConnectionHeader(
                garminMode = garminMode,
                connected = if (garminMode) garminMetrics != null else connected,
                paused = if (garminMode) garminMetrics?.isPaused == true else !running
            )
            completedTest -> StatusCard(
                title = "Stark gemacht!",
                body = "Dein Testlauf ist beendet. Die Testwerte wurden nicht als Garmin-Aktivität gespeichert.",
                iconResource = R.drawable.run_status_feedback_success
            )
        }

        if (waitingForGarmin) HowToStartCard()
        if (garminMode) GarminLiveDiagnosticsPanel(
            diagnostics = garminState.diagnostics,
            expanded = diagnosticsExpanded,
            onToggle = { diagnosticsExpanded = !diagnosticsExpanded }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Metric("Timer", when {
                waitingForGarmin -> "–:––"
                completedTest -> formatDuration(seconds)
                else -> displayedSeconds?.let(::formatDuration) ?: "0:00"
            }, R.drawable.ic_workout_timer, Modifier.weight(1f))
            Metric("Distanz", when {
                waitingForGarmin -> "– km"
                else -> displayedDistance?.let { "%.2f km".format(Locale.GERMANY, it / 1000.0) } ?: "0,00 km"
            }, R.drawable.ic_workout_location, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Metric("Tempo", if (waitingForGarmin) "–:–– /km" else displayedPace?.let(::formatPace) ?: "–:–– /km", R.drawable.ic_workout_pace, Modifier.weight(1f))
            Metric("Puls", if (waitingForGarmin) "– bpm" else displayedHeartRate?.let { "$it bpm" } ?: "– bpm", R.drawable.ic_workout_heart, Modifier.weight(1f), MaterialTheme.colorScheme.error)
        }

        if (activeRun && currentStep != null) {
            val stepPosition = currentStepIndex?.plus(1) ?: 1
            CoachIllustratedCard(
                title = "Abschnitt $stepPosition von ${run.steps.size}: ${currentStep.name}",
                body = buildString {
                    append("${currentStep.minutes} Min. · ")
                    append(currentStep.effortCue ?: "Belastung nach Gefühl")
                    if (currentStep.effortMinimum != null && currentStep.effortMaximum != null) {
                        append(" · RPE ${currentStep.effortMinimum}–${currentStep.effortMaximum}/10")
                    }
                    val paceTarget = run.prescription.paceRange.takeIf {
                        currentStep.name.contains("laufen", ignoreCase = true) && (currentStep.effortMinimum ?: 0) >= 2
                    }
                    paceTarget?.let {
                        append(" · Pace ${formatPace(it.fasterSecondsPerKm)}–${formatPace(it.slowerSecondsPerKm)} min/km")
                    }
                }
            )
        }

        if (activeRun) GoalProgressCard(displayedSeconds ?: 0, run.totalMinutes, goalProgress)
        AudioStatusCard(audioEnabled) {
            audioEnabled = it
            LiveCoachForegroundService.setAudioAnnouncementsEnabled(context, it)
        }
        CoachMessageCard(
            title = when { completedTest -> "DEIN COACH"; waitingForGarmin -> "DEIN COACH"; else -> "DEIN COACH" },
            message = when {
                completedTest -> "Super Lauf!"
                waitingForGarmin -> "Warte auf deine Uhr."
                readyForTest -> "Bereit für deinen Lauf."
                garminMode -> garminState.lastCoachCue?.let(::coachCueText) ?: "Laufe locker weiter."
                !connected -> "Die Testverbindung ist unterbrochen."
                else -> lastMessage.ifBlank { "Locker weiterlaufen." }
            }
        )

        when {
            waitingForGarmin -> {
                Button(
                    onClick = {
                        lastMessage = "LAP wartet auf die Garmin-Uhr."
                        onStartGarmin()
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) { Text("Erneut prüfen") }
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(42.dp)) { Text("Später") }
            }
            readyForTest -> {
                Button(
                    onClick = {
                        testFinished = false
                        running = true
                        connected = true
                        announcedSectionIndex = -1
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        LiveCoachForegroundService.start(context)
                        speak(CoachAudioCue.RUN_STARTED, "Der Testlauf läuft.")
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp).testTag("live-coach-start-pause")
                ) { Text("Lauf starten") }
                OutlinedButton(
                    onClick = {
                        running = false
                        garminMode = true
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        onStartGarmin()
                    },
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) { Text("Uhr verbinden") }
            }
            activeRun -> {
                Button(
                    onClick = {
                        running = !running
                        if (running) LiveCoachForegroundService.resume(context)
                        else LiveCoachForegroundService.pause(context)
                        if (!garminMode) {
                            speak(
                                if (running) CoachAudioCue.RUN_RESUMED else CoachAudioCue.RUN_PAUSED,
                                if (running) "Der Testlauf läuft weiter." else "Der Testlauf ist pausiert."
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp).testTag("live-coach-start-pause")
                ) { Text(if (running) "Pausieren" else "Weiterlaufen") }
                OutlinedButton(
                    onClick = {
                        running = false
                        if (garminMode) {
                            LiveCoachForegroundService.stop(context)
                            garminMode = false
                            onBack()
                        } else {
                            testFinished = true
                            LiveCoachForegroundService.stop(context)
                            speak(CoachAudioCue.RUN_FINISHED, "Der Testlauf ist beendet.")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) { Text(if (garminMode) "Live-Begleitung beenden" else "Beenden") }
            }
            completedTest -> {
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(46.dp)) { Text("Fertig") }
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(42.dp)) { Text("Details ansehen") }
            }
        }

        if (BuildConfig.DEBUG) {
            EmulatorTestTools(
                expanded = testToolsExpanded,
                onToggle = { testToolsExpanded = !testToolsExpanded },
                onUseEmulator = {
                    LiveCoachForegroundService.stop(context)
                    garminMode = false
                    running = false
                    testFinished = false
                    seconds = 0
                    pace = 375
                    smoothedPace = 375
                    heartRate = 142
                },
                onShowActivePreview = {
                    garminMode = false
                    testFinished = false
                    connected = true
                    seconds = 24 * 60 + 18
                    pace = 362
                    smoothedPace = 362
                    heartRate = 146
                    running = true
                },
                onShowCompletionPreview = {
                    garminMode = false
                    running = false
                    testFinished = true
                    connected = true
                    seconds = 32 * 60 + 14
                    pace = 370
                    smoothedPace = 370
                    heartRate = 145
                },
                onStartGarmin = {
                    garminMode = true
                    running = false
                    testFinished = false
                    onStartGarmin()
                },
                onToggleConnection = {
                    connected = !connected
                    if (connected) LiveCoachForegroundService.resume(context) else LiveCoachForegroundService.pause(context)
                    speak(
                        if (connected) CoachAudioCue.CONNECTION_RESTORED else CoachAudioCue.CONNECTION_LOST,
                        if (connected) "Die Testverbindung ist wieder da." else "Die Testverbindung ist unterbrochen."
                    )
                },
                onSetPace = { pace = it },
                onSetHeartRate = { heartRate = it },
                onToggleAudio = { audioEnabled = !audioEnabled },
                onPlayCue = { cue, message -> speak(cue, message) },
                garminMode = garminMode,
                audioStatus = if (!audioEnabled) "Aus" else if (audioReady) "MP3 bereit · Offline-Stimme verfügbar" else "MP3 bereit"
            )
        }
    }
    CoachDeviationPopup(coachNotice) { id ->
        if (coachNotice?.id == id) coachNotice = null
    }
}

@Composable
private fun Metric(
    title: String,
    value: String,
    iconResource: Int,
    modifier: Modifier = Modifier,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(painter = painterResource(iconResource), contentDescription = null, modifier = Modifier.size(15.dp), tint = iconTint)
                }
                Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StatusCard(title: String, body: String, iconResource: Int? = null) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = .10f)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (iconResource != null) {
                Image(painterResource(iconResource), contentDescription = null, modifier = Modifier.size(52.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CoachIllustratedCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(R.drawable.coach_runner_background),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
                alpha = .24f
            )
            Box(
                Modifier.matchParentSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = .74f))
            )
            content()
        }
    }
}

@Composable
private fun CoachIllustratedCard(title: String, body: String, iconResource: Int? = null) {
    CoachIllustratedCard {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (iconResource != null) {
                Image(painterResource(iconResource), contentDescription = null, modifier = Modifier.size(52.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HowToStartCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("So geht's", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                HowToStep(R.drawable.run_garmin_devices_watch, "Garmin-Uhr", "Auf der Uhr: Laufen")
                HowToStep(R.drawable.run_howto_shoes, "Laufschuhe", "Lauf starten")
                HowToStep(R.drawable.run_howto_phone, "Smartphone", "Zu LAP zurück")
            }
        }
    }
}

@Composable
private fun RowScope.HowToStep(iconResource: Int, imageDescription: String, label: String) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(iconResource), contentDescription = imageDescription, modifier = Modifier.size(34.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2, minLines = 2)
    }
}

@Composable
private fun RunConnectionHeader(garminMode: Boolean, connected: Boolean, paused: Boolean) {
    val live = connected && !paused
    val dotOpacity = if (live) {
        val transition = rememberInfiniteTransition(label = "live-status-pulse")
        val animatedOpacity by transition.animateFloat(
            initialValue = .12f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 450),
                repeatMode = RepeatMode.Reverse
            ),
            label = "live-dot-opacity"
        )
        animatedOpacity
    } else {
        1f
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier.size(10.dp)
                    .graphicsLayer {
                        alpha = dotOpacity
                        val pulseScale = .75f + dotOpacity * .45f
                        scaleX = pulseScale
                        scaleY = pulseScale
                    }
                    .clip(CircleShape)
                    .background(if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            )
            Text(if (!connected) "Getrennt" else if (paused) "Pausiert" else "Live", fontWeight = FontWeight.SemiBold)
        }
        Text(
            if (garminMode) if (connected) "Garmin verbunden" else "Warte auf Garmin"
            else if (connected) "Testlauf" else "Testverbindung getrennt",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GoalProgressCard(seconds: Int, targetMinutes: Int, progress: Float) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ziel", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${seconds.coerceAtLeast(0) / 60} von $targetMinutes Min", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer
            )
        }
    }
}

private fun transitionMessage(cue: CoachAudioCue): String = when (cue) {
    CoachAudioCue.WARMUP -> "Beginne mit dem geplanten Aufwärmen."
    CoachAudioCue.MAIN_PART -> "Jetzt beginnt der nächste Laufabschnitt."
    CoachAudioCue.RECOVERY -> "Jetzt folgt die geplante Erholung."
    CoachAudioCue.COOL_DOWN -> "Beginne mit dem geplanten Auslaufen."
    else -> "Der nächste Trainingsabschnitt beginnt."
}

@Composable
private fun AudioStatusCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Audioansagen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(if (enabled) "Aktiv" else "Aus", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.testTag("live-coach-audio-toggle")
            )
        }
    }
}

@Composable
private fun CoachMessageCard(title: String, message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(message, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EmulatorTestTools(
    expanded: Boolean,
    onToggle: () -> Unit,
    onUseEmulator: () -> Unit,
    onShowActivePreview: () -> Unit,
    onShowCompletionPreview: () -> Unit,
    onStartGarmin: () -> Unit,
    onToggleConnection: () -> Unit,
    onSetPace: (Int) -> Unit,
    onSetHeartRate: (Int) -> Unit,
    onToggleAudio: () -> Unit,
    onPlayCue: (CoachAudioCue, String) -> Unit,
    garminMode: Boolean,
    audioStatus: String
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
                Text(if (expanded) "Emulator-Testwerkzeuge schließen" else "Emulator-Testwerkzeuge anzeigen")
            }
            if (expanded) {
                Text("Nur für Entwicklung und Emulator-Tests", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onUseEmulator, modifier = Modifier.weight(1f)) { Text("Testdaten") }
                    OutlinedButton(onClick = onStartGarmin, modifier = Modifier.weight(1f)) { Text("Garmin-Empfang") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onShowActivePreview, modifier = Modifier.weight(1f)) { Text("Laufansicht") }
                    OutlinedButton(onClick = onShowCompletionPreview, modifier = Modifier.weight(1f)) { Text("Abschlussansicht") }
                }
                if (!garminMode) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onSetPace(330) }, modifier = Modifier.weight(1f).testTag("live-coach-fast")) { Text("Tempo schnell") }
                        OutlinedButton(onClick = { onSetPace(430) }, modifier = Modifier.weight(1f)) { Text("Tempo langsam") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onSetHeartRate(172) }, modifier = Modifier.weight(1f)) { Text("Puls hoch") }
                        OutlinedButton(onClick = onToggleConnection, modifier = Modifier.weight(1f)) { Text("Verbindung testen") }
                    }
                    OutlinedButton(onClick = onToggleAudio, modifier = Modifier.fillMaxWidth()) { Text("Audio: $audioStatus") }
                    Text("MP3-Ansagen testen", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onPlayCue(CoachAudioCue.TOO_FAST, "Du bist etwas zu schnell. Reduziere dein Tempo.") }, modifier = Modifier.weight(1f)) { Text("Zu schnell") }
                        OutlinedButton(onClick = { onPlayCue(CoachAudioCue.TOO_SLOW, "Du bist unter deinem geplanten Tempobereich.") }, modifier = Modifier.weight(1f)) { Text("Zu langsam") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onPlayCue(CoachAudioCue.HEART_RATE_HIGH, "Dein Puls liegt über dem Testbereich. Laufe etwas lockerer.") }, modifier = Modifier.weight(1f)) { Text("Puls") }
                        OutlinedButton(onClick = { onPlayCue(CoachAudioCue.INTERVAL_START, "Das nächste Intervall beginnt.") }, modifier = Modifier.weight(1f)) { Text("Intervall") }
                    }
                    OutlinedButton(onClick = { onPlayCue(CoachAudioCue.RECOVERY, "Die Erholungsphase beginnt.") }, modifier = Modifier.fillMaxWidth()) { Text("Erholung") }
                }
            }
        }
    }
}

private fun formatDuration(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)
private fun formatDuration(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)
private fun formatPace(seconds: Int): String = "%d:%02d /km".format(seconds / 60, seconds % 60)

private fun garminStatusText(status: GarminLiveConnectionStatus): String = when (status) {
    GarminLiveConnectionStatus.IDLE -> "Noch nicht verbunden."
    GarminLiveConnectionStatus.INITIALIZING -> "Garmin Connect wird geöffnet …"
    GarminLiveConnectionStatus.GARMIN_CONNECT_UNAVAILABLE -> "Garmin Connect Mobile ist nicht verfügbar."
    GarminLiveConnectionStatus.WATCH_NOT_PAIRED -> "Keine Garmin-Uhr in Garmin Connect gekoppelt."
    GarminLiveConnectionStatus.WATCH_DISCONNECTED -> "Garmin-Uhr ist nicht verbunden."
    GarminLiveConnectionStatus.WATCH_CONNECTED -> "Garmin-Uhr verbunden. Suche LAP Live …"
    GarminLiveConnectionStatus.DATA_FIELD_NOT_INSTALLED -> "LAP Live ist noch nicht auf der Uhr installiert."
    GarminLiveConnectionStatus.WAITING_FOR_DATA -> "Warte auf Garmin-Uhr. Starte Aktivitäten → Laufen und öffne LAP Live als Datenseite."
    GarminLiveConnectionStatus.PING_RECEIVED -> "Uhrenkontakt bestätigt. Warte auf echte Laufwerte."
    GarminLiveConnectionStatus.RECEIVING -> "Echte Garmin-Messwerte kommen an."
    GarminLiveConnectionStatus.DATA_STALE -> "Messwerte sind veraltet. Prüfe Garmin Connect und die Verbindung."
}

private fun coachCueText(cue: CoachAudioCue): String = when (cue) {
    CoachAudioCue.LIVE_CONNECTED -> "Garmin-Livedaten sind verbunden. Der Laufcoach hört mit."
    CoachAudioCue.CONNECTION_LOST -> "Die Garmin-Verbindung ist unterbrochen. Es kommen keine neuen Messwerte."
    CoachAudioCue.CONNECTION_RESTORED -> "Garmin-Livedaten sind wieder da."
    CoachAudioCue.TOO_FAST, CoachAudioCue.TOO_SLOW, CoachAudioCue.HEART_RATE_HIGH -> cue.deviationMessage().orEmpty()
    else -> "Der Live-Coach hat eine neue Rückmeldung."
}
