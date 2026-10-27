# Qualität

Ein Feature ist erst fertig, wenn Anforderung und Architektur eingehalten werden, Bestandsschutz nachgewiesen ist, alle relevanten Lade-, Leer-, Fehler- und Berechtigungsfälle abgedeckt sind, Barrierefreiheit berücksichtigt wird, fokussierte Unit-, Integrations- und UI-Tests existieren, statische Analyse und Build erfolgreich sind, keine unnötigen Abhängigkeiten eingeführt wurden und deutsche UI-Texte verständlich sind.

## Versionsstrategie

- Stabile Releases bevorzugen.
- Alpha-, Beta- und RC-Abhängigkeiten nur verwenden, wenn benötigte stabile Funktionalität fehlt und die Entscheidung dokumentiert ist.
- Vor einem größeren Versionssprung Breaking Changes prüfen, Tests ausführen, Room-Migrationen verifizieren und Garmin/Health-Connect-Verhalten erneut prüfen.
- WorkManager ist als stabile Jetpack-Abhängigkeit aufgenommen, damit freigegebene Health-Connect-Läufe auch bei geschlossener App regelmäßig eingelesen werden können.

## Medizinische Grenzen

Trainingshinweise sind allgemeine Fitnessplanung und keine Diagnose oder Garantie zur Verletzungsvermeidung.

## Emulator-Demodaten

Die synthetischen Garmin-Läufe sowie Schlaf-, Ruhepuls-, HRV-, Stress- und Schrittwerte liegen nur unter `app/src/debug/assets/run_demo_data.json`. Der Debug-Schalter blendet sie als Demodaten samt künstlicher persönlicher Vergleichsreihe ein; echte Freigaben und Health-Connect-Einträge werden dadurch nicht verändert. Die Asset-Datei ist nicht Teil des Release-Builds.

## Sicherungen

Der lokale Export und Import enthält nur LAP-eigene Daten. Änderungen an den exportierten Datenformaten benötigen eine explizite Versionsnummer und Tests für das aktuelle sowie unterstützte ältere Format. Ein Import prüft Datei, Version und Authentisierung, bevor vorhandene Daten verändert werden.

Das LAP-Auswertungspaket ist ein separates, nicht importierbares ZIP-Archiv mit thematisch sortierten Markdown-Berichten. Es kann persönliche Gesundheits- und Trainingswerte enthalten und wird nur nach ausdrücklicher Nutzeraktion über den Android-Dokumentdialog an den gewählten Ort geschrieben. Vorschau und App-Nutzung schreiben keine Dateien. Es wird nicht automatisch gespeichert oder übertragen.
