package de.run.app.domain

object GarminConnectHealthSharing {
    const val MINIMUM_ANDROID_API = 34

    fun isSupported(androidApiLevel: Int): Boolean = androidApiLevel >= MINIMUM_ANDROID_API
}
