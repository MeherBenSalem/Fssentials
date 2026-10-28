# Market analysis: essentials plugins (October 2026)

Why Donut Essentials 3.0 is built the way it is. Numbers are Modrinth downloads on 2026-10-28 unless noted.

## The field

| Plugin | Downloads | Price | Platforms | Range | What stands out | What is missing |
| --- | --- | --- | --- | --- | --- | --- |
| EssentialsX (+ Chat, Spawn) | 949k (+235k, +220k) | Free, GPL | Paper, Spigot | 1.8 – 26.x | The default; homes, warps, kits, economy, `/msg`, signs | **No Folia** (2.22.0 notes, issue #6633); one YAML file per player; takes over vanilla commands; mostly chat and commands, few menus; no moderation history; split into 9 jars |
| CMI | paid | ~$15+ | Spigot, Paper | 1.7 – 26.x | 300 commands, portals, holograms, kits GUI | Paid, closed, needs CMILib, no Folia mention |
| HuskHomes | 242k | Free | Paper, Folia, Fabric | 1.17 – 26.3 | Homes, warps, TPA, cross-server | Only teleports; menus need a third plugin |
| EssentialsC | 40k | Free, Apache | Paper, Folia | **1.20.6+, Java 21** | Menus, SQLite/MySQL, toggleable modules, EssentialsX importer, 29 languages | No `/mail`; no 1.20.1–1.20.5; no moderation history, alts or reports |
| zEssentials | 8.6k | Free tier, support paid | Paper, Folia | 1.18 – 26.3 | 130 commands, MiniMessage, Redis | **Needs zMenu** + PlaceholderAPI; non-commercial no-derivatives licence |
| EternalCore | 5.9k | Free, GPL | Paper, Folia | 1.19 – 26.2, Java 21 | 80 commands, server links, AFK, chat tools | No economy, no kits menu, no 26.3 yet |
| Essence, Tweaks, ServerSystem, Modern Essentials | 2–5k each | Free | mixed | mostly latest only | Lightweight | Narrow feature sets, frequent breaking changes |

Add-ons that exist only because the leader lacks the feature are the clearest signal of demand:
**EssentialsX-GUI** (35k: menus for homes, kits, warps), **clickaccept** (18k: clickable TPA buttons),
**EssentialsX Selectors**, **TPARequestGUI**, **EssentialsX Teleport Guard** (cancel teleports near danger) and
the combat-tag family (CombatMe, CombatLogX: block `/home`, `/tpa`, `/spawn` during a fight).

## What server owners ask for

The most-upvoted open enhancement requests on the EssentialsX tracker (sorted by reactions):

1. Better `/home` vs `/homes` (a real homes list) — #3957
2. **Kits that keep item flags and attributes** — #1105
3. **Customisable ways to deal with AFK players** — #2864
4. **Config auto-update** — #3286
5. Homes per world — #2393
6. **Command cooldowns and costs as one system** — #3200, #2107
7. **Payment notifications for offline players** — #3953
8. **Per-group teleport cooldowns** — #3509
9. Balance caps by permission — #2944
10. **Per-player toggles** — #6325
11. **Economy log** — #6387
12. **Cancel teleport on damage** — #5484
13. More `/back` points — #1720

Bold items are in Donut Essentials 3.0.

## Gaps Donut Essentials fills

1. **The full essentials set on Folia, from 1.20.1.** EssentialsX does not run on Folia. The Folia-ready
   alternatives start at 1.20.6 (EssentialsC), need Java 21 (EternalCore) or a second plugin (zEssentials).
   Donut Essentials runs on Paper, Folia and Purpur 1.20.1 – 26.3 from one jar compiled for Java 17, and every
   release is played by bots on real servers across that range.
2. **Moderation built in, not bolted on.** Every competitor stops at ban/mute/kick. Donut Essentials already had
   punishment IDs, history, notes, warn layouts, IP bans, IP lock and maintenance mode; 3.0 adds **warning
   escalation**, **alt detection with ban-evasion alerts**, **freeze**, **reports with a staff menu**, staff chat,
   command spy and **Discord moderation logs** without a bot. Servers otherwise buy LiteBans or install three plugins.
3. **Menus for everything, plus native dialogs.** Homes, warps, kits (with preview), a player hub, per-player
   settings, the richest players and staff reports are chest menus defined in `menus.yml`. On 1.21.7+ the rules
   screen and text inputs are **native Minecraft dialogs**; no other essentials plugin uses them.
4. **Teleports that behave.** Clickable **[Accept] [Deny]** TPA buttons, a title countdown cancelled by moving or
   damage, per-rank cooldowns, costs, `/back` for deaths and teleports, and **combat-aware teleports** (no `/home`
   out of a fight), so the add-ons above are not needed.
5. **Kits that keep everything.** Kits are made from your own inventory and stored in Paper's versioned item
   format: enchantments, attributes, flags, names, custom model data, books and potions survive, and survive
   Minecraft updates.
6. **One database, safe upgrades.** SQLite by default, MySQL/MariaDB optional. New settings are merged into your
   config on every update without touching your values.
7. **Switching is one command.** `/dess import essentials` brings homes, warps, spawn, balances, nicknames, kits and
   vanilla bans over from EssentialsX, with a dry run first. 2.x and Fssentials data upgrade automatically.
8. **Never hijacks vanilla.** `/gamemode`, `/give`, `/tp` and the rest stay vanilla unless you list them; every
   command can be switched off or renamed in `commands.yml`.
9. **Free and open source (Apache-2.0)**, no paid tier, no required library plugin.

## What 3.0 deliberately leaves out

Jails, portals, holograms, scoreboards and sign shops: each has a dedicated plugin that does it better, and they are
the "bloat" admins complain about in EssentialsX and CMI. Random teleport is included as a simple module that steps
aside automatically when Donut RTP is installed.

## Sources

- EssentialsX 2.22.0 release notes and download page (Folia not supported), issue #6633 (2026-10-01)
- EssentialsX open enhancement issues sorted by reactions, github.com/EssentialsX/Essentials
- Modrinth project pages and search API for every plugin in the table
- r/admincraft discussion on EssentialsX bloat and vanilla command overrides
