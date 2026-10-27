# Garmin- und Gesundheitsdaten

## Datenwege

Der Lauf wird ausschließlich auf der Garmin-Uhr gestartet: **Aktivitäten → Laufen**. Danach synchronisiert die Uhr mit Garmin Connect. LAP startet, steuert oder beendet keine Aktivität.

## Connect-IQ-Live-Coach

Das Connect-IQ-Projekt `connect-iq/LapLiveDataField` ist als Datenfeld für die vívoactive 5 angelegt. Es liest Timer, Timerstatus, Distanz, Geschwindigkeit und Puls aus `Activity.Info` während der nativen Laufaufzeichnung. LAP verwendet ausschließlich das offizielle Garmin Connect IQ Companion SDK für Android; Garmin Connect Mobile vermittelt die Nachrichten zwischen Uhr und LAP. Es gibt keine direkte zweite Bluetooth-Verbindung und keine Garmin-Schreibzugriffe.

Das versionierte Protokoll v1 sendet höchstens eine Nachricht gleichzeitig und speichert keine Warteschlange. Es verwirft Zwischenstände während eines laufenden Sendens. Fehlende Sensorwerte werden als fehlend übertragen, veraltete Pakete, falsche Versionen und wiederholte Sequenzen werden verworfen. Android aktualisiert den vorhandenen `LiveRunCoach` nur mit gültigen Garmin-Paketen. Der Android-Foreground-Service hält Empfang und MP3-Ausgabe bei gesperrtem Bildschirm aktiv; der Android-Emulator verwendet im Debug-Build die offizielle TETHERED-Verbindung zum Connect-IQ-Simulator.

Für eine ausgewählte Laufeinheit übergibt Android über die offizielle Connect-IQ-App-Nachricht deren stabile Trainings-ID und geplante Dauer. Das Datenfeld legt dieses Zeit-Ziel auf der Uhr ab, bestätigt ID und Dauer an LAP und kann den Fortschrittsring danach ohne Telefonverbindung aus der tatsächlichen Timerzeit berechnen. Der Feldzustand verbraucht das Ziel nach Ende der Aktivität; ein weiterer Lauf erhält ohne neue LAP-Übergabe kein altes Zeit-Ziel. Der Android-SDK-Sende-Callback gilt nur als „gesendet“; „von der Uhr übernommen“ wird erst nach der Rücknachricht angezeigt. Die aktuellen Planeinheiten enthalten Zeitziele, keine individuelle Soll-Distanz; deshalb wird keine Distanz aus einer Dauer abgeleitet.

**Transport ist erst nach einem echten Paket von vívoactive 5 zu LAP nachgewiesen.** Garmin Connect Mobile ist zwingend erforderlich. Der Companion SDK-Aufruf und die Garmin-Callback-Bestätigung allein beweisen nicht, dass die Nachricht bei LAP angekommen ist. Es gibt Berichte über Connect-Mobile-Versionen, die Watch-zu-Phone-Nachrichten trotz erfolgreicher Übertragungsbestätigung nicht an Companion-Apps weiterreichen; reale GCM-Version, Aktualisierungsrate, Hintergrundempfang, Kopfhöreraudio und EMUI-Energiesparen bleiben deshalb Hardwaretestpunkte. Für einen echten Test muss LAP Live auf der Uhr installiert und als Datenseite in der normalen Laufaufzeichnung ausgewählt werden.

Der offizielle Standardweg bleibt Garmin-Uhr → Garmin Connect → Health Connect → `HealthConnectRepository` → Domain → lokale Ablage → ViewModel → UI.

Für die ausschließlich private LAP-Installation ist zusätzlich ein explizit freigegebener, ausschließlich lesender Garmin-Connect-Webservicezugriff zulässig. Mit ausdrücklicher Freigabe der Kontoinhaberin darf er Aktivitäten suchen und Originaldateien herunterladen sowie verfügbare tägliche Schlaf-, Ruhepuls-, nächtliche HRV-/HFV-, Stress- und Schrittwerte lesen. Er läuft direkt auf dem Android-Gerät, speichert abgerufene Gesundheitswerte lokal und verwendet keine Cloud-Komponente. Schreibzugriffe, Änderungen, Löschungen, Archivierung oder das Markieren als synchronisiert bleiben verboten. Dieser Weg ist inoffiziell, kann jederzeit durch Garmin geändert oder blockiert werden und ist kein Ersatz für den offiziellen Health-Connect-Weg.

