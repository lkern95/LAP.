# Garmin- und Gesundheitsdaten

## Verbindlicher Weg

Der Lauf wird ausschließlich auf der Garmin-Uhr gestartet: **Aktivitäten → Laufen**. Danach synchronisiert die Uhr mit Garmin Connect. LAP startet, steuert oder beendet keine Aktivität.

Garmin-Uhr → Garmin Connect → Health Connect → `HealthConnectRepository` → Domain → lokale Ablage → ViewModel → UI.

Auf Android 10 bis 13 kann Health Sync optional Garmin-Connect-Läufe in Health Connect schreiben. LAP liest solche Einträge mit der Herkunft „Health Sync“ ein, ohne diese Quelle als Garmin Connect auszugeben. Health Sync ist eine eigenständige Drittanbieter-App: Installation, Garmin-Anmeldung, Berechtigungen und mögliche Lizenzkosten liegen außerhalb von LAP. Laut Datenschutzerklärung nutzt Health Sync für manche Verbindungen Serverbenachrichtigungen und teils einen sicheren Proxy; dabei sollen Laufdaten nicht gespeichert werden, aber Daten können über den Dienst geleitet werden. Das muss der Nutzer vor Einrichtung wissen. Für einen unabhängigen Offline-Import kann der Nutzer eine FIT-Aktivitätsdatei unter Fortschritt auswählen. LAP prüft Dateityp und Prüfsumme, übernimmt nur Lauf-Sessions und speichert den Import lokal idempotent.

Die angezeigte Health-Connect-Herkunft benennt ausschließlich die von Health Connect gemeldete schreibende App (`DataOrigin`). Bei unbekannten Paketnamen zeigt LAP „Unbekannte Quelle“. Ein Health-Sync-Datensatz beweist nicht, dass die ursprüngliche Uhr Garmin war. Fehlende Distanz- oder Pulswerte bleiben leer.

Bei einem wahrscheinlichen FIT-/Health-Connect-Duplikat vergleicht LAP Startzeit, Dauer und Distanz konservativ. Nur passende Datensätze werden zusammengeführt; die bestehende Quellidentität und das Nutzerfeedback bleiben erhalten. Vorhandene Messwerte werden nur mit tatsächlich übermittelten Werten ergänzt. Da FIT-Dateien und Health Connect keine gemeinsame Lauf-ID bereitstellen, kann die Zuordnung bei abweichenden Zeitstempeln, fehlender Distanz oder stark abweichenden Messungen nicht garantiert werden.

Health Connect wird ausschließlich über ein dediziertes Repository angesprochen. Compose und ViewModels kennen Health Connect nicht. Garmin Connect ist nicht die Datenbank der App. FIT-Dateien werden über Androids Dokumentauswahl lokal in ein separates Import-Repository eingelesen; Garmin-Zugangsdaten werden nicht verarbeitet.

## Verfügbarkeit

Garmin Connect teilt ausgewählte Daten einseitig mit Health Connect und unterstützt diesen Weg laut Garmin ab Android 14. Garmin nennt unter anderem Aktivitätsdistanz, Herzfrequenz, Tempo/Geschwindigkeit, Schritte, Höhenmeter, Kalorien, Schlafphasen, Gewicht und Körperfett. Nicht jeder Datensatz enthält alle Werte. Ein vollständiges Training mit GPS-Spur wird nicht vorausgesetzt.

LAP unterstützt Android 10 (API 29) und neuer. Garmin Connects direkte Health-Connect-Freigabe steht laut Garmin ab Android 14 zur Verfügung. Auf älteren Versionen kann Health Sync als Drittanbieter-Brücke genutzt werden, muss aber separat installiert und eingerichtet werden. Dieser Weg wurde auf dem P30 Lite noch nicht nachgewiesen. Ohne Health Sync können FIT-Aktivitätsdateien lokal importiert werden. Health-Connect-Daten anderer Apps können eingelesen werden, sofern der Anbieter, Health Connect, Google Play-Dienste und die jeweilige Freigabe verfügbar sind.

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

## Verboten

Keine inoffiziellen Garmin-Endpunkte, Scraper, Reverse Engineering oder Garmin-Zugangsdaten. Falls eine direkte Integration später erforderlich wird, liegt sie hinter einer separaten Domain-Schnittstelle und ist nicht Voraussetzung für den bestehenden Datenfluss.

## Umfang der aktuellen Integration

LAP liest freigegebene Laufdaten ein. Im Projekt gibt es keinen Ablauf, der Trainingspläne oder Aktivitäten an Garmin Connect sendet. Die App startet und steuert keine Uhrenaktivität.

Auf der Garmin-Seite kann der Nutzer die Uhr antippen, um LAPs Health-Connect-Daten erneut lesen zu lassen. Der Ring dreht während dieses Abrufs, wird nach einem erfolgreichen Abruf grün mit Haken und zeigt bei fehlender Freigabe, nicht verfügbarem Provider oder Abruffehler rot mit X. „Health Connect geprüft“ bedeutet nur, dass LAP den lokalen Anbieter erfolgreich lesen konnte; es beweist weder, dass Garmin Connect synchronisiert hat, noch dass neue Läufe vorhanden sind. „Keine neuen Läufe gefunden“ bleibt daher ein eigener sichtbarer Status. Auf Android 10 steuert dieser Tipp Health Sync nicht fern; dessen Übertragung muss in Health Sync eingerichtet und gestartet werden.
