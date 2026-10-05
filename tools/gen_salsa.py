#!/usr/bin/env python3
"""The muchachas (phase four: "Hey! Where are the muchachas?"): skeleton Papons in salsa dresses who rise out of the floor
by the band's stage and dance to his last song (boss/SalsaDancerEntity, client/SalsaDancerRenderer).

Model `salsa_dancer` (the wkcr Papon, bone for bone, arms split by rig_hands): long black hair down her back and a crown
of marigolds with a red rose; gold hoop earrings; a fitted bodice with an off-the-shoulder frill, and a two-tier ruffled
skirt flaring to the floor (`skirt` > `skirt_2`: animations swing and flare them). Turned to bone like the band of the
dead (gen_muertos.turned: the sugar-skull face, the eyes aglow), four dresses: salsa_dancer_0 red and black,
_1 marigold and purple, _2 turquoise and rosa, _3 black and gold.

Animations (salsa_dancer.animation.json), on Persona Ideal's beat (0.47 s):
  salsa_a/_b/_c  loops of 8 counts: the forward-and-back basic, the side basic with a spin, a cross-body shimmy
  rise           2.0 s = SalsaDancerEntity.State.RISE: up out of the floor spinning, arms up, into a pose
  twirled        5.5 s = TWIRLED (in step with the boss's p4_muchachas from p4.twirl): her hand up into his, three
                 turns under it, the skirt flying out; the dip; a few steps with him
  mourn          a loop: still, head hung

  python3.11 tools/gen_salsa.py
"""
import json
import os

import numpy as np
from PIL import Image

from gen_anims import guard_brows
from gen_npc_anims import Pinned
from gen_vendor import Model, bone, rig_hands, HAND_R, HAND_L

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/papon')
OUT_ANIM = os.path.join(ASSETS, 'animations/entity/salsa_dancer.animation.json')

DRESS = (200, 24, 36)          # painted in this red, recoloured per dress
TRIM = (24, 20, 26)            # and this black
HAIR = (26, 20, 24)
GOLD = (236, 186, 64)
MARIGOLD = (255, 150, 24)
ROSE = (200, 20, 40)

LOOKS = [
    ((200, 24, 36), (24, 20, 26)),        # red, black ruffles
    ((255, 150, 24), (122, 44, 186)),     # marigold, purple
    ((24, 186, 178), (236, 46, 140)),     # turquoise, rosa
    ((28, 24, 32), (236, 186, 64)),       # black, gold
]


def ruffle(p, f):
    """A frilled edge: the trim colour scalloped along the bottom rows of each side."""
    for s in ('front', 'back', 'left', 'right'):
        x0, y0, x1, y1 = (int(n) for n in f[s])
        for x in range(x0, x1):
            for y in range(max(y0, y1 - 2), y1):
                if (x + (y1 - y)) % 3 != 0:
                    p.put(x, y, TRIM, 0.04)


def bodice(p, f):
    for s in ('front', 'back', 'left', 'right'):
        x0, y0, x1, y1 = (int(n) for n in f[s])
        for x in range(x0, x1):
            p.put(x, y1 - 1, TRIM, 0.04)
        # lacing down the front
        if s == 'front':
            cx = (x0 + x1) // 2
            for y in range(y0, y1 - 1, 2):
                p.put(cx, y, GOLD, 0.03)


def hair_paint(p, f):
    for s in f.values():
        x0, y0, x1, y1 = (int(n) for n in s)
        for x in range(x0, x1):
            for y in range(y0, y1):
                if (x * 3 + y) % 5 == 0:
                    p.put(x, y, (52, 40, 48), 0.03)


def flower_paint(color):
    def paint(p, f):
        for s in f.values():
            x0, y0, x1, y1 = (int(n) for n in s)
            cx, cy = (x0 + x1 - 1) / 2, (y0 + y1 - 1) / 2
            for x in range(x0, x1):
                for y in range(y0, y1):
                    if abs(x - cx) < 0.6 and abs(y - cy) < 0.6:
                        p.put(x, y, (255, 214, 80) if color != ROSE else (120, 10, 24), 0.02)
                    elif (x + y) % 2:
                        p.put(x, y, tuple(min(255, int(c * 1.15)) for c in color), 0.04)
    return paint


