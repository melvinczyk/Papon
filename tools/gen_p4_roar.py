#!/usr/bin/env python3
"""His roar as the Final Guardian awakens (P4_HALFBAKED's `p4_burst`, "RRRAAAAAAAAAAAAAAGH!"), synthesized (2026-10-06, the user: "the
phase 4 transformation sound for papon sounds very bad, it needs to be completely redone"). The cloned voice's screams were thin
(gen_boss_voice.PICKED said so): this is built the way a roar is, not read out by a speech model:

  a glottal source climbing from a growl through a scream to a held wail, ragged (jitter, a subharmonic growl in the amplitude, a
  voice break), shaped by moving vowel formants (the mouth opening on "AH"), breath and rasp on top, a sub-bass under it, driven
  into distortion and given a hall

Written over sounds/voice/boss2/p4_burst.ogg at the built line's length (4.4 s: the beats that time the transformation are made
from it), with its env / pitch / accent tracks and mouth shapes in voice/boss2.json; gen_boss_voice.SYNTH keeps it from being
taken again.
  python tools/gen_p4_roar.py [--look]
"""
import json
import os
import sys

import numpy as np
import soundfile as sf

import sfx
from sfx import SR, TAU

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/papon')
LINE = 'p4_burst'


def contour(t, sec):
    """The pitch (Hz) of the roar: a growl, up to a scream, a break, up again, held, torn down and cut."""
    k = [(0.0, 88), (0.18, 105), (0.55, 170), (1.1, 255), (1.55, 330), (1.62, 410), (2.3, 450), (2.6, 380), (2.75, 520), (3.5, 560),
         (3.95, 430), (4.2, 230), (sec, 140)]
    return np.interp(t, [a for a, _ in k], [b for _, b in k])


def formant_gain(f, t, sec):
    """How much the mouth lets through at frequency f (an array) at times t: an 'ah' that opens as the roar rises."""
    open_ = np.clip(t / 1.4, 0, 1)
    F1 = 640 + 330 * open_ + 40 * np.sin(TAU * 0.9 * t)
    F2 = 1050 + 280 * open_
    F3 = 2500 + 0 * t
    g = 1.0 * np.exp(-0.5 * ((f - F1) / (110 + 40 * open_)) ** 2) + 0.62 * np.exp(-0.5 * ((f - F2) / 170) ** 2) \
        + 0.3 * np.exp(-0.5 * ((f - F3) / 260) ** 2) + 0.07 + 0.12 * np.exp(-0.5 * ((f - 3600) / 600) ** 2)
    return g


def roar(sec=4.4, seed=3):
    r = np.random.default_rng(seed)
    n = int(sec * SR)
    t = np.arange(n) / SR
    f0 = contour(t, sec)
    ramp = np.clip(t / 0.5, 0, 1)
    # ragged: a wobble in the pitch (vibrato growing with the effort) and jitter, and the break (a quick leap) at 1.6 s and 2.7 s
    vib = 1 + (0.012 + 0.014 * ramp) * np.sin(TAU * 6.1 * t + 0.4) + sfx.lp(r.normal(0, 1, n), 60) * 0.035
    f = f0 * vib
    ph = np.cumsum(TAU * f / SR)
    # the source: harmonics weighted by the moving formants (gains evaluated every 256 samples and interpolated)
    blk = 256
    tb = np.arange(0, n + blk, blk) / SR
    f0b = contour(tb, sec)
    src = np.zeros(n)
    nh = 46
    for k in range(1, nh + 1):
        fk = k * f0b
        g = formant_gain(fk, tb, sec) * (fk < 7800)
        gt = np.interp(t, tb, g)
        src += gt / k ** 0.78 * np.sin(k * ph + k * 0.31)
    # the growl: the whole voice chopped by a ~70 Hz flutter, deep at the start and the end, a little in the middle
    depth = 0.75 * np.exp(-t * 2.2) + 0.55 * np.clip((t - 3.7) / 0.5, 0, 1) + 0.2
    grow = 1 - depth * (0.5 + 0.5 * np.sin(TAU * (68 + 14 * np.sin(TAU * 0.7 * t)) * t))
    src *= grow
    # the effort: loudness swelling in, and the crest at the top of the scream; torn off at the end
    amp = np.interp(t, [0, 0.05, 0.5, 1.5, 2.3, 3.5, 4.0, 4.25, sec], [0.0, 0.55, 0.7, 0.95, 1.0, 1.0, 0.9, 0.5, 0.0])
    # breath and rasp: noise through the mouth's own resonances
    nz = r.normal(0, 1, n)
    rasp = sfx.sweep_bp(nz, formant_centres(t), 0.45) * 2.0
    rasp += sfx.hp(nz, 3000) * 0.18
    # the sub: a roar from the chest, an octave below the voice, saturated
    sub = np.sin(np.cumsum(TAU * np.maximum(40, f0 * 0.5) / SR)) * (0.6 + 0.4 * grow)
    sub += sfx.lp(sfx.saw(np.cumsum(TAU * np.maximum(40, f0 * 0.25) / SR)), 220) * 0.5
    y = sfx.norm(src) * 1.0 + sfx.norm(rasp) * 0.5 * (0.5 + 0.5 * np.clip(amp, 0, 1)) + sfx.norm(sub) * 0.5
    y *= amp
    # drive it: a roar is a voice pushed past what it can carry
    y = np.tanh(y / (np.abs(y).max() + 1e-9) * 3.2)
    y = sfx.hp(y, 55)
    # a presence lift and a hall
    y = y + sfx.bp(y, 1800, 4500) * 0.35
    y = sfx.reverb(y, sec=1.5, wet=0.2, seed=71)[:n]
    y *= np.minimum(1, t / 0.01) * np.minimum(1, (sec - t) / 0.12)
    return y / (np.abs(y).max() + 1e-9) * 0.95


