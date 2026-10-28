// Records the promo reel's footage. Phase A builds the world at normal speed (a mineflayer bot stands in for
// Moonbeam: plaza, homes, warps, kits, money, groups). Phase B restarts the server and starts the vanilla client in a
// portrait 1080x1920 display, both on a clock slowed by SLOW (libfaketime), and records every shot with x11grab. The
// game runs 1/SLOW times slower than the wall clock, so every frame the software renderer draws is captured; prep.py
// later indexes the new frames and their game time, and compose.py samples them by game time.
//
// Needs what tools/marketing/capture.mjs needs, plus libfaketime. Run from the repository root after ./gradlew build:
//   node tools/marketing/reel/record.mjs            # both phases (about 40 minutes)
//   node tools/marketing/reel/record.mjs --setup    # phase A only
//   node tools/marketing/reel/record.mjs --record   # phase B only, on the world phase A left
// Footage goes to $REEL_DIR/raw (default $ESSLAB_HOME/reel/raw).
import { spawn, execFileSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { Server, joinBot, jdk, serverJar, pluginJar, vaultJar, modrinth, fixtureJar } from '../../smoke/run.mjs';
import { SCENES, context } from '../scenes.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const lab = process.env.ESSLAB_HOME || path.join(os.homedir(), '.cache', 'donutessentials-smoke');
const REEL = process.env.REEL_DIR || path.join(lab, 'reel');
const SLOW = Number(process.env.SLOW || 0.05);
const PORT = Number(process.env.REEL_PORT || 25660);
const VERSION = '1.21.11';
const P = 'Moonbeam';
const DISPLAY = process.env.REEL_DISPLAY || ':98';
const W = 1080;
const H = 1920;
const SCALE = 3;
const CURSOR = 96;
const python = process.env.CAPTURE_PYTHON || path.join(lab, 'venv', 'bin', 'python3');
const portablemc = process.env.PORTABLEMC || path.join(lab, 'venv', 'bin', 'portablemc');
const clientDir = process.env.CAPTURE_CLIENT_DIR || path.join(lab, 'client');
const FAKE = {
  FAKETIME: `+0 x${SLOW}`, FAKETIME_DONT_FAKE_MONOTONIC: '0', LD_PRELOAD: '/usr/lib/x86_64-linux-gnu/faketime/libfaketimeMT.so.1',
};
const BOTS = ['Kiri', 'Ember', 'Pixel', 'Rook', 'Nova', 'Sol'];
const SPOTS = {
  Kiri: [-2.5, 1.5], Ember: [2.5, 1.0], Pixel: [4.5, -1.5], Rook: [-4.5, -1.0], Nova: [0.5, -2.5], Sol: [-1.0, -4.5],
};

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
/** Waits `ms` of game time. */
const g = (ms) => sleep(ms / SLOW);
const x = (...args) => execFileSync(python, [path.join(here, '..', 'xinput.py'), ...args.map(String)], { env: { ...process.env, DISPLAY } });

/** Pixel centre of a chest-menu slot at GUI scale 3 on the portrait display. */
function slot(index, rows) {
  const left = Math.floor((W / SCALE - 176) / 2);
  const top = Math.floor((H / SCALE - (114 + rows * 18)) / 2);
  return [(left + 8 + (index % 9) * 18 + 8) * SCALE, (top + 18 + Math.floor(index / 9) * 18 + 8) * SCALE];
}

/** Moves the cursor in small steps over `ms` of game time, so it glides in the recording. */
async function glide([x0, y0], [x1, y1], ms) {
  const steps = Math.max(2, Math.round(ms / 20));
  for (let i = 1; i <= steps; i++) {
    const u = i / steps;
    const e = u < 0.5 ? 2 * u * u : 1 - (-2 * u + 2) ** 2 / 2;
    x('move', Math.round(x0 + (x1 - x0) * e), Math.round(y0 + (y1 - y0) * e));
    await g(ms / steps);
  }
}

/** Records the display for `gameSeconds` of game time into raw/<name>.mkv. */
function record(name, gameSeconds) {
  fs.mkdirSync(path.join(REEL, 'raw'), { recursive: true });
  const file = path.join(REEL, 'raw', `${name}.mkv`);
  const proc = spawn('ffmpeg', ['-loglevel', 'error', '-y', '-f', 'x11grab', '-video_size', `${W}x${H}`, '-framerate', '15', '-draw_mouse', '1',
    '-i', `${DISPLAY}.0`, '-t', String(gameSeconds / SLOW), '-threads', '2', '-c:v', 'libx264', '-preset', 'ultrafast', '-crf', '8',
    '-pix_fmt', 'yuv444p', file], { stdio: 'ignore' });
  console.log(`  recording ${name} (${gameSeconds} game s, ${Math.round(gameSeconds / SLOW)} s)`);
  return new Promise((resolve) => proc.on('exit', () => resolve(file)));
}

function serverDir() {
  return path.join(REEL, 'server');
}

async function prepareServer(java) {
  const dir = serverDir();
  fs.rmSync(dir, { recursive: true, force: true });
  fs.mkdirSync(path.join(dir, 'plugins'), { recursive: true });
  fs.writeFileSync(path.join(dir, 'eula.txt'), 'eula=true\n');
  fs.writeFileSync(path.join(dir, 'server.properties'), [
    'online-mode=false', 'white-list=false', 'enforce-secure-profile=false', `server-port=${PORT}`, 'spawn-protection=0',
    'difficulty=peaceful', 'spawn-monsters=false', 'allow-flight=true', 'pvp=true', 'level-seed=8675309', 'view-distance=8',
    'simulation-distance=5', 'motd=Donut Essentials', 'gamemode=survival',
  ].join('\n') + '\n');
  for (const jar of [pluginJar(), fixtureJar(), await vaultJar('Vault'), await modrinth('placeholderapi'), await modrinth('luckperms', 'bukkit')]) {
    fs.copyFileSync(jar, path.join(dir, 'plugins', path.basename(jar)));
  }
  return dir;
}

const WORLD_SETUP = ['time set 6000', 'weather clear',
  'gamerule advance_time false', 'gamerule advance_weather false', 'gamerule show_advancement_messages false', 'gamerule send_command_feedback false',
  'gamerule spawn_mobs false', 'gamerule log_admin_commands false'];
const CONFIG = [
  ['update-check.enabled', false], ['metrics.enabled', false], ['rules.require-accept', true], ['server-links.enabled', true],
  ['economy.baltop.refresh-seconds', 3], ['announcements.enabled', false], ['afk.idle-minutes', 120], ['kits.per-kit-permissions', true],
  ['teleport.cooldowns.home', 0], ['teleport.cooldowns.warp', 0], ['teleport.cooldowns.spawn', 0], ['teleport.cooldowns.tpa', 0],
];
const PLAYER_NODES = ['donutessentials.bypass.warmup false', 'donutessentials.bypass.cooldown false', 'donutessentials.tier.vip false',
  'donutessentials.tier.mvp false', 'donutessentials.homes.unlimited false', 'donutessentials.homes.8 true', 'donutessentials.kit.elytra false'];
const SWORD = 'diamond_sword[enchantments={sharpness:5,unbreaking:3,looting:3},custom_name={text:"Dawnbreaker",color:"gold",italic:false},lore=[{text:"Forged at spawn.",color:"gray",italic:false}]]';

// ── Phase A: the world, at normal speed ─────────────────────────────────────────────────────────────────────────
async function setup(java) {
  const dir = await prepareServer(java);
  const server = new Server(dir, java, await serverJar('paper', VERSION));
  server.start();
  await server.waitFor(/Done \([0-9.,]+s\)!/, 600_000, 'server start');
  for (const command of WORLD_SETUP) server.command(command);
  for (const [key, value] of CONFIG) await server.lab(`set config.yml ${key} ${value}`);
  server.command('donutessentials reload');
  await server.waitFor(/Reloaded config/, 20_000, 'reload');
  for (const command of ['lp creategroup admin', 'lp creategroup vip', 'lp creategroup builder',
    'lp group vip meta setprefix 100 "&6[VIP] &f"', 'lp group builder meta setprefix 100 "&b[Builder] &f"']) {
    server.command(command);
    await sleep(400);
  }
  const bots = new Map();
  const bot = async (name) => {
    if (!bots.has(name)) {
      bots.set(name, await joinBot(name, VERSION, null, {}, PORT));
      await sleep(600);
      await server.lab(`sudo ${name} rules accept`);
    }
    return bots.get(name);
  };
  await bot(P);
  for (const command of [`op ${P}`, `lp user ${P} parent set admin`, ...PLAYER_NODES.map((n) => `lp user ${P} permission set ${n}`)]) {
    server.command(command);
    await sleep(300);
  }
  const ctx = context({ server, bot, lab, consoleDir: null });
  await SCENES.base(ctx);
  const { px, py, pz } = ctx;
  fs.writeFileSync(path.join(REEL, 'plaza.json'), JSON.stringify({ px, py, pz }));
  const say = (who, line) => server.lab(`sudo ${who} ${line}`);
  const tp = (who, dx, dz, dy = 0, yaw = 0, pitch = 0) => server.command(`tp ${who} ${px + dx} ${py + dy} ${pz + dz} ${yaw} ${pitch}`);

  // Homes in three dimensions.
  for (const [name, go] of [
    ['base', () => tp(P, 0.5, 3.5)],
    ['farm', () => tp(P, 9.5, -9.5)],
    ['mine', () => {
      server.command(`fill ${px + 20} ${py - 30} ${pz + 20} ${px + 22} ${py - 28} ${pz + 22} air`);
      tp(P, 21.5, 21.5, -30);
    }],
    ['nether', async () => {
      server.command('execute in minecraft:the_nether run fill 6 100 6 10 103 10 air');
      server.command('execute in minecraft:the_nether run fill 6 99 6 10 99 10 netherrack');
      await server.lab(`tp ${P} world_nether 8.5 100 8.5`);
    }],
    ['island', async () => {
      server.command('execute in minecraft:the_end run fill 98 59 -2 102 59 2 end_stone');
      await server.lab(`tp ${P} world_the_end 100.5 60 0.5`);
    }],
  ]) {
    await go();
    await sleep(1500);
    await say(P, `sethome ${name}`);
    await sleep(500);
  }
  await server.lab(`tp ${P} world ${px + 0.5} ${py} ${pz + 9.5}`);
  await sleep(1500);

  // Warps with the held item as the icon, and some visits.
  for (const [name, icon, dx, dz] of [['shop', 'emerald', 6.5, -6.5], ['arena', 'iron_sword', -6.5, -6.5], ['farms', 'wheat', 6.5, 6.5],
    ['mines', 'diamond_pickaxe', -6.5, 6.5], ['parkour', 'feather', 0.5, -6.5], ['market', 'gold_ingot', 6.5, 0.5],
    ['crates', 'tripwire_hook', -6.5, 0.5]]) {
    tp(P, dx, dz, 0, 180);
    await sleep(300);
    await server.lab(`hold ${P} ${icon}`);
    await say(P, `setwarp ${name}`);
    await sleep(400);
  }
  for (const name of BOTS) await bot(name);
  for (const [warp, who] of Object.entries({ shop: ['Kiri', 'Ember', 'Pixel', 'Nova', 'Sol'], arena: ['Rook', 'Kiri', 'Pixel'], market: ['Ember', 'Nova'] })) {
    for (const name of who) await say(name, `warp ${warp}`);
    await sleep(3500);
  }

  // Kits made from an inventory: the first item lands in the selected slot and is the kit's icon.
  const kits = [
    ['warrior', '1d', '0', [SWORD, 'diamond_helmet[enchantments={protection:4}]', 'diamond_chestplate[enchantments={protection:4}]',
      'diamond_leggings[enchantments={protection:4}]', 'diamond_boots[enchantments={protection:4,feather_falling:4}]', 'golden_apple 8', 'cooked_beef 32']],
    ['miner', '6h', '0', ['diamond_pickaxe[enchantments={efficiency:5,fortune:3,unbreaking:3},custom_name={text:"Deep Digger",color:"aqua",italic:false}]',
      'torch 64', 'ladder 32', 'bread 32']],
    ['builder', '12h', '500', ['bricks 64', 'oak_planks 64', 'glass 64', 'stone_bricks 64', 'lantern 16']],
    ['elytra', '7d', '0', ['elytra[enchantments={unbreaking:3,mending:1}]', 'firework_rocket 64']],
    ['potions', 'once', '0', ['potion[potion_contents={potion:"minecraft:strong_healing"}] 3', 'potion[potion_contents={potion:"minecraft:long_swiftness"}] 2']],
  ];
  for (const [name, cooldown, cost, items] of kits) {
    await server.lab(`clear ${P}`);
    for (const item of items) {
      const [spec, count] = item.split(/ (?=\d+$)/);
      server.command(`give ${P} ${spec} ${count || 1}`);
    }
    await sleep(1000);
    await say(P, `createkit ${name} ${cooldown} ${cost}`);
    await sleep(600);
  }
  await say(P, 'kit warrior');
  await sleep(600);
  await say(P, 'kit potions');
  await sleep(600);
  await server.lab(`clear ${P}`);
  server.command(`give ${P} ${SWORD} 1`);

  // Money and ranks.
  for (const command of ['lp user Kiri parent set vip', 'lp user Ember parent set builder', 'lp user Pixel parent set vip',
    'eco set Kiri 125k', 'eco set Ember 87.5k', 'eco set Pixel 42k', 'eco set Rook 15k', 'eco set Nova 9800', 'eco set Sol 3200', `eco set ${P} 56k`,
    `advancement grant ${P} everything`, `recipe give ${P} *`]) {
    server.command(command);
    await sleep(300);
  }
  await sleep(4000);
  for (const b of bots.values()) {
    try { b.quit(); } catch { /* gone */ }
  }
  await sleep(2000);
  await server.stop();
  console.log('phase A done');
}

// ── Phase B: the shots, on a slowed clock ───────────────────────────────────────────────────────────────────────
function writeClientOptions() {
  fs.mkdirSync(clientDir, { recursive: true });
  fs.writeFileSync(path.join(clientDir, 'options.txt'), [
    'version:4671', 'guiScale:3', 'renderDistance:8', 'simulationDistance:5', 'maxFps:60', 'graphicsMode:1',
    'pauseOnLostFocus:false', 'narrator:0', 'tutorialStep:none', 'skipMultiplayerWarning:true', 'joinedFirstServer:true',
    'onboardAccessibility:false', 'lang:en_us', 'gamma:1.0', 'fov:0.0', 'bobView:false', 'chatScale:1.0', 'chatWidth:1.0',
    'soundCategory_master:0.0', 'darkMojangStudiosBackground:true', 'hideServerAddress:true', 'renderClouds:"fancy"',
    'entityShadows:true', 'particles:0', 'mipmapLevels:4', 'ao:true', 'menuBackgroundBlurriness:3', 'chatLineSpacing:0.0',
    'notificationDisplayTime:0.5',
  ].join('\n') + '\n');
}

async function shoot(java) {
  const { px, py, pz } = JSON.parse(fs.readFileSync(path.join(REEL, 'plaza.json'), 'utf8'));
  const server = new Server(serverDir(), java, await serverJar('paper', VERSION), FAKE);
  server.start();
  await server.waitFor(/Done \([0-9.,]+s\)!/, 1_800_000, 'slowed server start');
  // A new version of the rules: everyone accepts again, so the rules screen shows on join.
  await server.lab('set config.yml rules.version 2', 300_000);
  server.command('donutessentials reload');
  await server.waitFor(/Reloaded config/, 300_000, 'reload');
  const tp = (who, dx, dz, yaw = 0, pitch = 0, dy = 0) => server.command(`tp ${who} ${px + dx} ${py + dy} ${pz + dz} ${yaw} ${pitch}`);
  const say = (who, line) => server.lab(`sudo ${who} ${line}`, 300_000);

  const xvfb = spawn('Xvfb', [DISPLAY, '-screen', '0', `${W}x${H}x24`, '-nolisten', 'tcp', '-noreset'], { stdio: 'ignore' });
  await sleep(1500);
  // A big arrow instead of the bare server's little cross, so every click reads on a phone.
  x('cursor', 'Adwaita', CURSOR);
  writeClientOptions();
  const client = spawn(portablemc, ['--main-dir', clientDir, 'start', `mojang:${VERSION}`, '-u', P, '--join-server', '127.0.0.1',
    '--join-server-port', String(PORT), '--resolution', `${W}x${H}`, '--jvm', java, '--jvm-arg=-Xmx3G'],
  {
    env: { ...process.env, ...FAKE, DISPLAY, LIBGL_ALWAYS_SOFTWARE: '1', __GL_SYNC_TO_VBLANK: '0', XCURSOR_THEME: 'Adwaita', XCURSOR_SIZE: String(CURSOR) },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  const clientLog = fs.createWriteStream(path.join(REEL, 'client.log'));
  client.stdout.pipe(clientLog);
  client.stderr.pipe(clientLog);
  await server.waitFor(new RegExp(`${P}\\[[^\\]]*\\] logged in`), 3_600_000, 'client join');
  console.log('client joined');
  // Chunks render and the join toasts leave before the first shot.
  await g(9000);
  x('move', 540, 1500);

  // The rules screen: accept it.
  const [accept, done] = [[318, 1871], record('rules', 3.4)];
  await g(500);
  await glide([540, 1500], accept, 1100);
  await g(300);
  x('click', ...accept, 1);
  await done;

  // Everyone else accepts too, and stands around the plaza.
  const bots = new Map();
  for (const name of BOTS) {
    bots.set(name, await joinBot(name, VERSION, null, { keepAlive: 3_600_000 }, PORT));
    await say(name, 'rules accept');
  }
  const place = async () => {
    for (const [name, [dx, dz]] of Object.entries(SPOTS)) tp(name, dx, dz, 0, 0);
    await g(300);
  };
  const camera = () => tp(P, 0.5, 9.5, 180, 8);
  // Blank lines push old messages out of the chat history; unfocused lines fade after 10 game seconds.
  const clearChat = async () => {
    for (let i = 0; i < 24; i++) server.command(`tellraw ${P} ""`);
    await g(11000);
  };
  camera();
  await place();
  server.command(`execute at ${P} run kill @e[type=!player,distance=..64]`);
  await clearChat();

  // Hook: the player hub, the cursor running over every button.
  await say(P, 'menu');
  await g(800);
  x('move', ...slot(10, 4));
  await g(400);
  let shot = record('hook', 4.2);
  await glide(slot(10, 4), slot(16, 4), 3800);
  await shot;
  x('key', 'Escape');
  await g(600);

  // Homes: the menu, a click on "farm", the title countdown, then there.
  await say(P, 'homes');
  await g(800);
  x('move', ...slot(10, 4));
  shot = record('homes', 6.4);
  await glide(slot(10, 4), slot(14, 4), 1200);
  await g(300);
  await glide(slot(14, 4), slot(11, 4), 400);
  await g(200);
  x('click', ...slot(11, 4), 1);
  await shot;
  camera();
  await g(1500);

  // A teleport request: click [Accept] in chat.
  await say('Kiri', `tpa ${P}`);
  await g(600);
  shot = record('tpa', 3.2);
  await g(400);
  x('key', 't');
  await g(200);
  await glide([540, 1300], [140, 1787], 900);
  await g(250);
  x('click', 140, 1787, 1);
  await g(1100);
  x('key', 'Escape');
  await shot;
  // Kiri's own countdown ends after the shot; let it land before everyone goes back to their spots.
  await g(3000);
  await place();
  await g(1000);

  // Warps.
  await say(P, 'warps');
  await g(800);
  x('move', ...slot(10, 4));
  shot = record('warps', 2.6);
  await glide(slot(10, 4), slot(16, 4), 2400);
  await shot;
  x('key', 'Escape');
  await g(600);

  // Kits: warrior is on cooldown; right-click previews it, down to the named sword.
  await say(P, 'kits');
  await g(800);
  x('move', ...slot(12, 4));
  shot = record('kits', 4.4);
  await glide(slot(12, 4), slot(15, 4), 900);
  await g(500);
  x('click', ...slot(15, 4), 3);
  await g(500);
  await glide(slot(15, 4), slot(0, 6), 700);
  await shot;
  x('key', 'Escape');
  await g(600);

  // Economy: a payment arrives, then the richest players.
  shot = record('economy', 3.8);
  await g(200);
  await say('Kiri', `pay ${P} 2.5k`);
  await g(1100);
  await say(P, 'baltop');
  await g(500);
  await glide(slot(13, 6), slot(49, 6), 700);
  await shot;
  x('key', 'Escape');
  await g(600);

  // Chat: ranks, a mention, and [item] with its tooltip.
  server.command(`item replace entity ${P} hotbar.0 with ${SWORD}`);
  await clearChat();
  const chat = (who, text) => bots.get(who).chat(text);
  chat('Kiri', 'anyone up for the arena tonight?');
  await g(500);
  shot = record('chat', 5.0);
  await g(300);
  chat('Ember', `@${P} the new spawn looks amazing`);
  await g(700);
  chat('Pixel', 'what did you get from the warrior kit?');
  await g(700);
  x('key', 't');
  await g(100);
  x('type', 'this! [item]');
  x('key', 'Return');
  await g(700);
  x('key', 't');
  await g(200);
  await glide([540, 1300], [500, 1787], 800);
  await shot;
  x('key', 'Escape');
  await g(600);

  // Moderation: a warning, a mute, a report with staff buttons, the reports menu.
  shot = record('moderation', 5.4);
  await g(200);
  await say(P, 'warn Rook Spamming in chat');
  await g(800);
  await say(P, 'tempmute Rook 30m Advertising');
  await g(800);
  await say('Kiri', 'report Rook Flying around the shop');
  await g(1100);
  await say(P, 'reports');
  await g(400);
  await glide([540, 1300], slot(10, 6), 600);
  await g(800);
  await shot;
  x('key', 'Escape');
  await g(600);

  // Wide shots for the platform pills and the title card, HUD hidden.
  server.command(`gamemode spectator ${P}`);
  tp(P, 11, 13, 140, 26, 7);
  await g(4000);
  x('key', 'F1');
  await g(500);
  await record('wide', 3.8);
  tp(P, 0.5, 12.5, 180, 4, 1.5);
  await g(3000);
  await record('title', 5.2);
  x('key', 'F1');
  server.command(`gamemode survival ${P}`);
  camera();
  await g(3000);

  // Loop end: back at the plaza, the hub opens and the cursor lands where the hook starts.
  shot = record('loopend', 3.6);
  await g(700);
  await say(P, 'menu');
  await g(700);
  await glide([540, 1300], slot(10, 4), 900);
  await shot;
  x('key', 'Escape');
  await g(600);

  // Combat last (the tag lasts 15 game seconds): hit, the timer, and /spawn refused.
  shot = record('combat', 3.2);
  await g(300);
  await server.lab(`hit Rook ${P} 1`, 300_000);
  await g(700);
  await say(P, 'spawn');
  await shot;

  for (const b of bots.values()) {
    try { b.quit(); } catch { /* gone */ }
  }
  client.kill('SIGTERM');
  await sleep(3000);
  await server.stop();
  xvfb.kill('SIGTERM');
  console.log('phase B done');
}

async function main() {
  fs.mkdirSync(REEL, { recursive: true });
  const java = await jdk(21);
  if (!process.argv.includes('--record')) {
    await setup(java);
  }
  if (!process.argv.includes('--setup')) {
    await shoot(java);
  }
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
