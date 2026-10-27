# Datenmodell

## Domain-Werte

- `Workout`: absolvierte Einheit, einschließlich Dauer, Strecke, Aktivitätstyp, Herkunft und verfügbarer Kadenz. Laufplanung und Laufbelastung verwenden ausschließlich Einheiten vom Typ `RUNNING`; andere importierte Aktivitäten bleiben im Verlauf sichtbar.
- `Sleep`: Zeitraum und verfügbare Schlafphasen.
- `HeartRateSample`: Zeitpunkt, Schläge pro Minute und Herkunft.
- `DailyActivity`: lokales Datum, Schritte, Distanz und Kalorien, soweit vorhanden.
- `GarminHealthDay`: lokal gespeicherte optionale Tageswerte von Garmin Connect: Schlafdauer, Ruhepuls, nächtlicher HRV-Durchschnitt mit Garmin-Status/Baseline, Stressdurchschnitt und Schritte. Fehlende Werte werden nicht abgeleitet.
- `BodyMeasurement`: Messzeitpunkt, Messart, Wert und Herkunft.
- Die Heute-Ansicht liest Schlafdauer und Ruhepuls direkt aus Health Connect. Diese Momentaufnahme wird nicht zusätzlich in Room gespeichert; Herkunft und Berechtigungszustand bleiben beim Abruf erhalten.
- `TrainingPlan`: optionale Wettkampfzeit, Status, Wochen und geplante Einheiten. Ein wiederholbarer Plan zum Formhalten hat keinen Wettkampftermin.
- `TrainingProfile` speichert persönliche Laufeinstellungen: Ausgangslage, wiederkehrende Verfügbarkeit oder flexible Zeitobergrenzen, einen optional bevorzugten langen Lauftag sowie den Wunsch, Erholung nach Nachtdienst zu berücksichtigen. Flexible Verfügbarkeit enthält eine übliche Zeitobergrenze und eine optionale Obergrenze für gelegentlich längere Einheiten. Diese Werte beschreiben keine Trainingsvorgaben.
- `TrainingGoal` ist ein eigenständiger, änderbarer Datensatz mit Wettkampftag, frei wählbarer Strecke in Metern, Zielart und Status. Zielverlauf bleibt neben dem persönlichen Profil und der Trainingshistorie erhalten. Wettkampfabschluss und Erreichen der persönlichen Zielzeit sind getrennte Angaben.
- Der bisher im Profil gespeicherte Wettkampftag, die Strecke und Zielart werden beim ersten Lesen additiv in den Zielverlauf übernommen. Die ursprünglichen Einstellungen werden nicht gelöscht; bisherige Läufe und Room-Schema bleiben unberührt.
- Ein importierter Lauf wird als möglicher Zielwettkampf angeboten, wenn er am lokalen Zieldatum begonnen wurde und mindestens 90 % der eingestellten Wettkampfstrecke umfasst. Ein abgelaufenes Zieldatum allein schließt keinen Wettkampf ab.
- Der Planmodus unterscheidet Zieltraining, einen wiederholbaren Vier-Wochen-Plan zum Formhalten und Pause. Ein neues Ziel ersetzt nur den aktiven Zielstatus; absolvierte Läufe und frühere Ziele bleiben erhalten.
- Datumsgebundene Verfügbarkeit unterscheidet `UNKNOWN`, `AVAILABLE` und `UNAVAILABLE`. Nicht eingetragene Termine bleiben `UNKNOWN`; nur eine ausdrückliche Auswahl wird als verfügbare oder nicht verfügbare Zeit gespeichert. Diese kurzlebigen Angaben verbleiben in DataStore, nicht in der Trainingshistorie.
- `TrainingWeek.availability` enthält den Status je Wochentag. `scheduledRuns` enthält nur Vorschläge auf ausdrücklich verfügbarer Zeit; Einheiten ohne bestätigten Termin bleiben als vorläufige Vorschläge erkennbar.
- Im flexiblen Modus bleiben Einheiten ohne festen Tag in `TrainingWeek.runs`; sie werden nicht als tägliche Termine gespeichert. Die Zeitobergrenzen liegen als additive DataStore-Einstellungen im Profil. Bereits gespeicherte Datumsangaben bleiben erhalten, werden aber nicht mehr zur Terminplanung verwendet und nicht in wiederkehrende Verfügbarkeit umgedeutet.
- `ExerciseCompletion`: Planübung, Datum und Erledigungsstatus.
- `Workout`: bei importierten Aktivitäten optionale Strecke, durchschnittlicher und höchster Puls sowie Kadenz. Fehlende Werte bleiben leer und werden nicht geschätzt. Aktivitäten werden deterministisch als Laufen, Gehen, Radfahren, Krafttraining oder Sonstiges typisiert.
- `WorkoutFeedback`: optionale, lokal gespeicherte Selbsteinschätzung der Anstrengung (0–10), Antwort zu Beschwerden beim Laufen (`Nein`, `Ja`, `Unsicher`) und Erfassungszeit. Sie ist über die stabile Quell-ID dem Lauf zugeordnet. Überspringen speichert einen Erfassungszeitpunkt mit unbekannten Antworten; es erzeugt keine angenommene Beschwerdefreiheit.
- `PlannedRun`: konkrete lokale Planeinheit mit stabiler Trainings-ID und Datum für terminierte Einheiten, Trainingstyp, Zeitabschnitten, Sprechtempo/RPE-Vorgabe, begründeter empirischer Pace-Referenz und optionaler Pulszone. Ein persönlicher Pacebereich wird nur aus mindestens fünf vergleichbaren lockeren Läufen mit expliziter RPE 2–4 gebildet. Ohne belastbare persönliche Pulsgrenze bleibt die Pulszone leer.
- `WorkoutPlanMatch`: einmalig gesicherte Zuordnung eines importierten Laufs zu seiner Planeinheit oder als Zusatz-/unklarer Lauf. Der Datensatz bewahrt Plan-ID, Datum, Dauer, vorhandenen Pacebereich und Bewertungsstatus auf, damit spätere Planneuberechnungen die ursprüngliche Vergleichsgrundlage nicht ersetzen. Zusatzläufe erhöhen weiterhin die gemessene Belastung, erfüllen aber keine Planeinheit.
- `TrainingDecisionSnapshot`: genau eine aktuelle lokale Zusammenfassung mit Regelversion, Ziel- und Eingaberevision, Auswertungstag, Anzahl/Laufzeit des jüngsten Trainingsstands, nächster Empfehlung und Grundcode. Die Revision ist ein Hash; der Snapshot enthält keine Kopie der Rohaktivitäten. Er wird nach Planberechnungen ersetzt und nicht als zweite Quelle für den Plan benutzt.

