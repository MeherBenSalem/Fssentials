// Captures the listing screenshots and GIFs from a real game: a Paper server running Donut Essentials (with Vault,
// LuckPerms and PlaceholderAPI) and the unmodified vanilla Minecraft client (software rendering under Xvfb), driven
// through console commands and XTEST mouse and keyboard input. mineflayer bots are the other players.
//
// Needs: Xvfb, ImageMagick (import), ffmpeg, python3 with python-xlib, portablemc, and the smoke-test setup
// (tools/smoke, its fixture built). Run from the repository root after ./gradlew build:
//   node tools/marketing/capture.mjs                          # every scene and both console captures (about 30 minutes)
//   node tools/marketing/capture.mjs --scenes=base,homes,kits # just those scenes (base sets up the world first)
//   node tools/marketing/capture.mjs --consoles-only          # only the upgrade and EssentialsX import consoles
import { spawn, execFileSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { Server, joinBot, jdk, serverJar, pluginJar, vaultJar, modrinth, fixtureJar } from '../smoke/run.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, '..', '..');
const lab = process.env.ESSLAB_HOME || path.join(os.homedir(), '.cache', 'donutessentials-smoke');
const PORT = Number(process.env.CAPTURE_PORT || 25650);
const VERSION = process.env.CAPTURE_VERSION || '1.21.11';
const PLAYER = 'Moonbeam';
const DISPLAY = process.env.CAPTURE_DISPLAY || ':99';
const W = 1920;
const H = 1080;
const SCALE = 3;
const shots = path.join(repo, 'marketing', 'images', 'screenshots');
const gifs = path.join(repo, 'marketing', 'images', 'gifs');
const consoleDir = path.join(repo, 'marketing', 'source', 'console');
const python = process.env.CAPTURE_PYTHON || path.join(lab, 'venv', 'bin', 'python3');
const portablemc = process.env.PORTABLEMC || path.join(lab, 'venv', 'bin', 'portablemc');
const clientDir = process.env.CAPTURE_CLIENT_DIR || path.join(lab, 'client');

// Noon, clear sky, no command echo in chat. 1.21.11 renamed the game rules, so both spellings are sent.
const WORLD_SETUP = ['time set 6000', 'weather clear',
  'gamerule advance_time false', 'gamerule advance_weather false', 'gamerule show_advancement_messages false', 'gamerule send_command_feedback false',
  'gamerule doDaylightCycle false', 'gamerule doWeatherCycle false', 'gamerule announceAdvancements false', 'gamerule sendCommandFeedback false',
  'gamerule spawn_mobs false', 'gamerule doMobSpawning false', 'gamerule log_admin_commands false', 'gamerule logAdminCommands false'];

// What the shots show: the rules screen for new players, server links, quick leaderboards, and nothing popping into
// chat on its own (announcements, AFK after 5 minutes).
const CONFIG = [
  ['update-check.enabled', false], ['metrics.enabled', false], ['rules.require-accept', true], ['server-links.enabled', true],
  ['economy.baltop.refresh-seconds', 3], ['announcements.enabled', false], ['afk.idle-minutes', 120],
  ['teleport.cooldowns.home', 0], ['teleport.cooldowns.warp', 0], ['teleport.cooldowns.spawn', 0], ['teleport.cooldowns.tpa', 0],
  ['teleport.cooldowns.back', 0], ['teleport.cooldowns.rtp', 0],
];

// LuckPerms groups: chat prefixes come from the permissions plugin through Vault; config.yml has a format for admin.
const GROUPS = [
  'lp creategroup admin', 'lp creategroup vip', 'lp creategroup builder',
  'lp group vip meta setprefix 100 "&6[VIP] &f"', 'lp group builder meta setprefix 100 "&b[Builder] &f"',
  'lp group vip permission set donutessentials.tier.vip true',
];

export const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
export const x = (...args) => execFileSync(python, [path.join(here, 'xinput.py'), ...args.map(String)], { env: { ...process.env, DISPLAY } });
/** Holds a key in the background (e.g. Tab for the player list) and resolves when it is released. */
export const holdKey = (name, seconds) => new Promise((resolve) => {
  const proc = spawn(python, [path.join(here, 'xinput.py'), 'hold', name, String(seconds)], { env: { ...process.env, DISPLAY }, stdio: 'ignore' });
  proc.on('exit', resolve);
});

/** Pixel centre of a slot in a chest menu with the given number of rows (GUI scale 3). */
export function slot(index, rows) {
  const left = Math.floor((W / SCALE - 176) / 2);
  const top = Math.floor((H / SCALE - (114 + rows * 18)) / 2);
  if (index >= rows * 9) {
    // The player's inventory: 3 rows, then the hotbar 4 px lower.
    const inv = index - rows * 9;
    const row = Math.floor(inv / 9);
    const y = top + 18 + rows * 18 + 13 + (row < 3 ? row * 18 : 3 * 18 + 4);
    return [(left + 8 + (inv % 9) * 18 + 8) * SCALE, (y + 8) * SCALE];
  }
  return [(left + 8 + (index % 9) * 18 + 8) * SCALE, (top + 18 + Math.floor(index / 9) * 18 + 8) * SCALE];
}

export function screenshot(name, quality = 92) {
  fs.mkdirSync(shots, { recursive: true });
  const file = path.join(shots, `${name}.jpg`);
  execFileSync('import', ['-display', DISPLAY, '-window', 'root', '-quality', String(quality), file]);
  console.log(`  captured ${path.relative(repo, file)}`);
  return file;
}

export async function hover(index, rows = 6, settle = 800) {
  const [px, py] = slot(index, rows);
  x('move', px - 9, py - 9);
  await sleep(120);
  x('move', px, py);
  await sleep(settle);
}

export async function clickSlot(index, rows = 6, settle = 900, button = 1) {
  const [px, py] = slot(index, rows);
  x('click', px, py, button);
  await sleep(settle);
}

export const park = async () => {
  x('move', 60, 60);
  await sleep(700);
};

/** Records the screen for `seconds` (x11grab); returns a promise for the finished MP4. */
export function record(name, seconds) {
  fs.mkdirSync(path.join(lab, 'capture'), { recursive: true });
  const raw = path.join(lab, 'capture', `${name}.mp4`);
  const proc = spawn('ffmpeg', ['-y', '-loglevel', 'error', '-f', 'x11grab', '-video_size', `${W}x${H}`, '-framerate', '20', '-i', `${DISPLAY}.0`,
    '-t', String(seconds), '-c:v', 'libx264', '-preset', 'ultrafast', '-crf', '16', raw], { stdio: 'ignore' });
  return new Promise((resolve) => proc.on('exit', () => resolve(raw)));
}

/**
 * A GIF from recordings played back to back, by default the centre 1280x720 scaled to 640x360.
 * Parts: { raw, start?, length? }.
 */
export function gif(parts, name, crop = '1280:720:320:180', size = '640:360') {
  fs.mkdirSync(gifs, { recursive: true });
  const file = path.join(gifs, `${name}.gif`);
  const inputs = parts.flatMap(({ raw, start = 0, length = null }) =>
    [...(start ? ['-ss', start.toFixed(2)] : []), ...(length ? ['-t', length.toFixed(2)] : []), '-i', raw]);
  const joined = parts.map((_, i) => `[${i}:v]`).join('') + `concat=n=${parts.length}:v=1:a=0,`;
  execFileSync('ffmpeg', ['-y', '-loglevel', 'error', ...inputs, '-filter_complex',
    `${joined}crop=${crop},fps=12,scale=${size}:flags=lanczos,split[a][b];[a]palettegen=max_colors=160:stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=4:diff_mode=rectangle`,
    file]);
  console.log(`  recorded ${path.relative(repo, file)}`);
}

function writeClientOptions() {
  fs.mkdirSync(clientDir, { recursive: true });
  fs.writeFileSync(path.join(clientDir, 'options.txt'), [
    'version:4671', 'guiScale:3', 'renderDistance:8', 'simulationDistance:5', 'maxFps:30', 'graphicsMode:1',
    'pauseOnLostFocus:false', 'narrator:0', 'tutorialStep:none', 'skipMultiplayerWarning:true', 'joinedFirstServer:true',
    'onboardAccessibility:false', 'lang:en_us', 'gamma:1.0', 'fov:0.0', 'bobView:false', 'chatScale:1.0', 'chatWidth:1.0',
    'soundCategory_master:0.0', 'darkMojangStudiosBackground:true', 'hideServerAddress:true', 'renderClouds:"fancy"',
    'entityShadows:true', 'particles:0', 'mipmapLevels:4', 'ao:true', 'menuBackgroundBlurriness:3', 'chatLineSpacing:0.0',
    // Toasts (advancements, recipes, "chat messages can't be verified") leave the screen sooner.
    'notificationDisplayTime:0.5',
  ].join('\n') + '\n');
}

function serverProperties(dir, extra = []) {
  fs.writeFileSync(path.join(dir, 'eula.txt'), 'eula=true\n');
  fs.writeFileSync(path.join(dir, 'server.properties'), [
    'online-mode=false', 'white-list=false', 'enforce-secure-profile=false', `server-port=${PORT}`, 'spawn-protection=0',
    'difficulty=peaceful', 'spawn-monsters=false', 'allow-flight=true', 'pvp=true', ...extra,
  ].join('\n') + '\n');
}

async function prepareServer(java) {
  const dir = path.join(lab, 'capture', 'server');
  fs.rmSync(dir, { recursive: true, force: true });
  fs.mkdirSync(path.join(dir, 'plugins'), { recursive: true });
  serverProperties(dir, ['level-seed=8675309', 'view-distance=8', 'simulation-distance=5', 'motd=Donut Essentials', 'gamemode=survival']);
  for (const jar of [pluginJar(), fixtureJar(), await vaultJar('Vault'), await modrinth('placeholderapi'), await modrinth('luckperms', 'bukkit')]) {
    fs.copyFileSync(jar, path.join(dir, 'plugins', path.basename(jar)));
  }
  return { dir, server: new Server(dir, java, await serverJar('paper', VERSION)) };
}

/** Starts the server, Xvfb and the client; resolves once the client has joined. */
export async function boot() {
  const java = await jdk(21);
  const { server } = await prepareServer(java);
  server.start();
  await server.waitFor(/Done \([0-9.,]+s\)!/, 600_000, 'server start');
  for (const command of WORLD_SETUP) server.command(command);
  for (const [key, value] of CONFIG) await server.lab(`set config.yml ${key} ${value}`);
  server.command('donutessentials reload');
  await server.waitFor(/Reloaded config/, 20_000, 'reload');
  for (const command of GROUPS) {
    server.command(command);
    await sleep(400);
  }
  server.command(`op ${PLAYER}`);
  const xvfb = spawn('Xvfb', [DISPLAY, '-screen', '0', `${W}x${H}x24`, '-nolisten', 'tcp'], { stdio: 'ignore' });
  await sleep(1500);
  writeClientOptions();
  const client = spawn(portablemc, ['--main-dir', clientDir, 'start', `mojang:${VERSION}`, '-u', PLAYER, '--join-server', '127.0.0.1',
    '--join-server-port', String(PORT), '--resolution', `${W}x${H}`, '--jvm', java, '--jvm-arg=-Xmx3G'],
  { env: { ...process.env, DISPLAY, LIBGL_ALWAYS_SOFTWARE: '1', __GL_SYNC_TO_VBLANK: '0' }, stdio: ['ignore', 'pipe', 'pipe'] });
  fs.mkdirSync(path.join(lab, 'capture'), { recursive: true });
  const clientLog = fs.createWriteStream(path.join(lab, 'capture', 'client.log'));
  client.stdout.pipe(clientLog);
  client.stderr.pipe(clientLog);
  await server.waitFor(new RegExp(`${PLAYER}\\[[^\\]]*\\] logged in`), 900_000, 'client join');
  console.log('client joined');
  const stop = async () => {
    client.kill('SIGTERM');
    await sleep(2000);
    await server.stop();
    xvfb.kill('SIGTERM');
  };
  return { server, stop };
}

/** The plugin's own console lines since `from`, without the logger prefix. */
function pluginLines(server, from, pattern = /\[DonutEssentials\]|Donut »/) {
  return server.lines.slice(from).filter((line) => pattern.test(line) && !/Loading server plugin|Enabling DonutEssentials|Hikari|PluginRemapper/.test(line))
    .map((line) => line.replace(/^\[[^\]]*(INFO|WARN)\]:? ?/, '').replace(/^\[DonutEssentials\] /, ''));
}

