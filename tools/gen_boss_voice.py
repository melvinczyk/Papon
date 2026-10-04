#!/usr/bin/env python3
"""Chef Papon's phase-two voice: every line he says from the moment he kneels (the cutscene, El Coliseo
Papa and the rest of the fight) in English, in a cloned voice, with the data his mouth needs to say it.

  1. takes   (Modal GPU, profile melvinczyk) Qwen3-TTS-12Hz-1.7B-Base clones the reference recording
             (tools/src/voice/reference.mp3, gitignored: Spanish-accented English, the charm the user wants:
             Antonio Banderas). Each line is directed: its text marks the stresses (capitals) and the
             dramatic pauses (ellipses), and its mood picks the clip to clone from (REFS: shouts from his
             most worked-up sentence, the rest from his liveliest stretch) and an instruction for the
             delivery (DIRECTION). `TAKES` takes a line, different seeds; Whisper transcribes each back,
             and Qwen's speaker embedding scores it against the reference. Cached in
             tools/.cache/voice/takes: only new or changed lines go back to the GPU (tools/voice_modal.py).
  2. pick    never a take the model cut off mid-word; then one that says the line (Whisper, to a quarter
             of the words); then the most expressive: widest pitch range, loudness swing, the pauses.
  3. build   trimmed (a quarter second kept after the last sound, so no word's end is clipped),
             loudness-matched, mono Vorbis at sounds/voice/boss2/<line>.ogg, a sound event
             "voice.boss2.<line>", the bubble text "speech.papon.boss2.<line>", and for the lip sync
             assets/papon/voice/boss2.json:
               lines.<line>.len      seconds
               lines.<line>.mouth    Rhubarb Lip Sync cues [[t, 'A'..'H'|'X'], ...] (PocketSphinx with
                                     the line's text as the dialog, so the phonemes are the right ones)
               lines.<line>.env      loudness 0..1 at `rate` Hz (how wide the mouth opens)
               lines.<line>.pitch    semitones over his median (brows), 0 when unvoiced
               lines.<line>.accent   0..1 spikes on the stressed syllables (nods, brow pops)
               lines.<line>.mood     the expression he wears saying it (client/BossFace)
               beats                 the phase-two cutscene timed to the lines (beats()): PaponBossEntity,
                                     client/Cinematic and gen_boss_anims all read them, so nothing is
                                     talked over and the gestures land on the words. Run
                                     gen_boss_anims.py after this.

  python3.11 tools/gen_boss_voice.py            # generate what is missing, then build
  python3.11 tools/gen_boss_voice.py --redo apology,resume   # new takes for these lines
  python3.11 tools/gen_boss_voice.py --build    # rebuild from the cached takes only (no GPU)
Needs: pip install modal librosa soundfile numpy; ffmpeg; Rhubarb in tools/.cache/rhubarb (macOS build
of 1.13.0); a Modal token for the melvinczyk profile (`python3.11 -m modal token new --profile melvinczyk`).
"""
import argparse
import glob
import json
import os
import re
import subprocess
import sys
import tempfile

import numpy as np
import soundfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/papon')
CACHE = os.path.join(ROOT, 'tools/.cache/voice')
REFERENCE = os.path.join(ROOT, 'tools/src/voice/reference.mp3')
RHUBARB = sorted(glob.glob(os.path.join(ROOT, 'tools/.cache/rhubarb/Rhubarb-Lip-Sync-*/rhubarb*')))
TAKES = 8
RATE = 50                      # Hz of the env / pitch / accent tracks
MODAL_PROFILE = 'melvinczyk'

# The references: the same man worked up, or animated. The clone copies the delivery of the clip it is
# given, so shouts are cloned from his "He will never pay!" and everything else from the liveliest stretch
# of the recording; both with their words as Whisper heard them.
REFS = {
    'animated': ((36.3, 44.5), "Ser mono. Curious, because mono, to be a monkey. But to be a monkey in Spanish, ser mono is "
                               "just to be cute."),
    'excited': ((8.7, 12.1), "You're taking a coffee with somebody who is an encogido. He will never pay!"),
}

