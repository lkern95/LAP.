package de.run.app.application

import de.run.app.domain.model.HealthMetric
import de.run.app.domain.model.HealthMetricAvailability
import de.run.app.domain.model.ReadinessAssessment
import de.run.app.domain.model.ReadinessGuidance
import de.run.app.domain.model.ReadinessGuidanceKind
import de.run.app.domain.model.ReadinessHealthSnapshot
import de.run.app.domain.model.TrainingHistoryAccess
import de.run.app.domain.model.TrainingHistoryCoverage
import de.run.app.domain.TrainingPlanBuilder
import de.run.app.domain.model.RaceGoal
import de.run.app.domain.model.TrainingGoal
import de.run.app.domain.model.TrainingProfile
import java.time.Instant
import java.time.LocalDate
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LapEvaluationReportTest {
    @Test
    fun `plan report distinguishes estimated distance from missing distance and identifies race day`() {
        val today = LocalDate.of(2026, 10, 5)
        val goal = TrainingGoal(
            id = "report-goal",
            raceDate = today.plusDays(8),
            raceDistanceMeters = 21_100,
            raceGoal = RaceGoal.RUN_THROUGH
        )
        val profile = TrainingProfile(45, false, 1)
        val plan = TrainingPlanBuilder().build(profile, goal, today)
        val report = LapEvaluationReportBuilder().build(
            LapEvaluationReportInput(
                generatedAt = Instant.parse("2026-10-05T12:00:00Z"),
                appVersion = "test",
                isDebugBuild = false,
                isDemoData = false,
                profile = profile,
                goal = goal,
                plan = plan,
                progress = null,
                nextRun = null,
                forecast = null,
                feedback = null,
                workouts = emptyList(),
                health = null,
                readiness = ReadinessAssessment(null),
                readinessFacts = null,
                readinessGuidance = ReadinessGuidance(ReadinessGuidanceKind.NO_RUNS)
            )
        )

        assertTrue(report.contains("geplante Distanz ist nicht gemessen"))
        assertFalse(report.contains("absolvierte Distanz ist gemessen"))
        assertTrue(report.contains("Wettkampf: 21,1 km"))
        assertTrue(report.contains("erkannter Lauf am Wettkampftag wartet auf Bestätigung: nein"))
    }

    @Test
    fun `archive creates one markdown file per report section only when requested`() {
        val archive = LapEvaluationArchiveBuilder().build(
            "# LAP-Auswertungsprotokoll\n\n## Trainingsplan\nPlantext\n\n## Erholung\nMesswerte\n"
        )
        val entries = ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            buildMap {
                while (true) {
                    val entry = zip.nextEntry ?: break
                    put(entry.name, zip.readBytes().toString(Charsets.UTF_8))
                }
            }
        }

        assertEquals(3, entries.size)
        assertTrue(entries.keys.contains("LAP-Auswertung/00_Uebersicht.md"))
        assertTrue(entries.getValue("LAP-Auswertung/00_Uebersicht.md").contains("LAP-Auswertungsprotokoll"))
        assertTrue(entries.keys.any { it.endsWith("trainingsplan.md") })
        assertTrue(entries.keys.any { it.endsWith("erholung.md") })
        assertTrue(entries.values.any { it.contains("Plantext") })
        assertTrue(entries.values.any { it.contains("Messwerte") })
    }

    @Test
    fun `representative debug cases pass with production distance and duration rules`() {
        val results = LapEvaluationScenarioRunner().run(LocalDate.of(2026, 10, 25))

        assertEquals(11, results.size)
        assertTrue(results.all { it.passed })
    }

    @Test
    fun `report marks unavailable health values and separates synthetic checks`() {
        val unavailable = HealthMetric<Int>(null, HealthMetricAvailability.NO_DATA)
        val report = LapEvaluationReportBuilder().build(
            LapEvaluationReportInput(
                generatedAt = Instant.parse("2026-10-25T12:00:00Z"),
                appVersion = "test",
                isDebugBuild = true,
                isDemoData = false,
                profile = null,
                goal = null,
                plan = null,
                progress = null,
                nextRun = null,
                forecast = null,
                feedback = null,
                workouts = emptyList(),
                health = ReadinessHealthSnapshot(unavailable, unavailable),
                readiness = ReadinessAssessment(null),
                readinessFacts = null,
                readinessGuidance = ReadinessGuidance(ReadinessGuidanceKind.NO_RUNS),
                trainingHistoryCoverage = TrainingHistoryCoverage(
                    access = TrainingHistoryAccess.PERMISSION_NEEDED
                )
            )
        )

        assertTrue(report.contains("Erholungsschätzung: Noch nicht bewertbar"))
        assertTrue(report.contains("HRV/HFV letzte Nacht: nicht verfügbar"))
        assertTrue(report.contains("## Debug-Prüffälle"))
        assertTrue(report.contains("Zugriff auf Trainingshistorie: Freigabe erforderlich"))
        assertTrue(report.contains("Einordnung der Planbasis: Zugriff/Freigabe fehlt"))
        assertTrue(report.contains("BESTANDEN: über 100 %"))
        listOf(
            "## Ziele und Zielhistorie",
            "## Fortschrittsansicht und Diagrammdaten",
            "## Wochenvergleich und Zugriffsstatus",
            "## FIT-Import und Datenzugriff",
            "## Funktionsabdeckung der LAP-Auswertung",
            "TrainingAvailabilityPlanner",
            "TrainingProgressDashboardAnalyzer",
            "GarminLivePacketDecoder",
            "StrengthProgressAnalyzer",
            "LiveRunCoach",
            "AppBackupRepository"
        ).forEach { assertTrue("Fehlender LAP-Bereich: $it", report.contains(it)) }
        val exportedFiles = ZipInputStream(
            ByteArrayInputStream(LapEvaluationArchiveBuilder().build(report))
        ).use { zip ->
            buildSet {
                while (true) add(zip.nextEntry?.name ?: break)
            }
        }
        listOf(
            "ziele_und_zielhistorie",
            "fortschrittsansicht_und_diagrammdaten",
            "wochenvergleich_und_zugriffsstatus",
            "fit_import_und_datenzugriff",
            "live_laufbegleitung_und_audio",
            "funktionsabdeckung_der_lap_auswertung"
        ).forEach { expected ->
            assertTrue("ZIP-Datei für $expected fehlt", exportedFiles.any { it.endsWith("$expected.md") })
        }
        assertFalse(report.contains("NaN"))
    }
}
