#!/usr/bin/env python3
"""Ouros and Boros, Papon's feathered serpents (phase four: he shoots the moon with his bow and they come down out of the
black smoke). Dungeon Now Loading's Fairkeeper serpents (the user's pack: their models are used as they are, their
skins repainted) turned into Aztec serpent gods, after the user's picture of Quetzalcoatl:

  scales    green scales over the back and sides, a yellow band down each side, yellow belly plates
  head      Boros keeps his carved helmet, now beaten gold with jade inlay and red eyes (the gold mask of the picture);
            Ouros loses his stone horns for a crown of feathers; both get a ring of red, orange and gold feathers fanned
            round the back of the head, the mouth pink, white fangs, a red forked tongue
  body      a segment (green and gold), a collar segment (a ring of feathers at its front) and the tail (a fan of feathers)

Geometry: read from the decompiled models (tools/decompile_refs.sh: /tmp/papon_refs/dnl/src/client/model), vanilla
LayerDefinition parts, kept as they are (no conversion: client/SerpentModel builds the same ModelParts from the JSON),
the machinery (cannons, trap doors, dispensers) left out and our feathers added. Writes:
  assets/papon/serpent/<kind>_<part>.json          parts: {name: {parent, pose [x y z rx ry rz], cubes [{uv, box, mirror, grow}]}}
  assets/papon/textures/entity/serpent_<kind>_<part>.png   512 x 512 (+ _glow: the eyes)

  tools/decompile_refs.sh && python3.11 tools/gen_serpents.py
"""
import json
import math
import os
import re
import zipfile

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/papon')
SRC = os.environ.get('PAPON_DNL_SRC', '/tmp/papon_refs/dnl/src/client/model')
MODS = os.environ.get('PAPON_MODS', os.path.expanduser('~/Documents/curseforge/minecraft/Instances/Groid Pack OG/mods'))
JAR = os.path.join(MODS, 'Dungeon Now Loading-forge-1.20.1-2.2.jar')
TEX = 512
TEX_H = 768       # taller than the originals: room under them for the feathers

SCALE_DK = np.array([18, 74, 36])
SCALE = np.array([46, 142, 50])
SCALE_HI = np.array([132, 206, 64])
BELLY = np.array([240, 200, 48])
BELLY_DK = np.array([176, 128, 26])
GOLD_DK = np.array([132, 82, 18])
GOLD = np.array([226, 172, 44])
GOLD_HI = np.array([255, 234, 128])
JADE = np.array([28, 168, 132])
JADE_HI = np.array([112, 232, 190])
RED = np.array([206, 32, 28])
ORANGE = np.array([248, 120, 24])
YELLOW = np.array([255, 206, 52])
MOUTH = np.array([156, 44, 66])
MOUTH_DK = np.array([96, 22, 40])
TONGUE = np.array([214, 38, 52])
TEETH = np.array([242, 236, 216])


# ---------------------------------------------------------------------------------------------------- parsing the models

def parse(java):
    """The LayerDefinition in a decompiled model: {name: {parent, pose, cubes}}, and the texture size."""
    src = open(java, encoding='utf-8').read()
    body = src[src.index('createBodyLayer()'):]
    body = body[:body.index('return LayerDefinition')]
    parts = {}
    names = {'partdefinition': None}
    for m in re.finditer(r'PartDefinition (\w+) = (\w+)\.m_171599_\(\s*"(\w+)",(.*?)\);\s*\n', body, re.S):
        var, parent_var, name, rest = m.groups()
        pose_at = rest.rindex('PartPose.')
        chain, pose = rest[:pose_at], rest[pose_at:]
        nums = [float(x.rstrip('F')) for x in re.findall(r'-?\d+\.?\d*F?', pose.split('(', 1)[1])]
        if 'm_171423_' in pose:
            p = nums[:6]
        else:
            p = nums[:3] + [0, 0, 0]
        cubes, uv, mirror = [], [0, 0], False
        for call in re.finditer(r'\.(m_\d+_)\(([^()]*(?:\([^()]*\))?[^()]*)\)', chain):
            fn, args = call.groups()
            if fn == 'm_171514_':
                uv = [int(float(a.strip().rstrip('F'))) for a in args.split(',')]
            elif fn == 'm_171480_':
                mirror = True
            elif fn == 'm_171555_':
                mirror = 'true' in args
            elif fn == 'm_171488_':
                vals = [float(x.rstrip('F')) for x in re.findall(r'-?\d+\.?\d*F?', args)]
                cubes.append({'uv': list(uv), 'box': vals[:6], 'mirror': mirror, 'grow': vals[6] if len(vals) > 6 else 0.0})
        parent = names.get(parent_var)
        names[var] = name
        parts[name] = {'parent': parent, 'pose': [round(v, 4) for v in p], 'cubes': cubes}
    size = re.search(r'm_171565_\(meshdefinition, (\d+), (\d+)\)', src)
    return parts, (int(size.group(1)), int(size.group(2)))


