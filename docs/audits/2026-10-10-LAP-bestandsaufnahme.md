# Technische Bestandsaufnahme: LAP

**Stand:** 10. Oktober 2026  
**Prüfumfang:** vorhandene Android-App, Dokumentation, Persistenz, Health-Connect-/Garmin-Datenweg, Trainingslogik, Tests und lokale Build-Artefakte.  
**Arbeitsmodus beim Erst-Audit:** lesende Prüfung; die nachfolgende Nachverfolgung dokumentiert gezielte Korrekturen. Für Änderungen wurde zuvor ein lokaler, nicht eingecheckter Bestandssnapshot erstellt.

## Kurzfassung

1. Das Repository enthält eine native Android-App mit einer Activity und einem `app`-Modul; die sichtbare Marke ist laut Produktdokument **LAP.**, nicht Tempo.
2. Laufdaten kommen über Health Connect und werden lokal in Room gespeichert; Profil, Zielhistorie und Designwahl liegen lokal in DataStore.
3. Der Code startet und steuert keine Läufe auf dem Telefon und verwendet keine direkte oder inoffizielle Garmin-Connect-Schnittstelle.
4. Die additiven Room-Migrationen bis Version 5 und das Zusammenführen wiederholt importierter Laufdatensätze sind implementiert; Migration, Upsert und Feedback-Erhalt wurden gezielt auf der isolierten AVD geprüft.
5. Die Unit-Suite besteht aus 65 bestandenen Tests. Nach den gezielten Korrekturen bestanden zwei aufeinanderfolgende vollständige Android-/Compose-/Room-Läufe jeweils 34/34 Tests auf der isolierten Android-16-/API-36-AVD.
6. Debug-Build und Lint liefen erfolgreich; Lint meldete 0 Fehler und 18 Warnungen.
7. Es gibt weiterhin kein signiertes Release und keinen Installations-/Update-Nachweis. Die Release-Signierprüfung stoppt erwartungsgemäß, weil lokale Signierwerte fehlen.
8. Garmin-/Health-Connect-Live-Sync, tatsächliche Provider-/Rechtefehler, Hintergrundausführung und Release-Update sind weiterhin nicht nachgewiesen; Live-Laufansicht, Export und Sprachführung bleiben separate offene Produktanforderungen.

## Ausgangslage und Grenzen

- Der Projektordner heißt `run`, die Anwendung heißt laut [PRODUCT.md](../PRODUCT.md) **LAP.**. Der Auftragstext verwendet „Tempo“; das ist ein Namenskonflikt im Auftrag, kein Befund zur sichtbaren App.
- Git meldet den Branch `main`, aber es gibt keinen Commit/HEAD. Der gesamte Projektbestand erscheint als unversioniert (`??`). Es existiert daher keine belastbare Commit-Basis, um die Änderungen gegenüber einem früheren Stand zu vergleichen. Vorhandene Dateien wurden als Bestand behandelt.
- Ein Pixel-Emulator ist verbunden und meldet Android API 36. Ich habe ihn nicht gestartet, installiert, zurückgesetzt oder mit Instrumentationstests verändert.
- Es wurde kein externer Quellcode-Auditdienst und kein CVE-/Abhängigkeits-Auditdienst aufgerufen. Das Audit stützt sich auf lokalen Quellcode, Build-Ausgaben und offizielle Android-Dokumentation.
- Funktionsbefunde aus Code sind als **statisch bestätigt** gekennzeichnet; erfolgreiche Testläufe bestätigen nur den ausgeführten Testumfang. Garmin Connect und echte Health-Connect-Daten wurden nicht live getestet.

## Systemübersicht

### Plattform und Struktur

| Merkmal | Bestand |
|---|---|
| Plattform | Native Android-App in Kotlin, Jetpack Compose, Material 3, Navigation Compose |
| Architektur | Eine Activity, ViewModels/StateFlow, Coroutines; einzelnes Gradle-Modul `:app` |
| Verantwortlichkeitspakete | `presentation`, `domain`, `data/local`, `data/healthconnect`, `data/demo` |
| Android-Konfiguration | `applicationId` `de.run.app`, `minSdk 34`, `targetSdk 36`, `compileSdk 36`, JVM-Ziel 17 |
| Build-Werkzeuge | Gradle Wrapper 8.12, Android Gradle Plugin 8.10.1, Kotlin 2.2.21 |
| Weitere Plattformen | Keine iOS-, Web- oder Backend-App in den Android-Quellsets gefunden |
| Verteilung | `versionCode 1`, `versionName 0.1.0`; keine Release-Signaturkonfiguration im erzeugten Release-Artefakt |

Die Struktur hält Android- und Compose-Abhängigkeiten weitgehend aus `domain` heraus. Repositories sind Schnittstellen in der Domain, Health Connect und Room liegen in `data`, und die Activity baut die Abhängigkeiten über einen kleinen `AppContainer` zusammen. Ein eigenes `application`-Paket mit Use Cases ist nicht vorhanden; mehrere Abläufe werden in ViewModels koordiniert. Das ist eine begrenzte Abweichung von der in [ARCHITECTURE.md](../ARCHITECTURE.md) beschriebenen Schichtenfolge, aber es wurde kein zweites DI- oder Architekturframework eingeführt.

### Datenflüsse und Speicherung

```text
Garmin-Uhr → Garmin Connect → Health Connect
                         → AndroidHealthConnectReader / HealthConnectReadinessRepository
                         → Domain-Modelle
                         → Room (Laufhistorie/Kraftabschlüsse) oder DataStore (Profil/Ziele/Theme)
                         → Repository → ViewModel/StateFlow → Compose
```

