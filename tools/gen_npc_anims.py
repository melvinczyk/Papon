#!/usr/bin/env python3
"""Generates the Taco Truck crowd's GeckoLib animations.

Writes, under src/main/resources/assets/papon/animations/entity/:
  customer.animation.json    the humans (Don Pollo, EDP445, Badlands Chugs, Reviewbrah), one shared
                             rig from gen_customers.py
  papon_npc.animation.json   the Papons: customers (customer_*) and the taquero (vendor_*), the
                             wkcr rig from gen_vendor.py

Conventions are the Wandering Papon's (see gen_anims.py): rot X < 0 swings a limb forward and tilts
a head or torso back; arm_r Z > 0 / arm_l Z < 0 raise an arm out sideways; arm_r Y < 0 / arm_l Y > 0
swing it inward across the body. Catmull-Rom by default; GeckoLib easings on accents.

Props hide by scale 0. Each file pins its "pinned" props to 0 in every animation that does not key
them. The humans' own items (drumstick, jug, goblet) are not pinned: they are always in hand unless
an animation says otherwise.

Lengths of one-shot animations are state durations in Java; keep them in step:
  receive / customer_receive   1.5 s = Customer.SERVE
  react_<who>                  = Customer.<WHO>.reactTicks / 20
EDP445 and Badlands Chugs (meshes with elbows, jaw and eyelids) have their own full sets in
gen_celeb_anims.py, with poses solved on their models by pose_solver.py.
  vendor_serve 1.5 s = VendorState.SERVING, vendor_inspect 7.5 s = INSPECTING, vendor_fraud 3.0 s = FRAUD
  vendor_exposed 3.0 s = EXPOSED, vendor_unmask 5.0 s = UNMASK, vendor_realize 3.0 s = REALIZE,
  vendor_rage 3.5 s = RAGE (the unmasking; TacoVendorEntity fires its effects at the same ticks)
  vendor_cook loops every 4.0 s = Customer.COOK
  vendor_wave 2.5 s = GREETING (beaten and friendly: the welcome back)
"""
import json
import os

from gen_anims import guard_brows, EPS, Anim
from gen_celeb_anims import chugs_anims, edp_anims

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, 'src/main/resources/assets/papon/animations/entity')


class Pinned(Anim):
    pins = []

    def compile(self):
        bones = {b: {ch: dict(sorted(keys.items(), key=lambda kv: float(kv[0]))) for ch, keys in c.items()}
                 for b, c in self.bones.items()}
        for prop in self.pins:
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


class H(Pinned):
    """Human customer animation."""
    pins = ['taco', 'phone']


class P(Pinned):
    """Papon (customer or taquero) animation."""
    pins = ['taco', 'notepad', 'pencil', 'spatula', 'served_taco', 'fraud_coupon', 'magnifier',
            'hair', 'wisps', 'sweat', 'blush', 'tears']


def osc(t0, t1, step, a, b, ease=None):
    """Keys alternating a, b every `step` from t0 to t1 (inclusive of t0)."""
    keys, t, i = [], t0, 0
    while t <= t1 + 1e-6:
        k = (round(t, 4), a if i % 2 == 0 else b)
        keys.append(k + ((ease,) if ease else ()))
        t += step
        i += 1
    return keys


# ===================================================================== humans

REST_L = [0, 0, -3]
REST_R = [0, 0, 3]
HOLD_TACO = [-52, -12, 0]       # right hand out in front, taco up
GLASS = [-48, 18, 0]            # Reviewbrah's glass at chest height
SIP = [-104, 32, 0]             # ...and raised to his lips
GLASS_UP = [44, 0, 0]           # goblet counter-rotations that keep it upright in those poses
SIP_TIP = [74, 0, 0]
TO_MOUTH_R = [-102, -38, 0]     # right hand at the mouth
TO_MOUTH_L = [-102, 38, 0]


def h_walk():
    a = H('walk', 1.0, True)
    a.rot('leg_r', [(0, [28, 0, 0]), (0.5, [-28, 0, 0]), (1.0, [28, 0, 0])])
    a.rot('leg_l', [(0, [-28, 0, 0]), (0.5, [28, 0, 0]), (1.0, [-28, 0, 0])])
    a.rot('arm_r', [(0, [-24, 0, 3]), (0.5, [22, 0, 3]), (1.0, [-24, 0, 3])])
    a.rot('arm_l', [(0, [22, 0, -3]), (0.5, [-24, 0, -3]), (1.0, [22, 0, -3])])
    a.pos('root', [(0, [0, 0, 0]), (0.25, [0, 0.9, 0]), (0.5, [0, 0, 0]), (0.75, [0, 0.9, 0]), (1.0, [0, 0, 0])])
    a.rot('torso', [(0, [2, 5, 0]), (0.5, [2, -5, 0]), (1.0, [2, 5, 0])])
    a.rot('head', [(0, [0, -4, 0]), (0.5, [0, 4, 0]), (1.0, [0, -4, 0])])
    a.rot('cape', [(0, [10, 0, 0]), (0.25, [16, 0, 2]), (0.5, [10, 0, 0]), (0.75, [16, 0, -2]), (1.0, [10, 0, 0])])
    return a


def h_walk_taco():
    a = h_walk()
    a.name = 'walk_taco'
    a.rot('arm_r', [(0, HOLD_TACO), (0.25, [-56, -12, 0]), (0.5, HOLD_TACO), (0.75, [-56, -12, 0]), (1.0, HOLD_TACO)])
    a.scale('taco', [(0, [1, 1, 1])], 'lin')
    return a


def h_hold_taco():
    a = H('hold_taco', 2.0, True)
    a.rot('arm_r', [(0, HOLD_TACO), (1.0, [-58, -12, 0]), (2.0, HOLD_TACO)])
    a.rot('arm_l', [(0, REST_L), (1.0, [-3, 0, -5]), (2.0, REST_L)])
    a.scale('torso', [(0, [1, 1, 1]), (1.0, [1.01, 1.02, 1.01]), (2.0, [1, 1, 1])])
    a.scale('taco', [(0, [1, 1, 1])], 'lin')
    return a


def h_idle():
    a = H('idle', 4.0, True)
    a.scale('torso', [(0, [1, 1, 1]), (2.0, [1.012, 1.02, 1.012]), (4.0, [1, 1, 1])])
    a.rot('head', [(0, [0, 0, 0]), (1.0, [0, 14, 0]), (1.8, [0, 14, 0]), (2.8, [0, -10, 0]), (3.4, [0, -10, 0]),
                   (4.0, [0, 0, 0])])
    a.rot('arm_r', [(0, REST_R), (2.0, [-3, 0, 5]), (4.0, REST_R)])
    a.rot('arm_l', [(0, REST_L), (2.0, [-3, 0, -5]), (4.0, REST_L)])
    a.rot('cape', [(0, [6, 0, 0]), (2.0, [8, 0, 1]), (4.0, [6, 0, 0])])
    return a


def h_idle_don_pollo():
    """Working through his rotisserie chicken, vibing to music only he can hear."""
    a = H('idle_don_pollo', 6.0, True)
    a.rot('arm_l', [(0, [-30, 10, -4]), (0.8, [-30, 10, -4]), (1.2, TO_MOUTH_L, 'easeInOutSine'), (1.7, TO_MOUTH_L),
                    (2.1, [-30, 10, -4], 'easeInOutSine'), (6.0, [-30, 10, -4])], 'lin')
    a.rot('head', [(0, [0, 0, 0]), (1.1, [0, 0, 0]), (1.3, [8, 0, 0]), (1.5, [-6, 0, 0]), (1.7, [6, 0, 0]),
                   (1.9, [-4, 0, 0]), (2.1, [4, 0, 0]), (2.4, [0, 0, 0]),
                   (3.0, [0, 0, 0]), (3.25, [6, 0, 6]), (3.5, [0, 0, 0]), (3.75, [6, 0, -6]), (4.0, [0, 0, 0]),
                   (4.25, [6, 0, 6]), (4.5, [0, 0, 0]), (6.0, [0, 0, 0])])
    a.rot('torso', [(0, [0, 0, 0]), (3.0, [0, 0, 0]), (3.25, [0, 0, -5]), (3.5, [0, 0, 0]), (3.75, [0, 0, 5]),
                    (4.0, [0, 0, 0]), (4.25, [0, 0, -5]), (4.5, [0, 0, 0]), (6.0, [0, 0, 0])])
    a.rot('arm_r', [(0, REST_R), (3.0, REST_R), (3.25, [-10, 0, 14]), (3.5, REST_R), (3.75, [-10, 0, 14]),
                    (4.0, REST_R), (4.25, [-10, 0, 14]), (4.5, REST_R), (6.0, REST_R)])
    a.pos('root', [(0, [0, 0, 0]), (3.0, [0, 0, 0]), (3.25, [0, -0.6, 0]), (3.5, [0, 0, 0]), (3.75, [0, -0.6, 0]),
                   (4.0, [0, 0, 0]), (4.25, [0, -0.6, 0]), (4.5, [0, 0, 0]), (6.0, [0, 0, 0])])
    a.rot('cape', [(0, [6, 0, 0]), (3.25, [8, 0, 3]), (3.75, [8, 0, -3]), (4.25, [8, 0, 3]), (4.75, [6, 0, 0]),
                   (6.0, [6, 0, 0])])
    a.sound(1.45, 'bite')
    return a


