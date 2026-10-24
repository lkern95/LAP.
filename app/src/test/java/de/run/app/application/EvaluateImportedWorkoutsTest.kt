package de.run.app.application

import de.run.app.domain.model.AvailabilityMode
import de.run.app.domain.model.RaceGoal
import de.run.app.domain.model.TrainingGoal
import de.run.app.domain.model.TrainingProfile
import de.run.app.domain.model.Workout
import de.run.app.domain.model.WorkoutFeedback
import de.run.app.domain.model.WorkoutPlanMatch
import de.run.app.domain.repository.TrainingGoalRepository
import de.run.app.domain.repository.TrainingProfileRepository
import de.run.app.domain.repository.WorkoutRepository
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class EvaluateImportedWorkoutsTest {
    @Test
    fun repeatedEvaluationPreservesTheFirstSavedPlanMatch() = runTest {
        val date = LocalDate.of(2026, 10, 20)
        val workout = workout(date)
        val repository = FakeWorkoutRepository(workout)
        val useCase = EvaluateImportedWorkouts(
            repository,
            FakeProfileRepository(),
            FakeGoalRepository(TrainingGoal("hm", date.plusWeeks(22), 21_100, RaceGoal.TARGET_TIME, 150))
        )

        val first = useCase(listOf(workout), emptyList()).single()
        val repeated = useCase(listOf(workout), emptyList()).single()

        assertNotNull(first.planMatch)
        assertEquals(first.planMatch, repeated.planMatch)
        assertEquals(1, repository.savedMatchCount)
    }

    private fun workout(date: LocalDate) = Workout(
        sourceId = "fit:${date}",
        sourceName = "FIT",
        title = "Laufen",
        startedAtMillis = date.atTime(9, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
        endedAtMillis = date.atTime(9, 40).toInstant(ZoneOffset.UTC).toEpochMilli(),
        distanceMeters = 5_000.0
    )

    private class FakeWorkoutRepository(initial: Workout) : WorkoutRepository {
        private val records = MutableStateFlow(listOf(initial))
        var savedMatchCount = 0
            private set

        override fun observeRecent(): Flow<List<Workout>> = records
        override suspend fun refresh(from: Instant, to: Instant, readInBackground: Boolean) =
            error("Not used in this test")
        override suspend fun saveFeedback(sourceId: String, feedback: WorkoutFeedback) = false
        override suspend fun savePlanMatch(sourceId: String, match: WorkoutPlanMatch): Boolean {
            val current = records.value
            records.value = current.map { if (it.sourceId == sourceId) it.copy(planMatch = match) else it }
            savedMatchCount++
            return true
        }
    }

    private class FakeProfileRepository : TrainingProfileRepository {
        override fun observe(): Flow<TrainingProfile?> = MutableStateFlow(
            TrainingProfile(
                longestComfortableRunMinutes = 35,
                optionalRun = false,
                strengthSessionsPerWeek = 0,
                availableRunDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SUNDAY),
                longRunDay = DayOfWeek.SUNDAY,
                availabilityMode = AvailabilityMode.RECURRING
            )
        )
        override suspend fun save(profile: TrainingProfile) = Unit
    }

    private class FakeGoalRepository(private val goal: TrainingGoal) : TrainingGoalRepository {
        override fun observeHistory(): Flow<List<TrainingGoal>> = MutableStateFlow(listOf(goal))
        override suspend fun saveAsCurrent(goal: TrainingGoal) = Unit
        override suspend fun update(goal: TrainingGoal) = Unit
    }
}
