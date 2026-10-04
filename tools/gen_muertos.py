#!/usr/bin/env python3
"""Generates the art of phase four, Dia de los Muertos (the user's spec, 2026-10-04): El Coliseo de los Muertos and
everything in it that is new. Writes only its own files (so re-running it never re-encodes anything else).

  blocks    textures/block/muertos_*.png, bone_column_*, ectoplasm (animated), soul_void (animated), spirit_brazier,
            the candles; models/block, blockstates (registry/ModBlocks, boss/Coliseum)
  flag      textures/entity/potato_flag_muertos.png: La Bandera Papa of the dead (purple | black | marigold, the potato a
            calavera in a wreath of marigolds) (client/FlagCloth)
  sky       textures/environment/muertos_moon.png: the moon after his hammer's bolt, a blue calavera (client/MuertosSky);
            the moon before it is the potato moon's full phase (gen_sky.py)
  fx        textures/entity/papel_picado.png (the banners, client/MuertosFx), soul.png (the souls in the rift),
            spirit_giant.png (El Gigante's hologram)

  python tools/gen_muertos.py [blocks] [flag] [sky] [fx]      (all of them with no arguments)

Colours are the fiesta's: cempasuchil marigold, rosa mexicano, a royal purple, bone, black, and the dead's blue: the
ectoplasm cyan everything glows with.
"""
import json
import math
import os
import random
import sys

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/papon')

MARIGOLD = (255, 150, 24)
MARIGOLD_HI = (255, 206, 60)
MARIGOLD_DK = (204, 92, 12)
PINK = (236, 46, 140)
PINK_HI = (255, 120, 190)
PURPLE = (122, 44, 186)
PURPLE_HI = (170, 96, 232)
BONE = (236, 228, 206)
BONE_DK = (196, 184, 156)
BONE_HI = (250, 246, 232)
INK = (20, 14, 26)
ECTO = (40, 226, 255)
ECTO_HI = (210, 255, 255)
ECTO_DK = (8, 120, 196)
NIGHT = (44, 36, 58)
NIGHT_DK = (26, 21, 36)
NIGHT_HI = (66, 56, 84)


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (255,)


def save(im, rel):
    path = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)
    print(path)


def write_json(rel, data):
    path = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', newline='\n') as f:
        json.dump(data, f, indent=2)


def cube_all(name, tex=None):
    write_json('models/block/%s.json' % name, {'parent': 'minecraft:block/cube_all', 'textures': {'all': 'papon:block/%s' % (tex or name)}})
    write_json('blockstates/%s.json' % name, {'variants': {'': {'model': 'papon:block/%s' % name}}})


GLOW = {'block_light': 15, 'sky_light': 15}


# ===================================================================== the blocks

def tile(rng):
    """The floor: night-stone, four tiles to a block, dark grout, a faint marigold petal cut in one corner of each."""
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            gx, gy = x % 8, y % 8
            c = NIGHT
            if gx == 0 or gy == 0:
                c = NIGHT_DK
            elif gx == 1 or gy == 1:
                c = NIGHT_HI
            im.putpixel((x, y), shade(c, 1 + rng.uniform(-0.05, 0.05)))
    for ox, oy in ((0, 0), (8, 8)):
        for dx, dy in ((4, 3), (3, 4), (5, 4), (4, 5), (4, 4)):
            im.putpixel((ox + dx, oy + dy), shade(MARIGOLD_DK, 0.55 if (dx, dy) != (4, 4) else 0.8))
    return im


def bricks(rng):
    """Dark bricks, staggered courses, bone-pale flecks in the mortar."""
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            course = y // 4
            off = 4 if course % 2 else 0
            mortar = y % 4 == 0 or (x + off) % 8 == 0
            c = (30, 25, 40) if mortar else (54, 45, 66)
            k = 1 + rng.uniform(-0.06, 0.06)
            if not mortar and y % 4 == 1:
                k *= 1.12
            im.putpixel((x, y), shade(c, k))
    for _ in range(4):
        x, y = rng.randrange(16), rng.randrange(0, 16, 4)
        im.putpixel((x, y), shade(BONE_DK, 0.7))
    return im


def stone(rng):
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            c = (70, 61, 84)
            if y == 0 or x == 0:
                c = (92, 82, 108)
            if y == 15 or x == 15:
                c = (48, 41, 60)
            im.putpixel((x, y), shade(c, 1 + rng.uniform(-0.04, 0.04)))
    return im


