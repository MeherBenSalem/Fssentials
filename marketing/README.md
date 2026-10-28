# Marketing assets: 3.0 "The All-in-One Update"

Listing copy and artwork for CurseForge, Modrinth and Hangar. Nothing here ships in the plugin jar.

## Listing

| File | Use |
| --- | --- |
| [`curseforge/description.md`](curseforge/description.md) | Full project description (Markdown; also works on Modrinth and Hangar) |
| [`curseforge/summary.txt`](curseforge/summary.txt) | Short project summary (Modrinth allows 256 characters) |
| [`images/icon.png`](images/icon.png) | Project avatar |
| [`../DonutEssentials-3.0.0-PatchNotes.md`](../DonutEssentials-3.0.0-PatchNotes.md) | File changelog the publish workflow uploads |

Image links in the description point at `raw.githubusercontent.com/MeherBenSalem/Fssentials/main/marketing/images/...`,
so they work once this folder is on `main`. To host on CurseForge instead, upload the images to the project gallery and
replace each URL.

**Modrinth**: the *Sync Modrinth listing* workflow (`.github/workflows/listing.yml`, run by hand) sets the summary,
description, icon and gallery from these files. **CurseForge** has no API for the description: paste `description.md`
into the project's description editor (Markdown) and `summary.txt` into its summary.

The artwork shares the pixel donut, fonts and dark backdrop of the other Donut plugins (Donut Teams, DonutShards,
DonutAuctionHouse, Donut Bosses). Donut Essentials' donut has blueberry icing and a gold "ESS" tag.

## Adding a Minecraft version

The supported range comes from [`release/supported-minecraft.json`](../release/supported-minecraft.json), which the
publish workflow also uses. The banner and the compatibility card read it, so a new version needs no artwork edits:

1. Add the version to `release/supported-minecraft.json` and to `MATRIX` in
   [`tools/smoke/run.mjs`](../tools/smoke/run.mjs).
2. Run the smoke matrix, then `node tools/smoke/compat.mjs` to refresh `source/compat.json`.
3. Rerun the renderer for `banner compatibility`.

A new release name or number goes in `RELEASE` in `source/render.cjs` and in the first lines of the description.

## Images

| File | Size | Use |
| --- | --- | --- |
| `images/banner.png` | 1800×600 | Description header: the player hub, "3.0 · The All-in-One Update", platforms and versions |
| `images/icon.png` | 512×512 | Project avatar: the pixel donut with an "ESS" tag |
| `images/card-*.png` | 1600×900 | Feature cards |
| `images/header-*.png` | 1400×120, transparent | Section headings for the description |
| `images/divider.png` | 1400×40, transparent | Section divider |
| `images/gifs/home.gif` | 640×360 | A home teleport, from the title countdown to the farm |
| `images/gifs/rtp.gif` | 640×360 | A random teleport |
| `images/screenshots/*.jpg` | 1920×1080 | Gallery uploads and card art |

### How the screenshots were captured

Every screenshot and GIF is an unedited capture of the **vanilla Minecraft 1.21.11 client**, launched with
portablemc, on a **Paper 1.21.11** server running Donut Essentials 3.0.0 with Vault, LuckPerms and PlaceholderAPI.

- The client ran in a 1920×1080 Xvfb display with software rendering (Mesa llvmpipe) at GUI scale 3.
- It was driven by [`tools/marketing/capture.mjs`](../tools/marketing/capture.mjs) and
  [`scenes.mjs`](../tools/marketing/scenes.mjs) through console commands and XTEST mouse and keyboard input.
- **The world** is seed 8675309 on peaceful, at noon. A spawn plaza with a beacon was built in the nearest sunflower plains with
  `/fill`.
- **The players**: Moonbeam is the real client (an operator in the LuckPerms `admin` group). Kiri, Ember, Pixel, Rook,
  Nova and Sol are mineflayer bots, so they wear the default skins; Kiri and Pixel are in a `vip` group and Ember in
  `builder`, for the chat prefixes.
- **Real actions**: every home, warp, kit, payment, request, message, mail, report, warning and teleport in the shots
  happened through the plugin, run as Moonbeam or the bots (`esslab sudo` runs a command as that player). The menus were
  opened and hovered with the mouse, and the rules dialog was accepted with a click.
- **Set up with the console**: the plaza, player positions, starting balances (`/eco set`), the items kits were made
  from (`/give`), LuckPerms groups, and Moonbeam's permissions (operators skip warmups and kit cooldowns, so those are
  switched off for that account to show them).
- **The upgrade and switch cards** show the real consoles of two separate servers: one that ran Donut Essentials 2.0.0
  first, and one that ran EssentialsX first.
- **The GIFs** are real-time recordings (x11grab at 12 fps) of the centre 1280×720 at half size.

## Regenerating the artwork

```bash
./gradlew build
./gradlew -p tools/smoke/fixture jar
node tools/marketing/capture.mjs                     # screenshots, GIFs and console text (about 30 minutes)
node tools/marketing/capture.mjs --consoles-only     # just the 2.0.0 upgrade and EssentialsX import consoles
node tools/marketing/capture.mjs --scenes=homes,kits # just some scenes (see SCENES in scenes.mjs)
cp marketing/images/screenshots/scenery.jpg marketing/source/backdrop.jpg
python3 tools/marketing/crops.py                      # crop boxes of the menus and tooltips
node tools/smoke/compat.mjs                           # the compatibility card's data
node marketing/source/render.cjs                      # every image, or name the ones to render
```

`render.cjs` needs Playwright with Chromium. The fonts are Outfit and Inter (SIL Open Font License 1.1, in
`source/fonts/`).
