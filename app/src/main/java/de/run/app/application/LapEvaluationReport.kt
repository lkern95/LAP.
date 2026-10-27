package de.run.app.application

import de.run.app.domain.HalfMarathonForecast
import de.run.app.domain.TrainingFeedback
import de.run.app.domain.TrainingPlanEvidence
import de.run.app.domain.TrainingPlanEvidenceAnalyzer
import de.run.app.domain.TrainingPlanEvidenceStatus
import de.run.app.domain.WorkoutPlanStatus
import de.run.app.domain.model.DistanceSpikeAssessment
import de.run.app.domain.model.DistanceSpikeLevel
import de.run.app.domain.model.HealthMetric
import de.run.app.domain.model.PlannedRun
import de.run.app.domain.model.ReadinessAssessment
import de.run.app.domain.model.ReadinessFacts
import de.run.app.domain.model.ReadinessGuidance
import de.run.app.domain.model.ReadinessHealthSnapshot
import de.run.app.domain.model.TrainingGoal
import de.run.app.domain.model.TrainingPlan
import de.run.app.domain.model.TrainingProfile
import de.run.app.domain.model.TrainingProgress
import de.run.app.domain.model.Workout
import de.run.app.domain.model.AppTheme
import de.run.app.domain.model.StrengthCatalog
import de.run.app.domain.model.StrengthSessionProgress
import de.run.app.domain.model.TrainingDecisionSnapshot
import de.run.app.domain.model.TrainingHistoryCoverage
import de.run.app.domain.model.TrainingHistoryAccess
import de.run.app.domain.model.isRunning
import de.run.app.domain.TrainingProgressDashboard
import de.run.app.domain.ProgressPeriod
import de.run.app.domain.WeeklyTrainingComparison
import de.run.app.domain.repository.FitActivityImportResult
import de.run.app.domain.repository.GarminConnectHistorySyncSummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class LapEvaluationReportInput(
    val generatedAt: Instant,
    val appVersion: String,
    val isDebugBuild: Boolean,
    val isDemoData: Boolean,
    val profile: TrainingProfile?,
    val goal: TrainingGoal?,
    val goals: List<TrainingGoal> = emptyList(),
    val plan: TrainingPlan?,
    val progress: TrainingProgress?,
    val nextRun: PlannedRun?,
    val forecast: HalfMarathonForecast?,
    val feedback: TrainingFeedback?,
    val workouts: List<Workout>,
    val health: ReadinessHealthSnapshot?,
    val readiness: ReadinessAssessment,
    val readinessFacts: ReadinessFacts?,
    val readinessGuidance: ReadinessGuidance,
    val readinessWeeklyComparison: WeeklyTrainingComparison? = null,
    val readinessHealthReadFailed: Boolean = false,
    val readinessWorkoutAccess: Boolean? = null,
    val readinessWorkoutsUnavailable: Boolean = false,
    val readinessWorkoutsFailed: Boolean = false,
    val workoutDashboard: TrainingProgressDashboard? = null,
    val workoutSelectedPeriod: ProgressPeriod = ProgressPeriod.MONTH,
    val workoutReadStatus: String = "Nicht erfasst",
    val workoutFullHistoryAccess: Boolean? = null,
    val workoutLastReadAt: Instant? = null,
    val workoutLastRefreshImportedCount: Int? = null,
    val fitImportResult: FitActivityImportResult? = null,
    val planCompletedRuns: Int = 0,
    val planLoadError: Boolean = false,
    val goalHistoryError: Boolean = false,
    val goalPaceSecondsPerKilometer: Long? = null,
    val goalPaceUsesForecast: Boolean = false,
    val pendingCompetitionCandidate: Boolean = false,
    val decisionSnapshot: TrainingDecisionSnapshot? = null,
    val trainingHistoryCoverage: TrainingHistoryCoverage = TrainingHistoryCoverage(),
    val planEvidence: TrainingPlanEvidence = TrainingPlanEvidenceAnalyzer().analyze(
        trainingHistoryCoverage, plan
    ),
    val liveCoach: LiveCoachEvaluationSnapshot = LiveCoachEvaluationSnapshot(),
    val theme: AppTheme = AppTheme.OLIVE,
    val garmin: GarminEvaluationSnapshot = GarminEvaluationSnapshot(),
    val strengthCatalog: StrengthCatalog? = null,
    val strengthSessions: List<StrengthSessionProgress> = emptyList(),
    val strengthCompletionCountsLastFourWeeks: Map<String, Int> = emptyMap()
)

data class GarminEvaluationSnapshot(
    val isConnected: Boolean = false,
    val isSyncing: Boolean = false,
    val hasSyncError: Boolean = false,
    val lastSyncAt: Instant? = null,
    val originalFitSaved: Boolean = false,
    val workoutImported: Boolean = false,
    val trainingPlanUpdated: Boolean = false,
    val lastHistorySyncSummary: GarminConnectHistorySyncSummary? = null
)

data class LiveCoachEvaluationSnapshot(
    val active: Boolean = false,
    val connectionStatus: String = "IDLE",
    val transport: String = "UNKNOWN",
    val receiver: String = "NOT_REGISTERED",
    val incomingMessages: Long = 0,
    val rejectedMessages: Long = 0,
    val validMeasurements: Long = 0,
    val targetTransfer: String = "NOT_SENT",
    val estimatedLatencyMillis: Long? = null,
    val averageMessagesPerSecond: Double = 0.0,
    val audioAnnouncementsEnabled: Boolean = false,
    val lastCue: String? = null,
    val activeSeconds: Long? = null,
    val distanceMeters: Double? = null,
    val paceSecondsPerKilometer: Int? = null,
    val heartRateBpm: Int? = null
)

