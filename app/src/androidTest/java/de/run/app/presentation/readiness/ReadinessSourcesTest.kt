package de.run.app.presentation.readiness

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.run.app.domain.TrainingWeekStats
import de.run.app.domain.ReadinessAssessmentAnalyzer
import de.run.app.domain.WeeklyTrainingComparison
import de.run.app.domain.model.AppTheme
import de.run.app.domain.model.HealthMetric
import de.run.app.domain.model.HealthMetricAvailability
import de.run.app.domain.model.ReadinessFacts
import de.run.app.domain.model.ReadinessAssessment
import de.run.app.domain.model.ReadinessHealthSnapshot
import de.run.app.R
import de.run.app.presentation.components.AppPopup
import de.run.app.presentation.theme.RunTheme
import de.run.app.data.demo.DebugDemoDataSource
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals

class ReadinessSourcesTest {
    @get:Rule val compose = createComposeRule()

    @Test fun eachRecoveryMetricUsesItsOwnIcon() {
        val icons = listOf(
            readinessIconResource("HRV / HFV"),
            readinessIconResource("Ruhepuls"),
            readinessIconResource("Schlaf & Verlauf"),
            readinessIconResource("Trainingsbelastung"),
            readinessIconResource("Erholungszeit"),
            readinessIconResource("Stress & Alltagsaktivität")
        )

        assertTrue(icons.toSet() == setOf(
            R.drawable.readiness_hrv,
            R.drawable.readiness_resting_heart_rate,
            R.drawable.readiness_sleep,
            R.drawable.readiness_training_load,
            R.drawable.readiness_recovery,
            R.drawable.readiness_stress
        ))
    }

    @Test fun chartsConnectStoredMeasurementsAcrossMissingDaysWithoutAddingReadings() {
        val segments = continuousPlotSegments(listOf(48, null, null, 47, null, 50))

        assertEquals(2, segments.size)
        assertEquals(ReadinessPlotPoint(0, 48) to ReadinessPlotPoint(3, 47), segments[0])
        assertEquals(ReadinessPlotPoint(3, 47) to ReadinessPlotPoint(5, 50), segments[1])
    }

    @Test fun ringUsesSixDistinctSourcesAndTheCardShowsTheirActualAvailability() {
        val state = populatedState()
        compose.setContent {
            RunTheme(AppTheme.OLIVE) {
                Column {
                    ReadinessMeter(state)
                    ReadinessFactsCard(state)
                }
            }
        }

        compose.onNodeWithText("von 100").assertIsDisplayed()
        compose.onNodeWithText("Günstige Erholungssignale").assertIsDisplayed()
        compose.onNodeWithText("Datenbasis: 4 von 6 Bereichen verfügbar").assertIsDisplayed()
        compose.onNodeWithContentDescription("Geschätzte Erholung: 92 von 100").assertIsDisplayed()
        compose.onAllNodesWithText("Teilwerte (je 0–100): Schlaf 100 · Ruhepuls 100 · Laufbelastung 73").assertCountEquals(0)
        compose.onNodeWithText("HRV / HFV").assertIsDisplayed()
        compose.onNodeWithText("Ruhepuls").assertIsDisplayed()
        compose.onNodeWithText("Schlaf & Verlauf").assertIsDisplayed()
        compose.onNodeWithText("Trainingsbelastung").assertIsDisplayed()
        compose.onNodeWithText("Erholungszeit").assertIsDisplayed()
        compose.onNodeWithText("Stress & Alltagsaktivität").assertIsDisplayed()
        compose.onNodeWithText("7 Std. 0 Min. · persönlicher Vergleich: 7 Std. 0 Min.").assertIsDisplayed()
        compose.onNodeWithText("55 bpm · im persönlichen Normalbereich").assertIsDisplayed()
        compose.onNodeWithText("Letzte 7 Tage: 2 Läufe · 80 Min. · 10,0 km").assertIsDisplayed()
        compose.onNodeWithText("Letzter Lauf vor 1 Std. · 40 Min. Laufbelastung").assertIsDisplayed()
    }

