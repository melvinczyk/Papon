#!/usr/bin/env python3
"""Generates the mariachi band's animations: assets/papon/animations/entity/mariachi.animation.json.

Conventions are the rest of the mod's (gen_anims.py): rot X < 0 swings a limb forward and tilts the
body back; arm_r Z > 0 / arm_l Z < 0 raise an arm outward; arm_r Y < 0 / arm_l Y > 0 swing it inward.
The arms are split (gen_vendor.rig_hands): reaches stretch `arm_*_mesh` and slide `hand_*`.

Every animation says outright what is shown: the suit pieces they put on (`jacket`, sleeves,
`sombrero`, `shades`) or their places off them (`jacket_chair` / `jacket_floor`, `sombrero_floor`), each instrument in
hand or on the floor (the renderer hides every instrument but the member's own), the eyelids.

The playing loops are only the pose and a gentle sway: client/MariachiModel lays the music over them
from the song's own data (assets/papon/song/cancion.json): the beat (foot taps, bobbing), the guitar
strums, the bowing, and the singer's lips (Rhubarb shapes), brows (his pitch) and sway.

Hand poses on the instruments (the bowing arm, the trumpet valves, the requinto, the coro's hand
mic) are solved on the rig by band_ik.py; the play loops key them, and they are written to
assets/papon/song/band_poses.json for MariachiModel to blend between in time with the music.

Only Gordo (the guitarron, the dozer) has a chair. Everyone else sleeps sitting on the stage floor, his
jacket folded behind him (`jacket_floor`): the animations that start in the chair are written twice,
as themselves and as `<name>_floor` (FLOOR_ROOT/FLOOR_LEG_*, the jacket picked up off the floor);
boss/MariachiEntity picks the floor ones for everyone but Gordo.

Lengths of one-shots = BandState durations in Java:
  startle 1.0, getup 3.0, resleep 2.0, jacket 2.5, hat 1.5, shades 1.0, grab_* 1.5, count 4.0
  (the singer: "uno" 0.2, "dos" 1.15, "uno, dos, tres" 2.1 / 2.55 / 3.05, the downbeat at 4.0),
  bow 2.5, whacked 2.5 (the singer, hit on the head: up in the air 0-0.9), jacket_slow 4.5,
  hat_slow 2.5, robbed 2.0, catch 1.5; loops: sleep_a/b/c 3.0, ready_* 2.0, play_* 1.88 (a bar of the song),
  scared 1.0, awed 3.48 (two bars of Para Siempre), nervous_a / nervous_b 2.4;
  phase four: jaw_drop 0.6 (then the loops shocked_a / shocked_b 2.4), hype_* 3.2
"""
import json
import os

from gen_anims import guard_brows, EPS, Anim
from gen_boss_anims import stretch as boss_stretch
import band_ik
from gen_npc_anims import osc

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'src/main/resources/assets/papon/animations/entity/mariachi.animation.json')
POSES_OUT = os.path.join(ROOT, 'src/main/resources/assets/papon/song/band_poses.json')
SHOWN, HIDDEN = [1, 1, 1], [0, 0, 0]

DRESSED = ['jacket', 'sleeve_r', 'sleeve_l', 'sombrero']
UNDRESSED = ['jacket_chair', 'sombrero_floor']
# where the jacket waits while he sleeps: on the chair's back, or (ON_FLOOR) folded on the floor behind him
JACKET_OFF = 'jacket_chair'
ON_FLOOR = False
GUITARS = ['guitar', 'vihuela', 'guitarron', 'requinto']
HELD = GUITARS + ['trumpet', 'violin', 'bow', 'maraca', 'hand_mic']
FLOOR = ['guitar_floor', 'vihuela_floor', 'guitarron_floor', 'trumpet_floor', 'violin_floor', 'requinto_floor',
         'maraca_floor', 'hand_mic_floor']
POSES = band_ik.all_poses()

SEAT_ROOT = [0, 3, 0]
SEAT_LEG_R = [-80, 8, 0]
SEAT_LEG_L = [-80, -8, 0]
# sitting on the stage floor, legs out in front (the crowd's seated pose, client/ColiseumCrowdRenderer)
FLOOR_ROOT = [0, -4, 0]
FLOOR_LEG_R = [-88, 8, 0]
FLOOR_LEG_L = [-88, -8, 0]
BAR = 1.882          # a bar of the song (127.5 bpm)


# Bones client/MariachiModel sets while the band plays, at their rest values: every animation keys
# whichever of these it leaves alone, so no member's pose is left over on the shared model for the
# next one drawn (GeckoLib leaves unkeyed bones as they were).
DEFAULTS = {
    'rotation': {b: [0, 0, 0] for b in ('arm_r', 'arm_l', 'bow', 'violin', 'trumpet', 'body', 'torso')},
    'position': {b: [0, 0, 0] for b in ('hand_r', 'hand_l', 'valve_1', 'valve_2', 'valve_3', 'lip_low', 'brow_r', 'brow_l', 'root')},
    'scale': {'arm_r_mesh': [1, 1, 1], 'arm_l_mesh': [1, 1, 1], 'mouth': [1, 1, 1], 'mouth_in': [1, 0.001, 1]},
}


class M(Anim):
    def compile(self):
        for chan, bones in DEFAULTS.items():
            for b, v in bones.items():
                if chan not in self.bones.get(b, {}):
                    self._chan(b, chan, [(0, v)], 'lin')
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


def girth(k):
    """A stretched arm thins out (MariachiModel uses the same rule)."""
    return max(0.55, min(1.0, 1 - (k - 1) * 0.26))


def stretch(a, arm, keys):
    a.scale(arm + '_mesh', [(t, [girth(k), k, girth(k)]) + tuple(rest) for t, k, *rest in keys], 'lin')
    a.pos('hand_' + arm[-1], [(t, [0, -(k - 1) * 6.5, 0]) + tuple(rest) for t, k, *rest in keys], 'lin')


def show(a, bones, on, t=0):
    for b in bones:
        if b not in a.bones or 'scale' not in a.bones[b]:
            a.scale(b, [(t, SHOWN if on else HIDDEN)], 'lin')


def state(a, dressed, held, shades=None, lids=False):
    """What is on and what is off for the whole animation (keys a bone only if the animation has not)."""
    show(a, DRESSED, dressed)
    show(a, [JACKET_OFF, 'sombrero_floor'], not dressed)
    show(a, ['jacket_floor' if JACKET_OFF == 'jacket_chair' else 'jacket_chair'], False)
    show(a, HELD, held)
    show(a, FLOOR, not held)
    show(a, ['shades'], dressed if shades is None else shades)
    show(a, ['eyelids'], lids)
    # the teeth and tongue only ever show in the lip sync, the cheeks only when a trumpet blows (client/MariachiModel)
    show(a, ['teeth', 'tongue', 'cheek_r', 'cheek_l'], False)
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
    a.vis(JACKET_OFF, True, [(0.6, False)])
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
    bend = [(0.45, [44, 0, 0], 'easeInOutSine'), (0.65, [46, 0, 0]), (1.0, [0, 0, 0], 'easeInOutSine')] if ON_FLOOR else []
    a.rot('torso', [(0, [0, 0, 0])] + bend + [(1.65, [0, 0, 0]), (1.75, [-6, 0, 0]), (1.95, [-6, 0, 0]), (2.2, [0, 0, 0])])
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

def arm(a, side, pose, t0=0, t1=None):
    """Keys a solved arm pose (band_ik: rot + stretch) at t0 (and holds it to t1)."""
    keys = [(t0, pose['rot'])] + ([(t1, pose['rot'])] if t1 is not None else [])
    a.rot('arm_' + side, keys, 'lin')
    stretch(a, 'arm_' + side, [(t0, pose['k'])] + ([(t1, pose['k'])] if t1 is not None else []))


