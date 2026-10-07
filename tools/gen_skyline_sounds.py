#!/usr/bin/env python3
"""The Skyline's own sounds (Ascended Papon's car; the van's are gen_sounds.py's): an RB26, the twin-turbo straight
six. All maths, no samples (pip install numpy soundfile).

Rebuilt 2026-10-06 (the user: "create new sounds for it, the sounds it has currently sound bad"). The engine was a pulse
train through a fixed filter: every firing the same click, a buzz. It is now a small physical model of the car (`Engine`):
  - each firing a blowdown: a pressure pulse and the turbulent rush of gas behind it, its strength varying from cycle to
    cycle (combustion is never twice the same), its timing a hair off
  - six headers of slightly different lengths, each with its own echo off the collector, in two banks of three (1-2-3
    and 4-5-6, firing order 1-5-3-6-2-4) into the twin turbos, which round the pulses off
  - the exhaust: the pipe's own resonances (a wave running its length and back), the muffler's chamber and its roll-off,
    opening up under load; the tail pipe's radiation
  - the intake's roar on the throttle, the valve train's fine ticking under it, the overrun's thin, uneven firings
That runs for loops (circular: whole engine cycles, seamless) and for one-shots with the rpm and the load changing
(the start, the blips, the reveal's rev). client/CaravanSounds plays the loops:
  on_<i>.ogg, off_<i>.ogg     the six on the throttle and off it (the overrun) at ModSounds.SKYLINE_RPM (eight points,
                              900 to 7800): only the two nearest play, pitched a fifth at most off what they were made at;
                              louder with the revs (idle -25 dBFS RMS to -17 at the top), the overrun 4 dB under
  *_in.ogg                    the same from the cockpit: through the shell (the highs gone, its boom under it)
  turbo.ogg                   the compressors' whine and the air (only on boost; pitched by it)
  road.ogg, wind.ogg          the tyres on the ground, the wind
One-shots: start, rev (the reveal), shift, blip, pop, backfire, bov, door_open, door_close, chirp (the fob), glow (the
underglow), summon (the fob calling it: it reforms out of purple light), horn (two disc horns), screech (tyres).

  python tools/gen_skyline_sounds.py           (PYTHONPATH=tools)
  python tools/gen_skyline_sounds.py --sheet out.png    (spectrograms of the loops, to look at)
"""
import os
import sys

import numpy as np
import soundfile as sf

from gen_sounds import SR, OUT, save, t_of, fft_filter, padded_filter, peak, lowpass, highpass, band, fade

rng = np.random.default_rng(34)

# ModSounds.SKYLINE_RPM
RPM = [900, 1500, 2300, 3200, 4200, 5300, 6500, 7800]
FIRING = [0, 4, 2, 5, 1, 3]                  # cylinders 1-5-3-6-2-4 (0-based)
C_HOT = 560.0                                # speed of sound in hot exhaust gas, m/s
HEADER_M = [0.62, 0.58, 0.66, 0.64, 0.57, 0.68]
BANK = [0, 0, 0, 1, 1, 1]
PIPE_M = 3.3                                 # turbo to tail
CYL = np.array([1.0, 0.97, 1.03, 0.98, 1.02, 0.96])


# ---------------------------------------------------------------- the model
def blowdown(n, load, rpm):
    """One cylinder's exhaust pulse: a sharp rise and a decay a little longer at low revs."""
    t = t_of(n)
    tau = 0.00055 + 0.0006 * (1 - min(rpm, 8000) / 8000)
    p = (t / tau) * np.exp(1 - t / tau)
    # the valve's opening: a second, softer hump of the gas still pushed out by the piston
    tau2 = tau * 4.5
    p += 0.35 * (t / tau2) * np.exp(1 - t / tau2)
    return p * (0.35 + 0.65 * load)


def header_ir(n, length, reflect=0.42):
    """A header pipe: the pulse, then its echoes off the collector (inverted, softened each time)."""
    h = np.zeros(n)
    d = 2 * length / C_HOT * SR
    g = 1.0
    for k in range(5):
        pos = d * k
        i = int(pos)
        if i + 1 >= n:
            break
        fr = pos - i
        h[i] += g * (1 - fr)
        h[i + 1] += g * fr
        g *= -reflect
    # every trip down the pipe takes some top off
    h = np.convolve(h, np.exp(-np.arange(24) / 5.0) / 5.0)[:n]
    return h


