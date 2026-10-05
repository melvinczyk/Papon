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

from gen_model import JARRITOS, PROP_BONES

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'src/main/resources/assets/papon/animations/entity/wandering_papon.animation.json')
PREFIX = 'animation.wandering_papon.'
EPS = 0.02  # width of an instant visibility swap


def r4(x):
    return round(float(x), 4)


def guard_brows(compiled, half=3.0, max_tilt=20.0, floor=0.0):
    """Keeps a Papon's brows off his eyes, which sit right under them: tilts are capped at
    `max_tilt` degrees, and at every brow key the brow is lifted so that its lower end (half a
    brow, `half` px, from the pivot) never goes below `floor` px from where it rests. A frown still
    reads (the inner ends come down to the rest line), the eyes stay visible.
    `compiled` is one animation as written to JSON; it is changed in place and returned."""
    import math as _m
    for brow in ('brow_r', 'brow_l'):
        ch = compiled.get('bones', {}).get(brow)
        if not ch:
            continue
        rot = ch.get('rotation', {})
        pos = ch.get('position', {})
        rkeys = sorted((float(k), v['vector']) for k, v in rot.items())
        pkeys = sorted((float(k), v) for k, v in pos.items())

        def tilt(t):
            if not rkeys:
                return 0.0
            if t <= rkeys[0][0]:
                return rkeys[0][1][2]
            for (t0, a), (t1, b) in zip(rkeys, rkeys[1:]):
                if t0 <= t <= t1:
                    u = (t - t0) / max(t1 - t0, 1e-9)
                    return a[2] + (b[2] - a[2]) * u
            return rkeys[-1][1][2]

        def ypos(t):
            if not pkeys:
                return 0.0
            if t <= pkeys[0][0]:
                return pkeys[0][1]['vector'][1]
            for (t0, a), (t1, b) in zip(pkeys, pkeys[1:]):
                if t0 <= t <= t1:
                    u = (t - t0) / max(t1 - t0, 1e-9)
                    return a['vector'][1] + (b['vector'][1] - a['vector'][1]) * u
            return pkeys[-1][1]['vector'][1]

        for v in rot.values():
            v['vector'][2] = r4(max(-max_tilt, min(max_tilt, v['vector'][2])))
        times = sorted(set([t for t, _ in pkeys] + [t for t, _ in rkeys]))
        out = {}
        for t in times:
            z = max(-max_tilt, min(max_tilt, tilt(t)))
            old = next((v for tt, v in pkeys if tt == t), None)
            x, y, zz = old['vector'] if old else [0.0, ypos(t), 0.0]
            y = max(y, floor) + half * _m.sin(_m.radians(abs(z)))
            key = dict(old) if old else {}
            key['vector'] = [r4(x), r4(y), r4(zz)]
            out['%.4g' % t if t else '0.0'] = key
        ch['position'] = out
    return compiled


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
        bones = {b: {ch: dict(sorted(keys.items(), key=lambda kv: float(kv[0]))) for ch, keys in c.items()}
                 for b, c in self.bones.items()}
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
    blink(a, 2.2)
    return a


def blink(a, t):
    a.scale('eyelids', [(0, [0, 0, 0]), (t, [0, 0, 0]), (t + 0.05, [1, 1, 1]), (t + 0.13, [1, 1, 1]),
                        (t + 0.18, [0, 0, 0])], 'lin')


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


COUPON_UP = [-100, -52, 0]    # coupon held up in front of his face, as in receive_coupon
BUSTED_BACK = [0, 0, 3.5]     # where busted leaves him: a step back from the window; nervous keeps it


def queue_idle():
    """Waiting in line: taps his foot, takes the coupon out to admire it, puts it away."""
    a = Anim('queue_idle', 6.0, True)
    a.rot('leg_l', [(0, [0, 0, 0]), (0.4, [0, 0, 0])] + [(round(0.55 + 0.25 * i, 2), [-14 if i % 2 == 0 else 0, 0, 0])
                                                        for i in range(6)] + [(2.2, [0, 0, 0]), (6.0, [0, 0, 0])])
    for i in range(3):
        a.sound(0.55 + 0.5 * i, 'tap')
    a.vis('coupon', False, [(2.6, True), (4.3, False)])
    a.rot('arm_r', [(0, [0, 0, 1]), (2.3, [0, 0, 1]), (2.6, [62, 0, 22], 'easeInOutSine'), (2.95, COUPON_UP, 'easeOutBack'),
                    (3.9, COUPON_UP), (4.3, [62, 0, 22], 'easeInOutSine'), (4.6, [0, 0, 1], 'easeInOutSine'),
                    (6.0, [0, 0, 1])], 'lin')
    a.rot('torso', [(0, [0, 0, 0]), (2.9, [0, 0, 0]), (3.2, [6, 0, 0]), (3.8, [6, 0, 0]), (4.2, [0, 0, 0]), (6.0, [0, 0, 0])])
    for brow in ('brow_r', 'brow_l'):
        a.pos(brow, [(0, [0, 0, 0]), (3.0, [0, 0, 0]), (3.15, HAPPY_BROW), (3.3, [0, 1.2, 0], 'easeOutBack'),
                     (3.9, [0, 1.0, 0]), (4.3, [0, 0, 0]), (6.0, [0, 0, 0])], 'lin')
    a.rot('root', [(0, [0, 0, 0]), (4.6, [0, 0, 0]), (5.0, [0, -14, 0], 'easeInOutSine'), (5.5, [0, -14, 0]),
                   (6.0, [0, 0, 0], 'easeInOutSine')], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (3.0, [1.015, 1.03, 1.015]), (6.0, [1, 1, 1])])
    a.rot('hat', [(0, [0, 0, 0]), (0.55, [0, 0, 1.5]), (0.8, [0, 0, -1]), (1.05, [0, 0, 1.5]), (1.3, [0, 0, -1]),
                  (1.8, [0, 0, 0]), (6.0, [0, 0, 0])])
    a.sound(2.6, 'rummage').sound(2.95, 'paper').sound(3.3, 'hum')
    return a


