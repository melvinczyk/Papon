#!/usr/bin/env python3
"""Generates the Papons of the Taco Truck: the one working the window, and the customers.

Writes, under src/main/resources/assets/papon/:
  geo/entity/taco_vendor.geo.json      textures/entity/taco_vendor.png
  geo/entity/customer_papon.geo.json   textures/entity/customer_papon.png

Both keep the Walking Crops Papon's body, arms, legs and brows bone for bone at the same UVs
(tools/src/papon_base.png pasted at 0,0), like the Wandering Papon, and share one animation
file (gen_npc_anims.py) with that rig's bone names.

taco_vendor   the Papon in the truck, and the boss to come. Chef's toque, big mustache,
              apron. Props: notepad + pencil (taking orders), spatula (cooking), a served
              taco, the fraud coupon and a magnifying glass. Rendered at 1.5x.
customer_papon a Papon in line. Hats sombrero / cap / none are variants: the renderer hides
              the hats a given customer is not wearing. Prop: his taco.
"""
import os

from PIL import Image

from geo_util import ASSETS, ROOT, Packer, Painter, bone, cube, faces, write_geo

TEX = 128
SIDE = {'right': 'east', 'front': 'north', 'left': 'west', 'back': 'south', 'top': 'up', 'bottom': 'down'}

VENDOR_PROPS = ['notepad', 'pencil', 'spatula', 'served_taco', 'fraud_coupon', 'magnifier']
CUSTOMER_PROPS = ['taco']
HATS = ['sombrero', 'cap']


def base_bones():
    """The wkcr Papon: same bones and UVs as gen_model.py, without the travelling kit."""
    return [
        bone('root', [0, 0, 0]),
        bone('torso', [0, 12, 0], 'root'),
        bone('body', [0, 4, 0], 'torso', [cube([-8, 4, -8], [16, 20, 16], [0, 0])]),
        bone('brow_r', [-5, 20.5, -8], 'body', [cube([-8, 20, -8.1], [6, 1, 1], [0, 46])]),
        bone('brow_l', [5, 20.5, -8], 'body', [cube([2, 20, -8.1], [6, 1, 1], [0, 48])]),
        bone('arm_r', [-8, 11, 0], 'torso', [cube([-12, 4, -3], [4, 8, 6], [24, 36])]),
        bone('arm_l', [8, 11, 0], 'torso', [cube([8, 4, -3], [4, 8, 6], [44, 36])]),
        bone('leg_r', [-4, 4, 0], 'root', [cube([-7, 0, -3], [6, 4, 6], [0, 36])]),
        bone('leg_l', [4, 4, 0], 'root', [cube([1, 0, -3], [6, 4, 6], [0, 36])]),
    ]


class Model:
    """Base Papon texture in the top-left 64x64; new parts packed into the free space around it."""

    def __init__(self, name, seed):
        self.name = name
        self.bones = base_bones()
        self.p = Painter(TEX, TEX, seed)
        base = Image.open(os.path.join(ROOT, 'tools/src/papon_base.png')).convert('RGBA')
        self.p.im.paste(base, (0, 0))
        self.packers = [Packer(64, 64), Packer(TEX, 64)]
        self.offsets = [(64, 0), (0, 64)]

    def box(self, origin, size, color, grids=None, noise=0.05, inflate=0, extra=None):
        w, h, d = size
        for packer, (ox, oy) in zip(self.packers, self.offsets):
            try:
                u, v = packer.take(w, h, d)
                break
            except ValueError:
                continue
        else:
            raise ValueError('%s: texture full' % self.name)
        u, v = u + ox, v + oy
        f = faces(u, v, w, h, d)
        for r in f.values():
            self.p.fill(r, color, noise)
        for side, (rows, pal) in (grids or {}).items():
            self.p.grid(f[SIDE[side]], rows, pal)
        if extra:
            extra(self.p, {k: f[v_] for k, v_ in SIDE.items()})
        return cube(origin, size, (u, v), inflate)

    def add(self, *bones):
        self.bones.extend(bones)

    def save(self):
        geo = write_geo(self.name, self.bones, TEX, TEX, bounds=(4, 4, 1.5))
        return geo, self.p.save(os.path.join(ASSETS, 'textures/entity/%s.png' % self.name))