## Herkunft und Identität

Importierte Werte tragen Quelle und externe Datensatz-ID. App-eigene Plandaten und Notizen sind separat modelliert. Health-Connect-Daten von Garmin Connect und Health Sync bleiben als unterschiedliche Herkunft erkennbar. FIT-, GPX- und TCX-Importe erhalten eine deterministische ID aus dem Dateiinhalt. Originaldateien werden unverändert unter einer SHA-256-basierten Kennung im privaten App-Speicher gesichert. Garmin-Connect-Originaldateien verknüpfen Aktivitäts-ID, SHA-256 und kanonische LAP-Aktivität.

## Zeit und Einheiten

Zeitpunkte werden mit Zeitzonenbezug gespeichert; Tagesübersichten verwenden das lokale Datum. Distanzen liegen intern in Metern, Dauer in Sekunden, Herzfrequenz in Schlägen pro Minute. UI formatiert passend für die Nutzerin.

## Änderungen

Room-Schemaänderungen sind additiv und versioniert. Schema 8 ergänzt Aktivitätstyp und Kadenz. Schema 9 ergänzt für Garmin-Dateien die Zuordnung zur kanonischen LAP-Aktivität. Schema 11 speichert die ursprüngliche Plan-Zuordnung und Bewertung importierter Läufe in optionalen Spalten der Workout-Zeile. Schema 12 ergänzt ausschließlich optionale Tagesfelder für HRV, Stress und Schritte. Vorhandene Messwerte und Workout-Zeilen bleiben erhalten.
