# Designsystem

- Material 3 als Standard für Navigation, Schaltflächen, Karten, Listen, Dialoge und Formulare.
- Auswahl-Dropdowns verwenden den gemeinsamen `AppDropdown`-Baustein: Menü und Auswahlfeld haben dieselbe verfügbare Breite, abgerundete Ecken, Theme-Farben und eine begrenzte Scrollhöhe.
- Ruhige, gut lesbare Trainingsoberfläche mit klarer Informationshierarchie.
- Einheitliche deutsche Begriffe und identische Darstellung für identische Aktionen.
- Farben sind nie der einzige Informationsträger; Status zusätzlich mit Text und Symbol vermitteln.
- Zeitintervalle, Gehpausen und Belastungsniveau auf einen Blick unterscheidbar darstellen.
- Der Bildschirm „Erholung“ verwendet lokale Schlaf-, Ruhepuls-, HRV/HFV-, Stress- und Schrittwerte samt Verlauf sowie importierte Läufe. Ein animierter Halbkreis zeigt einen persönlichen Modellindex nur bei mindestens 14 Schlafmessungen und einem autonomischen Signal (HRV/HFV oder Ruhepuls) über 21 Tage sowie mindestens vier Läufen aus den vorherigen vier Wochen. Schlaf (40 %), ein autonomisches Signal (30 %) und Laufbelastung einschließlich Zeit seit dem letzten Lauf (30 %) bleiben nachvollziehbar; HRV/HFV ersetzt Ruhepuls, wenn beides verfügbar ist, damit ähnliche Werte nicht doppelt zählen. Stress und Schritte liefern Kontext und gehen nicht zusätzlich in den Score ein. Fehlende Vergleichswerte ergeben „Noch nicht bewertbar“. Die Datenvollständigkeit steht separat darunter. Der Index ist keine medizinische Messung und ändert den Trainingsplan nicht.
- Die Auswertung eines synchronisierten Laufs zeigt die Schildkröte einmal groß auf dem Hintergrund. Distanz, Dauer, Pace und Puls werden in einer ruhigen Zweierspalte mit eigenen Symbolen und deutlich betonten Werten dargestellt. Synchronisierung wird kompakt bestätigt; Details wie eine nicht übermittelte Streckenkarte bleiben zunächst eingeklappt. Der „Weiter“-Knopf bleibt am unteren Rand oberhalb der Systemleiste verankert.
- Keine Sonderkomponente ohne dokumentierten Usability-Grund.
- Die Fortschrittsseite nutzt die Material-3-Auswahl für Woche, Monat und Gesamt sowie ein barrierefrei beschriebenes Kilometerdiagramm. Die Gesamtansicht verwendet eine feste Zwölf-Monats-Achse, damit einzelne Laufmonate als einzelne Balken statt als vollbreite Fläche erscheinen. Kennzahlen darunter müssen direkt aus importierten Läufen messbar sein; Fortschrittswerte dürfen nicht als Bereitschaftspunkte oder Zielzeitprognosen dargestellt werden, solange kein belastbares Modell vorliegt.

## Farbstile

- **Olive** ist der Standard: warme helle Fläche, dunkles Olivgrün und zurückhaltendes Rosa.
- **Dunkel** nutzt das Material-3-Dunkelschema.
- **Hell** nutzt ein klares weißes Material-3-Hellschema.
- Die Auswahl gilt appweit und wird lokal gespeichert. Status und Bedeutung bleiben auch ohne Farberkennung durch Text und Symbole verständlich.

## Startmarke und Assets

- Der Start verbindet das loopfähige Willkommensvideo mit einer animierten Wortmarke: `LAP.` in Inter Black, `your running PAL.` dezent in Inter Extra Light.
- Das Video läuft stumm und behält sein Seitenverhältnis. Die Startansicht bleibt geöffnet, bis „Los geht's“ gewählt wird.
- Die gelieferten Lauf-, Plan-, Fortschritts-, Profil-, Kraft- und Statusillustrationen liegen als Android-Ressourcen in `app/src/main/res/drawable-nodpi/`. Das unveränderte Quellpaket wird unter `docs/design-assets/icons-source/` aufbewahrt.
- Das Zifferblattbild der Garmin-Uhr erscheint auf der Garmin-Seite. Die mitgelieferte Inter-Schrift ist variabel; ihre Lizenz liegt unter `docs/licenses/INTER-OFL.txt`.
- Die Garmin-Seite zeigt das vorhandene schwarze Garmin-Uhrenbild. Ein animierter Ring liegt proportional zum Bild und exakt über dem Mittelpunkt der tatsächlichen Displayfläche; ein grüner Haken erscheint bei bestätigten Garmin-Läufen, ein rotes X bei fehlender Freigabe oder einem Abruffehler. Während einer laufenden Prüfung dreht sich der Ring ohne Mittelpunkt-Symbol. Das Garmin-Uhr-Symbol steht auf der Karte „Läufe und Garmin Connect“. Statuszeilen nutzen die gelieferten Symbole für Lauf, Herzgesundheit und Kalender und öffnen Verlauf, Freigabe beziehungsweise Plan.