/** Builds a fresh, human-readable local report from the same domain results LAP currently displays. */
class LapEvaluationReportBuilder(
    private val scenarioRunner: LapEvaluationScenarioRunner = LapEvaluationScenarioRunner()
) {
    fun build(input: LapEvaluationReportInput): String = buildString {
        appendLine("# LAP-Auswertungsprotokoll")
        appendLine()
        appendLine("Erstellt: ${DATE_TIME.format(input.generatedAt.atZone(ZoneId.systemDefault()))}")
        appendLine("LAP-Version: ${input.appVersion}")
        appendLine("Umgebung: ${if (input.isDebugBuild) "Debug-Emulator / Debug-Build" else "App"}")
        appendLine("Datenmodus: ${if (input.isDemoData) "sichtbar als Demodaten gekennzeichnet" else "lokale App- und freigegebene Gerätedaten"}")
        appendLine()
        appendLine("> Dieser Bericht enthält persönliche Trainings- und Gesundheitswerte. Er wurde auf deine Anforderung erstellt und nur am ausgewählten Speicherort abgelegt.")
        appendLine()
        appendProfile(input.profile)
        appendGoalHistory(input.goals, input.goalHistoryError)
        appendPlan(input)
        appendProgress(input.progress)
        appendProgressDashboard(input.workoutDashboard, input.workoutSelectedPeriod)
        appendReadiness(input.health, input.readiness, input.readinessFacts, input.readinessGuidance)
        appendWeeklyComparison(input.readinessWeeklyComparison, input.readinessWorkoutAccess, input.readinessWorkoutsUnavailable, input.readinessWorkoutsFailed, input.readinessHealthReadFailed)
        appendForecast(input.forecast)
        appendFeedback(input.feedback)
        appendWorkouts(input.workouts)
        appendIntegrations(input)
        appendDataImportAndAccess(input)
        appendLiveCoach(input.liveCoach)
        appendStrength(input.strengthCatalog, input.strengthSessions, input.strengthCompletionCountsLastFourWeeks)
        appendFeatureCoverage()
        appendDebugScenarios(
            input.generatedAt.atZone(ZoneId.systemDefault()).toLocalDate(),
            input.isDebugBuild
        )
    }

    private fun StringBuilder.appendProfile(profile: TrainingProfile?) {
        appendLine("## Verwendetes Trainingsprofil")
        if (profile == null) appendLine("Noch kein Trainingsprofil gespeichert.")
        else appendLine("Planmodus ${profile.planMode.toUserLabel()}; Ausgangslauf ${profile.longestComfortableRunMinutes} Min.; optionale Laufeinheit ${yesNo(profile.optionalRun)}; Krafttermine/Woche ${profile.strengthSessionsPerWeek}; Verfügbarkeit ${if (profile.availabilityMode == de.run.app.domain.model.AvailabilityMode.RECURRING) "feste Wochentage" else "flexibel"}; typische Zeitobergrenze ${profile.usualRunTimeLimitMinutes} Min.; längere Gelegenheitsläufe ${profile.occasionalRunTimeLimitMinutes?.let { "$it Min." } ?: "nicht festgelegt"}.")
    }

    private fun StringBuilder.appendGoalHistory(goals: List<TrainingGoal>, readFailed: Boolean) {
        appendLine("\n## Ziele und Zielhistorie")
        appendLine("Historienabruf: ${if (readFailed) "fehlgeschlagen" else "verfügbarer aktueller Bestand"}; ${goals.size} Ziele.")
        if (goals.isEmpty()) appendLine("Keine gespeicherten Ziele verfügbar.")
        goals.forEach { goal ->
            appendLine("- ${km(goal.raceDistanceMeters.toDouble())} km am ${goal.raceDate}; Status ${goal.status}; Zielart ${goal.raceGoal}; Zielzeit ${goal.targetTimeMinutes?.let { "$it Min." } ?: "nicht gesetzt"}; Wettkampf absolviert ${goal.competitionCompleted?.let(::yesNo) ?: "offen"}; Ziel erreicht ${goal.goalAchieved?.let(::yesNo) ?: "nicht bewertet"}.")
        }
    }

    private fun StringBuilder.appendIntegrations(input: LapEvaluationReportInput) {
        appendLine("\n## Datenquellen und Geräte")
        appendLine("Aktiver Farbstil: ${input.theme.toUserLabel()}.")
        appendLine("Garmin Connect: ${if (input.garmin.isConnected) "verbunden" else "nicht verbunden"}; Synchronisierung ${if (input.garmin.isSyncing) "läuft" else "nicht aktiv"}; letzter Abgleich ${input.garmin.lastSyncAt?.let { DATE_TIME.format(it.atZone(ZoneId.systemDefault())) } ?: "in dieser Sitzung nicht erfasst"}.")
        appendLine("Letzter Garmin-Abgleich: ${if (input.garmin.hasSyncError) "mit Fehler gemeldet" else "kein Fehler gemeldet"}; FIT-Original gespeichert ${yesNo(input.garmin.originalFitSaved)}; Lauf übernommen ${yesNo(input.garmin.workoutImported)}; Plan dadurch aktualisiert ${yesNo(input.garmin.trainingPlanUpdated)}.")
        input.garmin.lastHistorySyncSummary?.let { summary ->
            appendLine("Letzter Garmin-Laufabgleich: ${if (summary.listingCompleted) "Liste vollständig" else "Liste unvollständig"}; ${summary.found} gefunden, ${summary.newlyImported} neu, ${summary.alreadyPresent} schon vorhanden, ${summary.failed} fehlgeschlagen, ${summary.originalsSaved} Originale lokal gesichert.")
            appendLine("Abbruchgründe: Zugriffsbremse ${yesNo(summary.stoppedByRateLimit)}, Garmin-Zugriff verweigert ${yesNo(summary.stoppedByGarminAccess)}; Gesundheitsdaten: ${summary.healthDaysUpdated} Tage aktualisiert, Fehler ${yesNo(summary.healthSyncFailed)}, blockiert ${yesNo(summary.healthSyncBlocked)}, Anmeldung abgelaufen ${yesNo(summary.healthAuthenticationExpired)}.")
        } ?: appendLine("Zusammenfassung des letzten Garmin-Abgleichs: in dieser Sitzung nicht verfügbar.")
        appendLine("Health Connect: tatsächliche Messwerte, Freigabe- und Lesestatus stehen im Abschnitt Erholung und Gesundheitswerte. Einzelne Synchronisationsereignisse werden nicht dauerhaft protokolliert.")
        appendLine("Letzte Workout-Abfrage: ${input.workoutReadStatus}; Vollzugriff auf Trainingshistorie ${input.workoutFullHistoryAccess?.let(::yesNo) ?: "nicht bekannt"}; letzter erfolgreicher Abruf ${input.workoutLastReadAt?.let { DATE_TIME.format(it.atZone(ZoneId.systemDefault())) } ?: "nicht vorhanden"}; beim letzten Abruf neu erkannt ${input.workoutLastRefreshImportedCount?.toString() ?: "nicht erfasst"}.")
    }

    private fun StringBuilder.appendDataImportAndAccess(input: LapEvaluationReportInput) {
        appendLine("\n## FIT-Import und Datenzugriff")
        when (val result = input.fitImportResult) {
            null -> appendLine("FIT-Import: in dieser Sitzung nicht ausgeführt oder Ergebnis nicht mehr verfügbar.")
            is FitActivityImportResult.Imported -> {
                appendLine("Letzter Import: ${result.foundActivities} Aktivitäten gefunden, ${result.newlyImportedActivities} neu, ${result.alreadyPresentActivities} bereits vorhanden; ${result.savedOriginalFiles} Originale gespeichert; ${result.skippedFiles} übersprungen.")
                result.unsupportedFiles.forEach { (category, count) -> appendLine("- Nicht unterstützt: $category ($count Dateien).") }
            }
            FitActivityImportResult.NotActivityFile -> appendLine("Letzter Import: Datei enthielt keine Aktivität.")
            FitActivityImportResult.NoActivitySessions -> appendLine("Letzter Import: keine Aktivitätssitzung in der Datei.")
            FitActivityImportResult.InvalidFile -> appendLine("Letzter Import: ungültige Datei.")
            FitActivityImportResult.FileTooLarge -> appendLine("Letzter Import: Sicherheitsgrößenlimit erreicht.")
            FitActivityImportResult.Failed -> appendLine("Letzter Import: fehlgeschlagen.")
        }
        appendLine("Import-Nachrichten, URI, Originaldateiinhalte und Geheimnisse werden nicht in diesen Bericht geschrieben.")
    }

    private fun StringBuilder.appendLiveCoach(live: LiveCoachEvaluationSnapshot) {
        appendLine("\n## Live-Laufbegleitung und Audio")
        appendLine("Laufbegleitung ${if (live.active) "aktiv" else "inaktiv"}; Garmin-Live-Verbindung ${live.connectionStatus}; Transport ${live.transport}; Empfänger ${live.receiver}; Audioansagen ${if (live.audioAnnouncementsEnabled) "aktiviert" else "deaktiviert"}.")
        appendLine("Technikwerte: eingehende Nachrichten ${live.incomingMessages}, verworfen ${live.rejectedMessages}, gültige Messungen ${live.validMeasurements}, Nachrichten/Sekunde ${"%.2f".format(Locale.GERMANY, live.averageMessagesPerSecond)}, Latenz ${live.estimatedLatencyMillis?.let { "$it ms" } ?: "n. v."}, Trainingsziel-Übertragung ${live.targetTransfer}; letzte Ansage ${live.lastCue ?: "keine"}.")
        appendLine("Letzter Live-Messpunkt: ${live.activeSeconds?.let { "$it s" } ?: "n. v."}; Strecke ${live.distanceMeters?.let { "${km(it)} km" } ?: "n. v."}; Pace ${live.paceSecondsPerKilometer?.let { pace(it.toLong()) } ?: "n. v."}; Puls ${live.heartRateBpm?.let { "$it bpm" } ?: "n. v."}.")
        appendLine("Ältere Live-Messpunkte und Ansagen werden nicht dauerhaft protokolliert.")
    }

    private fun StringBuilder.appendProgressDashboard(dashboard: TrainingProgressDashboard?, period: ProgressPeriod) {
        appendLine("\n## Fortschrittsansicht und Diagrammdaten")
        if (dashboard == null) {
            appendLine("Fortschrittsdiagramm beim Export nicht verfügbar.")
            return
        }
        appendLine("Ausgewählter Zeitraum ${period.label}; Laufanzahl ${dashboard.runCount}; Strecke ${km(dashboard.distanceMeters)} km; Vergleichszeitraum ${dashboard.previousDistanceMeters?.let { "${km(it)} km" } ?: "nicht vorhanden"}; längster jüngster Lauf ${dashboard.longestRecentRunMeters?.let { "${km(it)} km" } ?: "n. v."}; Durchschnittspace ${dashboard.averagePaceSecondsPerKilometer?.let(::pace) ?: "n. v."}.")
        appendLine("Diagrammwerte (Datum, Label, Strecke, Läufe):")
        dashboard.chartBuckets.forEach { bucket -> appendLine("- ${bucket.startsOn}, ${bucket.label}: ${km(bucket.distanceMeters)} km, ${bucket.runCount} Läufe.") }
        appendLine("Wochenverlauf Distanz (m): ${dashboard.weeklyDistanceTrendMeters.joinToString()}; längste Läufe (m): ${dashboard.weeklyLongestRunMeters.joinToString()}; Pace (s/km): ${dashboard.weeklyAveragePaceSecondsPerKilometer.joinToString { it?.toString() ?: "n. v." }}.")
    }

    private fun StringBuilder.appendWeeklyComparison(
        comparison: WeeklyTrainingComparison?, access: Boolean?, unavailable: Boolean, failed: Boolean, healthFailed: Boolean
    ) {
        appendLine("\n## Wochenvergleich und Zugriffsstatus")
        appendLine("Laufzugriff ${access?.let(::yesNo) ?: if (unavailable) "nicht verfügbar" else if (failed) "Lesefehler" else "nicht bekannt"}; Gesundheitslesung ${if (healthFailed) "fehlgeschlagen" else "kein Lesefehler gemeldet"}.")
        if (comparison == null) appendLine("Wochenvergleich noch nicht berechnet.")
        else {
            appendLine("Bewertung: ${comparison.feedback}; Distanzveränderung ${comparison.distanceChangePercent?.let { "$it %" } ?: "nicht berechenbar"}.")
            appendWeekStats("Aktuelle vollständige Woche", comparison.current)
            appendWeekStats("Vorherige vollständige Woche", comparison.previous)
        }
    }

    private fun StringBuilder.appendWeekStats(label: String, week: de.run.app.domain.TrainingWeekStats) {
        appendLine("- $label ab ${week.startsOn}: ${week.runCount} Läufe; Strecke ${week.distanceMeters?.let { "${km(it)} km" } ?: "unvollständig"}; ${week.durationSeconds / 60} Min.; Pace ${week.comparablePaceSecondsPerKilometer?.let(::pace) ?: "n. v."}; Puls ${week.averageHeartRateBpm?.let { "$it bpm" } ?: "n. v."}; vergleichbare Läufe ${week.comparablePaceRunCount}; Pulswerte ${week.heartRateRunCount}.")
    }

    private fun StringBuilder.appendStrength(
        catalog: StrengthCatalog?,
        sessions: List<StrengthSessionProgress>,
        completionCounts: Map<String, Int>
    ) {
        appendLine("\n## Krafttraining und Fortschritt")
        if (catalog == null) appendLine("Übungskatalog beim Erstellen der Auswertung nicht geladen.")
        else appendLine("Übungskatalog ${catalog.schemaVersion}: ${catalog.exercises.size} Übungen, ${catalog.workouts.size} Programme.")
        appendLine("Kraftübungs-Abschlüsse der letzten 4 Wochen:")
        if (completionCounts.isEmpty()) appendLine("- keine Abschlussdaten")
        else completionCounts.toSortedMap().forEach { (exerciseId, count) ->
            val title = catalog?.exercisesById?.get(exerciseId)?.title ?: "Übung $exerciseId"
            appendLine("- $title: $count Abschlüsse.")
        }
        val completed = sessions.count { it.status == StrengthSessionProgress.Status.COMPLETED }
        appendLine("Gespeicherte Einheiten: ${sessions.size}; abgeschlossen ${completed}; aktuell offen ${sessions.size - completed}.")
        sessions.sortedByDescending { it.startedAtMillis }.forEach { session ->
            val workout = catalog?.workouts?.firstOrNull { it.id == session.workoutId }
            val started = DATE_TIME.format(Instant.ofEpochMilli(session.startedAtMillis).atZone(ZoneId.systemDefault()))
            val finished = session.finishedAtMillis?.let { DATE_TIME.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) } ?: "nicht abgeschlossen"
            appendLine("- ${workout?.title ?: "Programm ${session.workoutId}"}; begonnen $started; beendet $finished; Status ${if (session.status == StrengthSessionProgress.Status.COMPLETED) "abgeschlossen" else "läuft"}; erledigte Sätze ${session.completedSets.size}; aktuelle Runde ${session.currentRound}.")
        }
    }

    private fun StringBuilder.appendFeatureCoverage() {
        appendLine("\n## Funktionsabdeckung der LAP-Auswertung")
        appendLine("Erfasst werden alle aktuellen fachlichen Funktionsbereiche und deren beim Export auslesbare Ergebnisse. Dies ist eine Momentaufnahme, kein automatisch geführtes Verlaufsprotokoll.")
        appendLine("### Profil, Ziele und Plan")
        appendLine("TrainingPlanBuilder, TrainingAvailabilityPlanner, TrainingResponseAnalyzer, TrainingPlanAdjustmentExplainer, RunPrescriptionAnalyzer, RunDistanceSpikeAnalyzer, ContinuousRunProgressionAnalyzer, ManualRunDurationLimit, TrainingRunDurationAdjuster, UpcomingScheduledRunSelector, WorkoutPlanCompletionAnalyzer und TrainingGoalCandidateSelector → Profil, Zielhistorie, Entscheidungssnapshot, rollierende Vorschau von höchstens zwei Wochen, Gesamt- und Durchlaufprogression auf Grundlage tatsächlich eingelesener Läufe und expliziter Rückmeldungen.")
        appendLine("### Training, Laufhistorie und Feedback")
        appendLine("TrainingProgressAnalyzer, TrainingProgressDashboardAnalyzer, WeeklyTrainingComparisonAnalyzer, WorkoutHistoryPeriodFilter, PlannedWorkoutMatcher, TrainingFeedbackEngine, EvaluateWorkoutTrainingFeedback, EvaluateImportedWorkouts und NewlySyncedGarminWorkoutSelector → gespeicherte Läufe, Planbewertungen, Lauf-Feedback, Wochenvergleich und Diagrammdaten.")
        appendLine("### Erholung und Gesundheitswerte")
        appendLine("ReadinessHealthRepository, ReadinessFactsAnalyzer, ReadinessGuidanceAnalyzer, ReadinessAssessmentAnalyzer und ReadinessSignalBands → Messwerte, Verlauf, Lesestatus, Teilwerte, Schätzung und Hinweis.")
        appendLine("### Ziele und Prognosen")
        appendLine("HalfMarathonForecastAnalyzer und Zielpaceberechnung → Prognose, Datenqualität, Evidenz, Zeitspanne, Begründung und Empfehlung.")
        appendLine("### Geräte, Import und Synchronisierung")
        appendLine("ConnectGarminConnect, SubmitGarminConnectMfa, SyncAllGarminConnectActivities, GarminConnectRepository, ImportFitActivity, FitActivityImportRepository und Health-Connect-Leser → Verbindungsstand, Synchronisierungsergebnis, FIT-Ergebnis, Freigaben und lokale Messwerte. Zugangsdaten und Datei-URIs werden nie exportiert.")
        appendLine("### Krafttraining")
        appendLine("StrengthSessionProgressor, StrengthProgressAnalyzer und Kraft-Repositorys → Katalog, gespeicherte Sätze/Sitzungen und Abschlüsse je Übung der letzten 4 Wochen.")
        appendLine("### Live-Laufbegleitung, Sicherung und Darstellung")
        appendLine("LiveRunCoach, GarminLivePacketDecoder, LivePaceSmoother, GarminTrainingTarget und GarminConnectHealthSharing → aktueller Live-Status, letzter Messpunkt, technische Zähler, Zielübertragung und Audioeinstellung. Ältere Sitzungen/Ansagen werden nicht gespeichert. AppBackupRepository und ExportLapEvaluationReport → Exportfunktionen; frühere Sicherungsaktionen werden nicht protokolliert. Theme und Version stehen im Kopfbereich.")
        appendLine("- Prüffälle unten: ausgewählte echte Grenzfälle, keine vollständige Kombinationstestung jeder denkbaren Eingabe.")
    }

    private fun StringBuilder.appendPlan(input: LapEvaluationReportInput) {
        appendLine("## Trainingsplan und nächste Empfehlung")
        appendLine("Planabruf: ${if (input.planLoadError) "mit Lesefehler" else "kein Lesefehler gemeldet"}; absolvierte geplante Läufe der aktuellen Woche: ${input.planCompletedRuns}; erkannter Lauf am Wettkampftag wartet auf Bestätigung: ${yesNo(input.pendingCompetitionCandidate)}; Zielpace ${input.goalPaceSecondsPerKilometer?.let(::pace) ?: "nicht verfügbar"}${if (input.goalPaceUsesForecast) " (aus Prognose)" else " (Zielzeit oder nicht berechnet)"}.")
        appendLine("Zugriff auf Trainingshistorie: ${input.trainingHistoryCoverage.access.toReportLabel()}; Vollzugriff ${input.trainingHistoryCoverage.hasFullHistoryAccess?.let(::yesNo) ?: "nicht bestätigt"}; Läufe im verfügbaren Zeitraum ${input.trainingHistoryCoverage.accessibleRunCount?.toString() ?: "nicht bekannt"}; lokal gespeicherte Läufe ${input.trainingHistoryCoverage.locallySavedRunCount}.")
        appendLine("Einordnung der Planbasis: ${input.planEvidence.status.toReportLabel()}; ${if (input.planEvidence.requiresStarterPlan) "vorläufiger Einstiegsplan (höchstens zwei Wochen sichtbar)" else "persönliche Pace-Vergleichsbasis vorhanden"}. Fehlender Zugriff wird nicht als Beleg für fehlende Aktivität gewertet.")
        appendLine("Planhorizont: höchstens zwei Wochen. Weitere Steigerungen werden nach tatsächlich aufgezeichneten Läufen neu berechnet; zukünftige Trainingswochen werden nicht aus einer angenommenen Erfüllung hochgerechnet.")
        if (input.goal?.raceGoal == de.run.app.domain.model.RaceGoal.RUN_THROUGH) {
            appendLine("Durchlaufziel: längster Laufabschnitt startet bei bis zu 30 Minuten am Stück und wächst je qualifiziertem langen Lauf um 5 Minuten (Plan passt, RPE 2–4, keine gemeldeten Beschwerden; letzte 84 Tage). Garmin-/Health-Connect-Daten enthalten keine verlässliche ununterbrochene Laufzeit; dies ist eine geplante Progression, keine Messung.")
        }
        input.decisionSnapshot?.let { snapshot ->
            appendLine("Entscheidungsnachweis: Regelversion ${snapshot.ruleVersion}; Datum ${snapshot.evaluatedOn}; ${snapshot.runCount} Läufe berücksichtigt; letzter Lauf ${snapshot.latestRunEndedAtMillis?.let { DATE_TIME.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) } ?: "keiner"}; Empfehlung ${snapshot.recommendationTitle ?: "n. v."} (${snapshot.recommendationMinutes?.let { "$it Min." } ?: "n. v."}); Grundcode ${snapshot.reasonCode}; lokale Eingaberevision ${snapshot.inputRevision}; Zielrevision ${snapshot.targetRevision ?: "kein Ziel"}.")
        } ?: appendLine("Entscheidungsnachweis: nicht verfügbar.")
        val plan = input.plan
        if (plan == null) appendLine("Noch kein Trainingsplan verfügbar.")
        else {
            appendLine("Planstatus: ${plan.status.toUserLabel()}${if (input.planEvidence.requiresStarterPlan) " (vorläufige Planbasis; nur die nächsten zwei Wochen werden angezeigt)" else ""}")
            appendLine("Planentscheidung: ${if (plan.adjustment == de.run.app.domain.model.PlanAdjustment.NO_RUN_DATA) input.planEvidence.noRunDataLabel() else plan.adjustment.toUserLabel()}")
            plan.adjustmentExplanation?.let { appendLine("Begründung: $it") }
            input.goal?.let { goal ->
                appendLine("Ziel: ${km(goal.raceDistanceMeters.toDouble())} km am ${goal.raceDate}; Zielart ${goal.raceGoal.toUserLabel()}; Zielzeit ${goal.targetTimeMinutes?.let { "$it Min." } ?: "nicht gesetzt"}.")
            }
            plan.weeks.forEach { week ->
                appendLine()
                appendLine("Woche ${week.number} ab ${week.startsOn} · Erholungswoche: ${yesNo(week.isRecoveryWeek)} · Krafttermine: ${week.strengthSessions} · geplante Lauftage: ${week.scheduledRuns.joinToString { it.scheduledDate?.toString() ?: it.day.toString() }.ifBlank { "flexibel/nicht festgelegt" }}")
                week.scheduledRuns.forEach { scheduled ->
                    appendRun(scheduled.run, scheduled.scheduledDate?.toString() ?: scheduled.day.name)
                }
                if (week.scheduledRuns.isEmpty()) week.runs.forEach { appendRun(it, "Tag nicht festgelegt") }
                input.goal?.takeIf { goal ->
                    goal.status == de.run.app.domain.model.TrainingGoalStatus.ACTIVE &&
                        goal.raceDate in week.startsOn..week.startsOn.plusDays(6)
                }?.let { goal -> appendLine("- ${goal.raceDate} · Wettkampf: ${km(goal.raceDistanceMeters.toDouble())} km") }
            }
        }
        appendLine("\nNächster Lauf: ${input.nextRun?.let { "${it.title}, ${it.totalMinutes} Min." } ?: "nicht verfügbar"}")
    }

    private fun TrainingPlanEvidenceStatus.toReportLabel(): String = when (this) {
        TrainingPlanEvidenceStatus.CHECK_REQUIRED -> "Zugriff noch nicht geprüft"
        TrainingPlanEvidenceStatus.ACCESS_REQUIRED -> "Zugriff/Freigabe fehlt"
        TrainingPlanEvidenceStatus.SOURCE_UNAVAILABLE -> "Datenquelle nicht verfügbar"
        TrainingPlanEvidenceStatus.READ_FAILED -> "Auslesen fehlgeschlagen"
        TrainingPlanEvidenceStatus.HISTORY_LIMITED -> "Trainingshistorie nur teilweise verfügbar"
        TrainingPlanEvidenceStatus.NO_RUNS_FOUND -> "Keine Läufe im abgerufenen Zeitraum gefunden"
        TrainingPlanEvidenceStatus.INSUFFICIENT_PERSONAL_BASELINE -> "Laufdaten vorhanden, persönliche Vergleichsbasis unzureichend"
        TrainingPlanEvidenceStatus.PERSONAL_BASELINE_AVAILABLE -> "Persönliche Pace-Vergleichsbasis verfügbar"
    }

    private fun TrainingHistoryAccess.toReportLabel(): String = when (this) {
        TrainingHistoryAccess.NOT_CHECKED -> "noch nicht geprüft"
        TrainingHistoryAccess.AVAILABLE -> "verfügbar"
        TrainingHistoryAccess.PERMISSION_NEEDED -> "Freigabe erforderlich"
        TrainingHistoryAccess.UNAVAILABLE -> "Quelle nicht verfügbar"
        TrainingHistoryAccess.FAILED -> "Lesefehler"
    }

    private fun TrainingPlanEvidence.noRunDataLabel(): String = when (status) {
        TrainingPlanEvidenceStatus.ACCESS_REQUIRED -> "Keine lokal gespeicherten Läufe; tatsächliche Aktivität wegen fehlendem Zugriff unbekannt"
        TrainingPlanEvidenceStatus.SOURCE_UNAVAILABLE -> "Keine lokal gespeicherten Läufe; Datenquelle nicht verfügbar"
        TrainingPlanEvidenceStatus.READ_FAILED -> "Keine lokal gespeicherten Läufe; Historie konnte nicht gelesen werden"
        TrainingPlanEvidenceStatus.NO_RUNS_FOUND -> "Keine Läufe im abgerufenen Zeitraum gefunden"
        TrainingPlanEvidenceStatus.HISTORY_LIMITED -> "Keine lokal gespeicherten Läufe; Historie nur teilweise verfügbar"
        TrainingPlanEvidenceStatus.CHECK_REQUIRED -> "Keine lokal gespeicherten Läufe; Zugriff noch nicht geprüft"
        TrainingPlanEvidenceStatus.INSUFFICIENT_PERSONAL_BASELINE -> "Laufdaten vorhanden, persönliche Vergleichsbasis unzureichend"
        TrainingPlanEvidenceStatus.PERSONAL_BASELINE_AVAILABLE -> "Keine Laufdatenbasis erforderlich"
    }

    private fun StringBuilder.appendRun(run: PlannedRun, date: String) {
        appendLine("- $date · ${run.title}${if (run.optional) " (Zusatzlauf)" else ""} · ${run.totalMinutes} Min. · Typ ${run.trainingType.toUserLabel()}")
        appendLine("  Ablauf: ${run.steps.joinToString("; ") { "${it.name}: ${it.minutes} Min." }}")
        appendLine("  Vorgabe: Pace ${run.prescription.paceRange?.let { "${it.fasterSecondsPerKm}–${it.slowerSecondsPerKm} s/km" } ?: "nicht vorhanden"}; RPE ${run.prescription.effortMinimum}–${run.prescription.effortMaximum}/10. ${run.prescription.rationale}")
        run.distanceSpikeAssessment?.let { appendLine("  30-Tage-Distanzprüfung: ${it.toReportText()}") }
        run.durationAdjustmentExplanation?.let { appendLine("  Manuelle Zeitänderung: $it") }
    }

    private fun StringBuilder.appendProgress(progress: TrainingProgress?) {
        appendLine("\n## Bisherige Belastung")
        if (progress == null) appendLine("Nicht verfügbar.")
        else {
            appendLine("Läufe letzte 4 Wochen: ${progress.runsLastFourWeeks}; Laufminuten: ${progress.minutesLastFourWeeks}; erfasste Strecke: ${km(progress.distanceMetersLastFourWeeks)} km (fehlende Distanzwerte sind nicht enthalten).")
            appendLine("Längster Lauf: ${progress.longestRunMinutesLastFourWeeks} Min. in 4 Wochen; ${progress.longestRunMinutesLastSixteenWeeks} Min. in 16 Wochen.")
            appendLine("Läufe in der letzten vollständigen Woche: ${progress.runsPreviousWeek}; letzter Lauf: ${progress.lastRunDate ?: "nicht vorhanden"}.")
            appendLine("Wochenwerte:")
            progress.weeklyRunVolumes.forEach { week ->
                appendLine("- Woche ab ${week.weekStartsOn}: ${week.runCount} Läufe, ${week.minutes} Min., ${week.distanceMeters?.let { "${km(it)} km" } ?: "Strecke unvollständig"}.")
            }
        }
    }

    private fun StringBuilder.appendReadiness(
        health: ReadinessHealthSnapshot?,
        assessment: ReadinessAssessment,
        facts: ReadinessFacts?,
        guidance: ReadinessGuidance
    ) {
        appendLine("\n## Erholung und Gesundheitswerte")
        appendLine("Erholungsschätzung: ${assessment.score?.let { "$it/100" } ?: "Noch nicht bewertbar"}; dies ist ein Modellwert, keine Messung.")
        appendLine("Der Erholungswert dient nur zur Orientierung und ändert den Trainingsplan nicht automatisch.")
        appendLine("Teilwerte: Schlaf ${assessment.sleepScore ?: "n. v."}; Herzsignal (${assessment.autonomicSignal ?: "n. v."}) ${assessment.autonomicScore ?: "n. v."}; Laufbelastung ${assessment.trainingLoadScore ?: "n. v."}.")
        appendLine("Tageswerte:")
        if (health == null) appendLine("- Gesundheitsdaten: nicht verfügbar oder noch nicht geladen.")
        else {
            appendMetric("Schlaf", health.sleepMinutes, "Min.")
            appendMetric("Ruhepuls", health.restingHeartRateBpm, "bpm")
            appendMetric("HRV/HFV letzte Nacht", health.hrvLastNightAverageMs, "ms")
            appendMetric("HRV-Status", health.hrvStatus, "")
            appendMetric("Stress", health.averageStressLevel, "/100")
            appendMetric("Schritte", health.steps, "")
            appendHistory("Schlaf", health.sleepHistory.map { it.date }, health.sleepHistory.size)
            appendHistory("Ruhepuls", health.restingHeartRateHistory.map { it.date }, health.restingHeartRateHistory.size)
            appendHistory("HRV/HFV", health.hrvHistory.map { it.date }, health.hrvHistory.size)
            appendHistory("Stress", health.stressHistory.map { it.date }, health.stressHistory.size)
            appendHistory("Schritte", health.stepsHistory.map { it.date }, health.stepsHistory.size)
            appendDailyValues("Schlafverlauf", health.sleepHistory, "Min.")
            appendDailyValues("Ruhepulsverlauf", health.restingHeartRateHistory, "bpm")
            appendDailyValues("HRV/HFV-Verlauf", health.hrvHistory, "ms")
            appendDailyValues("Stressverlauf", health.stressHistory, "/100")
            appendDailyValues("Schrittverlauf", health.stepsHistory, "Schritte")
        }
        appendLine("Erholungs-Einordnung: ${guidance.kind.toUserLabel()} (${guidance.completedRuns} von ${guidance.plannedRuns} geplanten Läufen).")
        appendLine("Aktivität: ${facts?.let { "${it.runsThisWeek}/${it.plannedRunsThisWeek} Läufe diese Woche; ${it.minutesSinceLastRun?.let(::durationHours) ?: "Zeit seit letztem Lauf n. v."}; ${km(it.distanceThisWeekMeters)} km" } ?: "nicht verfügbar"}.")
    }

    private fun StringBuilder.appendDailyValues(label: String, values: List<de.run.app.domain.model.DailyHealthValue>, unit: String) {
        appendLine("$label (konkrete gespeicherte Messwerte):")
        if (values.isEmpty()) appendLine("- keine gespeicherten Werte")
        else values.sortedBy { it.date }.forEach { appendLine("- ${it.date}: ${it.value} $unit") }
    }

    private fun StringBuilder.appendMetric(label: String, metric: HealthMetric<*>, unit: String) {
        val value = if (metric.availability == de.run.app.domain.model.HealthMetricAvailability.AVAILABLE) {
            "${metric.value ?: "Wert fehlt"}${if (unit.isBlank()) "" else " $unit"}"
        } else metric.availability.toUserLabel()
        appendLine("- $label: $value · Quelle: ${metric.sourceName ?: "nicht angegeben"}.")
    }

    private fun StringBuilder.appendHistory(label: String, dates: List<LocalDate>, count: Int) {
        val span = dates.minOrNull()?.let { start -> dates.maxOrNull()?.let { "$start bis $it" } }
        appendLine("- $label-Verlauf: $count gespeicherte Werte${span?.let { " ($it)" } ?: ""}.")
    }

    private fun StringBuilder.appendForecast(forecast: HalfMarathonForecast?) {
        appendLine("\n## Zielprognose")
        if (forecast == null) appendLine("Keine aktive Prognose verfügbar (z. B. kein aktives Halbmarathonziel).")
        else {
            appendLine("Einschätzung: ${forecast.outlookSummary} · ${forecast.targetOutlook.toUserLabel()} · Sicherheit ${forecast.confidence?.toUserLabel() ?: "nicht ausreichend"}.")
            appendLine("Zeitspanne: ${forecast.estimatedMinutes?.let { "${formatTime(it.first)}–${formatTime(it.last)} Min." } ?: "keine belastbare Zeitprognose"}.")
            appendLine("Datenqualität: ${forecast.dataQuality}; Leistungsnachweise: ${forecast.evidenceRuns}.")
            appendLine("Letzte 4 Wochen: ${forecast.recentRunCount} Läufe / ${km(forecast.recentDistanceKilometers * 1_000)} km; vorheriger Zeitraum: ${forecast.previousRunCount} / ${km(forecast.previousDistanceKilometers * 1_000)} km.")
            appendLine("Längster Lauf: ${km(forecast.recentLongestRunKilometers * 1_000)} km aktuell; ${km(forecast.previousLongestRunKilometers * 1_000)} km zuvor.")
            appendLine("Begründung: ${forecast.explanation}")
            appendLine("Empfehlung: ${forecast.recommendation}")
        }
    }

    private fun StringBuilder.appendFeedback(feedback: TrainingFeedback?) {
        appendLine("\n## Letztes Trainingsfeedback")
        if (feedback == null) appendLine("Noch kein Feedback verfügbar.")
        else {
            appendLine("Bewertung: ${feedback.status.toUserLabel()} · ${feedback.shortSummary}")
            appendLine("Zielbezug: ${feedback.goalSummary}")
            appendLine("Warum: ${feedback.reasons.joinToString(" | ").ifBlank { "keine Einzelbegründung" }}")
            appendLine("Nächster Schritt: ${feedback.nextAction}")
            appendLine("Wochenfokus: ${feedback.weeklyFocus}")
            feedback.planChangeSummary?.let { appendLine("Planänderung: $it") }
        }
    }

    private fun StringBuilder.appendWorkouts(workouts: List<Workout>) {
        val runs = workouts.filter(Workout::isRunning).groupBy(Workout::sourceId)
            .values.map { versions -> versions.maxBy(Workout::endedAtMillis) }
            .sortedByDescending(Workout::startedAtMillis)
        appendLine("\n## Gespeicherte Läufe und Rückmeldungen (${runs.size} Läufe)")
        runs.forEach { workout ->
                val date = Instant.ofEpochMilli(workout.startedAtMillis).atZone(ZoneId.systemDefault())
                val distance = workout.distanceMeters?.takeIf { it.isFinite() && it > 0 }?.let { "${km(it)} km" } ?: "Strecke nicht verfügbar"
                val minutes = workout.durationSeconds.takeIf { it > 0 }?.div(60)?.let { "$it Min." } ?: "Dauer nicht verfügbar"
                appendLine("- ${DATE_TIME.format(date)} · ${workout.title} · $distance · $minutes · Pace ${workout.paceSecondsPerKilometer?.let(::pace) ?: "n. v."} · Puls ${workout.averageHeartRateBpm?.let { "$it bpm" } ?: "n. v."} · Quelle ${workout.sourceName}.")
                workout.feedback?.let { appendLine("  Selbsteinschätzung: Anstrengung ${it.perceivedEffort ?: "nicht angegeben"}/10; Beschwerden ${it.runningAffectedBySymptoms?.toUserLabel() ?: "nicht angegeben"}.") }
                workout.planMatch?.let { appendLine("  Planabgleich: ${it.type.toUserLabel()}; Bewertung ${it.assessment?.toUserLabel() ?: "nicht bewertbar"}; geplant ${it.plannedDurationMinutes?.let { value -> "$value Min." } ?: "nicht vorhanden"}; ${it.rationale ?: "keine zusätzliche Begründung"}.") }
            }
    }

    private fun StringBuilder.appendDebugScenarios(today: LocalDate, isDebugBuild: Boolean) {
        if (!isDebugBuild) return
        appendLine("\n## Debug-Prüffälle")
        appendLine("Die folgenden Fälle laufen durch dieselben Distanz- und Zeitbegrenzungsregeln wie LAP. Sie prüfen wichtige Grenzen, nicht jede denkbare Datenkombination.")
        scenarioRunner.run(today).forEach { result ->
            appendLine("- ${if (result.passed) "BESTANDEN" else "FEHLER"}: ${result.name} · erwartet ${result.expected}, erhalten ${result.actual}.")
        }
    }

    private fun DistanceSpikeAssessment.toReportText(): String = when (level) {
        DistanceSpikeLevel.NOT_ASSESSABLE -> "nicht beurteilbar: ${unavailableReason ?: "Daten fehlen"}"
        DistanceSpikeLevel.NO_WARNING -> "keine Warnung allein aus dieser Regel (bis 10 %). ${increaseMaximumPercent ?: "n. v."} % Veränderung"
        DistanceSpikeLevel.NOTICE -> "Hinweis (>10 % bis 30 %); Veränderung ${increaseMinimumPercent ?: "n. v."}–${increaseMaximumPercent ?: "n. v."} %"
        DistanceSpikeLevel.WARNING -> "deutliche Warnung (>30 % bis 100 %); Veränderung ${increaseMinimumPercent ?: "n. v."}–${increaseMaximumPercent ?: "n. v."} %"
        DistanceSpikeLevel.STRONG_WARNING -> "starke Warnung (>100 %); Veränderung ${increaseMaximumPercent ?: "n. v."} %"
    } + "; Referenz ${referenceDistanceMeters?.let { "${km(it)} km am $referenceDate" } ?: "fehlt"}" + when {
        isEstimate -> "; geplante Distanz ist eine Schätzung"
        level == DistanceSpikeLevel.NOT_ASSESSABLE -> "; geplante Distanz ist nicht gemessen"
        else -> "; Distanz dieses absolvierten Laufs ist gemessen"
    }

    private fun km(meters: Double): String = "%.1f".format(Locale.GERMANY, meters / 1_000.0)
    private fun yesNo(value: Boolean): String = if (value) "ja" else "nein"
    private fun durationHours(minutes: Long): String = "${minutes / 60} Std. ${minutes % 60} Min. seit letztem Lauf"
    private fun pace(secondsPerKm: Long): String = "%d:%02d min/km".format(Locale.GERMANY, secondsPerKm / 60, secondsPerKm % 60)
    private fun formatTime(minutes: Int): String = "%d:%02d".format(Locale.GERMANY, minutes / 60, minutes % 60)

    private companion object {
        val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.GERMANY)
    }
}

