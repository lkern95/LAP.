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
        assertTrue(requireNotNull(result).explanation.contains("mindestens einen Lauf über 5 km"))
    }

    @Test fun runThroughWithoutTargetTimeUsesConsistencyAndLongRunHistory() {
        val workouts = listOf(
            run("recent-long-a", 12_000, 5_400, 3), run("recent-a", 6_000, 2_700, 7),
            run("recent-b", 7_000, 3_150, 11), run("recent-c", 8_000, 3_600, 15),
            run("recent-d", 6_000, 2_700, 19), run("recent-e", 6_000, 2_700, 23),
            run("previous-long", 10_000, 4_800, 33), run("previous-a", 6_000, 2_700, 38)
        )

        val result = requireNotNull(analyzer.analyze(goal(targetMinutes = null, runThrough = true), workouts, today, zone()))

        assertTrue(result.outlookSummary.contains("Auf Kurs"))
        assertEquals(6, result.recentRunCount)
        assertEquals(12.0, result.recentLongestRunKilometers, 0.01)
        assertEquals(10.0, result.previousLongestRunKilometers, 0.01)
        assertNull(result.estimatedMinutes)
        assertEquals(ForecastConfidence.TRAINING_ORIENTATION, result.confidence)
    }

    @Test fun runThroughWithFewCurrentRunsNamesTheMissingConsistency() {
        val result = requireNotNull(analyzer.analyze(
            goal(targetMinutes = null, runThrough = true),
            listOf(run("one", 5_000, 2_400, 2), run("two", 6_000, 2_900, 20)),
            today,
            zone()
        ))

        assertTrue(result.outlookSummary.contains("Regelmäßigkeit"))
        assertTrue(result.recommendation.contains("zwei Läufe pro Woche"))
    }

    @Test fun rpeSevenQualifiesAsPerformanceEvidenceAndProducesABroadTimeRange() {
        val result = requireNotNull(analyzer.analyze(
            goal(targetMinutes = null, runThrough = true),
            listOf(run("steady-10k", 10_000, 3_300, 4, effort = 7)),
            today,
            zone()
        ))

        assertNotNull(result.estimatedMinutes)
        assertEquals(ForecastConfidence.ROUGH, result.confidence)
        val range = requireNotNull(result.estimatedMinutes)
        assertTrue(range.last - range.first >= 30)
    }

    @Test fun rpeSixAloneDoesNotQualifyAsPerformanceEvidence() {
        val result = analyzer.analyze(
            goal(targetMinutes = null, runThrough = true),
            listOf(run("moderate-10k", 10_000, 3_300, 4, effort = 6)),
            today,
            zone()
        )

        assertNull(result?.estimatedMinutes)
        assertEquals(0, result?.evidenceRuns)
    }

    @Test fun twoAndAHalfKilometerActivityCannotProduceHalfMarathonTimeRange() {
        val result = analyzer.analyze(
            goal(targetMinutes = null, runThrough = true),
            listOf(run("short-easy", 2_500, 1_500, 1)), today, zone()
        )

        assertNull(result?.estimatedMinutes)
        assertEquals(0, result?.evidenceRuns)
    }

    @Test fun explicitlyMarkedRaceQualifiesWithoutRpeEntry() {
        val result = requireNotNull(analyzer.analyze(
            goal(targetMinutes = null, runThrough = true),
            listOf(run("race", 5_000, 1_500, 4).copy(title = "Wettkampf")), today, zone()
        ))

        assertNotNull(result.estimatedMinutes)
        assertEquals(1, result.evidenceRuns)
    }

    @Test fun shorterPerformanceDistanceProducesWiderRangeThanLongerOne() {
        val short = requireNotNull(analyzer.analyze(
            goal(targetMinutes = null, runThrough = true),
            listOf(run("short-race", 5_000, 1_500, 4, effort = 7)), today, zone()
        )).estimatedMinutes!!
        val long = requireNotNull(analyzer.analyze(
            goal(targetMinutes = null, runThrough = true),
            listOf(run("long-race", 10_000, 3_000, 4, effort = 7)), today, zone()
        )).estimatedMinutes!!

        assertTrue(short.last - short.first > long.last - long.first)
    }

    @Test fun olderPerformanceEvidenceProducesWiderRange() {
        val recent = requireNotNull(analyzer.analyze(
            goal(targetMinutes = null, runThrough = true),
            listOf(run("recent-race", 10_000, 3_000, 4, effort = 7)), today, zone()
        )).estimatedMinutes!!
        val older = requireNotNull(analyzer.analyze(
            goal(targetMinutes = null, runThrough = true),
            listOf(run("older-race", 10_000, 3_000, 70, effort = 7)), today, zone()
        )).estimatedMinutes!!

        assertTrue(older.last - older.first > recent.last - recent.first)
    }

    @Test fun disagreementBetweenPerformancesWidensRange() {
        val single = requireNotNull(analyzer.analyze(
            goal(targetMinutes = null, runThrough = true),
            listOf(run("single", 10_000, 3_000, 4, effort = 7)), today, zone()
        )).estimatedMinutes!!
        val divergent = requireNotNull(analyzer.analyze(
            goal(targetMinutes = null, runThrough = true),
            listOf(run("fast", 10_000, 3_000, 4, effort = 7), run("slow", 10_000, 3_600, 10, effort = 7)), today, zone()
        )).estimatedMinutes!!

        assertTrue(divergent.last - divergent.first > single.last - single.first)
    }

    @Test fun threeLongerOrdinaryTrainingRunsProduceAnEnduranceAssessmentWithoutTimePrediction() {
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

        assertNull(result.estimatedMinutes)
        assertEquals(ForecastConfidence.TRAINING_ORIENTATION, result.confidence)
        assertEquals(3, result.evidenceRuns)
        assertTrue(result.explanation.contains("keine Halbmarathon-Zeit"))
        assertTrue(result.dataQuality.contains("keine Wettkampfprognose"))
    }

    @Test fun threeLongRunsAcrossTwelveWeeksGiveOnlyTrainingOrientation() {
        val result = requireNotNull(analyzer.analyze(
            goal(targetMinutes = null, runThrough = true),
            listOf(
                run("older-long-a", 9_000, 4_200, 70),
                run("older-long-b", 10_000, 4_700, 50),
                run("recent-long", 11_000, 5_100, 20)
            ), today, zone()
        ))

        assertEquals(ForecastConfidence.TRAINING_ORIENTATION, result.confidence)
        assertEquals(3, result.evidenceRuns)
        assertTrue(result.dataQuality.contains("keine Wettkampfprognose"))
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
            run("hard-1", 10_000, 3_000, 7, effort = 9).copy(title = "Wettkampf"),
            run("hard-2", 10_000, 2_980, 18, effort = 8).copy(title = "Wettkampf")
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
