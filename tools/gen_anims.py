#!/usr/bin/env python3
"""Generates the Wandering Papon's GeckoLib animations.

Writes src/main/resources/assets/papon/animations/entity/wandering_papon.animation.json

Conventions, checked against Blockbench renders of this rig:
  rotation x < 0      swings an arm or leg forward; tilts the torso back
  arm_r rotation z > 0, arm_l rotation z < 0   raise the arm out sideways
  position z < 0      moves forward (the face is -Z), y > 0 moves up

Channels are Catmull-Rom by default so motion stays continuous through every
key; keys that want a deliberate snap (a pop, a landing) carry a GeckoLib easing
instead, which GeckoLib only honours on linear keys.

Prop visibility is scale: every animation that does not mention a prop pins it
to scale 0, so no prop ever leaks into an animation that has no use for it.

Sound keys name a cue ('flip', 'sizzle', ...). WanderingPaponEntity maps cues
to sounds, so a cue can be retuned without regenerating this file.
"""
import json
import os

from gen_model import PROP_BONES

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'src/main/resources/assets/papon/animations/entity/wandering_papon.animation.json')
PREFIX = 'animation.wandering_papon.'
EPS = 0.02  # width of an instant visibility swap


def r4(x):
    return round(float(x), 4)


class Anim:
    def __init__(self, name, length, loop):
        self.name, self.length, self.loop = name, length, loop
        self.bones = {}
        self.sounds = {}

    def _chan(self, bone, chan, keys, lerp):
        out = self.bones.setdefault(bone, {}).setdefault(chan, {})
        for k in keys:
            t, vec = k[0], k[1]
            ease = k[2] if len(k) > 2 else lerp
            kf = {'vector': [r4(v) for v in vec]}
            if ease == 'cat':
                kf['lerp_mode'] = 'catmullrom'
            elif ease and ease != 'lin':
                name, _, arg = ease.partition(':')
                kf['easing'] = name
                if arg:
                    kf['easingArgs'] = [float(arg)]
            out['%.4g' % t if t else '0.0'] = kf
        return self

    def rot(self, bone, keys, lerp='cat'):
        return self._chan(bone, 'rotation', keys, lerp)

    def pos(self, bone, keys, lerp='cat'):
        return self._chan(bone, 'position', keys, lerp)

    def scale(self, bone, keys, lerp='cat'):
        return self._chan(bone, 'scale', keys, lerp)

    def vis(self, bone, start, changes):
        """Instant show/hide: start visible?, then [(t, visible), ...]."""
        keys = [(0, [1, 1, 1] if start else [0, 0, 0], 'lin')]
        cur = start
        for t, v in changes:
            keys.append((t - EPS, [1, 1, 1] if cur else [0, 0, 0], 'lin'))
            keys.append((t, [1, 1, 1] if v else [0, 0, 0], 'lin'))
            cur = v
        return self.scale(bone, keys, 'lin')

    def sound(self, t, cue):
        self.sounds['%.4g' % t] = {'effect': cue}
        return self

    def compile(self):
        bones = {b: dict(c) for b, c in self.bones.items()}
        for prop in PROP_BONES + ['taco_a', 'taco_b']:
            if prop == 'taco_a' or prop == 'taco_b':
                # tacos ride inside the grill; hidden with it unless keyed
                continue
            if 'scale' not in bones.get(prop, {}):
                bones.setdefault(prop, {})['scale'] = {'0.0': {'vector': [0, 0, 0]}}
        a = {
            'loop': True if self.loop is True else ('hold_on_last_frame' if self.loop == 'hold' else False),
            'animation_length': self.length,
            'bones': bones,
        }
        if self.sounds:
            a['sound_effects'] = self.sounds
        return a


# Poses shared between animations, so one-shots hand over without a jump.
SIT_ROOT = [0, -4, 0]
SIT_LEG_POS = [0, 3, -6]
SIT_LEG_R = [-88, 30, 0]
SIT_LEG_L = [-88, -30, 0]
COOK_ARM_L = [-82, 38, 0]     # spatula hovering over the grill
COOK_ARM_R = [-28, -8, 6]     # shaker held at rest
HAPPY_BROW = [0, 0.4, 0]


