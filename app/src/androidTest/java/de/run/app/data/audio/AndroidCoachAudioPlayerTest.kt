package de.run.app.data.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.run.app.domain.CoachAudioCue
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AndroidCoachAudioPlayerTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun everyBundledMp3PlaysToCompletionOnAndroidMediaPlayer() {
        AndroidCoachAudioPlayer(context).use { player ->
            CoachAudioCue.entries.forEach { cue ->
                val completed = CountDownLatch(1)
                assertTrue("MP3 konnte nicht gestartet werden: $cue", player.play(cue, onCompleted = completed::countDown))
                assertTrue("MP3 wurde nicht vollständig abgespielt: $cue", completed.await(20, TimeUnit.SECONDS))
            }
        }
    }
}
