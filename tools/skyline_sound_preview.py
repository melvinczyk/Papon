#!/usr/bin/env python3
"""A listening preview of the Skyline's engine without starting the game: a drive (idle, two blips, flat out through four
gears with the shifts and the blow-off, the lift and the overrun's pops, back to idle) mixed from the loops the way
client/CaravanSounds mixes them (the two rpm points either side, equal power, pitched to the rpm; the throttle's pair into
the overrun's; the turbo on boost; the one-shots where the game plays them).

  python tools/skyline_sound_preview.py out.wav [--inside]      (PYTHONPATH=tools)
"""
import os
import sys

import numpy as np
import soundfile as sf

from gen_sounds import SR, OUT
from gen_skyline_sounds import RPM


def load(name):
    y, sr = sf.read(os.path.join(OUT, 'skyline', name + '.ogg'))
    return y


def play_loop(y, pitch, gain):
    """The loop played at a pitch that changes every sample (a phase accumulator), scaled by gain(t)."""
    idx = np.cumsum(pitch) % len(y)
    i0 = idx.astype(int)
    fr = idx - i0
    return (y[i0] * (1 - fr) + y[(i0 + 1) % len(y)] * fr) * gain


def main(out, inside):
    dt = 1 / SR
    # the drive's script: (seconds, rpm at the end, throttle) segments, linear in between
    script = [(1.6, 900, 0), (0.12, 4200, 1), (0.35, 1000, 0), (0.5, 900, 0), (0.12, 4600, 1), (0.4, 950, 0), (0.6, 900, 0),
              (0.25, 3400, 1), (1.3, 7400, 1), ('shift', 0.14), (0.0, 4600, 1), (1.6, 7500, 1), ('shift', 0.14), (0.0, 5200, 1),
              (2.0, 7500, 1), ('shift', 0.14), (0.0, 5600, 1), (2.4, 7400, 1), ('lift',), (3.2, 2600, 0), (1.0, 1200, 0), (1.5, 900, 0)]
    rpm, thr, events = [], [], []
    r, th = 900.0, 0.0
    t = 0.0
    for seg in script:
        if seg[0] == 'shift':
            n = int(seg[1] * SR)
            rpm += list(np.linspace(r, r * 0.97, n))
            thr += [0.0] * n
            events.append((t, 'shift'))
            events.append((t, 'bov'))
            t += seg[1]
            r *= 0.97
            continue
        if seg[0] == 'lift':
            events += [(t + 0.25 + k * 0.17, 'pop') for k in range(4)]
            continue
        sec, r1, th1 = seg
        n = int(sec * SR)
        if n:
            rpm += list(np.linspace(r, r1, n))
            thr += list(np.linspace(th1, th1, n))
            if th1 == 1 and r1 < r:
                pass
        r, th = r1, th1
        t += sec
    rpm, thr = np.array(rpm), np.array(thr)
    n = len(rpm)
    # eased like the game (load follows the throttle, boost builds behind it)
    load = np.zeros(n)
    boost = np.zeros(n)
    a = 1 - np.exp(-1 / (0.15 * SR))
    b_up, b_dn = 1 - np.exp(-1 / (0.7 * SR)), 1 - np.exp(-1 / (0.15 * SR))
    l = bo = 0.0
    for i in range(n):
        l += (thr[i] - l) * a
        want = thr[i] * np.clip((rpm[i] - 2600) / 2800, 0, 1) if thr[i] > 0.3 else 0
        bo += (want - bo) * (b_up if want > bo else b_dn)
        load[i], boost[i] = l, bo
    mix = np.zeros(n)
    suf = '_in' if inside else ''
    pts = np.array(RPM, float)
    i_lo = np.clip(np.searchsorted(pts, rpm) - 1, 0, len(pts) - 2)
    tt = np.clip((rpm - pts[i_lo]) / (pts[i_lo + 1] - pts[i_lo]), 0, 1)
    off = (1 - load) * np.clip((rpm - 1400) / 900, 0, 1)
    eng = 0.7 if inside else 0.9
    for k in range(len(pts)):
        w = np.where(i_lo == k, np.cos(tt * np.pi / 2), 0) + np.where(i_lo + 1 == k, np.sin(tt * np.pi / 2), 0)
        if not w.any():
            continue
        p = np.clip(rpm / pts[k], 0.5, 2)
        mix += play_loop(load_('on_%d%s' % (k, suf)), p, eng * w * np.sqrt(1 - off) * (0.7 + 0.3 * load))
        mix += play_loop(load_('off_%d%s' % (k, suf)), p, eng * w * np.sqrt(off) * 0.85)
    mix += play_loop(load_('turbo'), 0.7 + 0.8 * boost, (0.3 if inside else 0.36) * boost ** 1.5)
    for at, name in events:
        y = load_(name)
        k = int(at * SR)
        v = {'shift': 0.32, 'bov': 0.25, 'pop': 0.4}[name]
        m = min(len(y), n - k)
        mix[k:k + m] += y[:m] * v
    mix = np.concatenate([load_('start') * 1.1, mix])
    mix /= max(1.0, np.max(np.abs(mix)) / 0.95)
    sf.write(out, mix, SR)
    print(out, '%.1f s' % (len(mix) / SR))


_cache = {}


def load_(name):
    if name not in _cache:
        _cache[name] = load(name)
    return _cache[name]


if __name__ == '__main__':
    main(sys.argv[1], '--inside' in sys.argv)
