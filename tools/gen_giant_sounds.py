#!/usr/bin/env python3
"""El Gigante's own sounds (2026-10-06, the user: "I can't hear gigante laser attack. It needs to be loud and extremely powerful, the
most powerful laser attack in the entire boss fight... I need to be able to tell when gigante is about to do his grab attack or laser
attack"), synthesized (tools/sfx.py), mastered loud, played from far off (attenuation 128) and never ducked (client/SoundMix leaves
"boss.laser_" and "boss.giant_" alone):

  boss/laser_giant_charge  its eyes charging (1.3 s): a sub hum swelling, two whines climbing apart and beating, an inhale of air, crackle
  boss/laser_giant_fire    the beams firing (2.4 s): a sub boom falling, a sonic crack, a blast of white air, a zap, a shock wave, a hall
  boss/laser_giant_burn    the beams burning (4.2 s): a roar of saws and jet noise, a searing top, two beams beating, a sub, crackle
                           (cut by the server when the beams go out: ClientboundStopSoundPacket)
  boss/laser_giant_end     the beams going out (1.8 s): a power-down falling through the floor, a thump, steam
  boss/laser_giant_sizzle  where the beam burns the floor (0.7 s): a sizzle and a thud, now and then
  boss/giant_tell_grab     La Mano starting (1.6 s): a low horn blast, two heartbeats, dread rising
  boss/giant_tell_swipe    a sweep winding up (1.0 s): bones creaking, air gathering, a drum at its end
  boss/giant_tell_slam     a fist going up (1.1 s): a rumble climbing, an inhale, three drum hits closing up
  boss/giant_tell_lunge    La Embestida coiling (1.1 s): a growl rising out of its chest

  python tools/gen_giant_sounds.py [name ...] [--look]
"""
import os
import sys

import numpy as np

import sfx
from geo_util import read_sounds, write_sounds
from sfx import SR, TAU, tt, mix, norm, soft, lp, hp, bp, saw, sweep_bp, sweep_lp, reverb, voice, drum, conch, crackle, rattle, rms, fit

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/papon')


def laser_charge(sec=1.3):
    n = int(sec * SR)
    t = tt(sec)
    u = t / sec
    r = np.random.default_rng(51)
    f = 38 * (1 + 1.6 * u ** 1.5)
    sub = np.sin(np.cumsum(TAU * f / SR)) * 0.9 + np.sin(np.cumsum(TAU * f * 1.012 / SR)) * 0.6
    sub *= 0.25 + 0.75 * u
    # two whines, one from each eye, climbing apart and beating against each other
    f1 = 180 * 21 ** (u ** 1.3)
    f2 = f1 * 1.017
    p1, p2 = np.cumsum(TAU * f1 / SR), np.cumsum(TAU * f2 / SR)
    whine = np.sin(p1) + 0.4 * np.sin(2 * p1) + np.sin(p2) + 0.4 * np.sin(2 * p2) + 0.2 * saw(3 * p1)
    whine *= (0.3 + 0.7 * u ** 1.1) * (1 + 0.3 * np.sin(TAU * (6 + 34 * u) * t))
    air = sweep_bp(r.normal(0, 1, n), 140 * np.exp(u * 4.0), 0.7) * u ** 1.8
    cr = crackle(n, 40, 1.0, seed=5) * (0.1 + 1.6 * u ** 1.5)
    y = norm(sub) * 0.75 + norm(whine) * 0.8 + norm(air) * 0.55 + norm(cr) * 0.4
    return soft(y, 1.6, 0.97)


def laser_fire(sec=2.4):
    n = int(sec * SR)
    t = tt(sec)
    r = np.random.default_rng(52)
    boom = np.sin(np.cumsum(TAU * (24 + 125 * np.exp(-t * 7)) / SR)) * np.exp(-t * 1.7) * np.minimum(1, t / 0.0015)
    boom2 = np.sin(np.cumsum(TAU * (38 + 70 * np.exp(-t * 12)) / SR)) * np.exp(-t * 2.6) * 0.7
    crack = hp(r.normal(0, 1, n), 2000) * np.exp(-t * 42)
    blast = sweep_lp(r.normal(0, 1, n), 14000 * np.exp(-t * 4.0) + 700) * np.exp(-t * 3.0)
    zap = saw(np.cumsum(TAU * (3500 * np.exp(-t * 6) + 90) / SR)) * np.exp(-t * 5)
    wave = sweep_bp(r.normal(0, 1, n), 11000 * np.exp(-t * 2.5) + 250, 0.8) * np.exp(-t * 2.0) * np.minimum(1, t / 0.01)
    y = boom * 1.6 + boom2 + crack * 0.7 + blast * 0.9 + zap * 0.5 + wave * 0.7
    y = reverb(y, sec=3.0, wet=0.3, seed=53)[:n]
    y *= np.minimum(1, (sec - t) / 0.5)
    return soft(y, 1.7, 0.98)


