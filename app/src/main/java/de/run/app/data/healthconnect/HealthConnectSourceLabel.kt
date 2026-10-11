package de.run.app.data.healthconnect

/** Names the app recorded by Health Connect as the writer, without guessing the upstream device. */
internal object HealthConnectSourceLabel {
    fun forPackage(packageName: String): String = when (packageName) {
        GARMIN_CONNECT_PACKAGE -> "Garmin Connect"
        HEALTH_SYNC_PACKAGE -> "Health Sync"
        else -> "Unbekannte Quelle"
    }

    private const val GARMIN_CONNECT_PACKAGE = "com.garmin.android.apps.connectmobile"
    private const val HEALTH_SYNC_PACKAGE = "nl.appyhapps.healthsync"
}