def walk():
    a = Anim('walk', 0.8, True)
    a.rot('leg_r', [(0, [24, 0, 0]), (0.4, [-24, 0, 0]), (0.8, [24, 0, 0])])
    a.rot('leg_l', [(0, [-24, 0, 0]), (0.4, [24, 0, 0]), (0.8, [-24, 0, 0])])
    a.rot('arm_r', [(0, [-20, 0, 5]), (0.4, [18, 0, 3]), (0.8, [-20, 0, 5])])
    a.rot('arm_l', [(0, [18, 0, -3]), (0.4, [-20, 0, -5]), (0.8, [18, 0, -3])])
    # bob: two steps per cycle, lowest at contact
    a.pos('root', [(0, [0, 0, 0]), (0.2, [0, 1.3, 0]), (0.4, [0, 0, 0]), (0.6, [0, 1.3, 0]), (0.8, [0, 0, 0])])
    a.rot('torso', [(0, [3, 4, -3]), (0.4, [3, -4, 3]), (0.8, [3, 4, -3])])
    a.scale('body', [(0, [1.04, 0.96, 1.04]), (0.2, [0.98, 1.03, 0.98]), (0.4, [1.04, 0.96, 1.04]),
                     (0.6, [0.98, 1.03, 0.98]), (0.8, [1.04, 0.96, 1.04])])
    # secondary motion trails the torso by a tenth of a second
    a.rot('hat', [(0, [0, 0, 1.5]), (0.1, [0, 0, 2.5]), (0.5, [0, 0, -2.5]), (0.8, [0, 0, 1.5])])
    a.pos('hat', [(0, [0, 0, 0]), (0.25, [0, 0.35, 0]), (0.4, [0, 0, 0]), (0.65, [0, 0.35, 0]), (0.8, [0, 0, 0])])
    a.pos('backpack', [(0, [0, -0.2, 0]), (0.25, [0, 0.45, 0]), (0.4, [0, -0.2, 0]), (0.65, [0, 0.45, 0]),
                       (0.8, [0, -0.2, 0])])
    return a


def idle():
    a = Anim('idle', 4.0, True)
    a.scale('body', [(0, [1, 1, 1]), (2.0, [1.015, 1.03, 1.015]), (4.0, [1, 1, 1])])
    a.rot('root', [(0, [0, 0, 0]), (0.9, [0, 14, 0], 'easeInOutSine'), (1.8, [0, 14, 0], 'easeInOutSine'),
                   (2.7, [0, -12, 0], 'easeInOutSine'), (3.4, [0, -12, 0], 'easeInOutSine'),
                   (4.0, [0, 0, 0], 'easeInOutSine')], 'lin')
    a.pos('brow_r', [(0, [0, 0, 0]), (0.8, [0, 0, 0]), (1.0, [0, 0.6, 0]), (1.4, [0, 0.6, 0]), (1.6, [0, 0, 0]),
                     (4.0, [0, 0, 0])])
    a.pos('brow_l', [(0, [0, 0, 0]), (0.8, [0, 0, 0]), (1.0, [0, 0.6, 0]), (1.4, [0, 0.6, 0]), (1.6, [0, 0, 0]),
                     (4.0, [0, 0, 0])])
    a.rot('arm_r', [(0, [0, 0, 1]), (2.0, [-3, 0, 4]), (4.0, [0, 0, 1])])
    a.rot('arm_l', [(0, [0, 0, -1]), (2.0, [-3, 0, -4]), (4.0, [0, 0, -1])])
    a.rot('hat', [(0, [0, 0, 0]), (1.2, [0, 0, -1.5]), (2.9, [0, 0, 1.5]), (4.0, [0, 0, 0])])
    return a


