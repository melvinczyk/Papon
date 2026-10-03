#!/usr/bin/env python3
"""Generates the blocks of Chef Papon's arena, El Coliseo Papa (registry/ModBlocks.java, boss/Coliseum.java).

Writes, under src/main/resources/assets/papon/:
  textures/block/potato_skin.png    golden potato skin (the Papon's own), a few small eyes
  textures/block/potato_flesh.png   pale starchy flesh, faint grain
  textures/block/potato_eye.png     skin with a warm glowing eye and a pale sprout (a light)
  textures/block/arena_*.png        the coliseum: potato travertine, ashlar, fluted columns, raked sand,
                                    burlap-cushioned seats, golden potato, shadow, the tricolour drapes
                                    (the white one with a golden Papon), a bronze brazier
  blockstates/*.json, models/block/*.json
"""
import json
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/papon')


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c) + (255,)


# The palette of a Minecraft potato and of the Walking Crops Papon (vanilla potato item, papon_base.png)
SKIN, SKIN_MID, SKIN_HI, SKIN_DK, EYE = (217, 170, 81), (200, 151, 58), (233, 186, 98), (169, 119, 28), (157, 119, 46)


def skin(rng, eye=False):
    """Golden-tan skin in soft 2x2 blotches (the Papon's own look), two small eyes; the light block has
    a pale sprout curling out of a warm, glowing eye instead."""
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), shade(SKIN, 1 + rng.uniform(-0.015, 0.015)))
    for y in range(0, 16, 2):
        for x in range(0, 16, 2):
            k = rng.random()
            c = SKIN_MID if k < 0.25 else (SKIN_HI if k < 0.4 else None)
            if c:
                for dy in (0, 1):
                    for dx in (0, 1):
                        im.putpixel((x + dx, y + dy), shade(c, 1.0))
    if not eye:
        for cx, cy in ((3, 4), (11, 11)):
            im.putpixel((cx, cy), EYE + (255,))
            im.putpixel((cx + 1, cy), SKIN_DK + (255,))
    else:
        for (x, y), c in {(7, 7): (255, 244, 200), (8, 7): (255, 226, 150), (7, 8): (255, 226, 150), (8, 8): (255, 236, 176),
                          (6, 7): SKIN_DK, (9, 8): SKIN_DK, (7, 6): SKIN_DK, (8, 9): SKIN_DK,
                          (8, 6): (226, 232, 190), (9, 5): (214, 226, 170), (10, 4): (200, 220, 150), (10, 3): (176, 206, 120)}.items():
            im.putpixel((x, y), c + (255,))
    return im


def flesh(rng):
    im = Image.new('RGBA', (16, 16))
    base = (240, 205, 90)
    for y in range(16):
        for x in range(16):
            k = 1 + rng.uniform(-0.015, 0.015) + (0.025 if (x + 2 * y) % 7 == 0 else 0)
            im.putpixel((x, y), shade(base, k))
    for _ in range(10):
        x, y = rng.randrange(16), rng.randrange(16)
        im.putpixel((x, y), shade((248, 222, 134), 1.0))
    for x in range(16):                                   # the faint edge of the next slice
        im.putpixel((x, 15), shade(base, 0.9))
        im.putpixel((15, x), shade(base, 0.93))
    return im


def write(name, im):
    tex = os.path.join(ASSETS, 'textures/block/%s.png' % name)
    os.makedirs(os.path.dirname(tex), exist_ok=True)
    im.save(tex)
    for rel, data in (('blockstates/%s.json' % name, {'variants': {'': {'model': 'papon:block/%s' % name}}}),
                      ('models/block/%s.json' % name, {'parent': 'minecraft:block/cube_all',
                                                       'textures': {'all': 'papon:block/%s' % name}})):
        path = os.path.join(ASSETS, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, 'w', newline='\n') as f:
            json.dump(data, f, indent=2)
    print(tex)


# ===================================================================== El Coliseo Papa (boss/Coliseum.java)
#
# Roman travertine, but potato: a warm cream-gold stone, pitted in horizontal streaks like the real
# thing, with the odd potato eye in it. Everything the arena is built from is one of these.

TRAV = (222, 196, 142)
TRAV_DK = (196, 166, 110)
TRAV_HI = (236, 214, 166)
PIT = (150, 118, 70)


def noisy(rng, base, n=0.02):
    return shade(base, 1 + rng.uniform(-n, n))