private fun AppTheme.toUserLabel(): String = when (this) {
    AppTheme.OLIVE -> "Olive"
    AppTheme.DARK -> "Dunkel"
    AppTheme.LIGHT -> "Hell"
}

private fun de.run.app.domain.model.PlanStatus.toUserLabel(): String = when (this) {
    de.run.app.domain.model.PlanStatus.READY -> "Plan bereit"
    de.run.app.domain.model.PlanStatus.EVENT_TOO_CLOSE -> "Wenig Vorbereitungszeit"
    de.run.app.domain.model.PlanStatus.MAINTAINING_FORM -> "Form halten"
    de.run.app.domain.model.PlanStatus.PAUSED -> "Pausiert"
    de.run.app.domain.model.PlanStatus.GOAL_NEEDED -> "Ziel benötigt"
    de.run.app.domain.model.PlanStatus.GOAL_COMPLETED -> "Ziel abgeschlossen"
}

private fun de.run.app.domain.model.PlanAdjustment.toUserLabel(): String = when (this) {
    de.run.app.domain.model.PlanAdjustment.NO_RUN_DATA -> "Keine Laufdaten"
    de.run.app.domain.model.PlanAdjustment.CONTINUE -> "Aufbau fortsetzen"
    de.run.app.domain.model.PlanAdjustment.REPEAT_WEEK -> "Aufbau wiederholen"
    de.run.app.domain.model.PlanAdjustment.RETURN_GENTLY -> "Vorsichtig zurückkehren"
    de.run.app.domain.model.PlanAdjustment.REPEAT_AFTER_HIGH_EFFORT -> "Nach hoher Anstrengung wiederholen"
    de.run.app.domain.model.PlanAdjustment.REVIEW_REPORTED_SYMPTOMS -> "Gemeldete Beschwerden berücksichtigen"
    de.run.app.domain.model.PlanAdjustment.RECOVER_AFTER_HIGH_LOAD -> "Nach hoher Belastung erholen"
    de.run.app.domain.model.PlanAdjustment.HOLD_AFTER_SLOWING -> "Aufbau wegen langsamerem Tempo halten"
}

