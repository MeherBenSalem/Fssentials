// The listing artwork. Each entry returns { html, width, height, transparent?, file? } for render.cjs.
// Crops are pixel boxes [x, y, w, h] of the 1920x1080 captures in images/screenshots/ (GUI scale 3). Menus and their
// tooltips are measured by tools/marketing/crops.py into crops.json ("<name>-tip" is a tooltip alone); chat lines,
// titles and dialogs sit at fixed places on screen and are listed here.
const fs = require('fs');
const path = require('path');
const r = require('./render.cjs');

const HEADERS = {
  hub: 'The Player Hub', teleports: 'Teleports', homes: 'Homes', warps: 'Warps & Spawn', kits: 'Kits', economy: 'Economy',
  chat: 'Chat & Messages', moderation: 'Moderation', staff: 'Staff Tools', rules: 'Rules & Server Links', yours: 'Make It Yours',
  switch: 'Switching from EssentialsX', upgrade: 'Upgrading from 2.x', integrations: 'Integrations', compatibility: 'Compatibility',
  commands: 'Commands & Permissions', faq: 'Questions', gallery: 'Gallery',
};

/** Chat lines at GUI scale 3: the newest line ends at y = 962 (the hearts start below), each line is 27 px, the chat box
 * is 996 px wide. */
const chat = (lines, width = 996, bottom = 962) => [0, bottom - lines * 27 - 3, width, lines * 27 + 3];

const MEASURED = path.join(__dirname, 'crops.json');
const CROP = Object.assign({
  'tpa-lines': chat(2, 620),
  'combat-lines': chat(2, 770),
  'pay-lines': chat(2, 870),
  'messages-lines': chat(3, 620),
  'moderation-lines': chat(6, 940),
  'history-lines': chat(3, 920),
  'whois-lines': chat(12, 760),
  'broadcast-lines': chat(4),
  'afk-lines': chat(2),
  // The open chat with the [item] tooltip over it.
  'chat-item-scene': [0, 848, 1000, 228],
  'home-warmup-scene': [0, 0, 1920, 1080],
  'home-landed-scene': [0, 0, 1920, 1080],
  'rules-dialog-scene': [240, 0, 1440, 1080],
  'server-links-scene': [470, 10, 980, 390],
  'afk-tab-scene': [820, 10, 280, 225],
  'combat-bar': [760, 852, 400, 48],
}, fs.existsSync(MEASURED) ? JSON.parse(fs.readFileSync(MEASURED, 'utf8')) : {});
const FILES = {
  'tpa-lines': 'tpa-request', 'combat-lines': 'combat', 'pay-lines': 'pay-chat', 'messages-lines': 'messages',
  'moderation-lines': 'moderation-chat', 'history-lines': 'history', 'whois-lines': 'whois', 'broadcast-lines': 'broadcast',
  'afk-lines': 'afk-chat', 'chat-item-scene': 'chat-item', 'home-warmup-scene': 'home-warmup', 'home-landed-scene': 'home-landed',
  'rules-dialog-scene': 'rules-dialog', 'server-links-scene': 'server-links', 'afk-tab-scene': 'afk-tab', 'combat-bar': 'combat',
};

/** Width and height of a crop scaled to fit maxW x maxH. */
const fitted = (name, maxW, maxH) => {
  const box = CROP[name];
  if (!box) throw new Error(`no crop for ${name}`);
  const width = Math.round(Math.min(maxW, maxH * box[2] / box[3]));
  return [width, Math.round(width * box[3] / box[2])];
};
const fileOf = (name) => (FILES[name] || name.replace(/-tip$/, '')) + '.jpg';

/** A framed crop of a capture, as wide as possible within maxW x maxH. */
const shotArt = (name, maxW, maxH, style = '') => r.framed(r.region(fileOf(name), ...CROP[name], fitted(name, maxW, maxH)[0]), style);