def build():
    m = Model('salsa_dancer', 7140, tex=256)
    rig_hands(m)
    # the dress: a fitted bodice over the lower potato, a frill off the shoulders, the skirt in two ruffled tiers
    m.add(bone('bodice', [0, 8, 0], 'body', [m.box([-8, 4, -8], [16, 8, 16], DRESS, extra=bodice, inflate=0.3)]))
    m.add(bone('frill', [0, 12, 0], 'body', [m.box([-9, 11, -9], [18, 2, 18], DRESS, extra=ruffle, inflate=0.1)]))
    m.add(bone('skirt', [0, 6, 0], 'torso', [m.box([-10, 2, -10], [20, 5, 20], DRESS, extra=ruffle, noise=0.06)]))
    m.add(bone('skirt_2', [0, 3, 0], 'skirt', [m.box([-12, 0, -12], [24, 3, 24], DRESS, extra=ruffle, noise=0.06)]))
    # hair: a cap over the top of the potato, long down her back
    m.add(bone('hair', [0, 22, 4], 'body', [
        m.box([-8, 22, -8], [16, 3, 16], HAIR, extra=hair_paint, inflate=0.25),
        m.box([-7, 9, 8], [14, 14, 2], HAIR, extra=hair_paint),
        m.box([-5, 6, 8.5], [10, 4, 2], HAIR, extra=hair_paint),
    ]))
    # a crown of marigolds round the top, a red rose over her left ear
    flowers = []
    for x, z in ((-7, -4), (-4, -7), (0, -8), (4, -7), (7, -4), (8, 1), (-8, 1)):
        flowers.append(m.box([x - 1.5, 24, z - 1.5], [3, 3, 3], MARIGOLD, extra=flower_paint(MARIGOLD)))
    flowers.append(m.box([6, 20, -6], [4, 4, 4], ROSE, extra=flower_paint(ROSE)))
    m.add(bone('flowers', [0, 24, 0], 'body', flowers))
    # gold hoops
    m.add(bone('earrings', [0, 14, 0], 'body', [
        m.box([-8.7, 12, -1], [1, 3, 2], GOLD), m.box([7.7, 12, -1], [1, 3, 2], GOLD)]))
    out = m.save()
    looks(m)
    return out


def looks(m):
    """The four dresses, on the dead's bone (gen_muertos.turned)."""
    import gen_muertos
    import gen_muertos_papon as gmp
    base = np.asarray(gen_muertos.turned('salsa_dancer').convert('RGBA')).copy()
    g = json.load(open(os.path.join(ASSETS, 'geo/entity/salsa_dancer.geo.json')))['minecraft:geometry'][0]
    by = {b['name']: b for b in g['bones']}
    rects = []
    for n in ('bodice', 'frill', 'skirt', 'skirt_2'):
        rects += gmp.rect_list(by[n].get('cubes', []))
    for i, (dress, trim) in enumerate(LOOKS):
        img = base.copy()
        for x0, y0, w, h in rects:
            for y in range(y0, min(img.shape[0], y0 + h)):
                for x in range(x0, min(img.shape[1], x0 + w)):
                    px = img[y, x]
                    if not px[3]:
                        continue
                    if px[0] > 120 and px[1] < 80:          # the dress red: its shading kept
                        k = px[0] / DRESS[0]
                        img[y, x, :3] = np.clip(np.array(dress) * k, 0, 255)
                    elif px[0] < 60 and px[1] < 60 and px[2] < 60:      # the black trim
                        k = (int(px[0]) + 8) / (TRIM[0] + 8)
                        img[y, x, :3] = np.clip(np.array(trim) * k, 0, 255)
        Image.fromarray(img, 'RGBA').save(os.path.join(ASSETS, 'textures/entity/salsa_dancer_%d.png' % i))


# ===================================================================== animations

BEAT = 0.47


class S(Pinned):
    pins = []


def t(n):
    return round(n * BEAT, 4)


ARM_UP_R = [-10, 0, 168]          # straight up over her head (her hand in his for the twirl)
HIP_L = [10, -30, -38]            # left hand on her hip
FLOURISH_R = [-30, 0, 112]        # right arm up and out, the wrist turned


def hips(a, n, amount=7):
    """The hips (torso roll) swinging on every beat for n beats, the skirt following a little late."""
    a.rot('torso', [(t(i), [0, amount * 0.5 * (1 if i % 2 else -1), amount * (1 if i % 2 else -1)]) for i in range(n + 1)])
    a.rot('skirt', [(round(t(i) + 0.08, 4), [0, -amount * (1 if i % 2 else -1), -amount * 1.2 * (1 if i % 2 else -1)]) for i in range(n)]
          + [(t(n), [0, amount, amount * 1.2])])


