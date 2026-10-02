#!/usr/bin/env python3
"""Generates the Wandering Papon model and texture.

Writes, under src/main/resources/assets/papon/:
  geo/entity/wandering_papon.geo.json
  textures/entity/wandering_papon.png

The body, arms, legs and brows are the Walking Crops Papon (wkcr:papon) bone for
bone, at the same pixel UVs, so he reads as the same potato. Everything else is
travelling kit: a straw hat, a backpack with a bedroll, and the props for his
grill routine (a grill, two tacos, a spatula, a salt shaker, and the coupon).

Props that only appear in some animations are hidden by keying their scale to 0;
gen_anims.py adds that key to every animation that does not use them.

Re-run after editing, then load tools/blockbench/wandering_papon.bbmodel (built
by bb_build.py) to look at it.
"""
import json
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/papon')
TEX_W = TEX_H = 256
random.seed(7)


def cube(origin, size, uv, inflate=0):
    c = {'origin': origin, 'size': size, 'uv': uv}
    if inflate:
        c['inflate'] = inflate
    return c


def bone(name, pivot, parent=None, cubes=(), rotation=None):
    b = {'name': name, 'pivot': pivot}
    if parent:
        b['parent'] = parent
    if rotation:
        b['rotation'] = rotation
    if cubes:
        b['cubes'] = list(cubes)
    return b


