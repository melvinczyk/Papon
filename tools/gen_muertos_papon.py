#!/usr/bin/env python3
"""El Rey de los Muertos: Chef Papon's truest form (phase four, form 4). Ascended Papon (gen_ascended.py) burnt down to the
bone and painted for the Day of the Dead:

  skin      every potato-coloured pixel of his body, arms and legs turned to bone (by its own light and shade), the
            joints and the knuckles inked
  face      a sugar skull painted on: black sockets round his eyes ringed with marigold and pink petals, a heart of a nose,
            the mouth stitched across (teeth inked over and under it), a flower on his brow, swirls on his cheeks
  eyes      ectoplasm blue, glowing
  back      a spine and ribs inked where the flag tattoo was
  hat       a Catrin's top hat: black, a marigold and rosa band round it, two marigolds and a feather on it (bone catrin_hat)
  hair      the horseshoe and the wisps turned to marigold petals
  wings     the eagle wings' four planes a side repainted as bone wings: the arm bones along the top, finger bones fanning
            down, and between them spectral feathers of blue light, translucent, their edges glowing
  hammer    the spirit hammer in his right fist (bone spirit_hammer, shown by client/BossModel while he holds it): a femur
            for a handle, a head of sugar-skull bone with a face on each striking end, banded in marigold, inside a
            shell of blue light

Same bones as papon_ascended (so papon_boss.animation.json drives it), two more. Writes geo/entity/papon_muertos.geo.json and
textures/entity/papon_muertos.png (+ _glowmask), 512 x 512.

  python tools/gen_muertos_papon.py          (after gen_ascended.py)
"""
import colorsys
import copy
import json
import math
import os

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/papon')
TEX = 512

BONE = np.array([238, 230, 210])
BONE_HI = np.array([252, 248, 236])
BONE_DK = np.array([176, 164, 136])
INK = np.array([22, 16, 28])
# (2026-10-04) Papon, the Final Guardian: the souls he took burn jade green in him (eyes, wings, the hammer's light)
ECTO = np.array([52, 255, 132])
ECTO_HI = np.array([214, 255, 220])
MARIGOLD = np.array([255, 150, 24])
MARIGOLD_HI = np.array([255, 208, 64])
PINK = np.array([236, 46, 140])
PURPLE = np.array([122, 44, 186])
HAT = np.array([26, 22, 30])
GOLD = np.array([232, 180, 52])
GOLD_HI = np.array([255, 236, 140])
GOLD_DK = np.array([150, 96, 22])
JADE = np.array([24, 170, 120])
JADE_HI = np.array([120, 236, 184])
QUETZAL = np.array([20, 150, 80])
QUETZAL_HI = np.array([96, 230, 120])
CRIMSON = np.array([200, 30, 36])
OBSIDIAN = np.array([22, 18, 30])
BLOOD = np.array([150, 10, 16])
BLOOD_HI = np.array([216, 40, 40])


class Sheet:
    def __init__(self):
        self.img = np.asarray(Image.open(os.path.join(ASSETS, 'textures/entity/papon_ascended.png')).convert('RGBA')).copy()
        g = os.path.join(ASSETS, 'textures/entity/papon_ascended_glowmask.png')
        self.glow = np.asarray(Image.open(g).convert('RGBA')).copy() if os.path.exists(g) else np.zeros((TEX, TEX, 4), np.uint8)
        self.x, self.y, self.row = 0, 384, 0

    def take(self, w, h):
        if self.x + w > TEX:
            self.x, self.y, self.row = 0, self.y + self.row + 1, 0
        if self.y + h > TEX:
            raise ValueError('texture full')
        at = (self.x, self.y)
        self.x += w + 1
        self.row = max(self.row, h)
        return at


S = None
FACES = {'north': (0, 1), 'south': (0, 1), 'east': (2, 1), 'west': (2, 1), 'up': (0, 2), 'down': (0, 2)}


SHARED = {}