# A direction for each mood (the Base model takes an instruction prompt too: tested against none, it
# doubles his pitch range and keeps the voice as close to the reference)
STAR = 'like a passionate, theatrical Spanish movie star'
DIRECTION = {
    'shout': ('excited', 'SHOUT with explosive fury, %s: loud, intense, the words in capitals hit hardest.' % STAR),
    'angry': ('excited', 'Furious and indignant, %s: big emotion, the words in capitals hit hardest.' % STAR),
    'warm': ('animated', 'Speak %s: smooth, warm and charming, with dramatic pauses at the ellipses.' % STAR),
    'smug': ('animated', 'Speak %s: smug, self-satisfied and seductive, with dramatic pauses at the ellipses.' % STAR),
    'sly': ('animated', 'Speak %s: sly, playful and menacing, with dramatic pauses at the ellipses.' % STAR),
    'pained': ('animated', 'Speak %s, wounded and breathless, with dramatic pauses at the ellipses.' % STAR),
    'sad': ('animated', 'Speak %s, heartbroken and tragic, almost crying, with long dramatic pauses.' % STAR),
    'surprised': ('animated', 'Speak %s, in shocked disbelief, with dramatic pauses.' % STAR),
    'drunk': ('animated', 'Speak %s, drunk and slurring, wobbly and indignant.' % STAR),
    'cold': ('animated', 'Speak %s: cold, quiet and deadly serious, low and slow, with a long dramatic pause at the ellipsis.' % STAR),
    'contempt': ('animated', 'Speak %s: seething with quiet contempt, offended to the core, slow, with dramatic pauses at the ellipses.' % STAR),
}

