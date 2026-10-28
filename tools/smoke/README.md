# Cross-version smoke matrix

Boots real Paper and Folia servers across the supported range and plays Donut Essentials with
[mineflayer](https://github.com/PrismarineJS/mineflayer) bots over the network protocol. Every check goes through the
real plugin, the real Vault (or VaultUnlocked, or no Vault at all), PlaceholderAPI and the plugin's own database.

| Target | Java | Bot protocol | Vault | Extra |
| --- | --- | --- | --- | --- |
| Paper 1.20.1 | 17 | same version | Vault | |
| Paper 1.20.4 | 17 | same version | none | |
| Paper 1.20.6 | 21 | same version | Vault | starts from Donut Essentials 2.0.0 |
| Paper 1.21.1, 1.21.8 | 21 | same version | VaultUnlocked | |
| Paper 1.21.4 | 21 | same version | Vault | |
| Paper 1.21.11 | 21 | same version | Vault | starts from 2.0.0, restarts mid-test |
| Paper 1.21.11 + EssentialsX | 21 | same version | Vault | starts from EssentialsX, `/dess import essentials` |
| Paper 1.21.11 + MariaDB | 21 | same version | VaultUnlocked | MySQL storage, restarts mid-test |
| Paper 26.1.2, 26.2 | 25 | 26.1 through ViaVersion + ViaBackwards | | |
| Paper 26.3 | 25 | as above | VaultUnlocked | starts from 2.0.0 |
| Folia 1.20.4, 1.21.4 | 17 / 21 | same version | none / VaultUnlocked | |
| Folia 1.21.11 | 21 | same version | VaultUnlocked | restarts mid-test |
| Folia 26.3 | 25 | as above | VaultUnlocked | starts from 2.0.0 |

**Starting from 2.0.0**: the server first runs Donut Essentials 2.0.0 (from Modrinth). `Alice`, `Hank` and `Draxen`
join; the console warns and mutes Draxen, notes him, bans Hank, IP-locks Alice and whitelists Willow for maintenance.
Then the 3.0 jar replaces it: the punishments, IP lock and whitelist must move into the database, `config.yml` and
`messages.yml` must upgrade with backups, and Hank must still be refused.

**Starting from EssentialsX**: the server first runs EssentialsX and EssentialsX Spawn. `Willow` sets two homes, a
warp, the spawn and a nickname, `Rook` a home; the console sets both balances and bans `Draxen`. Then EssentialsX is
removed and `/donutessentials import essentials` runs: first the dry run, then `confirm`, and every home, warp, the
spawn, both balances, the nickname and the ban must arrive.

Then `scenario.mjs` runs on every server with `Alice` (operator), `Bob` and `Cara`:

1. The plugin enables on the right platform with 16 modules, the right storage and its own economy.
2. First join: the welcome message with the player number, the starter kit and $100.
3. Homes: `/sethome`, `/homes` (the menu), `/home` after the warmup, `/delhome`.
4. Warps and spawn: `/setwarp` with the held emerald as the icon, the warps menu, `/spawn`.
5. Teleport requests: the **[Accept]** button runs `/tpaccept Bob`; `/back`; `/tpahere` denied; `/tptoggle` refuses
   requests; damage cancels the warmup; a combat tag blocks `/spawn` and ends after 4 seconds.
6. Economy: `/pay 25`, a refused `2.5k`, `/eco give`, `/balance`, `/baltop`.
7. Kits: `/createkit` from an inventory with a named, enchanted sword; the kit gives it back exactly; cooldowns and
   once-only kits.
8. Chat: the format, a mention and `[item]` (read through the fixture's chat probe); `/ignore` hides a player.
9. `/msg`, `/r` and social spy; mail and a payment for an offline player, announced when they join.
10. AFK: the broadcast, the tab tag, sleeping ignored, back on chat. `/nick` and `/realname`.
11. Moderation: a warn with a title and `/history`; a mute blocks chat and `/msg`; a ban kicks and keeps out, the alt
    alert fires, `/unban`; freeze; a report with **[Teleport] [Claim]** buttons and the reports menu; staff chat;
    vanish (the online count goes 3 → 2 → 3); `/whois`, `/invsee`, `/seen`.
12. Utilities: fly, god, heal, feed, speed, game modes, hat, repair, top, day and night; the hub, settings, craft,
    ender chest and trash menus.
13. Command rules: a cooldown on a command.
14. Rules: a new player can't chat or build until they accept.
15. Maintenance keeps players out unless whitelisted or allowed; an IP lock refuses another address.
16. `%donutessentials_*%` placeholders through PlaceholderAPI.
17. `/donutessentials info` and `reload` on the console.
18. On restart targets the server restarts: homes, warps, balances, kit cooldowns and punishments all come back.
19. The console is scanned for errors and warnings from the plugin, stack frames in `dev.nightbeam.donutessentials`,
    and linkage errors.

## Running it

```bash
./gradlew build
./gradlew -p tools/smoke/fixture jar
npm --prefix tools/smoke install
node tools/smoke/run.mjs                               # all targets (about 50 minutes)
node tools/smoke/run.mjs paper-1.20.1 folia-26.3       # selected targets
```

The MariaDB target needs a local server with a `donutessentials` database and user (password in
`$ESSLAB_MYSQL_PASSWORD`, default `essentials`). JDKs, server jars and plugins are downloaded on first use and cached in
`$ESSLAB_HOME` (default `~/.cache/donutessentials-smoke`). Servers listen on `$ESSLAB_PORT` (default 25640).

A JSON report is written to `$ESSLAB_HOME/reports/`. `node tools/smoke/compat.mjs` turns the newest report into
`marketing/source/compat.json` for the compatibility card.

`fixture/` is a test-only plugin, never to be installed on a real server. It prints Donut Essentials' API events,
reads its state through the public API, and adds console helpers under `esslab`: `perm`, `pos`, `tp`, `sudo`, `hit`,
`damage`, `hurt`, `give`, `hold`, `damageitem`, `held`, `count`, `items`, `clear`, `fill`, `state`, `online`, `sees`,
`gamemode`, `time`, `block`, `papi`, `set`, `yaml`, `file` and `api`.
