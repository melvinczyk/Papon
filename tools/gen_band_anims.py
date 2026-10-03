#!/usr/bin/env python3
"""Generates the mariachi band's animations: assets/papon/animations/entity/mariachi.animation.json.

Conventions are the rest of the mod's (gen_anims.py): rot X < 0 swings a limb forward and tilts the
body back; arm_r Z > 0 / arm_l Z < 0 raise an arm outward; arm_r Y < 0 / arm_l Y > 0 swing it inward.
The arms are split (gen_vendor.rig_hands): reaches stretch `arm_*_mesh` and slide `hand_*`.

Every animation says outright what is shown: the suit pieces they put on (`jacket`, sleeves,
`sombrero`, `shades`) or their places off them (`jacket_chair`, `sombrero_floor`), each instrument in
hand or on the floor (the renderer hides every instrument but the member's own), the eyelids.

The playing loops are only the pose and a gentle sway: client/MariachiModel lays the music over them
from the song's own data (assets/papon/song/cancion.json): the beat (foot taps, bobbing), the guitar
strums, the bowing, and the singer's lips (Rhubarb shapes), brows (his pitch) and sway.

Lengths of one-shots = BandState durations in Java:
  startle 1.0, getup 3.0, resleep 2.0, jacket 2.5, hat 1.5, shades 1.0, grab_* 1.5, count 4.0
  (the singer: "uno" 0.2, "dos" 1.15, "uno, dos, tres" 2.1 / 2.55 / 3.05, the downbeat at 4.0),
  bow 2.5; loops: sleep_a/b/c 3.0, ready_* 2.0, play_* 1.88 (a bar of the song)
"""
import json
import os

from gen_anims import guard_brows, EPS, Anim
from gen_boss_anims import stretch as boss_stretch

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'src/main/resources/assets/papon/animations/entity/mariachi.animation.json')
SHOWN, HIDDEN = [1, 1, 1], [0, 0, 0]

DRESSED = ['jacket', 'sleeve_r', 'sleeve_l', 'sombrero']
UNDRESSED = ['jacket_chair', 'sombrero_floor']
GUITARS = ['guitar', 'vihuela', 'guitarron']
HELD = GUITARS + ['trumpet', 'violin', 'bow']
FLOOR = ['guitar_floor', 'vihuela_floor', 'guitarron_floor', 'trumpet_floor', 'violin_floor']

SEAT_ROOT = [0, 3, 0]
SEAT_LEG_R = [-80, 8, 0]
SEAT_LEG_L = [-80, -8, 0]
BAR = 1.882          # a bar of the song (127.5 bpm)


class M(Anim):
    def compile(self):
        bones = {b: {ch: dict(sorted(keys.items(), key=lambda kv: float(kv[0]))) for ch, keys in c.items()}
                 for b, c in self.bones.items()}
        a = {
            'loop': True if self.loop is True else ('hold_on_last_frame' if self.loop == 'hold' else False),
            'animation_length': self.length,
            'bones': bones,
        }
        if self.sounds:
            a['sound_effects'] = self.sounds
        return a


def stretch(a, arm, keys):
    a.scale(arm + '_mesh', [(t, [1, k, 1]) + tuple(rest) for t, k, *rest in keys], 'lin')
    a.pos('hand_' + arm[-1], [(t, [0, -(k - 1) * 6.5, 0]) + tuple(rest) for t, k, *rest in keys], 'lin')


def show(a, bones, on, t=0):
    for b in bones:
        if b not in a.bones or 'scale' not in a.bones[b]:
            a.scale(b, [(t, SHOWN if on else HIDDEN)], 'lin')


def state(a, dressed, held, shades=None, lids=False):
    """What is on and what is off for the whole animation (keys a bone only if the animation has not)."""
    show(a, DRESSED, dressed)
    show(a, UNDRESSED, not dressed)
    show(a, HELD, held)
    show(a, FLOOR, not held)
    show(a, ['shades'], dressed if shades is None else shades)
    show(a, ['eyelids'], lids)
    # the teeth and tongue only ever show in the lip sync (client/MariachiModel)
    show(a, ['teeth', 'tongue'], False)
    return a


