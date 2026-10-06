#!/usr/bin/env python3
"""The Skyline's own sounds (Ascended Papon's car; the van's are gen_sounds.py's): an RB26, the twin-turbo straight
six. All maths, no samples (pip install numpy soundfile).

Redone 2026-10-06 (the user: "the current vehicle sounds are annoying and overly loud... entering the Skyline should feel
like entering the cockpit of an extremely premium, powerful, cinematic vehicle... not a bunch of loud vehicle sounds
playing at once"). The engine is no longer two loops (an idle and a 5800 rpm scream with a turbo whine baked into it,
pitched by speed); it is layers that client/CaravanSounds mixes from a simulated engine (rpm, gear, throttle, boost):
  eng_idle/low/mid/high.ogg   the six on the throttle at 950, 2200, 4300 and 7000 rpm, seamless loops, all at one
                              loudness so the cross-fades between them never jump; pitched to the rpm between them
  eng_decel.ogg               off the throttle at 4300: the overrun's burble (lifting, engine braking)
  *_in.ogg                    the same from the cockpit: the bodywork and the glass between you and it (the highs gone,
                              the boom of the shell under it, less rasp): insulated, deep, never shrill
  turbo.ogg                   the turbos' spool (a breathy whistle, not a pure tone): only as boost builds
  road.ogg, wind.ogg          the tyres' roar and the wind, by speed
  shift.ogg, blip.ogg, pop.ogg  an upshift's mechanical clack, a downshift's rev-matched blip, one soft overrun pop
  door_open.ogg, door_close.ogg  a heavy, damped door: the latch, the seal, a deep thud
  start.ogg                   a quick starter, the catch, a controlled flare and the settle (no crackle)
Kept: rev.ogg (the reveal), bov.ogg, backfire.ogg, chirp.ogg (the fob), glow.ogg (the underglow).

  python tools/gen_skyline_sounds.py           (PYTHONPATH=tools)
"""
import os

import numpy as np
import soundfile as sf

from gen_sounds import SR, OUT, save, t_of, fft_filter, padded_filter, peak, lowpass, highpass, band, fade, engine_pulse

rng = np.random.default_rng(34)
# a straight six: even firing, the cylinders much more alike than the van's V6
CYL6 = np.array([1.0, 0.97, 1.02, 0.98, 1.01, 0.96])


def seam_rank(y):
    """Where the wrap's jolt (the error predicting each sample from the two before it) ranks among every other sample's."""
    e = np.abs(y[2:] - (2 * y[1:-1] - y[:-2]))
    return (e < abs(y[0] - (2 * y[-1] - y[-2]))).mean()


