package de.run.app.data.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.annotation.RawRes
import de.run.app.R
import de.run.app.domain.CoachAudioCue
import de.run.app.domain.mayInterrupt

class AndroidCoachAudioPlayer(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private var focusRequest: AudioFocusRequest? = null
    private var player: MediaPlayer? = null
    private var playingCue: CoachAudioCue? = null

    fun play(cue: CoachAudioCue, onError: () -> Unit = {}, onCompleted: () -> Unit = {}): Boolean {
        val activeCue = playingCue
        if (activeCue != null && !cue.mayInterrupt(activeCue)) return false
        stopCurrent()
        if (!requestFocus()) {
            onError()
            return false
        }
        val mediaPlayer = runCatching { MediaPlayer.create(appContext, cue.resourceId()) }.getOrNull()
        if (mediaPlayer == null) {
            abandonFocus()
            onError()
            return false
        }
        player = mediaPlayer
        playingCue = cue
        mediaPlayer.setAudioAttributes(audioAttributes)
        mediaPlayer.setOnCompletionListener {
            finish(mediaPlayer)
            onCompleted()
        }
        mediaPlayer.setOnErrorListener { _, _, _ ->
            finish(mediaPlayer)
            onError()
            true
        }
        return runCatching { mediaPlayer.start(); true }.getOrElse {
            finish(mediaPlayer)
            onError()
            false
        }
    }

    fun stop() = stopCurrent()

    override fun close() = stopCurrent()

    private fun requestFocus(): Boolean {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(audioAttributes)
            .setOnAudioFocusChangeListener { focusChange ->
                if (focusChange <= AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stopCurrent()
            }
            .build()
        focusRequest = request
        return audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun finish(completed: MediaPlayer) {
        if (player !== completed) return
        completed.setOnCompletionListener(null)
        completed.setOnErrorListener(null)
        completed.release()
        player = null
        playingCue = null
        abandonFocus()
    }

    private fun stopCurrent() {
        player?.runCatching { setOnCompletionListener(null); setOnErrorListener(null); stop(); release() }
        player = null
        playingCue = null
        abandonFocus()
    }

    private fun abandonFocus() {
        focusRequest?.let(audioManager::abandonAudioFocusRequest)
        focusRequest = null
    }

    @RawRes
    private fun CoachAudioCue.resourceId(): Int = when (this) {
        CoachAudioCue.READY -> R.raw.coach_ready
        CoachAudioCue.RUN_STARTED -> R.raw.coach_run_started
        CoachAudioCue.RUN_PAUSED -> R.raw.coach_run_paused
        CoachAudioCue.RUN_RESUMED -> R.raw.coach_run_resumed
        CoachAudioCue.RUN_FINISHED -> R.raw.coach_run_finished
        CoachAudioCue.TOO_FAST -> R.raw.coach_pace_too_fast
        CoachAudioCue.TOO_SLOW -> R.raw.coach_pace_too_slow
        CoachAudioCue.HEART_RATE_HIGH -> R.raw.coach_heart_rate_too_high
        CoachAudioCue.PACE_ON_TARGET -> R.raw.coach_pace_on_target
        CoachAudioCue.HEART_RATE_ON_TARGET -> R.raw.coach_heart_rate_on_target
        CoachAudioCue.HIGH_LOAD -> R.raw.coach_load_too_high
        CoachAudioCue.WARMUP -> R.raw.coach_warmup
        CoachAudioCue.MAIN_PART -> R.raw.coach_main_part
        CoachAudioCue.HALFWAY -> R.raw.coach_halfway
        CoachAudioCue.RECOVERY -> R.raw.coach_interval_recovery
        CoachAudioCue.INTERVAL_START -> R.raw.coach_interval_start
        CoachAudioCue.COOL_DOWN -> R.raw.coach_cooldown
        CoachAudioCue.LIVE_CONNECTED -> R.raw.coach_live_connected
        CoachAudioCue.CONNECTION_LOST -> R.raw.coach_connection_lost
        CoachAudioCue.CONNECTION_RESTORED -> R.raw.coach_connection_restored
        CoachAudioCue.LIVE_UNAVAILABLE -> R.raw.coach_live_unavailable
        CoachAudioCue.METRICS_UNRELIABLE -> R.raw.coach_metrics_unreliable
        CoachAudioCue.REST_DAY -> R.raw.coach_rest_day
        CoachAudioCue.FIVE_MINUTES -> R.raw.coach_five_minutes_left
        CoachAudioCue.TRAINING_COMPLETED -> R.raw.coach_training_completed
    }
}
