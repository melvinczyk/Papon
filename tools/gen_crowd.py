#!/usr/bin/env python3
"""Synthesizes the crowd of El Coliseo Papa and the band's warm-up (numpy + soundfile, no samples).

A crowd is a few dozen voices summed: each a glottal pulse train (a band-limited sawtooth with its
own pitch, jitter and vibrato) shaped by vowel formants, with its own syllable envelope and timing,
plus whistles and claps, in a big stone bowl's reverb.

Writes, under src/main/resources/assets/papon/sounds/:
  crowd/ambience.ogg   a seamless ~12 s loop: murmur, a cheer here and there (client/CrowdSounds)
  crowd/roar.ogg       everyone on their feet: a rising cheer, whistles, applause
  crowd/boo.ogg        a long, low boo
  crowd/gasp.ogg       a sharp collective intake of breath
  crowd/swoon.ogg      "awww": high, falling, adoring
  crowd/chant.ogg      "PA-PÓN!", once (played on every other beat while they chant)
  crowd/clap.ogg       one beat of the whole stadium clapping
  band/toot.ogg        a trumpet's warm-up toot
  band/tune.ogg        a violin tuning: a fifth, bowed
and their events in sounds.json.

  python tools/gen_crowd.py
"""
import json
from geo_util import read_sounds, write_sounds
import math
import os

import numpy as np
import soundfile as sf

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/papon')
OUT = os.path.join(ASSETS, 'sounds')
SR = 44100
rng = np.random.default_rng(1945)

VOWELS = {'a': (800, 1200, 2500), 'o': (500, 880, 2400), 'u': (340, 760, 2300), 'e': (520, 1800, 2500),
          'i': (300, 2250, 3000), 'ay': (700, 1500, 2600)}


def t_of(n):
    return np.arange(n) / SR


def fft_filter(x, response):
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    return np.fft.irfft(X * response(f), len(x))


def formant(vowel, bright=1.0):
    f1, f2, f3 = VOWELS[vowel]

    def r(f):
        g = (np.exp(-((f - f1) / (f1 * 0.12)) ** 2) * 1.0 + np.exp(-((f - f2) / (f2 * 0.09)) ** 2) * 0.7
             + np.exp(-((f - f3) / (f3 * 0.07)) ** 2) * 0.35 * bright)
        return g + 0.02
    return r


def band(lo, hi):
    return lambda f: (1 / np.sqrt(1 + (lo / np.maximum(f, 1)) ** 4)) / np.sqrt(1 + (f / hi) ** 4)


def glottal(f0_curve, jitter=0.01):
    """A sawtooth-ish pulse train following f0 (Hz per sample), band-limited by summing harmonics."""
    n = len(f0_curve)
    f0 = f0_curve * (1 + jitter * np.cumsum(rng.normal(0, 1, n)) / np.sqrt(n) * 3)
    ph = 2 * np.pi * np.cumsum(f0) / SR
    out = np.zeros(n)
    kmax = int(min(40, SR / 2 / max(80, f0.max())))
    for k in range(1, kmax + 1):
        out += np.sin(k * ph) / k
    return out


def voice(dur, f0, vowel_env, vowel, contour=None, vib=0.0):
    """One person's shout: pitch f0 (Hz) moving along `contour` (multiplier curve), shaped by `vowel_env`."""
    n = int(dur * SR)
    t = t_of(n)
    c = np.ones(n) if contour is None else np.interp(t, np.linspace(0, dur, len(contour)), contour)
    f = f0 * c * (1 + vib * np.sin(2 * np.pi * rng.uniform(4.5, 6.5) * t + rng.uniform(0, 6)))
    src = glottal(f) + rng.normal(0, 1, n) * 0.08
    return src * vowel_env(t)


def crowd_voices(dur, count, vowel_choices, make, spread=0.0):
    """`count` people; make(i) -> (signal, start) per person; summed per vowel and filtered."""
    n = int(dur * SR)
    groups = {v: np.zeros(n) for v in vowel_choices}
    for i in range(count):
        v = vowel_choices[rng.integers(len(vowel_choices))]
        sig, start = make(i)
        k = int(max(0.0, start) * SR)
        if k >= n:
            continue
        m = min(len(sig), n - k)
        groups[v][k:k + m] += sig[:m] * rng.uniform(0.6, 1.0)
    out = np.zeros(n)
    for v, g in groups.items():
        out += fft_filter(g, formant(v))
    return out


