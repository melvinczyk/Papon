"""A little synth kit for the cinematic sounds (gen_p4_sounds.py, gen_giant_sounds.py): filters, a convolution reverb, a formant
choir, the Aztec death whistle, a huehuetl drum, a temple bell, a brass braam, ogg writing and a spectrogram for checking by eye.

Everything is mono, 44.1 kHz, float arrays of any length; `mix` adds arrays of different lengths at an offset.
"""
import os

import numpy as np
import scipy.signal as ss
import soundfile as sf

SR = 44100
TAU = 2 * np.pi


def rng_of(seed):
    return np.random.default_rng(seed)


def tt(sec):
    return np.arange(int(sec * SR)) / SR


# ------------------------------------------------------------------ filters

def lp(x, f, order=2):
    return ss.sosfilt(ss.butter(order, min(f, SR / 2 - 200), fs=SR, output='sos'), x)


def hp(x, f, order=2):
    return ss.sosfilt(ss.butter(order, f, btype='high', fs=SR, output='sos'), x)


def bp(x, lo, hi, order=2):
    return ss.sosfilt(ss.butter(order, [lo, min(hi, SR / 2 - 200)], btype='band', fs=SR, output='sos'), x)


def sweep_bp(x, centre, width=0.5, block=1024):
    """Noise through a band-pass whose centre (Hz, an array like x) moves: blockwise, with a short overlap so the seams do not click."""
    out = np.zeros_like(x)
    for a in range(0, len(x), block):
        c = float(centre[min(a, len(centre) - 1)])
        lo, hi = max(40.0, c * (1 - width)), min(SR / 2 - 300, c * (1 + width))
        if hi <= lo * 1.05:
            continue
        s0 = max(0, a - 512)
        seg = bp(x[s0:a + block], lo, hi)
        out[a:a + block] = seg[a - s0:]
    return out


def sweep_lp(x, cutoff, block=1024):
    out = np.zeros_like(x)
    for a in range(0, len(x), block):
        c = float(cutoff[min(a, len(cutoff) - 1)])
        s0 = max(0, a - 512)
        seg = lp(x[s0:a + block], max(60.0, c))
        out[a:a + block] = seg[a - s0:]
    return out


def saw(ph):
    return 2 * ((ph / TAU) % 1) - 1


def norm(y, peak=0.9):
    return y / (np.abs(y).max() + 1e-9) * peak


def rms(y):
    return float(np.sqrt(np.mean(y * y) + 1e-12))


def soft(x, drive=2.0, peak=0.95):
    """Soft clip: loudness without the spikes (tanh, of the sound scaled to its peak first), to the peak."""
    x = np.tanh(x / (np.abs(x).max() + 1e-9) * drive)
    return x / (np.abs(x).max() + 1e-9) * peak


def fade(x, a=0.005, r=0.02):
    n = len(x)
    e = np.ones(n)
    na, nr = int(a * SR), int(r * SR)
    if na:
        e[:na] = np.linspace(0, 1, na)
    if nr:
        e[n - nr:] = np.linspace(1, 0, nr)
    return x * e


def mix(*parts, length=None):
    """parts: arrays, or (array, offset seconds[, gain])."""
    items = []
    for p in parts:
        if isinstance(p, tuple):
            y, off = p[0], p[1]
            g = p[2] if len(p) > 2 else 1.0
        else:
            y, off, g = p, 0.0, 1.0
        items.append((y * g, int(off * SR)))
    n = max(len(y) + o for y, o in items) if length is None else length
    out = np.zeros(n)
    for y, o in items:
        m = min(len(y), n - o)
        if m > 0:
            out[o:o + m] += y[:m]
    return out


def fit(y, n):
    """y cut or zero-padded to exactly n samples."""
    out = np.zeros(n)
    m = min(n, len(y))
    out[:m] = y[:m]
    return out


def pad(x, sec):
    return np.concatenate([x, np.zeros(int(sec * SR))])


# ------------------------------------------------------------------ space

