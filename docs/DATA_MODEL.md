# Datenmodell

## Domain-Werte

- `Workout`: geplante oder absolvierte Einheit, einschließlich Dauer, Strecke, Aktivitätstyp und Herkunft.
- `Sleep`: Zeitraum und verfügbare Schlafphasen.
- `HeartRateSample`: Zeitpunkt, Schläge pro Minute und Herkunft.
- `DailyActivity`: lokales Datum, Schritte, Distanz und Kalorien, soweit vorhanden.
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
- `Workout`: bei importierten Laufdaten zusätzlich optionale Strecke, durchschnittlicher und höchster Puls. Fehlende Werte bleiben leer und werden nicht geschätzt.
- `WorkoutFeedback`: optionale, lokal gespeicherte Selbsteinschätzung der Anstrengung (0–10), Antwort zu Beschwerden beim Laufen (`Nein`, `Ja`, `Unsicher`) und Erfassungszeit. Sie ist über die stabile Quell-ID dem Lauf zugeordnet. Überspringen speichert einen Erfassungszeitpunkt mit unbekannten Antworten; es erzeugt keine angenommene Beschwerdefreiheit.
- `TrainingDecisionSnapshot`: genau eine aktuelle lokale Zusammenfassung mit Regelversion, Ziel- und Eingaberevision, Auswertungstag, Anzahl/Laufzeit des jüngsten Trainingsstands, nächster Empfehlung und Grundcode. Die Revision ist ein Hash; der Snapshot enthält keine Kopie der Rohaktivitäten. Er wird nach Planberechnungen ersetzt und nicht als zweite Quelle für den Plan benutzt.

## Herkunft und Identität

Importierte Werte tragen Quelle, externe Datensatz-ID und Änderungszeit. App-eigene Plandaten und Notizen sind separat modelliert. Health-Connect-Daten von Garmin Connect und Health Sync bleiben als unterschiedliche Herkunft erkennbar. FIT-Importe erhalten eine deterministische ID aus dem Dateiinhalt, sodass erneuter Import dieselbe lokale Aktivität aktualisiert statt ein Duplikat anzulegen.

## Zeit und Einheiten

Zeitpunkte werden mit Zeitzonenbezug gespeichert; Tagesübersichten verwenden das lokale Datum. Distanzen liegen intern in Metern, Dauer in Sekunden, Herzfrequenz in Schlägen pro Minute. UI formatiert passend für die Nutzerin.

## Änderungen

Room-Schemaänderungen sind additiv und versioniert. Schema 2 hat optionale Pulswerte ergänzt, Schema 3 lokale Kraftübungserledigungen, Schema 4 optionale Lauf-Rückmeldungen und Schema 5 die aktuelle abgeleitete Trainingsentscheidung. Migrationen von Version 1 bis 5 erhalten vorhandene Läufe; alte Rückmeldungen bleiben unbekannt. Vor jeder weiteren Migration muss nachgewiesen sein, dass bestehende Einträge erhalten bleiben. Keine destruktive Schema-Neuerstellung.