def h_idle_reviewbrah():
    """Upright as a lamp post. Glass of blue energy drink at the ready. Straightens his tie."""
    a = H('idle_reviewbrah', 6.0, True)
    glass, sip = GLASS, SIP
    a.rot('arm_l', [(0, glass), (1.8, glass), (2.2, sip, 'easeInOutSine'), (2.8, sip), (3.2, glass, 'easeInOutSine'),
                    (6.0, glass)], 'lin')
    a.rot('goblet', [(0, GLASS_UP), (1.8, GLASS_UP), (2.2, SIP_TIP, 'easeInOutSine'), (2.8, SIP_TIP),
                     (3.2, GLASS_UP, 'easeInOutSine'), (6.0, GLASS_UP)], 'lin')
    a.rot('head', [(0, [0, 0, 0]), (1.9, [0, 0, 0]), (2.2, [-10, 0, 0]), (2.8, [-10, 0, 0]), (3.1, [0, 0, 0]),
                   (3.8, [8, 0, 0]), (4.4, [8, 0, 0]), (4.7, [0, 0, 0]), (6.0, [0, 0, 0])])
    a.rot('arm_r', [(0, [0, 0, 2]), (3.6, [0, 0, 2]), (3.9, [-62, -42, 0], 'easeInOutSine'), (4.05, [-66, -42, 0]),
                    (4.2, [-60, -42, 0]), (4.5, [0, 0, 2], 'easeInOutSine'), (6.0, [0, 0, 2])], 'lin')
    a.scale('torso', [(0, [1, 1, 1]), (3.0, [1.006, 1.012, 1.006]), (6.0, [1, 1, 1])])
    a.sound(2.4, 'sip').sound(4.0, 'tie')
    return a


def h_order():
    """At the window: points up at the menu, talks with his hands."""
    a = H('order', 3.0, True)
    a.rot('arm_r', [(0, [-20, 0, 6]), (0.35, [-150, -6, 12], 'easeOutBack'), (0.9, [-150, -6, 12]),
                    (1.2, [-70, -22, 0], 'easeInOutSine'), (1.45, [-84, -10, 12]), (1.7, [-66, -26, 0]),
                    (1.95, [-84, -10, 12]), (2.2, [-66, -26, 0]), (2.6, [-30, -10, 6], 'easeInOutSine'),
                    (3.0, [-20, 0, 6])], 'lin')
    a.rot('head', [(0, [-14, 0, 0]), (0.5, [-18, 4, 0]), (0.9, [-18, -4, 0]), (1.2, [-12, 0, 0]), (1.45, [-6, 0, 0]),
                   (1.7, [-12, 0, 0]), (1.95, [-6, 0, 0]), (2.2, [-12, 0, 0]), (3.0, [-14, 0, 0])])
    a.rot('torso', [(0, [0, 0, 0]), (0.4, [-3, -4, 0]), (1.4, [2, 2, 0]), (3.0, [0, 0, 0])])
    return a


def h_receive():
    """Reaches up to the counter, gets the taco (the taquero lets go at 0.75 s), happy hop."""
    a = H('receive', 1.5, 'hold')
    a.rot('arm_r', [(0, [-20, 0, 6]), (0.5, [-160, -12, 4], 'easeOutBack'), (0.75, [-160, -12, 4]),
                    (1.1, HOLD_TACO, 'easeInOutSine'), (1.5, HOLD_TACO)], 'lin')
    a.vis('taco', False, [(0.75, True)])
    a.scale('taco', [(0, [0, 0, 0]), (0.75 - EPS, [0, 0, 0]), (0.75, [1.3, 1.3, 1.3]), (0.95, [1, 1, 1], 'easeOutBack'),
                     (1.5, [1, 1, 1])], 'lin')
    a.rot('head', [(0, [-14, 0, 0]), (0.5, [-22, 0, 0]), (0.9, [-8, 0, 0]), (1.2, [6, 0, 0]), (1.5, [0, 0, 0])])
    a.pos('root', [(0, [0, 0, 0]), (1.1, [0, 0, 0]), (1.22, [0, 1.6, 0], 'easeOutQuad'), (1.36, [0, 0, 0], 'easeInQuad'),
                   (1.5, [0, 0, 0])], 'lin')
    a.sound(0.75, 'pop')
    return a


def bite(a, t, shrink=0.7):
    """A bite at t: the sound, and the taco's scale keys up to it (the caller moves the arm)."""
    a.sound(t, 'bite')
    return [(0, [1, 1, 1]), (t - EPS, [1, 1, 1]), (t, [shrink, shrink, shrink], 'easeOutQuad')]


def h_react_don_pollo():
    """6.0 s. The salsa hits. Fans his mouth, hops, steams; then the phone rings, and he takes it."""
    a = H('react_don_pollo', 6.0, 'hold')
    taco = bite(a, 0.45)
    a.scale('taco', taco + [(0.95, [0.7, 0.7, 0.7]), (1.0, [0, 0, 0], 'easeInBack'), (6.0, [0, 0, 0])], 'lin')
    a.sound(0.8, 'gulp').sound(1.0, 'hot')
    # the heat: stiffen, head shake, fan, hop
    a.scale('torso', [(0, [1, 1, 1]), (0.95, [1, 1, 1]), (1.05, [0.94, 1.08, 0.94], 'easeOutBack'), (1.3, [1, 1, 1]),
                      (6.0, [1, 1, 1])], 'lin')
    head = [(0, [0, 0, 0]), (0.3, [-6, 0, 0]), (0.5, [4, 0, 0]), (0.7, [0, 0, 0]), (0.95, [0, 0, 0]),
            (1.05, [-14, 0, 0])]
    head += [(round(1.15 + 0.12 * i, 3), [-10, 18 if i % 2 == 0 else -18, 0]) for i in range(6)]
    head += [(2.0, [-6, 0, 0]), (3.2, [0, 0, 0])]
    fan = [(1.0, TO_MOUTH_R)] + [(round(1.1 + 0.15 * i, 3), [-112, -40, 26 if i % 2 == 0 else 0]) for i in range(13)]
    a.rot('arm_r', [(0, HOLD_TACO), (0.1, TO_MOUTH_R, 'easeInOutSine'), (0.6, TO_MOUTH_R)] + fan[1:]
          + [(3.2, [-112, -40, 0]), (3.4, [-10, 0, 8], 'easeInOutSine'),
             (3.7, [-30, -10, 4]), (3.95, [-158, -46, -6], 'easeOutBack'), (6.0, [-158, -46, -6])], 'lin')
    for i in range(8):
        a.sound(1.1 + 0.3 * i, 'fan')
    a.rot('arm_l', [(0, [-30, 10, -4]), (1.0, [-30, 10, -4]), (1.2, [-165, 0, -24], 'easeOutBack'),
                    (1.5, [-140, 0, -40]), (1.8, [-165, 0, -24]), (2.1, [-140, 0, -40]), (2.4, [-165, 0, -24]),
                    (2.7, [-140, 0, -40]), (3.2, [-30, 10, -4], 'easeInOutSine'),
                    # on the phone: talking with the drumstick
                    (4.3, [-30, 10, -4]), (4.6, [-70, 20, -14]), (4.9, [-50, 10, -4]), (5.2, [-74, 24, -16]),
                    (5.5, [-50, 10, -4]), (6.0, [-50, 10, -4])], 'lin')
    hop = [(0, [0, 0, 0]), (1.15, [0, 0, 0])]
    for t in (1.2, 1.8, 2.4):
        hop += [(t, [0, -0.8, 0], 'easeOutQuad'), (t + 0.15, [0, 2.6, 0], 'easeOutQuad'), (t + 0.32, [0, 0, 0], 'easeInQuad')]
    hop += [(3.3, [0, 0, 0]), (3.4, [0, 1.4, 0], 'easeOutQuad'), (3.52, [0, 0, 0], 'easeInQuad'), (6.0, [0, 0, 0])]
    a.pos('root', hop, 'lin')
    for t in (1.2, 1.8, 2.4):
        a.sound(t + 0.12, 'jump')
    # the phone: rings, he jumps, digs it out, answers
    a.sound(3.4, 'ring_hi').sound(3.55, 'ring_lo').sound(3.7, 'ring_mid').sound(3.85, 'ring_hi').sound(4.0, 'ring_lo')
    a.vis('phone', False, [(3.8, True)])
    head += [(3.4, [10, -20, 0], 'easeOutBack'), (3.8, [6, -10, 0]), (4.0, [0, -14, 8]), (4.4, [4, -14, 8]),
             (4.8, [-2, -14, 8]), (5.2, [4, -14, 8]), (5.6, [-2, -14, 8]), (6.0, [0, -14, 8])]
    a.rot('head', head)
    a.rot('torso', [(0, [0, 0, 0]), (3.3, [0, 0, 0]), (3.45, [-6, 0, 0]), (3.7, [6, -10, 0]), (4.0, [0, -6, 0]),
                    (6.0, [0, -6, 0])])
    a.rot('cape', [(0, [6, 0, 0]), (1.2, [20, 0, 4]), (1.5, [8, 0, -3]), (1.8, [20, 0, 4]), (2.1, [8, 0, -3]),
                   (2.4, [20, 0, 4]), (2.7, [8, 0, 0]), (3.4, [16, 0, 0]), (3.8, [6, 0, 0]), (6.0, [6, 0, 0])])
    a.sound(3.95, 'phone')
    return a


