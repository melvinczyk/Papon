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
  5. the rest of the band, from the same stems (client/MariachiModel plays them):
       harmony   0..1, the backing vocals: where a second voice sings with him (a second pitch in the
                 vocal stem that is not one of his harmonics, and the stem going wide in stereo, as
                 the doubled harmonies are); the coro sings there, on his mouth shapes
       horn      0..1, where the trumpets play: the instrumental breaks and the gaps between his
                 phrases (fills), never under the guitar solo or the breakdown
       notes     the note starts of the melody line in the guitar stem (the trumpets tongue them and
                 press the valves for them, the violins change bow on them)
       lead      the melody line's note (MIDI, 0 = none): the trumpet fingering, the violinists' and
                 the soloist's left hands up and down the neck
       bow       the violins' bowing worked out stroke by stroke: [time, bow position 0 (frog)..1 (tip)];
                 they bow his melody while he sings and the guitar line around it, and rest their
                 fiddles under the solo and the breakdown (fiddle 0..1)

Writes:
  src/main/resources/assets/papon/sounds/music/song/<segment>.ogg   each segment, with a short lead-in
                                                                     and tail so joins crossfade
  src/main/resources/assets/papon/song/cancion.json                 segments, beats, lip sync, envelopes
  sounds.json entries "music.song.<segment>" (streamed)

  pip install demucs librosa soundfile numpy scipy
  python tools/gen_song.py            (the .ogg segments are only written when missing; --audio redoes them)

