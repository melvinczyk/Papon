#!/usr/bin/env python3
"""Phase four's own sounds (the Final Guardian's awakening), synthesized (tools/sfx.py):

  boss/pant         his panting on his back, the scar torn open: a big man's breath out (rough, voiced underneath) and in
  boss/scratch      the band stopping dead as he is thrown flat: a record scratch
  boss/cine_souls   the souls of the stands lifting out of them (11 s): a sung choir moving Dm -> Bb -> A over a huehuetl heartbeat
                    that quickens, three death-whistle shrieks, wind and a sub drone
  boss/cine_soul    one soul streaking through the air into him (three variants): a whoosh with a wail in it, a sparkle in its tail
  boss/cine_absorb  a soul striking him: a breath drawn in, a deep drum, a bell
  boss/cine_moon    the moon turning into the Day of the Dead's: a huge temple bell over a sub boom
  boss/cine_swell   his power building to the burst (4.4 s, P4_HALFBAKED): a drone, a choir climbing, a drum roll that tightens to a
                    buzz, a whoosh, crackle, a death whistle, and a held breath (silence) in its last fifth of a second
  boss/cine_awaken  the flash, "Papon, the Final Guardian" arriving (8 s): a braam, a sub boom, a bell, the choir ringing out, glass
                    arpeggios and a shock wave of air (2026-10-06, the user: "completely redone")
  boss/cine_emerge  the new form standing up out of the light (4.5 s): a conch call, a drum, bones rattling, a rush of air

Writes sounds/boss/*.ogg and their sounds.json entries.
  python tools/gen_p4_sounds.py [name ...]      (with names, only those; --look also writes spectrograms to tools/.cache/p4_sounds)
"""
import os
import sys

import numpy as np
import scipy.signal as ss

import sfx
from geo_util import read_sounds, write_sounds
from sfx import SR, TAU, tt, mix, pad, norm, soft, fade, lp, hp, bp, saw, sweep_bp, reverb, choir, voice, death_whistle, drum, bell, braam, \
    conch, crackle, rattle, rms

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/papon')
rng = np.random.default_rng(4)

# D minor, the key of all of it (Hz)
D1, A1, D2, A2, D3, F3, A3, D4, F4, A4, D5, E5 = 36.71, 55.0, 73.42, 110.0, 146.83, 174.61, 220.0, 293.66, 349.23, 440.0, 587.33, 659.26


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


def fit(y, n):
    out = np.zeros(n)
    m = min(n, len(y))
    out[:m] = y[:m]
    return out


def swell_env(t, sec, a=0.1, r=0.1):
    return np.minimum(1, t / (sec * a)) * np.minimum(1, (sec - t) / (sec * r))


