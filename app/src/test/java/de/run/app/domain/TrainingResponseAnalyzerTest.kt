package de.run.app.domain

import de.run.app.domain.model.RunningSymptomFeedback
import de.run.app.domain.model.Workout
import de.run.app.domain.model.WorkoutFeedback
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class TrainingResponseAnalyzerTest {
    private val analyzer = TrainingResponseAnalyzer()
    private val today = LocalDate.of(2026, 10, 5)

    @Test
    fun `no feedback and high heart rate do not invent perceived effort`() {
        val run = workout(today.minusDays(1), averageHeartRate = 190)

        assertEquals(TrainingResponse.NO_CHANGE, analyzer.analyze(listOf(run), today, ZoneOffset.UTC))
    }

    @Test
    fun `one demanding report does not trigger a repeated response`() {
        val run = workout(today.minusDays(2)).withFeedback(effort = 9)

        assertEquals(TrainingResponse.NO_CHANGE, analyzer.analyze(listOf(run), today, ZoneOffset.UTC))
    }

    @Test
    fun `two similarly long demanding runs trigger a hold`() {
        val runs = listOf(
            workout(today.minusDays(2), durationMinutes = 40).withFeedback(effort = 7),
            workout(today.minusDays(6), durationMinutes = 45).withFeedback(effort = 8)
        )

        assertEquals(TrainingResponse.REPEAT_AFTER_HIGH_EFFORT, analyzer.analyze(runs, today, ZoneOffset.UTC))
    }

    @Test
    fun `different length demanding runs are not treated as comparable`() {
        val runs = listOf(
            workout(today.minusDays(2), durationMinutes = 25).withFeedback(effort = 8),
            workout(today.minusDays(6), durationMinutes = 50).withFeedback(effort = 8)
        )

        assertEquals(TrainingResponse.NO_CHANGE, analyzer.analyze(runs, today, ZoneOffset.UTC))
    }

    @Test
    fun `feedback older than four weeks no longer drives effort hold`() {
        val runs = listOf(
            workout(today.minusDays(32), durationMinutes = 40).withFeedback(effort = 8),
            workout(today.minusDays(35), durationMinutes = 42).withFeedback(effort = 8)
        )

        assertEquals(TrainingResponse.NO_CHANGE, analyzer.analyze(runs, today, ZoneOffset.UTC))
    }

    @Test
    fun `reported symptoms remain active until a later explicit no response`() {
        val reported = workout(today.minusDays(50)).withFeedback(symptoms = RunningSymptomFeedback.YES, answeredAt = 10)
        val skipped = workout(today.minusDays(2)).copy(
            feedback = WorkoutFeedback(null, null, 15)
        )
        val laterNo = workout(today.minusDays(1)).withFeedback(symptoms = RunningSymptomFeedback.NO, answeredAt = 20)

        assertEquals(TrainingResponse.REVIEW_REPORTED_SYMPTOMS, analyzer.analyze(listOf(reported), today, ZoneOffset.UTC))
        assertEquals(TrainingResponse.REVIEW_REPORTED_SYMPTOMS, analyzer.analyze(listOf(reported, skipped), today, ZoneOffset.UTC))
        assertEquals(TrainingResponse.NO_CHANGE, analyzer.analyze(listOf(reported, laterNo), today, ZoneOffset.UTC))
    }

    @Test
    fun `duplicate imported identity counts only its newest version`() {
        val first = workout(today.minusDays(2)).copy(sourceId = "same").withFeedback(effort = 9)
        val update = workout(today.minusDays(2), durationMinutes = 41).copy(sourceId = "same")
            .withFeedback(effort = 2, answeredAt = 20)
        val other = workout(today.minusDays(5)).withFeedback(effort = 8)

        assertEquals(TrainingResponse.NO_CHANGE, analyzer.analyze(listOf(first, update, other), today, ZoneOffset.UTC))
    }

    private fun workout(
        date: LocalDate,
        durationMinutes: Int = 40,
        averageHeartRate: Int? = null
    ) = Workout(
        sourceId = "garmin:$date:$durationMinutes",
        sourceName = "Garmin Connect",
        title = "Lauf",
        startedAtMillis = date.atTime(10, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
        endedAtMillis = date.atTime(10, 0).toInstant(ZoneOffset.UTC).plusSeconds(durationMinutes * 60L).toEpochMilli(),
        distanceMeters = 5_000.0,
        averageHeartRateBpm = averageHeartRate
    )

    private fun Workout.withFeedback(
        effort: Int? = null,
        symptoms: RunningSymptomFeedback = RunningSymptomFeedback.NO,
        answeredAt: Long = endedAtMillis
    ) = copy(feedback = WorkoutFeedback(effort, symptoms, answeredAt))
}
