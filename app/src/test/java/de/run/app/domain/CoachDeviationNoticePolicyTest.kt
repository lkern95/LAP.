package de.run.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachDeviationNoticePolicyTest {
    @Test fun importantActiveDeviationShowsTheNoticeOnlyWhileTheScreenIsAvailable() {
        assertTrue(shouldShowCoachDeviationNotice(CoachAudioCue.TOO_FAST, true, true, false))
        assertFalse(shouldShowCoachDeviationNotice(CoachAudioCue.TOO_FAST, false, true, false))
        assertFalse(shouldShowCoachDeviationNotice(CoachAudioCue.TOO_FAST, true, false, false))
        assertFalse(shouldShowCoachDeviationNotice(CoachAudioCue.TOO_FAST, true, true, true))
        assertFalse(shouldShowCoachDeviationNotice(CoachAudioCue.WARMUP, true, true, false))
    }

    @Test fun messageUsedForDeviationNoticeIsSharedAndNotAddedForRoutineCues() {
        assertEquals(
            "Du bist schneller als der geplante Tempobereich. Laufe etwas ruhiger.",
            CoachAudioCue.TOO_FAST.deviationMessage()
        )
        assertNull(CoachAudioCue.WARMUP.deviationMessage())
    }
}
