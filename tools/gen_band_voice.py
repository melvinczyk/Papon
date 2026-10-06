"""The mariachi band's lines in a cloned voice (2026-10-05, the user: "right before phase 3 the mariachi band has villager
sounds. They should not"): every speech.papon.band.* line but the count (gen_singer_voice.py) cloned from the singer's
reference (tools/src/voice/singer_reference.mp3, gitignored), the members told apart by pitch in game (MariachiEntity).

  python tools/gen_band_voice.py --takes [a,b]   clone TAKES takes of every line (or only those) on Modal
  python tools/gen_band_voice.py                 pick a take per line, write sounds/voice/band/<line>.ogg, voice/band.json
                                                 (the mouth: loudness at 25 Hz) and the sounds.json entries

The reference's transcript is gen_singer_voice's (--ref there). Needs what gen_singer_voice needs.
"""
import json
import os
import re
import sys

import numpy as np
import soundfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, 'tools'))
import gen_singer_voice as sv  # noqa: E402

ASSETS = sv.ASSETS
CACHE = os.path.join(ROOT, 'tools/.cache/band_voice')
LANG = os.path.join(ASSETS, 'lang/en_us.json')
SR = sv.SR
TAKES = 6
RATE = 25
GAIN_DB = -2.5                    # under the boss (his lines are mastered to the same level)

ME = ('You are a member of a Mexican mariachi band, in your charro suit on the stage of a huge coliseum: a real person '
      'reacting in the moment, never a narrator. ')
# line -> (what it sounds like, the delivery)
DIRECTION = {
    'sleepy': ('groggy', 'Mumble it half asleep, eyes still shut, slurred and slow, a lazy drawn-out sigh at the end.'),
    'yawn': ('groggy', 'A big real yawn first, then mumble the question sleepily, slowly.'),
    'kicked': ('yelp', 'Yelp as your chair is kicked out from under you, then hurry, grumbling, flustered.'),
    'ouch': ('yelp', 'You were just whacked on the head: a loud cry of pain, then outraged, indignant.'),
    'ya_voy': ('grumble', 'Grumble it under your breath, annoyed, rubbing your sore head, rolling your eyes.'),
    'sigh': ('grumble', 'A long weary sigh first, then complain tiredly, resigned, under your breath.'),
    'lamp_on': ('cheer', 'Cheerful and proud, calling out as you switch the stage lights on.'),
    'lamp_off': ('cheer', 'Warm and a little sleepy, greeting the sunrise as you switch the lights off.'),
    'hey': ('yelp', 'Outraged shout: someone just ripped your guitar out of your hands.'),
    'ay_dios': ('scared', 'Frightened, breathless, cowering.'),
    'got_it': ('scared', 'Frantic, juggling a guitar thrown at you, then relieved as you catch it.'),
    'p3_what': ('scared', 'Panicked, a loud frightened whisper to the man next to you.'),
    'p3_play': ('scared', 'Urgent, scared hissing whisper to your bandmates.'),
    'p3_loco': ('scared', 'A terrified mutter, eyes wide.'),
    'p3_wings': ('scared', 'Shrieking disbelief: the man in front of you just sprouted wings.'),
    'p3_mama': ('scream', 'A high terrified scream for your mother.'),
    'p3_sorry': ('scared', 'Pleading desperately, cringing.'),
    'p3_dios': ('scared', 'Awestruck terror, almost a whisper.'),
    'p3_stop': ('scared', 'Urgent hiss to stop the music, then a frightened whisper.'),
    'p4_bones': ('cheer', 'Surprised to see your own skeleton, then absolutely delighted, laughing.'),
    'p4_ay': ('cheer', 'A joyful mariachi grito, loud and long, full of party.'),
    'p4_listo': ('moved', 'Moved, quiet and proud, a lump in your throat: your boss asked for one last song.'),
    'p4_hype_last': ('cheer', 'Rallying your bandmates before the last song of your lives: loud, fired up, a shout.'),
    'p4_hype_andale': ('cheer', 'Pumped up and excited, egging the band on, fast.'),
    'p4_hype_eso': ('cheer', 'Fired up, a big shout, fist in the air.'),
    'gasp': ('yelp', 'A sharp startled gasp, jolted awake.'),
}
# what is spoken where the bubble's text has stage directions or is not a line
SPOKEN = {
    'yawn': 'Aaaaah... ¿Ya es la hora?',
    'sigh': 'Aaay... Siempre lo mismo con este señor...',
    'gasp': '¡Ah!',
}
EXTRA = {'gasp'}                                  # sound cues, not lines (no bubble)
NO_CHECK = {'p3_mama', 'gasp'}                     # a scream or a gasp: Whisper cannot judge it
ENGLISH = re.compile(r'\b(what|do|we|just|play|before|he|kills|us|is|looking|at|has|wings|feel|great|one|last|song)\b', re.I)


def lines():
    lang = json.load(open(LANG, encoding='utf-8'))
    out = {}
    for k, v in lang.items():
        if k.startswith('speech.papon.band.') and not k.endswith('.gloss'):
            line = k[len('speech.papon.band.'):]
            if line != 'count':
                out[line] = SPOKEN.get(line, v)
    for line in EXTRA:
        out[line] = SPOKEN[line]
    missing = [l for l in out if l not in DIRECTION]
    if missing:
        raise SystemExit('no direction for ' + ', '.join(missing))
    return out