def present_coupon():
    """2.4 s. At the window: stands tall and holds the coupon up to the counter. It is taken at 1.6 s."""
    a = Anim('present_coupon', 2.4, 'hold')
    a.vis('coupon', False, [(0.35, True), (1.6, False)])
    a.rot('arm_r', [(0, [0, 0, 1]), (0.35, [62, 0, 22], 'easeInOutSine'), (0.8, [-165, -16, 6], 'easeOutBack'),
                    (1.6, [-165, -16, 6]), (2.0, [-20, 0, 6], 'easeInOutSine'), (2.4, [-10, 0, 4])], 'lin')
    a.rot('arm_l', [(0, [0, 0, 0]), (0.8, [-20, 0, -14]), (2.4, [-10, 0, -6])])
    a.scale('body', [(0, [1, 1, 1]), (0.25, [0.95, 1.08, 0.95], 'easeOutBack'), (0.6, [1, 1.03, 1]), (2.4, [1, 1.02, 1])], 'lin')
    for brow in ('brow_r', 'brow_l'):
        a.pos(brow, [(0, [0, 0, 0]), (0.3, [0, 1.4, 0], 'easeOutBack'), (2.4, [0, 1.2, 0])], 'lin')
    a.rot('torso', [(0, [0, 0, 0]), (0.8, [-6, 0, 0]), (1.6, [-6, 0, 0]), (2.0, [0, 0, 0]), (2.4, [0, 0, 0])])
    a.pos('hat', [(0, [0, 0, 0]), (0.25, [0, 1.2, 0]), (0.5, [0, 0, 0]), (2.4, [0, 0, 0])])
    a.sound(0.35, 'rummage').sound(0.8, 'paper').sound(1.0, 'hum')
    return a


def busted():
    """3.0 s. "FRAUD!": his brows hit the hat, the hat leaves, he backs away from the window, sweating."""
    a = Anim('busted', 3.0, 'hold')
    for brow, s in (('brow_r', 1), ('brow_l', -1)):
        a.pos(brow, [(0, [0, 1.2, 0]), (0.12, [0, 2.2, 0], 'easeOutBack'), (3.0, [0, 1.8, 0])], 'lin')
        a.rot(brow, [(0, [0, 0, 0]), (0.2, [0, 0, -14 * s]), (3.0, [0, 0, -14 * s])])
    a.scale('body', [(0, [1, 1.02, 1]), (0.1, [0.88, 1.16, 0.88], 'easeOutQuad'), (0.35, [1, 1, 1], 'easeOutBack'),
                     (3.0, [1, 1, 1])], 'lin')
    a.pos('hat', [(0, [0, 0, 0]), (0.1, [0, 0, 0]), (0.35, [0, 9, 0], 'easeOutQuad'), (0.8, [0, 0, 0], 'easeOutBounce'),
                  (3.0, [0, 0, 0])], 'lin')
    a.rot('hat', [(0, [0, 0, 0]), (0.1, [0, 0, 0]), (0.8, [0, 0, -30]), (1.0, [0, 0, -6]), (3.0, [0, 0, -6])])
    a.pos('root', [(0, [0, 0, 0]), (0.4, [0, 0, 0]), (0.6, [0, 0.8, 1.6], 'easeOutQuad'), (0.8, [0, 0, 2.2], 'easeInQuad'),
                   (1.0, [0, 0.8, 3.0], 'easeOutQuad'), (1.2, BUSTED_BACK, 'easeInQuad'), (3.0, BUSTED_BACK)], 'lin')
    for leg, s in (('leg_r', 1), ('leg_l', -1)):
        a.rot(leg, [(0, [0, 0, 0]), (0.5, [18 * s, 0, 0]), (0.7, [-18 * s, 0, 0]), (0.9, [18 * s, 0, 0]), (1.1, [-18 * s, 0, 0]),
                    (1.25, [0, 0, 0]), (3.0, [0, 0, 0])])
    guard = [-80, 0, 18]
    a.rot('arm_r', [(0, [-10, 0, 4]), (0.25, guard, 'easeOutBack'), (3.0, guard)], 'lin')
    a.rot('arm_l', [(0, [-10, 0, -6]), (0.25, [guard[0], 0, -guard[2]], 'easeOutBack'), (3.0, [guard[0], 0, -guard[2]])], 'lin')
    a.rot('root', [(0, [0, 0, 0]), (1.3, [0, 0, 0]), (1.6, [0, 24, 0], 'easeInOutSine'), (2.0, [0, 24, 0]),
                   (2.4, [0, -22, 0], 'easeInOutSine'), (2.7, [0, -22, 0]), (3.0, [0, 0, 0], 'easeInOutSine')], 'lin')
    a.sound(0.05, 'gasp').sound(0.35, 'pop').sound(0.6, 'jump').sound(1.0, 'jump').sound(1.6, 'sigh')
    return a


def nervous():
    """2.0 s loop after busted: still backed off, trembling, glancing about for the exit."""
    a = Anim('nervous', 2.0, True)
    a.pos('root', [(0, BUSTED_BACK)], 'lin')
    a.rot('torso', [(round(0.1 * i, 2), [0, 0, 2 if i % 2 == 0 else -2]) for i in range(21)])
    for brow, s in (('brow_r', 1), ('brow_l', -1)):
        a.pos(brow, [(0, [0, 1.8, 0]), (1.0, [0, 1.6, 0]), (2.0, [0, 1.8, 0])])
        a.rot(brow, [(0, [0, 0, -14 * s])], 'lin')
    guard = [-80, 0, 18]
    a.rot('arm_r', [(0, guard), (1.0, [-74, 0, 22]), (2.0, guard)])
    a.rot('arm_l', [(0, [guard[0], 0, -guard[2]]), (1.0, [-74, 0, -22]), (2.0, [guard[0], 0, -guard[2]])])
    a.rot('root', [(0, [0, 0, 0]), (0.4, [0, 18, 0]), (0.8, [0, 18, 0]), (1.2, [0, -16, 0]), (1.6, [0, -16, 0]),
                   (2.0, [0, 0, 0])])
    a.rot('hat', [(0, [0, 0, -6])], 'lin')
    return a


# ---------------------------------------------------------------- the llama raft
# While rafting the entity floats with its feet at the water line (y=0 here). He sits on the
# llama's back (top at y=3), so root drops by 1 (his body bottom is at y=4 in the rig).
RAFT_ROOT = [0, -1, 2]
RAFT_LEG_POS = [0, 3, -5]
RAFT_LEG_R = [-80, 45, 0]
RAFT_LEG_L = [-80, -45, 0]
ROW_READY_R = [-40, 0, 28]
ROW_READY_L = [-40, 0, -28]


def vis_jarritos(a, start, changes):
    for j in JARRITOS:
        a.vis(j, start, changes)


def raft_sit(a, t0, t1):
    a.pos('root', [(t0, RAFT_ROOT), (t1, RAFT_ROOT)], 'lin')
    for leg, rot in (('leg_r', RAFT_LEG_R), ('leg_l', RAFT_LEG_L)):
        a.rot(leg, [(t0, rot), (t1, rot)], 'lin')
        a.pos(leg, [(t0, RAFT_LEG_POS), (t1, RAFT_LEG_POS)], 'lin')


