#!/usr/bin/env python3
"""Generates the four internet-famous customers who queue at Papon's Taco Truck.

Writes, under src/main/resources/assets/papon/:
  geo/entity/<name>.geo.json and textures/entity/<name>.png
for don_pollo, edp445, badlands_chugs and reviewbrah.

All four share one humanoid rig, so they share one animation file (gen_npc_anims.py):

  root
   +- leg_r, leg_l                 pivots at the hips
   +- torso                        pivot at the hips; chest, belly, cape
       +- head                     pivot at the neck; face, hair, hats
       +- arm_r, arm_l             pivots at the shoulders
           +- <props>              held items, pivots at the hand

Right is -X, front is -Z, exactly like the Wandering Papon's rig, so the same rotation
conventions hold (rot X < 0 swings a limb forward; arm_r Z > 0 raises it outward).

They are caricatures built from public photos, readable at a glance in game:
  Don Pollo       bald, short black boxed beard, huge grin, yellow shirt and gold chain,
                  the "King of Ohio" crown and red cape; rotisserie chicken and his phone
  EDP445          round face, wide eyes, toothy grin, midnight-green Eagles-style jersey
                  numbered 445 and a green beanie with a silver stripe
  Badlands Chugs  the biggest of them; bald with a shine, goatee, white tee with red trim,
                  and a gallon jug to chug
  Reviewbrah      thin and pale, tall slicked-back hair, vintage charcoal suit and red tie,
                  and his glass of blue energy drink

Props are hidden by animation scale, like the Papon's: PROPS lists them for gen_npc_anims.py.
"""
import os

from geo_util import ASSETS, Packer, Painter, bone, cube, faces, write_geo

TEX = 128
PROPS = ['taco', 'goblet', 'jug', 'drumstick', 'phone']

SIDE = {'right': 'east', 'front': 'north', 'left': 'west', 'back': 'south', 'top': 'up', 'bottom': 'down'}


class Model:
    def __init__(self, name, seed):
        self.name = name
        self.bones = []
        self.packer = Packer(TEX, TEX)
        self.p = Painter(TEX, TEX, seed)

    def box(self, origin, size, color, grids=None, noise=0.05, inflate=0, extra=None):
        """A cube filled with `color`, then any per-face grids {side: (rows, palette)} stamped on."""
        w, h, d = size
        uv = self.packer.take(w, h, d)
        f = faces(uv[0], uv[1], w, h, d)
        for r in f.values():
            self.p.fill(r, color, noise)
        for side, (rows, pal) in (grids or {}).items():
            self.p.grid(f[SIDE[side]], rows, pal)
        if extra:
            extra(self.p, {k: f[v] for k, v in SIDE.items()})
        return cube(origin, size, uv, inflate)

    def add(self, *bones):
        self.bones.extend(bones)

    def save(self):
        geo = write_geo(self.name, self.bones, TEX, TEX, bounds=(3, 3.5, 1.5))
        tex = self.p.save(os.path.join(ASSETS, 'textures/entity/%s.png' % self.name))
        return geo, tex


def band(p, rect, row, color, noise=0.04):
    """Paint one texture row across a face rect."""
    x0, y0, x1, _ = (int(n) for n in rect)
    p.fill((x0, y0 + row, x1, y0 + row + 1), color, noise)


def rows_of(rect, r0, r1, p, color, noise=0.04):
    x0, y0, x1, _ = (int(n) for n in rect)
    p.fill((x0, y0 + r0, x1, y0 + r1), color, noise)