def sit_down():
    """Walking pose -> sitting at the grill, ready to cook. Ends on the grill loop's first frame."""
    a = Anim('sit_down', 3.0, 'hold')
    # looks around for anyone, gets sad
    a.rot('root', [(0, [0, 0, 0]), (0.25, [0, -20, 0], 'easeInOutSine'), (0.55, [0, 20, 0], 'easeInOutSine'),
                   (0.8, [0, 0, 0], 'easeInOutSine'), (3.0, [0, 0, 0])], 'lin')
    a.rot('brow_r', [(0, [0, 0, 0]), (0.35, [0, 0, -12]), (2.2, [0, 0, -12]), (2.45, [0, 0, 0])])
    a.rot('brow_l', [(0, [0, 0, 0]), (0.35, [0, 0, 12]), (2.2, [0, 0, 12]), (2.45, [0, 0, 0])])
    a.pos('brow_r', [(0, [0, 0, 0]), (2.3, [0, 0, 0]), (2.5, [0, 0.9, 0], 'easeOutBack'), (3.0, HAPPY_BROW)], 'lin')
    a.pos('brow_l', [(0, [0, 0, 0]), (2.3, [0, 0, 0]), (2.5, [0, 0.9, 0], 'easeOutBack'), (3.0, HAPPY_BROW)], 'lin')
    # plop: tiny rise, drop, squash, settle
    a.pos('root', [(0, [0, 0, 0]), (0.85, [0, 0, 0]), (0.95, [0, 0.8, 0], 'easeOutQuad'),
                   (1.12, SIT_ROOT, 'easeInQuad'), (3.0, SIT_ROOT)], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (1.1, [1, 1, 1]), (1.16, [1.14, 0.84, 1.14], 'easeOutQuad'),
                     (1.32, [0.96, 1.05, 0.96], 'easeOutQuad'), (1.5, [1, 1, 1], 'easeOutBack'), (3.0, [1, 1, 1])], 'lin')
    for leg, rot in (('leg_r', SIT_LEG_R), ('leg_l', SIT_LEG_L)):
        a.rot(leg, [(0, [0, 0, 0]), (0.9, [0, 0, 0]), (1.12, rot, 'easeOutQuad'), (3.0, rot)], 'lin')
        a.pos(leg, [(0, [0, 0, 0]), (0.9, [0, 0, 0]), (1.12, SIT_LEG_POS, 'easeOutQuad'), (3.0, SIT_LEG_POS)], 'lin')
    a.rot('hat', [(0, [0, 0, 0]), (1.12, [0, 0, 0]), (1.2, [6, 0, -4]), (1.45, [-2, 0, 1]), (1.7, [0, 0, 0]),
                  (3.0, [0, 0, 0])])
    a.pos('hat', [(0, [0, 0, 0]), (1.12, [0, 0, 0]), (1.2, [0, 1.2, 0]), (1.4, [0, 0, 0]), (3.0, [0, 0, 0])])
    # right arm: reach back to the pack, bring the grill round, set it down
    a.rot('arm_r', [(0, [0, 0, 0]), (1.45, [0, 0, 0]), (1.8, [65, 0, 28], 'easeInOutSine'),
                    (2.12, [-72, -30, 4], 'easeInOutBack:0.8'), (2.25, [-58, -36, 0], 'easeOutQuad'),
                    (2.55, [42, 0, 18], 'easeInOutSine'), (2.65, [42, 0, 18]),
                    (3.0, COOK_ARM_R, 'easeOutBack')], 'lin')
    a.vis('grill_hand', False, [(1.8, True), (2.25, False)])
    a.vis('grill', False, [(2.25, True)])
    a.pos('grill', [(0, [0, 0, 0]), (2.25, [0, 1.6, 0]), (2.42, [0, 0, 0], 'easeOutBounce'), (3.0, [0, 0, 0])], 'lin')
    a.vis('shaker', False, [(2.6, True)])
    # left arm: spatula out of the pack, held over the grill
    a.rot('arm_l', [(0, [0, 0, 0]), (2.25, [0, 0, 0]), (2.5, [55, 0, -18], 'easeInOutSine'),
                    (2.85, COOK_ARM_L, 'easeOutBack'), (3.0, COOK_ARM_L)], 'lin')
    a.vis('spatula', False, [(2.5, True)])
    # lean in to place the grill
    a.rot('torso', [(0, [0, 0, 0]), (1.5, [0, 0, 0]), (1.8, [-4, -10, 0]), (2.2, [10, 6, 0]), (2.55, [0, 0, 0]),
                    (3.0, [0, 0, 0])])
    # tacos pop onto the grill
    for bone, t in (('taco_a', 2.7), ('taco_b', 2.82)):
        a.scale(bone, [(0, [0, 0, 0]), (t, [0, 0, 0]), (t + 0.18, [1, 1, 1], 'easeOutBack:2'), (3.0, [1, 1, 1])], 'lin')
    a.sound(0.0, 'sigh').sound(1.12, 'plop').sound(1.8, 'rummage').sound(2.3, 'clank')
    a.sound(2.5, 'rummage').sound(2.75, 'pop').sound(2.87, 'pop')
    return a


