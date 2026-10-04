#!/usr/bin/env python3
"""The Skyline's own sounds (Ascended Papon's car; the van's are gen_sounds.py's): an RB26, the twin-turbo straight
six, not the van's tired V6. All maths, no samples (pip install numpy soundfile).

Writes under src/main/resources/assets/papon/sounds/skyline/:
  idle.ogg       950 rpm, an even six with a lumpy cam and a raspy exhaust, a seamless loop (CaravanSounds pitches it)
  drive.ogg      5800 rpm on boost: the six screaming, the turbos whistling over it, road and wind, a seamless loop
  start.ogg      a quick starter, the catch, a flare to 3000 and a crackle, settling to the lumpy idle
  rev.ogg        the reveal: two blips, then flat out to 8000 with the turbos spooling up, the lift: the blow-off
                 valve's "pssh" and its flutter, and the exhaust crackling and popping on the overrun
  bov.ogg        the blow-off valve alone (between the rams)
  backfire.ogg   one bang out of the exhaust, a flame with it (played pitched)
  chirp.ogg      the key fob: two chirps and the locks clunking
  glow.ogg       the underglow switching on: a relay click, the neon striking and stuttering, settling to a hum

  python3.11 tools/gen_skyline_sounds.py
"""
import numpy as np

from gen_sounds import SR, save, t_of, fft_filter, padded_filter, peak, lowpass, highpass, band, fade, engine_pulse

rng = np.random.default_rng(34)
# a straight six: even firing, the cylinders much more alike than the van's V6
CYL6 = np.array([1.0, 0.97, 1.02, 0.98, 1.01, 0.96])


def rb_response(load):
    """An RB26 through a big exhaust: less low rumble than the V6, a rasp in the upper mids, brighter on boost."""
    def r(f):
        body = peak(f, 130, 2.0, 1.6) * peak(f, 340, 3.0, 2.2) * peak(f, 900, 3.5, 1.0 + 1.4 * load) * peak(f, 2300, 4.0, 0.3 + 0.8 * load)
        return body * lowpass(f, 1600 + 3600 * load, 2) * highpass(f, 45, 2)
    return r


def rasp(x, k):
    """The exhaust's rasp: soft clipping, more of it with load."""
    x = x / (np.max(np.abs(x)) or 1)
    return np.tanh(x * (1 + 3 * k)) / np.tanh(1 + 3 * k)


def six_loop(rpm, seconds, load, lope=0.0, road=0.0, whistle=0.0):
    """A seamless loop of the six at `rpm`: whole engine cycles, filtered circularly. `lope`: the cam's lumpiness
    (some firings weaker, the idle rocking); `whistle`: the turbos (a whine at a fixed pitch, breathing a little)."""
    fire = rpm / 60 * 3
    firings = int(round(fire * seconds / 6)) * 6
    n = int(round(firings / fire * SR))
    fire = firings / (n / SR)
    x = np.zeros(n)
    for i in range(firings):
        pos = (i / fire + rng.normal(0, 0.0001 + 0.0006 * lope)) * SR
        k = int(pos) % n
        frac = pos - int(pos)
        # the cam: every few firings a weak one (the overlap), the idle loping
        a = CYL6[i % 6] * (1 + rng.normal(0, 0.03)) * (1 - lope * 0.5 * (i % 12 in (3, 4, 10)))
        x[k] += a * (1 - frac)
        x[(k + 1) % n] += a * frac
    pulse = np.zeros(n)
    p = engine_pulse(int(0.05 * SR), f_res=230.0, tau=0.0045)
    pulse[:len(p)] = p
    eng = np.fft.irfft(np.fft.rfft(x) * np.fft.rfft(pulse), n)
    eng = fft_filter(eng, rb_response(load))
    eng = rasp(eng, load)
    t = t_of(n)
    noise = fft_filter(rng.normal(0, 1, n), lambda f: band(f, 120, 900 + 2400 * load))
    out = eng + noise * (0.03 + 0.1 * load)
    # the cam's rock, locked to the cycle
    out *= 1 + lope * 0.18 * np.sin(2 * np.pi * (fire / 6) * t)
    if whistle:
        # the turbos: two whines a little apart (one per turbo), beating; whole cycles so the loop stays seamless
        for fw, g in ((3600, 1.0), (3640, 0.7)):
            fw = round(fw * seconds) / seconds
            out += np.sin(2 * np.pi * fw * t) * 0.05 * whistle * g * (1 + 0.15 * np.sin(2 * np.pi * (2 / seconds) * t))
        out += fft_filter(rng.normal(0, 1, n), lambda f: band(f, 2500, 7000)) * 0.03 * whistle
    if road:
        out += fft_filter(rng.normal(0, 1, n), lambda f: lowpass(f, 600, 2) * highpass(f, 40)) * road * 0.8
    return out