def drop(parts, *names):
    """Leaves out these parts and everything under them."""
    gone = set(names)
    changed = True
    while changed:
        changed = False
        for n, p in parts.items():
            if n not in gone and p['parent'] in gone:
                gone.add(n)
                changed = True
    return {n: p for n, p in parts.items() if n not in gone}


# ---------------------------------------------------------------------------------------------------- face rectangles

def faces(c):
    """A cube's six faces on its texture (box UV, as ModelPart.Cube lays them out): name -> (u, v, w, h). 'top' is the face
    drawn on top in the world (the model's -Y), 'front' faces -Z (the way the serpent goes)."""
    u, v = c['uv']
    w, h, d = c['box'][3:6]
    w, h, d = int(math.floor(w)), int(math.floor(h)), int(math.floor(d))
    return {
        'top': (u + d, v, w, d),
        'bottom': (u + d + w, v, w, d),
        'side_a': (u, v + d, d, h),
        'front': (u + d, v + d, w, h),
        'side_b': (u + d + w, v + d, d, h),
        'back': (u + d + w + d, v + d, w, h),
    }


# ---------------------------------------------------------------------------------------------------- painting

class Canvas:
    def __init__(self, src_png):
        self.img = np.zeros((TEX_H, TEX, 4), np.uint8)
        self.glow = np.zeros((TEX_H, TEX, 4), np.uint8)
        src = np.asarray(Image.open(src_png).convert('RGBA'), dtype=np.float32) if src_png else None
        self.lum = np.full((TEX_H, TEX), 0.5, np.float32)
        self.red = np.zeros((TEX_H, TEX), bool)
        if src is not None:
            h, w = src.shape[:2]
            lum = (0.3 * src[..., 0] + 0.59 * src[..., 1] + 0.11 * src[..., 2]) / 255.0
            self.lum[:h, :w] = lum
            # the stone's glowing red bits (eyes, vents): eyes stay eyes
            self.red[:h, :w] = (src[..., 0] > 120) & (src[..., 0] > src[..., 1] * 2.2) & (src[..., 3] > 0)
        self.rng = np.random.default_rng(7)

    def rect(self, r, fn):
        u, v, w, h = r
        for y in range(max(0, v), min(TEX_H, v + h)):
            for x in range(max(0, u), min(TEX, u + w)):
                col = fn(x - u, y - v, w, h, x, y)
                if col is None:
                    continue
                a = 255
                if len(col) == 4:
                    a = col[3]
                    col = col[:3]
                self.img[y, x, :3] = np.clip(col, 0, 255)
                self.img[y, x, 3] = a

    def detail(self, x, y, k=0.35):
        """The original carving, as light and shade: around 1 where it was mid grey."""
        return 1.0 + (self.lum[y, x] - 0.5) * k


def lerp(a, b, t):
    return a + (b - a) * t


def scales(fx, fy, cv, gx, gy, belly_rows=0, h=1):
    """Green scales: rows of little arches, each lit at its top, its rim dark."""
    row = fy // 3
    sx = (fx + (row % 2) * 2) % 4
    sy = fy % 3
    base = SCALE
    if sy == 0 and sx in (1, 2):
        base = SCALE_HI
    elif sy == 2 or sx == 0:
        base = SCALE_DK
    if belly_rows and fy >= h - belly_rows:
        # the yellow band low on each side
        base = BELLY if (fy - (h - belly_rows)) % 3 else BELLY_DK
    return base * cv.detail(gx, gy, 0.25)