def cook_pose(a, t):
    """Keys every bone the grill loop owns at its rest pose, at time t."""
    a.rot('arm_l', [(t, COOK_ARM_L)])
    a.rot('arm_r', [(t, COOK_ARM_R)])


def grill():
    a = Anim('grill', 4.0, True)
    a.pos('root', [(0, SIT_ROOT), (2.6, SIT_ROOT), (2.85, [0, -3.4, 0]), (3.1, SIT_ROOT), (3.35, [0, -3.4, 0]),
                   (3.6, SIT_ROOT), (4.0, SIT_ROOT)])
    for leg, rot in (('leg_r', SIT_LEG_R), ('leg_l', SIT_LEG_L)):
        a.rot(leg, [(0, rot)], 'lin')
        a.pos(leg, [(0, SIT_LEG_POS)], 'lin')
    a.vis('grill', True, [])
    a.vis('spatula', True, [])
    a.vis('shaker', True, [])
    # --- flip taco_b with the spatula
    a.rot('arm_l', [(0, COOK_ARM_L), (0.3, [-88, 42, 0], 'easeInOutSine'), (0.38, [-88, 42, 0]),
                    (0.48, [-120, 36, 0], 'easeOutQuad'), (0.8, [-80, 36, 0], 'easeInOutSine'),
                    (4.0, COOK_ARM_L)], 'lin')
    a.pos('taco_b', [(0, [0, 0, 0]), (0.45, [0, 0, 0]), (0.78, [0, 8, 0], 'easeOutQuad'),
                     (1.1, [0, 0, 0], 'easeInQuad'), (4.0, [0, 0, 0])], 'lin')
    a.rot('taco_b', [(0, [0, 0, 0]), (0.45, [0, 0, 0]), (1.1, [-360, 0, 0], 'lin'), (1.1 + EPS, [0, 0, 0], 'lin'),
                     (4.0, [0, 0, 0])], 'lin')
    a.scale('taco_b', [(0, [1, 1, 1]), (1.1, [1, 1, 1]), (1.16, [1.35, 0.65, 1.35], 'easeOutQuad'),
                       (1.4, [1, 1, 1], 'easeOutBack:2'), (4.0, [1, 1, 1])], 'lin')
    a.scale('taco_a', [(0, [1, 1, 1]), (1.12, [1, 1, 1]), (1.2, [1.1, 0.85, 1.1]), (1.4, [1, 1, 1]),
                       (4.0, [1, 1, 1])])
    # --- season with the shaker
    shake = [(0, COOK_ARM_R), (1.3, COOK_ARM_R), (1.6, [-98, -34, 0], 'easeInOutSine')]
    t = 1.6
    for i in range(6):
        t += 0.1
        shake.append((t, [-98, -34, 12 if i % 2 == 0 else -12], 'easeInOutSine'))
    shake += [(2.3, [-98, -34, 0], 'easeInOutSine'), (2.6, COOK_ARM_R, 'easeInOutSine'), (4.0, COOK_ARM_R)]
    a.rot('arm_r', shake, 'lin')
    a.rot('torso', [(0, [0, 0, 0]), (1.3, [0, 0, 0]), (1.6, [8, -6, 0]), (2.3, [8, -6, 0]), (2.6, [0, 0, 0]),
                    (2.85, [0, 0, 5]), (3.1, [0, 0, -5]), (3.35, [0, 0, 5]), (3.6, [0, 0, -5]),
                    (4.0, [0, 0, 0])])
    # --- happy wiggle: brows dance, hat bobs
    a.pos('brow_r', [(0, HAPPY_BROW), (2.85, HAPPY_BROW), (2.95, [0, 1.0, 0]), (3.1, HAPPY_BROW),
                     (3.35, [0, 1.0, 0]), (3.5, HAPPY_BROW), (4.0, HAPPY_BROW)])
    a.pos('brow_l', [(0, HAPPY_BROW), (2.95, HAPPY_BROW), (3.05, [0, 1.0, 0]), (3.2, HAPPY_BROW),
                     (3.45, [0, 1.0, 0]), (3.6, HAPPY_BROW), (4.0, HAPPY_BROW)])
    a.rot('hat', [(0, [0, 0, 0]), (2.9, [0, 0, -3]), (3.15, [0, 0, 3]), (3.4, [0, 0, -3]), (3.65, [0, 0, 3]),
                  (4.0, [0, 0, 0])])
    a.scale('body', [(0, [1, 1, 1]), (0.48, [1, 1, 1]), (0.56, [0.97, 1.04, 0.97]), (0.75, [1, 1, 1]),
                     (4.0, [1, 1, 1])])
    a.sound(0.42, 'flip').sound(1.1, 'sizzle').sound(1.75, 'shake').sound(2.05, 'shake').sound(2.9, 'hum')
    return a


