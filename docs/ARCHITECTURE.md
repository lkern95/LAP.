# Architektur

## Grundform

Eine native Android-App mit einer Activity, Kotlin, Jetpack Compose, Material 3, Navigation Compose, ViewModels, StateFlow und Coroutines. Das erste Release bleibt ein einzelnes `app`-Modul; Pakete markieren die Grenzen der Verantwortlichkeiten, ohne unnötige Modulkomplexität einzuführen.

## Abhängigkeitsrichtung

`presentation → application → domain`

`data → domain`

Die Domain kennt weder Android noch Compose, Room oder Health Connect. Präsentationscode enthält Darstellung und UI-Zustand, aber keine Trainingsregeln oder Datenzugriffe.

## Verantwortlichkeiten

- `domain`: Lauf-/Schlaf-/Aktivitätswerte, Trainingsplanregeln, Repository-Verträge und deterministische Auswertungen.
- `application`: Anwendungsfälle und Orchestrierung zwischen Domäne und Repositories.
- `data/local`: Room-Entities, DAOs, Mapper und DataStore-Einstellungen.
- `data/healthconnect`: Health-Connect-Zugriff, Berechtigungen, Paging, Zuordnung externer Datensätze und optionale WorkManager-Hintergrundabfrage. Vorder- und Hintergrund nutzen dasselbe Workout-Repository und denselben eindeutigen Importweg.
- Gesundheitswerte der Heute-Ansicht nutzen ein eigenes Read-only-Repository in `data/healthconnect`; diese Abfrage schreibt keine Schlaf- oder Pulswerte in Room.
- `application`: Anwendungsabläufe und Orchestrierung, sofern ein Ablauf mehrere Domain-Regeln oder Datenquellen koordiniert.
- `presentation`: Compose-Bildschirme, Navigation, ViewModels und deutschsprachige Zustände. ViewModels koordinieren UI-Zustand und rufen Domain-Logik auf; sie enthalten keine Trainingsregeln und keinen direkten Plattform- oder Speicherzugriff.
- Verfügbarkeitsplanung und Erholungsabstände liegen als deterministische Regeln in der Domain. DataStore speichert wiederkehrende Wochentage oder flexible Zeitobergrenzen lokal. Flexible Vorschläge bleiben ohne Kalendertage; die Oberfläche und das ViewModel definieren keine Trainingseinheiten.
- Persönliche Laufeinstellungen und Wettkampfziele sind getrennte Domainmodelle und Repository-Verträge. Die Profileinstellung beschreibt die Verfügbarkeit und bleibt bei Zieländerungen bestehen. Das Ziel-Repository hält Zielhistorie und Wettkampfergebnis. Die Planregeln erzeugen daraus Zieltraining, Formhalten oder Pause.
- Die lokale Zielablage migriert frühere Wettkampfangaben additiv aus den bestehenden DataStore-Einstellungen; Laufhistorie, Gesundheitsdaten und Room-Schema werden dabei nicht verändert.

## Abhängigkeitsinjektion

Konstruktorinjektion und ein kleiner, expliziter App-Container genügen im ersten Release. Kein DI-Framework, solange keine konkrete Komplexität es rechtfertigt.

## Datenfluss

Die App liest freigegebene Daten aus Health Connect über ein dediziertes Repository. Der Datenadapter ordnet sie Domain-Werten zu. Die lokale Ablage ist Quelle für App-Verläufe und Trainingsfortschritt. UI beobachtet Repository-Daten über ViewModels. Darstellungsthemen werden in DataStore als persönliche Einstellung gesichert.

## Zustand

ViewModels stellen explizite Lade-, Inhalts-, Leer-, Fehler- und Berechtigungszustände über StateFlow bereit. Anwendungsregeln bleiben unabhängig von Compose.