def save_level(rel, x, rms_db=-20.0):
    """Saves a loop at a set loudness (RMS, dBFS), peaks softly limited: layers mixed against each other must match. The
    loop is circular, so it may start anywhere: of its quietest upward zero crossings, the start whose wrap survives the
    encoder most smoothly (each tried: Vorbis leaves its own small step at the ends) is kept, so OpenAL's looping of the
    decoded buffer does not tick."""
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
    for k in up[np.argsort(env[up])[:40]]:
        cand = np.roll(x, -int(k + 1))
        sf.write(path, cand, SR, format='OGG', subtype='VORBIS')
        y, _ = sf.read(path)
        r = seam_rank(y)
        if r < best_rank:
            best, best_rank = cand, r
    sf.write(path, best, SR, format='OGG', subtype='VORBIS')
    print(path, '%.1f s' % (len(x) / SR), '%d KB' % (os.path.getsize(path) // 1024), 'seam at the %.0fth percentile' % (100 * best_rank))


def rb_response(load):
    """An RB26 through a good exhaust: a deep body, a growl in the low mids, an edge on boost that never turns to fizz."""
    def r(f):
        body = peak(f, 115, 2.0, 2.2) * peak(f, 310, 2.6, 1.8) * peak(f, 820, 3.2, 0.5 + 0.9 * load) * peak(f, 2100, 4.0, 0.15 + 0.35 * load)
        return body * lowpass(f, 1400 + 2200 * load, 2) * highpass(f, 40, 2)
    return r


def cabin(f, load):
    """From the cockpit: the shell takes the top off (an insulated car), its boom lifts the lows."""
    return lowpass(f, 650 + 450 * load, 2) * peak(f, 62, 1.6, 1.6) * peak(f, 150, 2.0, 0.9) * highpass(f, 30, 2)


def soft(x, k):
    """A little saturation, more with load (warmth, not rasp)."""
    x = x / (np.max(np.abs(x)) or 1)
    return np.tanh(x * (1 + 1.6 * k)) / np.tanh(1 + 1.6 * k)


def six_loop(rpm, seconds, load, lope=0.0, inside=False, burble=0.0):
    """A seamless loop of the six at `rpm`: whole engine cycles, filtered circularly. `lope`: the cam's lumpiness at idle;
    `burble`: off-throttle firings uneven and soft (the overrun)."""
    fire = rpm / 60 * 3
    firings = int(round(fire * seconds / 6)) * 6
    n = int(round(firings / fire * SR))
    fire = firings / (n / SR)
    x = np.zeros(n)
    for i in range(firings):
        pos = (i / fire + rng.normal(0, 0.00008 + 0.0005 * lope + 0.0004 * burble)) * SR
        k = int(pos) % n
        frac = pos - int(pos)
        a = CYL6[i % 6] * (1 + rng.normal(0, 0.025 + 0.12 * burble)) * (1 - lope * 0.45 * (i % 12 in (3, 4, 10)))
        if burble and rng.random() < 0.12 * burble:
            a *= 0.3                                 # a skipped firing on the overrun
        x[k] += a * (1 - frac)
        x[(k + 1) % n] += a * frac
    pulse = np.zeros(n)
    p = engine_pulse(int(0.05 * SR), f_res=210.0, tau=0.005)
    pulse[:len(p)] = p
    eng = np.fft.irfft(np.fft.rfft(x) * np.fft.rfft(pulse), n)
    eng = fft_filter(eng, rb_response(load))
    eng = soft(eng, 0.25 + 0.45 * load)
    t = t_of(n)
    # intake and mechanical noise under it, breathing with the cycle
    noise = fft_filter(rng.normal(0, 1, n), lambda f: band(f, 90, 700 + 1600 * load))
    out = eng + noise * (0.025 + 0.06 * load) * (1 + 0.3 * np.sin(2 * np.pi * (fire / 6) * t))
    out *= 1 + lope * 0.16 * np.sin(2 * np.pi * (fire / 6) * t)
    if inside:
        out = fft_filter(out, lambda f: cabin(f, load))
        # the shell's own low hum, locked to the firing (a seamless loop), and the cabin's air (the vents, the tyres through
        # the floor): a faint broad bed that a real cockpit always has
        out += np.sin(2 * np.pi * (fire / 2) * t) * 0.05 * np.std(out) * (1 + load)
        out += fft_filter(rng.normal(0, 1, n), lambda f: band(f, 250, 5000)) * np.std(out) * 0.02
    return out


def six_sweep(rpm_of, load_of, seconds, boost_of=None, whine=1.0):
    """A one-shot of the six with the rpm (and load, and boost) changing: rpm_of(t), load_of(t), boost_of(t) 0..1."""
    n = int(seconds * SR)
    t = t_of(n)
    rpm = rpm_of(t)
    fire = rpm / 60 * 3
    phase = np.cumsum(fire) / SR
    idx = np.nonzero(np.diff(np.floor(phase)) > 0)[0]
    load = load_of(t)
    x = np.zeros(n)
    for j, k in enumerate(idx):
        x[k] = CYL6[j % 6] * (0.45 + 0.9 * load[k]) * (1 + rng.normal(0, 0.04))
    p = engine_pulse(int(0.05 * SR), f_res=210.0, tau=0.005)
    eng = np.convolve(x, p)[:n]
    dull = padded_filter(eng, rb_response(0.1))
    bright = padded_filter(eng, rb_response(1.0))
    out = dull * (1 - load) + bright * load
    out = soft(out, 0.5) * (0.5 + 0.5 * np.clip(rpm / 8000, 0, 1) ** 0.5)
    out += padded_filter(rng.normal(0, 1, n), lambda f: band(f, 120, 2200)) * (0.025 + 0.07 * load)
    if boost_of is not None:
        b = boost_of(t)
        # the spool: a breathy whistle rising with the boost, a hiss of intake air under it
        fw = 1300 + 2600 * b
        out += np.sin(2 * np.pi * np.cumsum(fw) / SR) * 0.05 * whine * b ** 1.5
        out += padded_filter(rng.normal(0, 1, n), lambda f: peak(f, 2600, 6, 3) * band(f, 1200, 6000)) * 0.04 * b
    return out


def bov(seconds=0.9):
    """The blow-off valve: a sharp release of air ("PSSHH") and the flutter after it (sutututu)."""
    n = int(seconds * SR)
    t = t_of(n)
    air = padded_filter(rng.normal(0, 1, n), lambda f: band(f, 1500, 9000) * peak(f, 4200, 2.0, 1.5))
    env = np.exp(-t / 0.16) * np.clip(t / 0.004, 0, 1)
    flutter_rate = 34 - 16 * np.clip(t / seconds, 0, 1)
    flutter = (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * np.cumsum(flutter_rate) / SR))) * np.exp(-t / 0.3) * (t > 0.08)
    chop = padded_filter(rng.normal(0, 1, n), lambda f: band(f, 300, 2500)) * flutter
    return fade(air * env * 1.0 + chop * 0.45, 0.001, 0.08)


