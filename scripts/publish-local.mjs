#!/usr/bin/env node
/**
 * Local / CI publish for Donut Essentials (Modrinth + CurseForge).
 * Defaults: MODRINTH_ID=1YpSYvSs, CURSEFORGE_ID=1487015
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

function loadEnvFile(filePath) {
  if (!fs.existsSync(filePath)) {
    return;
  }
  const text = fs.readFileSync(filePath, 'utf8');
  for (const line of text.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) {
      continue;
    }
    const eq = trimmed.indexOf('=');
    if (eq <= 0) {
      continue;
    }
    const key = trimmed.slice(0, eq).trim();
    let value = trimmed.slice(eq + 1).trim();
    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
      value = value.slice(1, -1);
    }
    if (process.env[key] === undefined) {
      process.env[key] = value;
    }
  }
}

loadEnvFile(path.join(repoRoot, '.env'));
loadEnvFile('C:\\Users\\mahou\\NightBeam-Knowledge-Base\\secrets\\local.env');

function parseArgs(argv) {
  const out = { platforms: process.env.PLATFORMS || 'both' };
  for (let i = 2; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--jar' && argv[i + 1]) {
      out.jar = argv[++i];
    } else if (a === '--version' && argv[i + 1]) {
      out.version = argv[++i];
    } else if (a === '--platforms' && argv[i + 1]) {
      out.platforms = argv[++i];
    } else if (a === '--dry-run') {
      out.dryRun = true;
    }
  }
  return out;
}

function readPomVersion() {
  const pom = fs.readFileSync(path.join(repoRoot, 'pom.xml'), 'utf8');
  const m = pom.match(/<version>([^<]+)<\/version>/);
  return m ? m[1].trim() : null;
}

function findJar(version) {
  const target = path.join(repoRoot, 'target');
  if (!fs.existsSync(target)) {
    return null;
  }
  const exact = path.join(target, `donutessentials-${version}.jar`);
  if (fs.existsSync(exact)) {
    return exact;
  }
  const candidates = fs
    .readdirSync(target)
    .filter((n) => n.startsWith('donutessentials-') && n.endsWith('.jar') && !n.startsWith('original-'))
    .map((n) => path.join(target, n));
  return candidates[0] ?? null;
}

function loadSupportedMatrix() {
  const file = path.join(repoRoot, 'release', 'supported-minecraft.json');
  const defaults = {
    loaders: ['paper', 'folia', 'purpur', 'spigot', 'bukkit'],
    game_versions: [
      '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.20.5', '1.20.6',
      '1.21', '1.21.1', '1.21.2', '1.21.3', '1.21.4', '1.21.5', '1.21.6',
      '1.21.7', '1.21.8', '1.21.9', '1.21.10', '1.21.11',
      '26.1', '26.1.1', '26.1.2', '26.2', '26.3',
    ],
  };
  if (!fs.existsSync(file)) {
    return defaults;
  }
  const parsed = JSON.parse(fs.readFileSync(file, 'utf8'));
  return {
    loaders: parsed.loaders ?? defaults.loaders,
    game_versions: parsed.game_versions ?? defaults.game_versions,
  };
}

function readChangelog(version) {
  for (const f of ['PATCHNOTES.md', 'PATCH_NOTES.md', 'CHANGELOG.md']) {
    const p = path.join(repoRoot, f);
    if (fs.existsSync(p)) {
      return fs.readFileSync(p, 'utf8');
    }
  }
  return `Release ${version}`;
}

const args = parseArgs(process.argv);
const version = args.version || process.env.VERSION || readPomVersion();
if (!version) {
  console.error('Could not resolve version (use --version or VERSION env)');
  process.exit(1);
}

const jarPath = args.jar ? path.resolve(args.jar) : findJar(version);
if (!jarPath || !fs.existsSync(jarPath)) {
  console.error('Jar not found. Run mvn -B package or pass --jar');
  process.exit(1);
}

const modrinthId = process.env.MODRINTH_ID || '1YpSYvSs';
const curseforgeId = process.env.CURSEFORGE_ID || '1487015';
const platforms = (args.platforms || 'both').toLowerCase();
const doModrinth = platforms === 'both' || platforms === 'modrinth';
const doCurse = platforms === 'both' || platforms === 'curseforge';

const { loaders, game_versions: gameVersions } = loadSupportedMatrix();
const changelog = readChangelog(version);

if (args.dryRun) {
  console.log('Dry run:', { version, jarPath, modrinthId, curseforgeId, loaders, gameVersions: gameVersions.length });
  process.exit(0);
}

if (doModrinth && !process.env.MODRINTH_TOKEN) {
  console.error('MODRINTH_TOKEN is required for Modrinth upload');
  process.exit(1);
}
if (doCurse && (!process.env.CURSEFORGE_TOKEN || !process.env.CURSEFORGE_API_KEY)) {
  console.error('CURSEFORGE_TOKEN and CURSEFORGE_API_KEY are required for CurseForge upload');
  process.exit(1);
}

(async () => {
  if (doModrinth) {
    const tagRes = await fetch('https://api.modrinth.com/v2/tag/game_version');
    const tags = await tagRes.json();
    const known = new Set(tags.map((t) => t.version));
    const filteredGameVersions = gameVersions.filter((v) => known.has(v));

    const body = {
      name: version,
      version_number: version,
      changelog,
      dependencies: [],
      game_versions: filteredGameVersions,
      version_type: 'release',
      loaders,
      featured: false,
      status: 'listed',
      project_id: modrinthId,
      file_parts: ['file_0'],
      primary_file: 'file_0',
    };
    const form = new FormData();
    form.append('data', JSON.stringify(body));
    form.append('file_0', new Blob([fs.readFileSync(jarPath)]), path.basename(jarPath));
    const mrRes = await fetch('https://api.modrinth.com/v2/version', {
      method: 'POST',
      headers: { Authorization: process.env.MODRINTH_TOKEN },
      body: form,
    });
    const mrText = await mrRes.text();
    if (!mrRes.ok) {
      throw new Error(`Modrinth ${mrRes.status} ${mrText.slice(0, 500)}`);
    }
    console.log('Modrinth OK', version, mrText.slice(0, 160));
  } else {
    console.log('Skipping Modrinth');
  }

  if (!doCurse) {
    console.log('Skipping CurseForge');
    return;
  }

  const gameVersionNames = [
    ...gameVersions,
    'Bukkit',
    'Spigot',
    'Paper',
    'Purpur',
    'Folia',
    'Client',
    'Server',
  ];
  const meta = {
    changelog,
    changelogType: 'markdown',
    displayName: version,
    gameVersionNames,
    releaseType: 'release',
  };
  const cfForm = new FormData();
  cfForm.append('metadata', JSON.stringify(meta));
  cfForm.append('file', new Blob([fs.readFileSync(jarPath)]), path.basename(jarPath));

  let ok = false;
  for (let attempt = 1; attempt <= 3; attempt++) {
    const cfRes = await fetch(
      `https://minecraft.curseforge.com/api/projects/${curseforgeId}/upload-file`,
      { method: 'POST', headers: { 'X-Api-Token': process.env.CURSEFORGE_TOKEN }, body: cfForm },
    );
    const cfText = await cfRes.text();
    console.log('CurseForge attempt', attempt, cfRes.status, cfText.slice(0, 200));
    if (cfRes.ok) {
      ok = true;
      break;
    }
    if (cfRes.status >= 500) {
      await new Promise((r) => setTimeout(r, 15000));
    } else {
      break;
    }
  }
  if (!ok) {
    throw new Error('CurseForge upload failed');
  }
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
