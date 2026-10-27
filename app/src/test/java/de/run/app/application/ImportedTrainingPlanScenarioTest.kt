package de.run.app.application

import de.run.app.domain.ContinuousRunProgressionAnalyzer
import de.run.app.domain.PlannedWorkoutMatcher
import de.run.app.domain.TrainingPlanBuilder
import de.run.app.domain.WorkoutPlanCompletionAnalyzer
import de.run.app.domain.model.AvailabilityMode
import de.run.app.domain.model.PlannedRunAssessment
import de.run.app.domain.model.PlannedRunMatchType
import de.run.app.domain.model.RaceGoal
import de.run.app.domain.model.RunningSymptomFeedback
import de.run.app.domain.model.TrainingGoal
import de.run.app.domain.model.TrainingPlan
import de.run.app.domain.model.TrainingProfile
import de.run.app.domain.model.TrainingType
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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ImportedTrainingPlanScenarioTest {
    private val seedDate = LocalDate.of(2026, 10, 26)
    private val profile = TrainingProfile(
        longestComfortableRunMinutes = 60,
        optionalRun = false,
        strengthSessionsPerWeek = 0,
        availableRunDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SUNDAY),
        availabilityMode = AvailabilityMode.RECURRING
    )
    private val goal = TrainingGoal(
        id = "scenario-goal",
        raceDate = seedDate.plusWeeks(23).plusDays(6),
        raceDistanceMeters = 21_100,
        raceGoal = RaceGoal.RUN_THROUGH
    )

    @Test
    fun `new scheduled import is assessed and changes the next continuous run target`() = runTest {
        val schedule = TrainingPlanBuilder().build(profile, goal, seedDate)
        val plannedLongRun = schedule.weeks.first().scheduledRuns
            .first { it.run.trainingType == TrainingType.LONG_EASY }
        val runDate = requireNotNull(plannedLongRun.scheduledDate)
        val scheduleMatch = PlannedWorkoutMatcher().match(
            workout(runDate, plannedLongRun.run.totalMinutes),
            emptyList(),
            profile,
            goal,
            ZoneOffset.UTC
        )
        val imported = workout(runDate, requireNotNull(scheduleMatch.plannedDurationMinutes))
        val repository = FakeWorkoutRepository(imported)
        val evaluation = EvaluateImportedWorkouts(
            repository,
            FakeProfileRepository(profile),
            FakeGoalRepository(goal)
        )

        val evaluated = evaluation(listOf(imported), emptyList()).single()
        val nextPlan = TrainingPlanBuilder().build(profile, goal, runDate.plusDays(1), listOf(evaluated))

        assertEquals(PlannedRunMatchType.SCHEDULED, evaluated.planMatch?.type)
        assertEquals(PlannedRunAssessment.WELL_MATCHED, evaluated.planMatch?.assessment)
        assertEquals(ContinuousRunProgressionAnalyzer.LONG_RUN_TITLE, evaluated.planMatch?.plannedTitle)
        assertEquals(
            35,
            ContinuousRunProgressionAnalyzer().targetMinutes(
                listOf(evaluated),
                runDate.plusDays(1),
                45,
                ZoneOffset.UTC
            )
        )
        assertNotNull(repository.records.value.single().planMatch)
        assertEquals(35, continuousTarget(nextPlan))
    }

    @Test
    fun `extra import does not falsely complete a planned unit`() {
        val plan = TrainingPlanBuilder().build(profile, goal, seedDate)
        val extra = workout(seedDate, 35)
        val match = PlannedWorkoutMatcher().match(extra, emptyList(), profile, goal, ZoneOffset.UTC)
        val assessed = extra.copy(planMatch = match)
        val completedCount = WorkoutPlanCompletionAnalyzer().completedRuns(
            listOf(assessed),
            plan.weeks.first(),
            profile,
            goal,
            ZoneOffset.UTC
        )

        assertEquals(PlannedRunMatchType.EXTRA, match.type)
        assertEquals(0, completedCount)
    }

    private fun continuousTarget(plan: TrainingPlan): Int = plan.weeks.first().runs.last().steps
        .first { it.name.contains("am Stück") }
        .minutes

    private fun workout(date: LocalDate, durationMinutes: Int) = Workout(
        sourceId = "fit:$date",
        sourceName = "FIT",
        title = ContinuousRunProgressionAnalyzer.LONG_RUN_TITLE,
        startedAtMillis = date.atTime(9, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
        endedAtMillis = date.atTime(9, 0).toInstant(ZoneOffset.UTC)
            .plusSeconds(durationMinutes * 60L).toEpochMilli(),
        distanceMeters = 8_000.0,
        feedback = WorkoutFeedback(3, RunningSymptomFeedback.NO, date.toEpochDay())
    )

    private class FakeWorkoutRepository(initial: Workout) : WorkoutRepository {
        val records = MutableStateFlow(listOf(initial))
        override fun observeRecent(): Flow<List<Workout>> = records
        override suspend fun refresh(from: Instant, to: Instant, readInBackground: Boolean) =
            error("Not used in this test")
        override suspend fun saveFeedback(sourceId: String, feedback: WorkoutFeedback) = false
        override suspend fun savePlanMatch(sourceId: String, match: WorkoutPlanMatch): Boolean {
            records.value = records.value.map { workout ->
                if (workout.sourceId == sourceId) workout.copy(planMatch = match) else workout
            }
            return true
        }
    }

    private class FakeProfileRepository(private val profile: TrainingProfile) : TrainingProfileRepository {
        override fun observe(): Flow<TrainingProfile?> = MutableStateFlow(profile)
        override suspend fun save(profile: TrainingProfile) = Unit
    }

    private class FakeGoalRepository(private val goal: TrainingGoal) : TrainingGoalRepository {
        override fun observeHistory(): Flow<List<TrainingGoal>> = MutableStateFlow(listOf(goal))
        override suspend fun saveAsCurrent(goal: TrainingGoal) = Unit
        override suspend fun update(goal: TrainingGoal) = Unit
    }
}
