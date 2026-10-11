package de.run.app.presentation.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RaceDistanceLabelTest {
    @Test
    fun acceptsCustomCommaDecimalAndRecognizedRaceDistances() {
        assertEquals(42_200, parseRaceDistanceMeters("42,2"))
        assertEquals("Marathon", raceDistanceName(42_200))
        assertEquals("Halbmarathon", raceDistanceName(21_100))
        assertEquals("10 km Lauf", raceDistanceName(10_000))
    }

    @Test
    fun rejectsMissingOrOutOfRangeDistance() {
        assertNull(parseRaceDistanceMeters(""))
        assertNull(parseRaceDistanceMeters("0,5"))
        assertNull(parseRaceDistanceMeters("101"))
    }
}