# ---------------------------------------------------------------- geometry
# Front is -Z (the face). Units are pixels, origin at the feet.
BONES = [
    bone('root', [0, 0, 0]),
    bone('torso', [0, 12, 0], 'root'),
    bone('body', [0, 4, 0], 'torso', [cube([-8, 4, -8], [16, 20, 16], [0, 0])]),
    bone('brow_r', [-5, 20.5, -8], 'body', [cube([-8, 20, -8.1], [6, 1, 1], [0, 46])]),
    bone('brow_l', [5, 20.5, -8], 'body', [cube([2, 20, -8.1], [6, 1, 1], [0, 48])]),
    bone('hat', [0, 24, 0], 'body', [
        cube([-9, 24, -9], [18, 1, 18], [0, 64]),
        cube([-5, 25, -5], [10, 4, 10], [72, 64]),
    ]),
    bone('backpack', [0, 14, 10], 'body', [
        cube([-6, 7, 8], [12, 12, 4], [64, 0]),
        cube([-6, 19, 8.5], [12, 3, 3], [64, 16]),
    ]),
    bone('arm_r', [-8, 11, 0], 'torso', [cube([-12, 4, -3], [4, 8, 6], [24, 36])]),
    bone('grill_hand', [-10, 3, 0], 'arm_r', [cube([-14, 0, -3], [8, 3, 6], [72, 84])]),
    bone('shaker', [-10, 3, 0], 'arm_r', [cube([-11, 0, -1], [2, 3, 2], [112, 84])]),
    bone('coupon', [-10, 3, 0], 'arm_r', [cube([-13, 0.5, -9], [6, 0.2, 8], [72, 104])]),
    bone('arm_l', [8, 11, 0], 'torso', [cube([8, 4, -3], [4, 8, 6], [44, 36])]),
    bone('spatula', [10, 4, 0], 'arm_l', [
        cube([9.5, -2, -0.5], [1, 6, 1], [100, 84]),
        cube([8.5, -6, -0.25], [3, 4, 0.5], [104, 96]),
    ]),
    bone('leg_r', [-4, 4, 0], 'root', [cube([-7, 0, -3], [6, 4, 6], [0, 36])]),
    bone('leg_l', [4, 4, 0], 'root', [cube([1, 0, -3], [6, 4, 6], [0, 36])]),
    # Ground props live outside root so sitting (which lowers root) leaves them put.
    bone('grill', [0, 0, -15], None, [
        cube([-5, 3, -19], [10, 3, 8], [0, 84]),
        cube([-5, 0, -19], [1, 3, 1], [36, 84]),
        cube([4, 0, -19], [1, 3, 1], [36, 84]),
        cube([-5, 0, -12], [1, 3, 1], [36, 84]),
        cube([4, 0, -12], [1, 3, 1], [36, 84]),
        cube([-4.5, 5.5, -18.5], [9, 0, 7], [40, 84]),
        cube([-5, 6.05, -19], [10, 0, 8], [0, 96]),
    ]),
    bone('taco_a', [-2.5, 6, -15], 'grill', [cube([-4.5, 6, -16], [4, 3, 2], [40, 96])]),
    bone('taco_b', [2.5, 6, -15], 'grill', [cube([0.5, 6, -16], [4, 3, 2], [40, 96])]),

    # --- eyelids: closed for sleep and blinks
    bone('eyelids', [0, 20.2, -8], 'body', [
        cube([-6.5, 18.7, -8.15], [4, 1.5, 0.1], [244, 32]),
        cube([2.5, 18.7, -8.15], [4, 1.5, 0.1], [244, 32]),
    ]),

    # --- llama raft. Water level is y=0 (the entity's feet while rafting); he sits on its back.
    bone('raft', [0, 0, 0], None, [cube([-9, -4, -10], [18, 7, 24], [128, 0])]),
    bone('llama_neck', [0, 2, -10], 'raft', [cube([-3, 1, -16], [6, 11, 6], [212, 0])]),
    bone('llama_head', [0, 12, -13], 'llama_neck', [
        cube([-3.5, 11, -17], [7, 6, 8], [128, 32]),
        cube([-2.5, 11, -21], [5, 4, 4], [160, 32]),
        cube([-3.5, 17, -12], [2, 3, 1], [180, 32]),
        cube([1.5, 17, -12], [2, 3, 1], [180, 32]),
    ]),
    bone('llama_leg_fr', [-9, -1, -6], 'raft', [cube([-17, -2.5, -7.5], [8, 3, 3], [188, 32])]),
    bone('llama_leg_fl', [9, -1, -6], 'raft', [cube([9, -2.5, -7.5], [8, 3, 3], [188, 32])]),
    bone('llama_leg_br', [-9, -1, 10], 'raft', [cube([-17, -2.5, 8.5], [8, 3, 3], [188, 32])]),
    bone('llama_leg_bl', [9, -1, 10], 'raft', [cube([9, -2.5, 8.5], [8, 3, 3], [188, 32])]),
    bone('llama_tail', [0, 1, 14], 'raft', [cube([-1.5, 0, 14], [3, 3, 4], [212, 32])]),
    bone('paddle_r', [-10, 4, 0], 'arm_r', [
        cube([-10.5, -6, -0.5], [1, 10, 1], [228, 32]),
        cube([-11.5, -10, -0.5], [3, 4, 1], [234, 32]),
    ]),
    bone('paddle_l', [10, 4, 0], 'arm_l', [
        cube([9.5, -6, -0.5], [1, 10, 1], [228, 32]),
        cube([8.5, -10, -0.5], [3, 4, 1], [234, 32]),
    ]),

    # --- Jarritos, one bone per flavour; the renderer hides all but the one he was given.
] + [
    bone('jarrito_' + flavour, [-10, 4, 0], 'arm_r', [
        cube([-11, 0, -1], [2, 3, 2], [192, 48]),
        cube([-11.5, -5, -1.5], [3, 5, 3], [128 + 16 * i, 48]),
        cube([-11, 3, -1], [2, 1, 2], [200, 48]),
    ])
    for i, flavour in enumerate(('red', 'orange', 'yellow', 'green'))
] + [
    # --- bedtime: pack set down beside him, sleeping bag laid out behind him
    bone('backpack_ground', [-17, 0, 6], None, [
        cube([-23, 0, 4], [12, 12, 4], [64, 0]),
    ]),
    bone('sleeping_bag', [0, 0, -2], None, [cube([-13, 0, -2], [26, 1, 30], [0, 128])]),
    bone('bag_cover', [-12.5, 1, 8], 'sleeping_bag', [cube([-12.5, 0.5, -1.5], [25, 17, 18], [0, 160])]),
]