def bob(a, bone, length, amp, base=(0, 0, 0), cycles=1):
    """A seamless sine-like bob on y: `cycles` full periods across `length`."""
    p = length / cycles
    keys = []
    for c in range(cycles):
        for i, dy in enumerate((0, amp, 0, -amp)):
            keys.append((c * p + i * p / 4, [base[0], base[1] + dy, base[2]]))
    keys.append((length, list(base)))
    a.pos(bone, keys)


def llama_paddle(a, length, cycles=2, amp=24):
    """Doggy-paddle: front and back legs sweep fore and aft out of phase."""
    p = length / cycles
    for leg, sign, flip in (('llama_leg_fr', 1, False), ('llama_leg_fl', -1, True), ('llama_leg_br', 1, True),
                            ('llama_leg_bl', -1, False)):
        k0 = [0, amp * sign, 10 * sign]
        k1 = [0, -amp * sign, -6 * sign]
        if flip:
            k0, k1 = k1, k0
        keys = []
        for c in range(cycles):
            keys += [(c * p, k0), (c * p + p / 2, k1)]
        keys.append((length, k0))
        a.rot(leg, keys)


def raft_board():
    """In the water: pulls the llama out of his backpack, it inflates, he hops on and grabs the paddles."""
    a = Anim('raft_board', 1.6, 'hold')
    a.vis('raft', False, [(0.4, True)])
    a.pos('raft', [(0, [0, 14, 12]), (0.4, [0, 14, 12]), (0.85, [0, 0, 0], 'easeOutBack:1.4'), (1.6, [0, 0, 0])], 'lin')
    a.scale('raft', [(0, [0.05, 0.05, 0.05]), (0.4, [0.05, 0.05, 0.05]), (0.95, [1, 1, 1], 'easeOutElastic:1'),
                     (1.28, [1, 1, 1]), (1.34, [1.12, 0.8, 1.12], 'easeOutQuad'), (1.5, [1, 1, 1], 'easeOutBack'),
                     (1.6, [1, 1, 1])], 'lin')
    a.rot('llama_neck', [(0, [0, 0, 0]), (0.9, [0, 0, 0]), (1.05, [-12, 0, 0]), (1.25, [6, 0, 0]),
                         (1.45, [0, 0, 0]), (1.6, [0, 0, 0])])
    a.rot('llama_head', [(0, [0, 0, 0]), (0.95, [0, 0, 0]), (1.1, [0, 35, 0]), (1.4, [0, 35, 0]),
                         (1.6, [0, 0, 0])])
    a.rot('arm_r', [(0, [0, 0, 0]), (0.3, [65, 0, 28], 'easeInOutSine'), (0.5, [-30, 0, 10], 'easeOutBack'),
                    (1.0, [-150, 0, 20], 'easeOutQuad'), (1.3, [-60, 0, 25]), (1.45, [50, 0, 20], 'easeInOutSine'),
                    (1.6, ROW_READY_R, 'easeOutBack')], 'lin')
    a.rot('arm_l', [(0, [0, 0, 0]), (0.9, [0, 0, 0]), (1.0, [-150, 0, -20], 'easeOutQuad'), (1.3, [-60, 0, -25]),
                    (1.45, [50, 0, -20], 'easeInOutSine'), (1.6, ROW_READY_L, 'easeOutBack')], 'lin')
    a.vis('paddle_r', False, [(1.45, True)])
    a.vis('paddle_l', False, [(1.45, True)])
    a.pos('root', [(0, [0, 0, 0]), (0.9, [0, 0, 0]), (0.97, [0, -1, 0], 'easeOutQuad'),
                   (1.12, [0, 8, 1], 'easeOutQuad'), (1.3, RAFT_ROOT, 'easeInQuad'), (1.6, RAFT_ROOT)], 'lin')
    for leg, rot in (('leg_r', RAFT_LEG_R), ('leg_l', RAFT_LEG_L)):
        a.rot(leg, [(0, [0, 0, 0]), (1.0, [0, 0, 0]), (1.3, rot, 'easeOutQuad'), (1.6, rot)], 'lin')
        a.pos(leg, [(0, [0, 0, 0]), (1.0, [0, 0, 0]), (1.3, RAFT_LEG_POS, 'easeOutQuad'), (1.6, RAFT_LEG_POS)], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (0.95, [1, 1, 1]), (1.0, [1.1, 0.9, 1.1]), (1.12, [0.92, 1.1, 0.92]),
                     (1.32, [1.12, 0.88, 1.12], 'easeOutQuad'), (1.5, [1, 1, 1], 'easeOutBack'), (1.6, [1, 1, 1])], 'lin')
    for brow in ('brow_r', 'brow_l'):
        a.pos(brow, [(0, [0, 0, 0]), (0.45, [0, 1.2, 0], 'easeOutBack'), (1.3, [0, 0.6, 0]), (1.6, [0, 0.4, 0])], 'lin')
    a.sound(0.15, 'rummage').sound(0.45, 'boing').sound(0.9, 'llama').sound(1.0, 'jump').sound(1.32, 'plop')
    return a


def raft():
    """Rowing loop: alternate paddles, llama doggy-paddles, everything bobs on the swell."""
    L = 1.2
    a = Anim('raft', L, True)
    a.vis('raft', True, [])
    a.vis('paddle_r', True, [])
    a.vis('paddle_l', True, [])
    for leg, rot in (('leg_r', RAFT_LEG_R), ('leg_l', RAFT_LEG_L)):
        a.rot(leg, [(0, rot)], 'lin')
        a.pos(leg, [(0, RAFT_LEG_POS)], 'lin')
    # one stroke per half cycle: reach forward, dig, pull back
    a.rot('arm_r', [(0, [-55, 0, 30]), (0.3, [30, 0, 34]), (0.6, [10, 0, 20]), (0.9, [-55, 0, 24]), (L, [-55, 0, 30])])
    a.rot('arm_l', [(0, [10, 0, -20]), (0.3, [-55, 0, -24]), (0.6, [-55, 0, -30]), (0.9, [30, 0, -34]),
                    (L, [10, 0, -20])])
    a.rot('torso', [(0, [6, -6, 3]), (0.3, [-4, 6, -2]), (0.6, [6, 6, -3]), (0.9, [-4, -6, 2]), (L, [6, -6, 3])])
    bob(a, 'raft', L, 0.6)
    bob(a, 'root', L, 0.6, RAFT_ROOT)
    a.rot('raft', [(0, [0, 0, 1.5]), (0.6, [0, 0, -1.5]), (L, [0, 0, 1.5])])
    llama_paddle(a, L, 2)
    a.rot('llama_neck', [(0, [4, 0, 0]), (0.3, [-3, 0, 0]), (0.6, [4, 0, 0]), (0.9, [-3, 0, 0]), (L, [4, 0, 0])])
    a.rot('llama_head', [(0, [-4, 0, 0]), (0.3, [3, 0, 0]), (0.6, [-4, 0, 0]), (0.9, [3, 0, 0]), (L, [-4, 0, 0])])
    a.rot('llama_tail', [(0, [0, 15, 0]), (0.3, [0, -15, 0]), (0.6, [0, 15, 0]), (0.9, [0, -15, 0]), (L, [0, 15, 0])])
    a.pos('brow_r', [(0, [0, 0.4, 0])], 'lin')
    a.pos('brow_l', [(0, [0, 0.4, 0])], 'lin')
    a.rot('hat', [(0, [0, 0, -2]), (0.6, [0, 0, 2]), (L, [0, 0, -2])])
    a.sound(0.28, 'splash').sound(0.88, 'splash')
    return a


