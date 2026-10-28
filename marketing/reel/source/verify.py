"""Smoothness check: repeated frames and motion jitter, per shot.

A repeated frame (no visible change from the previous one) shows as a stutter. Jitter compares each
frame's motion with the average of its neighbours; steady motion scores near 0, judder (uneven steps
such as 1-2-1-2 source frames per output frame) scores high."""
import json, sys
import cv2
import numpy as np

path = sys.argv[1]
edl = json.load(open(sys.argv[2]))["shots"] if len(sys.argv) > 2 else None
cap = cv2.VideoCapture(path)
prev, d = None, []
while True:
    ok, f = cap.read()
    if not ok:
        break
    g = cv2.resize(cv2.cvtColor(f, cv2.COLOR_BGR2GRAY), (270, 480), interpolation=cv2.INTER_AREA).astype(np.float32)
    if prev is not None:
        d.append(float(np.abs(g - prev).mean()))
    prev = g
d = np.array(d)
t = (np.arange(len(d)) + 1) / 30.0
dups = t[d < 0.08]
print(f"{path}: {len(d) + 1} frames, repeated frames: {len(dups)}")
if len(dups):
    print("  at", " ".join(f"{x:.2f}" for x in dups[:40]))
if edl:
    for s in edl:
        m = (t > s["start"] + 0.05) & (t < s["end"] - 0.05)
        x = d[m]
        if len(x) < 5:
            continue
        jit = np.abs(x[1:-1] - (x[:-2] + x[2:]) / 2).mean() / max(1e-6, x.mean())
        rep = int((x < 0.08).sum())
        print(f"  {s['name']:20s} motion {x.mean():6.2f}  jitter {jit:5.2f}  repeated {rep}")
