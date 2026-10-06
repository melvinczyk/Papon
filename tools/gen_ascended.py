#!/usr/bin/env python3
"""Ascended Papon: Chef Papon's last form (phase three). Still Papon, a better one.

The same potato (his own baked body, face rig and all, papon_boss bones and UVs), drawn bigger by the renderer
(BossRenderer.ASCENDED_SCALE), with:
  arms        stronger Papon arms: a shoulder with a little bicep bump, a forearm, a gold cuff, a fist (on
              arm_*_mesh, so his animations' reaches stretch them as before)
  six-pack    a few pixel lines on the lower front, under his mouth
  tattoo      the Mexican flag on the back of the potato, inked: green | white | red, the eagle in the middle
  eyes        white, glowing (glowmask; the renderer adds the lightning)
  wings       eagle wings in the Spiritcaller's style (Illage & Spillage): four chained flat planes a side
              (wing_r > wing_r2 > wing_r3 > wing_r4), each a stylised shape (a top rising to a point, tiers of
              feathers drawn as lines, the flight feathers' tips scalloping the bottom) with a one-pixel gold rim
              that glows; golden coverts darkening to brown flight feathers. They unfurl a segment at a time (the
              "form" controller's a_wings_* animations).
Gone: the apron, the fry crown, the butter, the fry wings.

Every papon_boss bone keeps its name and place, so papon_boss.animation.json drives this model as it is.

Writes geo/entity/papon_ascended.geo.json and textures/entity/papon_ascended.png (+ _glowmask), 512 x 512:
the baked boss sheet top-left (its cubes keep their UVs), the new parts packed around it, one texel a pixel.

  python3.11 tools/gen_ascended.py      (after gen_vendor.py: it reads papon_boss.geo.json and the baked sheet)
"""
import copy
import json
import math
import os

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/papon')
TEX = 512

GOLD = np.array([236, 190, 74])
FORE_PIVOT = (-10.5, 7.0, 0.0)   # the right elbow (the left mirrored): the bottom of the upper arm, client/BossModel.ELBOW
FLAG_G, FLAG_W, FLAG_R = np.array([22, 110, 70]), np.array([238, 234, 224]), np.array([204, 34, 46])
RIM = np.array([255, 222, 132])
COVERT = np.array([222, 162, 72])
MID = np.array([156, 100, 46])
DARK = np.array([92, 56, 28])
LINE = np.array([70, 42, 20])
SHAFT = np.array([176, 120, 60])
CREASE = np.array([176, 98, 32])


# ===================================================================== the sheet

class Sheet:
    def __init__(self):
        self.img = np.zeros((TEX, TEX, 4), np.uint8)
        self.glow = np.zeros((TEX, TEX, 4), np.uint8)
        base = np.asarray(Image.open(os.path.join(ASSETS, 'textures/entity/papon_boss_baked.png')).convert('RGBA'))
        self.img[:256, :256] = base
        g = os.path.join(ASSETS, 'textures/entity/papon_boss_baked_glowmask.png')
        if os.path.exists(g):
            self.glow[:256, :256] = np.asarray(Image.open(g).convert('RGBA'))
        self.regions = [[256, 0, TEX, 256], [0, 256, TEX, TEX]]
        self.cur = [[r[0], r[1], 0] for r in self.regions]

    def take(self, w, h):
        for i, (x0, y0, x1, y1) in enumerate(self.regions):
            x, y, sh = self.cur[i]
            if x + w > x1:
                x, y, sh = x0, y + sh + 1, 0
            if y + h > y1 or w > x1 - x0:
                continue
            self.cur[i] = [x + w + 1, y, max(sh, h)]
            return x, y
        raise ValueError('texture full')


SHEET = None
FACES = {'north': (0, 1), 'south': (0, 1), 'east': (2, 1), 'west': (2, 1), 'up': (0, 2), 'down': (0, 2)}


