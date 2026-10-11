# run project instructions

`run` is a personal Android app and is separate from every other project.

## Product and privacy
- User-facing language is plain German.
- Keep health data on the device. Do not add cloud services, unofficial Garmin APIs, scraping, reverse-engineered endpoints, or stored Garmin credentials.
- Garmin data enters through Garmin Connect and Health Connect. Keep Garmin-derived and app-owned data distinguishable.
- Never expose implementation terminology, exceptions, HTTP errors, or storage details in the UI.

## Architecture
- Keep presentation, application, domain, and data responsibilities separate.
- UI communicates through ViewModels and use cases/repositories; it never accesses Health Connect or storage directly.
- Preserve the existing architecture. Avoid duplicated business rules, unnecessary dependencies, and unrelated refactors.
- Before architectural changes, read `docs/ARCHITECTURE.md`.
- Before changing Garmin integration or health records, read `docs/GARMIN_INTEGRATION.md` and `docs/DATA_MODEL.md`.
- Before changing UI patterns, read `docs/UX_UI.md` and `docs/DESIGN_SYSTEM.md`.

## Quality and safety
- Keep local data intact across upgrades. Use additive migrations and verify them.
- Handle loading, content, empty, error, unavailable, and permission states where relevant.
- Consider accessibility and provide visible feedback for every action. Prefer undo to confirmation.
- Use stable dependencies. Before major upgrades, inspect breaking changes, run tests, and verify migrations and Health Connect behavior.
- Add focused unit, integration, and UI tests for changed behavior; run relevant tests, static analysis, and builds before completion.
- Inspect Git branch, HEAD, status, and recent commits before edits. Never reset, clean, stash, rebase, or pull without explicit instruction. Never push unless asked. Keep private data out of commits.