def raft_idle():
    """Nobody with him on the water: he leans back and sunbathes; the llama looks around."""
    L = 4.0
    a = Anim('raft_idle', L, True)
    a.vis('raft', True, [])
    for leg, rot in (('leg_r', RAFT_LEG_R), ('leg_l', RAFT_LEG_L)):
        a.rot(leg, [(0, rot)], 'lin')
        a.pos(leg, [(0, RAFT_LEG_POS)], 'lin')
    a.rot('torso', [(0, [-14, 0, 0])], 'lin')
    a.rot('arm_r', [(0, [-150, 0, -20])], 'lin')     # hands behind his head
    a.rot('arm_l', [(0, [-150, 0, 20])], 'lin')
    bob(a, 'raft', L, 0.4, cycles=2)
    bob(a, 'root', L, 0.4, RAFT_ROOT, cycles=2)
    a.rot('raft', [(0, [0.8, 0, 1]), (1.0, [-0.8, 0, -1]), (2.0, [0.8, 0, 1]), (3.0, [-0.8, 0, -1]),
                   (L, [0.8, 0, 1])])
    a.rot('llama_head', [(0, [0, 0, 0]), (0.8, [0, 0, 0]), (1.3, [0, 40, 4], 'easeInOutSine'),
                         (2.2, [0, 40, 4], 'easeInOutSine'), (2.8, [0, -35, -4], 'easeInOutSine'),
                         (3.5, [0, -35, -4], 'easeInOutSine'), (L, [0, 0, 0], 'easeInOutSine')], 'lin')
    a.rot('llama_tail', [(0, [0, 0, 0]), (1.0, [0, 20, 0]), (1.2, [0, -20, 0]), (1.4, [0, 0, 0]), (L, [0, 0, 0])])
    a.rot('hat', [(0, [22, 0, 0])], 'lin')            # hat tipped over his eyes
    a.pos('hat', [(0, [0, -1.5, -1])], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (2.0, [1.01, 1.025, 1.01]), (L, [1, 1, 1])])
    a.sound(1.2, 'llama')
    return a


def raft_land():
    """Just hopped onto the shore: shakes off, and the llama shrinks back into the pack."""
    a = Anim('raft_land', 1.4, 'hold')
    a.vis('raft', True, [(1.05, False)])
    a.pos('raft', [(0, [0, -10, 18]), (0.45, [0, -10, 18]), (1.0, [0, 14, 12], 'easeInBack')], 'lin')
    a.scale('raft', [(0, [1, 1, 1]), (0.45, [1, 1, 1]), (1.0, [0.05, 0.05, 0.05], 'easeInBack')], 'lin')
    a.rot('llama_head', [(0, [0, 0, 0]), (0.15, [0, 0, 20]), (0.3, [0, 0, -20]), (0.45, [0, 0, 0]), (1.4, [0, 0, 0])])
    a.vis('paddle_r', True, [(0.1, False)])
    a.vis('paddle_l', True, [(0.1, False)])
    a.rot('torso', [(0, [0, 0, 0]), (0.5, [0, 0, 0]), (0.6, [0, 0, 12]), (0.7, [0, 0, -12]), (0.8, [0, 0, 10]),
                    (0.9, [0, 0, -10]), (1.0, [0, 0, 6]), (1.1, [0, 0, -4]), (1.25, [0, 0, 0]), (1.4, [0, 0, 0])])
    a.rot('arm_r', [(0, [0, 0, 0]), (0.5, [0, 0, 10]), (0.75, [0, 0, 30]), (1.0, [0, 0, 10]), (1.4, [0, 0, 0])])
    a.rot('arm_l', [(0, [0, 0, 0]), (0.5, [0, 0, -10]), (0.75, [0, 0, -30]), (1.0, [0, 0, -10]), (1.4, [0, 0, 0])])
    a.rot('hat', [(0, [0, 0, 0]), (0.6, [0, 0, -8]), (0.7, [0, 0, 8]), (0.8, [0, 0, -6]), (0.9, [0, 0, 6]),
                  (1.1, [0, 0, 0]), (1.4, [0, 0, 0])])
    a.scale('body', [(0, [1.1, 0.9, 1.1]), (0.15, [1, 1, 1], 'easeOutBack'), (1.4, [1, 1, 1])], 'lin')
    a.sound(0.0, 'plop').sound(0.55, 'shake_off').sound(0.95, 'pop')
    return a


# ---------------------------------------------------------------- Jarritos
CHUG_R = [-112, -52, 0]


def run():
    """Jarrito zoomies: faster, bigger stride, leaning into it."""
    L = 0.5
    a = Anim('run', L, True)
    a.rot('leg_r', [(0, [36, 0, 0]), (L / 2, [-36, 0, 0]), (L, [36, 0, 0])])
    a.rot('leg_l', [(0, [-36, 0, 0]), (L / 2, [36, 0, 0]), (L, [-36, 0, 0])])
    a.rot('arm_r', [(0, [-50, 0, 10]), (L / 2, [40, 0, 6]), (L, [-50, 0, 10])])
    a.rot('arm_l', [(0, [40, 0, -6]), (L / 2, [-50, 0, -10]), (L, [40, 0, -6])])
    a.pos('root', [(0, [0, 0, 0]), (L / 4, [0, 2, 0]), (L / 2, [0, 0, 0]), (3 * L / 4, [0, 2, 0]), (L, [0, 0, 0])])
    a.rot('torso', [(0, [12, 6, -4]), (L / 2, [12, -6, 4]), (L, [12, 6, -4])])
    a.scale('body', [(0, [1.06, 0.94, 1.06]), (L / 4, [0.96, 1.05, 0.96]), (L / 2, [1.06, 0.94, 1.06]),
                     (3 * L / 4, [0.96, 1.05, 0.96]), (L, [1.06, 0.94, 1.06])])
    a.rot('hat', [(0, [-10, 0, 0]), (L / 4, [-16, 0, 2]), (L / 2, [-10, 0, 0]), (3 * L / 4, [-16, 0, -2]),
                  (L, [-10, 0, 0])])
    a.pos('hat', [(0, [0, 0, 0.5]), (L / 4, [0, 0.8, 1]), (L / 2, [0, 0, 0.5]), (3 * L / 4, [0, 0.8, 1]),
                  (L, [0, 0, 0.5])])
    a.pos('brow_r', [(0, [0, 0.8, 0])], 'lin')
    a.pos('brow_l', [(0, [0, 0.8, 0])], 'lin')
    a.pos('backpack', [(0, [0, -0.3, 0.3]), (L / 4, [0, 0.8, 0.8]), (L / 2, [0, -0.3, 0.3]),
                       (3 * L / 4, [0, 0.8, 0.8]), (L, [0, -0.3, 0.3])])
    return a


