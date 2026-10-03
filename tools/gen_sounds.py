#!/usr/bin/env python3
"""Synthesizes the mod's own sounds (pip install numpy soundfile; no samples, all maths).

Writes Ogg Vorbis files under src/main/resources/assets/papon/sounds/:
  caravan/idle.ogg        V6 at 720 rpm, a seamless 2 s loop (pitched up with speed by CaravanSounds)
  caravan/drive.ogg       V6 at 2400 rpm under load, road and wind noise, seamless loop
  caravan/start.ogg       starter motor cranking, the engine catching, a rev, settling to idle
  caravan/rev.ogg         a blip of the throttle to 4500 rpm and back (the boss's wind-up)
  caravan/horn.ogg        a dual-tone car horn
  caravan/screech.ogg     tyre squeal
  caravan/crash.ogg       crumpling metal, a thump, breaking glass
  caravan/hit.ogg         somebody getting hit by a minivan
  caravan/door_open.ogg / door_close.ogg
  boss/reveal.ogg         the bald head's reveal: a heavenly choir swell and a sparkle
  music/boss.ogg          the boss music: original mariachi, stereo, ~2 minutes, looping. Fanfarria
                          (fast) -> ranchera (slow waltz) -> son (fast 6/8 / 3/4) -> puente (slow,
                          ominous) -> son final (faster) and the classic "chan-chan" ending.
                          Trumpets and a violin section (additive synthesis), vihuela, guitar and
                          guitarron (Karplus-Strong), a touch of reverb.
Loops are built circularly (filtered in the frequency domain over exactly a whole number of engine
cycles), so they repeat without a click.
"""
import math
import os

import numpy as np
import soundfile as sf

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'src/main/resources/assets/papon/sounds')
SR = 44100
rng = np.random.default_rng(2001)