- **Läufe:** `AndroidHealthConnectReader` liest Lauf- und Laufband-Sitzungen, Distanz und Herzfrequenz. Die Herkunft bleibt erhalten. Garmin wird anhand des Paketnamens erkannt; andere Quellen werden als andere Lauf-App markiert. Jeder Datensatz erhält eine stabile ID aus Quelle und Health-Connect-ID. `RoomWorkoutRepository` führt Importe per Upsert zusammen, statt bei erneutem Lesen Duplikate anzulegen.
- **Gesundheitswerte:** Schlaf und Ruhepuls werden separat und lesend aus Health Connect abgefragt. Sie werden nicht zusätzlich in Room gespeichert. Fehlende Werte und nicht erteilte Berechtigungen haben eigene Zustände.
- **Profil und Ziele:** DataStore speichert Trainingsprofil, Verfügbarkeit, Zielhistorie und Einstellungen. Frühere Zieleinstellungen werden beim Lesen additiv in einen Zielverlauf überführt. Zielhistorie und absolviertes Laufarchiv sind getrennt.
- **Krafttraining:** Erledigungen liegen als Room-Datensätze vor.
- **Migrationsschutz:** Room-Schema Version 3 enthält additive Migrationen 1→2 und 2→3. Ein Android-Integrationstest baut eine Datenbank der Version 1 und prüft, dass der vorhandene Lauf samt Distanz nach beiden Migrationen bestehen bleibt.
- **Wiederherstellung:** Im Manifest ist `allowBackup=false` gesetzt. Damit ist kein Android-Backup als Wiederherstellungsweg vorgesehen. Ein Export-/Importweg wurde in den geprüften Quellsets nicht gefunden.

### Garmin, Health Connect und Datenschutz

- Der technische Datenweg entspricht [ADR-001](../decisions/ADR-001-health-connect.md): keine Garmin-Zugangsdaten, keine inoffiziellen Endpunkte, kein Scraping, keine direkte Garmin-Anmeldung.
- Das Manifest deklariert Health-Connect-Leserechte für Laufaktivitäten, Distanz, Puls, Schlaf, Ruhepuls sowie optionale Hintergrund- und historische Abfragen. Im zusammengeführten Release-Manifest wurde keine `INTERNET`-Berechtigung gefunden.
- Vordergrundabfragen werden beim Zurückkehren in LAP und beim Öffnen von Laufansichten angestoßen. WorkManager plant bei Hintergrundfreigabe regelmäßige Abfragen; der Android-Zeitplan ist nicht exakt garantiert. Ohne die zusätzliche Freigabe wird kein Hintergrundabruf erwartet.
- Ohne Freigabe für ältere Daten begrenzt die Implementierung die Laufhistorie auf 30 Tage. Mit entsprechender Freigabe fordert der Hintergrundlauf bis zu 90 Tage an.
- Das grüne „Verbunden“-Signal der Garmin-Seite wird aus vorhandenen Garmin-Laufdaten und dem erfolgreichen Abruf abgeleitet. Es bestätigt einen funktionierenden Importweg, aber keinen aktuellen Bluetooth-Kontakt oder Live-Status der Uhr.
- Es wurden in den App-Quellen keine HTTP-Clients, OAuth-Flows oder gespeicherten Garmin-Zugangsdaten gefunden. Datenverarbeitung ist gemäß [ADR-002](../decisions/ADR-002-local-first.md) lokal ausgelegt.

### Trainings- und Laufumfang

- Der Planer erzeugt zeitbasierte Einheiten, Gehpausen, Erholungswochen und einen Taper. Er passt den Vorschlag anhand importierter Laufhäufigkeit, letzter Aktivität und jüngster Trainingswochen an. Das ist nachvollziehbare, regelbasierte Planung, keine wissenschaftlich validierte Prognose. Die Dokumentation benennt diese Grenze ausdrücklich.
- Der Halbkreis auf der Erholungsansicht zeigt den Anteil von vier verfügbaren Angaben (Schlaf, Ruhepuls, Laufhistorie/Berechtigung), keinen physiologisch validierten Bereitschafts- oder Verletzungsrisikoscore. UI und Accessibility-Text benennen „x von 4 Werten verfügbar“.
- Läufe werden ausschließlich auf der Garmin-Uhr gestartet. Es gibt keinen Smartphone-Recorder, keine Standortberechtigung und keinen erkennbaren laufenden GPS-/Pulsbildschirm in den App-Quellen.
- Die WAV-Dateien im Projektstamm werden von den Android-Quellen nicht als Laufansagen verwendet. Die gefundene Medienwiedergabe ist die Begrüßungsanimation mit `welcome.mp4`.
- Die Debug-Demodaten sind synthetisch, liegen im Debug-Quellset und werden über `BuildConfig.DEBUG` abgeschaltet. Ihre Integration verändert die real gespeicherten Laufdaten nicht. Das Release-Artefakt wird aus dem `main`-Quellset erzeugt und enthält das Debug-Asset nicht.

## Tests und technische Prüfungen

### Historischer Stand vor dem Folgeauftrag

Die folgende Tabelle hält den Befund vor den Laufzeittests fest. Die aktuelle Messung steht im Abschnitt „Laufzeit- und Updateprüfung vom 10. Oktober 2026“.

