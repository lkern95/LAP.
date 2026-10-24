# run project instructions

`run` is a personal Android app and is separate from every other project.

## Product and privacy
- User-facing language is plain German.
- Keep health data on the device. Do not add cloud services or third-party servers.
- A narrowly scoped, private-use, read-only Garmin Connect web-service client is permitted only for the account owner, when explicitly authorized for this LAP project. With the owner's explicit authorization, it may also read sleep, resting-heart-rate, and immediately necessary daily health metrics for local training feedback. It may never write, edit, delete, archive, or mark Garmin data as synchronized.
- Never bypass CAPTCHA, MFA, rate limits, bot challenges, or other access controls. Do not rotate TLS fingerprints, imitate unrelated devices to evade controls, scrape HTML login pages, or use fallback strategies intended to evade blocks. If Garmin rejects the standard login/API flow, stop and report the stage and reason code.
- Garmin passwords are transient input only and must never be persisted or logged. Persist session tokens only when necessary, encrypted with an Android Keystore-backed key. Keep Garmin-derived and app-owned data distinguishable.
- Keep the existing Garmin Connect → Health Connect path and local FIT import available; the private Garmin Connect client must not replace them.
- A user-selected Garmin backup may be processed locally from ZIP, FIT, GPX, or TCX. Preserve original bytes with SHA-256, cap archive expansion, reject unsafe XML, retain successful entries when another is damaged, and report unsupported categories.
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