def cube(origin, size, painter, skip=()):
    """A cube with per-face UVs at one texel a pixel; painter(face, w, h) -> RGBA (or (RGBA, glow))."""
    c = {'origin': [round(float(v), 4) for v in origin], 'size': [round(float(v), 4) for v in size], 'uv': {}}
    for face, (a, b) in FACES.items():
        if face in skip:
            continue
        fw, fh = max(1, int(math.ceil(size[a]))), max(1, int(math.ceil(size[b])))
        out = painter(face, fw, fh)
        img, glow = out if isinstance(out, tuple) else (out, None)
        x, y = SHEET.take(fw, fh)
        SHEET.img[y:y + fh, x:x + fw] = img
        if glow is not None:
            SHEET.glow[y:y + fh, x:x + fw] = glow
        c['uv'][face] = {'uv': [x, y], 'uv_size': [fw, fh]}
    return c


# ===================================================================== his skin

_POT = None
_seed = [0]


def potato_src():
    global _POT
    if _POT is None:
        im = np.asarray(Image.open(os.path.join(ASSETS, 'textures/entity/papon_boss_baked.png')).convert('RGBA')).astype(float)
        _POT = [im[16:36, 0:16, :3], im[16:36, 32:48, :3], im[16:36, 48:64, :3]]
    return _POT


def skin(detail=None):
    """His potato skin (8 x 8 tiles of the baked potato's own sides) and an optional pixel-art detail."""
    def paint(face, w, h):
        _seed[0] += 1
        rng = np.random.default_rng(_seed[0] * 7919)
        src = potato_src()
        rgb = np.zeros((h, w, 3))
        for y in range(0, h, 8):
            for x in range(0, w, 8):
                s = src[rng.integers(len(src))]
                sy, sx = rng.integers(0, s.shape[0] - 8), rng.integers(0, s.shape[1] - 8)
                rgb[y:y + 8, x:x + 8] = s[sy:sy + 8, sx:sx + 8][:min(8, h - y), :min(8, w - x)]
        if detail:
            detail(face, rgb)
        return np.concatenate([np.clip(rgb, 0, 255), np.full((h, w, 1), 255)], -1).astype(np.uint8)
    return paint


def solid(col):
    def paint(face, w, h):
        _seed[0] += 1
        rng = np.random.default_rng(_seed[0])
        rgb = np.ones((h, w, 3)) * col * (1 + rng.normal(0, 0.05, (h, w, 1)))
        rgb[0, :] = rgb[0, :] * 1.12
        return np.concatenate([np.clip(rgb, 0, 255), np.full((h, w, 1), 255)], -1).astype(np.uint8)
    return paint


def bicep(face, rgb):
    h, w = rgb.shape[:2]
    if face == 'north':
        rgb[h - 1, 1:w - 1] = CREASE           # the curve under the bicep
        rgb[1, 2:w - 2] = rgb[1, 2:w - 2] * 1.08


# ===================================================================== the wings