def seated(a, t0, t1):
    a.pos('root', [(t0, SEAT_ROOT), (t1, SEAT_ROOT)], 'lin')
    a.rot('leg_r', [(t0, SEAT_LEG_R), (t1, SEAT_LEG_R)], 'lin')
    a.rot('leg_l', [(t0, SEAT_LEG_L), (t1, SEAT_LEG_L)], 'lin')


def mouth(a, keys):
    """keys: [(t, open px)]: the lower lip drops, the dark inside grows down from the upper lip."""
    a.pos('lip_low', [(t, [0, -o, 0]) + tuple(r) for t, o, *r in keys])
    a.scale('mouth_in', [(t, [1, max(0.0, o - 0.2), 1]) + tuple(r) for t, o, *r in keys])


# ===================================================================== asleep

def sleep_a():
    """Leaning back in his chair, mouth open, snoring at the ceiling."""
    a = state(M('sleep_a', 3.0, True), False, False, lids=True)
    seated(a, 0, 3.0)
    a.rot('torso', [(0, [-14, 0, 2]), (1.5, [-17, 0, 2]), (3.0, [-14, 0, 2])])
    a.scale('body', [(0, [1, 1, 1]), (1.4, [1.03, 1.035, 1.03]), (3.0, [1, 1, 1])])
    a.rot('arm_r', [(0, [10, 0, 14]), (1.5, [12, 0, 16]), (3.0, [10, 0, 14])])
    a.rot('arm_l', [(0, [10, 0, -14]), (1.5, [12, 0, -16]), (3.0, [10, 0, -14])])
    mouth(a, [(0, 0.9), (1.4, 1.6), (2.2, 0.8), (3.0, 0.9)])
    for b, s in (('brow_r', 1), ('brow_l', -1)):
        a.rot(b, [(0, [0, 0, -6 * s])], 'lin')
    a.sound(1.3, 'snore')
    return a


def sleep_b():
    """Slumped forward, chin on his chest, arms hanging between his knees."""
    a = state(M('sleep_b', 3.0, True), False, False, lids=True)
    seated(a, 0, 3.0)
    a.rot('torso', [(0, [26, 0, 0]), (1.5, [29, 0, -2]), (3.0, [26, 0, 0])])
    a.scale('body', [(0, [1, 1, 1]), (1.5, [1.035, 1.03, 1.035]), (3.0, [1, 1, 1])])
    a.rot('arm_r', [(0, [-24, -10, 4]), (1.5, [-22, -10, 4]), (3.0, [-24, -10, 4])])
    a.rot('arm_l', [(0, [-24, 10, -4]), (1.5, [-22, 10, -4]), (3.0, [-24, 10, -4])])
    mouth(a, [(0, 0.6), (1.5, 1.1), (3.0, 0.6)])
    a.sound(1.6, 'snore_lo')
    return a


def sleep_c():
    """Sagging sideways in the chair, one arm hanging off it, the other on his belly."""
    a = state(M('sleep_c', 3.0, True), False, False, lids=True)
    seated(a, 0, 3.0)
    a.rot('root', [(0, [0, 0, 9]), (3.0, [0, 0, 9])], 'lin')
    a.rot('torso', [(0, [6, 0, 10]), (1.5, [8, 0, 12]), (3.0, [6, 0, 10])])
    a.scale('body', [(0, [1, 1, 1]), (1.6, [1.03, 1.03, 1.03]), (3.0, [1, 1, 1])])
    a.rot('arm_r', [(0, [0, 0, 30]), (1.5, [3, 0, 32]), (3.0, [0, 0, 30])])
    a.rot('arm_l', [(0, [-30, 40, 0]), (3.0, [-30, 40, 0])], 'lin')
    mouth(a, [(0, 0.8), (1.6, 1.6), (3.0, 0.8)])
    a.sound(1.7, 'snore')
    return a


# ===================================================================== waking up