# line -> (text, mood). The text is directed: capitals are stressed, an ellipsis is a pause (one at most, and
# only where a pause belongs: too many and he reads word by word); the bubble shows it as written. No scream:
# the roars under it do that job better than a voice can.
LINES = {
    # ---- the transformation (on the ground)
    'bleed':      ("Heh heh... You made me BLEED, amigo.", 'pained'),
    'enough':     ("Okay, OKAY. Enough playing around.", 'smug'),
    'salt':       ("A little bit of salt...", 'warm'),
    'golden':     ("Mmm, and nice and GOLDEN!", 'warm'),
    # ---- El Coliseo Papa
    'listos':     ("ARE YOU READY?!", 'shout'),
    'musica':     ("MUSIC, MAESTRO!!!", 'shout'),
    'maestro':    ("Maestro? ...MAESTRO?!", 'surprised'),
    'despierten': ("WAKE UP, you lazy bums! We have an AUDIENCE!", 'angry'),
    'momentito':  ("One moment, amigo. Just ONE moment.", 'warm'),
    'ay':         ("Oh, NO!!!", 'surprised'),
    'gordo':      ("YOU TOO, GORDO!!!", 'angry'),
    'apology':    ("My apologies, amigo. Such POOR hospitality... making my guest wait like that.", 'warm'),
    'resume':     ("Now, where were we?", 'sly'),
    # ---- the fight (his old lines, in English now)
    'salud':      ("CHEERS, amigo!!!", 'shout'),
    'intro':      ("NOBODY sees my bald head and LIVES to tell!!!", 'angry'),
    'taunt0':     ("I am going to make TACOS out of YOU!", 'sly'),
    'taunt1':     ("My bald head shines BRIGHTER than your FUTURE!", 'smug'),
    'taunt2':     ("Come HERE, coward!!!", 'angry'),
    'taunt3':     ("This is PURE SALSA!", 'sly'),
    'keys':       ("Where did I leave the keys? AH! Here!", 'surprised'),
    'handle':     ("One little sip for the road...", 'sly'),
    'ram':        ("MAKE WAY!!!", 'shout'),
    'again':      ("AGAIN!!!", 'shout'),
    'reverse':    ("And in REVERSE!!!", 'shout'),
    'stumble':    ("Who moved the FLOOR?", 'drunk'),
    'stolen':     ("HEY!!! MY VAN!!!", 'angry'),
    'phase':      ("NOW I am REALLY MAD!!!", 'angry'),
    'death':      ("My hair... my beautiful HAIR...", 'sad'),
    'kick':       ("HERE IT COMES!!!", 'shout'),
    'bail':       ("THIS is my STOP!!!", 'shout'),
    'door':       ("Mind the DOOR!", 'sly'),
    'meteor':     ("It's RAINING POTATOES!!!", 'shout'),
    'catch':      ("CATCH!!!", 'shout'),
    'foil':       ("My TINFOIL!!! You ANIMAL!", 'angry'),
    'grab':       ("Gotcha, amigo.", 'sly'),
    'torch':      ("Nice and golden...", 'warm'),
    'season':     ("And a PINCH of salt!", 'sly'),
    'toss':       ("ON THE GRILL!!!", 'shout'),
    'dodge':      ("HA! You didn't even TOUCH me!", 'smug'),
    'burrito':    ("One burrito, TO GO!", 'sly'),
    'borracho':   ("I'm FINE!!! I'm FINE!!!", 'drunk'),
    'haymaker':   ("I TOLD you I was FINE!!!", 'drunk'),
    'brick':      ("You drive, I HIT.", 'sly'),
    'qte0':       ("Your last supper, amigo.", 'sly'),
    'qte1':       ("LAST CALL!!! Open WIDE!", 'shout'),
    'qte2':       ("MASHED TOURIST!!!", 'shout'),
    'qte_broken': ("OW!!! My BACK!!!", 'pained'),
    'gravity':    ("EVERYTHING revolves around the POTATO!!!", 'shout'),
    'echoes':     ("The THREE CHEFS!!!", 'shout'),
    # ---- the Taco Truck: Papon the Taquero (him in disguise), said as speech.papon.boss2.vendor_*
    'vendor_order0':     ("Coming RIGHT up!", 'warm'),
    'vendor_order1':     ("It's coming out RIGHT now!", 'warm'),
    'vendor_order2':     ("But of COURSE, amigo!", 'warm'),
    'vendor_serve0':     ("ENJOY your meal!", 'warm'),
    'vendor_serve1':     ("Here you go, amigo!", 'warm'),
    'vendor_serve2':     ("Enjoy it, my friend!", 'warm'),
    'vendor_inspect':    ("A coupon? Let me see, let me SEE...", 'warm'),
    'vendor_suspicious': ("Hmm... this does NOT look right.", 'sly'),
    'vendor_fraud':      ("A FAKE coupon?! FRAUD!!! NOBODY steals MY tacos!", 'angry'),
    'vendor_exposed':    ("Heh. Heh heh. Heh.", 'sly'),
    'vendor_unmask':     ("You want the TRUTH? HERE is the truth!", 'angry'),
    'vendor_realize':    ("Wait. No... DON'T LOOK!", 'surprised'),
    'vendor_rage':       ("WHAT ARE YOU LOOKING AT?!", 'shout'),
    # ---- new: his charm in the coliseum (phase-two taunts and reactions)
    'charm0':     ("Come, amigo. DANCE with me.", 'sly'),
    'charm1':     ("Listen to them. They LOVE me.", 'smug'),
    'charm2':     ("Ay, you are making me sweat, and I am ALREADY baked.", 'warm'),
    'charm3':     ("Every great potato needs a WORTHY audience.", 'smug'),
    'charm4':     ("Do not worry. I will make this quick... and BEAUTIFUL.", 'sly'),
    'charm5':     ("OLÉ!!!", 'shout'),
    'hit0':       ("AH!!! Not the FACE!", 'pained'),
    'hit1':       ("You will PAY for that, in TACOS.", 'angry'),
    'landed0':    ("Gracias, gracias. You are TOO kind.", 'smug'),
    'landed1':    ("And the crowd goes WILD!!!", 'shout'),
    # phase three: half health, the band's song is not his style; he takes the guitar and shows them
    'notstyle':   ("This... is NOT... my style.", 'contempt'),
    'realplay':   ("Let me show you how to REALLY play.", 'sly'),
    'gimme':      ("Give me THAT.", 'angry'),
    'mystyle':    ("THIS... is more my style. Learn, muchachos.", 'smug'),
    'go':         ("What are you WAITING for?! GO!!!", 'shout'),
    'dance':      ("Now... let's DANCE.", 'cold'),
}

NO_CHECK = {'charm5', 'vendor_exposed'}          # Whisper cannot judge a scream or a lone word


def norm(s):
    s = s.lower().replace("'", '')
    return re.sub(r'[^a-z ]+', ' ', s).split()


def wer(ref, hyp):
    r, h = norm(ref), norm(hyp)
    d = list(range(len(h) + 1))
    for i in range(1, len(r) + 1):
        prev, d[0] = d[0], i
        for j in range(1, len(h) + 1):
            cur = min(d[j] + 1, d[j - 1] + 1, prev + (r[i - 1] != h[j - 1]))
            prev, d[j] = d[j], cur
    return d[len(h)] / max(1, len(r))


def take_path(line, k):
    return os.path.join(CACHE, 'takes', '%s_%s.wav' % (line, k))


def meta_path():
    return os.path.join(CACHE, 'takes.json')


def signature(line):
    """What a line's takes were made from: if any of it changes, the line is taken again."""
    text, mood = LINES[line]
    ref, direction = DIRECTION[mood]
    return {'text': text, 'ref': ref, 'instruct': direction}


