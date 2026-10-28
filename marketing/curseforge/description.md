![Donut Essentials 3.0: The All-in-One Update](https://raw.githubusercontent.com/MeherBenSalem/Fssentials/main/marketing/images/banner.png)

## Every essential your server needs, in one free plugin

**Donut Essentials** is a free, open-source essentials plugin for Paper, Folia and Purpur, from Minecraft **1.20.1 to
26.3**, in one jar.

Homes, warps, spawn and random teleport. Teleport requests with **clickable [Accept] [Deny]**. **Kits that keep every
item detail.** A built-in economy for Vault. Chat formats, private messages, **offline mail** and AFK. And the
moderation suite Donut Essentials is known for, now with **history, alts, freeze and reports**. All of it has menus,
all of it runs on **Folia**, and none of it takes over vanilla commands unless you ask.

**3.0 "The All-in-One Update"** turns the moderation toolkit into a complete essentials plugin:
- teleports: TPA with buttons, `/back`, a title countdown that moving or damage cancels, rank tiers and **combat tags**;
- homes, warps and spawn with menus, and a safe random teleport;
- kits made from your inventory, with a kits menu, previews, cooldowns, once-only kits and costs;
- an economy with `/pay`, `/baltop` (a menu) and **payments shown to offline players**;
- chat formats with LuckPerms prefixes, mentions, `[item]`, `/msg`, `/mail`, `/ignore` and AFK;
- moderation history, warning escalation, alt alerts, freeze, reports, staff chat and **Discord logs**;
- a **rules screen** new players accept (a native dialog on 1.21.7+), **server links** and a player hub;
- `/donutessentials import essentials`: **switch from EssentialsX** with one command.

Each release is played by bots on 16 Paper and Folia servers from 1.20.1 to 26.3, including an upgrade from 2.0.0, a
switch from EssentialsX and MySQL storage.

![A home teleport: the countdown, then there](https://raw.githubusercontent.com/MeherBenSalem/Fssentials/main/marketing/images/gifs/home.gif)

![divider](https://raw.githubusercontent.com/MeherBenSalem/Fssentials/main/marketing/images/divider.png)

![The Player Hub](https://raw.githubusercontent.com/MeherBenSalem/Fssentials/main/marketing/images/header-hub.png)

![Everything in one menu](https://raw.githubusercontent.com/MeherBenSalem/Fssentials/main/marketing/images/card-hub.png)

`/menu` opens a **player hub**: homes, warps, kits, spawn, random teleport, money, mail and settings, with the
player's balance, homes and play time on their head. Buttons for switched-off commands disappear by themselves.

- **Every menu is in `menus.yml`**: titles, sizes, slots, items, names and lore, with pages for long lists.
- `/settings` gives every player **personal switches**: teleport requests, auto-accept, private messages, mention
  pings, payments and join messages.

![Teleports](https://raw.githubusercontent.com/MeherBenSalem/Fssentials/main/marketing/images/header-teleports.png)

![Teleports done right](https://raw.githubusercontent.com/MeherBenSalem/Fssentials/main/marketing/images/card-teleports.png)

- `/tpa` and `/tpahere` with **[Accept] [Deny]** buttons, and **[Cancel]** for the sender. `/tptoggle` refuses
  requests, `/tpauto` accepts them all.
- **One teleport engine** for homes, warps, spawn, requests, `/back` and random teleport: a **title countdown** that
  moving or taking damage cancels, cooldowns and costs per kind.
- **Rank tiers** (`donutessentials.tier.vip`): shorter warmups, cooldowns and costs, more homes.
- `/back` returns to where you teleported from, or where you died.
- **Combat tags**: hitting or being hit by a player tags both for 15 seconds, with an action-bar timer. Tagged players
  can't teleport or use the commands you list, and logging out in combat kills them.

![Homes](https://raw.githubusercontent.com/MeherBenSalem/Fssentials/main/marketing/images/header-homes.png)

![Homes](https://raw.githubusercontent.com/MeherBenSalem/Fssentials/main/marketing/images/card-homes.png)

- `/sethome`, `/home`, `/delhome`, and a **homes menu**: click to go, shift + right-click to delete, a button to set
  one where you stand. Each icon shows the dimension.
- Limits from a default, rank tiers and `donutessentials.homes.<n>`; worlds where homes are off.
- Staff reach anyone's homes: `/home Kiri:base`, `/homes Kiri`.

![Warps & Spawn](https://raw.githubusercontent.com/MeherBenSalem/Fssentials/main/marketing/images/header-warps.png)

![Warps and spawn](https://raw.githubusercontent.com/MeherBenSalem/Fssentials/main/marketing/images/card-warps.png)

- `/setwarp shop` uses **the item in your hand as the icon**. `/warps` lists each warp with its place and visit count;
  optional per-warp permissions.
- `/spawn` and `/setspawn`, plus `/setspawn first` for where new players start. Spawn on first join, on every join
  (optional) and on respawn without a bed.
- `/rtp`: a **safe random spot** (no water, lava or cactus; blocked biomes) within a ring around spawn, per world.
  Chunks load in the background. With Donut RTP installed, it steps aside.

![Kits](https://raw.githubusercontent.com/MeherBenSalem/Fssentials/main/marketing/images/header-kits.png)

![Kits that keep everything](https://raw.githubusercontent.com/MeherBenSalem/Fssentials/main/marketing/images/card-kits.png)

- `/createkit warrior 1d` makes a kit **from your inventory**; the held item is its icon. `/editkit` opens it to
  change it.
- **Every detail of every item is kept**: enchantments, attributes, names, lore, custom model data, books and potions,
  stored in Paper's versioned item format so kits survive Minecraft updates.
- A **kits menu** shows each kit as ready, on cooldown, claimed or locked; right-click to **preview** the items.
- Cooldowns, **once-only kits**, costs, per-kit permissions, a first-join kit, and `/kit warrior Kiri` for staff.

![Economy](https://raw.githubusercontent.com/MeherBenSalem/Fssentials/main/marketing/images/header-economy.png)

![A built-in economy](https://raw.githubusercontent.com/MeherBenSalem/Fssentials/main/marketing/images/card-economy.png)

- **A built-in economy registered with Vault**, so shops and jobs plugins use it. If another economy plugin is
  installed, it wins and Donut Essentials uses it.
- `/balance`, `/pay Kiri 2.5k`, `/paytoggle`, and the **richest players menu** (`/baltop`).
- **Payments made while you were offline** are listed when you join.
- `/eco give|take|set|reset` works on offline players and from the console. A daily economy log.
- Starting balance, a balance limit, the smallest payment and the currency format are yours to set.

![Chat & Messages](https://raw.githubusercontent.com/MeherBenSalem/Fssentials/main/marketing/images/header-chat.png)

![Chat and messages](https://raw.githubusercontent.com/MeherBenSalem/Fssentials/main/marketing/images/card-chat.png)

- **Chat formats** with prefixes and suffixes from your permissions plugin (LuckPerms through Vault) and
  PlaceholderAPI, and a format per group.
- **[item]** shows your held item with its full tooltip. **Mentions** highlight and ping the player.
- Colours by permission, anti-spam, a word filter, link blocking, `/chat clear|lock|slow`, `/broadcast` with a title,
  and auto announcements.
- `/msg`, `/r`, `/ignore`, `/msgtoggle` and social spy. **Mail** (`/mail send`) waits for offline players and is
  announced with a **[Read]** button.
- **AFK**: `/afk [reason]`, automatic after idle minutes, `[AFK]` in the tab list, and AFK players don't hold up night
  skipping.
- `/nick` with colours by permission (no copying other players' names) and `/realname`.

![Moderation](https://raw.githubusercontent.com/MeherBenSalem/Fssentials/main/marketing/images/header-moderation.png)

![Moderation built in](https://raw.githubusercontent.com/MeherBenSalem/Fssentials/main/marketing/images/card-moderation.png)

- Bans, temp-bans, mutes, warnings, notes, kicks and IP bans with IDs, **silent mode** (`-s`), reason layouts
  (`@cheating`) and time layouts (`#1st-offence`).
- **History** for every player (`/history`, `/check`, `/banlist`), `/change-reason` and record deletion.
- **Warning escalation**: e.g. the third warning mutes for an hour.
- **Alts**: `/alts` lists accounts seen on the same addresses, and staff are alerted when someone joins from a banned
  account's address.
- **Freeze** a player (no moving, building or commands); logging out is reported or banned.
- **Reports**: `/report Rook flying`; staff get **[Teleport] [Claim]** buttons and a `/reports` menu.
- **Staff chat** (`/sc`), **command spy**, and **Discord logs** of punishments and reports through a channel webhook,
  no bot needed.

![Staff Tools](https://raw.githubusercontent.com/MeherBenSalem/Fssentials/main/marketing/images/header-staff.png)

![Staff tools](https://raw.githubusercontent.com/MeherBenSalem/Fssentials/main/marketing/images/card-staff.png)

- **Vanish** hides you everywhere: join and leave messages, the tab list, item pickups, mobs and the online count.
  It stays on when you rejoin.
- `/invsee` (live, editing by permission), `/endersee`, `/whois`, `/seen`, `/offlinetp` and `/playerlist`.
- **Maintenance mode** with a whitelist, timers and its own server-list message, and **IP lock** for staff accounts
  (addresses, wildcards `1.2.3.*` and ranges `1.2.3.0/24`).
- `/fly`, `/god`, `/heal`, `/feed`, `/speed`, `/gm`, `/hat`, `/repair`, `/top`, `/near`, `/day`, `/night` and more.

![Rules & Server Links](https://raw.githubusercontent.com/MeherBenSalem/Fssentials/main/marketing/images/header-rules.png)

![Rules and server links](https://raw.githubusercontent.com/MeherBenSalem/Fssentials/main/marketing/images/card-rules.png)

- With `rules.require-accept`, new players get a **rules screen** they must accept before they can chat, build or use
  commands: a **native dialog** on 1.21.7+, clickable buttons before that. Change the rules and bump the version, and
  everyone accepts again.
- **Server links** in the pause menu (Minecraft 1.21+): Discord, website, store...
- A message of the day on join (`/motd`) and **first-join messages** with the player's number.

![Make It Yours](https://raw.githubusercontent.com/MeherBenSalem/Fssentials/main/marketing/images/header-yours.png)

![Your server, your rules](https://raw.githubusercontent.com/MeherBenSalem/Fssentials/main/marketing/images/card-yours.png)

- **Every module can be switched off** in `config.yml`: its commands, listeners and tasks are gone.
- **`commands.yml`**: switch off or rename any command and add aliases. Vanilla commands (`/gamemode`, `/give`,
  `/tp`...) are **never taken over** unless you list them; ours stay reachable as `/donutessentials:<name>`.
- **Command rules**: a cooldown or a cost for any command, other plugins' included.
- Every message is MiniMessage (or `&` codes) in `messages.yml`, every menu in `menus.yml`, every sound in
  `config.yml`. **New settings are added on every update without touching yours.**
- **One database**: SQLite, or MySQL/MariaDB with a table prefix.

![Switching from EssentialsX](https://raw.githubusercontent.com/MeherBenSalem/Fssentials/main/marketing/images/header-switch.png)

![Switch in one command](https://raw.githubusercontent.com/MeherBenSalem/Fssentials/main/marketing/images/card-switch.png)

1. Install Donut Essentials next to EssentialsX and start the server.
2. Run `/donutessentials import essentials` for a **dry run**: what would be imported, and nothing changed.
3. Run `/donutessentials import essentials confirm`. **Balances, homes, nicknames, ignores, warps, the spawn, kits and
   bans** come over. Names Donut Essentials already has are kept.
4. Remove EssentialsX.

![Upgrading from 2.x](https://raw.githubusercontent.com/MeherBenSalem/Fssentials/main/marketing/images/header-upgrade.png)

![Upgrade in place](https://raw.githubusercontent.com/MeherBenSalem/Fssentials/main/marketing/images/card-upgrade.png)

Replace the jar and restart. Donut Essentials (or Fssentials) 2.x upgrades on the first start:
- **Punishments, IP locks and the maintenance whitelist** move into the database (the old files are renamed
  `.imported`).
- `config.yml` keeps your values (the old file is saved as `config-2.x.yml`); `messages.yml` is replaced with the 3.0
  messages (yours is saved as `messages-2.x.yml`).
- 2.x commands, punishment syntax and permission nodes (`fssentials.*`, `donutessentials.punish.*`) keep working.
- Every new feature is on. Switch off what you don't want under `modules:`.

![Integrations](https://raw.githubusercontent.com/MeherBenSalem/Fssentials/main/marketing/images/header-integrations.png)

![Integrations](https://raw.githubusercontent.com/MeherBenSalem/Fssentials/main/marketing/images/card-integrations.png)

All of these are optional:

| Plugin | What Donut Essentials does with it |
| --- | --- |
| Vault (or VaultUnlocked) | Registers its economy; reads prefixes, suffixes and groups for chat |
| LuckPerms (or any Vault permissions plugin) | Chat prefixes and group formats, rank tiers |
| PlaceholderAPI | 30+ placeholders (balance, baltop, homes, AFK, combat, play time...), and placeholders in chat formats, the MOTD and announcements |
| MariaDB or MySQL | Storage instead of SQLite, with a table prefix |
| Discord | Moderation and report logs through a channel webhook |
| Donut RTP | Takes over `/rtp` when installed |

**For developers**: `DonutEssentials.getApi()` (players, homes, warps, kits, balances, punishments, AFK, vanish,
combat) and nine events, from `EssentialsTeleportEvent` and `KitClaimEvent` to `PaymentEvent` and
`PunishmentCreateEvent`.

![Compatibility](https://raw.githubusercontent.com/MeherBenSalem/Fssentials/main/marketing/images/header-compatibility.png)

![Tested from 1.20 to 26.3](https://raw.githubusercontent.com/MeherBenSalem/Fssentials/main/marketing/images/card-compatibility.png)

| Server | Minecraft | Java |
| --- | --- | --- |
| Paper, Purpur and other Paper forks | 1.20.1 – 1.20.4 | 17 |
| Paper, Purpur and other Paper forks | 1.20.5 – 1.21.11 | 21 |
| Paper, Purpur and other Paper forks | 26.1 – 26.3 | 25 |
| Folia | 1.20.1 – 26.3 | 17 – 25 |

One jar for all of them. Native dialogs need 1.21.7 or newer and server links 1.21; older servers get chat buttons.
Spigot and Bukkit are not supported (they lack Paper's API): the plugin says so and stops.

Each release is played by bots on Paper 1.20.1, 1.20.4, 1.20.6, 1.21.1, 1.21.4, 1.21.8, 1.21.11, 26.1.2, 26.2 and
26.3 and Folia 1.20.4, 1.21.4, 1.21.11 and 26.3, with SQLite and MySQL, with Vault, VaultUnlocked or no Vault at all:
about 40 checks each, from the first join to a restart, including an upgrade from 2.0.0 and a switch from EssentialsX.
The console must stay free of errors.

![Commands & Permissions](https://raw.githubusercontent.com/MeherBenSalem/Fssentials/main/marketing/images/header-commands.png)

| Area | Players | Staff |
| --- | --- | --- |
| Teleports | `/tpa`, `/tpahere`, `/tpaccept`, `/tpdeny`, `/tpacancel`, `/tptoggle`, `/tpauto`, `/back`, `/rtp` | `/tphere`, `/tpo` |
| Homes | `/sethome`, `/home`, `/delhome`, `/homes` | `/home <player>:<home>`, `/homes <player>` |
| Warps and spawn | `/warp`, `/warps`, `/spawn` | `/setwarp`, `/delwarp`, `/setspawn [first]` |
| Kits | `/kit [kit]`, `/kits` | `/createkit`, `/editkit`, `/delkit`, `/kit <kit> <player>` |
| Economy | `/balance`, `/pay`, `/paytoggle`, `/baltop` | `/eco give\|take\|set\|reset` |
| Chat and messages | `/msg`, `/r`, `/ignore`, `/msgtoggle`, `/mail`, `/afk`, `/realname` | `/chat`, `/broadcast`, `/nick`, `/socialspy` |
| Moderation | `/report` | `/ban`, `/tempban`, `/mute`, `/tempmute`, `/warn`, `/kick`, `/note`, `/banip`, `/unban`... `/history`, `/check`, `/alts`, `/freeze`, `/reports`, `/sc`, `/commandspy` |
| Staff | | `/vanish`, `/invsee`, `/endersee`, `/whois`, `/offlinetp`, `/maintenance`, `/iplock` |
| Everything else | `/menu`, `/settings`, `/rules`, `/motd`, `/seen`, `/playtime`, `/ping`, `/trash` | `/fly`, `/god`, `/heal`, `/feed`, `/speed`, `/gm`, `/craft`, `/enderchest`, `/hat`, `/repair`, `/top`, `/near`, `/day`, `/night` |
| Admin | | `/donutessentials info\|reload\|import essentials` (`/dess`) |

Every command has the permission `donutessentials.command.<name>`: player commands default to everyone, staff ones to
operators. A few more:

| Permission | Default | |
| --- | --- | --- |
| `donutessentials.tier.<name>` | nobody | A rank tier: faster, cheaper teleports and more homes |
| `donutessentials.homes.<n>` | nobody | Home limit |
| `donutessentials.bypass.warmup`, `.cooldown`, `.cost` | op | Teleport rules |
| `donutessentials.kit.<name>` | nobody | Per-kit access (when switched on) |
| `donutessentials.chat.color`, `.item` | op, everyone | Chat colours, `[item]` |
| `donutessentials.vanish.see`, `donutessentials.invsee.edit` | op | Staff |
| `donutessentials.*` | op | Everything except the exempt and bypass nodes |

![Questions](https://raw.githubusercontent.com/MeherBenSalem/Fssentials/main/marketing/images/header-faq.png)

**Can it replace EssentialsX?** For most servers, yes: teleports, homes, warps, spawn, kits, economy, chat, messages,
mail, AFK, moderation and the everyday utilities. `/donutessentials import essentials` brings your data over. Jails,
portals, holograms and sign shops are left to plugins that do them better.

**Does it work on Folia?** Yes, every feature, on every Folia version from 1.20.1 to 26.3.

**Will it break `/gamemode` or `/tp`?** No. Vanilla commands stay vanilla unless you add them to
`commands.take-over-vanilla`.

**I only want the moderation tools.** Switch the other modules off under `modules:` in `config.yml`. Nothing of a
switched-off module stays loaded.

**Does it need Vault?** No. It needs Vault (or VaultUnlocked) only for other plugins to see its economy, and for chat
prefixes from your permissions plugin.

**Several servers?** Use one database per server for now; punishments shared across a network are on the roadmap.

**Will my 2.x punishments survive?** Yes. They move into the database on the first start; back up
`plugins/DonutEssentials/` first anyway.

![Gallery](https://raw.githubusercontent.com/MeherBenSalem/Fssentials/main/marketing/images/header-gallery.png)

![The player hub](https://raw.githubusercontent.com/MeherBenSalem/Fssentials/main/marketing/images/screenshots/menu-hub.jpg)

![Homes](https://raw.githubusercontent.com/MeherBenSalem/Fssentials/main/marketing/images/screenshots/menu-homes.jpg)

![Kits](https://raw.githubusercontent.com/MeherBenSalem/Fssentials/main/marketing/images/screenshots/menu-kits.jpg)

![The rules screen](https://raw.githubusercontent.com/MeherBenSalem/Fssentials/main/marketing/images/screenshots/rules-dialog.jpg)

![Chat](https://raw.githubusercontent.com/MeherBenSalem/Fssentials/main/marketing/images/screenshots/chat.jpg)

![divider](https://raw.githubusercontent.com/MeherBenSalem/Fssentials/main/marketing/images/divider.png)

**Discord**: [discord.gg/TJc3CxR4jK](https://discord.gg/TJc3CxR4jK) · **Source**:
[GitHub](https://github.com/MeherBenSalem/Fssentials) (Apache 2.0) · **Support the project**:
[nightbeam.dev/support](https://nightbeam.dev/support)
