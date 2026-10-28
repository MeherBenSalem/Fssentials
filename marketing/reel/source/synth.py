"""Instruments and sound effects for the reel, synthesized from scratch (no samples, no licensing)."""
import numpy as np
from scipy.signal import butter, sosfilt

SR = 48000
rng = np.random.default_rng(7)


def t_axis(n):
    return np.arange(n) / SR


def lp(x, f, order=2):
    return sosfilt(butter(order, f, "low", fs=SR, output="sos"), x)


def hp(x, f, order=2):
    return sosfilt(butter(order, f, "high", fs=SR, output="sos"), x)


def bp(x, lo, hi, order=2):
    return sosfilt(butter(order, [lo, hi], "band", fs=SR, output="sos"), x)


def place(buf, sig, at, gain=1.0):
    i = int(round(at * SR))
    if i >= len(buf):
        return
    if i < 0:
        sig = sig[-i:]
        i = 0
    n = min(len(sig), len(buf) - i)
    buf[i:i + n] += sig[:n] * gain


def env_adsr(n, a=0.005, d=0.1, s=0.6, r=0.1):
    t = t_axis(n)
    dur = n / SR
    e = np.ones(n)
    e = np.where(t < a, t / max(a, 1e-6), e)
    e = np.where((t >= a) & (t < a + d), 1 - (1 - s) * (t - a) / max(d, 1e-6), e)
    e = np.where((t >= a + d) & (t < dur - r), s, e)
    e = np.where(t >= dur - r, s * np.clip((dur - t) / max(r, 1e-6), 0, 1), e)
    return e


def saw(freq, n, detune=0.0):
    t = t_axis(n)
    ph = (freq * (1 + detune)) * t
    return 2 * (ph - np.floor(ph + 0.5))


def note(m):
    return 440.0 * 2 ** ((m - 69) / 12)


# ---------------------------------------------------------------- instruments
def kick(gain=1.0):
    n = int(0.45 * SR)
    t = t_axis(n)
    f = 48 + 110 * np.exp(-t * 32)
    ph = 2 * np.pi * np.cumsum(f) / SR
    body = np.sin(ph) * np.exp(-t * 7.5)
    click = hp(rng.standard_normal(n), 2500) * np.exp(-t * 180) * 0.25
    return np.tanh((body + click) * 1.6) * gain


def clap(gain=1.0):
    n = int(0.3 * SR)
    t = t_axis(n)
    noise = bp(rng.standard_normal(n), 900, 5000)
    e = np.zeros(n)
    for k, off in enumerate([0, 0.011, 0.022]):
        e += np.where(t >= off, np.exp(-(t - off) * (90 if k < 2 else 22)), 0)
    return noise * e * 0.6 * gain


def hat(open_=False, gain=1.0):
    n = int((0.22 if open_ else 0.05) * SR)
    t = t_axis(n)
    x = hp(rng.standard_normal(n), 7000, 4)
    return x * np.exp(-t * (16 if open_ else 95)) * 0.32 * gain


def bass808(midi, dur, gain=1.0):
    n = int(dur * SR)
    t = t_axis(n)
    f = note(midi) * (1 + 0.6 * np.exp(-t * 40))
    ph = 2 * np.pi * np.cumsum(f) / SR
    x = np.sin(ph) + 0.12 * np.sin(2 * ph)
    return np.tanh(x * 1.8) * env_adsr(n, 0.004, 0.25, 0.75, 0.06) * 0.55 * gain


def pad(midis, dur, cutoff=1800, gain=1.0):
    n = int(dur * SR)
    x = np.zeros(n)
    for m in midis:
        for d in (-0.006, 0.0, 0.007):
            x += saw(note(m), n, d)
    x = lp(x / (3 * len(midis)), cutoff, 2)
    return x * env_adsr(n, 0.25, 0.3, 0.8, 0.35) * gain


def pluck(midi, dur=0.22, gain=1.0):
    n = int(dur * SR)
    t = t_axis(n)
    x = saw(note(midi), n) * 0.6 + saw(note(midi + 12), n, 0.003) * 0.3
    cutoff_env = 400 + 5200 * np.exp(-t * 18)
    # cheap time-varying low-pass: blend two filters by the envelope
    lo, hi = lp(x, 600), lp(x, 5600)
    k = (cutoff_env - 400) / 5200
    return (lo * (1 - k) + hi * k) * np.exp(-t * 11) * 0.42 * gain