    @Test fun readinessFactRowsGrowToShowWrappedValuesAndSources() {
        val source = "Garmin Connect – nächtliche Gesundheitsmessung"
        val history = (1L..28L).map {
            de.run.app.domain.model.DailyHealthValue(LocalDate.now().minusDays(it), 20)
        }
        val health = sampleHealth(467, 56).copy(
            hrvLastNightAverageMs = HealthMetric(48, HealthMetricAvailability.AVAILABLE, source),
            hrvStatus = HealthMetric("BALANCED", HealthMetricAvailability.AVAILABLE, source),
            restingHeartRateBpm = HealthMetric(56, HealthMetricAvailability.AVAILABLE, source),
            sleepMinutes = HealthMetric(467, HealthMetricAvailability.AVAILABLE, source),
            averageStressLevel = HealthMetric(24, HealthMetricAvailability.AVAILABLE, source),
            stressHistory = history,
            steps = HealthMetric(7_200, HealthMetricAvailability.AVAILABLE, source),
            stepsHistory = history.map { it.copy(value = 4_000) }
        )
        compose.setContent {
            RunTheme(AppTheme.OLIVE) { ReadinessFactsCard(populatedState().copy(health = health)) }
        }

        compose.onAllNodesWithText(source).assertCountEquals(4)
        compose.onNodeWithContentDescription(
            "Stress & Alltagsaktivität: Stress Ø 24/100 · dein Schnitt 20 · 7.200 Schritte · dein Schnitt 4.000"
        ).assertIsDisplayed()
    }

    @Test fun incompleteHistoryShowsNoScoreAndKeepsDataCoverageSeparate() {
        compose.setContent {
            RunTheme(AppTheme.OLIVE) {
                Column {
                    ReadinessMeter(ReadinessUiState(isLoading = false, hasWorkoutAccess = true))
                }
            }
        }

        compose.onNodeWithText("Noch nicht bewertbar").assertIsDisplayed()
        compose.onNodeWithText("Datenbasis: 0 von 6 Bereichen verfügbar").assertIsDisplayed()
        compose.onNodeWithContentDescription("Erholung noch nicht bewertbar").assertIsDisplayed()
    }

    @Test fun lowScoreUsesCautiousRecoveryLabel() {
        val workouts = baselineRuns() + (1L..5L).map { sampleRun("hard-$it", it * 86_400, 90, null) }
        val score = ReadinessAssessmentAnalyzer().analyze(sampleHealth(300, 65), workouts, Instant.now())
        compose.setContent {
            RunTheme(AppTheme.OLIVE) {
                Column {
                    ReadinessMeter(ReadinessUiState(isLoading = false, assessment = score))
                }
            }
        }

        compose.onNodeWithText("Erholungssignale beachten").assertIsDisplayed()
        compose.onNodeWithContentDescription("Geschätzte Erholung: ${score.score} von 100").assertIsDisplayed()
    }

    @Test fun recoveryMeterColorUsesRedYellowGreenThresholds() {
        val green = Color(0xFF146B35)
        val yellow = Color(0xFF9A7200)
        val red = Color(0xFFB3261E)

        assertEquals(red, recoveryMeterColor(59, green, yellow, red))
        assertEquals(yellow, recoveryMeterColor(60, green, yellow, red))
        assertEquals(yellow, recoveryMeterColor(79, green, yellow, red))
        assertEquals(green, recoveryMeterColor(80, green, yellow, red))
        assertEquals(green, recoveryMeterColor(100, green, yellow, red))
    }

    @Test fun hrvStatusBadgeUsesGreenYellowRedTrafficLightStates() {
        assertEquals(HrvStatusTone.GOOD, hrvStatusTone("Im Normalbereich"))
        assertEquals(HrvStatusTone.CAUTION, hrvStatusTone("Abweichend"))
        assertEquals(HrvStatusTone.LOW, hrvStatusTone("Niedrig"))
        assertEquals(HrvStatusTone.NEUTRAL, hrvStatusTone("Unbekannt"))
    }

