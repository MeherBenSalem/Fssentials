"""Renders the base video (footage only, no text) from edl.json.

The footage was captured with the game's clock slowed (0.1x for the hook timelapse, 0.025x for the rest), so every
rendered frame is in the capture. Each output frame asks for a source game time; the two rendered frames around it
are cross-faded, so no frame repeats or skips at any speed. Fast passages (the timelapse) average a few samples
across half a frame interval (a 180 degree shutter). A slow camera drift runs through every shot, so even still
scenes never produce a repeated frame. The last frames cross-fade into the hook's first frame for a seamless loop.
"""
import os
import bisect
import json
import math
import subprocess
import sys

import cv2
import numpy as np
from scipy.interpolate import PchipInterpolator

R = os.environ.get('REEL_DIR', os.path.expanduser('~/.cache/donutessentials-smoke/reel'))
E = f'{R}/edit'
EDL = json.load(open(f'{E}/edl.json'))
FPS = EDL['fps']
W, H = EDL['size']
DUR = json.load(open(f'{E}/cues.json'))['duration']
NF = int(round(DUR * FPS))
SHUTTER = 0.5


def ease_out(x):
    x = min(1.0, max(0.0, x))
    return 1 - (1 - x) ** 3


class Source:
    """Unique rendered frames of one capture, read sequentially with a small cache."""

    def __init__(self, name):
        j = json.load(open(f'{E}/idx/{name}.json'))
        self.name, self.idx, self.t = name, j['idx'], j['t']
        self.k_of = {c: k for k, c in enumerate(self.idx)}
        self.cap, self.pos, self.cache = None, 0, {}
        gaps = np.diff(self.t) if len(self.t) > 1 else np.array([0.03])
        self.fade = float(min(0.05, max(0.012, np.median(gaps))))

    def frame(self, k):
        k = max(0, min(len(self.idx) - 1, k))
        if k in self.cache:
            return self.cache[k]
        want = self.idx[k]
        if self.cap is None or want < self.pos:
            self.cap = cv2.VideoCapture(f'{R}/raw/{self.name}.mkv')
            self.pos = 0
        while self.pos <= want:
            ok, f = self.cap.read()
            if not ok:
                raise RuntimeError(f'{self.name}: ran out of frames at {self.pos}')
            kk = self.k_of.get(self.pos)
            if kk is not None and kk >= k - 8:
                self.cache[kk] = f
            self.pos += 1
        for kk in [x for x in self.cache if x < k - 8 or x > k + 8]:
            del self.cache[kk]
        return self.cache[k]

    def sample(self, g):
        """Frame at game time g, cross-fading into the next render as g approaches it."""
        t = self.t
        if g <= t[0]:
            return self.frame(0).astype(np.float32)
        i = bisect.bisect_right(t, g) - 1
        if i >= len(t) - 1:
            return self.frame(len(t) - 1).astype(np.float32)
        d = min(t[i + 1] - t[i], self.fade)  # a held screen only cross-fades in its last render interval
        w = (g - (t[i + 1] - d)) / d
        if w <= 0.002:
            return self.frame(i).astype(np.float32)
        if w >= 0.998:
            return self.frame(i + 1).astype(np.float32)
        return cv2.addWeighted(self.frame(i), 1 - w, self.frame(i + 1), w, 0, dtype=cv2.CV_32F)


def cam_at(shot, t):
    keys = shot['cam']
    if t <= keys[0][0]:
        return tuple(keys[0][1:])
    for a, b in zip(keys, keys[1:]):
        if a[0] <= t <= b[0]:
            u = (t - a[0]) / (b[0] - a[0])
            mode = shot.get('camEase')
            u = u * u if mode == 'in' else 1 - (1 - u) ** 2 if mode == 'out' else u
            return tuple(a[i] + (b[i] - a[i]) * u for i in (1, 2, 3))
    return tuple(keys[-1][1:])


def grade(img):
    f = img * (1.06 / 255.0) + (0.5 - 0.5 * 1.06 + 0.008)
    gray = f.mean(axis=2, keepdims=True)
    f = gray + (f - gray) * 1.16
    return np.clip(f * 255.0, 0, 255).astype(np.uint8)


def build_map(shot):
    xs = [p[0] for p in shot['map']]
    ys = [p[1] for p in shot['map']]
    if shot['interp'] == 'smooth':
        f = PchipInterpolator(xs, ys)
        return lambda u: float(f(min(max(u, xs[0]), xs[-1])))
    return lambda u: float(np.interp(u, xs, ys))


