// Boots real Paper and Folia servers across the supported range and drives Donut Essentials with mineflayer bots.
// Every version runs the same scenario (homes, warps, spawn, teleport requests, /back, combat, kits, economy, chat,
// private messages, mail, AFK, moderation, vanish, staff tools, rules, utilities, placeholders, admin commands) and
// the console is scanned for plugin errors. Targets marked `upgrade` first run Donut Essentials 2.0.0 from Modrinth
// and make punishments, an IP lock and a maintenance whitelist with bots, then swap in the new jar: the 3.0 start must
// keep everything. The `essentialsx` target first runs EssentialsX, then switches with /dess import essentials.
//
// Usage (from the repository root, after building the plugin and the fixture, see README.md):
//   node tools/smoke/run.mjs                 # the whole matrix
//   node tools/smoke/run.mjs paper-26.3      # selected targets (project-version[-variant])
//
// Downloads (JDKs, server jars, plugins) are cached in $ESSLAB_HOME (default ~/.cache/donutessentials-smoke).
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import mineflayer from 'mineflayer';
import nbt from 'prismarine-nbt';
import { scenario } from './scenario.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, '..', '..');
const lab = process.env.ESSLAB_HOME || path.join(os.homedir(), '.cache', 'donutessentials-smoke');
const cache = path.join(lab, 'cache');
const PORT = Number(process.env.ESSLAB_PORT || 25640);

// Bots speak the newest protocol mineflayer knows; newer servers get ViaVersion + ViaBackwards.
// `vault`: which Vault plugin is installed (Donut Essentials' own economy registers with it), or none.
export const MATRIX = [
  { project: 'paper', version: '1.20.1', java: 17, bot: '1.20.1', vault: 'Vault' },
  { project: 'paper', version: '1.20.4', java: 17, bot: '1.20.4', vault: 'none' },
  { project: 'paper', version: '1.20.6', java: 21, bot: '1.20.6', vault: 'Vault', upgrade: true },
  { project: 'paper', version: '1.21.1', java: 21, bot: '1.21.1', vault: 'VaultUnlocked' },
  { project: 'paper', version: '1.21.4', java: 21, bot: '1.21.4', vault: 'Vault' },
  { project: 'paper', version: '1.21.8', java: 21, bot: '1.21.8', vault: 'VaultUnlocked' },
  { project: 'paper', version: '1.21.11', java: 21, bot: '1.21.11', vault: 'Vault', upgrade: true, restart: true },
  { project: 'paper', version: '1.21.11', java: 21, bot: '1.21.11', vault: 'Vault', variant: 'essentialsx' },
  { project: 'paper', version: '1.21.11', java: 21, bot: '1.21.11', vault: 'VaultUnlocked', variant: 'mysql', restart: true },
  { project: 'paper', version: '26.1.2', java: 25, bot: '26.1', vault: 'VaultUnlocked', via: true },
  { project: 'paper', version: '26.2', java: 25, bot: '26.1', vault: 'Vault', via: true },
  { project: 'paper', version: '26.3', java: 25, bot: '26.1', vault: 'VaultUnlocked', via: true, upgrade: true },
  { project: 'folia', version: '1.20.4', java: 17, bot: '1.20.4', vault: 'none' },
  { project: 'folia', version: '1.21.4', java: 21, bot: '1.21.4', vault: 'VaultUnlocked' },
  { project: 'folia', version: '1.21.11', java: 21, bot: '1.21.11', vault: 'VaultUnlocked', restart: true },
  { project: 'folia', version: '26.3', java: 25, bot: '26.1', vault: 'VaultUnlocked', via: true, upgrade: true },
];

export const targetName = (t) => `${t.project}-${t.version}${t.variant ? '-' + t.variant : ''}`;

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const AGENT = { 'User-Agent': 'NightBeam-DonutEssentials-smoke' };

async function fetchJson(url) {
  const response = await fetch(url, { headers: AGENT });
  if (!response.ok) throw new Error(`${url}: HTTP ${response.status}`);
  return response.json();
}

async function download(url, file) {
  if (fs.existsSync(file)) return file;
  fs.mkdirSync(path.dirname(file), { recursive: true });
  const response = await fetch(url, { headers: AGENT });
  if (!response.ok) throw new Error(`${url}: HTTP ${response.status}`);
  fs.writeFileSync(file + '.part', Buffer.from(await response.arrayBuffer()));
  fs.renameSync(file + '.part', file);
  return file;
}

