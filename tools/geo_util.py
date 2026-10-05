"""Shared helpers for the Taco Truck crowd generators (gen_customers.py, gen_vendor.py).

Bedrock geometry in pixels (16 = 1 block), front = -Z, feet at y = 0. Box UV only, so
Blockbench opens every model in its default box-UV mode and the texture stays hand-editable.

Pixel-art faces are written as string grids, one character per pixel, row 0 at the top. A grid
is painted onto one face of a cube exactly as it reads when you look at that face from outside
the model (paint_grid un-mirrors the faces that box UV stores flipped).
"""
import json
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/papon')


def cube(origin, size, uv, inflate=0, mirror=False):
    c = {'origin': [float(v) for v in origin], 'size': [float(v) for v in size], 'uv': list(uv)}
    if inflate:
        c['inflate'] = inflate
    if mirror:
        c['mirror'] = True
    return c


def bone(name, pivot, parent=None, cubes=(), rotation=None):
    b = {'name': name, 'pivot': [float(v) for v in pivot]}
    if parent:
        b['parent'] = parent
    if rotation:
        b['rotation'] = rotation
    if cubes:
        b['cubes'] = list(cubes)
    return b


def faces(u, v, w, h, d):
    """Box-UV face rectangles (x0, y0, x1, y1) for a cube of size w*h*d at (u, v)."""
    return {
        'up': (u + d, v, u + d + w, v + d),
        'down': (u + d + w, v, u + d + 2 * w, v + d),
        'east': (u, v + d, u + d, v + d + h),
        'north': (u + d, v + d, u + d + w, v + d + h),
        'west': (u + d + w, v + d, u + 2 * d + w, v + d + h),
        'south': (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    }


def uv_span(w, h, d):
    """Texture footprint of a box-UV cube."""
    return 2 * (d + w), d + h


def shade(c, k):
    return tuple(max(0, min(255, int(ch * k))) for ch in c[:3]) + (255,)


class Painter:
    """Seeded noise fills plus grid stamping on a box-UV texture."""

    def __init__(self, w, h, seed):
        self.im = Image.new('RGBA', (w, h), (0, 0, 0, 0))
        self.px = self.im.load()
        self.rng = random.Random(seed)

    def fill(self, rect, base, noise=0.06, spots=None):
        x0, y0, x1, y1 = (int(round(n)) for n in rect)
        for y in range(y0, max(y1, y0 + 1)):
            for x in range(x0, max(x1, x0 + 1)):
                c = base
                if spots and self.rng.random() < spots[1]:
                    c = spots[0]
                self.px[x, y] = shade(c, 1 + self.rng.uniform(-noise, noise))

    def put(self, x, y, c, noise=0.0):
        self.px[x, y] = shade(c, 1 + self.rng.uniform(-noise, noise)) if noise else tuple(c[:3]) + (255,)

    def grid(self, rect, rows, palette, noise=0.04, flip=False):
        """Stamp a character grid into rect. '.' and ' ' leave the pixel alone."""
        x0, y0 = int(rect[0]), int(rect[1])
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch in '. ':
                    continue
                x = x0 + ((len(row) - 1 - i) if flip else i)
                self.put(x, y0 + j, palette[ch], noise)

    def save(self, path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        self.im.save(path)
        return path


class Packer:
    """Shelf packer that hands out box-UV origins, so cubes can be added without hand layout."""

    def __init__(self, width, height):
        self.w, self.h = width, height
        self.x = self.y = self.shelf = 0

    def take(self, w, h, d):
        sw, sh = uv_span(w, h, d)
        if sw > self.w:
            raise ValueError('too wide for this texture region: %dx%d' % (sw, sh))
        if self.x + sw > self.w:
            self.x, self.y, self.shelf = 0, self.y + self.shelf, 0
        if self.y + sh > self.h:
            raise ValueError('texture full: cannot fit %dx%d' % (sw, sh))
        uv = (self.x, self.y)
        self.x += sw
        self.shelf = max(self.shelf, sh)
        return uv


def write_geo(name, bones, tex_w, tex_h, bounds=(3, 3.5, 1.5)):
    geo = {
        'format_version': '1.12.0',
        'minecraft:geometry': [{
            'description': {
                'identifier': 'geometry.' + name,
                'texture_width': tex_w,
                'texture_height': tex_h,
                'visible_bounds_width': bounds[0],
                'visible_bounds_height': bounds[1],
                'visible_bounds_offset': [0, bounds[2], 0],
            },
            'bones': bones,
        }],
    }
    path = os.path.join(ASSETS, 'geo/entity/%s.geo.json' % name)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', newline='\n') as f:
        json.dump(geo, f, separators=(',', ':'))
    return path


SOUNDS = os.path.join(ASSETS, 'sounds.json')


def read_sounds():
    """assets/papon/sounds.json (every generator that adds sound events reads and writes it through these two)."""
    return json.load(open(SOUNDS, encoding='utf-8'))


def write_sounds(sounds):
    """sounds.json, one sound event to a line (it is merged by hand: a line per event keeps the merges readable)."""
    with open(SOUNDS, 'w', newline='\n', encoding='utf-8') as f:
        f.write('{\n' + ',\n'.join('  %s: %s' % (json.dumps(k, ensure_ascii=False), json.dumps(v, ensure_ascii=False, separators=(', ', ': ')))
                                     for k, v in sounds.items()) + '\n}\n')