PROP_BONES = ['grill', 'grill_hand', 'shaker', 'coupon', 'spatula', 'eyelids', 'raft', 'paddle_r', 'paddle_l',
              'jarrito_red', 'jarrito_orange', 'jarrito_yellow', 'jarrito_green', 'backpack_ground',
              'sleeping_bag']
JARRITOS = ['jarrito_red', 'jarrito_orange', 'jarrito_yellow', 'jarrito_green']


def write_geo():
    geo = {
        'format_version': '1.12.0',
        'minecraft:geometry': [{
            'description': {
                'identifier': 'geometry.wandering_papon',
                'texture_width': TEX_W,
                'texture_height': TEX_H,
                'visible_bounds_width': 4,
                'visible_bounds_height': 3.5,
                'visible_bounds_offset': [0, 1.25, 0],
            },
            'bones': BONES,
        }],
    }
    path = os.path.join(ASSETS, 'geo/entity/wandering_papon.geo.json')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(geo, f, indent='\t')
    return path


# ---------------------------------------------------------------- texture
def faces(u, v, w, h, d):
    """Box-UV face rectangles (x0, y0, x1, y1) for a cube of size w*h*d."""
    return {
        'up': (u + d, v, u + d + w, v + d),
        'down': (u + d + w, v, u + d + 2 * w, v + d),
        'east': (u, v + d, u + d, v + d + h),
        'north': (u + d, v + d, u + d + w, v + d + h),
        'west': (u + d + w, v + d, u + 2 * d + w, v + d + h),
        'south': (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    }


def shade(c, k):
    return tuple(max(0, min(255, int(ch * k))) for ch in c[:3]) + (255,)


def fill(px, rect, base, noise=0.08, spots=None):
    x0, y0, x1, y1 = (int(round(n)) for n in rect)
    for y in range(y0, max(y1, y0 + 1)):
        for x in range(x0, max(x1, x0 + 1)):
            k = 1 + random.uniform(-noise, noise)
            c = base
            if spots and random.random() < spots[1]:
                c = spots[0]
            px[x, y] = shade(c, k)


def paint(im):
    px = im.load()
    straw, straw_dk, band = (226, 196, 112), (184, 150, 72), (196, 52, 40)
    # hat brim 18x1x18 @ (0,64)
    for name, r in faces(0, 64, 18, 1, 18).items():
        fill(px, r, straw if name in ('up', 'down') else straw_dk, 0.12)
    x0, y0, x1, y1 = faces(0, 64, 18, 1, 18)['up']
    for y in range(y0, y1):  # woven rings on the brim top
        for x in range(x0, x1):
            ring = int(max(abs(x - (x0 + 8.5)), abs(y - (y0 + 8.5))))
            if ring % 2 == 0:
                px[x, y] = shade(straw, 0.9)
    # hat crown 10x4x10 @ (72,64): straw with a red band on the bottom row
    for name, r in faces(72, 64, 10, 4, 10).items():
        fill(px, r, straw, 0.12)
        if name not in ('up', 'down'):
            fill(px, (r[0], r[3] - 1, r[2], r[3]), band, 0.05)
    # backpack 12x12x4 @ (64,0): green canvas, darker flap, brass buckle
    canvas, canvas_dk = (92, 120, 64), (66, 90, 46)
    for name, r in faces(64, 0, 12, 12, 4).items():
        fill(px, r, canvas, 0.1)
    s = faces(64, 0, 12, 12, 4)['south']
    fill(px, (s[0], s[1], s[2], s[1] + 5), canvas_dk, 0.08)
    fill(px, (s[0] + 5, s[1] + 4, s[0] + 7, s[1] + 6), (214, 176, 64), 0.05)
    for name, r in faces(64, 0, 12, 12, 4).items():  # straps
        if name in ('south', 'up'):
            fill(px, (r[0] + 2, r[1], r[0] + 3, r[3]), (90, 60, 36), 0.05)
            fill(px, (r[2] - 3, r[1], r[2] - 2, r[3]), (90, 60, 36), 0.05)
    # bedroll 12x3x3 @ (64,16): red wool with ties
    for name, r in faces(64, 16, 12, 3, 3).items():
        fill(px, r, (168, 48, 44), 0.1)
        if name not in ('east', 'west'):
            fill(px, (r[0] + 2, r[1], r[0] + 3, r[3]), (70, 50, 30), 0.03)
            fill(px, (r[2] - 3, r[1], r[2] - 2, r[3]), (70, 50, 30), 0.03)
        else:
            fill(px, r, (196, 70, 60), 0.05)
            px[r[0] + 1, r[1] + 1] = (120, 30, 28, 255)
    # grill basin 10x3x8 @ (0,84): charcoal black metal, red stripe
    metal, metal_hi = (48, 48, 54), (88, 88, 98)
    for name, r in faces(0, 84, 10, 3, 8).items():
        fill(px, r, metal, 0.12)
        if name in ('north', 'south', 'east', 'west'):
            fill(px, (r[0], r[1], r[2], r[1] + 1), metal_hi, 0.06)
            fill(px, (r[0], r[1] + 1, r[2], r[1] + 2), (170, 40, 36), 0.06)
    # grill legs 1x3x1 @ (36,84)
    for r in faces(36, 84, 1, 3, 1).values():
        fill(px, r, (38, 38, 42), 0.1)
    # coals 9x0x7 @ (40,84): glowing embers
    for r in faces(40, 84, 9, 0, 7).values():
        fill(px, r, (60, 40, 36), 0.2, spots=((255, 120, 30), 0.35))
        x0, y0, x1, y1 = (int(n) for n in r)
        for y in range(y0, y1):
            for x in range(x0, x1):
                if random.random() < 0.12:
                    px[x, y] = (255, 210, 80, 255)
    # grate 10x0x8 @ (0,96): bars with transparent gaps
    g = faces(0, 96, 10, 0, 8)['up']
    for y in range(int(g[1]), int(g[3])):
        for x in range(int(g[0]), int(g[2])):
            edge = x in (g[0], g[2] - 1) or y in (g[1], g[3] - 1)
            if edge or (x - g[0]) % 2 == 1:
                px[x, y] = shade((130, 130, 140), 1 + random.uniform(-0.1, 0.1))
            else:
                px[x, y] = (0, 0, 0, 0)
    g2 = faces(0, 96, 10, 0, 8)['down']
    for y in range(int(g2[1]), int(g2[3])):
        for x in range(int(g2[0]), int(g2[2])):
            px[x, y] = px[int(g[0]) + (x - int(g2[0])), y]
    # taco 4x3x2 @ (40,96): toasted shell, filling on top
    shell = (238, 196, 92)
    for name, r in faces(40, 96, 4, 3, 2).items():
        fill(px, r, shell, 0.08, spots=((196, 140, 60), 0.18))
    t = faces(40, 96, 4, 3, 2)
    x0, y0, x1, y1 = (int(n) for n in t['up'])
    for x in range(x0, x1):  # lettuce, tomato, beef
        px[x, y0] = (92, 176, 60, 255) if x % 2 else (214, 54, 44, 255)
        if y1 - y0 > 1:
            px[x, y0 + 1] = (122, 72, 44, 255) if x % 2 else (92, 176, 60, 255)
    for side in ('north', 'south'):
        r = t[side]
        for x in range(int(r[0]), int(r[2])):
            px[x, int(r[1])] = (92, 176, 60, 255) if x % 2 else (214, 54, 44, 255)
    # grill folded in hand 8x3x6 @ (72,84)
    for name, r in faces(72, 84, 8, 3, 6).items():
        fill(px, r, metal, 0.12)
        if name in ('north', 'south'):
            fill(px, (r[0], r[1] + 1, r[2], r[1] + 2), (170, 40, 36), 0.06)
    # spatula handle 1x6x1 @ (100,84): wood; blade 3x4x0.5 @ (104,96): steel
    for name, r in faces(100, 84, 1, 6, 1).items():
        fill(px, r, (120, 78, 44), 0.1)
    fill(px, (104, 96, 112, 101), (196, 200, 210), 0.06)
    # shaker 2x3x2 @ (112,84): white with red cap
    for name, r in faces(112, 84, 2, 3, 2).items():
        fill(px, r, (236, 236, 236), 0.04)
        if name not in ('up', 'down'):
            fill(px, (r[0], r[1], r[2], r[1] + 1), (200, 50, 40), 0.04)
        elif name == 'up':
            fill(px, r, (200, 50, 40), 0.04)
    # coupon 4x0.2x5 @ (72,104): cream ticket, red border, taco stamp
    fill(px, (72, 104, 100, 113), (244, 230, 190), 0.03)
    for name, r in faces(72, 104, 6, 0.2, 8).items():
        if name in ('up', 'down'):
            x0, y0, x1, y1 = (int(round(n)) for n in r)
            for y in range(y0, y1):
                for x in range(x0, x1):
                    if x in (x0, x1 - 1) or y in (y0, y1 - 1):
                        px[x, y] = (196, 52, 40, 255)
            for dx, dy, c in ((2, 2, (238, 196, 92)), (3, 2, (238, 196, 92)), (2, 3, (92, 176, 60)),
                              (3, 3, (214, 54, 44)), (1, 5, (196, 52, 40)), (2, 5, (196, 52, 40)),
                              (3, 5, (196, 52, 40)), (4, 5, (196, 52, 40))):
                px[x0 + dx, y0 + dy] = c + (255,)


def paint_journey_kit(im):
    """Llama raft, paddles, Jarritos, eyelids, sleeping bag."""
    px = im.load()
    skin, lash = (206, 159, 71), (107, 82, 45)
    for r in faces(244, 32, 4, 1.5, 0.1).values():
        fill(px, r, skin, 0.03)
    fill(px, (244, 33, 252, 34), lash, 0.03)

    wool, wool_dk, wool_lt = (232, 220, 190), (196, 182, 150), (246, 238, 214)
    carpet = [(196, 52, 40), (240, 196, 60), (52, 110, 190), (80, 160, 70)]
    body = faces(128, 0, 18, 7, 24)
    for name, r in body.items():
        fill(px, r, wool, 0.07, spots=(wool_dk, 0.12))
    x0, y0, x1, y1 = (int(n) for n in body['up'])
    for y in range(y0 + 2, y1 - 2):  # trader-llama style carpet on its back
        for x in range(x0 + 1, x1 - 1):
            band = ((y - y0) // 2 + abs((x - x0) - 9) // 3) % 4
            px[x, y] = shade(carpet[band], 1 + random.uniform(-0.05, 0.05))
    for x in range(x0 + 1, x1 - 1):
        px[x, y0 + 1] = px[x, y1 - 2] = (240, 220, 120, 255)
    for name, r in faces(212, 0, 6, 11, 6).items():
        fill(px, r, wool, 0.07, spots=(wool_dk, 0.12))
    head = faces(128, 32, 7, 6, 8)
    for name, r in head.items():
        fill(px, r, wool_lt, 0.05)
    n = head['north']
    for ex in (1, 5):  # deadpan half-lidded eyes
        px[int(n[0]) + ex, int(n[1]) + 1] = wool_dk
        px[int(n[0]) + ex, int(n[1]) + 2] = (30, 24, 20, 255)
    for side in ('east', 'west'):
        r = head[side]
        px[int(r[0]) + 2, int(r[1]) + 1] = wool_dk
        px[int(r[0]) + 2, int(r[1]) + 2] = (30, 24, 20, 255)
    snout = faces(160, 32, 5, 4, 4)
    for name, r in snout.items():
        fill(px, r, (236, 226, 200), 0.04)
    n = snout['north']
    px[int(n[0]) + 1, int(n[1]) + 1] = px[int(n[0]) + 3, int(n[1]) + 1] = (90, 70, 60, 255)
    for x in range(int(n[0]) + 1, int(n[0]) + 4):
        px[x, int(n[1]) + 3] = (120, 96, 80, 255)
    for name, r in faces(180, 32, 2, 3, 1).items():
        fill(px, r, wool, 0.05)
    fill(px, faces(180, 32, 2, 3, 1)['north'], (230, 170, 170), 0.03)
    legs = faces(188, 32, 8, 3, 3)
    for name, r in legs.items():
        fill(px, r, wool, 0.07, spots=(wool_dk, 0.1))
    for end in ('east', 'west'):
        fill(px, legs[end], (70, 56, 46), 0.05)
    for r in faces(212, 32, 3, 3, 4).values():
        fill(px, r, wool_lt, 0.08, spots=(wool, 0.3))
    for r in faces(228, 32, 1, 10, 1).values():
        fill(px, r, (126, 84, 48), 0.08)
    for r in faces(234, 32, 3, 4, 1).values():
        fill(px, r, (176, 128, 76), 0.06)

    glass = (196, 228, 222)
    flavours = [(214, 36, 40), (250, 132, 20), (250, 214, 40), (84, 190, 60)]
    for i, liquid in enumerate(flavours):
        for name, r in faces(128 + 16 * i, 48, 3, 5, 3).items():
            fill(px, r, liquid, 0.06)
            if name not in ('up', 'down'):
                x0, y0, x1, y1 = (int(n) for n in r)
                for x in range(x0, x1):  # label band and the glass shoulder
                    px[x, y0] = shade(glass, 1.0)
                    px[x, y0 + 2] = (250, 250, 245, 255)
                    px[x, y0 + 3] = shade(liquid, 0.7)
                if name == 'north':
                    px[x0 + 1, y0 + 2] = (30, 30, 30, 255)
    for r in faces(192, 48, 2, 3, 2).values():
        fill(px, r, glass, 0.05)
    for r in faces(200, 48, 2, 1, 2).values():
        fill(px, r, (226, 196, 90), 0.05)

    pad = faces(0, 128, 26, 1, 30)
    for r in pad.values():
        fill(px, r, (60, 96, 160), 0.06)
    x0, y0, x1, y1 = (int(n) for n in pad['up'])
    for y in range(y0, y1, 4):
        for x in range(x0, x1):
            px[x, y] = (46, 76, 130, 255)
    cover = faces(0, 160, 25, 17, 18)
    red, red_dk = (210, 70, 46), (160, 46, 34)
    for name, r in cover.items():
        fill(px, r, red, 0.05)
        x0, y0, x1, y1 = (int(n) for n in r)
        for y in range(y0, y1):  # quilting
            for x in range(x0, x1):
                if (x - x0) % 5 == 0 or (y - y0) % 5 == 0:
                    px[x, y] = shade(red_dk, 1 + random.uniform(-0.04, 0.04))
    x0, y0, x1, y1 = (int(n) for n in cover['up'])
    for ty in range(y0 + 2, y1 - 2, 5):  # little tacos on the quilt
        for tx in range(x0 + 2, x1 - 2, 5):
            px[tx, ty] = px[tx + 1, ty] = (238, 190, 80, 255)
            px[tx, ty + 1] = (96, 178, 60, 255)
            px[tx + 1, ty + 1] = (226, 64, 50, 255)


def write_texture():
    base = Image.open(os.path.join(ROOT, 'tools/src/papon_base.png')).convert('RGBA')
    im = Image.new('RGBA', (TEX_W, TEX_H), (0, 0, 0, 0))
    im.paste(base, (0, 0))
    paint(im)
    paint_journey_kit(im)
    path = os.path.join(ASSETS, 'textures/entity/wandering_papon.png')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)
    return path


if __name__ == '__main__':
    print(write_geo())
    print(write_texture())