Der lesende Gesundheitsabruf verwendet ausschließlich dokumentierte Tagesendpunkte für Schlaf, Ruhepuls, nächtliche HRV-Zusammenfassungen, Stress und Schritte. Werte werden über ihr Garmin-Kalenderdatum zugeordnet, validiert und nur lokal gespeichert. Endpunkte werden sparsam und mit Pausen aufgerufen; 403, 429, MFA, CAPTCHA oder andere Zugangssperren werden respektiert und beenden den Ablauf ohne Umgehungsversuch. Keine TLS-Fingerprint-Rotation, Bot-Challenge-Umgehung, HTML-Login-Scraping oder automatische Strategie-Fallbacks. Referenz: Garmin Connect Personal API Swagger-Dokumentation und https://github.com/garmin/connect-personal-api-python/blob/main/README.md

Garmin-Passwörter werden nur als kurzlebige Eingabe verwendet und nie gespeichert. Falls die Sitzung dauerhaft benötigt wird, verschlüsselt LAP die Tokens mit einem Android-Keystore-Schlüssel. Diagnoseprotokolle enthalten nur Vorgang, Phase und Fehlercode, niemals Kontodaten, Tokens, Antworten, Aktivitätswerte oder FIT-Inhalte.

Ein ausgewähltes Garmin-Gesamtbackup wird direkt aus Androids Dateiauswahl verarbeitet. LAP öffnet ZIP-Dateien ohne manuelles Entpacken, einschließlich begrenzt verschachtelter ZIPs. FIT-Dateien werden mit dem Garmin-FIT-Decoder samt Integritätsprüfung gelesen; GPX und TCX werden mit einem XML-Parser gelesen, der DTDs und externe Entitäten ablehnt. Die Originaldateien werden unverändert über SHA-256 im privaten App-Speicher abgelegt. Beschädigte Einträge werden übersprungen, gültige Aktivitäten bleiben importierbar. Einträge anderer Kategorien werden in datensparsamen Zählgruppen wie Schlafdaten, Gesundheitsdaten, Körperwerte oder Zusatzdaten ausgewiesen; nicht unterstützte Messwerte werden nicht stillschweigend als importiert dargestellt.

Die Aktivitätshistorie umfasst Laufen, Gehen, Radfahren, Krafttraining und Sonstiges. Nur `RUNNING` fließt in Laufbelastung und Laufplan ein; die anderen Aktivitäten bleiben im Verlauf sichtbar. Duplikate zwischen FIT/Backup und Health Connect werden nur bei passendem Typ, Startzeit, Dauer und vorhandener ähnlicher Distanz zusammengeführt. Bei unsicherer Übereinstimmung bleiben Einträge getrennt. Feedback und lokale Nutzerkorrekturen werden beim Deduplizieren erhalten.

Der optionale private Garmin-Connect-Lesezugriff kann seitenweise Aktivitätslisten lesen und Originaldateien nacheinander herunterladen. Er filtert nicht nach einer einzelnen Sportart. Anfragen sind serialisiert und pausiert; bei 403/429 wird abgebrochen, ohne Umgehungsversuch. Erfolgreiche frühere Dateien bleiben gespeichert und ein späterer manueller Versuch kann fehlgeschlagene Aktivitäten erneut aufnehmen. Der Zugriff bleibt inoffiziell und kann von Garmin jederzeit geändert oder blockiert werden; eine echte Kontoanmeldung oder vollständige Live-Synchronisierung wurde vor einer ausdrücklichen Nutzerfreigabe nicht durchgeführt.

LAP bietet keine Installation oder Einrichtung einer Drittanbieter-Brücke an. Bereits vorhandene Health-Connect-Datensätze behalten ihre erkannte Herkunft, damit alte Importe nachvollziehbar bleiben. Für einen unabhängigen lokalen Import kann der Nutzer auf der Garmin-Seite ein Garmin-Backup oder eine Aktivitätsdatei auswählen. LAP prüft Dateityp und Prüfsumme, übernimmt unterstützte Aktivitäten und speichert den Import lokal idempotent.

Die angezeigte Health-Connect-Herkunft benennt ausschließlich die von Health Connect gemeldete schreibende App (`DataOrigin`). Bei unbekannten Paketnamen zeigt LAP „Unbekannte Quelle“. Ein Health-Sync-Datensatz beweist nicht, dass die ursprüngliche Uhr Garmin war. Fehlende Distanz- oder Pulswerte bleiben leer.

