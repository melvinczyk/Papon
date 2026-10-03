#!/usr/bin/env python3
"""Generates the blocks of Chef Papon's pocket dimension (registry/ModBlocks.java).

Writes, under src/main/resources/assets/papon/:
  textures/block/potato_skin.png    the dome: russet skin, speckles, a few shallow eyes
  textures/block/potato_flesh.png   the floor: pale starchy flesh, faint grain
  textures/block/potato_eye.png     the lights: skin with a glowing purple sprout eye
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


def skin(rng, eye=False):
    im = Image.new('RGBA', (16, 16))
    base = (150, 104, 58)
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), shade(base, 1 + rng.uniform(-0.08, 0.08)))
    for _ in range(26):                                   # dark speckles and lighter flecks
        x, y = rng.randrange(16), rng.randrange(16)
        im.putpixel((x, y), shade((96, 62, 32) if rng.random() < 0.6 else (186, 140, 86), 1 + rng.uniform(-0.05, 0.05)))
    for cx, cy in ((3, 4), (11, 11)) if not eye else ():  # shallow eyes: a dimple with a dark centre
        im.putpixel((cx, cy), (70, 44, 22, 255))
        im.putpixel((cx + 1, cy), (110, 74, 40, 255))
        im.putpixel((cx, cy + 1), (110, 74, 40, 255))
    if eye:
        for (x, y), c in {(7, 7): (255, 236, 255), (8, 7): (240, 180, 255), (7, 8): (220, 140, 255), (8, 8): (255, 210, 255),
                          (6, 7): (170, 90, 210), (9, 8): (170, 90, 210), (7, 6): (190, 110, 230), (8, 9): (190, 110, 230),
                          (6, 6): (120, 60, 140), (9, 9): (120, 60, 140), (9, 6): (130, 70, 160), (6, 9): (130, 70, 160),
                          (8, 5): (220, 160, 250), (9, 4): (240, 200, 255), (10, 3): (250, 230, 255)}.items():
            im.putpixel((x, y), c + (255,))
    return im


def flesh(rng):
    im = Image.new('RGBA', (16, 16))
    base = (238, 214, 140)
    for y in range(16):
        for x in range(16):
            k = 1 + rng.uniform(-0.04, 0.04) + (0.03 if (x + 2 * y) % 7 == 0 else 0)
            im.putpixel((x, y), shade(base, k))
    for _ in range(10):
        x, y = rng.randrange(16), rng.randrange(16)
        im.putpixel((x, y), shade((250, 232, 170), 1.0))
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