| Prüfung | Ergebnis | Aussagekraft / Einschränkung |
|---|---|---|
| `:app:testDebugUnitTest --rerun-tasks` | **Erfolgreich**, 12 XML-Suites, 43 Tests, 0 Fehler/Fehlschläge/Übersprünge | Domainregeln und Formatierung wurden ausgeführt; kein Beleg für Garmin oder Gerätedaten |
| `:app:compileDebugAndroidTestKotlin --rerun-tasks` | **Erfolgreich** | Android-/Compose-/Room-Testquellen kompilieren; Tests wurden nicht auf dem Emulator ausgeführt |
| `:app:assembleDebug` | **Erfolgreich** | Debug-APK gebaut; kein Geräteinstallations- oder visueller Laufzeittest |
| `:app:lintDebug` | **Erfolgreich**, 0 Fehler, 21 Warnungen | Siehe Warnungsgruppen unten |
| `:app:assembleRelease` | **Erfolgreich**, erzeugt `app-release-unsigned.apk` | Der APK-Signaturcheck meldet erwartungsgemäß, dass das Artefakt unsigniert ist |
| Instrumentierte UI-Tests | **Nicht ausgeführt** | Die Tests durchlaufen Onboarding, ändern unter anderem Einstellungen und schreiben in App-Speicher. Auf dem verbundenen Emulator wollte ich vorhandene persönliche App-Daten nicht durch Testläufe verändern. |
| Health Connect/Garmin live | **Nicht ausgeführt** | Keine Test-Garmin-Quelle oder isolierte Health-Connect-Testumgebung verwendet |
| Visuelle/Performance-Prüfung | **Nicht ausgeführt** | Keine Screenshots, Framezeiten, Animationen oder Tabwechsel auf dem Emulator geprüft |
| Abhängigkeits-/Sicherheitsdienst | **Nicht ausgeführt** | Keine externe Einspeisung von Quellcode oder Projektdateien |

Beim Online-Build waren die globalen Android/Kotlin-Daemon-Verzeichnisse nicht beschreibbar. Gradle fiel für Kotlin auf die eingebaute Kompilierstrategie zurück; die Builds und Testaufgaben endeten trotzdem erfolgreich. Der erste Offline-Versuch stoppte vor der Kompilierung, weil WorkManager 2.12.0 im Offline-Cache fehlte; der anschließende auflösende Build war erfolgreich.

### Historische Lint-Warnungen vor der Nachverfolgung

Lint meldete keine Fehler und 21 Warnungen. Darunter sind Hinweise auf neuere verfügbare Versionen für Gradle, Android Gradle Plugin, Compose BOM, Activity Compose, Lifecycle, Navigation und Core KTX; dazu ein Compose-Modifier-Hinweis, die Backup-Regel im Manifest, nicht verwendete Bildressourcen und eine KTX-Empfehlung. Die Versionshinweise sind keine automatischen Upgrade-Anweisungen. Vor einem Sprung auf Hauptversionen müssen Breaking Changes, Migrationen und Health-Connect-Verhalten geprüft werden.

## Befunde

Priorität: **P1** = vor einer geplanten Veröffentlichung/Erfüllung des zugesagten Kernumfangs klären; **P2** = mittlere Daten-, Funktions- oder Betriebsrelevanz; **P3** = Wartbarkeit/Politur. Status unterscheidet sichtbare Implementierung von tatsächlicher Prüfung.

| ID | Priorität / Status | Befund und Wirkung | Nachweis |
|---|---|---|---|
| LAP-A01 | **P1 · Signierpfad vorbereitet, Release-Nachweis offen** | Lokale Signierdaten fehlen. `verifyReleaseSigning` stoppt mit den fehlenden Variablennamen; ein signiertes Paket, Installations- oder Update-Test liegt nicht vor. Ein früheres unsigniertes APK ist kein Release-Nachweis. | `app/build.gradle.kts`; `docs/RELEASE.md`; aktuelle Folgeprüfung unten |
| LAP-A02 | **Offene Produktanforderung · nicht im Prüfgegenstand** | Eine passive Live-Laufansicht mit Pace, Distanz, Puls, Karte und Steuerflächen ist nicht vorhanden. Dieser Auftrag prüft den lesenden Import abgeschlossener Läufe. Live-Laufansicht, Garmin-Workout-Export und Sprachführung bleiben getrennte offene Produktanforderungen; sie wurden weder implementiert noch aus der Produktplanung entfernt. | `docs/PRODUCT.md`; `docs/GARMIN_INTEGRATION.md` |
| LAP-A03 | **P2 · statisch bestätigt; Testquellen nicht ausgeführt** | Ältere Beschreibung und neuer flexibler Planungsmodus widersprechen sich. `TRAINING_PLAN.md` sagt, wechselnde Dienste verwenden konkret bestätigte Tage der kommenden sieben Tage; der aktuelle Planer/Test beschreibt variable Verfügbarkeit als undatierte Vorschläge mit Zeitobergrenzen und ignoriert alte datumsgebundene Angaben. Das erschwert verlässliche Weiterentwicklung und Prüfung gegen die aktuelle Nutzerentscheidung. | `docs/TRAINING_PLAN.md:14-16`; `app/src/main/java/de/run/app/domain/TrainingAvailabilityPlanner.kt:25-64`; `app/src/test/java/de/run/app/domain/TrainingPlanBuilderTest.kt:48-112` |
| LAP-A04 | **Geschlossen, mit fokussierten Unit-Tests** | Das Zielarchiv hat ein versioniertes Format. Gültige Altdaten werden übernommen; ungültige/unbekannte Rohwerte bleiben erhalten und der Speichervorgang bricht ab. Die Zielhistorie bleibt bei Zieländerungen und Migrationen erhalten. Physische Dateibeschädigung und Prozessneustart wurden nicht auf einem Gerät getestet. | `TrainingGoalDataStore`/Codec und Zielarchivtests; Nachverfolgung LAP-A04 |
| LAP-A05 | **P2 · statisch bestätigt, bewusste Trade-off-Entscheidung** | `allowBackup=false` und kein Export-/Importpfad bedeuten, dass Profil, Zielhistorie und lokale Verläufe bei Deinstallation, Geräteverlust oder beschädigtem Speicher nicht aus einer LAP-Sicherung wiederhergestellt werden können. Das reduziert automatische Kopien sensibler Daten, lässt aber keine lokale Wiederherstellungsmöglichkeit erkennen. | `app/src/main/AndroidManifest.xml:13-19`; `docs/decisions/ADR-002-local-first.md:3-6` |
| LAP-A06 | **Geschlossen für die geprüften lokalen Kernabläufe** | Die vorherigen Scroll-/Erreichbarkeitsfehler wurden anhand ihrer Semantikursachen behoben. Die vollständige aktuelle Suite besteht zweimal hintereinander mit 34/34 Tests. Ein zusätzlicher Room-Test belegt Lauf- und Feedbackpersistenz über einen externen Prozessstopp und Kaltstart. Reale Garmin-Daten sowie Health-Connect-Rechte-/Providerwechsel bleiben mangels Testkonto/-anbieter ausdrücklich nicht live nachgewiesen. | Instrumentationstestläufe und Prozessprüfung unten; `app/src/androidTest` |
| LAP-A07 | **P3 · Lint** | Aktuelle `:app:lintDebug`-Prüfung: 0 Fehler und 18 Warnungen. Die 21 Warnungen waren der historische Stand vor drei gezielten Korrekturen; ein pauschales Upgrade ist nicht begründet. | `app/build/reports/lint-results-debug.xml`; `gradle/libs.versions.toml` |