def cine_souls(sec=11.0):
    n = int(sec * SR)
    t = tt(sec)
    u = t / sec
    # the choir, three chords handing over to each other: Dm(add9) -> Bb -> A (the dominant, leaning on the next thing)
    chords = [(0.0, 4.8, [D2, A2, D3, F3, A3, 329.63]), (3.9, 8.2, [58.27, 116.54, D3, F3, 233.08, F4]),
              (7.3, sec, [A1, A2, 138.59, 164.81, A3, 277.18, 329.63])]
    ch = np.zeros(n)
    for k, (a, b, notes) in enumerate(chords):
        d = b - a
        y = choir(notes, d, 'ah' if k != 1 else 'oh', voices=3, rise=1.4, seed=40 + k * 9)
        e = np.minimum(1, (tt(d)) / 1.3) ** 1.5 * np.minimum(1, (d - tt(d)) / 1.3) ** 1.2
        ia = int(a * SR)
        ch[ia:ia + len(y)] += (y * e)[:n - ia]
    ch *= (0.3 + 0.7 * u ** 1.3)
    # the huehuetl heartbeat, quickening and growing: one every second down to one every 0.42
    drums = np.zeros(n)
    at, i = 0.5, 0
    while at < sec - 0.8:
        k = at / sec
        h = drum(58 + 8 * k, 0.8, 0.8, seed=i, amp=0.35 + 0.65 * k ** 1.2)
        ia = int(at * SR)
        drums[ia:ia + len(h)] += h[:n - ia]
        if i % 2 == 1 and k > 0.3:                               # the second beat of a heart
            h2 = drum(52, 0.5, 0.6, seed=i + 90, amp=0.3 * k)
            ib = ia + int(0.19 * SR)
            drums[ib:ib + len(h2)] += h2[:max(0, n - ib)][:len(h2)]
        at += 1.0 - 0.58 * k ** 1.1
        i += 1
    # the wind: a band of noise climbing, breathing
    wind = sweep_bp(rng.normal(0, 1, n), 260 + 1700 * u ** 1.2 + 260 * np.sin(TAU * 0.33 * t), 0.7)
    wind *= (0.45 + 0.55 * np.sin(TAU * 0.21 * t) ** 2) * (0.2 + 0.8 * u)
    # a sub drone under it all: D1 with its fifth
    sub = (np.sin(TAU * D1 * t) * 0.8 + np.sin(TAU * A1 * t) * 0.35) * np.minimum(1, t / 2.0)
    # the death whistles: three screams out of the stands, higher each time
    wh = np.zeros(n)
    for at_, f0, f1, d, g in ((2.5, 1700, 2500, 1.3, 0.3), (5.4, 2000, 3300, 1.5, 0.36), (8.3, 2300, 4100, 1.7, 0.42)):
        y = death_whistle(d, f0, f1, seed=int(at_ * 10), peak=1.0)
        ia = int(at_ * SR)
        wh[ia:ia + len(y)] += y[:n - ia] * g
    # glints: crystal bells flung up the scale as the souls leave their seats
    gl = np.zeros(n)
    for j, at_ in enumerate(np.linspace(1.2, sec - 2.2, 11)):
        b_ = bell([D5, A4, F4, 659.26, 880.0, 1174.7][j % 6], 1.8, seed=j, bright=0.4) * 0.07 * (0.5 + 0.5 * at_ / sec)
        ia = int(at_ * SR)
        gl[ia:ia + len(b_)] += b_[:n - ia]
    dry = ch * 1.0 + drums * 0.62 + wind * 0.5 + sub * 0.4 + wh + gl
    y = reverb(dry, sec=3.4, wet=0.3, seed=21)[:n]
    y *= np.minimum(1, t / 0.5) * np.minimum(1, (sec - t) / 1.3) ** 1.3
    return soft(y, 1.3, 0.95)


def cine_soul(seed=0, base=520.0):
    """One soul flung across the arena: a whoosh climbing, a sung wail rising with it, a sparkle in its tail."""
    r = np.random.default_rng(100 + seed)
    sec = 1.5
    n = int(sec * SR)
    t = tt(sec)
    u = t / sec
    e = np.sin(np.pi * np.minimum(1, u * 1.15)) ** 1.6 * np.minimum(1, (sec - t) / 0.2)
    air = sweep_bp(r.normal(0, 1, n), 380 + 3300 * u ** 1.25, 0.55) * e
    wail = voice(base, sec, 'oo', vib=6.0, vib_depth=0.012, seed=seed, glide=lambda tt_: 1 + 0.95 * (tt_ / sec) ** 1.3 + 0.0 * tt_) * e * 0.5
    wail2 = voice(base * 1.5, sec, 'ah', vib=5.4, vib_depth=0.012, seed=seed + 7, glide=lambda tt_: 1 + 0.95 * (tt_ / sec) ** 1.3) * (u ** 2) * e * 0.28
    tail = crackle(n, 70, 0.5, seed=seed, lo=4500) * (u ** 2) * e
    y = norm(air) * 0.8 + norm(wail) * 0.38 + norm(wail2) * 0.2 + norm(tail) * 0.18
    y = reverb(y, sec=1.0, wet=0.22, seed=30 + seed)[:n]
    return norm(y * np.minimum(1, (sec - t) / 0.1), 0.88)