def riser(dur, gain=1.0, f0=300, f1=6000):
    n = int(dur * SR)
    t = t_axis(n)
    noise = rng.standard_normal(n)
    out = np.zeros(n)
    steps = 24
    for s in range(steps):
        a, b = int(s * n / steps), int((s + 1) * n / steps)
        fc = f0 * (f1 / f0) ** (s / (steps - 1))
        out[a:b] = bp(noise[a:b], fc * 0.7, min(fc * 1.4, SR / 2 - 100))
    tone_f = 110 * (8 ** (t / dur))
    tone = np.sin(2 * np.pi * np.cumsum(tone_f) / SR) * 0.15
    e = (t / dur) ** 2.2
    return (out * 0.5 + tone) * e * gain


def impact(gain=1.0, length=2.2):
    n = int(length * SR)
    t = t_axis(n)
    f = 38 + 70 * np.exp(-t * 9)
    sub = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 2.2)
    crack = lp(rng.standard_normal(n), 3500) * np.exp(-t * 14) * 0.7
    tail = lp(rng.standard_normal(n), 900) * np.exp(-t * 2.8) * 0.25
    return np.tanh((sub * 1.3 + crack + tail) * 1.4) * gain


def whoosh(dur=0.35, gain=1.0, up=True):
    n = int(dur * SR)
    t = t_axis(n)
    noise = rng.standard_normal(n)
    out = np.zeros(n)
    steps = 12
    for s in range(steps):
        a, b = int(s * n / steps), int((s + 1) * n / steps)
        frac = s / (steps - 1)
        fc = 400 * (12 ** (frac if up else 1 - frac))
        out[a:b] = bp(noise[a:b], fc * 0.6, min(fc * 1.6, SR / 2 - 100))
    e = np.sin(np.pi * np.clip(t / dur, 0, 1)) ** 1.5
    return out * e * 0.35 * gain


def blip(freq=1600, dur=0.07, gain=1.0):
    n = int(dur * SR)
    t = t_axis(n)
    return np.sin(2 * np.pi * freq * t) * np.exp(-t * 60) * 0.25 * gain


def chime(base=72, gain=1.0):
    n = int(1.2 * SR)
    out = np.zeros(n)
    for k, m in enumerate([base, base + 4, base + 7, base + 12]):
        seg = np.zeros(n)
        i = int(k * 0.06 * SR)
        tt = t_axis(n - i)
        seg[i:] = (np.sin(2 * np.pi * note(m) * tt) + 0.3 * np.sin(2 * np.pi * note(m) * 2.01 * tt)) * np.exp(-tt * 3.2)
        out += seg
    return out * 0.16 * gain


def shimmer(dur=1.0, gain=1.0):
    n = int(dur * SR)
    t = t_axis(n)
    out = np.zeros(n)
    for m in (84, 88, 91, 96):
        out += np.sin(2 * np.pi * note(m) * t * (1 + 0.002 * np.sin(2 * np.pi * 6 * t)))
    return out * np.exp(-t * 2.5) * (1 - np.exp(-t * 30)) * 0.06 * gain


def thunder(gain=1.0):
    n = int(1.4 * SR)
    t = t_axis(n)
    crack = hp(rng.standard_normal(n), 1500) * np.exp(-t * 30) * 0.8
    rumble = lp(rng.standard_normal(n), 220, 4) * np.exp(-t * 2.4) * 2.2
    return np.tanh((crack + rumble) * 1.2) * 0.5 * gain


def swirl(dur=0.9, gain=1.0):
    n = int(dur * SR)
    t = t_axis(n)
    noise = rng.standard_normal(n)
    lfo = 0.5 + 0.5 * np.sin(2 * np.pi * 7 * t)
    lo, hi = bp(noise, 300, 900), bp(noise, 900, 2600)
    return (lo * lfo + hi * (1 - lfo)) * np.sin(np.pi * t / dur) * 0.32 * gain