def cut_reference(span):
    return subprocess.run(['ffmpeg', '-loglevel', 'error', '-ss', str(span[0]), '-to', str(span[1]), '-i', REFERENCE,
                           '-ac', '1', '-ar', '24000', '-f', 'wav', 'pipe:1'], capture_output=True, check=True).stdout


def generate(lines, more=False):
    """Takes for `lines` on the GPU; saved under tools/.cache/voice/takes with what Whisper heard. With
    `more`, TAKES more takes are added to the ones a line already has (new seeds)."""
    os.environ['MODAL_PROFILE'] = MODAL_PROFILE
    sys.path.insert(0, os.path.join(ROOT, 'tools'))
    import modal
    import voice_modal
    refs = {name: [cut_reference(span), text] for name, (span, text) in REFS.items()}
    meta = json.load(open(meta_path())) if os.path.exists(meta_path()) else {}
    have = {k: len(meta[k]['takes']) if more and k in meta else 0 for k in lines}
    batch = [dict(id=k, seed0=have[k], **signature(k)) for k in lines]
    print('cloning %d lines x %d takes on Modal (%s)...' % (len(batch), TAKES, MODAL_PROFILE))
    os.makedirs(os.path.join(CACHE, 'takes'), exist_ok=True)
    with modal.enable_output(), voice_modal.app.run():
        # a few containers at once
        n = min(6, len(batch))
        chunks = [batch[i::n] for i in range(n)]
        for result in voice_modal.clone.map([refs] * n, chunks, [TAKES] * n):
            for line, takes in result.items():
                if not have[line]:
                    for old in glob.glob(take_path(line, '*')):
                        os.remove(old)
                    meta[line] = dict(signature(line), takes=[])
                for k, t in enumerate(takes, start=have[line]):
                    with open(take_path(line, k), 'wb') as f:
                        f.write(t['wav'])
                    meta[line]['takes'].append({'heard': t['heard'], 'seconds': t['seconds'], 'sim': t.get('sim')})
                json.dump(meta, open(meta_path(), 'w'), indent=1)
    return meta


def load_mono(path):
    data, sr = soundfile.read(path, dtype='float32', always_2d=True)
    return data.mean(axis=1), sr


def clipped(y, sr):
    """Whether the model stopped mid-sound (the take ends on a word, not on silence)."""
    peak = np.abs(y).max() + 1e-9
    return np.sqrt(np.mean(y[-int(0.05 * sr):] ** 2)) / peak > 0.03


def expressiveness(y, sr):
    """How much the take moves: its pitch range (semitones between the 10th and 90th percentile, on a
    median-smoothed track with octave slips thrown out), loudness swing (dB), the pauses (0.18 s or more)
    inside it, and how bright the voice is (spectral centroid, Hz: a shout is brighter)."""
    import librosa
    import scipy.signal
    y = librosa.resample(y, orig_sr=sr, target_sr=16000)
    hop = 160
    f0, voiced, _ = librosa.pyin(y, fmin=60, fmax=500, sr=16000, frame_length=1024, hop_length=hop)
    rng, bright = 0.0, 0.0
    if np.count_nonzero(voiced) > 8:
        st = 12 * np.log2(np.where(voiced, f0, np.nan))
        med = np.nanmedian(st)
        st = np.where(np.abs(st - med) > 10, np.nan, st)
        good = st[~np.isnan(st)]
        if len(good) > 8:
            good = scipy.signal.medfilt(good, 5)
            rng = float(np.percentile(good, 90) - np.percentile(good, 10))
        cent = librosa.feature.spectral_centroid(y=y, sr=16000, n_fft=1024, hop_length=hop)[0][:len(voiced)]
        bright = float(np.mean(cent[voiced[:len(cent)]]))
    db = 20 * np.log10(librosa.feature.rms(y=y, frame_length=400, hop_length=hop)[0] + 1e-6)
    speech = db > np.percentile(db, 95) - 25
    dyn = float(np.percentile(db, 95) - np.percentile(db[speech], 20)) if speech.any() else 0.0
    idx = np.nonzero(speech)[0]
    pauses, run = 0, 0
    for x in (~speech[idx[0]:idx[-1]] if len(idx) else []):
        run = run + 1 if x else 0
        if run == int(0.18 * 16000 / hop):
            pauses += 1
    return {'range': rng, 'dyn': dyn, 'pauses': pauses, 'bright': bright}