def env_adsr(a, d, s, r, dur):
    def e(t):
        out = np.where(t < a, t / max(a, 1e-4), 1 - (1 - s) * np.clip((t - a) / max(d, 1e-4), 0, 1))
        return out * np.clip((dur - t) / max(r, 1e-4), 0, 1)
    return e


def whistle(dur):
    n = int(dur * SR)
    t = t_of(n)
    f = rng.uniform(1800, 3200) * (1 + 0.25 * np.clip(t / (dur * 0.5), 0, 1)) * (1 + 0.01 * np.sin(2 * np.pi * 7 * t))
    ph = 2 * np.pi * np.cumsum(f) / SR
    return np.sin(ph) * np.clip(t / 0.05, 0, 1) * np.clip((dur - t) / 0.1, 0, 1) * 0.3


def clap():
    n = int(0.05 * SR)
    t = t_of(n)
    x = rng.normal(0, 1, n) * np.exp(-t / 0.008)
    return x


def claps(dur, rate, per_beat=None, jitter=0.02):
    n = int(dur * SR)
    out = np.zeros(n)
    if per_beat is None:
        times = np.sort(rng.uniform(0, dur, int(rate * dur)))
    else:
        times = np.concatenate([b + rng.normal(0, jitter, per_beat) for b in per_beat_times(dur)])
    for tm in times:
        k = int(max(0, tm) * SR)
        c = clap() * rng.uniform(0.4, 1.0)
        m = min(len(c), n - k)
        if m > 0:
            out[k:k + m] += c[:m]
    return fft_filter(out, band(700, 5000))


def per_beat_times(dur):
    return [0.0]