// The card art area is 850 x 780. main() sits top-left, inset() bottom-right; each label is pinned to its frame.
const ART_W = 850;
const ART_H = 780;
// A label overlaps the top of a menu (its title bar), but sits wholly above chat lines, tooltips and short crops.
const labelAbove = (name, h) => (/-lines?$|-tip$|-bar$/.test(name) || h < 200 ? 50 : 22);
const main = (name, maxW, maxH, top, text, kind = '', left = 0) =>
  `${shotArt(name, maxW, maxH, `position:absolute;left:${left}px;top:${top}px`)}${text ? r.label(text, `left:${left + 24}px;top:${top - labelAbove(name, fitted(name, maxW, maxH)[1])}px`, kind) : ''}`;
const inset = (name, maxW, maxH, bottom, text, kind = 'gold', right = -20) => {
  const [w, h] = fitted(name, maxW, maxH);
  const left = ART_W - right - w;
  const top = ART_H - bottom - h;
  const labelTop = top - labelAbove(name, h);
  return `${shotArt(name, maxW, maxH, `position:absolute;left:${left}px;top:${top}px`)}${text ? r.label(text, `left:${left + 24}px;top:${labelTop}px`, kind) : ''}`;
};

/** Any crop at an exact place in the art area. */
const at = (name, maxW, maxH, left, top, text, kind = '') =>
  `${shotArt(name, maxW, maxH, `position:absolute;left:${left}px;top:${top}px`)}${text ? r.label(text, `left:${left + 24}px;top:${top - labelAbove(name, fitted(name, maxW, maxH)[1])}px`, kind) : ''}`;

// Card art is built only when that card renders, so one missing capture does not stop the others.
const card = (key, options) => () => ({ width: 1600, height: 900, file: `card-${key}.png`, html: r.card(options()) });
const badge = (name) => `<span class="pill" style="margin:0 10px 12px 0"><i></i>${name}</span>`;