REQ = POSES['requinto']
BOW_MID = POSES['bow'][2]
TRUMPET_PLAY = POSES['trumpet']['play']
TRUMPET_DOWN = POSES['trumpet']['down']
CORO = POSES['coro']
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
    held = {'guitar': GUITARS, 'violin': ['violin', 'bow'], 'trumpet': ['trumpet'], 'coro': ['maraca', 'hand_mic']}[kind]
    floor = {'guitar': ['guitar_floor', 'vihuela_floor', 'guitarron_floor', 'requinto_floor'], 'violin': ['violin_floor'],
             'trumpet': ['trumpet_floor'], 'coro': ['maraca_floor', 'hand_mic_floor']}[kind]
    a = M('grab_' + kind, 1.5, 'hold')
    for b in floor:
        a.vis(b, True, [(0.5, False)])
    for b in held:
        a.scale(b, [(0, HIDDEN), (0.5 - EPS, HIDDEN), (0.5, [1.1, 1.1, 1.1]), (0.7, SHOWN), (1.5, SHOWN)], 'lin')
    state(a, True, False)
    a.rot('torso', [(0, [0, 0, 0]), (0.4, [36, 0, -18], 'easeInOutSine'), (0.5, [38, 0, -18]), (0.9, [0, 0, 0], 'easeInOutSine'), (1.5, [0, 0, 0])], 'lin')
    end_r, end_l = {'guitar': (GUITAR_R, GUITAR_L), 'violin': (VIOLIN_R, VIOLIN_L), 'trumpet': ([0, 0, 10], [-40, 20, -6]),
                    'coro': ([-30, -10, 10], CORO['down']['rot'])}[kind]
    a.rot('arm_l', [(0, [0, 0, -8]), (0.4, [-50, 0, -40], 'easeInOutSine'), (0.5, [-54, 0, -40]), (1.0, end_l, 'easeInOutSine'), (1.5, end_l)], 'lin')
    a.rot('arm_r', [(0, [0, 0, 8]), (0.5, [0, 0, 8]), (1.0, end_r, 'easeInOutSine'), (1.5, end_r)], 'lin')
    stretch(a, 'arm_l', [(0, 1), (0.4, 1.4), (0.5, 1.4), (1.0, 1.2), (1.5, 1.2)])
    stretch(a, 'arm_r', [(0, 1), (0.5, 1), (1.0, 1.2), (1.5, 1.2)])
    if kind == 'trumpet':
        a.rot('trumpet', [(0, [50, 0, 0]), (1.5, [50, 0, 0])], 'lin')
        a.sound(1.25, 'toot')
    if kind == 'guitar':
        a.sound(1.2, 'strum')
    if kind == 'coro':
        a.sound(1.2, 'maraca')
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
    elif kind == 'coro':
        a.rot('arm_r', [(0, [-30, -10, 10]), (0.5, [-36, -10, 10]), (1.0, [-30, -10, 10]), (1.5, [-36, -10, 10]), (2.0, [-30, -10, 10])])
        a.rot('arm_l', [(0, CORO['down']['rot'])], 'lin')
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


SINGER_VOICE = os.path.join(ROOT, 'src/main/resources/assets/papon/voice/singer.json')


def count():
    """4.0 s (the singer). Steps up to the microphone and throws his fist up over his head, leaning back for the breath;
    then "¡Un, dos, tres, cuatro!" in his own voice (tools/gen_singer_voice.py: sounds/voice/band/count.ogg, played 2.0 s
    in), one word on each of the four beats before the downbeat: a fist pump and a stomp on every number, the whole of him
    thrown into "¡CUATRO!", and his hand down onto the requinto for the first strum (4.0). The mouth follows the
    recording's loudness (voice/singer.json)."""
    voice = json.load(open(SINGER_VOICE, encoding='utf-8'))['count']
    words = voice['words']
    rate = voice['rate']
    env = voice['env']
    a = state(M('count', 4.0, 'hold'), True, True, shades=True)
    up = [-172, 0, -10]
    keys = [(0, [-30, 26, -10]), (0.5, [-40, 20, -12]), (1.1, up, 'easeOutBack'), (1.7, [-178, 0, -8])]
    for i, c in enumerate(words):
        last = i == len(words) - 1
        keys += [(round(c - 0.1, 2), [-178, 0, -8], 'easeOutQuad'), (round(c, 2), [-132 if not last else -158, 0, -16], 'easeOutBack'),
                 (round(c + 0.16, 2), [-160 if not last else -176, 0, -10])]
    keys += [(3.72, [-150, 0, -12]), (3.86, REQ['low']['rot'], 'easeInOutSine'), (4.0, REQ['low']['rot'])]
    a.rot('arm_l', keys, 'lin')
    stretch(a, 'arm_l', [(0, 1.2), (1.1, 1.45), (3.72, 1.45), (3.86, REQ['low']['k'])])
    sr = REQ['strum']['rot']
    a.rot('arm_r', [(0, sr), (3.7, sr), (3.88, [sr[0] - 18, sr[1], sr[2]], 'easeOutQuad'), (4.0, [sr[0] + 10, sr[1], sr[2]], 'easeInQuad')], 'lin')
    stretch(a, 'arm_r', [(0, REQ['strum']['k'])])
    stomps = [0.25, 0.55] + list(words)
    a.rot('leg_r', [(0, [0, 0, 0])] + [k for c in stomps for k in
                                      ((round(c - 0.1, 2), [-18, 0, 0]), (round(c, 2), [0, 0, 0], 'easeInQuad'))] + [(4.0, [0, 0, 0])], 'lin')
    a.pos('root', [(0, [0, 0, 0])] + [k for c in stomps for k in
                                     ((round(c - 0.08, 2), [0, 0.9, 0]), (round(c, 2), [0, 0, 0], 'easeInQuad'))] + [(4.0, [0, 0, 0])], 'lin')
    # back for the breath, into each number, and the whole of him into the fourth
    tk = [(0, [0, 0, 0]), (0.6, [-2, 0, 0]), (1.2, [9, 0, 0], 'easeOutQuad'), (1.85, [10, 0, 0])]
    for i, c in enumerate(words):
        last = i == len(words) - 1
        tk += [(round(c - 0.06, 2), [6, 0, 0]), (round(c + 0.08, 2), [-14 if last else -7, 0, -4 if i % 2 else 4], 'easeOutQuad')]
    tk += [(3.72, [-12, 0, 0]), (3.88, [-10, 0, 0]), (4.0, [6, 0, 0], 'easeInQuad')]
    a.rot('torso', tk, 'lin')
    a.scale('body', [(0, [1, 1, 1]), (1.2, [1.04, 1.05, 1.04], 'easeOutQuad'), (1.9, [1.05, 1.06, 1.05]), (round(words[0], 2), [1, 1, 1]),
                     (4.0, [1, 1, 1])], 'lin')
    # the mouth: the recording's loudness, from the breath before the first word to the downbeat
    mk = [(0, 0), (1.15, 0.9, 'easeOutQuad'), (1.6, 0.3)]
    t0 = 1.9
    n = int((4.0 - t0) * rate)
    for k in range(n + 1):
        t = round(t0 + k / rate, 2)
        i = int(round(t * rate))
        o = env[i] if i < len(env) else 0.0
        mk.append((t, round(min(2.6, 2.8 * o), 2)))
    mouth(a, mk)
    for c in words:
        a.sound(round(c, 2), 'count')
    for b, sg in (('brow_r', 1), ('brow_l', -1)):
        a.pos(b, [(0, [0, 0.6, 0]), (1.2, [0, 1.1, 0]), (round(words[-1], 2), [0, 1.3, 0]), (4.0, [0, 0.5, 0])])
    return a