def bang(seconds=0.5, size=1.0):
    """An exhaust pop: a sharp crack, a low thump with it, a short rumbling tail."""
    n = int(seconds * SR)
    t = t_of(n)
    crack = padded_filter(rng.normal(0, 1, n), lambda f: band(f, 400, 6000)) * np.exp(-t / (0.012 * size))
    thump = np.sin(2 * np.pi * (70 + 60 * np.exp(-t / 0.03)) * t) * np.exp(-t / (0.07 * size)) * 1.4
    tail = padded_filter(rng.normal(0, 1, n), lambda f: lowpass(f, 500, 2)) * np.exp(-t / 0.12) * 0.4
    return np.tanh((crack + thump + tail) * 1.8)


def crackle(seconds, density=14.0):
    """The overrun: pops and crackles out of the exhaust, sparser as it goes."""
    n = int(seconds * SR)
    out = np.zeros(n)
    t = 0.0
    while t < seconds - 0.05:
        d = density * (1 - t / seconds) + 2
        t += rng.exponential(1 / d)
        k = int(t * SR)
        if k >= n:
            break
        s = 0.3 + rng.random() * 0.8
        b = bang(0.25, s) * (0.25 + 0.75 * rng.random()) * (1 if rng.random() < 0.85 else 2.0)
        out[k:k + len(b)] += b[:n - k]
    return out


def loop_noise(seconds, response, mod=None):
    """Filtered noise as a seamless loop (circular), optionally amplitude-modulated by a function of t (whole cycles)."""
    n = int(seconds * SR)
    x = fft_filter(rng.normal(0, 1, n), response)
    if mod is not None:
        x *= mod(t_of(n))
    return x


def thud(seconds, f0, tau, click=0.0, seal=0.0):
    """A heavy panel closing: a damped low body, a latch click, the seal's breath."""
    n = int(seconds * SR)
    t = t_of(n)
    body = np.sin(2 * np.pi * (f0 + 40 * np.exp(-t / 0.02)) * t) * np.exp(-t / tau)
    body += 0.5 * np.sin(2 * np.pi * f0 * 1.9 * t) * np.exp(-t / (tau * 0.6))
    knock = padded_filter(rng.normal(0, 1, n), lambda f: band(f, 120, 900)) * np.exp(-t / 0.015)
    out = body * 1.2 + knock * 0.6
    if click:
        k = padded_filter(rng.normal(0, 1, n), lambda f: band(f, 2500, 7000)) * np.exp(-t / 0.003)
        out += k * click
    if seal:
        s = padded_filter(rng.normal(0, 1, n), lambda f: band(f, 300, 1800)) * np.exp(-((t - 0.03) / 0.04) ** 2)
        out += s * seal
    return out


