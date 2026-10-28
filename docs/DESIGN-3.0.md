# Donut Essentials 3.0 "The All-in-One Update": design

The market analysis (`MARKET-ANALYSIS.md`) explains the why. This file is the how: architecture, conventions and the
feature list per module. It is the reference for anyone adding to the plugin.

## Platform

- Paper, Folia, Purpur and other Paper forks, Minecraft **1.20.1 – 26.3**, one jar.
- Compiled for **Java 17** against the Paper 1.20.1 API; `./gradlew compileMatrix` compiles the same code against
  1.20.6, 1.21.4, 1.21.11, 26.1.2, 26.2 and 26.3 to catch removed methods.
- Newer features are reached reflectively or through a separately compiled source set (`src/dialogs`, Paper's dialog
  API, loaded only on 1.21.7+). Server links (1.21+) are reflective.
- **Folia first**: the Bukkit scheduler never appears outside `scheduler/` (the `verifySourceSafety` task fails the
  build). Player state is touched on the player's thread (`scheduler.forPlayer`), blocks on their region, global
  state on the global region. Teleports are `teleportAsync`.
- Spigot/CraftBukkit: the plugin says it needs Paper and disables itself.

## Architecture

```
DonutEssentialsPlugin      lifecycle, services, module manager
  config/                  ConfigFiles (merge + 2.x upgrade), Messages (MiniMessage + & codes)
  scheduler/               FoliaScheduler
  storage/                 Database (Hikari: SQLite | MySQL/MariaDB), Schema (versioned), DataWriter (one DB thread)
  command/                 CommandRegistry (dynamic, commands.yml), EssCommand, Args
  menu/                    Menu, MenuManager, MenuConfig (menus.yml), ConfirmMenu
  player/                  PlayerData, PlayerService (loaded at pre-login, saved on change/quit), PlayerListener
  hook/                    Hooks, Vault chat, PlaceholderAPI, UpdateChecker, DiscordWebhook, Metrics
  module/                  Module, ModuleManager
  api/                     DonutEssentialsApi, events
  util/                    Text, Amounts, Durations, TimeParser, ItemBuilder, ItemCodec, Sounds, Locations, Perms
  <module packages>        teleport, homes, warps, rtp, kits, economy, chat, messaging, afk, moderation, vanish,
                           staff, maintenance, iplock, rules, utility, importer, placeholder
```

### Modules

`modules:` in `config.yml` switches each module. A disabled module registers **no commands and no listeners** and
runs no tasks. A module implements `Module`:

```java
public interface Module {
    String id();                         // "homes"; also its config section and messages section
    default List<EssCommand> commands() { return List.of(); }
    default List<Listener> listeners() { return List.of(); }
    void enable();                       // after storage is up; load caches here
    default void reload() {}             // /dess reload: re-read config and messages
    default void disable() {}            // save and cancel tasks
}
```

### Commands

Commands are registered at runtime in the server's command map (not `plugin.yml`), from `commands.yml`:

```yaml
tpa:
  enabled: true
  aliases: [tpask]
```

- `enabled: false` means the command is not registered at all.
- A label that a vanilla or Bukkit command already uses (`gamemode`, `give`, `tp`, `list`...) is only taken when it is
  in `commands.take-over-vanilla` in `config.yml`. Otherwise the vanilla command keeps the name and ours stays
  reachable as `/donutessentials:<name>`. Moderation commands (`ban`, `kick`, `msg`...) take over by default: vanilla
  `/msg` ignores mutes.
- Every command has a permission `donutessentials.command.<name>`; defaults live in `plugin.yml`.
- `EssCommand` gives subclasses `player(sender)`, `target(args, i)`, async offline lookups, usage and permission
  messages, and tab completion of online (non-vanished) player names.

### Messages

`messages.yml`, one top-level section per module plus `common`. MiniMessage, and `&` / `&#RRGGBB` codes work too.
Placeholders are `<name>` tags passed as key/value pairs; player-typed values are inserted as unparsed text. An empty
message sends nothing.

House style:

| Use | Colour |
| --- | --- |
| Text | `<gray>` |
| Names, values | `<white>` |
| Accent | `<#6bc6ff>` (blueberry icing) |
| Money | `<#ffc24b>` |
| Success | `<#7ee081>` |
| Error | `<#ff6b6b>` |
| Buttons | `<#7ee081><bold>[Accept]`, `<#ff6b6b><bold>[Deny]` with hover text |

