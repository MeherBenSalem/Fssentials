// The Donut Essentials smoke scenario, run once per server in the matrix. See README.md for what each step proves.
import { plain } from './run.mjs';

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/** Waits for a chat line on the bot after the last one matched. */
async function chat(bot, pattern, timeout = 15_000) {
  const deadline = Date.now() + timeout;
  bot.seenChat = bot.seenChat || 0;
  while (Date.now() < deadline) {
    for (let i = bot.seenChat; i < bot.chatLog.length; i++) {
      const match = bot.chatLog[i].match(pattern);
      if (match) {
        bot.seenChat = i + 1;
        return { match, raw: bot.rawLog[i] || '', line: bot.chatLog[i] };
      }
    }
    await sleep(100);
  }
  throw new Error(`${bot.username} never saw ${pattern} (last: ${JSON.stringify(bot.chatLog.slice(-3))})`);
}

/** Skips everything the bot has seen so far. */
const forget = (...bots) => {
  for (const bot of bots) bot.seenChat = bot.chatLog.length;
};

/** True when the bot sees nothing matching within the time. */
async function quiet(bot, pattern, ms = 2500) {
  const from = bot.chatLog.length;
  await sleep(ms);
  return !bot.chatLog.slice(from).some((line) => pattern.test(line));
}

/** Runs an action and resolves with the window it opens. */
async function opens(bot, action, timeout = 15_000) {
  const opened = new Promise((resolve) => bot.once('windowOpen', resolve));
  await action();
  return Promise.race([opened, sleep(timeout).then(() => { throw new Error(`${bot.username}: no window opened`); })]);
}

const title = (window) => plain(window.title);
const itemName = (item) => (item ? plain(item.customName ?? item.displayName ?? '') : '');

/** `say` with a gap, so anti-spam never refuses two lines from the same bot. */
async function say(bot, line, gap = 1100) {
  bot.chat(line);
  await sleep(gap);
}