Bei einem wahrscheinlichen FIT-/Health-Connect-Duplikat vergleicht LAP Startzeit, Dauer und Distanz konservativ. Nur passende Datensätze werden zusammengeführt; die bestehende Quellidentität und das Nutzerfeedback bleiben erhalten. Vorhandene Messwerte werden nur mit tatsächlich übermittelten Werten ergänzt. Da FIT-Dateien und Health Connect keine gemeinsame Lauf-ID bereitstellen, kann die Zuordnung bei abweichenden Zeitstempeln, fehlender Distanz oder stark abweichenden Messungen nicht garantiert werden.

Health Connect wird ausschließlich über ein dediziertes Repository angesprochen. Compose und ViewModels kennen Health Connect nicht. Garmin Connect ist nicht die Datenbank der App. FIT-Dateien werden über Androids Dokumentauswahl lokal in ein separates Import-Repository eingelesen; Garmin-Zugangsdaten werden nicht verarbeitet.

## Verfügbarkeit

Garmin Connect teilt ausgewählte Daten einseitig mit Health Connect und unterstützt diesen Weg laut Garmin ab Android 14. Garmin nennt unter anderem Aktivitätsdistanz, Herzfrequenz, Tempo/Geschwindigkeit, Schritte, Höhenmeter, Kalorien, Schlafphasen, Gewicht und Körperfett. Nicht jeder Datensatz enthält alle Werte. Ein vollständiges Training mit GPS-Spur wird nicht vorausgesetzt.

LAP unterstützt Android 10 (API 29) und neuer. Garmin Connects direkte Health-Connect-Freigabe steht laut Garmin ab Android 14 zur Verfügung. Auf älteren Versionen bietet LAP stattdessen den Garmin-Abgleich und den lokalen Import eines Backups oder einer Aktivitätsdatei an. Health-Connect-Daten anderer Apps können weiterhin eingelesen werden, sofern Anbieter, Health Connect, Google Play-Dienste und die jeweilige Freigabe verfügbar sind.

## Rechte und Fehlerzustände

- Nur notwendige Leserechte anfordern und Zweck verständlich erklären.
- Für den Import ist nur die Freigabe für Laufaktivitäten erforderlich. Strecken- und Pulswerte liest LAP ergänzend, wenn die jeweilige Freigabe vorliegt; ihr Fehlen verhindert den Laufimport nicht.
- Die zusätzliche Freigabe für ältere Gesundheitsdaten wird für den Verlauf benötigt; ohne sie begrenzt Health Connect das Lesen fremder Daten auf 30 Tage. LAP kürzt den Abruf in diesem Fall auf die verfügbaren 30 Tage, statt an den älteren Einträgen zu scheitern.
- Die Garmin-Seite behauptet keine direkte Uhrenverbindung. Sie zeigt, ob Garmin-Läufe eingelesen wurden, wann LAP zuletzt erfolgreich nachgesehen hat und ob ältere Läufe noch nicht freigegeben sind. Ohne Freigabe für ältere Daten bedeutet ein leerer Verlauf ausdrücklich nicht, dass es keine älteren Läufe gibt.
- Health Connect fehlt: verständlichen Hinweis zeigen; App-eigener Trainingsplan bleibt benutzbar.
- Berechtigung fehlt oder wurde widerrufen: Status aktualisieren und erneut um Freigabe bitten.
- Garmin hat noch nicht synchronisiert: leeren Zustand mit Hinweis auf Gerätesynchronisierung.
- Health Connect ist vorübergehend nicht erreichbar: lokale Werte und Plan weiter anzeigen; erneuten Versuch ermöglichen.
- Wenn Huawei EMUI den Health-Connect-Lesedienst im Hintergrund blockiert, öffnet LAP die Health-Connect-Einstellungen. Nach der Rückkehr prüft LAP erneut.
- Späte und geänderte Einträge anhand ihrer Quellidentität aktualisieren.
- Daten mit identischer Quellidentität nicht doppelt anlegen.
- LAP liest beim Zurückkehren in die App sowie beim Öffnen von Garmin Connect in LAP und des Laufverlaufs die jüngsten Aktivitäten erneut ein. Gleichzeitige Anfragen werden zusammengefasst. Die Garmin-Seite bietet zusätzlich „Jetzt prüfen“ und zeigt das Ergebnis sowie die zuletzt lokal verfügbaren Laufdaten.
- Beim Zurückkehren in LAP wird ein neuer Garmin-Lauf mit vorhandenen Health-Connect-Werten einmalig in einer Auswertung gezeigt. Distanz, Dauer, Durchschnittspace und Durchschnittspuls werden nur aus diesem Lauf übernommen. Eine GPS-Streckenkarte ist nicht Teil des erwarteten Datensatzes und wird nicht erfunden.
- Die Heute-Ansicht fragt die jüngste Schlafsitzung und den jüngsten Ruhepuls separat ab. Beide Angaben können fehlen, auch wenn Laufdaten vorhanden sind. Die Freigaben bleiben unabhängig von der Freigabe der Laufdaten.
- Wenn der Nutzer die zusätzliche Freigabe für Abrufe im Hintergrund erteilt, prüft Android regelmäßig (etwa alle sechs Stunden) die letzten 90 Tage auf neue oder geänderte Läufe. Android bestimmt den genauen Zeitpunkt. Ohne diese Freigabe läuft nur der Abruf beim Öffnen der App.
- Importierte Läufe bleiben durch die von Health Connect gemeldete Quelle beziehungsweise „FIT-Datei“ erkennbar. Der lokale Verlauf wird durch erneute Importe anhand der Quellidentität aktualisiert; FIT-/Health-Connect-Dubletten werden nur bei ausreichend ähnlichen Messdaten zusammengeführt.

