"""Masters the band's songs to one loudness (2026-10-06, the user: "the music is still too quiet").

Minecraft clamps a sound's gain at 1, so the music cannot be turned up past full in code: the files themselves have to be
loud. Canción del Mariachi came out of gen_song.py near -10 LUFS; Para Siempre and Persona Ideal sat 3-7 dB under it. This
brings every song to TARGET with one gain per song (the segments are cut from one recording and play back to back, so they
keep their levels relative to each other and the seams stay matched), then a peak limiter (lookahead, fast release) so
nothing clips. Idempotent: a song already at TARGET is left alone.

  python tools/master_music.py [song ...]     (default: every folder under sounds/music/ but the phase-one boss track)
"""
import glob
import os
import sys

import numpy as np
import pyloudnorm as pyln
import soundfile as sf
from scipy.ndimage import maximum_filter1d

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'papon', 'sounds', 'music')
TARGET = -10.0          # LUFS over the whole song
CEILING = 10 ** (-0.8 / 20)
LOOKAHEAD = 0.004       # s
RELEASE = 0.08          # s
BLOCK = 128


def limit(x, sr):
    """Lookahead peak limiter: gain never lets a sample past CEILING, eases back up over RELEASE."""
    peak = np.abs(x).max(axis=1)
    win = max(1, int(LOOKAHEAD * sr))
    env = maximum_filter1d(peak, size=2 * win + 1)
    need = np.minimum(1.0, CEILING / np.maximum(env, 1e-9))
    nb = (len(need) + BLOCK - 1) // BLOCK
    blocks = np.ones(nb)
    for i in range(nb):
        blocks[i] = need[i * BLOCK:(i + 1) * BLOCK].min()
    rel = np.exp(-BLOCK / (RELEASE * sr))
    g = np.empty(nb)
    cur = 1.0
    for i in range(nb):
        cur = blocks[i] if blocks[i] < cur else blocks[i] + (cur - blocks[i]) * rel
        g[i] = cur
    gs = np.interp(np.arange(len(need)), np.arange(nb) * BLOCK + BLOCK / 2, g)
    gs = np.minimum(gs, need)
    return x * gs[:, None]


def master(folder):
    files = sorted(glob.glob(os.path.join(folder, '*.ogg')))
    if not files:
        return
    data = []
    sr = None
    for f in files:
        d, sr = sf.read(f, always_2d=True)
        data.append(d)
    whole = np.concatenate(data)
    loud = pyln.Meter(sr).integrated_loudness(whole)
    gain = TARGET - loud
    name = os.path.basename(folder)
    if gain < 0.5:
        print(f'{name}: {loud:.1f} LUFS, left alone')
        return
    k = 10 ** (gain / 20)
    for f, d in zip(files, data):
        out = limit(d * k, sr)
        with sf.SoundFile(f, 'w', sr, out.shape[1], format='OGG', subtype='VORBIS') as w:
            # in chunks: one big write overflows libsndfile's Vorbis encoder's stack on Windows
            for i in range(0, len(out), 1 << 15):
                w.write(out[i:i + (1 << 15)].astype(np.float32))
    after = pyln.Meter(sr).integrated_loudness(np.concatenate([sf.read(f, always_2d=True)[0] for f in files]))
    print(f'{name}: {loud:.1f} -> {after:.1f} LUFS (+{gain:.1f} dB), {len(files)} segments')


if __name__ == '__main__':
    names = sys.argv[1:] or [d for d in os.listdir(ROOT) if os.path.isdir(os.path.join(ROOT, d))]
    for n in names:
        master(os.path.join(ROOT, n))
