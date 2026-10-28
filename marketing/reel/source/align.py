"""Word timestamps of the voice track from faster-whisper (local), for the word-timed captions."""
import os
import json
import soundfile as sf
from faster_whisper import WhisperModel
from scipy.signal import resample_poly

E = os.path.join(os.environ.get('REEL_DIR', os.path.expanduser('~/.cache/donutessentials-smoke/reel')), 'edit')
audio, sr = sf.read(f'{E}/vo.wav', dtype='float32')
audio = resample_poly(audio, 2, 3).astype('float32')  # 24 kHz -> 16 kHz
m = WhisperModel('small.en', device='cpu', compute_type='int8')
segs, info = m.transcribe(audio, word_timestamps=True, beam_size=5, vad_filter=False)
words = []
for s in segs:
    for w in s.words:
        words.append({'w': w.word.strip(), 'start': round(w.start, 3), 'end': round(w.end, 3), 'p': round(w.probability, 2)})
json.dump(words, open(f'{E}/words.json', 'w'), indent=1)
print(' '.join(w['w'] for w in words))
print('low confidence:', [(w['w'], w['p']) for w in words if w['p'] < 0.6])
