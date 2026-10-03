#!/usr/bin/env python3
"""Generates the Papons of the Taco Truck: the one working the window, and the customers.

Writes, under src/main/resources/assets/papon/:
  geo/entity/taco_vendor.geo.json      textures/entity/taco_vendor.png
  geo/entity/customer_papon.geo.json   textures/entity/customer_papon.png

Both keep the Walking Crops Papon's body, arms, legs and brows bone for bone at the same UVs
(tools/src/papon_base.png pasted at 0,0), like the Wandering Papon, and share one animation
file (gen_npc_anims.py) with that rig's bone names.

taco_vendor   the Papon in the truck. Chef's toque, a (fake) mustache,
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

    def __init__(self, name, seed, tex=TEX):
        self.name = name
        self.tex = tex
        self.bones = base_bones()
        self.p = Painter(tex, tex, seed)
        base = Image.open(os.path.join(ROOT, 'tools/src/papon_base.png')).convert('RGBA')
        self.p.im.paste(base, (0, 0))
        self.packers = [Packer(tex - 64, 64), Packer(tex, tex - 64)]
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
        geo = write_geo(self.name, self.bones, self.tex, self.tex, bounds=(4, 4, 1.5))
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


def apron(m):
    """Red with white trim and a pocket (low, so it never reads as a mouth under the mustache)."""
    def apron_paint(p, f):
        band(p, f['front'], 0, (244, 244, 236))
        x0, y0, x1, y1 = (int(n) for n in f['front'])
        p.fill((x0 + 4, y0 + 2, x1 - 4, y0 + 3), (150, 26, 26), 0.02)   # pocket
    m.add(bone('apron', [0, 8, -8], 'body', [m.box([-6, 4, -9], [12, 4, 1], (186, 34, 32), extra=apron_paint, noise=0.04)]))


HAIR, HAIR_GREY = (44, 32, 26), (132, 126, 118)


def bald_parts(m):
    """What the toque and the mustache were hiding.

    hair         Norwood 7: a low horseshoe of hair around the sides and back, puffing out over
                 the ears, nothing on top. The top of the head (the body cube's up face) gets a
                 shine painted on, since the toque has always covered it.
    wisps        the two last hairs on top, which wave about
    stache_mark  the red patch the fake mustache leaves when it is ripped off
    sweat, blush caught out / embarrassed (the boss also blushes as he gets drunk)
    tears        the boss, beaten
    """
    def hair_paint(p, f):
        for r in f.values():
            x0, y0, x1, y1 = (int(n) for n in r)
            for x in range(x0, x1):
                for y in range(y0, y1):
                    if p.rng.random() < 0.22:
                        p.put(x, y, HAIR_GREY, 0.08)
                    elif p.rng.random() < 0.3:
                        p.put(x, y, (64, 48, 38), 0.08)
    h = lambda o, sz: m.box(o, sz, HAIR, extra=hair_paint, noise=0.1)
    m.add(bone('hair', [0, 16, 0], 'body', [
        h([-8.6, 15, -1], [1, 3, 9]),             # right side, from the temple back
        h([7.6, 15, -1], [1, 3, 9]),              # left side
        h([-8, 13, 7.6], [16, 5, 1]),             # around the back, lower and thicker
        h([-10.6, 14, -0.5], [2, 4, 5]),          # the puffs over the ears
        h([8.6, 14, -0.5], [2, 4, 5]),
        h([-6, 12, 7.6], [3, 1, 1]),              # a ragged edge at the nape
        h([2, 12, 7.6], [4, 1, 1]),
    ]))
    m.add(bone('wisps', [0, 24, 0], 'body', [
        m.box([-1.5, 24, -1], [1, 3, 0], HAIR, noise=0.05),
        m.box([1, 24, 1], [1, 2, 0], HAIR, noise=0.05),
    ]))
    # scalp shine on the top face of the body cube (uv 0,0 16x20x16: up face 16..32 x 0..16)
    for x, y in ((20, 3), (21, 3), (22, 3), (19, 4), (20, 4), (21, 4), (19, 5), (20, 5), (24, 6), (25, 6)):
        r, g, b, a = m.p.px[x, y]
        m.p.px[x, y] = (min(255, r + 70), min(255, g + 66), min(255, b + 60), 255)
    m.add(bone('stache_mark', [0, 13, -8], 'body', [m.box([-5, 12.5, -8.15], [10, 1, 0], (214, 120, 104), noise=0.05)]))
    drop = (176, 222, 255)
    m.add(bone('sweat', [0, 20, -8], 'body', [
        m.box([-7, 19, -8.6], [1, 2, 1], drop, noise=0.04),
        m.box([6, 21, -8.6], [1, 2, 1], drop, noise=0.04),
        m.box([8.1, 18, -5], [1, 2, 1], drop, noise=0.04),
    ]))
    pink = (240, 128, 140)
    m.add(bone('blush', [0, 13, -8], 'body', [
        m.box([-7, 13, -8.12], [3, 1, 0], pink, noise=0.04),
        m.box([4, 13, -8.12], [3, 1, 0], pink, noise=0.04),
    ]))
    tear = (120, 190, 255)
    m.add(bone('tears', [0, 19, -8], 'body', [
        m.box([-6, 15, -8.14], [1, 4, 0], tear, noise=0.04),
        m.box([5, 15, -8.14], [1, 4, 0], tear, noise=0.04),
        m.box([-6, 14, -8.6], [1, 1, 1], tear, noise=0.04),
        m.box([5, 14, -8.6], [1, 1, 1], tear, noise=0.04),
    ]))


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

    apron(m)
    bald_parts(m)

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


def glassy(tint, alpha=60, edge_alpha=190):
    """Box paint for clear glass: see-through faces with a brighter rim, so the liquid inside shows."""
    def paint(p, f):
        for r in f.values():
            x0, y0, x1, y1 = (int(n) for n in r)
            for x in range(x0, x1):
                for y in range(y0, y1):
                    rim = x in (x0, x1 - 1) or y in (y0, y1 - 1)
                    k = 1.15 if rim else 1.0
                    c = tuple(min(255, int(ch * k)) for ch in tint)
                    p.px[x, y] = c + (edge_alpha if rim else alpha,)
    return paint


def label(p, rect, base, band_color, mark):
    x0, y0, x1, y1 = (int(n) for n in rect)
    p.fill((x0, y0, x1, y1), base, 0.02)
    p.fill((x0, y0, x1, y0 + 1), band_color, 0.02)
    p.fill((x0, y1 - 1, x1, y1), band_color, 0.02)
    p.put((x0 + x1) // 2, (y0 + y1) // 2, mark)


TEQUILA = (222, 150, 40)
GLASS = (214, 236, 228)


def papon_boss():
    """Chef Papon after the reveal, as the boss: toque and mustache gone for good, Norwood 7 out in
    the open, the red patch where the mustache was. Rendered at 1.5x (1.0 in the van) with a
    translucent render type, so the bottles are see-through and their level reads from outside.

    Props (right hand unless noted; all along the arm's axis, so they follow the arm):
      bottle (+ bottle_liquid)   the 750 ml bottle he downs at the start of the fight
      handle (+ handle_liquid)   the 1.75 l handle he downs before every van attack: the warning
      big_spatula                his melee weapon
      molotov                    a lit bottle to throw
      lighter (left)             for the tequila fire breath
      keys (left)                the van keys, jingled on the way to the van
    The liquid bones pivot at their bottom, so scaling Y from 1 to 0 empties the bottle.
    """
    m = Model('papon_boss', 1945, tex=256)
    apron(m)
    bald_parts(m)

    # ---- the 750 ml bottle: square, clear glass, amber tequila, cream label, red cap
    def bottle_label(p, f):
        glassy(GLASS)(p, f)
        for side in ('front', 'back'):
            x0, y0, x1, y1 = (int(n) for n in f[side])
            label(p, (x0, y0 + 2, x1, y0 + 6), (242, 230, 196), (196, 40, 36), (60, 120, 60))
    # Both bottles hang from the fist by the neck (cap at the hand, body below), so with the arm
    # raised to his mouth the cap is at his lips and the bottle points up and away: a chug. Their
    # liquid pivots at the neck end, so as it shrinks it draws back toward his mouth.
    glass_body = m.box([-12, -9, -2], [4, 8, 4], GLASS, extra=bottle_label)
    m.add(bone('bottle', HAND_R, 'arm_r', [
        m.box([-11, 3, -1], [2, 1, 2], (176, 34, 30), noise=0.05),
        m.box([-11, 0, -1], [2, 3, 2], GLASS, extra=glassy(GLASS)),
        m.box([-11.5, -1, -1.5], [3, 1, 3], GLASS, extra=glassy(GLASS)),
        glass_body,
    ]))
    m.add(bone('bottle_liquid', [-10, -1.5, 0], 'bottle', [m.box([-11.5, -8.5, -1.5], [3, 7, 3], TEQUILA, noise=0.06)]))

    # ---- the handle: 1.75 l jug with a glass loop handle, black and gold label
    def handle_label(p, f):
        glassy(GLASS)(p, f)
        for side in ('front', 'back'):
            x0, y0, x1, y1 = (int(n) for n in f[side])
            label(p, (x0, y0 + 2, x1, y0 + 7), (30, 28, 26), (214, 172, 60), (214, 172, 60))
    m.add(bone('handle', HAND_R, 'arm_r', [
        m.box([-11, 3, -1], [2, 1, 2], (30, 28, 26), noise=0.05),
        m.box([-11, 0, -1], [2, 3, 2], GLASS, extra=glassy(GLASS)),
        m.box([-12, -1, -2], [4, 1, 4], GLASS, extra=glassy(GLASS)),
        m.box([-13, -11, -3], [6, 10, 6], GLASS, extra=handle_label),
        m.box([-15, -8, -0.5], [1, 6, 1], GLASS, extra=glassy(GLASS, 150, 220)),     # the loop
        m.box([-14, -3, -0.5], [1, 1, 1], GLASS, extra=glassy(GLASS, 150, 220)),
        m.box([-14, -8, -0.5], [1, 1, 1], GLASS, extra=glassy(GLASS, 150, 220)),
    ]))
    m.add(bone('handle_liquid', [-10, -1.5, 0], 'handle', [m.box([-12.5, -10.5, -2.5], [5, 9, 5], TEQUILA, noise=0.06)]))

    # ---- the giant griddle spatula
    steel, steel_dk = (206, 210, 218), (140, 146, 156)

    def slots(p, f):
        for side in ('left', 'right', 'front', 'back'):
            x0, y0, x1, y1 = (int(n) for n in f[side])
            for y in range(y0 + 2, y1 - 2, 2):
                p.fill((x0 + 1, y, x1 - 1, y + 1), steel_dk, 0.02)
    m.add(bone('big_spatula', HAND_R, 'arm_r', [
        m.box([-10.5, -9, -0.5], [1, 13, 1], (98, 60, 32), noise=0.08),
        m.box([-10.5, -11, -0.5], [1, 2, 1], steel_dk, noise=0.03),
        m.box([-14, -21, -0.5], [8, 10, 1], steel, extra=slots, noise=0.03),
    ]))

    # ---- molotov: held by the body, the burning rag stuffed in the neck at the far end
    m.add(bone('molotov', HAND_R, 'arm_r', [
        m.box([-11.5, -2, -1.5], [3, 6, 3], TEQUILA, extra=glassy((236, 176, 70), 200, 240)),
        m.box([-11, -4, -1], [2, 2, 2], GLASS, extra=glassy(GLASS)),
        m.box([-11.5, -6, -1.5], [3, 2, 3], (230, 222, 200), noise=0.12),                # rag
        m.box([-11, -9, -1], [2, 3, 2], (255, 170, 40), noise=0.15),                     # flame
        m.box([-10.5, -11, -0.5], [1, 2, 1], (255, 230, 120), noise=0.1),
    ]))

    # ---- left hand: lighter and the van keys
    m.add(bone('lighter', HAND_L, 'arm_l', [
        m.box([9.5, 1, -1], [1, 3, 2], (210, 40, 40), noise=0.05),
        m.box([9.5, 4, -0.5], [1, 1, 1], (200, 200, 200), noise=0.02),
    ]))
    m.add(bone('keys', HAND_L, 'arm_l', [
        m.box([9.5, 2, -0.5], [1, 2, 1], (220, 180, 60), noise=0.04),                    # ring
        m.box([9.5, -1, -1], [1, 3, 0], (190, 194, 200), noise=0.03),                    # keys
        m.box([9.5, -1, 0.5], [1, 2, 0], (214, 176, 70), noise=0.03),
        m.box([9, 0, -2], [2, 2, 1], (30, 30, 30), noise=0.05),                          # the fob
    ]))
    return m.save()


if __name__ == '__main__':
    for fn in (vendor, customer_papon, papon_boss):
        for path in fn():
            print(path)
