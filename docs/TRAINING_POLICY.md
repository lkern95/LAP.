# LAP: Trainingsregeln und Rückkopplung

**Regelstand:** `1`  
**Zweck:** nachvollziehbare, lokale Anpassung der vorhandenen zeitbasierten Empfehlungen.  
**Grenze:** keine medizinische Beurteilung, Verletzungsprognose oder individuell validierte Halbmarathonplanung.

## Befund vor der Ergänzung

| Bereich | Stand im Code | Beleg |
|---|---|---|
| Zeitbasierter Wettkampf- und Formhalteplan | Vorhanden und verwendet | `TrainingPlanBuilder` baut den Plan; `PlanViewModel` beobachtet Profil, Ziel und Läufe gemeinsam. |
| Abstände und verfügbare Zeiten | Vorhanden und verwendet | `TrainingAvailabilityPlanner` plant mindestens einen Ruhetag zwischen vorgeschlagenen Läufen und kürzt nach bestätigten Zeitgrenzen. |
| Tatsächliche Laufdauer, Strecke und Puls | Vorhanden und verwendet | `AndroidHealthConnectReader` liest Lauf-Sitzungen mit Strecken- und Pulsmessungen derselben Datenherkunft; `RoomWorkoutRepository` führt per Quell-ID zusammen. |
| Rückmeldung zur empfundenen Anstrengung und zu beeinträchtigenden Beschwerden | Fehlte | Für importierte Läufe gab es vor dieser Ergänzung kein lokales Rückmeldungsfeld. |
| Persönliche Vergleichsbasis für Schlaf/Ruhepuls | Nicht vorhanden | Werte werden getrennt und lesend aus Health Connect abgefragt; es gibt keine gespeicherte, vergleichbare persönliche Zeitreihe. Sie steuern deshalb diese Anpassung nicht. |
| Health-Connect-Änderungsprotokoll und bestätigte Löschungen | Nicht nachgewiesen | Der aktuelle Reader fragt Zeiträume ab und führt Datensätze zusammen. Eine Changes-Token-Verarbeitung ist nicht vorhanden; aus fehlenden Treffern werden keine lokalen Löschungen abgeleitet. |

## Regeln

| ID | Zweck und Eingaben | Bedingung und Wirkung | Parameter / Kennzeichnung | Grenzen und Tests |
|---|---|---|---|---|
| `LAP-PLAN-01` | Wiedereinstieg und bisherige Laufhäufigkeit aus eindeutigen, importierten Quell-IDs | Keine Laufhistorie: Einstieg ohne behauptete Pause. Letzter Lauf mindestens 14 Tage zurück: frühere Planphase wiederholen. Unter zwei importierte Läufe in der abgeschlossenen Vorwoche: laufende Phase wiederholen. | Bestehende Produktregel, keine wissenschaftliche Sicherheitsschwelle. | Nicht importierte Aktivitäten bleiben unbekannt. Test: `TrainingPlanBuilderTest`. |
| `LAP-FEEDBACK-01` | Freiwillige Rückmeldung pro stabiler Lauf-ID: Anstrengung 0–10 sowie Nein/Ja/Unsicher zu Beschwerden, die das Laufen beeinträchtigt haben | Keine Vorauswahl. „Überspringen“ speichert den Zeitpunkt, aber keine positive oder negative Antwort. Ein erneuter Import derselben Quelle erhält die Rückmeldung. | Nutzerangabe; lokal in Room. 0 und 10 werden in der Oberfläche erklärt. | Keine Diagnose oder Krankheitsakte. Tests: `WorkoutRepositoryTest`, `WorkoutAnalysisScreenTest`. |
| `LAP-RESPONSE-01` | Rückmeldete Anstrengung und Dauer derselben echten Läufe | Mindestens zwei Läufe in den letzten 28 Tagen mit Anstrengung ≥7/10 und Laufdauer innerhalb ±20 %: LAP wiederholt eine frühere Planphase und schlägt keinen optionalen dritten Lauf vor. | **Heuristik**, kein in den Quellen validierter Grenzwert. Die Dauerähnlichkeit ist nur ein einfacher Vergleich, kein Beweis gleicher Intensität oder Strecke. | Herzfrequenz ersetzt nie die eigene Angabe. Test: `TrainingResponseAnalyzerTest`, `TrainingPlanBuilderTest`. |
| `LAP-RESPONSE-02` | Jüngste gespeicherte Antwort auf beeinträchtigende Beschwerden | Ja/Unsicher hat Vorrang vor normaler Progression; LAP wiederholt zwei Planphasen und unterdrückt optionale Läufe. Der Zustand läuft nicht allein durch Zeit ab. Eine spätere ausdrückliche „Nein“-Rückmeldung zu einem neu erfassten Lauf aktualisiert ihn. | **Konservative Produktregel**, keine medizinische Freigabe. | Die App diagnostiziert nicht und verspricht keine Sicherheit. Ohne spätere Rückmeldung bleibt die Anpassung aktiv; eine separate manuelle Klärung ist derzeit nicht vorhanden. Tests: `TrainingResponseAnalyzerTest`, `TrainingPlanBuilderTest`. |
| `LAP-DATA-01` | Fehlende Gesundheitsmessungen und Import-/Rechtezustände | Fehlende Messungen bleiben unbekannt. Vorhandener Plan und Laufeinheiten bleiben nutzbar; Schlaf/Ruhepuls ändern diese Regel nicht. | Bestehende Health-Connect-Zustände werden in der Erholungsansicht getrennt dargestellt. | Der Planer erhält derzeit keine vollständige Abfragefenster-/Importvollständigkeitsrevision. Ältere Läufe außerhalb des Lesefensters können eine Pause vortäuschen; vor stärkerer automatischer Anpassung muss diese Grenze angebunden werden. |
| `LAP-PLAN-02` | Zeitgrenzen und tatsächlicher Trainingszeitpunkt | Bestehende Verfügbarkeitsplanung nutzt bestätigte Grenzen. Verpasste Läufe werden nicht als FIFO-Schuld gespeichert. Eine kurze Variante erhält nicht automatisch den Zweck eines langen Laufs. | Bestehende Produktregeln aus `TRAINING_PLAN.md`. | Kein täglicher Pflichtfragebogen. Eine vom Nutzer aktuell gemeldete Zeitabweichung ist nur für diese Auswahl gedacht. |