## Geräteprüfung 2026-10-11

ADB bestätigte auf dem verbundenen Huawei P30 Lite (MAR-LX1B, EMUI 12.0.0) Android 10/API 29. Garmin Connect, Health Sync 7.9.2.8 und Health Connect 2026.09.03 sind installiert. Der Health-Connect-Status meldete den Anbieter als verfügbar. EMUI blockierte zunächst den Start des Health-Connect-Lesedienstes; nach dem Öffnen der Health-Connect-Einstellungen funktionierte die Dienstverbindung. LAP hatte Lesezugriff auf Laufaktivitäten und den vollständigen Verlauf. Eine gezielte Health-Sync-Abfrage über höchstens einen Datensatz der letzten 365 Tage fand keinen Lauf. Damit ist die Übertragung durch Health Sync auf diesem Gerät weiterhin nicht nachgewiesen. Es wurden keine Laufmesswerte protokolliert. Das P30 wurde nicht zurückgesetzt.

Isolierte Gerätesuite: Workout-Repository/FIT-Deduplizierung 6/6 auf P30 und Pixel; Android-Back-Dispatcher-Weg Trainingsplan → Garmin → Profil 1/1 auf beiden Geräten. Der automatisierte EMUI-Edge-Wisch wurde auf dem P30 nicht als Back-Geste zugestellt (gleiche Testsequenz auf Pixel erfolgreich); Zurück-Pfeil und Dispatcher bestehen. Pixel_9 meldet Android 16/API 36 mit Codename CANARY und ist daher kein stabiler Android-Release-Nachweis. Garmin → Health Sync → Health Connect → LAP bleibt auf dem P30 unbewiesen, bis Health Sync konfiguriert und ein freiwillig bereitgestellter Testlauf importiert wurde.

## Sicherheitsgrenzen des privaten Lesezugriffs

- Nur Garmin-eigene HTTPS-Hosts und lesende Aktivitäts-/Download-Requests sowie die ausdrücklich freigegebenen Tagesabrufe für Schlaf, Ruhepuls, HRV, Stress und Schritte verwenden; keine anderen Garmin-Datenbereiche anfragen.
- Authentifizierung erfolgt auf dem Gerät durch die Person, der das Konto gehört. Fehlgeschlagene Standardanmeldung, CAPTCHA, MFA, Sperre oder Rate Limit wird nicht umgangen.
- Kein Credential- oder Token-Logging, keine Klartextdateien und keine Serverweitergabe. Passwortfelder werden nach dem Request geleert. Erneute Anmeldung ist jederzeit möglich.
- Nur GET-Aufrufe für Aktivitätssuche und Originaldatei-Download. Authentifizierungs- und Token-Erneuerungs-POSTs dürfen ausschließlich Sitzungstokens erstellen/erneuern; sie ändern keine Aktivitätsdaten. Keine Aktivitäts-Schreib-, Archivierungs-, Status- oder Löschoperationen.
- Importidentität: Garmin-Aktivitäts-ID und FIT-SHA-256. Vorhandene Läufe, Feedback und Krafttraining bleiben erhalten; Änderungen sind additiv und Duplikate werden nicht erneut gezählt.
- Vor echtem Konto-Zugriff werden Installation und Kontozugriff gesondert freigegeben. Simulationen und Tests mit synthetischen Daten gelten nie als echter Import.
- Wenn Garmin Connect den direkten Zugriff technisch blockiert, bleibt der offizielle Health-Connect-Weg beziehungsweise lokaler FIT-Import die unterstützte Alternative. LAP meldet dann den konkreten Status, behauptet aber keine Verbindung.

