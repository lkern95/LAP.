package de.run.app.domain.model

data class GarminLiveDiagnostics(
    val transport: GarminTransportMode = GarminTransportMode.UNKNOWN,
    val connectMobile: GarminConnectMobileState = GarminConnectMobileState.NOT_CHECKED,
    val dataFieldAppId: String? = null,
    val knownDeviceName: String? = null,
    val sdk: GarminSdkState = GarminSdkState.NOT_STARTED,
    val watch: GarminWatchState = GarminWatchState.UNKNOWN,
    val dataField: GarminDataFieldState = GarminDataFieldState.NOT_CHECKED,
    val receiver: GarminReceiverState = GarminReceiverState.NOT_REGISTERED,
    val incomingMessages: Long = 0L,
    val rejectedMessages: Long = 0L,
    val validPings: Long = 0L,
    val validMeasurements: Long = 0L,
    val targetTransfer: GarminTargetTransferState = GarminTargetTransferState.NOT_SENT,
    val targetTrainingId: String? = null
)

enum class GarminTargetTransferState { NOT_SENT, SENT, ACKNOWLEDGED, FAILED }

enum class GarminTransportMode { UNKNOWN, WIRELESS, TETHERED }
enum class GarminConnectMobileState { NOT_CHECKED, CHECKING, READY, NOT_INSTALLED, UPGRADE_REQUIRED, UNAVAILABLE }
enum class GarminSdkState { NOT_STARTED, INITIALIZING, READY, UNAVAILABLE }
enum class GarminWatchState { UNKNOWN, NOT_PAIRED, DISCONNECTED, CONNECTED }
enum class GarminDataFieldState { NOT_CHECKED, CHECKING, FOUND, NOT_INSTALLED, ERROR }
enum class GarminReceiverState { NOT_REGISTERED, REGISTERING, REGISTERED, FAILED }