def play(kind):
    """A bar-long loop: the pose, swaying side to side on the half bar. The music on top is code."""
    a = state(M('play_' + kind, BAR, True), True, True, shades=None)
    half = BAR / 2
    if kind == 'guitar':
        guitar_pose(a, 0)
        stretch(a, 'arm_r', [(0, 1.5)])
        stretch(a, 'arm_l', [(0, 1.6)])
    elif kind == 'singer':
        arm(a, 'r', REQ['strum'])
        arm(a, 'l', REQ['low'])
    elif kind == 'violin':
        arm(a, 'l', POSES['violin_l']['low'])
        arm(a, 'r', BOW_MID)
        a.rot('bow', [(0, BOW_MID['prop'])], 'lin')
        a.rot('violin', [(0, [0, 0, 0])], 'lin')
    elif kind == 'trumpet':
        arm(a, 'r', TRUMPET_PLAY['r'])
        arm(a, 'l', TRUMPET_PLAY['l'])
        a.rot('trumpet', [(0, [TRUMPET_PLAY['trumpet'], 0, 0])], 'lin')
        for v in ('valve_1', 'valve_2', 'valve_3'):
            a.pos(v, [(0, [0, 0, 0])], 'lin')
        for c in ('cheek_r', 'cheek_l'):
            a.scale(c, [(0, [0, 0, 0])], 'lin')
    elif kind == 'coro':
        arm(a, 'l', CORO['down'])
        a.rot('arm_r', [(0, [-40, -10, 12])], 'lin')
    a.rot('torso', [(0, [0, 0, -3]), (half, [0, 0, 3]), (BAR, [0, 0, -3])])
    a.rot('root', [(0, [0, 4, 0]), (half, [0, -4, 0]), (BAR, [0, 4, 0])])
    # everything MariachiModel sets outright each frame is keyed too (a single key: the rest pose)
    for b in ('arm_r', 'arm_l'):
        if b not in a.bones or 'rotation' not in a.bones[b]:
            a.rot(b, [(0, [0, 0, 0])], 'lin')
    for b in ('arm_r_mesh', 'arm_l_mesh'):
        if b not in a.bones or 'scale' not in a.bones[b]:
            a.scale(b, [(0, [1, 1, 1])], 'lin')
    for b in ('hand_r', 'hand_l'):
        if b not in a.bones or 'position' not in a.bones[b]:
            a.pos(b, [(0, [0, 0, 0])], 'lin')
    for b in ('bow', 'violin', 'body'):
        if b not in a.bones or 'rotation' not in a.bones[b]:
            a.rot(b, [(0, [0, 0, 0])], 'lin')
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


def whacked():
    """2.5 s (the singer). The boss's spatula on his sombrero-less head: squashed flat (0.0), then up
    out of his chair into the air (the animation flies him, so the chair stays put: up to 1.4 blocks
    at 0.42 s) with half a turn and his arms and legs everywhere, down on his feet in front of the
    chair (0.9) with a stagger; a hand to the bump, a look at the boss (the entity faces the front),
    and a long, unimpressed blink."""
    a = state(M('whacked', 2.5, 'hold'), False, False)
    a.vis('eyelids', True, [(0.04, False), (1.9, True), (2.15, False)])
    a.pos('root', [(0, SEAT_ROOT), (0.06, [0, 1.0, 0], 'easeOutQuad'), (0.42, [0, 22, 0], 'easeOutQuad'),
                   (0.88, [0, 0, -3], 'easeInQuad'), (1.0, [0, -0.8, -3]), (1.15, [0, 0, -3], 'easeOutQuad'), (2.5, [0, 0, -3])], 'lin')
    a.rot('root', [(0, [0, 0, 0]), (0.06, [0, 0, 0]), (0.5, [-14, 200, 10], 'easeOutQuad'), (0.88, [0, 360, 0], 'easeInQuad'),
                   (1.15, [0, 360, -6]), (1.4, [0, 360, 3]), (1.6, [0, 360, 0]), (2.5, [0, 360, 0])], 'lin')
    a.scale('body', [(0, [1.25, 0.72, 1.25]), (0.06, [1.25, 0.72, 1.25]), (0.16, [0.88, 1.18, 0.88], 'easeOutQuad'),
                     (0.4, [1, 1, 1]), (0.88, [1, 1, 1]), (0.96, [1.16, 0.84, 1.16], 'easeOutQuad'), (1.15, [0.98, 1.03, 0.98]),
                     (1.35, [1, 1, 1]), (2.5, [1, 1, 1])], 'lin')
    a.rot('leg_r', [(0, SEAT_LEG_R), (0.1, [-20, 0, 20]), (0.3, [30, 0, 30]), (0.5, [-40, 0, 14]), (0.8, [-10, 0, 6]),
                    (0.95, [0, 0, 6]), (1.2, [0, 0, 0]), (2.5, [0, 0, 0])])
    a.rot('leg_l', [(0, SEAT_LEG_L), (0.1, [30, 0, -24]), (0.3, [-40, 0, -30]), (0.5, [24, 0, -14]), (0.8, [-6, 0, -6]),
                    (0.95, [0, 0, -6]), (1.2, [0, 0, 0]), (2.5, [0, 0, 0])])
    a.rot('arm_r', [(0, [10, 0, 14]), (0.08, [-30, 0, 120], 'easeOutExpo'), (0.3, [-150, 0, 60]), (0.5, [-40, 0, 140]),
                    (0.8, [-20, 0, 70]), (1.0, [0, 0, 40]), (1.3, [-20, 0, 12]), (1.45, [-160, -40, 0], 'easeInOutSine'),
                    (1.6, [-170, -30, 4]), (1.75, [-160, -40, 0]), (1.9, [-170, -30, 4]), (2.2, [0, 0, 8], 'easeInOutSine'), (2.5, [0, 0, 8])], 'lin')
    a.rot('arm_l', [(0, [10, 0, -14]), (0.08, [-20, 0, -110], 'easeOutExpo'), (0.3, [-40, 0, -150]), (0.5, [-150, 0, -50]),
                    (0.8, [-30, 0, -70]), (1.0, [0, 0, -40]), (1.3, [-6, 0, -12]), (2.5, [0, 0, -8])], 'lin')
    stretch(a, 'arm_r', [(0, 1), (1.3, 1), (1.45, 1.55), (1.9, 1.55), (2.2, 1)])
    a.rot('torso', [(0, [-14, 0, 0]), (0.06, [6, 0, 0]), (0.3, [-20, 0, 0]), (0.8, [10, 0, 0]), (0.96, [16, 0, 0]),
                    (1.2, [-4, 0, 0]), (1.5, [6, 0, 0]), (2.5, [4, 0, 0])])
    for b, sgn in (('brow_r', 1), ('brow_l', -1)):
        a.pos(b, [(0, [0, 1.2, 0]), (0.9, [0, 1.0, 0]), (1.4, [0, -0.6, 0]), (2.5, [0, -0.6, 0])])
        a.rot(b, [(0, [0, 0, 0]), (1.3, [0, 0, 0]), (1.5, [0, 0, 14 * sgn]), (2.5, [0, 0, 14 * sgn])])
    mouth(a, [(0, 0.9), (0.06, 3.2, 'easeOutExpo'), (0.6, 2.8), (0.95, 1.4), (1.2, 0.2), (1.5, 0.6), (1.7, 0.1), (2.5, 0)])
    a.sound(0.04, 'bonk').sound(0.1, 'yelp').sound(0.92, 'thud').sound(1.55, 'grumble')
    return a