def cine_absorb():
    """A soul striking him: a breath drawn in (a whoosh in reverse), then a deep drum and a bell over a bloom of sparkle."""
    sec = 1.9
    n = int(sec * SR)
    t = tt(sec)
    r = np.random.default_rng(9)
    pre = 0.26
    ip = int(pre * SR)
    ramp = np.linspace(0, 1, ip) ** 2.2
    suck = sweep_bp(r.normal(0, 1, ip), np.linspace(500, 5200, ip), 0.6) * ramp * 1.4
    hit = drum(50, 1.5, 1.0, seed=5, amp=1.1)
    ring = mix((bell(988.0, 1.6, bright=0.5), 0, 0.28), (bell(1480.0, 1.2, bright=0.3), 0, 0.12))
    flare = sweep_bp(r.normal(0, 1, int(0.5 * SR)), np.linspace(5200, 700, int(0.5 * SR)), 0.6) * np.exp(-tt(0.5) * 7) * 0.5
    y = mix((suck, 0), (hit, pre), (ring, pre), (flare, pre), length=n)
    y = reverb(y, sec=1.1, wet=0.2, seed=44)[:n]
    return norm(y * np.minimum(1, (sec - t) / 0.2), 0.92)


def cine_moon():
    n = int(7.0 * SR)
    t = np.arange(n) / SR
    base = 55.0
    bell_ = sum(g * np.sin(2 * np.pi * base * r * t + k) * np.exp(-t * d) for k, (r, g, d) in enumerate(
        ((1.0, 1.0, 0.45), (2.0, 0.7, 0.6), (2.76, 0.6, 0.8), (5.4, 0.35, 1.3), (8.93, 0.2, 2.0), (0.5, 0.6, 0.35))))
    bell_ *= 1 + 0.15 * np.sin(2 * np.pi * 1.3 * t)        # the beat of a huge bell
    boom = np.sin(np.cumsum(2 * np.pi * (45 * np.exp(-t * 1.2) + 25) / SR)) * np.exp(-t * 1.4)
    rumble = ss.sosfilt(ss.butter(2, 180, fs=SR, output='sos'), rng.normal(0, 1, n)) * np.exp(-t * 0.7)
    y = norm(bell_) * 0.8 + boom * 0.7 + norm(rumble) * 0.4
    return norm(y * (1 - np.exp(-t * 300)), 0.95)


def cine_swell(sec=4.4):
    """His power building to the burst: everything climbing, a drum roll tightening to a buzz, then the held breath before the flash."""
    n = int(sec * SR)
    t = tt(sec)
    u = t / sec
    r = np.random.default_rng(13)
    # the drone climbing a fifth, thickening
    f = D1 * (1 + 0.5 * u ** 2)
    drone = (saw(np.cumsum(TAU * f / SR)) * 0.5 + np.sin(np.cumsum(TAU * f / SR)) * 0.8)
    drone = sfx.sweep_lp(drone, 220 + 700 * u) * (0.2 + 0.8 * u ** 1.1)
    # the choir, a Dm9 chord lifted a minor third as it goes (every voice climbing together), crescendo
    rise = lambda tt_: 1 + 0.19 * (tt_ / sec) ** 2.2
    ch = choir([D2, A2, D3, F3, A3, D4, 329.63 * 2 / 2, 523.25], sec, 'ah', voices=3, rise=2.0, seed=60, glide=rise)
    ch *= (u ** 2.0) * 1.2
    # the drum roll: hits every 0.62 s closing by a factor of 0.9 to a buzz
    drums = np.zeros(n)
    at, gap, i = 0.15, 0.62, 0
    while at < sec - 0.25:
        k = at / sec
        h = drum(62 + 14 * k, min(0.7, gap * 3), 0.7, seed=i, amp=0.25 + 0.75 * k ** 1.1)
        ia = int(at * SR)
        drums[ia:ia + len(h)] += h[:n - ia]
        at += gap
        gap = max(0.05, gap * 0.9)
        i += 1
    air = sweep_bp(r.normal(0, 1, n), 180 * np.exp(u * 3.6), 0.6) * (u ** 2.2) * 1.6
    cr = crackle(n, 30, 1.0, seed=3) * (0.1 + u ** 2) * (0.5 + 3 * u)
    wh = np.zeros(n)
    w = death_whistle(2.3, 1500, 4300, seed=8, peak=1.0, curve=1.6)
    ia = int(1.9 * SR)
    wh[ia:ia + len(w)] += w[:n - ia] * 0.34
    y = norm(drone) * 0.7 + norm(ch) * 0.8 + drums * 0.7 + norm(air) * 0.55 + norm(cr) * 0.3 + wh
    y = reverb(y, sec=1.8, wet=0.16, seed=17)[:n]
    # the held breath: everything sucked out of the last fifth of a second, a thin ring left
    gate = np.where(t > sec - 0.2, np.maximum(0, 1 - (t - (sec - 0.2)) / 0.14) ** 2, 1.0)
    y = y * gate + np.sin(TAU * 5200 * t) * 0.05 * np.clip((t - (sec - 0.5)) / 0.4, 0, 1) * gate ** 0.2
    return soft(y, 1.25, 0.95)


