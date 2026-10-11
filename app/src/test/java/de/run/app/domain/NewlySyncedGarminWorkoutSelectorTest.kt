package de.run.app.domain

import de.run.app.domain.model.Workout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NewlySyncedGarminWorkoutSelectorTest {
    private val selector = NewlySyncedGarminWorkoutSelector()

    @Test
    fun selectsMostRecentNewGarminRun() {
        val old = workout("old", endedAt = 1_000L)
        val newest = workout("newest", endedAt = 3_000L)
        val anotherNew = workout("new", endedAt = 2_000L)

        assertEquals(newest, selector.select(listOf(old), listOf(old, anotherNew, newest)))
    }

    @Test
    fun ignoresPreviouslySavedRunsAndOtherSources() {
        val saved = workout("saved", endedAt = 2_000L)
        val otherSource = workout("other", "Andere Lauf-App", endedAt = 3_000L)
        val simulated = workout("demo:run:1", endedAt = 4_000L)

        assertNull(selector.select(listOf(saved), listOf(saved, otherSource, simulated)))
    }

    @Test
    fun selectsRunsDeliveredByHealthSyncOnAndroidTen() {
        val healthSyncRun = workout("health-sync:run", "Health Sync", endedAt = 5_000L)

        assertEquals(healthSyncRun, selector.select(emptyList(), listOf(healthSyncRun)))
    }

    @Test
    fun ignoresRunsWithoutUsableDuration() {
        val empty = workout("empty", endedAt = 1_000L, startedAt = 1_000L)

        assertNull(selector.select(emptyList(), listOf(empty)))
    }

    private fun workout(
        id: String,
        source: String = "Garmin Connect",
        endedAt: Long,
        startedAt: Long = endedAt - 3_600_000L
    ) = Workout(id, source, "Lauf", startedAt, endedAt, 5_000.0, 140)
}