def startle():
    """1.0 s. The clang: bolt upright in the chair, arms flung out, eyes wide."""
    a = state(M('startle', 1.0, 'hold'), False, False)
    a.vis('eyelids', True, [(0.06, False)])
    a.pos('root', [(0, SEAT_ROOT), (0.1, [0, 6, 0], 'easeOutQuad'), (0.32, SEAT_ROOT, 'easeInQuad'), (1.0, SEAT_ROOT)], 'lin')
    a.rot('leg_r', [(0, SEAT_LEG_R), (0.1, [-100, 14, 0]), (0.4, SEAT_LEG_R), (1.0, SEAT_LEG_R)])
    a.rot('leg_l', [(0, SEAT_LEG_L), (0.1, [-100, -14, 0]), (0.4, SEAT_LEG_L), (1.0, SEAT_LEG_L)])
    for arm, s in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [10, 0, 14 * s]), (0.1, [-40, 0, 80 * s], 'easeOutExpo'), (0.5, [-30, 0, 60 * s]),
                    (1.0, [0, 0, 10 * s], 'easeInOutSine')], 'lin')
    a.rot('torso', [(0, [-14, 0, 0]), (0.1, [-6, 0, 0], 'easeOutExpo'), (1.0, [0, 0, 0])], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (0.1, [0.9, 1.16, 0.9], 'easeOutExpo'), (0.35, [1.04, 0.96, 1.04]), (0.6, [1, 1, 1])], 'lin')
    for b, s in (('brow_r', 1), ('brow_l', -1)):
        a.pos(b, [(0, [0, 0, 0]), (0.1, [0, 1.2, 0]), (0.8, [0, 0.8, 0]), (1.0, [0, 0.4, 0])])
    mouth(a, [(0, 1.0), (0.1, 2.6, 'easeOutExpo'), (0.5, 1.5), (1.0, 0)])
    a.sound(0.05, 'gasp')
    return a


def getup():
    """3.0 s. Rubs his eyes, a huge yawn and a stretch, gets up out of the chair, scratches his belly."""
    a = state(M('getup', 3.0, 'hold'), False, False)
    a.pos('root', [(0, SEAT_ROOT), (1.9, SEAT_ROOT), (2.35, [0, 0.5, -1], 'easeOutQuad'), (2.6, [0, 0, 0], 'easeInOutSine'),
                   (3.0, [0, 0, 0])], 'lin')
    a.rot('leg_r', [(0, SEAT_LEG_R), (1.9, SEAT_LEG_R), (2.4, [0, 0, 0], 'easeInOutSine'), (3.0, [0, 0, 0])], 'lin')
    a.rot('leg_l', [(0, SEAT_LEG_L), (1.9, SEAT_LEG_L), (2.4, [0, 0, 0], 'easeInOutSine'), (3.0, [0, 0, 0])], 'lin')
    rub_r, rub_l = [-150, -40, 0], [-150, 40, 0]
    a.rot('arm_r', [(0, [0, 0, 10]), (0.25, rub_r, 'easeInOutSine')]
          + [(round(0.3 + 0.12 * i, 2), [-150 + (6 if i % 2 else -6), -40, 0]) for i in range(6)]
          + [(1.05, [-175, 0, 22], 'easeInOutSine'), (1.75, [-178, 0, 26]), (2.0, [-20, 0, 12], 'easeInOutSine'),
             (2.5, [-40, -50, 0], 'easeInOutSine'), (2.65, [-44, -44, 0]), (2.8, [-40, -50, 0]), (2.95, [-44, -44, 0]), (3.0, [-30, -30, 4])], 'lin')
    a.rot('arm_l', [(0, [0, 0, -10]), (0.25, rub_l, 'easeInOutSine')]
          + [(round(0.3 + 0.12 * i, 2), [-150 + (-6 if i % 2 else 6), 40, 0]) for i in range(6)]
          + [(1.05, [-175, 0, -22], 'easeInOutSine'), (1.75, [-178, 0, -26]), (2.0, [-20, 0, -12], 'easeInOutSine'), (3.0, [0, 0, -8])], 'lin')
    stretch(a, 'arm_r', [(0, 1), (0.25, 1.35), (1.0, 1.35), (1.05, 1.5), (1.75, 1.5), (2.0, 1), (2.5, 1.2), (3.0, 1.1)])
    stretch(a, 'arm_l', [(0, 1), (0.25, 1.35), (1.0, 1.35), (1.05, 1.5), (1.75, 1.5), (2.0, 1)])
    a.rot('torso', [(0, [0, 0, 0]), (1.05, [-16, 0, 0], 'easeInOutSine'), (1.75, [-18, 0, 0]), (2.1, [22, 0, 0], 'easeInOutSine'),
                    (2.6, [0, 0, 0], 'easeInOutSine'), (3.0, [0, 0, 0])], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (1.05, [0.95, 1.12, 0.95], 'easeInOutSine'), (1.75, [0.94, 1.14, 0.94]),
                     (2.0, [1.04, 0.96, 1.04], 'easeOutQuad'), (2.3, [1, 1, 1])], 'lin')
    a.vis('eyelids', True, [(0.9, False), (1.1, True), (1.8, False)])
    mouth(a, [(0, 0), (1.0, 0.2), (1.2, 3.2, 'easeOutQuad'), (1.7, 3.4), (1.95, 0, 'easeInQuad'), (3.0, 0)])
    a.sound(1.15, 'yawn').sound(2.35, 'grunt')
    return a