def dark(rng):
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), shade((20, 16, 28), 1 + rng.uniform(-0.1, 0.1)))
    return im


def bone(rng):
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), shade(BONE, 1 + rng.uniform(-0.03, 0.03) + 0.04 * math.sin((x + y * 0.4) * 0.8)))
    for _ in range(9):
        x, y = rng.randrange(16), rng.randrange(16)
        im.putpixel((x, y), shade(BONE_DK, 0.92))
    x, y = rng.randrange(3, 12), rng.randrange(3, 12)
    for k in range(4):                                   # a hairline crack
        im.putpixel((x + k, y + (k // 2)), shade(BONE_DK, 0.8))
    return im


def column_side(rng):
    """A spine stood on end: a vertebra every four pixels, rounded, the disc between them dark."""
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        ph = y % 4
        for x in range(16):
            u = (x - 7.5) / 8
            round_ = 1 - 0.35 * u * u
            c = BONE
            if ph == 0:
                c = (150, 136, 112) if 2 < x < 13 else (100, 90, 76)
            elif ph == 1:
                c = BONE_HI
            k = round_ * (1 + rng.uniform(-0.03, 0.03))
            if x in (0, 15):
                k *= 0.8
            im.putpixel((x, y), shade(c, k))
    return im


def column_end(rng):
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            c = BONE if r > 4.5 else (196, 160, 130) if r > 2.5 else (150, 70, 60)
            im.putpixel((x, y), shade(c, 1 + rng.uniform(-0.04, 0.04)))
    return im


def skull_face(rng):
    """A sugar skull's face: bone, black sockets ringed with marigold and pink petals, glowing eyes, a heart of a nose,
    a stitched grin, a flower on the brow."""
    im = bone(rng)
    px = im.load()
    for sx in (4.5, 11.5):
        for y in range(16):
            for x in range(16):
                d = math.hypot((x + 0.5 - sx) / 1.0, (y + 0.5 - 7.0) / 1.1)
                if d <= 2.4:
                    px[x, y] = INK + (255,)
                elif d <= 3.4:
                    a = math.atan2(y + 0.5 - 7, x + 0.5 - sx)
                    px[x, y] = (PINK if int((a + math.pi) / (2 * math.pi) * 8) % 2 else MARIGOLD) + (255,)
        px[int(sx), 7] = ECTO_HI + (255,)
        px[int(sx) - 1, 7] = ECTO + (255,)
    for x, y in ((7, 10), (8, 10), (7, 11), (8, 11)):
        px[x, y] = INK + (255,)
    for x in range(3, 13):
        px[x, 13] = INK + (255,)
        if x % 2 == 1:
            px[x, 12] = INK + (255,)
            px[x, 14] = INK + (255,)
    for x, y, c in ((8, 2, PINK), (7, 2, MARIGOLD), (9, 2, MARIGOLD), (8, 1, MARIGOLD), (8, 3, MARIGOLD), (8, 2, PINK_HI)):
        px[x, y] = c + (255,)
    return im


def skull_glow():
    """Only the eyes glow (an overlay drawn full bright)."""
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for sx in (4, 11):
        im.putpixel((sx, 7), ECTO_HI + (255,))
        im.putpixel((sx - 1, 7), ECTO + (255,))
    return im


def flowing(frames, base, hi, dk, seed, souls=False):
    """An animated strip: a liquid of light, two layers of caustics drifting at different speeds and angles, tiling
    seamlessly; souls=True adds faint faces drifting through it (the void)."""
    rng = random.Random(seed)
    im = Image.new('RGBA', (16, 16 * frames))
    ph = [rng.uniform(0, 6.28) for _ in range(6)]
    for f in range(frames):
        t = f / frames * math.pi * 2
        for y in range(16):
            for x in range(16):
                u, v = x / 16 * math.pi * 2, y / 16 * math.pi * 2
                a = math.sin(u + v + t + ph[0]) + math.sin(2 * u - v - t + ph[1]) * 0.6
                b = math.sin(u * 2 + v * 2 - 2 * t + ph[2]) * 0.5 + math.sin(3 * v - u + t + ph[3]) * 0.4
                k = (a + b) / 2.5
                if k > 0.55:
                    c = hi
                elif k > 0.05:
                    m = (k - 0.05) / 0.5
                    c = tuple(int(base[i] * (1 - m) + hi[i] * m * 0.6 + base[i] * m * 0.4) for i in range(3))
                elif k > -0.5:
                    c = base
                else:
                    c = dk
                im.putpixel((x, f * 16 + y), tuple(c) + (255,))
        if souls:
            # a face drifting up through the dark: two eyes and a wailing mouth
            for s in range(2):
                fy = int((s * 8 - f * 16 / frames * 1.0) % 16)
                fx = 4 + s * 7
                for dx, dy, c in ((0, 0, ECTO), (3, 0, ECTO), (1, 2, ECTO_DK), (2, 2, ECTO_DK), (1, 3, ECTO_DK), (2, 3, ECTO_DK)):
                    im.putpixel(((fx + dx) % 16, f * 16 + (fy + dy) % 16), c + (255,))
    return im


def marigold(rng):
    """Cempasuchil: a mass of ruffled orange flower heads, gold at their hearts."""
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), shade(MARIGOLD_DK, 1 + rng.uniform(-0.08, 0.05)))
    for _ in range(14):
        cx, cy, r = rng.randrange(16), rng.randrange(16), rng.choice((2, 2, 3))
        for dy in range(-r, r + 1):
            for dx in range(-r, r + 1):
                d = math.hypot(dx, dy)
                if d > r + 0.3:
                    continue
                c = MARIGOLD_HI if d < 0.8 else MARIGOLD if (dx + dy) % 2 else shade(MARIGOLD, 1.08)[:3]
                im.putpixel(((cx + dx) % 16, (cy + dy) % 16), tuple(c) + (255,))
    for _ in range(3):
        im.putpixel((rng.randrange(16), rng.randrange(16)), (60, 120, 40, 255))
    return im


def painted(rng, color, hi):
    """A painted stone in one fiesta colour, a lighter brushed swirl in it."""
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            k = 1 + rng.uniform(-0.04, 0.04)
            s = math.sin((x * 0.7 + y * 0.35)) + math.sin(y * 0.9 - x * 0.2)
            c = hi if s > 1.4 else color
            im.putpixel((x, y), shade(c, k))
    return im


SERAPE = [PINK, MARIGOLD, MARIGOLD_HI, (60, 190, 90), ECTO, PURPLE]


def seat_top(rng):
    """A serape folded on the step: stripes in every fiesta colour, thin black lines between, a fringe at the edge."""
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        band = SERAPE[(y // 2) % len(SERAPE)]
        for x in range(16):
            c = INK if y % 2 == 1 and (y // 2) % 3 == 2 else band
            im.putpixel((x, y), shade(c, (1.05 if (x % 2) ^ (y % 2) else 0.95) * (1 + rng.uniform(-0.03, 0.03))))
    for y in range(16):
        im.putpixel((0, y), shade(BONE, 1.0 if y % 2 else 0.85))
    return im


def iron(rng):
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), shade((62, 60, 70), 1 + rng.uniform(-0.08, 0.08)))
    for x in range(16):
        im.putpixel((x, 0), shade(BONE, 0.9))                   # a bone rim
    return im


def spirit_coals(rng):
    pal = (ECTO_HI, ECTO, ECTO_DK, (12, 44, 90))
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            k = rng.random()
            c = pal[0] if k < 0.15 else pal[1] if k < 0.5 else pal[2] if k < 0.8 else pal[3]
            im.putpixel((x, y), c + (255,))
    return im


def wax(rng):
    """Candle wax: cream, drips running down from the top, the wick black."""
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), shade((242, 230, 204), 1 + rng.uniform(-0.03, 0.03)))
    for _ in range(6):
        x = rng.randrange(16)
        for y in range(rng.randint(2, 7)):
            im.putpixel((x, y), shade((252, 246, 228), 1.0))
    im.putpixel((0, 0), INK + (255,))
    return im


