// Renders overlay.html to a transparent PNG sequence: node render_overlay.cjs [t1 t2 ...] (test frames) or all frames.
const fs = require('fs'), path = require('path');
const { chromium } = require('playwright');
const R = require('path').join(process.env.REEL_DIR || require('path').join(require('os').homedir(), '.cache/donutessentials-smoke/reel'), 'edit');
(async () => {
  const html = fs.readFileSync(`${R}/overlay.html`, 'utf8')
    .replace('__CAPTIONS__', fs.readFileSync(`${R}/captions.json`, 'utf8'))
    .replace('__WORDS__', fs.readFileSync(`${R}/words.json`, 'utf8'))
    .replace('__CUES__', fs.readFileSync(`${R}/cues.json`, 'utf8'));
  fs.writeFileSync(`${R}/.overlay_run.html`, html);
  const dur = JSON.parse(fs.readFileSync(`${R}/cues.json`, 'utf8')).duration;
  const tests = process.argv.slice(2).map(Number);
  const out = tests.length ? `${R}/ovtest` : `${R}/ov`;
  fs.mkdirSync(out, { recursive: true });
  const times = tests.length ? tests : Array.from({ length: Math.round(dur * 30) }, (_, i) => i / 30);
  const browser = await chromium.launch();
  const workers = tests.length ? 1 : 3;
  const chunk = Math.ceil(times.length / workers);
  await Promise.all(Array.from({ length: workers }, async (_, w) => {
    const page = await browser.newPage({ viewport: { width: 1080, height: 1920 }, deviceScaleFactor: 1 });
    await page.goto('file://' + `${R}/.overlay_run.html`);
    await page.evaluate(() => document.fonts.ready);
    await page.waitForTimeout(300);
    for (let i = w * chunk; i < Math.min(times.length, (w + 1) * chunk); i++) {
      await page.evaluate(t => window.frame(t), times[i]);
      const name = tests.length ? `t_${times[i].toFixed(2)}.png` : `ov_${String(i).padStart(4, '0')}.png`;
      await page.screenshot({ path: path.join(out, name), omitBackground: true });
      if (!tests.length && i % 150 === 0) console.log('overlay', i, '/', times.length);
    }
  }));
  await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