## Umfang der aktuellen Integration

Der private Garmin-Connect-Leseabruf speichert zusätzlich tägliche Schlafdauer, Ruhepuls, nächtlichen HRV-Durchschnitt samt Garmin-Status und persönlichem Bereich, durchschnittlichen Stress sowie Schritte lokal. Fehlende Felder bleiben leer. Stress und Schritte werden als Kontext angezeigt, nicht zusätzlich in den Score gemischt. Für das autonome Signal nutzt das Modell bei ausreichender persönlicher Historie HRV; andernfalls Ruhepuls. Beide fließen nie gemeinsam ein. Die additive Datenbankmigration 11→12 ergänzt ausschließlich optionale Tagesfelder und erhält vorhandene Daten.

LAP liest freigegebene Laufdaten ein und sendet die ausgewählte Trainings-ID samt zeitbasiertem Soll-Ziel über Connect IQ an das Datenfeld. Es sendet keine Aktivitäten an Garmin Connect und startet, steuert oder beendet keine Uhrenaktivität.

Auf der Garmin-Seite kann der Nutzer die Uhr antippen, um LAPs verfügbare Health-Connect-Daten erneut lesen zu lassen. Der Ring dreht während dieses Abrufs, wird nach einem erfolgreichen Abruf grün mit Haken und zeigt bei fehlender Freigabe, nicht verfügbarem Provider oder Abruffehler rot mit X. „Health Connect geprüft“ bedeutet nur, dass LAP den lokalen Anbieter erfolgreich lesen konnte; es beweist weder, dass Garmin Connect synchronisiert hat, noch dass neue Läufe vorhanden sind. „Keine neuen Läufe gefunden“ bleibt daher ein eigener sichtbarer Status. Der lokale Dateiimport ist am Ende der Garmin-Seite verfügbar.

## Connect-IQ-Live-Transportprüfung 2026-10-18

- Windows Connect IQ SDK und Compiler: 9.2.0; `monkeyc` funktioniert und baut für `vivoactive5`.
- Der normale Datenfeldquellstand kompiliert als signiertes `.iq`-Paket. Für die erste Geräteprüfung wurde zusätzlich ein separater, signierter PING-only `.prg`-Build erzeugt. Er sendet genau einen Marker ohne Aktivitätswerte und ist nicht der normale Coach-Build.
- Der Simulator-Run bestätigte `Communications.transmit`-Aufruf mit Typ `PING` und drei Einträgen. Der Simulator blieb aktiv. In Android kam keine App-Nachricht an; die Android-Verbindung meldete „Looking for input“.
- Android Debug/TETHERED registriert jetzt sowohl die Manifest-UUID als auch die leere Simulator-ID. Beide Listener teilen Validierung und einen begrenzten 32-Einträge-Duplikatfilter. Beide Registrierungen wurden im Pixel-Log bestätigt. Wireless verwendet weiterhin ausschließlich die Manifest-UUID.
- Ergebnis: Die Nachricht geht nachweislich nach dem Aufruf von `transmit` und vor LAPs Android-App-Callback verloren. Ob Garmin den Socket-Write ausführt, ist mit dem Simulatorlog nicht nachgewiesen; daher ist der genaue Verlustpunkt innerhalb des Simulator-Sendepfads offen. Der gemeldete SDK-9.1/9.2-TETHERED-Fehler passt zum Befund, beweist ihn aber nicht allein.
- Der Pixel zeigt die Garmin-Live-Seite im wartenden Zustand; es wurde kein echter Transportnachweis erbracht. Auf der vívoactive 5 und dem P30 Lite wurde in dieser Prüfung nichts installiert.
- Der vorbereitete Hardware-PING benötigt die signierte `.prg`-Datei im Ordner `GARMIN/APPS` der per USB angeschlossenen Uhr. LAP muss gleichzeitig auf dem P30 Lite laufen; anschließend native Aktivität „Laufen“ starten und LAP Live als Datenfeld öffnen. Erst nach einem bestätigten PING wird der normale Live-Datenfeldbuild eingesetzt.