export function cached(...parts) {
  const file = path.join(cache, ...parts);
  return fs.existsSync(file) ? file : null;
}

export async function jdk(major) {
  const known = cached('jdk', String(major), 'bin', 'java');
  if (known) return known;
  const dir = path.join(cache, 'jdk', String(major));
  const archive = await download(`https://api.adoptium.net/v3/binary/latest/${major}/ga/linux/x64/jdk/hotspot/normal/eclipse`, dir + '.tar.gz');
  fs.mkdirSync(dir, { recursive: true });
  await run('tar', ['-xzf', archive, '-C', dir, '--strip-components=1']);
  fs.rmSync(archive);
  return path.join(dir, 'bin', 'java');
}

export async function serverJar(project, version) {
  const known = cached('servers', `${project}-${version}.jar`);
  if (known) return known;
  const build = await fetchJson(`https://fill.papermc.io/v3/projects/${project}/versions/${version}/builds/latest`);
  return download(build.downloads['server:default'].url, path.join(cache, 'servers', `${project}-${version}.jar`));
}

/** The newest release of a Modrinth project for a loader (or a given version number). */
export async function modrinth(project, loader = 'paper', versionNumber = null) {
  const name = `${project}-${loader}${versionNumber ? '-' + versionNumber : ''}.jar`;
  const known = cached('plugins', name);
  if (known) return known;
  const versions = await fetchJson(`https://api.modrinth.com/v2/project/${project}/version?loaders=${encodeURIComponent(JSON.stringify([loader]))}`);
  const release = versionNumber ? versions.find((v) => v.version_number === versionNumber)
    : versions.find((v) => v.version_type === 'release') || versions[0];
  if (!release) throw new Error(`no ${project} ${versionNumber || ''} for ${loader}`);
  return download(release.files.find((f) => f.primary)?.url || release.files[0].url, path.join(cache, 'plugins', name));
}

export async function vaultJar(name) {
  if (name === 'none') return null;
  if (name === 'Vault') {
    return cached('plugins', 'Vault-1.7.3.jar')
      || download('https://github.com/MilkBowl/Vault/releases/download/1.7.3/Vault.jar', path.join(cache, 'plugins', 'Vault-1.7.3.jar'));
  }
  return modrinth('vaultunlocked');
}

function run(command, args, options = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { stdio: 'inherit', ...options });
    child.on('exit', (code) => (code === 0 ? resolve() : reject(new Error(`${command} exited with ${code}`))));
  });
}

export function pluginJar() {
  if (process.env.PLUGIN_JAR) return process.env.PLUGIN_JAR;
  const libs = path.join(repo, 'build', 'libs');
  const jar = fs.existsSync(libs) && fs.readdirSync(libs).filter((f) => /^DonutEssentials-\d.*\.jar$/.test(f)).sort().at(-1);
  if (!jar) throw new Error('Build the plugin first: ./gradlew build');
  return path.join(libs, jar);
}

export const fixtureJar = () => path.join(here, 'fixture', 'build', 'libs', 'EssLabFixture.jar');

/** One running server: stdin for console commands, and a line log that waitFor() scans. */
export class Server {
  constructor(dir, java, jar) {
    this.dir = dir;
    this.java = java;
    this.jar = jar;
    this.lines = [];
    this.cursor = 0;
    this.waiters = [];
  }

