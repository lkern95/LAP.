package de.run.app.data.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import de.run.app.MainActivity
import de.run.app.BuildConfig
import de.run.app.R
import de.run.app.data.garmin.live.GarminConnectIqCompanion
import de.run.app.data.garmin.live.GarminLiveConnectionStatus
import de.run.app.domain.CoachAudioCue
import de.run.app.domain.GarminLivePacket
import de.run.app.domain.GarminLivePacketSequenceGate
import de.run.app.domain.GarminLivePing
import de.run.app.domain.LiveRunCoach
import de.run.app.domain.LiveRunMetrics
import de.run.app.domain.LiveRunTargets
import de.run.app.domain.GarminTrainingTarget
import de.run.app.domain.model.GarminTargetTransferState
import de.run.app.domain.toAudioCue
import de.run.app.domain.model.GarminLiveDiagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LiveCoachForegroundService : Service() {
    private var paused = false
    private var garminConnection: GarminConnectIqCompanion? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var coach = LiveRunCoach()
    private val sequenceGate = GarminLivePacketSequenceGate()
    private val paceSmoother = de.run.app.domain.LivePaceSmoother()
    private val audioPlayer by lazy { AndroidCoachAudioPlayer(this) }
    private val audioSettings by lazy { CoachAudioSettings(this) }
    private var lastPacketAtMillis = 0L
    private var receivedPacketCount = 0L
    private var firstPacketAtMillis = 0L
    private var connectionLostAnnounced = false
    private var lastCoachSectionIndex: Int? = null
    private var targets = LiveRunTargets()
    private var coachCueSequence = 0L

    private val freshnessCheck = object : Runnable {
        override fun run() {
            refreshGarminFreshness()
            if (garminConnection != null) mainHandler.postDelayed(this, FRESHNESS_INTERVAL_MILLIS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        coachCueSequence = mutableGarminState.value.lastCoachCueSequence
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopGarminConnection()
                audioPlayer.stop()
                mutableActive.value = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                paused = true
                audioPlayer.stop()
            }
            ACTION_RESUME -> paused = false
            ACTION_START -> paused = false
            ACTION_START_GARMIN -> {
                paused = false
                val sectionValues = intent.getIntegerArrayListExtra(EXTRA_TARGET_SECTIONS)
                val trainingTarget = intent.getStringExtra(EXTRA_TARGET_ID)?.let { id ->
                    intent.getIntExtra(EXTRA_TARGET_DURATION_SECONDS, NO_TARGET)
                        .takeIf { it > 0 }
                        ?.let { GarminTrainingTarget(id, it) }
                }
                targets = LiveRunTargets.fromTransportValues(sectionValues).takeIf { it.sections.isNotEmpty() }
                    ?: LiveRunTargets(
                        minPaceSecondsPerKilometer = intent.getIntExtra(EXTRA_MIN_PACE, NO_TARGET).takeIf { it > 0 },
                        maxPaceSecondsPerKilometer = intent.getIntExtra(EXTRA_MAX_PACE, NO_TARGET).takeIf { it > 0 },
                        maxHeartRateBpm = intent.getIntExtra(EXTRA_MAX_HEART_RATE, NO_TARGET).takeIf { it > 0 }
                    )
                targets = targets.copy(trainingTarget = trainingTarget)
            }
            ACTION_AUDIO_SETTING_CHANGED -> {
                if (!audioSettings.isEnabled()) audioPlayer.stop()
                return START_NOT_STICKY
            }
        }
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        mutableActive.value = true
        if (intent?.action == ACTION_START_GARMIN) startGarminConnection()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.run_navigation_home)
        .setContentTitle(if (BuildConfig.DEBUG) "LAP-Testlauf aktiv" else "LAP-Laufbegleitung aktiv")
        .setContentText(notificationStatus())
        .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), pendingIntentFlags()))
        .addAction(0, "Begleitung beenden", servicePendingIntent(ACTION_STOP))
        .build()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Laufbegleitung", NotificationManager.IMPORTANCE_LOW)
            .apply { description = "Status der aktiven LAP-Laufbegleitung" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun notificationStatus(): String {
        if (paused) return "Pausiert"
        return when (mutableGarminState.value.connectionStatus) {
            GarminLiveConnectionStatus.INITIALIZING -> "Verbinde mit Garmin Connect …"
            GarminLiveConnectionStatus.WATCH_NOT_PAIRED -> "Garmin-Uhr ist nicht mit Garmin Connect gekoppelt."
            GarminLiveConnectionStatus.WATCH_DISCONNECTED -> "Garmin-Uhr ist gerade nicht verbunden."
            GarminLiveConnectionStatus.DATA_FIELD_NOT_INSTALLED -> "LAP Live muss auf der Uhr installiert werden."
            GarminLiveConnectionStatus.WAITING_FOR_DATA -> "Warte auf LAP-Live-Daten aus der Laufaufzeichnung."
            GarminLiveConnectionStatus.PING_RECEIVED -> "Uhrenkontakt bestätigt. Warte auf Live-Laufwerte."
            GarminLiveConnectionStatus.RECEIVING -> "Empfange Live-Werte von der Garmin-Uhr."
            GarminLiveConnectionStatus.DATA_STALE -> "Keine aktuellen Garmin-Werte. Verbindung prüfen."
            GarminLiveConnectionStatus.GARMIN_CONNECT_UNAVAILABLE -> "Garmin Connect ist nicht erreichbar."
            else -> if (BuildConfig.DEBUG) "Emulator-Testlauf läuft." else "LAP-Laufbegleitung läuft."
        }
    }

    private fun startGarminConnection() {
        stopGarminConnection()
        mutableGarminState.value = GarminLiveSessionState(
            connectionStatus = GarminLiveConnectionStatus.INITIALIZING
        )
        lastPacketAtMillis = 0L
        receivedPacketCount = 0L
        firstPacketAtMillis = 0L
        connectionLostAnnounced = false
        lastCoachSectionIndex = null
        sequenceGate.reset()
        lastCoachSectionIndex = null
        coach = LiveRunCoach()
        paceSmoother.reset()
        garminConnection = GarminConnectIqCompanion(
            this,
            useSimulatorTransport = BuildConfig.DEBUG,
            onStatus = { status -> mainHandler.post { updateConnectionStatus(status) } },
            onDiagnostics = { diagnostics -> mainHandler.post { updateGarminDiagnostics(diagnostics) } },
            onMessage = { message -> mainHandler.post { receiveGarminMessage(message) } },
            trainingTarget = targets.trainingTarget,
            onTargetTransfer = { transferState ->
                mainHandler.post {
                    val current = mutableGarminState.value.diagnostics
                    val finalState = if (current.targetTransfer == GarminTargetTransferState.ACKNOWLEDGED &&
                        transferState == GarminTargetTransferState.SENT
                    ) current.targetTransfer else transferState
                    updateGarminDiagnostics(current.copy(targetTransfer = finalState))
                }
            }
        ).also { it.start() }
        mainHandler.removeCallbacks(freshnessCheck)
        mainHandler.postDelayed(freshnessCheck, FRESHNESS_INTERVAL_MILLIS)
    }

    private fun stopGarminConnection() {
        mainHandler.removeCallbacks(freshnessCheck)
        garminConnection?.stop()
        garminConnection = null
        mutableGarminState.value = GarminLiveSessionState()
    }

    private fun updateConnectionStatus(status: GarminLiveConnectionStatus) {
        mutableGarminState.value = mutableGarminState.value.copy(connectionStatus = status)
        updateForegroundNotification()
    }

    private fun updateGarminDiagnostics(diagnostics: GarminLiveDiagnostics) {
        val current = mutableGarminState.value.diagnostics
        val transferState = if (current.targetTransfer == GarminTargetTransferState.ACKNOWLEDGED &&
            diagnostics.targetTransfer != GarminTargetTransferState.FAILED
        ) current.targetTransfer else diagnostics.targetTransfer
        mutableGarminState.value = mutableGarminState.value.copy(
            diagnostics = diagnostics.copy(
                targetTransfer = transferState,
                targetTrainingId = diagnostics.targetTrainingId ?: current.targetTrainingId
            )
        )
    }

    private fun receiveGarminMessage(message: List<Any?>) {
        GarminTrainingTarget.decodeAcknowledgement(message)?.let { acknowledgement ->
            val diagnostics = mutableGarminState.value.diagnostics
            if (acknowledgement == targets.trainingTarget &&
                diagnostics.targetTransfer != GarminTargetTransferState.FAILED
            ) {
                mutableGarminState.value = mutableGarminState.value.copy(
                    diagnostics = diagnostics.copy(targetTransfer = GarminTargetTransferState.ACKNOWLEDGED)
                )
            }
            return
        }
        GarminLivePing.decode(message)?.let { ping ->
            val currentState = mutableGarminState.value
            val validPingCount = currentState.diagnostics.validPings + 1
            if (BuildConfig.DEBUG) Log.d(TAG, "ping_accepted count=$validPingCount sequence=${ping.sequence}")
            mutableGarminState.value = currentState.copy(
                connectionStatus = GarminLiveConnectionStatus.PING_RECEIVED,
                diagnostics = currentState.diagnostics.copy(validPings = validPingCount)
            )
            updateForegroundNotification()
            return
        }
        val now = System.currentTimeMillis()
        val packet = GarminLivePacket.decode(message, now) ?: run {
            if (BuildConfig.DEBUG) Log.d(TAG, "packet_rejected reason=invalid_shape_or_time entries=${message.size}")
            rejectGarminMessage()
            return
        }
        if (!sequenceGate.accept(packet)) {
            if (BuildConfig.DEBUG) Log.d(TAG, "packet_rejected reason=duplicate_or_reordered")
            rejectGarminMessage()
            return
        }
        val wasDisconnected = connectionLostAnnounced ||
            mutableGarminState.value.connectionStatus == GarminLiveConnectionStatus.DATA_STALE
        val isFirstPacket = receivedPacketCount == 0L
        val metrics = packet.toMetrics().let { current ->
            if (current.paceSecondsPerKilometer == null) current
            else current.copy(paceSecondsPerKilometer = paceSmoother.add(current.paceSecondsPerKilometer))
        }
        lastPacketAtMillis = now
        receivedPacketCount += 1
        if (BuildConfig.DEBUG) Log.d(TAG, "packet_accepted count=$receivedPacketCount")
        connectionLostAnnounced = false
        if (isFirstPacket) {
            firstPacketAtMillis = now
        }
        val elapsedMillis = now - firstPacketAtMillis
        val rate = if (receivedPacketCount <= 1 || elapsedMillis <= 0L) 0.0
        else (receivedPacketCount - 1) * 1_000.0 / elapsedMillis
        val latency = (now - packet.watchTimeSeconds * 1_000L).coerceAtLeast(0L)
        mutableGarminState.value = GarminLiveSessionState(
            connectionStatus = GarminLiveConnectionStatus.RECEIVING,
            metrics = metrics,
            estimatedLatencyMillis = latency,
            averageMessagesPerSecond = rate,
            diagnostics = mutableGarminState.value.diagnostics.copy(validMeasurements = receivedPacketCount),
            lastCoachCue = mutableGarminState.value.lastCoachCue
        )
        updateForegroundNotification()
        if (!paused && isFirstPacket) playCoachCue(CoachAudioCue.LIVE_CONNECTED)
        else if (!paused && wasDisconnected) playCoachCue(CoachAudioCue.CONNECTION_RESTORED)
        else if (!paused) {
            announceSectionChange(metrics.activeSeconds)
            coach.nextCue(metrics, targets.atElapsedSeconds(metrics.activeSeconds), now)
                ?.let { playCoachCue(it.toAudioCue()) }
        }
    }

    private fun announceSectionChange(activeSeconds: Long) {
        val sectionIndex = targets.sectionIndexAt(activeSeconds) ?: return
        if (sectionIndex == lastCoachSectionIndex) return
        val previousIndex = lastCoachSectionIndex
        lastCoachSectionIndex = sectionIndex
        if (previousIndex != null) {
            targets.sectionAt(activeSeconds)?.transitionCue?.let(::playCoachCue)
        }
    }

    private fun rejectGarminMessage() {
        val current = mutableGarminState.value
        mutableGarminState.value = current.copy(
            diagnostics = current.diagnostics.copy(rejectedMessages = current.diagnostics.rejectedMessages + 1)
        )
    }

    private fun refreshGarminFreshness() {
        if (garminConnection == null || lastPacketAtMillis == 0L) return
        if (System.currentTimeMillis() - lastPacketAtMillis <= DATA_STALE_AFTER_MILLIS) return
        val previousStatus = mutableGarminState.value.connectionStatus
        mutableGarminState.value = mutableGarminState.value.copy(
            connectionStatus = GarminLiveConnectionStatus.DATA_STALE,
            metrics = null
        )
        updateForegroundNotification()
        if (previousStatus == GarminLiveConnectionStatus.RECEIVING && !connectionLostAnnounced && !paused) {
            playCoachCue(CoachAudioCue.CONNECTION_LOST)
            connectionLostAnnounced = true
        }
    }

    private fun updateForegroundNotification() {
        val notification = buildNotification()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun playCoachCue(cue: CoachAudioCue) {
        coachCueSequence += 1
        mutableGarminState.value = mutableGarminState.value.copy(
            lastCoachCue = cue,
            lastCoachCueSequence = coachCueSequence,
            lastCoachCueAtMillis = System.currentTimeMillis()
        )
        if (audioSettings.isEnabled()) audioPlayer.play(cue)
    }

    private fun servicePendingIntent(action: String): PendingIntent = PendingIntent.getService(
        this,
        action.hashCode(),
        Intent(this, LiveCoachForegroundService::class.java).setAction(action),
        pendingIntentFlags()
    )

    private fun pendingIntentFlags(): Int = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    companion object {
        private const val TAG = "LAP-GarminLive"
        private const val CHANNEL_ID = "lap_live_coach"
        const val NOTIFICATION_ID = 4107
        private val mutableActive = MutableStateFlow(false)
        val active: StateFlow<Boolean> = mutableActive.asStateFlow()
        private val mutableGarminState = MutableStateFlow(GarminLiveSessionState())
        val garminState: StateFlow<GarminLiveSessionState> = mutableGarminState.asStateFlow()
        private const val ACTION_START = "de.run.app.livecoach.START"
        private const val ACTION_START_GARMIN = "de.run.app.livecoach.START_GARMIN"
        private const val ACTION_PAUSE = "de.run.app.livecoach.PAUSE"
        private const val ACTION_RESUME = "de.run.app.livecoach.RESUME"
        private const val ACTION_STOP = "de.run.app.livecoach.STOP"
        private const val ACTION_AUDIO_SETTING_CHANGED = "de.run.app.livecoach.AUDIO_SETTING_CHANGED"
        private const val EXTRA_MIN_PACE = "de.run.app.livecoach.MIN_PACE"
        private const val EXTRA_MAX_PACE = "de.run.app.livecoach.MAX_PACE"
        private const val EXTRA_MAX_HEART_RATE = "de.run.app.livecoach.MAX_HEART_RATE"
        private const val EXTRA_TARGET_SECTIONS = "de.run.app.livecoach.TARGET_SECTIONS"
        private const val EXTRA_TARGET_ID = "de.run.app.livecoach.TARGET_ID"
        private const val EXTRA_TARGET_DURATION_SECONDS = "de.run.app.livecoach.TARGET_DURATION_SECONDS"
        private const val NO_TARGET = -1

        fun start(context: Context) = send(context, ACTION_START, foreground = true)
        fun startGarmin(context: Context, targets: LiveRunTargets = LiveRunTargets()) =
            send(context, ACTION_START_GARMIN, foreground = true, targets = targets)
        fun pause(context: Context) = send(context, ACTION_PAUSE)
        fun resume(context: Context) = send(context, ACTION_RESUME)
        fun stop(context: Context) = send(context, ACTION_STOP)

        fun audioAnnouncementsEnabled(context: Context): Boolean = CoachAudioSettings(context).isEnabled()

        fun setAudioAnnouncementsEnabled(context: Context, enabled: Boolean) {
            CoachAudioSettings(context).setEnabled(enabled)
            if (mutableActive.value) send(context, ACTION_AUDIO_SETTING_CHANGED)
        }

        private fun send(
            context: Context,
            action: String,
            foreground: Boolean = false,
            targets: LiveRunTargets? = null
        ) {
            val intent = Intent(context, LiveCoachForegroundService::class.java).setAction(action)
            targets?.minPaceSecondsPerKilometer?.let { intent.putExtra(EXTRA_MIN_PACE, it) }
            targets?.maxPaceSecondsPerKilometer?.let { intent.putExtra(EXTRA_MAX_PACE, it) }
            targets?.maxHeartRateBpm?.let { intent.putExtra(EXTRA_MAX_HEART_RATE, it) }
            targets?.takeIf { it.sections.isNotEmpty() }?.toTransportValues()?.let { values ->
                intent.putIntegerArrayListExtra(EXTRA_TARGET_SECTIONS, values)
            }
            targets?.trainingTarget?.let { target ->
                intent.putExtra(EXTRA_TARGET_ID, target.trainingId)
                intent.putExtra(EXTRA_TARGET_DURATION_SECONDS, target.durationSeconds)
            }
            if (foreground) ContextCompat.startForegroundService(context, intent)
            else context.startService(intent)
        }
    }

    override fun onDestroy() {
        stopGarminConnection()
        audioPlayer.close()
        mutableActive.value = false
        super.onDestroy()
    }

    private val FRESHNESS_INTERVAL_MILLIS = 1_000L
    private val DATA_STALE_AFTER_MILLIS = 5_000L
}

data class GarminLiveSessionState(
    val connectionStatus: GarminLiveConnectionStatus = GarminLiveConnectionStatus.IDLE,
    val diagnostics: GarminLiveDiagnostics = GarminLiveDiagnostics(),
    val metrics: LiveRunMetrics? = null,
    val estimatedLatencyMillis: Long? = null,
    val averageMessagesPerSecond: Double = 0.0,
    val lastCoachCue: CoachAudioCue? = null,
    val lastCoachCueSequence: Long = 0L,
    val lastCoachCueAtMillis: Long = 0L
)
