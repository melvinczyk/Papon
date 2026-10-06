#!/usr/bin/env python3
"""Ojos de Papa's own sounds (2026-10-06, the user: "make the laser louder"; vanilla's beacon hum was lost under the fight):

  boss/laser_charge   his eyes charging (1.0 s, P3_LASER 0.7-1.7 s): a whine climbing to a shriek over a growing crackle
  boss/laser_fire     the beams firing: a sub boom, a white-noise blast and a zap falling out of it
  boss/laser_burn     the beams burning (3.1 s, the whole firing window): a searing, wavering roar with sizzle on top,
                      swelling in and cut off clean at the end
  boss/laser_end      the beams going out: a power-down whine and a hiss

Mastered loud (the fight's effects duck under his voice; client/SoundMix leaves "boss.laser_*" alone). Writes the .ogg files
and their sounds.json entries.
  python tools/gen_laser_sounds.py
"""
import os

import numpy as np
import scipy.signal as ss
import soundfile as sf

from geo_util import read_sounds, write_sounds

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/papon')
SR = 44100
rng = np.random.default_rng(11)


def t_of(sec):
    return np.arange(int(sec * SR)) / SR


def band(x, lo, hi, order=2):
    return ss.sosfilt(ss.butter(order, [lo, hi], btype='band', fs=SR, output='sos'), x)


def low(x, f, order=2):
    return ss.sosfilt(ss.butter(order, f, fs=SR, output='sos'), x)


def high(x, f, order=2):
    return ss.sosfilt(ss.butter(order, f, btype='high', fs=SR, output='sos'), x)


def saw(ph):
    return 2 * ((ph / (2 * np.pi)) % 1) - 1


def crackle(n, rate, amp=1.0):
    """Electric crackle: sparse clicks, each a short decaying burst of bright noise."""
    out = np.zeros(n)
    k = int(n / SR * rate)
    for i in rng.integers(0, n - 800, k):
        L = int(rng.integers(80, 600))
        out[i:i + L] += rng.normal(0, 1, L) * np.exp(-np.arange(L) / (L / 4)) * rng.uniform(0.3, 1.0)
    return high(out, 1800) * amp


def master(x, peak=0.8, drive=2.2):
    """Loud: a soft clip (tanh) after a drive, then to the peak."""
    x = x / (np.abs(x).max() + 1e-9)
    x = np.tanh(x * drive) / np.tanh(drive)
    return x / (np.abs(x).max() + 1e-9) * peak


def fade(x, a=0.005, r=0.02):
    n = len(x)
    e = np.ones(n)
    na, nr = int(a * SR), int(r * SR)
    if na:
        e[:na] = np.linspace(0, 1, na)
    if nr:
        e[n - nr:] = np.linspace(1, 0, nr)
    return x * e


def charge():
    sec = 1.0
    t = t_of(sec)
    k = t / sec
    f = 180 * (1 + 9 * k ** 1.6)                         # 180 Hz up to 1.8 kHz
    ph = np.cumsum(2 * np.pi * f / SR)
    whine = np.sin(ph) + 0.4 * np.sin(2 * ph) + 0.25 * saw(3 * ph)
    whine *= (0.3 + 0.7 * k ** 1.2) * (1 + 0.25 * np.sin(2 * np.pi * (6 + 30 * k) * t))     # a tremolo speeding up
    hum = saw(np.cumsum(2 * np.pi * (55 + 20 * k) / SR)) * 0.5
    hum = low(hum, 600) * (0.4 + 0.6 * k)
    cr = crackle(len(t), 40 + 160 * k.mean(), 0.8) * (0.2 + k)
    x = whine * 0.8 + hum + cr
    return fade(master(x, drive=1.8), 0.02, 0.01)


def fire():
    sec = 1.3
    t = t_of(sec)
    boom = np.sin(2 * np.pi * np.cumsum(70 * np.exp(-t * 3) + 32) / SR) * np.exp(-t * 3.2)
    blast = low(rng.normal(0, 1, len(t)), 5000) * np.exp(-t * 7)
    zf = 2400 * np.exp(-t * 5) + 120
    zap = saw(np.cumsum(2 * np.pi * zf / SR)) * np.exp(-t * 4)
    crack = high(rng.normal(0, 1, len(t)), 3000) * np.exp(-t * 30)
    x = boom * 1.4 + blast * 0.9 + zap * 0.55 + crack * 0.8
    return fade(master(x, peak=0.68, drive=3.0), 0.001, 0.15)


def burn():
    sec = 3.1
    t = t_of(sec)
    # a roaring core: detuned saws at 62 Hz, wavering, through a sweeping low-pass
    f0 = 62 * (1 + 0.02 * np.sin(2 * np.pi * 5.3 * t))
    core = sum(saw(np.cumsum(2 * np.pi * f0 * d / SR) + i) for i, d in enumerate((1.0, 1.007, 0.993, 2.003)))
    sweep = 900 + 500 * np.sin(2 * np.pi * 0.7 * t)
    core_f = np.zeros_like(core)
    for i in range(0, len(core), 2048):                 # blockwise sweep
        core_f[i:i + 2048] = low(core[max(0, i - 2048):i + 2048], float(sweep[i]))[-len(core[i:i + 2048]):]
    # the searing top: band noise fizzing, a shimmer of high partials
    sizzle = band(rng.normal(0, 1, len(t)), 2500, 9000) * (0.7 + 0.3 * np.sin(2 * np.pi * 11 * t))
    shimmer = np.sin(2 * np.pi * 1320 * t + 3 * np.sin(2 * np.pi * 7 * t)) * 0.25
    sub = np.sin(2 * np.pi * 41 * t) * 0.6
    x = core_f * 0.6 + sizzle * 0.5 + shimmer + sub + crackle(len(t), 90, 0.5)
    e = np.minimum(1, t / 0.12) * np.where(t > sec - 0.15, (sec - t) / 0.15, 1)
    return fade(master(x * e, drive=2.6), 0.0, 0.01)


def end():
    sec = 0.9
    t = t_of(sec)
    f = 900 * np.exp(-t * 3.5) + 40
    down = (np.sin(np.cumsum(2 * np.pi * f / SR)) + 0.3 * saw(np.cumsum(2 * np.pi * f * 0.5 / SR))) * np.exp(-t * 2.5)
    hiss = high(rng.normal(0, 1, len(t)), 2000) * np.exp(-t * 4) * 0.5
    return fade(master(down + hiss, drive=1.6), 0.002, 0.2)


SOUNDS = {'laser_charge': charge, 'laser_fire': fire, 'laser_burn': burn, 'laser_end': end}


def main():
    out_dir = os.path.join(ASSETS, 'sounds/boss')
    os.makedirs(out_dir, exist_ok=True)
    sounds = read_sounds()
    for name, fn in SOUNDS.items():
        x = fn().astype(np.float32)
        path = os.path.join(out_dir, name + '.ogg')
        with sf.SoundFile(path, 'w', SR, 1, format='OGG', subtype='VORBIS') as f:
            for i in range(0, len(x), 1 << 15):          # in chunks (libsndfile's Vorbis encoder overflows on Windows)
                f.write(x[i:i + (1 << 15)])
        sounds['boss.' + name] = {'sounds': [{'name': 'papon:boss/' + name, 'attenuation_distance': 72}],
                                  'subtitle': 'subtitles.papon.boss.laser'}
        print(path)
    write_sounds(sounds)


if __name__ == '__main__':
    main()
