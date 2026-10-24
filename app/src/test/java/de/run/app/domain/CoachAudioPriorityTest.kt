package de.run.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachAudioPriorityTest {
    @Test
    fun warningCanInterruptRoutinePrompt() {
        assertTrue(CoachAudioCue.HEART_RATE_HIGH.mayInterrupt(CoachAudioCue.HALFWAY))
    }

    @Test
    fun routinePromptCannotInterruptWarningOrRepeatItself() {
        assertFalse(CoachAudioCue.HALFWAY.mayInterrupt(CoachAudioCue.TOO_FAST))
        assertFalse(CoachAudioCue.TOO_FAST.mayInterrupt(CoachAudioCue.TOO_FAST))
    }

    @Test
    fun safetyPromptCanInterruptWarningButWarningCannotInterruptSafetyPrompt() {
        assertTrue(CoachAudioCue.CONNECTION_LOST.mayInterrupt(CoachAudioCue.TOO_SLOW))
        assertFalse(CoachAudioCue.TOO_SLOW.mayInterrupt(CoachAudioCue.CONNECTION_LOST))
    }
}