### Befund zum Git-Ausgangszustand

**LAP-A08 · Prüfgrenze:** Der Arbeitsstand hat weiterhin keinen Commit und der geprüfte Projektbestand steht Git-seitig unversioniert da. Die lokale ACL des `.git`-Ordners verweigert Schreibzugriff; es wurde nichts gestaged oder committed. Der aktuelle kuratierte Quellstand-Snapshot liegt außerhalb generierter Ordner unter `docs/audits/snapshots/LAP-source-final-r2-2026-10-10.zip`; SHA-256 `9FE4F760C77D010B41DFF6DD3FDFF0602DABB1D29EEFDEB0AAF6FC6729AC1135`. Er enthält 217 geprüfte Quell-, Dokumentations-, Gradle- und Schema-Dateien, aber keine Build-Ausgaben, Emulator-/App-Daten, lokalen SDK-Pfade, Signierschlüssel oder `.agent`-Dateien.

## Plattform- und Release-Matrix

| Ziel | Aktueller Stand | Ergebnis |
|---|---|---|
| Android 14+ / Emulator API 36 | `minSdk 34`, `targetSdk 36`; Emulator API 36 verbunden | Konfiguration und Testgerät passen zusammen; UI und Health Connect wurden nicht live getestet |
| Google Play, neue App oder Update | `targetSdk 36` | Entspricht laut offizieller Android-Dokumentation der ab 31. August 2026 geltenden Mindestanforderung API 36. Die Anforderungen an Health-Connect-Datenzugriff, Play Console-Erklärungen und Datenschutzhinweise müssen separat für die konkrete Verteilung abgeschlossen werden. |
| Signiertes Installationspaket | Release-APK ohne Signatur | Nicht bereit zur Installation/Veröffentlichung; Signaturschlüssel und Signierprozess fehlen im geprüften Build-Ergebnis |
| Wear OS, iOS, Web, Server | Kein entsprechender Anwendungscode | Nicht Teil des geprüften Projekts |

