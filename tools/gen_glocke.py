"""papon:die_glocke: Die Glocke, the bell-shaped "wonder weapon" of the postwar legend (Witkowski, Cook): a bell of dark
bell bronze about three blocks high, iron bands and rivets, a flared iron flange it stands on, a hook eye on its crown, and
through a window band round its waist the two counter-rotating drums that held the violet "Xerum 525" (glowing slits,
brass vanes; the lower drum's slits vertical, the upper's slanted the other way, so the counter-rotation reads). A pressure
gauge on the front, a red valve wheel and a brass data plate either side, three heavy cables from collared sockets out over
the ground to junction boxes with violet lamps. No use yet: the user wanted the model (2026-10-07).

Round parts are 16-gons: 8 boxes through the axis, each turned 22.5 degrees on, showing only their two end faces (the
other faces are left out of the JSON, so GeckoLib draws nothing there) and the caps a step shows. So the faces are per-face
UV (not the crowd models' box UV); every face of a ring shares one small painted region (two variants, alternating round).

Bones: root > glocke (shell, struts, hook, fixtures; trembles while it runs) > drum_low / drum_high (spun in code,
client/DieGlockeRenderer); root > cable_0..2 (sockets, cables, junction boxes; stay on the ground).
Writes geo/entity/die_glocke.geo.json, textures/entity/die_glocke.png + _glowmask.png (slits, lamps), the idle animation
and the spawn egg's item model. Preview: python tools/geo_render.py die_glocke out.png
"""
import json
import math
import os
import random

from PIL import Image

from geo_util import ASSETS, write_geo, shade

TEX = 128
SIDES = 16

BRONZE, BRONZE_HI, BRONZE_LO = (96, 80, 60), (148, 126, 94), (62, 51, 38)
PATINA = (74, 112, 96)
IRON, IRON_HI, IRON_LO = (56, 58, 62), (98, 101, 106), (30, 31, 34)
RIVET = (156, 152, 140)
BRASS, BRASS_HI = (170, 134, 62), (214, 182, 102)
DRUM = (38, 36, 44)
RUBBER = (26, 26, 28)
VIOLET, VIOLET_HI = (168, 96, 255), (238, 216, 255)
RED = (140, 34, 30)
CREAM = (222, 214, 188)

img = Image.new('RGBA', (TEX, TEX), (0, 0, 0, 0))
glow = Image.new('RGBA', (TEX, TEX), (0, 0, 0, 0))
px, gx = img.load(), glow.load()
rng = random.Random(1945)


class Atlas:
    """Shelf packer for plain face regions."""

    def __init__(self, w, h):
        self.w, self.h, self.x, self.y, self.shelf = w, h, 0, 0, 0

    def take(self, w, h):
        if self.x + w > self.w:
            self.x, self.y, self.shelf = 0, self.y + self.shelf, 0
        if self.y + h > self.h:
            raise ValueError('texture full')
        uv = (self.x, self.y)
        self.x += w
        self.shelf = max(self.shelf, h)
        return uv


atlas = Atlas(TEX, TEX)


def put(x, y, c, noise=0.0, lit=False):
    px[x, y] = shade(c, 1 + rng.uniform(-noise, noise)) if noise else tuple(c[:3]) + (255,)
    if lit:
        gx[x, y] = px[x, y]


def region(w, h, paint, *args):
    """A w x h face region (sizes rounded to whole texels), painted; returns (u, v, w, h)."""
    w, h = max(1, int(round(w))), max(1, int(round(h)))
    u, v = atlas.take(w, h)
    for y in range(h):
        for x in range(w):
            put(u + x, v + y, (255, 0, 255))
    paint(u, v, w, h, *args)
    return u, v, w, h


def fill(u, v, w, h, c, noise=0.06):
    for y in range(h):
        for x in range(w):
            put(u + x, v + y, c, noise)


