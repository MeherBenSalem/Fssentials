"""Production sound design for the Donut Essentials reel: real recorded sound effects, timed from cues.json.

Every sound is a professional recording from Mixkit (Mixkit Sound Effects Free License: free for commercial use in
videos, no attribution required; https://mixkit.co/license/), listed in SFX below by its Mixkit id. No synthesized
effects. Each cue places a recording so its main hit (its loudest 10 ms, measured per file) lands exactly on the
frame it belongs to; whooshes peak into the cut they lead to. Anything that starts before 0 wraps around to the end
of the reel. Writes sfx.wav (48 kHz stereo).

The library itself is not stored in the repository: fetch_sfx.sh downloads the files from Mixkit by id.
"""
import json
import os

import numpy as np
import soundfile as sf
from scipy.signal import butter, resample_poly, sosfilt

R = os.environ.get('REEL_DIR', os.path.expanduser('~/.cache/donutessentials-smoke/reel'))
E = f'{R}/edit'
LIB = f'{R}/sfxlib'
SR = 48000
C = json.load(open(f'{E}/cues.json'))
DUR = C['duration']
N = int(round(DUR * SR))

# Mixkit id -> what it is (titles as published on mixkit.co)
SFX = {
    1143: 'Cinematic whoosh deep impact', 2908: 'Movie trailer epic impact', 2901: 'Epic movie impact', 2950: 'Intro text glitch',
    1490: 'Fast whoosh transition', 1481: 'Cinematic swoosh', 2568: 'Cool interface click tone', 3124: 'Modern technology select',
    2350: 'Magic sparkle whoosh', 2583: 'Magical light transition', 2354: 'Message pop alert', 2357: 'Bubble pop up alert notification',
    2867: 'Confirmation tone', 1993: 'Clinking coins', 2155: 'Impact of a strong punch', 2143: 'Hard and quick punch',
    1110: 'Click error', 933: 'Bell notification', 2364: 'Hard pop click',
}

_cache = {}


def load(iid):
    """A recording at 48 kHz stereo, normalized to 0 dBFS peak, and the time of its main hit."""
    if iid not in _cache:
        a, sr = sf.read(os.path.join(LIB, f'{iid}.wav'), dtype='float32', always_2d=True)
        if a.shape[1] == 1:
            a = np.repeat(a, 2, axis=1)
        a = a[:, :2]
        if sr != SR:
            a = np.stack([resample_poly(a[:, c], SR, sr) for c in range(2)], axis=1).astype(np.float32)
        a /= max(1e-6, np.abs(a).max())
        m = a.mean(1)
        win = int(0.01 * SR)
        env = np.sqrt(np.convolve(m ** 2, np.ones(win) / win, 'same'))
        _cache[iid] = (a, int(np.argmax(env)) / SR)
    return _cache[iid]


out = np.zeros((N, 2), np.float32)


def place(iid, at, db=0.0, align='peak', start=0.0, length=None, fade_in=0.0, fade_out=0.08, hp=None, lp=None):
    """Places recording iid so its `align` point ("peak", "start" or "end") lands at `at` seconds.

    start/length pick a segment of the file (seconds); fades are in seconds; hp/lp are filter corners in Hz."""
    a, peak = load(iid)
    seg = a[int(start * SR): int((start + length) * SR) if length else None].copy()
    if hp:
        seg = sosfilt(butter(2, hp, 'high', fs=SR, output='sos'), seg, axis=0).astype(np.float32)
    if lp:
        seg = sosfilt(butter(2, lp, 'low', fs=SR, output='sos'), seg, axis=0).astype(np.float32)
    n = len(seg)
    if fade_in:
        k = min(n, int(fade_in * SR))
        seg[:k] *= np.linspace(0, 1, k)[:, None]
    if fade_out:
        k = min(n, int(fade_out * SR))
        seg[n - k:] *= np.linspace(1, 0, k)[:, None]
    ref = {'peak': peak - start, 'start': 0.0, 'end': n / SR}[align]
    i0 = int(round((at - ref) * SR))
    g = 10 ** (db / 20)
    idx = (np.arange(n) + i0) % N  # wraps around the loop
    np.add.at(out, idx, seg * g)


EV = C['events']


def ev(key):
    return EV.get(key, [])


# ---------------------------------------------------------------- hook: the hub is already open
place(1143, 0.0, db=-7)                                                    # the opening hit
for t in ev('hook.hovers'):
    place(2568, t, db=-21, hp=400)                                         # the cursor crossing each button
place(2901, EV['drop'], db=-8)                                             # the beat drops on the first cut
# ---------------------------------------------------------------- homes: a click, the countdown, there
for t in ev('homes.click'):
    place(2568, t, db=-12)
for t in ev('homes.ticks'):
    place(3124, t, db=-17)
for t in ev('homes.land'):
    place(2350, t, db=-11)                                                 # the teleport
    place(2583, t + 0.05, db=-19)
# ---------------------------------------------------------------- teleport request: click [Accept]
for t in ev('tpa.click'):
    place(2568, t, db=-11)
for t in ev('tpa.accepted'):
    place(2867, t, db=-16)
# ---------------------------------------------------------------- warps and kits
for t in ev('warps.hovers') + ev('kits.hovers'):
    place(2568, t, db=-22, hp=400)
for t in ev('kits.rclick'):
    place(2568, t, db=-12)
for t in ev('kits.open'):
    place(3124, t, db=-15)
for t in ev('kits.tooltip'):
    place(2583, t, db=-20)                                                 # the enchanted sword's shimmer
# ---------------------------------------------------------------- economy: money arrives
for t in ev('economy.pay'):
    place(1993, t, db=-11)
for t in ev('economy.open'):
    place(2568, t, db=-14)
for t in ev('economy.tooltip'):
    place(2568, t, db=-21, hp=400)
# ---------------------------------------------------------------- chat
for t in ev('chat.lines'):
    place(2354, t, db=-19)
for t in ev('chat.mention'):
    place(2357, t, db=-16)
for t in ev('chat.tooltip'):
    place(2364, t, db=-18)
# ---------------------------------------------------------------- combat
for t in ev('combat.hit'):
    place(2155, t, db=-7)
    place(2143, t + 0.02, db=-12)
for t in ev('combat.refused'):
    place(1110, t, db=-13)
# ---------------------------------------------------------------- moderation
for t in ev('moderation.lines'):
    place(933, t, db=-18)
for t in ev('moderation.open'):
    place(2568, t, db=-14)
for t in ev('moderation.tooltip'):
    place(2568, t, db=-21, hp=400)
# ---------------------------------------------------------------- rules
for t in ev('rules.click'):
    place(2568, t, db=-11)
    place(2867, t + 0.12, db=-15)
# ---------------------------------------------------------------- platforms, title, back to the hub
place(1490, EV['wide'], db=-16)                                            # out to the whole plaza
TITLE = EV['title']
place(2908, TITLE, db=-4)                                                  # title hit
place(2950, TITLE + 0.15, db=-19, hp=300)                                  # the name glitches in
place(1481, EV['loopend'], db=-17)                                         # back to the plaza
for t in ev('loopend.open'):
    place(2568, t, db=-13)
for t in ev('loopend.tooltip'):
    place(2568, t, db=-21, hp=400)                                         # lands on Homes, where the hook starts

peak = np.abs(out).max()
out *= 0.9 / max(1e-6, peak)
sf.write(f'{E}/sfx.wav', out, SR)
print(f'sfx.wav: {len(SFX)} recordings, {DUR:.2f} s, peak normalized from {20 * np.log10(peak):.1f} dBFS')