def jacket_slow():
    """4.5 s (the singer, under protest). A huge sigh, shoulders down; drags the jacket off the chair
    (it trails along the ground), shoves an arm in, then the other, with a roll of the eyes, and gives
    the lapels one tug: fine. Whatever."""
    a = state(M('jacket_slow', 4.5, 'hold'), False, False)
    a.vis(JACKET_OFF, True, [(1.5, False)])
    a.scale('jacket', [(0, HIDDEN), (2.3 - EPS, HIDDEN), (2.3, [1.2, 0.6, 1.2]), (2.7, [0.97, 1.04, 0.97], 'easeOutBack'), (3.0, SHOWN), (4.5, SHOWN)], 'lin')
    a.scale('sleeve_r', [(0, HIDDEN), (2.3 - EPS, HIDDEN), (2.3, [1.2, 0.3, 1.2]), (2.7, SHOWN, 'easeOutBack'), (4.5, SHOWN)], 'lin')
    a.scale('sleeve_l', [(0, HIDDEN), (3.0 - EPS, HIDDEN), (3.0, [1.2, 0.3, 1.2]), (3.4, SHOWN, 'easeOutBack'), (4.5, SHOWN)], 'lin')
    # the sigh: up, and all the way down
    a.scale('body', [(0, [1, 1, 1]), (0.5, [1.04, 1.06, 1.04], 'easeInOutSine'), (1.1, [1.03, 0.9, 1.03], 'easeInOutSine'), (1.4, [1, 0.94, 1]),
                     (3.8, [1, 0.96, 1]), (4.1, [1.02, 1.02, 1.02], 'easeOutQuad'), (4.5, [1, 1, 1])])
    dip = [(1.45, [40, 0, 0], 'easeInOutSine'), (1.6, [42, 0, 0]), (2.0, [10, 0, 0], 'easeInOutSine')] if ON_FLOOR else [(1.4, [10, 0, 0])]
    a.rot('torso', [(0, [0, 0, 0]), (0.5, [-6, 0, 0]), (1.1, [14, 0, 0], 'easeInOutSine')] + dip + [(3.8, [8, 0, 0]),
                    (4.1, [-4, 0, 0]), (4.5, [0, 0, 0])])
    a.rot('root', [(0, [0, 0, 0]), (1.1, [0, 0, 0]), (1.45, [0, 150, 0], 'easeInOutSine'), (1.6, [0, 150, 0]), (2.0, [0, 0, 0], 'easeInOutSine'), (4.5, [0, 0, 0])], 'lin')
    a.rot('arm_r', [(0, [0, 0, 6]), (1.1, [10, 0, 4]), (1.45, [-60, 0, 8], 'easeInOutSine'), (1.6, [-66, 0, 8]), (2.0, [20, 0, 20]),
                    (2.3, [-30, 0, 70], 'easeInOutSine'), (2.6, [10, 0, 20]), (3.8, [0, 0, 6]), (3.95, [-80, -40, 0], 'easeInOutSine'),
                    (4.15, [-86, -36, 0]), (4.5, [0, 0, 6], 'easeInOutSine')], 'lin')
    a.rot('arm_l', [(0, [0, 0, -6]), (1.1, [10, 0, -4]), (2.6, [10, 0, -4]), (3.0, [-30, 0, -70], 'easeInOutSine'), (3.3, [10, 0, -20]),
                    (3.8, [0, 0, -6]), (3.95, [-80, 40, 0], 'easeInOutSine'), (4.15, [-86, 36, 0]), (4.5, [0, 0, -6], 'easeInOutSine')], 'lin')
    stretch(a, 'arm_r', [(0, 1), (1.45, 1.4), (1.6, 1.4), (2.0, 1), (3.8, 1), (3.95, 1.3), (4.15, 1.3), (4.5, 1)])
    stretch(a, 'arm_l', [(0, 1), (3.8, 1), (3.95, 1.3), (4.15, 1.3), (4.5, 1)])
    for b, sgn in (('brow_r', 1), ('brow_l', -1)):
        a.pos(b, [(0, [0, -0.5, 0]), (2.9, [0, -0.5, 0]), (3.1, [0, 1.0, 0]), (3.45, [0, 1.0, 0]), (3.7, [0, -0.5, 0]), (4.5, [0, -0.3, 0])])
        a.rot(b, [(0, [0, 0, 10 * sgn]), (2.9, [0, 0, 10 * sgn]), (3.1, [0, 0, -4 * sgn]), (3.7, [0, 0, 10 * sgn]), (4.5, [0, 0, 6 * sgn])])
    a.vis('eyelids', False, [(0.6, True), (1.0, False), (3.1, True), (3.4, False)])
    mouth(a, [(0, 0), (0.45, 0.6), (1.1, 1.6, 'easeOutQuad'), (1.4, 0.2), (3.0, 0), (3.2, 0.6), (3.4, 0), (4.5, 0)])
    a.sound(0.5, 'sigh').sound(1.5, 'cloth').sound(2.4, 'cloth').sound(3.1, 'cloth').sound(4.0, 'flex')
    return a


def hat_slow():
    """2.5 s (the singer, still sulking). Scoops the sombrero off the floor without bending much,
    drops it on his head, and pushes the brim up off his eyes with one finger."""
    a = state(M('hat_slow', 2.5, 'hold'), True, False, shades=False)
    a.vis('sombrero_floor', True, [(0.7, False)])
    a.scale('sombrero', [(0, HIDDEN), (1.35 - EPS, HIDDEN), (1.35, SHOWN), (2.5, SHOWN)], 'lin')
    a.pos('sombrero', [(0, [0, 0, 0]), (1.35, [0, 3, -1]), (1.5, [0, -1.2, -1.5], 'easeInQuad'), (1.6, [0, -1.0, -1.5]),
                       (2.0, [0, -1.0, -1.5]), (2.25, [0, 0, 0], 'easeOutBack'), (2.5, [0, 0, 0])], 'lin')
    a.rot('sombrero', [(0, [0, 0, 0]), (1.5, [10, 0, 6]), (2.0, [10, 0, 6]), (2.25, [-4, 0, 3], 'easeOutBack'), (2.5, [-4, 0, 3])], 'lin')
    a.rot('torso', [(0, [8, 0, 0]), (0.55, [26, 0, 16], 'easeInOutSine'), (0.75, [28, 0, 16]), (1.1, [6, 0, 0], 'easeInOutSine'), (2.5, [4, 0, 0])], 'lin')
    a.rot('arm_r', [(0, [0, 0, 8]), (0.55, [-40, 0, 40], 'easeInOutSine'), (0.75, [-44, 0, 40]), (1.3, [-175, 0, 20], 'easeInOutSine'),
                    (1.45, [-170, 0, 22]), (1.7, [0, 0, 8], 'easeInOutSine'), (2.5, [0, 0, 8])], 'lin')
    stretch(a, 'arm_r', [(0, 1), (0.55, 1.45), (0.75, 1.45), (1.3, 1.5), (1.45, 1.5), (1.7, 1)])
    a.rot('arm_l', [(0, [0, 0, -8]), (1.8, [0, 0, -8]), (2.05, [-168, 12, 0], 'easeInOutSine'), (2.25, [-172, 12, 0]), (2.5, [0, 0, -8], 'easeInOutSine')], 'lin')
    stretch(a, 'arm_l', [(0, 1), (1.8, 1), (2.05, 1.7), (2.25, 1.7), (2.5, 1)])
    for b, sgn in (('brow_r', 1), ('brow_l', -1)):
        a.pos(b, [(0, [0, -0.4, 0]), (2.5, [0, -0.4, 0])])
        a.rot(b, [(0, [0, 0, 8 * sgn]), (2.5, [0, 0, 8 * sgn])])
    a.sound(0.7, 'pick').sound(1.5, 'cloth')
    return a


# ===================================================================== phase three