def travertine(rng, pits=7, eye=True):
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        band = 1 + 0.03 * ((y * 7 + 3) % 5 - 2) / 2       # the layering runs across the block
        for x in range(16):
            im.putpixel((x, y), shade(TRAV, band * (1 + rng.uniform(-0.02, 0.02))))
    for _ in range(pits):                                  # pits: short horizontal streaks
        x, y, n = rng.randrange(14), rng.randrange(16), rng.randint(1, 3)
        for k in range(n):
            im.putpixel((x + k, y), shade(PIT, 1 + rng.uniform(-0.05, 0.05)))
        if y + 1 < 16:
            im.putpixel((x, y + 1), shade(TRAV_DK, 1.0))
    for _ in range(6):
        x, y = rng.randrange(16), rng.randrange(16)
        im.putpixel((x, y), shade(TRAV_HI, 1.0))
    if eye:
        x, y = rng.randrange(3, 13), rng.randrange(3, 13)
        im.putpixel((x, y), EYE + (255,))
        im.putpixel((x + 1, y), SKIN_DK + (255,))
    return im


def ashlar(rng):
    """Big dressed blocks, two courses to a block, the joints staggered."""
    im = travertine(rng, pits=5, eye=False)
    joint = shade(TRAV_DK, 0.88)
    for x in range(16):
        im.putpixel((x, 7), joint)
        im.putpixel((x, 15), joint)
    for y in range(0, 7):
        im.putpixel((10, y), joint)
    for y in range(8, 15):
        im.putpixel((3, y), joint)
    for x in range(16):                                    # a lighter top edge on each course
        im.putpixel((x, 0), shade(TRAV_HI, 1.0))
        im.putpixel((x, 8), shade(TRAV_HI, 0.98))
    return im


def column_side(rng):
    """Fluted: light ridges and shadowed flutes, top to bottom."""
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            k = (0.86, 0.95, 1.05, 1.08)[x % 4]
            im.putpixel((x, y), shade(TRAV, k * (1 + rng.uniform(-0.015, 0.015))))
    return im


def column_end(rng):
    im = travertine(rng, pits=2, eye=False)
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if 6.2 < d < 7.4:
                im.putpixel((x, y), shade(TRAV_DK, 0.95))
    return im


def sand(rng):
    """The arena floor (arena: Latin for sand): raked, with a few grains of potato."""
    im = Image.new('RGBA', (16, 16))
    base = (232, 204, 146)
    for y in range(16):
        for x in range(16):
            rake = 0.97 if (x + y) % 4 == 0 else 1.0
            im.putpixel((x, y), shade(base, rake * (1 + rng.uniform(-0.035, 0.035))))
    for _ in range(12):
        x, y = rng.randrange(16), rng.randrange(16)
        im.putpixel((x, y), shade((214, 180, 118), 1 + rng.uniform(-0.05, 0.05)))
    for _ in range(5):
        x, y = rng.randrange(16), rng.randrange(16)
        im.putpixel((x, y), shade((246, 226, 178), 1.0))
    return im


def seat_top(rng):
    """A seat: a burlap potato-sack cushion laid on the stone step, its stitched hem at the edges."""
    im = Image.new('RGBA', (16, 16))
    burlap = (176, 136, 82)
    for y in range(16):
        for x in range(16):
            weave = 1.06 if (x % 2) ^ (y % 2) else 0.94
            im.putpixel((x, y), shade(burlap, weave * (1 + rng.uniform(-0.03, 0.03))))
    for x in range(16):
        for y in (0, 15):
            im.putpixel((x, y), shade(TRAV, 1.0))
        if x % 3 == 0:
            im.putpixel((x, 2), shade((120, 88, 48), 1.0))
            im.putpixel((x, 13), shade((120, 88, 48), 1.0))
    for y in range(16):
        im.putpixel((0, y), shade(TRAV_DK, 1.0))
    return im


def gold(rng):
    """Golden potato: the statues and the trim. Polished, a few shallow eyes."""
    im = Image.new('RGBA', (16, 16))
    base, hi, dk = (238, 186, 56), (255, 230, 120), (190, 132, 30)
    for y in range(16):
        for x in range(16):
            sheen = 1 + 0.08 * max(0, 1 - abs((x + y) - 12) / 4)
            im.putpixel((x, y), shade(base, sheen * (1 + rng.uniform(-0.02, 0.02))))
    for x, y in ((3, 4), (11, 10), (6, 12)):
        im.putpixel((x, y), shade(dk, 1.0))
        im.putpixel((x + 1, y), shade(dk, 1.1))
    for x, y in ((9, 2), (10, 3), (2, 9)):
        im.putpixel((x, y), shade(hi, 1.0))
    return im


def dark(rng):
    """Shadow: the tunnels behind the gates and the inside of the arches."""
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), shade((58, 46, 34), 1 + rng.uniform(-0.06, 0.06)))
    return im


