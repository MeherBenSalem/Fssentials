"""Picks the best take of every voiceover line: the right words first, then the most energetic read.

usage: python pick_takes.py <script.json> <takes_dir> <out_dir> [--ref voice_ref.wav]

script.json is [["s01", "text"], ...], the format of the channel voice runner (Chatterbox, kept with the channel's
reference clip outside this repository), which writes <takes_dir>/<id>_t<take>.wav. Generate 4 takes of every line (seeds follow the
runner's 1000 x take + line number), run this, and generate takes 5-8 only for lines that come back with no good take.

A take counts only if faster-whisper hears exactly the script (numbers may be written out; see NUMBERS and JOINED
below), it has no long pause or trailing noise, and its median pitch is within ~4 semitones of the reference
voice (a take far off it has drifted). Among those, the score is speaking pace (weighted x1.5), pitch movement and
punch (loudness range), z-scored within the line: the owner wants an energetic read, not a sleepy one. Winners are
trimmed of silence, never sped up, and written to <out_dir>/<id>.wav, with picks.json beside them.

Needs numpy, soundfile, scipy and faster-whisper (any venv; the Chatterbox venv works).
"""
import argparse
import glob
import json
import os
import re

import numpy as np
import soundfile as sf
from faster_whisper import WhisperModel
from scipy.signal import resample_poly

ap = argparse.ArgumentParser()
ap.add_argument("script")
ap.add_argument("takes_dir")
ap.add_argument("out_dir")
ap.add_argument("--ref", default=os.environ.get("VOICE_REF", "voice_ref.wav"))
args = ap.parse_args()
os.makedirs(args.out_dir, exist_ok=True)
script = json.load(open(args.script))
model = WhisperModel("small.en", device="cpu", compute_type="int8")

NUMBERS = {}
JOINED = {}


def norm(s):
    s = " ".join(s.lower().split())
    for k, v in {**NUMBERS, **JOINED}.items():
        s = s.replace(k, v)
    return re.sub(r"[^a-z ]", " ", s).split()


def syllables(word):
    return max(1, len(re.findall(r"[aeiouy]+", word)))


def trim(a, sr):
    env = np.convolve(np.abs(a), np.ones(int(0.01 * sr)) / int(0.01 * sr), "same")
    on = np.where(env > 0.012)[0]
    return a[max(0, on[0] - int(0.03 * sr)): on[-1] + int(0.08 * sr)] if len(on) else a


def f0_track(a, sr):
    f0, hop, win = [], int(0.01 * sr), int(0.04 * sr)
    for i in range(0, len(a) - win, hop):
        fr = a[i:i + win] - a[i:i + win].mean()
        if np.sqrt((fr ** 2).mean()) < 0.02:
            continue
        ac = np.correlate(fr, fr, "full")[win - 1:]
        lo, hi = int(sr / 350), int(sr / 70)
        lag = lo + int(np.argmax(ac[lo:hi]))
        if ac[lag] > 0.35 * ac[0]:
            f0.append(sr / lag)
    return np.array(f0)


ref, rsr = sf.read(args.ref, dtype="float32")
REF_F0 = float(np.median(f0_track(ref, rsr)))
print(f"reference voice: median pitch {REF_F0:.0f} Hz")

picks = {}
for sid, text in script:
    want = norm(text)
    res = []
    for path in sorted(glob.glob(os.path.join(args.takes_dir, f"{sid}_t*.wav"))):
        take = int(re.search(r"_t(\d+)\.wav$", path).group(1))
        a, sr = sf.read(path, dtype="float32")
        a = trim(a, sr)
        segs, _ = model.transcribe(resample_poly(a, 16000, sr).astype(np.float32), word_timestamps=True, beam_size=5)
        words = [w for s in segs for w in s.words]
        heard = norm(" ".join(w.word for w in words))
        gaps = [b.start - a_.end for a_, b in zip(words, words[1:])]
        # a sentence break inside the line ("Plasma cutters! Pulse rifles!") may pause longer
        long_gap = max(gaps, default=0) > (0.65 if re.search(r"[.!?,] \w", text) else 0.45)
        tail = len(a) / sr - (words[-1].end if words else 0)
        speech = (words[-1].end - words[0].start) if words else len(a) / sr
        f0 = f0_track(a, sr)
        med = float(np.median(f0)) if len(f0) else 0.0
        env = np.sqrt(np.convolve(a ** 2, np.ones(int(0.05 * sr)) / int(0.05 * sr), "same"))
        voiced = env[env > 0.02]
        ok = heard == want and not long_gap and tail < 0.6 and med > 0 and abs(np.log2(med / REF_F0)) <= 0.3
        res.append({"take": take, "ok": bool(ok), "heard": " ".join(heard), "dur": round(len(a) / sr, 3),
                    "rate": sum(syllables(w) for w in want) / max(0.2, speech),
                    "pitch": float(np.std(12 * np.log2(f0 / med))) if len(f0) > 5 else 0.0,
                    "punch": float(np.percentile(20 * np.log10(voiced), 95) - np.percentile(20 * np.log10(voiced), 30)) if len(voiced) else 0.0,
                    "f0": round(med), "_audio": a, "_sr": sr})
    good = [r for r in res if r["ok"]]
    if not good:
        print(f"{sid}: NO GOOD TAKE, generate more ({', '.join(r['heard'] or '-' for r in res)})")
        picks[sid] = None
        continue
    score = np.zeros(len(good))
    for key, weight in (("rate", 1.5), ("pitch", 1.0), ("punch", 1.0)):
        v = np.array([r[key] for r in good], float)
        score += weight * (v - v.mean()) / (v.std() + 1e-6)
    best = good[int(np.argmax(score))]
    sf.write(os.path.join(args.out_dir, f"{sid}.wav"), best["_audio"], best["_sr"])
    picks[sid] = {k: (round(v, 2) if isinstance(v, float) else v) for k, v in best.items() if not k.startswith("_")}
    print(f"{sid}: take {best['take']} of {len(res)} ({len(good)} good), {best['dur']} s, "
          f"{best['rate']:.1f} syll/s, pitch spread {best['pitch']:.1f} st, {best['f0']} Hz")
json.dump(picks, open(os.path.join(args.out_dir, "picks.json"), "w"), indent=1)