/**
 * A separate server that runs Donut Essentials 2.0.0 first (a ban, a mute, a warning, an IP lock and a maintenance
 * whitelist entry), then 3.0: the console of that first 3.0 start is the upgrade card.
 */
async function upgradeConsole(java) {
  const dir = path.join(lab, 'capture', 'upgrade');
  fs.rmSync(dir, { recursive: true, force: true });
  fs.mkdirSync(path.join(dir, 'plugins'), { recursive: true });
  serverProperties(dir, ['level-type=minecraft\\:flat', 'generate-structures=false', 'view-distance=3']);
  const legacy = await modrinth('1YpSYvSs', 'paper', '2.0.0');
  fs.copyFileSync(legacy, path.join(dir, 'plugins', 'DonutEssentials-2.0.0.jar'));
  fs.copyFileSync(await vaultJar('Vault'), path.join(dir, 'plugins', 'Vault.jar'));
  const jar = await serverJar('paper', VERSION);
  let server = new Server(dir, java, jar);
  server.start();
  await server.waitFor(/Done \([0-9.,]+s\)!/, 300_000, 'legacy start');
  const bots = [];
  for (const name of ['Draxen', 'Kiri']) bots.push(await joinBot(name, VERSION, null, {}, PORT));
  for (const command of ['warn Draxen Spamming', 'tempmute Draxen 1d Spamming', 'ban Draxen Griefing', 'iplock set Kiri 127.0.0.1',
    'maintenance add Kiri']) {
    server.command(command);
    await sleep(900);
  }
  for (const bot of bots) bot.quit();
  await server.stop();
  fs.rmSync(path.join(dir, 'plugins', 'DonutEssentials-2.0.0.jar'));
  fs.copyFileSync(pluginJar(), path.join(dir, 'plugins', path.basename(pluginJar())));
  server = new Server(dir, java, jar);
  const from = 0;
  server.start();
  await server.waitFor(/Donut Essentials 3\.\d+\.\d+ enabled on/, 300_000, 'upgrade start');
  await sleep(1000);
  const lines = pluginLines(server, from);
  await server.stop();
  fs.mkdirSync(consoleDir, { recursive: true });
  fs.writeFileSync(path.join(consoleDir, 'upgrade.txt'), lines.join('\n') + '\n');
  console.log(`  upgrade console: ${lines.length} lines`);
}

