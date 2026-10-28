// The listing artwork. Each entry returns { html, width, height, transparent?, file? } for render.cjs.
// (Cards are added once the screenshots exist; see marketing/README.md.)
const r = require('./render.cjs');

const HEADERS = {};

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
};

for (const [key, title] of Object.entries(HEADERS)) {
  assets[`header-${key}`] = () => ({ width: 1400, height: 120, transparent: true, html: r.header(title) });
}
module.exports = assets;
