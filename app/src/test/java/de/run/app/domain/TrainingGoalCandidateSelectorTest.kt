package de.run.app.domain

import de.run.app.domain.model.RaceGoal
import de.run.app.domain.model.TrainingGoal
import de.run.app.domain.model.TrainingGoalStatus
import de.run.app.domain.model.Workout
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrainingGoalCandidateSelectorTest {
    private val selector = TrainingGoalCandidateSelector()
    private val raceDate = LocalDate.of(2026, 10, 9)
    private val goal = TrainingGoal("race", raceDate, 21_100, RaceGoal.RUN_THROUGH)

    @Test
    fun `same day run at exactly ninety percent is a candidate`() {
        val run = workout(raceDate, 18_990.0)

        assertEquals(run, selector.select(goal, listOf(run)))
    }

    @Test
    fun `shorter run is not a candidate`() {
        assertNull(selector.select(goal, listOf(workout(raceDate, 18_989.0))))
    }

    @Test
    fun `run on adjacent local date is not a candidate`() {
        assertNull(selector.select(goal, listOf(workout(raceDate.plusDays(1), 21_100.0))))
    }

    @Test
    fun `completed goal does not prompt again`() {
        val completed = goal.copy(status = TrainingGoalStatus.COMPLETED)

        assertNull(selector.select(completed, listOf(workout(raceDate, 21_100.0))))
    }

    private fun workout(date: LocalDate, distance: Double): Workout {
        val start = date.atTime(8, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return Workout("run-$date-$distance", "Garmin Connect", "Lauf", start, start + 60_000, distance)
    }
}
