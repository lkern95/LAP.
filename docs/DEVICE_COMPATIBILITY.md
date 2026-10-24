# Gerätekompatibilität

## Mindestversion

LAP unterstützt Android 10 (API 29) und neuer. Gesundheitsdaten sind eine optionale Zusatzfunktion: Für sie braucht das Gerät Google Play-Dienste und die Health-Connect-App beziehungsweise die ab Android 14 eingebaute Systemfunktion. Ohne diese Voraussetzungen bleiben Trainingsplan, Ziele, lokale Angaben und Sicherungen verfügbar.

Garmin Connect gibt seine Daten laut Garmin erst ab Android 14 direkt an Health Connect weiter. Auf Android 10 bis 13 bietet LAP den Garmin-Abgleich und einen lokalen Import von Garmin-Backups oder Aktivitätsdateien an. Vorhandene Health-Connect-Daten bleiben mit ihrer ursprünglichen Quelle erhalten.

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

Die installierte Health-Connect-App unterstützt API 29. Garmin Connects direkte Datenfreigabe bleibt laut Garmin auf Android 14 und neuer beschränkt. Auf diesem Telefon kann LAP Läufe über den Garmin-Abgleich oder den lokalen Import eines Backups beziehungsweise einer Aktivitätsdatei übernehmen.

## Tests und Grenzen

- Der App-Mindestwert ist API 29; `targetSdk` und `compileSdk` bleiben API 36.
- FIT-Dateien werden mit dem offiziellen Garmin FIT SDK 21.218.0 gelesen; Androids Dokumentauswahl gibt LAP nur Zugriff auf die ausgewählte Datei.
- Ein Unit-Test prüft die Garmin-Unterstützungsgrenze bei API 29, 33, 34 und 36.
- Ein Android-UI-Test prüft, dass Onboarding auf älteren Geräten fortgesetzt werden kann und keine nicht verfügbare Garmin-Verbindung verspricht.
- Ein Debug-Build wird auf dem P30 Lite installiert und geöffnet; die Laufzeitprüfungen werden protokolliert, ohne App-Daten zurückzusetzen.
- Der lokale Garmin-Backup- und Aktivitätsdateiimport bleibt der verfügbare Weg, wenn Garmin Connect keine Daten an Health Connect teilt.
- Der FIT-Import wurde mit gültigen und beschädigten synthetischen FIT-Aktivitäten getestet; auf dem P30 muss zusätzlich die tatsächliche Dokumentauswahl und der Import einer echten Garmin-Datei geprüft werden.
- Hersteller, Geräteupdates, Google-Dienste und Health-Connect-Version können sich unterscheiden. Auf neuen Geräten prüft LAP die Datenfreigabe zur Laufzeit und hält den Plan bei fehlender Freigabe verfügbar.
