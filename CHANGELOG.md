# Donut Essentials — Changelog

## [2.0.0] — 2026-09-17

### Changed
- Rebrand from **Fssentials** to **Donut Essentials** (`dev.nightbeam.donutessentials`, plugin command `/donutessentials`, legacy alias `/fssentials`).
- License changed to **Apache License 2.0** with NOTICE and OSS contribution docs.
- Permission nodes use `donutessentials.*` with legacy `fssentials.*` compatibility in code checks and `plugin.yml`.
- Automatic migration of data from `plugins/Fssentials` when upgrading with an empty new data folder.

### Added
- Platform support matrix documented for Paper / Folia / Purpur **1.20.1–1.26.3** (Java 21+).
- bStats metrics and update-check hooks (see plugin configuration).

### Prior release (Fssentials)

**[1.1.2]** — 2026-09-04: Paper/Folia API 26.2 target; unit tests for time parsing and text helpers.