def blocks():
    save(tile(random.Random(101)), 'textures/block/muertos_tile.png')
    cube_all('muertos_tile')
    save(bricks(random.Random(102)), 'textures/block/muertos_bricks.png')
    cube_all('muertos_bricks')
    save(stone(random.Random(103)), 'textures/block/muertos_stone.png')
    cube_all('muertos_stone')
    save(dark(random.Random(104)), 'textures/block/muertos_dark.png')
    cube_all('muertos_dark')
    save(bone(random.Random(105)), 'textures/block/muertos_bone.png')
    cube_all('muertos_bone')
    save(marigold(random.Random(106)), 'textures/block/muertos_marigold.png')
    cube_all('muertos_marigold')
    save(painted(random.Random(107), PINK, PINK_HI), 'textures/block/muertos_pink.png')
    cube_all('muertos_pink')
    save(painted(random.Random(108), PURPLE, PURPLE_HI), 'textures/block/muertos_purple.png')
    cube_all('muertos_purple')
    save(seat_top(random.Random(109)), 'textures/block/muertos_seat_top.png')
    write_json('models/block/muertos_seat.json', {'parent': 'minecraft:block/cube_bottom_top', 'textures': {
        'top': 'papon:block/muertos_seat_top', 'side': 'papon:block/muertos_stone', 'bottom': 'papon:block/muertos_stone'}})
    write_json('blockstates/muertos_seat.json', {'variants': {'': {'model': 'papon:block/muertos_seat'}}})
    # the spine columns
    save(column_side(random.Random(110)), 'textures/block/bone_column_side.png')
    save(column_end(random.Random(111)), 'textures/block/bone_column_end.png')
    write_json('models/block/bone_column.json', {'parent': 'minecraft:block/cube_column',
                                                 'textures': {'side': 'papon:block/bone_column_side', 'end': 'papon:block/bone_column_end'}})
    write_json('blockstates/bone_column.json', {'variants': {
        'axis=y': {'model': 'papon:block/bone_column'},
        'axis=x': {'model': 'papon:block/bone_column', 'x': 90, 'y': 90},
        'axis=z': {'model': 'papon:block/bone_column', 'x': 90}}})
    # the sugar skull: its face north in the model, turned by the blockstate; the eyes glow (a second, full-bright layer)
    save(skull_face(random.Random(112)), 'textures/block/muertos_skull_face.png')
    save(skull_glow(), 'textures/block/muertos_skull_glow.png')
    faces = {d: {'texture': '#bone', 'cullface': d} for d in ('down', 'up', 'south', 'west', 'east')}
    faces['north'] = {'texture': '#face', 'cullface': 'north'}
    write_json('models/block/muertos_skull.json', {
        'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout',
        'textures': {'particle': 'papon:block/muertos_bone', 'bone': 'papon:block/muertos_bone', 'face': 'papon:block/muertos_skull_face',
                     'glow': 'papon:block/muertos_skull_glow'},
        'elements': [{'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': faces},
                     {'from': [0, 0, -0.01], 'to': [16, 16, 0], 'faces': {'north': {'texture': '#glow', 'cullface': 'north', 'forge_data': GLOW}}}]})
    write_json('blockstates/muertos_skull.json', {'variants': {
        'facing=%s' % f: {'model': 'papon:block/muertos_skull', 'y': y} for f, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270))}})
    # the ectoplasm: two pixels below the tiles, swirling, full bright
    save(flowing(16, ECTO, ECTO_HI, ECTO_DK, 7), 'textures/block/ectoplasm.png')
    write_json('textures/block/ectoplasm.png.mcmeta', {'animation': {'frametime': 3, 'interpolate': True}})
    write_json('models/block/ectoplasm.json', {
        'parent': 'minecraft:block/block', 'ambientocclusion': False,
        'textures': {'particle': 'papon:block/ectoplasm', 'ecto': 'papon:block/ectoplasm'},
        'elements': [{'from': [0, 0, 0], 'to': [16, 14, 16], 'faces': {
            d: dict({'texture': '#ecto', 'forge_data': GLOW}, **({'cullface': d} if d != 'up' else {}))
            for d in ('down', 'up', 'north', 'south', 'west', 'east')}}]})
    write_json('blockstates/ectoplasm.json', {'variants': {'': {'model': 'papon:block/ectoplasm'}}})
    # the soul void: the dark under the floor, souls drifting in it
    save(flowing(20, (14, 8, 30), (60, 40, 120), (4, 2, 10), 9, souls=True), 'textures/block/soul_void.png')
    write_json('textures/block/soul_void.png.mcmeta', {'animation': {'frametime': 3, 'interpolate': True}})
    write_json('models/block/soul_void.json', {
        'parent': 'minecraft:block/block', 'ambientocclusion': False,
        'textures': {'particle': 'papon:block/soul_void', 'void': 'papon:block/soul_void'},
        'elements': [{'from': [0, 0, 0], 'to': [16, 15, 16], 'faces': {
            d: dict({'texture': '#void', 'forge_data': {'block_light': 9, 'sky_light': 0}}, **({'cullface': d} if d != 'up' else {}))
            for d in ('down', 'up', 'north', 'south', 'west', 'east')}}]})
    write_json('blockstates/soul_void.json', {'variants': {'': {'model': 'papon:block/soul_void'}}})
    # the spirit brazier: an iron bowl on a stem, a rim of bone, blue coals full bright
    save(iron(random.Random(113)), 'textures/block/spirit_brazier.png')
    save(spirit_coals(random.Random(114)), 'textures/block/spirit_coals.png')
    b, c = '#iron', '#coals'

    def box(fr, to, top=b):
        f = {d: {'texture': b} for d in ('north', 'south', 'east', 'west', 'down')}
        f['up'] = {'texture': top, 'forge_data': GLOW} if top == c else {'texture': top}
        return {'from': fr, 'to': to, 'faces': f}
    write_json('models/block/spirit_brazier.json', {
        'parent': 'minecraft:block/block', 'ambientocclusion': False,
        'textures': {'particle': 'papon:block/spirit_brazier', 'iron': 'papon:block/spirit_brazier', 'coals': 'papon:block/spirit_coals'},
        'elements': [box([4, 0, 4], [12, 1, 12]), box([7, 1, 7], [9, 7, 9]), box([3, 7, 3], [13, 9, 13]),
                     box([1, 9, 1], [15, 13, 15], top=c)]})
    write_json('blockstates/spirit_brazier.json', {'variants': {'': {'model': 'papon:block/spirit_brazier'}}})
    # the candles: five of them, tall and short, wicks black (the flames are particles: block/MuertosCandlesBlock)
    save(wax(random.Random(115)), 'textures/block/muertos_candle.png')

    def candle(x, z, h, w=2.0):
        f = {d: {'texture': '#wax', 'uv': [2, 0, 2 + w, h]} for d in ('north', 'south', 'east', 'west')}
        f['up'] = {'texture': '#wax', 'uv': [2, 2, 2 + w, 2 + w]}
        f['down'] = {'texture': '#wax', 'uv': [2, 2, 2 + w, 2 + w]}
        wick = {d: {'texture': '#wax', 'uv': [0, 0, 1, 1]} for d in ('north', 'south', 'east', 'west', 'up')}
        return [{'from': [x - w / 2, 0, z - w / 2], 'to': [x + w / 2, h, z + w / 2], 'faces': f},
                {'from': [x - 0.25, h, z - 0.25], 'to': [x + 0.25, h + 0.75, z + 0.25], 'faces': wick}]
    els = []
    for x, h, z in ((5, 11, 5), (11, 8, 6), (6, 7, 11), (11, 9.5, 11), (8.5, 5.5, 8.5)):
        els += candle(x, z, h)
    write_json('models/block/muertos_candles.json', {'parent': 'minecraft:block/block', 'ambientocclusion': False,
                                                     'textures': {'particle': 'papon:block/muertos_candle', 'wax': 'papon:block/muertos_candle'},
                                                     'elements': els})
    write_json('blockstates/muertos_candles.json', {'variants': {'': {'model': 'papon:block/muertos_candles'}}})


