# Donut Essentials 3.0.0: The All-in-One Update

The biggest Donut Essentials update yet: a full rebuild that turns a moderation toolkit into a complete essentials
plugin, still free and open source. Homes, warps, teleports, kits, an economy, chat, mail and AFK join the moderation
tools you already use, and every feature runs on Folia. Replace the jar and restart: your 2.x punishments, IP locks,
maintenance whitelist and settings carry over.

## New

### Teleports
* `/tpa`, `/tpahere`, `/tpaccept`, `/tpdeny`, `/tpacancel` with clickable **[Accept] [Deny]** buttons and a
  **[Cancel]** for the sender. Requests expire after 60 seconds.
* `/tptoggle` refuses requests; `/tpauto` accepts every `/tpa` at once.
* `/back` goes back to where you teleported from, or where you died.
* Staff: `/tphere` and `/tpo` (instant, ignoring toggles).
* **One teleport engine** for homes, warps, spawn, requests, `/back` and random teleport: a title countdown that
  moving or taking damage cancels, cooldowns and costs per kind, and **rank tiers** (`donutessentials.tier.<name>`)
  for shorter warmups, cooldowns and costs.
* **Combat tags**: hitting or being hit by a player tags both for 15 seconds (an action-bar timer). Players in
  combat can't teleport or use the listed commands, and logging out in combat kills them (configurable).

### Homes
* `/sethome`, `/home`, `/delhome` and a **homes menu**: click to go, shift + right-click to delete, a button to set
  one where you stand. Icons show the dimension.
* Limits from `homes.default-limit`, rank tiers and `donutessentials.homes.<n>`; worlds where homes are off.
* Staff reach anyone's homes with `/home <player>:<home>` and `/homes <player>`.

