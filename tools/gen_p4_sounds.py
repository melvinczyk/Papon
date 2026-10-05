#!/usr/bin/env python3
"""Phase four's own sounds (the Final Guardian's awakening), synthesized:

  boss/pant      his panting on his back, the scar torn open: a big man's breath out (rough, voiced underneath) and in
  boss/scratch   the band stopping dead as he is thrown flat: a record scratch

Writes sounds/boss/pant.ogg, sounds/boss/scratch.ogg and their sounds.json entries (boss.pant, boss.scratch).
  python3.11 tools/gen_p4_sounds.py
"""
import json
from geo_util import read_sounds, write_sounds
import os

import numpy as np
import soundfile as sf
import scipy.signal as ss

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/papon')
SR = 44100
rng = np.random.default_rng(4)


def env(n, a, r):
    e = np.ones(n)
    na, nr = int(a * n), int(r * n)
    e[:na] = np.linspace(0, 1, na) ** 1.5
    e[n - nr:] = np.linspace(1, 0, nr) ** 2
    return e


def breath(sec, lo, hi, voiced, amp):
    n = int(sec * SR)
    noise = rng.normal(0, 1, n)
    sos = ss.butter(2, [lo, hi], btype='band', fs=SR, output='sos')
    b = ss.sosfilt(sos, noise)
    # two throat formants
    for f, q in ((700, 4), (1250, 5)):
        bb, aa = ss.iirpeak(f, q, fs=SR)
        b = b + 0.6 * ss.lfilter(bb, aa, noise)
    t = np.arange(n) / SR
    if voiced:
        f0 = 92 * (1 - 0.15 * t / sec)
        ph = np.cumsum(2 * np.pi * f0 / SR)
        v = np.sign(np.sin(ph)) * 0.25 + np.sin(ph * 2) * 0.1
        v = ss.sosfilt(ss.butter(2, 900, fs=SR, output='sos'), v)
        b = b / (np.abs(b).max() + 1e-9) + voiced * v
    b = b / (np.abs(b).max() + 1e-9)
    return b * env(n, 0.25, 0.55) * amp


def pant():
    out = np.concatenate([breath(0.42, 300, 3200, 0.55, 1.0), np.zeros(int(0.04 * SR)),
                          breath(0.34, 600, 4200, 0.0, 0.55), np.zeros(int(0.08 * SR))])
    return out * 0.9


def scratch():
    n = int(0.75 * SR)
    t = np.arange(n) / SR
    # the record dragged back and forth under a finger: a mid-range buzz swept down and up, then dropped
    f = 900 + 700 * np.sin(2 * np.pi * 3.2 * t) * np.exp(-t * 1.5) - 500 * t
    ph = np.cumsum(2 * np.pi * np.maximum(80, f) / SR)
    tone = ss.sawtooth(ph) * 0.6 + rng.normal(0, 0.35, n)
    tone = ss.sosfilt(ss.butter(2, [200, 5000], btype='band', fs=SR, output='sos'), tone)
    e = np.exp(-t * 2.2) * (1 - np.exp(-t * 80))
    out = tone * e
    return out / (np.abs(out).max() + 1e-9) * 0.9


def write(name, y):
    path = os.path.join(ASSETS, 'sounds/boss/%s.ogg' % name)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    sf.write(path, y.astype(np.float32), SR, format='OGG', subtype='VORBIS')
    return path


if __name__ == '__main__':
    write('pant', pant())
    write('scratch', scratch())
    sounds = read_sounds()
    for k in ('pant', 'scratch'):
        sounds['boss.' + k] = {'sounds': [{'name': 'papon:boss/' + k}], 'subtitle': 'subtitles.papon.boss.' + k}
    write_sounds(sounds)
    print('ok')
