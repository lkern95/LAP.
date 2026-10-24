package de.run.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutDuplicateMatcherTest {
    @Test
    fun matchesSameRunAcrossFitAndHealthConnectAndKeepsFeedback() {
        val healthConnect = workout("com.garmin.android.apps.connectmobile:hc-1", "Garmin Connect", 10_000L, 3_000.0)
        val fit = workout("fit:1", "FIT-Datei", 20_000L, 3_020.0)
        val existing = healthConnect.copy(perceivedEffort = 6, feedbackRecordedAtMillis = 99L)

        assertTrue(WorkoutDuplicateMatcher.isDuplicate(existing, fit))
        val merged = WorkoutDuplicateMatcher.merge(existing, fit)
        assertEquals(healthConnect.sourceId, merged.sourceId)
        assertEquals("Garmin Connect", merged.sourceName)
        assertEquals(6, merged.perceivedEffort)
        assertEquals(99L, merged.feedbackRecordedAtMillis)
        assertEquals(3_000.0, merged.distanceMeters!!, 0.0)
    }

    @Test
    fun doesNotMergeSeparateRunsOrRecordsFromTheSameSource() {
        val garmin = workout("garmin:1", "Garmin Connect", 10_000L, 5_000.0)
        val laterFit = workout("fit:2", "FIT-Datei", 200_000L, 5_000.0)
        val secondGarmin = workout("garmin:2", "Garmin Connect", 10_000L, 5_000.0)

        assertFalse(WorkoutDuplicateMatcher.isDuplicate(garmin, laterFit))
        assertFalse(WorkoutDuplicateMatcher.isDuplicate(garmin, secondGarmin))
    }

    @Test
    fun doesNotMergeWhenDistanceIsMissingOrMateriallyDifferent() {
        val known = workout("garmin:1", "Garmin Connect", 10_000L, 5_000.0)
        assertFalse(WorkoutDuplicateMatcher.isDuplicate(known, workout("fit:1", "FIT-Datei", 10_000L, null)))
        assertFalse(WorkoutDuplicateMatcher.isDuplicate(known, workout("fit:2", "FIT-Datei", 10_000L, 7_000.0)))
    }

    private fun workout(id: String, source: String, start: Long, distance: Double?) = WorkoutEntity(
        sourceId = id,
        sourceName = source,
        title = "Lauf",
        startedAtMillis = start,
        endedAtMillis = start + 1_800_000L,
        distanceMeters = distance,
        averageHeartRateBpm = null,
        maximumHeartRateBpm = null
    )
}