def h_react_reviewbrah():
    """6.0 s. A measured bite. Deliberation. The verdict, to camera. A sip. Sign-off with a small bow."""
    a = H('react_reviewbrah', 6.0, 'hold')
    present = [-78, -8, 0]
    a.rot('arm_r', [(0, HOLD_TACO), (0.6, TO_MOUTH_R, 'easeInOutSine'), (0.95, TO_MOUTH_R), (1.4, [-60, -14, 0], 'easeInOutSine'),
                    (1.9, [-60, -14, 0]), (2.2, present, 'easeInOutSine'), (2.5, [-80, -4, 4]), (2.8, present),
                    (3.1, [-80, -4, 4]), (3.4, present), (4.6, present), (5.0, HOLD_TACO, 'easeInOutSine'),
                    (6.0, HOLD_TACO)], 'lin')
    a.scale('taco', [(0, [1, 1, 1]), (0.8 - EPS, [1, 1, 1]), (0.8, [0.82, 0.82, 0.82], 'easeOutQuad'), (6.0, [0.82, 0.82, 0.82])], 'lin')
    a.sound(0.8, 'bite')
    glass, sip = GLASS, SIP
    a.rot('arm_l', [(0, glass), (3.5, glass), (3.85, sip, 'easeInOutSine'), (4.35, sip), (4.7, glass, 'easeInOutSine'),
                    (6.0, glass)], 'lin')
    a.rot('goblet', [(0, GLASS_UP), (3.5, GLASS_UP), (3.85, SIP_TIP, 'easeInOutSine'), (4.35, SIP_TIP),
                     (4.7, GLASS_UP, 'easeInOutSine'), (6.0, GLASS_UP)], 'lin')
    a.sound(4.0, 'sip')
    a.rot('head', [(0, [0, 0, 0]), (0.6, [6, 0, 0]), (0.95, [4, 0, 0]), (1.2, [-6, 0, 5]), (1.5, [-8, 0, -4]),
                   (1.8, [-6, 0, 5]), (2.1, [0, 0, 0]), (2.4, [2, 0, 0]), (2.7, [0, 0, 0]), (3.0, [2, 0, 0]),
                   (3.5, [0, 0, 0]), (3.85, [-10, 0, 0]), (4.35, [-10, 0, 0]), (4.7, [0, 0, 0]),
                   (4.9, [0, 0, 0]), (5.2, [14, 0, 0], 'easeInOutSine'), (5.5, [14, 0, 0]), (5.8, [0, 0, 0], 'easeInOutSine'),
                   (6.0, [0, 0, 0])])
    a.rot('torso', [(0, [0, 0, 0]), (4.9, [0, 0, 0]), (5.2, [14, 0, 0], 'easeInOutSine'), (5.5, [14, 0, 0]),
                    (5.8, [0, 0, 0], 'easeInOutSine'), (6.0, [0, 0, 0])], 'lin')
    a.sound(1.4, 'hmm').sound(5.2, 'tie')
    return a


HUMAN = [h_walk, h_walk_taco, h_hold_taco, h_idle, h_idle_don_pollo, h_idle_reviewbrah, h_order, h_receive,
         h_react_don_pollo, h_react_reviewbrah,
         *[lambda a=a: a for a in edp_anims(H) + chugs_anims(H)]]   # gen_celeb_anims.py


# ===================================================================== papons

P_HOLD = [-60, -16, 0]
P_MOUTH = [-96, -48, 0]
ANGRY_R = [0, 0, 18]
ANGRY_L = [0, 0, -18]


def p_walk():
    a = P('customer_walk', 0.8, True)
    a.rot('leg_r', [(0, [24, 0, 0]), (0.4, [-24, 0, 0]), (0.8, [24, 0, 0])])
    a.rot('leg_l', [(0, [-24, 0, 0]), (0.4, [24, 0, 0]), (0.8, [-24, 0, 0])])
    a.rot('arm_r', [(0, [-20, 0, 5]), (0.4, [18, 0, 3]), (0.8, [-20, 0, 5])])
    a.rot('arm_l', [(0, [18, 0, -3]), (0.4, [-20, 0, -5]), (0.8, [18, 0, -3])])
    a.pos('root', [(0, [0, 0, 0]), (0.2, [0, 1.3, 0]), (0.4, [0, 0, 0]), (0.6, [0, 1.3, 0]), (0.8, [0, 0, 0])])
    a.rot('torso', [(0, [3, 4, -3]), (0.4, [3, -4, 3]), (0.8, [3, 4, -3])])
    a.scale('body', [(0, [1.04, 0.96, 1.04]), (0.2, [0.98, 1.03, 0.98]), (0.4, [1.04, 0.96, 1.04]),
                     (0.6, [0.98, 1.03, 0.98]), (0.8, [1.04, 0.96, 1.04])])
    for hat in ('sombrero', 'cap'):
        a.rot(hat, [(0, [0, 0, 1.5]), (0.1, [0, 0, 2.5]), (0.5, [0, 0, -2.5]), (0.8, [0, 0, 1.5])])
    return a


def p_walk_taco():
    a = p_walk()
    a.name = 'customer_walk_taco'
    a.rot('arm_r', [(0, P_HOLD), (0.2, [-64, -16, 0]), (0.4, P_HOLD), (0.6, [-64, -16, 0]), (0.8, P_HOLD)])
    a.scale('taco', [(0, [1, 1, 1])], 'lin')
    return a


def p_hold_taco():
    a = P('customer_hold_taco', 2.0, True)
    a.rot('arm_r', [(0, P_HOLD), (1.0, [-64, -16, 0]), (2.0, P_HOLD)])
    a.scale('body', [(0, [1, 1, 1]), (1.0, [1.015, 1.03, 1.015]), (2.0, [1, 1, 1])])
    a.scale('taco', [(0, [1, 1, 1])], 'lin')
    return a


