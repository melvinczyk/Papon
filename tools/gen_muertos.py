#!/usr/bin/env python3
"""Generates the art of phase four, Dia de los Muertos (the user's spec, 2026-10-04): El Coliseo de los Muertos and
everything in it that is new. Writes only its own files (so re-running it never re-encodes anything else).

  blocks    textures/block/muertos_*.png, bone_column_*, ectoplasm (animated), soul_void (animated), spirit_brazier,
            the candles; models/block, blockstates (registry/ModBlocks, boss/Coliseum)
  flag      textures/entity/potato_flag_muertos.png: La Bandera Papa of the dead (purple | black | marigold, the potato a
            calavera in a wreath of marigolds) (client/FlagCloth)
  sky       textures/environment/muertos_moon.png: the moon after his hammer's bolt, a blue calavera (client/MuertosSky);
            the moon before it is the potato moon's full phase (gen_sky.py)
  fx        textures/entity/papel_picado.png (the banners, client/PropRenderer), soul.png (the souls in the rift),
            papon_aura_dead.png (the blue aura round him)
  band      textures/entity/mariachi_muertos.png, arena_fan_muertos.png: the band and the stands as the dead

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
        json.dump(data, f, separators=(',', ':'))


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


# ===================================================================== the band and the crowd, turned

def turned(name):
    """mariachi.png / arena_fan.png as the dead (MariachiModel, ColiseumCrowdRenderer): every potato pixel bone (its light and
    shade kept, gen_muertos_papon.to_bone), the face a sugar skull with glowing blue eyes in its sockets; the band's gold
    botonadura and trim turned silver, a marigold band on their sombreros; the fans' straw hats and jerseys in the fiesta's
    colours. Same layout as the living texture (the same geo draws both)."""
    import types
    import numpy as np
    import gen_muertos_papon as gmp
    tex = os.path.join(ASSETS, 'textures/entity/%s.png' % name)
    img = np.asarray(Image.open(tex).convert('RGBA')).copy()
    gmp.S = types.SimpleNamespace(img=img, glow=np.zeros_like(img))
    g = json.load(open(os.path.join(ASSETS, 'geo/entity/%s.geo.json' % name)))['minecraft:geometry'][0]
    by = {b['name']: b for b in g['bones']}
    skin = []
    for n in ('body', 'leg_r', 'leg_l', 'arm_r', 'arm_l', 'arm_r_mesh', 'arm_l_mesh', 'eyelids', 'cheek_r', 'cheek_l'):
        if n in by:
            skin += gmp.rect_list(by[n].get('cubes', []))
    gmp.bone_over(skin)
    body = by['body']['cubes'][0]
    u0, v0 = (int(v) for v in body['uv'])
    w, h, d = (int(v) for v in body['size'])
    # the face wiped to bone first (the potato's painted eyes and mouth bar), then the skull painted on
    for y in range(v0 + d, v0 + d + h):
        for x in range(u0 + d, u0 + d + w):
            if img[y, x, 3]:
                img[y, x, :3] = gmp.to_bone(img[y, x]).astype(np.uint8)
    gmp.scrub(body)
    gmp.face_paint(body)
    gmp.back_paint(body)
    fx, fy = u0 + d, v0 + d
    for ex in (-4.5, 4.5):                      # the eyes: a blue light deep in each socket
        cx, cy = fx + (8 - ex), fy + (24 - 19.5)
        for yy in range(int(cy) - 1, int(cy) + 1):
            for xx in range(int(cx) - 1, int(cx) + 1):
                img[yy, xx, :3] = ECTO
        img[int(cy) - 1, int(cx) - 1, :3] = ECTO_HI
    for n in ('lip_up', 'lip_low', 'mouth_in'):
        if n in by:
            for (x0, y0, ww, hh) in gmp.rect_list(by[n].get('cubes', [])):
                img[y0:y0 + hh, x0:x0 + ww, :3] = INK
                if n != 'mouth_in':
                    img[y0:y0 + hh, x0:x0 + ww:2, :3] = BONE_DK
    if 'teeth' in by:
        for (x0, y0, ww, hh) in gmp.rect_list(by['teeth']['cubes']):
            img[y0:y0 + hh, x0:x0 + ww, :3] = BONE_HI
            img[y0:y0 + hh, x0:x0 + ww:2, :3] = BONE_DK

    def recolour(names, fn):
        for n in names:
            if n not in by:
                continue
            for (x0, y0, ww, hh) in gmp.rect_list(by[n].get('cubes', [])):
                for y in range(y0, min(img.shape[0], y0 + hh)):
                    for x in range(x0, min(img.shape[1], x0 + ww)):
                        if img[y, x, 3]:
                            img[y, x, :3] = fn(img[y, x], x, y)

    import colorsys

    def hsv(px):
        return colorsys.rgb_to_hsv(*(c / 255 for c in px[:3]))

    def silver(px, x, y):
        hh, ss, vv = hsv(px)
        if 0.08 <= hh <= 0.18 and ss > 0.35 and vv > 0.45:          # gold to silver, a glint of blue
            k = vv
            return (int(150 + 90 * k), int(160 + 88 * k), int(176 + 79 * k))
        if vv < 0.3:                                                  # the black cloth a touch purple
            return (int(px[0] * 0.9 + 6), int(px[1] * 0.8), int(px[2] * 0.9 + 12))
        return tuple(px[:3])

    if name == 'mariachi':
        recolour(('jacket', 'sleeve_r', 'sleeve_l', 'pant_r', 'pant_l', 'jacket_chair', 'jacket_floor', 'shirt', 'shades'), silver)
        hats = ('sombrero', 'sombrero_floor')
        recolour(hats, silver)
        for n in hats:                          # a marigold band round the crown, rosa in every third pixel
            cubes = by[n].get('cubes', [])
            if len(cubes) > 2:
                crown = max(cubes, key=lambda c: c['size'][1])
                (x0, y0, ww, hh) = gmp.rect_list([crown])[0]
                cd = int(math.ceil(crown['size'][2]))
                yb = y0 + cd + int(math.ceil(crown['size'][1])) - 2
                for x in range(x0, x0 + ww):
                    if 0 <= yb < img.shape[0] and img[yb, x, 3]:
                        img[yb, x, :3] = PINK if x % 3 == 0 else MARIGOLD
    else:
        def straw(px, x, y):
            hh, ss, vv = hsv(px)
            if hh < 0.05 or hh > 0.9:                                  # the red band: rosa mexicano
                return PINK
            if 0.2 < hh < 0.5:                                         # the green band: marigold
                return MARIGOLD
            k = vv
            return (int(30 + 40 * k), int(22 + 30 * k), int(40 + 46 * k))   # a black straw hat
        recolour(('sombrero',), straw)

        def jersey(px, x, y):
            hh, ss, vv = hsv(px)
            if ss < 0.2 and vv > 0.7:
                return BONE_HI
            return (int(PURPLE[0] * (0.6 + 0.6 * vv)), int(PURPLE[1] * (0.6 + 0.6 * vv)), int(PURPLE[2] * (0.6 + 0.5 * vv)))
        recolour(('jersey', 'cap'), jersey)
        recolour(('foam_hat',), lambda px, x, y: MARIGOLD_HI if (x + y) % 5 == 0 else MARIGOLD)
        recolour(('foam_finger',), lambda px, x, y: BONE_HI if hsv(px)[1] < 0.2 else PINK)
    return Image.fromarray(img, 'RGBA')


# ===================================================================== the sky

def dead_moon():
    """The moon after his hammer's bolt (client/MuertosSky, drawn over the black sky with alpha): a great calavera of cold blue
    bone, the flag's emblem without its wreath, cracks of light across it, the eyes burning ectoplasm."""
    n = 256
    im = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    rng = random.Random(1102)
    c = n / 2
    r = n * 0.47
    d.ellipse([c - r, c - r, c + r, c + r], fill=(176, 214, 236))
    px = im.load()
    for y in range(n):
        for x in range(n):
            if px[x, y][3] == 0:
                continue
            dx, dy = (x - c) / r, (y - c) / r
            rim = math.hypot(dx, dy)
            light = 1.0 - 0.35 * max(0, rim - 0.55) / 0.45 - 0.15 * (dx + dy) * 0.5
            f = light * (1 + rng.uniform(-0.04, 0.04))
            px[x, y] = (int(150 * f + 40), int(200 * f + 30), int(232 * f + 22), 255)
    # craters
    for _ in range(18):
        a, rr = rng.uniform(0, math.pi * 2), r * math.sqrt(rng.random()) * 0.85
        cx, cy, s = c + math.cos(a) * rr, c + math.sin(a) * rr, rng.uniform(4, 13)
        d.ellipse([cx - s, cy - s, cx + s, cy + s], fill=(120, 160, 196, 255))
        d.ellipse([cx - s * 0.7, cy - s * 0.7, cx + s * 0.85, cy + s * 0.85], fill=(140, 182, 214, 255))
    u = n / 34.0
    cx, cy = c, c + 0.6 * u
    for side in (-1, 1):                                # the sockets, ringed in petals of light
        ex = cx + side * 5.6 * u
        for k in range(12):
            a = k / 12 * math.pi * 2
            px_, py_ = ex + math.cos(a) * 4.6 * u, cy - 2.4 * u + math.sin(a) * 4.8 * u
            d.ellipse([px_ - 1.4 * u, py_ - 1.4 * u, px_ + 1.4 * u, py_ + 1.4 * u], fill=(PINK if k % 2 else MARIGOLD) + (255,))
        d.ellipse([ex - 3.7 * u, cy - 6.4 * u, ex + 3.7 * u, cy + 1.6 * u], fill=(10, 14, 34, 255))
        d.ellipse([ex - 1.6 * u, cy - 4.0 * u, ex + 1.6 * u, cy - 0.8 * u], fill=ECTO + (255,))
        d.ellipse([ex - 0.7 * u, cy - 3.4 * u, ex + 0.3 * u, cy - 2.4 * u], fill=ECTO_HI + (255,))
    d.polygon([(cx - 1.8 * u, cy + 3.0 * u), (cx + 1.8 * u, cy + 3.0 * u), (cx, cy + 5.8 * u)], fill=(10, 14, 34, 255))
    d.line([(cx - 8.0 * u, cy + 9.0 * u), (cx + 8.0 * u, cy + 9.0 * u)], fill=(10, 14, 34, 255), width=int(0.8 * u))
    for k in range(-6, 7):
        x = cx + k * 1.25 * u
        d.line([(x, cy + 7.6 * u), (x, cy + 10.4 * u)], fill=(10, 14, 34, 255), width=max(1, int(0.5 * u)))
    for k in range(6):                                  # the flower on its brow
        a = k / 6 * math.pi * 2
        fx_, fy_ = cx + math.cos(a) * 2.0 * u, cy - 11.0 * u + math.sin(a) * 2.0 * u
        d.ellipse([fx_ - 1.3 * u, fy_ - 1.3 * u, fx_ + 1.3 * u, fy_ + 1.3 * u], fill=PINK + (255,))
    d.ellipse([cx - 1.1 * u, cy - 12.1 * u, cx + 1.1 * u, cy - 9.9 * u], fill=MARIGOLD_HI + (255,))
    for side in (-1, 1):                                # swirls on the cheeks
        sx = cx + side * 10.5 * u
        d.arc([sx - 2.4 * u, cy + 2 * u, sx + 2.4 * u, cy + 6.8 * u], 0, 300, fill=PURPLE + (255,), width=int(0.7 * u))
    # the cracks the bolt left: lines of light
    for _ in range(5):
        a = rng.uniform(0, math.pi * 2)
        x, y = c + math.cos(a) * r * 0.98, c + math.sin(a) * r * 0.98
        for _ in range(7):
            a2 = math.atan2(c - y, c - x) + rng.uniform(-0.9, 0.9)
            nx, ny = x + math.cos(a2) * rng.uniform(6, 14), y + math.sin(a2) * rng.uniform(6, 14)
            d.line([(x, y), (nx, ny)], fill=ECTO_HI + (255,), width=2)
            x, y = nx, ny
    return im