def heard_energy(heard):
    """What Whisper's transcript gives away about the delivery: exclamation marks, words in capitals, a
    stretched vowel (a yell)."""
    caps = sum(1 for w in re.findall(r"[A-Za-z']+", heard) if len(w) > 1 and w.isupper())
    stretch = max((len(m.group(0)) for m in re.finditer(r'([a-zA-Z])\1{2,}', heard)), default=0)
    return heard.count('!'), caps, stretch


def pick(line, info):
    """The most expressive take that says the line (Whisper), sounds like him and is not cut off."""
    text, mood = LINES[line]
    loud = '!' in text
    cands = []
    for k, t in enumerate(info['takes']):
        path = take_path(line, k)
        if not os.path.exists(path):
            continue
        if 'expr2' not in t:
            y, sr = load_mono(path)
            t['expr2'] = expressiveness(y, sr)
            t['clipped'] = bool(clipped(y, sr))
        e = t['expr2']
        excl, caps, stretch = heard_energy(t['heard'])
        err = wer(text, t['heard'])
        score = e['range'] + 0.4 * e['dyn'] + 40 * (t.get('sim') or 0)
        # pauses: as many as the text has (its ellipses and full stops inside it), not more; and not word by word
        allowed = text.count('...') + len(re.findall(r'[.!?](?=\s+\S)', text.replace('...', '')))
        score -= 4.0 * max(0, e['pauses'] - allowed)
        words = max(1, len(norm(text)))
        score -= 12.0 * max(0.0, t['seconds'] / words - 0.55)
        if loud:
            score += 3.0 * min(excl, 3) + 2.0 * min(caps, 3)
        if mood in ('shout', 'angry'):
            score += e['bright'] / 250.0
        if line in NO_CHECK:
            # the scream / a lone word: whatever Whisper made of it, a yell (a stretched vowel), short of words
            err = max(0, len(norm(t['heard'])) - 2) / 4
            score += 4 * min(stretch, 6) + t['seconds']
        # a line written to be shouted: a take Whisper heard as flat (no "!") only if no take was exclaimed
        flat = loud and excl == 0 and line not in NO_CHECK
        cands.append((t['clipped'], round(min(err, 0.5) * 4) / 4, flat, -score, k))
    if not cands:
        return None, None
    # not cut off first, then said right (to a quarter of the words), then exclaimed if it should be, then the liveliest
    best = min(cands)
    return best[4], best


def trim(y, sr, floor_db=-55, lead=0.05, tail=0.25):
    """Silence off both ends, with room left after the last sound, so no word's end is clipped; t = 0 is
    (just before) the first sound."""
    win = int(sr * 0.01)
    rms = np.sqrt(np.convolve(y * y, np.ones(win) / win, mode='same') + 1e-12)
    loud = np.where(20 * np.log10(rms / (np.abs(y).max() + 1e-9) + 1e-12) > floor_db)[0]
    if len(loud) == 0:
        return y
    a = max(0, loud[0] - int(lead * sr))
    b = min(len(y), loud[-1] + int(tail * sr))
    out = y[a:b].copy()
    out[:int(0.005 * sr)] *= np.linspace(0, 1, int(0.005 * sr))
    fade = min(int(0.08 * sr), max(1, len(out) - (loud[-1] - a)))      # only over the quiet tail
    out[-fade:] *= np.linspace(1, 0, fade)
    return out


def loudness(y, sr, target=-9.5):
    """One gain for the whole line to an integrated loudness near `target` LUFS (ffmpeg ebur128), then a
    limiter, so he sits on top of the band and the crowd (they also duck under him: client/VoiceDuck)."""
    with tempfile.NamedTemporaryFile(suffix='.wav') as f:
        soundfile.write(f.name, y, sr, subtype='PCM_16')
        r = subprocess.run(['ffmpeg', '-hide_banner', '-nostats', '-i', f.name, '-af', 'ebur128', '-f', 'null', '-'],
                           capture_output=True, text=True).stderr
    m = re.findall(r'I:\s+(-?[\d.]+) LUFS', r)
    lufs = float(m[-1]) if m else -20.0
    with tempfile.TemporaryDirectory() as tmp:
        a, b = os.path.join(tmp, 'a.wav'), os.path.join(tmp, 'b.wav')
        soundfile.write(a, y, sr, subtype='FLOAT')
        subprocess.run(['ffmpeg', '-loglevel', 'error', '-y', '-i', a, '-af',
                        'volume=%.2fdB,alimiter=limit=0.94:attack=3:release=60:level=disabled' % (target - lufs), b], check=True)
        out, _ = soundfile.read(b, dtype='float32')
    return out[:len(y)] if len(out) >= len(y) else np.pad(out, (0, len(y) - len(out)))