def p_idle():
    a = P('customer_idle', 4.0, True)
    a.scale('body', [(0, [1, 1, 1]), (2.0, [1.015, 1.03, 1.015]), (4.0, [1, 1, 1])])
    a.rot('root', [(0, [0, 0, 0]), (0.9, [0, 14, 0], 'easeInOutSine'), (1.8, [0, 14, 0], 'easeInOutSine'),
                   (2.7, [0, -12, 0], 'easeInOutSine'), (3.4, [0, -12, 0], 'easeInOutSine'),
                   (4.0, [0, 0, 0], 'easeInOutSine')], 'lin')
    for brow in ('brow_r', 'brow_l'):
        a.pos(brow, [(0, [0, 0, 0]), (0.8, [0, 0, 0]), (1.0, [0, 0.6, 0]), (1.4, [0, 0.6, 0]), (1.6, [0, 0, 0]),
                     (4.0, [0, 0, 0])])
    a.rot('arm_r', [(0, [0, 0, 1]), (2.0, [-3, 0, 4]), (4.0, [0, 0, 1])])
    a.rot('arm_l', [(0, [0, 0, -1]), (2.0, [-3, 0, -4]), (4.0, [0, 0, -1])])
    return a


def p_order():
    a = P('customer_order', 2.5, True)
    a.rot('arm_r', [(0, [-20, 0, 5]), (0.3, [-160, 0, 12], 'easeOutBack'), (0.6, [-150, 0, 22]), (0.9, [-160, 0, 12]),
                    (1.2, [-150, 0, 22]), (1.6, [-40, 0, 6], 'easeInOutSine'), (2.5, [-20, 0, 5])], 'lin')
    for brow in ('brow_r', 'brow_l'):
        a.pos(brow, [(0, [0, 0, 0]), (0.3, [0, 1.2, 0], 'easeOutBack'), (1.6, [0, 1.0, 0]), (2.0, [0, 0, 0]),
                     (2.5, [0, 0, 0])], 'lin')
    a.pos('root', [(0, [0, 0, 0]), (0.3, [0, 0.8, 0]), (0.5, [0, 0, 0]), (2.5, [0, 0, 0])])
    a.sound(0.3, 'hmm')
    return a


def p_receive():
    a = P('customer_receive', 1.5, 'hold')
    a.rot('arm_r', [(0, [-20, 0, 5]), (0.5, [-165, -10, 6], 'easeOutBack'), (0.75, [-165, -10, 6]),
                    (1.1, P_HOLD, 'easeInOutSine'), (1.5, P_HOLD)], 'lin')
    a.vis('taco', False, [(0.75, True)])
    for brow in ('brow_r', 'brow_l'):
        a.pos(brow, [(0, [0, 0, 0]), (0.75, [0, 0, 0]), (0.85, [0, 1.4, 0], 'easeOutBack'), (1.5, [0, 1.0, 0])], 'lin')
    a.pos('root', [(0, [0, 0, 0]), (1.1, [0, 0, 0]), (1.22, [0, 2.4, 0], 'easeOutQuad'), (1.38, [0, 0, 0], 'easeInQuad'),
                   (1.5, [0, 0, 0])], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (1.38, [1, 1, 1]), (1.42, [1.1, 0.9, 1.1]), (1.5, [1, 1, 1])], 'lin')
    a.sound(0.75, 'pop')
    return a


def p_react():
    """2.5 s. Bite, bliss, a little hop-spin."""
    a = P('customer_react', 2.5, 'hold')
    a.rot('arm_r', [(0, P_HOLD), (0.3, P_MOUTH, 'easeInOutSine'), (0.55, P_MOUTH), (0.7, [-80, -40, 0]),
                    (0.9, P_MOUTH), (1.1, [0, 0, 4], 'easeInOutSine'), (2.5, [0, 0, 2])], 'lin')
    a.scale('taco', [(0, [1, 1, 1]), (0.4, [1, 1, 1]), (0.42, [0.65, 0.65, 0.65]), (0.95, [0.65, 0.65, 0.65]),
                     (1.05, [0, 0, 0], 'easeInBack'), (2.5, [0, 0, 0])], 'lin')
    for brow, s in (('brow_r', 1), ('brow_l', -1)):
        a.pos(brow, [(0, [0, 0, 0]), (0.45, [0, 1.5, 0], 'easeOutBack'), (1.4, [0, 1.2, 0]), (1.6, [0, 1.6, 0]),
                     (1.8, [0, 1.0, 0]), (2.5, [0, 0.4, 0])], 'lin')
        a.rot(brow, [(0, [0, 0, 0]), (0.5, [0, 0, -10 * s]), (2.5, [0, 0, -4 * s])])
    a.pos('root', [(0, [0, 0, 0]), (1.2, [0, 0, 0]), (1.3, [0, -0.6, 0], 'easeOutQuad'), (1.55, [0, 6, 0], 'easeOutQuad'),
                   (1.85, [0, 0, 0], 'easeInQuad'), (2.5, [0, 0, 0])], 'lin')
    a.rot('root', [(0, [0, 0, 0]), (1.3, [0, 0, 0]), (1.85, [0, 360, 0], 'easeInOutSine'), (1.85 + EPS, [0, 0, 0], 'lin'),
                   (2.5, [0, 0, 0])], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (0.42, [1, 1, 1]), (0.5, [1.06, 0.94, 1.06]), (0.7, [1, 1, 1]), (1.85, [1, 1, 1]),
                     (1.9, [1.14, 0.86, 1.14], 'easeOutQuad'), (2.1, [1, 1, 1], 'easeOutBack'), (2.5, [1, 1, 1])], 'lin')
    a.sound(0.42, 'bite').sound(1.0, 'gulp').sound(1.3, 'jump').sound(1.85, 'plop')
    return a


# ---- Papon the Taquero

V_PAD = [-72, 26, 0]
V_PENCIL = [-76, -30, 0]


def v_idle():
    """6.0 s loop. Wipes the counter, looks out for customers, twirls the mustache."""
    a = P('vendor_idle', 6.0, True)
    wipe = [(0, [0, 0, -2]), (0.4, [-78, 20, 0], 'easeInOutSine')]
    for i in range(6):
        wipe.append((round(0.7 + 0.3 * i, 2), [-78, 40 if i % 2 == 0 else 6, 0]))
    wipe += [(2.6, [-78, 20, 0]), (2.9, [0, 0, -2], 'easeInOutSine'), (6.0, [0, 0, -2])]
    a.rot('arm_l', wipe, 'lin')
    a.rot('torso', [(0, [0, 0, 0]), (0.4, [8, 0, 0]), (2.6, [8, 0, 0]), (2.9, [0, 0, 0]), (6.0, [0, 0, 0])])
    a.rot('root', [(0, [0, 0, 0]), (3.0, [0, 0, 0]), (3.4, [0, 24, 0], 'easeInOutSine'), (3.9, [0, 24, 0]),
                   (4.3, [0, -20, 0], 'easeInOutSine'), (4.7, [0, -20, 0]), (5.1, [0, 0, 0], 'easeInOutSine'),
                   (6.0, [0, 0, 0])], 'lin')
    a.rot('arm_r', [(0, [0, 0, 2]), (4.9, [0, 0, 2]), (5.15, [-140, -52, 0], 'easeInOutSine'), (5.3, [-136, -46, 6]),
                    (5.45, [-140, -52, 0]), (5.6, [-136, -46, 6]), (5.9, [0, 0, 2], 'easeInOutSine'), (6.0, [0, 0, 2])], 'lin')
    a.rot('mustache', [(0, [0, 0, 0]), (5.15, [0, 0, 0]), (5.3, [0, 0, 6]), (5.45, [0, 0, -2]), (5.6, [0, 0, 6]),
                       (5.9, [0, 0, 0]), (6.0, [0, 0, 0])])
    a.scale('body', [(0, [1, 1, 1]), (3.0, [1.01, 1.025, 1.01]), (6.0, [1, 1, 1])])
    a.pos('brow_r', [(0, [0, 0, 0]), (3.4, [0, 0, 0]), (3.6, [0, 0.8, 0]), (4.3, [0, 0.8, 0]), (4.5, [0, 0, 0]),
                     (6.0, [0, 0, 0])])
    a.sound(5.3, 'hmm')
    return a