def band(p, rect, row, color, noise=0.04):
    x0, y0, x1, _ = (int(n) for n in rect)
    p.fill((x0, y0 + row, x1, y0 + row + 1), color, noise)


def taco_cube(m, origin):
    def fillings(p, f):
        x0, y0, x1, y1 = (int(n) for n in f['top'])
        for x in range(x0, x1):
            for y in range(y0, y1):
                p.put(x, y, (92, 176, 60) if (x + y) % 2 else (214, 54, 44), 0.05)
        for s in ('front', 'back'):
            r = f[s]
            for x in range(int(r[0]), int(r[2])):
                p.put(x, int(r[1]), (92, 176, 60) if x % 2 else (214, 54, 44))
    return m.box(origin, [2, 3, 5], (238, 196, 92), extra=fillings, noise=0.08)


HAND_R = [-10, 4.5, 0]
HAND_L = [10, 4.5, 0]


def vendor():
    m = Model('taco_vendor', 31)
    white, white_dk = (246, 246, 240), (214, 214, 206)

    # chef's toque: a band and a big pleated puff
    def pleats(p, f):
        for s in ('front', 'back', 'left', 'right'):
            x0, y0, x1, y1 = (int(n) for n in f[s])
            for x in range(x0, x1):
                if (x - x0) % 3 == 0:
                    for y in range(y0, y1):
                        p.put(x, y, white_dk, 0.03)
    hat_band = m.box([-8.5, 24, -8.5], [17, 3, 17], white, noise=0.03,
                     extra=lambda p, f: [band(p, f[s], 2, white_dk) for s in ('front', 'back', 'left', 'right')])
    puff = m.box([-9.5, 27, -9.5], [19, 6, 19], white, noise=0.03, extra=pleats)
    m.add(bone('toque', [0, 24, 0], 'body', [hat_band, puff]))

    # a big black handlebar mustache, just above his mouth
    stache = (30, 22, 18)
    m.add(bone('mustache', [0, 12, -8], 'body', [
        m.box([-4, 12, -9], [8, 2, 1], stache, noise=0.08),
        m.box([-6, 11, -9], [2, 2, 1], stache, noise=0.08),
        m.box([4, 11, -9], [2, 2, 1], stache, noise=0.08),
        m.box([-7, 10, -9], [1, 2, 1], stache, noise=0.08),
        m.box([6, 10, -9], [1, 2, 1], stache, noise=0.08),
    ]))

    # apron: red with white trim and a pocket (low, so it never reads as a mouth under the mustache)
    def apron_paint(p, f):
        band(p, f['front'], 0, (244, 244, 236))
        x0, y0, x1, y1 = (int(n) for n in f['front'])
        p.fill((x0 + 4, y0 + 2, x1 - 4, y0 + 3), (150, 26, 26), 0.02)   # pocket
    m.add(bone('apron', [0, 8, -8], 'body', [m.box([-6, 4, -9], [12, 4, 1], (186, 34, 32), extra=apron_paint, noise=0.04)]))

    # right hand: pencil, magnifying glass, the served taco
    pencil = m.box([-10.5, 1, -6], [1, 1, 5], (240, 196, 60),
                   extra=lambda p, f: p.fill(f['front'], (40, 40, 40)))
    m.add(bone('pencil', HAND_R, 'arm_r', [pencil]))
    glass_ring = (60, 56, 52)
    m.add(bone('magnifier', HAND_R, 'arm_r', [
        m.box([-10.5, 0, -5], [1, 3, 1], (110, 70, 40)),             # handle
        m.box([-12.5, 3, -5], [5, 1, 1], glass_ring),
        m.box([-12.5, 7, -5], [5, 1, 1], glass_ring),
        m.box([-12.5, 4, -5], [1, 3, 1], glass_ring),
        m.box([-8.5, 4, -5], [1, 3, 1], glass_ring),
        m.box([-11.5, 4, -5], [3, 3, 1], (170, 210, 236), noise=0.1),
    ]))
    m.add(bone('served_taco', HAND_R, 'arm_r', [taco_cube(m, [-11, 2, -9])]))

    # left hand: notepad, spatula, and the coupon he is about to read
    def pad_paint(p, f):
        x0, y0, x1, y1 = (int(n) for n in f['top'])
        p.fill((x0, y0, x1, y1), (250, 248, 232), 0.02)
        for y in range(y0 + 1, y1, 2):
            for x in range(x0 + 1, x1 - 1):
                p.put(x, y, (120, 160, 210))
        p.fill((x0, y0, x1, y0 + 1), (196, 40, 36), 0.03)
    m.add(bone('notepad', HAND_L, 'arm_l', [m.box([8, 3, -9], [4, 1, 6], (250, 248, 232), extra=pad_paint)]))
    m.add(bone('spatula', HAND_L, 'arm_l', [
        m.box([9.5, 1, -7], [1, 1, 5], (120, 78, 44)),
        m.box([8.5, 0.5, -12], [3, 1, 5], (200, 204, 212), noise=0.03),
    ]))

    def coupon_paint(p, f):
        for s in ('top', 'bottom'):
            x0, y0, x1, y1 = (int(n) for n in f[s])
            p.fill((x0, y0, x1, y1), (244, 230, 190), 0.02)
            for y in range(y0, y1):
                for x in range(x0, x1):
                    if x in (x0, x1 - 1) or y in (y0, y1 - 1):
                        p.put(x, y, (196, 52, 40))
            p.put(x0 + 2, y0 + 2, (238, 196, 92))
            p.put(x0 + 3, y0 + 2, (238, 196, 92))
            p.put(x0 + 2, y0 + 3, (214, 54, 44))
    m.add(bone('fraud_coupon', HAND_L, 'arm_l', [m.box([7, 4, -10], [6, 1, 8], (244, 230, 190), extra=coupon_paint)]))
    return m.save()