def reverb(x, sec=3.0, wet=0.3, pre=0.015, dark=5000, seed=7):
    """Convolution with a synthetic hall: decaying noise, the highs dying faster than the lows. Returns the input plus its tail."""
    r = rng_of(seed)
    n = int(sec * SR)
    t = np.arange(n) / SR
    k = 6.9 / sec
    ir = lp(r.normal(0, 1, n), dark) * np.exp(-t * k) + hp(r.normal(0, 1, n), 3000) * np.exp(-t * k * 2.4) * 0.5
    ir[:int(pre * SR)] = 0
    ir *= np.minimum(1, t / 0.012)
    ir /= np.sqrt(np.sum(ir * ir)) + 1e-9
    y = np.concatenate([x, np.zeros(n)])
    w = ss.fftconvolve(y, ir)[:len(y)]
    w *= rms(x) / (rms(w) + 1e-9) * 0.9
    return y * (1 - wet * 0.5) + w * wet * 1.6


# ------------------------------------------------------------------ instruments

VOWELS = {
    'ah': [(800, 90, 1.0), (1150, 100, 0.55), (2900, 140, 0.14)],
    'oh': [(450, 70, 1.0), (800, 90, 0.45), (2830, 140, 0.1)],
    'oo': [(325, 60, 1.0), (700, 80, 0.3), (2700, 140, 0.08)],
    'ee': [(300, 60, 0.8), (2300, 150, 0.7), (3000, 160, 0.3)],
}


def voice(f, dur, vowel='ah', vib=5.2, vib_depth=0.006, rise=0.0, breath=0.07, harmonics=22, seed=0, glide=None):
    """One sung voice: harmonics weighted by the vowel's formants, a vibrato that grows in, a breath of noise through the same mouth."""
    r = rng_of(seed)
    n = int(dur * SR)
    t = np.arange(n) / SR
    ph0 = r.uniform(0, TAU)
    drift = lp(r.normal(0, 1, n), 1.5) * 0.0035
    fr = f * (1 + vib_depth * np.sin(TAU * vib * t + ph0) * np.clip(t / 0.7, 0, 1) + drift)
    if glide is not None:
        fr = fr * glide(t)
    ph = np.cumsum(TAU * fr / SR)
    out = np.zeros(n)
    for k in range(1, harmonics + 1):
        fk = f * k
        if fk > 9000:
            break
        g = 0.015 + sum(a * np.exp(-0.5 * ((fk - F) / bw) ** 2) for F, bw, a in VOWELS[vowel])
        out += g / k ** 0.35 * np.sin(k * ph + k * 0.7)
    mouth = sum(bp(r.normal(0, 1, n), max(120, F - bw), F + bw) * a for F, bw, a in VOWELS[vowel])
    out = out / (np.abs(out).max() + 1e-9) + breath * mouth / (np.abs(mouth).max() + 1e-9)
    e = np.minimum(1, t / max(0.05, rise)) if rise > 0 else np.ones(n)
    return out * e


def choir(notes, dur, vowel='ah', voices=3, rise=1.0, seed=0, glide=None, spread=0.006, tilt=0.18):
    """A chord sung by an ensemble: each note a few detuned voices (so it shimmers and does not beat in a line)."""
    out = np.zeros(int(dur * SR))
    for i, f in enumerate(notes):
        for v in range(voices):
            d = (v - (voices - 1) / 2) * spread
            y = voice(f * (1 + d), dur, vowel, rise=rise, seed=seed + i * 17 + v * 3, vib=4.8 + 0.25 * ((i + v) % 5), glide=glide)
            out += y / (1 + i * tilt)
    return out / (len(notes) * voices) ** 0.55