# ---------------------------------------------------------------- ability sounds (one per montage shot)
def fireburst(gain=1.0):
    """Ignition: a low whump that opens into a crackling roar."""
    n = int(1.1 * SR)
    t = t_axis(n)
    whump = np.sin(2 * np.pi * np.cumsum(70 + 90 * np.exp(-t * 18)) / SR) * np.exp(-t * 9) * 0.9
    roar = bp(rng.standard_normal(n), 250, 2600) * (1 - np.exp(-t * 40)) * np.exp(-t * 3.2) * 0.8
    crackle = np.zeros(n)
    for _ in range(70):
        i = int(rng.uniform(0.02, 0.9) * n)
        k = int(0.004 * SR)
        crackle[i:i + k] += hp(rng.standard_normal(k), 2500) * rng.uniform(0.2, 0.7)
    crackle *= np.exp(-t * 2.5)
    return np.tanh((whump + roar + crackle) * 1.3) * 0.55 * gain


def ice(gain=1.0):
    """Frost snap: a glassy crack and a bed of high crystalline partials."""
    n = int(1.2 * SR)
    t = t_axis(n)
    crack = hp(rng.standard_normal(n), 3000) * np.exp(-t * 60) * 0.9
    out = np.zeros(n)
    for f in (2350, 3120, 4180, 5270, 6650):
        out += np.sin(2 * np.pi * f * t + rng.uniform(0, 6)) * np.exp(-t * rng.uniform(3, 6))
    shimmer_ = out / 5 * (1 - np.exp(-t * 80)) * 0.5
    hiss = bp(rng.standard_normal(n), 5000, 11000) * np.exp(-t * 5) * 0.18
    return (crack + shimmer_ + hiss) * 0.6 * gain


def radiant(gain=1.0):
    """Holy nova: a bright major chord swell with a soft airy burst."""
    n = int(1.3 * SR)
    t = t_axis(n)
    out = np.zeros(n)
    for m in (76, 80, 83, 88, 92):
        out += np.sin(2 * np.pi * note(m) * t) * (1 + 0.3 * np.sin(2 * np.pi * 5 * t))
    env = (1 - np.exp(-t * 25)) * np.exp(-t * 2.4)
    air = bp(rng.standard_normal(n), 3000, 9000) * np.exp(-t * 7) * 0.35
    return (out / 5 * env * 0.6 + air) * 0.6 * gain


def void(gain=1.0):
    """Eclipse: a reversed swell sucked into a deep sub drop."""
    n = int(1.3 * SR)
    t = t_axis(n)
    pre = int(0.12 * SR)
    swell = lp(rng.standard_normal(pre), 1800) * np.linspace(0, 1, pre) ** 2 * 0.6
    f = 30 + 80 * np.exp(-t * 6)
    sub = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 2.2) * 1.2
    dark = lp(rng.standard_normal(n), 400, 4) * np.exp(-t * 3) * 1.4
    body = np.tanh((sub + dark) * 1.5) * 0.6
    out = np.zeros(n + pre)
    out[:pre] += swell
    out[pre:] += body
    return out * gain


def earth(gain=1.0):
    """Tremor: a heavy thud with gravel and a rolling low rumble."""
    n = int(1.3 * SR)
    t = t_axis(n)
    thud = np.sin(2 * np.pi * np.cumsum(42 + 60 * np.exp(-t * 14)) / SR) * np.exp(-t * 4) * 1.3
    rumble = lp(rng.standard_normal(n), 160, 4) * (0.6 + 0.4 * np.sin(2 * np.pi * 9 * t)) * np.exp(-t * 2.2) * 2.4
    gravel = bp(rng.standard_normal(n), 600, 3000) * np.exp(-t * 9) * 0.35
    return np.tanh((thud + rumble + gravel) * 1.2) * 0.55 * gain


def zap(gain=1.0):
    """Chain lightning: three fast electric arcs."""
    n = int(0.6 * SR)
    t = t_axis(n)
    out = np.zeros(n)
    for k, off in enumerate((0.0, 0.09, 0.19)):
        i = int(off * SR)
        m = n - i
        tt = t_axis(m)
        buzz = np.sign(np.sin(2 * np.pi * (180 + 60 * k) * tt)) * 0.3 + hp(rng.standard_normal(m), 2500) * 0.7
        out[i:] += buzz * np.exp(-tt * 22) * (0.9 - 0.2 * k)
    return np.tanh(out * 1.2) * 0.38 * gain