def customer_papon():
    m = Model('customer_papon', 77)
    # sombrero: wide straw brim, tall crown, red and green band
    straw, straw_dk = (222, 192, 110), (186, 152, 76)

    def brim_rings(p, f):
        x0, y0, x1, y1 = (int(n) for n in f['top'])
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
        for y in range(y0, y1):
            for x in range(x0, x1):
                if int(max(abs(x + 0.5 - cx), abs(y + 0.5 - cy))) % 2 == 0:
                    p.put(x, y, straw_dk, 0.05)
        for s in ('front', 'back', 'left', 'right'):
            p.fill(f[s], (196, 52, 40), 0.04)
    crown_band = lambda p, f: [(band(p, f[s], 4, (196, 52, 40)), band(p, f[s], 3, (60, 140, 60)))
                               for s in ('front', 'back', 'left', 'right')]
    m.add(bone('sombrero', [0, 24, 0], 'body', [
        m.box([-12, 24, -12], [24, 1, 24], straw, extra=brim_rings, noise=0.08),
        m.box([-5, 25, -5], [10, 5, 10], straw, extra=crown_band, noise=0.08),
        m.box([-3, 30, -3], [6, 1, 6], straw, noise=0.08),
    ]))
    # ball cap, bill forward
    red, red_dk = (186, 36, 40), (150, 26, 30)
    m.add(bone('cap', [0, 24, 0], 'body', [
        m.box([-8.5, 24, -8.5], [17, 3, 17], red, noise=0.05,
              extra=lambda p, f: p.fill((f['front'][0] + 7, f['front'][1], f['front'][0] + 10, f['front'][1] + 2),
                                        (244, 244, 244), 0.02)),
        m.box([-5, 24, -15.5], [10, 1, 7], red_dk, noise=0.05),
        m.box([-1, 27, -1], [2, 1, 2], red_dk),
    ]))
    m.add(bone('taco', HAND_R, 'arm_r', [taco_cube(m, [-11, 2, -9])]))
    return m.save()


if __name__ == '__main__':
    for fn in (vendor, customer_papon):
        for path in fn():
            print(path)