private fun de.run.app.domain.model.TrainingPlanMode.toUserLabel(): String = when (this) {
    de.run.app.domain.model.TrainingPlanMode.RACE -> "Zielwettkampf vorbereiten"
    de.run.app.domain.model.TrainingPlanMode.MAINTAIN_FORM -> "Form halten"
    de.run.app.domain.model.TrainingPlanMode.PAUSED -> "Pausiert"
}

private fun de.run.app.domain.model.TrainingType.toUserLabel(): String = when (this) {
    de.run.app.domain.model.TrainingType.EASY -> "Lockerer Lauf"
    de.run.app.domain.model.TrainingType.LONG_EASY -> "Langer ruhiger Lauf"
    de.run.app.domain.model.TrainingType.RECOVERY -> "Erholungslauf"
}

private fun de.run.app.domain.model.RaceGoal.toUserLabel(): String = when (this) {
    de.run.app.domain.model.RaceGoal.ARRIVE -> "Ankommen"
    de.run.app.domain.model.RaceGoal.RUN_THROUGH -> "Durchlaufen"
    de.run.app.domain.model.RaceGoal.TARGET_TIME -> "Zielzeit"
}

private fun de.run.app.domain.model.HealthMetricAvailability.toUserLabel(): String = when (this) {
    de.run.app.domain.model.HealthMetricAvailability.AVAILABLE -> "vorhanden"
    de.run.app.domain.model.HealthMetricAvailability.NO_DATA -> "keine Messdaten"
    de.run.app.domain.model.HealthMetricAvailability.PERMISSION_NEEDED -> "Freigabe fehlt"
    de.run.app.domain.model.HealthMetricAvailability.UNAVAILABLE -> "nicht verfügbar"
    de.run.app.domain.model.HealthMetricAvailability.FAILED -> "konnte nicht gelesen werden"
}

