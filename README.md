# LAP.

Eine persönliche Lauf- und Trainings-App für Android. Das voreingestellte Beispielziel ist der Halbmarathon am 21. März 2027; Wettkampftag und Strecke lassen sich beim Einrichten ändern.

## Daten

Garmin Connect kann ausgewählte Lauf- und Gesundheitsdaten mit Health Connect teilen. LAP liest freigegebene Einträge lokal ein und speichert den für den Trainingsverlauf benötigten Stand auf dem Gerät. Trainingsplan und eigene Notizen bleiben von importierten Garmin-Daten unterscheidbar.

**So zeichnest du einen Lauf auf:** Starte ihn auf deiner Garmin-Uhr über „Aktivitäten → Laufen“ und synchronisiere danach mit Garmin Connect. LAP startet keine Aufzeichnung. Die App liest beim Öffnen und auf der Fortschrittsseite automatisch neue verfügbare Läufe ein und wertet sie aus. Mit der zusätzlichen Freigabe für Hintergrundabrufe prüft Android ungefähr alle sechs Stunden neue oder geänderte Läufe der vergangenen 90 Tage, auch wenn LAP geschlossen ist. Ohne diese Freigabe erfolgt der Abruf beim Öffnen der App. Android bestimmt den genauen Zeitpunkt. Die Freigabe für ältere Trainingsdaten kann den Verlauf erweitern; ohne sie sind nur 30 Tage verfügbar.

Der Trainingsvorschlag ist eine konservative, nachvollziehbare Orientierung. Verfügbare Tage sind keine Pflichttermine. Bei wechselnden Diensten gelten nur bekannte Angaben der kommenden sieben Tage; ein unbekannter Tag wird weder als frei noch als Ruhetag behandelt. Ohne bekannte Termine zeigt LAP einen vorläufigen Vorschlag. Verpasste Einheiten werden nicht zusammengedrängt.

Unter „Ich“ kannst du appweit die Farbstile Olive, Dunkel und Hell wählen. Der Kraftbereich zeigt einfache Übungen und deine erledigten Einheiten der letzten vier Wochen.

## Projektaufbau

- `app/`: Android-App
- `docs/`: Produkt-, Architektur-, Daten-, Garmin-, UX- und Qualitätsregeln
- `.agent/PLANS.md`: Arbeitsplan

## Markenassets

Das Laufuhrbild liegt in `app/src/main/res/drawable-nodpi/garmin.png`, das Startvideo in `app/src/main/res/raw/welcome.mp4` und die Inter-Variable-Schrift in `app/src/main/res/font/inter_variable.ttf`. Die Lizenz liegt in `docs/licenses/INTER-OFL.txt`.

## Voraussetzungen

Android Studio mit Android SDK und JDK 21. Garmin-Übertragung zu Health Connect setzt Android 14 oder neuer voraus.