def resleep():
    """2.0 s. Up for a second, then he just... sits back down and is gone again."""
    a = state(M('resleep', 2.0, 'hold'), False, False)
    a.pos('root', [(0, [0, 0, 0]), (0.5, [0, 0, 0]), (0.9, SEAT_ROOT, 'easeInQuad'), (2.0, SEAT_ROOT)], 'lin')
    a.rot('leg_r', [(0, [0, 0, 0]), (0.5, [0, 0, 0]), (0.9, SEAT_LEG_R, 'easeInQuad'), (2.0, SEAT_LEG_R)], 'lin')
    a.rot('leg_l', [(0, [0, 0, 0]), (0.5, [0, 0, 0]), (0.9, SEAT_LEG_L, 'easeInQuad'), (2.0, SEAT_LEG_L)], 'lin')
    a.rot('torso', [(0, [0, 0, 0]), (0.9, [4, 0, 0]), (1.6, [26, 0, 0], 'easeInOutSine'), (2.0, [26, 0, 0])], 'lin')
    a.rot('arm_r', [(0, [0, 0, 6]), (1.6, [-24, -10, 4]), (2.0, [-24, -10, 4])])
    a.rot('arm_l', [(0, [0, 0, -6]), (1.6, [-24, 10, -4]), (2.0, [-24, 10, -4])])
    a.vis('eyelids', False, [(1.2, True)])
    mouth(a, [(0, 0), (1.4, 0), (2.0, 0.6)])
    a.sound(1.9, 'snore_lo')
    return a