export async function scenario({ server: current, target, restart, joinBot, step }) {
  let server = current();
  const check = async (label, body) => {
    try {
      const detail = await body();
      step(label, true, typeof detail === 'string' ? detail : '');
    } catch (error) {
      step(label, false, error.message);
    }
  };
  const lab = (line) => server.lab(line);
  const api = async (line) => (await lab(`api ${line}`)).replace(/^api /, '');
  const papi = async (who, text) => (await lab(`papi ${who} ${text}`)).replace(/^papi \S+ /, '');
  const pos = async (who) => {
    const m = (await lab(`pos ${who}`)).match(/^pos \S+ (\S+) (-?[\d.]+) (-?[\d.]+) (-?[\d.]+)/);
    return { world: m[1], x: Number(m[2]), y: Number(m[3]), z: Number(m[4]) };
  };
  const distance = (a, b) => (a.world === b.world ? Math.hypot(a.x - b.x, a.z - b.z) : Infinity);
  const state = async (who) => {
    const line = await lab(`state ${who}`);
    const out = {};
    for (const [, key, value] of line.matchAll(/(\w+)=(\S*)/g)) out[key] = value;
    return out;
  };
  const consoleSays = async (command, pattern, label = command) => {
    server.mark();
    server.command(command);
    return server.waitFor(pattern, 20_000, label);
  };
  const refused = async (name, pattern) => {
    try {
      const bot = await joinBot(name);
      bot.quit();
      throw new Error(`${name} could join`);
    } catch (error) {
      if (!pattern.test(error.message)) throw new Error(`${name}: ${error.message.slice(0, 200)}`);
      return error.message.match(pattern)[0];
    }
  };

  // ── 1. Start-up ───────────────────────────────────────────────────────────────────────────────────────────────
  await check('plugin enables', async () => {
    const line = server.lines.find((l) => /Donut Essentials 3\.\d+\.\d+ enabled on/.test(l));
    if (!line) throw new Error('no enable line');
    const platform = target.project === 'folia' ? /enabled on Folia/ : /enabled on (Paper|Purpur)/;
    if (!platform.test(line)) throw new Error(line);
    if (!/16 modules/.test(line)) throw new Error(`modules: ${line}`);
    const storage = target.variant === 'mysql' ? /storage MySQL/ : /storage SQLite/;
    if (!storage.test(line)) throw new Error(`storage: ${line}`);
    if (!/economy Donut Essentials/.test(line)) throw new Error(`economy: ${line}`);
    if (!/PlaceholderAPI true/.test(line)) throw new Error(`PlaceholderAPI: ${line}`);
    return line.replace(/.*enabled on /, '');
  });

  if (target.upgrade) {
    await check('2.0.0 data upgraded', async () => {
      if (!server.seen(/Upgraded config\.yml from 2\.x/)) throw new Error('no config upgrade line');
      for (const file of ['config-2.x.yml', 'messages-2.x.yml', 'punishments.yml.imported', 'ip-locks.yml.imported']) {
        if (!/exists=true/.test(await lab(`file ${file}`))) throw new Error(`no ${file}`);
      }
      const draxen = await api('punish Draxen');
      if (!/muted=true/.test(draxen) || !/MUTE/.test(draxen) || !/NOTE/.test(draxen) || !/WARN/.test(draxen)) throw new Error(draxen);
      const hank = await api('punish Hank');
      if (!/banned=true/.test(hank)) throw new Error(hank);
      await consoleSays('iplock check Alice', /Alice may join from 127\.0\.0\.1/);
      await consoleSays('maintenance whitelist', /Maintenance whitelist.*willow/i);
      return 'punishments, IP lock and maintenance whitelist kept';
    });
    await check('a 2.0.0 ban still keeps the player out', async () => refused('Hank', /You are banned/));
  }

  if (target.variant === 'essentialsx') {
    await check('/dess import essentials (dry run, then confirm)', async () => {
      await consoleSays('donutessentials import essentials', /Players 2 .*balances 2/);
      await consoleSays('donutessentials import essentials confirm',
        /Imported from EssentialsX: 2 players, 2 balances, 3 homes, 1 nicknames, 0 ignores, 1 warps, the spawn, 5 kits, 1 bans/);
      const homes = await api('homes Willow');
      if (!/ 2 base@world,farm@world/.test(homes)) throw new Error(homes);
      if (!/ 1 tower@world/.test(await api('homes Rook'))) throw new Error('Rook homes');
      if (!/market@world/.test(await api('warps'))) throw new Error('no market warp');
      if (!/spawn world/.test(await api('spawn'))) throw new Error('no spawn');
      const willow = await api('balance Willow');
      if (!/ 4321\.00 /.test(willow)) throw new Error(willow);
      if (!/ 75\.00 /.test(await api('balance Rook'))) throw new Error('Rook balance');
      if (!/banned=true/.test(await api('punish Draxen'))) throw new Error('Draxen not banned');
      if (!/nick=Willo /.test(await api('player Willow'))) throw new Error(await api('player Willow'));
      // EssentialsX's default kits use its own item names (stonesword, dpickaxe, lhelm).
      const kits = await api('kits');
      if (!/\btools\b/.test(kits) || !/\bdtools\b/.test(kits)) throw new Error(kits);
      return 'homes, warp, spawn, balances, nickname, ban and the 5 default kits';
    });
  }

  // ── 2. Joining ────────────────────────────────────────────────────────────────────────────────────────────────
  const alice = await joinBot('Alice');
  const bob = await joinBot('Bob');
  const cara = await joinBot('Cara');
  server.command('op Alice');
  await sleep(1000);
  await check('first join: welcome, player number, starter kit, account', async () => {
    await chat(bob, /Welcome Bob to the server\. \(player #\d+\)/);
    await chat(bob, /starter kit/);
    const sword = await lab('count Bob STONE_SWORD');
    if (!/ 1$/.test(sword)) throw new Error(sword);
    const balance = await api('balance Bob');
    if (!/ 100\.00 own=true/.test(balance)) throw new Error(balance);
    return 'starter kit and $100';
  });

  // ── 3. Homes ──────────────────────────────────────────────────────────────────────────────────────────────────
  const spawnPos = await pos('Bob');
  await check('homes: set, list, teleport after the warmup, delete', async () => {
    server.mark();
    await say(bob, '/sethome base');
    await chat(bob, /Home base set\. \(1\/3\)/);
    await server.waitFor(/\[ESSLAB\] event sethome Bob base/, 10_000, 'sethome event');
    await say(bob, '/sethome mine');
    await chat(bob, /Home mine set\. \(2\/3\)/);
    await lab(`tp Bob ${spawnPos.world} ${spawnPos.x + 60} 120 ${spawnPos.z + 60}`);
    await sleep(1500);
    await say(bob, '/home base', 200);
    await chat(bob, /Teleporting in 2s/);
    await chat(bob, /Welcome home \(base\)/, 10_000);
    const at = await pos('Bob');
    if (distance(at, spawnPos) > 2) throw new Error(`landed ${JSON.stringify(at)}`);
    await say(bob, '/delhome mine');
    await chat(bob, /Home mine deleted/);
    const homes = await api('homes Bob');
    if (!/ 1 base@/.test(homes)) throw new Error(homes);
    const menu = await opens(bob, () => bob.chat('/homes'));
    if (!/Homes · Bob \(1\/3\)/.test(title(menu))) throw new Error(`title "${title(menu)}"`);
    if (!/base/.test(itemName(menu.slots[10]))) throw new Error(`slot 10 ${itemName(menu.slots[10])}`);
    bob.closeWindow(menu);
    return 'base kept, mine deleted, menu lists homes';
  });

  // ── 4. Warps and spawn ────────────────────────────────────────────────────────────────────────────────────────
  await check('warps and spawn', async () => {
    await lab(`tp Alice ${spawnPos.world} ${spawnPos.x + 20} ${spawnPos.y} ${spawnPos.z + 5}`);
    await sleep(1500);
    await lab('hold Alice EMERALD');
    await say(alice, '/setwarp shop');
    await chat(alice, /Warp shop set/);
    await say(alice, '/setspawn');
    await chat(alice, /Spawn set at/);
    server.mark();
    await say(bob, '/warp shop', 200);
    await chat(bob, /Warped to shop/, 10_000);
    await server.waitFor(/\[ESSLAB\] event teleport Bob warp/, 5000, 'warp event');
    const at = await pos('Bob');
    if (distance(at, { ...spawnPos, x: spawnPos.x + 20, z: spawnPos.z + 5 }) > 2) throw new Error(`at ${JSON.stringify(at)}`);
    const menu = await opens(bob, () => bob.chat('/warps'));
    if (!/Warps/.test(title(menu))) throw new Error(`title "${title(menu)}"`);
    if (!menu.slots.some((s) => s && s.name === 'emerald')) throw new Error('no emerald icon');
    bob.closeWindow(menu);
    await lab(`tp Bob ${spawnPos.world} ${spawnPos.x - 40} 120 ${spawnPos.z}`);
    await sleep(1200);
    await say(bob, '/spawn', 200);
    await chat(bob, /Welcome to spawn/, 10_000);
    return 'warp with an emerald icon, /spawn';
  });

  // ── 5. Teleport requests and /back ────────────────────────────────────────────────────────────────────────────
  await check('teleport request with clickable buttons', async () => {
    // In the air: at spawn height this spot can be inside a hill (Bob would suffocate and the warmup cancel).
    await lab(`tp Bob ${spawnPos.world} ${spawnPos.x - 30} 120 ${spawnPos.z - 30}`);
    await sleep(1500);
    forget(alice, bob);
    server.mark();
    await say(bob, '/tpa Alice');
    await chat(alice, /Bob wants to teleport to you/);
    const buttons = await chat(alice, /\[Accept\] .*\[Deny\]/);
    if (!buttons.raw.includes('/tpaccept Bob')) throw new Error('no clickable accept');
    await server.waitFor(/\[ESSLAB\] event tpa Bob Alice here=false/, 5000, 'tpa event');
    await say(alice, '/tpaccept');
    await chat(bob, /Alice accepted your request/);
    await chat(bob, /Teleported to Alice/, 10_000);
    const [a, b] = [await pos('Alice'), await pos('Bob')];
    if (distance(a, b) > 2) throw new Error(`apart: ${JSON.stringify(a)} ${JSON.stringify(b)}`);
    return 'accept button runs /tpaccept Bob';
  });
  await check('/back returns to the last place', async () => {
    await say(bob, '/back', 200);
    await chat(bob, /Back where you were/, 10_000);
    const at = await pos('Bob');
    if (distance(at, { ...spawnPos, x: spawnPos.x - 30, z: spawnPos.z - 30 }) > 2) throw new Error(`at ${JSON.stringify(at)}`);
  });
  await check('/tpahere denied, /tptoggle refuses requests', async () => {
    await say(alice, '/tpahere Bob');
    await chat(bob, /Alice wants you to teleport to them/);
    await say(bob, '/tpdeny');
    await chat(alice, /Bob denied your request/);
    await say(bob, '/tptoggle');
    await chat(bob, /Teleport requests are now off/);
    await say(cara, '/tpa Bob');
    await chat(cara, /Bob isn't taking teleport requests/);
    await say(bob, '/tptoggle');
    await chat(bob, /Teleport requests are now on/);
  });
  await check('warmup cancelled by damage', async () => {
    await say(bob, '/home base', 300);
    await chat(bob, /Teleporting in 2s/);
    await lab('damage Bob 1');
    await chat(bob, /Teleport cancelled: you took damage/);
  });

  // ── 6. Combat ─────────────────────────────────────────────────────────────────────────────────────────────────
  await check('combat tag blocks teleports, then ends', async () => {
    await sleep(500);
    await lab('hit Alice Bob 1');
    await chat(bob, /You're in combat for 4s/);
    await say(bob, '/spawn');
    await chat(bob, /You can't use that in combat|You're in combat\. Wait/);
    await chat(bob, /You're no longer in combat/, 10_000);
    await lab('hurt Bob 20 20');
    return 'tagged, /spawn blocked, untagged after 4s';
  });

  // ── 7. Economy ────────────────────────────────────────────────────────────────────────────────────────────────
  await check('pay, balance, eco and baltop', async () => {
    server.mark();
    await say(bob, '/pay Alice 25');
    await chat(bob, /You paid Alice \$25\.00/);
    await chat(alice, /You received \$25\.00 from Bob/);
    await server.waitFor(/\[ESSLAB\] event pay Bob Alice 25\.00/, 5000, 'pay event');
    if (!/ 125\.00 /.test(await api('balance Alice'))) throw new Error(await api('balance Alice'));
    await say(bob, '/pay Alice 2.5k');
    await chat(bob, /You don't have \$2,500\.00/);
    await consoleSays('eco give Bob 1000', /Gave \$1,000\.00 to Bob/);
    await say(bob, '/balance');
    await chat(bob, /Balance: \$1,075\.00/);
    const menu = await opens(bob, () => bob.chat('/baltop'));
    if (!/Richest/i.test(title(menu))) throw new Error(`title "${title(menu)}"`);
    bob.closeWindow(menu);
    return '$25 paid, 2.5k refused, $1,075.00 after /eco give';
  });

  // ── 8. Kits ───────────────────────────────────────────────────────────────────────────────────────────────────
  await check('kits keep every item detail; cooldowns and once-only', async () => {
    await lab('clear Alice');
    await lab('hold Alice DIAMOND_SWORD Moon Blade');
    await sleep(500);
    await say(alice, '/createkit pvp 1h');
    await chat(alice, /Kit pvp created with 1 items/);
    await lab('clear Bob');
    server.mark();
    await say(bob, '/kit pvp');
    await chat(bob, /You got the pvp kit/);
    await server.waitFor(/\[ESSLAB\] event kit Bob pvp bypass=false/, 5000, 'kit event');
    const items = await lab('items Bob');
    if (!/DIAMOND_SWORDx1\[Moon Blade\]/.test(items)) throw new Error(items);
    await say(bob, '/kit pvp');
    await chat(bob, /You can claim pvp again in (59m|1h)/);
    await say(bob, '/kit starter');
    await chat(bob, /You already claimed the starter kit/);
    const menu = await opens(bob, () => bob.chat('/kits'));
    if (!/Kits/.test(title(menu))) throw new Error(`title "${title(menu)}"`);
    bob.closeWindow(menu);
    return 'the named sword came back named';
  });

  // ── 9. Chat ───────────────────────────────────────────────────────────────────────────────────────────────────
  await check('chat format, mentions and [item]', async () => {
    forget(alice);
    server.mark();
    await say(bob, 'hello there Alice');
    await chat(alice, /hello there .*Alice/);
    const line = await server.waitFor(/\[ESSLAB\] chat (.*Bob » hello there @?Alice) item=false/, 10_000, 'formatted chat');
    await lab('hold Bob DIAMOND');
    await sleep(400);
    server.mark();
    await say(bob, 'look at my [item]');
    await server.waitFor(/\[ESSLAB\] chat .*Bob » look at my .*Diamond.* item=true/, 10_000, 'item tooltip');
    return `"${line[1]}", item tooltip`;
  });
  await check('ignore hides a player', async () => {
    await say(alice, '/ignore Bob');
    await chat(alice, /You're ignoring Bob now/);
    await say(bob, 'can you hear me');
    if (!(await quiet(alice, /can you hear me/))) throw new Error('Alice saw an ignored player');
    await say(alice, '/ignore Bob');
    await chat(alice, /You stopped ignoring Bob/);
  });

  // ── 10. Private messages and mail ─────────────────────────────────────────────────────────────────────────────
  await lab('perm Cara donutessentials.command.socialspy true');
  await check('msg, reply and social spy', async () => {
    await say(cara, '/socialspy');
    await chat(cara, /Social spy is on/);
    await say(bob, '/msg Alice meet at spawn');
    await chat(alice, /\[Bob → me\] meet at spawn/);
    await chat(cara, /\[Spy\] Bob → Alice: meet at spawn/);
    await say(alice, '/r on my way');
    await chat(bob, /\[Alice → me\] on my way/);
  });
  const dan = await joinBot('Dan');
  await sleep(1000);
  dan.quit();
  await sleep(1500);
  await check('mail and payments for an offline player', async () => {
    await say(bob, '/mail send Dan check the shop');
    await chat(bob, /Mail sent to Dan/);
    await say(bob, '/pay Dan 10');
    await chat(bob, /You paid Dan \$10\.00/);
    const back = await joinBot('Dan');
    await chat(back, /While you were away/, 20_000);
    await chat(back, /\$10\.00 from Bob/);
    await chat(back, /You have 1 unread mail/, 20_000);
    await say(back, '/mail read');
    await chat(back, /Bob: check the shop/);
    return 'payment notice and mail on join';
  });

  // ── 11. AFK and nicknames ─────────────────────────────────────────────────────────────────────────────────────
  await check('AFK: broadcast, tab tag, sleep ignored, back on chat', async () => {
    server.mark();
    await say(bob, '/afk');
    await chat(alice, /Bob is now AFK/);
    await server.waitFor(/\[ESSLAB\] event afk Bob true auto=false/, 5000, 'afk event');
    const s = await state('Bob');
    if (s.sleepIgnored !== 'true') throw new Error(`sleepIgnored ${s.sleepIgnored}`);
    if (!/\[AFK\]/.test(s.list)) throw new Error(`tab ${s.list}`);
    await sleep(2500);
    await say(bob, 'im back now');
    await chat(alice, /Bob is back/);
    return `tab "${s.list}"`;
  });
  await check('nickname and /realname', async () => {
    await lab('perm Bob donutessentials.command.nick true');
    await sleep(400);
    await say(bob, '/nick Bobby');
    await chat(bob, /Your nickname is now Bobby/);
    if (!/nick=Bobby /.test(await api('player Bob'))) throw new Error(await api('player Bob'));
    await say(alice, '/realname Bobby');
    await chat(alice, /Bobby is Bob/);
    await say(bob, '/nick off');
    await chat(bob, /Your nickname was removed/);
  });

  // ── 12. Moderation ────────────────────────────────────────────────────────────────────────────────────────────
  await check('warn with a title, and history', async () => {
    server.mark();
    await say(alice, '/warn Bob Spamming the market');
    await chat(bob, /You were warned by Alice: Spamming the market/);
    await server.waitFor(/\[ESSLAB\] event punish WARN Bob 0/, 5000, 'punish event');
    if (!/active=WARN/.test(await api('punish Bob'))) throw new Error(await api('punish Bob'));
    await say(alice, '/history Bob');
    await chat(alice, /History of Bob/);
  });
  await check('mute blocks chat and private messages', async () => {
    await say(alice, '/tempmute Cara 10m Shouting');
    await chat(cara, /You were muted by Alice: Shouting/);
    await say(cara, 'can anyone hear');
    await chat(cara, /You are muted: Shouting/);
    await say(cara, '/msg Bob psst');
    await chat(cara, /You are muted/);
    await say(alice, '/unmute Cara');
    await chat(cara, /You can chat again/);
  });
  await check('ban kicks and keeps out; alts alert; unban', async () => {
    await say(alice, '/ban Dan Griefing the spawn');
    await sleep(1500);
    await refused('Dan', /You are banned/);
    const eve = await joinBot('Eve');
    await chat(alice, /Possible ban evasion: Eve shares an address with banned Dan/);
    eve.quit();
    await say(alice, '/alts Bob');
    await chat(alice, /Alts of Bob/);
    await say(alice, '/unban Dan');
    await chat(alice, /Lifted Ban/);
    const back = await joinBot('Dan');
    back.quit();
    return 'banned, alt alert, unbanned';
  });
  await check('freeze', async () => {
    await say(alice, '/freeze Bob');
    await chat(bob, /You were frozen by Alice/);
    await say(bob, '/spawn');
    await chat(bob, /You can't do that while frozen/);
    await say(alice, '/freeze Bob');
    await chat(bob, /You were unfrozen/);
  });
  await check('reports with clickable staff buttons, and the reports menu', async () => {
    await say(bob, '/report Cara using x-ray');
    await chat(bob, /Thanks\. Staff will look at your report on Cara/);
    const notice = await chat(alice, /\[Report #\d+\] Bob reported Cara: using x-ray/);
    const buttons = await chat(alice, /\[Teleport\]|\[Claim\]/).catch(() => notice);
    if (!/\/reports (tp|claim)/.test(notice.raw + buttons.raw)) throw new Error('no clickable report buttons');
    const menu = await opens(alice, () => alice.chat('/reports'));
    if (!/Reports/i.test(title(menu))) throw new Error(`title "${title(menu)}"`);
    alice.closeWindow(menu);
  });
  await check('staff chat reaches staff only', async () => {
    await lab('perm Cara donutessentials.command.staffchat true');
    await sleep(500);
    await say(alice, '/sc check the report');
    await chat(cara, /Staff Alice: check the report/);
    if (!(await quiet(bob, /check the report/, 1500))) throw new Error('Bob saw staff chat');
  });

  // ── 13. Vanish and staff tools ────────────────────────────────────────────────────────────────────────────────
  await check('vanish hides a player', async () => {
    const before = Number(await papi('Bob', '%donutessentials_online%'));
    await say(alice, '/vanish');
    await chat(alice, /You vanished/);
    await sleep(800);
    if (!/ false$/.test(await lab('sees Bob Alice'))) throw new Error('Bob still sees Alice');
    const after = Number(await papi('Bob', '%donutessentials_online%'));
    if (after !== before - 1) throw new Error(`online ${before} → ${after}`);
    await say(bob, '/msg Alice are you there');
    await chat(bob, /No player named Alice is online/);
    await say(alice, '/vanish');
    await chat(alice, /You're visible again/);
    await sleep(800);
    if (!/ true$/.test(await lab('sees Bob Alice'))) throw new Error('Bob cannot see Alice again');
    return `online ${before} → ${after} → back`;
  });
  await check('whois, invsee and seen', async () => {
    await say(alice, '/whois Bob');
    await chat(alice, /Bob ● online/);
    const inv = await opens(alice, () => alice.chat('/invsee Bob'));
    if (!/Bob/.test(title(inv)) && inv.type !== 'minecraft:generic_9x4') throw new Error(`title "${title(inv)}"`);
    alice.closeWindow(inv);
    await say(alice, '/seen Dan');
    await chat(alice, /Dan was last seen/);
  });

  // ── 14. Utilities ─────────────────────────────────────────────────────────────────────────────────────────────
  await check('fly, god, heal, feed, speed and game modes', async () => {
    await say(alice, '/fly');
    await chat(alice, /Flight on/);
    if ((await state('Alice')).allowFlight !== 'true') throw new Error('no flight');
    await say(alice, '/god');
    await chat(alice, /God mode on/);
    const hit = await lab('damage Alice 5');
    if (!/before=20\.0 after=20\.0/.test(hit)) throw new Error(hit);
    await say(alice, '/god');
    await lab('hurt Alice 6 4');
    await say(alice, '/heal');
    await chat(alice, /Healed/);
    await say(alice, '/feed');
    await chat(alice, /Fed/);
    const fed = await state('Alice');
    if (fed.health !== '20.0' || fed.food !== '20') throw new Error(`health ${fed.health} food ${fed.food}`);
    await say(alice, '/speed 5');
    await chat(alice, /speed is now 5/);
    await say(alice, '/gmc');
    await chat(alice, /Game mode: Creative/);
    await say(alice, '/gms');
    await chat(alice, /Game mode: Survival/);
    await say(alice, '/fly');
  });
  await check('hat, repair, top and the time commands', async () => {
    await lab('hold Alice GOLDEN_HELMET');
    await sleep(300);
    await say(alice, '/hat');
    await chat(alice, /Nice hat/);
    await lab('hold Alice IRON_PICKAXE');
    await lab('damageitem Alice 100');
    await say(alice, '/repair');
    await chat(alice, /Repaired the item in your hand/);
    const held = await lab('held Alice');
    if (!/damage=0 helmet=GOLDEN_HELMET/.test(held)) throw new Error(held);
    await say(alice, '/day');
    await chat(alice, /It's day in/);
    await say(alice, '/top');
    await chat(alice, /Up you go|already at the top/);
  });
  await check('menus: hub, settings, craft, ender chest, trash', async () => {
    const hub = await opens(bob, () => bob.chat('/menu'));
    if (!hub.slots.some((s) => s && /Homes/i.test(itemName(s)))) throw new Error(`hub "${title(hub)}" has no homes button`);
    bob.closeWindow(hub);
    await sleep(500);
    const settings = await opens(bob, () => bob.chat('/settings'));
    if (!settings.slots.some((s) => s && /Teleport requests/i.test(itemName(s)))) throw new Error('no teleport requests setting');
    bob.closeWindow(settings);
    await sleep(500);
    for (const command of ['/craft', '/enderchest', '/trash']) {
      await lab(`perm Bob donutessentials.command.${command.slice(1)} true`);
      await sleep(300);
      const window = await opens(bob, () => bob.chat(command));
      bob.closeWindow(window);
      await sleep(500);
    }
    return 'hub lists homes, settings has toggles';
  });
  await check('command rules: a cooldown on any command', async () => {
    await lab('perm Bob donutessentials.command.near true');
    await sleep(300);
    await say(bob, '/near');
    await chat(bob, /Within 100 blocks|Nobody within/);
    await say(bob, '/near');
    await chat(bob, /You can use \/near again in/);
  });

  // ── 15. Rules ─────────────────────────────────────────────────────────────────────────────────────────────────
  await check('rules must be accepted before playing', async () => {
    await lab('set config.yml rules.require-accept true');
    await consoleSays('donutessentials reload', /Reloaded config/);
    const frank = await joinBot('Frank');
    await chat(frank, /Server rules/);
    const buttons = await chat(frank, /\[Accept\].*\[Decline\]/);
    if (!buttons.raw.includes('/rules accept')) throw new Error('no clickable accept');
    await say(frank, 'hi all');
    await chat(frank, /Accept the rules first/);
    await say(frank, '/rules accept');
    await chat(frank, /Thanks for accepting the rules/);
    frank.quit();
    await lab('set config.yml rules.require-accept false');
    await consoleSays('donutessentials reload', /Reloaded config/);
  });

  // ── 16. Maintenance and IP lock ───────────────────────────────────────────────────────────────────────────────
  await check('maintenance keeps players out unless whitelisted or allowed', async () => {
    await lab('perm Bob donutessentials.maintenance.bypass true');
    await lab('perm Cara donutessentials.maintenance.bypass true');
    await consoleSays('maintenance on', /Maintenance is on/);
    await sleep(1500);
    if (!/ true$/.test(await lab('online Bob'))) throw new Error('Bob (bypass) was kicked');
    await refused('Gus', /maintenance/i);
    await consoleSays('maintenance add Gus', /Gus can join during maintenance/);
    const gus = await joinBot('Gus');
    gus.quit();
    await consoleSays('maintenance off', /Maintenance is off/);
  });
  await check('IP lock refuses another address', async () => {
    await consoleSays('iplock set Gus 10.9.9.9', /Gus can now only join from 10\.9\.9\.9/);
    await refused('Gus', /IP address|can't join from/i);
    await consoleSays('iplock remove Gus', /Removed the IP lock of Gus/);
    const gus = await joinBot('Gus');
    gus.quit();
  });

  // ── 17. Placeholders and admin ────────────────────────────────────────────────────────────────────────────────
  await check('placeholders', async () => {
    const text = await papi('Bob', '%donutessentials_balance_formatted%|%donutessentials_homes%/%donutessentials_homes_max%|%donutessentials_afk%|%donutessentials_warns%|%donutessentials_baltop_1_name%');
    const [balance, homes, afk, warns, top] = text.split('|');
    const richest = target.variant === 'essentialsx' ? 'Willow' : 'Bob';
    if (balance !== '$1,065.00' || homes !== '1/3' || afk !== 'false' || warns !== '1' || top !== richest) throw new Error(text);
    return text;
  });
  await check('/donutessentials info and reload from the console', async () => {
    await consoleSays('donutessentials info', /Donut Essentials 3\.\d+\.\d+/);
    await consoleSays('donutessentials reload', /Reloaded config/);
  });

  // ── 18. Restart: everything comes back ────────────────────────────────────────────────────────────────────────
  if (target.restart) {
    await check('after a restart: homes, warps, balances, kit cooldowns, punishments', async () => {
      await say(bob, '/homes');
      await sleep(1500);
      server = await restart();
      const homes = await api('homes Bob');
      if (!/ 1 base@/.test(homes)) throw new Error(homes);
      if (!/shop@/.test(await api('warps'))) throw new Error('no shop warp');
      if (!/ 1065\.00 /.test(await api('balance Bob'))) throw new Error(await api('balance Bob'));
      if (!/active=WARN/.test(await api('punish Bob'))) throw new Error(await api('punish Bob'));
      const bob2 = await joinBot('Bob');
      await say(bob2, '/kit pvp');
      await chat(bob2, /You can claim pvp again in/);
      return 'all kept';
    });
  }
}