# ===================================================================== the moves' things

def papel_picado():
    """Papel Picado's sheets (client/PropRenderer.drawPapel: four in a row, u = colour * 0.25): rosa, marigold, purple, green,
    a calavera and flowers cut out of each, a fringe of cut triangles at the bottom, the fold along the top for the string."""
    cw, ch = 48, 40
    im = Image.new('RGBA', (cw * 4, ch), (0, 0, 0, 0))
    cols = [(PINK, PINK_HI), (MARIGOLD, MARIGOLD_HI), (PURPLE, PURPLE_HI), ((40, 176, 90), (110, 220, 140))]
    for i, (col, hi) in enumerate(cols):
        sheet = Image.new('RGBA', (cw, ch), col + (255,))
        d = ImageDraw.Draw(sheet)
        d.rectangle([0, 0, cw - 1, 2], fill=hi + (255,))
        cut = (0, 0, 0, 0)
        # the bottom fringe
        for x in range(0, cw, 4):
            d.polygon([(x, ch), (x + 4, ch), (x + 2, ch - 4)], fill=cut)
        # a border of little holes
        for x in range(3, cw - 2, 4):
            d.rectangle([x, 5, x + 1, 6], fill=cut)
        for y in range(9, ch - 8, 4):
            d.rectangle([3, y, 4, y + 1], fill=cut)
            d.rectangle([cw - 5, y, cw - 4, y + 1], fill=cut)
        # the calavera in the middle (its skull cut out, its features left in paper)
        cx, cy = cw / 2, ch / 2 + 1
        d.ellipse([cx - 9, cy - 10, cx + 9, cy + 7], fill=cut)
        d.rectangle([cx - 5, cy + 4, cx + 5, cy + 10], fill=cut)
        for sx in (-4, 4):
            d.ellipse([cx + sx - 3, cy - 5, cx + sx + 3, cy + 1], fill=col + (255,))
        d.polygon([(cx - 1.5, cy + 3), (cx + 1.5, cy + 3), (cx, cy + 5)], fill=col + (255,))
        for k in range(-4, 5, 2):
            d.line([(cx + k, cy + 6), (cx + k, cy + 9)], fill=col + (255,), width=1)
        # flowers either side
        for fx in (8, cw - 9):
            for k in range(6):
                a = k / 6 * math.pi * 2
                x, y = fx + math.cos(a) * 3, cy + math.sin(a) * 3
                d.ellipse([x - 1.4, y - 1.4, x + 1.4, y + 1.4], fill=cut)
        im.alpha_composite(sheet, (i * cw, 0))
    return im