def jacket():
    """2.5 s. Turns round to the chair, takes his jacket off its back, shrugs it on, tugs the lapels straight."""
    a = state(M('jacket', 2.5, 'hold'), False, False)
    a.vis('jacket_chair', True, [(0.6, False)])
    a.scale('jacket', [(0, HIDDEN), (1.0 - EPS, HIDDEN), (1.0, [1.25, 0.5, 1.25]), (1.25, [0.96, 1.06, 0.96], 'easeOutBack'),
                       (1.4, SHOWN), (2.5, SHOWN)], 'lin')
    for s in ('sleeve_r', 'sleeve_l'):
        a.scale(s, [(0, HIDDEN), (1.05 - EPS, HIDDEN), (1.05, [1.2, 0.3, 1.2]), (1.3, SHOWN, 'easeOutBack'), (2.5, SHOWN)], 'lin')
    a.rot('root', [(0, [0, 0, 0]), (0.45, [0, 165, 0], 'easeInOutSine'), (0.65, [0, 165, 0]), (1.0, [0, 0, 0], 'easeInOutSine'),
                   (2.5, [0, 0, 0])], 'lin')
    for arm, s in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [0, 0, 8 * s]), (0.45, [-70, 0, 10 * s], 'easeInOutSine'), (0.65, [-80, 0, 6 * s]),
                    (0.95, [50, 0, 40 * s], 'easeInOutSine'), (1.15, [30, 0, 20 * s]), (1.5, [-70, -40 * s, 0], 'easeInOutSine'),
                    (1.65, [-80, -40 * s, 0]), (1.8, [-70, -40 * s, 0]), (1.95, [-80, -40 * s, 0]), (2.3, [0, 0, 8 * s], 'easeInOutSine'),
                    (2.5, [0, 0, 8 * s])], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (1.0, [1, 1, 1]), (1.1, [1.06, 0.92, 1.06], 'easeOutQuad'), (1.3, [0.98, 1.04, 0.98]),
                     (1.5, [1, 1, 1])], 'lin')
    a.rot('torso', [(0, [0, 0, 0]), (1.65, [0, 0, 0]), (1.75, [-6, 0, 0]), (1.95, [-6, 0, 0]), (2.2, [0, 0, 0])])
    a.sound(0.6, 'cloth').sound(1.05, 'cloth').sound(1.7, 'flex')
    return a


def hat():
    """1.5 s. Bends down to the sombrero by his chair, puts it on with both hands, sets the brim."""
    a = state(M('hat', 1.5, 'hold'), True, False, shades=False)
    a.vis('sombrero_floor', True, [(0.5, False)])
    a.scale('sombrero', [(0, HIDDEN), (0.95 - EPS, HIDDEN), (0.95, [1.2, 1.2, 1.2]), (1.15, SHOWN, 'easeOutBounce'), (1.5, SHOWN)], 'lin')
    a.pos('sombrero', [(0, [0, 0, 0]), (0.95, [0, 4, 0]), (1.15, [0, 0, 0], 'easeOutBounce')], 'lin')
    a.rot('torso', [(0, [0, 0, 0]), (0.45, [34, 0, -16], 'easeInOutSine'), (0.55, [36, 0, -16]), (0.9, [-4, 0, 0], 'easeInOutSine'),
                    (1.5, [0, 0, 0])], 'lin')
    a.rot('arm_l', [(0, [0, 0, -8]), (0.45, [-50, 0, -40], 'easeInOutSine'), (0.55, [-54, 0, -40]),
                    (0.95, [-170, 0, -36], 'easeInOutSine'), (1.15, [-168, 0, -34]), (1.5, [0, 0, -8], 'easeInOutSine')], 'lin')
    a.rot('arm_r', [(0, [0, 0, 8]), (0.55, [0, 0, 8]), (0.95, [-170, 0, 36], 'easeInOutSine'), (1.15, [-168, 0, 34]),
                    (1.3, [-160, 0, 40]), (1.5, [0, 0, 8], 'easeInOutSine')], 'lin')
    stretch(a, 'arm_l', [(0, 1), (0.45, 1.4), (0.55, 1.4), (0.95, 1.5), (1.15, 1.5), (1.5, 1)])
    stretch(a, 'arm_r', [(0, 1), (0.55, 1), (0.95, 1.5), (1.3, 1.5), (1.5, 1)])
    a.sound(0.5, 'pick').sound(1.0, 'cloth')
    return a


def shades():
    """1.0 s (the singer). Slides his aviators on. Cool."""
    a = state(M('shades', 1.0, 'hold'), True, False, shades=True)
    a.scale('shades', [(0, HIDDEN), (0.35 - EPS, HIDDEN), (0.35, SHOWN), (1.0, SHOWN)], 'lin')
    a.pos('shades', [(0, [0, 4, -1]), (0.35, [0, 4, -1]), (0.6, [0, 0, 0], 'easeOutQuad'), (1.0, [0, 0, 0])], 'lin')
    a.rot('arm_r', [(0, [0, 0, 8]), (0.3, [-140, -38, 0], 'easeInOutSine'), (0.6, [-150, -36, 0]), (1.0, [0, 0, 8], 'easeInOutSine')], 'lin')
    stretch(a, 'arm_r', [(0, 1), (0.3, 1.5), (0.6, 1.4), (1.0, 1)])
    a.rot('torso', [(0, [0, 0, 0]), (0.6, [-4, 0, 0]), (0.8, [-6, 0, 7], 'easeOutBack'), (1.0, [-4, 0, 5])], 'lin')
    a.sound(0.55, 'tie')
    return a