    @Test fun recoveryMetricBadgesUseAvailableMetricSpecificSignals() {
        val base = populatedState()
        val selected = mutableStateOf("Ruhepuls" to base)
        compose.setContent {
            RunTheme(AppTheme.OLIVE) {
                ReadinessDataPopup(selected.value.first, selected.value.second)
            }
        }

        listOf(
            25 to "Ruhe",
            26 to "Niedrig",
            51 to "Mittel",
            76 to "Hoch"
        ).forEach { (value, label) ->
            val health = base.health!!.copy(
                averageStressLevel = HealthMetric(value, HealthMetricAvailability.AVAILABLE)
            )
            compose.runOnIdle {
                selected.value = "Stress & Alltagsaktivität" to base.copy(health = health)
            }
            compose.waitForIdle()
            compose.onNodeWithText(label).assertIsDisplayed()
        }

        val pulseAssessment = ReadinessAssessment(
            score = 90,
            sleepScore = 90,
            autonomicScore = 85,
            autonomicSignal = "Ruhepuls",
            trainingLoadScore = 90
        )
        compose.runOnIdle { selected.value = "Ruhepuls" to base.copy(assessment = pulseAssessment) }
        compose.waitForIdle()
        compose.onNodeWithText("Im persönlichen Bereich").assertIsDisplayed()

        val hrvTakesPrecedence = pulseAssessment.copy(autonomicSignal = "HRV/HFV")
        compose.runOnIdle { selected.value = "Ruhepuls" to base.copy(assessment = hrvTakesPrecedence) }
        compose.waitForIdle()
        compose.onAllNodesWithText("Im persönlichen Bereich").assertCountEquals(0)
    }

    @Test fun hrvChartScaleUsesReadableBoundsForVariableAndFlatData() {
        val variable = hrvChartScale(listOf(36, 48))!!
        assertEquals(30, variable.low)
        assertEquals(60, variable.high)
        assertEquals(listOf(60, 50, 40, 30), variable.ticks)

        val flat = hrvChartScale(listOf(48, 48, 48))!!
        assertEquals(30, flat.low)
        assertEquals(60, flat.high)
        assertEquals(listOf(60, 50, 40, 30), flat.ticks)
    }

    @Test fun weeklyComparisonShowsTwoWeeksThenColorCodedDifferences() {
        val previous = TrainingWeekStats(
            startsOn = LocalDate.of(2026, 10, 5), runCount = 3, distanceMeters = 18_400.0,
            durationSeconds = 8_340, comparablePaceSecondsPerKilometer = 434, averageHeartRateBpm = 144
        )
        val current = TrainingWeekStats(
            startsOn = LocalDate.of(2026, 10, 12), runCount = 3, distanceMeters = 19_200.0,
            durationSeconds = 8_760, comparablePaceSecondsPerKilometer = 437, averageHeartRateBpm = 145
        )
        val comparison = WeeklyTrainingComparison(current, previous, 4, "")
        compose.setContent {
            RunTheme(AppTheme.OLIVE) { WeeklyTrainingComparisonCard(comparison) }
        }

        compose.onNodeWithText("Deine zwei letzten Trainingswochen").assertIsDisplayed()
        compose.onNodeWithText("5. Okt. – 11. Okt.: 3 absolvierte Läufe").assertIsDisplayed()
        compose.onNodeWithText("18,4 km · 139 Min. · Pace 7:14 min/km · Puls 144 bpm").assertIsDisplayed()
        compose.onNodeWithText("12. Okt. – 18. Okt.: 3 absolvierte Läufe").assertIsDisplayed()
        compose.onNodeWithText("5. Okt. – 18. Okt.: 6 absolvierte Läufe").assertIsDisplayed()
        compose.onNodeWithText("Strecke").assertIsDisplayed()
        compose.onNodeWithText("Laufzeit").assertIsDisplayed()
        compose.onNodeWithText("Pace").assertIsDisplayed()
        compose.onNodeWithText("Puls").assertIsDisplayed()
        compose.onNodeWithContentDescription("Strecke: ↑ +0,8 km").assertIsDisplayed()
        compose.onNodeWithContentDescription("Laufzeit: ↑ +7 Min.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Pace: ↓ +0:03 min/km").assertIsDisplayed()
        compose.onNodeWithContentDescription("Puls: ↓ +1 bpm").assertIsDisplayed()
    }