def cube(origin, size, painter, skip=(), share=None):
    """A cube with its faces painted onto the sheet; with {@code share}, cubes of the same key reuse the first one's pixels."""
    if share is not None and share in SHARED:
        return {'origin': [round(float(v), 4) for v in origin], 'size': [round(float(v), 4) for v in size], 'uv': SHARED[share]}
    c = {'origin': [round(float(v), 4) for v in origin], 'size': [round(float(v), 4) for v in size], 'uv': {}}
    for face, (a, b) in FACES.items():
        if face in skip:
            continue
        fw, fh = max(1, int(math.ceil(size[a]))), max(1, int(math.ceil(size[b])))
        out = painter(face, fw, fh)
        img, glow = out if isinstance(out, tuple) else (out, None)
        x, y = S.take(fw, fh)
        S.img[y:y + fh, x:x + fw] = img
        if glow is not None:
            S.glow[y:y + fh, x:x + fw] = glow
        c['uv'][face] = {'uv': [x, y], 'uv_size': [fw, fh]}
    if share is not None:
        SHARED[share] = c['uv']
    return c


def rgba(rgb, a=255):
    return np.concatenate([np.clip(rgb, 0, 255), np.full(rgb.shape[:2] + (1,), a)], -1).astype(np.uint8)


def solid(col, noise=0.05, seed=[0]):
    def paint(face, w, h):
        seed[0] += 1
        rng = np.random.default_rng(seed[0] * 31)
        rgb = np.ones((h, w, 3)) * col * (1 + rng.normal(0, noise, (h, w, 1)))
        return rgba(rgb)
    return paint


# ===================================================================== bone, from potato

def to_bone(px):
    """A potato pixel to bone: its light and shade kept, its colour gone."""
    r, g, b = [v / 255 for v in px[:3]]
    lum = 0.3 * r + 0.59 * g + 0.11 * b
    k = np.clip((lum - 0.35) / 0.45, 0, 1)
    return BONE_DK * (1 - k) + BONE_HI * k


def is_potato(px):
    if px[3] == 0:
        return False
    h, s, v = colorsys.rgb_to_hsv(*(c / 255 for c in px[:3]))
    return 0.04 <= h <= 0.16 and s > 0.25 and v > 0.25


def rect_list(cubes):
    """Every face rectangle of these cubes on the sheet (box UV or per-face)."""
    out = []
    for c in cubes:
        uv = c.get('uv')
        w, h, d = (int(math.ceil(v)) for v in c['size'])
        if isinstance(uv, dict):
            for f in uv.values():
                out.append((int(f['uv'][0]), int(f['uv'][1]), int(math.ceil(f['uv_size'][0])), int(math.ceil(f['uv_size'][1]))))
        elif uv is not None:
            u, v = int(uv[0]), int(uv[1])
            out.append((u, v, 2 * (w + d), d + h))
    return out


def bone_over(rects):
    for x0, y0, w, h in rects:
        for y in range(y0, min(TEX, y0 + h)):
            for x in range(x0, min(TEX, x0 + w)):
                px = S.img[y, x]
                if is_potato(px):
                    S.img[y, x, :3] = to_bone(px).astype(np.uint8)
                    S.glow[y, x] = 0


def ring(cx, cy, r0, r1, colors, x0, y0, w, h, n=10):
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            d = math.hypot(x + 0.5 - cx, (y + 0.5 - cy) * 1.05)
            if r0 <= d < r1:
                a = math.atan2(y + 0.5 - cy, x + 0.5 - cx)
                S.img[y, x, :3] = colors[int((a + math.pi) / (2 * math.pi) * n) % len(colors)]


def face_paint(body):
    """The sugar skull on his face (the body's north face of its box UV: u = 16 + (8 - x), v = 16 + (24 - y))."""
    u0, v0 = int(body['uv'][0]), int(body['uv'][1])
    w, h, d = (int(v) for v in body['size'])
    fx, fy = u0 + d, v0 + d                       # the north face's top-left on the sheet

    def at(x, y):                                 # model (x, y) -> sheet pixel centre
        return fx + (8 - x), fy + (24 - y)
    for ex in (-4.5, 4.5):
        cx, cy = at(ex, 19.5)
        ring(cx, cy, 2.7, 3.9, [MARIGOLD, PINK], fx, fy, w, h, n=10)
        for y in range(fy, fy + h):
            for x in range(fx, fx + w):
                if math.hypot(x + 0.5 - cx, (y + 0.5 - cy) * 1.05) < 2.7:
                    S.img[y, x, :3] = INK
    # the nose: an upside-down heart
    nx, ny = at(0, 15.5)
    for dx, dy in ((-1.5, -1), (-0.5, -1), (0.5, -1), (1.5, -1), (-1, 0), (0, 0), (1, 0), (0, 1)):
        S.img[int(ny + dy), int(nx + dx), :3] = INK
    # the stitched mouth: a line across under the lips, stitches over and under it
    mx, my = at(0, 8.5)
    for x in range(int(mx - 6), int(mx + 7)):
        S.img[int(my), x, :3] = INK
        if x % 2 == 0:
            S.img[int(my) - 1, x, :3] = INK
            S.img[int(my) + 1, x, :3] = INK
    # the brow's flower and the cheeks' swirls
    bx, by = at(0, 23)
    for dx, dy, c in ((0, 0, PINK), (-1, 0, MARIGOLD), (1, 0, MARIGOLD), (0, -1, MARIGOLD), (0, 1, MARIGOLD)):
        S.img[int(by + dy), int(bx + dx), :3] = c
    for side in (-1, 1):
        cx, cy = at(side * 6.5, 14)
        for dx, dy in ((0, 0), (1, -1), (-1, 1), (side, 1)):
            S.img[int(cy + dy), int(cx + dx), :3] = PURPLE