# ---------------------------------------------------------------- machine sounds (Core Utilities reel)
def drill(dur=2.5, gain=1.0):
    """Quarry: a low grinding motor with a gritty, pulsing bite."""
    n = int(dur * SR)
    t = t_axis(n)
    motor = np.sin(2 * np.pi * 55 * t + 0.6 * np.sin(2 * np.pi * 11 * t)) * 0.5
    grit = bp(rng.standard_normal(n), 300, 2400) * (0.55 + 0.45 * np.sin(2 * np.pi * 9 * t) ** 2)
    e = np.minimum(1, t / 0.05) * np.minimum(1, (dur - t) / 0.3)
    return np.tanh((motor + grit * 0.8) * 1.4) * e * 0.4 * gain


def powerup(gain=1.0):
    """Machines switching on: a rising sine sweep into a steady electric hum."""
    n = int(1.6 * SR)
    t = t_axis(n)
    f = 80 * (1 + 7 * np.clip(t / 0.35, 0, 1))
    sweep = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-np.maximum(0, t - 0.35) * 2.0)
    hum = (np.sin(2 * np.pi * 120 * t) + 0.4 * np.sin(2 * np.pi * 240 * t)) * np.clip((t - 0.25) / 0.1, 0, 1) * np.exp(-t * 1.6)
    zap_ = hp(rng.standard_normal(n), 3000) * np.exp(-np.abs(t - 0.35) * 40) * 0.5
    return (sweep * 0.5 + hum * 0.35 + zap_) * 0.6 * gain


def clank(gain=1.0, pitch=1.0):
    """Metal hit with a short ring."""
    n = int(0.6 * SR)
    t = t_axis(n)
    out = np.zeros(n)
    for f in (410, 1130, 2210, 3570):
        out += np.sin(2 * np.pi * f * pitch * t + rng.uniform(0, 6)) * np.exp(-t * rng.uniform(9, 16))
    knock = lp(rng.standard_normal(n), 1800) * np.exp(-t * 60)
    return (out * 0.18 + knock * 0.6) * gain


def hiss(dur=0.5, gain=1.0):
    """Hydraulic press release."""
    n = int(dur * SR)
    t = t_axis(n)
    return bp(rng.standard_normal(n), 2500, 9000) * np.exp(-t * 5) * (1 - np.exp(-t * 60)) * 0.3 * gain


def crunch(dur=1.0, gain=1.0):
    """Crusher: stone grinding in bursts."""
    n = int(dur * SR)
    t = t_axis(n)
    bursts = (np.sin(2 * np.pi * 6 * t) > 0.3).astype(float)
    x = bp(rng.standard_normal(n), 200, 3000) * (0.4 + 0.6 * lp(bursts, 30))
    return np.tanh(x * 1.6) * np.minimum(1, (dur - t) / 0.2) * 0.35 * gain


def bubble(gain=1.0):
    """Lava: a few low, round pops."""
    n = int(1.0 * SR)
    out = np.zeros(n)
    for k in range(5):
        i = int(rng.uniform(0, 0.75) * n)
        m = int(0.12 * SR)
        tt = t_axis(m)
        f = rng.uniform(110, 200) * (1 + 1.5 * tt / 0.12)
        out[i:i + m] += np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-tt * 30)
    return lp(out, 900) * 0.6 * gain


def swish(gain=1.0):
    """Harvest: a short airy blade swipe."""
    return whoosh(0.22, 0.6 * gain, up=False)


def whirr(dur=1.2, gain=1.0):
    """Grinder blades spinning up."""
    n = int(dur * SR)
    t = t_axis(n)
    f = 180 + 260 * np.clip(t / 0.4, 0, 1)
    x = np.sign(np.sin(2 * np.pi * np.cumsum(f) / SR)) * 0.25 + bp(rng.standard_normal(n), 800, 4000) * 0.4
    return lp(x, 3500) * np.minimum(1, t / 0.05) * np.exp(-t * 1.5) * 0.4 * gain


def tick(gain=1.0):
    """UI click."""
    n = int(0.05 * SR)
    t = t_axis(n)
    return (np.sin(2 * np.pi * 2400 * t) * np.exp(-t * 120) + hp(rng.standard_normal(n), 4000) * np.exp(-t * 300) * 0.4) * 0.3 * gain