def v_take_order():
    """3.0 s loop. Notepad up, pencil scribbling, nodding along."""
    a = P('vendor_take_order', 3.0, True)
    a.scale('notepad', [(0, [1, 1, 1])], 'lin')
    a.scale('pencil', [(0, [1, 1, 1])], 'lin')
    a.rot('arm_l', [(0, V_PAD), (1.5, [-76, 26, 0]), (3.0, V_PAD)])
    scrib = [(0, V_PENCIL)]
    for i in range(14):
        scrib.append((round(0.1 + 0.2 * i, 2), [-76 + (4 if i % 2 else -2), -30, 6 if i % 2 else -6]))
    scrib.append((3.0, V_PENCIL))
    a.rot('arm_r', scrib)
    a.rot('torso', [(0, [6, 0, 0]), (0.4, [10, 0, 0]), (0.8, [6, 0, 0]), (1.6, [10, 0, 0]), (2.0, [6, 0, 0]),
                    (3.0, [6, 0, 0])])
    a.pos('brow_l', [(0, [0, 0.8, 0]), (1.5, [0, 1.0, 0]), (3.0, [0, 0.8, 0])])
    a.sound(0.4, 'scribble').sound(1.6, 'scribble').sound(2.6, 'scribble')
    return a


def v_cook():
    """4.0 s loop. Turns to the griddle behind him, flips twice, turns back to check the window."""
    a = P('vendor_cook', 4.0, True)
    a.rot('root', [(0, [0, 0, 0]), (0.45, [0, 180, 0], 'easeInOutSine'), (3.55, [0, 180, 0]),
                   (4.0, [0, 360, 0], 'easeInOutSine')], 'lin')
    a.scale('spatula', [(0, [0, 0, 0]), (0.4 - EPS, [0, 0, 0]), (0.4, [1, 1, 1]), (3.6, [1, 1, 1]),
                        (3.6 + EPS, [0, 0, 0]), (4.0, [0, 0, 0])], 'lin')
    a.rot('arm_l', [(0, [0, 0, -2]), (0.45, [-82, 34, 0], 'easeInOutSine'), (1.0, [-88, 38, 0]), (1.1, [-122, 32, 0], 'easeOutQuad'),
                    (1.4, [-82, 34, 0]), (2.4, [-88, 38, 0]), (2.5, [-122, 32, 0], 'easeOutQuad'), (2.8, [-82, 34, 0]),
                    (3.5, [-82, 34, 0]), (3.9, [0, 0, -2], 'easeInOutSine'), (4.0, [0, 0, -2])], 'lin')
    shake = [(0, [0, 0, 2]), (1.5, [0, 0, 2]), (1.7, [-100, -30, 0], 'easeInOutSine')]
    t = 1.7
    for i in range(5):
        t += 0.1
        shake.append((round(t, 2), [-100, -30, 12 if i % 2 == 0 else -12]))
    shake += [(2.4, [-100, -30, 0]), (2.6, [0, 0, 2], 'easeInOutSine'), (4.0, [0, 0, 2])]
    a.rot('arm_r', shake, 'lin')
    a.rot('torso', [(0, [0, 0, 0]), (0.5, [10, 0, 0]), (3.4, [10, 0, 0]), (3.8, [0, 0, 0]), (4.0, [0, 0, 0])])
    a.rot('toque', [(0, [0, 0, 0]), (1.1, [0, 0, 0]), (1.2, [-4, 0, 3]), (1.4, [0, 0, 0]), (2.5, [0, 0, 0]), (2.6, [-4, 0, -3]),
                    (2.8, [0, 0, 0]), (4.0, [0, 0, 0])])
    a.sound(1.1, 'flip').sound(1.3, 'sizzle').sound(1.85, 'sizzle').sound(2.5, 'flip').sound(2.7, 'sizzle')
    return a


def v_serve():
    """1.5 s. Leans out of the window and hands the taco down; lets go at 0.75 s."""
    a = P('vendor_serve', 1.5, 'hold')
    a.vis('served_taco', True, [(0.75, False)])
    a.rot('arm_r', [(0, [0, 0, 2]), (0.45, [-100, -8, 0], 'easeOutBack'), (0.75, [-100, -8, 0]), (1.1, [0, 0, 2], 'easeInOutSine'),
                    (1.5, [0, 0, 2])], 'lin')
    a.rot('torso', [(0, [0, 0, 0]), (0.45, [16, 0, 0], 'easeOutQuad'), (0.8, [16, 0, 0]), (1.15, [0, 0, 0], 'easeInOutSine'),
                    (1.5, [0, 0, 0])], 'lin')
    for brow in ('brow_r', 'brow_l'):
        a.pos(brow, [(0, [0, 0, 0]), (0.5, [0, 1.0, 0], 'easeOutBack'), (1.2, [0, 0.6, 0]), (1.5, [0, 0, 0])], 'lin')
    a.sound(0.75, 'serve').sound(1.0, 'ding')
    return a


def v_inspect():
    """7.5 s. Takes the coupon. Reads it. Magnifying glass. Holds it to the light. It is a fake."""
    a = P('vendor_inspect', 7.5, 'hold')
    take = [-98, 24, 0]
    close = [-124, 36, 0]
    light = [-172, 10, -10]
    a.rot('arm_l', [(0, [0, 0, -2]), (1.25, [0, 0, -2]), (1.6, take, 'easeOutBack'), (1.7, take), (2.1, close, 'easeInOutSine'),
                    (4.8, close), (5.2, light, 'easeInOutSine'), (5.7, light), (6.0, [-110, 28, 0], 'easeInQuad'),
                    (7.5, [-110, 28, 0])], 'lin')
    a.vis('fraud_coupon', False, [(1.6, True)])
    a.scale('fraud_coupon', [(0, [0, 0, 0]), (1.6 - EPS, [0, 0, 0]), (1.6, [1, 1, 1]), (6.6, [1, 1, 1]),
                             (6.85, [0.55, 0.8, 0.45], 'easeOutBack'), (7.5, [0.5, 0.8, 0.4])], 'lin')
    a.vis('magnifier', False, [(3.2, True), (5.05, False)])
    a.rot('arm_r', [(0, [0, 0, 2]), (3.0, [0, 0, 2]), (3.3, [-122, -34, 0], 'easeOutBack'), (3.6, [-122, -20, 0]),
                    (3.9, [-122, -44, 0]), (4.2, [-126, -20, 0]), (4.5, [-122, -44, 0]), (4.8, [-122, -32, 0]),
                    (5.1, [0, 0, 2], 'easeInOutSine'), (5.9, [0, 0, 2]), (6.1, [-30, 0, 24], 'easeOutQuad'),
                    (7.5, [-30, 0, 24])], 'lin')
    a.rot('torso', [(0, [0, 0, 0]), (0.6, [6, 0, 0]), (1.6, [10, 0, 0]), (2.1, [2, 0, 0]), (2.6, [2, 0, 5]), (3.1, [2, 0, -5]),
                    (3.4, [12, 0, 0]), (4.8, [12, 0, 0]), (5.2, [-10, 0, 0]), (5.7, [-10, 0, 0]), (6.0, [4, 0, 0])]
           + [(round(6.1 + 0.1 * i, 2), [4, 0, 2.5 if i % 2 == 0 else -2.5]) for i in range(14)], 'cat')
    # brows: curious, then suspicious, then furious
    a.pos('brow_r', [(0, [0, 0, 0]), (0.6, [0, 1.0, 0]), (2.2, [0, 0.4, 0]), (3.4, [0, -0.3, 0]), (5.2, [0, 0.8, 0]),
                     (5.7, [0, 0.8, 0]), (5.85, [0, -0.7, 0], 'easeOutQuad'), (7.5, [0, -0.7, 0])], 'lin')
    a.pos('brow_l', [(0, [0, 0, 0]), (0.6, [0, 1.0, 0]), (2.2, [0, 0.8, 0]), (3.4, [0, 0.2, 0]), (5.2, [0, 0.8, 0]),
                     (5.7, [0, 0.8, 0]), (5.85, [0, -0.7, 0], 'easeOutQuad'), (7.5, [0, -0.7, 0])], 'lin')
    a.rot('brow_r', [(0, [0, 0, 0]), (3.4, [0, 0, 8]), (5.7, [0, 0, 0]), (5.85, ANGRY_R, 'easeOutQuad'), (7.5, ANGRY_R)], 'lin')
    a.rot('brow_l', [(0, [0, 0, 0]), (3.4, [0, 0, 0]), (5.7, [0, 0, 0]), (5.85, ANGRY_L, 'easeOutQuad'), (7.5, ANGRY_L)], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (5.8, [1, 1, 1])]
            + [(round(6.0 + 0.12 * i, 2), [1.03, 1.03, 1.03] if i % 2 == 0 else [1, 1, 1]) for i in range(13)], 'lin')
    a.rot('mustache', [(0, [0, 0, 0]), (5.8, [0, 0, 0])]
          + [(round(6.0 + 0.1 * i, 2), [0, 0, 4 if i % 2 == 0 else -4]) for i in range(15)])
    a.sound(1.6, 'paper').sound(2.6, 'squint').sound(3.25, 'pop').sound(4.4, 'squint').sound(5.85, 'gasp')
    a.sound(6.1, 'rumble').sound(6.8, 'crumple')
    return a


