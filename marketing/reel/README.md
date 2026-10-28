# Instagram Reel: "Your Minecraft server needs this plugin!"

| File | Use |
| --- | --- |
| `donut-essentials-reel.mp4` | The Reel: 1080×1920, 30 fps, 39 s, H.264 + AAC. Not tracked in git. Rebuild it with the steps below |
| `cover.jpg` | Cover image. The text sits inside the centre 3:4 area, so it survives the profile-grid crop |
| `caption.txt` | Post caption with hashtags |
| `captions.md` | Captions for Instagram, YouTube Shorts and TikTok, alternate first lines, the pinned comment, alt text and the posting checklist |

## Structure

The edit puts the payoff first (the player hub already open, "ONE PLUGIN. EVERYTHING." on frame one), gives each
feature its own beat, burns in word-timed captions for sound-off viewers, and loops seamlessly into the start. Every
cut, voice line, label and sound sits on a 120 BPM grid; the Reel is exactly 78 beats long, so the beat carries on
across the loop. Chat, action-bar and button moments happen at the bottom of a portrait screen, under the platform's
caption, so they are lifted into the middle as a zoomed card while the scene behind dims.

| Time | Shot | Voiceover |
| --- | --- | --- |
| 0.0–3.5 | The player hub, the cursor running over every button | "Your Minecraft server needs this plugin!" |
| 3.5–7.0 | The homes menu, a click on "farm", the title countdown, then there (the beat drops on the cut) | "Homes, with a menu!" |
| 7.0–9.5 | A teleport request: [Accept] clicked in chat (zoomed card) | "Click to accept a teleport!" |
| 9.5–11.5 | The warps menu with icons and visit counts | "Warps for everything!" |
| 11.5–14.5 | The kits menu with cooldowns, then a preview down to the named, enchanted sword | "Kits that keep every enchant!" |
| 14.5–17.0 | A payment arrives (zoomed card), then the richest players menu | "A real economy, built in!" |
| 17.0–20.5 | Chat with rank prefixes, a mention and `[item]` with its tooltip (zoomed card) | "Show off your loot in chat!" |
| 20.5–23.0 | A hit, the combat timer, and `/spawn` refused (zoomed card) | "No running from a fight!" |
| 23.0–26.5 | A warning, a mute and a report with staff buttons (zoomed card), then the reports menu | "Warnings, mutes and reports, all of it!" |
| 26.5–29.5 | The rules screen on join: "I accept" clicked (zoomed card) | "New players accept your rules!" |
| 29.5–32.5 | The plaza from above, with Paper, Folia and Purpur pills and the version range | "Paper and Folia, one jar!" |
| 32.5–36.5 | Title card over the plaza | "It's called Donut Essentials. And it's free!" |
| 36.5–39.0 | The hub opens again and the cursor lands where the hook starts | "Now go open the menu!" |

## How it was made

- **Footage:** unedited captures of the vanilla 1.21.11 client at 1080×1920 (a portrait virtual display) on a Paper
  1.21.11 server running Donut Essentials 3.0.0 with Vault, LuckPerms and PlaceholderAPI. The world, homes, warps,
  kits, balances and ranks were built at normal speed first; then both game processes ran on a clock slowed 20× with
  libfaketime, so the software renderer still produced a new frame every few milliseconds of game time, and the screen
  was grabbed often enough to keep every one of them. Six bot players stand around the plaza, chat and pay. Everything
  is scripted in `tools/marketing/reel/record.mjs` (`--setup` builds the world, `--record` shoots).
- **Smooth playback:** `source/prep.py` indexes which captured frames are new renders and their game time.
  `source/compose.py` asks for a game time per output frame and cross-fades the two renders around it, so no frame
  repeats or skips at any speed. Gameplay never plays slower than real time, and a slow camera drift keeps still scenes
  moving. The zoomed cards are crops of the same frame. `source/verify.py` checks the result for repeated frames.
- **Voice:** the NightBeam channel voice: Chatterbox (MIT) cloning the channel reference clip with the channel's fixed
  settings, four takes per line (more only for lines that failed). `source/pick_takes.py` kept the take that
  transcribes exactly and reads with the most energy. Nothing is time-stretched; the edit is timed around the real line
  lengths (`plan.py` prints a fit check). Captions are timed per word from a Whisper transcript (`source/align.py`,
  `source/captions.py`).
- **Sound effects:** real recordings from Mixkit (free for commercial use in videos, no attribution required), listed by
  id in `source/sfx_pro.py` and downloaded by `source/fetch_sfx.sh`. Each recording's main hit is measured and placed
  on its frame: interface clicks on every click and hover, a tick per countdown second, a sparkle on the teleport,
  coins on the payment, message pops in chat, punches on the hit, an error tone when `/spawn` is refused, bells on the
  staff notices, a trailer hit on the drop.
- **Music:** an original 120 BPM bed, synthesized in `source/audio.py` and `source/synth.py`, with no hits or risers in
  it. The music is sidechained under the voice and the effects are ducked lightly under it in the final mix
  (`source/final.sh`), which applies the channel narration chain to the voice only.
- **Edit:** `source/plan.py` is the single source of truth (beat grid, shots, time maps, voice slots, cues), reading
  the measured event times, cameras and zoomed cards from `source/events.json`. `source/overlay.html` is rendered frame
  by frame with Playwright (`source/render_overlay.cjs`) for the headline, captions, feature labels, platform pills,
  the title card and the call to action.
- **Fonts:** Anton and Montserrat (SIL OFL 1.1, in `source/fonts/`).

## Rebuilding

The scripts read the working folder from `REEL_DIR` (default `~/.cache/donutessentials-smoke/reel`). Record with
`node tools/marketing/reel/record.mjs` (needs Xvfb, ffmpeg, libfaketime and the smoke-test cache), index each take with
`prep.py <take> 0.05`, generate the voice with the channel runner and `pick_takes.py`, copy `events.json`, `lines.json`
and `script_cb.json` into `$REEL_DIR/edit/`, and run `plan.py`, `build_vo.py`, `align.py`, `captions.py`, `audio.py`,
`fetch_sfx.sh`, `sfx_pro.py`, `compose.py`, `render_overlay.cjs` (with `overlay.html`, `fonts/` and the plugin icon as
`logo.png` in the edit folder), `final.sh` and `verify.py <video>` in that order. `cover.cjs` renders `cover.html`.

## Posting checklist

See `captions.md`.