### Warps and spawn
* `/warp`, `/warps` (a menu with each warp's icon, place and visit count), `/setwarp` (the held item is the icon),
  `/delwarp`; optional per-warp permissions.
* `/spawn` and `/setspawn`, plus `/setspawn first` for where new players start. Spawn on first join, on every join
  (optional) and on respawn without a bed.

### Random teleport
* `/rtp`: a safe spot (no water, lava, cactus...; blocked biomes) within a ring around the spawn, per world. Chunks
  load in the background. When Donut RTP is installed it handles `/rtp` instead.

### Kits
* `/kit` and `/kits` open a kits menu (ready, cooldown, claimed or locked; right-click previews the items).
* `/createkit <name> [cooldown] [cost]` makes a kit from your inventory. **Every detail of every item is kept**:
  enchantments, attributes, names, lore, custom model data, books and potions, in Paper's versioned item format, so
  kits survive Minecraft updates.
* `/editkit` opens the kit to change it; `/delkit`; once-only kits; costs; `/kit <kit> <player>` for staff.
* A first-join kit (a starter kit is made on the first start).

### Economy
* A built-in economy registered with Vault. If another economy plugin is installed, it wins and Donut Essentials uses
  it.
* `/balance`, `/pay <player> <amount>` (`2.5k`, `1m`), `/paytoggle`, a **richest-players menu** (`/baltop`) and
  `/eco give|take|set|reset` (works for offline players and from the console).
* **Payments made while you were offline** are listed when you join. A daily economy log in `logs/`.
* Starting balance, a balance limit, the smallest payment, and the currency format.

### Chat
* Chat formats with prefixes and suffixes from your permissions plugin (through Vault: LuckPerms works) and
  PlaceholderAPI, and a format per group.
* **[item]** shows your held item with its full tooltip. **Mentions** of online players are highlighted and ping them.
* Colours by permission, anti-spam, a word filter, link blocking, and `/chat clear|lock|unlock|slow`.
* Join, quit and **first-join** messages (with the player's number); players can hide them in `/settings`.
* `/broadcast` (the 2.x title broadcasts) and **auto announcements**.
* `/nick` (colours by permission, no copying other players' names) and `/realname`.

### Private messages and mail
* `/msg`, `/r`, `/msgtoggle`, `/ignore`, `/ignorelist` and `/socialspy`. Vanilla `/msg` ignored mutes; ours doesn't.
* **Mail**: `/mail send|read|clear`, announced on join with a **[Read]** button.

### AFK
* `/afk [reason]`, automatic after 5 idle minutes, `[AFK]` in the tab list, announcements, an optional kick, and AFK
  players don't block night skipping.

### Moderation
* All 2.x punishments keep their syntax (`-s`, `@layouts`, `#time-layouts`), now with **history in the database**,
  `/history` pages, `/check`, `/banlist`, `/change-reason` and `/punishment info|delete record`.
* **Warning escalation**: e.g. the 3rd warning runs a one-hour mute (`moderation.warn-escalation`).
* **Alts**: `/alts <player>` lists accounts seen on the same addresses; staff are alerted when someone joins on an
  address of a banned account.
* **Freeze**: `/freeze <player>` stops them moving, building and using commands; logging out is reported (or banned).
* **Reports**: `/report <player> <reason>`; staff get clickable **[Teleport] [Claim]** buttons and a `/reports` menu.
* **Staff chat** (`/sc`) and **command spy**.
* **Discord logs**: punishments and reports posted to a channel webhook, no bot needed.

### Staff tools
* Vanish now hides join and leave messages, item pickups, mob targeting, the tab list and the online-count
  placeholder, and stays on when you rejoin.
* `/invsee` (live, editing by permission), `/endersee`, `/whois`, `/offlinetp`, `/playerlist`.
* Maintenance mode and IP lock as in 2.x, now in the database; IP locks accept wildcards (`1.2.3.*`) and ranges
  (`1.2.3.0/24`).

### Rules, server links and MOTD
* `/rules`. With `rules.require-accept`, new players see a **rules screen** they must accept before they can chat,
  build or use commands: a native dialog on 1.21.7+, clickable buttons on older versions.
* **Server links** in the pause menu (Minecraft 1.21+): Discord, website, store...
* A join message of the day (`/motd`).

### Utilities
* `/fly`, `/god`, `/heal`, `/feed`, `/speed`, `/gm` and `/gmc` `/gms` `/gma` `/gmsp`, `/craft`, `/enderchest`,
  `/trash`, `/hat`, `/repair [all]`, `/top`, `/seen`, `/playtime`, `/ping`, `/near`, `/day` `/night` `/sun` `/rain`,
  `/clearinventory`.
* `/menu`: a **player hub** with homes, warps, kits, spawn, random teleport, balance, mail and settings.
* `/settings`: **personal toggles** for teleport requests, auto-accept, private messages, mention pings, payments and
  join messages.

### For server owners
* **Every module can be switched off** in `config.yml`: no commands, listeners or tasks are left behind.
* **`commands.yml`**: switch off or rename any command and add aliases. Vanilla commands (`/gamemode`, `/give`, `/tp`...)
  are **never taken over** unless you list them; ours stay reachable as `/donutessentials:<name>`.
* **Command rules**: a cooldown or a cost for any command, including other plugins'.
* **One database**: SQLite, or MySQL/MariaDB with a table prefix.
* `config.yml`, `messages.yml`, `menus.yml` and `commands.yml` gain new settings on every update without touching
  yours.
* **Switch from EssentialsX** with `/donutessentials import essentials`: balances, homes, nicknames, ignores, warps,
  the spawn, kits and bans, with a dry run first.
* PlaceholderAPI (30+ placeholders), a developer API with nine events, bStats, update notices.

## Changed
* Fssentials and Donut Essentials 2.x commands, permissions and messages work as before; the 2.x permission nodes
  (`donutessentials.punish.*`, `fssentials.*`, `plugin.iplock.*`, `server.broadcast`) grant the new ones.
* A punishment's reason is optional now (`moderation.default-reason`), and kicks appear in the history.
* A new ban or mute replaces the previous one instead of being refused.
* A plain ban now blocks the account only; `/banip` and `/tempipban` block an address.
* `donutessentials.iplock.bypass` is off for everyone by default (in 2.x operators could never be locked).
* Login checks (bans, maintenance, IP lock) happen before the player joins, so Paper keeps its re-configuration
  features.

## Compatibility
* Paper, Folia and Purpur (and other Paper forks), Minecraft **1.20.1 to 26.3**, one jar.
* Java 17 for 1.20.1-1.20.4, 21 for 1.20.5-1.21.11, 25 for 26.x (the server's own requirement).
* Every release is played by bots on real Paper and Folia servers from 1.20.1 to 26.3, including an upgrade from
  2.0.0, a switch from EssentialsX and MySQL storage.

## Upgrading from 2.x
1. Back up `plugins/DonutEssentials/` (or `plugins/Fssentials/`).
2. Replace the jar and restart.
3. On the first start:
   * punishments, IP locks and the maintenance whitelist move into `data.db` (the old files are renamed
     `punishments.yml.imported` and `ip-locks.yml.imported`);
   * `config.yml` is upgraded with your values (the old file is kept as `config-2.x.yml`);
   * `messages.yml` is replaced with the 3.0 messages (yours is kept as `messages-2.x.yml`), and `menus.yml` and
     `commands.yml` are added.
4. Every new feature is on by default. Switch off what you don't want under `modules:` in `config.yml`.