def tracks(y, sr):
    """Loudness, pitch and accents at RATE Hz."""
    import librosa
    hop = sr // RATE
    rms = librosa.feature.rms(y=y, frame_length=hop * 2, hop_length=hop, center=True)[0]
    env = rms / (np.percentile(rms, 95) + 1e-9)
    env = np.clip(env, 0, 1) ** 0.8
    env = np.convolve(env, [0.25, 0.5, 0.25], mode='same')
    f0, voiced, _ = librosa.pyin(y, fmin=65, fmax=420, sr=sr, frame_length=hop * 8, hop_length=hop)
    n = len(env)
    f0, voiced = f0[:n], voiced[:n]
    semis = np.where(voiced, 12 * np.log2(np.nan_to_num(f0, nan=1.0) + 1e-9), np.nan)
    med = np.nanmedian(semis) if np.any(~np.isnan(semis)) else 0
    pitch = np.nan_to_num(semis - med, nan=0.0)
    pitch = np.clip(pitch, -8, 12)
    pitch = np.pad(pitch, (0, n - len(pitch)))
    # accents: where the loudness jumps (a syllable's onset), kept if it is a strong one
    rise = np.maximum(0, np.diff(env, prepend=0))
    rise = np.convolve(rise, [1, 1, 1], mode='same')
    acc = np.zeros(n)
    thr = max(0.12, np.percentile(rise, 90))
    last = -99
    for i in range(1, n - 1):
        if rise[i] >= thr and rise[i] >= rise[i - 1] and rise[i] >= rise[i + 1] and i - last > RATE * 0.22:
            acc[i] = min(1.0, rise[i] / (thr * 2))
            last = i
    # each accent decays over 0.25 s, so the client can read it as a pulse
    out = np.zeros(n)
    for i in np.nonzero(acc)[0]:
        for j in range(i, min(n, i + int(RATE * 0.25))):
            out[j] = max(out[j], acc[i] * (1 - (j - i) / (RATE * 0.25)))
    return env, pitch, out


def rhubarb(wav_path, text):
    if not RHUBARB:
        raise SystemExit('Rhubarb not found in tools/.cache/rhubarb (download Rhubarb-Lip-Sync-1.13.0-macOS.zip)')
    with tempfile.NamedTemporaryFile('w', suffix='.txt', delete=False) as d:
        d.write(re.sub(r'[^A-Za-z\' ,.!?]+', ' ', text))
    try:
        r = subprocess.run([RHUBARB[0], '-q', '-f', 'json', '-r', 'pocketSphinx', '--extendedShapes', 'GHX',
                            '--dialogFile', d.name, wav_path], capture_output=True, text=True, check=True)
    finally:
        os.unlink(d.name)
    cues = json.loads(r.stdout)['mouthCues']
    return [[round(c['start'], 3), c['value']] for c in cues]


SHOWTIME = 70            # BossState.P2_SHOWTIME: the guitar close-up before he turns to you
HYPE = 160               # BossState.P2_HYPE_LENGTH: after "where were we?" he works the stands up until the first sung word