def scared():
    """1.0 s loop. Cowering from the boss: down low, shoulders up, the forearms over the head, the instrument
    hugged in (whatever is strapped on stays where it is), trembling; brows up, mouth open."""
    a = state(M('scared', 1.0, True), True, True)
    a.pos('root', [(0, [0, -1.6, 0])], 'lin')
    a.rot('torso', osc(0, 1.0, 0.05, [24, 0, -1.5], [24, 0, 1.5]), 'lin')
    a.rot('arm_r', [(0, [-150, -40, 10])], 'lin')
    a.rot('arm_l', [(0, [-150, 40, -10])], 'lin')
    stretch(a, 'arm_r', [(0, 1.2)])
    stretch(a, 'arm_l', [(0, 1.2)])
    a.rot('leg_r', [(0, [-30, 0, 8])], 'lin')
    a.rot('leg_l', [(0, [-30, 0, -8])], 'lin')
    a.scale('body', osc(0, 1.0, 0.1, [1.0, 0.94, 1.0], [1.02, 0.92, 1.02]), 'lin')
    for b, s_ in (('brow_r', 1), ('brow_l', -1)):
        a.pos(b, [(0, [0, 1.1, 0])], 'lin')
        a.rot(b, [(0, [0, 0, -14 * s_])], 'lin')
    mouth(a, [(0, 1.2), (0.5, 1.5), (1.0, 1.2)])
    return a


def robbed():
    """2.0 s (the singer). His requinto torn out of his hands: spun half round by it (0-0.4), stumbles; looks down
    at his empty hands, held out (0.6-1.3), then up at the boss, and down he goes into a cower."""
    a = state(M('robbed', 2.0, 'hold'), True, True, shades=True)     # the renderer leaves his requinto off
    a.scale('requinto', [(0, HIDDEN)], 'lin')
    a.rot('root', [(0, [0, 0, 0]), (0.1, [0, -70, -6], 'easeOutQuad'), (0.4, [0, 20, 4], 'easeOutBack'), (0.6, [0, 0, 0]), (2.0, [0, 0, 0])], 'lin')
    a.pos('root', [(0, [0, 0, 0]), (0.1, [0, 0.8, 0]), (0.4, [0, 0, 1.0]), (1.4, [0, 0, 1.0]), (1.8, [0, -1.6, 1.0], 'easeInOutSine'), (2.0, [0, -1.6, 1.0])], 'lin')
    for arm, sgn in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, REQ['strum']['rot'] if sgn > 0 else REQ['low']['rot']), (0.1, [-120, 0, 40 * sgn], 'easeOutQuad'),
                    (0.4, [-40, 0, 30 * sgn]), (0.6, [-64, 20 * sgn, 10 * sgn], 'easeInOutSine'), (1.3, [-66, 22 * sgn, 10 * sgn]),
                    (1.7, [-150, -40 * sgn, 10 * sgn], 'easeInOutSine'), (2.0, [-150, -40 * sgn, 10 * sgn])], 'lin')
    stretch(a, 'arm_r', [(0, REQ['strum']['k']), (0.4, 1.0), (0.6, 1.1), (1.3, 1.1), (1.7, 1.2), (2.0, 1.2)])
    stretch(a, 'arm_l', [(0, REQ['low']['k']), (0.4, 1.0), (0.6, 1.1), (1.3, 1.1), (1.7, 1.2), (2.0, 1.2)])
    a.rot('torso', [(0, [0, 0, 0]), (0.1, [-12, 0, 0]), (0.4, [10, 0, 0]), (0.6, [22, 0, 0], 'easeInOutSine'), (1.3, [22, 0, 0]),
                    (1.45, [-6, 0, 0], 'easeOutQuad'), (1.8, [24, 0, 0], 'easeInOutSine'), (2.0, [24, 0, 0])], 'lin')
    for b, s_ in (('brow_r', 1), ('brow_l', -1)):
        a.pos(b, [(0, [0, 0, 0]), (0.1, [0, 1.4, 0]), (2.0, [0, 1.1, 0])], 'lin')
        a.rot(b, [(0, [0, 0, 0]), (0.6, [0, 0, -14 * s_]), (2.0, [0, 0, -14 * s_])], 'lin')
    mouth(a, [(0, 0), (0.1, 2.4, 'easeOutQuad'), (0.5, 1.0), (1.45, 2.6, 'easeOutQuad'), (2.0, 1.2)])
    a.sound(0.1, 'yelp').sound(1.45, 'gasp')
    return a


def awed():
    """3.48 s loop (two bars of Para Siempre). The boss is playing in the spotlight and they forget to be
    scared: up out of the crouch, hands clasped under the chin, swaying with it; brows up, mouths a little open."""
    L = 3.48
    a = state(M('awed', L, True), True, True)
    a.rot('torso', [(0, [6, 0, -4]), (L / 2, [6, 0, 4]), (L, [6, 0, -4])])
    a.rot('root', [(0, [0, -4, 0]), (L / 2, [0, 4, 0]), (L, [0, -4, 0])])
    a.rot('arm_r', [(0, [-100, -46, 0])], 'lin')
    a.rot('arm_l', [(0, [-100, 46, 0])], 'lin')
    stretch(a, 'arm_r', [(0, 1.15)])
    stretch(a, 'arm_l', [(0, 1.15)])
    for b, s_ in (('brow_r', 1), ('brow_l', -1)):
        a.pos(b, [(0, [0, 0.8, 0])], 'lin')
        a.rot(b, [(0, [0, 0, -10 * s_])], 'lin')
    mouth(a, [(0, 0.5)])
    return a


def catch():
    """1.5 s (the singer). The requinto comes spinning back at him: hands up, a fumble (it bounces off his
    palms, 0.1-0.5, the prop entity is it), a lunge, and he has it hugged to his chest (0.7); a relieved breath
    and into his playing position."""
    a = state(M('catch', 1.5, 'hold'), True, True, shades=True)
    a.rot('arm_r', [(0, [-150, -20, 20]), (0.15, [-120, -30, 30], 'easeOutQuad'), (0.3, [-160, 0, 10]), (0.45, [-100, -40, 30]),
                    (0.7, [-80, -50, 0], 'easeOutQuad'), (1.1, [-80, -50, 0]), (1.4, REQ['strum']['rot'], 'easeInOutSine'), (1.5, REQ['strum']['rot'])], 'lin')
    a.rot('arm_l', [(0, [-150, 20, -20]), (0.15, [-170, 10, -10], 'easeOutQuad'), (0.3, [-110, 30, -30]), (0.45, [-150, 20, -10]),
                    (0.7, [-80, 50, 0], 'easeOutQuad'), (1.1, [-80, 50, 0]), (1.4, REQ['low']['rot'], 'easeInOutSine'), (1.5, REQ['low']['rot'])], 'lin')
    stretch(a, 'arm_r', [(0, 1.3), (0.7, 1.2), (1.1, 1.2), (1.4, REQ['strum']['k']), (1.5, REQ['strum']['k'])])
    stretch(a, 'arm_l', [(0, 1.3), (0.7, 1.2), (1.1, 1.2), (1.4, REQ['low']['k']), (1.5, REQ['low']['k'])])
    a.pos('root', [(0, [0, 0, 0]), (0.5, [0, 0, -1.2], 'easeOutQuad'), (0.7, [0, -0.8, -1.2]), (1.1, [0, 0, 0], 'easeInOutSine'), (1.5, [0, 0, 0])], 'lin')
    a.rot('torso', [(0, [-10, 0, 0]), (0.3, [-14, 0, 6]), (0.5, [18, 0, -4], 'easeOutQuad'), (0.7, [24, 0, 0]), (0.9, [10, 0, 0]),
                    (1.1, [-6, 0, 0], 'easeInOutSine'), (1.5, [0, 0, 0])], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (0.9, [1, 1, 1]), (1.0, [1.04, 1.05, 1.04]), (1.3, [1, 0.97, 1]), (1.5, [1, 1, 1])], 'lin')
    mouth(a, [(0, 1.6), (0.3, 2.2), (0.7, 0.4), (1.0, 1.0), (1.3, 0), (1.5, 0)])
    a.sound(0.15, 'bump').sound(0.4, 'bump').sound(0.7, 'strum').sound(1.0, 'sigh')
    return a


