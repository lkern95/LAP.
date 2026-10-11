# Wettkampf-Trainingsplan

## Ziel und Status

Wettkampftag und Strecke (1–100 km) werden beim Einrichten selbst festgelegt und lokal gespeichert. Marathon- und Halbmarathondistanzen erhalten passende Namen; sonst erscheint die eingegebene Strecke. Laufeinheiten bleiben zeitbasiert und ohne vorgegebene Zielzeit. Häufigkeit und verfügbare Trainingstage werden lokal gewählt.

## Trainingsaufbau

Alle Laufeinheiten richten sich nach Zeit und leichtem Gesprächstempo, nicht nach Tempo-Ziel. Jede Einheit beginnt mit 5–8 Minuten zügigem Gehen und endet mit mindestens 5 Minuten lockerem Gehen. Gehpausen sind erlaubt und kein Rückschritt.

- Einheit A: 30–45 Minuten locker mit Gehpausen nach Bedarf.
- Einheit B: längere, ruhige Einheit; in der ersten Phase 45–60 Minuten, danach in kleinen Schritten verlängern, etwa jede vierte Woche kürzer.
- Optionale Einheit C: 20–30 Minuten sehr locker; entfällt zuerst bei Müdigkeit oder engem Abstand zu Schichtarbeit.
- Verfügbarkeit beschreibt Zeitobergrenzen, keine Trainingstermine. Bei einem regelmäßigen Rhythmus können alle Wochentage als möglich markiert werden. Sieben mögliche Tage erzeugen nicht sieben Einheiten.
- Im flexiblen Modus genügen eine übliche Zeitobergrenze und optional eine längere Zeit, die gelegentlich möglich ist. Es gibt keine wöchentliche Kalendereingabe. Der Trainingsvorschlag bleibt ohne festen Tag, bis der Nutzer eine konkrete Einheit startet.
- Unbekannte Verfügbarkeit ist weder bestätigte Freizeit noch ein Ruhetag. Bei einer kurzfristigen Zeitabweichung kann der Nutzer beim Start angeben, wie viel Zeit heute verfügbar ist.
- Zwischen eingeplanten Läufen bleibt mindestens ein Tag ohne Laufeinheit, auch über Wochenwechsel hinweg. Tatsächlich absolvierte Einheiten aus der Vorwoche zählen für diese Abstandsregel mit.
- Nicht zwei längere oder anstrengende Einheiten direkt hintereinander legen.
- In den letzten zwei Wochen Umfang reduzieren; die Wettkampfwoche enthält nur kurze lockere Läufe und den gewählten Wettkampf.

## Belastungsregel

Der Plan darf einzelne Wochen wiederholen. Verpasste Einheiten werden nicht nachgeholt und nicht zusammengelegt. Eine dritte Laufeinheit erhöht nicht automatisch den langen Lauf; sie wird erst nach wiederholter Laufkonstanz vorgeschlagen und bleibt optional. Es wird keine starre „10-Prozent-Regel“ als Sicherheitsgarantie verwendet.

## Anpassung anhand aufgezeichneter Läufe

LAP wertet die Anzahl, Dauer und Strecke der importierten Läufe der letzten vier Wochen, Läufe der Vorwoche sowie die Pause seit dem letzten Lauf aus. Wenn in der Vorwoche weniger als zwei Läufe erfasst wurden, wird eine Belastungswoche wiederholt. Nach mindestens 14 Tagen ohne erfassten Lauf wird ein kürzerer Wiedereinstieg vorgeschlagen.

Diese Regeln sind bewusst einfach und vorsichtig; sie sind kein wissenschaftlich validiertes Prognose- oder Bereitschaftsmodell. Nicht importierte Aktivitäten können fälschlich wie eine Trainingspause wirken. Deshalb zeigt die App die zugrunde liegenden Laufdaten und verwendet keine erfundenen Bereitschaftspunkte, Zielzeitprognosen oder Herzfrequenzzonen. Eine Empfehlung ersetzt keine individuelle medizinische oder trainingswissenschaftliche Beurteilung.

Nach einer neu importierten Laufaktivität kann die Nutzerin die Anstrengung auf einer erklärten Skala von 0 bis 10 und beeinträchtigende Beschwerden mit Nein, Ja oder Unsicher angeben. Beide Angaben sind optional und werden an die stabile Quell-ID des Laufs gebunden; Überspringen bleibt unbekannt. LAP wiederholt nach zwei ähnlich langen, in den letzten 28 Tagen mit mindestens 7/10 bewerteten Läufen eine Planphase. Eine jüngste Ja-/Unsicher-Antwort zu Beschwerden unterdrückt den Aufbau und optionale Läufe, bis eine spätere ausdrückliche Nein-Antwort erfasst wird. Diese Regeln sind vorsichtige Produktheuristiken; die Grenzwerte und die Wirkung sind nicht wissenschaftlich validiert. Details und Tests stehen in [TRAINING_POLICY.md](TRAINING_POLICY.md).

Die Daueranweisungen sind Orientierung: lockeres Gesprächstempo, Gehen jederzeit erlaubt. Garmin zeichnet den Lauf auf; die App zeigt nur die Planung und wertet importierte Einheiten aus.

## Kraft und Verlauf

Das kurze Kraftprogramm enthält einfache Übungen für Knie, Sprunggelenke und Rumpf. Die App zählt, wie oft einzelne Übungen in den letzten vier Wochen als erledigt markiert wurden. Sie misst keine Kraftwerte oder Bewegungsqualität. Übungsauswahl und Progression sind kein medizinisches Rehabilitationsprogramm. Ein bis zwei Einheiten pro Woche sind ein vorsichtiger Einstieg, aber keine Garantie gegen Verletzungen.

## Herkunft der Hinweise

Zeit- und Gehintervalle orientieren sich am schrittweisen Einsteigerprinzip, wie es der NHS für Laufanfänger beschreibt: nicht aufeinanderfolgende Einheiten und Ruhe dazwischen. NIOSH weist darauf hin, dass Schichtarbeit Schlaf und Erholung stören kann; LAP verwendet deshalb ausdrücklich als Nachtdienst markierte Verfügbarkeit vorsichtiger und schlägt dort höchstens einen lockeren Lauf vor. Quellen: [NHS Couch to 5K](https://www.nhs.uk/better-health/get-active/get-running-with-couch-to-5k/), [NIOSH zu Schichtarbeit und Erschöpfung](https://www.cdc.gov/niosh/learning/safetyculturehc/module-2/9.html). Studien zu Krafttraining bei Läufern berichten mögliche Leistungsverbesserungen, untersuchen aber überwiegend trainierte Läufer und belegen keine individuelle Verletzungsfreiheit. Übersichten zur Vorbeugung von Laufverletzungen bewerten die Evidenz für viele Maßnahmen als niedrig oder sehr niedrig. Daher verspricht die App keine sichere Verletzungsvermeidung. Quellen: [Krafttraining bei Mittel- und Langstreckenläufern](https://pmc.ncbi.nlm.nih.gov/articles/PMC11052887/), [Übersicht zur Verletzungsprävention](https://pubmed.ncbi.nlm.nih.gov/36150753/).