Sentences are short and plain. No exclamation marks in errors.

### Storage

One database: SQLite (`plugins/DonutEssentials/data.db`) or MySQL/MariaDB with a table prefix. All access goes
through `DataWriter`, one thread, in order: changes are made in memory and queued; reads that must see earlier writes
(`query`) run on the same thread. One server per database.

Tables (prefix omitted): `meta`, `players`, `homes`, `warps`, `locations`, `kits`, `kit_claims`, `balances`,
`notices`, `mail`, `ignores`, `punishments`, `ip_history`, `ip_locks`, `reports`.

### Configuration upkeep

`config.yml`, `messages.yml`, `menus.yml` and `commands.yml` carry a `*-version` key. On every start, keys added by an
update are merged in without changing an admin's values. A 2.x `config.yml` (no `config-version`) is upgraded: its
values move to their 3.0 places and the old file is kept as `config-2.x.yml`; `messages.yml` is replaced and kept as
`messages-2.x.yml`. 2.x data (`punishments.yml`, `ip-locks.yml`, the maintenance whitelist) is imported into the
database once and the files renamed `*.imported`.

## Features by module

### teleport
- `/tpa <player>`, `/tpahere <player>`, `/tpaccept [player]`, `/tpdeny [player]`, `/tpacancel [player]`,
  `/tptoggle` (block requests). Requests carry clickable **[Accept] [Deny]**; they expire (60 s).
- `/back`: the last place you teleported from, or where you died (`donutessentials.back.death`).
- Shared teleport engine for every module: **warmup** shown as a title countdown, cancelled by moving a block or taking
  damage; **cooldowns** per kind (home, warp, spawn, tpa, back, rtp) with **rank tiers** (`teleport.tiers`, matched by
  `donutessentials.tier.<name>`); optional **costs**; `donutessentials.bypass.warmup|cooldown|cost`.
- **Combat tag**: PvP damage tags both players for 15 s (action bar timer); tagged players cannot teleport or use the
  listed commands; optional punishment for logging out in combat (`combat.on-logout: KILL | NOTHING`).
- `/tphere <player>`, `/tpo <player>` staff teleports (ignore toggles).

### homes
- `/sethome [name]`, `/home [name]`, `/delhome <name>`, `/homes` menu (click to go, shift-right-click to delete with a
  confirm menu, icon by dimension), `/home <player>:<name>` for staff.
- Limits by permission `donutessentials.homes.<n>` / `.unlimited` and `homes.limits` tiers; disabled worlds.

### warps (warps and spawn)
- `/warp [name]` (menu without a name), `/setwarp <name>` (held item becomes the icon), `/delwarp <name>`, `/warps`.
  Optional per-warp permission `donutessentials.warp.<name>`.
- `/spawn`, `/setspawn`; teleport to spawn on first join and optionally every join, and on respawn without a bed.
- First join: welcome broadcast with the unique player number, optional starter kit.

### rtp
- `/rtp`: a random safe spot within a radius of the world spawn, chunks loaded asynchronously, per-world radius,
  blocked biomes and blocks. Steps aside when the Donut RTP plugin is installed.

### kits
- `/kit [name]` (menu: available, on cooldown, locked; right-click previews), `/kit <name> <player>` for staff.
- `/createkit <name> [cooldown]` from your inventory, stored in Paper's versioned item format (every component kept),
  `/delkit`, `/editkit <name>` (reopen the contents), one-time kits, costs, first-join kit.
- A kit that does not fit is refused (`kits.full-inventory: DENY`) or the rest is dropped (`DROP`).

### economy
- Built-in economy registered with Vault at low priority (another economy plugin wins automatically; our commands then
  use it). `/balance [player]`, `/pay <player> <amount>` (`2.5k`, `1m`), `/baltop` menu, `/eco give|take|set|reset`.
- Payments received while offline are listed on join. Daily economy log in `logs/`.
- Starting balance, balance cap with permission tiers, minimum payment, currency format.

### chat
- Chat format with Vault/LuckPerms prefix and suffix and PlaceholderAPI (`chat.format`), per-group formats.
- `[item]` shows the held item with its tooltip; `@name` mentions highlight and ping.
- Colours by permission, anti-spam (rate and repeats), word filter, `/chat clear|lock|slow <seconds>`.
- Join, quit and first-join messages; `/broadcast` titles; rotating auto-announcements.