class Rig:
    """Positions for a humanoid with the given part sizes (w, h, d), in pixels."""

    def __init__(self, leg, torso, arm, head):
        self.leg, self.torso, self.arm, self.head = leg, torso, arm, head
        lw, lh, ld = leg
        tw, th, td = torso
        aw, ah, ad = arm
        self.hip = lh
        self.neck = lh + th
        self.leg_x = max(lw / 2, tw / 4)
        self.arm_x = tw / 2 + aw / 2
        self.shoulder = self.neck - 2
        self.hand = self.neck - ah + 1.5

    def legs(self, m, paint):
        lw, lh, ld = self.leg
        out = []
        for name, s in (('leg_r', -1), ('leg_l', 1)):
            cx = s * self.leg_x
            out.append(bone(name, [cx, lh, 0], 'root',
                            [paint(m, [cx - lw / 2, 0, -ld / 2], self.leg)]))
        return out

    def chest(self, m, color, grids=None, extra=None):
        tw, th, td = self.torso
        return m.box([-tw / 2, self.hip, -td / 2], self.torso, color, grids, extra=extra)

    def head_cube(self, m, color, grids=None, extra=None):
        hw, hh, hd = self.head
        return m.box([-hw / 2, self.neck, -hd / 2], self.head, color, grids, extra=extra)

    def arm_origin(self, side):
        aw, ah, ad = self.arm
        x = -self.torso[0] / 2 - aw if side < 0 else self.torso[0] / 2
        return [x, self.neck - ah, -ad / 2]

    def arms(self, m, paint):
        out = []
        for name, s in (('arm_r', -1), ('arm_l', 1)):
            out.append(bone(name, [s * self.arm_x, self.shoulder, 0], 'torso',
                            [paint(m, self.arm_origin(s), self.arm, s)]))
        return out

    def hand_pivot(self, side):
        return [side * self.arm_x, self.hand, 0]


# ---------------------------------------------------------------- shared props

def taco(m, rig, side=-1):
    """Taco held in the hand, shell up, sticking forward out of the fist."""
    hx, hy, _ = rig.hand_pivot(side)
    ad = rig.arm[2]

    def fillings(p, f):
        top = f['top']
        x0, y0, x1, y1 = (int(n) for n in top)
        for x in range(x0, x1):
            for y in range(y0, y1):
                p.put(x, y, (92, 176, 60) if (x + y) % 2 else (214, 54, 44), 0.05)
        for s in ('front', 'back'):
            r = f[s]
            for x in range(int(r[0]), int(r[2])):
                p.put(x, int(r[1]), (92, 176, 60) if x % 2 else (214, 54, 44))
    shell = m.box([hx - 1, hy - 3, -ad / 2 - 4], [2, 3, 5], (238, 196, 92), extra=fillings, noise=0.08)
    return bone('taco', [hx, hy, 0], 'arm_r' if side < 0 else 'arm_l', [shell])


def phone(m, rig, side=-1):
    hx, hy, _ = rig.hand_pivot(side)
    ad = rig.arm[2]

    def screen(p, f):
        r = f['front']
        p.fill((r[0], r[1], r[2], r[3]), (70, 140, 220), 0.1)
        p.put(int(r[0]), int(r[1]), (30, 30, 34))
    body = m.box([hx - 1, hy - 4, -ad / 2 - 1], [2, 4, 1], (28, 28, 32), extra=screen)
    return bone('phone', [hx, hy, 0], 'arm_r' if side < 0 else 'arm_l', [body])


# ---------------------------------------------------------------- Don Pollo