def nervous(name, sgn):
    """2.4 s loop. The requinto is back and the boss has his back to them: nobody knows who goes first. Each
    looks at the man beside him, then the other side, back again (sgn: which way first), a gulp, a shrug of
    the instrument, brows up; no one plays."""
    a = state(M(name, 2.4, True), True, True)
    look = [(0, [0, 0, 0]), (0.25, [0, 38 * sgn, 0], 'easeOutQuad'), (0.85, [0, 38 * sgn, 0]), (1.1, [0, -34 * sgn, 0], 'easeOutQuad'),
            (1.7, [0, -34 * sgn, 0]), (2.0, [0, 0, 0], 'easeInOutSine'), (2.4, [0, 0, 0])]
    a.rot('root', look, 'lin')
    a.rot('torso', [(0, [4, 0, 0]), (1.0, [6, 0, 3 * sgn]), (2.0, [4, 0, -3 * sgn]), (2.4, [4, 0, 0])])
    a.rot('arm_r', [(0, [-40, -20, 8]), (2.0, [-40, -20, 8]), (2.15, [-52, -20, 18], 'easeOutQuad'), (2.4, [-40, -20, 8])], 'lin')
    a.rot('arm_l', [(0, [-40, 20, -8]), (2.0, [-40, 20, -8]), (2.15, [-52, 20, -18], 'easeOutQuad'), (2.4, [-40, 20, -8])], 'lin')
    stretch(a, 'arm_r', [(0, 1.15)])
    stretch(a, 'arm_l', [(0, 1.15)])
    a.pos('root', [(0, [0, 0, 0]), (2.0, [0, 0, 0]), (2.15, [0, 0.6, 0], 'easeOutQuad'), (2.4, [0, 0, 0])], 'lin')
    for b, s_ in (('brow_r', 1), ('brow_l', -1)):
        a.pos(b, [(0, [0, 1.0, 0])], 'lin')
        a.rot(b, [(0, [0, 0, -12 * s_])], 'lin')
    mouth(a, [(0, 0.2), (1.5, 0.2), (1.6, 0.8), (1.75, 0.1), (2.4, 0.2)])
    a.sound(1.6, 'gulp')
    return a


# ===================================================================== phase four

JAW = 3.3            # the jaw on the floor: how far the lower lip drops (px)
LIMP_R, LIMP_L = [6, 0, 7], [6, 0, -7]


def brows(a, keys):
    """keys: [(t, lift px, tilt deg)]: both brows up, the tilt raising their inner ends (worry, wonder)."""
    for b, s_ in (('brow_r', 1), ('brow_l', -1)):
        a.pos(b, [(t, [0, up, 0]) + tuple(r) for t, up, tilt, *r in keys])
        a.rot(b, [(t, [0, 0, -tilt * s_]) + tuple(r) for t, up, tilt, *r in keys])


def jaw_drop():
    """0.6 s (into shocked_a / shocked_b). The scar has opened again: a jolt back on his heels, the brows shoot up and
    the jaw drops (a little bounce at the bottom), the arms falling to his sides, instrument forgotten on its strap."""
    a = state(M('jaw_drop', 0.6, 'hold'), True, True)
    a.pos('root', [(0, [0, 0, 0]), (0.12, [0, 0.5, 0.8], 'easeOutQuad'), (0.35, [0, 0, 0.6], 'easeInOutSine'), (0.6, [0, 0, 0.6])], 'lin')
    a.rot('torso', [(0, [0, 0, 0]), (0.12, [-12, 0, 0], 'easeOutQuad'), (0.4, [-7, 0, 0], 'easeInOutSine'), (0.6, [-7, 0, 0])], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (0.1, [0.93, 1.09, 0.93], 'easeOutQuad'), (0.3, [1.02, 0.98, 1.02]), (0.45, [1, 1, 1]), (0.6, [1, 1, 1])], 'lin')
    a.rot('arm_r', [(0, [-30, -20, 6]), (0.15, [-20, 0, 24], 'easeOutQuad'), (0.45, LIMP_R, 'easeInOutSine'), (0.6, LIMP_R)], 'lin')
    a.rot('arm_l', [(0, [-30, 20, -6]), (0.15, [-20, 0, -24], 'easeOutQuad'), (0.45, LIMP_L, 'easeInOutSine'), (0.6, LIMP_L)], 'lin')
    stretch(a, 'arm_r', [(0, 1.0), (0.6, 1.0)])
    stretch(a, 'arm_l', [(0, 1.0), (0.6, 1.0)])
    a.rot('trumpet', [(0, [52, 0, 0])], 'lin')
    a.rot('violin', [(0, [40, 0, 0])], 'lin')
    brows(a, [(0, 0, 0), (0.08, 1.7, 8, 'easeOutQuad'), (0.6, 1.5, 8)])
    mouth(a, [(0, 0), (0.18, JAW + 0.5, 'easeOutQuad'), (0.3, JAW - 0.3, 'easeInOutSine'), (0.42, JAW), (0.6, JAW)])
    return a


def shocked(name, hands):
    """2.4 s loop. Jaw on the floor, staring: a slow shake of the head (he cannot believe it), a swallow; brows
    right up. hands=False: arms hanging limp; True: both hands up on the sides of his head, under the sombrero."""
    L = 2.4
    a = state(M(name, L, True), True, True)
    a.pos('root', [(0, [0, 0, 0.6])], 'lin')
    a.rot('root', [(0, [0, 0, 0]), (0.5, [0, -9, 0]), (1.0, [0, 8, 0]), (1.5, [0, -6, 0]), (2.0, [0, 0, 0]), (L, [0, 0, 0])])
    a.rot('torso', [(0, [-7, 0, 0]), (1.2, [-8, 0, 1]), (L, [-7, 0, 0])])
    a.scale('body', [(0, [1, 1, 1]), (1.2, [1.015, 1.02, 1.015]), (L, [1, 1, 1])])
    if hands:
        # the heels of his hands to his temples, fingers up under the brim
        a.rot('arm_r', [(0, [-156, -30, 30]), (1.2, [-154, -32, 32]), (L, [-156, -30, 30])])
        a.rot('arm_l', [(0, [-156, 30, -30]), (1.2, [-154, 32, -32]), (L, [-156, 30, -30])])
        stretch(a, 'arm_r', [(0, 1.5)])
        stretch(a, 'arm_l', [(0, 1.5)])
    else:
        a.rot('arm_r', [(0, LIMP_R), (1.2, [4, 0, 8]), (L, LIMP_R)])
        a.rot('arm_l', [(0, LIMP_L), (1.2, [4, 0, -8]), (L, LIMP_L)])
        stretch(a, 'arm_r', [(0, 1.0)])
        stretch(a, 'arm_l', [(0, 1.0)])
    a.rot('trumpet', [(0, [52, 0, 0])], 'lin')
    a.rot('violin', [(0, [40, 0, 0])], 'lin')
    brows(a, [(0, 1.5, 8), (1.2, 1.7, 10), (L, 1.5, 8)])
    # hanging open, a tremble in it; a gulp (closes and drops again) at 1.8
    mouth(a, [(0, JAW), (0.6, JAW - 0.2), (1.2, JAW + 0.1), (1.7, JAW), (1.8, 0.6, 'easeInQuad'), (1.95, JAW, 'easeOutQuad'), (L, JAW)])
    return a


def bounce(a, L, step=0.4, h=0.9):
    """Up on his toes on every count, legs stomping in turn: the hype's beat."""
    a.pos('root', osc(0, L, step / 2, [0, 0, 0], [0, h, 0]), 'cat')
    keys_r, keys_l = [], []
    t, i = 0.0, 0
    while t <= L + 1e-6:
        keys_r.append((round(t, 4), [-16, 0, 0] if i % 4 == 1 else [0, 0, 0]))
        keys_l.append((round(t, 4), [-16, 0, 0] if i % 4 == 3 else [0, 0, 0]))
        t += step / 2
        i += 1
    a.rot('leg_r', keys_r, 'lin')
    a.rot('leg_l', keys_l, 'lin')