def wing_segment(seg, nseg):
    """One plane of a wing, drawn as the Spiritcaller's are: a filled shape with a one-pixel rim. The top rises
    toward the outer edge; inside, the small coverts (scallops), the greater coverts over a dark line, then the
    flight feathers (a shaft every three pixels) whose tips scallop the bottom edge; the outermost segment's
    primaries part into fingers. Gold coverts darkening to brown; the rim gold and glowing."""
    def paint(face, w, h):
        img = np.zeros((h, w, 4))
        inside = np.zeros((h, w), bool)
        for x in range(w):
            u = (x + 0.5) / w
            top = int(round((h * 0.16) * (1 - u) + (h * 0.02) * u + (2 if (seg == 0 and u < 0.3) else 0)))
            k = x % 3
            bot = h - 1 - (0 if k == 1 else 1) - int(round(h * 0.12 * (1 - (seg + u) / nseg)))
            if seg == nseg - 1 and u > 0.55:
                bot -= int(round((u - 0.55) * h * 0.55))          # the wing tip sweeps up
                if k != 1 and bot > top + 4:
                    bot -= 3                                      # ...and the primaries part: fingers
            inside[top:bot + 1, x] = True
        c1, c2 = int(h * 0.30), int(h * 0.52)
        for y in range(h):
            for x in range(w):
                if not inside[y, x]:
                    continue
                if y < c1:
                    col = COVERT * (0.92 if ((x + (y // 2)) % 3 == 0) else 1.0)
                    if y % 3 == 2 and (x + y // 3) % 3 != 1:
                        col = col * 0.82                          # the scallops of the small coverts
                elif y < c2:
                    t = (y - c1) / max(1, c2 - c1)
                    col = COVERT * (1 - t) * 0.85 + MID * t
                    if y == c2 - 1 or (y == c2 - 2 and x % 3 != 1):
                        col = LINE                                # the line under the greater coverts
                else:
                    t = (y - c2) / max(1, h - c2)
                    col = MID * (1 - t) + DARK * t
                    if x % 3 == 1:
                        col = SHAFT * (1 - 0.5 * t)               # each flight feather's shaft
                    elif x % 3 == 0:
                        col = col * 0.82                          # the edge between feathers
                img[y, x, :3] = col
                img[y, x, 3] = 255
        # the rim: along the top and the feather tips (and round the wing tip), not where segments meet, so the
        # four planes read as one wing
        rim = np.zeros((h, w), bool)
        for y in range(h):
            for x in range(w):
                if not inside[y, x]:
                    continue
                for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    yy, xx = y + dy, x + dx
                    if dx != 0 and not (0 <= xx < w):
                        if seg == nseg - 1 and xx >= w:
                            rim[y, x] = True
                        continue
                    if not (0 <= yy < h and 0 <= xx < w) or not inside[yy, xx]:
                        rim[y, x] = True
        img[rim, :3] = RIM
        glow = np.zeros((h, w, 4))
        glow[rim, :3] = RIM
        glow[rim, 3] = 255
        return np.clip(img, 0, 255).astype(np.uint8), glow.astype(np.uint8)
    return paint


# segments (width, height) inner to tip, px; the root on his upper back; the rest pose spread like the flag's eagle
WING_SEGS = [(9, 28), (8, 24), (8, 20), (7, 16)]          # broad by his body, narrowing to the tip
WING_ROOT = [4.0, 20.0, 8.6]
WING_REST = [(0, -12, 28), (0, -4, -6), (0, -4, -7), (0, -3, -9)]


def wings():
    out = []
    for side, s in (('r', -1), ('l', 1)):
        parent = 'torso'
        x = 0.0
        for i, (w, h) in enumerate(WING_SEGS):
            name = 'wing_' + side + ('' if i == 0 else str(i + 1))
            piv = [s * (WING_ROOT[0] + x), WING_ROOT[1] + 1.5 * i, WING_ROOT[2]]
            top = 3 + i
            o = [piv[0] if s > 0 else piv[0] - w, piv[1] + top - h, piv[2]]
            painter = wing_segment(i, len(WING_SEGS))
            if s < 0:
                base = painter

                def painter(face, fw, fh, base=base):
                    img, gl = base(face, fw, fh)
                    return img[:, ::-1].copy(), gl[:, ::-1].copy()
            rx, ry, rz = WING_REST[i]
            out.append({'name': name, 'parent': parent, 'pivot': [round(v, 4) for v in piv],
                        'rotation': [rx, ry * -s, rz * -s],
                        'cubes': [cube(o, [w, h, 0], painter, skip=('up', 'down', 'east', 'west'))]})
            parent = name
            x += w
    return out


# ===================================================================== the model

DROP = {'apron', 'butter', 'fry_crown', 'wing_r', 'wing_l'} | {'fry_%s%d' % (s, i) for s in 'rl' for i in range(1, 8)}


def build():
    global SHEET
    SHEET = Sheet()
    g = json.load(open(os.path.join(ASSETS, 'geo/entity/papon_boss.geo.json')))['minecraft:geometry'][0]
    bones = [b for b in copy.deepcopy(g['bones']) if b['name'] not in DROP]
    by = {b['name']: b for b in bones}

    # ---- stronger Papon arms on the stretching mesh bones (the shoulder at the old pivot)
    for side, s in (('r', -1), ('l', 1)):
        def add(o, sz, p=None, s=s):
            o = o if s < 0 else [-o[0] - sz[0], o[1], o[2]]
            return cube(o, sz, p or skin())
        mesh = by['arm_%s_mesh' % side]
        mesh['cubes'] = [add([-13, 7, -3.5], [5, 6, 7], skin(bicep))]        # shoulder and upper arm, the bicep drawn on
        # the forearm on its own bone, hinged at the elbow (2026-10-06: his arms bend round the guitar). It hangs off the arm,
        # not the stretched mesh (a rotation under a scaled parent shears): client/BossModel slides it to the end of the
        # stretched upper arm and stretches it the same every frame, so animations that only stretch look as they did
        fore = {'name': 'fore_' + side, 'parent': 'arm_' + side, 'pivot': [-s * FORE_PIVOT[0], FORE_PIVOT[1], FORE_PIVOT[2]], 'cubes': [
            add([-12.5, 3.5, -3], [4, 4, 6]),                     # forearm
            add([-12.8, 4.5, -3.3], [4.6, 1, 6.6], solid(GOLD)),  # gold cuff
            add([-13, 1.5, -3.2], [5, 3, 6.4]),                   # fist
        ]}
        bones.insert(bones.index(mesh) + 1, fore)
    # ---- the potato's back: the tattoo; the lower front: a six-pack (on his own sheet's faces)
    body = by['body']['cubes'][0]
    u, v = body['uv']
    w, h, d = (int(x) for x in body['size'])
    tattoo(SHEET.img, int(u) + 2 * d + w, int(v) + d, w, h)          # box UV south face
    six_pack(SHEET.img, int(u) + d, int(v) + d, w, h)                # box UV north face
    # ---- his eyes: white and glowing
    for side in ('r', 'l'):
        for c in by['eye_' + side].get('cubes', []):
            cu, cv = c['uv']
            cw, ch, cd = (int(math.ceil(x)) for x in c['size'])
            x0, y0 = int(cu), int(cv)
            SHEET.img[y0:y0 + cd + ch, x0:x0 + 2 * (cw + cd)] = [252, 252, 255, 255]
            SHEET.glow[y0:y0 + cd + ch, x0:x0 + 2 * (cw + cd)] = [255, 255, 255, 255]
    bones += wings()

    geo = {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': 'geometry.papon_ascended', 'texture_width': TEX, 'texture_height': TEX,
                        'visible_bounds_width': 8, 'visible_bounds_height': 5, 'visible_bounds_offset': [0, 1.5, 0]},
        'bones': bones}]}
    out = os.path.join(ASSETS, 'geo/entity/papon_ascended.geo.json')
    with open(out, 'w', newline='\n') as f:
        json.dump(geo, f, separators=(',', ':'))
    Image.fromarray(SHEET.img, 'RGBA').save(os.path.join(ASSETS, 'textures/entity/papon_ascended.png'))
    Image.fromarray(SHEET.glow, 'RGBA').save(os.path.join(ASSETS, 'textures/entity/papon_ascended_glowmask.png'))
    return out


def tattoo(img, x0, y0, w, h):
    """The Mexican flag inked on the potato's back (seen from behind): three bands, the coat of arms in the
    white one, in the middle of his back with a pixel of skin round it."""
    tx0, ty0, tw, th = x0 + 1, y0 + 10, w - 2, 9          # below his horseshoe of hair
    sk = img[ty0:ty0 + th, tx0:tx0 + tw, :3].astype(float)
    band = np.zeros((th, tw, 3))
    for x in range(tw):
        band[:, x] = FLAG_G if x < tw / 3 else FLAG_W if x < 2 * tw / 3 else FLAG_R
    ink = sk * 0.3 + band * 0.7
    cx, cy = tw // 2, th // 2
    for dx, dy in [(-1, -2), (0, -2), (-2, -1), (-1, -1), (0, -1), (1, -1), (-1, 0), (0, 0), (1, -3)]:
        ink[cy + dy, cx + dx] = [100, 62, 30]
    for dx, dy in [(0, 1), (-1, 2), (0, 2), (1, 2)]:
        ink[cy + dy, cx + dx] = [48, 116, 52]
    img[ty0:ty0 + th, tx0:tx0 + tw, :3] = np.clip(ink, 0, 255).astype(np.uint8)


def six_pack(img, x0, y0, w, h):
    """A few pixel lines under his mouth: the six-pack (two columns of two: his front is mostly face)."""
    cx = x0 + w // 2
    yb = y0 + h - 1
    for y in range(yb - 6, yb - 1):
        img[y, cx - 1, :3] = (img[y, cx - 1, :3] * 0.8).astype(np.uint8)
    for y in (yb - 4, yb - 2):
        img[y, cx - 4:cx + 3, :3] = (img[y, cx - 4:cx + 3, :3] * 0.82).astype(np.uint8)


if __name__ == '__main__':
    print(build())
