package de.run.app.presentation.plan

import android.Manifest
import android.app.Instrumentation
import android.content.Context
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.SystemClock
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import de.run.app.domain.model.PlannedRun
import de.run.app.domain.model.RunStep
import de.run.app.domain.model.AppTheme
import de.run.app.presentation.theme.RunTheme
import de.run.app.domain.model.GarminDataFieldState
import de.run.app.domain.model.GarminLiveDiagnostics
import de.run.app.domain.model.GarminReceiverState
import de.run.app.domain.model.GarminSdkState
import de.run.app.domain.model.GarminWatchState
import de.run.app.BuildConfig
import de.run.app.data.audio.LiveCoachForegroundService
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LiveRunCoachScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun keepsTestToolsOutOfTheMainRunScreenUntilExpanded() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        LiveCoachForegroundService.setAudioAnnouncementsEnabled(context, true)
        compose.setContent {
            RunTheme(AppTheme.OLIVE) {
                LiveRunCoachScreen(
                    PlannedRun("Lockerer Lauf", false, listOf(RunStep("Locker laufen", 30))),
                    onBack = {}
                )
            }
        }

        compose.onNodeWithTag("live-run-coach-screen").assertIsDisplayed()
        compose.onNodeWithText("Lockerer Lauf").assertIsDisplayed()
        compose.onNodeWithText("Audioansagen").assertIsDisplayed()
        compose.onNodeWithTag("live-coach-audio-toggle").assertIsOn().performClick()
        compose.onNodeWithTag("live-coach-audio-toggle").assertIsOff()
        assertTrue(!LiveCoachForegroundService.audioAnnouncementsEnabled(context))
        compose.onNodeWithText("Aus").assertIsDisplayed()
        compose.onNodeWithTag("live-coach-audio-toggle").performClick().assertIsOn()
        assertTrue(LiveCoachForegroundService.audioAnnouncementsEnabled(context))
        compose.onNodeWithText("Aktiv").assertIsDisplayed()
        compose.onNodeWithText("Lauf starten").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Emulator-Testwerkzeuge anzeigen").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("MP3-Ansagen testen").assertCountEquals(0)
        compose.onNodeWithText("Emulator-Testwerkzeuge anzeigen").performClick()
        compose.onNodeWithTag("live-coach-fast").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Nur für Entwicklung und Emulator-Tests").assertIsDisplayed()
    }

    @Test
    fun garminModeShowsNoSyntheticMetricsBeforeAWatchPacketArrives() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.POST_NOTIFICATIONS
        )
        compose.setContent {
            RunTheme(AppTheme.OLIVE) {
                LiveRunCoachScreen(
                    PlannedRun("Lockerer Lauf", false, listOf(RunStep("Locker laufen", 30))),
                    onBack = {}
                )
            }
        }

        compose.onNodeWithText("Emulator-Testwerkzeuge anzeigen").performScrollTo().performClick()
        compose.onNodeWithText("Garmin-Empfang").performScrollTo().performClick()
        compose.onNodeWithText("Uhr nicht verbunden").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Auf der Uhr: Laufen").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Lauf starten").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Zu LAP zurück").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Uhr öffnen").assertCountEquals(0)
        compose.onNodeWithText("–:––").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("– km").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("– bpm").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("6:15 /km").assertCountEquals(0)
        compose.onAllNodesWithText("142 bpm").assertCountEquals(0)
        compose.onAllNodesWithText("MP3-Ansagen testen").assertCountEquals(0)
    }

    @Test
    fun releaseEntryStartsGarminReceptionWithoutSyntheticValues() {
        var started = false
        compose.setContent {
            RunTheme(AppTheme.OLIVE) {
                LiveRunCoachScreen(
                    run = PlannedRun("Lockerer Lauf", false, listOf(RunStep("Locker laufen", 30))),
                    onBack = {},
                    startGarminOnOpen = true,
                    onStartGarmin = { started = true }
                )
            }
        }

        compose.waitForIdle()
        assertTrue(started)
        compose.onNodeWithText("Uhr nicht verbunden").assertIsDisplayed()
        compose.onNodeWithText("Verbindungsdetails").assertIsDisplayed()
        compose.onAllNodesWithText("Transportmodus").assertCountEquals(0)
        compose.onNodeWithText("Verbindungsdetails").performClick()
        compose.onNodeWithText("Transportmodus").assertIsDisplayed()
        compose.onNodeWithText("Warte auf deine Uhr.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("–:––").assertIsDisplayed()
        compose.onNodeWithText("– km").assertIsDisplayed()
        compose.onNodeWithText("– bpm").assertIsDisplayed()
        compose.onNodeWithText("Erneut prüfen").performScrollTo().assertIsDisplayed()
        if (BuildConfig.DEBUG) compose.onNodeWithText("Emulator-Testwerkzeuge anzeigen").performScrollTo().assertIsDisplayed()
        else compose.onAllNodesWithText("Emulator-Testwerkzeuge anzeigen").assertCountEquals(0)
    }

    @Test
    fun diagnosticsSeparateSdkFieldReceiverAndIncomingPingStates() {
        compose.setContent {
            RunTheme(AppTheme.OLIVE) {
                GarminLiveDiagnosticsPanel(
                    GarminLiveDiagnostics(
                        transport = de.run.app.domain.model.GarminTransportMode.WIRELESS,
                        connectMobile = de.run.app.domain.model.GarminConnectMobileState.READY,
                        knownDeviceName = "vívoactive 5",
                        sdk = GarminSdkState.READY,
                        watch = GarminWatchState.CONNECTED,
                        dataField = GarminDataFieldState.FOUND,
                        receiver = GarminReceiverState.REGISTERED,
                        incomingMessages = 1,
                        validPings = 1
                    ),
                    expanded = true
                )
            }
        }

        compose.onNodeWithText("Connect-IQ-SDK").assertIsDisplayed()
        compose.onNodeWithText("Transportmodus").assertIsDisplayed()
        compose.onNodeWithText("WIRELESS · Garmin Connect").assertIsDisplayed()
        compose.onNodeWithText("Garmin Connect Mobile").assertIsDisplayed()
        compose.onNodeWithText("Erreichbar").assertIsDisplayed()
        compose.onNodeWithText("Erkanntes Garmin-Gerät").assertIsDisplayed()
        compose.onNodeWithText("vívoactive 5").assertIsDisplayed()
        compose.onNodeWithText("Bereit").assertIsDisplayed()
        compose.onNodeWithText("LAP-Live-Datenfeld").assertIsDisplayed()
        compose.onNodeWithText("Erkannt").assertIsDisplayed()
        compose.onNodeWithText("Nachrichtenempfänger").assertIsDisplayed()
        compose.onNodeWithText("Aufruf erfolgreich").assertIsDisplayed()
        compose.onNodeWithText("Eingegangene Garmin-Nachrichten").assertIsDisplayed()
        compose.onNodeWithText("Gültige PINGs").assertIsDisplayed()
        compose.onNodeWithText("Bereit für genau einen PING. Erst jetzt LAP Live auf der Uhr aktivieren. Empfang gilt erst mit PING-Zähler 1 als bestätigt.").assertIsDisplayed()
    }

    @Test
    fun diagnosticsTellUserNotToStartWhenReceiverIsNotRegistered() {
        compose.setContent {
            RunTheme(AppTheme.OLIVE) {
                GarminLiveDiagnosticsPanel(GarminLiveDiagnostics(), expanded = true)
            }
        }

        compose.onNodeWithText("PING-Test noch nicht starten: Für die echte Uhr muss WIRELESS aktiv sein.").assertIsDisplayed()
    }

    @Test
    fun diagnosticsDistinguishSentTargetFromWatchAcknowledgement() {
        compose.setContent {
            RunTheme(AppTheme.OLIVE) {
                GarminLiveDiagnosticsPanel(
                    GarminLiveDiagnostics(
                        targetTrainingId = "lap-run-test",
                        targetTransfer = de.run.app.domain.model.GarminTargetTransferState.ACKNOWLEDGED
                    ),
                    expanded = true
                )
            }
        }

        compose.onNodeWithText("LAP-Trainingsziel").assertIsDisplayed()
        compose.onNodeWithText("Von der Uhr übernommen").assertIsDisplayed()
    }

    @Test
    fun capturesLiveCoachDeviationPopupWithBlur() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        LiveCoachForegroundService.setAudioAnnouncementsEnabled(context, true)
        compose.setContent {
            RunTheme(AppTheme.OLIVE) {
                LiveRunCoachScreen(
                    PlannedRun("Lockerer Lauf", false, listOf(RunStep("Locker laufen", 30))),
                    onBack = {}
                )
            }
        }

        compose.onNodeWithText("Bereit für deinen Lauf.").assertIsDisplayed()
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        SystemClock.sleep(300)
        saveCoachScreenshot(instrumentation, context, "lap-live-coach-card-layered-20261024.png")

        compose.onNodeWithText("Emulator-Testwerkzeuge anzeigen").performScrollTo().performClick()
        compose.onNodeWithText("Zu schnell").performScrollTo().performClick()
        compose.onNodeWithText("Du bist schneller als der geplante Tempobereich. Laufe etwas ruhiger.").assertIsDisplayed()
        compose.waitForIdle()

        saveCoachScreenshot(instrumentation, context, "lap-coach-deviation-popup-artwork-final-20261024.png")
    }

    @Test
    fun deviationPopupStaysHiddenWhenAudioHintsAreDisabled() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        LiveCoachForegroundService.setAudioAnnouncementsEnabled(context, false)
        compose.setContent {
            RunTheme(AppTheme.OLIVE) {
                LiveRunCoachScreen(
                    PlannedRun("Lockerer Lauf", false, listOf(RunStep("Locker laufen", 30))),
                    onBack = {}
                )
            }
        }

        compose.onNodeWithText("Emulator-Testwerkzeuge anzeigen").performScrollTo().performClick()
        compose.onNodeWithText("Zu schnell").performScrollTo().performClick()
        compose.onAllNodesWithTag("coach-deviation-notice").assertCountEquals(0)
    }

    @Test
    fun releaseBuildDoesNotShowEmulatorOrAudioTestControls() {
        if (BuildConfig.DEBUG) return

        compose.setContent {
            RunTheme(AppTheme.OLIVE) {
                LiveRunCoachScreen(
                    PlannedRun("Lockerer Lauf", false, listOf(RunStep("Locker laufen", 30))),
                    onBack = {}
                )
            }
        }

        compose.onNodeWithText("Uhr nicht verbunden").assertIsDisplayed()
        compose.onAllNodesWithText("Emulator-Testwerkzeuge anzeigen").assertCountEquals(0)
        compose.onAllNodesWithText("Testlauf starten").assertCountEquals(0)
        compose.onAllNodesWithText("MP3-Ansagen testen").assertCountEquals(0)
        compose.onAllNodesWithText("Verbindungsprüfung").assertCountEquals(0)
        compose.onAllNodesWithText("SDK").assertCountEquals(0)
    }
}

private fun saveCoachScreenshot(instrumentation: Instrumentation, context: Context, name: String) {
    val image = instrumentation.uiAutomation.takeScreenshot()
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "${android.os.Environment.DIRECTORY_PICTURES}/LAP")
    }
    val screenshotUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    assertTrue(screenshotUri != null)
    val output = requireNotNull(context.contentResolver.openOutputStream(screenshotUri!!))
    output.use { stream -> assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, stream)) }
}