def main():
    # ---- the engine's layers, outside and inside
    for name, rpm, load, lope, burble in (('idle', 950, 0.25, 1.0, 0), ('low', 2200, 0.75, 0.15, 0), ('mid', 4300, 0.9, 0, 0),
                                          ('high', 7000, 1.0, 0, 0), ('decel', 4300, 0.12, 0, 1.0)):
        level = -22.0 if name == 'decel' else -20.0
        save_level('skyline/eng_%s.ogg' % name, six_loop(rpm, 2.0, load, lope=lope, burble=burble), level)
        save_level('skyline/eng_%s_in.ogg' % name, six_loop(rpm, 2.0, load, lope=lope, burble=burble, inside=True), level)

    # ---- the turbos: a breathy whistle and the rush of air, gently breathing (a seamless loop)
    sec = 2.0
    n = int(sec * SR)
    t = t_of(n)
    whistle = loop_noise(sec, lambda f: peak(f, 2600, 14, 8) * band(f, 1800, 4200))
    whistle = whistle / np.std(whistle)
    tone = np.sin(2 * np.pi * round(2600 * sec) / sec * t + 0.6 * np.sin(2 * np.pi * 3 / sec * t)) * 0.35
    air = loop_noise(sec, lambda f: band(f, 600, 7000) * peak(f, 1500, 1.5, 0.8))
    air = air / np.std(air)
    save_level('skyline/turbo.ogg', (whistle * 0.55 + tone + air * 0.5) * (1 + 0.12 * np.sin(2 * np.pi * 2 / sec * t)), -24.0)
    # ---- the road: the tyres' roar and the tread's thrum
    road = loop_noise(2.0, lambda f: lowpass(f, 420, 2) * highpass(f, 35) + 0.2 * band(f, 600, 1600),
                      lambda tt: 1 + 0.18 * np.sin(2 * np.pi * 24 / 2.0 * tt))
    save_level('skyline/road.ogg', road, -22.0)
    # ---- the wind: a broad rush with slow gusts
    wind = loop_noise(3.0, lambda f: band(f, 180, 2400) * peak(f, 500, 1.2, 0.6),
                      lambda tt: 1 + 0.25 * np.sin(2 * np.pi * 1 / 3.0 * tt) + 0.1 * np.sin(2 * np.pi * 3 / 3.0 * tt))
    save_level('skyline/wind.ogg', wind, -24.0)

    # ---- the start: a quick starter, the catch, a controlled flare to 2600, settling to the lope
    total = 2.4
    n = int(total * SR)
    t = t_of(n)
    crank_s = 0.42
    nc = int(crank_s * SR)
    tc = t[:nc]
    whine_f = 820 + 50 * np.sin(2 * np.pi * 11 * tc)
    crank = np.zeros(n)
    crank[:nc] = fade((np.sin(2 * np.pi * np.cumsum(whine_f) / SR) * 0.18 + padded_filter(rng.normal(0, 1, nc), lambda f: band(f, 300, 2200)) * 0.22)
                      * (0.6 + 0.4 * np.abs(np.sin(2 * np.pi * 11 * tc))), 0.01, 0.04)

    def rpm_s(tt):
        up = 300 + 2300 * np.clip((tt - crank_s) / 0.3, 0, 1) ** 0.7
        down = 2600 - 1650 * np.clip((tt - crank_s - 0.4) / 1.0, 0, 1) ** 0.8
        return np.maximum(1, np.where(tt < crank_s + 0.4, up, down) * (tt > crank_s - 0.05))

    def load_s(tt):
        return np.clip(1 - np.abs(tt - crank_s - 0.22) / 0.5, 0.15, 0.85)

    eng = six_sweep(rpm_s, load_s, total)
    eng[:nc] *= np.linspace(0, 1, nc) ** 6
    save('skyline/start.ogg', fade(fft_filter(crank * 0.7 + eng, lambda f: lowpass(f, 3200, 2)), 0.005, 0.3))

    # ---- an upshift: the gearbox's clack and a short breath of air
    n = int(0.35 * SR)
    t = t_of(n)
    clack = padded_filter(rng.normal(0, 1, n), lambda f: band(f, 800, 3500)) * np.exp(-t / 0.008)
    clunk = padded_filter(rng.normal(0, 1, n), lambda f: band(f, 90, 500)) * np.exp(-t / 0.03)
    air = padded_filter(rng.normal(0, 1, n), lambda f: band(f, 2000, 7000)) * np.exp(-((t - 0.05) / 0.05) ** 2) * 0.25
    save('skyline/shift.ogg', fade(clack * 0.7 + clunk * 1.0 + air, 0.0005, 0.05))

    # ---- a downshift's blip: up and back down in under half a second
    def rpm_b(tt):
        return 2600 + 2400 * np.sin(np.pi * np.clip(tt / 0.42, 0, 1)) ** 1.5

    save('skyline/blip.ogg', fade(six_sweep(rpm_b, lambda tt: np.clip(0.9 - tt * 1.5, 0.15, 0.9), 0.6), 0.003, 0.15))
    # ---- one soft overrun pop
    save('skyline/pop.ogg', fade(fft_filter(bang(0.3, 0.55), lambda f: lowpass(f, 2400, 2)), 0.0005, 0.08))
    # ---- the doors: heavy and damped
    save('skyline/door_open.ogg', fade(thud(0.5, 140, 0.03, click=0.9, seal=0.5) * 0.6, 0.0005, 0.1))
    save('skyline/door_close.ogg', fade(thud(0.7, 72, 0.06, click=0.5, seal=0.8), 0.0005, 0.12))

    # ---- the reveal's rev (the new engine's roar, a cutscene): blip, blip, flat out, the spool, the lift and the
    # blow-off, crackling on the overrun
    total = 4.6

    def rpm_r(tt):
        r = np.full_like(tt, 950.0)
        for t0 in (0.15, 0.75):
            k = np.clip((tt - t0) / 0.12, 0, 1) ** 0.6 * (tt < t0 + 0.2) + np.clip(1 - (tt - t0 - 0.2) / 0.3, 0, 1) * (tt >= t0 + 0.2)
            r = np.maximum(r, 950 + 3600 * k)
        up = 950 + 7050 * np.clip((tt - 1.45) / 1.15, 0, 1) ** 0.75
        down = 8000 - 6900 * np.clip((tt - 2.7) / 1.3, 0, 1) ** 0.6
        long = np.where(tt < 2.7, up, down) * (tt >= 1.45)
        return np.maximum(r, long)

    def load_r(tt):
        return np.where(tt < 2.7, np.clip(rpm_r(tt) / 4500, 0.2, 1), 0.12)

    def boost_r(tt):
        return np.clip((tt - 1.7) / 0.8, 0, 1) ** 1.4 * (tt < 2.72) + np.clip(1 - (tt - 2.72) / 0.12, 0, 1) * (tt >= 2.72)

    rev = six_sweep(rpm_r, load_r, total, boost_r, whine=1.4)
    n = len(rev)
    b = bov(1.0)
    k = int(2.72 * SR)
    rev[k:k + len(b)] += b[:n - k] * 0.8
    c = crackle(1.6, 16)
    k = int(2.85 * SR)
    rev[k:k + len(c)] += c[:n - k] * 0.45
    for at in (0.42, 1.0):                      # a pop after each blip
        p = bang(0.3, 0.8) * 0.4
        k = int(at * SR)
        rev[k:k + len(p)] += p[:n - k]
    save('skyline/rev.ogg', fade(rev, 0.005, 0.4))

    save('skyline/bov.ogg', bov(0.9))
    save('skyline/backfire.ogg', fade(bang(0.6, 1.3), 0.0005, 0.2))

    # the fob: two chirps (a square-ish 3.1 kHz with a fast decay) and the locks
    n = int(0.75 * SR)
    t = t_of(n)
    out = np.zeros(n)
    for t0 in (0.0, 0.16):
        tt = np.clip(t - t0, 0, None)
        env = (t >= t0) * np.exp(-tt / 0.045) * np.clip(tt / 0.003, 0, 1)
        out += np.tanh(np.sin(2 * np.pi * 3100 * tt) * 3) * env * 0.6
    k = int(0.36 * SR)
    clunk = padded_filter(rng.normal(0, 1, int(0.12 * SR)), lambda f: band(f, 150, 1800)) * np.exp(-t_of(int(0.12 * SR)) / 0.02)
    out[k:k + len(clunk)] += clunk * 0.8
    save('skyline/chirp.ogg', fade(out, 0.001, 0.05))

    # the underglow: a relay click, the neon striking and stuttering (the 100 Hz buzz cut in and out), then a quiet hum
    total = 1.8
    n = int(total * SR)
    t = t_of(n)
    click = padded_filter(rng.normal(0, 1, n), lambda f: band(f, 1500, 6000)) * np.exp(-t / 0.004)
    buzz = sum(np.sin(2 * np.pi * 100 * h * t) / h for h in (1, 2, 3, 5, 7)) * 0.35
    zap = padded_filter(rng.normal(0, 1, n), lambda f: band(f, 2000, 9000)) * 0.15
    gate = np.zeros(n)
    for a, b2 in [(0.05, 0.09), (0.14, 0.17), (0.22, 0.3), (0.34, 0.36), (0.4, total)]:
        gate[int(a * SR):int(b2 * SR)] = 1
    gate = np.convolve(gate, np.ones(80) / 80, mode='same')
    level = np.where(t < 0.6, 1.0, 0.35 + 0.65 * np.exp(-(t - 0.6) / 0.25))
    swell = np.sin(2 * np.pi * np.cumsum(180 + 900 * np.clip((t - 0.4) / 0.35, 0, 1)) / SR) * 0.12 * np.exp(-np.abs(t - 0.75) / 0.2)
    out = click * 0.8 + (buzz + zap * (t < 0.45)) * gate * level + swell
    save('skyline/glow.ogg', fade(out, 0.001, 0.4))
    # the old two-loop engine is gone
    for old in ('idle.ogg', 'drive.ogg'):
        p = os.path.join(OUT, 'skyline', old)
        if os.path.exists(p):
            os.remove(p)


# the sound events (sounds.json): the engine's layers, the one-shots; subtitles only where a sound means something
EVENTS = {'eng_idle': 'engine', 'eng_low': None, 'eng_mid': None, 'eng_high': None, 'eng_decel': None,
          'eng_idle_in': 'engine', 'eng_low_in': None, 'eng_mid_in': None, 'eng_high_in': None, 'eng_decel_in': None,
          'turbo': 'turbo', 'road': None, 'wind': None, 'shift': 'shift', 'blip': 'blip', 'pop': 'pop',
          'door_open': 'door_open', 'door_close': 'door_close'}


def register():
    from geo_util import read_sounds, write_sounds
    sounds = read_sounds()
    for old in ('skyline.idle', 'skyline.drive'):
        sounds.pop(old, None)
    for name, sub in EVENTS.items():
        e = {'sounds': [{'name': 'papon:skyline/' + name}]}
        if sub:
            e['subtitle'] = 'subtitles.papon.skyline.' + sub
        sounds['skyline.' + name] = e
    write_sounds(sounds)


if __name__ == '__main__':
    main()
    register()