def cine_awaken(sec=8.0):
    """The flash. A hit with everything in it: a boom and its air, a braam, a bell, then the choir ringing out over glass arpeggios."""
    n = int(sec * SR)
    t = tt(sec)
    r = np.random.default_rng(23)
    # the strike (0 s): a sub boom falling from 150 to 28 Hz, a crack, a burst of white air
    boom = np.sin(np.cumsum(TAU * (28 + 130 * np.exp(-t * 9)) / SR)) * np.exp(-t * 1.5) * np.minimum(1, t / 0.002)
    boom2 = np.sin(np.cumsum(TAU * (41 + 80 * np.exp(-t * 13)) / SR)) * np.exp(-t * 2.4) * 0.6
    crack = hp(r.normal(0, 1, n), 2500) * np.exp(-t * 38)
    blast = lp(r.normal(0, 1, n), 4200) * np.exp(-t * 6.5)
    # the shock wave of air going out: a band falling from 9 kHz to 200
    wave = sweep_bp(r.normal(0, 1, n), 9000 * np.exp(-t * 2.3) + 200, 0.7) * np.exp(-t * 1.9) * np.minimum(1, t / 0.01)
    # the braam: D, A, D, F, the low brass of the temple, snapping open
    br = braam([D1, D2, A2, D3, F3], 6.0, cutoff=(220, 2600, 420), seed=2, bite=1.0)
    br = fit(br, n)
    # the bell: a great one (D2) struck with the boom and a bright one a fifth over it
    b1 = fit(bell(D2, sec, bright=1.0), n) * 0.5
    b2 = fit(bell(A3, 5.0, bright=0.8), n) * 0.22
    # the choir comes in under the hit and rings out in a big open D minor
    ch = choir([D2, A2, D3, F3, A3, D4, F4, A4], 6.5, 'ah', voices=3, rise=0.5, seed=77)
    ch *= np.minimum(1, tt(6.5) / 0.9) ** 1.2 * np.minimum(1, (6.5 - tt(6.5)) / 2.2) ** 1.4 * 1.3
    ch = fit(ch, n)
    # glass arpeggios: D F A D E climbing, again an octave up, soft
    arp = np.zeros(n)
    notes = [D4, F4, A4, D5, F4 * 2, A4 * 2, D5 * 2]
    for j, f in enumerate(notes * 2):
        at = 0.55 + j * 0.2
        if at > sec - 2.5:
            break
        y = bell(f, 1.8, seed=j, bright=0.3) * 0.2 * (0.9 ** j)
        ia = int(at * SR)
        arp[ia:ia + len(y)] += y[:n - ia]
    # the scream: a death whistle tearing up under the choir, over by a second
    wh = np.zeros(n)
    w = death_whistle(1.5, 2200, 4600, seed=31, peak=1.0, curve=0.8)
    ia = int(0.08 * SR)
    wh[ia:ia + len(w)] += w[:n - ia] * 0.3
    # a low rumble tail
    rumble = lp(r.normal(0, 1, n), 140) * np.exp(-t * 0.55) * np.minimum(1, t / 0.2)
    y = boom * 1.5 + boom2 + crack * 0.55 + blast * 0.8 + wave * 0.75 + norm(br) * 1.0 + b1 + b2 + ch * 0.55 + arp + wh + rumble * 0.7
    y = reverb(y, sec=4.0, wet=0.28, seed=29)[:n]
    y *= np.minimum(1, (sec - t) / 1.5)
    return soft(y, 1.5, 0.97)


