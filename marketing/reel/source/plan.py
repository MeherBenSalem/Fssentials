"""Single source of truth for the Donut Essentials reel: beat grid, voice placement, shots, time maps and overlay cues.

120 BPM, 78 beats (39 s). The hook is the player hub already open on frame one, with "ONE PLUGIN. EVERYTHING." over
it; the beat drops on the first cut. Every cut sits on a beat and every feature gets at least four beats (2 s) on
screen. Gameplay never plays slower than real time, and each voice line gets a slot that fits its real length
(checked against the picked takes in vo_pick/). The reel ends on the hub opening again and cross-fades into the
hook's first frame, so it loops without a seam. Source event times (game seconds inside each capture, measured from
the footage) come from events.json. Writes edl.json, cues.json and timeline_plan.json.
"""
import json
import os

R = os.environ.get('REEL_DIR', os.path.expanduser('~/.cache/donutessentials-smoke/reel'))
E = f'{R}/edit'
BPM = 120.0
BEAT = 60.0 / BPM
EV = json.load(open(f'{E}/events.json'))


def b(k):
    return round(k * BEAT, 4)


# ------------------------------------------------------------------ shot grid (beats) and voice slots
GRID = [('hook', 0, 7), ('homes', 7, 14), ('tpa', 14, 19), ('warps', 19, 23), ('kits', 23, 29), ('economy', 29, 34),
        ('chat', 34, 41), ('combat', 41, 46), ('moderation', 46, 53), ('rules', 53, 59), ('wide', 59, 65), ('title', 65, 73),
        ('loopend', 73, 78)]
G = {n: (b(a), b(z)) for n, a, z in GRID}
DURATION = b(GRID[-1][2])
DROP = G['homes'][0]
# (line index, start): each line starts just after its shot's cut
ORDER = ['hook', 'homes', 'tpa', 'warps', 'kits', 'economy', 'chat', 'combat', 'moderation', 'rules', 'wide', 'title', 'loopend']
VO = [(i, G[name][0] + (0.12 if i == 0 else 0.1)) for i, name in enumerate(ORDER)]

shots = []


def shot(name, src, a, z, mp, cam, interp='linear', **kw):
    s = {'name': name, 'src': src, 'start': round(a, 4), 'end': round(z, 4), 'map': mp, 'interp': interp, 'cam': cam}
    s.update(kw)
    shots.append(s)


def span(a, z, g0, g1):
    """Plays source game time g0..g1 over the shot; never slower than real time."""
    assert g1 - g0 >= (z - a) - 1e-6, f'gameplay never plays slower than real time ({g0}..{g1} over {z - a})'
    return [[0.0, g0], [round(z - a, 4), g1]]


def drift(a, z, s0, s1, cx0=540, cy0=960, cx1=None, cy1=None):
    return [[a, s0, cx0, cy0], [z, s1, cx1 if cx1 is not None else cx0, cy1 if cy1 is not None else cy0]]


def local(mp, game_t):
    """Seconds into a shot at which its source reaches a game time."""
    for (u0, v0), (u1, v1) in zip(mp, mp[1:]):
        if v0 <= game_t <= v1:
            return round(u0 + (game_t - v0) * (u1 - u0) / (v1 - v0), 4)
    return 0.0 if game_t < mp[0][1] else mp[-1][0]


for name in ORDER:
    a, z = G[name]
    e = EV[name]
    g0, g1 = e['from'], e['to']
    if 'map' in e:  # a piecewise map (output seconds into the shot -> game seconds)
        mp = e['map']
        for (u0, v0), (u1, v1) in zip(mp, mp[1:]):
            assert v1 - v0 >= (u1 - u0) - 1e-6, f'{name}: slower than real time'
        mp = [[round(u * (z - a) / mp[-1][0], 4), v] for u, v in mp]
        if e.get('interp') == 'smooth':  # the smoothed curve must not dip below real time either
            from scipy.interpolate import PchipInterpolator
            curve = PchipInterpolator([u for u, _ in mp], [v for _, v in mp]).derivative()
            slowest = min(float(curve(k * (z - a) / 400)) for k in range(401))
            assert slowest >= 1 - 1e-3, f'{name}: the smoothed map plays at {slowest:.2f}x'
    else:
        mp = span(a, z, g0, g1)
    c = e['cam']
    extra = {'fade_to_hook': 0.25} if name == 'loopend' else {}
    if 'callouts' in e:  # zoomed cards of the chat or action bar, timed in game seconds
        extra['callouts'] = [{'t0': local(mp, k['from']), 't1': local(mp, k['to']) if k['to'] is not None else round(z - a, 4),
                              'box': k['box'], 'y': k.get('y', 1000), 'scale': k['scale']} for k in e['callouts']]
    shot(name, name, a, z, mp, drift(a, z, *c), interp=e.get('interp', 'linear'), punch=e.get('punch', 0.04), **extra)

