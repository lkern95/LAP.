# Sicherheit und Datenschutz

- Verarbeitung und Speicherung standardmäßig lokal.
- Keine Konten, Telemetrie, Werbung oder Cloud-Sicherung für Gesundheitswerte ohne ausdrückliche spätere Anforderung.
- Android-Cloud-Sicherung und automatische Geräteübertragung bleiben für LAP-Daten ausgeschlossen; die Regeln stehen in `app/src/main/res/xml/data_extraction_rules.xml`.
- Unter „Ich → Sicherung“ können LAP-eigene Daten manuell als passwortgeschützte Datei gespeichert und auf einem neuen Handy wiederhergestellt werden. Die Datei nutzt AES-GCM mit einem aus dem Passwort abgeleiteten Schlüssel. Das Passwort muss mindestens acht Zeichen lang sein und wird nicht gespeichert.
- Enthalten sind lokale Läufe einschließlich Rückmeldungen, Krafttrainings, Trainingsentscheidungen, Trainingsziele samt Verlauf, Trainingsprofil und Farbstil. Health-Connect-Freigaben und Systemberechtigungen werden nicht übertragen und müssen auf dem neuen Handy erneut erteilt werden.
- Vorhandene LAP-Daten werden beim Wiederherstellen nur nach einer ausdrücklichen Bestätigung ersetzt. Eine nicht lesbare, beschädigte oder nicht unterstützte Sicherung wird vor dem Schreiben abgelehnt.
- Beim Dateidialog wählt der Nutzer selbst den Speicherort. LAP lädt keine Sicherungsdateien hoch und verwendet dafür kein Konto oder einen eigenen Cloud-Dienst.
- Health-Connect-Leserechte auf tatsächlich verwendete Datentypen beschränken.
- Keine Werte, Namen, Prompts oder vollständigen Gesundheitsdatensätze in Logs.
- Keine Geheimnisse oder Zugangsdaten im Quellcode oder in Präferenzen ablegen.
- Lösch- und Berechtigungsänderungen verständlich und ohne Datenverlust für unabhängige App-Daten behandeln.
- Keine Garmin-Anmeldedaten anfordern oder speichern; keine nicht-offizielle Garmin-Schnittstelle verwenden.
- Gesundheitsdaten werden nur nach Health-Connect-Freigabe gelesen. LAP liest beim Start und beim Öffnen der Fortschrittsseite erneut. Eine optionale Hintergrundabfrage wird erst aktiviert, wenn dafür die zusätzliche Health-Connect-Freigabe erteilt wird; ohne diese Freigabe läuft kein Hintergrundabruf.
