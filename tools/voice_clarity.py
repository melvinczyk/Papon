#!/usr/bin/env python3
"""How clearly Chef Papon's built lines are said (2026-10-05, the user: "some lines currently sound jumbled or poorly
articulated... improve his diction wherever necessary so that every line is clearly understandable").

Every built line (assets/papon/sounds/voice/boss2/<line>.ogg) goes through Whisper large-v3 on Modal (voice_modal.clarity):
what it hears, against what he should be saying (gen_boss_voice.LINES, or SPOKEN where the model is given another
spelling), and how sure it is of each word. A line Whisper mishears, or is unsure of, is a line a player mishears.

  PYTHONPATH=tools python tools/voice_clarity.py              # every line; report in tools/.cache/voice/clarity.json
  PYTHONPATH=tools python tools/voice_clarity.py p4_mistake   # just these

Prints the lines worst first: word error rate, mean and least word confidence, and what was heard. gen_boss_voice.UNCLEAR
lists the ones to retake with the articulated direction.
"""
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, 'tools'))
import gen_boss_voice as g  # noqa: E402

OUT = os.path.join(g.CACHE, 'clarity.json')


def spoken(line):
    return g.SPOKEN.get(line, g.LINES[line][0])


def main():
    built = json.load(open(os.path.join(g.ASSETS, 'voice/boss2.json'), encoding='utf-8'))['lines']
    lines = [l for l in (sys.argv[1:] or sorted(built)) if l in g.LINES and l in built]
    clips = {}
    for line in lines:
        path = os.path.join(g.ASSETS, 'sounds/voice/boss2/%s.ogg' % line)
        if os.path.exists(path):
            clips[line] = open(path, 'rb').read()
    if g.MODAL_PROFILE:
        os.environ['MODAL_PROFILE'] = g.MODAL_PROFILE
    import modal
    import voice_modal
    print('checking %d lines on Modal...' % len(clips))
    with modal.enable_output(), voice_modal.app.run():
        res = voice_modal.clarity.remote(clips)
    report = json.load(open(OUT, encoding='utf-8')) if os.path.exists(OUT) else {}
    for line, r in res.items():
        r['wer'] = g.wer(spoken(line), r['heard'])
        r['text'] = spoken(line)
        report[line] = r
    os.makedirs(g.CACHE, exist_ok=True)
    json.dump(report, open(OUT, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
    rows = sorted(res.items(), key=lambda kv: (-(kv[1]['wer'] if kv[0] not in g.NO_CHECK else 0), kv[1]['conf']))
    for line, r in rows:
        print('%-16s wer %.2f conf %.2f min %.2f | %s' % (line, r['wer'], r['conf'], r['minconf'], r['heard']))


if __name__ == '__main__':
    main()
