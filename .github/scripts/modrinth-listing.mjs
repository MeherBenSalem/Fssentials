// Updates the Modrinth project page from the repository: the summary (marketing/curseforge/summary.txt), the
// description (marketing/curseforge/description.md), the icon and the gallery. Gallery images are added once, matched
// by title, so running it again only replaces the text and the icon.
//
// Env: MODRINTH_ID, MODRINTH_TOKEN (needs the "Write projects" scope), DRY_RUN=true to only print what it would do.
// Run from the repository root: node .github/scripts/modrinth-listing.mjs
import fs from 'node:fs';

const API = 'https://api.modrinth.com/v2';
const id = process.env.MODRINTH_ID;
const token = process.env.MODRINTH_TOKEN;
const dry = process.env.DRY_RUN === 'true';

const GALLERY = [
  { file: 'marketing/images/banner.png', title: 'Donut Essentials 3.0', description: 'The All-in-One Update', featured: true },
  { file: 'marketing/images/screenshots/menu-hub.jpg', title: 'The player hub', description: 'Homes, warps, kits, spawn, rtp, balance and mail in /menu.' },
  { file: 'marketing/images/screenshots/menu-homes.jpg', title: 'Homes', description: 'Click to go, shift + right-click to delete.' },
  { file: 'marketing/images/screenshots/menu-warps.jpg', title: 'Warps', description: 'Each warp with its icon, place and visits.' },
  { file: 'marketing/images/screenshots/menu-kits.jpg', title: 'Kits', description: 'Ready, on cooldown, claimed or locked, with a preview.' },
  { file: 'marketing/images/screenshots/menu-baltop.jpg', title: 'Richest players', description: 'The built-in economy, registered with Vault.' },
  { file: 'marketing/images/screenshots/tpa.jpg', title: 'Teleport requests', description: 'Clickable Accept and Deny, a title countdown.' },
  { file: 'marketing/images/screenshots/chat.jpg', title: 'Chat', description: 'Formats, mentions and [item] with the full tooltip.' },
  { file: 'marketing/images/screenshots/moderation.jpg', title: 'Moderation', description: 'History, alts, freeze and reports with buttons.' },
  { file: 'marketing/images/screenshots/rules.jpg', title: 'Rules screen', description: 'New players accept the rules before they play.' },
];

async function call(method, path, body, type) {
  if (dry) {
    console.log(`dry run: ${method} ${path} (${type}, ${body.length} bytes)`);
    return;
  }
  const response = await fetch(API + path, { method, headers: { Authorization: token, 'Content-Type': type }, body });
  if (!response.ok) {
    throw new Error(`${method} ${path}: ${response.status} ${(await response.text()).slice(0, 300)}`);
  }
  console.log(`${method} ${path}: ${response.status}`);
}

if (!id || (!token && !dry)) {
  throw new Error('MODRINTH_ID and MODRINTH_TOKEN are needed');
}
const project = await (await fetch(`${API}/project/${id}`)).json();
const summary = fs.readFileSync('marketing/curseforge/summary.txt', 'utf8').trim();
const body = fs.readFileSync('marketing/curseforge/description.md', 'utf8');
if (summary.length > 256) {
  throw new Error(`the summary is ${summary.length} characters; Modrinth allows 256`);
}

await call('PATCH', `/project/${id}`, JSON.stringify({ description: summary, body }), 'application/json');
await call('PATCH', `/project/${id}/icon?ext=png`, fs.readFileSync('marketing/images/icon.png'), 'image/png');

const present = new Set((project.gallery || []).map((image) => image.title));
for (const [ordering, image] of GALLERY.entries()) {
  if (present.has(image.title)) {
    console.log(`gallery already has "${image.title}"`);
    continue;
  }
  if (!fs.existsSync(image.file)) {
    console.log(`skipping "${image.title}": ${image.file} is missing`);
    continue;
  }
  const ext = image.file.endsWith('.png') ? 'png' : 'jpeg';
  const query = new URLSearchParams({
    ext, featured: String(Boolean(image.featured)), title: image.title, description: image.description, ordering: String(ordering),
  });
  await call('POST', `/project/${id}/gallery?${query}`, fs.readFileSync(image.file), `image/${ext}`);
}
console.log(`https://modrinth.com/plugin/${project.slug} updated${dry ? ' (dry run)' : ''}`);