/**
 * EssentialsX with homes, warps, a spawn, balances and a nickname, then Donut Essentials in its place:
 * the console of /donutessentials import essentials (the dry run, then confirm) is the switch card.
 */
async function importConsole(java) {
  const dir = path.join(lab, 'capture', 'import');
  fs.rmSync(dir, { recursive: true, force: true });
  fs.mkdirSync(path.join(dir, 'plugins'), { recursive: true });
  serverProperties(dir, ['level-type=minecraft\\:flat', 'generate-structures=false', 'view-distance=3']);
  fs.copyFileSync(await modrinth('essentialsx', 'paper'), path.join(dir, 'plugins', 'EssentialsX.jar'));
  fs.copyFileSync(await modrinth('essentialsx-spawn', 'paper'), path.join(dir, 'plugins', 'EssentialsXSpawn.jar'));
  fs.copyFileSync(await vaultJar('Vault'), path.join(dir, 'plugins', 'Vault.jar'));
  const jar = await serverJar('paper', VERSION);
  let server = new Server(dir, java, jar);
  server.start();
  await server.waitFor(/Done \([0-9.,]+s\)!/, 300_000, 'EssentialsX start');
  const players = { Willow: ['/sethome base', '/sethome farm', '/setwarp market', '/setwarp arena', '/setspawn', '/nick Willo'],
    Rook: ['/sethome tower', '/sethome mine'], Kiri: ['/sethome home'] };
  const bots = [];
  for (const [name, lines] of Object.entries(players)) {
    const bot = await joinBot(name, VERSION, null, {}, PORT);
    bots.push(bot);
    server.command(`op ${name}`);
    await sleep(800);
    for (const line of lines) {
      bot.chat(line);
      await sleep(900);
    }
  }
  for (const command of ['eco set Willow 4321', 'eco set Rook 750', 'eco set Kiri 12500', 'ban Draxen Griefing']) server.command(command);
  await sleep(2500);
  for (const bot of bots) bot.quit();
  await server.stop();
  fs.rmSync(path.join(dir, 'plugins', 'EssentialsX.jar'));
  fs.rmSync(path.join(dir, 'plugins', 'EssentialsXSpawn.jar'));
  fs.copyFileSync(pluginJar(), path.join(dir, 'plugins', path.basename(pluginJar())));
  server = new Server(dir, java, jar);
  server.start();
  await server.waitFor(/Done \([0-9.,]+s\)!/, 300_000, 'import start');
  const from = server.lines.length;
  server.command('donutessentials import essentials');
  await server.waitFor(/Nothing was changed/, 60_000, 'dry run');
  server.command('donutessentials import essentials confirm');
  await server.waitFor(/You can remove EssentialsX now/, 60_000, 'import');
  await sleep(500);
  // The summaries come from an async thread, so take every plugin message line, not only the logger's.
  const lines = server.lines.slice(from).filter((line) => !/issued server command|EssLab|\[Vault\]/.test(line))
    .map((line) => line.replace(/^\[[^\]]*(INFO|WARN)\]:? ?/, '').replace(/^\[DonutEssentials\] /, ''))
    .filter((line) => line.trim() && !/^Imported from EssentialsX: /.test(line));
  await server.stop();
  // The dry run, then what confirm did (it reads the same data again first; that part is left out).
  const dry = lines.slice(0, lines.findIndex((line) => /Nothing was changed/.test(line)) + 1);
  const done = lines.slice(lines.findIndex((line) => /^Imported from EssentialsX/.test(line)), lines.findIndex((line) => /remove EssentialsX now/.test(line)) + 1);
  fs.mkdirSync(consoleDir, { recursive: true });
  fs.writeFileSync(path.join(consoleDir, 'import.txt'),
    ['> donutessentials import essentials', ...dry, '> donutessentials import essentials confirm', ...done].join('\n') + '\n');
  console.log(`  import console: ${lines.length} lines`);
}

const bots = new Map();
export async function bot(server, name) {
  if (bots.has(name)) return bots.get(name);
  const b = await joinBot(name, VERSION, null, {}, PORT);
  bots.set(name, b);
  return b;
}

async function main() {
  // --scenes=base,homes reruns only those scenes (in their usual order) and skips the console captures.
  const only = process.argv.find((arg) => arg.startsWith('--scenes='))?.slice('--scenes='.length).split(',');
  if (process.argv.includes('--consoles-only') || !only) {
    const java = await jdk(21);
    await upgradeConsole(java);
    await importConsole(java);
    if (process.argv.includes('--consoles-only')) {
      return;
    }
  }
  const { server, stop } = await boot();
  try {
    const { scenes } = await import('./scenes.mjs');
    await scenes({ server, bot: (name) => bot(server, name), lab, consoleDir, only });
  } finally {
    for (const b of bots.values()) {
      try { b.quit(); } catch { /* gone */ }
    }
    await stop();
  }
}

if (process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1])) {
  main().catch((error) => {
    console.error(error);
    process.exit(1);
  });
}
