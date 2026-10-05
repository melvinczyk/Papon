#!/usr/bin/env python3
"""Phase four's own sounds (the Final Guardian's awakening), synthesized:

  boss/pant      his panting on his back, the scar torn open: a big man's breath out (rough, voiced underneath) and in
  boss/scratch   the band stopping dead as he is thrown flat: a record scratch
  boss/cine_souls   the souls of the stands lifting out of them (11 s): a ghostly choir swelling over a rising wind
  boss/cine_soul    one soul streaking through the air into him: a whoosh with a wail in it
  boss/cine_absorb  a soul striking him: a deep thump and a green chime
  boss/cine_moon    the moon turning into the Day of the Dead's: a huge temple bell over a sub boom
  boss/cine_swell   his power building to the burst (3 s): a riser

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


def norm(y, peak=0.9):
    return y / (np.abs(y).max() + 1e-9) * peak


def formants(x, fs=((730, 6, 1.0), (1090, 7, 0.6), (2440, 9, 0.3))):
    out = np.zeros_like(x)
    for f, q, g in fs:
        b, a = ss.iirpeak(f, q, fs=SR)
        out += g * ss.lfilter(b, a, x)
    return out


def cine_souls(sec=11.0):
    n = int(sec * SR)
    t = np.arange(n) / SR
    u = t / sec
    swell = np.clip(u / 0.75, 0, 1) ** 1.6 * np.clip((1 - u) / 0.12, 0, 1)
    # the choir: a minor chord of voices ("ah"), each detuned and with its own vibrato, the top voices coming in later
    choir = np.zeros(n)
    notes = [110, 164.8, 220, 261.6, 329.6, 440]
    for i, f in enumerate(notes):
        start = i * 0.09
        e = np.clip((u - start) / 0.35, 0, 1) ** 1.5
        for d in (-0.006, 0.0, 0.007):
            vib = 1 + 0.006 * np.sin(2 * np.pi * (4.6 + i * 0.3) * t + i) + d
            glide = 1 + 0.03 * u                      # the whole choir lifting a little as they rise
            ph = np.cumsum(2 * np.pi * f * vib * glide / SR)
            choir += e * ss.sawtooth(ph) / (1 + i * 0.25)
    choir = formants(choir)
    choir = ss.sosfilt(ss.butter(2, 4500, fs=SR, output='sos'), choir)
    # the wind: noise in a band that sweeps up and breathes
    wind = rng.normal(0, 1, n)
    centre = 300 + 1500 * u + 300 * np.sin(2 * np.pi * 0.35 * t)
    out_w = np.zeros(n)
    blk = 2048
    for a in range(0, n, blk):
        c = float(centre[a])
        sos = ss.butter(2, [max(60, c * 0.5), min(SR / 2 - 100, c * 1.6)], btype='band', fs=SR, output='sos')
        out_w[a:a + blk] = ss.sosfilt(sos, wind[a:a + blk])
    out_w *= 0.6 + 0.4 * np.sin(2 * np.pi * 0.27 * t) ** 2
    # the shimmer: high partials trembling
    sh = sum(np.sin(2 * np.pi * f * t + k) for k, f in enumerate((1760, 2217, 2637, 3520))) * (0.5 + 0.5 * np.sin(2 * np.pi * 7 * t))
    sh *= np.clip((u - 0.35) / 0.4, 0, 1)
    # a sub drone under it all
    sub = np.sin(2 * np.pi * 55 * t) * 0.8 + np.sin(2 * np.pi * 82.4 * t) * 0.4
    y = norm(choir) * 0.75 + norm(out_w) * 0.45 + norm(sh) * 0.12 + sub * 0.25
    return norm(y * swell, 0.92)


def cine_soul():
    n = int(1.3 * SR)
    t = np.arange(n) / SR
    u = t / t[-1]
    e = np.sin(np.pi * u) ** 1.5
    noise = rng.normal(0, 1, n)
    out = np.zeros(n)
    blk = 1024
    for a in range(0, n, blk):
        c = 400 + 3200 * (u[a] ** 1.3)
        sos = ss.butter(2, [c * 0.6, min(SR / 2 - 100, c * 1.5)], btype='band', fs=SR, output='sos')
        out[a:a + blk] = ss.sosfilt(sos, noise[a:a + blk])
    wail_f = 520 * (1 + 0.6 * u) * (1 + 0.01 * np.sin(2 * np.pi * 6 * t))
    wail = np.sin(np.cumsum(2 * np.pi * wail_f / SR)) + 0.3 * np.sin(np.cumsum(2 * np.pi * 2 * wail_f / SR))
    return norm((norm(out) * 0.8 + wail * 0.35) * e, 0.85)


def cine_absorb():
    n = int(1.6 * SR)
    t = np.arange(n) / SR
    f = 70 * np.exp(-t * 2.5) + 38
    thump = np.sin(np.cumsum(2 * np.pi * f / SR)) * np.exp(-t * 4.5) * (1 - np.exp(-t * 400))
    chime = sum(np.sin(2 * np.pi * fr * t) * np.exp(-t * dec) * g for fr, dec, g in
                ((1318.5, 2.2, 1.0), (1975.5, 3.0, 0.6), (2637, 3.8, 0.4), (3951, 5, 0.2)))
    whoosh = ss.sosfilt(ss.butter(2, [300, 2500], btype='band', fs=SR, output='sos'), rng.normal(0, 1, n)) * np.exp(-t * 9)
    return norm(thump * 1.0 + norm(chime) * 0.35 + norm(whoosh) * 0.25, 0.9)


def cine_moon():
    n = int(7.0 * SR)
    t = np.arange(n) / SR
    base = 55.0
    bell = sum(g * np.sin(2 * np.pi * base * r * t + k) * np.exp(-t * d) for k, (r, g, d) in enumerate(
        ((1.0, 1.0, 0.45), (2.0, 0.7, 0.6), (2.76, 0.6, 0.8), (5.4, 0.35, 1.3), (8.93, 0.2, 2.0), (0.5, 0.6, 0.35))))
    bell *= 1 + 0.15 * np.sin(2 * np.pi * 1.3 * t)        # the beat of a huge bell
    boom = np.sin(np.cumsum(2 * np.pi * (45 * np.exp(-t * 1.2) + 25) / SR)) * np.exp(-t * 1.4)
    rumble = ss.sosfilt(ss.butter(2, 180, fs=SR, output='sos'), rng.normal(0, 1, n)) * np.exp(-t * 0.7)
    y = norm(bell) * 0.8 + boom * 0.7 + norm(rumble) * 0.4
    return norm(y * (1 - np.exp(-t * 300)), 0.95)


def cine_swell(sec=3.2):
    n = int(sec * SR)
    t = np.arange(n) / SR
    u = t / sec
    f = 90 * 2 ** (u * 3.2)
    saw = sum(ss.sawtooth(np.cumsum(2 * np.pi * f * m * (1 + d) / SR)) for m, d in ((1, 0), (1, 0.008), (1.5, -0.005), (2, 0.004)))
    saw = ss.sosfilt(ss.butter(2, 3000, fs=SR, output='sos'), saw)
    noise = ss.sosfilt(ss.butter(2, 800, btype='high', fs=SR, output='sos'), rng.normal(0, 1, n)) * u ** 2
    trem = 0.7 + 0.3 * np.sin(2 * np.pi * (4 + 18 * u ** 2) * t)
    y = (norm(saw) * 0.7 + norm(noise) * 0.45) * trem * u ** 1.4
    y[-int(0.01 * SR):] *= np.linspace(1, 0, int(0.01 * SR))
    return norm(y, 0.9)


CINE = {'cine_souls': cine_souls, 'cine_soul': cine_soul, 'cine_absorb': cine_absorb, 'cine_moon': cine_moon,
        'cine_swell': cine_swell}


def write(name, y):
    path = os.path.join(ASSETS, 'sounds/boss/%s.ogg' % name)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    sf.write(path, y.astype(np.float32), SR, format='OGG', subtype='VORBIS')
    return path


if __name__ == '__main__':
    write('pant', pant())
    write('scratch', scratch())
    for k, f in CINE.items():
        write(k, f())
    sounds = read_sounds()
    for k in ('pant', 'scratch') + tuple(CINE):
        sounds['boss.' + k] = {'sounds': [{'name': 'papon:boss/' + k}], 'subtitle': 'subtitles.papon.boss.' + k}
    write_sounds(sounds)
    print('ok')