def save(rel, x, stereo=False):
    path = os.path.join(OUT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    x = np.asarray(x, dtype=np.float64)
    peak = np.max(np.abs(x)) or 1.0
    x = x / peak * 0.85
    data = x.T if stereo else x
    # libsndfile's Vorbis encoder overflows the stack on one huge write: feed it in chunks
    with sf.SoundFile(path, 'w', SR, 2 if stereo else 1, format='OGG', subtype='VORBIS') as f:
        for i in range(0, len(data), SR):
            f.write(data[i:i + SR])
    print(path, '%.1f s' % (x.shape[-1] / SR), '%d KB' % (os.path.getsize(path) // 1024))


def t_of(n):
    return np.arange(n) / SR


def fft_filter(x, response):
    """Filter by multiplying the spectrum by response(freqs). Circular: perfect for loops."""
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    return np.fft.irfft(X * response(f), len(x))


def padded_filter(x, response, pad=0.5):
    y = fft_filter(np.concatenate([x, np.zeros(int(pad * SR))]), response)
    return y[:len(x)]


def peak(f, f0, q, gain):
    return 1 + gain / (1 + ((f - f0) / (f0 / q)) ** 2)


def lowpass(f, fc, order=2):
    return 1 / np.sqrt(1 + (f / fc) ** (2 * order))


def highpass(f, fc, order=2):
    return 1 - lowpass(f, fc, order) * 0.999


def band(f, lo, hi):
    return highpass(f, lo) * lowpass(f, hi)


def fade(x, a=0.005, r=0.02):
    n = len(x)
    env = np.ones(n)
    na, nr = int(a * SR), int(r * SR)
    if na:
        env[:na] = np.linspace(0, 1, na)
    if nr:
        env[-nr:] *= np.linspace(1, 0, nr)
    return x * env


# ===================================================================== the van

CYL = np.array([1.0, 0.84, 0.95, 0.9, 1.06, 0.8])     # the six cylinders are not quite equal


def engine_pulse(n, f_res=140.0, tau=0.006):
    t = t_of(n)
    return np.exp(-t / tau) * np.sin(2 * np.pi * f_res * t) + 0.3 * np.exp(-t / 0.0012)


def engine_response(load):
    def r(f):
        body = peak(f, 92, 2.0, 3.0) * peak(f, 210, 3.0, 1.5) * peak(f, 520, 4.0, 0.8 + load)
        return body * lowpass(f, 900 + 2200 * load, 2) * highpass(f, 28, 2)
    return r


def engine_loop(rpm, seconds, load, road=0.0):
    fire = rpm / 60 * 3
    firings = int(round(fire * seconds / 6)) * 6
    n = int(round(firings / fire * SR))
    fire = firings / (n / SR)                  # exact whole cycles in the loop
    x = np.zeros(n)
    jitter = rng.normal(0, 0.00015, 6)
    for i in range(firings):
        pos = (i / fire + jitter[i % 6]) * SR
        k = int(pos) % n
        frac = pos - int(pos)
        a = CYL[i % 6] * (1 + rng.normal(0, 0.05))
        x[k] += a * (1 - frac)
        x[(k + 1) % n] += a * frac
    pulse = np.zeros(n)
    p = engine_pulse(int(0.06 * SR))
    pulse[:len(p)] = p
    eng = np.fft.irfft(np.fft.rfft(x) * np.fft.rfft(pulse), n)
    eng = fft_filter(eng, engine_response(load))
    t = t_of(n)
    # crankshaft rumble and valvetrain tick, locked to the cycle so the loop stays seamless
    crank = np.sin(2 * np.pi * (fire / 3) * t) * 0.25
    tick = fft_filter(rng.normal(0, 1, n), lambda f: band(f, 1800, 4500)) * (0.5 + 0.5 * np.sin(2 * np.pi * fire / 2 * t)) ** 4
    noise = fft_filter(rng.normal(0, 1, n), lambda f: band(f, 60, 700 + 900 * load))
    out = eng / np.max(np.abs(eng)) + crank * 0.6 + tick * 0.03 + noise * (0.04 + 0.12 * load)
    if road:
        out += fft_filter(rng.normal(0, 1, n), lambda f: lowpass(f, 500, 2) * highpass(f, 40)) * road
        out += np.sin(2 * np.pi * 118 * t) * 0.05 * road * (1 + 0.3 * np.sin(2 * np.pi * 3 * t))
    return out


def rpm_sweep(curve, seconds, load_curve):
    """One-shot engine with a changing rpm: curve(t) -> rpm, load_curve(t) -> 0..1."""
    n = int(seconds * SR)
    t = t_of(n)
    rpm = curve(t)
    fire = rpm / 60 * 3
    phase = np.cumsum(fire) / SR
    idx = np.nonzero(np.diff(np.floor(phase)) > 0)[0]
    x = np.zeros(n)
    load = load_curve(t)
    for j, k in enumerate(idx):
        x[k] = CYL[j % 6] * (0.5 + 0.8 * load[k]) * (1 + rng.normal(0, 0.06))
    p = engine_pulse(int(0.06 * SR))
    eng = np.convolve(x, p)[:n]
    # brightness follows the load: filter two versions and cross-fade
    dull = padded_filter(eng, engine_response(0.1))
    bright = padded_filter(eng, engine_response(1.0))
    out = dull * (1 - load) + bright * load
    out /= np.max(np.abs(out)) or 1
    out += padded_filter(rng.normal(0, 1, n), lambda f: band(f, 80, 1500)) * (0.04 + 0.1 * load)
    return out


def caravan_sounds():
    save('caravan/idle.ogg', engine_loop(720, 2.0, 0.15))
    save('caravan/drive.ogg', engine_loop(2400, 2.0, 0.9, road=0.5))

    # start: crank, crank, crank... catch, rev to 1700, settle at idle
    crank_s, total = 0.95, 2.8
    n = int(total * SR)
    t = t_of(n)
    crank = np.zeros(n)
    nc = int(crank_s * SR)
    tc = t[:nc]
    comp = 0.5 + 0.5 * np.sign(np.sin(2 * np.pi * 9.5 * tc)) * (np.abs(np.sin(2 * np.pi * 9.5 * tc)) ** 0.5)
    whine_f = 820 + 60 * np.sin(2 * np.pi * 9.5 * tc)
    whine = np.sin(2 * np.pi * np.cumsum(whine_f) / SR) + 0.4 * np.sin(4 * np.pi * np.cumsum(whine_f) / SR)
    grind = padded_filter(rng.normal(0, 1, nc), lambda f: band(f, 300, 2500))
    crank[:nc] = (whine * 0.25 + grind * 0.35) * comp + np.sin(2 * np.pi * 9.5 * tc) * 0.3
    crank[:nc] = fade(crank[:nc], 0.01, 0.05)

    def rpm_c(tt):
        s = np.clip((tt - crank_s) / 0.25, 0, 1)
        up = 300 + 1400 * np.clip((tt - crank_s) / 0.35, 0, 1) ** 0.7
        down = 1700 - 980 * np.clip((tt - crank_s - 0.45) / 0.8, 0, 1) ** 0.8
        return np.where(tt < crank_s + 0.45, up, down) * s + 1 * (1 - s)

    def load_c(tt):
        return np.clip(1 - np.abs(tt - crank_s - 0.3) / 0.5, 0.15, 1)

    eng = rpm_sweep(rpm_c, total, load_c)
    eng[:nc] *= np.linspace(0, 1, nc) ** 6
    save('caravan/start.ogg', fade(crank * 0.8 + eng * 0.9, 0.005, 0.25))

    def rpm_r(tt):
        up = 760 + 3800 * np.clip((tt - 0.15) / 0.45, 0, 1) ** 0.6
        down = 4560 - 3700 * np.clip((tt - 0.95) / 0.9, 0, 1) ** 0.7
        return np.where(tt < 0.95, up, down)

    def load_r(tt):
        return np.where(tt < 0.95, np.clip((tt - 0.1) / 0.2, 0.15, 1), 0.25)

    save('caravan/rev.ogg', fade(rpm_sweep(rpm_r, 2.2, load_r), 0.005, 0.3))

    # horn: two tones a third apart, square-ish, through a small horn's resonance
    n = int(0.75 * SR)
    t = t_of(n)
    h = np.zeros(n)
    for f0 in (392.0, 494.0):
        ph = 2 * np.pi * f0 * t * (1 + 0.002 * np.sin(2 * np.pi * 6 * t))
        for k in range(1, 16, 1):
            h += np.sin(k * ph) / k ** 1.15 * (1 if k % 2 else 0.55)
    h = padded_filter(h, lambda f: peak(f, 1600, 2, 3) * peak(f, 2900, 3, 2) * band(f, 250, 6000))
    env = np.clip(t / 0.02, 0, 1) * np.clip((0.75 - t) / 0.06, 0, 1)
    save('caravan/horn.ogg', np.tanh(h / np.max(np.abs(h)) * 2.2) * env)

    # screech: a wandering whistle with stick-slip flutter
    n = int(1.4 * SR)
    t = t_of(n)
    walk = np.cumsum(rng.normal(0, 1, n))
    walk = padded_filter(walk - walk.mean(), lambda f: lowpass(f, 6, 1))
    walk = walk / (np.max(np.abs(walk)) or 1)
    f0 = 1650 + 240 * walk + 120 * np.sin(2 * np.pi * 5.5 * t)
    ph = 2 * np.pi * np.cumsum(f0) / SR
    s = np.sin(ph) + 0.45 * np.sin(2 * ph + 0.3) + 0.2 * np.sin(3 * ph)
    s *= 0.6 + 0.4 * np.sin(2 * np.pi * 43 * t) ** 2
    s += padded_filter(rng.normal(0, 1, n), lambda f: band(f, 1200, 5000)) * 0.35
    env = np.clip(t / 0.05, 0, 1) * np.clip((1.4 - t) / 0.5, 0, 1)
    save('caravan/screech.ogg', s * env)

    # crash: thump, crumpling metal (inharmonic partials), glass
    n = int(1.8 * SR)
    t = t_of(n)
    c = np.sin(2 * np.pi * 55 * t) * np.exp(-t / 0.18) * 1.4
    c += padded_filter(rng.normal(0, 1, n), lambda f: band(f, 80, 3000)) * np.exp(-t / 0.12) * 1.2
    for f0, tau, a in ((340, 0.5, 0.5), (781, 0.35, 0.4), (1233, 0.6, 0.3), (1907, 0.3, 0.25), (2741, 0.45, 0.2), (3530, 0.25, 0.12)):
        c += np.sin(2 * np.pi * f0 * t + rng.uniform(0, 6)) * np.exp(-t / tau) * a * (1 + 0.5 * np.sin(2 * np.pi * rng.uniform(5, 13) * t))
    crumple = padded_filter(rng.normal(0, 1, n), lambda f: band(f, 400, 2500))
    crumple *= (rng.random(n) < 0.002).astype(float).cumsum() % 2 * 0.6 + 0.4
    c += crumple * np.exp(-t / 0.35) * 0.5
    glass = np.zeros(n)
    for _ in range(40):
        k = int(rng.uniform(0.02, 0.7) * SR)
        fz = rng.uniform(3000, 8000)
        m = min(n - k, int(0.08 * SR))
        tt = t_of(m)
        glass[k:k + m] += np.sin(2 * np.pi * fz * tt) * np.exp(-tt / 0.015) * rng.uniform(0.1, 0.35)
    save('caravan/crash.ogg', fade(np.tanh((c + glass) * 1.3), 0.001, 0.3))

    # hit: a bumper meeting somebody
    n = int(0.7 * SR)
    t = t_of(n)
    hit = np.sin(2 * np.pi * 70 * t) * np.exp(-t / 0.12) * 1.5
    hit += padded_filter(rng.normal(0, 1, n), lambda f: band(f, 1500, 6000)) * np.exp(-t / 0.012) * 0.8
    hit += padded_filter(rng.normal(0, 1, n), lambda f: band(f, 300, 1500)) * np.exp(-t / 0.05) * 0.7
    hit += np.sin(2 * np.pi * 410 * t) * np.exp(-t / 0.2) * 0.25
    save('caravan/hit.ogg', fade(np.tanh(hit * 1.4), 0.001, 0.1))

    # doors
    n = int(0.5 * SR)
    t = t_of(n)
    click = padded_filter(rng.normal(0, 1, n), lambda f: highpass(f, 2500)) * np.exp(-t / 0.004)
    ping = np.sin(2 * np.pi * 2400 * t) * np.exp(-t / 0.04) * 0.3
    creak_f = 180 + 30 * np.sin(2 * np.pi * 7 * t)
    creak = np.zeros(n)
    tc = t - 0.05
    ph = 2 * np.pi * np.cumsum(creak_f) / SR
    creak = (np.sin(ph) + 0.5 * np.sin(3 * ph)) * np.clip(tc / 0.03, 0, 1) * np.exp(-np.clip(tc, 0, None) / 0.12) * 0.15
    save('caravan/door_open.ogg', fade(click + ping + creak, 0.001, 0.1))
    thump = np.sin(2 * np.pi * 85 * t) * np.exp(-t / 0.09) * 1.2
    thump += padded_filter(rng.normal(0, 1, n), lambda f: lowpass(f, 400, 2)) * np.exp(-t / 0.05) * 0.9
    rattle = padded_filter(rng.normal(0, 1, n), lambda f: band(f, 900, 2200)) * np.exp(-t / 0.08) * (0.5 + 0.5 * np.sin(2 * np.pi * 38 * t)) * 0.3
    latch = np.zeros(n)
    k = int(0.012 * SR)
    latch[k:] = click[:n - k] * 0.8
    save('caravan/door_close.ogg', fade(np.tanh((thump + rattle + latch) * 1.2), 0.001, 0.1))


# ===================================================================== instruments

def midi(m):
    return 440.0 * 2 ** ((m - 69) / 12)


def adsr(n, a, d, s, r, hold):
    """Envelope n samples long: attack a, decay d to level s, held until `hold` s, release r."""
    t = t_of(n)
    env = np.where(t < a, t / max(a, 1e-4), s + (1 - s) * np.exp(-(t - a) / max(d, 1e-4)))
    rel = np.clip((t - hold) / max(r, 1e-4), 0, 1)
    return env * (1 - rel)


def trumpet(f, dur, vel=1.0, stacc=False):
    rel = 0.08 if stacc else 0.14
    n = int((dur + rel) * SR)
    t = t_of(n)
    env = adsr(n, 0.025, 0.12, 0.78, rel, dur)
    vib_amt = np.clip((t - 0.18) / 0.25, 0, 1) * (0 if stacc else 0.0055)
    vib = vib_amt * np.sin(2 * np.pi * 5.6 * t + rng.uniform(0, 6))
    scoop = 2 ** (-35 * np.exp(-t / 0.03) / 1200)
    inst = f * (1 + vib) * scoop
    ph = 2 * np.pi * np.cumsum(inst) / SR
    k_max = int(min(26, SR / 2 / (f * 1.04)))
    expo = 2.5 - 1.35 * env * vel
    out = np.zeros(n)
    norm = np.zeros(n)
    for k in range(1, k_max + 1):
        a = np.exp(-np.log(k) * expo) * (1.15 if k in (3, 4, 5) else 1)
        out += a * np.sin(k * ph)
        norm += a
    out /= norm
    breath = rng.normal(0, 1, n) * np.exp(-t / 0.03) * 0.04
    return (out + breath) * env * vel


def violins(f, dur, vel=0.8, voices=4, trem=False):
    rel = 0.18
    n = int((dur + rel) * SR)
    t = t_of(n)
    env = adsr(n, 0.07, 0.3, 0.85, rel, dur)
    if trem:
        env = env * (0.6 + 0.4 * np.sin(2 * np.pi * 13 * t) ** 2)
    k_max = int(min(28, SR / 2 / (f * 1.06)))
    ks = np.arange(1, k_max + 1)
    freqs = ks * f
    w = (1 + 1.6 * np.exp(-((freqs - 470) / 220) ** 2) + 1.2 * np.exp(-((freqs - 1150) / 380) ** 2)
         + 0.9 * np.exp(-((freqs - 2900) / 800) ** 2)) / ks ** 1.05
    out = np.zeros(n)
    for v in range(voices):
        det = 2 ** (rng.uniform(-7, 7) / 1200)
        vib = np.clip((t - 0.12) / 0.3, 0, 1) * 0.0045 * np.sin(2 * np.pi * rng.uniform(5.2, 6.4) * t + rng.uniform(0, 6))
        ph = 2 * np.pi * np.cumsum(f * det * (1 + vib)) / SR + rng.uniform(0, 6)
        for k, a in zip(ks, w):
            out += a * np.sin(k * ph)
    out /= voices * np.sum(w)
    out += rng.normal(0, 1, n) * 0.012 * env       # rosin
    return out * env * vel


def pluck(f, dur, bright=0.5, decay=0.996, body=1.0):
    """Karplus-Strong, run a whole period at a time, then resampled to the exact pitch."""
    N = max(4, int(round(SR / f)))
    L = int(dur * SR) + 2 * N
    init = rng.uniform(-1, 1, N)
    smooth = max(1, int((1 - bright) * 6))
    if smooth > 1:
        init = np.convolve(init, np.ones(smooth) / smooth, mode='same')
    y = np.zeros(L)
    y[:N] = init
    for k in range(1, L // N + 1):
        s, e = k * N, min(L, (k + 1) * N)
        if s >= L:
            break
        prev = y[s - N:e - N]
        prev2 = y[s - N - 1:e - N - 1] if s - N - 1 >= 0 else np.concatenate([[y[0]], y[s - N:e - N - 1]])
        y[s:e] = decay * 0.5 * (prev + prev2)
    f_ks = SR / (N + 0.5)
    pos = np.arange(int(dur * SR)) * (f / f_ks)
    out = np.interp(pos, np.arange(L), y)
    return fade(out, 0.0005, min(0.04, dur * 0.3)) * body


def strum(notes, dur, down=True, spread=0.007, bright=0.75, decay=0.994, vel=1.0, mute=False):
    order = notes if down else notes[::-1]
    n = int((dur + spread * len(notes) + 0.05) * SR)
    out = np.zeros(n)
    for i, m in enumerate(order):
        p = pluck(midi(m), dur, bright, decay)
        k = int(i * spread * SR)
        out[k:k + len(p)] += p * (0.8 + 0.2 * rng.random())
    if mute:
        out += rng.normal(0, 1, n) * np.exp(-t_of(n) / 0.01) * 0.12
    return out * vel / max(1, len(notes) ** 0.5)


def maraca(vel=0.3):
    n = int(0.09 * SR)
    t = t_of(n)
    return padded_filter(rng.normal(0, 1, n), lambda f: band(f, 4000, 11000)) * np.exp(-t / 0.02) * np.clip(t / 0.004, 0, 1) * vel


def glock(f, vel=0.4):
    n = int(1.2 * SR)
    t = t_of(n)
    return (np.sin(2 * np.pi * f * t) + 0.35 * np.sin(2 * np.pi * f * 2.76 * t) * np.exp(-t / 0.1)) * np.exp(-t / 0.35) * vel


# ===================================================================== the score

A_MINOR = [57, 59, 60, 62, 64, 65, 68]      # harmonic minor: the G# leads home


def third_below(m):
    pcs = [p % 12 for p in A_MINOR]
    if m % 12 in pcs:
        i = pcs.index(m % 12)
        target = pcs[(i - 2) % 7]
        d = (m % 12 - target) % 12
        return m - d
    return m - 4


CHORDS = {
    'Am': [9, 0, 4], 'E7': [4, 8, 11, 2], 'Dm': [2, 5, 9], 'G': [7, 11, 2], 'F': [5, 9, 0], 'C': [0, 4, 7],
    'G7': [7, 11, 2, 5],
}
ROOT_PC = {'Am': 9, 'E7': 4, 'Dm': 2, 'G': 7, 'F': 5, 'C': 0, 'G7': 7}


def voicing(chord, lo, hi, maxn=5):
    pcs = CHORDS[chord]
    notes = [m for m in range(lo, hi + 1) if m % 12 in pcs]
    return notes[:maxn]


def bass_root(chord, lo=33):
    pc = ROOT_PC[chord]
    m = lo + ((pc - lo) % 12)
    return m


def N(name):
    """'A4', 'G#5', 'Bb3' -> midi."""
    names = {'C': 0, 'D': 2, 'E': 4, 'F': 5, 'G': 7, 'A': 9, 'B': 11}
    pc = names[name[0]]
    rest = name[1:]
    if rest[0] == '#':
        pc += 1
        rest = rest[1:]
    elif rest[0] == 'b':
        pc -= 1
        rest = rest[1:]
    return 12 * (int(rest) + 1) + pc


def mel(s):
    """'E5:2 C5:1 r:1' -> [(midi or None, beats)]."""
    out = []
    for tok in s.split():
        n, b = tok.split(':')
        out.append((None if n == 'r' else N(n), float(b)))
    return out


class Mix:
    def __init__(self, seconds):
        self.n = int(seconds * SR)
        self.L = np.zeros(self.n)
        self.R = np.zeros(self.n)

    def add(self, sig, t, pan=0.0, gain=1.0):
        k = int(t * SR)
        if k >= self.n:
            return
        m = min(len(sig), self.n - k)
        gl = math.cos((pan + 1) * math.pi / 4) * gain
        gr = math.sin((pan + 1) * math.pi / 4) * gain
        self.L[k:k + m] += sig[:m] * gl
        self.R[k:k + m] += sig[:m] * gr


def play_melody(mix, start, bpm, notes, inst, pan, gain, harmony=False, octave=0, stacc=False, trem=False):
    beat = 60.0 / bpm
    t = start
    for m, b in notes:
        if m is not None:
            dur = b * beat * (0.55 if stacc else 0.95)
            f = midi(m + octave)
            sig = trumpet(f, dur, 0.95, stacc) if inst == 'tp' else violins(f, dur, 0.85, trem=trem)
            mix.add(sig, t, pan, gain)
            if harmony:
                f2 = midi(third_below(m) + octave)
                sig2 = trumpet(f2, dur, 0.85, stacc) if inst == 'tp' else violins(f2, dur, 0.75, trem=trem)
                mix.add(sig2, t, -pan, gain * 0.8)
        t += b * beat
    return t


def waltz(mix, start, bpm, chords, gain=1.0):
    """Ranchera: guitarron on one, vihuela and guitar on two and three."""
    beat = 60.0 / bpm
    for i, c in enumerate(chords):
        t0 = start + i * 3 * beat
        root = bass_root(c)
        b = pluck(midi(root), beat * 1.6, 0.55, 0.997) + 0.6 * pluck(midi(root + 12), beat * 1.6, 0.6, 0.996)
        mix.add(b, t0, 0.0, 0.9 * gain)
        fifth = root + 7 if root + 7 <= 47 else root - 5
        if i % 2 == 1:
            mix.add(pluck(midi(fifth), beat * 0.9, 0.55, 0.996), t0 + 2 * beat, 0.0, 0.5 * gain)
        for beat_i in (1, 2):
            v = strum(voicing(c, 64, 79), beat * 0.6, True, 0.006, 0.85, 0.992, 0.9)
            g = strum(voicing(c, 52, 67), beat * 0.7, True, 0.008, 0.6, 0.994, 0.8)
            mix.add(v, t0 + beat_i * beat, 0.45, 0.55 * gain)
            mix.add(g, t0 + beat_i * beat + 0.004, -0.45, 0.5 * gain)


def son(mix, start, bpm, chords, gain=1.0, maracas=True):
    """Son jalisciense: bars alternate 6/8 and 3/4 (sesquialtera); vihuela strums every eighth."""
    beat = 60.0 / bpm
    eighth = beat / 2
    for i, c in enumerate(chords):
        t0 = start + i * 3 * beat
        six_eight = i % 2 == 0
        accents = (0, 3) if six_eight else (0, 2, 4)
        dirs = 'DUDDUD' if six_eight else 'DUDUDU'
        for e in range(6):
            acc = e in accents
            v = strum(voicing(c, 64, 79), eighth * (1.3 if acc else 0.8), dirs[e] == 'D', 0.004, 0.9, 0.99,
                      1.0 if acc else 0.55, mute=(not acc and e % 2 == 1))
            mix.add(v, t0 + e * eighth, 0.45, 0.5 * gain)
            if acc:
                g = strum(voicing(c, 52, 67), eighth * 1.6, True, 0.006, 0.65, 0.993, 0.8)
                mix.add(g, t0 + e * eighth + 0.003, -0.45, 0.45 * gain)
            if maracas:
                mix.add(maraca(0.2 if acc else 0.1), t0 + e * eighth, 0.7 if e % 2 else -0.7, gain)
        root = bass_root(c)
        fifth = root + 7 if root + 7 <= 47 else root - 5
        hits = [(0, root), (3, fifth)] if six_eight else [(0, root), (2, fifth), (4, root + 12 if root + 12 <= 47 else root)]
        for e, m in hits:
            b = pluck(midi(m), eighth * 1.8, 0.6, 0.996) + 0.55 * pluck(midi(m + 12), eighth * 1.8, 0.65, 0.995)
            mix.add(b, t0 + e * eighth, 0.0, 0.85 * gain)


def bars(s):
    return s.split()


def compose():
    sections = []          # (start, tempo, bars, kind)
    t = 0.0
    total_guess = 140
    mix = Mix(total_guess)

    # ---------------- 1. Fanfarria (fast 3/4)
    bpm = 168
    beat = 60 / bpm
    ch = bars('Am Am E7 E7 Am Dm E7 Am')
    son(mix, t, bpm, ch, 0.8, maracas=False)
    fan = mel('A4:1 C5:1 E5:1 A5:3 G#5:1 F5:0.5 E5:0.5 D5:1 B4:2 E5:1 '
              'C5:1 E5:1 A5:1 F5:1 A5:1 D6:1 E5:0.5 F5:0.5 E5:0.5 D5:0.5 C5:0.5 B4:0.5 A4:2 r:1')
    play_melody(mix, t, bpm, fan, 'tp', 0.15, 0.55, harmony=True)
    play_melody(mix, t + 4 * 3 * beat, bpm, mel('A5:3 A5:3 G#5:3 A5:2 r:1'), 'vn', -0.35, 0.35)
    t += len(ch) * 3 * beat

    # ---------------- 2. Ranchera lenta (slow waltz): violins sing, trumpets join for the second half
    bpm = 84
    beat = 60 / bpm
    ch = bars('Am Am Dm Dm E7 E7 Am Am F Dm Dm Am E7 E7 Am E7')
    waltz(mix, t, bpm, ch)
    ranchera = mel('E5:2 C5:1 A4:1 B4:1 C5:1 D5:2 F5:1 E5:1.5 D5:0.5 C5:1 B4:2 G#4:1 B4:1 D5:1 F5:1 E5:3 A4:1 B4:1 C5:1 '
                   'C5:2 A4:1 D5:1 E5:1 F5:1 A5:2 G5:0.5 F5:0.5 E5:2 C5:1 D5:1.5 C5:0.5 B4:1 G#4:1 B4:1 E5:1 A4:3 r:1 B4:1 G#4:1')
    play_melody(mix, t, bpm, ranchera, 'vn', -0.3, 0.75)
    play_melody(mix, t + 8 * 3 * beat, bpm,
                mel('A4:3 F4:3 F4:3 E4:3 E4:3 D4:3 C4:3 B3:3'), 'tp', 0.3, 0.32, harmony=True, octave=12)
    t += len(ch) * 3 * beat

    # ---------------- 3. Son (fast): the violins' riff, the trumpets answer, then everyone
    bpm = 180
    beat = 60 / bpm
    phrase1 = bars('Am Am G G F F E7 E7')
    phrase2 = bars('Am E7 E7 Am Am E7 E7 Am')
    ch = phrase1 + phrase2 + phrase1
    son(mix, t, bpm, ch)
    riff = mel('A5:0.5 E5:0.5 C5:0.5 E5:0.5 A5:0.5 B5:0.5 C6:1 B5:0.5 A5:0.5 E5:1 '
               'D5:0.5 G5:0.5 B5:0.5 G5:0.5 D6:0.5 B5:0.5 D6:1 C6:0.5 B5:0.5 G5:1 '
               'C5:0.5 F5:0.5 A5:0.5 F5:0.5 C6:0.5 A5:0.5 C6:1 B5:0.5 A5:0.5 F5:1 '
               'E5:0.5 G#5:0.5 B5:0.5 D6:0.5 B5:0.5 G#5:0.5 E5:1.5 r:1.5')
    answer = mel('r:3 r:3 r:3 r:3 r:3 r:3 r:3 r:1 B4:0.5 C5:0.5 D5:0.5 E5:0.5')
    trumpets2 = mel('E5:1 A5:1 G#5:0.5 A5:0.5 B5:1.5 A5:0.5 G#5:1 F5:1 E5:1 D5:1 C5:1 E5:1 A5:1 '
                    'C6:1.5 B5:0.5 A5:1 G#5:1 F5:1 E5:1 D5:0.5 E5:0.5 F5:0.5 G#5:0.5 B5:1 A5:2 r:1')
    t1 = play_melody(mix, t, bpm, riff, 'vn', -0.3, 0.6)
    play_melody(mix, t, bpm, answer, 'tp', 0.25, 0.5, harmony=True)
    t2 = play_melody(mix, t1, bpm, trumpets2, 'tp', 0.2, 0.55, harmony=True)
    play_melody(mix, t1, bpm, mel('A4:3 B4:3 B4:3 C5:3 E5:3 D5:3 B4:3 A4:3'), 'vn', -0.35, 0.3)
    play_melody(mix, t2, bpm, riff, 'vn', -0.3, 0.6)
    play_melody(mix, t2, bpm, riff, 'tp', 0.2, 0.3, harmony=True, octave=-12)
    t += len(ch) * 3 * beat

    # ---------------- 4. Puente lento (slow, ominous): a lone trumpet over trembling strings
    bpm = 72
    beat = 60 / bpm
    ch = bars('Dm Am E7 Am F Dm E7 E7')
    for i, c in enumerate(ch):
        t0 = t + i * 3 * beat
        root = bass_root(c)
        mix.add(pluck(midi(root), beat * 2.8, 0.5, 0.998) + 0.5 * pluck(midi(root + 12), beat * 2.8, 0.5, 0.997), t0, 0, 0.8)
        hold = 4.4 if i == len(ch) - 1 else 2.9          # the last chord rides through the fermata
        for m in voicing(c, 57, 69, 3):
            mix.add(violins(midi(m), beat * hold, 0.5, voices=3, trem=True), t0, -0.4 + 0.4 * (m % 3), 0.28)
        mix.add(strum(voicing(c, 52, 67), beat * 2.5, True, 0.03, 0.5, 0.997, 0.7), t0 + beat, -0.5, 0.35)
    solo = mel('F5:2 E5:1 E5:1 C5:1 A4:1 G#4:1.5 A4:0.5 B4:1 C5:3 A5:2 G5:1 F5:1 E5:1 D5:1 E5:1 D5:1 B4:1 G#4:4.4')
    play_melody(mix, t, bpm, solo, 'tp', 0.1, 0.6)
    t += len(ch) * 3 * beat + beat * 1.5           # the fermata

    # ---------------- 5. Son final (faster), and the chan-chan
    bpm = 200
    beat = 60 / bpm
    ch = phrase1 + phrase2 + bars('E7 E7 E7 E7')
    son(mix, t, bpm, ch, 1.0)
    t1 = play_melody(mix, t, bpm, riff, 'vn', -0.3, 0.6)
    play_melody(mix, t, bpm, riff, 'tp', 0.25, 0.4, harmony=True)
    t2 = play_melody(mix, t1, bpm, trumpets2, 'tp', 0.2, 0.6, harmony=True, octave=0)
    play_melody(mix, t1, bpm, trumpets2, 'vn', -0.3, 0.35, octave=12)
    run = mel('E5:0.5 F5:0.5 G#5:0.5 A5:0.5 B5:0.5 C6:0.5 D6:0.5 C6:0.5 B5:0.5 A5:0.5 G#5:0.5 F5:0.5 '
              'E5:0.5 G#5:0.5 B5:0.5 D6:0.5 E6:1 D6:0.5 B5:0.5 G#5:0.5 B5:0.5 E6:2')
    t3 = play_melody(mix, t2, bpm, run, 'vn', -0.3, 0.55)
    play_melody(mix, t2, bpm, mel('B4:3 B4:3 D5:3 E5:3'), 'tp', 0.2, 0.5, harmony=True)
    # chan-chan: the dominant and the tonic, short and loud, then silence for the loop
    end = t2 + 4 * 3 * beat
    for k, (c, top) in enumerate((('E7', 'B5'), ('Am', 'A5'))):
        tt = end + k * 1.0 * beat
        mix.add(strum(voicing(c, 52, 79, 7), beat * 0.35, True, 0.003, 0.9, 0.98, 1.4), tt, 0.3, 0.8)
        mix.add(pluck(midi(bass_root(c)), beat * 0.5, 0.7, 0.99) * 1.4, tt, 0, 1.0)
        mix.add(trumpet(midi(N(top)), beat * 0.3, 1.0, True), tt, 0.2, 0.7)
        mix.add(trumpet(midi(third_below(N(top))), beat * 0.3, 0.9, True), tt, -0.2, 0.6)
        mix.add(violins(midi(N(top) + 12), beat * 0.3, 0.9), tt, -0.4, 0.4)
    length = end + 2.4 * beat + 1.2
    return mix, length


def reverb(x, seconds=1.7, wet=0.16, seed=0):
    r = np.random.default_rng(seed)
    n = int(seconds * SR)
    t = t_of(n)
    ir = r.normal(0, 1, n) * np.exp(-t / 0.42)
    ir = padded_filter(ir, lambda f: lowpass(f, 4500, 1))
    ir[:int(0.012 * SR)] = 0
    ir /= np.sqrt(np.sum(ir ** 2))
    size = 1 << int(np.ceil(np.log2(len(x) + n)))
    y = np.fft.irfft(np.fft.rfft(x, size) * np.fft.rfft(ir, size), size)
    # fold the tail back to the start so the loop's reverb carries over the seam
    y_loop = y[:len(x)].copy()
    tail = y[len(x):len(x) + n]
    y_loop[:len(tail)] += tail
    return x * (1 - wet) + y_loop * wet * 2.2


def music():
    mix, length = compose()
    n = int(length * SR)
    L, R = mix.L[:n], mix.R[:n]
    L = reverb(L, wet=0.17, seed=1)
    R = reverb(R, wet=0.17, seed=2)
    st = np.stack([L, R])
    st /= np.max(np.abs(st))
    st = np.tanh(st * 1.6) / np.tanh(1.6)
    save('music/boss.ogg', st, stereo=True)


def reveal():
    """The scalp catches the light: a choir of angels on C major, swelling, and a glockenspiel sparkle."""
    n = int(3.0 * SR)
    t = t_of(n)
    out = np.zeros(n)
    for m in (60, 64, 67, 72, 76):
        f = midi(m)
        for v in range(3):
            det = 2 ** (rng.uniform(-9, 9) / 1200)
            ph = 2 * np.pi * f * det * t + 0.004 * np.sin(2 * np.pi * 5.5 * t + v) * 2 * np.pi
            k_max = int(min(30, SR / 2 / f))
            for k in range(1, k_max + 1):
                fk = k * f
                form = np.exp(-((fk - 800) / 260) ** 2) + 0.8 * np.exp(-((fk - 1150) / 300) ** 2) + 0.25 * np.exp(-((fk - 2900) / 500) ** 2)
                out += np.sin(k * ph) * (0.15 / k + form) * 0.1
    env = np.clip(t / 0.5, 0, 1) ** 1.5 * np.clip((3.0 - t) / 1.0, 0, 1)
    out *= env
    for i, m in enumerate((84, 88, 91, 96, 100)):
        g = glock(midi(m), 0.5)
        k = int((0.45 + i * 0.07) * SR)
        out[k:k + len(g)] += g[:n - k] * 1.6
    shimmer = padded_filter(rng.normal(0, 1, n), lambda f: band(f, 6000, 14000)) * np.exp(-np.abs(t - 0.5) / 0.25) * 0.15
    save('boss/reveal.ogg', fade(out + shimmer, 0.01, 0.3))


if __name__ == '__main__':
    caravan_sounds()
    reveal()
    music()