    @Test fun realGarminMetricFixturesShowHrvStressAndStepsSeparatelyFromScore() {
        val health = sampleHealth(420, 55).copy(
            hrvLastNightAverageMs = HealthMetric(45, HealthMetricAvailability.AVAILABLE, "Garmin Connect"),
            hrvStatus = HealthMetric("BALANCED", HealthMetricAvailability.AVAILABLE, "Garmin Connect"),
            hrvHistory = (1L..28L).map { de.run.app.domain.model.DailyHealthValue(LocalDate.now().minusDays(it), 42) },
            averageStressLevel = HealthMetric(24, HealthMetricAvailability.AVAILABLE, "Garmin Connect"),
            stressHistory = (1L..28L).map { de.run.app.domain.model.DailyHealthValue(LocalDate.now().minusDays(it), 20) },
            steps = HealthMetric(7_200, HealthMetricAvailability.AVAILABLE, "Garmin Connect"),
            stepsHistory = (1L..28L).map { de.run.app.domain.model.DailyHealthValue(LocalDate.now().minusDays(it), 4_000) }
        )
        val state = populatedState().copy(
            health = health,
            assessment = ReadinessAssessmentAnalyzer().analyze(health, baselineRuns() + recentRuns(), Instant.now())
        )
        compose.setContent {
            RunTheme(AppTheme.OLIVE) {
                Column {
                    ReadinessMeter(state)
                    ReadinessFactsCard(state)
                }
            }
        }

        compose.onNodeWithText("Datenbasis: 6 von 6 Bereichen verfügbar").assertIsDisplayed()
        compose.onNodeWithText("Ausgeglichen · 45 ms letzte Nacht · persönlicher Schnitt 42 ms").assertIsDisplayed()
        compose.onNodeWithText("Stress Ø 24/100 · dein Schnitt 20 · 7.200 Schritte · dein Schnitt 4.000").assertIsDisplayed()
        compose.onAllNodesWithText("Teilwerte (je 0–100): Schlaf 100 · HRV/HFV 100 · Laufbelastung 73").assertCountEquals(0)
    }

    @Test fun tappingTheRecoveryScoreOpensItsCalculationExplanation() {
        val openedDetail = mutableStateOf<Pair<String, String>?>(null)
        val state = populatedState()
        compose.setContent {
            RunTheme(AppTheme.OLIVE) {
                ReadinessMeter(state) { title, detail -> openedDetail.value = title to detail }
            }
        }

        compose.onNodeWithText("92").performClick()

        assertTrue(openedDetail.value?.first == "So entsteht dein Erholungswert")
        assertTrue(openedDetail.value?.second?.contains("Dein Schlaf, deine Herzwerte") == true)
        assertTrue(openedDetail.value?.second?.contains("Je höher der Wert, desto besser") == true)
        assertTrue(openedDetail.value?.second?.contains("keine exakte Messung") == true)
    }

    @Test fun partialHealthDataShowsOnlyThePresentMetricAndNoScore() {
        val partialHealth = sampleHealth(420, null)
        val partial = ReadinessUiState(
            isLoading = false,
            assessment = ReadinessAssessmentAnalyzer().analyze(partialHealth, baselineRuns(), Instant.now()),
            health = partialHealth,
            hasWorkoutAccess = true
        )
        compose.setContent {
            RunTheme(AppTheme.OLIVE) {
                Column {
                    ReadinessMeter(partial)
                    ReadinessFactsCard(partial)
                }
            }
        }

        compose.onNodeWithText("Noch nicht bewertbar").assertIsDisplayed()
        compose.onNodeWithText("Datenbasis: 1 von 6 Bereichen verfügbar").assertIsDisplayed()
        compose.onNodeWithText("7 Std. 0 Min. · persönlicher Vergleich: 7 Std. 0 Min.").assertIsDisplayed()
        compose.onNodeWithText("Noch keine Daten").assertIsDisplayed()
        compose.onNodeWithContentDescription("HRV / HFV: Nicht verfügbar").assertIsDisplayed()
    }

    @Test fun historyRowsOpenOnlyWhenStoredHistoryExists() {
        val openedDetail = mutableStateOf(false)
        val withSavedHistory = populatedState().copy(
            health = populatedState().health?.copy(
                restingHeartRateHistory = listOf(
                    de.run.app.domain.model.DailyHealthValue(LocalDate.of(2026, 10, 23), 55)
                )
            )
        )
        compose.setContent {
            RunTheme(AppTheme.OLIVE) {
                Column {
                    ReadinessFactsCard(withSavedHistory) { title, detail ->
                        openedDetail.value = title == "Ruhepuls" && detail.contains("55 bpm")
                    }
                }
            }
        }

        compose.onNodeWithContentDescription("Ruhepuls: 55 Schläge pro Minute · persönlicher Verlauf noch zu kurz")
            .performClick()
        assertTrue(openedDetail.value)
    }