  start() {
    const log = fs.createWriteStream(path.join(this.dir, 'console.log'), { flags: 'a' });
    this.process = spawn(this.java, ['-Xms1G', '-Xmx3G', '-Dterminal.jline=false', '-Dterminal.ansi=false', '-Dfile.encoding=UTF-8',
      '-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8', '-Dpaper.disableChannelLimit=true', '-jar', this.jar, '--nogui'], { cwd: this.dir });
    let buffer = '';
    const onData = (chunk) => {
      log.write(chunk);
      buffer += chunk.toString('utf8');
      let index;
      while ((index = buffer.indexOf('\n')) >= 0) {
        const line = buffer.slice(0, index).replace(/\x1b\[[0-9;]*m/g, '');
        buffer = buffer.slice(index + 1);
        this.lines.push(line);
        this.waiters = this.waiters.filter((waiter) => !waiter.test(line));
      }
    };
    this.process.stdout.on('data', onData);
    this.process.stderr.on('data', onData);
    this.exited = new Promise((resolve) => this.process.on('exit', resolve));
  }

  /** Resolves with the first line after the cursor that matches. */
  waitFor(pattern, timeout = 30_000, label = String(pattern)) {
    for (let i = this.cursor; i < this.lines.length; i++) {
      const match = this.lines[i].match(pattern);
      if (match) {
        this.cursor = i + 1;
        return Promise.resolve(match);
      }
    }
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.waiters = this.waiters.filter((waiter) => waiter !== entry);
        reject(new Error(`timed out waiting for ${label}`));
      }, timeout);
      const entry = {
        test: (line) => {
          const match = line.match(pattern);
          if (!match) return false;
          clearTimeout(timer);
          this.cursor = this.lines.length;
          resolve(match);
          return true;
        },
      };
      this.waiters.push(entry);
    });
  }

  seen(pattern) {
    return this.lines.some((line) => pattern.test(line));
  }

  mark() {
    this.cursor = this.lines.length;
  }

  command(line) {
    this.process.stdin.write(line + '\n');
  }

  /** Runs a fixture command and returns the text after "[ESSLAB] ". */
  async lab(line, timeout = 20_000) {
    this.mark();
    this.command('esslab ' + line);
    const word = line.split(' ')[0];
    const match = await this.waitFor(new RegExp(`\\[ESSLAB\\] ((?:${word}|error) .*)`), timeout, `esslab ${line}`);
    if (match[1].startsWith('error')) throw new Error(match[1]);
    return match[1];
  }

  async stop() {
    if (!this.process || this.process.exitCode !== null) return;
    this.command('stop');
    const timeout = sleep(90_000).then(() => 'timeout');
    if ((await Promise.race([this.exited, timeout])) === 'timeout') this.process.kill('SIGKILL');
  }
}

/** Plain text of a chat component: JSON text, a JSON string, or network NBT. */
export function plain(component) {
  if (component == null) return '';
  if (typeof component === 'number' || typeof component === 'boolean') return String(component);
  if (typeof component === 'string') {
    if (!/^[{\[]/.test(component.trim())) return component;
    try { return plain(JSON.parse(component)); } catch { return component; }
  }
  if (Array.isArray(component)) return component.map(plain).join('');
  if (typeof component.type === 'string' && 'value' in component) return plain(nbt.simplify(component));
  const text = component.text ?? component[''] ?? '';
  return (typeof text === 'string' ? text : plain(text)) + (Array.isArray(component.extra) ? component.extra.map(plain).join('') : '');
}

/**
 * A mineflayer bot with its chat (plain and raw JSON) and action bars recorded. Bots stand still unless
 * {@code moves} is set: through ViaBackwards, newer servers can reject an older client's movement packets.
 */
export async function joinBot(name, version, chatFile, { moves = false } = {}, port = PORT) {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port, username: name, version, auth: 'offline', checkTimeoutInterval: 120_000 });
  bot.chatLog = [];
  bot.rawLog = [];
  bot.actionBars = [];
  const record = (kind, text) => chatFile && fs.appendFileSync(chatFile, `${kind}\t${text}\n`);
  bot.on('message', (message, position) => {
    if (position === 'game_info') {
      bot.actionBars.push(message.toString());
      return;
    }
    bot.chatLog.push(message.toString());
    try {
      bot.rawLog.push(JSON.stringify(message.json));
    } catch {
      bot.rawLog.push('');
    }
    record(position, message.toString());
  });
  bot._client.on('packet', (data, meta) => {
    if (/action_bar/.test(meta.name)) {
      const text = plain(data.text);
      bot.actionBars.push(text);
      record('action_bar', text);
    }
  });
  if (!moves) {
    bot.physicsEnabled = false;
    bot.once('login', () => { bot.physicsEnabled = false; });
    const write = bot._client.write.bind(bot._client);
    bot._client.write = (packet, params) => (/^(position|position_look|look|flying)$/.test(packet) ? undefined : write(packet, params));
  }
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`${name} did not spawn`)), 120_000);
    bot.once('spawn', () => { clearTimeout(timer); resolve(); });
    bot.once('kicked', (reason) => { clearTimeout(timer); reject(new Error(`${name} kicked: ${JSON.stringify(reason)}`)); });
    bot.once('error', (error) => { clearTimeout(timer); reject(error); });
  });
  // A server stopping under the bot (a restart) closes the connection; the scenario notices on its own.
  bot.on('error', () => {});
  await sleep(1500);
  return bot;
}