def v_fraud():
    """3.0 s. Both fists slam the counter, the toque blasts off, he roars."""
    a = P('vendor_fraud', 3.0, 'hold')
    for arm, s in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-30, 0, 24 * s]), (0.3, [-172, 0, 22 * s], 'easeOutQuad'), (0.45, [-60, 0, 8 * s], 'easeInQuad'),
                    (0.7, [-64, 0, 8 * s]), (1.0, [-150, 0, 40 * s], 'easeOutBack'), (2.0, [-150, 0, 40 * s]),
                    (2.4, [-20, 0, 26 * s], 'easeInOutSine'), (3.0, [-20, 0, 26 * s])], 'lin')
    a.rot('torso', [(0, [4, 0, 0]), (0.3, [-10, 0, 0]), (0.45, [22, 0, 0], 'easeInQuad'), (0.8, [16, 0, 0]),
                    (1.0, [-14, 0, 0], 'easeOutBack'), (2.0, [-14, 0, 0]), (2.4, [4, 0, 0]), (3.0, [4, 0, 0])], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (0.45, [1, 1, 1]), (0.5, [1.12, 0.86, 1.12], 'easeOutQuad'), (0.7, [1, 1, 1]),
                     (1.0, [0.92, 1.16, 0.92], 'easeOutBack')]
            + [(round(1.1 + 0.1 * i, 2), [0.94, 1.14, 0.94] if i % 2 == 0 else [0.9, 1.18, 0.9]) for i in range(10)]
            + [(2.4, [1.04, 1.04, 1.04], 'easeInOutSine'), (3.0, [1, 1, 1])], 'lin')
    # the toque jolts with the slam but stays put: it comes off on purpose, later (vendor_unmask)
    a.pos('toque', [(0, [0, 0, 0]), (0.45, [0, 0, 0]), (0.55, [0, 3, 0], 'easeOutQuad'), (0.8, [0, 0, 0], 'easeOutBounce'),
                    (3.0, [0, 0, 0])], 'lin')
    a.rot('toque', [(0, [0, 0, 0]), (0.45, [0, 0, 0]), (0.55, [-6, 0, 8]), (0.9, [0, 0, 0], 'easeOutBounce'),
                    (3.0, [0, 0, 0])], 'lin')
    for brow, ang in (('brow_r', ANGRY_R), ('brow_l', ANGRY_L)):
        a.pos(brow, [(0, [0, -0.7, 0]), (1.0, [0, -1.0, 0]), (3.0, [0, -0.7, 0])])
        a.rot(brow, [(0, ang), (1.0, [0, 0, ang[2] * 1.4]), (3.0, ang)])
    a.rot('mustache', [(0, [0, 0, 0]), (1.0, [-12, 0, 0]), (2.0, [-12, 0, 0]), (2.4, [0, 0, 0]), (3.0, [0, 0, 0])])
    a.sound(0.45, 'slam').sound(0.75, 'pop').sound(1.0, 'roar').sound(1.3, 'plop')
    return a


def v_furious():
    """2.0 s loop. Heaving, trembling, brows down, fists out."""
    a = P('vendor_furious', 2.0, True)
    a.scale('body', [(0, [1, 1, 1]), (1.0, [1.05, 1.06, 1.05]), (2.0, [1, 1, 1])])
    a.rot('torso', [(round(0.1 * i, 2), [4, 0, 1.5 if i % 2 == 0 else -1.5]) for i in range(21)])
    for arm, s in (('arm_r', 1), ('arm_l', -1)):
        a.rot(arm, [(0, [-20, 0, 26 * s]), (1.0, [-26, 0, 30 * s]), (2.0, [-20, 0, 26 * s])])
    for brow, ang in (('brow_r', ANGRY_R), ('brow_l', ANGRY_L)):
        a.pos(brow, [(0, [0, -0.7, 0])], 'lin')
        a.rot(brow, [(0, ang)], 'lin')
    a.rot('mustache', [(0, [0, 0, 0]), (0.5, [0, 0, 3]), (1.0, [0, 0, 0]), (1.5, [0, 0, -3]), (2.0, [0, 0, 0])])
    return a


# ---- the unmasking. After FRAUD the taquero knows the game is up: the coupon was a fake, and so
# is he. He rips off his (fake) mustache, takes off his toque, and there it is: Norwood 7. Then it
# dawns on him what he has just shown everybody.

HIDDEN = [0, 0, 0]
SHOWN = [1, 1, 1]


def stretch(a, arm, keys):
    """Papon's arms are stubs: they stretch (scale Y about the shoulder) to reach his face and hat."""
    a.scale(arm, [(t, [1, k, 1]) + tuple(rest) for t, k, *rest in keys], 'lin')
TO_STACHE = [-92, -42, 0]         # right hand at his mustache (arm stretched 1.5x to get there)
RIP_OUT = [-80, 10, 96]           # ...yanked out to his right
HANDS_UP_R = [-176, 0, 2]         # both hands up at the toque's brim (arms stretched 2.3x: cartoon reach)
HANDS_UP_L = [-176, 0, -2]
LIFT_R = [-180, 0, -2]
LIFT_L = [-180, 0, 2]
ON_HEAD_R = [-166, -20, 4]        # hands clamped on his bare head (arms stretched 1.8x)
ON_HEAD_L = [-166, 20, -4]


def unmasked(a):
    """Keys for every animation after the toque and mustache are gone."""
    a.scale('toque', [(0, HIDDEN)], 'lin')
    a.scale('mustache', [(0, HIDDEN)], 'lin')
    a.scale('hair', [(0, SHOWN)], 'lin')
    a.scale('wisps', [(0, SHOWN)], 'lin')
    return a


def nervous_brows(a, t0, t1):
    for brow, s in (('brow_r', 1), ('brow_l', -1)):
        a.pos(brow, [(0, [0, -0.7, 0]), (t0, [0, 1.6, 0], 'easeOutBack'), (t1, [0, 1.4, 0])], 'lin')
        a.rot(brow, [(0, [0, 0, 18 * s]), (t0, [0, 0, -14 * s], 'easeOutBack'), (t1, [0, 0, -12 * s])], 'lin')


def v_exposed():
    """3.0 s. Found out. The fury drains away: sweat, darting looks, a tug at the collar, a weak laugh."""
    a = P('vendor_exposed', 3.0, 'hold')
    a.rot('arm_r', [(0, [-20, 0, 26]), (0.35, [-6, 0, 6], 'easeInOutSine'), (3.0, [-6, 0, 6])], 'lin')
    # the collar tug
    a.rot('arm_l', [(0, [-20, 0, -26]), (0.35, [-6, 0, -6], 'easeInOutSine'), (1.5, [-6, 0, -6]),
                    (1.75, [-128, 30, 0], 'easeOutQuad'), (1.9, [-122, 34, 0]), (2.05, [-128, 30, 0]), (2.2, [-122, 34, 0]),
                    (2.5, [-6, 0, -6], 'easeInOutSine'), (3.0, [-6, 0, -6])], 'lin')
    a.scale('body', [(0, [1.04, 1.04, 1.04]), (0.4, [0.96, 0.95, 0.96], 'easeOutQuad'), (3.0, [0.96, 0.95, 0.96])], 'lin')
    a.rot('torso', [(0, [4, 0, 0]), (0.4, [8, 0, 0]), (3.0, [8, 0, 0])])
    # eyes dart: the whole potato glances left, right, back
    a.rot('root', [(0, [0, 0, 0]), (0.5, [0, 0, 0]), (0.62, [0, 24, 0], 'easeOutQuad'), (0.95, [0, 24, 0]),
                   (1.07, [0, -24, 0], 'easeOutQuad'), (1.35, [0, -24, 0]), (1.47, [0, 0, 0], 'easeOutQuad'),
                   (3.0, [0, 0, 0])], 'lin')
    nervous_brows(a, 0.3, 3.0)
    a.vis('sweat', False, [(0.45, True)])
    a.pos('sweat', [(0, [0, 0, 0]), (0.45, [0, 0, 0]), (3.0, [0, -2.5, 0])], 'lin')
    a.rot('mustache', [(0, [0, 0, 0]), (0.4, [8, 0, 0]), (2.6, [8, 0, 0])]
          + [(round(2.6 + 0.07 * i, 2), [8, 0, 3 if i % 2 == 0 else -3]) for i in range(6)])
    # the weak laugh: three little hops
    a.pos('root', [(0, [0, 0, 0]), (2.5, [0, 0, 0]), (2.6, [0, 0.6, 0]), (2.7, [0, 0, 0]), (2.8, [0, 0.6, 0]),
                   (2.9, [0, 0, 0]), (3.0, [0, 0, 0])], 'lin')
    a.sound(0.4, 'gulp').sound(1.75, 'tie').sound(2.55, 'nervous')
    return a


