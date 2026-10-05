#!/usr/bin/env python3
"""Generates the Papons of the Taco Truck: the one working the window, and the customers.

Writes, under src/main/resources/assets/papon/:
  geo/entity/taco_vendor.geo.json      textures/entity/taco_vendor.png
  geo/entity/customer_papon.geo.json   textures/entity/customer_papon.png
  geo/entity/papon_boss.geo.json       textures/entity/papon_boss.png

Both keep the Walking Crops Papon's body, arms, legs and brows bone for bone at the same UVs
(tools/src/papon_base.png pasted at 0,0), like the Wandering Papon, and share one animation
file (gen_npc_anims.py) with that rig's bone names.

taco_vendor   the Papon in the truck. Chef's toque, a (fake) mustache,
              apron. Props: notepad + pencil (taking orders), spatula (cooking), a served
              taco, the fraud coupon and a magnifying glass. Rendered at 1.5x.
customer_papon a Papon in line. Hats sombrero / cap / none are variants: the renderer hides
              the hats a given customer is not wearing. Prop: his taco.
papon_boss    Chef Papon after the unmasking, the boss (256x256): no toque, no fake mustache, the
              Norwood 7 hair (no red anywhere on his face: a normal Papon's); tequila bottle and handle with
              draining liquid, giant spatula, molotov, lighter, van keys, and his phase-two form.
              See papon_boss().
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
        # brows half a pixel higher than the Walking Crops Papon's, so they never sit on his eyes
        # (gen_anims.guard_brows keeps the animations off them too)
        bone('brow_r', [-5, 21, -8], 'body', [cube([-8, 20.5, -8.1], [6, 1, 1], [0, 46])]),
        bone('brow_l', [5, 21, -8], 'body', [cube([2, 20.5, -8.1], [6, 1, 1], [0, 48])]),
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
        self.glow = None        # a second Painter for <name>_glowmask.png, when the model has one

    def box(self, origin, size, color, grids=None, noise=0.05, inflate=0, extra=None, glow=None):
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
        if glow and self.glow:
            glow(self.glow, {k: f[v_] for k, v_ in SIDE.items()})
        return cube(origin, size, (u, v), inflate)

    def add(self, *bones):
        self.bones.extend(bones)

    def save(self):
        geo = write_geo(self.name, self.bones, self.tex, self.tex, bounds=(5, 4.5, 1.5))
        out = [geo, self.p.save(os.path.join(ASSETS, 'textures/entity/%s.png' % self.name))]
        if self.glow:
            out.append(self.glow.save(os.path.join(ASSETS, 'textures/entity/%s_glowmask.png' % self.name)))
        return out


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

    hair         Norwood 7: all that is left is a thin, low horseshoe, a fringe lying flat on the
                 scalp around the back of the head and along the sides as far as behind the ears,
                 narrowing to a pixel at its ends; nothing on top, nothing puffing out. The top of
                 the head (the body cube's up face) gets a shine painted on, since the toque has
                 always covered it.
    wisps        the two last hairs on top, which wave about
    sweat, blush caught out / embarrassed (the boss also blushes as he gets drunk)
    tears        the boss, beaten
    The fake mustache leaves nothing behind: under it is his plain face.
    """
    def hair_paint(p, f):
        for r in f.values():
            x0, y0, x1, y1 = (int(n) for n in r)
            for x in range(x0, x1):
                for y in range(y0, y1):
                    k = p.rng.random()
                    if k < 0.3:
                        p.put(x, y, HAIR_GREY, 0.08)
                    elif k < 0.5:
                        p.put(x, y, (70, 54, 42), 0.08)
    # one pixel thick but sunk into the head, a quarter pixel proud of the skin, so the fringe
    # reads as hair lying on the scalp rather than a block stuck to it
    h = lambda o, sz: m.box(o, sz, HAIR, extra=hair_paint, noise=0.1)
    m.add(bone('hair', [0, 16, 0], 'body', [
        h([-8.25, 16, 2], [1, 2, 6]),             # right side: two rows from behind the ear back...
        h([-8.25, 17, -1], [1, 1, 3]),            # ...one row tapering toward the ear
        h([7.25, 16, 2], [1, 2, 6]),              # left side
        h([7.25, 17, -1], [1, 1, 3]),
        h([-8, 15, 7.25], [16, 3, 1]),            # around the back, the widest part
        h([-6, 14, 7.25], [3, 1, 1]),             # a ragged lower edge at the nape
        h([1, 14, 7.25], [4, 1, 1]),
    ]))
    m.add(bone('wisps', [0, 24, 0], 'body', [
        m.box([-1.5, 24, -1], [1, 3, 0], HAIR, noise=0.05),
        m.box([1, 24, 1], [1, 2, 0], HAIR, noise=0.05),
    ]))
    # scalp shine on the top face of the body cube (uv 0,0 16x20x16: up face 16..32 x 0..16)
    for x, y in ((20, 3), (21, 3), (22, 3), (19, 4), (20, 4), (21, 4), (19, 5), (20, 5), (24, 6), (25, 6)):
        r, g, b, a = m.p.px[x, y]
        m.p.px[x, y] = (min(255, r + 70), min(255, g + 66), min(255, b + 60), 255)
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

    # the disguise: a costume-shop handlebar mustache, glued onto his bare lip. Too big,
    # too black, too shiny, curled up at the tips, the tan adhesive strip showing along its top
    # and the price tag still on it
    stache = (16, 14, 16)

    def plastic(p, f):
        x0, y0, x1, y1 = (int(n) for n in f['front'])
        for x in range(x0, x1):
            if (x - x0) % 3 == 1:
                p.put(x, y0, (92, 92, 104), 0.02)              # a plastic sheen along the top

    def tag(p, f):
        x0, y0, x1, y1 = (int(n) for n in f['front'])
        p.put(x0, y0, (200, 30, 30), 0)                        # "$4.99" as far as anyone can tell
    tan = (214, 184, 128)
    m.add(bone('mustache', [0, 12, -8], 'body', [
        m.box([-5, 12, -9.2], [10, 3, 1], stache, noise=0.04, extra=plastic),
        m.box([-5, 15, -9.25], [10, 1, 0], tan, noise=0.05),                          # the glue strip
        m.box([-7, 11, -9.2], [2, 3, 1], stache, noise=0.04, extra=plastic),
        m.box([5, 11, -9.2], [2, 3, 1], stache, noise=0.04, extra=plastic),
        m.box([-9, 11, -9.2], [2, 2, 1], stache, noise=0.04, extra=plastic),
        m.box([7, 11, -9.2], [2, 2, 1], stache, noise=0.04, extra=plastic),
        m.box([-10, 12, -9.2], [1, 2, 1], stache, noise=0.04, extra=plastic),         # curled-up tips
        m.box([9, 12, -9.2], [1, 2, 1], stache, noise=0.04, extra=plastic),
        m.box([9, 9, -9.15], [1, 2, 0], (236, 236, 236), noise=0.0),                  # the tag's string
        m.box([8, 7, -9.15], [2, 2, 0], (248, 246, 236), noise=0.0, extra=tag),      # the price tag
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


BLOOD, BLOOD_LIT = (112, 8, 14), (168, 22, 26)
FRY, FRY_DK, FRY_TIP = (240, 196, 84), (214, 160, 58), (178, 112, 40)
BUTTER, BUTTER_HI = (250, 226, 120), (255, 244, 186)


def mirror_x(origin, size):
    return [-(origin[0] + size[0])] + list(origin[1:])


def crinkle(p, f):
    """A crinkle-cut fry: ridges across it, browned ends, a little salt."""
    for side in ('front', 'back', 'top', 'bottom'):
        x0, y0, x1, y1 = (int(n) for n in f[side])
        for x in range(x0, max(x1, x0 + 1)):
            for y in range(y0, max(y1, y0 + 1)):
                if (x - x0) % 2 == 0:
                    p.put(x, y, FRY_DK, 0.05)
                if p.rng.random() < 0.05:
                    p.put(x, y, (250, 250, 244))
        if x1 - x0 > 2:
            for y in range(y0, max(y1, y0 + 1)):
                p.put(x0, y, FRY_TIP, 0.06)
    for side in ('left', 'right'):
        p.fill(f[side], FRY_TIP, 0.06)


# (length, fan angle in degrees, up is positive) of the fries in one wing, top to bottom
WING_FRIES = [(13, 62), (17, 44), (20, 27), (20, 11), (18, -4), (15, -18), (11, -31)]


def phase_two(m):
    """The bones of his baked form (see papon_boss)."""
    # ---- blood from the left corner of his mouth (the mouth is the dark line at y 10-11, x -5..5)
    m.add(bone('blood', [0, 10, -8], 'body', [
        m.box([-5.5, 7, -8.13], [1, 4, 0], BLOOD, noise=0.08),
        m.box([-6, 6, -8.13], [1, 2, 0], BLOOD_LIT, noise=0.06),
        m.box([4.5, 9, -8.13], [1, 2, 0], BLOOD, noise=0.08),
    ]))
    m.add(bone('blood_drop', [-5.5, 6, -8.5], 'blood', [m.box([-6, 5, -8.6], [1, 1, 1], BLOOD_LIT, noise=0.05)]))
    # ---- blood on his left fist (p2_kneel: he looks at his hand and it is bleeding too): smeared over the
    # front of the fist (up, when the arm is held out in front of him) and its end, a run up the arm, a drop
    m.add(bone('hand_blood', HAND_L, 'hand_l', [
        m.box([8.4, 4, -3.13], [3, 3, 0], BLOOD, noise=0.1),
        m.box([9.4, 7, -3.13], [1, 2, 0], BLOOD_LIT, noise=0.06),
        m.box([10.6, 6, -3.13], [1, 1, 0], BLOOD_LIT, noise=0.06),
        m.box([8.5, 3.95, -2.6], [3, 0, 4], BLOOD, noise=0.1),
        m.box([7.95, 4, -2.4], [0, 2, 2], BLOOD, noise=0.08),
    ]))
    m.add(bone('hand_blood_drop', [10, 4, -2], 'hand_blood', [m.box([9.5, 3, -2.5], [1, 1, 1], BLOOD_LIT, noise=0.05)]))

    # ---- a pat of butter melting on his bald head, two runs of it down the back
    m.add(bone('butter', [0, 24, 1], 'body', [
        m.box([-3, 24, -2], [6, 2, 5], BUTTER, noise=0.04,
              extra=lambda p, f: [p.put(int(f['top'][0]) + 1, int(f['top'][1]) + 1, BUTTER_HI)]),
        m.box([-4, 24, -3], [8, 0.5, 7], BUTTER, noise=0.05),          # the melted puddle under it
        m.box([-2, 20, 8.02], [1, 4, 0.5], BUTTER, noise=0.05),        # runs down the back of his head
        m.box([1.5, 21.5, 8.02], [1, 2.5, 0.5], BUTTER, noise=0.05),
    ]))

    # ---- the crown: eight fries standing in a ring, floating over his head (it glows)
    def gold(p, f):
        for r in f.values():
            x0, y0, x1, y1 = (int(n) for n in r)
            for x in range(x0, max(x1, x0 + 1)):
                for y in range(y0, max(y1, y0 + 1)):
                    p.px[x, y] = (255, 255, 255, 255)
    crown = []
    import math
    for k in range(8):
        a = k * math.pi / 4
        cx, cz = math.cos(a) * 6.5, math.sin(a) * 6.5
        crown.append(m.box([cx - 1, 29, cz - 1], [2, 6 if k % 2 == 0 else 4, 2], FRY, noise=0.05, extra=crinkle, glow=gold))
    crown.append(m.box([-7, 28, -7], [14, 1, 14], FRY_DK, noise=0.05, glow=gold))     # the band they stand on
    m.add(bone('fry_crown', [0, 28, 0], 'body', crown))

    # ---- the wings: a fan of crinkle-cut fries off each shoulder blade. Every fry is its own bone
    # with its fan angle built in, so the wing opens and folds by turning them toward each other
    for side, s in (('r', 1), ('l', -1)):
        root = 'wing_' + side
        m.add(bone(root, [-3 * s, 18, 8.5], 'body'))
        for i, (length, angle) in enumerate(WING_FRIES):
            o, sz = [-3 - length, 17, 8], [length, 2, 2]
            if s < 0:
                o = mirror_x(o, sz)
            m.add(bone('fry_%s%d' % (side, i + 1), [-3 * s, 18, 9], root,
                       [m.box(o, sz, FRY, noise=0.05, extra=crinkle)], rotation=[0, 0, -angle * s]))


def baked(im, skin=()):
    """The baked form's texture: the same sheet with his own potato skin (the 64 x 64 corner, and the
    `skin` rects: his eyelids) toasted golden, chives on top."""
    import random
    rng = random.Random(5)
    out = im.copy()
    px = out.load()
    cells = {(x, y) for y in range(64) for x in range(64)}
    for x0, y0, x1, y1 in skin:
        cells |= {(x, y) for y in range(int(y0), int(y1)) for x in range(int(x0), int(x1))}
    for x, y in sorted(cells):
        r, g, b, a = px[x, y]
        if a == 0 or (r < 90 and g < 90):                      # leave his eyes, mouth and brows alone
            continue
        if abs(r - g) < 30 and r > 200:                        # eye whites
            continue
        px[x, y] = (min(255, int(r * 1.0)), int(g * 0.88), int(b * 0.72), a)
    for _ in range(26):                                           # chives on the top and shoulders
        x, y = rng.randrange(16, 32), rng.randrange(0, 16)
        px[x, y] = (70, 150, 58, 255)
    return out


def face_rig(m):
    """His face for phase two, when he talks (client/BossFace drives all of it; at rest it is the face he
    always had): the painted mouth bar comes off and is rebuilt as the mariachi singer's lip rig
    (gen_band.mouth_rig: `mouth` > `lip_up`, `lip_low`, `mouth_in`, `teeth`, `tongue`), plus
      eye_r / eye_l      the eyes (painted off the face): moved about in their sockets, scaled for wide or narrow
      mouth_corner_r/_l  a pixel of lip at each end, lifted for a smile or a smirk, dropped for a scowl
      lid_r / lid_l      eyelids hinged above each eye (y 21; the eye is the row y 19-20): scale Y 0.67
                         covers the eye (a blink), 0.45 half shuts it (that smouldering look), one at
                         a time for a wink. Pinned to 0 by every animation (gen_boss_anims.B.pins)
    Returns the texture rects of the skin-coloured lids, which baked() toasts too."""
    from gen_band import unpaint_mouth, mouth_rig, LIP
    unpaint_mouth(m)
    mouth_rig(m)
    # the eyes: off the face (each a dark 3 x 1 with a glint, row 20 of the sheet) and back as cubes that move
    # about in their sockets: where he looks, darting, wide with shock, narrowed when he schemes
    socket = (0xb6, 0x87, 0x2f)
    for x in list(range(18, 21)) + list(range(27, 30)):
        m.p.put(x, 20, socket)
    eye = lambda p, f: [p.put(int(f['front'][0]) + 1, int(f['front'][1]), (255, 255, 255))]
    for side, x in (('r', -6), ('l', 3)):
        m.add(bone('eye_' + side, [x + 1.5, 19.5, -8], 'body', [m.box([x, 19, -8.03], [3, 1, 1], (0x34, 0x29, 0x18), extra=eye, noise=0.0)]))
    # the corners sit on the ends of the upper lip (same colour, a hair in front), so at rest they vanish
    for side, x in (('r', -5), ('l', 4)):
        m.add(bone('mouth_corner_' + side, [x + 0.5, 10.5, -8], 'mouth',
                   [m.box([x, 10, -8.18], [1, 1, 1], LIP, noise=0.0)]))
    skin = []
    lid = lambda p, f: (skin.extend(f.values()),
                        [p.put(x, int(f['front'][3]) - 1, (110, 76, 30)) for x in range(int(f['front'][0]), int(f['front'][2]))])
    for side, x in (('r', -7), ('l', 2)):
        # in front of the eye but behind the brow (-8.1), so a lowered lid never hides the brow
        m.add(bone('lid_' + side, [x + 2.5, 21, -8], 'body', [m.box([x, 18, -8.06], [5, 3, 1], (0xd9, 0xaa, 0x51), extra=lid, noise=0.03)]))
    return skin


def rig_hands(m):
    """Splits each arm so his reaches do not warp what he holds: the arm bone keeps the pivot (and
    takes the rotations), its cube moves to a child `arm_*_mesh` (the one that is stretched), and a
    `hand_*` bone at the fist carries every prop; the animations slide the hand to the end of the
    stretched arm (gen_boss_anims.stretch) instead of scaling the props with it."""
    for side, hand in (('r', HAND_R), ('l', HAND_L)):
        arm = 'arm_' + side
        i = next(k for k, b in enumerate(m.bones) if b['name'] == arm)
        a = m.bones[i]
        mesh = bone(arm + '_mesh', a['pivot'], arm, a.pop('cubes'))
        h = bone('hand_' + side, hand, arm)
        for b in m.bones:
            if b.get('parent') == arm:
                b['parent'] = 'hand_' + side
        m.bones[i + 1:i + 1] = [mesh, h]


def papon_boss():
    """Chef Papon after the reveal, as the boss: toque and fake mustache gone for good, Norwood 7 out
    in the open, his own thin mustache (the fake one took nothing with it: no marks). Rendered at 1.5x (1.0 in the van) with a
    translucent render type, so the bottles are see-through and their level reads from outside.

    Props (right hand unless noted; all along the arm's axis, so they follow the arm):
      bottle (+ bottle_liquid)   the 750 ml bottle he downs at the start of the fight
      handle (+ handle_liquid)   the 1.75 l handle he downs before every van attack: the warning
      big_spatula                his melee weapon
      molotov                    a lit bottle to throw
      lighter (left)             for the tequila fire breath
      keys (left)                the van keys, jingled on the way to the van
      giant_shaker (off root)    the huge salt shaker he summons over his head (P2_SEASON)
      torch (+ torch_flame), salt_shaker (left)
                                 the grab: blowtorched, then salted (GRAB_COMBO); the flame glows
    The liquid bones pivot at their bottom, so scaling Y from 1 to 0 empties the bottle. All of these
    hang off `hand_r` / `hand_l`, not the arm itself (rig_hands), so a stretched arm does not stretch them.

    Phase two, "El Papa Horneado" (the main animations pin all of these to scale 0; only the
    second, "form" controller's p2_* animations show them, and the cutscene's show the blood):
      blood (+ blood_drop)       a trickle from the corner of his mouth; the drop falls in p2_kneel
      butter                     a pat of butter melting on his bald head
      fry_crown                  a ring of standing fries floating over his head (glows)
      wing_r > fry_r1..7 (and _l) wings of crinkle-cut fries, fanned; each fry has its fan angle in
                                 its rest rotation, so animations open and fold them
    papon_boss_baked.png is the same sheet with his skin baked golden and split (baked()); the
    renderer swaps to it once he has transformed. The glowmasks light only the crown: GeckoLib
    takes glowmasked pixels out of the normal pass, so nothing he wears in phase one may be in it.
    """
    m = Model('papon_boss', 1945, tex=256)
    m.glow = Painter(256, 256, 7)
    apron(m)
    bald_parts(m)
    lids = face_rig(m)
    phase_two(m)

    # ---- the tequila (gen_props.tequila is the item): a squat añejo bottle, clear glass, gold
    # tequila, a cream label with a red band and a blue agave, a wooden ball cork under a green stamp.
    # Both bottles hang from the fist by the neck (cork at the hand, body below), so with the arm
    # raised to his mouth the cork is at his lips and the bottle points up and away: a chug. Their
    # liquid pivots at the neck end, so as it shrinks it draws back toward his mouth.
    def agave_label(dark=False):
        def paint(p, f):
            glassy(GLASS)(p, f)
            for side in ('front', 'back'):
                x0, y0, x1, y1 = (int(n) for n in f[side])
                h = y1 - y0
                lx0, lx1, ly0, ly1 = x0 + 1, x1 - 1, y0 + h // 4, y1 - 1
                p.fill((lx0, ly0, lx1, ly1), (30, 28, 26) if dark else (244, 234, 206), 0.02)
                p.fill((lx0, ly0, lx1, ly0 + 1), (178, 34, 30) if not dark else (214, 172, 60), 0.02)
                cx, base = (lx0 + lx1) // 2, ly1 - 1
                for dx, hh in ((-2, 2), (-1, 3), (0, 4), (1, 3), (2, 2)):
                    for k in range(min(hh, ly1 - ly0 - 2)):
                        p.put(cx + dx, base - k, (214, 172, 60) if dark else (62, 128, 140))
        return paint

    def wood(p, f):
        for r in f.values():
            x0, y0, x1, y1 = (int(n) for n in r)
            for x in range(x0, max(x1, x0 + 1)):
                for y in range(y0, max(y1, y0 + 1)):
                    p.put(x, y, (176 if (x + y) % 3 else 150, 120 if (x + y) % 3 else 100, 70), 0.05)
    m.add(bone('bottle', HAND_R, 'arm_r', [
        m.box([-11.5, 2, -1.5], [3, 3, 3], (176, 120, 70), extra=wood),                  # the cork
        m.box([-10.5, 2, -2], [1, 3, 4], (52, 132, 70), noise=0.05),                     # the tax stamp over it
        m.box([-11, -1, -1], [2, 3, 2], GLASS, extra=glassy(GLASS, 120, 220)),           # neck
        m.box([-12, -2, -2], [4, 1, 4], GLASS, extra=glassy(GLASS, 110, 220)),           # shoulder
        m.box([-13, -10, -3], [6, 8, 6], GLASS, extra=agave_label()),
    ]))
    m.add(bone('bottle_liquid', [-10, -2.5, 0], 'bottle', [m.box([-12.5, -9.5, -2.5], [5, 7, 5], TEQUILA, noise=0.06)]))

    # ---- the handle: a 1.75 l jug of the same, black and gold label, a glass loop on the side
    m.add(bone('handle', HAND_R, 'arm_r', [
        m.box([-11.5, 2, -1.5], [3, 2, 3], (176, 120, 70), extra=wood),
        m.box([-10.5, 2, -2], [1, 2, 4], (52, 132, 70), noise=0.05),
        m.box([-11, -1, -1], [2, 3, 2], GLASS, extra=glassy(GLASS, 120, 220)),
        m.box([-12.5, -2, -2.5], [5, 1, 5], GLASS, extra=glassy(GLASS, 110, 220)),
        m.box([-14, -13, -4], [8, 11, 8], GLASS, extra=agave_label(True)),
        m.box([-16, -10, -0.5], [1, 7, 1], GLASS, extra=glassy(GLASS, 150, 220)),     # the loop
        m.box([-15, -4, -0.5], [1, 1, 1], GLASS, extra=glassy(GLASS, 150, 220)),
        m.box([-15, -10, -0.5], [1, 1, 1], GLASS, extra=glassy(GLASS, 150, 220)),
    ]))
    m.add(bone('handle_liquid', [-10, -2.5, 0], 'handle', [m.box([-13.5, -12.5, -3.5], [7, 10, 7], TEQUILA, noise=0.06)]))

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

    # ---- molotov: the same tequila bottle, held by the body, a burning rag stuffed in the neck at the far end
    m.add(bone('molotov', HAND_R, 'arm_r', [
        m.box([-12, -3, -2], [4, 6, 4], TEQUILA, extra=agave_label()),
        m.box([-11, -5, -1], [2, 2, 2], GLASS, extra=glassy(GLASS, 120, 220)),
        m.box([-11.5, -7, -1.5], [3, 2, 3], (230, 222, 200), noise=0.12),                # rag
        m.box([-11, -10, -1], [2, 3, 2], (255, 170, 40), noise=0.15),                    # flame
        m.box([-10.5, -12, -0.5], [1, 2, 1], (255, 230, 120), noise=0.1),
    ]))

    # ---- the comal (left hand): a round cast-iron griddle, his shield. A disc across the end of the
    # fist, so with the arm thrown forward its face (the bottom of the disc) faces you
    iron, iron_hi = (46, 46, 50), (92, 92, 100)

    def comal_face(p, f):
        x0, y0, x1, y1 = (int(n) for n in f['bottom'])
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
        for x in range(x0, x1):
            for y in range(y0, y1):
                d = ((x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2) ** 0.5
                if abs(d - 4) < 0.7 or (d < 1.2):
                    p.put(x, y, (210, 140, 50))                         # a sun cast into the iron, rubbed bright
                elif p.rng.random() < 0.08:
                    p.put(x, y, iron_hi)
    m.add(bone('comal', HAND_L, 'arm_l', [
        m.box([9.5, 1, -0.5], [1, 3, 1], (70, 50, 36), noise=0.06),                       # the grip
        m.box([3, 0, -5], [14, 1, 10], iron, noise=0.05, extra=comal_face),
        m.box([4, 0, -6], [12, 1, 12], iron, noise=0.05, extra=comal_face),
        m.box([5, 0, -7], [10, 1, 14], iron, noise=0.05, extra=comal_face),
        m.box([2.5, -0.5, -3], [1, 2, 6], iron_hi, noise=0.05),                           # the rim
        m.box([16.5, -0.5, -3], [1, 2, 6], iron_hi, noise=0.05),
    ]))

    # ---- the cleavers (one in each fist): Dungeons Delight's iron cleaver (gen_props.cleaver_sprite, the
    # thrown one too) in 3D, a box per run of one colour in each row of its sprite, half a unit a pixel;
    # turned so the grip lies in his fist along the arm and the edge faces forward
    from gen_props import cleaver_sprite
    sprite, wood = cleaver_sprite()
    spx = sprite.load()
    PX = 1.2         # a giant butcher's cleaver: ~1.9 blocks long on him at 1.5x

    def q(c):
        return tuple(min(255, (v + 8) // 16 * 16) for v in c[:3])
    for side, hx in (('r', -10.0), ('l', 10.0)):
        cubes = []
        yc, zc = 2.0 - 6.36 * PX, 0.0      # the grip (4.5 px from the sprite's middle, turned) in his fist
        for v in range(sprite.size[1]):
            u = 0
            while u < sprite.size[0]:
                if not spx[u, v][3]:
                    u += 1
                    continue
                col, hd = q(spx[u, v]), (u, v) in wood
                u1 = u
                while u1 < sprite.size[0] and spx[u1, v][3] and q(spx[u1, v]) == col and ((u1, v) in wood) == hd:
                    u1 += 1
                t = 2.0 if hd else 1.2
                size = [t, PX, (u1 - u) * PX]
                # one colour all over, so the box UV can be packed whole-pixel and the cube keep its real size
                c = m.box([hx - t / 2, yc - (v + 1 - 8) * PX, zc + (u - 8) * PX], [max(1, round(n + 0.49)) for n in size], col, noise=0)
                c['size'] = [float(n) for n in size]
                c['pivot'] = [hx, yc, zc]
                c['rotation'] = [-135, 0, 0]
                cubes.append(c)
                u = u1
        m.add(bone('cleaver_' + side, HAND_R if side == 'r' else HAND_L, 'arm_' + side, cubes))

    # ---- the tortilla (right hand): a huge flour tortilla, held up flat over his head for the wrap
    def tortilla(p, f):
        for r in f.values():
            x0, y0, x1, y1 = (int(n) for n in r)
            for x in range(x0, max(x1, x0 + 1)):
                for y in range(y0, max(y1, y0 + 1)):
                    if p.rng.random() < 0.04:
                        p.put(x, y, (166, 112, 52))
    tcol = (238, 214, 156)
    m.add(bone('tortilla', HAND_R, 'arm_r', [
        m.box([-19, -2, -6], [18, 1, 12], tcol, noise=0.04, extra=tortilla),
        m.box([-18, -2, -8], [16, 1, 16], tcol, noise=0.04, extra=tortilla),
        m.box([-16, -2, -9], [12, 1, 18], tcol, noise=0.04, extra=tortilla),
    ]))

    # ---- the brick (left hand): for the gas pedal
    def mortar(p, f):
        for side in ('front', 'back', 'left', 'right'):
            x0, y0, x1, y1 = (int(n) for n in f[side])
            p.fill((x0, (y0 + y1) // 2, x1, (y0 + y1) // 2 + 1), (196, 186, 170), 0.03)
    m.add(bone('brick', HAND_L, 'arm_l', [m.box([8.5, -1, -2], [3, 3, 4], (156, 62, 44), noise=0.1, extra=mortar)]))

    # ---- the tater tot (right hand, phase two): the cluster bomb
    m.add(bone('tot', HAND_R, 'arm_r', [m.box([-12, -3, -2], [4, 5, 4], (222, 164, 64), noise=0.14)]))

    # ---- the potato gatling: a gunmetal receiver along the arm (so it points where the fist does),
    # a hopper of spuds on top, three barrels on their own bone so they spin about the arm's axis
    gun = (58, 60, 66)
    m.add(bone('potato_gun', HAND_R, 'arm_r', [
        m.box([-13, -4, -2.5], [5, 8, 5], gun, noise=0.06),
        m.box([-14, -2, -6], [4, 5, 4], (150, 110, 60), noise=0.12),                  # the hopper
        m.box([-13.5, 1, -6.5], [5, 1, 5], (196, 150, 84), noise=0.15),                # spuds heaped in it
        m.box([-12, 4, -1.5], [3, 2, 3], (40, 40, 44), noise=0.05),                    # grip end
    ]))
    m.add(bone('gun_barrels', [-10.5, -4, 0], 'potato_gun', [
        m.box([-11.5, -14, -2.4], [2, 10, 2], (110, 112, 118), noise=0.04),
        m.box([-11.5, -14, 0.4], [2, 10, 2], (110, 112, 118), noise=0.04),
        m.box([-13.6, -14, -1], [2, 10, 2], (110, 112, 118), noise=0.04),
        m.box([-14, -15, -3], [5, 1, 6], (40, 40, 44), noise=0.04),                    # muzzle ring
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
    # ---- the grab (GRAB_COMBO): a kitchen blowtorch, then a salt shaker, both in his left hand
    # (his right holds you up)
    def lit(p, f):
        for r in f.values():
            x0, y0, x1, y1 = (int(n) for n in r)
            for x in range(x0, max(x1, x0 + 1)):
                for y in range(y0, max(y1, y0 + 1)):
                    p.px[x, y] = (255, 255, 255, 230)
    # the kitchen blowtorch, hanging from his fist along the arm: the butane can (orange, a white
    # label, a dark base), the black head with its trigger and gas knob, a collared steel nozzle out
    # the front, and its flame, a blue cone round a white-blue core (lit: glowmask)
    def can_paint(p, f):
        for side in ('front', 'back', 'left', 'right'):
            x0, y0, x1, y1 = (int(n) for n in f[side])
            p.fill((x0, y0 + 2, x1, y0 + 4), (244, 240, 230), 0.02)            # the label
            p.put(x0 + (x1 - x0) // 2, y0 + 3, (200, 40, 30))                  # a flame logo
            p.fill((x0, y1 - 1, x1, y1), (60, 58, 56), 0.03)                   # the base ring
            for y in range(y0, y1):
                p.put(x0, y, (255, 150, 70), 0.04)                            # a highlight down one edge

    def head_paint(p, f):
        x0, y0, x1, y1 = (int(n) for n in f['front'])
        p.put(x0 + 1, y0 + 1, (90, 90, 96))
    m.add(bone('torch', HAND_L, 'arm_l', [
        m.box([8.5, -5, -1.5], [3, 7, 3], (232, 96, 34), noise=0.05, extra=can_paint),
        m.box([8.3, -5.6, -1.7], [3.4, 0.6, 3.4], (52, 50, 48), noise=0.03),          # base ring under the head
        m.box([8.5, -8, -2.5], [3, 2.5, 4.5], (30, 30, 34), noise=0.04, extra=head_paint),
        m.box([9.5, -7, -3.5], [1, 2, 1], (240, 150, 40), noise=0.04),                 # the trigger
        m.box([11.5, -7.5, -1], [0.6, 1, 1], (196, 200, 210), noise=0.02),            # the gas knob
        m.box([9.3, -7.7, -4.2], [1.4, 1.4, 1], (110, 112, 118), noise=0.03),         # the collar
        m.box([9.5, -7.5, -8.5], [1, 1, 4.5], (196, 200, 210), noise=0.03),           # the nozzle
    ]))

    def blue(c):
        def paint(p, f):
            for r in f.values():
                x0, y0, x1, y1 = (int(n) for n in r)
                for x in range(x0, max(x1, x0 + 1)):
                    for y in range(y0, max(y1, y0 + 1)):
                        p.px[x, y] = (255, 255, 255, c)
        return paint
    m.add(bone('torch_flame', [10, -7, -8.5], 'torch', [
        m.box([9.6, -7.4, -12], [0.8, 0.8, 3.5], (230, 244, 255), noise=0.02, glow=blue(255)),   # the core
        m.box([9, -8, -11], [2, 2, 2.5], (70, 130, 255), noise=0.06, glow=blue(170)),          # the cone
        m.box([9.3, -7.7, -13], [1.4, 1.4, 2], (110, 170, 255), noise=0.06, glow=blue(150)),
        m.box([9.6, -7.4, -14.5], [0.8, 0.8, 1.5], (150, 200, 255), noise=0.06, glow=blue(120)),  # the tip
    ]))

    def shaker_salt(p, f):
        glassy(GLASS, 90, 200)(p, f)
        for side in ('front', 'back', 'left', 'right'):
            x0, y0, x1, y1 = (int(n) for n in f[side])
            for y in range(y0 + 2, y1 - 1):
                for x in range(x0 + 1, x1 - 1):
                    p.px[x, y] = (246, 246, 242, 255) if p.rng.random() < 0.85 else (226, 226, 222, 255)
            p.fill((x0, y0 + 1, x1, y0 + 2), (60, 110, 190), 0.03)

    def shaker_holes(p, f):
        x0, y0, x1, y1 = (int(n) for n in f['bottom'])
        for y in range(y0 + 1, y1 - 1, 2):
            for x in range(x0 + 1, x1 - 1, 2):
                p.put(x, y, (40, 40, 44))
    m.add(bone('salt_shaker', HAND_L, 'arm_l', [
        m.box([8, -5, -2], [4, 7, 4], GLASS, extra=shaker_salt),
        m.box([7.8, -7, -2.2], [4.4, 2, 4.4], (196, 200, 210), noise=0.04, extra=shaker_holes),
    ]))

    # ---- the seasoning (P2_SEASON): a huge salt shaker he summons over his head, upside down, cap
    # first, that shakes itself over him. Off the root, so it hangs in the air whatever he does
    def salt(p, f):
        glassy(GLASS, 90, 200)(p, f)
        for side in ('front', 'back', 'left', 'right'):
            x0, y0, x1, y1 = (int(n) for n in f[side])
            for y in range(y0 + (y1 - y0) // 3, y1 - 1):           # salt settled at the cap end (down)
                for x in range(x0 + 1, x1 - 1):
                    p.px[x, y] = (246, 246, 242, 255) if p.rng.random() < 0.85 else (226, 226, 222, 255)
            p.fill((x0, y0 + 2, x1, y0 + 4), (60, 110, 190), 0.03)   # a blue label at the base (up here)
        x0, y0, x1, y1 = (int(n) for n in f['top'])
        p.fill((x0, y0, x1, y1), (220, 224, 230), 0.03)              # the glass bottom

    def holes(p, f):
        x0, y0, x1, y1 = (int(n) for n in f['bottom'])
        for y in range(y0 + 2, y1 - 1, 3):
            for x in range(x0 + 2, x1 - 1, 3):
                p.put(x, y, (40, 40, 44))
    m.add(bone('giant_shaker', [0, 44, 0], 'root', [
        m.box([-7, 44, -7], [14, 20, 14], GLASS, extra=salt),
        m.box([-7.5, 40, -7.5], [15, 4, 15], (196, 200, 210), noise=0.04, extra=holes),
    ]))

    phase_three(m)
    rig_hands(m)
    out = m.save()
    base = os.path.join(ASSETS, 'textures/entity/')
    baked(Image.open(base + 'papon_boss.png').convert('RGBA'), lids).save(base + 'papon_boss_baked.png')
    m.glow.im.save(base + 'papon_boss_baked_glowmask.png')
    return out + [base + 'papon_boss_baked.png', base + 'papon_boss_baked_glowmask.png']


# the requinto's soundhole (phase three): low on his right hip, the neck level across under his mouth, so his lips
# (the brown bar low on the potato, y 9.3-11, opening down to 6.7) are never behind it while he sings
P3_GUITAR = [-7.0, 5.0, -10.4]
P3_GUITAR_ROT = [0, 0, 86]


def phase_three(m):
    """Phase three (half health): the singer's requinto, which he snatches and plays himself. The same
    instrument as the band's (gen_band.requinto_cubes, the same place on the same wkcr body):
      p3_guitar        played: on his right hip, the neck level across under his mouth, out to his left
      p3_guitar_carry  carried in his right fist by the neck, the body hanging below it (the snatch, the
                       walk back, the toss over his shoulder)
    Both pinned to 0 in every animation that does not show them (gen_boss_anims B.pins)."""
    import gen_band
    # played: on his right hip, the neck level under his mouth (his mouth is low on him: the guitar across his belly
    # hid it); the animations show it at 1.35x (gen_boss_anims.GUITAR_SCALE): he makes the little requinto look like his
    m.add(bone('p3_guitar', P3_GUITAR, 'body', gen_band.placed(gen_band.requinto_cubes(m), P3_GUITAR), rotation=P3_GUITAR_ROT))
    # the neck in the fist: the soundhole (the bone's pivot) 12 px down the neck from the hand, upside down
    carry = [HAND_R[0], HAND_R[1] - 1.5, HAND_R[2]]
    m.add(bone('p3_guitar_carry', carry, 'arm_r', gen_band.placed(gen_band.requinto_cubes(m), [carry[0], carry[1] - 11.5, carry[2] + 1.2]),
               rotation=[0, 0, 0]))


def aura():
    """textures/entity/papon_aura.png: the flowing aura of the seasoning, drawn by BossRenderer as a
    scrolling energy swirl over a slightly bigger copy of him (the charged creeper's trick). Wavy
    streaks of gold and salt-white on black (black is see-through in an additive swirl)."""
    import math
    im = Image.new('RGBA', (64, 64), (0, 0, 0, 255))
    px = im.load()
    for y in range(64):
        for x in range(64):
            w = math.sin((x + 6 * math.sin(y * math.pi / 16)) * math.pi / 8)
            v = max(0.0, w) ** 3
            sparkle = 1.0 if (x * 7 + y * 13) % 53 == 0 else 0.0
            r, g, b = 255 * v, 190 * v + 40 * v * v, 70 * v * v
            r, g, b = max(r, 255 * sparkle), max(g, 255 * sparkle), max(b, 240 * sparkle)
            px[x, y] = (int(r), int(g), int(b), 255)
    path = os.path.join(ASSETS, 'textures/entity/papon_aura.png')
    im.save(path)
    return [path]


def foil_potato():
    """papon:foil_potato, the shields that circle him in phase two: a baked potato in crinkled foil, round like a real
    one (layered slabs into an oval), the foil's twisted ends, the top torn open into petals curled back and the potato
    split inside: fluffy, a melting pat of butter, sour cream and chives. Shiny foil: lit tops, creased facets, glints.
    64 x 64 (rebuilt 2026-10-04: the user found the old box ugly)."""
    from geo_util import Packer as _P
    p = Painter(64, 64, 99)
    pk = _P(64, 64)
    foil, foil_dk, foil_hi, foil_glint = (190, 195, 204), (118, 124, 136), (232, 236, 242), (255, 255, 255)

    def crinkle(r, k):
        """A face of crinkled foil: shaded by which way it faces (k), creases and facets, a glint or two."""
        x0, y0, x1, y1 = (int(n) for n in r)
        for x in range(x0, max(x1, x0 + 1)):
            for y in range(y0, max(y1, y0 + 1)):
                base = tuple(min(255, int(c * k)) for c in foil)
                v = p.rng.random()
                if v < 0.16:
                    base = tuple(int(c * k) for c in foil_dk)
                elif v < 0.34:
                    base = tuple(min(255, int(c * k)) for c in foil_hi)
                p.put(x, y, base, 0.03)
        # diagonal creases and the light catching their ridges
        w, h = max(1, x1 - x0), max(1, y1 - y0)
        for _ in range(max(1, (w * h) // 14)):
            cx, cy = x0 + p.rng.randrange(w), y0 + p.rng.randrange(h)
            d = p.rng.choice((1, -1))
            for i in range(p.rng.randint(2, 4)):
                xx, yy = cx + i, cy + i * d
                if x0 <= xx < x1 and y0 <= yy < y1:
                    p.put(xx, yy, tuple(int(c * k * 0.82) for c in foil_dk))
                if x0 <= xx < x1 and y0 <= yy - d < y1:
                    p.put(xx, yy - d, tuple(min(255, int(c * min(1.15, k * 1.1))) for c in foil_hi))
        if w * h > 12 and k >= 1.0:
            gx, gy = x0 + p.rng.randrange(w), y0 + p.rng.randrange(h)
            p.put(gx, gy, foil_glint)

    def box(origin, size, color=None, paint=None, rot=None, pivot=None, noise=0.06):
        u, v = pk.take(*size)
        f = faces(u, v, *size)
        for side, r in f.items():
            if paint == 'foil':
                crinkle(r, {'up': 1.12, 'down': 0.72, 'north': 0.95, 'south': 0.95, 'east': 0.88, 'west': 0.88}[side])
            elif paint:
                paint(side, r)
            else:
                p.fill(r, color, noise)
        c = cube(origin, size, (u, v))
        if rot:
            c['rotation'] = rot
            c['pivot'] = pivot or [origin[0] + size[0] / 2, origin[1], origin[2] + size[2] / 2]
        return c

    def flesh(side, r):
        if side == 'up':
            p.fill(r, (246, 234, 196), 0.05, spots=((232, 214, 168), 0.25))
        else:
            p.fill(r, (156, 104, 58), 0.08, spots=((120, 78, 40), 0.2))      # the skin at the split's edge

    def cream(side, r):
        p.fill(r, (250, 250, 244), 0.03, spots=((70, 150, 60), 0.18 if side == 'up' else 0.06))   # sour cream, chives

    def butter(side, r):
        p.fill(r, (252, 222, 96) if side == 'up' else (240, 196, 70), 0.04)

    body = [
        # the oval, slab by slab (a real potato's roundness, not a box)
        box([-5, 0, -3], [10, 1, 6], paint='foil'),
        box([-6.5, 1, -4], [13, 1, 8], paint='foil'),
        box([-7, 2, -4.5], [14, 3, 9], paint='foil'),
        box([-6.5, 5, -4], [13, 1, 8], paint='foil'),
        # the twisted ends: pinched, then a twist at an angle
        box([7, 2.5, -1.5], [1.5, 2, 3], paint='foil'),
        box([8.5, 3, -0.75], [1.5, 1, 1.5], paint='foil', rot=[0, 0, 20], pivot=[8.5, 3.5, 0]),
        box([-8.5, 2.5, -1.5], [1.5, 2, 3], paint='foil'),
        box([-10, 3, -0.75], [1.5, 1, 1.5], paint='foil', rot=[0, 0, -20], pivot=[-8.5, 3.5, 0]),
    ]
    # the torn opening: petals of foil curled back all round the split
    petals = []
    # (x, z, w, d, rx, rz); geometry signs (by the Blockbench import, X mirrored): +rx leans a petal's top to -Z, +rz to +X
    for x, z, w, d, rx, rz in [(-5, -4, 10, 1, 38, 0), (-5, 3, 10, 1, -38, 0), (-6.5, -2.5, 1, 5, 0, -34), (5.5, -2.5, 1, 5, 0, 34),
                               (-4.5, -4.3, 3, 1, 58, 0), (1.5, 3.3, 3, 1, -54, 0)]:
        # each hinged on its inner edge, at the rim
        px = x + w / 2 if not rz else (x + w if rz < 0 else x)
        pz = z + d / 2 if not rx else (z + d if rx > 0 else z)
        petals.append(box([x, 6, z], [w, 1.6, d], paint='foil', rot=[rx, 0, rz], pivot=[px, 6, pz]))
    potato = [
        box([-5, 5.6, -3], [10, 1, 6], paint=flesh),                       # the split, fluffy, the skin round it
        box([-4, 6.4, -2], [8, 0.6, 4], (244, 230, 188), noise=0.06),      # piled up in the middle
        box([-1.5, 6.9, -1.5], [3, 0.8, 3], paint=butter),                 # butter, melting
        box([-1, 7.6, -1], [2, 0.4, 2], paint=butter),
        box([1.6, 6.9, -0.8], [2.4, 1, 2], paint=cream),                   # sour cream and chives
    ]
    bones = [
        bone('root', [0, 0, 0]),
        bone('foil', [0, 4, 0], 'root', body + petals),
        bone('potato', [0, 6, 0], 'foil', potato),
    ]
    geo = write_geo('foil_potato', bones, 64, 64, bounds=(1.5, 1.5, 0.5))
    import json
    anim = {'format_version': '1.8.0', 'animations': {'animation.foil_potato.idle': {
        'loop': True, 'animation_length': 2.0, 'bones': {
            'foil': {'rotation': {'0.0': {'vector': [4, 0, -6]}, '1.0': {'vector': [-4, 0, 6]}, '2.0': {'vector': [4, 0, -6]}},
                     'position': {'0.0': {'vector': [0, 0, 0]}, '1.0': {'vector': [0, 0.6, 0]}, '2.0': {'vector': [0, 0, 0]}}},
            'potato': {'scale': {'0.0': {'vector': [1, 1, 1]}, '0.5': {'vector': [1.03, 1.06, 1.03]}, '1.0': {'vector': [1, 1, 1]},
                                 '2.0': {'vector': [1, 1, 1]}}}}}}}
    apath = os.path.join(ASSETS, 'animations/entity/foil_potato.animation.json')
    with open(apath, 'w', newline='\n') as f:
        json.dump(anim, f, separators=(',', ':'))
    return [geo, p.save(os.path.join(ASSETS, 'textures/entity/foil_potato.png'))]

if __name__ == '__main__':
    for fn in (vendor, customer_papon, papon_boss, aura, foil_potato):
        for path in fn():
            print(path)