def laser_burn(sec=4.2):
    n = int(sec * SR)
    t = tt(sec)
    r = np.random.default_rng(54)
    f0 = 52 * (1 + 0.015 * np.sin(TAU * 4.1 * t))
    core = sum(saw(np.cumsum(TAU * f0 * d / SR) + i) for i, d in enumerate((1.0, 1.006, 0.994, 2.004, 3.01)))
    core = sweep_lp(core, 700 + 500 * np.sin(TAU * 0.8 * t))
    jet = lp(r.normal(0, 1, n), 520) * (0.6 + 0.4 * np.sin(TAU * 7 * t))
    sizzle = bp(r.normal(0, 1, n), 2500, 11000) * (0.65 + 0.35 * np.sin(TAU * 13 * t))
    beat = np.sin(TAU * 880 * t) + np.sin(TAU * 887 * t) + 0.6 * np.sin(TAU * 1320 * t + 2 * np.sin(TAU * 5 * t))
    sub = np.sin(TAU * 36 * t) * 0.9 + np.sin(TAU * 54.5 * t + 1.0) * 0.4
    sub *= 0.8 + 0.2 * np.sin(TAU * 3.3 * t)
    cr = crackle(n, 170, 1.0, seed=7)
    y = norm(core) * 0.6 + norm(jet) * 0.75 + norm(sizzle) * 0.5 + norm(beat) * 0.13 + norm(sub) * 0.75 + norm(cr) * 0.4
    y *= np.minimum(1, t / 0.04) * np.minimum(1, (sec - t) / 0.15)
    return soft(y, 2.0, 0.98)


def laser_end(sec=1.8):
    n = int(sec * SR)
    t = tt(sec)
    r = np.random.default_rng(55)
    f = 1400 * np.exp(-t * 3.2) + 38
    down = (np.sin(np.cumsum(TAU * f / SR)) + 0.3 * saw(np.cumsum(TAU * f * 0.5 / SR))) * np.exp(-t * 1.6)
    thump = np.sin(np.cumsum(TAU * (28 + 60 * np.exp(-t * 8)) / SR)) * np.exp(-t * 3.0)
    hiss = hp(r.normal(0, 1, n), 2500) * np.exp(-t * 3.2) * 0.5
    rumble = lp(r.normal(0, 1, n), 160) * np.exp(-t * 1.5)
    y = norm(down) * 0.7 + thump * 1.0 + hiss + norm(rumble) * 0.5
    y = reverb(y, sec=1.4, wet=0.28, seed=56)[:n]
    y *= np.minimum(1, (sec - t) / 0.3)
    return soft(y, 1.5, 0.95)


def laser_sizzle(sec=0.7):
    n = int(sec * SR)
    t = tt(sec)
    r = np.random.default_rng(57)
    thud = np.sin(np.cumsum(TAU * (34 + 50 * np.exp(-t * 14)) / SR)) * np.exp(-t * 6)
    sz = bp(r.normal(0, 1, n), 2800, 10000) * np.exp(-t * 5) * (0.6 + 0.4 * np.sin(TAU * 40 * t))
    cr = crackle(n, 90, 1.0, seed=2) * np.exp(-t * 3)
    y = thud * 1.1 + norm(sz) * 0.6 + norm(cr) * 0.35
    return soft(y * np.minimum(1, (sec - t) / 0.1), 1.5, 0.95)


def tell_grab(sec=1.6):
    n = int(sec * SR)
    t = tt(sec)
    r = np.random.default_rng(61)
    horn = fit(conch(55.0, 1.5, seed=3, vib=4.0), n)
    horn2 = fit(conch(82.4, 1.3, seed=4, vib=4.6), n)
    d = np.zeros(n)
    for j, (at, a) in enumerate(((0.0, 1.2), (0.46, 0.95), (0.95, 1.3))):
        h = drum(50, 0.9, 1.0, seed=j, amp=a)
        ia = int(at * SR)
        d[ia:ia + len(h)] += h[:n - ia]
    dread = sweep_bp(r.normal(0, 1, n), 120 * np.exp(t / sec * 2.6), 0.7) * (t / sec) ** 1.5
    y = norm(horn) * 0.9 + norm(horn2) * 0.45 + d * 0.85 + norm(dread) * 0.45
    y = reverb(y, sec=1.8, wet=0.22, seed=62)[:n]
    y *= np.minimum(1, (sec - t) / 0.3)
    return soft(y, 1.5, 0.97)