def exhaust(load, inside=False):
    """The turbos, the pipe and the muffler, as a frequency response (applied to the merged collector signal)."""
    tau = 2 * PIPE_M / C_HOT

    def r(f):
        z = np.exp(-2j * np.pi * f * tau)
        g = 0.42
        pipe = np.abs(1 / (1 + g * z))                       # the pipe's own resonances (open both ends, reflected)
        turbo = lowpass(f, 2400 + 2600 * load, 2)            # the turbines round the pulses off
        muff = lowpass(f, 750 + 1500 * load, 2) * peak(f, 140, 1.8, 1.1) * peak(f, 420, 2.5, 0.35 + 0.5 * load)
        rad = np.sqrt(f / (f + 220.0))                       # the tail's radiation: the lows leave weaker
        out = pipe * turbo * muff * rad * highpass(f, 28, 2)
        if inside:
            out = out * lowpass(f, 380 + 300 * load, 2) * peak(f, 72, 1.6, 2.4) * peak(f, 160, 2.2, 0.8) * highpass(f, 24, 2)
        return out
    return r


class Engine:
    """The six, for `n` samples at rpm(t) and load(t) (arrays). circular: a loop (whole cycles, everything wraps)."""

    def __init__(self, n, rpm, load, circular=False, burble=0.0, idle_lope=0.0, inside=False, seed=0):
        self.n, self.rpm, self.load, self.circ = n, np.asarray(rpm, float), np.asarray(load, float), circular
        self.burble, self.lope, self.inside = burble, idle_lope, inside
        self.r = np.random.default_rng(seed)

    def firings(self):
        """(sample position, cylinder, strength) of every firing."""
        fire = self.rpm / 60 * 3                                 # firings a second
        phase = np.cumsum(fire) / SR
        k = np.nonzero(np.diff(np.floor(phase)) > 0)[0]
        out = []
        for j, i in enumerate(k):
            cyl = FIRING[j % 6]
            l = self.load[i]
            a = CYL[cyl] * (1 + self.r.normal(0, 0.035 + 0.05 * self.lope + 0.25 * self.burble))
            if self.lope:
                a *= 1 - self.lope * 0.3 * (j % 12 in (2, 3, 9))  # the cam's lope at idle
            if self.burble:
                a *= 0.22 + 0.25 * self.r.random() ** 2
                if self.r.random() < 0.1 * self.burble:
                    a *= 0.15                                   # nearly nothing: a lean cylinder on the overrun
            jit = self.r.normal(0, 0.00012 + 0.00025 * self.burble) * SR
            out.append((i + jit, cyl, max(0.0, a), l))
        return out

    def render(self):
        n = self.n
        trains = np.zeros((6, n))
        env_train = np.zeros(n)
        mean_rpm = float(np.mean(self.rpm))
        mean_load = float(np.mean(self.load))
        for pos, cyl, a, l in self.firings():
            i = int(np.floor(pos))
            fr = pos - i
            for w, ii in ((1 - fr, i), (fr, i + 1)):
                if self.circ:
                    ii %= n
                elif not 0 <= ii < n:
                    continue
                trains[cyl, ii] += a * w * (0.35 + 0.65 * l)
                env_train[ii] += a * w * (0.3 + 0.7 * l)
        m = 8192
        pulse = blowdown(m, 1.0, mean_rpm)
        conv = (lambda x, h: np.fft.irfft(np.fft.rfft(x) * np.fft.rfft(h, n), n)) if self.circ else \
            (lambda x, h: np.fft.irfft(np.fft.rfft(x, n + m) * np.fft.rfft(h, n + m), n + m)[:n])
        banks = [np.zeros(n), np.zeros(n)]
        for c in range(6):
            k = np.convolve(pulse, header_ir(m, HEADER_M[c]))[:m]
            banks[BANK[c]] += conv(trains[c], k)
        # the two banks meet after their turbos, one a little further down the pipe
        lag = int(0.0007 * SR)
        merged = banks[0] + (np.roll(banks[1], lag) if self.circ else np.concatenate([np.zeros(lag), banks[1][:-lag]]))
        # the gas behind each pulse: noise shaped by the firings
        burst = np.exp(-np.arange(m) / (0.0022 * SR)) * (1 - np.exp(-np.arange(m) / 30.0))
        env = conv(env_train, burst)
        gas = self.r.normal(0, 1, n) * env
        filt = fft_filter if self.circ else padded_filter
        gas = filt(gas, lambda f: band(f, 180, 5500) * peak(f, 900, 1.2, 0.6))
        sig = merged / (np.std(merged) or 1) + gas / (np.std(gas) or 1) * (0.32 + 0.25 * self.burble)
        sig = filt(sig, exhaust(mean_load, self.inside))
        # a little saturation under load (the pipe driven hard): warmth, not fizz
        k = 0.4 + 1.2 * mean_load
        sig = sig / (np.max(np.abs(sig)) or 1)
        sig = np.tanh(sig * (1 + k)) / np.tanh(1 + k)
        s = np.std(sig) or 1
        t = t_of(n)
        fire = self.rpm / 60 * 3
        ph = np.cumsum(fire) / SR
        if not self.inside:
            # the intake's roar on the throttle, pulsing with the firings
            roar = filt(self.r.normal(0, 1, n), lambda f: band(f, 220, 1600) * peak(f, 480, 2.0, 1.2))
            roar *= 1 + 0.7 * np.sin(2 * np.pi * ph)
            sig += roar / (np.std(roar) or 1) * s * 0.18 * mean_load ** 1.5
            # the valve train: 24 valves, fine ticks
            ticks = np.zeros(n)
            vph = np.cumsum(self.rpm / 120 * 24) / SR
            for i in np.nonzero(np.diff(np.floor(vph)) > 0)[0]:
                ticks[i] += 0.6 + 0.4 * self.r.random()
            ticks = filt(conv(ticks, np.exp(-np.arange(m) / 6.0) * self.r.normal(0, 1, m)), lambda f: band(f, 2500, 9000))
            sig += ticks / (np.std(ticks) or 1) * s * (0.05 if mean_rpm < 2000 else 0.025)
        else:
            # through the floor and the seat: the firing's thrum, and the cabin's air
            sig += np.sin(2 * np.pi * ph) * s * 0.18 * (0.5 + mean_load)
            sig += np.sin(2 * np.pi * ph * 2 + 0.7) * s * 0.06
            air = filt(self.r.normal(0, 1, n), lambda f: band(f, 120, 3000) * lowpass(f, 900, 1))
            sig += air / (np.std(air) or 1) * s * 0.035
        return sig


