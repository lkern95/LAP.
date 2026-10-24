package de.run.app.data.audio

import android.content.Context

/** Stores the user's audio-announcement choice for both the screen and live service. */
internal class CoachAudioSettings(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = preferences.getBoolean(KEY_ENABLED, true)

    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    private companion object {
        const val FILE_NAME = "coach_audio_settings"
        const val KEY_ENABLED = "announcements_enabled"
    }
}