def tell_swipe(sec=1.0):
    n = int(sec * SR)
    t = tt(sec)
    u = t / sec
    r = np.random.default_rng(63)
    air = sweep_bp(r.normal(0, 1, n), 160 * np.exp(u * 3.0), 0.6) * u ** 1.4
    bones = fit(rattle(sec, 55, seed=9, lo=1800, hi=7000), n) * (0.3 + u)
    low = lp(r.normal(0, 1, n), 140) * u ** 2
    hit = fit(drum(60, 0.5, 1.0, seed=1, amp=1.1), n)
    hit = np.roll(hit, int((sec - 0.5) * SR)) * (t > sec - 0.5)
    y = norm(air) * 0.8 + norm(bones) * 0.5 + norm(low) * 0.5 + hit * 0.8
    return soft(y * np.minimum(1, (sec - t) / 0.08), 1.5, 0.96)


def tell_slam(sec=1.1):
    n = int(sec * SR)
    t = tt(sec)
    u = t / sec
    r = np.random.default_rng(64)
    rum = sweep_lp(r.normal(0, 1, n), 90 + 420 * u ** 1.5) * (0.3 + 0.7 * u)
    inhale = sweep_bp(r.normal(0, 1, n), 200 * np.exp(u * 2.4), 0.7) * u ** 2
    d = np.zeros(n)
    for j, (at, a) in enumerate(((0.45, 0.8), (0.75, 1.0), (0.97, 1.2))):
        h = drum(54, 0.5, 1.0, seed=j + 4, amp=a)
        ia = int(at * SR)
        d[ia:ia + len(h)] += h[:max(0, n - ia)][:len(h)]
    y = norm(rum) * 0.9 + norm(inhale) * 0.5 + d * 0.9
    return soft(y * np.minimum(1, (sec - t) / 0.06), 1.5, 0.96)


def tell_lunge(sec=1.1):
    n = int(sec * SR)
    t = tt(sec)
    u = t / sec
    r = np.random.default_rng(65)
    growl = voice(62.0, sec, 'ah', vib=7.0, vib_depth=0.03, breath=0.4, seed=9, glide=lambda x: 1 + 0.5 * (x / sec) ** 1.5)
    growl = np.tanh(growl * 2.5) * (0.2 + 0.8 * u ** 1.2)
    air = sweep_bp(r.normal(0, 1, n), 110 * np.exp(u * 2.8), 0.7) * u ** 1.6
    rum = lp(r.normal(0, 1, n), 100) * u
    y = norm(growl) * 0.9 + norm(air) * 0.5 + norm(rum) * 0.5
    return soft(y * np.minimum(1, (sec - t) / 0.06), 1.4, 0.96)


NAMES = {'laser_giant_charge': laser_charge, 'laser_giant_fire': laser_fire, 'laser_giant_burn': laser_burn, 'laser_giant_end': laser_end,
         'laser_giant_sizzle': laser_sizzle, 'giant_tell_grab': tell_grab, 'giant_tell_swipe': tell_swipe, 'giant_tell_slam': tell_slam,
         'giant_tell_lunge': tell_lunge}
SUBTITLE = {'laser_giant_charge': 'subtitles.papon.boss.giant_laser_charge', 'laser_giant_fire': 'subtitles.papon.boss.giant_laser_fire',
            'laser_giant_burn': 'subtitles.papon.boss.giant_laser_burn', 'laser_giant_end': 'subtitles.papon.boss.giant_laser_end',
            'laser_giant_sizzle': 'subtitles.papon.boss.giant_laser_burn', 'giant_tell_grab': 'subtitles.papon.boss.giant_tell_grab',
            'giant_tell_swipe': 'subtitles.papon.boss.giant_tell_swipe', 'giant_tell_slam': 'subtitles.papon.boss.giant_tell_slam',
            'giant_tell_lunge': 'subtitles.papon.boss.giant_tell_lunge'}


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    look = '--look' in sys.argv
    sounds = read_sounds()
    for name, fn in NAMES.items():
        if args and name not in args:
            continue
        y = fn()
        sfx.write_ogg(os.path.join(ASSETS, 'sounds/boss/%s.ogg' % name), y)
        if look:
            d = os.path.join(ROOT, 'tools/.cache/giant_sounds')
            os.makedirs(d, exist_ok=True)
            sfx.spectrogram_png(y, os.path.join(d, name + '.png'))
        print('%-20s %5.2f s  peak %.2f  rms %.1f dB' % (name, len(y) / SR, np.abs(y).max(), 20 * np.log10(rms(y) + 1e-9)))
        sounds['boss.' + name] = {'sounds': [{'name': 'papon:boss/' + name, 'attenuation_distance': 128}], 'subtitle': SUBTITLE[name]}
    write_sounds(sounds)


if __name__ == '__main__':
    main()
