# ADR-001: Health Connect als Garmin-Datenweg

- Status: angenommen
- Entscheidung: Die persönliche App liest Garmin-Daten ausschließlich aus Health Connect.
- Gründe: offizieller Garmin-Datenfluss, lokale Android-Schnittstelle, keine Garmin-Zugangsdaten in `run`.
- Folgen: Synchronisation ist abhängig von Garmin Connect, Android-Version und erteilten Rechten. Die App stellt nur bestätigte Datentypen dar.