def render_shot_frame(shot, src, fmap, t):
    local = t - shot['start']
    g = fmap(local)
    v = abs(fmap(local + 0.004) - fmap(local - 0.004)) / 0.008
    span = SHUTTER * v / FPS if shot.get('blur') else 0.0
    n = 1 if span < 0.02 else min(9, int(math.ceil(span / 0.02)) + 1)
    if n == 1:
        img = src.sample(g)
    else:
        img = None
        for k in range(n):
            s_k = src.sample(g + span * (k / (n - 1) - 0.5))
            img = s_k if img is None else img + s_k
        img /= n
    s, cx, cy = cam_at(shot, t)
    if shot.get('punch', 0) and local < 0.22:
        s *= 1 + shot['punch'] * (1 - ease_out(local / 0.22))
    s = max(s, 1.0, W / (2 * min(cx, W - cx)), H / (2 * min(cy, H - cy)))
    m = np.array([[s, 0, W / 2 - s * cx], [0, s, H / 2 - s * cy]], dtype=np.float32)
    out = cv2.warpAffine(img, m, (W, H), flags=cv2.INTER_LINEAR, borderMode=cv2.BORDER_REPLICATE)
    for c in shot.get('callouts', []):
        out = callout(out, img, c, local)
    return out


def callout(out, img, c, local):
    """A zoomed card of one part of the source frame (the chat, the action bar) lifted into the middle of the screen,
    since the bottom of a portrait reel sits under the platform's caption. The scene behind dims while it shows."""
    t0, t1 = c['t0'], c['t1']
    if not t0 <= local < t1:
        return out
    a = min(ease_out((local - t0) / 0.16), ease_out((t1 - local) / 0.12))
    x0, y0, x1, y1 = c['box']
    k = c['scale'] * (0.9 + 0.1 * a)
    w, h = int(round((x1 - x0) * k)), int(round((y1 - y0) * k))
    card = cv2.resize(img[y0:y1, x0:x1], (w, h), interpolation=cv2.INTER_LINEAR)
    X0, Y0 = W // 2 - w // 2, int(c['y']) - h // 2
    out = out * (1 - 0.4 * a)
    pad, border = 26, 6
    sy0, sy1, sx0, sx1 = max(0, Y0 - pad + 16), min(H, Y0 + h + pad + 16), max(0, X0 - pad), min(W, X0 + w + pad)
    out[sy0:sy1, sx0:sx1] *= 1 - 0.45 * a  # drop shadow
    by0, by1, bx0, bx1 = max(0, Y0 - border), min(H, Y0 + h + border), max(0, X0 - border), min(W, X0 + w + border)
    out[by0:by1, bx0:bx1] = out[by0:by1, bx0:bx1] * (1 - a) + 245.0 * a
    cy0, cy1, cx0, cx1 = max(0, Y0), min(H, Y0 + h), max(0, X0), min(W, X0 + w)
    out[cy0:cy1, cx0:cx1] = out[cy0:cy1, cx0:cx1] * (1 - a) + card[cy0 - Y0:cy1 - Y0, cx0 - X0:cx1 - X0] * a
    return out


def main():
    only = None
    if len(sys.argv) > 1:  # optional: render a time range for checks, e.g. compose.py 8 15
        only = (float(sys.argv[1]), float(sys.argv[2]))
    out_path = f'{E}/base.mp4' if only is None else f'{E}/base_part.mp4'
    sources, maps = {}, {}
    ff = subprocess.Popen(['ffmpeg', '-loglevel', 'error', '-y', '-f', 'rawvideo', '-pix_fmt', 'bgr24', '-s', f'{W}x{H}',
                           '-r', str(FPS), '-i', '-', '-c:v', 'libx264', '-preset', 'medium', '-crf', '12',
                           '-pix_fmt', 'yuv420p', out_path], stdin=subprocess.PIPE)
    shots = EDL['shots']
    first = shots[0]
    for fno in range(NF):
        t = fno / FPS
        if only and not (only[0] <= t < only[1]):
            continue
        shot = next((s for s in shots if s['start'] <= t < s['end']), shots[-1])
        src = sources.setdefault(shot['src'], Source(shot['src']))
        fmap = maps.setdefault(shot['name'], build_map(shot))
        out = render_shot_frame(shot, src, fmap, t)
        fade = shot.get('fade_to_hook', 0)
        if fade and t > DUR - fade:
            hsrc = sources.setdefault(first['src'], Source(first['src']))
            hmap = maps.setdefault(first['name'], build_map(first))
            hook = render_shot_frame(first, hsrc, hmap, 0.0)
            w = (t - (DUR - fade)) / fade
            w = w * w * (3 - 2 * w)
            out = out * (1 - w) + hook * w
        ff.stdin.write(grade(out).tobytes())
        if fno % 90 == 0:
            print(f'frame {fno}/{NF} {shot["name"]}', flush=True)
    ff.stdin.close()
    ff.wait()
    print(out_path, 'done')


main()
