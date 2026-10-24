# LAP lokal für eine Veröffentlichung vorbereiten

## Signierdaten

Die Debug-App verwendet die Paketkennung `de.run.app.debug`. Sie kann dadurch
für persönliche Tests neben einer vorhandenen LAP-Installation liegen, ohne
diese oder deren lokale Daten zu ersetzen. Der Release-Build behält
`de.run.app`.

Die vorhandene Android-Release-Signierdatei liegt außerhalb des Projekts. Das Passwort wird DPAPI-geschützt außerhalb des Projekts gehalten und nur während eines lokalen Builds in Umgebungsvariablen bereitgestellt. Das Release-Zertifikat wurde gegen die installierte LAP-App auf dem P30 Lite geprüft.

Lege die vorhandene Signierdatei außerhalb des Projektordners ab und setze die folgenden Umgebungsvariablen nur in der lokalen Build-Sitzung:

- `LAP_RELEASE_STORE_FILE`: vollständiger Pfad zur vorhandenen Signierdatei
- `LAP_RELEASE_STORE_PASSWORD`: Passwort der Signierdatei
- `LAP_RELEASE_KEY_ALIAS`: Alias des Schlüssels
- `LAP_RELEASE_KEY_PASSWORD`: Passwort des Schlüssels

Unter PowerShell setzt du sie für das aktuelle Terminal mit `$env:LAP_RELEASE_STORE_FILE = '…'` und entsprechend für die übrigen drei Werte. Keine Passwörter in Projektdateien, Git, Build-Ausgaben oder Screenshots schreiben.

## Vor dem Release

1. `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
2. `./gradlew :app:verifyReleaseSigning`
3. `./gradlew :app:assembleRelease`
4. APK-Signatur mit `apksigner verify --verbose app/build/outputs/apk/release/app-release.apk` prüfen.
5. Vor einer Installation auf dem P30 Lite die ausdrückliche Freigabe einholen. Paketkennung, Zertifikat und Room-Schema vorher gegen die installierte Version prüfen.

Der Release-Build bricht vor dem Erzeugen des Artefakts mit einer verständlichen Meldung ab, wenn Signierwerte fehlen oder die angegebene Datei nicht vorhanden ist. `verifyReleaseSigning` zeigt niemals Passwortwerte oder den Signaturschlüssel an. Die Versionsnummer und der `applicationId` bleiben bis zu einer ausdrücklich geplanten Veröffentlichung unverändert.

## Garmin-Live-Test 2026-10-18

- Das P30 Lite läuft mit `de.run.app` Version 0.3.4 (versionCode 7). Die Installation erfolgte mit `adb install -r` und derselben geprüften Signatur; der App-Datenpfad blieb unverändert.
- Version 0.3.5 (versionCode 8) ergänzt den regulären Start der Garmin-Live-Begleitung, PING-/Messungszähler und verständlichen Empfangsstatus. Der Release-Build ist signiert und geprüft, wartet aber auf die ausdrückliche Installationsfreigabe.
- Das Room-Schema bleibt bei Version 10; der Live-Einstieg benötigt keine Datenbankmigration.
- Das signierte PING-Datenfeld für `vivoactive5` wurde nach `GARMIN/APPS` übertragen und durch Rückkopieren mit SHA-256 verifiziert. Es wird erst nach sicherem Trennen der Uhr aktiv.
- Der PING-Hardwaretest und der Empfang bei gesperrtem Handy sind offen. Es wurde noch kein PING von der Uhr gesendet.

Dieses Dokument enthält keine Passwörter oder Schlüsselmaterial.
## Live-Verbindungsdiagnose 0.3.7

- Version 0.3.7 (versionCode 10) ergänzt die Connect-IQ-Verbindungsdiagnose im regulären LAP-Live-Bildschirm. Der Release-Pfad ist WIRELESS; TETHERED und der leere App-ID-Testlistener bleiben auf Debug/Simulator beschränkt.
- Die Diagnose zeigt Garmin Connect Mobile/SDK, bekanntes Gerät und Verbindungsstatus, Datenfeld/App-ID, lokalen Empfängerstatus sowie Nachrichten- und PING-Zähler. LAP markiert Empfang erst nach einem tatsächlich eingegangenen PING als bestätigt.
- Keine Room-Migration. Unit- und Emulator-UI-Prüfungen wurden offline ausgeführt. Der Release-Build ist für die erneute Signatur-/Paketprüfung vorbereitet; Installation auf P30 oder Garmin und echter PING-Test warten auf erneute ausdrückliche Freigabe.