def drape(rng, color, emblem=False):
    """Hanging cloth in the flag's colours: vertical folds; the white one carries a golden Papon."""
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            fold = 0.9 + 0.12 * abs(((x + 2) % 6) - 3) / 3
            im.putpixel((x, y), shade(color, fold * (1 + rng.uniform(-0.015, 0.015))))
    if emblem:
        g, gd = (236, 184, 56), (176, 122, 30)
        for y in range(16):
            for x in range(16):
                if ((x - 7.5) / 5.2) ** 2 + ((y - 8.5) / 6.2) ** 2 <= 1:
                    im.putpixel((x, y), shade(g, 1 + rng.uniform(-0.03, 0.03)))
        for x, y in ((5, 6), (6, 6), (9, 6), (10, 6)):         # brows
            im.putpixel((x, y), shade(gd, 0.7))
        for x, y in ((5, 8), (10, 8)):                          # eyes
            im.putpixel((x, y), (40, 28, 18, 255))
        for x in range(6, 10):                                  # the mouth bar
            im.putpixel((x, 11), shade(gd, 0.8))
        for x, y in ((7, 1), (8, 1), (6, 2), (9, 2)):           # a little crown of fries
            im.putpixel((x, y), (250, 214, 90, 255))
    return im


def brazier(rng):
    """Bronze, hammered."""
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), shade((150, 96, 46), (1.08 if (x * 3 + y) % 7 == 0 else 1.0) * (1 + rng.uniform(-0.05, 0.05))))
    for x in range(16):
        im.putpixel((x, 0), shade((196, 140, 70), 1.0))
    return im


def coals(rng):
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            k = rng.random()
            c = (255, 210, 90) if k < 0.15 else (240, 120, 30) if k < 0.5 else (150, 40, 16) if k < 0.8 else (60, 22, 14)
            im.putpixel((x, y), c + (255,))
    return im


def write_json(rel, data):
    path = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', newline='\n') as f:
        json.dump(data, f, indent=2)


def write_tex(name, im):
    tex = os.path.join(ASSETS, 'textures/block/%s.png' % name)
    os.makedirs(os.path.dirname(tex), exist_ok=True)
    im.save(tex)
    print(tex)


def write_model(name, model):
    write_json('models/block/%s.json' % name, model)
    write_json('blockstates/%s.json' % name, {'variants': {'': {'model': 'papon:block/%s' % name}}})


def arena():
    write('arena_stone', travertine(random.Random(11)))
    write('arena_bricks', ashlar(random.Random(12)))
    write('arena_sand', sand(random.Random(13)))
    write('arena_gold', gold(random.Random(14)))
    write('arena_dark', dark(random.Random(15)))
    for i, (name, col) in enumerate((('green', (32, 122, 64)), ('white', (238, 234, 222)), ('red', (196, 40, 44)))):
        write('arena_drape_' + name, drape(random.Random(20 + i), col))
    write('arena_drape_emblem', drape(random.Random(23), (238, 234, 222), emblem=True))
    write_tex('arena_column_side', column_side(random.Random(16)))
    write_tex('arena_column_end', column_end(random.Random(17)))
    write_model('arena_column', {'parent': 'minecraft:block/cube_column',
                                 'textures': {'side': 'papon:block/arena_column_side', 'end': 'papon:block/arena_column_end'}})
    write_json('blockstates/arena_column.json', {'variants': {
        'axis=y': {'model': 'papon:block/arena_column'},
        'axis=x': {'model': 'papon:block/arena_column', 'x': 90, 'y': 90},
        'axis=z': {'model': 'papon:block/arena_column', 'x': 90}}})
    write_tex('arena_seat_top', seat_top(random.Random(18)))
    write_model('arena_seat', {'parent': 'minecraft:block/cube_bottom_top',
                               'textures': {'top': 'papon:block/arena_seat_top', 'side': 'papon:block/arena_stone',
                                            'bottom': 'papon:block/arena_stone'}})
    # a bronze bowl on a stem and a foot, full of glowing coals (the flames are particles)
    write_tex('arena_brazier', brazier(random.Random(19)))
    write_tex('arena_coals', coals(random.Random(24)))
    b, c = '#bronze', '#coals'

    def box(fr, to, top=b):
        return {'from': fr, 'to': to, 'faces': {
            'north': {'texture': b}, 'south': {'texture': b}, 'east': {'texture': b}, 'west': {'texture': b},
            'up': {'texture': top}, 'down': {'texture': b}}}
    write_model('arena_brazier', {
        'parent': 'minecraft:block/block', 'ambientocclusion': False,
        'textures': {'particle': 'papon:block/arena_brazier', 'bronze': 'papon:block/arena_brazier',
                     'coals': 'papon:block/arena_coals'},
        'elements': [box([4, 0, 4], [12, 1, 12]), box([7, 1, 7], [9, 7, 9]), box([3, 7, 3], [13, 9, 13]),
                     box([1, 9, 1], [15, 13, 15], top=c)]})
    # invisible: a model with nothing in it (the block only keeps players in the arena)
    write_json('models/block/arena_barrier.json', {'textures': {'particle': 'papon:block/arena_stone'}})
    write_json('blockstates/arena_barrier.json', {'variants': {'': {'model': 'papon:block/arena_barrier'}}})


if __name__ == '__main__':
    write('potato_skin', skin(random.Random(1)))
    write('potato_flesh', flesh(random.Random(2)))
    write('potato_eye', skin(random.Random(3), eye=True))
    arena()