def soul():
    """A tortured soul out of the rift (client/PropRenderer.drawSoul: translucent, emissive): a long wailing face of blue
    light, hollow eyes, its mouth stretched open, arms reaching out, the body trailing away into wisps."""
    w, h = 48, 64
    big = 4
    im = Image.new('RGBA', (w * big, h * big), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    s = big
    # the trail: a tapering, wavering tail below
    for y in range(26 * s, h * s):
        k = (y - 26 * s) / ((h - 26) * s)
        half = (11 - 9 * k) * s
        off = math.sin(k * 7.0) * 4 * s
        a = int(200 * (1 - k) ** 1.4)
        d.line([(w * s / 2 + off - half, y), (w * s / 2 + off + half, y)], fill=(80, 210, 255, a))
    # the arms reaching out and up
    for side in (-1, 1):
        pts = [(w * s / 2 + side * 8 * s, 30 * s), (w * s / 2 + side * 18 * s, 22 * s), (w * s / 2 + side * 22 * s, 14 * s)]
        d.line(pts, fill=(120, 230, 255, 210), width=3 * s)
        for k in range(3):                                   # the fingers
            fx, fy = pts[-1]
            d.line([(fx, fy), (fx + side * (2 + k) * s, fy - (4 - k) * s)], fill=(160, 240, 255, 220), width=s)
    # the head
    d.ellipse([w * s / 2 - 11 * s, 4 * s, w * s / 2 + 11 * s, 34 * s], fill=(110, 226, 255, 215))
    d.ellipse([w * s / 2 - 9 * s, 6 * s, w * s / 2 + 9 * s, 30 * s], fill=(170, 244, 255, 225))
    for side in (-1, 1):                                     # hollow eyes, sagging
        d.ellipse([w * s / 2 + side * 4.5 * s - 3 * s, 11 * s, w * s / 2 + side * 4.5 * s + 3 * s, 19 * s], fill=(6, 30, 70, 240))
    d.ellipse([w * s / 2 - 4 * s, 20 * s, w * s / 2 + 4 * s, 31 * s], fill=(6, 30, 70, 245))   # the wail
    im = im.resize((w, h), Image.LANCZOS)
    return im


def guardian_aura():
    """The Final Guardian's aura (phase four, reworked: the souls he took burn jade green in him): papon_aura.png's flow in
    green, its light kept."""
    import numpy as np
    a = np.asarray(Image.open(os.path.join(ASSETS, 'textures/entity/papon_aura.png')).convert('RGBA')).astype(float)
    lum = (0.3 * a[..., 0] + 0.59 * a[..., 1] + 0.11 * a[..., 2]) / 255.0
    lum = lum / max(1e-6, lum.max())
    out = np.zeros_like(a)
    for i, (lo, hi) in enumerate(zip((8, 110, 50), (214, 255, 220))):
        out[..., i] = lo * (1 - lum) + hi * lum
    out *= np.clip(lum * 1.6, 0, 1)[..., None]
    out[..., 3] = a[..., 3]
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), 'RGBA')