def scrub(body):
    """Whatever colour is left on his body but the face (the flag tattoo, the sprouts on top): bone."""
    u0, v0 = int(body['uv'][0]), int(body['uv'][1])
    w, h, d = (int(v) for v in body['size'])
    for y in range(v0, v0 + d + h):
        for x in range(u0, u0 + 2 * (w + d)):
            if u0 + d <= x < u0 + d + w and y >= v0 + d:
                continue                                  # the face
            px = S.img[y, x]
            if px[3] == 0:
                continue
            hh, ss, vv = colorsys.rgb_to_hsv(*(c / 255 for c in px[:3]))
            if ss > 0.18:
                S.img[y, x, :3] = (BONE * (0.9 + 0.1 * vv)).astype(np.uint8)


def back_paint(body):
    """A spine and ribs inked down his back (the box UV's south face)."""
    u0, v0 = int(body['uv'][0]), int(body['uv'][1])
    w, h, d = (int(v) for v in body['size'])
    x0, y0 = u0 + 2 * d + w, v0 + d
    cx = x0 + w // 2
    for y in range(y0 + 3, y0 + h - 1):
        S.img[y, cx - 1:cx + 1, :3] = BONE_DK * 0.6
        if (y - y0) % 3 == 0:
            S.img[y, cx - 1:cx + 1, :3] = INK
    for k in range(5):
        y = y0 + 5 + k * 3
        for s in (-1, 1):
            for i in range(2, 7 - k // 2):
                S.img[y + i // 3, cx + s * i - (1 if s < 0 else 0), :3] = INK


def eyes(by):
    for side in ('r', 'l'):
        for c in by['eye_' + side].get('cubes', []):
            cu, cv = (int(v) for v in c['uv'])
            cw, ch, cd = (int(math.ceil(x)) for x in c['size'])
            S.img[cv:cv + cd + ch, cu:cu + 2 * (cw + cd), :3] = ECTO
            S.img[cv + cd:cv + cd + ch, cu + cd + cw // 2, :3] = ECTO_HI
            S.glow[cv:cv + cd + ch, cu:cu + 2 * (cw + cd)] = np.append(ECTO_HI, 255)


def flowers(rects):
    """The horseshoe and the wisps turned to marigold petals."""
    rng = np.random.default_rng(7)
    for x0, y0, w, h in rects:
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                if S.img[y, x, 3] == 0:
                    continue
                k = rng.random()
                S.img[y, x, :3] = MARIGOLD_HI if k < 0.3 else MARIGOLD if k < 0.8 else PINK


def lips(by):
    for name in ('lip_up', 'lip_low'):
        for (x0, y0, w, h) in rect_list(by[name].get('cubes', [])):
            S.img[y0:y0 + h, x0:x0 + w, :3] = INK
            S.img[y0:y0 + h, x0:x0 + w:2, :3] = BONE_DK


# ===================================================================== the wings

def bone_wing(seg, nseg):
    """A plane of a bone wing: the arm bone along the top, two finger bones fanning down, spectral feathers between."""
    def paint(face, w, h):
        img = np.zeros((h, w, 4))
        glow = np.zeros((h, w, 4))
        # feathers of blue light: long tapering shapes hanging from the bone, translucent, a bright rim
        for x in range(w):
            u = (x + 0.5) / w
            top = 2
            k = x % 3
            bot = h - 1 - (0 if k == 1 else 2) - int(round(h * 0.18 * (1 - (seg + u) / nseg)))
            if seg == nseg - 1 and u > 0.55:
                bot -= int(round((u - 0.55) * h * 0.6))
            for y in range(top, max(top, bot) + 1):
                t = (y - top) / max(1, bot - top)
                col = ECTO * (1 - t) * 0.9 + np.array([12, 104, 76]) * t
                a = 150 - 60 * t
                if y >= bot - 1 or k == 0:
                    col, a = ECTO_HI, 220                       # the feather's edge
                    glow[y, x] = np.append(ECTO_HI, 255)
                img[y, x] = np.append(col, a)
        # the bones: the arm bone along the top (knuckled at its ends), finger bones down to the tip
        for x in range(w):
            for y in (0, 1, 2):
                img[y, x] = np.append(BONE_HI if y == 0 else BONE if y == 1 else BONE_DK, 255)
                glow[y, x] = 0
        for end in (0, w - 1):
            for y in range(0, 4):
                img[y, end] = np.append(BONE, 255)
        for f in range(2):
            x0 = int(w * (0.3 + 0.45 * f))
            for y in range(3, int(h * (0.7 if f else 0.55))):
                x = x0 + int((y - 3) * 0.15 * (1 if f else -1))
                if 0 <= x < w:
                    img[y, x] = np.append(BONE if y % 5 else BONE_DK, 255)
                    glow[y, x] = 0
        return np.clip(img, 0, 255).astype(np.uint8), glow.astype(np.uint8)
    return paint


def repaint_wings(bones):
    for b in bones:
        n = b['name']
        if not n.startswith('wing_'):
            continue
        seg = 0 if n in ('wing_r', 'wing_l') else int(n[-1]) - 1
        flip = n.startswith('wing_r')
        for c in b.get('cubes', []):
            for face, f in c['uv'].items():
                x, y = (int(v) for v in f['uv'])
                w, h = (int(math.ceil(v)) for v in f['uv_size'])
                img, glow = bone_wing(seg, 4)(face, w, h)
                if flip:
                    img, glow = img[:, ::-1], glow[:, ::-1]
                S.img[y:y + h, x:x + w] = img
                S.glow[y:y + h, x:x + w] = glow


# ===================================================================== the hat and the hammer

def hat_paint(band=False):
    def paint(face, w, h):
        rgb = np.ones((h, w, 3)) * HAT
        rgb[0, :] = HAT * 1.8
        if band and face not in ('up', 'down'):
            rgb[h - 3:h - 1, :] = MARIGOLD
            rgb[h - 3:h - 1, ::3] = PINK
        return rgba(rgb)
    return paint


def flower(face, w, h):
    rgb = np.ones((h, w, 3)) * MARIGOLD
    rgb[::2, ::2] = MARIGOLD_HI
    rgb[h // 2, w // 2] = PINK
    return rgba(rgb)


def hat():
    """A Catrin's top hat on the top of the potato, tipped forward a touch."""
    return {'name': 'catrin_hat', 'parent': 'body', 'pivot': [0, 24, 0], 'rotation': [-6, 0, 4], 'cubes': [
        cube([-8.5, 24, -8.5], [17, 1, 17], hat_paint()),                  # the brim
        cube([-5, 25, -5], [10, 10, 10], hat_paint(band=True)),            # the crown, banded
        cube([3.5, 26, -6], [3, 3, 3], flower),                            # marigolds on the band
        cube([1, 25.5, -6.2], [2.5, 2.5, 2.5], flower),
        cube([5.6, 28, -2], [0.6, 7, 1.5], solid(PINK, 0.08)),             # a feather up the side
    ]}


def femur(face, w, h):
    rgb = np.ones((h, w, 3)) * BONE
    rgb[:, 0] = BONE_HI
    rgb[:, -1] = BONE_DK
    return rgba(rgb)


def head_face(face, w, h):
    """The hammer's head: sugar-skull bone, a marigold band round its middle; on each striking end a little calavera."""
    rgb = np.ones((h, w, 3)) * BONE
    if face in ('north', 'south'):
        cx, cy = (w - 1) / 2, (h - 1) / 2
        for y in range(h):
            for x in range(w):
                for sx in (-1.8, 1.8):
                    if math.hypot(x - cx - sx, y - cy + 0.8) < 1.3:
                        rgb[y, x] = INK
                if abs(x - cx) < 0.6 and abs(y - cy - 1.0) < 0.6:
                    rgb[y, x] = INK
                if y == h - 2 and 1 <= x < w - 1:
                    rgb[y, x] = INK if x % 2 else BONE_DK
        glow = np.zeros((h, w, 4), np.uint8)
        for sx in (-1.8, 1.8):
            gx, gy = int(round(cx + sx)), int(round(cy - 0.8))
            rgb[gy, gx] = ECTO_HI
            glow[gy, gx] = np.append(ECTO_HI, 255)
        return rgba(rgb), glow
    mid = w // 2 if face in ('up', 'down') else w // 2
    if face in ('east', 'west', 'up', 'down'):
        rgb[:, max(0, mid - 1):mid + 1] = MARIGOLD
        rgb[::2, max(0, mid - 1):mid + 1] = PINK
    return rgba(rgb)


def aura(face, w, h):
    """The shell of blue light round the head: see-through, brighter at its edges, glowing."""
    img = np.zeros((h, w, 4))
    glow = np.zeros((h, w, 4))
    for y in range(h):
        for x in range(w):
            edge = x in (0, w - 1) or y in (0, h - 1)
            img[y, x] = np.append(ECTO_HI if edge else ECTO, 170 if edge else 70)
            glow[y, x] = np.append(ECTO_HI if edge else ECTO, 255 if edge else 120)
    return img.astype(np.uint8), glow.astype(np.uint8)


def hammer():
    """In the right fist (props hang off the fist down the arm's line, like the big spatula)."""
    return {'name': 'spirit_hammer', 'parent': 'hand_r', 'pivot': [-10, 4.5, 0], 'cubes': [
        cube([-10.6, 4.5, -0.6], [1.2, 2.0, 1.2], femur),            # the pommel, a knuckle of bone
        cube([-11.2, 6.2, -1.2], [2.4, 1.6, 2.4], femur),
        cube([-10.7, -16, -0.7], [1.4, 20.5, 1.4], femur),           # the femur
        cube([-11.4, -17.5, -1.4], [2.8, 2.2, 2.8], femur),          # its knob, into the head
        cube([-14.5, -27, -7.5], [9, 9.5, 15], head_face),           # the head: striking ends to the front and back
        cube([-15.3, -27.8, -8.3], [10.6, 11.1, 16.6], aura),       # the light round it
    ]}


# ===================================================================== the Final Guardian (2026-10-04)

def gold_paint(face, w, h):
    rgb = np.ones((h, w, 3)) * GOLD
    rgb[0, :] = GOLD_HI
    rgb[-1, :] = GOLD_DK
    rgb[:, ::4] = GOLD_HI * 0.95
    return rgba(rgb)


def band_paint(face, w, h):
    """The headdress's band: beaten gold, a row of jade studs."""
    rgb = np.ones((h, w, 3)) * GOLD
    rgb[0, :] = GOLD_HI
    rgb[-1, :] = GOLD_DK
    if face not in ('up', 'down') and h >= 2:
        rgb[h // 2, 1::3] = JADE_HI
    return rgba(rgb)


def medallion(face, w, h):
    """A sun disc of gold, a jade stone in its heart (glowing)."""
    rgb = np.ones((h, w, 3)) * GOLD
    glow = np.zeros((h, w, 4), np.uint8)
    cx, cy = (w - 1) / 2, (h - 1) / 2
    for y in range(h):
        for x in range(w):
            d = math.hypot(x - cx, y - cy)
            if face in ('north', 'south'):
                if d < 1.2:
                    rgb[y, x] = JADE_HI
                    glow[y, x] = np.append(ECTO, 255)
                elif d < 2.0:
                    rgb[y, x] = JADE
                elif abs(d - (min(w, h) / 2 - 0.6)) < 0.5:
                    rgb[y, x] = GOLD_HI
                elif (x + y) % 2 == 0:
                    rgb[y, x] = GOLD_DK * 1.1
    return rgba(rgb), glow


def feather_paint(colors):
    """A long feather, its quill down the middle: colours from the base (bottom) to the tip."""
    def paint(face, w, h):
        img = np.zeros((h, w, 4), np.uint8)
        for y in range(h):
            t = 1 - y / max(1, h - 1)            # 1 at the tip (top row)
            col = colors[min(len(colors) - 1, int(t * len(colors)))]
            half = (w / 2) * (0.45 + 0.55 * math.sin(math.pi * min(1, 0.1 + t)) ** 0.6)
            for x in range(w):
                dx = abs(x + 0.5 - w / 2)
                if dx > half + 0.01:
                    continue
                c = col * (0.85 + 0.15 * (1 - dx / max(0.5, half)))
                if w >= 3 and dx < 0.6:
                    c = GOLD_HI if t < 0.3 else c * 1.15
                img[y, x] = np.append(np.clip(c, 0, 255), 255)
        return img
    return paint


def penacho():
    """An Aztec feather headdress (a penacho, after Moctezuma's): a band of beaten gold round the top of the potato studded
    with jade, a sun disc at the front, and a great fan of quetzal feathers standing up behind, green with gold quills and
    red tips, an inner row of crimson and gold. Bones: penacho > penacho_f0.. (each its own rotation)."""
    bones = [{'name': 'penacho', 'parent': 'body', 'pivot': [0, 24, 0], 'cubes': [
        cube([-8.6, 22.2, -8.6], [17.2, 2.4, 0.6], band_paint),             # the band: front, back, sides
        cube([-8.6, 22.2, 8.0], [17.2, 2.4, 0.6], band_paint),
        cube([-8.6, 22.2, -8.0], [0.6, 2.4, 16], band_paint),
        cube([8.0, 22.2, -8.0], [0.6, 2.4, 16], band_paint),
        cube([-2.6, 23.0, -9.4], [5.2, 5.2, 0.8], medallion),               # the sun disc over his brow
    ]}]
    rows = [
        # (count, length, width, colours base->tip, tilt back, spread degrees, radius)
        (15, 40, 5, [GOLD, QUETZAL, QUETZAL_HI, QUETZAL, CRIMSON], 24, 210, 7.5),
        (11, 27, 4, [GOLD, CRIMSON, GOLD_HI, CRIMSON], 14, 170, 6.5),
    ]
    k = 0
    for ri, (count, length, width, colors, tilt, spread, rad) in enumerate(rows):
        for i in range(count):
            phi = math.radians(-spread / 2 + spread * i / (count - 1))       # 0 = straight behind him
            x, z = rad * math.sin(phi), rad * math.cos(phi)
            bones.append({'name': 'penacho_f%d' % k, 'parent': 'penacho', 'pivot': [round(x, 3), 24, round(z, 3)],
                          'rotation': [round(-tilt - 8 * abs(math.sin(phi)), 2), round(-math.degrees(phi), 2), 0],
                          'cubes': [cube([x - width / 2, 24, z], [width, length, 0], feather_paint(colors), share=('penacho', ri))]})
            k += 1
    # jade ear spools
    for sx in (-1, 1):
        bones[0]['cubes'].append(cube([8.0 * sx - (0.8 if sx < 0 else 0), 13.5, -1.6], [0.8, 3.2, 3.2], medallion))
    return bones


def scar_paint(face, w, h):
    rgb = np.ones((h, w, 3)) * BLOOD
    rgb[0, :] = BLOOD_HI
    return rgba(rgb)


def scar_glow(face, w, h):
    """In his truest form the old wound is a crack of the souls' light."""
    rgb = np.ones((h, w, 3)) * ECTO
    rgb[0, :] = ECTO_HI
    glow = np.zeros((h, w, 4), np.uint8)
    glow[:] = np.append(ECTO_HI, 255)
    return rgba(rgb), glow


def scar(glowing=False):
    """The scar on his chest (phase four: it tears open): a jagged gash slanting across the lower front of the potato, below
    the mouth, and the blood running down from it (scar_blood: its drips grow, scaled in Y from the top)."""
    pts = [(-7.2, 8.6), (-5.0, 7.4), (-3.4, 8.0), (-1.2, 6.6), (0.8, 7.0), (2.6, 5.8), (4.6, 6.1), (6.8, 4.9)]
    paint = scar_glow if glowing else scar_paint
    cubes = []
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        n = 4
        for j in range(n):
            x = x0 + (x1 - x0) * j / n
            y = y0 + (y1 - y0) * j / n
            cubes.append(cube([round(x - 0.35, 3), round(y - 0.45, 3), -8.28], [0.9, 0.9, 0.3], paint))
    bones = [{'name': 'scar', 'parent': 'body', 'pivot': [0, 7, -8], 'cubes': cubes}]
    if not glowing:
        drips = []
        for x, y, l in ((-5.0, 7.0, 2.6), (-1.2, 6.2, 2.0), (2.6, 5.4, 1.4), (4.8, 5.7, 1.1), (-3.4, 7.6, 1.6)):
            drips.append(cube([x - 0.3, y - l, -8.24], [0.6, l, 0.2], scar_paint))
        bones.append({'name': 'scar_blood', 'parent': 'scar', 'pivot': [0, 8, -8], 'cubes': drips})
    return bones


def wood(face, w, h):
    rgb = np.ones((h, w, 3)) * np.array([120, 70, 34])
    rgb[::3] = GOLD
    rgb[1::6] = JADE
    return rgba(rgb)


def string_paint(face, w, h):
    return rgba(np.ones((h, w, 3)) * np.array([236, 230, 206]))


def bow():
    """An Aztec war bow in his left fist (aztec_bow: shown by client/BossModel while spiritProp is 3), held across the arm's
    line: when the arm points at the moon the stave stands upright. Wrapped in gold and jade, its tips curled forward and
    tipped with feathers; the string straight (bow_string) or drawn back to his right hand (bow_drawn, keyed by p4_bow), and
    an arrow of the souls' light with an obsidian head (aztec_arrow, keyed by p4_bow)."""
    hx, hy = 10.0, 4.5
    out = [{'name': 'aztec_bow', 'parent': 'hand_l', 'pivot': [hx, hy, 0], 'cubes': [
        cube([hx - 0.7, 1.4, -1.6], [1.4, 2.4, 3.2], solid(CRIMSON, 0.06)),          # the grip, bound in red
    ]}]
    for sign, name in ((-1, 'u'), (1, 'l')):
        L = 12.5
        out.append({'name': 'bow_limb_' + name, 'parent': 'aztec_bow', 'pivot': [hx, 2.6, 1.5 * sign],
                    'rotation': [12 * sign, 0, 0],
                    'cubes': [cube([hx - 0.5, 2.1, 1.5 if sign > 0 else -1.5 - L], [1.0, 1.0, L], wood)]})
        tz = (1.5 + L * math.cos(math.radians(12))) * sign
        ty = 2.6 + L * math.sin(math.radians(12))
        out.append({'name': 'bow_tip_' + name, 'parent': 'bow_limb_' + name, 'pivot': [hx, ty, tz],
                    'rotation': [-20 if sign > 0 else 20, 0, 0],
                    'cubes': [cube([hx - 0.4, ty - 0.4, tz if sign > 0 else tz - 3.2], [0.8, 0.8, 3.2], solid(GOLD, 0.05)),
                              cube([hx - 0.05, ty - 0.2, tz + (2.4 if sign > 0 else -4.4)], [0.1, 3.6, 2.0],
                                   feather_paint([GOLD, QUETZAL_HI, CRIMSON]))]})
    tip_z, tip_y = 16.6, 4.6
    out.append({'name': 'bow_string', 'parent': 'aztec_bow', 'pivot': [hx, tip_y, 0], 'cubes': [
        cube([hx - 0.12, tip_y - 0.12, -tip_z], [0.24, 0.24, 2 * tip_z], string_paint)]})
    draw = 9.5
    L = math.hypot(tip_z, draw)
    a = math.degrees(math.atan2(draw, tip_z))
    out.append({'name': 'bow_drawn', 'parent': 'aztec_bow', 'pivot': [hx, tip_y, 0], 'cubes': []})
    out.append({'name': 'bow_drawn_u', 'parent': 'bow_drawn', 'pivot': [hx, tip_y, -tip_z], 'rotation': [a, 0, 0],
                'cubes': [cube([hx - 0.12, tip_y - 0.12, -tip_z], [0.24, 0.24, L], string_paint)]})
    out.append({'name': 'bow_drawn_l', 'parent': 'bow_drawn', 'pivot': [hx, tip_y, tip_z], 'rotation': [-a, 0, 0],
                'cubes': [cube([hx - 0.12, tip_y - 0.12, tip_z - L], [0.24, 0.24, L], string_paint)]})
    ax = hx + 0.9
    top = tip_y + draw
    out.append({'name': 'aztec_arrow', 'parent': 'aztec_bow', 'pivot': [ax, top, 0], 'cubes': [
        cube([ax - 0.25, -4.0, -0.25], [0.5, top + 4.0, 0.5], scar_glow),                    # the shaft: light
        cube([ax - 0.6, -6.6, -0.6], [1.2, 2.6, 1.2], solid(OBSIDIAN, 0.05)),                # the obsidian head
        cube([ax - 0.3, -7.6, -0.3], [0.6, 1.0, 0.6], solid(OBSIDIAN, 0.05)),
        cube([ax, top - 3.2, -1.0], [0.0, 2.8, 2.0], feather_paint([CRIMSON, QUETZAL_HI])),  # the fletching
        cube([ax - 1.0, top - 3.2, 0.0], [2.0, 2.8, 0.0], feather_paint([CRIMSON, QUETZAL_HI])),
    ]})
    return out


def give_ascended_scar(bones):
    """The scar tears open while he is still Ascended (P4_FALL on): papon_ascended gets the bleeding scar bones, their pixels
    copied to the same place on its sheet (rows 384 on). Pinned to 0 in every animation; client/BossModel shows it."""
    geo_path = os.path.join(ASSETS, 'geo/entity/papon_ascended.geo.json')
    geo = json.load(open(geo_path))
    g = geo['minecraft:geometry'][0]
    names = {b['name'] for b in bones}
    g['bones'] = [b for b in g['bones'] if b['name'] not in names] + [copy.deepcopy(b) for b in bones]
    with open(geo_path, 'w', newline='\n') as f:
        json.dump(geo, f, separators=(',', ':'))


# ===================================================================== the model

def build():
    global S
    S = Sheet()
    g = json.load(open(os.path.join(ASSETS, 'geo/entity/papon_ascended.geo.json')))['minecraft:geometry'][0]
    bones = copy.deepcopy(g['bones'])
    by = {b['name']: b for b in bones}
    # the eagle wings' glow goes (their rims), the bone takes over the potato
    skin_rects = rect_list(by['body']['cubes'])
    for n in ('leg_r', 'leg_l', 'arm_r_mesh', 'arm_l_mesh'):
        skin_rects += rect_list(by[n].get('cubes', []))
    bone_over(skin_rects)
    body = by['body']['cubes'][0]
    scrub(body)
    face_paint(body)
    back_paint(body)
    eyes(by)
    lips(by)
    flowers(rect_list(by['hair'].get('cubes', [])) + rect_list(by['wisps'].get('cubes', [])))
    repaint_wings(bones)
    bones.append(hammer())
    # the Final Guardian: the penacho, the bow, the scar burning green (the bleeding one goes on Ascended, drawn first)
    ascended_scar = scar(False)
    bones.extend(penacho())
    bones.extend(bow())
    bones.extend(scar(True))
    geo = {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': 'geometry.papon_muertos', 'texture_width': TEX, 'texture_height': TEX,
                        'visible_bounds_width': 9, 'visible_bounds_height': 6, 'visible_bounds_offset': [0, 1.5, 0]},
        'bones': bones}]}
    out = os.path.join(ASSETS, 'geo/entity/papon_muertos.geo.json')
    with open(out, 'w', newline='\n') as f:
        json.dump(geo, f, separators=(',', ':'))
    Image.fromarray(S.img, 'RGBA').save(os.path.join(ASSETS, 'textures/entity/papon_muertos.png'))
    Image.fromarray(S.glow, 'RGBA').save(os.path.join(ASSETS, 'textures/entity/papon_muertos_glowmask.png'))
    give_ascended_hammer(bones)
    give_ascended_scar(ascended_scar)
    return out


def give_ascended_hammer(bones):
    """The hammer forms in his hand while he is still Ascended (P4_HAMMER, P4_STRIKE: the skeleton comes later), so
    papon_ascended gets the same spirit_hammer bone, its pixels copied to the same place on its sheet (rows 384 on, empty
    there). Pinned to 0 in every animation (gen_boss_anims B.pins); client/BossModel shows it."""
    geo_path = os.path.join(ASSETS, 'geo/entity/papon_ascended.geo.json')
    geo = json.load(open(geo_path))
    g = geo['minecraft:geometry'][0]
    g['bones'] = [b for b in g['bones'] if b['name'] not in ('spirit_hammer', 'scar', 'scar_blood')] \
        + [copy.deepcopy(b) for b in bones if b['name'] == 'spirit_hammer']
    with open(geo_path, 'w', newline='\n') as f:
        json.dump(geo, f, separators=(',', ':'))
    for name, src in (('papon_ascended.png', S.img), ('papon_ascended_glowmask.png', S.glow)):
        path = os.path.join(ASSETS, 'textures/entity', name)
        im = np.asarray(Image.open(path).convert('RGBA')).copy()
        im[384:] = src[384:]
        Image.fromarray(im, 'RGBA').save(path)


if __name__ == '__main__':
    print(build())