Phase three's song, "Para Siempre" (--song para_siempre; tools/src/song/para_siempre.mp3, the user's
copy): split with htdemucs_6s (it has a guitar stem: the first 18 s are a guitar and a voice only, and
the boss plays and sings exactly that himself, the `serenade` segment); the trumpets and violins are the
6-stem model's `other`, so where they play comes from that stem's loudness instead of a hand-made map.
Writes song/para_siempre.json and sounds/music/para_siempre/<segment>.ogg ("music.para_siempre.<id>").
"""
import json
import os
import subprocess

import numpy as np
import soundfile as sf

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/papon')
SONG = __import__('sys').argv[__import__('sys').argv.index('--song') + 1] if '--song' in __import__('sys').argv else 'cancion'
SRC = os.path.join(ROOT, 'tools/src/song/' + {'cancion': 'cancion_del_mariachi.mp3'}.get(SONG, SONG + '.mp3'))
CACHE = os.path.join(ROOT, 'tools/.cache/' + {'cancion': 'song', 'para_siempre': 'song_p3', 'persona_ideal': 'song_p4'}[SONG])
SIX = SONG in ('para_siempre', 'persona_ideal')   # split with htdemucs_6s: a guitar stem of its own, `other` = the brass and strings
RHUBARB = (sorted(__import__('glob').glob(os.path.join(ROOT, 'tools/.cache/rhubarb/Rhubarb-Lip-Sync-*/rhubarb')))
           or [os.path.join(ROOT, 'tools/.cache/rhubarb/Rhubarb-Lip-Sync-1.13.0-Windows/rhubarb.exe')])[0]
SR = 44100
RATE = 25            # envelope samples per second
PRE = 0.08           # lead-in kept before a segment's first beat (pickups, the attack)
TAIL = 0.10          # kept after its end, faded: the overlap into the next segment
AUDIO = '--audio' in __import__('sys').argv    # re-encode the segments even if they are there

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

# "Para Siempre" (phase three). A 6/8 ballad, three tracked beats to the bar. Two segments are cut by
# time instead of by beat: the boss's own serenade (the guitar intro and the first lines, guitar and
# voice alone, up to the end of the guitar fill) and the pickup the singer sings a cappella before the
# band comes in on beat 34.
SECTIONS_P3 = [
    ('serenade',   0.0, 18.36, 'cutscene', 0.40, True),    # his: the spotlight, the lighters
    ('pickup',     18.36, 34, 'intro', 0.50, True),         # the singer, holding the guitar he just caught
    ('verse1',     34, 88, 'mid', 0.70, True),
    ('chorus1',    88, 145, 'high', 0.88, True),
    ('break',      145, 175, 'low', 0.50, False),           # guitar and guitarron alone
    ('verse2',     175, 214, 'mid', 0.74, True),
    ('climax',     214, 265, 'high', 1.00, True),
    ('ending',     265, 290, 'end', 0.70, True),            # voice and guitar, rubato, to the last chord
]
if SONG == 'para_siempre':
    SECTIONS = SECTIONS_P3

# "Persona Ideal (Me Tengo Que Ir)" (phase four, --song persona_ideal; tools/src/song/persona_ideal.mp3, the user's
# copy): his last song, the one he asks the band for. ~128 bpm in 4/4; a brass fanfare opens it (the cutscene: the
# muchachas, the twirl, "SHOW ME A GOOD TIME"), the fight starts on the first sung line. Two brass solos.
SECTIONS_P4 = [
    ('intro',      0, 24, 'intro', 0.62, False),        # the brass fanfare
    ('verse1',     24, 100, 'mid', 0.72, True),
    ('chorus1',    100, 176, 'high', 0.88, True),
    ('interlude',  176, 188, 'low', 0.50, False),       # guitar and bass
    ('verse2',     188, 276, 'mid', 0.75, True),
    ('chorus2',    276, 328, 'high', 0.92, True),
    ('trompetas',  328, 388, 'high', 0.95, False),      # the brass take it
    ('verse3',     388, 432, 'mid', 0.80, True),
    ('trompetas2', 432, 472, 'high', 0.96, False),
    ('breakdown',  472, 480, 'low', 0.45, True),
    ('climax',     480, 556, 'high', 1.00, True),
    ('ending',     556, 600, 'end', 0.80, True),        # the brass play it out
]
if SONG == 'persona_ideal':
    SECTIONS = SECTIONS_P4


def run(cmd):
    print('$', ' '.join(cmd))
    subprocess.run(cmd, check=True)


def stems():
    want = [os.path.join(CACHE, n + '.wav') for n in ('vocals', 'other', 'drums', 'bass')
            + (('guitar',) if SIX else ())]
    if all(os.path.exists(p) for p in want):
        return
    import torch
    from demucs.apply import apply_model
    from demucs.pretrained import get_model
    x, sr = sf.read(SRC, always_2d=True)
    model = get_model('htdemucs_6s' if SIX else 'htdemucs')
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
        _, b = librosa.beat.beat_track(onset_envelope=env, sr=22050, units='time', start_bpm=103 if SONG == 'para_siempre' else 128, tightness=200)
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


def smooth(x, n):
    return np.convolve(x, np.ones(n) / n, 'same') if n > 1 else x


def frames(n_sec):
    return int(round(n_sec * RATE))


def harmony(vocal_lr, voice):
    """0..1 per envelope frame: a second voice with his.

    Two cues, both from the vocal stem: polyphony (the strongest pitch's harmonic sum, his own
    harmonics and octaves taken out, against the next strongest left; a harmony a third or a sixth
    off shows up there) and width (the backing vocals are doubled and spread across the stereo
    field, he is in the middle). Only where he sings; smoothed over a beat, held for at least one
    and a half."""
    import librosa
    y = librosa.resample(vocal_lr.mean(1).astype(np.float32), orig_sr=SR, target_sr=16000)
    hop = 16000 // RATE
    cq = np.abs(librosa.cqt(y, sr=16000, hop_length=hop, fmin=librosa.note_to_hz('C2'), n_bins=196, bins_per_octave=36))
    nb = 108
    sal = np.zeros((nb, cq.shape[1]))
    for h, w in zip([1, 2, 3, 4, 5], [1, 0.8, 0.6, 0.45, 0.35]):
        off = int(round(36 * np.log2(h)))
        sal += w * cq[off:off + nb]
    poly = np.zeros(cq.shape[1])
    for t in range(cq.shape[1]):
        s = sal[:, t]
        if s.max() < 1e-3:
            continue
        i = s.argmax()
        s2 = s.copy()
        for rel in (0, 12, -12, 19, -19, 24, -24, 28, 31, 36):
            j = i + rel * 3
            s2[max(0, j - 4):min(nb, j + 5)] = 0
        poly[t] = s2.max() / s.max()
    hopn = SR // RATE
    n = len(vocal_lr) // hopn
    def env(x):
        return np.sqrt((x[:n * hopn].reshape(n, hopn) ** 2).mean(1))
    mid = smooth(env(vocal_lr.mean(1)), 12)
    side = smooth(env((vocal_lr[:, 0] - vocal_lr[:, 1]) / 2), 12)
    width = side / (mid + 1e-4)
    m = min(len(poly), len(width), len(voice))
    score = 0.55 * np.clip((smooth(poly[:m], 12) - 0.22) / 0.25, 0, 1) + 0.45 * np.clip((width[:m] - 0.17) / 0.09, 0, 1)
    sung = smooth(voice[:m], 25) > 0.08
    on = (smooth(score, 12) > 0.5) & sung
    # at least a beat and a half on, and gaps under a beat closed
    beat = frames(0.47)
    on = close_gaps(on, beat)
    on = drop_short(on, int(beat * 1.5))
    # eased in and out (they draw breath and close their mouths): 0.15 s ramps
    return np.clip(smooth(on.astype(float), frames(0.15)), 0, 1)


def runs(mask):
    """(start, end) frame index pairs of the True runs."""
    d = np.diff(np.concatenate([[0], mask.astype(int), [0]]))
    return list(zip(np.where(d == 1)[0], np.where(d == -1)[0]))


def close_gaps(mask, n):
    mask = mask.copy()
    r = runs(mask)
    for (a0, a1), (b0, b1) in zip(r, r[1:]):
        if b0 - a1 < n:
            mask[a1:b0] = True
    return mask


def drop_short(mask, n):
    mask = mask.copy()
    for a, b in runs(mask):
        if b - a < n:
            mask[a:b] = False
    return mask


def at(beats, x):
    """A section bound in seconds: a beat index, or (a float) a time already."""
    return float(x) if isinstance(x, float) else float(beats[min(x, len(beats) - 1)])


def section_mask(n, beats, ids):
    """Frames inside the named sections."""
    m = np.zeros(n, bool)
    for sid, b0, b1, *_ in SECTIONS:
        if sid in ids:
            m[frames(at(beats, b0)):frames(at(beats, b1))] = True
    return m


def stem_mask(sig, n, floor=0.18):
    """Where a stem plays (Para Siempre's trumpets and violins: the 6-stem `other`): its loudness over
    `floor` of its loud parts, smoothed over a beat, gaps under 0.6 s closed, nothing under a second."""
    e = smooth(envelope(sig), frames(0.5))[:n]
    e = np.pad(e, (0, max(0, n - len(e))))
    on = e > floor
    return drop_short(close_gaps(on, frames(0.6)), frames(1.0))


def lead_line(other):
    """The melody line in the guitar stem: pYIN over the band the tune lives in, as MIDI notes per
    envelope frame (0 = nothing clear), median-filtered so a note holds."""
    import librosa
    import scipy.signal as ss
    y = librosa.resample(other.astype(np.float32), orig_sr=SR, target_sr=16000)
    y = ss.sosfilt(ss.butter(4, [350, 2500], btype='band', fs=16000, output='sos'), y)
    f0, voiced, _ = librosa.pyin(y, fmin=200, fmax=1400, sr=16000, frame_length=1024, hop_length=16000 // RATE)
    midi = np.where(voiced, np.round(12 * np.log2(np.nan_to_num(f0, nan=440.0) / 440) + 69), 0)
    midi = ss.medfilt(midi, 5)
    return midi.astype(int), y


def note_starts(y16, midi, beats):
    """Where notes of the melody line start: attacks in the band-passed guitar stem and changes of
    note in the line, snapped to the eighth-note grid when close to it, at least 0.12 s apart."""
    import librosa
    env = librosa.onset.onset_strength(y=y16, sr=16000, hop_length=160)
    on = librosa.onset.onset_detect(onset_envelope=env, sr=16000, hop_length=160, units='time', delta=0.06, wait=10)
    times = list(on)
    for i in range(1, len(midi)):
        if midi[i] and midi[i] != midi[i - 1] and (i < 2 or midi[i] == midi[min(len(midi) - 1, i + 1)]):
            times.append(i / RATE)
    grid = np.sort(np.concatenate([beats, (beats[:-1] + beats[1:]) / 2]))
    snapped = []
    for t in sorted(times):
        g = grid[np.abs(grid - t).argmin()]
        snapped.append(g if abs(g - t) < 0.06 else t)
    out = []
    for t in sorted(snapped):
        if not out or t - out[-1] >= 0.12:
            out.append(t)
    return np.array(out)


def horn_mask(voice, beats, n):
    """Where the trumpets play. A mariachi's trumpets answer the singer and carry the big parts: here
    they take the instrumental break, the chorus half of each verse (where the coro comes in too),
    the second half of the solo (the drums come in), the climax all through and the ending. They
    rest under the intro guitar, the verses' first halves, the first solo and the breakdown, and
    come in at least half a second at a time."""
    m = section_mask(n, beats, ('break1', 'climax1', 'climax2', 'ending'))
    for b0, b1 in ((40, 64), (112, 128), (160, 176)):
        m[frames(beats[b0]):frames(beats[b1])] = True
    m[frames(beats[-1]):] = False
    return drop_short(close_gaps(m, frames(0.35)), frames(0.6))


def bowing(notes, voice_lr, beats, n, play=None):
    """The violins, stroke by stroke. They play everywhere but under the intro, the first solo and
    the breakdown; while he sings they change bow on his syllables (onsets of the vocal stem), around
    him on the melody line's notes; a stroke lasts at most two beats (a long note gets a fresh bow).
    Each stroke turns the bow round: down-bow towards the tip, up-bow back towards the frog, using
    more bow the longer and louder the note. Returns [(time, position)] keys and the fiddle mask."""
    import librosa
    if play is None:
        play = ~section_mask(n, beats, ('intro', 'solo1', 'breakdown'))
    play = play.copy()
    play[frames(beats[-1]):] = False
    y = librosa.resample(voice_lr.mean(1).astype(np.float32), orig_sr=SR, target_sr=16000)
    env = librosa.onset.onset_strength(y=y, sr=16000, hop_length=160)
    syll = librosa.onset.onset_detect(onset_envelope=env, sr=16000, hop_length=160, units='time', delta=0.08, wait=18)
    sung_sections = section_mask(n, beats, tuple(sec[0] for sec in SECTIONS if sec[5] and sec[3] != 'intro'))
    times = [t for t in syll if frames(t) < n and sung_sections[frames(t)]]
    times += [t for t in notes if frames(t) < n and not sung_sections[frames(t)]]
    times = sorted(t for t in times if frames(t) < n and play[frames(t)])
    # long notes: a fresh bow every two beats
    step = 2 * float(np.median(np.diff(beats)))
    filled = []
    for a, b in zip(times, times[1:] + [times[-1] + step]):
        filled.append(a)
        t = a + step
        while t < b - 0.25 and frames(t) < n and play[frames(t)]:
            filled.append(t)
            t += step
    strokes = []
    for t in filled:
        if not strokes or t - strokes[-1] >= 0.16:
            strokes.append(t)
    keys = []
    pos, down = 0.15, True
    for a, b in zip(strokes, strokes[1:] + [strokes[-1] + 0.5]):
        dur = min(b - a, step)
        use = float(np.clip(0.18 + dur * 0.55, 0.18, 0.8))
        keys.append((round(a, 3), round(pos, 3)))
        nxt = pos + use if down else pos - use
        if nxt > 0.95 or nxt < 0.05:
            # out of bow: turn round early
            down = not down
            nxt = pos + use if down else pos - use
            nxt = float(np.clip(nxt, 0.05, 0.95))
        pos = nxt
        down = not down
    if strokes:
        keys.append((round(strokes[-1] + 0.5, 3), round(pos, 3)))
    fiddle = close_gaps(play, frames(0.5)).astype(float)
    return keys, np.clip(smooth(fiddle, frames(0.3)), 0, 1)


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
    vocals_lr = sf.read(os.path.join(CACHE, 'vocals.wav'), always_2d=True)[0]
    vocals = vocals_lr.mean(1)
    other = sf.read(os.path.join(CACHE, 'other.wav'), always_2d=True)[0].mean(1)
    # Para Siempre: the guitar has its own stem (the strums); `other` is the trumpets and violins (the melody line)
    guitar = sf.read(os.path.join(CACHE, 'guitar.wav'), always_2d=True)[0].mean(1) if SIX else other
    cues = lipsync()
    b = beats(mix)
    folder = 'song' if SONG == 'cancion' else SONG
    out_dir = os.path.join(ASSETS, 'sounds/music/' + folder)
    os.makedirs(out_dir, exist_ok=True)
    sounds_path = os.path.join(ASSETS, 'sounds.json')
    sounds = json.load(open(sounds_path, encoding='utf-8'))
    for k in [k for k in sounds if k.startswith('music.%s.' % folder)]:
        del sounds[k]
    segs = []
    for i, (sid, b0, b1, level, energy, sung) in enumerate(SECTIONS):
        t0 = at(b, b0)
        t1 = at(b, b1)
        if sid == 'ending':
            t1 = len(mix) / SR - 0.2
        # the intro keeps the guitar's pickup before its first downbeat (it overlaps the previous
        # pass when the intro repeats); everything else a short lead-in. His serenade ends on the guitar
        # fill ringing out (a long fade: he holds the last chord while he talks), the pickup starts dry.
        lead = 0.3 if sid == 'intro' else 0.0 if isinstance(b0, float) else PRE
        audio, pre = segment_audio(mix, t0, t1, i == 0 or isinstance(b0, float), sid in ('ending', 'serenade'), lead)
        ogg = os.path.join(out_dir, sid + '.ogg')
        if AUDIO or not os.path.exists(ogg):
            with sf.SoundFile(ogg, 'w', SR, 2, format='OGG', subtype='VORBIS') as f:
                for k in range(0, len(audio), SR):
                    f.write(audio[k:k + SR])
        sounds['music.%s.%s' % (folder, sid)] = {'sounds': [{'name': 'papon:music/%s/%s' % (folder, sid), 'stream': True}]}
        segs.append({'id': sid, 'start': round(t0, 4), 'length': round(t1 - t0, 4), 'pre': round(pre, 4),
                     'level': level, 'energy': energy, 'sung': sung,
                     'beats': int(round(np.searchsorted(b, t1 - 0.05) - np.searchsorted(b, t0 - 0.05)))})
        print('%-10s %7.2f s  %6.2f s  %s' % (sid, t0, t1 - t0, level))
    with open(sounds_path, 'w', newline='\n', encoding='utf-8') as f:
        json.dump(sounds, f, indent=2, ensure_ascii=False)
        f.write('\n')
    voice = envelope(vocals)
    n = len(voice)
    midi, y16 = lead_line(other)
    midi = np.pad(midi, (0, max(0, n - len(midi))))[:n]
    notes = note_starts(y16, midi, b)
    if SIX:
        band = stem_mask(other, n)
        horn = band.copy()
        bow_keys, fiddle = bowing(notes, vocals_lr, b, n, band)
    else:
        horn = horn_mask(voice, b, n)
        bow_keys, fiddle = bowing(notes, vocals_lr, b, n)
    tail = frames(len(mix) / SR - 1.5)
    horn[tail:] = False
    harm = harmony(vocals_lr, voice)
    harm = np.pad(harm, (0, max(0, n - len(harm))))[:n]
    print('notes %d, horn %.0f%%, harmony %.0f%%, bow strokes %d' % (len(notes), horn.mean() * 100, (harm > 0.5).mean() * 100, len(bow_keys)))
    data = {
        'title': {'cancion': 'Canción del Mariachi', 'para_siempre': 'Para Siempre', 'persona_ideal': 'Persona Ideal'}[SONG],
        'sounds': 'music.%s.' % folder,
        'segments': segs,
        'beats': [round(float(x), 3) for x in b if x < len(mix) / SR + 1],
        'mouth': [[round(t, 3), s] for t, s in cues],
        'rate': RATE,
        'voice': [round(float(v), 3) for v in voice],
        'pitch': [round(float(v), 2) for v in pitch(vocals)],
        'strum': [round(float(v), 3) for v in strums(guitar)],
        'harmony': [round(float(v), 2) for v in harm],
        'horn': [int(v) for v in horn],
        'notes': [round(float(t), 3) for t in notes],
        'lead': [int(v) for v in midi],
        'bow': [[t, p] for t, p in bow_keys],
        'fiddle': [round(float(v), 2) for v in fiddle],
    }
    path = os.path.join(ASSETS, 'song/%s.json' % SONG)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', newline='\n', encoding='utf-8') as f:
        json.dump(data, f, separators=(',', ':'), ensure_ascii=False)
    print(path, os.path.getsize(path) // 1024, 'KB')


if __name__ == '__main__':
    main()