/** Overrides on the shipped config.yml that make the scenario fast (short warmups and cooldowns). */
export function smokeConfig(target = {}) {
  let config = fs.readFileSync(path.join(repo, 'src', 'main', 'resources', 'config.yml'), 'utf8');
  config = config.replace(/(\n  warmup-seconds: )3/, '$12')
    .replace(/cooldowns: \{home: 3, warp: 3, spawn: 3, tpa: 10, back: 10, rtp: 120\}/, 'cooldowns: {home: 0, warp: 0, spawn: 0, tpa: 0, back: 0, rtp: 0}')
    .replace(/(update-check:\n  enabled: )true/, '$1false').replace(/(metrics:\n  enabled: )true/, '$1false')
    // Bots can't answer native dialogs, and a short combat tag keeps the run quick.
    .replace(/(dialogs:\n  # .*\n  enabled: )true/, '$1false').replace(/(baltop:\n(?:    #.*\n)*    refresh-seconds: )\d+/, '$15').replace(/(combat:\n  # .*\n  enabled: true\n  seconds: )15/, '$14');
  if (target.variant === 'mysql') {
    // A local MariaDB with a donutessentials database and user (password in $ESSLAB_MYSQL_PASSWORD, default essentials).
    config = config.replace('type: SQLITE', 'type: MYSQL').replace('host: localhost', 'host: 127.0.0.1')
      .replace('password: ""', `password: "${process.env.ESSLAB_MYSQL_PASSWORD || 'essentials'}"`)
      .replace('table-prefix: "de_"', `table-prefix: "smoke${Date.now().toString(36)}_"`);
  }
  return config;
}

function prepare(target, jars) {
  const dir = path.join(lab, 'servers', targetName(target));
  fs.rmSync(dir, { recursive: true, force: true });
  fs.mkdirSync(path.join(dir, 'plugins'), { recursive: true });
  fs.writeFileSync(path.join(dir, 'eula.txt'), 'eula=true\n');
  fs.writeFileSync(path.join(dir, 'server.properties'), [
    'online-mode=false', 'white-list=false', 'enforce-secure-profile=false', `server-port=${PORT}`, 'level-seed=donutessentials-lab',
    'spawn-protection=0', 'view-distance=4', 'simulation-distance=3', 'allow-flight=true', 'motd=Donut Essentials smoke',
    'difficulty=peaceful', 'spawn-monsters=false', 'generate-structures=false', 'pvp=true',
  ].join('\n') + '\n');
  for (const jar of jars.filter(Boolean)) fs.copyFileSync(jar, path.join(dir, 'plugins', path.basename(jar)));
  return dir;
}

/** Plugin errors in the console: severe/error lines from the plugin, stack frames in its packages, linkage errors. */
export function pluginErrors(lines) {
  const problems = [];
  lines.forEach((line, index) => {
    const ours = /\[DonutEssentials\]/.test(line) && /(ERROR|SEVERE|WARN)/.test(line);
    // Paper prints plugin frames as "at DonutEssentials-3.0.0.jar//dev.nightbeam...".
    const frame = /at (?:\S+\/\/)?dev\.nightbeam\.donutessentials\./.test(line);
    const load = /Could not load '.*DonutEssentials|Error occurred while (?:enabling|disabling) DonutEssentials|NoSuchMethodError|NoClassDefFoundError|NoSuchFieldError|AbstractMethodError|IncompatibleClassChangeError/.test(line);
    if (ours || load || (frame && !problems.includes(lines[index - 1]))) problems.push(line);
  });
  return problems;
}

/** Donut Essentials 2.0.0 gets punishments, an IP lock and a maintenance whitelist, before the 3.0 jar replaces it. */
async function legacyPhase(target, dir, java, jar, result) {
  const legacy = await modrinth('1YpSYvSs', target.project === 'folia' ? 'folia' : 'paper', '2.0.0');
  const plugins = path.join(dir, 'plugins');
  fs.copyFileSync(legacy, path.join(plugins, 'DonutEssentials-2.0.0.jar'));
  const server = new Server(dir, java, jar);
  server.start();
  const bots = [];
  try {
    await server.waitFor(/Done \([0-9.,]+s\)!/, 300_000, 'legacy server start');
    if (!server.seen(/Donut Essentials is online/)) throw new Error('Donut Essentials 2.0.0 did not enable');
    for (const name of ['Alice', 'Hank', 'Draxen']) bots.push(await joinBot(name, target.bot, path.join(dir, `chat-legacy-${name}.log`)));
    for (const command of ['warn Draxen Being rude', 'tempmute Draxen 1d Spamming', 'note Draxen Watch this one', 'ban Hank Griefing',
      'iplock set Alice 127.0.0.1', 'maintenance add Willow']) {
      server.command(command);
      await sleep(800);
    }
    await sleep(1500);
    result.steps.push({ label: '2.0.0 install made punishments', ok: true, detail: 'a warn, a mute, a note, a ban, an IP lock, a whitelist entry' });
  } catch (error) {
    result.steps.push({ label: '2.0.0 install made punishments', ok: false, detail: error.message });
  } finally {
    for (const bot of bots) {
      try { bot.quit(); } catch { /* gone */ }
    }
    await server.stop();
    fs.rmSync(path.join(plugins, 'DonutEssentials-2.0.0.jar'));
  }
}

/** EssentialsX with homes, a warp, a spawn and balances, before Donut Essentials replaces it (/dess import essentials). */
async function essentialsPhase(target, dir, java, jar, result) {
  const plugins = path.join(dir, 'plugins');
  const essentials = await modrinth('essentialsx', 'paper');
  const spawn = await modrinth('essentialsx-spawn', 'paper');
  fs.copyFileSync(essentials, path.join(plugins, 'EssentialsX.jar'));
  fs.copyFileSync(spawn, path.join(plugins, 'EssentialsXSpawn.jar'));
  const server = new Server(dir, java, jar);
  server.start();
  const bots = [];
  try {
    await server.waitFor(/Done \([0-9.,]+s\)!/, 300_000, 'EssentialsX server start');
    const willow = await joinBot('Willow', target.bot, path.join(dir, 'chat-ess-Willow.log'));
    bots.push(willow);
    const rook = await joinBot('Rook', target.bot, path.join(dir, 'chat-ess-Rook.log'));
    bots.push(rook);
    // EssentialsX needs a permission for /sethome, and the fixture has no permissions plugin.
    server.command('op Willow');
    server.command('op Rook');
    await sleep(800);
    for (const line of ['/sethome base', '/sethome farm', '/setwarp market', '/setspawn', '/nick Willo']) {
      willow.chat(line);
      await sleep(900);
    }
    rook.chat('/sethome tower');
    await sleep(800);
    server.command('eco set Willow 4321');
    server.command('eco set Rook 75');
    server.command('ban Draxen Griefing');
    await sleep(2500);
    result.steps.push({ label: 'EssentialsX install made data', ok: true, detail: '3 homes, a warp, a spawn, 2 balances, a nickname, a ban' });
  } catch (error) {
    result.steps.push({ label: 'EssentialsX install made data', ok: false, detail: error.message });
  } finally {
    for (const bot of bots) {
      try { bot.quit(); } catch { /* gone */ }
    }
    await server.stop();
    fs.rmSync(path.join(plugins, 'EssentialsX.jar'));
    fs.rmSync(path.join(plugins, 'EssentialsXSpawn.jar'));
  }
}

async function runTarget(target) {
  const name = targetName(target);
  const started = Date.now();
  const java = await jdk(target.java);
  const jars = [fixtureJar(), await vaultJar(target.vault), cached('plugins', 'PlaceholderAPI.jar') || await modrinth('placeholderapi')];
  if (target.via) {
    jars.push(await modrinth('viaversion'));
    jars.push(await modrinth('viabackwards'));
  }
  const dir = prepare(target, jars);
  const jar = await serverJar(target.project, target.version);
  const result = { target: name, java: target.java, bot: target.bot, vault: target.vault, storage: target.variant === 'mysql' ? 'MySQL' : 'SQLite',
    upgrade: Boolean(target.upgrade), essentials: target.variant === 'essentialsx', steps: [], errors: [] };
  if (target.upgrade) {
    await legacyPhase(target, dir, java, jar, result);
  }
  if (target.variant === 'essentialsx') {
    await essentialsPhase(target, dir, java, jar, result);
  }
  fs.copyFileSync(pluginJar(), path.join(dir, 'plugins', path.basename(pluginJar())));
  const folder = path.join(dir, 'plugins', 'DonutEssentials');
  fs.mkdirSync(folder, { recursive: true });
  if (!target.upgrade) {
    fs.writeFileSync(path.join(folder, 'config.yml'), smokeConfig(target));
  }
  let server = new Server(dir, java, jar);
  server.start();
  const bots = [];
  const restart = async () => {
    // Stopped with the players still online (one with a menu open): the plugin must disable cleanly.
    await server.stop();
    for (const bot of bots.splice(0)) {
      try { bot.quit(); } catch { /* gone */ }
    }
    const before = server.lines;
    server = new Server(dir, java, jar);
    server.lines = [...before, '--- restart ---'];
    server.cursor = server.lines.length;
    server.start();
    await server.waitFor(/Done \([0-9.,]+s\)!/, 300_000, 'server restart');
    await server.waitFor(/\[ESSLAB\] api listening/, 30_000, 'fixture API listener');
    return server;
  };
  try {
    await server.waitFor(/Done \([0-9.,]+s\)!/, 300_000, 'server start');
    await server.waitFor(/\[ESSLAB\] api (listening|absent)/, 30_000, 'fixture API listener');
    if (target.upgrade) {
      // The upgraded config keeps the 2.x values; speed up what the scenario waits on.
      for (const [key, value] of [['teleport.warmup-seconds', 2], ['teleport.cooldowns.home', 0], ['teleport.cooldowns.warp', 0],
        ['teleport.cooldowns.spawn', 0], ['teleport.cooldowns.tpa', 0], ['teleport.cooldowns.back', 0], ['teleport.cooldowns.rtp', 0],
        ['update-check.enabled', false], ['dialogs.enabled', false], ['combat.seconds', 4]]) {
        await server.lab(`set config.yml ${key} ${value}`);
      }
      server.command('donutessentials reload');
      await server.waitFor(/Reloaded config/, 20_000, 'reload');
    }
    await scenario({
      server: () => server, target, restart, dir,
      joinBot: async (botName, options) => {
        const bot = await joinBot(botName, target.bot, path.join(dir, `chat-${botName}.log`), options);
        bots.push(bot);
        return bot;
      },
      step: (label, ok, detail = '') => {
        result.steps.push({ label, ok, detail });
        console.log(`  ${ok ? 'PASS' : 'FAIL'} ${label}${detail ? ` (${detail})` : ''}`);
      },
    });
  } catch (error) {
    result.steps.push({ label: 'scenario', ok: false, detail: error.message });
    console.log(`  FAIL scenario (${error.message})`);
  } finally {
    for (const bot of bots) {
      try { bot.quit(); } catch { /* already gone */ }
    }
    await server.stop();
  }
  result.errors = pluginErrors(server.lines);
  result.ok = result.steps.length > 0 && result.steps.every((s) => s.ok) && result.errors.length === 0;
  result.seconds = Math.round((Date.now() - started) / 1000);
  return result;
}

async function main() {
  const wanted = process.argv.slice(2);
  const targets = MATRIX.filter((t) => wanted.length === 0 || wanted.includes(targetName(t)));
  const results = [];
  for (const target of targets) {
    console.log(`\n== ${targetName(target)} (Java ${target.java}, bot ${target.bot}${target.via ? ' via ViaBackwards' : ''}, ${target.vault}${target.upgrade ? ', from 2.0.0' : ''})`);
    const result = await runTarget(target);
    for (const error of result.errors.slice(0, 20)) console.log(`  ERROR ${error}`);
    console.log(`  ${result.ok ? 'PASS' : 'FAIL'} in ${result.seconds}s`);
    results.push(result);
  }
  fs.mkdirSync(path.join(lab, 'reports'), { recursive: true });
  const report = path.join(lab, 'reports', `smoke-${new Date().toISOString().replace(/[:.]/g, '-')}.json`);
  fs.writeFileSync(report, JSON.stringify(results, null, 2));
  console.log('\n| Server | Java | Vault | From 2.0.0 | Steps | Plugin errors | Result |\n| --- | --- | --- | --- | --- | --- | --- |');
  for (const r of results) {
    console.log(`| ${r.target} | ${r.java} | ${r.vault} | ${r.upgrade ? 'yes' : '-'} | ${r.steps.filter((s) => s.ok).length}/${r.steps.length} | ${r.errors.length} | ${r.ok ? 'PASS' : 'FAIL'} |`);
  }
  console.log(`\nReport: ${report}`);
  process.exit(results.every((r) => r.ok) ? 0 : 1);
}

if (process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1])) {
  main().catch((error) => {
    console.error(error);
    process.exit(1);
  });
}
