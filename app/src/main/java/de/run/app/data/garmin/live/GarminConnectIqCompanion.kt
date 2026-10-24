package de.run.app.data.garmin.live

import android.content.Context
import android.util.Log
import de.run.app.BuildConfig
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import de.run.app.domain.model.GarminDataFieldState
import de.run.app.domain.model.GarminLiveDiagnostics
import de.run.app.domain.model.GarminConnectMobileState
import de.run.app.domain.model.GarminReceiverState
import de.run.app.domain.model.GarminSdkState
import de.run.app.domain.model.GarminTransportMode
import de.run.app.domain.model.GarminWatchState
import de.run.app.domain.GarminTrainingTarget
import de.run.app.domain.model.GarminTargetTransferState

/** Receives only messages from LAP's Connect IQ data field through Garmin Connect Mobile. */
internal class GarminConnectIqCompanion(
    context: Context,
    private val useSimulatorTransport: Boolean,
    private val onStatus: (GarminLiveConnectionStatus) -> Unit,
    private val onDiagnostics: (GarminLiveDiagnostics) -> Unit,
    private val onMessage: (List<Any?>) -> Unit,
    private val trainingTarget: GarminTrainingTarget?,
    private val onTargetTransfer: (GarminTargetTransferState) -> Unit
) {
    private val appContext = context.applicationContext
    private val connectionType = if (useSimulatorTransport) {
        ConnectIQ.IQConnectType.TETHERED
    } else ConnectIQ.IQConnectType.WIRELESS
    private val connectIq = ConnectIQ.getInstance(appContext, connectionType)
    private val registeredDevices = mutableSetOf<IQDevice>()
    private val registeredApplications = mutableMapOf<IQDevice, MutableList<IQApp>>()
    private val pendingApplicationInfo = mutableSetOf<IQDevice>()
    private val recentSimulatorMessages = LinkedHashSet<List<Any?>>()
    private var initialized = false
    private var stopRequested = false
    private var diagnostics = GarminLiveDiagnostics(
        transport = transportMode(useSimulatorTransport),
        dataFieldAppId = LAP_LIVE_DATA_FIELD_ID
    )

    fun start() {
        updateDiagnostics {
            it.copy(
                transport = transportMode(useSimulatorTransport),
                dataFieldAppId = LAP_LIVE_DATA_FIELD_ID,
                connectMobile = GarminConnectMobileState.CHECKING,
                knownDeviceName = null,
                sdk = GarminSdkState.INITIALIZING,
                watch = GarminWatchState.UNKNOWN,
                dataField = GarminDataFieldState.NOT_CHECKED,
                receiver = GarminReceiverState.NOT_REGISTERED,
                incomingMessages = 0L,
                rejectedMessages = 0L,
                validPings = 0L,
                validMeasurements = 0L,
                targetTransfer = GarminTargetTransferState.NOT_SENT,
                targetTrainingId = trainingTarget?.trainingId
            )
        }
        onStatus(GarminLiveConnectionStatus.INITIALIZING)
        if (useSimulatorTransport && tetheredSdkInitialized) {
            initialized = true
            updateDiagnostics {
                it.copy(connectMobile = GarminConnectMobileState.READY, sdk = GarminSdkState.READY)
            }
            loadPairedDevices()
            return
        }
        runCatching {
            connectIq.initialize(appContext, false, object : ConnectIQ.ConnectIQListener {
                override fun onSdkReady() {
                    initialized = true
                    updateDiagnostics {
                        it.copy(connectMobile = GarminConnectMobileState.READY, sdk = GarminSdkState.READY)
                    }
                    if (useSimulatorTransport) tetheredSdkInitialized = true
                    if (stopRequested) {
                        stop()
                        return
                    }
                    loadPairedDevices()
                }

                override fun onInitializeError(status: ConnectIQ.IQSdkErrorStatus) {
                    val connectMobile = when (status) {
                        ConnectIQ.IQSdkErrorStatus.GCM_NOT_INSTALLED -> GarminConnectMobileState.NOT_INSTALLED
                        ConnectIQ.IQSdkErrorStatus.GCM_UPGRADE_NEEDED -> GarminConnectMobileState.UPGRADE_REQUIRED
                        else -> GarminConnectMobileState.UNAVAILABLE
                    }
                    updateDiagnostics {
                        it.copy(connectMobile = connectMobile, sdk = GarminSdkState.UNAVAILABLE)
                    }
                    onStatus(GarminLiveConnectionStatus.GARMIN_CONNECT_UNAVAILABLE)
                }

                override fun onSdkShutDown() {
                    initialized = false
                    updateDiagnostics {
                        it.copy(connectMobile = GarminConnectMobileState.UNAVAILABLE, sdk = GarminSdkState.UNAVAILABLE)
                    }
                    onStatus(GarminLiveConnectionStatus.GARMIN_CONNECT_UNAVAILABLE)
                }
            })
        }.onFailure {
            updateDiagnostics {
                it.copy(connectMobile = GarminConnectMobileState.UNAVAILABLE, sdk = GarminSdkState.UNAVAILABLE)
            }
            onStatus(GarminLiveConnectionStatus.GARMIN_CONNECT_UNAVAILABLE)
        }
    }

    fun stop() {
        stopRequested = true
        if (!initialized) return
        registeredApplications.forEach { (device, apps) ->
            apps.forEach { app -> runCatching { connectIq.unregisterForApplicationEvents(device, app) } }
        }
        registeredDevices.forEach { device -> runCatching { connectIq.unregisterForDeviceEvents(device) } }
        registeredApplications.clear()
        registeredDevices.clear()
        pendingApplicationInfo.clear()
        synchronized(recentSimulatorMessages) { recentSimulatorMessages.clear() }
        if (!useSimulatorTransport) {
            runCatching { connectIq.shutdown(appContext) }
        }
        initialized = false
    }

    private fun loadPairedDevices() {
        val devices = runCatching { connectIq.knownDevices.orEmpty() }.getOrElse {
            updateDiagnostics {
                it.copy(connectMobile = GarminConnectMobileState.UNAVAILABLE, sdk = GarminSdkState.UNAVAILABLE)
            }
            onStatus(GarminLiveConnectionStatus.GARMIN_CONNECT_UNAVAILABLE)
            return
        }
        if (devices.isEmpty()) {
            updateDiagnostics { it.copy(watch = GarminWatchState.NOT_PAIRED) }
            onStatus(GarminLiveConnectionStatus.WATCH_NOT_PAIRED)
            return
        }
        val connectedDevice = devices.firstOrNull { it.status == IQDevice.IQDeviceStatus.CONNECTED }
        val displayedDevice = connectedDevice ?: devices.first()
        updateDiagnostics {
            it.copy(
                knownDeviceName = displayedDevice.friendlyName,
                watch = if (connectedDevice == null) GarminWatchState.DISCONNECTED else GarminWatchState.CONNECTED
            )
        }
        devices.forEach(::watchDevice)
        if (devices.none { it.status == IQDevice.IQDeviceStatus.CONNECTED }) {
            updateDiagnostics { it.copy(watch = GarminWatchState.DISCONNECTED) }
            onStatus(GarminLiveConnectionStatus.WATCH_DISCONNECTED)
        }
    }

    private fun watchDevice(device: IQDevice) {
        if (registeredDevices.add(device)) {
            runCatching {
                connectIq.registerForDeviceEvents(device) { changedDevice, newStatus ->
                    if (newStatus == IQDevice.IQDeviceStatus.CONNECTED) {
                        updateDiagnostics {
                            it.copy(watch = GarminWatchState.CONNECTED, knownDeviceName = changedDevice.friendlyName)
                        }
                        onStatus(GarminLiveConnectionStatus.WATCH_CONNECTED)
                        requestDataField(changedDevice)
                    } else {
                        pendingApplicationInfo.remove(changedDevice)
                        updateDiagnostics {
                            it.copy(watch = GarminWatchState.DISCONNECTED, knownDeviceName = changedDevice.friendlyName)
                        }
                        onStatus(GarminLiveConnectionStatus.WATCH_DISCONNECTED)
                    }
                }
            }.onFailure {
                registeredDevices.remove(device)
                updateDiagnostics { it.copy(watch = GarminWatchState.DISCONNECTED) }
                onStatus(GarminLiveConnectionStatus.GARMIN_CONNECT_UNAVAILABLE)
            }
        }
        if (device.status == IQDevice.IQDeviceStatus.CONNECTED) {
            updateDiagnostics { it.copy(watch = GarminWatchState.CONNECTED) }
            onStatus(GarminLiveConnectionStatus.WATCH_CONNECTED)
            requestDataField(device)
        } else {
            updateDiagnostics { it.copy(watch = GarminWatchState.DISCONNECTED) }
        }
    }

    private fun requestDataField(device: IQDevice) {
        if (device in registeredApplications || !pendingApplicationInfo.add(device)) return
        updateDiagnostics { it.copy(dataField = GarminDataFieldState.CHECKING) }
        if (useSimulatorTransport) {
            pendingApplicationInfo.remove(device)
            updateDiagnostics { it.copy(dataField = GarminDataFieldState.FOUND) }
            registerSimulatorMessages(device)
            return
        }
        runCatching {
            connectIq.getApplicationInfo(LAP_LIVE_DATA_FIELD_ID, device, object : ConnectIQ.IQApplicationInfoListener {
                override fun onApplicationInfoReceived(app: IQApp) {
                    pendingApplicationInfo.remove(device)
                    if (!isApplicationAvailable(app.status, useSimulatorTransport)) {
                        updateDiagnostics { it.copy(dataField = GarminDataFieldState.NOT_INSTALLED) }
                        onStatus(GarminLiveConnectionStatus.DATA_FIELD_NOT_INSTALLED)
                        return
                    }
                    updateDiagnostics { it.copy(dataField = GarminDataFieldState.FOUND) }
                    registerMessages(device, app)
                }

                override fun onApplicationNotInstalled(applicationId: String) {
                    pendingApplicationInfo.remove(device)
                    updateDiagnostics { it.copy(dataField = GarminDataFieldState.NOT_INSTALLED) }
                    onStatus(GarminLiveConnectionStatus.DATA_FIELD_NOT_INSTALLED)
                }
            })
        }.onFailure {
            pendingApplicationInfo.remove(device)
            updateDiagnostics { it.copy(dataField = GarminDataFieldState.ERROR) }
            onStatus(GarminLiveConnectionStatus.GARMIN_CONNECT_UNAVAILABLE)
        }
    }

    private fun registerMessages(device: IQDevice, app: IQApp) {
        registerMessages(device, listOf(app))
    }

    private fun registerSimulatorMessages(device: IQDevice) {
        registerMessages(
            device,
            listenerAppIds(LAP_LIVE_DATA_FIELD_ID, useSimulatorTransport = true).map { IQApp(it) },
            simulatorMode = true
        )
    }

    private fun registerMessages(device: IQDevice, apps: List<IQApp>) {
        registerMessages(device, apps, simulatorMode = false)
    }

    private fun registerMessages(device: IQDevice, apps: List<IQApp>, simulatorMode: Boolean) {
        if (device in registeredApplications) return
        updateDiagnostics { it.copy(receiver = GarminReceiverState.REGISTERING) }
        val registered = mutableListOf<IQApp>()
        registeredApplications[device] = registered
        apps.forEachIndexed { index, app ->
            val role = if (simulatorMode && index == 1) "empty" else "field_uuid"
            runCatching {
                connectIq.registerForAppEvents(device, app) { _, _, message, status ->
                    handleApplicationMessage(message, status)
                }
                registered.add(app)
                if (BuildConfig.DEBUG) Log.d(TAG, "listener_registered role=$role")
            }.onFailure {
                if (BuildConfig.DEBUG) Log.d(TAG, "listener_registration_failed role=$role")
            }
        }
        if (registered.isEmpty()) {
            registeredApplications.remove(device)
            updateDiagnostics { it.copy(receiver = GarminReceiverState.FAILED) }
            onStatus(GarminLiveConnectionStatus.GARMIN_CONNECT_UNAVAILABLE)
            return
        }
        updateDiagnostics { it.copy(receiver = GarminReceiverState.REGISTERED) }
        onStatus(GarminLiveConnectionStatus.WAITING_FOR_DATA)
        val targetAppId = if (simulatorMode) "" else LAP_LIVE_DATA_FIELD_ID
        sendTrainingTarget(device, registered.firstOrNull { it.applicationId == targetAppId })
    }

    private fun sendTrainingTarget(device: IQDevice, app: IQApp?) {
        val target = trainingTarget ?: return
        if (app == null) return
        runCatching {
            connectIq.sendMessage(device, app, target.toMessage()) { _, _, status ->
                onTargetTransfer(
                    if (status == ConnectIQ.IQMessageStatus.SUCCESS) GarminTargetTransferState.SENT
                    else GarminTargetTransferState.FAILED
                )
            }
        }.onFailure {
            onTargetTransfer(GarminTargetTransferState.FAILED)
        }
    }

    private fun handleApplicationMessage(message: List<Any?>?, status: ConnectIQ.IQMessageStatus) {
        updateDiagnostics {
            it.copy(
                incomingMessages = it.incomingMessages + 1,
                rejectedMessages = it.rejectedMessages + if (status == ConnectIQ.IQMessageStatus.SUCCESS) 0 else 1
            )
        }
        if (status != ConnectIQ.IQMessageStatus.SUCCESS) {
            if (BuildConfig.DEBUG) Log.d(TAG, "app_message_rejected status=$status")
            return
        }
        val stableMessage = message?.toList()
        if (useSimulatorTransport && stableMessage != null && !rememberNewSimulatorMessage(stableMessage)) {
            if (BuildConfig.DEBUG) Log.d(TAG, "app_message_duplicate")
            return
        }
        if (BuildConfig.DEBUG) Log.d(TAG, "app_message_received entries=${message?.size ?: 0}")
        onMessage(message.orEmpty())
    }

    private fun updateDiagnostics(transform: (GarminLiveDiagnostics) -> GarminLiveDiagnostics) {
        diagnostics = transform(diagnostics)
        onDiagnostics(diagnostics)
    }

    private fun rememberNewSimulatorMessage(message: List<Any?>): Boolean {
        return synchronized(recentSimulatorMessages) {
            rememberNewMessage(message, recentSimulatorMessages, MAX_RECENT_SIMULATOR_MESSAGES)
        }
    }

    companion object {
        private const val TAG = "LAP-GarminLive"
        const val LAP_LIVE_DATA_FIELD_ID = "7D574A829AAC4E83AF6A6E1F04E8A09B"
        private const val MAX_RECENT_SIMULATOR_MESSAGES = 32
        @Volatile
        private var tetheredSdkInitialized = false

        internal fun isApplicationAvailable(status: IQApp.IQAppStatus, useSimulatorTransport: Boolean): Boolean =
            status == IQApp.IQAppStatus.INSTALLED ||
                (useSimulatorTransport && status == IQApp.IQAppStatus.UNKNOWN)

        internal fun listenerAppIds(appId: String, useSimulatorTransport: Boolean): List<String> =
            if (useSimulatorTransport) listOf(appId, "") else listOf(appId)

        internal fun transportMode(useSimulatorTransport: Boolean): GarminTransportMode =
            if (useSimulatorTransport) GarminTransportMode.TETHERED else GarminTransportMode.WIRELESS

        internal fun rememberNewMessage(
            message: List<Any?>,
            recentMessages: LinkedHashSet<List<Any?>>,
            capacity: Int = MAX_RECENT_SIMULATOR_MESSAGES
        ): Boolean {
            if (!recentMessages.add(message)) return false
            while (recentMessages.size > capacity) recentMessages.remove(recentMessages.first())
            return true
        }
    }
}

enum class GarminLiveConnectionStatus {
    IDLE,
    INITIALIZING,
    GARMIN_CONNECT_UNAVAILABLE,
    WATCH_NOT_PAIRED,
    WATCH_DISCONNECTED,
    WATCH_CONNECTED,
    DATA_FIELD_NOT_INSTALLED,
    WAITING_FOR_DATA,
    PING_RECEIVED,
    RECEIVING,
    DATA_STALE
}
