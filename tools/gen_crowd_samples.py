#!/usr/bin/env python3
"""The coliseum's crowd from real stadium recordings (the user's, in tools/src/crowd, gitignored), over the
synthesized ones (gen_crowd.py), which stay for what the recordings do not have (boos, gasps, the swoon,
the chant, claps):

  crowd/ambience_rec_a..c.ogg  the bed (client/CrowdSounds.Bed): 25-60 s of stadium ambience each, cut into
                               seamless loops (the end crossfaded into the start), stereo, -17 LUFS
  crowd/roar_rec_a..c.ogg      ROAR: the goal roar and the two biggest bursts of the cheering, -12 LUFS
and points crowd.ambience / crowd.roar in sounds.json at them (several sounds: one is picked each time).

  python3.11 tools/gen_crowd_samples.py      (after gen_crowd.py; needs ffmpeg, numpy, soundfile)
"""
import json
import os
import re
import subprocess
import tempfile

import numpy as np
import soundfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/papon')
SRC = os.path.join(ROOT, 'tools/src/crowd')
SR = 44100

# (out, source, start s, length s)
BEDS = [('ambience_rec_a', 'ambience3.mp3', 30, 60), ('ambience_rec_b', 'ambience4.mp3', 60, 60),
        ('ambience_rec_c', 'ambience_large.mp3', 2, 24)]
# (out, source, start s, length s, fade-out s)
ROARS = [('roar_rec_a', 'goal_roar.mp3', 0.55, 9.0, 3.0), ('roar_rec_b', 'cheering.mp3', 0.8, 8.0, 3.5),
         ('roar_rec_c', 'cheering.mp3', 16.8, 8.0, 3.0)]


def read(name, start, length):
    raw = subprocess.run(['ffmpeg', '-loglevel', 'error', '-ss', str(start), '-t', str(length), '-i', os.path.join(SRC, name),
                          '-ac', '2', '-ar', str(SR), '-f', 'f32le', 'pipe:1'], capture_output=True, check=True).stdout
    return np.frombuffer(raw, dtype=np.float32).reshape(-1, 2).copy()


def lufs(y):
    with tempfile.NamedTemporaryFile(suffix='.wav') as f:
        soundfile.write(f.name, y, SR, subtype='FLOAT')
        r = subprocess.run(['ffmpeg', '-hide_banner', '-nostats', '-i', f.name, '-af', 'ebur128', '-f', 'null', '-'],
                           capture_output=True, text=True).stderr
    return float(re.findall(r'I:\s+(-?[\d.]+) LUFS', r)[-1])


def level(y, target):
    y = y * 10 ** ((target - lufs(y)) / 20)
    peak = np.abs(y).max()
    return y * (0.95 / peak) if peak > 0.95 else y


def loop(y, xf):
    """The last `xf` seconds crossfaded (equal power) into the first: played round, it never jumps."""
    n = int(xf * SR)
    body, tail = y[:-n].copy(), y[-n:]
    t = np.linspace(0, np.pi / 2, n)[:, None]
    body[:n] = body[:n] * np.sin(t) + tail * np.cos(t)
    return body


def save(name, y):
    out = os.path.join(ASSETS, 'sounds/crowd', name + '.ogg')
    soundfile.write(out, y, SR, format='OGG', subtype='VORBIS')
    return out


def main():
    for name, src, start, length in BEDS:
        print(save(name, level(loop(read(src, start, length + 3), 3.0), -17)))
    for name, src, start, length, fade in ROARS:
        y = read(src, start, length)
        n, k = len(y), int(fade * SR)
        y[:int(0.02 * SR)] *= np.linspace(0, 1, int(0.02 * SR))[:, None]
        y[n - k:] *= (np.linspace(1, 0, k) ** 1.5)[:, None]
        print(save(name, level(y, -12)))
    path = os.path.join(ASSETS, 'sounds.json')
    sounds = json.load(open(path, encoding='utf-8'))
    sounds['crowd.ambience'] = {'sounds': [{'name': 'papon:crowd/' + b[0], 'stream': True} for b in BEDS]}
    sounds['crowd.roar'] = {'sounds': [{'name': 'papon:crowd/' + r[0]} for r in ROARS]}
    with open(path, 'w', newline='\n', encoding='utf-8') as f:
        json.dump(sounds, f, indent=2, ensure_ascii=False)
        f.write('\n')


if __name__ == '__main__':
    main()
