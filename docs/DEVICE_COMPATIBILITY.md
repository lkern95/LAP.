# Gerätekompatibilität

## Mindestversion

LAP unterstützt Android 10 (API 29) und neuer. Gesundheitsdaten sind eine optionale Zusatzfunktion: Für sie braucht das Gerät Google Play-Dienste und die Health-Connect-App beziehungsweise die ab Android 14 eingebaute Systemfunktion. Ohne diese Voraussetzungen bleiben Trainingsplan, Ziele, lokale Angaben und Sicherungen verfügbar.

Garmin Connect gibt seine Daten laut Garmin erst ab Android 14 direkt an Health Connect weiter. Auf Android 10 bis 13 kann die Drittanbieter-App Health Sync Garmin-Connect-Läufe an Health Connect weitergeben. LAP bietet außerdem einen lokalen FIT-Dateiimport, falls der automatische Weg nicht eingerichtet ist. Der Health-Sync-Weg wurde auf dem P30 Lite noch nicht praktisch bestätigt.

Quellen: [Garmin: Daten mit Health Connect teilen](https://support.garmin.com/en-GB/?faq=JToBEy0jfe6pIygark2Ui5) und [Android: Verfügbarkeit von Health Connect](https://developer.android.com/health-and-fitness/health-connect/availability?hl=de).

## Huawei P30 Lite / EMUI 12

Am 11. Oktober 2026 wurde per ADB auf dem verbundenen Gerät geprüft:

| Angabe | Ergebnis |
| --- | --- |
| Modell | HUAWEI MAR-LX1B |
| EMUI | 12.0.0 |
| Android | 10 (API 29) |
| Google Play Store | Vorhanden |
| Google Play-Dienste | Vorhanden |
| Health Connect | Vorhanden, Version 2026.09.03.00.release |
| Garmin Connect | Vorhanden, Version 5.30.1 |

Die installierte Health-Connect-App unterstützt API 29. Garmin Connects direkte Datenfreigabe bleibt laut Garmin auf Android 14 und neuer beschränkt. Health Sync ist derzeit nicht installiert, daher ist automatische Garmin-Übernahme auf diesem Telefon noch nicht nachgewiesen. Mit eingerichtetem Health Sync oder dem lokalen FIT-Import kann LAP Laufdaten übernehmen.

## Tests und Grenzen

- Der App-Mindestwert ist API 29; `targetSdk` und `compileSdk` bleiben API 36.
- FIT-Dateien werden mit dem offiziellen Garmin FIT SDK 21.218.0 gelesen; Androids Dokumentauswahl gibt LAP nur Zugriff auf die ausgewählte Datei.
- Ein Unit-Test prüft die Garmin-Unterstützungsgrenze bei API 29, 33, 34 und 36.
- Ein Android-UI-Test prüft, dass Onboarding auf älteren Geräten fortgesetzt werden kann und keine nicht verfügbare Garmin-Verbindung verspricht.
- Ein Debug-Build wird auf dem P30 Lite installiert und geöffnet; die Laufzeitprüfungen werden protokolliert, ohne App-Daten zurückzusetzen.
- Die Garmin-Connect-zu-Health-Sync-zu-Health-Connect-Übertragung muss nach Installation und Nutzereinrichtung noch mit einer echten Aktivität geprüft werden. Bis dahin wird sie nicht als erfolgreich bestätigt.
- Der FIT-Import wurde mit gültigen und beschädigten synthetischen FIT-Aktivitäten getestet; auf dem P30 muss zusätzlich die tatsächliche Dokumentauswahl und der Import einer echten Garmin-Datei geprüft werden.
- Hersteller, Geräteupdates, Google-Dienste und Health-Connect-Version können sich unterscheiden. Auf neuen Geräten prüft LAP die Datenfreigabe zur Laufzeit und hält den Plan bei fehlender Freigabe verfügbar.