def salsa_a():
    """The basic: forward on 1-2-3, hold, back on 5-6-7, hold; the hips on every beat; her right arm up flourishing."""
    a = S('salsa_a', t(8), True)
    hips(a, 8)
    a.pos('root', [(t(0), [0, 0, 0]), (t(1), [0, 0.5, -1.6]), (t(2), [0, 0, -0.8]), (t(3), [0, 0, 0]), (t(4), [0, 0, 0]),
                   (t(5), [0, 0.5, 1.6]), (t(6), [0, 0, 0.8]), (t(7), [0, 0, 0]), (t(8), [0, 0, 0])])
    a.rot('arm_r', [(t(i), [FLOURISH_R[0] + (8 if i % 2 else -8), FLOURISH_R[1] + (14 if i % 4 < 2 else -14), FLOURISH_R[2]]) for i in range(9)])
    a.rot('arm_l', [(0, HIP_L), (t(8), HIP_L)])
    a.rot('body', [(t(i), [0, (6 if i % 4 < 2 else -6), (3 if i % 2 else -3)]) for i in range(9)])
    a.rot('hair', [(t(i) + 0.1, [6 if i % 2 else -2, 0, 4 if i % 2 else -4]) for i in range(8)] + [(t(8), [0, 0, 0])])
    a.rot('leg_r', [(t(i), [-14 if i in (1, 5) else 0, 0, 0]) for i in range(9)])
    a.rot('leg_l', [(t(i), [-14 if i in (2, 6) else 0, 0, 0]) for i in range(9)])
    return a


def salsa_b():
    """Side to side, then on 5-6-7 a full turn, both arms up, the skirt flying out."""
    a = S('salsa_b', t(8), True)
    hips(a, 4)
    a.pos('root', [(t(0), [0, 0, 0]), (t(1), [-1.4, 0.4, 0]), (t(2), [0, 0, 0]), (t(3), [1.4, 0.4, 0]), (t(4), [0, 0, 0]), (t(8), [0, 0, 0])])
    a.rot('root', [(t(0), [0, 0, 0]), (t(4.5), [0, 0, 0]), (t(7), [0, 360, 0], 'easeInOutSine'), (t(8), [0, 360, 0])], 'lin')
    a.rot('arm_r', [(0, [-20, 0, 70]), (t(4), [-20, 0, 70]), (t(4.6), ARM_UP_R), (t(7.2), ARM_UP_R), (t(8), [-20, 0, 70])])
    a.rot('arm_l', [(0, [-20, 0, -70]), (t(4), [-20, 0, -70]), (t(4.6), [-10, 0, -150]), (t(7.2), [-10, 0, -150]), (t(8), [-20, 0, -70])])
    a.scale('skirt', [(0, [1, 1, 1]), (t(4.6), [1, 1, 1]), (t(5.6), [1.25, 0.9, 1.25]), (t(7), [1.25, 0.9, 1.25]), (t(8), [1, 1, 1])])
    a.scale('skirt_2', [(0, [1, 1, 1]), (t(4.6), [1, 1, 1]), (t(5.6), [1.35, 0.85, 1.35]), (t(7), [1.35, 0.85, 1.35]), (t(8), [1, 1, 1])])
    a.rot('body', [(t(i), [0, 0, (4 if i % 2 else -4)]) for i in range(9)])
    return a


def salsa_c():
    """A cross-body lead and a shimmy: the shoulders shaking, the arms out like wings, a kick on 4 and 8."""
    a = S('salsa_c', t(8), True)
    a.rot('torso', [(round(i * BEAT / 4, 4), [0, 9 if i % 2 else -9, 0]) for i in range(33)], 'lin')
    a.rot('arm_r', [(t(i), [-30, 10, 100 + (12 if i % 2 else -6)]) for i in range(9)])
    a.rot('arm_l', [(t(i), [-30, -10, -100 - (12 if i % 2 else -6)]) for i in range(9)])
    a.rot('root', [(t(0), [0, 0, 0]), (t(2), [0, -40, 0]), (t(4), [0, 0, 0]), (t(6), [0, 40, 0]), (t(8), [0, 0, 0])])
    a.pos('root', [(t(i), [0, 0.6 if i % 2 else 0, 0]) for i in range(9)])
    a.rot('leg_r', [(t(3.5), [0, 0, 0]), (t(4), [-40, 0, 0]), (t(4.5), [0, 0, 0])])
    a.rot('leg_l', [(t(7.5), [0, 0, 0]), (t(8), [-40, 0, 0])])
    a.rot('skirt', [(t(i), [0, (12 if i % 2 else -12), 0]) for i in range(9)])
    a.rot('body', [(t(i), [-6 if i % 4 == 0 else 0, 0, 0]) for i in range(9)])
    return a


