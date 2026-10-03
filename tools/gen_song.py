#!/usr/bin/env python3
"""The phase-two song: the band's singer sings it, the fight is scored by it.

Input (not in git, the user's own copy): tools/src/song/cancion_del_mariachi.mp3
  ("Canción del Mariachi", Antonio Banderas & Los Lobos).

Steps (each cached under tools/.cache/song/, delete a file there to redo that step):
  1. demucs (htdemucs) splits it into vocals / drums / bass / other (the guitars)
  2. Rhubarb Lip Sync (tools/.cache/rhubarb, phonetic recogniser: it is sung in Spanish) reads the
     vocals into mouth shapes A-H, X
  3. librosa tracks the beats; the song is cut on beats, a whole number of bars to a segment, into
     the sections below, each with an energy level for the adaptive score (client/SongPlayer)
  4. the vocal stem gives a loudness envelope (how wide he opens up) and a pitch track (his brows
     go up on the high notes); the guitar stem gives the strums (his right hand)

Writes:
  src/main/resources/assets/papon/sounds/music/song/<segment>.ogg   each segment, with a short lead-in
                                                                     and tail so joins crossfade
  src/main/resources/assets/papon/song/cancion.json                 segments, beats, lip sync, envelopes
  sounds.json entries "music.song.<segment>" (streamed)

  pip install demucs librosa soundfile numpy
  python tools/gen_song.py
"""
import json
import os
import subprocess

import numpy as np
import soundfile as sf

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/papon')
SRC = os.path.join(ROOT, 'tools/src/song/cancion_del_mariachi.mp3')
CACHE = os.path.join(ROOT, 'tools/.cache/song')
RHUBARB = os.path.join(ROOT, 'tools/.cache/rhubarb/Rhubarb-Lip-Sync-1.13.0-Windows/rhubarb.exe')
SR = 44100
RATE = 25            # envelope samples per second
PRE = 0.08           # lead-in kept before a segment's first beat (pickups, the attack)
TAIL = 0.10          # kept after its end, faded: the overlap into the next segment

# The song, section by section. Beats are indices into the tracked beats (bar lines every 4).
#   level: intro (the cutscene), low / mid / high (the fight's moods), end (his death)
#   energy: how hard it hits, 0..1 (the crowd and the band use it too)
SECTIONS = [
    # id           first beat, last beat (exclusive), level, energy, sung?
    ('intro',      0, 16, 'intro', 0.55, False),   # the guitar alone: the close-up, the apology
    ('verse1',     16, 64, 'mid', 0.72, True),     # first verse and chorus
    ('break1',     64, 80, 'low', 0.55, False),    # a guitar break
    ('verse2',     80, 128, 'mid', 0.75, True),    # second verse and chorus
    ('solo1',      128, 144, 'mid', 0.70, False),  # the guitar solo
    ('solo2',      144, 176, 'high', 0.86, False), # ...the drums come in under it
    ('breakdown',  176, 192, 'low', 0.42, True),   # voice and hand drums only
    ('climax1',    192, 224, 'high', 0.95, True),  # the last choruses, all in
    ('climax2',    224, 248, 'high', 1.00, True),
    ('ending',     248, 260, 'end', 0.80, True),   # the last line and the final hit
]


def run(cmd):
    print('$', ' '.join(cmd))
    subprocess.run(cmd, check=True)


def stems():
    want = [os.path.join(CACHE, n + '.wav') for n in ('vocals', 'other', 'drums', 'bass')]
    if all(os.path.exists(p) for p in want):
        return
    import torch
    from demucs.apply import apply_model
    from demucs.pretrained import get_model
    x, sr = sf.read(SRC, always_2d=True)
    model = get_model('htdemucs')
    model.eval()
    wav = torch.tensor(x.T, dtype=torch.float32)
    ref = wav.mean(0)
    wav = (wav - ref.mean()) / ref.std()
    with torch.no_grad():
        out = apply_model(model, wav[None], device='cpu', split=True, overlap=0.25, progress=True)[0]
    out = out * ref.std() + ref.mean()
    for name, stem in zip(model.sources, out):
        sf.write(os.path.join(CACHE, name + '.wav'), stem.numpy().T, model.samplerate)


def lipsync():
    out = os.path.join(CACHE, 'mouth.json')
    if not os.path.exists(out):
        run([RHUBARB, '-r', 'phonetic', '--extendedShapes', 'GHX', '-f', 'json', '-o', out,
             os.path.join(CACHE, 'vocals.wav')])
    return [(c['start'], c['value']) for c in json.load(open(out))['mouthCues']]


def beats(mix):
    import librosa
    path = os.path.join(CACHE, 'beats.npy')
    if os.path.exists(path):
        b = np.load(path)
    else:
        y = librosa.resample(mix.mean(1).astype(np.float32), orig_sr=SR, target_sr=22050)
        env = librosa.onset.onset_strength(y=y, sr=22050, aggregate=np.median)
        _, b = librosa.beat.beat_track(onset_envelope=env, sr=22050, units='time', start_bpm=128, tightness=200)
        np.save(path, b)
    # the tracker gives up before the end: carry on at the last few beats' pace
    step = float(np.median(np.diff(b[-16:])))
    while b[-1] < len(mix) / SR + 2:
        b = np.append(b, b[-1] + step)
    return b


