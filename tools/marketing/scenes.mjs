// The capture scenes, in order. Every action goes through the real plugin: commands run as Moonbeam (the real
// client) or the bots, menu clicks with the mouse, and the rules dialog clicked like a player would. The world and the
// items are set up with console commands and the test fixture.
//
// Run some scenes again with: node tools/marketing/capture.mjs --scenes=base,kits   (base always comes first: it
// builds the spawn the other scenes stand in).
import fs from 'node:fs';
import path from 'node:path';
import { x, sleep, hover, clickSlot, park, screenshot, record, gif, holdKey } from './capture.mjs';

const P = 'Moonbeam';

// Where each bot stands around the spawn centre: [dx, dz, yaw]. The camera stands at (0.5, 9.5) looking north.
const SPOTS = {
  Kiri: [-2.5, 1.5, -20], Ember: [2.5, 1.0, 25], Pixel: [4.5, -1.5, 35], Rook: [-4.5, -1.0, -35],
  Nova: [0.5, -2.5, 0], Sol: [-1.0, -4.5, -10],
};

// Moonbeam is an operator, which skips warmups, cooldowns and kit locks: switch those off so the shots show them.
const PLAYER_NODES = ['donutessentials.bypass.warmup false', 'donutessentials.bypass.cooldown false', 'donutessentials.tier.vip false',
  'donutessentials.tier.mvp false', 'donutessentials.homes.unlimited false', 'donutessentials.homes.8 true', 'donutessentials.kit.elytra false'];

// Positions at GUI scale 3, measured on 1920x1080 captures.
export const UI = {
  dialogAccept: [738, 1030],
  serverLinks: [1118, 467],
};

export function context({ server, bot, lab, consoleDir }) {
  const ctx = { server, lab, consoleDir, P };
  /** Runs a command as that player. */
  ctx.say = (who, line) => server.lab(`sudo ${who} ${line}`);
  /** A bot says something in chat (signed chat, like a real player). */
  ctx.chat = async (who, text) => (await ctx.bot(who)).chat(text);
  ctx.chatFree = () => sleep(11_000); // chat lines fade after ten seconds
  /** A bot that has accepted the rules (the server asks every new player). */
  ctx.bot = async (name) => {
    const b = await bot(name);
    if (!b.rulesAccepted) {
      await sleep(600);
      await ctx.say(name, 'rules accept');
      b.rulesAccepted = true;
    }
    return b;
  };
  /** Types a chat line or command in the real client. */
  ctx.type = async (text) => {
    x('key', 't');
    await sleep(500);
    x('type', text);
    await sleep(300);
    x('key', 'Return');
    await sleep(900);
  };
  /** Teleports a player relative to the spawn centre. */
  ctx.tp = async (who, dx, dz, yaw = 0, pitch = 0, dy = 0) => {
    server.command(`tp ${who} ${ctx.px + dx} ${ctx.py + dy} ${ctx.pz + dz} ${yaw} ${pitch}`);
    await sleep(250);
  };
  /** The camera: Moonbeam at the south edge of the plaza looking at the beacon. */
  ctx.camera = () => ctx.tp(P, 0.5, 9.5, 180, 8);
  /** Puts a bot on its spot, facing the camera. */
  ctx.place = (who) => ctx.tp(who, ...SPOTS[who], 0);
  /** Opens a menu with a command and waits for it to draw. */
  ctx.open = async (command) => {
    await ctx.say(P, command);
    await sleep(2500);
  };
  ctx.close = async () => {
    x('key', 'Escape');
    await sleep(800);
  };
  ctx.give = (who, item, count = 1) => server.command(`give ${who} ${item} ${count}`);
  return ctx;
}