# ------------------------------------------------------------------ overlay cues
labels = [
    dict(t0=G['homes'][0], t1=G['homes'][1], name='HOMES', sub='OVERWORLD · NETHER · END', color='#7ee081'),
    dict(t0=G['tpa'][0], t1=G['tpa'][1], name='TELEPORTS', sub='CLICK TO ACCEPT', color='#6bc6ff'),
    dict(t0=G['warps'][0], t1=G['warps'][1], name='WARPS', sub='ICONS · VISITS', color='#ffc24b'),
    dict(t0=G['kits'][0], t1=G['kits'][1], name='KITS', sub='EVERY ENCHANT KEPT', color='#c792ea'),
    dict(t0=G['economy'][0], t1=G['economy'][1], name='ECONOMY', sub='PAY · BALTOP · VAULT', color='#ffc24b'),
    dict(t0=G['chat'][0], t1=G['chat'][1], name='CHAT', sub='RANKS · [ITEM] · @MENTIONS', color='#ff6fae'),
    dict(t0=G['combat'][0], t1=G['combat'][1], name='COMBAT TAG', sub='NO ESCAPING A FIGHT', color='#ff6b6b'),
    dict(t0=G['moderation'][0], t1=G['moderation'][0] + 1.9, name='MODERATION', sub='WARN · MUTE · REPORT', color='#ff6b6b'),
    dict(t0=G['rules'][0], t1=G['rules'][1], name='RULES', sub='ACCEPT TO PLAY', color='#7ee081', top=790),  # under the rules text
]
W0, T0, Q0 = G['wide'][0], G['title'][0], G['loopend'][0]
cap_top = {'kits': 1420, 'economy': 1420, 'moderation': 1420}
cues = {
    'duration': DURATION, 'beat': BEAT, 'drop': DROP, 'labels': labels,
    'hook': [0.0, G['hook'][1] - 0.05],
    'loaders': {'after': W0, 'bg': [W0, T0 - 0.05], 'end': T0 - 0.05},
    'title': [T0, Q0 - 0.05],
    'cta': [Q0 + 0.1, DURATION - 0.35],
    'flashes': [[DROP, 0.3], [T0, 0.45]],
    'capTop': [{'t0': G[n][0], 't1': G[n][1], 'top': cap_top.get(n, 1300)} for n in ORDER],
}


def at(name, game_t):
    """Reel time of a game time inside a shot's source."""
    s = next(sh for sh in shots if sh['name'] == name)
    for (u0, v0), (u1, v1) in zip(s['map'], s['map'][1:]):
        if v0 <= game_t <= v1:
            return round(s['start'] + u0 + (game_t - v0) * (u1 - u0) / (v1 - v0), 3)
    return None


events = {'drop': DROP, 'title': T0, 'wide': W0, 'loopend': Q0, 'cuts': [s['start'] for s in shots[1:]],
          'whooshes': [[W0, True], [Q0, False]]}
for name in ORDER:
    for key, val in EV[name].get('events', {}).items():
        times = [at(name, v) for v in (val if isinstance(val, list) else [val])]
        events[f'{name}.{key}'] = [t for t in times if t is not None]
cues['events'] = events
json.dump({'fps': 30, 'size': [1080, 1920], 'shots': shots}, open(f'{E}/edl.json', 'w'), indent=1)
json.dump(cues, open(f'{E}/cues.json', 'w'), indent=1)
lines = json.load(open(f'{E}/lines.json'))
json.dump({'duration': DURATION, 'lines': [{'i': i, 'start': round(s, 3), 'cap': lines[i]['cap']} for i, s in VO]},
          open(f'{E}/timeline_plan.json', 'w'), indent=1)
for s in shots:
    print(f"{s['start']:6.2f}-{s['end']:6.2f} {s['name']:12s} map {s['map'][0][1]:.2f}->{s['map'][-1][1]:.2f}")
print({k: v for k, v in events.items() if '.' in k})

# fit check: every picked take must end before the next line starts (and the last before the reel ends)
import soundfile as sf  # noqa: E402
starts = [st for _, st in VO] + [DURATION - 0.3]
for (i, st), nxt in zip(VO, starts[1:]):
    f = f'{E}/vo_pick/s{i + 1:02d}.wav'
    if os.path.exists(f):
        d = sf.info(f).duration
        flag = 'OK ' if st + d + 0.05 <= nxt else 'OVER'
        print(f'{flag} line {i + 1:2d} {st:6.2f}-{st + d:6.2f} (next {nxt:6.2f})')
