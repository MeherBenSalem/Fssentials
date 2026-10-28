// Renders the Reel cover (1080x1920) from edit/cover.html.
const { chromium } = require('playwright');
const E = require('path').join(process.env.REEL_DIR || require('path').join(require('os').homedir(), '.cache/donutessentials-smoke/reel'), 'edit');
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1080, height: 1920 } });
  await p.goto('file://' + E + '/cover.html');
  await p.evaluate(() => document.fonts.ready);
  await p.screenshot({ path: E + '/cover.png' });
  await b.close();
})();
