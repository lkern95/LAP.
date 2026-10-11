package de.run.app.domain

import de.run.app.domain.model.PlanAdjustment
import de.run.app.domain.model.PlanStatus
import de.run.app.domain.model.RaceGoal
import de.run.app.domain.model.TrainingGoal
import de.run.app.domain.model.TrainingPlan
import de.run.app.domain.model.TrainingProfile
import de.run.app.domain.model.Workout
import de.run.app.domain.model.WorkoutFeedback
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TrainingDecisionSnapshotFactoryTest {
    private val factory = TrainingDecisionSnapshotFactory()
    private val today = LocalDate.of(2026, 10, 5)
    private val profile = TrainingProfile(45, false, 1)
    private val goal = TrainingGoal("goal-a", today.plusWeeks(20), 21_100, RaceGoal.RUN_THROUGH)
    private val plan = TrainingPlan(today.plusWeeks(20), PlanStatus.READY, emptyList(), PlanAdjustment.CONTINUE)

    @Test
    fun `input revision does not depend on source ordering`() {
        val first = workout("run-a", today.minusDays(2))
        val second = workout("run-b", today.minusDays(4))

        val forward = factory.create(profile, goal, listOf(first, second), plan, today)
        val reversed = factory.create(profile, goal, listOf(second, first), plan, today)

        assertEquals(forward.inputRevision, reversed.inputRevision)
        assertEquals(forward.targetRevision, reversed.targetRevision)
    }

    @Test
    fun `feedback and goal changes create distinct revisions`() {
        val run = workout("run-a", today.minusDays(2))
        val initial = factory.create(profile, goal, listOf(run), plan, today)
        val withFeedback = factory.create(
            profile,
            goal,
            listOf(run.copy(feedback = WorkoutFeedback(8, null, 10))),
            plan,
            today
        )
        val changedGoal = factory.create(profile, goal.copy(raceDate = goal.raceDate.plusDays(1)), listOf(run), plan, today)

        assertNotEquals(initial.inputRevision, withFeedback.inputRevision)
        assertNotEquals(initial.targetRevision, changedGoal.targetRevision)
        assertEquals(1, initial.ruleVersion)
    }

    private fun workout(id: String, date: LocalDate) = Workout(
        sourceId = id,
        sourceName = "Garmin Connect",
        title = "Lauf",
        startedAtMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        endedAtMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).plusSeconds(1_800).toEpochMilli(),
        distanceMeters = null
    )
}