PUMP_R = [-170, 4, 30]          # a fist (or whatever is in it) punched up, out past the brim
PUMP_K = 1.7                     # (stretched: his arms are short and the sombrero is wide)


def hype(kind):
    """3.2 s loop: phase four, the last song coming: no fear left in them, up on their toes, hyping each other up.
    guitar (and the singer's requinto): chugging strums, a windmill, a fist pumped twice with a shout. trumpet: the
    horn up for a warm-up toot, down, fist pumps. violin: the bow up like a sword, circling, conducting, a point at
    the sky. coro: the maraca shaken high, the hand mic pumped, hips swaying. Mouths open on every shout."""
    L = 3.2
    a = state(M('hype_' + kind, L, True), True, True)
    bounce(a, L)
    a.rot('torso', [(0, [-4, 0, -4]), (0.8, [-6, 0, 4]), (1.6, [-4, 0, -4]), (2.4, [-8, 0, 4]), (L, [-4, 0, -4])])
    a.rot('root', [(0, [0, -6, 0]), (1.6, [0, 6, 0]), (L, [0, -6, 0])])
    shouts = []
    if kind == 'guitar':
        down, up = [-36, -52, 0], [-104, -52, 0]
        guitar_pose(a, 0)
        r = [(0, up)]
        for i in range(4):                           # four chugs, a beat each
            r += [(0.2 + i * 0.4, down, 'easeInQuad'), (0.4 + i * 0.4, up, 'easeOutQuad')]
        # the windmill: the arm right round over his shoulder, down onto the strings
        r += [(1.75, [-150, -20, 30], 'easeInOutSine'), (1.95, [-250, 0, 40]), (2.1, [-350, -20, 30]), (2.2, [-396, -52, 0], 'easeOutQuad'),
              (2.4, [-396, -52, 0])]
        a.rot('arm_r', r, 'lin')
        # (the windmill ends a full turn round: the loop jumps back from -396 to -36 + -360, the same pose)
        a.rot('arm_r', [(2.55, [-530, 4, 30], 'easeOutBack'), (2.7, [-470, -52, 0], 'easeInQuad'), (2.85, [-530, 4, 30], 'easeOutBack'),
                        (3.05, [-464, -52, 0], 'easeInOutSine'), (L, [-464, -52, 0])], 'lin')
        stretch(a, 'arm_r', [(0, 1.5), (1.6, 1.5), (1.95, 1.7), (2.2, 1.5), (2.55, PUMP_K), (2.7, 1.4), (2.85, PUMP_K), (3.05, 1.5), (L, 1.5)])
        stretch(a, 'arm_l', [(0, 1.6)])
        for i in range(4):
            a.sound(round(0.2 + i * 0.4, 2), 'strum')
        a.sound(2.2, 'strum')
        shouts = [(2.55, 2.4), (2.85, 2.6)]
    elif kind == 'trumpet':
        p, d = TRUMPET_PLAY, TRUMPET_DOWN
        a.rot('arm_r', [(0, d['r']['rot']), (0.3, p['r']['rot'], 'easeOutQuad'), (1.2, p['r']['rot']), (1.45, d['r']['rot'], 'easeInOutSine'),
                        (1.9, d['r']['rot']), (2.05, PUMP_R, 'easeOutBack'), (2.25, d['r']['rot'], 'easeInQuad'), (2.6, d['r']['rot']),
                        (2.75, PUMP_R, 'easeOutBack'), (2.95, d['r']['rot'], 'easeInQuad'), (L, d['r']['rot'])], 'lin')
        a.rot('arm_l', [(0, d['l']['rot']), (0.3, p['l']['rot'], 'easeOutQuad'), (1.2, p['l']['rot']), (1.45, d['l']['rot'], 'easeInOutSine'), (L, d['l']['rot'])], 'lin')
        stretch(a, 'arm_r', [(0, d['r']['k']), (0.3, p['r']['k']), (1.2, p['r']['k']), (1.45, d['r']['k']), (1.9, d['r']['k']), (2.05, PUMP_K), (2.25, d['r']['k']),
                             (2.6, d['r']['k']), (2.75, PUMP_K), (2.95, d['r']['k']), (L, d['r']['k'])])
        stretch(a, 'arm_l', [(0, d['l']['k']), (0.3, p['l']['k']), (1.2, p['l']['k']), (1.45, d['l']['k']), (L, d['l']['k'])])
        a.rot('trumpet', [(0, [d['trumpet'], 0, 0]), (0.3, [p['trumpet'], 0, 0], 'easeOutQuad'), (0.5, [-12, 0, 0]), (0.9, [-18, 0, 0]),
                          (1.2, [p['trumpet'], 0, 0]), (1.45, [d['trumpet'], 0, 0], 'easeInOutSine'), (L, [d['trumpet'], 0, 0])], 'lin')
        for c in ('cheek_r', 'cheek_l'):
            a.scale(c, [(0, HIDDEN), (0.32, HIDDEN), (0.4, [1, 1, 1]), (1.15, [1, 1, 1]), (1.22, HIDDEN), (L, HIDDEN)], 'lin')
        for v, at in (('valve_1', 0.5), ('valve_2', 0.7), ('valve_3', 0.9)):
            a.pos(v, [(0, [0, 0, 0]), (at, [0, 0, 0]), (at + 0.05, [0, -0.6, 0]), (at + 0.15, [0, 0, 0]), (L, [0, 0, 0])], 'lin')
        a.sound(0.42, 'toot')
        shouts = [(2.05, 2.4), (2.75, 2.6)]
    elif kind == 'violin':
        # the violin tucked under his left arm, the bow (right hand) a sword held up, circled, brought down, pointed out
        a.rot('arm_l', [(0, [-40, 10, -16])], 'lin')
        a.rot('violin', [(0, [40, 0, 0])], 'lin')
        stretch(a, 'arm_l', [(0, 1.2)])
        a.rot('arm_r', [(0, [-60, -10, 10]), (0.3, [-170, -6, 26], 'easeOutBack'), (0.55, [-164, -6, 38]), (0.8, [-172, -6, 18]), (1.05, [-164, -6, 38]),
                        (1.3, [-172, -6, 18]), (1.6, [-90, -30, 0], 'easeInQuad'), (1.8, [-140, 0, 10], 'easeOutQuad'), (2.0, [-90, -30, 0], 'easeInQuad'),
                        (2.3, [-118, 20, 30], 'easeOutBack'), (2.9, [-120, 20, 32]), (L, [-60, -10, 10], 'easeInOutSine')], 'lin')
        stretch(a, 'arm_r', [(0, 1.15), (0.3, PUMP_K), (1.3, PUMP_K), (1.6, 1.2), (2.3, 1.5), (2.9, 1.5), (L, 1.15)])
        a.rot('bow', [(0, BOW_MID['prop']), (0.3, [0, 20, 0], 'easeOutQuad'), (1.3, [0, 20, 0]), (1.6, BOW_MID['prop'], 'easeInOutSine'),
                      (2.3, [0, 40, 0], 'easeOutQuad'), (2.9, [0, 40, 0]), (L, BOW_MID['prop'], 'easeInOutSine')], 'lin')
        shouts = [(0.3, 2.2), (2.3, 2.8)]
    else:   # coro
        shake = osc(0.2, 1.6, 0.1, [-162, -8, 36], [-146, -8, 44])
        a.rot('arm_r', [(0, [-40, -10, 12])] + shake + [(1.9, [-60, -10, 16], 'easeInOutSine'), (2.2, [-160, -8, 36], 'easeOutBack'),
                                                         (2.5, [-60, -10, 16]), (2.8, [-160, -8, 36], 'easeOutBack'), (L, [-40, -10, 12], 'easeInOutSine')], 'lin')
        stretch(a, 'arm_r', [(0, 1.2), (0.2, 1.7), (1.6, 1.7), (1.9, 1.2), (2.2, PUMP_K), (2.5, 1.2), (2.8, PUMP_K), (L, 1.2)])
        a.rot('arm_l', [(0, CORO['down']['rot']), (0.4, [-160, 10, -34], 'easeOutBack'), (0.8, CORO['down']['rot']), (1.2, [-160, 10, -34], 'easeOutBack'),
                        (1.6, CORO['down']['rot']), (L, CORO['down']['rot'])], 'lin')
        stretch(a, 'arm_l', [(0, CORO['down']['k']), (0.4, PUMP_K), (0.8, CORO['down']['k']), (1.2, PUMP_K), (1.6, CORO['down']['k']), (L, CORO['down']['k'])])
        a.rot('root', [(0, [0, -6, -5]), (0.4, [0, -2, 5]), (0.8, [0, 2, -5]), (1.2, [0, 6, 5]), (1.6, [0, 6, -5]), (2.0, [0, 2, 5]), (2.4, [0, -2, -5]),
                       (2.8, [0, -6, 5]), (L, [0, -6, -5])])
        for i in range(8):
            a.sound(round(0.25 + i * 0.18, 2), 'maraca')
        shouts = [(0.4, 2.0), (1.2, 2.2), (2.2, 2.6), (2.8, 2.4)]
    mk = [(0, 0.3)]
    for t, o in shouts:
        mk += [(t - 0.06, 0.4), (t + 0.04, o, 'easeOutQuad'), (t + 0.3, 0.6, 'easeInQuad')]
    mk.append((L, 0.3))
    mouth(a, sorted(mk, key=lambda k: k[0]))
    brows(a, [(0, 0.6, 0)] + [k for t, o in shouts for k in ((t, 1.2, -6, 'easeOutQuad'), (t + 0.4, 0.6, 0))] + [(L, 0.6, 0)])
    return a