def death_whistle(dur, f0, f1, seed=0, peak=0.9, curve=1.0):
    """The Aztec death whistle: a human scream out of a skull-shaped tube. A shrill pitch that wavers, shot through with breath and
    a second, rougher whistle a fifth over it, swelling in and tearing off at the end."""
    r = rng_of(seed)
    n = int(dur * SR)
    t = np.arange(n) / SR
    u = t / dur
    fc = f0 + (f1 - f0) * u ** curve
    fc = fc * (1 + 0.012 * np.sin(TAU * 6.3 * t) + lp(r.normal(0, 1, n), 40) * 0.05)
    ph = np.cumsum(TAU * fc / SR)
    amp = 0.55 + 0.45 * np.abs(lp(r.normal(0, 1, n), 25)) * 6
    tone = np.sin(ph) + 0.55 * np.sin(2 * ph + 0.4) + 0.3 * np.sin(1.5 * ph)
    tone *= np.minimum(1.5, amp)
    breathy = sweep_bp(r.normal(0, 1, n), fc * 1.1, 0.35) * 2.2
    y = tone * 0.6 + breathy
    y = hp(y, 500)
    e = np.minimum(1, t / (dur * 0.35 + 0.01)) ** 1.4 * np.minimum(1, (dur - t) / 0.09)
    return norm(y * e, peak)


def drum(f=70, dur=0.9, hardness=1.0, seed=0, amp=1.0):
    """A huehuetl: a deep skin drum, the head's pitch falling after the strike, a slap of noise on top."""
    r = rng_of(seed)
    t = tt(dur)
    fr = f * (1 + 0.55 * np.exp(-t * 28)) + 6 * np.exp(-t * 4)
    body = np.sin(np.cumsum(TAU * fr / SR)) * np.exp(-t * (5.0 + 0.5 * hardness))
    over = np.sin(np.cumsum(TAU * fr * 1.58 / SR)) * np.exp(-t * 11) * 0.35
    slap = bp(r.normal(0, 1, len(t)), 900, 6000) * np.exp(-t * 55) * 0.55 * hardness
    thud = lp(r.normal(0, 1, len(t)), 220) * np.exp(-t * 24) * 0.5
    return (body + over + slap + thud) * amp * np.minimum(1, t / 0.002)


def bell(f, dur, seed=0, bright=1.0):
    """A big temple bell: inharmonic partials, the upper ones dying first, the beating pair of a cast bell."""
    t = tt(dur)
    parts = ((0.5, 0.45, 0.3), (1.0, 1.0, 0.42), (1.0042, 0.8, 0.45), (1.19, 0.55, 0.62), (1.5, 0.4, 0.8), (2.0, 0.62, 0.7), (2.74, 0.4, 1.1),
             (3.0, 0.3, 1.4), (4.16, 0.22 * bright, 2.0), (5.43, 0.16 * bright, 2.8), (6.8, 0.1 * bright, 3.5))
    y = sum(g * np.sin(TAU * f * r * t + k) * np.exp(-t * d) for k, (r, g, d) in enumerate(parts))
    strike = hp(rng_of(seed).normal(0, 1, len(t)), 2500) * np.exp(-t * 60) * 0.4
    return (y + strike) * np.minimum(1, t / 0.002)