def pack_up():
    """Grill pose -> standing and ready to walk."""
    a = Anim('pack_up', 2.4, 'hold')
    a.pos('brow_r', [(0, HAPPY_BROW), (0.12, [0, 1.4, 0], 'easeOutBack'), (0.8, [0, 1.2, 0]), (1.2, [0, 0, 0]),
                     (2.4, [0, 0, 0])], 'lin')
    a.pos('brow_l', [(0, HAPPY_BROW), (0.12, [0, 1.4, 0], 'easeOutBack'), (0.8, [0, 1.2, 0]), (1.2, [0, 0, 0]),
                     (2.4, [0, 0, 0])], 'lin')
    a.pos('root', [(0, SIT_ROOT), (0.14, [0, -2.2, 0], 'easeOutQuad'), (0.3, SIT_ROOT, 'easeInQuad'),
                   (1.5, SIT_ROOT), (1.78, [0, 2.2, 0], 'easeOutQuad'), (2.0, [0, 0, 0], 'easeInQuad'),
                   (2.4, [0, 0, 0])], 'lin')
    a.pos('hat', [(0, [0, 0, 0]), (0.16, [0, 3.2, 0], 'easeOutQuad'), (0.38, [0, 0, 0], 'easeOutBounce'),
                  (1.78, [0, 0, 0]), (1.9, [0, 1.6, 0]), (2.1, [0, 0, 0]), (2.4, [0, 0, 0])], 'lin')
    a.rot('hat', [(0, [0, 0, 0]), (0.16, [0, 0, 9]), (0.38, [0, 0, -2]), (0.6, [0, 0, 0]), (2.4, [0, 0, 0])])
    # tacos get tossed into the pack, tools stowed
    for bone, t in (('taco_a', 0.3), ('taco_b', 0.4)):
        a.pos(bone, [(0, [0, 0, 0]), (t, [0, 0, 0]), (t + 0.22, [0, 6, 6], 'easeOutQuad')], 'lin')
        a.scale(bone, [(0, [1, 1, 1]), (t, [1, 1, 1]), (t + 0.22, [0, 0, 0], 'easeInBack')], 'lin')
    a.rot('arm_l', [(0, COOK_ARM_L), (0.35, COOK_ARM_L), (0.62, [50, 0, -18], 'easeInOutSine'),
                    (0.9, [0, 0, -4], 'easeInOutSine'), (1.9, [0, 0, -4]), (2.05, [-165, 0, -10], 'easeOutBack'),
                    (2.25, [-165, 0, -10]), (2.4, [0, 0, 0], 'easeInOutSine')], 'lin')
    a.vis('spatula', True, [(0.62, False)])
    a.rot('arm_r', [(0, COOK_ARM_R), (0.4, COOK_ARM_R), (0.62, [40, 0, 18], 'easeInOutSine'),
                    (0.95, [-60, -36, 0], 'easeInOutSine'), (1.05, [-60, -36, 0]),
                    (1.38, [70, 0, 25], 'easeInOutBack:0.8'), (1.6, [0, 0, 3], 'easeInOutSine'),
                    (2.4, [0, 0, 0])], 'lin')
    a.vis('shaker', True, [(0.62, False)])
    a.vis('grill', True, [(1.02, False)])
    a.vis('grill_hand', False, [(1.02, True), (1.4, False)])
    a.rot('torso', [(0, [0, 0, 0]), (0.8, [0, 0, 0]), (1.0, [10, 6, 0]), (1.38, [-4, -10, 0]), (1.6, [0, 0, 0]),
                    (2.4, [0, 0, 0])])
    for leg, rot in (('leg_r', SIT_LEG_R), ('leg_l', SIT_LEG_L)):
        a.rot(leg, [(0, rot), (1.5, rot), (1.78, [0, 0, 0], 'easeOutQuad'), (2.4, [0, 0, 0])], 'lin')
        a.pos(leg, [(0, SIT_LEG_POS), (1.5, SIT_LEG_POS), (1.78, [0, 0, 0], 'easeOutQuad'), (2.4, [0, 0, 0])], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (1.5, [1, 1, 1]), (1.58, [1.08, 0.92, 1.08]), (1.78, [0.95, 1.06, 0.95]),
                     (2.02, [1.1, 0.9, 1.1], 'easeOutQuad'), (2.25, [1, 1, 1], 'easeOutBack'), (2.4, [1, 1, 1])], 'lin')
    a.sound(0.05, 'notice').sound(0.45, 'pop').sound(1.02, 'clank').sound(1.4, 'rummage').sound(2.0, 'plop')
    a.sound(2.05, 'cheer')
    return a


