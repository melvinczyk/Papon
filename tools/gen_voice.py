#!/usr/bin/env python3
"""Chef Papon's voice: every line of his (speech.papon.boss.* and speech.papon.vendor.* in en_us.json,
without the .gloss translations) read by macOS text-to-speech in a Mexican Spanish voice, pitched down
for a heavier chef, and written as sounds/voice/<who>/<line>.ogg plus a "voice.<who>.<line>" event in
sounds.json. A stand-in until real recordings: drop one over the .ogg to replace a line.

Needs macOS `say`, ffmpeg (pitch) and soundfile (mono Vorbis, so the line comes from where he stands):
  pip install numpy soundfile && python3 tools/gen_voice.py
"""
import json
from geo_util import read_sounds, write_sounds
import os
import re
import subprocess
import tempfile

import soundfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/papon')
VOICE = 'Rocko (Spanish (Mexico))'
RATE = 185            # words per minute: quick, he is angry
PITCH = 0.86          # resampled down: deeper, rounder


def speakable(text):
    # what the bubble shows, minus what TTS would read out loud (ellipses, emphasis marks)
    text = re.sub(r'§.', '', text)
    return text.replace('...', ', ').replace('…', ', ').strip()


def main():
    lang = json.load(open(os.path.join(ASSETS, 'lang/en_us.json')))
    sounds = read_sounds()
    for k in [k for k in sounds if k.startswith('voice.')]:
        del sounds[k]
    n = 0
    with tempfile.TemporaryDirectory() as tmp:
        for key, text in sorted(lang.items()):
            m = re.fullmatch(r'speech\.papon\.(boss|vendor)\.([a-z0-9_]+)', key)
            if not m:
                continue
            who, line = m.groups()
            out_dir = os.path.join(ASSETS, 'sounds/voice', who)
            os.makedirs(out_dir, exist_ok=True)
            aiff = os.path.join(tmp, '%s_%s.aiff' % (who, line))
            subprocess.run(['say', '-v', VOICE, '-r', str(RATE), '-o', aiff, speakable(text)], check=True)
            wav = os.path.join(tmp, '%s_%s.wav' % (who, line))
            subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', aiff, '-ac', '1',
                            '-af', 'asetrate=22050*%s,aresample=44100,atempo=%s,volume=1.6' % (PITCH, 1 / PITCH * 0.95),
                            wav], check=True)
            data, rate = soundfile.read(wav)
            soundfile.write(os.path.join(out_dir, line + '.ogg'), data, rate, format='OGG', subtype='VORBIS')
            sounds['voice.%s.%s' % (who, line)] = {'sounds': [{'name': 'papon:voice/%s/%s' % (who, line)}]}
            n += 1
    write_sounds(sounds)
    print(n, 'lines voiced')


if __name__ == '__main__':
    main()