export const SCENES = {
  // ── The rules screen a new player gets (a native dialog on 1.21.7+) ──────────────────────────────────────────
  async rules(ctx) {
    const { server } = ctx;
    // Wait out the join toasts (the starter kit's advancement, "chat messages can't be verified").
    await sleep(14_000);
    screenshot('rules-dialog');
    x('move', ...UI.dialogAccept);
    await sleep(600);
    x('click', ...UI.dialogAccept, 1);
    await sleep(2500);
    screenshot('rules-accepted');
    // In case the click missed: the rest of the scenes need the rules accepted.
    await ctx.say(P, 'rules accept');
    // LuckPerms knows Moonbeam once they joined: the admin group (its chat format) and the nodes above.
    server.command(`lp user ${P} parent set admin`);
    for (const node of PLAYER_NODES) server.command(`lp user ${P} permission set ${node}`);
    // Everything unlocked now, so no advancement or recipe toast pops up in a later shot.
    server.command(`advancement grant ${P} everything`);
    server.command(`recipe give ${P} *`);
    await sleep(1500);
  },

  // ── The spawn: a plaza with a beacon in sunflower plains ─────────────────────────────────────────────────────
  async base(ctx) {
    const { server } = ctx;
    server.mark();
    server.command('locate biome minecraft:sunflower_plains');
    const located = await server.waitFor(/sunflower_plains is at \[(-?\d+), [^,]+, (-?\d+)\]/, 180_000, 'locate');
    const px = Number(located[1]);
    const pz = Number(located[2]);
    server.command(`spreadplayers ${px} ${pz} 0 1 false ${P}`);
    await sleep(3000);
    const surface = await server.lab(`pos ${P}`);
    const py = Math.floor(Number(surface.split(' ')[4]));
    Object.assign(ctx, { px, py, pz });
    server.command(`forceload add ${px - 32} ${pz - 32} ${px + 32} ${pz + 32}`);
    await sleep(5000);
    const box = (r, y, block) => `fill ${px - r} ${py + y} ${pz - r} ${px + r} ${py + y} ${pz + r} ${block}`;
    for (const command of [
      `fill ${px - 11} ${py} ${pz - 11} ${px + 11} ${py + 14} ${pz + 11} air`,
      `fill ${px - 11} ${py - 4} ${pz - 11} ${px + 11} ${py - 2} ${pz + 11} dirt`,
      box(11, -1, 'grass_block'),
      box(8, -1, 'stone_bricks'),
      box(7, -1, 'polished_andesite'),
      box(2, -1, 'smooth_quartz'),
      `fill ${px - 1} ${py - 2} ${pz - 1} ${px + 1} ${py - 2} ${pz + 1} iron_block`,
      `setblock ${px} ${py - 1} ${pz} beacon`,
      // Paths out of the plaza.
      `fill ${px} ${py - 1} ${pz + 3} ${px} ${py - 1} ${pz + 11} light_blue_glazed_terracotta`,
      `fill ${px} ${py - 1} ${pz - 11} ${px} ${py - 1} ${pz - 3} light_blue_glazed_terracotta`,
      `fill ${px + 3} ${py - 1} ${pz} ${px + 11} ${py - 1} ${pz} light_blue_glazed_terracotta`,
      `fill ${px - 11} ${py - 1} ${pz} ${px - 3} ${py - 1} ${pz} light_blue_glazed_terracotta`,
      // Lantern posts at the corners.
      ...[[-7, -7], [7, -7], [-7, 7], [7, 7]].flatMap(([dx, dz]) => [
        `fill ${px + dx} ${py} ${pz + dz} ${px + dx} ${py + 2} ${pz + dz} stone_brick_wall`,
        `setblock ${px + dx} ${py + 3} ${pz + dz} lantern`,
      ]),
      // Flower beds.
      ...[[-5, -5], [5, -5], [-5, 5], [5, 5]].flatMap(([dx, dz]) => [
        `setblock ${px + dx} ${py - 1} ${pz + dz} grass_block`,
        `setblock ${px + dx} ${py} ${pz + dz} ${dx < 0 ? 'cornflower' : 'allium'}`,
      ]),
    ]) server.command(command);
    await sleep(1500);
    await ctx.tp(P, 0.5, 3.5, 0, 0);
    await sleep(800);
    await ctx.say(P, 'setspawn');
    await ctx.camera();
    await sleep(6000);
  },

  // The scenery behind every card: the plaza from above, HUD hidden.
  async scenery(ctx) {
    const { server, tp } = ctx;
    server.command(`gamemode spectator ${P}`);
    await tp(P, 11, 13, 140, 26, 7);
    await sleep(40_000);
    x('key', 'F1');
    await sleep(1500);
    screenshot('scenery');
    x('key', 'F1');
    server.command(`gamemode survival ${P}`);
    await ctx.camera();
    await sleep(5000);
  },

  // ── The other players ─────────────────────────────────────────────────────────────────────────────────────────
  async people(ctx) {
    const { server } = ctx;
    for (const name of Object.keys(SPOTS)) {
      await ctx.bot(name);
      await ctx.place(name);
    }
    for (const command of ['lp user Kiri parent set vip', 'lp user Ember parent set builder', 'lp user Pixel parent set vip']) server.command(command);
    for (const [name, amount] of [['Kiri', '125k'], ['Ember', '87.5k'], ['Pixel', '42k'], ['Rook', '15k'], ['Nova', '9800'], ['Sol', '3200'],
      [P, '56k']]) server.command(`eco set ${name} ${amount}`);
    await sleep(2000);
    await ctx.chatFree();
  },

  // ── /menu ─────────────────────────────────────────────────────────────────────────────────────────────────────
  async hub(ctx) {
    await ctx.open('menu');
    await park();
    screenshot('menu-hub');
    await hover(4, 4);
    screenshot('menu-hub-profile');
    await hover(10, 4);
    screenshot('menu-hub-homes');
    await ctx.close();
  },

  // ── Homes: five places, one of them in the Nether and one in the End ────────────────────────────────────────
  async homes(ctx) {
    const { server, say, tp } = ctx;
    const places = [
      ['base', () => tp(P, 0.5, 3.5, 0, 0)],
      ['farm', () => tp(P, 9.5, -9.5, 0, 0)],
      ['mine', async () => {
        server.command(`fill ${ctx.px + 20} ${ctx.py - 30} ${ctx.pz + 20} ${ctx.px + 22} ${ctx.py - 28} ${ctx.pz + 22} air`);
        await tp(P, 21.5, 21.5, 0, 0, -30);
      }],
      ['nether', async () => {
        server.command('execute in minecraft:the_nether run fill 6 100 6 10 103 10 air');
        server.command('execute in minecraft:the_nether run fill 6 99 6 10 99 10 netherrack');
        await server.lab(`tp ${P} world_nether 8.5 100 8.5`);
        await sleep(3500);
      }],
      ['island', async () => {
        server.command('execute in minecraft:the_end run fill 98 59 -2 102 59 2 end_stone');
        await server.lab(`tp ${P} world_the_end 100.5 60 0.5`);
        await sleep(3500);
      }],
    ];
    for (const [name, go] of places) {
      await go();
      await sleep(900);
      await say(P, `sethome ${name}`);
      await sleep(600);
    }
    await server.lab(`tp ${P} world ${ctx.px + 0.5} ${ctx.py} ${ctx.pz + 9.5}`);
    await sleep(3000);
    await ctx.camera();
    await ctx.chatFree();
    await ctx.open('homes');
    await park();
    screenshot('menu-homes');
    await hover(10, 4);
    screenshot('menu-homes-tip');
    await hover(14, 4);
    screenshot('menu-homes-nether');
    await ctx.close();
  },

  // ── A home teleport: the title countdown, then there ─────────────────────────────────────────────────────────
  async home(ctx) {
    await ctx.camera();
    await sleep(1500);
    const clip = record('home', 9);
    await sleep(700);
    await ctx.say(P, 'home farm');
    await sleep(1400);
    screenshot('home-warmup');
    await sleep(3200);
    screenshot('home-landed');
    const raw = await clip;
    gif([{ raw }], 'home');
    await ctx.camera();
    await ctx.chatFree();
  },

  // ── Warps: each with the icon held when it was set ───────────────────────────────────────────────────────────
  async warps(ctx) {
    const { server, say, tp } = ctx;
    const warps = [
      ['shop', 'emerald', [6.5, -6.5]], ['arena', 'iron_sword', [-6.5, -6.5]], ['farms', 'wheat', [6.5, 6.5]],
      ['mines', 'diamond_pickaxe', [-6.5, 6.5]], ['parkour', 'feather', [0.5, -6.5]], ['market', 'gold_ingot', [6.5, 0.5]],
      ['crates', 'tripwire_hook', [-6.5, 0.5]],
    ];
    for (const [name, icon, [dx, dz]] of warps) {
      await tp(P, dx, dz, 180, 0);
      await server.lab(`hold ${P} ${icon}`);
      await say(P, `setwarp ${name}`);
      await sleep(500);
    }
    await server.lab(`hold ${P} air`).catch(() => {});
    // Visits: the bots use the warps (each visit counts once the teleport finishes).
    const visits = { shop: ['Kiri', 'Ember', 'Pixel', 'Nova', 'Sol'], arena: ['Rook', 'Kiri', 'Pixel'], market: ['Ember', 'Nova'], crates: ['Sol'] };
    for (const [warp, who] of Object.entries(visits)) {
      for (const name of who) await say(name, `warp ${warp}`);
      await sleep(3500);
    }
    for (const name of Object.keys(SPOTS)) await ctx.place(name);
    await ctx.camera();
    await ctx.chatFree();
    await ctx.open('warps');
    await park();
    screenshot('menu-warps');
    await hover(16, 4);
    screenshot('menu-warps-tip');
    await ctx.close();
  },

  // ── Kits: made from an inventory, shown with their state ─────────────────────────────────────────────────────
  async kits(ctx) {
    const { server, say, give } = ctx;
    await server.lab('set config.yml kits.per-kit-permissions true');
    server.command('donutessentials reload');
    await sleep(1500);
    const kits = [
      ['warrior', '1d', '0', [
        'diamond_sword[enchantments={sharpness:5,unbreaking:3,looting:3},custom_name={text:"Dawnbreaker",color:"gold",italic:false},lore=[{text:"Forged at spawn.",color:"gray",italic:false}]]',
        'diamond_helmet[enchantments={protection:4}]', 'diamond_chestplate[enchantments={protection:4}]',
        'diamond_leggings[enchantments={protection:4}]', 'diamond_boots[enchantments={protection:4,feather_falling:4}]',
        'golden_apple 8', 'cooked_beef 32']],
      ['miner', '6h', '0', [
        'diamond_pickaxe[enchantments={efficiency:5,fortune:3,unbreaking:3},custom_name={text:"Deep Digger",color:"aqua",italic:false}]',
        'torch 64', 'ladder 32', 'bread 32']],
      ['builder', '12h', '500', ['bricks 64', 'oak_planks 64', 'glass 64', 'stone_bricks 64', 'lantern 16']],
      ['elytra', '7d', '0', ['elytra[enchantments={unbreaking:3,mending:1}]', 'firework_rocket 64']],
      ['potions', 'once', '0', ['potion[potion_contents={potion:"minecraft:strong_healing"}] 3', 'potion[potion_contents={potion:"minecraft:long_swiftness"}] 2']],
    ];
    for (const [name, cooldown, cost, items] of kits) {
      await server.lab(`clear ${P}`);
      for (const item of items) {
        const [spec, count] = item.split(/ (?=\d+$)/);
        give(P, spec, count || 1);
      }
      // The first item lands in the selected hotbar slot: the held item is the kit's icon.
      await sleep(1000);
      await say(P, `createkit ${name} ${cooldown} ${cost}`);
      await sleep(700);
    }
    await server.lab(`clear ${P}`);
    // States: starter claimed on the first join, warrior just claimed (cooldown), potions claimed once.
    await say(P, 'kit warrior');
    await sleep(800);
    await say(P, 'kit potions');
    await sleep(800);
    await server.lab(`clear ${P}`);
    await ctx.chatFree();
    await ctx.open('kits');
    await park();
    screenshot('menu-kits');
    // Sorted by name: builder, elytra, miner, potions, starter, warrior (slots 10-15).
    await hover(15, 4);
    screenshot('menu-kits-tip');
    await hover(11, 4);
    screenshot('menu-kits-locked');
    await hover(12, 4);
    screenshot('menu-kits-ready');
    // Right-click: the preview of warrior, and its named sword.
    await clickSlot(15, 4, 1800, 3);
    await park();
    screenshot('menu-kit-preview');
    await hover(0, 6);
    screenshot('menu-kit-preview-tip');
    await ctx.close();
  },

  // ── Money: payments and the richest players ─────────────────────────────────────────────────────────────────
  async economy(ctx) {
    const { say } = ctx;
    await say('Kiri', `pay ${P} 2.5k`);
    await sleep(900);
    await say(P, 'pay Ember 1k');
    await sleep(1500);
    screenshot('pay-chat');
    await sleep(4000);
    await ctx.open('baltop');
    await park();
    screenshot('menu-baltop');
    await hover(49, 6);
    screenshot('menu-baltop-you');
    await ctx.close();
    await ctx.chatFree();
  },

  // ── A teleport request with buttons ─────────────────────────────────────────────────────────────────────────
  async tpa(ctx) {
    await ctx.say('Kiri', `tpa ${P}`);
    await sleep(1500);
    screenshot('tpa-request');
    await ctx.say(P, 'tpaccept Kiri');
    await sleep(4500);
    screenshot('tpa-accepted');
    await ctx.place('Kiri');
    await ctx.chatFree();
  },

  // ── Chat: rank prefixes, a mention and [item] ───────────────────────────────────────────────────────────────
  async chat(ctx) {
    const { server, chat, type } = ctx;
    server.command(`give ${P} diamond_sword[enchantments={sharpness:5,unbreaking:3},custom_name={text:"Dawnbreaker",color:"gold",italic:false}] 1`);
    server.command(`item replace entity ${P} hotbar.0 with diamond_sword[enchantments={sharpness:5,unbreaking:3},custom_name={text:"Dawnbreaker",color:"gold",italic:false}]`);
    await sleep(600);
    await chat('Kiri', 'anyone up for the arena tonight?');
    await sleep(1300);
    await chat('Ember', `@${P} the new spawn looks amazing`);
    await sleep(1300);
    await chat('Pixel', 'what did you get from the warrior kit?');
    await sleep(1300);
    await type('this! [item]');
    await sleep(1500);
    screenshot('chat');
    // The [item] tooltip: open the chat and hover the item name ("[Admin] Moonbeam » this! " is about 400 px wide).
    x('key', 't');
    await sleep(700);
    x('move', 500, 947);
    await sleep(900);
    screenshot('chat-item');
    x('key', 'Escape');
    await sleep(800);
    await ctx.chatFree();
  },

  // ── Private messages and mail ───────────────────────────────────────────────────────────────────────────────
  async messages(ctx) {
    const { say } = ctx;
    await say('Kiri', `msg ${P} can you tp me to the shop?`);
    await sleep(1200);
    await say(P, 'r sure, one sec!');
    await sleep(1200);
    await say('Ember', `mail send ${P} Left you some diamonds at the shop`);
    await sleep(1500);
    screenshot('messages');
    await ctx.chatFree();
  },

  // ── Moderation: a warning, a mute, a report with buttons, the history ───────────────────────────────────────
  async moderation(ctx) {
    const { say } = ctx;
    await say(P, 'warn Rook Spamming in chat');
    await sleep(1200);
    await say(P, 'tempmute Rook 30m Advertising');
    await sleep(1200);
    await say('Kiri', 'report Rook Flying around the shop');
    await sleep(1500);
    screenshot('moderation-chat');
    await ctx.chatFree();
    await say(P, 'history Rook');
    await sleep(1500);
    screenshot('history');
    await ctx.chatFree();
    await say('Nova', 'report Rook Selling stolen items');
    await sleep(800);
    await say('Sol', 'report Pixel Mean words');
    await sleep(800);
    await ctx.chatFree();
    await ctx.open('reports');
    await park();
    screenshot('menu-reports');
    // Newest first: Sol's report, Nova's, then Kiri's (slots 10-12).
    await hover(12, 6);
    screenshot('menu-reports-tip');
    await ctx.close();
  },

  // ── Staff tools: /invsee and /whois ─────────────────────────────────────────────────────────────────────────
  async staff(ctx) {
    const { server, say, give } = ctx;
    for (const item of ['diamond_pickaxe[enchantments={efficiency:4}]', 'cooked_porkchop 24', 'oak_log 48', 'iron_ingot 17', 'torch 40',
      'shield', 'bow[enchantments={power:3}]', 'arrow 52', 'emerald 9']) {
      const [spec, count] = item.split(/ (?=\d+$)/);
      give('Kiri', spec, count || 1);
    }
    server.command('item replace entity Kiri armor.chest with iron_chestplate');
    server.command('item replace entity Kiri armor.head with iron_helmet');
    await sleep(800);
    await ctx.open('invsee Kiri');
    await park();
    screenshot('invsee');
    await ctx.close();
    await say(P, 'whois Kiri');
    await sleep(1500);
    // More lines than the closed chat shows: open it.
    x('key', 't');
    await sleep(700);
    x('move', 1500, 300);
    await sleep(300);
    screenshot('whois');
    x('key', 'Escape');
    await sleep(800);
    await ctx.chatFree();
  },

  // ── AFK in the player list ──────────────────────────────────────────────────────────────────────────────────
  async afk(ctx) {
    await ctx.say('Pixel', 'afk');
    await sleep(800);
    await ctx.say('Sol', 'afk getting food');
    await sleep(1500);
    screenshot('afk-chat');
    const held = holdKey('Tab', 2.5);
    await sleep(1500);
    screenshot('afk-tab');
    await held;
    await ctx.chatFree();
  },

  // ── Combat: tagged, and /spawn refused ──────────────────────────────────────────────────────────────────────
  async combat(ctx) {
    const { server, say } = ctx;
    await ctx.camera();
    await server.lab(`hit Rook ${P} 1`);
    await sleep(500);
    await say(P, 'spawn');
    await sleep(900);
    screenshot('combat');
    await sleep(16_000);
    await ctx.chatFree();
  },

  // ── A broadcast: chat lines and a title ─────────────────────────────────────────────────────────────────────
  async broadcast(ctx) {
    ctx.server.command('broadcast Double XP weekend! | Every mob drops twice the experience until Monday.');
    await sleep(1500);
    screenshot('broadcast');
    await sleep(5000);
    await ctx.chatFree();
  },

  // ── Server links in the pause menu ──────────────────────────────────────────────────────────────────────────
  async links(ctx) {
    x('key', 'Escape');
    await sleep(1500);
    screenshot('pause-menu');
    x('move', ...UI.serverLinks);
    await sleep(500);
    x('click', ...UI.serverLinks, 1);
    await sleep(1500);
    screenshot('server-links');
    x('key', 'Escape');
    await sleep(800);
    x('key', 'Escape');
    await sleep(800);
    void ctx;
  },

  // ── /settings ───────────────────────────────────────────────────────────────────────────────────────────────
  async settings(ctx) {
    await ctx.open('settings');
    await park();
    screenshot('menu-settings');
    await hover(11, 4);
    screenshot('menu-settings-tip');
    await ctx.close();
  },

  // ── Random teleport ─────────────────────────────────────────────────────────────────────────────────────────
  async rtp(ctx) {
    await ctx.camera();
    await sleep(1000);
    const clip = record('rtp', 14);
    await sleep(700);
    await ctx.say(P, 'rtp');
    await sleep(1500);
    screenshot('rtp-warmup');
    await sleep(9000);
    screenshot('rtp-landed');
    const raw = await clip;
    gif([{ raw }], 'rtp');
  },

  // ── The console report ──────────────────────────────────────────────────────────────────────────────────────
  async report(ctx) {
    const { server } = ctx;
    const from = server.lines.length;
    server.command('donutessentials info');
    await sleep(2500);
    const report = server.lines.slice(from).map((line) => line.replace(/^\[[^\]]*INFO\]:? ?/, '')).filter((line) => line.trim());
    fs.mkdirSync(ctx.consoleDir, { recursive: true });
    fs.writeFileSync(path.join(ctx.consoleDir, 'admin-info.txt'), report.join('\n') + '\n');
  },
};

export async function scenes(options) {
  const { server } = options;
  const ctx = context(options);
  for (const [name, scene] of Object.entries(SCENES)) {
    if (options.only && !options.only.includes(name) && name !== 'base' && name !== 'rules' && name !== 'people') continue;
    console.log(`scene ${name}`);
    // Animals wander into shots.
    if (ctx.px !== undefined) server.command(`execute at ${P} run kill @e[type=!player,distance=..64]`);
    await scene(ctx);
  }
}
