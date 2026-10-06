"""The mariachi singer's count-in, "¡Un, dos, tres, cuatro!", in a cloned voice (2026-10-05, the user's reference: a
Castilian male voiceover demo, tools/src/voice/singer_reference.mp3, gitignored).

  python tools/gen_singer_voice.py --ref      Whisper on the reference (Modal): its words and timings, cached
  python tools/gen_singer_voice.py --takes    clone the count TAKES times per wording (Modal: tools/voice_modal.py)
  python tools/gen_singer_voice.py            pick a take, cut its four words and lay each on its beat, write
                                              sounds/voice/band/count.ogg, voice/singer.json (the mouth), sounds.json

The four words are counted at half time, one every two of the song's beats (~0.94 s), the last ending before the band's
downbeat: the count is BandState.COUNT (5.6 s) long, the downbeat falls at its end, so the words fall at 1.84, 2.78, 3.72
and 4.66 s into the count and "¡cuatro!" has the last two beats to ring out (2026-10-05, the user: the count was cut off at
"tres": at 0.47 s apart "cua-tro" ran past the downbeat, where the singer's mouth goes over to the song, and "un" came out
ten times quieter than the rest). Every word is brought to the same loudness, "cuatro" a little over. The clip is played
START s into the count (MariachiEntity.tickBand, COUNT). Then run gen_band_anims.py: the
singer's count animation takes its mouth and its fist pumps from voice/singer.json.

Needs: pip install modal soundfile numpy librosa pyloudnorm; a Modal token (PAPON_MODAL_PROFILE or the active one).
"""
import json
import os
import re
import sys

import numpy as np
import soundfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, 'tools'))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/papon')
CACHE = os.path.join(ROOT, 'tools/.cache/singer')
REFERENCE = os.path.join(ROOT, 'tools/src/voice/singer_reference.mp3')
SR = 24000
TAKES = 12
BEAT = 0.47                       # the song's beat at its start (cancion.json beats 0-8)
DOWNBEAT = 5.6                    # BandState.COUNT
STEP = 2 * BEAT                   # a word every other beat: the count at half time, deliberate
START = 1.7                       # the clip starts this far into the count (MariachiEntity: t == 34)
WORDS = ['un', 'dos', 'tres', 'cuatro']
BEATS = [round(DOWNBEAT - STEP * (4 - i), 3) for i in range(4)]
RING = 0.12                       # "cuatro" is over this long before the downbeat
LEAD = 0.035                      # a word's attack comes this much before its beat (the consonant before the vowel)

# the wordings to take: the same count, phrased so the model punches each number
TEXTS = {
    'a': '¡Un, dos, tres, cuatro!',
    'b': '¡Un! ¡Dos! ¡Tres! ¡Cuatro!',
}
INSTRUCT = ('You are the lead singer of a mariachi band on a huge stage in front of a roaring stadium, counting your band in '
            'right before the music explodes: loud, fiery, full of joy and swagger, every number punched out like a shout, '
            'building up, and the last one, cuatro, a big passionate yell. Not a narrator: a showman on fire.')


def load(path):
    import librosa
    y, sr = soundfile.read(path, dtype='float32', always_2d=True)
    y = y.mean(axis=1)
    return (librosa.resample(y, orig_sr=sr, target_sr=SR) if sr != SR else y), SR


def wav_bytes(y):
    import io
    buf = io.BytesIO()
    soundfile.write(buf, y, SR, format='WAV', subtype='PCM_16')
    return buf.getvalue()


def modal_app():
    if os.environ.get('PAPON_MODAL_PROFILE'):
        os.environ['MODAL_PROFILE'] = os.environ['PAPON_MODAL_PROFILE']
    import modal
    import voice_modal
    return modal, voice_modal


def ref_path():
    return os.path.join(CACHE, 'ref.json')


