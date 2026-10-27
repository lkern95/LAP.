package de.run.app.application

import de.run.app.domain.ManualRunDurationLimit
import de.run.app.domain.RunDistanceSpikeAnalyzer
import de.run.app.domain.model.DistanceSpikeLevel
import de.run.app.domain.model.PaceTargetRange
import de.run.app.domain.model.PlannedRun
import de.run.app.domain.model.RunPrescription
import de.run.app.domain.model.RunStep
import de.run.app.domain.model.TrainingType
import de.run.app.domain.model.Workout
import java.time.LocalDate
import java.time.ZoneOffset

data class LapEvaluationScenarioResult(
    val name: String,
    val expected: String,
    val actual: String,
    val passed: Boolean
)

/** Exercises representative boundary and missing-data cases with production domain rules. */
class LapEvaluationScenarioRunner(
    private val distanceAnalyzer: RunDistanceSpikeAnalyzer = RunDistanceSpikeAnalyzer(),
    private val durationLimit: ManualRunDurationLimit = ManualRunDurationLimit()
) {
    fun run(today: LocalDate): List<LapEvaluationScenarioResult> {
        val baseline = workout("baseline", today.minusDays(2), 10_000.0, 60)
        val distanceCases = listOf(
            DistanceCase("10 % Distanzanstieg: keine Warnung", 11_000.0, DistanceSpikeLevel.NO_WARNING),
            DistanceCase("über 10 %: Hinweis", 11_100.0, DistanceSpikeLevel.NOTICE),
            DistanceCase("30 %: noch Hinweis", 13_000.0, DistanceSpikeLevel.NOTICE),
            DistanceCase("über 30 %: deutliche Warnung", 13_100.0, DistanceSpikeLevel.WARNING),
            DistanceCase("100 %: deutliche Warnung", 20_000.0, DistanceSpikeLevel.WARNING),
            DistanceCase("über 100 %: starke Warnung", 20_100.0, DistanceSpikeLevel.STRONG_WARNING)
        )
        val measuredResults = distanceCases.map { case ->
            val actual = distanceAnalyzer.assessCompleted(
                workout("target", today, case.distanceMeters, 60),
                listOf(baseline, workout("target", today, case.distanceMeters, 60)),
                ZoneOffset.UTC
            ).level.name
            result(case.name, case.expected.name, actual)
        }
        val missingBaseline = distanceAnalyzer.assessCompleted(
            workout("target", today, 10_000.0, 60), emptyList(), ZoneOffset.UTC
        ).level.name
        val missingDistance = distanceAnalyzer.assessCompleted(
            workout("target", today, null, 60), listOf(baseline), ZoneOffset.UTC
        ).level.name
        val run = PlannedRun("Testlauf", false, listOf(RunStep("Locker laufen", 40)))
        val noHistoryLimit = durationLimit.maximumMinutes(run, emptyList(), today, 90, ZoneOffset.UTC).maximumMinutes
        val actualDuration = durationLimit.maximumMinutes(
            run, listOf(baseline), today, 90, ZoneOffset.UTC
        ).maximumMinutes
        val recoveryLimit = durationLimit.maximumMinutes(
            run.copy(trainingType = TrainingType.RECOVERY), listOf(baseline), today, 90, ZoneOffset.UTC
        ).maximumMinutes
        return measuredResults + listOf(
            result("fehlende 30-Tage-Basis", DistanceSpikeLevel.NOT_ASSESSABLE.name, missingBaseline),
            result("fehlende gemessene Distanz", DistanceSpikeLevel.NOT_ASSESSABLE.name, missingDistance),
            result("keine Historie: keine manuelle Verlängerung", "40", noHistoryLimit.toString()),
            result("vorhandene Historie: bestehender 10-Minuten-Aufbauschritt", "70", actualDuration.toString()),
            result("Erholungslauf: nicht manuell verlängern", "40", recoveryLimit.toString())
        )
    }

    private fun result(name: String, expected: String, actual: String) =
        LapEvaluationScenarioResult(name, expected, actual, expected == actual)

    private fun workout(id: String, date: LocalDate, distance: Double?, minutes: Int): Workout {
        val start = date.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        return Workout(id, "LAP-Prüffall", "Lauf", start, start + minutes * 60_000L, distance)
    }

    private data class DistanceCase(
        val name: String,
        val distanceMeters: Double,
        val expected: DistanceSpikeLevel
    )
}
