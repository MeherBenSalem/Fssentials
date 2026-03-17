# Fssentials - Complete Feature Reference

Fssentials is a production-ready moderation and admin utilities plugin for Paper (Folia-aware). This document enumerates every feature implemented in the current codebase so server operators can configure, use, and extend the plugin confidently.

---

## High-level Overview
- Folia-friendly scheduler utilities for safe async/global/entity dispatch (`FoliaScheduler`).
- Full-featured punishment system with persistent YAML storage and temporary punishments.
- IP-locking (per-account allowed IP entries + login-blocking) with admin commands and bypass permission.
- Maintenance subsystem with whitelist, MOTD sets, scheduling/timers and join notifications.
- Broadcast/announcement system using MiniMessage titles with optional configurable sound.
- Vanish and admin protections for hidden staff members.
- Inventory viewing and editing (InvSee) with Folia-safe snapshot/restore behavior.
- Central `MessageService` for configurable chat messages and prefix handling.

---

## Features (exhaustive)

**Punishment System**
- Supported punishment types: `KICK`, `BAN`, `MUTE`, `WARN`, `NOTE`, `IP_BAN`.
- Supports permanent and temporary punishments. Temporary durations parse via `TimeParser` and server-configurable time-layout tokens (e.g. `#default`).
- Each punishment stores: id, type, target UUID/name/ip, actor, reason, createdAt, expiresAt, active flag and silent flag.
- Storage: persisted to `punishments.yml` via `YamlPunishmentStorage` when `storage.persistence-enabled` is true.
- Management: create, deactivate (unpunish), change reason, delete record, list history, view active punishments, page helper utilities (`PunishmentManager`).
- Expiration: scheduled expiration runs on Folia async scheduler and notifies staff when punishments expire.

Commands (handled by `CommandRouter`):
- `kick <player> <reason...>` — immediate kick with enforcement message.
- `ban <player> <reason...>` — permanent ban (kicks online target).
- `mute <player> <reason...>` — mute (blocks chat via `PunishmentListener`).
- `warn <player> <reason...>` — adds a warn record; notifies target if online.
- `note <player> <note...>` — internal note record.
- `banip <player|ip> <reason...>` — IP ban (kicks matching IPs).
- `tempban/tempmute/tempwarn/tempipban <player|ip> <duration> <reason...>` — temporary punishments.
- `unban/unmute/unwarn/unnote/unpunish <id|player>` — remove punishments.
- `change-reason <id> <new reason>` — edit a punishment's reason.
- `warns <player>` / `notes <player>` — list warns/notes.
- `check <player>` — show UUID, IP, ban/mute status, counts.
- `banlist [page]` — list active bans (paginated).
- `history <player> [page]` — view player's punishment history (paginated).

Notes:
- Commands support a `-s` prefix to make an action silent (announce only to staff with the configured silent notify permission).
- Reason layouts can be referenced with `@layoutKey` to reuse predefined layouts from `config.yml` (`layouts.`).
- Time-layout tokens (e.g. `#default`) map to values in `time-layouts` config.

**IP Locking**
- Per-account IP lock storage in `ip-locks.yml` (`IpLockManager`).
- Admin commands to `set`, `remove`, `change`, and `check` an account's allowed IP.
- Normalizes IPs using Java networking APIs; invalid inputs are rejected.
- Login flow hooks: pre-login collects attempted IP and if mismatch is found the player is kicked (unless they have bypass permission).
- Configurable: `ip-lock.enabled`, `ip-lock.log-blocked-attempts`, `ip-lock.kick-message`.

**Maintenance Mode**
- Enable/disable maintenance mode programmatically or via commands.
- Whitelist management (add/remove/list) for players allowed during maintenance.
- MOTD sets: multiple message sets supported and editable using `/maintenance motd` and `setmotd`.
- Scheduling/timers: `starttimer`, `endtimer`, `schedule`, `aborttimer` to schedule enable/disable automatically (`MaintenanceTimer`).
- Join notifications: staff with `notify` permission receive join-attempt notifications during maintenance.
- Configurable messages and bypass/notify permissions via `MaintenanceConfig`.

Maintenance commands (via `maintenance` command):
- `maintenance on|off|reload`
- `maintenance add <player>` / `remove <player>` / `whitelist`
- `maintenance starttimer <minutes>` / `endtimer <minutes>` / `schedule <startMinutes> <durationMinutes>` / `aborttimer`
- `maintenance motd` / `maintenance setmotd <index> <line> <message>`