def beats(lengths):
    """The phase-two cutscene's timing, in ticks from the start of each state, worked out from how long
    he takes to say things, so no line is cut off or talked over and every gesture lands on its words.
    PaponBossEntity, client/Cinematic, client/BossFace and gen_boss_anims all read these.
    `lengths`: line -> seconds."""
    import math
    L = lambda k: int(math.ceil(lengths.get(k, 2.0) * 20))
    b = {}
    # on one knee: the chuckle and "You made me... BLEED", then up on "Okay... OKAY. Enough..." and two neck cracks
    # (every line gets a beat of silence after it: he lets them land)
    b['talk.bleed'] = 6
    b['talk.enough'] = b['talk.bleed'] + L('bleed') + 24
    b['talk.rip'] = b['talk.enough'] + max(L('enough'), 24) + 6          # tears his apron off and flings it
    b['talk.end'] = b['talk.rip'] + 16 + 26
    # the salt, the bake (80), "Mmm... and nice... and GOLDEN!" with a chef's kiss on the last word
    b['season.salt'] = 4
    b['season.golden'] = 84
    b['season.kiss'] = b['season.golden'] + max(20, L('golden') - 16)
    b['season.end'] = max(100, b['season.golden'] + L('golden') + 16)
    # the scream (15): everyone is pulled up into the coliseum only once it is out of him
    b['scream.end'] = 70
    # the coliseum: down he comes (36), "ARE... YOU... READY?!", then the point at the band stand: "MUSIC, MAESTRO!!!"
    b['arrive.listos'] = 42
    b['arrive.musica'] = max(74, b['arrive.listos'] + L('listos') + 22)
    b['arrive.hush'] = b['arrive.musica'] + L('musica') + 6
    b['arrive.end'] = max(100, b['arrive.hush'] + 14)
    # nothing. "Maestro...? ...MAESTRO?!", and he fumes
    b['notice.maestro'] = 20
    b['notice.fume'] = b['notice.maestro'] + L('maestro') + 8
    b['notice.end'] = b['notice.fume'] + 14
    # three clangs on the comal, then the bellow
    b['wake.clang1'], b['wake.clang2'], b['wake.clang3'] = 17, 27, 37
    b['wake.line'] = 42
    b['wake.end'] = b['wake.line'] + L('despierten') + 16
    # foot tapping: "One moment, amigo..."; only after it does he spot the guitarron asleep again ("Oh... NO!!!")
    b['wait.momentito'] = 22
    b['wait.ay'] = b['wait.momentito'] + L('momentito') + 24
    # "YOU TOO, GORDO!!!", the boot on the last word
    b['kick.gordo'] = 2
    b['kick.boot'] = max(12, b['kick.gordo'] + L('gordo') - 4)
    b['kick.end'] = b['kick.boot'] + 18
    # the apology as he comes over, the aura farm, "Now... where... were we?", and the hype (HYPE ticks)
    # ending on the first sung word
    b['apology.line'] = 12
    b['apology.aura'] = b['apology.line'] + L('apology') + 14
    b['resume.line'] = 2
    b['resume.total'] = b['resume.line'] + L('resume') + 8
    song = json.load(open(os.path.join(ASSETS, 'song/cancion.json')))
    intro = next(s['length'] for s in song['segments'] if s['id'] == 'intro')
    need = SHOWTIME + b['apology.aura'] + 60 + b['resume.total'] + HYPE        # at least 3 s of aura farm
    b['song.passes'] = max(2, math.ceil(need / (intro * 20)))
    fight = int(round(intro * b['song.passes'] * 20))
    b['apology.end'] = fight - SHOWTIME - b['resume.total'] - HYPE
    b['apology.wink'] = b['apology.aura'] + min(40, (b['apology.end'] - b['apology.aura']) // 2)
    # phase three (half health). Up off the sand, the lip wiped, his eyes slide over to the band; a maraca drops in
    # the silence. "This... is NOT... my style." A beat. "Let me show you how to REALLY play."
    b['glare.maraca'] = 26
    b['glare.notstyle'] = 40
    b['glare.realplay'] = b['glare.notstyle'] + L('notstyle') + 18
    b['glare.end'] = b['glare.realplay'] + L('realplay') + 12
    # looming over the singer: "Give me THAT." and the guitar ripped out of his hands on the last word
    b['snatch.gimme'] = 6
    b['snatch.grab'] = max(18, b['snatch.gimme'] + L('gimme') - 6)
    b['snatch.end'] = b['snatch.grab'] + 34
    # in the spotlight: the serenade is the song's first 18 s (guitar and voice), the last chord left ringing
    ps = json.load(open(os.path.join(ASSETS, 'song/para_siempre.json')))
    seren = next(s['length'] for s in ps['segments'] if s['id'] == 'serenade')
    pickup = next(s['length'] for s in ps['segments'] if s['id'] == 'pickup')
    b['serenade.start'] = 4
    b['serenade.end'] = b['serenade.start'] + int(math.ceil(seren * 20)) + 12
    # the guitar down from his chin, the chord dying: "THIS... is more my style. Learn, muchachos."
    b['verdict.line'] = 10
    b['verdict.end'] = b['verdict.line'] + L('mystyle') + 10
    # over his shoulder without looking (6), the singer juggles it and has it (26); then nothing: the band look at
    # each other, scared, nobody moves (toss.glance on), and only then does he whirl round on them: "...GO!!!"
    b['toss.throw'] = 6
    b['toss.catch'] = 26
    b['toss.glance'] = 40
    b['toss.go'] = 104
    b['toss.end'] = b['toss.go'] + L('go') + 2
    # the singer's pickup, a cappella, then the band crashes in; once the fire is back: "Now... let's DANCE."
    b['dance.drop'] = int(round(pickup * 20))
    b['dance.line'] = b['dance.drop'] + 34
    b['dance.end'] = b['dance.line'] + L('dance') + 24
    return b


def build(meta):
    out_dir = os.path.join(ASSETS, 'sounds/voice/boss2')
    os.makedirs(out_dir, exist_ok=True)
    data = {'rate': RATE, 'lines': {}}
    lang_path = os.path.join(ASSETS, 'lang/en_us.json')
    lang = json.load(open(lang_path))
    sounds_path = os.path.join(ASSETS, 'sounds.json')
    sounds = json.load(open(sounds_path))
    for k in [k for k in sounds if k.startswith('voice.boss2.')]:
        del sounds[k]
    for k in [k for k in lang if k.startswith('speech.papon.boss2.')]:
        del lang[k]
    report = []
    for line, (text, mood) in LINES.items():
        info = meta.get(line)
        if not info or any(info.get(k) != v for k, v in signature(line).items()):
            print('  (no takes for %s yet)' % line)
            continue
        k, key = pick(line, info)
        if k is None:
            continue
        y, sr = load_mono(take_path(line, k))
        y = loudness(trim(y, sr), sr)
        with tempfile.TemporaryDirectory() as tmp:
            wav = os.path.join(tmp, 'l.wav')
            soundfile.write(wav, y, sr, subtype='PCM_16')
            mouth = rhubarb(wav, text)
        env, pitch, acc = tracks(y, sr)
        soundfile.write(os.path.join(out_dir, line + '.ogg'), y, sr, format='OGG', subtype='VORBIS')
        sounds['voice.boss2.' + line] = {'sounds': [{'name': 'papon:voice/boss2/' + line}]}
        lang['speech.papon.boss2.' + line] = text
        data['lines'][line] = {'len': round(len(y) / sr, 3), 'mood': mood, 'mouth': mouth,
                               'env': [round(float(v), 3) for v in env], 'pitch': [round(float(v), 2) for v in pitch],
                               'accent': [round(float(v), 3) for v in acc]}
        e = info['takes'][k]['expr2']
        report.append('%-10s %5.2fs take %d  range %4.1f dyn %4.1f pauses %d bright %4d%s | %s' % (
            line, len(y) / sr, k, e['range'], e['dyn'], e['pauses'], e['bright'], '  CLIPPED' if key[0] else '', info['takes'][k]['heard']))
    json.dump(meta, open(meta_path(), 'w'), indent=1)          # with the expressiveness, for next time
    data['beats'] = beats({k: v['len'] for k, v in data['lines'].items()})
    report.append('beats: ' + json.dumps(data['beats']))
    os.makedirs(os.path.join(ASSETS, 'voice'), exist_ok=True)
    with open(os.path.join(ASSETS, 'voice/boss2.json'), 'w', newline='\n') as f:
        json.dump(data, f, separators=(',', ':'))
    with open(sounds_path, 'w', newline='\n') as f:
        json.dump(sounds, f, indent=2, ensure_ascii=False)
        f.write('\n')
    with open(lang_path, 'w', newline='\n') as f:
        json.dump(lang, f, indent=2, ensure_ascii=False)
        f.write('\n')
    print('\n'.join(report))
    print(len(data['lines']), 'lines built')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--redo', default='', help='comma-separated lines to take again')
    ap.add_argument('--build', action='store_true', help='only build from cached takes')
    ap.add_argument('--more', default='', help='comma-separated lines to give TAKES more takes (keeping theirs)')
    a = ap.parse_args()
    meta = json.load(open(meta_path())) if os.path.exists(meta_path()) else {}
    if not a.build:
        redo = {s for s in a.redo.split(',') if s}
        todo = [l for l in LINES
                if l in redo or l not in meta or any(meta[l].get(k) != v for k, v in signature(l).items())
                or not all(os.path.exists(take_path(l, k)) for k in range(len(meta[l]['takes'])))]
        if todo:
            meta = generate(todo)
        if a.more:
            meta = generate([s for s in a.more.split(',') if s], more=True)
    build(meta)


def rebeat():
    """Only the cutscene's beats again, from the lines already in voice/boss2.json (no recording)."""
    path = os.path.join(ASSETS, 'voice/boss2.json')
    data = json.load(open(path))
    data['beats'] = beats({k: v['len'] for k, v in data['lines'].items()})
    with open(path, 'w', newline='\n') as f:
        json.dump(data, f, separators=(',', ':'))
    print('beats:', json.dumps(data['beats']))


if __name__ == '__main__':
    if '--beats' in sys.argv:
        rebeat()
    else:
        main()