def rise():
    """2.0 s: up out of the floor, spinning, arms rising; a pose on the last beat: right arm high, left on her hip."""
    a = S('rise', 2.0, 'hold')
    a.pos('root', [(0, [0, -26, 0]), (1.2, [0, 0.6, 0], 'easeOutBack'), (1.5, [0, 0, 0]), (2.0, [0, 0, 0])])
    a.rot('root', [(0, [0, -540, 0]), (1.3, [0, 0, 0], 'easeOutCubic'), (2.0, [0, 0, 0])], 'lin')
    a.rot('arm_r', [(0, [-10, 0, 160]), (1.3, [-10, 0, 160]), (1.6, FLOURISH_R, 'easeOutBack'), (2.0, FLOURISH_R)])
    a.rot('arm_l', [(0, [-10, 0, -160]), (1.3, [-10, 0, -160]), (1.6, HIP_L, 'easeOutBack'), (2.0, HIP_L)])
    a.scale('skirt', [(0, [1.3, 0.8, 1.3]), (1.3, [1.3, 0.8, 1.3]), (1.7, [1, 1, 1]), (2.0, [1, 1, 1])])
    a.scale('skirt_2', [(0, [1.4, 0.8, 1.4]), (1.3, [1.4, 0.8, 1.4]), (1.7, [1, 1, 1]), (2.0, [1, 1, 1])])
    a.rot('body', [(1.5, [0, 0, 0]), (1.7, [-10, 0, 8], 'easeOutBack'), (2.0, [-8, 0, 8])])
    a.sound(0.1, 'whoosh')
    return a


def twirled():
    """5.5 s (110 t): her right hand up into his (0-0.4); three turns under it (0.4-2.4), the skirt flying; facing him again;
    the dip (2.9-4.0: back over his arm, her left arm flung out); up (4.0-4.6); a few steps together, hips going."""
    a = S('twirled', 5.5, 'hold')
    a.rot('arm_r', [(0, FLOURISH_R), (0.4, ARM_UP_R, 'easeOutBack'), (2.4, ARM_UP_R), (2.8, [-60, 0, 40]), (4.0, [-60, 0, 40]),
                    (4.6, FLOURISH_R), (5.5, FLOURISH_R)])
    a.rot('arm_l', [(0, HIP_L), (0.4, [-10, 0, -80]), (2.4, [-10, 0, -80]), (2.9, [-40, 0, -130], 'easeOutBack'), (4.0, [-40, 0, -140]),
                    (4.6, HIP_L), (5.5, HIP_L)])
    a.rot('root', [(0, [0, 0, 0]), (0.4, [0, 0, 0]), (2.4, [0, 1080, 0], 'easeInOutSine'), (5.5, [0, 1080, 0])], 'lin')
    a.scale('skirt', [(0, [1, 1, 1]), (0.6, [1.3, 0.85, 1.3]), (2.2, [1.3, 0.85, 1.3]), (2.7, [1, 1, 1]), (5.5, [1, 1, 1])])
    a.scale('skirt_2', [(0, [1, 1, 1]), (0.6, [1.45, 0.8, 1.45]), (2.2, [1.45, 0.8, 1.45]), (2.7, [1, 1, 1]), (5.5, [1, 1, 1])])
    a.rot('torso', [(0, [0, 0, 0]), (2.6, [0, 0, 0]), (2.9, [-42, 0, 0], 'easeOutQuad'), (3.8, [-46, 0, 0]), (4.3, [0, 0, 0], 'easeInOutSine'),
                    (4.6, [0, 0, 0])] + [(round(4.6 + i * BEAT, 3), [0, 4, 7 if i % 2 else -7]) for i in range(1, 3)])
    a.pos('root', [(0, [0, 0, 0]), (2.6, [0, 0, 0]), (2.9, [0, -2.5, 0]), (3.8, [0, -3, 0]), (4.3, [0, 0, 0]), (5.5, [0, 0, 0])])
    a.rot('leg_r', [(2.6, [0, 0, 0]), (2.9, [-50, 0, 0]), (3.8, [-50, 0, 0]), (4.3, [0, 0, 0])])
    a.rot('hair', [(0.4, [0, 0, 0]), (0.8, [30, 0, 20]), (2.2, [30, 0, 20]), (2.6, [0, 0, 0]), (2.9, [40, 0, 0]), (3.8, [44, 0, 0]),
                   (4.3, [0, 0, 0])])
    a.sound(0.6, 'whoosh').sound(1.3, 'whoosh').sound(2.0, 'whoosh')
    return a


def mourn():
    a = S('mourn', 4.0, True)
    a.rot('torso', [(0, [22, 0, 0]), (2.0, [26, 0, 2]), (4.0, [22, 0, 0])])
    a.rot('arm_r', [(0, [8, 0, 4]), (4.0, [8, 0, 4])])
    a.rot('arm_l', [(0, [-60, 30, -10]), (2.0, [-64, 30, -10]), (4.0, [-60, 30, -10])])
    return a


ANIMS = [salsa_a, salsa_b, salsa_c, rise, twirled, mourn]


def write_anims():
    data = {'format_version': '1.8.0', 'animations': {}}
    for fn in ANIMS:
        a = fn()
        data['animations']['animation.salsa_dancer.' + a.name] = guard_brows(a.compile())
    with open(OUT_ANIM, 'w', newline='\n') as f:
        json.dump(data, f, indent='\t')
    return OUT_ANIM


if __name__ == '__main__':
    print(build())
    print(write_anims())