private fun de.run.app.domain.model.ReadinessGuidanceKind.toUserLabel(): String = when (this) {
    de.run.app.domain.model.ReadinessGuidanceKind.LOADING -> "Wird geladen"
    de.run.app.domain.model.ReadinessGuidanceKind.ACCESS_NEEDED -> "Laufdatenfreigabe fehlt"
    de.run.app.domain.model.ReadinessGuidanceKind.UNAVAILABLE -> "Laufdaten nicht verfügbar"
    de.run.app.domain.model.ReadinessGuidanceKind.FAILED -> "Laufdaten konnten nicht gelesen werden"
    de.run.app.domain.model.ReadinessGuidanceKind.NO_RUNS -> "Noch kein Lauf gespeichert"
    de.run.app.domain.model.ReadinessGuidanceKind.NO_PLAN -> "Kein aktueller Wochenplan"
    de.run.app.domain.model.ReadinessGuidanceKind.WEEK_COMPLETE -> "Wochenläufe abgeschlossen"
    de.run.app.domain.model.ReadinessGuidanceKind.FOLLOW_PLAN -> "Am Wochenplan orientieren"
}

private fun de.run.app.domain.GoalOutlook.toUserLabel(): String = when (this) {
    de.run.app.domain.GoalOutlook.LIKELY -> "Wahrscheinlich machbar"
    de.run.app.domain.GoalOutlook.POSSIBLE -> "Möglich, weiterer Aufbau nötig"
    de.run.app.domain.GoalOutlook.UNLIKELY -> "Aktuell eher nicht machbar"
    de.run.app.domain.GoalOutlook.NOT_ENOUGH_DATA -> "Zu wenig Daten"
}