def belly(fx, fy, cv, gx, gy):
    """Belly plates: wide yellow bands, a dark seam between."""
    return (BELLY_DK if fy % 3 == 2 else BELLY) * cv.detail(gx, gy, 0.2)


def gold(cv, gx, gy, k=1.0):
    """Beaten gold, by the stone's own light and shade (its carving reads as chasing)."""
    l = cv.lum[gy, gx]
    if l < 0.35:
        c = lerp(GOLD_DK, GOLD, l / 0.35)
    else:
        c = lerp(GOLD, GOLD_HI, min(1.0, (l - 0.35) / 0.45))
    return c * k


def greca(fx, fy, w, h):
    """An Aztec step-fret band (xicalcoliuhqui) along a face: True on the pattern's lines."""
    p = fx % 8
    q = fy % 8
    return (q == 0) or (p == 0 and q < 6) or (q == 5 and p < 5) or (p == 4 and 2 <= q <= 5) or (q == 2 and 2 <= p <= 4)


def paint_scaled(cv, c, role):
    """A body or jaw box: top and sides scaled, a yellow band low on the sides, belly plates under."""
    f = faces(c)
    for name, r in f.items():
        if name == 'bottom':
            cv.rect(r, lambda x, y, w, h, gx, gy: belly(x, y, cv, gx, gy))
        elif name in ('side_a', 'side_b'):
            band = max(2, r[3] // 4) if role != 'shield' else 0
            cv.rect(r, lambda x, y, w, h, gx, gy, band=band: scales(x, y, cv, gx, gy, band, h))
        elif name == 'top' and role == 'shield':
            # Boros's back: scaled, a jade-and-gold step fret down the spine
            def spine(x, y, w, h, gx, gy):
                mid = abs(x - w / 2) < 4
                if mid:
                    return GOLD_HI if greca(x, y, w, h) else (JADE if (x + y) % 5 else JADE_HI)
                if abs(x - w / 2) < 5:
                    return GOLD
                return scales(x, y, cv, gx, gy)
            cv.rect(r, spine)
        else:
            cv.rect(r, lambda x, y, w, h, gx, gy: scales(x, y, cv, gx, gy))


def paint_flat(cv, c, fn):
    for r in faces(c).values():
        cv.rect(r, fn)


def eye(cv, r, at_x, at_y, rad=2.6):
    """A red eye ringed with gold (glowing)."""
    u, v, w, h = r
    cx, cy = u + at_x, v + at_y
    for y in range(int(cy - rad - 2), int(cy + rad + 3)):
        for x in range(int(cx - rad - 2), int(cx + rad + 3)):
            if not (0 <= x < TEX and 0 <= y < TEX_H):
                continue
            d = math.hypot(x - cx, y - cy)
            if d <= rad * 0.55:
                col = np.array([255, 236, 120]) if d < rad * 0.25 else RED
                cv.img[y, x, :3] = col
                cv.glow[y, x] = list(col) + [255]
            elif d <= rad:
                cv.img[y, x, :3] = RED * 0.8
                cv.glow[y, x] = list(RED) + [200]
            elif d <= rad + 1.2:
                cv.img[y, x, :3] = GOLD_HI


# ---------------------------------------------------------------------------------------------------- feathers

class Feathers:
    """Feather planes (0-deep cubes) are given texture space from row 400 down, left to right."""
    def __init__(self, cv):
        self.cv, self.x, self.y, self.row = cv, 0, 384, 0

    def take(self, w, h):
        if self.x + w * 2 > TEX:
            self.x, self.y, self.row = 0, self.y + self.row + 1, 0
        if self.y + h > TEX_H:
            raise ValueError('no room left for feathers')
        at = (self.x, self.y)
        self.x += w * 2 + 1
        self.row = max(self.row, h)
        return at

    def feather(self, w, h, colors, seed):
        """A feather w x h (0 deep: its two faces side by side), tip up (y 0), quill down the middle; colours from the
        quill end to the tip."""
        u, v = self.take(w, h)
        rng = np.random.default_rng(seed)
        for face in range(2):
            for y in range(h):
                t = 1 - y / max(1, h - 1)               # 0 at the quill end .. 1 at the tip
                half = (w / 2) * (0.35 + 0.65 * math.sin(math.pi * min(1.0, 0.15 + t * 0.95)) ** 0.7)
                for x in range(w):
                    dx = abs(x + 0.5 - w / 2)
                    if dx > half:
                        continue
                    # notches in the vane
                    if dx > half - 1 and rng.random() < 0.25:
                        continue
                    seg = min(len(colors) - 1, int(t * len(colors)))
                    col = colors[seg].astype(float)
                    if dx < 0.6:
                        col = lerp(col, np.array([250, 240, 210]), 0.55)   # the quill
                    else:
                        col = col * (0.82 + 0.18 * (1 - dx / max(0.5, half))) * (0.92 + 0.08 * ((y // 2) % 2))
                    self.cv.img[v + y, u + face * w + x, :3] = np.clip(col, 0, 255)
                    self.cv.img[v + y, u + face * w + x, 3] = 255
        return [u, v]


def crest(parts, parent, at, feathers, rings, tilt_base, name='crest'):
    """A ring of feathers fanned round a point (in the parent's space), flaring back: rings = [(count, length, width,
    colours, tilt, phase)]."""
    parts[name] = {'parent': parent, 'pose': [at[0], at[1], at[2], 0, 0, 0], 'cubes': []}
    k = 0
    for ri, (count, length, width, colors, tilt, phase) in enumerate(rings):
        for i in range(count):
            ang = 2 * math.pi * (i + phase) / count
            uv = feathers.feather(width, length, colors, seed=ri * 100 + i)
            parts['%s_%d' % (name, k)] = {'parent': name, 'pose': [0, 0, 0, round(-(tilt_base + tilt), 4), 0, round(ang, 4)],
                                          'cubes': [{'uv': uv, 'box': [-width / 2, -length, 0, width, length, 0], 'mirror': False,
                                                     'grow': 0.0}]}
            k += 1


FIERY = [YELLOW, ORANGE, ORANGE, RED, RED]
QUETZAL = [JADE, SCALE, SCALE_HI, YELLOW]
CROWN = [ORANGE, RED, RED, np.array([120, 14, 20])]


# ---------------------------------------------------------------------------------------------------- the serpents

def build(kind):
    head_java = os.path.join(SRC, 'Fairkeeper%sModel.java' % kind.capitalize())
    body_java = os.path.join(SRC, 'Fairkeeper%sBodyModel.java' % kind.capitalize())
    head, _ = parse(head_java)
    body, _ = parse(body_java)
    zf = zipfile.ZipFile(JAR)
    tmp = os.path.join(ROOT, 'tools/.cache/serpents')
    os.makedirs(tmp, exist_ok=True)
    srcs = {}
    for part in ('head', 'body'):
        p = os.path.join(tmp, '%s_%s.png' % (kind, part))
        with open(p, 'wb') as f:
            f.write(zf.read('assets/dungeonnowloading/textures/entity/fairkeeper_%s/fairkeeper_%s_%s.png' % (kind, kind, part)))
        srcs[part] = p

    # ---- the head
    cv = Canvas(srcs['head'])
    fe = Feathers(cv)
    if kind == 'ouros':
        head = drop(head, 'horns')
        jaw = 'upper_jaw'
    else:
        head = drop(head, 'eye_socket')
        jaw = 'upper_jaw'
    for name, p in head.items():
        for c in p['cubes']:
            w, h, d = c['box'][3:6]
            flat = min(w, h, d) == 0
            if name in ('fang', 'small_teeth', 'teeth') or (flat and name != 'mouth_flap_right' and name != 'mouth_flap_left'):
                paint_flat(cv, c, lambda x, y, w, h, gx, gy: TEETH * (0.9 + 0.1 * ((x + y) % 2)))
            elif name in ('mouth_flap_right', 'mouth_flap_left'):
                paint_flat(cv, c, lambda x, y, w, h, gx, gy: lerp(MOUTH_DK, SCALE_DK, 0.4))
            elif name in ('fang_sheath',):
                paint_flat(cv, c, lambda x, y, w, h, gx, gy: MOUTH)
            elif name in ('tongue',):
                paint_flat(cv, c, lambda x, y, w, h, gx, gy: TONGUE * cv.detail(gx, gy, 0.3))
            elif name in ('tongue_sheath', 'glottis'):
                paint_flat(cv, c, lambda x, y, w, h, gx, gy: MOUTH_DK * cv.detail(gx, gy, 0.3))
            elif name in ('stone_helmet', 'cube_r1', 'cube_r2'):
                # the gold mask: chased gold, a jade inlay in the big faces' step frets, red eyes kept
                def mask(x, y, w, h, gx, gy):
                    if cv.red[gy, gx]:
                        cv.glow[gy, gx] = [255, 60, 40, 255]
                        return np.array([255, 70, 40])
                    if w >= 12 and h >= 8 and 2 <= y < h - 2 and greca(x, y, w, h):
                        return JADE_HI if (x // 8) % 2 else JADE
                    return gold(cv, gx, gy)
                paint_flat(cv, c, mask)
            elif name == 'fuel_gland':
                paint_flat(cv, c, lambda x, y, w, h, gx, gy: JADE * cv.detail(gx, gy, 0.6))
            elif name == jaw and c['box'][4] >= 9:
                # the head proper: scaled on top and sides, the mouth's roof pink, a gold snout plate, eyes
                f = faces(c)
                paint_scaled(cv, c, 'head')
                cv.rect(f['bottom'], lambda x, y, w, h, gx, gy: MOUTH * cv.detail(gx, gy, 0.3))
                cv.rect(f['front'], lambda x, y, w, h, gx, gy: GOLD_HI if greca(x, y, w, h) else gold(cv, gx, gy))

                def snout(x, y, w, h, gx, gy):
                    # gold plates over the front third of the head's top, the rest scaled
                    if y < h * 0.4:
                        return GOLD_HI if (x in (w // 2 - 1, w // 2)) or greca(x, y, w, h) else gold(cv, gx, gy)
                    return scales(x, y, cv, gx, gy)
                cv.rect(f['top'], snout)
                if kind == 'ouros':
                    # eyes: near the back of the head, high on each side
                    for side in ('side_a', 'side_b'):
                        u, v, w, h = f[side]
                        eye(cv, f[side], w * (0.72 if side == 'side_a' else 0.28), h * 0.32, 2.8)
            elif name == 'lower_jaw':
                f = faces(c)
                paint_scaled(cv, c, 'jaw')
                cv.rect(f['top'], lambda x, y, w, h, gx, gy: MOUTH * cv.detail(gx, gy, 0.3))
                cv.rect(f['front'], lambda x, y, w, h, gx, gy: gold(cv, gx, gy))
            else:
                paint_scaled(cv, c, 'head')
    # the feather crown round the back of the head, flaring back over the neck
    back_z = 0.0 if kind == 'ouros' else 3.0
    crest(head, jaw, [0, -4 if kind == 'ouros' else -14, back_z], fe, [
        (20, 52, 12, FIERY, 0.0, 0.0),
        (16, 40, 11, QUETZAL, 0.16, 0.5),
        (14, 28, 9, FIERY, 0.3, 0.25),
        (10, 18, 7, CROWN, 0.45, 0.75),
    ], 0.5)
    if kind == 'ouros':
        # Ouros's horns were stone: a tall crown of feathers over his brow instead
        head['brow'] = {'parent': jaw, 'pose': [0, -11, -14, 0, 0, 0], 'cubes': []}
        for i in range(5):
            uv = fe.feather(6, 22 + (8 if i == 2 else 0) - abs(i - 2) * 3, [JADE, SCALE_HI, YELLOW, RED], seed=500 + i)
            head['brow_%d' % i] = {'parent': 'brow', 'pose': [(i - 2) * 4.0, 0, 0, -0.35, 0, round((i - 2) * 0.22, 4)],
                                   'cubes': [{'uv': uv, 'box': [-3, -(22 + (8 if i == 2 else 0) - abs(i - 2) * 3), 0, 6,
                                                                22 + (8 if i == 2 else 0) - abs(i - 2) * 3, 0], 'mirror': False, 'grow': 0.0}]}
    write(kind, 'head', head, cv)

    # ---- the body
    cv = Canvas(srcs['body'])
    fe = Feathers(cv)
    seg = 'ouros_segment' if kind == 'ouros' else 'boros_segment'
    body = drop(body, 'cannon', 'trap_door_front', 'trap_door_back', 'horn', 'horn2', 'horn3', 'horn4', 'horn5', 'horn6',
                'dispenser', 'shield')
    for name, p in body.items():
        for c in p['cubes']:
            w, h, d = c['box'][3:6]
            if name == 'stone_shield':
                if w >= 30:
                    paint_scaled(cv, c, 'shield')
                else:
                    paint_flat(cv, c, lambda x, y, w, h, gx, gy: JADE_HI if (x + y) % 3 == 0 else gold(cv, gx, gy))
            elif name == 'body' and w <= 4 and d <= 8:
                # Ouros's little dorsal knobs: gold studs
                paint_flat(cv, c, lambda x, y, w, h, gx, gy: gold(cv, gx, gy))
            elif name == 'body' and w <= 2:
                # Ouros's side plates: dark scales edged in gold
                paint_flat(cv, c, lambda x, y, w, h, gx, gy: GOLD if y in (0, h - 1) else scales(x, y, cv, gx, gy))
            else:
                paint_scaled(cv, c, 'body')
    # a collar of feathers round the neck end (the first segment), and the tail's fan
    crest(body, seg, [0, -9 if kind == 'ouros' else -14, -22], fe, [
        (16, 30, 10, FIERY, 0.0, 0.0),
        (12, 20, 8, QUETZAL, 0.2, 0.5),
    ], 0.95, name='collar')
    tail_z = 21 if kind == 'ouros' else 23
    body['fan'] = {'parent': 'tail', 'pose': [0, -11 if kind == 'ouros' else -2, tail_z, 0, 0, 0], 'cubes': []}
    for i in range(11):
        length = 56 - abs(i - 5) * 4
        uv = fe.feather(12, length, [YELLOW, ORANGE, RED, RED, JADE], seed=900 + i)
        body['fan_%d' % i] = {'parent': 'fan', 'pose': [0, 0, 0, round(-1.35 - abs(i - 5) * 0.05, 4), round((i - 5) * 0.17, 4), 0],
                              'cubes': [{'uv': uv, 'box': [-6, -length, 0, 12, length, 0], 'mirror': False, 'grow': 0.0}]}
    write(kind, 'body', body, cv)


def write(kind, part, parts, cv):
    out = {'texture': [TEX, TEX_H], 'parts': parts}
    d = os.path.join(ASSETS, 'serpent')
    os.makedirs(d, exist_ok=True)
    with open(os.path.join(d, '%s_%s.json' % (kind, part)), 'w', newline='\n') as f:
        json.dump(out, f, separators=(',', ':'))
    tex = os.path.join(ASSETS, 'textures/entity/serpent_%s_%s.png' % (kind, part))
    Image.fromarray(cv.img).save(tex)
    Image.fromarray(cv.glow).save(tex.replace('.png', '_glow.png'))
    print(tex, len(parts), 'parts')


if __name__ == '__main__':
    for k in ('ouros', 'boros'):
        build(k)


def smoke():
    """textures/entity/black_smoke.png: a soft, lumpy puff of smoke (white, tinted near-black when drawn)."""
    n = 64
    rng = np.random.default_rng(3)
    img = np.zeros((n, n, 4), np.uint8)
    blobs = [(rng.uniform(20, 44), rng.uniform(20, 44), rng.uniform(9, 17)) for _ in range(9)]
    for y in range(n):
        for x in range(n):
            a = 0.0
            for bx, by, br in blobs:
                d = math.hypot(x - bx, y - by) / br
                a = max(a, 1 - d * d)
            a *= 0.75 + 0.25 * rng.random()
            img[y, x] = [255, 255, 255, int(max(0, min(1, a)) * 255)]
    Image.fromarray(img).save(os.path.join(ASSETS, 'textures/entity/black_smoke.png'))


if __name__ == '__main__':
    smoke()