# ---------------------------------------------------------------- writing
def seam_rank(y):
    """Where the wrap's jolt (the error predicting each sample from the two before it) ranks among every other sample's."""
    e = np.abs(y[2:] - (2 * y[1:-1] - y[:-2]))
    return (e < abs(y[0] - (2 * y[-1] - y[-2]))).mean()


def save_level(rel, x, rms_db=-20.0):
    """A loop at a set loudness (RMS, dBFS), peaks softly limited. It is circular, so it may start anywhere: of its
    quietest upward zero crossings, the start whose wrap survives the encoder most smoothly is kept (OpenAL loops the
    decoded buffer: no tick)."""
    x = np.asarray(x, np.float64)
    rms = np.sqrt(np.mean(x * x)) or 1.0
    x = x * (10 ** (rms_db / 20) / rms)
    if np.max(np.abs(x)) > 0.9:
        x = np.tanh(x * 1.05) / np.tanh(1.05)
    path = os.path.join(OUT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    env = np.convolve(np.abs(x), np.ones(64) / 64, mode='same')
    up = np.nonzero((x[:-1] <= 0) & (x[1:] > 0))[0]
    best, best_rank = x, 2.0
    for k in up[np.argsort(env[up])[:24]]:
        cand = np.roll(x, -int(k + 1))
        sf.write(path, cand, SR, format='OGG', subtype='VORBIS')
        y, _ = sf.read(path)
        r = seam_rank(y)
        if r < best_rank:
            best, best_rank = cand, r
        if r < 0.5:
            break
    sf.write(path, best, SR, format='OGG', subtype='VORBIS')
    print(path, '%.1f s' % (len(x) / SR), '%d KB' % (os.path.getsize(path) // 1024), 'seam at the %.0fth percentile' % (100 * best_rank))


def write(x, rel):
    """A one-shot at the level given (save() normalizes every file to full scale), peaks softly held under 0.95."""
    x = np.asarray(x, np.float64)
    if np.max(np.abs(x)) > 0.95:
        x = np.tanh(x / 0.95) * 0.95
    path = os.path.join(OUT, rel)
    with sf.SoundFile(path, 'w', SR, 1, format='OGG', subtype='VORBIS') as f:
        for i in range(0, len(x), SR):
            f.write(x[i:i + SR])
    print(path, '%.1f s' % (len(x) / SR), 'peak %.2f' % np.max(np.abs(x)))


def loop_len(rpm, seconds):
    """Samples for whole engine cycles (two turns) close to `seconds`."""
    cyc = 120.0 / rpm
    k = max(1, int(round(seconds / cyc)))
    return int(round(k * cyc * SR)), k * cyc


def engine_loop(rpm, on, inside, seed):
    n, sec = loop_len(rpm, 3.0)
    # the sample count is rounded: nudge the rpm so the cycles fit exactly
    rpm_exact = rpm * sec / (n / SR)
    load = (0.3 + 0.7 * min(1.0, (rpm - 700) / 2500)) if on else 0.08
    if rpm <= 950:
        load = 0.28 if on else 0.1
    e = Engine(n, np.full(n, rpm_exact), np.full(n, load), circular=True, burble=0.0 if on else 1.0,
               idle_lope=1.0 if rpm <= 950 else 0.25 if rpm <= 1500 else 0.0, inside=inside, seed=seed)
    return e.render()


def sweep(rpm_of, load_of, seconds, seed=1, inside=False, burble=0.0):
    n = int(seconds * SR)
    t = t_of(n)
    return Engine(n, rpm_of(t), load_of(t), circular=False, burble=burble, seed=seed, inside=inside).render()


def loop_noise(seconds, response, mod=None, seed=0):
    r = np.random.default_rng(seed)
    n = int(seconds * SR)
    x = fft_filter(r.normal(0, 1, n), response)
    if mod is not None:
        x *= mod(t_of(n))
    return x


# ---------------------------------------------------------------- one-shot pieces
def bov(seconds=1.0):
    """The blow-off valve: a sharp release of boost (PSSHH) and the compressor's flutter after it (stu-tu-tu)."""
    n = int(seconds * SR)
    t = t_of(n)
    air = padded_filter(rng.normal(0, 1, n), lambda f: band(f, 1200, 10000) * peak(f, 3800, 1.6, 1.8))
    env = np.exp(-t / 0.13) * np.clip(t / 0.003, 0, 1)
    rate = 24 - 12 * np.clip(t / 0.6, 0, 1)
    ph = np.cumsum(rate) / SR
    flutter = (0.5 + 0.5 * np.cos(2 * np.pi * ph)) ** 3 * np.exp(-t / 0.22) * np.clip((t - 0.05) / 0.03, 0, 1)
    chop = padded_filter(rng.normal(0, 1, n), lambda f: band(f, 250, 2200) * peak(f, 700, 2, 1.5)) * flutter
    whine = np.sin(2 * np.pi * np.cumsum(5200 - 3000 * np.clip(t / 0.4, 0, 1)) / SR) * np.exp(-t / 0.1) * 0.12
    return fade(air * env + chop * 0.6 + whine, 0.001, 0.1)


def bang(seconds=0.5, size=1.0, seed=0):
    """An exhaust pop: a crack in the pipe, its thump, ringing through the exhaust's own resonances."""
    r = np.random.default_rng(seed)
    n = int(seconds * SR)
    t = t_of(n)
    crack = padded_filter(r.normal(0, 1, n), lambda f: band(f, 300, 7000)) * np.exp(-t / (0.006 * size))
    thump = np.sin(2 * np.pi * (60 + 90 * np.exp(-t / 0.015)) * t) * np.exp(-t / (0.05 * size)) * 1.6
    x = crack + thump
    x = padded_filter(x, exhaust(0.8))
    x = x / (np.max(np.abs(x)) or 1)
    return np.tanh(x * 2.2)


def thud(seconds, f0, tau, seed=0):
    """A heavy door's panel: a damped low body and the panel ringing a little over it."""
    r = np.random.default_rng(seed)
    n = int(seconds * SR)
    t = t_of(n)
    body = np.sin(2 * np.pi * (f0 + 35 * np.exp(-t / 0.012)) * t) * np.exp(-t / tau)
    panel = sum(np.sin(2 * np.pi * f * t + r.random() * 6) * np.exp(-t / (tau * d)) * a
                for f, d, a in ((f0 * 2.7, 0.5, 0.25), (f0 * 4.1, 0.35, 0.15), (f0 * 6.3, 0.25, 0.08)))
    knock = padded_filter(r.normal(0, 1, n), lambda f: band(f, 90, 1100)) * np.exp(-t / 0.008)
    return body * 1.3 + panel + knock * 0.7


def click(seconds, at, bright=1.0, seed=0, tau=0.0025):
    """A latch's metal click."""
    r = np.random.default_rng(seed)
    n = int(seconds * SR)
    t = t_of(n)
    tt = np.clip(t - at, 0, None)
    x = padded_filter(r.normal(0, 1, n), lambda f: band(f, 1800, 9000) * peak(f, 4200, 3, 1.2 * bright)) * np.exp(-tt / tau) * (t >= at)
    x += np.sin(2 * np.pi * 2600 * tt) * np.exp(-tt / 0.004) * (t >= at) * 0.3
    return x


def at(x, sec, total):
    out = np.zeros(int(total * SR))
    k = int(sec * SR)
    m = min(len(x), len(out) - k)
    out[k:k + m] += x[:m]
    return out


def horn(seconds=0.5):
    """Two disc horns, ~410 and ~500 Hz: each a buzzing diaphragm (a narrow pulse rich in harmonics) through its little
    trumpet's resonance; a fast attack, a short tail."""
    n = int(seconds * SR)
    t = t_of(n)
    out = np.zeros(n)
    for f0, ph in ((412.0, 0.0), (498.0, 1.3)):
        f = f0 * (1 + 0.004 * np.sin(2 * np.pi * 6 * t + ph))
        p = np.cumsum(f) / SR
        pulse = (np.mod(p, 1.0) < 0.22).astype(float) - 0.22
        pulse = padded_filter(pulse, lambda fr: peak(fr, 2300, 2.5, 3.0) * peak(fr, 900, 2.0, 1.2) * lowpass(fr, 5000, 2) * highpass(fr, 250, 2))
        out += pulse / (np.std(pulse) or 1)
    env = np.clip(t / 0.012, 0, 1) * np.clip((seconds - t) / 0.06, 0, 1)
    return np.tanh(out * 0.6 * env)


def screech(seconds=1.2):
    """Tyres losing grip: a squeal wandering round 900 Hz (rubber stick-slip), its harmonics, the scrub's noise."""
    n = int(seconds * SR)
    t = t_of(n)
    drift = padded_filter(rng.normal(0, 1, n), lambda f: lowpass(f, 6, 2))
    drift = drift / (np.max(np.abs(drift)) or 1)
    f = 880 + 140 * drift + 25 * np.sin(2 * np.pi * 17 * t)
    p = np.cumsum(f) / SR
    tone = np.sin(2 * np.pi * p) + 0.45 * np.sin(4 * np.pi * p + 0.5) + 0.2 * np.sin(6 * np.pi * p + 1.1)
    am = 0.7 + 0.3 * padded_filter(rng.normal(0, 1, n), lambda f: lowpass(f, 25, 2)) / 0.05
    am = np.clip(am, 0.2, 1.4)
    scrub = padded_filter(rng.normal(0, 1, n), lambda fr: band(fr, 400, 6000))
    env = np.clip(t / 0.05, 0, 1) * np.clip((seconds - t) / 0.35, 0, 1)
    return (tone * am * 0.6 + scrub * 0.35) * env


# ---------------------------------------------------------------- the files
def loops():
    for i, rpm in enumerate(RPM):
        level = -25 + 8 * i / (len(RPM) - 1)
        for on in (True, False):
            for inside in (False, True):
                x = engine_loop(rpm, on, inside, seed=100 + i * 4 + 2 * on + inside)
                name = 'skyline/%s_%d%s.ogg' % ('on' if on else 'off', i, '_in' if inside else '')
                save_level(name, x, level - (0 if on else 4) - (1.5 if inside else 0))
    # the turbos: two compressors' whine (blade-pass tone with its harmonics, wavering) over the rush of air
    sec = 3.0
    n = int(sec * SR)
    t = t_of(n)
    whine = np.zeros(n)
    for f0, ph in ((2900, 0.0), (2975, 1.7)):                     # two turbos, never quite in step
        f = round(f0 * sec) / sec
        wob = 0.16 * np.sin(2 * np.pi * 2 / sec * t + ph)             # a slow waver (phase, whole cycles: it loops)
        p = 2 * np.pi * f * t
        whine += np.sin(p + wob) + 0.3 * np.sin(2 * p + 2 * wob)
    hiss = loop_noise(sec, lambda f: band(f, 900, 9000) * peak(f, 3200, 1.2, 1.2), seed=5)
    air = loop_noise(sec, lambda f: band(f, 200, 2500), seed=6)
    save_level('skyline/turbo.ogg', whine * 0.22 + hiss / np.std(hiss) * 0.5 + air / np.std(air) * 0.35, -24.0)
    # the road: the tyres' roar, the tread's thrum
    road = loop_noise(3.0, lambda f: lowpass(f, 380, 2) * highpass(f, 30) * peak(f, 90, 1.5, 1.0) + 0.15 * band(f, 600, 1800),
                      lambda tt: 1 + 0.15 * np.sin(2 * np.pi * 30 / 3.0 * tt), seed=7)
    save_level('skyline/road.ogg', road, -22.0)
    wind = loop_noise(3.0, lambda f: band(f, 150, 2600) * peak(f, 450, 1.2, 0.6),
                      lambda tt: 1 + 0.25 * np.sin(2 * np.pi / 3.0 * tt) + 0.1 * np.sin(2 * np.pi * 3 / 3.0 * tt), seed=8)
    save_level('skyline/wind.ogg', wind, -24.0)


def one_shots():
    # ---- the start: the starter grinding the six over (compression in every stroke), the catch, a flare and the settle
    total = 2.6
    n = int(total * SR)
    t = t_of(n)
    crank_s = 0.55
    crank_rpm = 260 + 40 * np.clip(t / crank_s, 0, 1)
    comp = np.cumsum(crank_rpm / 60 * 3) / SR                  # compression strokes
    load_mod = 0.55 + 0.45 * np.cos(2 * np.pi * comp) ** 2
    motor = np.sin(2 * np.pi * np.cumsum(1050 * (crank_rpm / 300) * (0.92 + 0.08 * load_mod)) / SR)
    gear = padded_filter(rng.normal(0, 1, n), lambda f: band(f, 400, 3500) * peak(f, 1600, 3, 1.5))
    crank = (motor * 0.25 + gear / np.std(gear) * 0.2) * load_mod * (t < crank_s) * np.clip(t / 0.02, 0, 1)
    crank += padded_filter(rng.normal(0, 1, n), lambda f: lowpass(f, 300, 2)) * load_mod * (t < crank_s) * 0.25

    def rpm_s(tt):
        up = 300 + 2000 * np.clip((tt - crank_s) / 0.25, 0, 1) ** 0.6
        down = 2300 - 1400 * np.clip((tt - crank_s - 0.35) / 1.2, 0, 1) ** 0.7
        return np.where(tt < crank_s + 0.35, up, down)

    def load_s(tt):
        return np.clip(0.9 - np.abs(tt - crank_s - 0.2) / 0.5, 0.25, 0.9) * (tt > crank_s - 0.02)

    eng = sweep(rpm_s, load_s, total, seed=11)
    eng *= np.clip((t - crank_s + 0.03) / 0.04, 0, 1)
    eng /= np.max(np.abs(eng)) or 1
    start = fade(crank / (np.max(np.abs(crank)) or 1) * 0.5 + eng, 0.003, 0.35)
    # its settle at the idle loop's loudness as the game plays them (the loop ~0.7 of its file, this one at 1): the flare
    # stands out, the idle it settles into does not jump
    tail = start[-int(0.9 * SR):-int(0.35 * SR)]
    write(start * (10 ** (-28 / 20) / (np.sqrt(np.mean(tail * tail)) or 1)), 'skyline/start.ogg')

    # ---- an upshift: the gearbox's double clack through the shifter, a breath of the clutch
    tot = 0.32
    sh = click(tot, 0.0, 0.8, seed=21, tau=0.004) + click(tot, 0.07, 0.6, seed=22, tau=0.003) * 0.7
    clunk = padded_filter(rng.normal(0, 1, int(tot * SR)), lambda f: band(f, 80, 600)) * np.exp(-t_of(int(tot * SR)) / 0.025)
    save('skyline/shift.ogg', fade(sh * 0.8 + clunk / np.std(clunk) * 0.35, 0.0005, 0.05))

    # ---- a downshift's blip: up and back down
    def rpm_b(tt):
        return 2700 + 2300 * np.sin(np.pi * np.clip(tt / 0.38, 0, 1)) ** 1.4

    save('skyline/blip.ogg', fade(sweep(rpm_b, lambda tt: np.where(tt < 0.12, 0.9, 0.15), 0.6, seed=12), 0.004, 0.15))
    # ---- one overrun pop, a bigger backfire
    save('skyline/pop.ogg', fade(bang(0.35, 0.5, seed=3) * 0.8, 0.0005, 0.1))
    save('skyline/backfire.ogg', fade(bang(0.7, 1.4, seed=4), 0.0005, 0.25))
    save('skyline/bov.ogg', bov(1.0))

    # ---- the doors: the handle and the latch letting go, the seal parting, a hinge; closing: the thunk and the latch
    tot = 0.6
    op = click(tot, 0.0, 1.0, seed=31) * 0.8 + click(tot, 0.05, 0.6, seed=32, tau=0.004) * 0.6
    seal = padded_filter(rng.normal(0, 1, int(tot * SR)), lambda f: band(f, 300, 2500)) * np.exp(-((t_of(int(tot * SR)) - 0.09) / 0.035) ** 2)
    op += seal / np.std(seal) * 0.25 + at(thud(0.3, 160, 0.02, seed=33) * 0.35, 0.06, tot)
    save('skyline/door_open.ogg', fade(op, 0.0005, 0.1))
    tot = 0.8
    cl = thud(tot, 78, 0.07, seed=34) + click(tot, 0.006, 0.7, seed=35, tau=0.003) * 0.7 + click(tot, 0.03, 0.4, seed=36) * 0.4
    cl = padded_filter(cl, lambda f: lowpass(f, 5000, 2))
    save('skyline/door_close.ogg', fade(cl, 0.0005, 0.15))

    # ---- the fob: two chirps (a square 3.1 kHz, quickly decaying) and the locks' actuators
    tot = 0.75
    t = t_of(int(tot * SR))
    out = np.zeros(len(t))
    for t0 in (0.0, 0.15):
        tt = np.clip(t - t0, 0, None)
        env = (t >= t0) * np.exp(-tt / 0.05) * np.clip(tt / 0.003, 0, 1)
        out += np.tanh(np.sin(2 * np.pi * 3100 * tt) * 4) * env * 0.5
    for k, t0 in enumerate((0.33, 0.36, 0.38)):
        out += click(tot, t0, 0.5, seed=40 + k, tau=0.005) * 0.4
    save('skyline/chirp.ogg', fade(padded_filter(out, lambda f: lowpass(f, 7000, 2)), 0.001, 0.05))

    # ---- the underglow: a relay click, the neon striking and stuttering (the 100 Hz buzz cut in and out), a quiet hum
    tot = 1.8
    t = t_of(int(tot * SR))
    n = len(t)
    cl = padded_filter(rng.normal(0, 1, n), lambda f: band(f, 1500, 6000)) * np.exp(-t / 0.004)
    buzz = sum(np.sin(2 * np.pi * 100 * h * t) / h for h in (1, 2, 3, 5, 7)) * 0.35
    zap = padded_filter(rng.normal(0, 1, n), lambda f: band(f, 2000, 9000)) * 0.15
    gate = np.zeros(n)
    for a, b2 in [(0.05, 0.09), (0.14, 0.17), (0.22, 0.3), (0.34, 0.36), (0.4, tot)]:
        gate[int(a * SR):int(b2 * SR)] = 1
    gate = np.convolve(gate, np.ones(80) / 80, mode='same')
    level = np.where(t < 0.6, 1.0, 0.35 + 0.65 * np.exp(-(t - 0.6) / 0.25))
    out = cl * 0.8 + (buzz + zap * (t < 0.45)) * gate * level
    save('skyline/glow.ogg', fade(out, 0.001, 0.4))

    # ---- the summons: a rush drawn in (rising, filtered air), a shimmer of purple (a bright chord, bowed), the metal
    # folding back out with a crunch and a deep settle
    tot = 1.6
    t = t_of(int(tot * SR))
    n = len(t)
    rise = np.clip(t / 1.0, 0, 1) ** 2.2 * (t < 1.0) + np.clip(1 - (t - 1.0) / 0.25, 0, 1) * (t >= 1.0)
    cen = 300 + 4200 * np.clip(t / 1.0, 0, 1) ** 1.5
    rush = np.zeros(n)
    blk = 2048
    noise = rng.normal(0, 1, n + blk)
    for s0 in range(0, n, blk):
        c = float(np.mean(cen[s0:s0 + blk]))
        seg = padded_filter(noise[s0:s0 + blk * 2], lambda f: peak(f, c, 1.5, 6) * band(f, 100, 12000), pad=0.05)[:blk]
        rush[s0:s0 + blk] = seg[:min(blk, n - s0)]
    rush = rush / np.std(rush) * rise
    shimmer = sum(np.sin(2 * np.pi * f * t * (1 + 0.003 * np.sin(2 * np.pi * 5.5 * t + f))) for f in (523.3, 659.3, 784.0, 1046.5, 1568.0))
    shimmer *= np.clip(t / 0.9, 0, 1) ** 2 * np.exp(-np.clip(t - 1.0, 0, None) / 0.35) * 0.12
    crunch = np.zeros(n)
    for k in range(14):
        crunch += click(tot, 0.95 + rng.random() * 0.12, 0.3 + rng.random() * 0.5, seed=60 + k, tau=0.006) * (0.3 + rng.random() * 0.4)
    settle = at(thud(0.6, 55, 0.12, seed=70) * 0.9, 1.04, tot)
    out = rush * 0.35 + shimmer + crunch * 0.6 + settle
    save('skyline/summon.ogg', fade(out, 0.01, 0.2))

    save('skyline/horn.ogg', fade(horn(0.5), 0.002, 0.04))
    save('skyline/screech.ogg', fade(screech(1.2), 0.01, 0.2))

    # ---- the reveal's rev (the new engine's roar in his cutscene): two blips, flat out to the limiter, the turbos
    # spooling, the lift and the blow-off, popping on the overrun
    total = 4.6

    def rpm_r(tt):
        r = np.full_like(tt, 900.0)
        for t0 in (0.15, 0.75):
            k = np.clip((tt - t0) / 0.12, 0, 1) ** 0.6 * (tt < t0 + 0.2) + np.clip(1 - (tt - t0 - 0.2) / 0.3, 0, 1) * (tt >= t0 + 0.2)
            r = np.maximum(r, 900 + 3600 * k)
        up = 900 + 7100 * np.clip((tt - 1.45) / 1.2, 0, 1) ** 0.75
        # the limiter: a stutter at the top
        lim = (tt > 2.45) & (tt < 2.7)
        up = np.where(lim, 7800 + 250 * np.sign(np.sin(2 * np.pi * 14 * tt)), up)
        down = 8000 - 7000 * np.clip((tt - 2.7) / 1.4, 0, 1) ** 0.6
        long = np.where(tt < 2.7, up, down) * (tt >= 1.45)
        return np.maximum(r, long)

    def load_r(tt):
        return np.where(tt < 2.7, np.clip(rpm_r(tt) / 4000, 0.25, 1), 0.08)

    rev = sweep(rpm_r, load_r, total, seed=13)
    rev /= np.max(np.abs(rev)) or 1
    n = len(rev)
    t = t_of(n)
    boost = np.clip((t - 1.7) / 0.8, 0, 1) ** 1.4 * (t < 2.72)
    spool = np.sin(2 * np.pi * np.cumsum(1800 + 3200 * boost) / SR) * boost ** 1.5 * 0.08
    spool += padded_filter(rng.normal(0, 1, n), lambda f: band(f, 1500, 8000) * peak(f, 3200, 2, 2)) * boost * 0.03
    rev += spool
    rev += at(bov(1.0) * 0.55, 2.72, total)
    for t0, s in ((0.42, 0.7), (1.02, 0.6), (2.9, 0.9), (3.05, 0.5), (3.18, 0.8), (3.4, 0.4), (3.7, 0.35)):
        rev += at(bang(0.3, s, seed=int(t0 * 100)) * 0.3, t0, total)
    save('skyline/rev.ogg', fade(rev, 0.005, 0.4))


def cleanup():
    """The four-layer engine is gone."""
    for old in ('idle', 'drive', 'eng_idle', 'eng_low', 'eng_mid', 'eng_high', 'eng_decel'):
        for suf in ('', '_in'):
            p = os.path.join(OUT, 'skyline', old + suf + '.ogg')
            if os.path.exists(p):
                os.remove(p)


# the sound events (sounds.json): subtitles only where a sound means something
def events():
    e = {}
    for i in range(len(RPM)):
        for kind in ('on', 'off'):
            for suf in ('', '_in'):
                e['%s_%d%s' % (kind, i, suf)] = 'engine' if (kind, i) == ('on', 0) else None
    e.update({'turbo': 'turbo', 'road': None, 'wind': None, 'shift': 'shift', 'blip': 'blip', 'pop': 'pop',
              'door_open': 'door_open', 'door_close': 'door_close', 'summon': 'summon', 'horn': 'horn', 'screech': 'screech'})
    return e


def register():
    from geo_util import read_sounds, write_sounds
    sounds = read_sounds()
    for old in list(sounds):
        if old.startswith('skyline.eng_') or old in ('skyline.idle', 'skyline.drive'):
            sounds.pop(old)
    for name, sub in events().items():
        ev = {'sounds': [{'name': 'papon:skyline/' + name}]}
        if sub:
            ev['subtitle'] = 'subtitles.papon.skyline.' + sub
        sounds['skyline.' + name] = ev
    write_sounds(sounds)


def sheet(path):
    """Spectrograms of the outside loops, on and off the throttle, to look at."""
    from PIL import Image, ImageDraw
    rows = []
    for kind in ('on', 'off'):
        for i in range(len(RPM)):
            y, _ = sf.read(os.path.join(OUT, 'skyline/%s_%d.ogg' % (kind, i)))
            y = y[:SR]
            win, hop = 2048, 256
            frames = np.array([np.abs(np.fft.rfft(y[k:k + win] * np.hanning(win))) for k in range(0, len(y) - win, hop)]).T
            frames = frames[:int(4000 / (SR / win))]
            db = 20 * np.log10(frames + 1e-6)
            db = np.clip((db - db.max() + 70) / 70, 0, 1)
            im = Image.fromarray((db[::-1] * 255).astype(np.uint8)).resize((300, 200))
            ImageDraw.Draw(im).text((4, 4), '%s %d rpm' % (kind, RPM[i]), fill=255)
            rows.append(im)
    out = Image.new('L', (300 * 8, 400))
    for k, im in enumerate(rows):
        out.paste(im, ((k % 8) * 300, (k // 8) * 200))
    out.save(path)
    print(path)


if __name__ == '__main__':
    if len(sys.argv) > 2 and sys.argv[1] == '--sheet':
        sheet(sys.argv[2])
    else:
        loops()
        one_shots()
        cleanup()
        register()