def cine_emerge(sec=4.5):
    """The Final Guardian standing up out of the light: a conch call and its answer, a drum, bones rattling, a rush of air."""
    n = int(sec * SR)
    t = tt(sec)
    r = np.random.default_rng(37)
    c1 = conch(D2, 2.0, seed=1)
    c2 = conch(A2, 2.2, seed=2)
    horn = mix((c1, 0.15, 1.0), (c2, 1.55, 1.0), length=n)
    d = np.zeros(n)
    for j, (at, f, a) in enumerate(((0.0, 55, 1.2), (0.62, 62, 0.8), (1.25, 58, 0.9), (1.5, 52, 1.1))):
        h = drum(f, 1.2, 1.0, seed=j, amp=a)
        ia = int(at * SR)
        d[ia:ia + len(h)] += h[:n - ia]
    bones = mix((rattle(1.1, 70, seed=2), 0.05, 0.5), (rattle(1.2, 80, seed=5), 1.4, 0.45), (rattle(0.8, 50, seed=7), 2.7, 0.4), length=n)
    rush = sweep_bp(r.normal(0, 1, n), 300 + 2600 * np.sin(np.pi * np.minimum(1, t / sec) ** 0.8), 0.7) * np.sin(np.pi * np.minimum(1, t / sec)) ** 1.3
    ch = choir([D3, A3, D4, F4], sec, 'oh', voices=3, rise=1.0, seed=90)
    ch = fit(ch, n) * np.minimum(1, t / 0.8) * np.minimum(1, (sec - t) / 1.5) * 0.9
    y = norm(horn) * 0.8 + d * 0.8 + norm(bones) * 0.35 + norm(rush) * 0.5 + norm(ch) * 0.42
    y = reverb(y, sec=2.6, wet=0.24, seed=33)[:n]
    y *= np.minimum(1, (sec - t) / 1.0)
    return soft(y, 1.35, 0.95)


NAMES = {'pant': pant, 'scratch': scratch, 'cine_souls': cine_souls, 'cine_absorb': cine_absorb, 'cine_moon': cine_moon,
         'cine_swell': cine_swell, 'cine_awaken': cine_awaken, 'cine_emerge': cine_emerge}
SOULS = [('cine_soul_a', 0, 440.0), ('cine_soul_b', 1, 520.0), ('cine_soul_c', 2, 620.0)]


def write(name, y, look=False):
    path = os.path.join(ASSETS, 'sounds/boss/%s.ogg' % name)
    sfx.write_ogg(path, y)
    if look:
        d = os.path.join(ROOT, 'tools/.cache/p4_sounds')
        os.makedirs(d, exist_ok=True)
        sfx.spectrogram_png(y, os.path.join(d, name + '.png'))
    print('%-14s %5.2f s  peak %.2f  rms %.1f dB' % (name, len(y) / SR, np.abs(y).max(), 20 * np.log10(rms(y) + 1e-9)))
    return path


if __name__ == '__main__':
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    look = '--look' in sys.argv
    todo = {k: v for k, v in NAMES.items() if not args or k in args}
    for k, f in todo.items():
        write(k, f(), look)
    if not args or 'cine_soul' in args:
        for name, seed, base in SOULS:
            write(name, cine_soul(seed, base), look)
    sounds = read_sounds()
    for k in ('pant', 'scratch', 'cine_souls', 'cine_absorb', 'cine_moon', 'cine_swell', 'cine_awaken', 'cine_emerge'):
        sounds['boss.' + k] = {'sounds': [{'name': 'papon:boss/' + k}], 'subtitle': 'subtitles.papon.boss.' + k}
    sounds['boss.cine_soul'] = {'sounds': [{'name': 'papon:boss/' + n} for n, _, _ in SOULS], 'subtitle': 'subtitles.papon.boss.cine_soul'}
    write_sounds(sounds)
    print('ok')