**Broadcast / Announcements**
- `/broadcast <title> [subtitle]` — sends a full-screen title to all players using MiniMessage formatting.
- Supports splitting title/subtitle by `|` or heuristics; configurable sound (`announcements.sound.*` in config).

**Vanish & Admin Tools**
- Toggle vanish with `/vanish` (visibility handled by `VanishService`).
- `AdminToolsListener` protects vanished staff from being interacted with or damaged.
- Vanish state is reapplied on join for session-owned vanished players.

**InvSee (Inventory View/Edit)**
- `/invsee <player>` opens a 54-slot GUI snapshot of the target player's inventory (uses `InvseeInventoryHolder`).
- Snapshot and restore are Folia-safe: inventory cloning happens on the target's entity scheduler and GUI open on the viewer's entity scheduler.
- Viewer edits are written back on close (invsee allows editing of player storage, armor, off-hand slots as described in the GUI layout).

**Message Service & Localization**
- `MessageService` loads `messages.yml` from the plugin folder; messages use a `prefix` and support placeholder replacement via `MessageService.get(key, placeholders)`.
- Error/usage/enforcement messages are all configurable in `messages.yml` and `config.yml`.

**Folia Scheduler Compatibility**
- `FoliaScheduler` centralizes Folia-friendly task dispatch: `runAsync`, `runAsyncTimer`, `runGlobal`, `runAtEntity`.
- All file I/O and long-running work is dispatched async; player-specific operations run on the player's entity scheduler.

**Storage Files Created/Used**
- `punishments.yml` — persisted punishment records (via `YamlPunishmentStorage`).
- `ip-locks.yml` — per-account IP lock entries.
- `messages.yml` — plugin messages loaded by `MessageService` (copied from plugin jar to data folder on first run).

---

## Commands (complete list registered at runtime)
- `kick`, `ban`, `mute`, `warn`, `note`, `banip`
- `tempban`, `tempmute`, `tempwarn`, `tempipban`
- `unban`, `unmute`, `unwarn`, `unnote`, `unpunish`
- `change-reason`, `warns`, `notes`, `check`, `banlist`, `history`
- `fssentials`, `systemprefs`, `vanish`, `offlinetp`, `playerlist`, `punishment`, `invsee`
- `maintenance` (separately registered with its own executor)
- `broadcast`, `iplock` (separately registered)

Refer to `plugin.yml` for the exact help/usage lines; `fssentials` command prints configurable help-lines from `config.yml`.

---

## Permissions (used in code)
- `fssentials.punish.kick` — kick players
- `fssentials.punish.ban` / `fssentials.punish.tempban` — ban players
- `fssentials.punish.mute` / `fssentials.punish.tempmute` — mute players
- `fssentials.punish.warn` / `fssentials.punish.tempwarn` — warn players
- `fssentials.punish.banip` / `fssentials.punish.tempipban` — IP ban
- `fssentials.punish.unpunish` — remove punishments
- `fssentials.punish.change-reason` — edit punishment reason
- `fssentials.view.banlist` / `fssentials.view.history` / `fssentials.view.check` / `fssentials.view.playerlist` — view lists/check info
- `fssentials.invsee` — view and edit player inventories
- `fssentials.vanish` — toggle vanish
- `fssentials.admin.reload` — reload plugin
- `fssentials.admin.systemprefs` — view system prefs
- `fssentials.maintenance.admin` — full maintenance administration
- maintenance bypass/notify permissions are configurable (defaults shown in `MaintenanceConfig`).
- IP-locking bypass permission: `plugin.iplock.bypass` (constant in `IpLockManager`).

Note: the plugin uses permission checks liberally; ensure your server's permission system maps the nodes to the desired roles.

---

## Configuration keys of interest (quick reference)
- `storage.persistence-enabled` — enable/disable punishment persistence.
- `permissions.silent-notify` — permission for staff to receive silent announces.
- `time-layouts` — named duration shortcuts (e.g. `#default`).
- `layouts` — reusable reason text layouts accessed via `@key`.
- `announcements.sound.enabled`, `announcements.sound.type`, `announcements.sound.volume`, `announcements.sound.pitch` — broadcast sounds.
- `maintenance.enabled`, `whitelist`, `motd` and messages under `messages.*` — maintenance control.
- `ip-lock.enabled`, `ip-lock.log-blocked-attempts`, `ip-lock.kick-message` — ip-lock config.

For exact keys and defaults, inspect `MaintenanceConfig`, `IpLockManager`, and `YamlPunishmentStorage` in source.