### messaging
- `/msg`, `/r`, `/msgtoggle`, `/ignore <player>`, `/ignorelist`, `/socialspy`. Mutes apply.
- `/mail send <player> <message>`, `/mail read`, `/mail clear`: offline mail, announced on join with a button.

### afk
- `/afk [message]`, automatic after idle minutes, `[AFK]` in the tab list, broadcasts, AFK players do not block
  sleeping, kick after N minutes (`donutessentials.afk.kickexempt`).

### nick (in chat)
- `/nick <name|off> [player]`, `/realname <nick>`. Colours by permission, length limit, no impersonation of other
  players' names.

### moderation (from 2.x, extended)
- Punishments with IDs: `kick`, `ban`, `tempban`, `mute`, `tempmute`, `warn`, `tempwarn`, `note`, `banip`,
  `tempipban`, `unban`, `unmute`, `unwarn`, `unnote`, `unpunish`, `change-reason`, `history`, `check`, `warns`,
  `notes`, `banlist`, `punishment delete record`. `-s` silent, `@layout` reasons, `#layout` durations.
- **Warning escalation**: `moderation.warn-escalation` maps warn counts to commands.
- **Alts**: `/alts <player>` lists accounts seen on the same IPs; staff are alerted when a banned player's alt joins.
- **Freeze**: `/freeze <player>`; frozen players cannot move, build or use commands; logging out is reported (or
  banned, `freeze.on-logout`).
- **Reports**: `/report <player> <reason>`, `/reports` staff menu (teleport, claim, close).
- `/staffchat` (`/sc`), `/commandspy`, Discord webhook for punishments and reports.

### vanish
- `/vanish [player]`, `/vanish list`. Hidden from players, the tab list, join/quit messages and
  `%donutessentials_online%`; no item pickup; action bar reminder; kept across rejoin.

### staff
- `/invsee <player>` (edit with `donutessentials.invsee.edit`), `/endersee <player>`, `/whois <player>`,
  `/offlinetp <player>`, `/playerlist`.

### maintenance, iplock (from 2.x)
- Maintenance mode with whitelist, MOTD, timers and join notifications; IP lock for staff accounts.

### rules
- `/rules`: the rules. With `rules.require-accept`, new players must accept them; on 1.21.7+ this is a **native
  dialog** with Accept and Disconnect; older servers get chat with clickable buttons. Until accepted, chat and building
  are blocked.
- **Server links** (1.21+): Discord, website, store and rules links in the pause menu.
- Join MOTD (`/motd`).

### utility
- `/fly`, `/god`, `/heal`, `/feed`, `/speed`, `/gmc` `/gms` `/gma` `/gmsp` (`/gm <mode>`), `/craft`, `/enderchest`,
  `/trash`, `/hat`, `/repair [all]`, `/top`, `/seen`, `/playtime`, `/ping`, `/near`, `/day` `/night` `/sun` `/rain`,
  `/clearinventory`, `/menu` (player hub), `/settings` (per-player toggles).

### importer
- `/dess import essentials [confirm]`: EssentialsX users (balances, homes, nicknames, ignores), warps, spawn, kits and
  vanilla bans. Without `confirm` it is a dry run that prints what would be imported.
- 2.x / Fssentials data is imported automatically on the first 3.0 start.

### placeholder
- `%donutessentials_*%`: balance (plain, formatted, short), baltop entries, homes and limit, afk, vanished, nick,
  playtime, first join, online (without vanished), warns, muted, tpa toggle, combat.

## API

`DonutEssentialsApi` (Bukkit services and `DonutEssentials.getApi()`): homes, warps, balances, AFK, vanish, combat,
punishments. Cancellable events: `EssentialsTeleportEvent`, `HomeSetEvent`, `KitClaimEvent`, `BalanceChangeEvent`,
`PunishmentCreateEvent`, `AfkChangeEvent`, `VanishChangeEvent`, `TpaRequestEvent`.

## Testing

- Unit tests for parsers, formatters, YAML merging, the 2.x upgrade, schema and importers.
- `tools/smoke`: bots play every module on real Paper and Folia servers from 1.20.1 to 26.3, including upgrades from
  Donut Essentials 2.0.0 and a switch from EssentialsX.