# ===================================================================== the lamplighter

def lamp_walk():
    """0.8 s loop (the third of the coro, off to the floodlight and back): a brisk little walk, the
    maraca and the hand mic kept up, a bob on every step."""
    a = state(M('lamp_walk', 0.8, True), True, True)
    a.rot('leg_r', [(0, [-28, 0, 0]), (0.4, [28, 0, 0]), (0.8, [-28, 0, 0])])
    a.rot('leg_l', [(0, [28, 0, 0]), (0.4, [-28, 0, 0]), (0.8, [28, 0, 0])])
    a.pos('root', [(0, [0, 0, 0]), (0.2, [0, 0.8, 0]), (0.4, [0, 0, 0]), (0.6, [0, 0.8, 0]), (0.8, [0, 0, 0])])
    a.rot('arm_r', [(0, [-24, -10, 10]), (0.4, [-40, -10, 10]), (0.8, [-24, -10, 10])])
    a.rot('arm_l', [(0, CORO['down']['rot'])], 'lin')
    stretch(a, 'arm_l', [(0, 1.2)])
    stretch(a, 'arm_r', [(0, 1.2)])
    a.rot('torso', [(0, [4, 0, -3]), (0.4, [4, 0, 3]), (0.8, [4, 0, -3])])
    a.sound(0.0, 'step').sound(0.4, 'step')
    return a


def lamp_switch():
    """2.0 s. Up on his toes, the maraca hand up to the floodlight's chain, a tug (0.8 s: the light),
    a pleased little shake of the maraca at it, and back down."""
    a = state(M('lamp_switch', 2.0, 'hold'), True, True)
    a.rot('arm_r', [(0, [-30, -10, 10]), (0.45, [-165, 0, 28], 'easeInOutSine'), (0.7, [-170, 0, 26]), (0.8, [-140, 0, 26], 'easeOutQuad'),
                    (0.95, [-165, 0, 28]), (1.1, [-110, 0, 40], 'easeInOutSine'), (1.25, [-118, 0, 46]), (1.4, [-106, 0, 36]),
                    (1.55, [-118, 0, 46]), (2.0, [-30, -10, 10], 'easeInOutSine')], 'lin')
    stretch(a, 'arm_r', [(0, 1.2), (0.45, 1.55), (0.8, 1.45), (1.1, 1.2), (2.0, 1.2)])
    a.rot('arm_l', [(0, CORO['down']['rot'])], 'lin')
    stretch(a, 'arm_l', [(0, 1.2)])
    a.pos('root', [(0, [0, 0, 0]), (0.45, [0, 1.6, 0], 'easeOutQuad'), (0.8, [0, 1.4, 0]), (0.95, [0, 0, 0], 'easeInQuad'), (2.0, [0, 0, 0])], 'lin')
    a.rot('torso', [(0, [0, 0, 0]), (0.45, [-10, 0, 6], 'easeInOutSine'), (0.8, [-6, 0, 6]), (1.1, [0, 0, 0]), (1.4, [0, 0, 4]),
                    (2.0, [0, 0, 0])])
    for b in ('brow_r', 'brow_l'):
        a.pos(b, [(0, [0, 0, 0]), (0.8, [0, 0, 0]), (0.9, [0, 1.0, 0]), (1.6, [0, 0.8, 0]), (2.0, [0, 0, 0])])
    mouth(a, [(0, 0), (0.85, 0), (1.0, 1.8, 'easeOutQuad'), (1.5, 1.4), (2.0, 0)])
    a.sound(0.8, 'pick').sound(1.15, 'shake').sound(1.45, 'shake')
    return a


ALL = [sleep_a, sleep_b, sleep_c, startle, getup, resleep, jacket, hat, shades,
       lambda: grab('guitar'), lambda: grab('violin'), lambda: grab('trumpet'), lambda: grab('coro'),
       lambda: ready('guitar'), lambda: ready('violin'), lambda: ready('trumpet'), lambda: ready('coro'),
       count, lambda: play('guitar'), lambda: play('singer'), lambda: play('violin'), lambda: play('trumpet'),
       lambda: play('coro'), bow, whacked, jacket_slow, hat_slow, lamp_walk, lamp_switch, scared, robbed, awed, catch,
       lambda: nervous('nervous_a', 1), lambda: nervous('nervous_b', -1),
       jaw_drop, lambda: shocked('shocked_a', False), lambda: shocked('shocked_b', True),
       lambda: hype('guitar'), lambda: hype('trumpet'), lambda: hype('violin'), lambda: hype('coro')]


# the ones that start in a chair: written again sitting on the floor (everyone but Gordo)
FROM_CHAIR = [sleep_a, sleep_b, sleep_c, startle, getup, jacket, whacked, jacket_slow]


def floor_variants():
    """The FROM_CHAIR animations as `<name>_floor`: sat on the stage, the jacket folded behind him."""
    global SEAT_ROOT, SEAT_LEG_R, SEAT_LEG_L, JACKET_OFF, ON_FLOOR
    saved = SEAT_ROOT, SEAT_LEG_R, SEAT_LEG_L, JACKET_OFF, ON_FLOOR
    SEAT_ROOT, SEAT_LEG_R, SEAT_LEG_L, JACKET_OFF, ON_FLOOR = FLOOR_ROOT, FLOOR_LEG_R, FLOOR_LEG_L, 'jacket_floor', True
    try:
        out = []
        for fn in FROM_CHAIR:
            a = fn()
            a.name += '_floor'
            out.append(a)
        return out
    finally:
        SEAT_ROOT, SEAT_LEG_R, SEAT_LEG_L, JACKET_OFF, ON_FLOOR = saved


def main():
    data = {'format_version': '1.8.0', 'animations': {}}
    for a in [fn() for fn in ALL] + floor_variants():
        data['animations']['animation.mariachi.' + a.name] = guard_brows(a.compile())
    with open(OUT, 'w', newline='\n') as f:
        json.dump(data, f, separators=(',', ':'))
    print(OUT, len(data['animations']), 'animations')
    with open(POSES_OUT, 'w', newline='\n') as f:
        json.dump(POSES, f, separators=(',', ':'))
    print(POSES_OUT)


if __name__ == '__main__':
    main()
