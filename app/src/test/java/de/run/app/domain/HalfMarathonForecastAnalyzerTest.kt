package de.run.app.domain

import de.run.app.domain.model.RunningSymptomFeedback
import de.run.app.domain.model.TrainingGoal
import de.run.app.domain.model.TrainingGoalStatus
import de.run.app.domain.model.RaceGoal
import de.run.app.domain.model.Workout
import de.run.app.domain.model.WorkoutFeedback
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HalfMarathonForecastAnalyzerTest {
    private val analyzer = HalfMarathonForecastAnalyzer()
    private val today = LocalDate.of(2026, 10, 17)

    @Test fun ordinaryEasyRunsDoNotBecomeRacePerformanceForecast() {
        val result = analyzer.analyze(goal(), (1..3).map { run("easy-$it", 6_000, 2_400, daysAgo = it * 7) }, today, zone())
        assertNull(result?.estimatedMinutes)
        assertEquals(GoalOutlook.NOT_ENOUGH_DATA, result?.targetOutlook)
        assertTrue(requireNotNull(result).explanation.contains("Lockere Trainingsläufe allein reichen dafür"))
    }

    @Test fun runThroughWithoutTargetTimeUsesConsistencyAndLongRunHistory() {
        val workouts = listOf(
            run("recent-long-a", 12_000, 5_400, 3), run("recent-a", 6_000, 2_700, 7),
            run("recent-b", 7_000, 3_150, 11), run("recent-c", 8_000, 3_600, 15),
            run("recent-d", 6_000, 2_700, 19), run("recent-e", 6_000, 2_700, 23),
            run("previous-long", 10_000, 4_800, 33), run("previous-a", 6_000, 2_700, 38)
        )

        val result = requireNotNull(analyzer.analyze(goal(targetMinutes = null, runThrough = true), workouts, today, zone()))

        assertTrue(result.outlookSummary.contains("auf Kurs"))
        assertEquals(6, result.recentRunCount)
        assertEquals(12.0, result.recentLongestRunKilometers, 0.01)
        assertEquals(10.0, result.previousLongestRunKilometers, 0.01)
        assertNull(result.estimatedMinutes)
    }

    @Test fun runThroughWithFewCurrentRunsNamesTheMissingConsistency() {
        val result = requireNotNull(analyzer.analyze(
            goal(targetMinutes = null, runThrough = true),
            listOf(run("one", 5_000, 2_400, 2), run("two", 6_000, 2_900, 20)),
            today,
            zone()
        ))

        assertTrue(result.outlookSummary.contains("regelmäßigkeit"))
        assertTrue(result.recommendation.contains("zwei Läufe pro Woche"))
    }

    @Test fun oneModeratelyHardRecentRunProducesOnlyABroadOrientationWithoutTargetTime() {
        val result = requireNotNull(analyzer.analyze(
            goal(targetMinutes = null, runThrough = true),
            listOf(run("steady-10k", 10_000, 3_300, 4, effort = 6)),
            today,
            zone()
        ))

        assertNotNull(result.estimatedMinutes)
        assertEquals(ForecastConfidence.ROUGH, result.confidence)
        val range = requireNotNull(result.estimatedMinutes)
        assertTrue(range.last - range.first >= 30)
    }

    @Test fun threeLongerOrdinaryTrainingRunsProduceOnlyABroadTrainingOrientation() {
        val result = requireNotNull(analyzer.analyze(
            goal(targetMinutes = null, runThrough = true),
            listOf(
                run("long-a", 10_000, 4_500, 4),
                run("long-b", 9_000, 4_050, 12),
                run("long-c", 11_000, 4_950, 20)
            ),
            today,
            zone()
        ))

        assertNotNull(result.estimatedMinutes)
        assertEquals(ForecastConfidence.TRAINING_ORIENTATION, result.confidence)
        assertEquals(3, result.evidenceRuns)
        assertTrue(requireNotNull(result.estimatedMinutes).last - result.estimatedMinutes.first >= 100)
        assertTrue(result.explanation.contains("Trainingspace ist keine Wettkampfpace"))
    }

    @Test fun singleLongEasyRunStillDoesNotProduceATimeForecast() {
        val result = analyzer.analyze(
            goal(targetMinutes = null, runThrough = true),
            listOf(run("single-long-easy", 12_000, 6_000, 4)),
            today,
            zone()
        )

        assertNull(result?.estimatedMinutes)
    }

    @Test fun repeatedExplicitHardEffortsProduceBroadEstimateAndSeparateGoalAssessment() {
        val workouts = listOf(
            run("hard-1", 10_000, 3_000, 7, effort = 9),
            run("hard-2", 10_000, 2_980, 18, effort = 8)
        ) + listOf(
            run("long", 15_000, 5_400, 12),
            run("easy-a", 6_000, 2_100, 3),
            run("easy-b", 6_000, 2_100, 9),
            run("easy-c", 6_000, 2_100, 17),
            run("easy-d", 6_000, 2_100, 25),
            run("easy-e", 6_000, 2_100, 1),
            run("easy-f", 6_000, 2_100, 21)
        )
        val result = analyzer.analyze(goal(targetMinutes = 180), workouts, today, zone())
        assertNotNull(result?.estimatedMinutes)
        assertEquals(2, result?.evidenceRuns)
        assertEquals(GoalOutlook.LIKELY, result?.targetOutlook)
        assertTrue(requireNotNull(result?.estimatedMinutes).first < result.estimatedMinutes.last)
        assertEquals(ForecastConfidence.SUPPORTED, result?.confidence)
    }

    @Test fun effortWithReportedSymptomsDoesNotQualifyAsPerformanceEvidence() {
        val workouts = listOf(
            run("hard-a", 10_000, 3_000, 7, 9, RunningSymptomFeedback.NO),
            run("hard-b", 10_000, 3_000, 14, 9, RunningSymptomFeedback.YES)
        )
        val result = analyzer.analyze(goal(), workouts, today, zone())
        assertEquals(ForecastConfidence.ROUGH, result?.confidence)
        assertNotNull(result?.estimatedMinutes)
        assertEquals(1, result?.evidenceRuns)
    }

    @Test fun missingTargetTimeIsReportedSeparatelyFromMissingPerformanceEvidence() {
        val result = analyzer.analyze(goal(targetMinutes = null), (1..3).map { run("easy-$it", 6_000, 2_400, it * 7) }, today, zone())
        assertEquals(GoalOutlook.NOT_ENOUGH_DATA, result?.targetOutlook)
        assertNull(result?.estimatedMinutes)
    }

    @Test fun lowFrequencyOrShortLongRunPreventsPositiveTargetAssessment() {
        val workouts = listOf(
            run("hard-a", 10_000, 3_000, 7, 9), run("hard-b", 10_000, 3_000, 14, 9),
            run("one", 6_000, 2_100, 3)
        )
        val result = analyzer.analyze(goal(targetMinutes = 180), workouts, today, zone())
        assertEquals(GoalOutlook.UNLIKELY, result?.targetOutlook)
        assertTrue(requireNotNull(result).recommendation.contains("zwei Läufe pro Woche"))
    }

    @Test fun oldEffortsAndNonRunningRecordsAreNotUsed() {
        val stale = run("old", 10_000, 3_000, 90, 9)
        val recent = run("recent", 10_000, 3_000, 14, 9)
        val result = analyzer.analyze(goal(), listOf(stale, recent), today, zone())
        assertEquals(ForecastConfidence.ROUGH, result?.confidence)
        assertNotNull(result?.estimatedMinutes)
        assertEquals(1, result?.evidenceRuns)
        assertNull(analyzer.analyze(goal(distance = 10_000), listOf(recent), today, zone()))
        assertNull(analyzer.analyze(goal().copy(status = TrainingGoalStatus.COMPLETED), listOf(recent), today, zone()))
    }

    private fun goal(distance: Int = 21_097, targetMinutes: Int? = 130, runThrough: Boolean = false) = TrainingGoal(
        id = "goal", raceDate = today.plusWeeks(12), raceDistanceMeters = distance,
        raceGoal = if (runThrough) RaceGoal.RUN_THROUGH else if (targetMinutes == null) RaceGoal.ARRIVE else RaceGoal.TARGET_TIME,
        targetTimeMinutes = targetMinutes
    )

    private fun run(
        id: String, distance: Int, duration: Int, daysAgo: Int = 1,
        effort: Int? = null, symptoms: RunningSymptomFeedback? = if (effort == null) null else RunningSymptomFeedback.NO
    ): Workout {
        val end = today.minusDays(daysAgo.toLong()).atTime(12, 0).atZone(zone()).toInstant().toEpochMilli()
        val feedback = effort?.let { WorkoutFeedback(it, symptoms, end) }
        return Workout(id, "Test", "Lauf", end - duration * 1_000L, end, distance.toDouble(), feedback = feedback)
    }

    private fun zone() = ZoneId.of("UTC")
}