def v_unmask():
    """5.0 s. Rips off the mustache (0.95 s) and flings it out of the window; lifts off the toque
    (from 2.85 s: the reveal); tosses it; stands there, bald as an egg, for exactly one second."""
    a = P('vendor_unmask', 5.0, 'hold')
    a.rot('arm_r', [(0, [-6, 0, 6]), (0.55, TO_STACHE, 'easeInOutSine'), (0.75, [-106, -46, 2]), (0.85, [-102, -48, -2]),
                    (0.95, RIP_OUT, 'easeOutExpo'), (1.5, [-84, 10, 92]), (1.75, [-150, -20, 40], 'easeInQuad'),
                    (1.95, [-30, -30, 10], 'easeOutQuad'), (2.3, [-30, -30, 10]), (2.7, HANDS_UP_R, 'easeInOutSine'),
                    (2.85, HANDS_UP_R), (3.6, LIFT_R, 'easeInOutSine'), (3.9, LIFT_R), (4.25, [-60, 0, 70], 'easeInQuad'),
                    (4.45, [-20, 0, 30], 'easeOutQuad'), (5.0, [-20, 0, 34])], 'lin')
    a.rot('arm_l', [(0, [-6, 0, -6]), (2.3, [-6, 0, -6]), (2.7, HANDS_UP_L, 'easeInOutSine'), (2.85, HANDS_UP_L),
                    (3.6, LIFT_L, 'easeInOutSine'), (3.9, LIFT_L), (4.25, [-60, 0, -70], 'easeInQuad'),
                    (4.45, [-20, 0, -30], 'easeOutQuad'), (5.0, [-20, 0, -34])], 'lin')
    # the mustache comes away in his hand, hangs there while he winces, then flies out the window
    a.pos('mustache', [(0, [0, 0, 0]), (0.9, [0, 0, 0]), (0.95, [-11, -1, 6], 'easeOutExpo'), (1.5, [-11, -1.5, 6]),
                       (1.75, [-8, 12, 2], 'easeInQuad'), (2.2, [-4, 18, -40], 'easeOutQuad')], 'lin')
    a.rot('mustache', [(0, [0, 0, 0]), (0.9, [0, 0, 0]), (0.95, [0, 50, 70], 'easeOutExpo'), (1.5, [0, 50, 76]),
                       (2.2, [360, 90, 300], 'easeOutQuad')], 'lin')
    a.scale('mustache', [(0, SHOWN), (2.0, SHOWN), (2.2, HIDDEN, 'easeInQuad'), (5.0, HIDDEN)], 'lin')
    stretch(a, 'arm_r', [(0, 1), (0.55, 1.5, 'easeInOutSine'), (0.9, 1.5), (0.95, 1.2, 'easeOutExpo'), (1.5, 1.2),
                          (1.95, 1, 'easeOutQuad'), (2.3, 1), (2.7, 2.3, 'easeInOutSine'), (3.6, 2.6, 'easeInOutSine'),
                          (3.9, 2.6), (4.25, 1.6, 'easeInQuad'), (4.45, 1, 'easeOutQuad'), (5.0, 1)])
    stretch(a, 'arm_l', [(0, 1), (2.3, 1), (2.7, 2.3, 'easeInOutSine'), (3.6, 2.6, 'easeInOutSine'), (3.9, 2.6),
                          (4.25, 1.6, 'easeInQuad'), (4.45, 1, 'easeOutQuad'), (5.0, 1)])
    # the toque: lifted slow and high (the hair is under it), held, swept down and tossed behind him
    a.pos('toque', [(0, [0, 0, 0]), (2.85, [0, 0, 0]), (3.6, [0, 6, 0], 'easeInOutSine'), (3.9, [0, 6.5, 0]),
                    (4.25, [-10, 4, 6], 'easeInQuad'), (4.6, [-22, -10, 18], 'easeOutQuad')], 'lin')
    a.rot('toque', [(0, [0, 0, 0]), (3.9, [0, 0, 0]), (4.6, [-60, 40, 120], 'easeOutQuad')], 'lin')
    a.scale('toque', [(0, SHOWN), (4.4, SHOWN), (4.6, HIDDEN, 'easeInQuad'), (5.0, HIDDEN)], 'lin')
    a.vis('hair', False, [(2.86, True)])
    a.vis('wisps', False, [(2.86, True)])
    a.rot('wisps', [(0, [0, 0, 0]), (2.86, [0, 0, 0]), (3.2, [0, 0, 18], 'easeOutBack'), (3.5, [0, 0, -12]),
                    (3.8, [0, 0, 8]), (4.2, [0, 0, -4]), (5.0, [0, 0, 0])])
    # body: lean in to grab, jolt at the rip, wince, stand tall for the reveal
    a.rot('torso', [(0, [8, 0, 0]), (0.55, [10, 0, 0]), (0.9, [10, 0, 0]), (0.95, [-6, 0, -8], 'easeOutExpo'),
                    (1.2, [4, 0, 4]), (1.5, [4, 0, -2]), (1.95, [6, 0, 0]), (2.85, [-4, 0, 0]),
                    (3.6, [-10, 0, 0]), (4.45, [-6, 0, 0]), (5.0, [-8, 0, 0])])
    a.scale('body', [(0, [0.96, 0.95, 0.96]), (0.9, [0.96, 0.95, 0.96]), (0.95, [1.08, 0.92, 1.08], 'easeOutExpo'),
                     (1.15, [0.97, 1.03, 0.97]), (1.4, [1, 1, 1]), (3.6, [1.03, 1.04, 1.03]), (4.5, [1.06, 1.06, 1.06]),
                     (5.0, [1.06, 1.06, 1.06])], 'cat')
    for brow, s in (('brow_r', 1), ('brow_l', -1)):
        a.pos(brow, [(0, [0, 1.4, 0]), (0.5, [0, -0.6, 0]), (0.9, [0, -0.6, 0]), (0.95, [0, -1.4, 0], 'easeOutExpo'),
                     (1.6, [0, -1.0, 0]), (2.85, [0, -0.5, 0]), (3.6, [0, 0.4, 0]), (5.0, [0, 0.2, 0])], 'lin')
        a.rot(brow, [(0, [0, 0, -12 * s]), (0.5, [0, 0, 18 * s]), (0.95, [0, 0, 30 * s], 'easeOutExpo'),
                     (1.6, [0, 0, 18 * s]), (3.6, [0, 0, 4 * s]), (5.0, [0, 0, 6 * s])], 'lin')
    a.vis('sweat', True, [(0.9, False)])
    a.sound(0.5, 'tie').sound(0.95, 'rip').sound(1.05, 'yelp').sound(1.85, 'whoosh').sound(2.8, 'cloth')
    a.sound(3.55, 'reveal').sound(4.3, 'whoosh').sound(4.6, 'plop')
    return a