def drink():
    """Studies the bottle, chugs it in three gulps, tosses it, burps, and crouches to sprint."""
    a = Anim('drink', 2.4, 'hold')
    vis_jarritos(a, True, [(1.9, False)])
    a.rot('arm_r', [(0, [-30, 0, 4]), (0.35, [-80, -45, 0], 'easeOutBack'), (0.5, [-80, -45, 0]),
                    (0.7, CHUG_R, 'easeInOutSine'), (1.6, CHUG_R), (1.75, [-80, -10, 10], 'easeInOutSine'),
                    (1.9, [60, 0, 30], 'easeOutQuad'), (2.15, [0, 0, 4], 'easeInOutSine'), (2.4, [20, 0, 6])], 'lin')
    a.rot('torso', [(0, [0, 0, 0]), (0.35, [6, 0, 0]), (0.7, [-16, 0, 0], 'easeInOutSine'), (1.6, [-18, 0, 0]),
                    (1.8, [0, 0, 0], 'easeInOutSine'), (2.0, [-6, 0, 0]), (2.15, [0, 0, 0]),
                    (2.4, [14, 0, 0], 'easeOutBack')], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (0.75, [1, 1, 1]), (0.85, [1.04, 0.96, 1.04]), (0.95, [1, 1, 1]),
                     (1.15, [1.04, 0.96, 1.04]), (1.25, [1, 1, 1]), (1.45, [1.04, 0.96, 1.04]), (1.55, [1, 1, 1]),
                     (1.98, [1, 1, 1]), (2.05, [0.9, 1.14, 0.9], 'easeOutQuad'), (2.2, [1, 1, 1], 'easeOutBack'),
                     (2.4, [1.05, 0.95, 1.05])], 'lin')
    for brow in ('brow_r', 'brow_l'):
        a.pos(brow, [(0, [0, 0, 0]), (0.35, [0, 1.0, 0], 'easeOutBack'), (0.7, [0, 0, 0]), (1.6, [0, 0, 0]),
                     (2.0, [0, 0, 0]), (2.06, [0, 1.6, 0], 'easeOutBack'), (2.4, [0, 0.8, 0])], 'lin')
    a.scale('eyelids', [(0, [0, 0, 0]), (0.7, [0, 0, 0]), (0.8, [1, 1, 1]), (1.6, [1, 1, 1]), (1.7, [0, 0, 0]),
                        (2.4, [0, 0, 0])], 'lin')
    a.pos('root', [(0, [0, 0, 0]), (2.2, [0, 0, 0]), (2.4, [0, -1.2, 0], 'easeOutQuad')], 'lin')
    for leg, s in (('leg_r', 1), ('leg_l', -1)):
        a.rot(leg, [(0, [0, 0, 0]), (2.2, [0, 0, 0]), (2.4, [-20 * s, 0, 0])])
    a.rot('hat', [(0, [0, 0, 0]), (0.7, [-10, 0, 0]), (1.6, [-12, 0, 0]), (2.05, [0, 0, 8]), (2.4, [-6, 0, 0])])
    a.sound(0.3, 'uncap').sound(0.85, 'gulp').sound(1.15, 'gulp').sound(1.45, 'gulp').sound(1.9, 'toss')
    a.sound(2.02, 'burp')
    return a


def smash():
    """The yellow one. He takes it, realises, seethes, and smashes it on the ground. Impact at 1.4s."""
    a = Anim('smash', 3.0, 'hold')
    vis_jarritos(a, True, [(1.42, False)])
    a.rot('arm_r', [(0, [-30, 0, 4]), (0.35, [-80, -45, 0], 'easeOutBack'), (0.9, [-80, -45, 0]),
                    (1.0, [-84, -45, 4]), (1.05, [-76, -45, -4]), (1.1, [-84, -45, 4]),
                    (1.32, [-178, 0, 12], 'easeOutQuad'), (1.42, [25, 0, 6], 'easeInQuad'), (1.6, [10, 0, 4]),
                    (2.4, [10, 0, 4]), (2.7, [-70, 30, -40], 'easeInOutSine'), (3.0, [-70, 30, -40])], 'lin')
    a.rot('arm_l', [(0, [0, 0, 0]), (0.9, [0, 0, 0]), (1.0, [0, 0, -14]), (1.4, [0, 0, -14]),
                    (2.4, [0, 0, -6]), (2.7, [-70, -30, 40], 'easeInOutSine'), (3.0, [-70, -30, 40])], 'lin')
    for brow, s in (('brow_r', 1), ('brow_l', -1)):
        a.rot(brow, [(0, [0, 0, 0]), (0.35, [0, 0, 0]), (0.6, [0, 0, 18 * s], 'easeOutBack'),
                     (3.0, [0, 0, 18 * s])], 'lin')
        a.pos(brow, [(0, [0, 0, 0]), (0.35, [0, 0.8, 0]), (0.6, [0, -0.4, 0]), (3.0, [0, -0.4, 0])], 'lin')
    shake = [(0, [0, 0, 0]), (0.6, [0, 0, 0])]
    t = 0.6
    for i in range(6):
        t += 0.06
        shake.append((t, [0, 0, 2.5 if i % 2 == 0 else -2.5]))
    shake += [(1.0, [0, 0, 0]), (1.32, [-10, 0, 0], 'easeOutQuad'), (1.42, [18, 0, 0], 'easeInQuad'),
              (1.65, [6, 0, 0]), (2.4, [6, 0, 0]), (2.7, [-4, 0, 0]), (3.0, [-4, 0, 0])]
    a.rot('torso', shake, 'lin')
    a.scale('body', [(0, [1, 1, 1]), (0.6, [1, 1, 1]), (0.75, [1.05, 0.97, 1.05]), (0.9, [1, 1, 1]),
                     (1.32, [0.94, 1.08, 0.94]), (1.42, [1.12, 0.88, 1.12], 'easeInQuad'),
                     (1.6, [1, 1, 1], 'easeOutBack'), (2.7, [1, 1, 1]), (2.8, [1.04, 0.97, 1.04]),
                     (3.0, [1, 1, 1])], 'lin')
    stomps = [(0, [0, 0, 0]), (1.55, [0, 0, 0])]
    for t0, leg in ((1.6, 'r'), (1.85, 'l'), (2.1, 'r')):
        stomps += [(t0, [0, 0, 0]), (t0 + 0.1, [0, 3, 0], 'easeOutQuad'), (t0 + 0.18, [0, 0, 0], 'easeInQuad')]
    stomps.append((3.0, [0, 0, 0]))
    a.pos('root', stomps, 'lin')
    for leg, ts in (('leg_r', (1.6, 2.1)), ('leg_l', (1.85,))):
        keys = [(0, [0, 0, 0])]
        for t0 in ts:
            keys += [(t0, [0, 0, 0]), (t0 + 0.1, [-40, 0, 0], 'easeOutQuad'), (t0 + 0.18, [0, 0, 0], 'easeInQuad')]
        keys.append((3.0, [0, 0, 0]))
        a.rot(leg, keys, 'lin')
    a.pos('hat', [(0, [0, 0, 0]), (1.42, [0, 0, 0]), (1.5, [0, 2, 0], 'easeOutQuad'), (1.65, [0, 0, 0], 'easeInQuad'),
                  (3.0, [0, 0, 0])], 'lin')
    a.sound(0.4, 'notice').sound(0.65, 'grumble').sound(1.42, 'smash').sound(1.68, 'stomp').sound(1.93, 'stomp')
    a.sound(2.18, 'stomp').sound(2.7, 'huff')
    return a