private fun de.run.app.domain.ForecastConfidence.toUserLabel(): String = when (this) {
    de.run.app.domain.ForecastConfidence.TRAINING_ORIENTATION -> "nur Trainingsorientierung"
    de.run.app.domain.ForecastConfidence.ROUGH -> "grobe Schätzung"
    de.run.app.domain.ForecastConfidence.SUPPORTED -> "durch passende Läufe gestützt"
}

private fun WorkoutPlanStatus.toUserLabel(): String = when (this) {
    WorkoutPlanStatus.WELL_MATCHED -> "Passt zum Plan"
    WorkoutPlanStatus.SOME_DEVIATION -> "Weicht teilweise ab"
    WorkoutPlanStatus.HIGHER_LOAD -> "Höhere Belastung als geplant"
    WorkoutPlanStatus.EXTRA_RUN -> "Zusatzlauf"
    WorkoutPlanStatus.NOT_ASSESSABLE -> "Nicht bewertbar"
}

private fun de.run.app.domain.model.PlannedRunMatchType.toUserLabel(): String = when (this) {
    de.run.app.domain.model.PlannedRunMatchType.SCHEDULED -> "Geplante Einheit"
    de.run.app.domain.model.PlannedRunMatchType.EXTRA -> "Zusatzlauf"
    de.run.app.domain.model.PlannedRunMatchType.AMBIGUOUS -> "Nicht sicher zugeordnet"
}

private fun de.run.app.domain.model.PlannedRunAssessment.toUserLabel(): String = when (this) {
    de.run.app.domain.model.PlannedRunAssessment.WELL_MATCHED -> "Passt zum Plan"
    de.run.app.domain.model.PlannedRunAssessment.SOME_DEVIATION -> "Teilweise abweichend"
    de.run.app.domain.model.PlannedRunAssessment.HIGHER_LOAD -> "Deutlich höhere Belastung"
    de.run.app.domain.model.PlannedRunAssessment.NOT_ASSESSABLE -> "Nicht bewertbar"
}

private fun de.run.app.domain.model.RunningSymptomFeedback.toUserLabel(): String = when (this) {
    de.run.app.domain.model.RunningSymptomFeedback.NO -> "Nein"
    de.run.app.domain.model.RunningSymptomFeedback.YES -> "Ja"
    de.run.app.domain.model.RunningSymptomFeedback.UNSURE -> "Unsicher"
}