def take_path(line, k):
    return os.path.join(CACHE, 'takes', '%s_%d.wav' % (line, k))


def takes(only=None):
    modal, vm = sv.modal_app()
    clip, text = sv.reference_clip()
    refs = {'band': [clip, text]}
    todo = [(l, t) for l, t in lines().items() if not only or l in only]
    reqs = []
    for line, t in todo:
        mixed = bool(ENGLISH.search(t))
        reqs.append(dict(id=line, text=t, ref='band', language='Spanish', stt=None if mixed else 'es',
                         instruct=ME + DIRECTION[line][1], gen={'temperature': 1.0}))
    os.makedirs(os.path.join(CACHE, 'takes'), exist_ok=True)
    meta_file = os.path.join(CACHE, 'takes.json')
    meta = json.load(open(meta_file, encoding='utf-8')) if os.path.exists(meta_file) else {}
    with modal.enable_output(), vm.app.run():
        for result in vm.clone.map([refs] * len(reqs), [[r] for r in reqs], [TAKES] * len(reqs)):
            for line, ts in result.items():
                meta[line] = {'text': dict(todo)[line], 'takes': []}
                for k, t in enumerate(ts):
                    with open(take_path(line, k), 'wb') as f:
                        f.write(t['wav'])
                    meta[line]['takes'].append({'heard': t['heard'], 'seconds': t['seconds'], 'sim': t.get('sim')})
                json.dump(meta, open(meta_file, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)


def words(s):
    import unicodedata
    s = unicodedata.normalize('NFKD', s.lower())
    s = ''.join(c for c in s if not unicodedata.combining(c))
    return re.sub(r'[^a-z ]+', ' ', s).split()


def wer(ref, hyp):
    r, h = words(ref), words(hyp)
    d = list(range(len(h) + 1))
    for i in range(1, len(r) + 1):
        prev, d[0] = d[0], i
        for j in range(1, len(h) + 1):
            cur = min(d[j] + 1, d[j - 1] + 1, prev + (r[i - 1] != h[j - 1]))
            prev, d[j] = d[j], cur
    return d[len(h)] / max(1, len(r))


def pick(line, info):
    """Said right (Whisper), not cut off, sounding like the reference, then the liveliest."""
    import gen_boss_voice as gbv
    cands = []
    for k, t in enumerate(info['takes']):
        y, sr = gbv.load_mono(take_path(line, k))
        e = gbv.expressiveness(y, sr)
        err = 0 if line in NO_CHECK else wer(info['text'], t['heard'])
        n = max(1, len(words(info['text'])))
        score = 1.2 * min(e['range'], 16) + 0.5 * min(e['dyn'], 24) + 40 * ((t.get('sim') or 0.9) - 0.9) \
            - 10 * max(0.0, t['seconds'] / n - 0.6)
        if line in NO_CHECK:
            score -= 4 * abs(t['seconds'] - (0.6 if line == 'gasp' else 1.4))
        cands.append((bool(gbv.clipped(y, sr)), round(min(err, 0.6) * 4) / 4, -score, k))
    best = min(cands)
    return best[3], best


def build():
    import gen_boss_voice as gbv
    import geo_util
    meta = json.load(open(os.path.join(CACHE, 'takes.json'), encoding='utf-8'))
    out_dir = os.path.join(ASSETS, 'sounds/voice/band')
    os.makedirs(out_dir, exist_ok=True)
    mouths = {}
    sounds = geo_util.read_sounds()
    for line in sorted(lines()):
        if line not in meta:
            print('no takes:', line)
            continue
        k, why = pick(line, meta[line])
        y, sr = gbv.load_mono(take_path(line, k))
        if sr != SR:
            import librosa
            y = librosa.resample(y, orig_sr=sr, target_sr=SR)
        y = gbv.trim(y, SR)
        y = gbv.loudness(y, SR) * 10 ** (GAIN_DB / 20)
        soundfile.write(os.path.join(out_dir, line + '.ogg'), y, SR, format='OGG', subtype='VORBIS')
        env = sv.envelope(y, SR // RATE)
        env = np.clip(env / (np.percentile(env, 97) + 1e-9), 0, 1) ** 0.8
        mouths[line] = [round(float(v), 2) for v in env]
        # played at volume 1 under the music (client/SoundMix), heard 48 blocks off
        sounds['voice.band.' + line] = {'sounds': [{'name': 'papon:voice/band/' + line, 'attenuation_distance': 48}]}
        print('%-15s take %d %s %.2fs | %s' % (line, k, why[:3], len(y) / SR, meta[line]['takes'][k]['heard']))
    with open(os.path.join(ASSETS, 'voice/band.json'), 'w', encoding='utf-8', newline='\n') as f:
        json.dump({'rate': RATE, 'lines': mouths}, f, separators=(',', ':'))
    geo_util.write_sounds(sounds)


if __name__ == '__main__':
    if '--takes' in sys.argv:
        i = sys.argv.index('--takes')
        takes(set(sys.argv[i + 1].split(',')) if len(sys.argv) > i + 1 else None)
    else:
        build()
