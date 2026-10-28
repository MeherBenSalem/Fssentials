"""Places each voice line at its planned start (timeline_plan.json) and writes vo.wav + timeline.json."""
import os
import json
import numpy as np
import soundfile as sf

E = os.path.join(os.environ.get('REEL_DIR', os.path.expanduser('~/.cache/donutessentials-smoke/reel')), 'edit')
P = json.load(open(f'{E}/timeline_plan.json'))
sr = 24000
out = np.zeros(int(round(P['duration'] * sr)) + sr, dtype=np.float32)
timeline, prev_end = [], 0.0
for line in P['lines']:
    a, r = sf.read(f"{E}/vo_pick/s{line['i'] + 1:02d}.wav", dtype='float32')
    assert r == sr
    st = line['start']
    assert st >= prev_end + 0.03, f"line {line['i']} starts {st} before previous ends {prev_end}"
    i = int(round(st * sr))
    out[i:i + len(a)] += a
    prev_end = st + len(a) / sr
    timeline.append({'i': line['i'], 'start': st, 'end': round(prev_end, 3), 'cap': line['cap']})
assert prev_end < P['duration'] - 0.3
out = out[:int(round(P['duration'] * sr))]
out = out / max(1e-6, np.abs(out).max()) * 0.89
sf.write(f'{E}/vo.wav', out, sr)
json.dump({'duration': P['duration'], 'lines': timeline}, open(f'{E}/timeline.json', 'w'), indent=1)
for l in timeline:
    print(f"{l['start']:6.2f}-{l['end']:6.2f} {l['cap']}")
