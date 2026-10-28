# Donut Essentials — Changelog

## [3.0.0] — 2026-10-28 · The All-in-One Update

A full rebuild into an all-in-one essentials plugin. The complete notes are in
[DonutEssentials-3.0.0-PatchNotes.md](DonutEssentials-3.0.0-PatchNotes.md).

### Added
- Modules (each can be switched off): teleports (TPA with buttons, `/back`, warmups, tiers, combat tags), homes,
  warps and spawn, random teleport, kits (full item data, menus, previews), economy (Vault provider, `/pay`,
  `/baltop`, offline payment notices), chat (formats, mentions, `[item]`, filters, announcements, `/nick`), messages
  and mail, AFK, staff tools (vanish, `/invsee`, `/whois`), utilities, a player hub and personal settings, rules with
  an accept screen (native dialogs on 1.21.7+), server links and MOTD.
- Moderation: history in the database, warning escalation, alts and ban-evasion alerts, freeze, reports with a menu,
  staff chat, command spy and Discord webhook logs.
- `commands.yml` (rename, switch off, aliases; vanilla commands left alone), command rules (cooldowns and costs for any
  command), SQLite or MySQL/MariaDB storage.
- `/donutessentials import essentials` (EssentialsX balances, homes, nicknames, ignores, warps, spawn, kits and bans).
- PlaceholderAPI expansion, developer API with nine events.
- Gradle build, cross-version smoke matrix with bots (Paper and Folia 1.20.1 – 26.3), CI and release workflows.

### Changed
- Built with Gradle; one jar for Paper, Folia and Purpur 1.20.1 – 26.3 on Java 17+.
- 2.x punishments, IP locks and the maintenance whitelist move into the database on the first start; `config.yml` and
  `messages.yml` are upgraded with backups.
- Login checks (bans, maintenance, IP lock) run before the player joins.

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