def receive_coupon():
    """Standing. Reads the coupon, realises what it is, celebrates, stows it, sets off."""
    a = Anim('receive_coupon', 4.2, 'hold')
    a.vis('coupon', True, [(3.25, False)])
    a.rot('arm_r', [(0, [-20, 0, 4]), (0.45, [-100, -52, 0], 'easeOutBack'), (1.3, [-100, -52, 0]),
                    (1.42, [-120, -10, 6], 'easeOutQuad'), (1.7, [-40, 0, 10], 'easeInOutSine'),
                    (1.85, [-168, 0, 18], 'easeOutBack'), (2.5, [-168, 0, 18]),
                    (2.7, [-150, 0, 26]), (2.9, [-168, 0, 18]), (3.22, [62, 0, 22], 'easeInOutSine'),
                    (3.5, [0, 0, 2], 'easeInOutSine'), (4.2, [0, 0, 0])], 'lin')
    a.rot('arm_l', [(0, [0, 0, 0]), (1.3, [0, 0, 0]), (1.45, [-20, 0, -30], 'easeOutBack'),
                    (1.85, [-168, 0, -18], 'easeOutBack'), (2.5, [-168, 0, -18]), (2.7, [-150, 0, -26]),
                    (2.9, [-168, 0, -18]), (3.2, [0, 0, -3], 'easeInOutSine'),
                    (3.55, [-92, 0, 0], 'easeOutBack:1.6'), (3.95, [-92, 0, 0]),
                    (4.2, [0, 0, 0], 'easeInOutSine')], 'lin')
    # reading: lean in, scan the lines
    a.rot('torso', [(0, [0, 0, 0]), (0.5, [8, 0, 0]), (0.75, [8, 4, 0]), (0.95, [8, -4, 0]), (1.15, [8, 4, 0]),
                    (1.3, [8, 0, 0]), (1.42, [-8, 0, 0]), (1.7, [0, 0, 0]), (3.2, [0, 0, 0]),
                    (3.55, [6, 0, 0]), (4.2, [0, 0, 0])])
    a.rot('brow_r', [(0, [0, 0, 0]), (0.5, [0, 0, 10]), (1.3, [0, 0, 10]), (1.4, [0, 0, 0]), (3.3, [0, 0, 0]),
                     (3.5, [0, 0, 16]), (4.2, [0, 0, 16])])
    a.rot('brow_l', [(0, [0, 0, 0]), (0.5, [0, 0, -10]), (1.3, [0, 0, -10]), (1.4, [0, 0, 0]), (3.3, [0, 0, 0]),
                     (3.5, [0, 0, -16]), (4.2, [0, 0, -16])])
    a.pos('brow_r', [(0, [0, 0, 0]), (1.3, [0, -0.3, 0]), (1.4, [0, 1.6, 0], 'easeOutBack'), (2.9, [0, 1.2, 0]),
                     (3.3, [0, 0, 0]), (4.2, [0, 0, 0])], 'lin')
    a.pos('brow_l', [(0, [0, 0, 0]), (1.3, [0, -0.3, 0]), (1.4, [0, 1.6, 0], 'easeOutBack'), (2.9, [0, 1.2, 0]),
                     (3.3, [0, 0, 0]), (4.2, [0, 0, 0])], 'lin')
    # realisation: stretch, hat pops off
    a.scale('body', [(0, [1, 1, 1]), (1.3, [1, 1, 1]), (1.4, [0.9, 1.15, 0.9], 'easeOutQuad'),
                     (1.6, [1, 1, 1], 'easeOutBack'), (1.7, [1, 1, 1]), (1.76, [1.12, 0.88, 1.12], 'easeOutQuad'),
                     (1.84, [0.92, 1.1, 0.92], 'easeOutQuad'), (2.5, [1, 1, 1]),
                     (2.56, [1.16, 0.84, 1.16], 'easeOutQuad'), (2.8, [1, 1, 1], 'easeOutBack'), (4.2, [1, 1, 1])], 'lin')
    a.pos('hat', [(0, [0, 0, 0]), (1.36, [0, 0, 0]), (1.55, [0, 6, 0], 'easeOutQuad'),
                  (1.8, [0, 0, 0], 'easeOutBounce'), (1.84, [0, 0, 0]), (2.1, [0, 3, 0], 'easeOutQuad'),
                  (2.55, [0, 0, 0], 'easeInQuad'), (2.62, [0, 0.6, 0]), (2.75, [0, 0, 0]), (4.2, [0, 0, 0])], 'lin')
    a.rot('hat', [(0, [0, 0, 0]), (1.36, [0, 0, 0]), (1.55, [0, 0, 18]), (1.8, [0, 0, 0]), (2.1, [0, 0, -10]),
                  (2.55, [0, 0, 0]), (4.2, [0, 0, 0])])
    # jump with a full spin
    a.pos('root', [(0, [0, 0, 0]), (1.7, [0, 0, 0]), (1.76, [0, -0.8, 0], 'easeOutQuad'),
                   (2.12, [0, 9, 0], 'easeOutQuad'), (2.5, [0, 0, 0], 'easeInQuad'), (4.2, [0, 0, 0])], 'lin')
    a.rot('root', [(0, [0, 0, 0]), (1.78, [0, 0, 0]), (2.48, [0, 360, 0], 'easeInOutSine'),
                   (2.48 + EPS, [0, 0, 0], 'lin'), (4.2, [0, 0, 0])], 'lin')
    for leg, s in (('leg_r', 1), ('leg_l', -1)):
        a.rot(leg, [(0, [0, 0, 0]), (1.78, [0, 0, 0]), (2.0, [-30, 0, 10 * s]), (2.4, [-10, 0, 4 * s]),
                    (2.52, [0, 0, 0]), (4.2, [0, 0, 0])])
    a.sound(0.15, 'paper').sound(1.38, 'gasp').sound(1.78, 'jump').sound(2.0, 'cheer').sound(2.52, 'plop')
    a.sound(3.22, 'rummage').sound(3.55, 'determined')
    return a