def dead_aura():
    """The aura that swirls round him in phase four (client/BossRenderer): papon_aura.png's flow (gold, almost no blue: tinted
    blue it came out green) turned to the dead's ectoplasm, its light kept."""
    import numpy as np
    a = np.asarray(Image.open(os.path.join(ASSETS, 'textures/entity/papon_aura.png')).convert('RGBA')).astype(float)
    lum = (0.3 * a[..., 0] + 0.59 * a[..., 1] + 0.11 * a[..., 2]) / 255.0
    lum = lum / max(1e-6, lum.max())
    out = np.zeros_like(a)
    for i, (lo, hi) in enumerate(zip(ECTO_DK, ECTO_HI)):
        out[..., i] = lo * (1 - lum) + hi * lum
    out *= np.clip(lum * 1.6, 0, 1)[..., None]
    out[..., 3] = a[..., 3]
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), 'RGBA')


if __name__ == '__main__':
    what = set(sys.argv[1:]) or {'blocks', 'flag', 'sky', 'fx', 'band'}
    if 'blocks' in what:
        blocks()
    if 'flag' in what:
        save(flag(), 'textures/entity/potato_flag_muertos.png')
    if 'sky' in what:
        save(dead_moon(), 'textures/environment/muertos_moon.png')
    if 'fx' in what:
        save(papel_picado(), 'textures/entity/papel_picado.png')
        save(soul(), 'textures/entity/soul.png')
        save(dead_aura(), 'textures/entity/papon_aura_dead.png')
        save(guardian_aura(), 'textures/entity/papon_aura_guardian.png')
    if 'band' in what:
        save(turned('mariachi'), 'textures/entity/mariachi_muertos.png')
        save(turned('arena_fan'), 'textures/entity/arena_fan_muertos.png')
