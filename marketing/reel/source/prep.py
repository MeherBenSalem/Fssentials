"""Indexes a slowed-clock capture: which captured frames are new renders, and the game time of each."""
import os
import json, sys
import cv2
import numpy as np

R = os.environ.get('REEL_DIR', os.path.expanduser('~/.cache/donutessentials-smoke/reel'))
SLOW, CAP_FPS, THRESH = float(sys.argv[2]) if len(sys.argv) > 2 else 0.1, 15.0, 8
name = sys.argv[1]
cap = cv2.VideoCapture(f"{R}/raw/{name}.mkv")
prev, n, idx, motion = None, 0, [], []
while True:
    ok, f = cap.read()
    if not ok:
        break
    g = cv2.resize(cv2.cvtColor(f, cv2.COLOR_BGR2GRAY), (270, 480), interpolation=cv2.INTER_AREA).astype(np.int16)
    d = None if prev is None else np.abs(g - prev)
    if d is None or d.max() > THRESH:
        idx.append(n)
        motion.append(0.0 if d is None else round(float(d.mean()), 3))
    prev, n = g, n + 1
times = [round(i / CAP_FPS * SLOW, 5) for i in idx]
gaps = np.diff(times) if len(times) > 1 else np.array([1 / 60])
json.dump({"name": name, "frames": n, "game_len": round(n / CAP_FPS * SLOW, 4), "idx": idx, "t": times, "motion": motion,
           "dmed": round(float(np.median(gaps)), 5)}, open(f"{R}/edit/idx/{name}.json", "w"))
print(f"{name}: {len(idx)} renders over {n / CAP_FPS * SLOW:.2f} game s, median gap {np.median(gaps) * 1000:.1f} ms (p90 {np.percentile(gaps, 90) * 1000:.1f} ms)")