## Warum es eine Rückmeldung gibt

Die systematische Übersichtsarbeit von Saw et al. fand, dass subjektive Angaben in den untersuchten Studien Trainingsreaktionen teils empfindlicher und konsistenter abbildeten als häufig verwendete objektive Werte. Das stützt einen zusätzlichen Nutzerkanal, aber **nicht** die hier gewählte 0–10-Grenze oder eine automatische Laufentscheidung. [Saw et al., PubMed](https://pubmed.ncbi.nlm.nih.gov/26423706/)

Eine kleine Studie mit Freizeitläufern untersuchte individualisierte Anpassungen anhand von HRV, subjektiver Erholung und einer Herzfrequenz-Laufgeschwindigkeitskennzahl. Sie ist kein validiertes Modell für LAP, die vorhandenen Daten oder jede einzelne Läuferin. [Nuuttila et al., PubMed](https://pubmed.ncbi.nlm.nih.gov/35975912/)

Die 2025er Kohortenstudie zu einzelnen Distanzsprüngen ist ein Beobachtungsbefund. Sie begründet hier weder eine universelle sichere Prozentgrenze noch eine entsprechende Minutengrenze. [Frandsen et al., PubMed](https://pubmed.ncbi.nlm.nih.gov/40623829/)

## Nachweisgrenzen

- Laufwerte stammen aus dem Health-Connect-Lesepfad. Laufdaten werden nicht aus durchschnittlichem Puls in Intervalle, Schwellen oder Zieltempo umgerechnet.
- Ein sauberer Quell-ID-Upsert und ein Datenbank-Migrationsschutz verhindern nicht automatisch jede fachliche Fehlzuordnung oder einen fehlenden Health-Connect-Datensatz.
- Profil, Ziel, eindeutige Laufhistorie und Rückmeldungen bilden lokal den Plan. Die App speichert dazu die jeweils aktuelle Kurzentscheidung mit Regelversion, Zielrevision, Eingabe-Hash, Empfehlung und Grundcode. Das ist kein unveränderliches Entscheidungsarchiv; wiederholte identische Eingaben überschreiben den Snapshot nicht und Planberechnungen lesen ihn nicht als Eingabe zurück.
- Keine der Tests beweist einen Leistungsgewinn, eine Verletzungsvermeidung oder wissenschaftliche Optimalität. Dafür wäre eine separate, prospektive Evaluation nötig.