def six_sweep(rpm_of, load_of, seconds, boost_of=None):
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
    p = engine_pulse(int(0.05 * SR), f_res=230.0, tau=0.0045)
    eng = np.convolve(x, p)[:n]
    dull = padded_filter(eng, rb_response(0.1))
    bright = padded_filter(eng, rb_response(1.0))
    out = dull * (1 - load) + bright * load
    out = rasp(out, 0.6) * (0.5 + 0.5 * np.clip(rpm / 8000, 0, 1) ** 0.5)
    out += padded_filter(rng.normal(0, 1, n), lambda f: band(f, 150, 2500)) * (0.03 + 0.09 * load)
    if boost_of is not None:
        b = boost_of(t)
        # the spool: the whine rises with the boost (pitch and level), a hiss of intake air under it
        fw = 1400 + 3600 * b
        out += np.sin(2 * np.pi * np.cumsum(fw) / SR) * 0.11 * b ** 1.5
        out += np.sin(2 * np.pi * np.cumsum(fw * 1.012) / SR) * 0.07 * b ** 1.5
        out += padded_filter(rng.normal(0, 1, n), lambda f: band(f, 2000, 8000)) * 0.05 * b
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


def crackle(seconds, density=14.0, start_density=None):
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


def main():
    save('skyline/idle.ogg', six_loop(950, 2.0, 0.2, lope=1.0))
    save('skyline/drive.ogg', six_loop(5800, 2.0, 0.95, road=0.4, whistle=1.0))

    # the start: a quick, high starter, the catch, a flare to 3000 with a crack, settling to the lope
    total = 2.6
    n = int(total * SR)
    t = t_of(n)
    crank_s = 0.55
    nc = int(crank_s * SR)
    tc = t[:nc]
    whine_f = 1150 + 80 * np.sin(2 * np.pi * 13 * tc)
    crank = np.zeros(n)
    crank[:nc] = fade((np.sin(2 * np.pi * np.cumsum(whine_f) / SR) * 0.25 + padded_filter(rng.normal(0, 1, nc), lambda f: band(f, 400, 3000)) * 0.3)
                      * (0.6 + 0.4 * np.abs(np.sin(2 * np.pi * 13 * tc))), 0.01, 0.04)

    def rpm_s(tt):
        up = 300 + 2700 * np.clip((tt - crank_s) / 0.28, 0, 1) ** 0.6
        down = 3000 - 2050 * np.clip((tt - crank_s - 0.35) / 0.9, 0, 1) ** 0.7
        return np.maximum(1, np.where(tt < crank_s + 0.35, up, down) * (tt > crank_s - 0.05))

    def load_s(tt):
        return np.clip(1 - np.abs(tt - crank_s - 0.2) / 0.45, 0.15, 1)

    eng = six_sweep(rpm_s, load_s, total)
    eng[:nc] *= np.linspace(0, 1, nc) ** 6
    pops = np.zeros(n)
    for at in (crank_s + 0.42, crank_s + 0.55, crank_s + 0.62):
        b = bang(0.4, 0.9) * 0.5
        k = int(at * SR)
        pops[k:k + len(b)] += b[:n - k]
    save('skyline/start.ogg', fade(crank * 0.8 + eng + pops, 0.005, 0.3))

    # the reveal's rev: blip, blip, then flat out, the spool, the lift and the blow-off, crackling on the overrun
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

    rev = six_sweep(rpm_r, load_r, total, boost_r)
    n = len(rev)
    b = bov(1.0)
    k = int(2.72 * SR)
    rev[k:k + len(b)] += b[:n - k] * 0.9
    c = crackle(1.6, 16)
    k = int(2.85 * SR)
    rev[k:k + len(c)] += c[:n - k] * 0.55
    for at in (0.42, 1.0):                      # a pop after each blip
        p = bang(0.3, 0.8) * 0.45
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
    on_at = [(0.05, 0.09), (0.14, 0.17), (0.22, 0.3), (0.34, 0.36), (0.4, total)]
    for a, b2 in on_at:
        gate[int(a * SR):int(b2 * SR)] = 1
    gate = np.convolve(gate, np.ones(80) / 80, mode='same')
    level = np.where(t < 0.6, 1.0, 0.35 + 0.65 * np.exp(-(t - 0.6) / 0.25))
    swell = np.sin(2 * np.pi * np.cumsum(180 + 900 * np.clip((t - 0.4) / 0.35, 0, 1)) / SR) * 0.12 * np.exp(-np.abs(t - 0.75) / 0.2)
    out = click * 0.8 + (buzz + zap * (t < 0.45)) * gate * level + swell
    save('skyline/glow.ogg', fade(out, 0.001, 0.4))


if __name__ == '__main__':
    main()
