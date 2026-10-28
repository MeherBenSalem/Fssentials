"""Original music bed, synthesized from scratch (no samples, no licensed audio). The sound effects are real
recordings placed by sfx_pro.py; the bed itself carries no hits or risers.

120 BPM in E minor, as long as the reel, so the groove continues across the loop seam. The hook and the loop end
share the same kick-less texture, the beat drops on the first cut, the platform shot is a breakdown and the title
card gets a break.
"""
import json
import os
import sys

import numpy as np
import soundfile as sf

R = os.environ.get('REEL_DIR', os.path.expanduser('~/.cache/donutessentials-smoke/reel'))
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from synth import SR, bass808, clap, hat, kick, pad, place, pluck  # noqa: E402

E = f'{R}/edit'
C = json.load(open(f'{E}/cues.json'))
DURATION, BEAT, DROP = C['duration'], C['beat'], C['drop']
N = int(round(DURATION * SR))
T0, T1 = C['title']
L0 = C['loaders']['bg'][0]
music = np.zeros(N)

CHORDS = [[64, 67, 71], [60, 64, 67], [55, 59, 62], [62, 66, 69]]  # Em C G D
ROOTS = [40, 36, 43, 38]
ARP = [[76, 79, 83, 79], [72, 76, 79, 76], [71, 74, 79, 74], [74, 78, 81, 78]]

beats = int(round(DURATION / BEAT))
for k in range(beats):
    bt = k * BEAT
    bar, pos = k // 4, k % 4
    chord = bar % 4
    intro = bt < DROP - 1e-3 or bt >= T1 - 1e-3
    breakdown = L0 - 1e-3 <= bt < T0 - 1e-3
    title = T0 - 1e-3 <= bt < T1 - 1e-3
    if not intro and not title:
        place(music, kick(0.95 if pos == 0 else 0.8), bt)
        if pos in (1, 3) and not breakdown:
            place(music, clap(0.8), bt)
        for h in range(2):
            place(music, hat(False, 0.85 if h == 0 else 0.5), bt + h * BEAT / 2)
        if not breakdown:
            place(music, hat(True, 0.35), bt + BEAT / 2)
    for s in range(4):
        if intro and s % 2 == 1:
            continue
        place(music, pluck(ARP[chord][(pos * 4 + s) % 4] + (12 if s == 3 and pos == 3 else 0), 0.2, 0.55 if intro else 0.7), bt + s * BEAT / 4)
    if pos == 0:
        if not intro:
            place(music, bass808(ROOTS[chord], BEAT * 4 - 0.02, 0.9 if not title else 0.6), bt)
        place(music, pad(CHORDS[chord], BEAT * 4, 1400 if intro or title else 2200, 0.3), bt)


def gap(at, length):
    i0, i1 = int((at - length) * SR), int(at * SR)
    fade = np.linspace(1, 0.12, max(1, int(0.03 * SR)))
    music[i0:i0 + len(fade)] *= fade
    music[i0 + len(fade):i1] *= 0.12


gap(DROP, 0.15)
gap(T0, 0.12)
fi, fo = int(0.02 * SR), int(0.08 * SR)
music[:fi] *= np.linspace(0, 1, fi)
music[-fo:] *= np.linspace(1, 0.6, fo)

def norm(x, peak):
    return x / max(1e-9, np.abs(x).max()) * peak


sf.write(f'{E}/music.wav', norm(music, 0.9).astype(np.float32), SR)
print('music written', DURATION)
