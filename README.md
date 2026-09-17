# Donut Essentials

Moderation and admin essentials for Paper, Folia, and Purpur servers. Donut Essentials (formerly Fssentials) provides punishments, IP lock, maintenance mode, broadcasts, vanish, and inventory inspection with Folia-safe scheduling.

## Features

- **Punishments** — kick, ban, mute, warn, note, IP ban; temporary durations; history, banlist, and silent (`-s`) staff notifications
- **IP lock** — restrict accounts to authorized IPs with admin commands and bypass permission
- **Maintenance mode** — whitelist, MOTD sets, timers, and join notifications
- **Broadcast** — server-wide MiniMessage title announcements with optional sound
- **Vanish** — staff invisibility with interaction protections
- **InvSee** — view and edit player inventories on Folia-safe schedulers

## Requirements

- **Runtime:** Paper, Folia, or Purpur **1.20.1** through **26.3**
- **Java:** 21 or newer

Storefront listings may mention Spigot or Bukkit for discoverability; use Paper, Folia, or Purpur at runtime.

## Installation

1. Download the latest jar from [Modrinth](https://modrinth.com/plugin/donutessentials) or [CurseForge](https://www.curseforge.com/minecraft/bukkit-plugins/donut-essentials) (project ID `1487015`, Modrinth project `1YpSYvSs`).
2. Place the jar in your server's `plugins` folder.
3. Restart the server and configure `plugins/DonutEssentials/config.yml` and `messages.yml`.

Upgrading from Fssentials: on first start, if `plugins/DonutEssentials/config.yml` is missing and `plugins/Fssentials` exists, configs and data files are copied automatically. Remove the old `Fssentials.jar` after upgrading.

## Build

```bash
mvn -B package
```

The shaded plugin jar is written to `target/`.

## Publishing

Tagged releases (`v*.*.*`) run [.github/workflows/publish.yml](.github/workflows/publish.yml): Maven build, then one Modrinth version and CurseForge file using [release/supported-minecraft.json](release/supported-minecraft.json).

Set GitHub repository variables **`MODRINTH_ID`** (`1YpSYvSs`) and **`CURSEFORGE_ID`** (`1487015`) when they differ from those defaults. Required secrets: `MODRINTH_TOKEN`, `CURSEFORGE_TOKEN`, `CURSEFORGE_API_KEY`.

Local publish (after `mvn -B package`):

```powershell
.\scripts\publish-local.ps1
```

The script loads `C:\Users\mahou\NightBeam-Knowledge-Base\secrets\local.env` and repo `.env`, then runs `scripts/publish-local.mjs`.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). By contributing, you agree to license your work under Apache-2.0.

## Security

See [SECURITY.md](.github/SECURITY.md).

## License

Licensed under the [Apache License, Version 2.0](LICENSE). See [NOTICE](NOTICE) for attribution.