# ===================================================================== the instruments

GUITAR_R = [-62, -52, 0]         # the strumming hand over the soundhole
GUITAR_L = [-72, 6, -30]        # the fretting hand up the neck
TRUMPET_R = [-92, -30, 0]        # both hands at the horn, at his lips
TRUMPET_L = [-92, 30, 0]
VIOLIN_L = [-88, 8, -18]         # holding the neck out in front of his left shoulder
VIOLIN_R = [-64, -46, 0]         # the bow across the strings


def guitar_pose(a, t0, t1=None):
    a.rot('arm_r', [(t0, GUITAR_R)] + ([(t1, GUITAR_R)] if t1 else []), 'lin')
    a.rot('arm_l', [(t0, GUITAR_L)] + ([(t1, GUITAR_L)] if t1 else []), 'lin')


def grab(kind):
    """1.5 s. Bends down to it, picks it up (0.5 s), and gets into position to play."""
    held = {'guitar': GUITARS, 'violin': ['violin', 'bow'], 'trumpet': ['trumpet']}[kind]
    floor = {'guitar': ['guitar_floor', 'vihuela_floor', 'guitarron_floor'], 'violin': ['violin_floor'], 'trumpet': ['trumpet_floor']}[kind]
    a = M('grab_' + kind, 1.5, 'hold')
    for b in floor:
        a.vis(b, True, [(0.5, False)])
    for b in held:
        a.scale(b, [(0, HIDDEN), (0.5 - EPS, HIDDEN), (0.5, [1.1, 1.1, 1.1]), (0.7, SHOWN), (1.5, SHOWN)], 'lin')
    state(a, True, False)
    a.rot('torso', [(0, [0, 0, 0]), (0.4, [36, 0, -18], 'easeInOutSine'), (0.5, [38, 0, -18]), (0.9, [0, 0, 0], 'easeInOutSine'), (1.5, [0, 0, 0])], 'lin')
    end_r, end_l = {'guitar': (GUITAR_R, GUITAR_L), 'violin': (VIOLIN_R, VIOLIN_L), 'trumpet': ([0, 0, 10], [-40, 20, -6])}[kind]
    a.rot('arm_l', [(0, [0, 0, -8]), (0.4, [-50, 0, -40], 'easeInOutSine'), (0.5, [-54, 0, -40]), (1.0, end_l, 'easeInOutSine'), (1.5, end_l)], 'lin')
    a.rot('arm_r', [(0, [0, 0, 8]), (0.5, [0, 0, 8]), (1.0, end_r, 'easeInOutSine'), (1.5, end_r)], 'lin')
    stretch(a, 'arm_l', [(0, 1), (0.4, 1.4), (0.5, 1.4), (1.0, 1.2), (1.5, 1.2)])
    stretch(a, 'arm_r', [(0, 1), (0.5, 1), (1.0, 1.2), (1.5, 1.2)])
    if kind == 'trumpet':
        a.rot('trumpet', [(0, [50, 0, 0]), (1.5, [50, 0, 0])], 'lin')
        a.sound(1.25, 'toot')
    if kind == 'guitar':
        a.sound(1.2, 'strum')
    if kind == 'violin':
        a.sound(1.2, 'violin_tune')
    a.sound(0.5, 'pick')
    return a


