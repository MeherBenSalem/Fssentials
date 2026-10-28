// Turns smoke reports into marketing/source/compat.json, the data behind the compatibility card.
// Usage: node tools/smoke/compat.mjs [report.json...]   (default: the newest report in $ESSLAB_HOME/reports)
// With several reports, a later report's result for a server replaces an earlier one (a rerun of some targets).
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, '..', '..');
const lab = process.env.ESSLAB_HOME || path.join(os.homedir(), '.cache', 'donutessentials-smoke');
const reports = path.join(lab, 'reports');
const files = process.argv.length > 2 ? process.argv.slice(2)
  : [path.join(reports, fs.readdirSync(reports).filter((f) => /^smoke-.*\.json$/.test(f)).sort().at(-1))];
const byTarget = new Map();
for (const report of files) {
  for (const result of JSON.parse(fs.readFileSync(report, 'utf8'))) byTarget.set(result.target, result);
}
const file = files.at(-1);
const version = fs.readFileSync(path.join(repo, 'build.gradle'), 'utf8').match(/^version = '([^']+)'/m);
const order = (t) => [t.startsWith('folia') ? 1 : 0, ...t.replace(/^\w+-/, '').split(/[.-]/).map((p) => (/^\d+$/.test(p) ? Number(p) : 0))];
const results = [...byTarget.values()].sort((a, b) => {
  const [x, y] = [order(a.target), order(b.target)];
  for (let i = 0; i < Math.max(x.length, y.length); i++) if ((x[i] ?? -1) !== (y[i] ?? -1)) return (x[i] ?? -1) - (y[i] ?? -1);
  return a.target.localeCompare(b.target);
});

const compat = {
  plugin: version ? version[1] : null,
  date: new Date(fs.statSync(file).mtimeMs).toISOString().slice(0, 10),
  targets: results.map((r) => {
    const [project, rest] = r.target.split(/-(.+)/);
    const [gameVersion, variant] = rest.split(/-(?=[a-z])/);
    return { project, version: gameVersion, variant: variant || null, java: r.java, client: r.bot, vault: r.vault, storage: r.storage,
      upgrade: r.upgrade, essentials: Boolean(r.essentials), passed: r.steps.filter((s) => s.ok).length, steps: r.steps.length,
      errors: r.errors.length, ok: r.ok };
  }),
};
const out = path.join(repo, 'marketing', 'source', 'compat.json');
fs.mkdirSync(path.dirname(out), { recursive: true });
fs.writeFileSync(out, JSON.stringify(compat, null, 2) + '\n');
console.log(`${path.relative(repo, out)}: ${compat.targets.filter((t) => t.ok).length}/${compat.targets.length} targets pass (from ${files.join(', ')})`);