def reverb(x, seconds=2.6, wet=0.32, decay=0.75):
    n = int(seconds * SR)
    t = t_of(n)
    ir = rng.normal(0, 1, n) * np.exp(-t / decay)
    ir = fft_filter(np.concatenate([ir, np.zeros(SR // 2)]), band(80, 5000))[:n]
    ir[:int(0.03 * SR)] = 0
    ir /= np.sqrt(np.sum(ir ** 2))
    size = 1 << int(np.ceil(np.log2(len(x) + n)))
    y = np.fft.irfft(np.fft.rfft(x, size) * np.fft.rfft(ir, size), size)[:len(x) + n]
    out = np.zeros(len(x) + n)
    out[:len(x)] += x * (1 - wet)
    out += y * wet * 2.5
    return out


def loop_reverb(x, seconds=2.6, wet=0.3):
    y = reverb(x, seconds, wet)
    out = y[:len(x)].copy()
    tail = y[len(x):]
    out[:len(tail)] += tail
    return out


def save(rel, x, stereo_width=0.0):
    path = os.path.join(OUT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    x = np.asarray(x, dtype=np.float64)
    x = x / (np.max(np.abs(x)) or 1) * 0.85
    with sf.SoundFile(path, 'w', SR, 1, format='OGG', subtype='VORBIS') as f:
        for i in range(0, len(x), SR):
            f.write(x[i:i + SR])
    print(path, '%.1f s' % (len(x) / SR))


def fade(x, a=0.01, r=0.2):
    n = len(x)
    e = np.ones(n)
    na, nr = int(a * SR), int(r * SR)
    if na:
        e[:na] = np.linspace(0, 1, na)
    if nr:
        e[-nr:] *= np.linspace(1, 0, nr)
    return x * e


# ===================================================================== the crowd

def ambience():
    """A seamless loop: a sea of talk with syllables in it, a shout or a whistle now and then."""
    dur = 12.0
    n = int(dur * SR)
    t = t_of(n)

    def chatter(i):
        f0 = rng.uniform(95, 240)
        rate = rng.uniform(3.5, 6.5)
        ph = rng.uniform(0, 6)
        # whole-number cycles over the loop so it wraps
        rate = round(rate * dur) / dur
        syl = lambda tt: np.clip(np.sin(2 * np.pi * rate * tt + ph), 0, 1) ** 0.7 * (0.5 + 0.5 * np.sin(2 * np.pi * tt / dur * rng.integers(1, 4) + ph))
        return voice(dur, f0, syl, 'a', contour=1 + 0.08 * np.sin(np.linspace(0, 2 * np.pi * rng.integers(2, 9), 200)), vib=0.01), 0.0
    talk = crowd_voices(dur, 70, ['a', 'e', 'o', 'i'], chatter)
    bed = fft_filter(rng.normal(0, 1, n), band(180, 2400)) * 0.25
    out = talk / np.max(np.abs(talk)) + bed
    for _ in range(9):
        s = rng.uniform(0, dur - 1.5)
        sh = voice(1.2, rng.uniform(170, 300), env_adsr(0.05, 0.3, 0.6, 0.4, 1.2), 'ay', contour=[1, 1.15, 1.1, 0.95], vib=0.02)
        k = int(s * SR)
        out[k:k + len(sh)] += fft_filter(sh, formant('ay')) / (np.max(np.abs(sh)) or 1) * 0.4
    for _ in range(5):
        s = rng.uniform(0, dur - 1)
        w = whistle(rng.uniform(0.4, 0.9))
        k = int(s * SR)
        out[k:k + len(w)] += w * 0.5
    out += claps(dur, 6) * 0.25
    save('crowd/ambience.ogg', loop_reverb(out))


def roar():
    dur = 4.5

    def shout(i):
        f0 = rng.uniform(140, 320)
        d = rng.uniform(1.8, 3.6)
        return voice(d, f0, env_adsr(rng.uniform(0.05, 0.3), 0.6, 0.7, 0.8, d), 'a',
                     contour=[0.9, 1.2, 1.15, 1.1, 1.0], vib=0.025), rng.uniform(0, 0.6)
    x = crowd_voices(dur, 80, ['a', 'ay', 'e', 'o'], shout)
    x /= np.max(np.abs(x))
    for _ in range(10):
        w = whistle(rng.uniform(0.5, 1.3))
        k = int(rng.uniform(0.2, 2.5) * SR)
        x[k:k + len(w)] += w * 0.6
    x += claps(dur, 60) * 0.5 * np.clip(t_of(int(dur * SR)) / 1.0, 0, 1) * np.clip((dur - t_of(int(dur * SR))) / 2, 0, 1)
    save('crowd/roar.ogg', fade(reverb(x), 0.02, 1.0))


def boo():
    dur = 3.2

    def b(i):
        f0 = rng.uniform(85, 170)
        d = rng.uniform(1.8, 2.9)
        return voice(d, f0, env_adsr(0.15, 0.5, 0.8, 0.6, d), 'u', contour=[1.02, 1.0, 0.92, 0.85], vib=0.02), rng.uniform(0, 0.35)
    x = crowd_voices(dur, 60, ['u', 'o'], b)
    save('crowd/boo.ogg', fade(reverb(x / np.max(np.abs(x))), 0.02, 0.8))


def gasp():
    dur = 1.2
    n = int(dur * SR)
    t = t_of(n)
    breath = fft_filter(rng.normal(0, 1, n), formant('a', 1.5)) * np.clip(t / 0.08, 0, 1) * np.exp(-np.clip(t - 0.15, 0, None) / 0.12)

    def g(i):
        f0 = rng.uniform(200, 380)
        return voice(0.35, f0, env_adsr(0.04, 0.1, 0.5, 0.15, 0.35), 'a', contour=[1, 1.3]) * 0.3, rng.uniform(0, 0.08)
    x = crowd_voices(dur, 40, ['a', 'o'], g)
    x = x / np.max(np.abs(x)) * 0.6 + breath / np.max(np.abs(breath))
    save('crowd/gasp.ogg', fade(reverb(x, wet=0.25), 0.005, 0.4))


def swoon():
    dur = 2.8

    def a(i):
        f0 = rng.uniform(230, 420)
        d = rng.uniform(1.4, 2.2)
        return voice(d, f0, env_adsr(0.1, 0.4, 0.8, 0.6, d), 'o', contour=[1.25, 1.2, 1.0, 0.82], vib=0.03), rng.uniform(0, 0.3)
    x = crowd_voices(dur, 55, ['o', 'a', 'u'], a)
    save('crowd/swoon.ogg', fade(reverb(x / np.max(np.abs(x))), 0.02, 0.6))


def chant():
    """PA-PÓN: 'pa' on the beat, 'pón' on the next (0.47 s on, the song's tempo), fifty throats."""
    beat = 0.4706
    dur = beat * 2 + 0.6
    n = int(dur * SR)
    out = np.zeros(n)
    for syl, t0, vowel, length, nasal in (('pa', 0.0, 'a', 0.2, False), ('pon', beat, 'o', 0.34, True)):
        def make(i, t0=t0, length=length, nasal=nasal):
            f0 = rng.uniform(140, 260)
            e = env_adsr(0.012, 0.06, 0.8, 0.07, length)
            sig = voice(length, f0, e, vowel, contour=[1.08, 1.0, 0.94] if nasal else [1.0, 1.04], vib=0.0)
            pop = rng.normal(0, 1, int(0.012 * SR)) * 0.6
            sig[:len(pop)] += pop
            return sig, t0 + rng.normal(0, 0.018)
        g = crowd_voices(dur, 50, [vowel], make)
        if nasal:
            # the n: the voices close off into a hum
            k0 = int((t0 + length * 0.55) * SR)
            k1 = min(n, int((t0 + length + 0.05) * SR))
            g[k0:k1] = g[k0:k1] * np.linspace(1, 0.35, k1 - k0) + fft_filter(g, lambda f: 1 / np.sqrt(1 + (f / 400) ** 4))[k0:k1] * np.linspace(0, 0.6, k1 - k0)
        out += g / (np.max(np.abs(g)) or 1)
    save('crowd/chant.ogg', fade(reverb(out, wet=0.3), 0.002, 0.3))


def clap_hit():
    dur = 0.6
    n = int(dur * SR)
    out = np.zeros(n)
    for _ in range(120):
        c = clap() * rng.uniform(0.4, 1.0)
        k = int(abs(rng.normal(0, 0.012)) * SR)
        out[k:k + len(c)] += c[:n - k]
    save('crowd/clap.ogg', fade(reverb(fft_filter(out, band(700, 6000)), wet=0.3)[:int(1.4 * SR)], 0.001, 0.4))


# ===================================================================== the band warming up

def trumpet_note(f, dur, vel=0.9):
    n = int(dur * SR)
    t = t_of(n)
    env = np.clip(t / 0.03, 0, 1) * np.clip((dur - t) / 0.08, 0, 1)
    ph = 2 * np.pi * np.cumsum(f * (1 + 0.004 * np.sin(2 * np.pi * 5.5 * t) * np.clip((t - 0.15) / 0.2, 0, 1))) / SR
    out = np.zeros(n)
    for k in range(1, 20):
        out += np.sin(k * ph) * np.exp(-np.log(k) * (2.4 - 1.2 * env * vel)) * (1.2 if k in (3, 4, 5) else 1)
    return out * env


def toot():
    # a lip slur up and a held note, a bit sour, then a little scale
    x = np.concatenate([trumpet_note(392, 0.18), trumpet_note(523, 0.45), np.zeros(int(0.08 * SR)),
                        trumpet_note(587, 0.12), trumpet_note(659, 0.12), trumpet_note(784, 0.4)])
    save('band/toot.ogg', fade(reverb(x, wet=0.2), 0.005, 0.3))


def tune():
    dur = 1.6
    n = int(dur * SR)
    t = t_of(n)
    out = np.zeros(n)
    for f in (440 * 0.995, 659.3 * 1.004):
        ph = 2 * np.pi * np.cumsum(f * (1 + 0.003 * np.sin(2 * np.pi * 5.8 * t))) / SR
        for k in range(1, 22):
            out += np.sin(k * ph) / k ** 1.1
    out += rng.normal(0, 1, n) * 0.04
    out *= np.clip(t / 0.15, 0, 1) * np.clip((dur - t) / 0.3, 0, 1)
    save('band/tune.ogg', fade(reverb(fft_filter(out, formant('e')), wet=0.18), 0.01, 0.3))


EVENTS = ['crowd/ambience', 'crowd/roar', 'crowd/boo', 'crowd/gasp', 'crowd/swoon', 'crowd/chant', 'crowd/clap',
          'band/toot', 'band/tune']


def register():
    sounds = read_sounds()
    for e in EVENTS:
        if e in ('crowd/ambience', 'crowd/roar') and os.path.exists(os.path.join(ASSETS, 'sounds/crowd/ambience_rec_a.ogg')):
            continue                # the recorded crowd (gen_crowd_samples.py) has these
        sounds[e.replace('/', '.')] = {'sounds': [{'name': 'papon:' + e, 'stream': e == 'crowd/ambience'}]}
    write_sounds(sounds)


if __name__ == '__main__':
    ambience()
    roar()
    boo()
    gasp()
    swoon()
    chant()
    clap_hit()
    toot()
    tune()
    register()