def tired():
    """The Jarrito wears off: hunched, panting, wipes his brow, sighs, straightens up."""
    a = Anim('tired', 2.6, 'hold')
    a.rot('torso', [(0, [12, 0, 0]), (0.25, [24, 0, 0], 'easeOutQuad'), (1.1, [22, 0, 0]), (1.3, [8, 0, 0]),
                    (1.8, [8, 0, 0]), (2.0, [26, 0, 0]), (2.35, [0, 0, 0], 'easeOutBack'), (2.6, [0, 0, 0])], 'lin')
    a.rot('arm_r', [(0, [-20, 0, 6]), (0.25, [-18, 0, 4]), (1.1, [-18, 0, 4]), (1.35, [-160, -40, 0], 'easeOutQuad'),
                    (1.45, [-160, -60, 0]), (1.55, [-160, -25, 0]), (1.65, [-160, -55, 0]),
                    (1.9, [-10, 0, 4], 'easeInOutSine'), (2.6, [0, 0, 0])], 'lin')
    a.rot('arm_l', [(0, [-20, 0, -6]), (0.25, [-18, 0, -4]), (2.0, [-18, 0, -4]), (2.6, [0, 0, 0])])
    pant = [(0, [1, 1, 1])]
    t = 0.0
    while t < 1.1:
        pant += [(t + 0.09, [1.04, 0.95, 1.04]), (t + 0.18, [1, 1, 1])]
        t += 0.18
    pant += [(2.0, [1.02, 0.98, 1.02]), (2.2, [0.97, 1.03, 0.97]), (2.6, [1, 1, 1])]
    a.scale('body', pant)
    for brow, s in (('brow_r', 1), ('brow_l', -1)):
        a.rot(brow, [(0, [0, 0, -12 * s]), (2.2, [0, 0, -12 * s]), (2.6, [0, 0, 0])])
    a.scale('eyelids', [(0, [0.6, 0.6, 0.6]), (2.2, [0.6, 0.6, 0.6]), (2.35, [0, 0, 0]), (2.6, [0, 0, 0])], 'lin')
    a.rot('hat', [(0, [8, 0, 0]), (1.4, [8, 0, 0]), (1.55, [-6, 0, 4]), (1.8, [8, 0, 0]), (2.35, [0, 0, 0]),
                  (2.6, [0, 0, 0])])
    a.pos('root', [(0, [0, 0, 0]), (0.25, [0, -0.8, 0]), (2.2, [0, -0.8, 0]), (2.4, [0, 0, 0]), (2.6, [0, 0, 0])])
    a.sound(0.1, 'pant').sound(0.6, 'pant').sound(1.4, 'wipe').sound(2.05, 'sigh')
    return a


# ---------------------------------------------------------------- bedtime
# Lying on his back on the pad: root tipped back 90 degrees and lifted by half his depth.
LIE_ROOT_POS = [0, 8, 0]
LIE_ROOT_ROT = [-90, 0, 0]


def lying(a, t0, t1):
    a.pos('root', [(t0, LIE_ROOT_POS), (t1, LIE_ROOT_POS)], 'lin')
    a.rot('root', [(t0, LIE_ROOT_ROT), (t1, LIE_ROOT_ROT)], 'lin')