# ===================================================================== the flag

W, H, S = 120, 68, 8


def calavera_emblem(size):
    """The coat of arms of the dead: Papon's potato as a sugar skull (bone, black sockets ringed in petals, glowing
    eyes, a heart nose, a stitched grin), a crown of bone fries, inside a wreath of marigolds."""
    n = size * S
    im = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    c = n / 2
    u = n / 34.0

    def ell(cx, cy, rx, ry, fill, outline=None, width=0):
        d.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=fill, outline=outline, width=int(width))

    # the wreath: marigold heads all round, a few leaves
    r = 13.4 * u
    for i in range(22):
        a = math.radians(i * 360 / 22 + 8)
        x, y = c + r * math.sin(a), c + 1.0 * u + r * math.cos(a)
        if math.cos(a) > 0.93:
            continue                                   # the knot at the bottom
        ell(x, y, 2.0 * u, 2.0 * u, MARIGOLD_DK)
        ell(x, y, 1.5 * u, 1.5 * u, MARIGOLD)
        ell(x - 0.3 * u, y - 0.3 * u, 0.7 * u, 0.7 * u, MARIGOLD_HI)
    for side in (-1, 1):
        d.polygon([(c, c + 14.2 * u), (c + side * 5.0 * u, c + 16.8 * u), (c + side * 3.4 * u, c + 15.0 * u),
                   (c + side * 4.6 * u, c + 13.0 * u)], fill=PINK if side < 0 else PURPLE)
    ell(c, c + 14.2 * u, 1.6 * u, 1.3 * u, BONE)
    # the crown: bone fries
    for i, ang in enumerate((-42, -22, -6, 8, 24, 42)):
        L = (8.2 if i in (2, 3) else 7.0) * u
        a = math.radians(ang)
        bx, by = c + math.sin(a) * 6.0 * u, c - 6.0 * u
        tx, ty = bx + math.sin(a) * L, by - math.cos(a) * L
        w = 1.25 * u
        nx, ny = math.cos(a) * w, math.sin(a) * w
        d.polygon([(bx - nx, by - ny), (bx + nx, by + ny), (tx + nx, ty + ny), (tx - nx, ty - ny)], fill=BONE, outline=BONE_DK)
        ell(tx, ty, w * 1.1, w * 1.1, BONE_HI)
    # the skull potato
    cx, cy = c, c + 1.0 * u
    rx, ry = 9.6 * u, 10.6 * u
    pts = []
    for k in range(72):
        t = k / 72 * math.pi * 2
        lump = 1 + 0.035 * math.sin(3 * t + 0.6) + 0.025 * math.sin(5 * t + 2.1)
        pts.append((cx + rx * lump * math.cos(t), cy + ry * lump * math.sin(t)))
    d.polygon(pts, fill=BONE, outline=INK)
    d.line(pts + [pts[0]], fill=INK, width=int(0.9 * u))
    for side in (-1, 1):
        ex = cx + side * 3.8 * u
        for k in range(10):
            a = k / 10 * math.pi * 2
            ell(ex + math.cos(a) * 3.0 * u, cy - 1.8 * u + math.sin(a) * 3.2 * u, 1.0 * u, 1.0 * u, PINK if k % 2 else MARIGOLD)
        ell(ex, cy - 1.8 * u, 2.4 * u, 2.7 * u, INK)
        ell(ex, cy - 1.8 * u, 0.9 * u, 0.9 * u, ECTO)
        ell(ex - 0.3 * u, cy - 2.1 * u, 0.35 * u, 0.35 * u, ECTO_HI)
    d.polygon([(cx - 1.2 * u, cy + 1.6 * u), (cx + 1.2 * u, cy + 1.6 * u), (cx, cy + 3.4 * u)], fill=INK)
    d.line([(cx - 5.0 * u, cy + 5.4 * u), (cx + 5.0 * u, cy + 5.4 * u)], fill=INK, width=int(0.6 * u))
    for k in range(-4, 5):
        d.line([(cx + k * 1.1 * u, cy + 4.4 * u), (cx + k * 1.1 * u, cy + 6.4 * u)], fill=INK, width=int(0.45 * u))
    for k in range(6):
        a = k / 6 * math.pi * 2
        ell(cx + math.cos(a) * 1.4 * u, cy - 7.4 * u + math.sin(a) * 1.4 * u, 0.9 * u, 0.9 * u, PINK)
    ell(cx, cy - 7.4 * u, 0.8 * u, 0.8 * u, MARIGOLD_HI)
    return im.resize((size, size), Image.LANCZOS)