def v_realize():
    """3.0 s. A breeze. The two hairs on top wave in it. He feels it on his scalp, pats his head,
    and it dawns on him: everyone saw. Blushing, he clamps both hands over his head."""
    a = unmasked(P('vendor_realize', 3.0, 'hold'))
    a.rot('wisps', [(round(0.15 * i, 2), [0, 0, 22 if i % 2 == 0 else -22]) for i in range(21)])
    a.rot('arm_r', [(0, [-20, 0, 34]), (0.5, [-20, 0, 34]), (1.0, [-176, -24, 10], 'easeInOutSine'),
                    (1.15, [-168, -24, 10]), (1.3, [-176, -24, 10]), (1.45, [-168, -24, 10]), (1.75, [-20, 0, 30], 'easeInOutSine'),
                    (2.15, [-20, 0, 30]), (2.35, ON_HEAD_R, 'easeOutBack')]
          + [(round(2.45 + 0.07 * i, 2), [ON_HEAD_R[0] + (3 if i % 2 else -3), ON_HEAD_R[1], ON_HEAD_R[2]]) for i in range(8)], 'lin')
    a.rot('arm_l', [(0, [-20, 0, -34]), (2.15, [-20, 0, -34]), (2.35, ON_HEAD_L, 'easeOutBack')]
          + [(round(2.45 + 0.07 * i, 2), [ON_HEAD_L[0] + (3 if i % 2 else -3), ON_HEAD_L[1], ON_HEAD_L[2]]) for i in range(8)], 'lin')
    stretch(a, 'arm_r', [(0, 1), (0.5, 1), (1.0, 2.0, 'easeInOutSine'), (1.45, 2.0), (1.75, 1, 'easeInOutSine'),
                          (2.15, 1), (2.35, 1.8, 'easeOutBack'), (3.0, 1.8)])
    stretch(a, 'arm_l', [(0, 1), (2.15, 1), (2.35, 1.8, 'easeOutBack'), (3.0, 1.8)])
    # he looks down at the crowd... then straight at you
    a.rot('torso', [(0, [-8, 0, 0]), (0.6, [-4, 0, 0]), (1.75, [0, 0, 0]), (2.0, [10, 0, 0]), (2.35, [14, 0, 0], 'easeOutBack'),
                    (3.0, [14, 0, 0])])
    a.rot('root', [(0, [0, 0, 0]), (1.75, [0, 0, 0]), (2.0, [0, -10, 0]), (2.15, [0, 0, 0])])
    a.scale('body', [(0, [1.06, 1.06, 1.06]), (0.6, [1.02, 1.02, 1.02]), (1.9, [1, 1, 1]), (2.15, [0.94, 0.94, 0.94]),
                     (2.4, [0.92, 0.9, 0.92], 'easeOutBack'), (3.0, [0.92, 0.9, 0.92])], 'cat')
    for brow, s in (('brow_r', 1), ('brow_l', -1)):
        a.pos(brow, [(0, [0, 0.2, 0]), (1.0, [0, 0.2, 0]), (1.6, [0, 1.0, 0]), (1.9, [0, 2.0, 0], 'easeOutBack'),
                     (3.0, [0, 1.8, 0])], 'lin')
        a.rot(brow, [(0, [0, 0, 6 * s]), (1.6, [0, 0, -6 * s]), (1.9, [0, 0, -18 * s], 'easeOutBack'), (3.0, [0, 0, -16 * s])], 'lin')
    a.sound(0.2, 'breeze').sound(1.1, 'pat').sound(1.4, 'pat').sound(1.9, 'gasp').sound(2.35, 'pat')
    return a


def v_rage():
    """3.5 s. Embarrassment boils over into fury: trembling, swelling, red, then the hands come off
    his head and he ROARS (1.7 s). Ends crouched to jump: the boss's leap_out starts from this pose."""
    a = unmasked(P('vendor_rage', 3.5, 'hold'))
    a.scale('body', [(round(0.1 * i, 2), [1 + 0.012 * i + (0.02 if i % 2 else 0), 1 + 0.01 * i, 1 + 0.012 * i + (0.02 if i % 2 else 0)])
                     for i in range(16)]
            + [(1.7, [0.9, 1.25, 0.9], 'easeOutExpo')]
            + [(round(1.8 + 0.08 * i, 2), [0.92, 1.22, 0.92] if i % 2 else [0.88, 1.27, 0.88]) for i in range(10)]
            + [(3.0, [1.15, 0.85, 1.15], 'easeInOutSine'), (3.5, [1.18, 0.82, 1.18])], 'lin')
    a.rot('torso', [(0, [14, 0, 0]), (1.5, [18, 0, 0]), (1.7, [-22, 0, 0], 'easeOutExpo'), (2.6, [-20, 0, 0]),
                    (3.1, [26, 0, 0], 'easeInOutSine'), (3.5, [28, 0, 0])], 'lin')
    a.pos('root', [(0, [0, 0, 0]), (2.6, [0, 0, 0]), (3.1, [0, -3, 0], 'easeInOutSine'), (3.5, [0, -3.5, 0])], 'lin')
    for leg in ('leg_r', 'leg_l'):
        a.rot(leg, [(0, [0, 0, 0]), (2.6, [0, 0, 0]), (3.1, [-40, 0, 0], 'easeInOutSine'), (3.5, [-44, 0, 0])], 'lin')
    for brow, ang in (('brow_r', ANGRY_R), ('brow_l', ANGRY_L)):
        a.pos(brow, [(0, [0, 1.8, 0]), (1.2, [0, 0, 0]), (1.6, [0, -1.2, 0]), (3.5, [0, -1.0, 0])], 'lin')
        a.rot(brow, [(0, [0, 0, -ang[2] * 0.8]), (1.2, [0, 0, 0]), (1.6, [0, 0, ang[2] * 1.6]), (3.5, [0, 0, ang[2] * 1.5])], 'lin')
    a.rot('wisps', [(0, [0, 0, 0]), (1.7, [0, 0, 0]), (1.8, [0, 0, 30]), (1.9, [0, 0, -30]), (2.0, [0, 0, 30]),
                    (2.1, [0, 0, -30]), (2.3, [0, 0, 0])])
    a.sound(0.1, 'rumble').sound(0.9, 'rumble').sound(1.7, 'roar').sound(1.75, 'slam').sound(3.1, 'crouch')
    return a


def v_wave():
    """2.5 s (beaten, back at his window, friendly). A big wave over his head, a lean out of the
    window, a pat on the counter: come on over, amigo. Bald and proud (client/VendorRenderer shows the
    hair this pins away)."""
    a = P('vendor_wave', 2.5, 'hold')
    wave = [(0, [0, 0, 2]), (0.35, [-160, 0, 30], 'easeOutBack')]
    for i in range(5):
        wave.append((round(0.55 + 0.22 * i, 2), [-165, 0, 48 if i % 2 == 0 else 18]))
    wave += [(1.75, [-150, 0, 30]), (2.1, [0, 0, 2], 'easeInOutSine'), (2.5, [0, 0, 2])]
    a.rot('arm_r', wave, 'lin')
    a.rot('arm_l', [(0, [0, 0, -2]), (1.8, [0, 0, -2]), (2.0, [-70, 10, 0], 'easeInOutSine'), (2.12, [-62, 10, 0]),
                    (2.24, [-70, 10, 0]), (2.36, [-62, 10, 0]), (2.5, [-40, 6, 0], 'easeInOutSine')], 'lin')
    a.rot('torso', [(0, [0, 0, 0]), (0.35, [10, 0, -6], 'easeOutQuad'), (1.0, [10, 0, 6]), (1.6, [10, 0, -4]),
                    (2.0, [14, 0, 0], 'easeInOutSine'), (2.5, [4, 0, 0])], 'lin')
    a.scale('body', [(0, [1, 1, 1]), (0.3, [1.04, 0.96, 1.04], 'easeOutQuad'), (0.6, [1, 1, 1])], 'lin')
    for brow in ('brow_r', 'brow_l'):
        a.pos(brow, [(0, [0, 0, 0]), (0.35, [0, 1.0, 0], 'easeOutBack'), (2.0, [0, 0.8, 0]), (2.5, [0, 0.3, 0])], 'lin')
    a.sound(0.3, 'hmm').sound(2.0, 'tap').sound(2.24, 'tap')
    return a


PAPON = [p_walk, p_walk_taco, p_hold_taco, p_idle, p_order, p_receive, p_react,
         v_idle, v_take_order, v_cook, v_serve, v_inspect, v_fraud, v_furious,
         v_exposed, v_unmask, v_realize, v_rage, v_wave]


def write(name, fns, prefix):
    data = {'format_version': '1.8.0', 'animations': {}}
    for fn in fns:
        a = fn()
        data['animations'][prefix + a.name] = guard_brows(a.compile())
    path = os.path.join(OUT_DIR, name + '.animation.json')
    os.makedirs(OUT_DIR, exist_ok=True)
    with open(path, 'w', newline='\n') as f:
        json.dump(data, f, separators=(',', ':'))
    return path


if __name__ == '__main__':
    print(write('customer', HUMAN, 'animation.customer.'))
    print(write('papon_npc', PAPON, 'animation.papon_npc.'))