def celebrate():
    """Arrival at the Taco Truck."""
    a = Anim('celebrate', 3.2, 'hold')
    a.pos('root', [(0, [0, 0, 0]), (0.25, [0, -0.8, 0], 'easeOutQuad'), (0.55, [0, 7, 0], 'easeOutQuad'),
                   (0.85, [0, 0, 0], 'easeInQuad'), (1.0, [0, -0.6, 0], 'easeOutQuad'),
                   (1.35, [0, 10, 0], 'easeOutQuad'), (1.75, [0, 0, 0], 'easeInQuad'),
                   (1.95, [0, 0, 0]), (2.15, [0, 1.2, 0]), (2.35, [0, 0, 0]), (2.55, [0, 1.2, 0]),
                   (2.75, [0, 0, 0]), (3.2, [0, 0, 0])], 'lin')
    a.rot('root', [(0, [0, 0, 0]), (1.0, [0, 0, 0]), (1.72, [0, -360, 0], 'easeInOutSine'),
                   (1.72 + EPS, [0, 0, 0], 'lin'), (3.2, [0, 0, 0])], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (0.25, [1.12, 0.88, 1.12]), (0.45, [0.92, 1.1, 0.92]), (0.85, [1, 1, 1]),
                     (0.9, [1.14, 0.86, 1.14], 'easeOutQuad'), (1.05, [0.92, 1.1, 0.92], 'easeOutQuad'),
                     (1.75, [1, 1, 1]), (1.8, [1.16, 0.84, 1.16], 'easeOutQuad'),
                     (2.0, [1, 1, 1], 'easeOutBack'), (3.2, [1, 1, 1])], 'lin')
    for arm, s in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [0, 0, 0]), (0.3, [-170, 0, 35 * s], 'easeOutBack'), (0.85, [-170, 0, 35 * s]),
                    (1.0, [-150, 0, 30 * s]), (1.35, [-175, 0, 12 * s]), (1.8, [-160, 0, 24 * s]),
                    (2.0, [-140, 0, 40 * s]), (2.2, [-170, 0, 10 * s]), (2.4, [-140, 0, 40 * s]),
                    (2.6, [-170, 0, 10 * s]), (2.8, [-140, 0, 40 * s]), (3.2, [0, 0, 0], 'easeInOutSine')], 'lin')
    a.rot('torso', [(0, [0, 0, 0]), (1.9, [0, 0, 0]), (2.1, [0, 0, 8]), (2.3, [0, 0, -8]), (2.5, [0, 0, 8]),
                    (2.7, [0, 0, -8]), (3.0, [0, 0, 0]), (3.2, [0, 0, 0])])
    a.pos('hat', [(0, [0, 0, 0]), (0.5, [0, 0, 0]), (0.7, [0, 4, 0]), (0.9, [0, 0, 0]), (1.3, [0, 0, 0]),
                  (1.6, [0, 8, 0], 'easeOutQuad'), (2.0, [0, 0, 0], 'easeOutBounce'), (3.2, [0, 0, 0])], 'lin')
    a.rot('hat', [(0, [0, 0, 0]), (1.3, [0, 0, 0]), (2.0, [0, 0, 360], 'easeOutQuad'),
                  (2.0 + EPS, [0, 0, 0], 'lin'), (3.2, [0, 0, 0])], 'lin')
    for brow in ('brow_r', 'brow_l'):
        a.pos(brow, [(0, [0, 0, 0]), (0.2, [0, 1.4, 0], 'easeOutBack'), (2.9, [0, 1.2, 0]), (3.2, [0, 0, 0])], 'lin')
    a.sound(0.3, 'jump').sound(0.4, 'cheer').sound(1.05, 'jump').sound(1.78, 'plop').sound(2.0, 'cheer')
    return a


ANIMS = [walk, idle, sit_down, grill, pack_up, receive_coupon, celebrate]


def build():
    data = {'format_version': '1.8.0', 'animations': {}}
    for fn in ANIMS:
        a = fn()
        data['animations'][PREFIX + a.name] = a.compile()
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, 'w') as f:
        json.dump(data, f, indent='\t')
    return OUT


if __name__ == '__main__':
    print(build())