def ready(kind):
    """2.0 s loop. Instrument ready, waiting on the singer: a foot tapping, a little sway, the trumpets
    resting their horns, the violins theirs under the arm."""
    a = state(M('ready_' + kind, 2.0, True), True, True)
    if kind == 'guitar':
        guitar_pose(a, 0)
    elif kind == 'violin':
        a.rot('arm_l', [(0, [-40, 10, -16])], 'lin')
        a.rot('arm_r', [(0, [-20, -20, 6])], 'lin')
        a.rot('violin', [(0, [40, 0, 0])], 'lin')
    else:
        a.rot('arm_r', [(0, [-30, -20, 6])], 'lin')
        a.rot('arm_l', [(0, [-30, 20, -6])], 'lin')
        a.rot('trumpet', [(0, [50, 0, 0])], 'lin')
    stretch(a, 'arm_l', [(0, 1.2)])
    stretch(a, 'arm_r', [(0, 1.2)])
    a.rot('leg_r', [(0, [0, 0, 0]), (0.4, [0, 0, 0]), (0.5, [-14, 0, 0]), (0.6, [0, 0, 0]), (1.4, [0, 0, 0]), (1.5, [-14, 0, 0]),
                    (1.6, [0, 0, 0]), (2.0, [0, 0, 0])], 'lin')
    a.rot('torso', [(0, [0, 0, -2]), (1.0, [0, 0, 2]), (2.0, [0, 0, -2])])
    a.sound(0.55, 'tap').sound(1.55, 'tap')
    return a


def count():
    """4.0 s (the singer). Steps up to the microphone: "¡Uno...! ¡...dos!" (0.2 / 1.15), then on the
    beat "¡uno, dos, tres!" (2.1 / 2.55 / 3.05), a stomp on four (3.5), and the downbeat: the first
    strum (4.0)."""
    a = state(M('count', 4.0, 'hold'), True, True, shades=True)
    counts = [0.2, 1.15, 2.1, 2.55, 3.05]
    keys = [(0, [-30, 26, -10])]
    for c in counts:
        keys += [(round(c - 0.12, 2), [-168, 0, -12], 'easeOutQuad'), (c, [-140, 0, -14], 'easeOutBack'), (round(c + 0.18, 2), [-150, 0, -12])]
    keys += [(3.4, [-120, 0, -12]), (3.75, GUITAR_L, 'easeInOutSine'), (4.0, GUITAR_L)]
    a.rot('arm_l', keys, 'lin')
    stretch(a, 'arm_l', [(0, 1.2), (0.1, 1.4), (3.4, 1.4), (3.75, 1.2)])
    a.rot('arm_r', [(0, GUITAR_R), (3.6, GUITAR_R), (3.85, [-70, -30, 10], 'easeOutQuad'), (4.0, [-36, -50, -4], 'easeInQuad')], 'lin')
    stretch(a, 'arm_r', [(0, 1.2)])
    a.rot('leg_r', [(0, [0, 0, 0])] + [k for c in counts + [3.5] for k in
                                      ((round(c - 0.1, 2), [-16, 0, 0]), (c, [0, 0, 0], 'easeInQuad'))] + [(4.0, [0, 0, 0])], 'lin')
    a.pos('root', [(0, [0, 0, 0])] + [k for c in counts + [3.5] for k in
                                     ((round(c - 0.08, 2), [0, 0.8, 0]), (c, [0, 0, 0], 'easeInQuad'))] + [(4.0, [0, 0, 0])], 'lin')
    a.rot('torso', [(0, [0, 0, 0]), (0.2, [-6, 0, 0]), (2.0, [-4, 0, 0]), (3.6, [-2, 0, 0]), (3.85, [-10, 0, 0]), (4.0, [6, 0, 0], 'easeInQuad')], 'lin')
    words = [(0.2, 2.4), (1.15, 2.0), (2.1, 2.2), (2.55, 1.8), (3.05, 2.6)]
    mk = [(0, 0)]
    for t, o in words:
        mk += [(round(t - 0.05, 2), 0.2), (t, o, 'easeOutQuad'), (round(t + 0.22, 2), 0.3)]
    mouth(a, mk + [(4.0, 0)])
    for t, _ in words:
        a.sound(t, 'count')
    for b, s in (('brow_r', 1), ('brow_l', -1)):
        a.pos(b, [(0, [0, 0.6, 0]), (3.0, [0, 0.9, 0]), (4.0, [0, 0.4, 0])])
    return a