def transcribe_ref():
    modal, vm = modal_app()
    y, _ = load(REFERENCE)
    with modal.enable_output(), vm.app.run():
        r = vm.transcribe.remote(wav_bytes(y), 'es')
    os.makedirs(CACHE, exist_ok=True)
    json.dump(r, open(ref_path(), 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
    for s in r['segments']:
        print('%6.2f-%6.2f %s' % (s['start'], s['end'], s['text']))


def reference_clip():
    """The liveliest 9-14 s of whole segments of the reference, with their words."""
    r = json.load(open(ref_path(), encoding='utf-8'))
    y, _ = load(REFERENCE)
    segs = r['segments']
    best = None
    for i in range(len(segs)):
        for j in range(i, len(segs)):
            a, b = segs[i]['start'], segs[j]['end']
            if b - a > 14:
                break
            if b - a < 9:
                continue
            part = y[int(a * SR):int(b * SR)]
            import librosa
            f0, voiced, _ = librosa.pyin(part, fmin=60, fmax=400, sr=SR, frame_length=2048, hop_length=480)
            st = 12 * np.log2(f0[voiced]) if np.count_nonzero(voiced) > 10 else np.zeros(1)
            score = float(np.percentile(st, 90) - np.percentile(st, 10)) + 20 * float(np.sqrt(np.mean(part ** 2)))
            if best is None or score > best[0]:
                best = (score, a, b, ' '.join(s['text'].strip() for s in segs[i:j + 1]))
    _, a, b, text = best
    print('reference: %.2f-%.2f "%s"' % (a, b, text))
    return wav_bytes(y[int(max(0, a - 0.05) * SR):int((b + 0.1) * SR)]), text


def takes():
    modal, vm = modal_app()
    clip, text = reference_clip()
    refs = {'singer': [clip, text]}
    lines = [dict(id=k, text=t, ref='singer', language='Spanish', stt='es', instruct=INSTRUCT, gen={'temperature': 1.0})
             for k, t in TEXTS.items()]
    os.makedirs(os.path.join(CACHE, 'takes'), exist_ok=True)
    meta = {}
    with modal.enable_output(), vm.app.run():
        for result in vm.clone.map([refs] * len(lines), [[l] for l in lines], [TAKES] * len(lines)):
            for line, ts in result.items():
                for k, t in enumerate(ts):
                    with open(os.path.join(CACHE, 'takes', '%s_%d.wav' % (line, k)), 'wb') as f:
                        f.write(t['wav'])
                    meta['%s_%d' % (line, k)] = {'heard': t['heard'], 'seconds': t['seconds'], 'sim': t.get('sim')}
    json.dump(meta, open(os.path.join(CACHE, 'takes.json'), 'w', encoding='utf-8'), ensure_ascii=False, indent=1)


def envelope(y, hop):
    win = hop * 2
    pad = np.pad(y, (win // 2, win // 2))
    return np.array([np.sqrt(np.mean(pad[i:i + win] ** 2)) for i in range(0, len(y), hop)])


def words(y):
    """The four words as (start, end) in seconds: the take's loud stretch cut at the three most prominent dips in its
    loudness (the gaps between the numbers; "cuatro"'s own dip, at its t, loses to them or makes a word too short)."""
    import itertools
    import scipy.signal
    hop = SR // 100
    env = np.convolve(envelope(y, hop), np.ones(3) / 3, mode='same')
    db = 20 * np.log10(env / (env.max() + 1e-9) + 1e-9)
    idx = np.nonzero(db > -30)[0]
    if len(idx) == 0:
        return None
    a, b = idx[0], idx[-1]
    dips, props = scipy.signal.find_peaks(-db[a:b + 1], prominence=3)
    if len(dips) < 3:
        return None
    order = np.argsort(props['prominences'])[::-1][:7]
    best = None
    for combo in itertools.combinations(sorted(order), 3):
        cuts = [a + dips[i] for i in combo]
        edges = [a] + cuts + [b]
        lens = [(edges[i + 1] - edges[i]) / 100 for i in range(4)]
        if min(lens[:3]) < 0.12 or max(lens[:3]) > 0.7 or lens[3] < 0.22:
            continue
        sc = sum(props['prominences'][i] for i in combo)
        if best is None or sc > best[0]:
            best = (sc, edges)
    if best is None:
        return None
    edges = best[1]
    out = []
    for i in range(4):
        s, e = edges[i], edges[i + 1]
        seg = db[s:e + 1]
        on = s + int(np.argmax(seg > -24))                  # the attack
        off = e - int(np.argmax(seg[::-1] > -36))           # the word's tail
        out.append((on * hop / SR, max(off, on + 5) * hop / SR))
    return out


def attack(seg, hop):
    """Seconds into a word's segment where it first reaches a third of its loudest."""
    env = envelope(seg, hop)
    return float(np.argmax(env >= env.max() / 3)) * hop / SR if len(env) else 0.0


def score(name, y, heard):
    import gen_boss_voice as gbv
    # Whisper writes the numbers as digits as often as words
    h = re.sub(r'[^a-záéíóúñ0-9 ]', ' ', heard.lower())
    said = sum(1 for i, w in enumerate(WORDS) if re.search(r'\b(%s|%d)\b' % ('uno?' if w == 'un' else w, i + 1), h))
    ws = words(y)
    if ws is None:
        return None
    e = gbv.expressiveness(y, SR)
    # every word whole in its two beats, "cuatro" over before the downbeat (a take whose words run long is cut short)
    lens = [w[1] - w[0] for w in ws]
    fit = -sum(max(0.0, l - (STEP - 0.05)) for l in lens[:3]) - max(0.0, lens[3] - (STEP - RING))
    # and as even as possible: the quietest word next to the loudest (the "un" that was ten times quieter)
    lv = [float(np.sqrt(np.mean(y[int(a * SR):int(b * SR)] ** 2))) + 1e-6 for a, b in ws]
    even = 20 * np.log10(min(lv) / max(lv))
    return (said, e['dyn'] + 0.6 * e['range'] + e['bright'] / 400 + 8 * fit + 0.5 * even, ws)


def build():
    meta = json.load(open(os.path.join(CACHE, 'takes.json'), encoding='utf-8'))
    cands = []
    for name, m in meta.items():
        y, _ = load(os.path.join(CACHE, 'takes', name + '.wav'))
        s = score(name, y, m['heard'])
        print('%-6s %-34s %s' % (name, m['heard'], s and (s[0], round(s[1], 2))))
        if s:
            cands.append((s[0], s[1], name, y, s[2]))
    cands.sort(key=lambda c: (c[0], c[1]), reverse=True)
    said, sc, name, y, ws = cands[0]
    print('picked', name, meta[name]['heard'])

    # lay each word on its beat, all at one loudness ("cuatro" 2 dB over); a word longer than its two beats is faded out
    # under the next, and "cuatro" is over RING before the downbeat
    total = DOWNBEAT - START
    out = np.zeros(int(total * SR), np.float32)
    placed = []
    lv = [float(np.sqrt(np.mean(y[int(s * SR):int(e * SR)] ** 2))) + 1e-6 for s, e in ws]
    # each word's attack is where it first reaches a third of its own peak (a breath before "un" read as its start)
    hop = SR // 200
    ws = [(s + attack(y[int(s * SR):int(e * SR)], hop), e) for s, e in ws]
    for i, (s, e) in enumerate(ws):
        a = int(max(0, s - 0.03) * SR)
        b = int(min(len(y), (e + 0.12) * SR))
        seg = y[a:b].copy() * (max(lv) / lv[i]) * (10 ** (2 / 20) if i == 3 else 1)
        at = BEATS[i] - LEAD - START - (s - a / SR)
        room = (BEATS[i + 1] - BEATS[i]) if i < 3 else DOWNBEAT - RING - BEATS[i]
        n = min(len(seg), int((room + 0.02) * SR))
        seg = seg[:n]
        fade = min(int(0.06 * SR), n // 3)
        seg[-fade:] *= np.linspace(1, 0, fade)
        o = int(at * SR)
        out[o:o + n] += seg[:max(0, min(n, len(out) - o))]
        placed.append(round(START + at + (s - a / SR), 3))
    import gen_boss_voice as gbv
    out = gbv.loudness_py(out, SR)
    path = os.path.join(ASSETS, 'sounds/voice/band')
    os.makedirs(path, exist_ok=True)
    soundfile.write(os.path.join(path, 'count.ogg'), out, SR, format='OGG', subtype='VORBIS')

    # the mouth: loudness at 25 Hz from the count's start, and where each word starts
    hop = SR // 25
    env = envelope(out, hop)
    env = np.clip(env / (np.percentile(env, 97) + 1e-9), 0, 1) ** 0.8
    lead = int(START * 25)
    data = {'count': {'start': START, 'rate': 25, 'words': placed,
                      'env': [0.0] * lead + [round(float(v), 2) for v in env]}}
    with open(os.path.join(ASSETS, 'voice/singer.json'), 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, separators=(',', ':'))

    import geo_util
    sounds = geo_util.read_sounds()
    sounds['voice.band.count'] = {'sounds': [{'name': 'papon:voice/band/count', 'attenuation_distance': 48}]}
    geo_util.write_sounds(sounds)
    print('words at', placed, 'clip %.2f s' % (len(out) / SR))


if __name__ == '__main__':
    if '--ref' in sys.argv:
        transcribe_ref()
    elif '--takes' in sys.argv:
        takes()
    else:
        build()