const assets = {
  icon: () => ({ width: 512, height: 512, html: r.page(512, 512, `
    <div style="position:absolute;inset:0;background:radial-gradient(circle at 50% 42%,#173052 0%,#0f1a2b 58%,#080c13 100%)"></div>
    ${r.sprinkles(26, 0.55)}
    <div style="position:absolute;left:46px;top:46px;width:420px;height:420px;border-radius:50%;
      background:radial-gradient(circle,rgba(107,198,255,.32) 0%,rgba(107,198,255,0) 66%)"></div>
    <div style="position:absolute;left:64px;top:56px;filter:drop-shadow(0 18px 26px rgba(0,0,0,.6))">${r.donutSvg(384)}</div>
    <div style="position:absolute;right:30px;bottom:40px;display:flex;align-items:center;gap:10px;padding:12px 22px 11px 20px;border-radius:999px;
      background:linear-gradient(180deg,#ffd76a,#f0a92e);box-shadow:0 10px 26px rgba(0,0,0,.55),inset 0 -3px 0 rgba(120,70,0,.35)">
      <span style="font-family:DoDisplay;font-weight:900;font-size:44px;line-height:1;color:#3b2203;letter-spacing:.04em">ESS</span></div>`) }),

  divider: () => ({ width: 1400, height: 40, transparent: true, html: r.divider() }),

  banner: () => ({ width: 1800, height: 600, html: r.page(1800, 600, `
    ${r.backdrop(0.5, 6)}
    ${r.sprinkles(60, 0.4)}
    <div style="position:absolute;left:880px;top:-40px;width:1100px;height:700px;perspective:1700px">
      <div style="transform:rotateY(-17deg) rotateX(4deg) translateZ(0);transform-origin:left center;margin-top:70px;position:relative">
        ${shotArt('menu-hub-profile', 760, 470, 'display:inline-block')}
      </div>
    </div>
    <div style="position:absolute;inset:0;background:linear-gradient(90deg,rgba(8,12,18,.95) 0%,rgba(8,12,18,.86) 42%,rgba(8,12,18,.15) 64%,rgba(8,12,18,0) 100%)"></div>
    <div style="position:absolute;left:90px;top:0;bottom:0;width:860px;display:flex;flex-direction:column;justify-content:center;z-index:2">
      <div class="kicker"><span class="dot"></span>${r.RELEASE.version} · ${r.RELEASE.name}</div>
      <div style="display:flex;align-items:center;gap:26px;margin:22px 0 6px">
        <div style="width:118px;height:118px;filter:drop-shadow(0 12px 20px rgba(0,0,0,.6))">${r.donutSvg(118)}</div>
        <div class="display" style="font-size:100px;line-height:.92">Donut<br><span class="grad">Essentials</span></div>
      </div>
      <div class="rule" style="width:72%;margin:26px 0 24px"></div>
      <div style="font-size:30px;line-height:1.35;color:var(--text)">Homes, warps, kits, an economy, chat and moderation.<br>Every essential, with menus, on Folia too. <b class="hi">Free.</b></div>
      <div style="margin-top:30px;display:flex;align-items:center">
        <span class="pill"><i></i>Paper</span><span class="pill"><i></i>Folia</span><span class="pill"><i></i>Purpur</span>
        <span style="font-family:DoDisplay;font-weight:700;font-size:26px;color:var(--gold-hi);margin-left:12px">${r.RANGE}</span>
      </div>
    </div>`) }),

  hub: card('hub', () => ({
    kicker: 'The Player Hub', title: 'Everything in one menu',
    points: [
      '<code class="inline">/menu</code>: homes, warps, kits, spawn, rtp, money, mail',
      '<b class="hi">Personal settings</b> for requests, messages and pings',
      'Every title, slot, item and line in <code class="inline">menus.yml</code>',
      'Buttons for switched-off commands hide themselves',
    ],
    art: `<div style="position:relative;height:780px">
      ${main('menu-hub', 620, 400, 40, '/menu')}
      ${at('menu-settings-tip-tip', 360, 220, 0, 520, '/settings', 'sky')}
      ${inset('menu-hub-profile-tip', 440, 180, 250, 'Your profile')}
    </div>`,
  })),

  teleports: card('teleports', () => ({
    kicker: 'Teleports', title: 'Teleports done right',
    points: [
      '<code class="inline">/tpa</code> with <b class="hi">[Accept] [Deny]</b> buttons',
      'A title countdown; moving or damage cancels',
      '<b class="hi">Rank tiers</b> for warmups, cooldowns and costs',
      '<b class="hi">Combat tags</b> stop escapes mid-fight',
    ],
    art: `<div style="position:relative;height:780px">
      ${main('home-warmup-scene', 850, 480, 40, 'The countdown')}
      ${inset('tpa-lines', 760, 70, 150, 'A request', 'sky')}
      ${inset('combat-lines', 760, 70, 20, 'In combat', 'red')}
    </div>`,
  })),

  homes: card('homes', () => ({
    kicker: 'Homes', title: 'A home in every world',
    points: [
      '<code class="inline">/sethome</code>, <code class="inline">/home</code> and a <b class="hi">homes menu</b>',
      'Click to go; shift + right-click to delete',
      'Limits by rank, tier or permission',
      'Icons show the Overworld, Nether or End',
    ],
    art: `<div style="position:relative;height:780px">
      ${main('menu-homes', 640, 420, 40, '/homes')}
      ${inset('menu-homes-nether-tip', 460, 240, 60, 'In the Nether')}
    </div>`,
  })),

  warps: card('warps', () => ({
    kicker: 'Warps & Spawn', title: 'Places worth going',
    points: [
      '<code class="inline">/setwarp</code>: <b class="hi">the held item is the icon</b>',
      'Visit counts and optional per-warp permissions',
      '<code class="inline">/spawn</code>, a first-join spawn, respawn at spawn',
      '<code class="inline">/rtp</code>: a safe random spot, chunks loaded async',
    ],
    art: `<div style="position:relative;height:780px">
      ${main('menu-warps', 640, 420, 40, '/warps')}
      ${inset('menu-warps-tip-tip', 460, 260, 50, 'Visits counted')}
    </div>`,
  })),

  kits: card('kits', () => ({
    kicker: 'Kits', title: 'Kits that keep everything',
    points: [
      '<code class="inline">/createkit</code> from your own inventory',
      '<b class="hi">Every enchantment, name, lore and attribute kept</b>',
      'Ready, cooldown, claimed or locked at a glance',
      'Right-click to <b class="hi">preview</b>; once-only kits; costs',
    ],
    art: `<div style="position:relative;height:780px">
      ${main('menu-kits-tip', 760, 420, 40, '/kits')}
      ${at('menu-kits-locked-tip', 330, 260, 0, 520, 'Locked', 'red')}
      ${inset('menu-kit-preview-tip-tip', 380, 380, 20, 'Every detail kept')}
    </div>`,
  })),

  economy: card('economy', () => ({
    kicker: 'Economy', title: 'Money, built in',
    points: [
      'A <b class="hi">Vault economy</b> for your shops and jobs',
      '<code class="inline">/pay Kiri 2.5k</code>, <code class="inline">/balance</code>, <code class="inline">/eco</code>',
      'The <b class="hi">richest players</b> menu',
      '<b class="hi">Offline payments</b> listed on join; a daily log',
    ],
    art: `<div style="position:relative;height:780px">
      ${main('menu-baltop', 560, 440, 40, '/baltop')}
      ${inset('menu-baltop-you-tip', 400, 180, 190, 'You')}
      ${inset('pay-lines', 760, 70, 20, 'Payments', 'gold')}
    </div>`,
  })),

  chat: card('chat', () => ({
    kicker: 'Chat & Messages', title: 'Chat people enjoy',
    points: [
      'Formats with <b class="hi">LuckPerms prefixes</b> and PlaceholderAPI',
      '<b class="hi">Mentions</b> that ping; <b class="hi">[item]</b> with its tooltip',
      '<code class="inline">/msg</code>, <code class="inline">/r</code>, <code class="inline">/ignore</code> and <b class="hi">offline mail</b>',
      'Anti-spam, a word filter, AFK in the tab list',
    ],
    art: `<div style="position:relative;height:780px">
      ${main('chat-item-scene', 850, 300, 40, 'Prefixes, mentions, [item]')}
      ${at('afk-tab-scene', 260, 220, 0, 340, 'AFK', 'gold')}
      ${inset('messages-lines', 520, 110, 120, 'Messages and mail', 'sky')}
    </div>`,
  })),

  moderation: card('moderation', () => ({
    kicker: 'Moderation', title: 'Moderation built in',
    points: [
      'Bans, mutes, warnings and notes with <b class="hi">history</b>',
      '<b class="hi">Warning escalation</b>, alts and ban-evasion alerts',
      '<b class="hi">Reports</b> with buttons and a staff menu',
      'Freeze, staff chat, <b class="hi">Discord logs</b>',
    ],
    art: `<div style="position:relative;height:780px">
      ${main('moderation-lines', 850, 200, 40, 'Warn, mute, report')}
      ${at('history-lines', 760, 90, 0, 290, '/history Rook', 'sky')}
      ${inset('menu-reports-tip-tip', 420, 260, 0, '/reports', 'red')}
    </div>`,
  })),

  staff: card('staff', () => ({
    kicker: 'Staff Tools', title: 'Tools for your team',
    points: [
      '<b class="hi">Vanish</b> that hides you everywhere',
      '<code class="inline">/invsee</code>, <code class="inline">/endersee</code>, <code class="inline">/whois</code>, <code class="inline">/seen</code>',
      '<b class="hi">Maintenance mode</b> with timers',
      '<b class="hi">IP lock</b> for staff accounts',
    ],
    art: `<div style="position:relative;height:780px">
      ${main('invsee', 560, 400, 40, '/invsee Kiri')}
      ${inset('whois-lines', 560, 400, 0, '/whois Kiri', 'sky')}
    </div>`,
  })),

  rules: card('rules', () => ({
    kicker: 'Rules & Server Links', title: 'A first join done right',
    points: [
      'New players <b class="hi">accept the rules</b> before they play',
      'A <b class="hi">native dialog</b> on 1.21.7+, buttons before',
      '<b class="hi">Server links</b> in the pause menu',
      'A message of the day and first-join messages',
    ],
    art: `<div style="position:relative;height:780px">
      ${main('rules-dialog-scene', 640, 480, 40, 'The rules screen')}
      ${inset('server-links-scene', 460, 200, 0, 'Server links', 'sky')}
    </div>`,
  })),

  yours: card('yours', () => ({
    kicker: 'Make It Yours', title: 'Your server, your rules',
    points: [
      '<b class="hi">Every module can be switched off</b>',
      'Rename or switch off any command in <code class="inline">commands.yml</code>',
      'Vanilla commands are <b class="hi">never taken over</b>',
      'Cooldowns and costs for <b class="hi">any</b> command',
    ],
    art: (() => {
      const modules = r.yamlSection('src/main/resources/config.yml', 'modules').filter((line) => !line.trim().startsWith('#')).slice(0, 9);
      const commands = ['home: {enabled: true, aliases: [h], description: "Teleport to a home"}',
        'rtp: {enabled: false}', 'balance: {enabled: true, aliases: [bal, money, coins]}'];
      const rules = r.yamlSection('src/main/resources/config.yml', 'command-rules').filter((line) => !line.trim().startsWith('#'));
      return `<div style="position:relative;height:780px;display:flex;flex-direction:column;justify-content:center;gap:20px">
        ${r.codePanel('config.yml', r.yaml(modules) + '\n<span style="color:var(--faint)">  ...</span>', { width: 560, size: 18 })}
        ${r.codePanel('commands.yml', r.yaml(commands), { width: 850, size: 17 })}
        ${r.codePanel('config.yml', r.yaml(rules), { width: 560, size: 17 })}
      </div>`;
    })(),
  })),

  switch: card('switch', () => ({
    kicker: 'Switching from EssentialsX', title: 'Switch in one command',
    points: [
      'Balances, homes, nicknames, ignores',
      'Warps, the spawn, kits and bans',
      'A <b class="hi">dry run</b> first; nothing overwritten',
      'Then remove EssentialsX',
    ],
    art: `<div style="position:relative;height:780px;display:flex;flex-direction:column;justify-content:center">
      ${r.codePanel('Console · EssentialsX data, then Donut Essentials', r.consoleText('import.txt').map((line) => (line.startsWith('>')
        ? `<span style="color:var(--gold-hi)">${r.escapeHtml(line)}</span>` : r.escapeHtml(line))).join('\n'), { width: 850, size: 17, wrap: true })}
    </div>`,
  })),

  upgrade: card('upgrade', () => ({
    kicker: 'Upgrading from 2.x', title: 'Upgrade in place',
    points: [
      'Punishments, IP locks and the whitelist kept',
      'Your config values carried over',
      'Old files kept as <code class="inline">config-2.x.yml</code>',
      '2.x commands and permissions still work',
    ],
    art: `<div style="position:relative;height:780px;display:flex;flex-direction:column;justify-content:center">
      ${r.codePanel('Console · first start of 3.0 on a 2.0.0 server', r.consoleText('upgrade.txt').map(r.escapeHtml).join('\n'), { width: 850, size: 16, wrap: true })}
    </div>`,
  })),

  integrations: card('integrations', () => ({
    kicker: 'Integrations', title: 'Plays well with others',
    points: [
      'A <b class="hi">Vault</b> economy; prefixes from <b class="hi">LuckPerms</b>',
      '<b class="hi">PlaceholderAPI</b>: 30+ placeholders',
      '<b class="hi">MySQL</b> and MariaDB, <b class="hi">Discord</b> webhooks',
      'An API with nine events',
    ],
    art: (() => {
      const papi = ['%donutessentials_balance_formatted%', '%donutessentials_baltop_1_name%', '%donutessentials_homes%/%donutessentials_homes_max%',
        '%donutessentials_afk_tag%', '%donutessentials_combat_time%', '%donutessentials_playtime%']
        .map((p) => `<span style="color:var(--gold-hi)">${p}</span>`).join('\n');
      const api = `<span style="color:var(--accent-hi)">@EventHandler</span>
<span style="color:var(--accent-hi)">public void</span> onKit(KitClaimEvent event) {
    <span style="color:var(--accent-hi)">if</span> (event.getKit().equals(<span style="color:var(--gold-hi)">"warrior"</span>) &amp;&amp; inArena(event.getPlayer()))
        event.setCancelled(<span style="color:var(--accent-hi)">true</span>);
}`;
      return `<div style="position:relative;height:780px;display:flex;flex-direction:column;justify-content:center;gap:24px">
        <div style="display:flex;flex-wrap:wrap;width:820px">${['Vault', 'VaultUnlocked', 'LuckPerms', 'PlaceholderAPI', 'MySQL', 'MariaDB', 'Discord', 'Donut RTP'].map(badge).join('')}</div>
        ${r.codePanel('PlaceholderAPI', papi, { width: 640, size: 19 })}
        ${r.codePanel('Developer API', api, { width: 850, size: 17 })}
      </div>`;
    })(),
  })),

  compatibility: card('compatibility', () => ({
    kicker: 'Compatibility', title: 'Tested from 1.20 to 26.3',
    points: [
      'Paper, Folia and Purpur, one jar',
      `Minecraft ${r.RANGE}`,
      'Bots play every feature on each server shown',
      'SQLite and MySQL; upgrades from 2.0.0 and EssentialsX',
    ],
    art: (() => {
      const data = r.compat();
      const targets = data ? data.targets : [];
      const extra = (t) => (t.upgrade ? ' · from 2.0.0' : t.essentials ? ' · EssentialsX' : t.storage === 'MySQL' ? ' · MySQL' : '');
      const tile = (t) => `<div class="panel" style="padding:12px 16px 10px;border-color:${t.ok ? 'rgba(126,224,129,.5)' : 'rgba(255,107,107,.6)'}">
        <div style="font-family:DoDisplay;font-weight:700;font-size:13px;letter-spacing:.16em;text-transform:uppercase;color:var(--dim)">${t.project}${t.variant ? ' · ' + (t.variant === 'essentialsx' ? 'EssX' : t.variant) : ''}</div>
        <div style="font-family:DoDisplay;font-weight:800;font-size:28px;color:var(--hi);line-height:1.1">${t.version}</div>
        <div style="font-size:14px;color:${t.ok ? 'var(--mint)' : 'var(--red)'};font-weight:600;margin-top:3px;white-space:nowrap">${t.ok ? '✔' : '✖'} ${t.passed}/${t.steps} · Java ${t.java}${extra(t)}</div></div>`;
      return `<div style="display:grid;grid-template-columns:repeat(4,1fr);gap:12px;width:850px">${targets.map(tile).join('')}</div>
        <div style="margin-top:16px;font-size:18px;color:var(--dim)">Donut Essentials ${data ? data.plugin : ''} · automated run of tools/smoke on ${data ? data.date : ''}</div>`;
    })(),
  })),
};

for (const [key, title] of Object.entries(HEADERS)) {
  assets[`header-${key}`] = () => ({ width: 1400, height: 120, transparent: true, html: r.header(title) });
}
module.exports = assets;