def play(kind):
    """A bar-long loop: the pose, swaying side to side on the half bar. The music on top is code."""
    a = state(M('play_' + kind, BAR, True), True, True, shades=None)
    half = BAR / 2
    if kind in ('guitar', 'singer'):
        guitar_pose(a, 0)
        stretch(a, 'arm_r', [(0, 1.5)])
        stretch(a, 'arm_l', [(0, 1.6)])
    elif kind == 'violin':
        a.rot('arm_l', [(0, VIOLIN_L)], 'lin')
        a.rot('arm_r', [(0, VIOLIN_R)], 'lin')
        stretch(a, 'arm_l', [(0, 1.3)])
        stretch(a, 'arm_r', [(0, 1.25)])
        a.rot('violin', [(0, [0, 0, 0])], 'lin')
    elif kind == 'trumpet':
        a.rot('arm_r', [(0, TRUMPET_R)], 'lin')
        a.rot('arm_l', [(0, TRUMPET_L)], 'lin')
        stretch(a, 'arm_r', [(0, 1.9)])
        stretch(a, 'arm_l', [(0, 1.9)])
        a.rot('trumpet', [(0, [-6, 0, 0])], 'lin')
    a.rot('torso', [(0, [0, 0, -3]), (half, [0, 0, 3]), (BAR, [0, 0, -3])])
    a.rot('root', [(0, [0, 4, 0]), (half, [0, -4, 0]), (BAR, [0, 4, 0])])
    # every bone MariachiModel adds the music to is keyed here, so each frame starts from the
    # animation (GeckoLib leaves unkeyed bones as they were, and the additions would pile up)
    a.pos('root', [(0, [0, 0, 0])], 'lin')
    a.rot('leg_r', [(0, [0, 0, 0])], 'lin')
    a.pos('lip_low', [(0, [0, 0, 0])], 'lin')
    a.scale('mouth_in', [(0, [1, 0.001, 1])], 'lin')
    a.scale('mouth', [(0, [1, 1, 1])], 'lin')
    for b in ('brow_r', 'brow_l'):
        a.pos(b, [(0, [0, 0, 0])], 'lin')
    if 'trumpet' not in a.bones or 'rotation' not in a.bones['trumpet']:
        a.rot('trumpet', [(0, [0, 0, 0])], 'lin')
    return a


def bow():
    """2.5 s. The last chord rings out: a deep bow, instrument and all, and he stays down."""
    a = state(M('bow', 2.5, 'hold'), True, True)
    a.rot('torso', [(0, [0, 0, 0]), (0.6, [42, 0, 0], 'easeInOutSine'), (2.5, [40, 0, 0])], 'lin')
    a.rot('arm_r', [(0, [-30, -20, 6]), (0.6, [-10, 0, 20], 'easeInOutSine'), (2.5, [-10, 0, 20])], 'lin')
    a.rot('arm_l', [(0, [-30, 20, -6]), (0.6, [-10, 0, -20], 'easeInOutSine'), (2.5, [-10, 0, -20])], 'lin')
    for b, s in (('brow_r', 1), ('brow_l', -1)):
        a.rot(b, [(0, [0, 0, 0]), (0.6, [0, 0, -10 * s])])
    return a


ALL = [sleep_a, sleep_b, sleep_c, startle, getup, resleep, jacket, hat, shades,
       lambda: grab('guitar'), lambda: grab('violin'), lambda: grab('trumpet'),
       lambda: ready('guitar'), lambda: ready('violin'), lambda: ready('trumpet'),
       count, lambda: play('guitar'), lambda: play('singer'), lambda: play('violin'), lambda: play('trumpet'), bow]


def main():
    data = {'format_version': '1.8.0', 'animations': {}}
    for fn in ALL:
        a = fn()
        data['animations']['animation.mariachi.' + a.name] = guard_brows(a.compile())
    with open(OUT, 'w', newline='\n') as f:
        json.dump(data, f, indent='\t')
    print(OUT, len(data['animations']), 'animations')


if __name__ == '__main__':
    main()