def flag():
    rng = random.Random(78)
    im = Image.new('RGBA', (W, H))
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 39, H], fill=PURPLE)
    d.rectangle([40, 0, 79, H], fill=INK)
    d.rectangle([80, 0, W, H], fill=MARIGOLD)
    # papel picado cut-outs along the top and bottom edges of the black
    for x in range(40, 80, 4):
        for y in (2, H - 4):
            d.polygon([(x + 1, y), (x + 3, y), (x + 2, y + 2)], fill=(64, 52, 80))
    e = calavera_emblem(40)
    im.alpha_composite(e, (40, (H - 40) // 2))
    px = im.load()
    for y in range(H):
        for x in range(W):
            r, g, b, a = px[x, y]
            f = 1 + rng.uniform(-0.025, 0.025) + (0.012 if (x + y) % 2 else -0.012)
            px[x, y] = (min(255, int(r * f)), min(255, int(g * f)), min(255, int(b * f)), a)
    for y in range(H):
        px[0, y] = shade(PURPLE, 0.8)
        px[W - 1, y] = shade(MARIGOLD, 0.85)
    return im


if __name__ == '__main__':
    what = set(sys.argv[1:]) or {'blocks', 'flag', 'sky', 'fx'}
    if 'blocks' in what:
        blocks()
    if 'flag' in what:
        save(flag(), 'textures/entity/potato_flag_muertos.png')