Quellen, abgerufen am **10. Oktober 2026**: [Google-Play-Ziel-API-Anforderung](https://developer.android.com/google/play/requirements/target-sdk) (API 36 für neue Apps und Updates ab 31.08.2026); [Health-Connect-Berechtigungen](https://developer.android.com/health-and-fitness/guides/health-connect/ui/permissions); [Health-Connect-Datentypen und zusätzliche Rechte für Hintergrund-/Historienzugriff](https://developer.android.com/health-and-fitness/health-connect/data-types).

## Priorisierte nächste Schritte

### Historische Empfehlungen vor dem Folgeauftrag

Die vor der aktuellen Geräteprüfung formulierten Empfehlungen sind historisch. Abgearbeitete oder neu eingeordnete Punkte stehen im Folgeprüfungsbericht weiter unten.

## Einordnung der Maßnahmen

### Vor Release erforderlich

- Signierter Release-Prozess, falls die App außerhalb des Debug-Builds verteilt werden soll.
- Health-Connect-/Play-Console-Erklärungen und verständliche Datenschutzhinweise, falls Veröffentlichung über Google Play vorgesehen ist.
- Entscheidung und Umsetzung/Abgrenzung zur Live-Laufansicht, falls diese frühere Anforderung weiter gilt.
- Reproduzierbare Geräteprüfung von Laufimport, Rechteentzug und Hintergrundsync in einer isolierten Installation.

### Später ausreichend

- Robusteres, versioniertes Format und Tests für DataStore-Zielhistorie.
- Backup-/Exportentscheidung unter Wahrung des lokalen Datenschutzmodells.
- Aktualisierung der widersprüchlichen Flexibilitätsbeschreibung und Review der Lint-Hinweise.
- Gezielte Bibliotheksupdates nach Prüfung von Breaking Changes, Schema und Health Connect.

### Bereits ausreichend – nicht umbauen

- Health Connect ist der einzige reale Garmin-Datenweg; es wurden keine Garmin-Zugangsdaten oder inoffiziellen Schnittstellen gefunden.
- UI greift nicht direkt auf Health Connect oder Room zu; ViewModels verwenden Repository-Verträge.
- Garmin-/andere Laufquellen bleiben unterscheidbar; wiederholter Laufimport verwendet stabile Quellidentitäten und Upsert.
- Die Room-Migrationen sind additiv und der Migrationstest prüft die Erhaltung eines vorhandenen Laufs.
- Readiness-Halbkreis zeigt Datenverfügbarkeit statt eines erfundenen medizinischen Scores; die Dokumentation grenzt Trainingshinweise als nicht validierte Fitnessplanung ein.
- Synthetische Demo-Laufdaten sind an den Debug-Build gebunden und getrennt von realen Health-Connect-/Room-Daten.

## Nachverfolgung der Befunde

**Historischer Stand nach der ersten Umsetzung:** 10. Oktober 2026. Vor den Änderungen wurde der damalige Projektbestand unter `build/audit-baseline/LAP-prechange-2026-10-10-v2.zip` gesichert; SHA-256: `D34043BB0BE200E71BC366C2A52D9276FDDB5A163958B11557D2BD2CD9344A04`. Der Snapshot liegt im ignorierten Build-Ordner. Der aktuelle Quellstand-Snapshot außerhalb generierter Ordner wird unten dokumentiert.

| ID | Ergebnis nach Prüfung/Umsetzung |
|---|---|
| LAP-A01 | **Teilweise vorbereitet, Freigabe blockiert:** Lokale Signierwerte werden ausschließlich aus Umgebungsvariablen gelesen. `verifyReleaseSigning` stoppt bei fehlenden Werten mit verständlicher Meldung; Release-Builds hängen von dieser Prüfung ab. Keine Signierdatei oder Zugangsdaten wurden bereitgestellt oder erzeugt. Es gibt weiterhin kein signiertes Release, keinen Signaturtest und keinen Installations-/Update-Test. Anleitung: [RELEASE.md](../RELEASE.md). |
| LAP-A02 | **Separate offene Produktanforderung:** LAP zeichnet keine Telefonläufe auf. Der bestätigte Ablauf bleibt „Lauf auf der Garmin-Uhr starten, danach importieren“. Live-Laufansicht, Garmin-Workout-Export und Sprachführung sind nicht durch diesen Auftrag abgedeckt und bleiben als offene Produktanforderungen bestehen. |
| LAP-A03 | **Dokumentationskonflikt behoben:** Der Plan beschreibt feste Tage als Verfügbarkeit und flexiblen Modus als Zeitgrenzen ohne Wocheneingabe. Bestehende Domainregeln und Unit-Tests wurden mit der Dokumentation gegengeprüft; die Regeln wurden nicht durch eine neue Planungslogik ersetzt. |
| LAP-A04 | **Sicheres Lesen/Schreiben und fokussierte Tests ergänzt:** Das Zielarchiv erhält einen Formatpräfix. Gültige ältere Datensätze werden additiv übernommen; beschädigte Zeilen, doppelte IDs, mehrere aktive Ziele, ungültige Teilmigrationen und unbekannte Versionen führen zu einem verständlichen Fehler. Speichern bricht atomar ab und belässt den gespeicherten Rohwert unverändert. Gültige vorhandene Zielverläufe bleiben erhalten; Zieleinstellungen und Laufarchiv bleiben getrennte Speicher. Repository-Tests verwenden einen kontrollierten In-Memory-DataStore; physische Dateibeschädigung wurde nicht simuliert. |
| LAP-A05 | **Wiederherstellungsentscheidung offen:** Cloud-Backup und Geräteübertragung sind jetzt explizit ausgeschlossen. Ein lokaler Export/Import wurde bewusst nicht ergänzt; vor einer größeren Verteilung muss die Abwägung zwischen lokalem Datenschutz und Wiederherstellbarkeit entschieden werden. |
| LAP-A06 | **Historisch teilweise geprüft:** Der frühere Lauf bestand aus 53 Unit- und 29 Instrumentationstests. Der aktuelle Nachweis steht im Abschnitt „Laufzeit- und Updateprüfung vom 10. Oktober 2026“ weiter unten. `Pixel_9` (`emulator-5554`) blieb unangetastet. Ein älterer Sichtcheck umfasste nur den Begrüßungsbildschirm bei 720×1600 und 1,3-facher Systemschrift. |
| LAP-A07 | **Bestätigt:** `:app:lintDebug` lief erneut erfolgreich: 0 Fehler, 18 Warnungen. Keine neue Versions- oder Ressourcenbereinigung wurde vorgenommen. |
| LAP-A08 | **Quellstand gesichert, Commit blockiert:** Siehe den aktuellen Quellstand-Snapshot und die bestätigte ACL-Sperre im Abschnitt „Laufzeit- und Updateprüfung vom 10. Oktober 2026“ weiter unten. |

### Folgeprüfungen des vorigen Durchgangs (historisch)

- `:app:testDebugUnitTest`: erfolgreich; der neue Fokus umfasst acht Zielhistorie-/Migrationstests und zwei Tests zum Status des Laufimports. Alle bestehenden Unit-Tests wurden mit ausgeführt.
- `:app:compileDebugAndroidTestKotlin`: erfolgreich; Android-UI- und Integrations-Testquellen kompilieren. Sie wurden nicht auf einem Gerät ausgeführt.
- `:app:assembleDebug`: erfolgreich.
- `:app:lintDebug`: erfolgreich, keine Fehler und 18 verbleibende Warnungen.
- `:app:verifyReleaseSigning`: erwartungsgemäß fehlgeschlagen, weil keine lokale Signierdatei und keine Signierwerte vorliegen; die Meldung nennt nur fehlende Variablennamen.
- Garmin-/Health-Connect-Import wurde nicht live validiert. Die Garmin-Seite bezeichnet nun den Vorgang als Import, zeigt die letzte erfolgreiche Prüfung und weist bei fehlender Freigabe für ältere Daten auf den 30-Tage-Zeitraum hin. Es gibt im Projekt keinen Garmin-Export; eine Übertragung von Plänen/Aktivitäten zu Garmin ist nicht implementiert.

## Erste Laufzeitprüfung vor den UI-Korrekturen vom 10. Oktober 2026 (historisch)

| Prüfung | Ergebnis | Aussagekraft / Grenze |
|---|---|---|
| `:app:testDebugUnitTest --rerun-tasks --no-daemon --project-prop=kotlin.compiler.execution.strategy=in-process` | **Bestanden**, 16 XML-Suites, 65 Tests, 0 Fehler/Fehlschläge/Übersprünge | Umfasst Domainregeln für Rückmeldung, Plananpassung und Entscheidungssnapshot. |
| `:app:assembleDebug :app:assembleDebugAndroidTest` | **Bestanden** | Debug-App und Instrumentation-APK wurden nach der UI-Korrektur neu gebaut. |
| Zwei gezielte Instrumentation-Klassen via `adb -s emulator-5556 shell am instrument -w -r -e class 'de.run.app.data.local.WorkoutRepositoryTest,de.run.app.presentation.history.WorkoutAnalysisScreenTest' ...` | **Bestanden**, 10/10 Tests auf `LAP_Isolated_Test`, API 36 / Android 16 | Room-Migration/Upsert/Feedback-Erhalt und Lauf-Auswertungsscreen einschließlich Speichern und Überspringen; nur synthetische Testdaten. |
| Vollständige Instrumentation mit `adb -s emulator-5556 shell am instrument -w -r de.run.app.test/androidx.test.runner.AndroidJUnitRunner` | **Nicht bestanden**, 33 Tests ausgeführt, 23 bestanden, 10 fehlgeschlagen | Fehler in `ProgressScreenTest` (1), `RunNavigationTest` (5), `TrainingDetailsScreenTest` (1) und `TrainingOnboardingScreenTest` (3). Unter anderem fehlen erwartete UI-Texte/Navigation. Diese Flows sind nicht als regressionsfrei nachgewiesen. Vor dem vollständigen Lauf wurden ausschließlich die Appdaten auf emulator-5556 geleert. |
| Gezielter Skip-Test nach Korrektur | **Bestanden**, 1/1 auf emulator-5556 | Die Aktionen „Rückmeldung speichern“ und „Überspringen“ waren nebeneinander auf schmalen Displays nicht zuverlässig erreichbar. Sie liegen nun untereinander über die Kartenbreite. |
| Kaltstart + Screenshot `adb -s emulator-5556 shell am start -W -n de.run.app/.MainActivity` | **Bestanden**, Activity startet; Begrüßungsansicht und zentrale Taste „Los geht's“ bei 720×1600 sichtbar | Nur Startansicht geprüft; keine vollständige Sichtprüfung, Navigation oder Systemschrift-Vergrößerung. Screenshot liegt im ignorierten `app/build/lap-isolated-review.png`. |
| `:app:lintDebug` | **Bestanden**, 0 Fehler, 18 Warnungen | Bericht: `app/build/reports/lint-results-debug.xml`; keine pauschale Warnungsbereinigung oder Versionsupdates. |
| `:app:verifyReleaseSigning` | **Erwartungsgemäß blockiert** | Die vier lokalen Signier-Variablen fehlen; die Ausgabe nennt nur Variablennamen. Kein Release-Artefakt wurde signiert oder geprüft. |
| Health Connect / Garmin-Integration | **Nicht live nachgewiesen** | Die isolierte AVD enthält Health Connect Controller- und Backup/Restore-Pakete, aber keinen echten Garmin-Lauf, kein Testkonto und keine realen Health-Connect-Datensätze. Paketpräsenz belegt keinen Import-/Rechtepfad. |

Die isolierte AVD heißt `LAP_Isolated_Test` (Android 16/API 36, Google-Play-Systemabbild). Alle adb-Schreibbefehle und Tests nannten ausdrücklich `-s emulator-5556`; das persönliche `Pixel_9` auf `emulator-5554` wurde nicht installiert, gelöscht oder instrumentiert. Die AVD-Appdaten wurden vor dem vollständigen Lauf zurückgesetzt. Gradle verwendete einen projektlokalen Cache und Kotlin im Prozess; sichtbar waren harmlose Zugriffs-/Pfadwarnungen zum globalen Android-Analytics-Verzeichnis und zu einem doppelten platform-tools-Pfad. Es wurden keine SDK-Pakete heruntergeladen.

Dieser Abschnitt hält ausschließlich den Fehlersuchstand vor der nachfolgenden Reparaturabnahme fest. Seine fehlgeschlagene Instrumentation ist kein aktueller Status. Die Roh-Ausgabe liegt in den ignorierten `app/build/lap-screen-review/`-Logs und ist nicht Bestandteil des Quell-Snapshots.

### Release- und Updatepfad: weiterhin offen

- `applicationId`: `de.run.app`; `versionCode`: `1`; `versionName`: `0.1.0`.
- Die lokalen Signier-Variablen `LAP_RELEASE_STORE_FILE`, `LAP_RELEASE_STORE_PASSWORD`, `LAP_RELEASE_KEY_ALIAS` und `LAP_RELEASE_KEY_PASSWORD` sind nicht gesetzt. Es wurde kein dauerhafter Schlüssel erzeugt oder gesucht.
- Vorhanden ist nur `app-release-unsigned.apk`. `apksigner verify --verbose` meldet `DOES NOT VERIFY`; Signatur, Installation und Updatepfad sind nicht nachgewiesen.
- Außerhalb generierter `build`-Ordner wurde kein APK/AAB-Vorgängerartefakt gefunden. Ein passendes älteres signiertes Paket mit gleicher App-Identität/Signatur und niedrigerem Versionscode fehlt.
- Der Git-Arbeitsbaum ist weiterhin vollständig unversioniert und hat keinen Commit. Die lokale ACL von `.git` verweigert Schreibzugriff; daher war trotz der im Auftrag erlaubten Baseline-Erstellung kein Commit möglich. Es wurde nichts gestaged.

### Frühere nächste Schritte (durch Reparaturabnahme superseded)

1. Die 10 fehlgeschlagenen Instrumentation-Flows anhand der genannten Semantikfehler prüfen; echte UI-Defekte minimal beheben und die komplette Suite erneut ausführen.
2. Einen isolierten Health-Connect-Anbietertest für nicht verfügbar, verweigert/entzogen, leer und erneuten Import aufbauen; synthetische Ergebnisse als solche kennzeichnen.
3. Eine vorhandene Signierdatei und ihre Zugangsdaten lokal gemäß `docs/RELEASE.md` bereitstellen; danach signierten Release-Build und Signaturprüfung ausführen.
4. Für den Update-Nachweis ein älteres signiertes Paket derselben App-Identität und Signatur mit niedrigerem Versionscode bereitstellen; Daten nach dem Upgrade prüfen.
5. Garmin-Connect-Live-Test mit Testkonto und Uhr auf einem getrennten Gerät durchführen sowie die Ausführung des Hintergrundabrufs nachweisen.

## Reparaturabnahme vor persönlichem Handytest vom 10. Oktober 2026

Die Ausgangsfehler wurden auf dem isolierten Emulator `LAP_Isolated_Test` (Android 16/API 36, AVD serial `emulator-5556`) anhand ihrer Compose-Semantikursachen behoben. Das persönliche `Pixel_9` und ein Handy wurden nicht verändert.

### Ursachen und Korrekturen

| Fehlerbild | Ursache und Korrektur | Nachweis |
|---|---|---|
| Fortschrittsdiagramm im Test nicht erreichbar | Das Diagramm war nicht komponiert, solange es außerhalb der LazyColumn lag. Ein stabiler Listen-Tag erlaubt jetzt den Scroll zum tatsächlichen Eintrag; die Werte-/Bereichsassertions blieben unverändert. | `progressPeriodSelectionChangesTheSelectedRange` besteht. |
| Designauswahl im Profil nicht erreichbar | Anders als bei den übrigen Fällen war dies ein echter UI-Fehler: große Schrift konnte die Theme-Auswahl abschneiden und es gab keinen Scrollcontainer. Das bestehende Profil ist jetzt scrollbar; Theme-Auswahl und Speichern bleiben erhalten. | `themeCanBeChangedFromProfile` besteht; visuell bei 1,3-facher Schrift geprüft. |
| Garmin-, Verlauf- und Bereitschaftsinhalte im Test nicht sichtbar | Tests suchten Inhalte außerhalb des Viewports bzw. prüften vor beendetem Navigationswechsel. Sie scrollen nun im echten Container und warten auf den Zielscreen. | Garmin-Demo, Verlauf, Berechtigungszustand und Navigation bestehen in beiden Gesamtläufen. |
| Flexible Zeitoption „Noch unklar“ nicht sichtbar | Die Option lag unterhalb des Test-Viewports. Der Test scrollt zu ihr; es wurde keine Eingabe und keine Funktion entfernt. | `flexibleAvailabilityAsksOnlyForTimeLimitsAndNoWeekDates` besteht. |
| Trainingsdetails-Abschnitt nicht sichtbar | Der Test erreichte den Ablaufabschnitt nicht, bevor er ihn abfragte. Er scrollt nun gezielt dorthin und prüft weiterhin Inhalt und Uhr-Hinweis. | `TrainingDetailsScreenTest` besteht. |
| Readiness-Demo vor Sichtbarkeit/Navigationsende geprüft | Der Heute-Eintrag wird vor dem Tap ins Bild geholt; die Demo wird erst nach sichtbarem Erholungsbildschirm abgefragt. | `readinessDetailsCanShowDebugOnlyGarminDemoData` besteht. |
| Einmaliger Activity-Lifecycle-Fehler in der Laufanalyse | Im fokussierten Klassenlauf nicht reproduzierbar; keine Assertions entfernt oder gelockert. | Analyse-/Feedbacktests bestehen in beiden vollständigen Läufen. |

Keine Tests wurden gelöscht, deaktiviert oder übersprungen. Die ursprünglichen fachlichen Assertions blieben bestehen; die Anzahl stieg durch den zusätzlichen Persistenztest auf 34.

### Abschlussprüfungen

| Prüfung | Ergebnis |
|---|---|
| `:app:testDebugUnitTest --rerun-tasks` | **65/65 bestanden**, 0 Fehler und 0 Übersprünge. |
| Vollständige `:app:connectedDebugAndroidTest`, Lauf 1 | **34/34 bestanden**, 0 Fehler/Übersprünge; `app/build/lap-screen-review/repair-final-full-1.log`; 20m20s. |
| Vollständige `:app:connectedDebugAndroidTest`, Lauf 2 | **34/34 bestanden**, 0 Fehler/Übersprünge, unveränderter Code; `app/build/lap-screen-review/repair-final-full-2.log`; 5m43s. |
| `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug --rerun-tasks` | **Erfolgreich**; Lint: 0 Fehler, 18 Warnungen. Keine pauschale Warnungs- oder Versionsbereinigung. |
| Room-Lauf und Rückmeldung nach echtem Prozessende | **Bestanden:** synthetischer Lauf mit Anstrengungs-/Körperrückmeldung in Room geschrieben; Ziel-App per `am force-stop` beendet und kalt gestartet; Datensatz separat erneut gelesen und geprüft, Testzeile anschließend entfernt. Die Prüfung lief außerhalb des Instrumentation-Prozesses. |
| Onboarding, Ziel und Theme nach Prozessende | **Manuell bestanden:** synthetische Einrichtung und Theme gespeichert, App-Prozess beendet und neu gestartet; Heute-/Planansicht, Halbmarathontermin/Ziel und Theme blieben erhalten. Screenshots sind in `app/build/lap-screen-review/`. |
| Finales `LAP-test.apk` | SHA-256 `C68BAF67A6C656114B632E3F0E7329E953D015E4318B2DF2FFBFA32F5B4C5F9E`; Debug-Signatur v2 verifiziert; Paket `de.run.app.debug`, Version `0.1.0`. Exakt dieses APK auf `LAP_Isolated_Test` installiert; Kaltstart meldete `Status: ok`, `LaunchState: COLD`; erneuter Kaltstart im Flugmodus gelang. |
| Anzeige/Schrift | Screenshots von Begrüßung, Heute, Plan, Fortschritt, Ich/Garmin, Trainingsdetails, Erholung, Kraft, Zielbearbeitung und Verfügbarkeit bei 720×1600 und 1080×2424, Schriftgröße 1,3. Screens: `app/build/lap-screen-review/`. Die separate echte Live-Laufansicht war kein Teil dieses Reparaturumfangs. |

### Assets und verbleibende externe Nachweise

- Verwendete Rastericons wurden pixelgenau mit `docs/design-assets/icons-source/` verglichen: die vier Navigation-Icons (ausgewählte und nicht ausgewählte Zustände), Lauf-/Kraft-/Intervall-/Langlauf-/Pause-/Erholungssymbole, Puls/Health-Check, Garmin-Import/Sync/Uhr und App-Symbol stimmen mit den jeweiligen Quellen überein. `garmin.png` bleibt das separate Uhrenbild. Es wurden keine Originalassets gelöscht oder pauschal ersetzt.
- Der Profil-Scrollfehler bei großer Schrift wurde behoben. Die Screenshots bestätigen die vorhandenen Ansichten und lesbare Inhalte; dies ist kein Pixel-für-Pixel-Abgleich jedes Screens gegen alle externen Theme-Vorlagen.
- Echter Garmin-Liveimport, reales Provider-/Berechtigungsentziehen und Hintergrundsync wurden ohne Testkonto/-anbieter nicht vorgetäuscht. Import-, Duplikat-, Update- und Feedbackregeln wurden mit synthetischen Daten geprüft. Das APK selbst startet im isolierten Emulator ohne Netzwerk.
- Die installierbare Debug-Datei liegt im nachweislich vorhandenen Projektordner unter `LAP-test.apk`; es wurde kein APK im übergeordneten Desktopordner überschrieben. Kein Handy wurde installiert oder verändert. Diese Übergabe ist eine Debug-Testversion, keine Store-/Release-Freigabe.

Die vollständigen Rohprotokolle bleiben unter dem ignorierten `app/build/lap-screen-review/`-Pfad; sie sind nicht Teil des Quell-Snapshots.

## Freigabeeinschätzung

Die lokale Abnahme für einen **kontrollierten persönlichen Debug-Handytest** ist bestanden: 65 Unit-Tests, zwei vollständige Instrumentation-Läufe mit je 34/34 bestandenen Tests, Build, Lint, Iconvergleich, Prozesspersistenz und APK-Installation/Kaltstart auf der isolierten AVD. Der historische Stand mit 10/33 Fehlern wurde durch die dokumentierten gezielten Korrekturen ersetzt. Das APK ist signiert mit dem Android-Debugschlüssel, nicht als Release; ein älteres kompatibel signiertes Paket für einen Update-Test fehlt. Echter Garmin-/Health-Connect-Livebetrieb ist nicht belegt und muss beim persönlichen Test mit freigegebenem Anbieter/Konto separat geprüft werden. Softwaretests validieren keine wissenschaftlich optimale Trainingssteuerung.
