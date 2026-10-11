# LAP lokal für eine Veröffentlichung vorbereiten

## Signierdaten

Die Debug-App verwendet die Paketkennung `de.run.app.debug`. Sie kann dadurch
für persönliche Tests neben einer vorhandenen LAP-Installation liegen, ohne
diese oder deren lokale Daten zu ersetzen. Der Release-Build behält
`de.run.app`.

Eine lokale Signierdatei wurde für dieses Projekt nicht bereitgestellt. Es wurde kein Schlüssel erzeugt. Ohne vorhandene Signierdatei ist kein Update-Build zur Installation über eine bestehende LAP-Version möglich.

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
5. Installations- und Update-Verhalten auf einem separaten, datenfreien Testgerät mit synthetischen Daten prüfen.

Der Release-Build bricht vor dem Erzeugen des Artefakts mit einer verständlichen Meldung ab, wenn Signierwerte fehlen oder die angegebene Datei nicht vorhanden ist. `verifyReleaseSigning` zeigt niemals Passwortwerte oder den Signaturschlüssel an. Die Versionsnummer und der `applicationId` bleiben bis zu einer ausdrücklich geplanten Veröffentlichung unverändert.

## Noch offen

Ein echter Signatur-, Installations- und Update-Test kann erst erfolgen, wenn die zuständige Person die bestehende Signierdatei und deren Zugangsdaten lokal bereitgestellt hat. Dieses Dokument enthält keine Zugangsdaten.