def formant_centres(t):
    open_ = np.clip(t / 1.4, 0, 1)
    return 1700 + 500 * open_ + 400 * np.sin(TAU * 0.8 * t)


def mouth_shapes(sec):
    """Rhubarb's shapes: closed, then the mouth wide for the whole roar, working a little, shut at the end."""
    return [[0.0, 'X'], [0.1, 'B'], [0.24, 'F'], [0.5, 'D'], [1.0, 'C'], [1.3, 'D'], [1.9, 'D'], [2.5, 'C'], [2.75, 'D'], [3.9, 'D'], [4.15, 'C'],
            [4.3, 'B'], [sec - 0.04, 'X']]


def main():
    sys.path.insert(0, os.path.join(ROOT, 'tools'))
    import gen_boss_voice as gbv
    path = os.path.join(ASSETS, 'voice/boss2.json')
    data = json.load(open(path, encoding='utf-8'))
    old = data['lines'][LINE]
    sec = old['len']
    y = roar(sec)
    info = sf.info(os.path.join(ASSETS, 'sounds/voice/boss2/%s.ogg' % LINE))
    sr = info.samplerate
    if sr != SR:
        import librosa
        y = librosa.resample(y, orig_sr=SR, target_sr=sr)
    y = gbv.loudness(y, sr)
    peak = np.abs(y).max()
    if peak > 0.98:
        y = y / peak * 0.98
    out = os.path.join(ASSETS, 'sounds/voice/boss2/%s.ogg' % LINE)
    with sf.SoundFile(out, 'w', sr, 1, format='OGG', subtype='VORBIS') as f:
        for i in range(0, len(y), 1 << 15):
            f.write(y[i:i + (1 << 15)].astype(np.float32))
    env, pitch, acc = gbv.tracks(y, sr)
    data['lines'][LINE] = {'len': round(len(y) / sr, 3), 'mood': old['mood'], 'mouth': mouth_shapes(len(y) / sr),
                           'env': [round(float(v), 3) for v in env], 'pitch': [round(float(v), 2) for v in pitch],
                           'accent': [round(float(v), 3) for v in acc]}
    with open(path, 'w', newline='\n') as f:
        json.dump(data, f, separators=(',', ':'))
    print('%s: %.2f s (was %.2f), peak %.2f, rms %.1f dB' % (LINE, len(y) / sr, sec, np.abs(y).max(), 20 * np.log10(sfx.rms(y) + 1e-9)))
    if '--look' in sys.argv:
        d = os.path.join(ROOT, 'tools/.cache/p4_sounds')
        os.makedirs(d, exist_ok=True)
        sfx.spectrogram_png(y, os.path.join(d, 'roar.png'))


if __name__ == '__main__':
    main()
