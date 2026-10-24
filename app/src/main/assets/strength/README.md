# Läufertraining – JSON v1.1 (PNF und Hüfte)

LAP speichert persönliche Sessionstände getrennt vom Katalog lokal in Room. Das Fortschrittsformat ist dafür auf `2.0.0` erweitert: jede Durchführung hat eine eigene `sessionId`, einen vollständigen Katalog-Snapshot und eindeutige Satzstände. Katalog-IDs bleiben stabil; geänderte Bewegungen erhalten eine neue Übungs-ID. Bereits gespeicherte Sessions behalten ihren damaligen Snapshot.

## Dateien
- `runner_training_catalog_v2.schema.json`: JSON Schema Draft 2020-12 für das Content-Archiv.
- `runner_training_catalog_v2.json`: 12 Übungen, 3 Trainingseinheiten; Texte, Ziele, Dosierungen, Technik, Sicherheitsangaben und Schildkröten-Bild-Briefings.
- `runner_training_progress.schema.json`: Separates JSON-Schema für Nutzerfortschritt, unverändert kompatibel.
- `runner_training_progress.example.json`: Beispiel für Nutzerfortschritt.

## Änderungen gegenüber Version 1.0.0
- Entfernt: `hueftdiagonale_band_ext_abd_aro` (nicht klassisches PNF). **Nicht automatisch Fortschritte auf ein anderes Bewegungsmuster übertragen.** Historische abgeschlossene Einheiten mit alten Content-Versionen aufbewahren.
- Neu: `pnf_sprinter_d1_band`, `miniband_seitschritte`, `glute_bridge`, `adduktoren_rockback_dehnung`.
- `movementType` akzeptiert nun zusätzlich `mobility`.
- `focusAreas` akzeptiert nun `hip_adductors`.
- `runnerGoals` um `gait_coordination`, `hip_abductor_strength`, `hip_extension_strength`, `adductor_mobility` erweitert.
- Neues optionales Feld `exercise.pnfPattern` mit strukturierter D1-/D2-Anatomie.
- Neues optionales Feld `workout.optionalExerciseIds`: optionale Einheiten nicht als Pflichtabschluss zählen.

## PNF: Wichtige anatomische Differenzierung
- Das gangorientierte **Sprinter-D1-Muster** kombiniert auf dem Schwungbein Flexion–Adduktion–Außenrotation, und als Gegenrichtung Extension–Abduktion–Innenrotation.
- Zur D1-Flexion gehört am Sprunggelenk Dorsalflexion/Inversion; zur Extension Plantarflexion/Eversion. In der eigenständigen Bandübung klein und ohne Zwang ausführen.
- Extension–Abduktion–Außenrotation ist **keine** klassische D1- oder D2-Diagonale. Keine falsche PNF-Klassifikation verwenden.
- Das standardisierte PNF-Muster wird hier vereinfacht als Übung für Gangkoordination umgesetzt. Nicht als exakte Simulation aller Gangphasen oder als Wirksamkeitsgarantie verstehen.

## UI-Bindings passend zum Screenshot
- Workout: `workout.title`, `workout.estimatedMinutes`, `workout.exerciseIds.length`, `workout.rounds`.
- Karte: `exercise.title`, `exercise.subtitle`, `exercise.prescription`, `exercise.media.imageKey`.
- Detail: `exercise.steps`, `exercise.tip`, `exercise.prescription`, `workout.rounds`.
- Reps: `prescription.type == "repetitions"` -> `reps`; `type == "hold"` -> `holdSeconds` in Sekunden. `perSide` ergibt "je Seite" (bei Seitwärtsschritten: "je Richtung").
- `rounds` = Anzahl Durchgänge/Sätze *pro* Übung; nicht doppelt multiplizieren.
- Anzeige "x von y Übungen": Pflichtübungen über `exerciseIds` minus `optionalExerciseIds` zählen; optionale Übungen können auch abgeschlossen werden, blockieren aber nicht.
- Bilddateien sind noch **nicht** enthalten; `imageKey` ist ein Platzhalter und `illustrationDirection` eine Gestaltungsbeschreibung.

## Sicherheit / Interpretation
- Die optional gehaltene Adduktoren-Dehnung kann bei bereits ausreichender Beweglichkeit oder Hypermobilität entfallen.
- Übungen bei schmerzhafter Knieinstabilität, deutlichen Rückenschmerzen oder Schwellung medizinisch/physiotherapeutisch abklären.
- Bandankerpunkte und Fußschlaufen gegen Lösen sichern. Bei Unsicherheit zuerst ohne Band.
- Glute Bridging kräftigt Hüftstrecker, ersetzt keine Ursachenanalyse einer ausgeprägten Lordose.
- Die Zeitangaben sind UI-Schätzungen, nicht klinisch bestimmte Zeitdosen.

## Hintergrundquellen (nicht Wirksamkeitsbelege für jede Einzelübung)
- Dietz B., *PNF in Lokomotion: Let's sprint, let's skate*. Springer: https://link.springer.com/book/10.1007/978-3-642-27666-8
- *Effects of dynamic exercise utilizing PNF patterns on the balance of healthy adults*: https://pmc.ncbi.nlm.nih.gov/articles/PMC7113419/
- *Hip-Muscle Activity in Men and Women During Resisted Side Stepping With Different Band Positions*: https://pmc.ncbi.nlm.nih.gov/articles/PMC6333220/
- *Physical Therapy* der Ehlers-Danlos Society (Dehnen und Hypermobilität): https://www.ehlers-danlos.com/physical-therapy/