## PING-Fehlerdiagnose und Statusanzeige 2026-10-18

- Der PING-Testbuild enthält dieselbe 16-Byte-App-ID wie das Manifest (`7D574A829AAC4E83AF6A6E1F04E8A09B`); die Android-Konstante und `getApplicationInfo` verwenden dieselbe UUID. Ein App-ID-Konflikt ist damit nicht belegt.
- Android fragt `getApplicationInfo` erst nach `IQDeviceStatus.CONNECTED` ab. Erst wenn der SDK-Aufruf `registerForAppEvents` ohne Exception zurückkehrt, gilt der lokale Empfänger als registriert. Das ist ausdrücklich kein Beleg, dass Garmin Connect Mobile eine Watch-Nachricht weitergeleitet hat.
- Beim letzten P30-Anzeigezustand stand „Garmin-Uhr ist nicht verbunden“. In diesem Zustand war keine erfolgreiche Feldabfrage oder Empfängerregistrierung nachgewiesen; `getApplicationInfo`/`registerForAppEvents` werden nur nach der Geräteverbindung ausgeführt. Daher kann der ausgebliebene PING nicht als Reproduktion von CIQQA-4631 eingeordnet werden.
- Garmin-Forum CIQQA-4631 ist als „Acknowledged“ geführt. Der Bericht beschreibt für Garmin Connect Mobile 5.27.3/Android 14 und SDK 2.4.0, dass `Communications.transmit` auf der Uhr `onComplete` erhält, aber Android keinen `INCOMING_MESSAGE`-Broadcast sendet. Ein späterer Kommentar berichtet eine Reproduktion mit GCM 5.28.1, Android 16 und Garmins offiziellem Comm-Beispiel. LAP hat diese spezielle Situation noch nicht nachgewiesen.
- Die Uhrendiagnose zeigt nun Datenfeldaktivität und die Sendestatus-Stufen. „Callback OK“ bedeutet ausschließlich, dass die Garmin-Übertragungs-API einen Abschluss gemeldet hat; nur der Android-Nachrichtenzähler bestätigt, dass ein Ereignis LAP erreicht hat.
- Der LAP-Live-Bildschirm zeigt getrennt SDK-Bereitschaft, Uhrenverbindung, Feldabfrage, lokalen Listener-Aufruf, eingegangene Ereignisse, gültige PINGs und gültige Messpakete. Ein nächster Hardwareversuch ist nur aussagekräftig, wenn die Diagnose SDK „Bereit“, Uhr „Verbunden“, Datenfeld „Erkannt“ und Listener-Aufruf „Erfolgreich“ zeigt, bevor auf der Uhr der Lauf gestartet wird.
- P30, Uhr und Garmin-Connect-Aktivitätsabgleich waren für diese Offline-Änderung nicht verfügbar. Es gab keine neue Geräteinstallation und keinen weiteren echten Sendeversuch.
## Live-Verbindungsdiagnose Release 0.3.7

Der Release-Build verwendet `WIRELESS` über Garmin Connect Mobile; `TETHERED` und der leere App-ID-Testlistener sind auf den Debug-/Simulatorpfad beschränkt. Die normale Garmin-Live-Seite zeigt Transport, Connect-Mobile-/SDK-Bereitschaft, bekanntes Gerät und dessen Verbindungsstatus, die Prüfung des Datenfelds mit Manifest-App-ID, die lokale Registrierung des Nachrichtenempfängers sowie Nachrichten- und PING-Zähler.

Der PING ist erst startbereit, wenn WIRELESS, Connect Mobile, SDK, Uhrverbindung, Datenfeld und Listener-Aufruf erfolgreich angezeigt werden. Eine erfolgreiche lokale Registrierung oder Garmin-Sendebestätigung ist kein Empfangsnachweis; dafür muss LAP eine Nachricht zählen. Version 0.3.7 (versionCode 10) enthält keine Datenbankmigration. Ein echter erneuter Uhr-/P30-Test ist noch nicht erfolgt.
