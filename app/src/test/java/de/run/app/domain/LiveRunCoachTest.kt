package de.run.app.domain

import de.run.app.domain.model.PaceTargetRange
import de.run.app.domain.model.PlannedRun
import de.run.app.domain.model.RunPrescription
import de.run.app.domain.model.RunStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveRunCoachTest {
    private val targets = LiveRunTargets(360, 390, 160)

    @Test
    fun ignoresSingleFastPaceSampleAndSpeaksAfterPersistenceWindow() {
        val coach = LiveRunCoach()
        assertNull(coach.nextCue(metrics(pace = 330, time = 0), targets, 0))
        assertEquals(LiveCoachCue.TOO_FAST, coach.nextCue(metrics(pace = 330, time = 15_000), targets, 15_000))
    }

    @Test
    fun prioritizesHighHeartRateAndSuppressesPausedOrStaleData() {
        val coach = LiveRunCoach()
        assertNull(coach.nextCue(metrics(pace = 330, heartRate = 170, time = 1_000), targets, 1_000))
        assertEquals(
            LiveCoachCue.HEART_RATE_HIGH,
            coach.nextCue(metrics(pace = 330, heartRate = 170, time = 16_000), targets, 16_000)
        )
        assertNull(coach.nextCue(metrics(pace = 330, time = 20_000, paused = true), targets, 20_000))
        assertNull(coach.nextCue(metrics(pace = 330, time = 0), targets, 20_000))
    }

    @Test
    fun appliesCooldownBetweenRepeatedPrompts() {
        val coach = LiveRunCoach()
        coach.nextCue(metrics(pace = 330, time = 0), targets, 0)
        assertEquals(LiveCoachCue.TOO_FAST, coach.nextCue(metrics(pace = 330, time = 15_000), targets, 15_000))
        coach.nextCue(metrics(pace = 330, time = 16_000), targets, 16_000)
        assertNull(coach.nextCue(metrics(pace = 330, time = 31_000), targets, 31_000))
    }

    @Test
    fun paceSmootherRejectsOneGpsSpike() {
        val smoother = LivePaceSmoother()
        repeat(4) { smoother.add(370) }
        assertEquals(370, smoother.add(180))
    }

    @Test
    fun appliesThePersonalTargetOnlyDuringThePlannedRunningSection() {
        val planned = PlannedRun(
            "Dauerlauf",
            false,
            listOf(
                RunStep("Zügig gehen", 5, 1, 2, "Bequem gehen"),
                RunStep("Locker laufen", 25, 2, 4, "Ganze Sätze sprechen können"),
                RunStep("Locker ausgehen", 5, 1, 2, "Bequem gehen")
            ),
            prescription = RunPrescription(paceRange = PaceTargetRange(390, 360))
        )
        val actualTargets = LiveRunTargets.from(planned)

        assertNull(actualTargets.atElapsedSeconds(0).minPaceSecondsPerKilometer)
        assertEquals(360, actualTargets.atElapsedSeconds(300).minPaceSecondsPerKilometer)
        assertEquals(390, actualTargets.atElapsedSeconds(300).maxPaceSecondsPerKilometer)
        assertNull(actualTargets.atElapsedSeconds(1_800).minPaceSecondsPerKilometer)
        assertNull(actualTargets.atElapsedSeconds(300).maxHeartRateBpm)
        assertEquals(1, actualTargets.sectionIndexAt(300))
        assertEquals(actualTargets.sections, LiveRunTargets.fromTransportValues(actualTargets.toTransportValues()).sections)
        assertNull(LiveRunCoach().nextCue(metrics(pace = 330, heartRate = 190, time = 0), LiveRunTargets(), 0))
    }

    @Test
    fun carriesTheStablePlannedTrainingIdAndDurationToGarmin() {
        val planned = PlannedRun(
            title = "Locker laufen",
            optional = false,
            steps = listOf(RunStep("Locker laufen", 35, 2, 4)),
            trainingId = "lap-run-2026-10-26-easy",
            prescription = RunPrescription(paceRange = PaceTargetRange(390, 360))
        )

        val target = LiveRunTargets.from(planned).trainingTarget

        assertEquals(GarminTrainingTarget("lap-run-2026-10-26-easy", 2_100), target)
        assertEquals(
            listOf("LAP_TARGET", 1, "lap-run-2026-10-26-easy", 2_100),
            target?.toMessage()
        )
        assertEquals(target, GarminTrainingTarget.decodeAcknowledgement(target?.acknowledgementMessage()))
    }

    private fun metrics(
        pace: Int,
        heartRate: Int? = 140,
        time: Long,
        paused: Boolean = false
    ) = LiveRunMetrics(
        activeSeconds = time / 1_000,
        distanceMeters = 100.0,
        paceSecondsPerKilometer = pace,
        heartRateBpm = heartRate,
        measuredAtMillis = time,
        source = LiveMetricSource.EMULATOR_TEST,
        isPaused = paused
    )
}
