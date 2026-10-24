package de.run.app.data.garmin

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GarminTargetActivitySelectorTest {
    private val target = LocalDateTime.of(2026, 10, 13, 18, 12)

    @Test
    fun selectsClosestActivityOnTargetDateWithinTolerance() {
        val farther = summary("farther", target.plusMinutes(12))
        val closest = summary("closest", target.minusMinutes(3))

        val selected = GarminTargetActivitySelector.select(listOf(farther, closest), target)

        assertEquals(closest, selected)
    }

    @Test
    fun rejectsActivityOutsideExactThirtyMinuteBoundary() {
        assertNull(GarminTargetActivitySelector.select(listOf(summary("late", target.plusSeconds(30 * 60 + 1))), target))
    }

    @Test
    fun acceptsActivityOnExactThirtyMinuteBoundary() {
        val expected = summary("boundary", target.plusMinutes(30))

        assertEquals(expected, GarminTargetActivitySelector.select(listOf(expected), target))
    }

    @Test
    fun doesNotSelectActivityFromAdjacentDate() {
        val adjacent = summary("previous-day", target.toLocalDate().atStartOfDay().minusSeconds(1))

        assertNull(GarminTargetActivitySelector.select(listOf(adjacent), target))
    }

    private fun summary(id: String, startedAt: LocalDateTime) = GarminActivitySummary(id, startedAt)
}