    @Test fun hrvPopupShowsACompactFourteenDayTrendAndHidesDailyRowsUntilRequested() {
        val history = (1L..28L).map {
            de.run.app.domain.model.DailyHealthValue(LocalDate.now().minusDays(it), 48)
        }
        val state = populatedState().let { base ->
            val health = base.health!!.copy(
                hrvLastNightAverageMs = HealthMetric(48, HealthMetricAvailability.AVAILABLE),
                hrvHistory = history
            )
            base.copy(health = health)
        }
        compose.setContent {
            RunTheme(AppTheme.OLIVE) {
                ReadinessDataPopup("HRV / HFV", state)
            }
        }

        compose.onAllNodesWithText("48 ms")[0].assertIsDisplayed()
        compose.onNodeWithText("Unverändert").assertIsDisplayed()
        compose.onNodeWithText("Übersicht · letzte 28 Tage").assertIsDisplayed()
        compose.onNodeWithText("Durchschnitt").assertIsDisplayed()
        compose.onNodeWithText("Höchster Wert").assertIsDisplayed()
        compose.onNodeWithText("Niedrigster Wert").assertIsDisplayed()
        listOf("60", "50", "40", "30").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onNodeWithContentDescription("HRV / HFV: 14 gespeicherte Messwerte der letzten 14 Tage; vergangene Punkte offen, heutiger Punkt gefüllt und pulsierend").assertIsDisplayed()
        compose.onAllNodesWithText("Dein Normalbereich").assertCountEquals(0)
        compose.onAllNodesWithText("48 ms").assertCountEquals(4)
        compose.onNodeWithText("Alle Werte anzeigen").performClick()
        compose.onAllNodesWithText("48 ms").assertCountEquals(18)
        val todayLabel = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("d. MMM", java.util.Locale.GERMAN))
        compose.onNodeWithText(todayLabel).assertIsDisplayed()
    }

    @Test fun hrvComparisonShowsGreenUpwardDifferenceFromPersonalAverage() {
        val history = varyingHrvHistory()
        val state = populatedState().let { base -> base.copy(health = base.health!!.copy(
            hrvLastNightAverageMs = HealthMetric(48, HealthMetricAvailability.AVAILABLE),
            hrvHistory = history
        )) }
        compose.setContent {
            RunTheme(AppTheme.OLIVE) {
                AppPopup(visible = true, title = "HRV / HFV", onDismiss = {}, bottomSheet = true,
                    largeTitle = true, bottomSheetHeightFraction = 0.82f) {
                    ReadinessDataPopup("HRV / HFV", state)
                }
            }
        }

        compose.onNodeWithText("+6 ms").assertIsDisplayed()
        compose.onNodeWithText("im Vergleich zu deinem Durchschnitt").assertIsDisplayed()
        compose.onNodeWithContentDescription("Trend nach oben", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("HRV / HFV: 14 gespeicherte Messwerte der letzten 14 Tage; vergangene Punkte offen, heutiger Punkt gefüllt und pulsierend").assertIsDisplayed()
        compose.onNodeWithContentDescription("Durchschnittsdiagramm").assertIsDisplayed()
        compose.onNodeWithContentDescription("Pfeil nach oben").assertIsDisplayed()
        compose.onNodeWithContentDescription("Pfeil nach unten").assertIsDisplayed()
    }

    @Test fun hrvComparisonShowsRedDownwardDifferenceFromPersonalAverage() {
        val history = varyingHrvHistory()
        val state = populatedState().let { base -> base.copy(health = base.health!!.copy(
            hrvLastNightAverageMs = HealthMetric(36, HealthMetricAvailability.AVAILABLE),
            hrvHistory = history
        )) }
        compose.setContent {
            RunTheme(AppTheme.OLIVE) {
                AppPopup(visible = true, title = "HRV / HFV", onDismiss = {}, bottomSheet = true,
                    largeTitle = true, bottomSheetHeightFraction = 0.82f) {
                    ReadinessDataPopup("HRV / HFV", state)
                }
            }
        }

        compose.onNodeWithText("−6 ms").assertIsDisplayed()
        compose.onNodeWithText("im Vergleich zu deinem Durchschnitt").assertIsDisplayed()
        compose.onNodeWithContentDescription("Trend nach unten", substring = true).assertIsDisplayed()
    }

    @Test fun emulatorDemoShowsVariableHrvHistoryAndPlusSixComparison() {
        val demo = DebugDemoDataSource(InstrumentationRegistry.getInstrumentation().targetContext)
        val health = demo.healthSnapshot()
        val comparison = hrvAverageComparison(health.hrvHistory, health.hrvLastNightAverageMs.value)

        assertTrue(health.hrvHistory.map { it.value }.distinct().size > 1)
        assertEquals(6, comparison?.differenceMs)
        assertEquals(90, health.hrvHistory.size)
        assertEquals(90, health.sleepHistory.size)
        assertEquals(90, health.restingHeartRateHistory.size)
        assertEquals(90, health.stressHistory.size)
        assertEquals(90, health.stepsHistory.size)

        val workouts = demo.workouts()
        val weekStart = LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        assertTrue(workouts.any {
            it.planMatch?.type == de.run.app.domain.model.PlannedRunMatchType.EXTRA &&
                !it.planMatch.scheduledDate!!.isBefore(weekStart) &&
                !it.planMatch.scheduledDate!!.isAfter(LocalDate.now())
        })
        assertTrue(workouts.minOf { LocalDate.ofInstant(java.time.Instant.ofEpochMilli(it.startedAtMillis), java.time.ZoneId.systemDefault()) }
            .isBefore(LocalDate.now().minusDays(85)))
    }

    @Test fun hrvPopupAdaptsToEveryAppTheme() {
        val history = (1L..28L).map { offset ->
            de.run.app.domain.model.DailyHealthValue(LocalDate.now().minusDays(offset), 40 + ((offset * 7) % 20).toInt())
        }
        val base = populatedState()
        val state = base.copy(health = base.health!!.copy(
            hrvLastNightAverageMs = HealthMetric(48, HealthMetricAvailability.AVAILABLE),
            hrvStatus = HealthMetric("BALANCED", HealthMetricAvailability.AVAILABLE),
            hrvHistory = history
        ))
        val selectedTheme = mutableStateOf(AppTheme.OLIVE)
        compose.setContent {
            RunTheme(selectedTheme.value) {
                AppPopup(
                    visible = true,
                    title = "HRV / HFV",
                    onDismiss = {},
                    bottomSheet = true,
                    largeTitle = true,
                    bottomSheetHeightFraction = 0.82f
                ) {
                    ReadinessDataPopup("HRV / HFV", state)
                }
            }
        }

        AppTheme.entries.forEach { theme ->
            compose.runOnIdle { selectedTheme.value = theme }
            compose.waitForIdle()
            compose.onNodeWithText("HRV / HFV").assertIsDisplayed()
            compose.onAllNodesWithText("48 ms")[0].assertIsDisplayed()
            compose.onNodeWithText("Im Normalbereich").assertIsDisplayed()
            compose.onNodeWithContentDescription("HRV / HFV: 14 gespeicherte Messwerte der letzten 14 Tage; vergangene Punkte offen, heutiger Punkt gefüllt und pulsierend").assertIsDisplayed()
            compose.onNodeWithText("Dein Normalbereich").assertIsDisplayed()
            compose.onNodeWithText("Übersicht · letzte 28 Tage").assertIsDisplayed()
            compose.onNodeWithText("Je höher deine HRV im Vergleich zu deinem persönlichen Bereich", substring = true).assertIsDisplayed()
        }
    }

    @Test fun hrvPopupRequiresAPersonalBaselineBeforeShowingGarminStatus() {
        val base = populatedState()
        val currentOnlyHistory = (1L..13L).map {
            de.run.app.domain.model.DailyHealthValue(LocalDate.now().minusDays(it), 48)
        }
        val withShortBaseline = base.copy(health = base.health!!.copy(
            hrvLastNightAverageMs = HealthMetric(48, HealthMetricAvailability.AVAILABLE),
            hrvStatus = HealthMetric("BALANCED", HealthMetricAvailability.AVAILABLE),
            hrvHistory = currentOnlyHistory
        ))
        val withPersonalBaseline = withShortBaseline.copy(health = withShortBaseline.health!!.copy(
            hrvHistory = (1L..28L).map {
                de.run.app.domain.model.DailyHealthValue(LocalDate.now().minusDays(it), 48)
            }
        ))
        val selectedState = mutableStateOf(withShortBaseline)
        compose.setContent {
            RunTheme(AppTheme.OLIVE) { ReadinessDataPopup("HRV / HFV", selectedState.value) }
        }

        compose.onAllNodesWithText("Im Normalbereich").assertCountEquals(0)
        compose.onAllNodesWithText("im Vergleich zu deinem Durchschnitt").assertCountEquals(0)
        compose.runOnIdle { selectedState.value = withPersonalBaseline }
        compose.onNodeWithText("Im Normalbereich").assertIsDisplayed()
    }

    @Test fun hrvPopupShowsPersonalRangeOnlyWhenStoredValuesVary() {
        val base = populatedState()
        val history = (1L..28L).map { offset ->
            de.run.app.domain.model.DailyHealthValue(
                LocalDate.now().minusDays(offset),
                value = 40 + ((offset * 7) % 20).toInt()
            )
        }
        val state = base.copy(health = base.health!!.copy(
            hrvLastNightAverageMs = HealthMetric(48, HealthMetricAvailability.AVAILABLE),
            hrvHistory = history
        ))
        compose.setContent {
            RunTheme(AppTheme.OLIVE) { ReadinessDataPopup("HRV / HFV", state) }
        }

        compose.onNodeWithText("Dein Normalbereich").assertIsDisplayed()
        compose.onNodeWithText("Je höher deine HRV im Vergleich zu deinem persönlichen Bereich", substring = true).assertIsDisplayed()
    }

    @Test fun recoveryValuePopupUsesTheSharedBlurBottomSheet() {
        compose.setContent {
            RunTheme(AppTheme.OLIVE) {
                AppPopup(visible = true, title = "Ruhepuls", onDismiss = {}, bottomSheet = true) {
                    androidx.compose.material3.Text("Gemeinsamer Erholungs-Popup")
                }
            }
        }

        compose.onNodeWithText("Gemeinsamer Erholungs-Popup").assertIsDisplayed()
    }

    @Test fun hrvPopupDoesNotInventMissingDaysOrInterpretWithoutPersonalRange() {
        val history = listOf(
            de.run.app.domain.model.DailyHealthValue(LocalDate.now().minusDays(2), 48),
            de.run.app.domain.model.DailyHealthValue(LocalDate.now().minusDays(5), 47)
        )
        val state = populatedState().let { base -> base.copy(health = base.health!!.copy(
            hrvLastNightAverageMs = HealthMetric(48, HealthMetricAvailability.AVAILABLE),
            hrvHistory = history
        )) }
        compose.setContent {
            RunTheme(AppTheme.OLIVE) {
                ReadinessDataPopup("HRV / HFV", state)
            }
        }

        compose.onNodeWithContentDescription("HRV / HFV: 3 gespeicherte Messwerte der letzten 14 Tage; vergangene Punkte offen, heutiger Punkt gefüllt und pulsierend").assertIsDisplayed()
        compose.onNodeWithText("Deine nächtliche", substring = true).assertIsDisplayed()
    }

    @Test fun unavailableHrvHasNoChartStatisticsOrFakeHistoryAction() {
        val state = populatedState().let { base ->
            base.copy(health = base.health!!.copy(
                hrvLastNightAverageMs = HealthMetric(null, HealthMetricAvailability.NO_DATA),
                hrvHistory = emptyList()
            ))
        }
        compose.setContent {
            RunTheme(AppTheme.OLIVE) { ReadinessDataPopup("HRV / HFV", state) }
        }

        compose.onNodeWithText("Nicht verfügbar").assertIsDisplayed()
        compose.onNodeWithText("Noch keine Messwerte für den Verlauf gespeichert.").assertIsDisplayed()
        compose.onAllNodesWithText("Alle Werte anzeigen").assertCountEquals(0)
        compose.onAllNodesWithText("Übersicht · letzte 28 Tage").assertCountEquals(0)
        compose.onAllNodesWithText("HRV / HFV: 0 gespeicherte Messwerte der letzten 14 Tage").assertCountEquals(0)
    }

    @Test fun allSixRecoveryPopupsUseTheirStoredMeasurements() {
        val base = populatedState()
        val daily = (1L..28L).map { de.run.app.domain.model.DailyHealthValue(LocalDate.now().minusDays(it), 48) }
        val health = base.health!!.copy(
            hrvLastNightAverageMs = HealthMetric(48, HealthMetricAvailability.AVAILABLE),
            hrvHistory = daily,
            averageStressLevel = HealthMetric(24, HealthMetricAvailability.AVAILABLE),
            stressHistory = daily.map { it.copy(value = 24) },
            steps = HealthMetric(7_200, HealthMetricAvailability.AVAILABLE),
            stepsHistory = daily.map { it.copy(value = 7_200) }
        )
        val state = base.copy(health = health)
        val cases = listOf(
            "HRV / HFV" to "48 ms",
            "Ruhepuls" to "55 bpm",
            "Schlaf & Verlauf" to "7 Std. 0 Min.",
            "Trainingsbelastung" to "2 Läufe · 80 Min.",
            "Erholungszeit" to "1 Std.",
            "Stress & Alltagsaktivität" to "24 von 100"
        )

        val selectedTitle = mutableStateOf(cases.first().first)
        compose.setContent {
            RunTheme(AppTheme.OLIVE) { ReadinessDataPopup(selectedTitle.value, state) }
        }
        cases.forEach { (title, value) ->
            compose.runOnIdle { selectedTitle.value = title }
            compose.waitForIdle()
            val expectedValueRows = if (title in setOf("Trainingsbelastung", "Erholungszeit")) 1 else 4
            compose.onAllNodesWithText(value).assertCountEquals(expectedValueRows)
            compose.onAllNodesWithText("Alle Werte anzeigen")
                .assertCountEquals(if (title == "Stress & Alltagsaktivität") 2 else 1)
        }
        compose.onAllNodesWithText("7.200 Schritte").assertCountEquals(4)
    }

    private fun populatedState() = ReadinessUiState(
        isLoading = false,
        assessment = ReadinessAssessmentAnalyzer().analyze(sampleHealth(420, 55), baselineRuns() + recentRuns(), Instant.now()),
        health = sampleHealth(420, 55),
        facts = ReadinessFacts(
            minutesSinceLastRun = 90,
            runsThisWeek = 1,
            plannedRunsThisWeek = 2,
            distanceThisWeekMeters = 5_000.0
        ),
        hasWorkoutAccess = true,
        recentWorkouts = baselineRuns() + recentRuns(),
        weeklyTrainingComparison = WeeklyTrainingComparison(
            current = TrainingWeekStats(
                startsOn = LocalDate.of(2026, 10, 12), runCount = 2, distanceMeters = 10_000.0,
                durationSeconds = 4_800, comparablePaceSecondsPerKilometer = null, averageHeartRateBpm = null
            ),
            previous = TrainingWeekStats(
                startsOn = LocalDate.of(2026, 10, 5), runCount = 1, distanceMeters = 5_000.0,
                durationSeconds = 2_400, comparablePaceSecondsPerKilometer = null, averageHeartRateBpm = null
            ),
            distanceChangePercent = 100,
            feedback = ""
        )
    )

    private fun sampleRun(id: String, secondsAgo: Long, durationMinutes: Int, distanceMeters: Double?) =
        de.run.app.domain.model.Workout(
            sourceId = id,
            sourceName = "Garmin Connect",
            title = "Lauf",
            startedAtMillis = System.currentTimeMillis() - secondsAgo * 1_000 - durationMinutes * 60_000L,
            endedAtMillis = System.currentTimeMillis() - secondsAgo * 1_000,
            distanceMeters = distanceMeters
        )

    private fun sampleHealth(sleepMinutes: Int, restingHeartRate: Int?) = ReadinessHealthSnapshot(
        sleepMinutes = HealthMetric(sleepMinutes, HealthMetricAvailability.AVAILABLE),
        restingHeartRateBpm = restingHeartRate?.let { HealthMetric(it, HealthMetricAvailability.AVAILABLE) }
            ?: HealthMetric(null, HealthMetricAvailability.NO_DATA),
        sleepHistory = (1L..28L).map { de.run.app.domain.model.DailyHealthValue(LocalDate.now().minusDays(it), 420) },
        restingHeartRateHistory = (1L..28L).map { de.run.app.domain.model.DailyHealthValue(LocalDate.now().minusDays(it), 55) }
    )

    private fun varyingHrvHistory() = (1L..28L).map { offset ->
        de.run.app.domain.model.DailyHealthValue(
            LocalDate.now().minusDays(offset),
            36 + ((offset * 7) % 13).toInt()
        )
    }


    private fun baselineRuns() = listOf(9L, 16L, 23L, 34L).map { sampleRun("base-$it", it * 86_400, 60, null) }

    private fun recentRuns() = listOf(
        sampleRun("run-1", 90, 40, 5_000.0),
        sampleRun("run-2", 2 * 86_400, 40, 5_000.0)
    )
}