def sleep_setup():
    """Yawns, sets the pack down, unrolls the bag, lies down, pulls the cover over, falls asleep."""
    a = Anim('sleep_setup', 4.5, 'hold')
    # yawn and stretch
    a.rot('arm_r', [(0, [0, 0, 0]), (0.35, [-165, 0, 28], 'easeOutQuad'), (0.6, [-165, 0, 28]),
                    (0.8, [0, 0, 6], 'easeInOutSine'), (0.95, [65, 0, 28], 'easeInOutSine'),
                    (1.15, [-30, 0, 40], 'easeInOutSine'), (1.3, [-10, 0, 20]), (1.55, [-50, -20, 10]),
                    (1.9, [-50, -20, 10]), (2.2, [0, 0, 6]), (4.5, [0, 0, 6])], 'lin')
    a.rot('arm_l', [(0, [0, 0, 0]), (0.35, [-165, 0, -28], 'easeOutQuad'), (0.6, [-165, 0, -28]),
                    (0.8, [0, 0, -6], 'easeInOutSine'), (4.5, [0, 0, -6])], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (0.35, [0.94, 1.1, 0.94]), (0.6, [0.94, 1.1, 0.94]), (0.8, [1, 1, 1]),
                     (2.9, [1, 1, 1]), (2.96, [1.06, 0.94, 1.06]), (3.1, [1, 1, 1]), (3.9, [1, 1, 1]),
                     (4.2, [1.01, 1.02, 1.01]), (4.5, [1, 1, 1])])
    a.scale('eyelids', [(0, [0, 0, 0]), (0.25, [1, 1, 1]), (0.6, [1, 1, 1]), (0.75, [0, 0, 0]), (3.8, [0, 0, 0]),
                        (3.95, [0.5, 0.5, 0.5]), (4.05, [0.2, 0.2, 0.2]), (4.25, [1, 1, 1]), (4.5, [1, 1, 1])], 'lin')
    # pack off his back and onto the ground beside him
    a.vis('backpack', True, [(1.0, False)])
    a.vis('backpack_ground', False, [(1.0, True)])
    a.pos('backpack_ground', [(0, [14, 10, 0]), (1.0, [14, 10, 0]), (1.3, [0, 0, 0], 'easeOutBounce'),
                              (4.5, [0, 0, 0])], 'lin')
    # the bedroll unrolls behind him
    a.vis('sleeping_bag', False, [(1.4, True)])
    a.scale('sleeping_bag', [(0, [0, 0, 0]), (1.38, [0, 0, 0]), (1.4, [1, 1, 0.1], 'lin'),
                             (1.95, [1, 1, 1], 'easeOutBack'), (4.5, [1, 1, 1])], 'lin')
    # blanket pulled up from his feet to his chin
    a.scale('bag_cover', [(0, [0, 0, 0]), (3.18, [0, 0, 0]), (3.2, [1, 0.25, 0.05], 'lin'),
                          (3.65, [1, 1, 1], 'easeOutBack'), (4.5, [1, 1, 1])], 'lin')
    # lie back onto the pad
    a.pos('root', [(0, [0, 0, 0]), (2.3, [0, 0, 0]), (2.45, [0, -1, 0], 'easeOutQuad'),
                   (2.9, LIE_ROOT_POS, 'easeInQuad'), (2.98, [0, 7.4, 0]), (3.1, LIE_ROOT_POS), (4.5, LIE_ROOT_POS)], 'lin')
    a.rot('root', [(0, [0, 0, 0]), (2.45, [0, 0, 0]), (2.9, LIE_ROOT_ROT, 'easeInQuad'), (4.5, LIE_ROOT_ROT)], 'lin')
    a.rot('torso', [(0, [0, 0, 0]), (0.35, [-8, 0, 0]), (0.6, [-8, 0, 0]), (0.8, [0, 0, 0]), (1.55, [10, 10, 0]),
                    (1.9, [10, 10, 0]), (2.2, [0, 0, 0]), (4.5, [0, 0, 0])])
    for brow, s in (('brow_r', 1), ('brow_l', -1)):
        a.rot(brow, [(0, [0, 0, 0]), (3.8, [0, 0, 0]), (4.3, [0, 0, -8 * s]), (4.5, [0, 0, -8 * s])])
    a.rot('hat', [(0, [0, 0, 0]), (3.0, [0, 0, 0]), (3.3, [-30, 0, 0], 'easeOutBack'), (4.5, [-30, 0, 0])], 'lin')
    a.sound(0.15, 'yawn').sound(1.25, 'thud').sound(1.45, 'unroll').sound(2.9, 'plop').sound(3.4, 'rustle')
    return a


def sleep_pose(a, t0, t1):
    lying(a, t0, t1)
    a.vis('backpack', False, [])
    a.vis('backpack_ground', True, [])
    a.vis('sleeping_bag', True, [])
    a.rot('arm_r', [(t0, [0, 0, 6])], 'lin')
    a.rot('arm_l', [(t0, [0, 0, -6])], 'lin')
    a.rot('hat', [(t0, [-30, 0, 0])], 'lin')


def sleep():
    L = 4.0
    a = Anim('sleep', L, True)
    sleep_pose(a, 0, L)
    a.vis('eyelids', True, [])
    a.scale('bag_cover', [(0, [1, 1, 1]), (L / 2, [1.01, 1.05, 1.01]), (L, [1, 1, 1])])
    a.scale('body', [(0, [1, 1, 1]), (L / 2, [1.015, 1.03, 1.015]), (L, [1, 1, 1])])
    for brow, s in (('brow_r', 1), ('brow_l', -1)):
        a.rot(brow, [(0, [0, 0, -8 * s]), (2.6, [0, 0, -8 * s]), (2.7, [0, 0, -2 * s]), (2.85, [0, 0, -8 * s]),
                     (L, [0, 0, -8 * s])])
    a.rot('hat', [(0, [-30, 0, 0]), (L / 2, [-27, 0, 0]), (L, [-30, 0, 0])])
    a.sound(0.4, 'snore').sound(2.4, 'snore_out')
    return a


def sleep_flinch():
    """A chancla that did not wake him: a jolt, one eye flutters, back to sleep."""
    a = Anim('sleep_flinch', 0.8, 'hold')
    sleep_pose(a, 0, 0.8)
    a.pos('root', [(0, LIE_ROOT_POS), (0.06, [0, 10, 0], 'easeOutQuad'), (0.25, LIE_ROOT_POS, 'easeOutBounce'),
                   (0.8, LIE_ROOT_POS)], 'lin')
    a.scale('bag_cover', [(0, [1, 1, 1]), (0.06, [1.1, 0.85, 1.1]), (0.3, [1, 1, 1], 'easeOutBack'), (0.8, [1, 1, 1])], 'lin')
    a.scale('eyelids', [(0, [1, 1, 1]), (0.08, [0.3, 0.3, 0.3]), (0.3, [0.3, 0.3, 0.3]), (0.45, [1, 1, 1]),
                        (0.8, [1, 1, 1])], 'lin')
    for brow, s in (('brow_r', 1), ('brow_l', -1)):
        a.pos(brow, [(0, [0, 0, 0]), (0.08, [0, 1.2, 0]), (0.4, [0, 0, 0]), (0.8, [0, 0, 0])])
        a.rot(brow, [(0, [0, 0, -8 * s]), (0.08, [0, 0, 6 * s]), (0.5, [0, 0, -8 * s]), (0.8, [0, 0, -8 * s])])
    a.rot('hat', [(0, [-30, 0, 0]), (0.06, [-18, 0, 10]), (0.3, [-30, 0, 0]), (0.8, [-30, 0, 0])])
    a.sound(0.02, 'snort')
    return a


def pack_bed(a, t_roll, t_pack):
    """Rolls the bag up and swings the pack back on."""
    a.scale('sleeping_bag', [(0, [1, 1, 1]), (t_roll, [1, 1, 1]), (t_roll + 0.45, [1, 1, 0.1], 'easeInQuad'),
                             (t_roll + 0.47, [0, 0, 0], 'lin')], 'lin')
    a.vis('backpack_ground', True, [(t_pack, False)])
    a.vis('backpack', False, [(t_pack + 0.15, True)])