def rivet(u, v, w, h, x, y, under):
    if 0 <= x < w and 0 <= y < h:
        put(u + x, v + y, RIVET, 0.04)
        if y + 1 < h:
            put(u + x, v + y + 1, shade(under, 0.62))


def casing(u, v, w, h, var):
    """Cast bronze: lit towards the top, the band above's shadow on its first row, a rivet row top and bottom (on every
    other face: rivets on every face lined up like staves); variant 1 has a verdigris drip."""
    for y in range(h):
        k = 1.08 - 0.22 * y / max(1, h - 1)
        for x in range(w):
            put(u + x, v + y, shade(BRONZE, k), 0.05)
    for x in range(w):
        put(u + x, v, shade(BRONZE, 0.72), 0.04)
    if not var:
        rivet(u, v, w, h, w // 2, 1, BRONZE)
        if h > 4:
            rivet(u, v, w, h, w // 2, h - 2, BRONZE)
    elif h > 3:
        x = w // 2
        for y in range(1, min(h - 1, 7)):
            k = 0.85 - y / 9
            c = tuple(int(p * k + b * (1 - k)) for p, b in zip(PATINA, BRONZE))
            put(u + x, v + y, c, 0.05)


def dome(u, v, w, h, var, k):
    fill(u, v, w, h, shade(BRONZE, k), 0.05)
    if var and rng.random() < 0.3:
        put(u + rng.randrange(w), v + rng.randrange(h), shade(PATINA, k * 0.9), 0.06)


def band(u, v, w, h, var):
    fill(u, v, w, h, IRON, 0.05)
    for x in range(w):
        put(u + x, v, IRON_HI, 0.05)
        if h > 1:
            put(u + x, v + h - 1, IRON_LO, 0.05)
    if h > 1:
        rivet(u, v, w, h, w // 2 if not var else w // 2 - 1, 0 if h == 2 else h // 2, IRON)


def flange(u, v, w, h, var):
    fill(u, v, w, h, IRON, 0.05)
    for x in range(w):
        put(u + x, v, IRON_HI, 0.05)
    put(u + w // 2, v + h - 1, RIVET, 0.04)
    if var:
        put(u + 1, v + h - 1, shade((120, 70, 40), 0.9), 0.1)  # a rust bloom


def iron(u, v, w, h, *_):
    fill(u, v, w, h, IRON, 0.06)
    for x in range(w):
        put(u + x, v, IRON_HI, 0.05)


def flat(u, v, w, h, c):
    fill(u, v, w, h, c, 0)


def drum(u, v, w, h, kind):
    """The drum's dark steel and its slit of glowing Xerum: vertical on the lower drum, slanted on the upper."""
    fill(u, v, w, h, DRUM, 0.05)
    for y in range(h):
        x = w // 2 if kind == 'low' else (w - 1 - y) % w
        c = VIOLET_HI if 0 < y < h - 1 else VIOLET
        put(u + x, v + y, c, 0, lit=True)


def vane(u, v, w, h, *_):
    fill(u, v, w, h, BRASS, 0.06)
    for x in range(w):
        put(u + x, v, BRASS_HI, 0.04)


def strut(u, v, w, h, *_):
    fill(u, v, w, h, IRON, 0.05)
    for y in range(h):
        put(u, v + y, IRON_HI, 0.04)
    rivet(u, v, w, h, w // 2, 1, IRON)
    rivet(u, v, w, h, w // 2, h - 3, IRON)


def gauge(u, v, w, h, *_):
    fill(u, v, w, h, CREAM, 0.03)
    for i in range(w):
        put(u + i, v, IRON_LO)
        put(u + i, v + h - 1, IRON_LO)
        put(u, v + i, IRON_LO)
        put(u + w - 1, v + i, IRON_LO)
    put(u + 1, v + 2, RED)  # the needle, well into the red
    put(u + 2, v + 1, RED)


def plate(u, v, w, h, *_):
    fill(u, v, w, h, BRASS, 0.05)
    for x in range(1, w - 1):
        if rng.random() < 0.7:
            put(u + x, v + 1, shade(BRASS, 0.45))
        if rng.random() < 0.5 and h > 3:
            put(u + x, v + 2, shade(BRASS, 0.5))
    for x in (0, w - 1):
        for y in (0, h - 1):
            put(u + x, v + y, RIVET)


def valve(u, v, w, h, *_):
    fill(u, v, w, h, RED, 0.08)
    put(u + rng.randrange(w), v + rng.randrange(h), IRON_HI)


def lamp(u, v, w, h, *_):
    for y in range(h):
        for x in range(w):
            put(u + x, v + y, VIOLET_HI, 0, lit=True)


def uvface(r):
    return {'uv': [r[0], r[1]], 'uv_size': [r[2], r[3]]}


def rnd(vals):
    return [round(float(n), 4) for n in vals]


def box(cubes, origin, size, faces, rot=None, pivot=None):
    c = {'origin': rnd(origin), 'size': rnd(size), 'uv': {k: uvface(r) for k, r in faces.items()}}
    if rot and any(rot):
        c['rotation'] = rnd(rot)
        c['pivot'] = rnd(pivot)
    cubes.append(c)


def every(r):
    return {k: r for k in ('north', 'south', 'east', 'west', 'up', 'down')}


def side(r):
    """Width of a face of the 16-gon with apothem r."""
    return 2 * r * math.tan(math.pi / SIDES)


def ring(cubes, y0, h, r, sides, cap=None, top=False, bottom=False, offset=0.0):
    """A 16-gon slab: 8 boxes through the axis, only their end faces (and the caps asked for)."""
    s = side(r)
    for i in range(SIDES // 2):
        f = {'north': sides[i % 2], 'south': sides[i % 2]}
        if top:
            f['up'] = cap
        if bottom:
            f['down'] = cap
        box(cubes, (-s / 2, y0, -r), (s, h, 2 * r), f, (0, offset + i * 360 / SIDES, 0), (0, y0, 0))


def eye(cubes, cx, cy, R, t, depth, face):
    """A ring standing in the XY plane (the hook eye, the valve wheel): 8 boxes round (cx, cy)."""
    w = 2 * (R + t / 2) * math.tan(math.pi / 8)
    for i in range(8):
        box(cubes, (cx - w / 2, cy + R - t / 2, -depth / 2), (w, t, depth), every(face), (0, 0, i * 45), (cx, cy, 0))


# ---- the shell's profile, bottom up: (y0, height, apothem, paint, args) -------------------------------------------
WIN0, WIN1, DRUM_R = 16, 24, 10.6
PROFILE = [
    (0, 1, 14.0, flange, ()),
    (1, 1, 13.4, flange, ()),
    (2, 1, 12.8, band, ()),
    (3, 11, 12.0, casing, ()),
    (14, 2, 12.6, band, ()),
    # the window: WIN0..WIN1, the drums
    (WIN1, 2, 12.6, band, ()),
    (WIN1 + 2, 5, 11.8, casing, ()),
]
DOME0, DOME_H, DOME_R = WIN1 + 7, 13.5, 11.6
for _i in range(12):
    _t = (_i + 1) / DOME_H
    PROFILE.append((DOME0 + _i, 1, DOME_R * math.sqrt(max(0.0, 1 - _t * _t)) + 0.15, dome, (0.95 + 0.03 * _i,)))
PROFILE += [(DOME0 + 12, 1, 3.4, iron, ()), (DOME0 + 13, 0.6, 2.6, iron, ())]
TOP = DOME0 + 13.6


def build():
    cap_iron = region(2, 2, flat, IRON)
    cap_bronze = region(2, 2, flat, shade(BRONZE_HI, 0.95))
    cap_dark = region(2, 2, flat, IRON_LO)
    shell, low, high = [], [], []
    tops = {y0 + h: (y0, r) for y0, h, r, _, _ in PROFILE}
    for y0, h, r, paint, args in PROFILE:
        faces = [region(side(r), h, paint, var, *args) for var in (0, 1)]
        above = next((p for p in PROFILE if p[0] == y0 + h), None)
        below = tops.get(y0)
        top = above is None or above[2] < r
        bottom = y0 > 0 and (below is None or below[1] < r)
        cap = cap_bronze if paint is dome else cap_iron
        ring(shell, y0, h, r, faces, cap, top, bottom)

    # the drums, each its own bone, and the fixed ring between them
    for cubes, kind, y0 in ((low, 'low', WIN0), (high, 'high', WIN0 + 4)):
        faces = [region(side(DRUM_R), 4, drum, kind)] * 2
        ring(cubes, y0, 4, DRUM_R, faces)
        vf = region(2, 3, vane)
        for k in range(8):
            ang = 360 / SIDES / 2 + k * 45
            box(cubes, (-0.5, y0 + 0.4, -11.5), (1.0, 3.2, 1.3), every(vf), (0, ang, 0), (0, y0, 0))
    sep = [region(side(11.4), 1, band, 0)] * 2
    ring(shell, WIN0 + 3.5, 1, 11.4, sep, cap_dark, True, True)

    # struts across the window
    sf = region(2, 8, strut)
    for k in range(8):
        box(shell, (-0.7, WIN0, -12.9), (1.4, WIN1 - WIN0, 1.1),
            {'north': sf, 'south': sf, 'east': sf, 'west': sf}, (0, k * 45, 0), (0, WIN0, 0))

    # the crown: shackle and hook eye
    shack = region(3, 3, iron)
    box(shell, (-1.5, TOP, -1.5), (3, 2.5, 3), every(shack))
    box(shell, (-2.2, TOP - 0.2, -0.9), (4.4, 1.0, 1.8), every(cap_iron))
    eye(shell, 0, TOP + 5.9, 3.2, 1.6, 1.6, shack)

    # the gauge on the front
    gf, bez = region(4, 4, gauge), region(2, 2, flat, BRASS)
    box(shell, (-1.6, 8.4, -12.8), (3.2, 3.2, 0.8), {**every(bez), 'north': gf})
    box(shell, (-1.6, 8.4, -12.7), (3.2, 3.2, 0.7), every(bez), (0, 0, 45), (0, 10.0, 0))

    # the valve wheel, one face round to the right, and the data plate to the left (sub-bones turned about the axis)
    valve_c, plate_c = [], []
    red, stem = region(2, 2, valve), region(2, 2, flat, IRON_LO)
    box(valve_c, (-0.5, 5.5, -14.2), (1, 1, 2.3), every(stem))
    eye(valve_c, 0, 6.0, 2.0, 0.7, 0.7, red)
    for c in valve_c[1:]:
        c['origin'][2] -= 14.2 - 0.35
    box(valve_c, (-2.0, 5.65, -14.5), (4.0, 0.7, 0.5), every(red))
    box(valve_c, (-0.35, 4.0, -14.5), (0.7, 4.0, 0.5), every(red))
    box(valve_c, (-0.6, 5.4, -14.8), (1.2, 1.2, 0.8), every(stem))
    pf, brass = region(5, 3, plate), region(2, 2, flat, BRASS)
    box(plate_c, (-2.2, 4.6, -12.45), (4.4, 2.8, 0.5), {**every(brass), 'north': pf})

    # the cables: (yaw, ground run, kink yaw, kink run)
    rubber, collar, socket = region(2, 2, flat, RUBBER), region(2, 2, flat, BRASS), region(3, 3, iron)
    junction, lampf = region(4, 3, iron), region(1, 1, lamp)
    cables = []
    for yaw, run, kink, run2 in ((100, 6, 24, 7), (180, 9, -18, 5), (236, 4, -30, 8)):
        c = []
        box(c, (-1.5, 4.5, -13.6), (3, 3, 1.8), every(socket))
        box(c, (-1.9, 4.1, -14.4), (3.8, 3.8, 0.8), every(collar))
        pts = [(6.0, -14.4), (3.8, -16.4), (1.2, -18.8), (1.2, -18.8 - run)]
        for (ys, zs), (ye, ze) in zip(pts, pts[1:]):
            L = math.hypot(ys - ye, zs - ze)
            pitch = math.degrees(math.atan2(ys - ye, zs - ze))
            box(c, (-1.2, ys - 1.2, zs - L), (2.4, 2.4, L), every(rubber), (pitch, 0, 0), (0, ys, zs))
        for ys, zs in pts[1:3]:
            box(c, (-1.25, ys - 1.25, zs - 1.25), (2.5, 2.5, 2.5), every(rubber))
        zs = pts[-1][1]
        box(c, (-1.2, 0, zs - run2), (2.4, 2.4, run2), every(rubber), (0, kink, 0), (0, 1.2, zs))
        b = math.radians(kink)
        ex, ez = -run2 * math.sin(b), zs - run2 * math.cos(b)
        box(c, (ex - 1.8, 0, ez - 3.4), (3.6, 2.6, 3.6), every(junction), (0, kink, 0), (ex, 0, ez))
        box(c, (ex - 0.45, 2.6, ez - 1.95), (0.9, 0.7, 0.9), every(lampf), (0, kink, 0), (ex, 0, ez))
        cables.append((yaw, c))

    bones = [{'name': 'root', 'pivot': [0, 0, 0]},
             {'name': 'glocke', 'parent': 'root', 'pivot': [0, 0, 0], 'cubes': shell},
             {'name': 'drum_low', 'parent': 'glocke', 'pivot': [0, WIN0, 0], 'cubes': low},
             {'name': 'drum_high', 'parent': 'glocke', 'pivot': [0, WIN0 + 4, 0], 'cubes': high},
             {'name': 'valve', 'parent': 'glocke', 'pivot': [0, 0, 0], 'rotation': [0, -45, 0], 'cubes': valve_c},
             {'name': 'plate', 'parent': 'glocke', 'pivot': [0, 0, 0], 'rotation': [0, 45, 0], 'cubes': plate_c}]
    for i, (yaw, c) in enumerate(cables):
        bones.append({'name': 'cable_%d' % i, 'parent': 'root', 'pivot': [0, 0, 0], 'rotation': [0, yaw, 0], 'cubes': c})
    return bones


def main():
    bones = build()
    out = [write_geo('die_glocke', bones, TEX, TEX, bounds=(5, 4.5, 1.6))]
    tex = os.path.join(ASSETS, 'textures/entity')
    img.save(os.path.join(tex, 'die_glocke.png'))
    glow.save(os.path.join(tex, 'die_glocke_glowmask.png'))
    out += [os.path.join(tex, 'die_glocke.png'), os.path.join(tex, 'die_glocke_glowmask.png')]
    # the drums and the tremble are driven in code (client/DieGlockeRenderer): the animation only keeps the root keyed
    anim = {'format_version': '1.8.0', 'animations': {'animation.die_glocke.idle': {
        'loop': True, 'animation_length': 4.0, 'bones': {'root': {'position': {'0.0': {'vector': [0, 0, 0]}}}}}}}
    path = os.path.join(ASSETS, 'animations/entity/die_glocke.animation.json')
    with open(path, 'w', newline='\n') as f:
        json.dump(anim, f, separators=(',', ':'))
    out.append(path)
    path = os.path.join(ASSETS, 'models/item/die_glocke_spawn_egg.json')
    with open(path, 'w', newline='\n') as f:
        json.dump({'parent': 'minecraft:item/template_spawn_egg'}, f, separators=(',', ':'))
    out.append(path)
    for p in out:
        print(p)


if __name__ == '__main__':
    main()