def envelope(sig, smooth=0.0):
    hop = SR // RATE
    n = len(sig) // hop
    e = np.sqrt((sig[:n * hop].reshape(n, hop) ** 2).mean(1))
    e = e / (np.percentile(e, 98) or 1)
    return np.clip(e, 0, 1)


def pitch(vocals):
    """The sung note per envelope frame, in semitones over the singer's median (0 where unvoiced)."""
    import librosa
    y = librosa.resample(vocals.astype(np.float32), orig_sr=SR, target_sr=16000)
    f0, voiced, _ = librosa.pyin(y, fmin=80, fmax=800, sr=16000, frame_length=1024, hop_length=640)
    semis = np.where(voiced, 12 * np.log2(np.nan_to_num(f0, nan=1.0) / 1.0 + 1e-9), np.nan)
    med = np.nanmedian(semis)
    out = np.nan_to_num(semis - med, nan=0.0)
    return np.clip(out, -12, 12)


def strums(other):
    """Guitar attacks: a spiky onset curve of the guitar stem, 0..1 per envelope frame."""
    import librosa
    y = librosa.resample(other.astype(np.float32), orig_sr=SR, target_sr=22050)
    env = librosa.onset.onset_strength(y=y, sr=22050, hop_length=882)
    env = env / (np.percentile(env, 99) or 1)
    return np.clip(env, 0, 1)


def segment_audio(mix, t0, t1, first, last, pre=PRE):
    a = max(0, int((t0 - pre) * SR))
    b = min(len(mix), int((t1 + TAIL) * SR))
    seg = mix[a:b].copy()
    n_in = int(0.006 * SR) if first else int(pre * SR * 0.6)
    n_out = int(TAIL * SR) if not last else int(0.8 * SR)
    seg[:n_in] *= np.linspace(0, 1, n_in)[:, None]
    seg[-n_out:] *= np.linspace(1, 0, n_out)[:, None] ** 1.5
    return seg, (t0 - a / SR)


def main():
    os.makedirs(CACHE, exist_ok=True)
    mix, sr = sf.read(SRC, always_2d=True)
    assert sr == SR, sr
    stems()
    vocals = sf.read(os.path.join(CACHE, 'vocals.wav'), always_2d=True)[0].mean(1)
    other = sf.read(os.path.join(CACHE, 'other.wav'), always_2d=True)[0].mean(1)
    cues = lipsync()
    b = beats(mix)
    out_dir = os.path.join(ASSETS, 'sounds/music/song')
    os.makedirs(out_dir, exist_ok=True)
    sounds_path = os.path.join(ASSETS, 'sounds.json')
    sounds = json.load(open(sounds_path, encoding='utf-8'))
    for k in [k for k in sounds if k.startswith('music.song.')]:
        del sounds[k]
    segs = []
    for i, (sid, b0, b1, level, energy, sung) in enumerate(SECTIONS):
        t0 = float(b[b0])
        t1 = float(b[min(b1, len(b) - 1)])
        if sid == 'ending':
            t1 = len(mix) / SR - 0.2
        # the intro keeps the guitar's pickup before its first downbeat (it overlaps the previous
        # pass when the intro repeats); everything else a short lead-in
        audio, pre = segment_audio(mix, t0, t1, i == 0, sid == 'ending', 0.3 if sid == 'intro' else PRE)
        with sf.SoundFile(os.path.join(out_dir, sid + '.ogg'), 'w', SR, 2, format='OGG', subtype='VORBIS') as f:
            for k in range(0, len(audio), SR):
                f.write(audio[k:k + SR])
        sounds['music.song.' + sid] = {'sounds': [{'name': 'papon:music/song/' + sid, 'stream': True}]}
        segs.append({'id': sid, 'start': round(t0, 4), 'length': round(t1 - t0, 4), 'pre': round(pre, 4),
                     'level': level, 'energy': energy, 'sung': sung, 'beats': b1 - b0})
        print('%-10s %7.2f s  %6.2f s  %s' % (sid, t0, t1 - t0, level))
    with open(sounds_path, 'w', newline='\n', encoding='utf-8') as f:
        json.dump(sounds, f, indent=2, ensure_ascii=False)
        f.write('\n')
    data = {
        'title': 'Canción del Mariachi',
        'segments': segs,
        'beats': [round(float(x), 3) for x in b if x < len(mix) / SR + 1],
        'mouth': [[round(t, 3), s] for t, s in cues],
        'rate': RATE,
        'voice': [round(float(v), 3) for v in envelope(vocals)],
        'pitch': [round(float(v), 2) for v in pitch(vocals)],
        'strum': [round(float(v), 3) for v in strums(other)],
    }
    path = os.path.join(ASSETS, 'song/cancion.json')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', newline='\n', encoding='utf-8') as f:
        json.dump(data, f, separators=(',', ':'), ensure_ascii=False)
    print(path, os.path.getsize(path) // 1024, 'KB')


if __name__ == '__main__':
    main()