def wake_up():
    """Morning: eyes open, stretch, throws the cover off, sits up, rolls up, pack on."""
    a = Anim('wake_up', 4.0, 'hold')
    a.scale('eyelids', [(0, [1, 1, 1]), (0.3, [1, 1, 1]), (0.45, [0.4, 0.4, 0.4]), (0.6, [1, 1, 1]),
                        (0.75, [0, 0, 0]), (1.75, [0, 0, 0]), (1.85, [1, 1, 1]), (2.1, [1, 1, 1]), (2.2, [0, 0, 0])], 'lin')
    a.scale('bag_cover', [(0, [1, 1, 1]), (0.75, [1, 1, 1]), (1.05, [1, 0.25, 0.05], 'easeInQuad'),
                          (1.07, [0, 0, 0], 'lin')], 'lin')
    a.pos('root', [(0, LIE_ROOT_POS), (1.0, LIE_ROOT_POS), (1.5, [0, 0, 0], 'easeOutBack'), (4.0, [0, 0, 0])], 'lin')
    a.rot('root', [(0, LIE_ROOT_ROT), (1.0, LIE_ROOT_ROT), (1.5, [0, 0, 0], 'easeOutBack'), (4.0, [0, 0, 0])], 'lin')
    a.rot('arm_r', [(0, [0, 0, 6]), (0.7, [0, 0, 6]), (0.95, [-90, 0, 40], 'easeOutQuad'), (1.5, [0, 0, 6]),
                    (1.75, [-165, 0, 28], 'easeOutQuad'), (2.2, [-165, 0, 28]), (2.4, [0, 0, 6]),
                    (3.0, [-40, -20, 20]), (3.1, [65, 0, 28], 'easeInOutSine'), (3.4, [0, 0, 3], 'easeInOutSine'),
                    (4.0, [0, 0, 0])], 'lin')
    a.rot('arm_l', [(0, [0, 0, -6]), (1.5, [0, 0, -6]), (1.75, [-165, 0, -28], 'easeOutQuad'), (2.2, [-165, 0, -28]),
                    (2.4, [0, 0, -6]), (4.0, [0, 0, 0])], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (1.75, [0.94, 1.1, 0.94]), (2.2, [0.94, 1.1, 0.94]), (2.4, [1, 1, 1]),
                     (3.6, [1, 1, 1]), (3.7, [1.08, 0.92, 1.08]), (3.85, [1, 1, 1], 'easeOutBack'), (4.0, [1, 1, 1])], 'lin')
    pack_bed(a, 2.4, 3.1)
    a.pos('backpack_ground', [(0, [0, 0, 0]), (3.0, [0, 0, 0]), (3.1, [8, 8, 0])], 'lin')
    a.rot('hat', [(0, [-30, 0, 0]), (1.0, [-30, 0, 0]), (1.5, [6, 0, 0], 'easeOutBack'), (1.8, [0, 0, 0]),
                  (4.0, [0, 0, 0])])
    a.sound(0.4, 'yawn').sound(1.0, 'rustle').sound(2.4, 'unroll').sound(3.15, 'rummage')
    return a


def angry_wake():
    """The 2% chancla: bolts awake, furious, shakes his fist, packs up in a huff."""
    a = Anim('angry_wake', 2.6, 'hold')
    a.vis('eyelids', True, [(0.05, False)])
    a.scale('bag_cover', [(0, [1, 1, 1]), (0.35, [1, 1, 1]), (0.4, [0, 0, 0], 'lin')], 'lin')
    a.pos('bag_cover', [(0, [0, 0, 0]), (0.35, [0, 12, 8], 'easeOutQuad')], 'lin')
    a.rot('bag_cover', [(0, [0, 0, 0]), (0.35, [-50, 0, 25], 'easeOutQuad')], 'lin')
    a.pos('root', [(0, LIE_ROOT_POS), (0.1, LIE_ROOT_POS), (0.4, [0, 6, 0], 'easeOutQuad'),
                   (0.6, [0, 0, 0], 'easeInQuad'), (2.6, [0, 0, 0])], 'lin')
    a.rot('root', [(0, LIE_ROOT_ROT), (0.1, LIE_ROOT_ROT), (0.45, [0, 0, 0], 'easeOutBack'), (2.6, [0, 0, 0])], 'lin')
    for brow, s in (('brow_r', 1), ('brow_l', -1)):
        a.rot(brow, [(0, [0, 0, -8 * s]), (0.08, [0, 0, 20 * s], 'easeOutBack'), (2.3, [0, 0, 18 * s]),
                     (2.6, [0, 0, 10 * s])], 'lin')
        a.pos(brow, [(0, [0, 0, 0]), (0.08, [0, 1.6, 0]), (0.3, [0, -0.5, 0]), (2.6, [0, -0.3, 0])], 'lin')
    shake = [(0, [0, 0, 6]), (0.5, [0, 0, 6]), (0.7, [-160, 0, 20], 'easeOutBack')]
    t = 0.7
    for i in range(7):
        t += 0.08
        shake.append((t, [-160, 0, 30 if i % 2 == 0 else 10]))
    shake += [(1.5, [-30, 0, 10], 'easeInOutSine'), (1.7, [-40, -20, 20]), (2.0, [65, 0, 28], 'easeInOutSine'),
              (2.3, [0, 0, 4], 'easeInOutSine'), (2.6, [0, 0, 0])]
    a.rot('arm_r', shake, 'lin')
    a.rot('arm_l', [(0, [0, 0, -6]), (0.45, [0, 0, -30]), (0.7, [0, 0, -14]), (1.4, [0, 0, -14]),
                    (2.6, [0, 0, 0])], 'lin')
    a.rot('torso', [(0, [0, 0, 0]), (0.7, [-6, 0, 0]), (1.4, [-6, 0, 0]), (1.55, [10, 0, 0]), (1.7, [0, 0, 0]),
                    (2.6, [0, 0, 0])])
    a.pos('hat', [(0, [0, 0, 0]), (0.3, [0, 7, 0], 'easeOutQuad'), (0.65, [0, 0, 0], 'easeOutBounce'),
                  (2.6, [0, 0, 0])], 'lin')
    a.rot('hat', [(0, [-30, 0, 0]), (0.3, [0, 0, 25]), (0.65, [0, 0, 0]), (2.6, [0, 0, 0])])
    pack_bed(a, 1.5, 2.0)
    a.pos('backpack_ground', [(0, [0, 0, 0]), (1.95, [0, 0, 0]), (2.0, [8, 8, 0])], 'lin')
    a.sound(0.05, 'gasp').sound(0.75, 'angry').sound(1.2, 'angry').sound(1.6, 'unroll').sound(2.0, 'rummage')
    return a


ANIMS = [walk, idle, sit_down, grill, pack_up, receive_coupon, celebrate,
         raft_board, raft, raft_idle, raft_land, run, drink, smash, tired,
         sleep_setup, sleep, sleep_flinch, wake_up, angry_wake,
         queue_idle, present_coupon, busted, nervous]



def build():
    data = {'format_version': '1.8.0', 'animations': {}}
    for fn in ANIMS:
        a = fn()
        data['animations'][PREFIX + a.name] = a.compile()
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, 'w') as f:
        json.dump(data, f, separators=(',', ':'))
    return OUT


if __name__ == '__main__':
    print(build())