def don_pollo():
    m = Model('don_pollo', 11)
    rig = Rig(leg=(5, 10, 5), torso=(10, 11, 6), arm=(4, 11, 5), head=(10, 10, 10))
    skin, skin_dk = (124, 80, 52), (100, 62, 40)
    beard, teeth = (34, 26, 24), (240, 236, 226)
    yellow, yellow_dk = (238, 198, 48), (204, 162, 34)
    gold, gold_dk, ruby = (236, 186, 52), (178, 128, 30), (196, 26, 40)
    jeans, shoe = (58, 78, 120), (232, 232, 232)

    def leg(m_, o, size):
        def paint(p, f):
            for s in ('front', 'back', 'left', 'right'):
                rows_of(f[s], 8, 10, p, shoe)
        return m_.box(o, size, jeans, extra=paint)

    m.add(bone('root', [0, 0, 0]))
    m.add(*rig.legs(m, leg))

    shirt = {'front': ([
        'YYYDDDDYYY',
        'YYgDDDDgYY',
        'YYYgDDgYYY',
        'YYYYggYYYY',
        'YYYYGGYYYY',
        'YYYYwYYYYY',
        'YYYYYYYYYY',
        'YYYYwYYYYY',
        'YYYYYYYYYY',
        'YYYYwYYYYY',
        'yyyyyyyyyy',
    ], {'Y': yellow, 'y': yellow_dk, 'D': skin, 'g': gold, 'G': ruby, 'w': (250, 244, 214)})}
    chest = rig.chest(m, yellow, shirt)
    belly = m.box([-4, rig.hip + 1, -rig.torso[2] / 2 - 1.5], [8, 5, 2], yellow,
                  {'front': (['YYYYwYYY', 'YYYYYYYY', 'YYYYwYYY', 'YYYYYYYY', 'yyyyyyyy'],
                             {'Y': yellow, 'y': yellow_dk, 'w': (250, 244, 214)})})
    m.add(bone('torso', [0, rig.hip, 0], 'root', [chest, belly]))

    # The King of Ohio's cape, hanging from the shoulders so it can sway.
    def cape_paint(p, f):
        for s in ('back', 'front'):
            band(p, f[s], 0, (244, 244, 238))
            band(p, f[s], 1, (244, 244, 238))
            x0, y0, x1, y1 = (int(n) for n in f[s])
            for x in range(x0, x1, 3):
                p.put(x + 1, y0 + 1, (24, 24, 24))
            for y in range(y0 + 2, y1):
                p.put(x0, y, gold)
                p.put(x1 - 1, y, gold)
            band(p, f[s], y1 - y0 - 1, gold)
    cape = m.box([-6, rig.neck - 14, rig.torso[2] / 2], [12, 14, 1], (176, 26, 34), extra=cape_paint, noise=0.07)
    m.add(bone('cape', [0, rig.neck, rig.torso[2] / 2], 'torso', [cape]))

    face = {
        'front': ([
            'SSSSSSSSSS',
            'SSSSSSSSSS',
            'SbbbSSbbbS',
            'SEPSSSSPES',
            'SSSSssSSSS',
            'BSSSNNSSSB',
            'BBBBBBBBBB',
            'BBWWWWWWBB',
            'BBBWWWWBBB',
            'BBBBBBBBBB',
        ], {'S': skin, 's': skin_dk, 'b': beard, 'E': (236, 232, 224), 'P': (28, 20, 18),
            'N': skin_dk, 'B': beard, 'W': teeth}),
        'right': ([
            'SSSSSSSSSS', 'SSSSSSSSSS', 'SSSSSSSSSS', 'SSSSSSSSSS', 'SSSSkSSSSS',
            'SSSSkkSSSB', 'SSSSSSSSBB', 'SSSSSSSBBB', 'SSSSSSBBBB', 'SSSSSBBBBB',
        ], {'S': skin, 'k': skin_dk, 'B': beard}),
        'left': ([
            'SSSSSSSSSS', 'SSSSSSSSSS', 'SSSSSSSSSS', 'SSSSSSSSSS', 'SSSSSkSSSS',
            'BSSSkkSSSS', 'BBSSSSSSSS', 'BBBSSSSSSS', 'BBBBSSSSSS', 'BBBBBSSSSS',
        ], {'S': skin, 'k': skin_dk, 'B': beard}),
        'bottom': (['BBBBBBBBBB'] * 4 + ['SSSSSSSSSS'] * 6, {'B': beard, 'S': skin_dk}),
        'top': ([
            'SSSSSSSSSS', 'SSShhhSSSS', 'SShhhhhSSS', 'SSShhhSSSS', 'SSSSSSSSSS',
            'SSSSSSSSSS', 'SSSSSSSSSS', 'SSSSSSSSSS', 'SSSSSSSSSS', 'SSSSSSSSSS',
        ], {'S': skin, 'h': (150, 102, 70)}),
    }
    head = rig.head_cube(m, skin, face)

    def crown_paint(p, f):
        for s in ('front', 'back', 'left', 'right'):
            band(p, f[s], 1, gold_dk)
            r = f[s]
            for x in range(int(r[0]) + 1, int(r[2]) - 1, 3):
                p.put(x, int(r[1]), ruby if x % 2 else (40, 90, 200))
    top = rig.neck + rig.head[1]
    crown = [m.box([-5.5, top - 1, -5.5], [11, 2, 11], gold, extra=crown_paint, noise=0.06)]
    for x, z in ((-5.5, -5.5), (-0.5, -5.5), (4.5, -5.5), (-5.5, 4.5), (-0.5, 4.5), (4.5, 4.5),
                 (-5.5, -0.5), (4.5, -0.5)):
        crown.append(m.box([x, top + 1, z], [1, 2, 1], gold, noise=0.08))
    m.add(bone('head', [0, rig.neck, 0], 'torso', [head]),
          bone('crown', [0, top, 0], 'head', crown))

    def arm(m_, o, size, s):
        def paint(p, f):
            for side in ('front', 'back', 'left', 'right', 'top'):
                rows_of(f[side], 0, 4, p, yellow) if side != 'top' else p.fill(f[side], yellow)
                if side != 'top':
                    band(p, f[side], 3, yellow_dk)
        return m_.box(o, size, skin, extra=paint)
    m.add(*rig.arms(m, arm))

    # rotisserie chicken drumstick in the left hand, phone and taco in the right
    hx, hy, _ = rig.hand_pivot(1)
    meat = m.box([hx - 1.5, hy - 3, -rig.arm[2] / 2 - 4], [3, 3, 4], (176, 102, 44), noise=0.12)
    bone_end = m.box([hx - 0.5, hy - 2, -rig.arm[2] / 2 - 6], [1, 1, 2], (240, 232, 214))
    m.add(bone('drumstick', [hx, hy, 0], 'arm_l', [meat, bone_end]))
    m.add(taco(m, rig), phone(m, rig))
    return m.save()


