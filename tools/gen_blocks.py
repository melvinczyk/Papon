#!/usr/bin/env python3
"""Generates the blocks of Chef Papon's pocket dimension (registry/ModBlocks.java).

Writes, under src/main/resources/assets/papon/:
  textures/block/potato_skin.png    the dome: golden potato skin (the Papon's own), a few small eyes
  textures/block/potato_flesh.png   the floor: pale starchy flesh, faint grain
  textures/block/potato_eye.png     the lights: skin with a warm glowing eye and a pale sprout
  blockstates/*.json, models/block/*.json (cube_all)
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


if __name__ == '__main__':
    write('potato_skin', skin(random.Random(1)))
    write('potato_flesh', flesh(random.Random(2)))
    write('potato_eye', skin(random.Random(3), eye=True))