def braam(freqs, dur, cutoff=(250, 2200, 500), seed=0, bite=1.0):
    """The cinematic brass hit: fat detuned saws, a filter that snaps open on the strike and closes as it rings out."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    y = np.zeros(n)
    for i, f in enumerate(freqs):
        for d in (-0.011, 0.0, 0.009):
            y += saw(np.cumsum(TAU * f * (1 + d + 0.002 * i) / SR) + i + d * 300)
    cut = cutoff[0] + (cutoff[1] - cutoff[0]) * np.exp(-t * 7) * bite + (cutoff[2] - cutoff[0]) * (1 - np.exp(-t * 0.8)) * 0.4
    y = sweep_lp(y, cut)
    env = np.minimum(1, t / 0.03) * np.exp(-t * 0.85)
    return y * env / len(freqs)


def conch(f, dur, seed=0, vib=5.0):
    """A conch-shell trumpet: a hollow, breathy horn with a slow swell and a wavering pitch, the call of the temples."""
    r = rng_of(seed)
    n = int(dur * SR)
    t = np.arange(n) / SR
    fr = f * (1 + 0.012 * np.sin(TAU * vib * t) * np.clip(t / 0.5, 0, 1) + lp(r.normal(0, 1, n), 8) * 0.012) * (1 - 0.05 * np.exp(-t * 12))
    ph = np.cumsum(TAU * fr / SR)
    y = sum(np.sin(k * ph) / k ** 1.1 for k in range(1, 9)) + 0.5 * np.sin(ph * 0.5)
    y = lp(y, 1400)
    y += bp(r.normal(0, 1, n), 300, 1800) * 0.25
    e = np.minimum(1, t / 0.22) ** 1.2 * np.minimum(1, (dur - t) / 0.35)
    return y * e


def crackle(n, rate, amp=1.0, seed=0, lo=1800):
    """Electric / fire crackle: sparse clicks, each a short decaying burst of bright noise."""
    r = rng_of(seed)
    out = np.zeros(n)
    k = int(n / SR * rate)
    for i in r.integers(0, max(1, n - 800), k):
        L = int(r.integers(80, 600))
        out[i:i + L] += r.normal(0, 1, L) * np.exp(-np.arange(L) / (L / 4)) * r.uniform(0.3, 1.0)
    return hp(out, lo) * amp


def rattle(dur, hits, seed=0, lo=2500, hi=9000):
    """Bone rattle: a burst of clicks of dry, hollow noise."""
    r = rng_of(seed)
    n = int(dur * SR)
    out = np.zeros(n)
    for _ in range(hits):
        i = int(r.uniform(0, 0.92) * n)
        L = int(r.integers(300, 1400))
        seg = bp(r.normal(0, 1, L), lo * r.uniform(0.8, 1.3), hi) * np.exp(-np.arange(L) / (L / 5)) * r.uniform(0.3, 1.0)
        out[i:i + L] += seg[:max(0, n - i)][:L]
    return out


# ------------------------------------------------------------------ output

def write_ogg(path, y, peak=None):
    y = np.asarray(y, dtype=np.float32)
    if peak:
        y = y / (np.abs(y).max() + 1e-9) * peak
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with sf.SoundFile(path, 'w', SR, 1, format='OGG', subtype='VORBIS') as f:
        for i in range(0, len(y), 1 << 15):            # in chunks (libsndfile's Vorbis encoder overflows the stack on Windows)
            f.write(y[i:i + (1 << 15)])


def spectrogram_png(y, path, win=2048, hop=512, fmax=10000, label=None):
    """A quick look at a sound: dB spectrogram, time across, 0..fmax Hz up, plus its level (RMS dBFS) along the bottom."""
    from PIL import Image
    f, t, S = ss.spectrogram(y, SR, nperseg=win, noverlap=win - hop, window='hann')
    S = 10 * np.log10(S + 1e-12)
    keep = f <= fmax
    S = S[keep][::-1]
    img = np.clip((S - (S.max() - 80)) / 80, 0, 1)
    h = img.shape[0]
    rgb = np.zeros((h, img.shape[1], 3))
    rgb[..., 0] = np.clip(img * 2.2 - 0.6, 0, 1)
    rgb[..., 1] = np.clip(img * 2.0 - 0.9, 0, 1)
    rgb[..., 2] = np.clip(img * 1.4, 0, 1) * 0.9
    out = (rgb * 255).astype(np.uint8)
    # the level strip
    seg = hop
    nlev = img.shape[1]
    lev = np.array([20 * np.log10(rms(y[i * seg:(i + 1) * seg * 4]) + 1e-6) for i in range(nlev)])
    strip = np.zeros((40, nlev, 3), dtype=np.uint8)
    for i in range(nlev):
        v = int(np.clip((lev[i] + 60) / 60, 0, 1) * 38)
        strip[39 - v:, i] = (240, 200, 60)
    Image.fromarray(np.vstack([out, strip])).resize((min(1600, img.shape[1]), h // 2 + 40)).save(path)