# ---------------------------------------------------------------- EDP445

DIGITS = {
    '4': ['W.W', 'W.W', 'WWW', '..W', '..W'],
    '5': ['WWW', 'W..', 'WWW', '..W', 'WWW'],
    'E': ['WWW', 'W..', 'WW.', 'W..', 'WWW'],
    'D': ['WW.', 'W.W', 'W.W', 'W.W', 'WW.'],
    'P': ['WWW', 'W.W', 'WWW', 'W..', 'W..'],
}


def text_rows(text, width, fill, ink='W'):
    """Five rows of 3x5 glyphs, one space between, right-padded to width with `fill`."""
    rows = []
    for r in range(5):
        line = fill.join(DIGITS[c][r].replace('.', fill).replace('W', ink) for c in text)
        pad = width - len(line)
        rows.append(fill * (pad - pad // 2) + line + fill * (pad // 2))
    return rows


def edp445():
    m = Model('edp445', 445)
    rig = Rig(leg=(5, 11, 5), torso=(12, 12, 6), arm=(4, 12, 5), head=(10, 10, 10))
    skin, skin_dk = (74, 46, 32), (58, 36, 24)
    green, green_dk = (0, 78, 86), (0, 56, 62)
    white, black, silver = (244, 244, 244), (22, 22, 24), (168, 174, 178)
    pants = (64, 66, 72)

    def leg(m_, o, size):
        def paint(p, f):
            for s in ('front', 'back', 'left', 'right'):
                rows_of(f[s], 9, 11, p, white)
                band(p, f[s], 10, (40, 120, 90))
        return m_.box(o, size, pants, extra=paint)

    m.add(bone('root', [0, 0, 0]))
    m.add(*rig.legs(m, leg))

    pal = {'G': green, 'g': green_dk, 'W': white, 'K': black, 'D': skin, 'S': silver}
    front = (['GGGGWDDWGGGG', 'GGGGGWWGGGGG']
             + text_rows('445', 12, 'G')
             + ['GGGGGGGGGGGG'] * 4 + ['gggggggggggg'])
    back = (['GGGGGGGGGGGG'] + text_rows('EDP', 12, 'G', 'S')
            + ['GGGGGGGGGGGG'] + text_rows('445', 12, 'G')[:4] + ['gggggggggggg'])
    # back holds the name above the number; the number's last row sits on the hem
    back = back[:11] + [text_rows('445', 12, 'g')[4]]
    chest = rig.chest(m, green, {'front': (front, pal), 'back': (back, pal)})
    belly = m.box([-5, rig.hip + 1, -rig.torso[2] / 2 - 2], [10, 5, 2], green,
                  {'front': (['GGGGGGGGGG', 'GGGGGGGGGG', 'GGGGGGGGGG', 'GGGGGGGGGG', 'gggggggggg'], pal)})
    m.add(bone('torso', [0, rig.hip, 0], 'root', [chest, belly]))

    face = {'front': ([
        'DDDDDDDDDD',
        'DDDDDDDDDD',
        'DDDDDDDDDD',
        'DBBDDDDBBD',
        'DEEEDDEEED',
        'DEPEDDEPED',
        'DDDDNNDDDD',
        'DMWWWWWWMD',
        'DDMWWWWMDD',
        'DDDMMMMDDD',
    ], {'D': skin, 'B': (20, 14, 12), 'E': (246, 244, 240), 'P': (18, 12, 10), 'N': skin_dk,
        'M': (90, 36, 34), 'W': (246, 244, 236)}),
        'right': (['DDDDDDDDDD'] * 4 + ['DDDDkDDDDD', 'DDDDkkDDDD'] + ['DDDDDDDDDD'] * 4, {'D': skin, 'k': skin_dk}),
        'left': (['DDDDDDDDDD'] * 4 + ['DDDDDkDDDD', 'DDDDkkDDDD'] + ['DDDDDDDDDD'] * 4, {'D': skin, 'k': skin_dk}),
    }
    head = rig.head_cube(m, skin, face)

    def beanie_paint(p, f):
        for s in ('front', 'back', 'left', 'right'):
            band(p, f[s], 0, green)
            band(p, f[s], 1, silver)
            band(p, f[s], 2, green_dk)
            band(p, f[s], 3, green_dk)
    top = rig.neck + rig.head[1]
    beanie = m.box([-5, top - 3, -5], [10, 4, 10], green, extra=beanie_paint, inflate=0.5)
    pom = m.box([-1, top + 1.5, -1], [2, 2, 2], silver, noise=0.15)
    m.add(bone('head', [0, rig.neck, 0], 'torso', [head]),
          bone('beanie', [0, top, 0], 'head', [beanie, pom]))

    def arm(m_, o, size, s):
        def paint(p, f):
            for side in ('front', 'back', 'left', 'right'):
                rows_of(f[side], 0, 4, p, green)
                band(p, f[side], 1, white)
                band(p, f[side], 2, black)
            p.fill(f['top'], green)
        return m_.box(o, size, skin, extra=paint)
    m.add(*rig.arms(m, arm))
    m.add(taco(m, rig), phone(m, rig, 1))
    return m.save()


# ---------------------------------------------------------------- Badlands Chugs

def badlands_chugs():
    m = Model('badlands_chugs', 1969)
    rig = Rig(leg=(6, 12, 6), torso=(14, 13, 8), arm=(5, 13, 6), head=(10, 10, 10))
    skin, skin_dk, shine = (66, 40, 28), (50, 30, 20), (110, 76, 58)
    tee, tee_dk, red = (238, 238, 234), (208, 208, 204), (196, 36, 40)
    jeans, shoe = (40, 46, 66), (24, 24, 26)

    def leg(m_, o, size):
        def paint(p, f):
            for s in ('front', 'back', 'left', 'right'):
                rows_of(f[s], 10, 12, p, shoe)
        return m_.box(o, size, jeans, extra=paint)

    m.add(bone('root', [0, 0, 0]))
    m.add(*rig.legs(m, leg))
    pal = {'T': tee, 't': tee_dk, 'R': red, 'D': skin}
    front = (['TTTTTRDDRTTTTT', 'TTTTTTRRTTTTTT', 'TTTTTTTTTTTTTT', 'RRRRRRRRRRRRRR', 'RRRRRRRRRRRRRR']
             + ['TTTTTTTTTTTTTT'] * 7 + ['tttttttttttttt'])
    back = ['TTTTTRRRRTTTTT'] + ['TTTTTTTTTTTTTT'] * 2 + ['RRRRRRRRRRRRRR'] * 2 + ['TTTTTTTTTTTTTT'] * 7 + ['tttttttttttttt']
    side = ['TTTTTTTT'] * 3 + ['RRRRRRRR'] * 2 + ['TTTTTTTT'] * 7 + ['tttttttt']
    chest = rig.chest(m, tee, {'front': (front, pal), 'back': (back, pal), 'left': (side, pal), 'right': (side, pal)})
    belly = m.box([-6, rig.hip, -rig.torso[2] / 2 - 3], [12, 7, 3], tee,
                  {'front': (['TTTTTTTTTTTT'] * 6 + ['tttttttttttt'], pal)})
    m.add(bone('torso', [0, rig.hip, 0], 'root', [chest, belly]))

    face = {'front': ([
        'SSShhhhSSS',
        'SSSShhSSSS',
        'SbbbSSbbbS',
        'SEPSSSSPES',
        'SSSSSSSSSS',
        'SSSSNNSSSS',
        'SSBBBBBBSS',
        'SSBWWWWBSS',
        'SSSBBBBSSS',
        'SSSSBBSSSS',
    ], {'S': skin, 'h': shine, 'b': (16, 12, 10), 'E': (236, 230, 222), 'P': (16, 10, 8), 'N': skin_dk,
        'B': (18, 14, 12), 'W': (244, 240, 230)}),
        'top': (['SSSSSSSSSS', 'SSSSSSSSSS', 'SSSShhhSSS', 'SSShhhhhSS', 'SSShhhhhSS', 'SSSShhhSSS',
                 'SSSSShSSSS', 'SSSSSSSSSS', 'SSSShhSSSS', 'SSShhhhSSS'],
                {'S': skin, 'h': shine}),
        'right': (['SSSSSSSSSS'] * 4 + ['SSSSkSSSSS', 'SSSSkkSSSS'] + ['SSSSSSSSSS'] * 4, {'S': skin, 'k': skin_dk}),
        'left': (['SSSSSSSSSS'] * 4 + ['SSSSSkSSSS', 'SSSSkkSSSS'] + ['SSSSSSSSSS'] * 4, {'S': skin, 'k': skin_dk}),
    }
    m.add(bone('head', [0, rig.neck, 0], 'torso', [rig.head_cube(m, skin, face)]))

    def arm(m_, o, size, s):
        def paint(p, f):
            for side in ('front', 'back', 'left', 'right'):
                rows_of(f[side], 0, 4, p, tee)
                band(p, f[side], 3, red)
            p.fill(f['top'], tee)
        return m_.box(o, size, skin, extra=paint)
    m.add(*rig.arms(m, arm))

    # the gallon jug, left hand: lemonade-yellow, white cap, a handle on the outside
    hx, hy, _ = rig.hand_pivot(1)
    ad = rig.arm[2]

    def jug_paint(p, f):
        for s in ('front', 'back', 'left', 'right'):
            rows_of(f[s], 0, 2, p, (236, 236, 226))
            band(p, f[s], 4, (250, 250, 250), 0.02)
    body = m.box([hx - 2.5, hy - 6, -ad / 2 - 5], [5, 7, 5], (246, 226, 112), extra=jug_paint, noise=0.04)
    cap = m.box([hx - 1, hy + 1, -ad / 2 - 3.5], [2, 1, 2], (240, 240, 240))
    handle = m.box([hx + 2.5, hy - 4, -ad / 2 - 3], [1, 4, 2], (236, 236, 226))
    m.add(bone('jug', [hx, hy, 0], 'arm_l', [body, cap, handle]))
    m.add(taco(m, rig))
    return m.save()


# ---------------------------------------------------------------- Reviewbrah

def reviewbrah():
    m = Model('reviewbrah', 2007)
    rig = Rig(leg=(4, 12, 4), torso=(8, 11, 4), arm=(3, 11, 4), head=(8, 9, 8))
    skin, skin_dk = (238, 204, 180), (214, 176, 152)
    suit, suit_lt, shirt = (56, 58, 66), (72, 74, 84), (246, 246, 242)
    tie, tie_dk = (160, 30, 40), (112, 20, 28)
    hair, hair_lt = (56, 38, 28), (78, 56, 40)
    shoe = (22, 20, 20)

    def leg(m_, o, size):
        def paint(p, f):
            for s in ('front', 'back', 'left', 'right'):
                rows_of(f[s], 10, 12, p, shoe)
            band(p, f['front'], 10, (44, 40, 40))
        return m_.box(o, size, suit, extra=paint)

    m.add(bone('root', [0, 0, 0]))
    m.add(*rig.legs(m, leg))
    pal = {'J': suit, 'L': suit_lt, 'W': shirt, 'T': tie, 't': tie_dk, 'b': (30, 30, 34)}
    front = [
        'JWWTTWWJ',
        'JLWTTWLJ',
        'JLWttWLJ',
        'JJLTTLJJ',
        'JJLttLJJ',
        'JJJTTJJJ',
        'JJJttJJJ',
        'JJJJJJJJ',
        'JJJbJJJJ',
        'JJJJJJJJ',
        'JJJbJJJJ',
    ]
    chest = rig.chest(m, suit, {'front': (front, pal), 'back': (['JWWWWWWJ'] + ['JJJJJJJJ'] * 10, pal)})
    m.add(bone('torso', [0, rig.hip, 0], 'root', [chest]))

    face = {'front': ([
        'HHHHHHHH',
        'SSSSSSSS',
        'SbbSSbbS',
        'SEPSSPES',
        'SSSSSSSS',
        'SSSNNSSS',
        'SSSSSSSS',
        'SSMMMMSS',
        'SSSSSSSS',
    ], {'H': hair, 'S': skin, 'b': (60, 42, 32), 'E': (240, 240, 240), 'P': (84, 110, 130),
        'N': skin_dk, 'M': (184, 112, 106)}),
        'right': (['HHHHHHHH', 'HHHHHSSS', 'HHHHSSSS', 'HHHSSSSS', 'HHSSkSSS', 'HSSSkkSS', 'SSSSSSSS',
                   'SSSSSSSS', 'SSSSSSSS'], {'H': hair, 'S': skin, 'k': skin_dk}),
        'left': (['HHHHHHHH', 'SSSHHHHH', 'SSSSHHHH', 'SSSSSHHH', 'SSSkSSHH', 'SSkkSSSH', 'SSSSSSSS',
                  'SSSSSSSS', 'SSSSSSSS'], {'H': hair, 'S': skin, 'k': skin_dk}),
        'back': (['HHHHHHHH'] * 6 + ['SSSSSSSS'] * 3, {'H': hair, 'S': skin}),
        'top': (['HHHHHHHH'] * 8, {'H': hair}),
    }
    head = rig.head_cube(m, skin, face)

    # slicked back: a cap of hair combed in rows, rising into a pompadour at the front
    def comb(p, f):
        # streaks run front to back: straight columns on top, straight rows on the sides
        for s, r in f.items():
            x0, y0, x1, y1 = (int(n) for n in r)
            for y in range(y0, y1):
                for x in range(x0, x1):
                    k = x if s in ('top', 'bottom') else (y if s in ('left', 'right') else -1)
                    if k >= 0 and k % 3 == 1:
                        p.put(x, y, hair_lt, 0.03)
    top = rig.neck + rig.head[1]
    cap = m.box([-4, top, -4], [8, 1, 8], hair, extra=comb, noise=0.03, inflate=0.25)
    quiff = m.box([-4, top + 1, -4.5], [8, 2, 4], hair, extra=comb, noise=0.03, inflate=0.25)
    crest = m.box([-3, top + 3, -4.5], [6, 1, 2], hair, extra=comb, noise=0.03, inflate=0.25)
    m.add(bone('head', [0, rig.neck, 0], 'torso', [head]),
          bone('hair', [0, top, 0], 'head', [cap, quiff, crest]))

    def arm(m_, o, size, s):
        def paint(p, f):
            for side in ('front', 'back', 'left', 'right'):
                band(p, f[side], 8, shirt)
                rows_of(f[side], 9, 11, p, skin)
            p.fill(f['bottom'], skin)
        return m_.box(o, size, suit, extra=paint)
    m.add(*rig.arms(m, arm))

    # the glass of blue energy drink, left hand
    hx, hy, _ = rig.hand_pivot(1)
    ad = rig.arm[2]
    base = m.box([hx - 1.5, hy - 2, -ad / 2 - 3.5], [3, 1, 3], (220, 228, 236))
    stem = m.box([hx - 0.5, hy - 1, -ad / 2 - 2.5], [1, 2, 1], (220, 228, 236))
    cup = m.box([hx - 1.5, hy + 1, -ad / 2 - 3.5], [3, 3, 3], (52, 128, 250),
                extra=lambda p, f: band(p, f['front'], 0, (200, 230, 255)), noise=0.08)
    m.add(bone('goblet', [hx, hy, 0], 'arm_l', [base, stem, cup]))
    m.add(taco(m, rig))
    return m.save()


CUSTOMERS = [don_pollo, edp445, badlands_chugs, reviewbrah]

if __name__ == '__main__':
    for fn in CUSTOMERS:
        for path in fn():
            print(path)
