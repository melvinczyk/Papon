#!/usr/bin/env python3
"""Generates Papon's van: a 2001 Dodge Caravan (4th generation, short wheelbase) in Patriot Blue,
the boss's car and a drivable vehicle (papon:caravan).

Writes, under src/main/resources/assets/papon/:
  geo/entity/caravan.geo.json
  textures/entity/caravan.png (512x512, translucent glass)
  textures/entity/caravan_glowmask.png (headlights, tail lamps; drawn while the engine runs)
  animations/entity/caravan.animation.json (door_open, door_close, engine_idle)

Scale: about 1.15x real (16 px = 1 block = 1 m * 1.15): 89 px long, 34 px wide (40 with
mirrors), 34 px tall, wheelbase 53 px, 13 px tires. Front is -Z, the driver's side is +X (model
left), like every rig in this mod.

Shape: the cab-forward one-box minivan of 2001-2007: short sloped hood running into a steep raked
windshield, long flat roof with rails, three side windows a side with black pillars, vertical
tail lamps on the D-pillars, the crosshair grille, a sliding door on the passenger side. Papon's
has had a life: a grey primer passenger-side fender, a missing hubcap on the driver's front wheel,
a dent in the hood, fuzzy dice on the mirror.

Bones (CaravanModel drives the procedural ones):
  root > body (all panels; rolls, pitches and shakes) > door_l (driver door, hinged at its front
         edge), steering_wheel, dice
  root > steer_fl > wheel_fl, steer_fr > wheel_fr, wheel_rl, wheel_rr   (spin about X, steer about Y)
CaravanEntity.SEAT_* match the driver's seat here (cushion top 12 px, x +8, z -10).
"""
import json
import os

from PIL import Image

from geo_util import ASSETS, Packer, Painter, bone, faces, shade

TEX = 512
# semantic face -> box-UV face (model right is -X, see geo_util / gen_vendor SIDE)
FACE = {'nx': 'east', 'px': 'west', 'nz': 'north', 'pz': 'south', 'up': 'up', 'down': 'down'}

BLUE = (40, 62, 112)
BLUE_LT = (70, 98, 150)
PRIMER = (132, 132, 126)
CLAD = (78, 80, 86)
BLACK = (26, 26, 30)
CHROME = (206, 210, 216)
GLASS = (40, 54, 70)
SEAT = (96, 92, 86)
DASH = (52, 52, 56)
TIRE = (30, 30, 32)
RED_LAMP = (196, 24, 28)


class Van:
    def __init__(self):
        self.p = Painter(TEX, TEX, 2001)
        self.glow = Image.new('RGBA', (TEX, TEX), (0, 0, 0, 0))
        self.packer = Packer(TEX, TEX)
        self.bones = {}
        self.order = []

    def bone(self, name, pivot, parent=None, rotation=None):
        b = bone(name, pivot, parent, rotation=rotation)
        b['cubes'] = []
        self.bones[name] = b
        self.order.append(name)
        return b

    def box(self, bone_name, origin, size, color, noise=0.04, paint=None, pivot=None, rotation=None, inflate=0):
        w, h, d = (int(round(v)) for v in size)
        u, v = self.packer.take(max(w, 1), max(h, 1), max(d, 1))
        f = faces(u, v, max(w, 1), max(h, 1), max(d, 1))
        for r in f.values():
            self.p.fill(r, color, noise)
        if paint:
            paint(self, {k: f[b] for k, b in FACE.items()})
        c = {'origin': [float(x) for x in origin], 'size': [float(x) for x in size], 'uv': [u, v]}
        if inflate:
            c['inflate'] = inflate
        if rotation:
            c['pivot'] = [float(x) for x in pivot]
            c['rotation'] = [float(x) for x in rotation]
        self.bones[bone_name]['cubes'].append(c)
        return f

    # ---- painting helpers (rects are x0, y0, x1, y1 in texture space; row 0 = top of a side face)
    def rect(self, r):
        return tuple(int(n) for n in r)

    def put(self, x, y, c, noise=0.0):
        self.p.put(x, y, c, noise)

    def fill(self, r, c, noise=0.03):
        self.p.fill(r, c, noise)

    def alpha(self, r, c, a):
        x0, y0, x1, y1 = self.rect(r)
        for x in range(x0, x1):
            for y in range(y0, y1):
                self.p.px[x, y] = tuple(c[:3]) + (a,)

    def lit(self, r, c):
        """Marks a rect as glowing (glowmask) with colour c."""
        x0, y0, x1, y1 = self.rect(r)
        for x in range(x0, x1):
            for y in range(y0, y1):
                self.glow.putpixel((x, y), tuple(c[:3]) + (255,))


# ---------------------------------------------------------------- paint jobs
def metal(base=BLUE):
    """Body paint: a lighter band near the top of each side (the pearl catching the sky)."""
    def paint(van, f):
        for k in ('nx', 'px', 'nz', 'pz'):
            x0, y0, x1, y1 = van.rect(f[k])
            hgt = max(1, y1 - y0)
            for y in range(y0, y1):
                t = (y - y0) / hgt
                k_ = 1.12 - 0.22 * t
                for x in range(x0, x1):
                    van.put(x, y, shade(base, k_), 0.025)
        x0, y0, x1, y1 = van.rect(f['up'])
        for x in range(x0, x1):
            for y in range(y0, y1):
                van.put(x, y, shade(base, 1.15), 0.025)
    return paint


def glass(van, f, a=120):
    for r in f.values():
        x0, y0, x1, y1 = van.rect(r)
        for x in range(x0, x1):
            for y in range(y0, y1):
                streak = (x + y) % 9 == 0
                c = shade(GLASS, 1.5 if streak else 1.0)
                van.p.px[x, y] = c[:3] + (a + (40 if streak else 0),)


def windshield(van, f):
    glass(van, f, 110)
    # wipers parked along the bottom edge (the cube's front end, as it lies before it is tilted)
    x0, y0, x1, y1 = van.rect(f['up'])
    for x in range(x0 + 3, x0 + 13):
        van.put(x, y1 - 2, BLACK)
    for x in range(x1 - 14, x1 - 4):
        van.put(x, y1 - 2, BLACK)


def door_side(handle_from_left):
    """A door skin: body paint, a black handle near its back edge, the shut line."""
    def paint(van, f):
        metal()(van, f)
        for k in ('px', 'nx'):
            x0, y0, x1, y1 = van.rect(f[k])
            hx = x0 + 1 if handle_from_left(k) else x1 - 4
            van.fill((hx, y0 + 3, hx + 3, y0 + 4), BLACK, 0.02)
            for y in range(y0, y1):
                van.put(x0, y, shade(BLUE, 0.6))
                van.put(x1 - 1, y, shade(BLUE, 0.6))
    return paint


def fascia(van, f):
    metal()(van, f)
    x0, y0, x1, y1 = van.rect(f['nz'])      # 32 x 9 front face, row 0 = y 13
    cx = (x0 + x1) // 2
    # lower intake
    van.fill((x0 + 6, y1 - 3, x1 - 6, y1 - 1), BLACK, 0.03)
    # crosshair grille: chrome frame, black mesh, a chrome cross
    gx0, gx1, gy0, gy1 = cx - 7, cx + 7, y0, y0 + 4
    van.fill((gx0, gy0, gx1, gy1), BLACK, 0.02)
    for x in range(gx0, gx1):
        van.put(x, gy0, CHROME)
        van.put(x, gy1 - 1, CHROME)
    for y in range(gy0, gy1):
        van.put(gx0, y, CHROME)
        van.put(gx1 - 1, y, CHROME)
        van.put(cx - 1, y, CHROME)
        van.put(cx, y, CHROME)
    for x in range(gx0, gx1):
        van.put(x, gy0 + 2, CHROME)
    van.fill((cx - 2, gy0 + 1, cx + 2, gy0 + 3), (190, 194, 200), 0.02)    # the badge in the middle
    # license plate
    van.fill((cx - 4, y1 - 6, cx + 4, y1 - 3), (232, 228, 214), 0.02)
    for x in range(cx - 3, cx + 3, 2):
        van.put(x, y1 - 5, (40, 60, 120))


def hood(van, f):
    metal()(van, f)
    x0, y0, x1, y1 = van.rect(f['up'])
    # the dent: a darker smudge with a bright rim
    for dx, dy, k in ((6, 6, 0.75), (7, 6, 0.7), (6, 7, 0.8), (7, 7, 0.72), (5, 6, 1.2), (8, 7, 1.18)):
        van.put(x0 + dx, y0 + dy, shade(BLUE, k))


def lamp(color, glow):
    def paint(van, f):
        for r in f.values():
            van.fill(r, color, 0.05)
            van.lit(r, glow)
    return paint


def headlight(van, f):
    for k, r in f.items():
        x0, y0, x1, y1 = van.rect(r)
        van.fill(r, (226, 232, 236), 0.04)
        van.lit(r, (255, 250, 220))
        if k == 'nz':
            # amber corner light at the outboard end, reflector dots
            van.fill((x0, y0, x0 + 2, y1), (250, 170, 40), 0.03)
            van.fill((x1 - 2, y0, x1, y1), (250, 170, 40), 0.03)
            van.put((x0 + x1) // 2, (y0 + y1) // 2, CHROME)


def tail_lamp(van, f):
    for k, r in f.items():
        x0, y0, x1, y1 = van.rect(r)
        van.fill(r, RED_LAMP, 0.06)
        van.lit(r, (255, 40, 40))
        if k in ('pz', 'nx', 'px') and y1 - y0 > 6:
            van.fill((x0, y1 - 4, x1, y1 - 2), (236, 236, 240), 0.02)     # reverse light
            van.lit((x0, y1 - 4, x1, y1 - 2), (255, 255, 255))
            van.fill((x0, y0 + 2, x1, y0 + 4), (232, 120, 30), 0.03)      # amber signal


def hatch(van, f):
    metal()(van, f)
    x0, y0, x1, y1 = van.rect(f['pz'])     # 30 x 13 back face, row 0 = y 20
    cx = (x0 + x1) // 2
    van.fill((cx - 4, y1 - 6, cx + 4, y1 - 2), (232, 228, 214), 0.02)       # plate
    for x in range(cx - 3, cx + 3, 2):
        van.put(x, y1 - 4, (40, 60, 120))
    van.fill((cx - 1, y0 + 2, cx + 1, y0 + 4), CHROME, 0.02)                # handle / badge
    # "CARAVAN" badge, a chrome dash on the right as seen from behind
    van.fill((x1 - 9, y0 + 3, x1 - 3, y0 + 4), CHROME, 0.02)
    # bumper sticker on the left: a little taco
    sx, sy = x0 + 3, y1 - 5
    van.fill((sx, sy, sx + 6, sy + 3), (250, 246, 230), 0.02)
    van.put(sx + 1, sy + 1, (238, 190, 80))
    van.put(sx + 2, sy + 1, (238, 190, 80))
    van.put(sx + 3, sy + 1, (96, 178, 60))
    van.put(sx + 4, sy + 1, (226, 64, 50))


def tire(van, f):
    for k, r in f.items():
        x0, y0, x1, y1 = van.rect(r)
        van.fill(r, TIRE, 0.05)
        if k in ('nz', 'pz', 'up', 'down'):      # tread
            for x in range(x0, x1):
                for y in range(y0, y1):
                    if (x + y) % 3 == 0:
                        van.put(x, y, shade(TIRE, 1.4))


def hubcap(van, f):
    for k, r in f.items():
        x0, y0, x1, y1 = van.rect(r)
        van.fill(r, CHROME, 0.04)
        if k in ('px', 'nx'):
            cx, cy = (x0 + x1 - 1) / 2, (y0 + y1 - 1) / 2
            for x in range(x0, x1):
                for y in range(y0, y1):
                    dx, dy = x - cx, y - cy
                    rr = (dx * dx + dy * dy) ** 0.5
                    if rr > 2.9:
                        van.put(x, y, shade(CHROME, 0.78))
                    elif abs(dx) < 0.6 or abs(dy) < 0.6 or abs(abs(dx) - abs(dy)) < 0.6:
                        van.put(x, y, shade(CHROME, 0.62))           # spokes
                    elif rr < 1.2:
                        van.put(x, y, (40, 60, 120))                 # centre cap


def steel_wheel(van, f):
    for k, r in f.items():
        x0, y0, x1, y1 = van.rect(r)
        van.fill(r, (44, 44, 48), 0.05)
        if k in ('px', 'nx'):
            cx, cy = (x0 + x1 - 1) // 2, (y0 + y1 - 1) // 2
            for dx, dy in ((-2, 0), (2, 0), (0, -2), (0, 2), (0, 0)):
                van.put(cx + dx, cy + dy, (150, 150, 150))           # lug nuts


def seat(van, f):
    for r in f.values():
        x0, y0, x1, y1 = van.rect(r)
        van.fill(r, SEAT, 0.08)
        for x in range(x0, x1):
            for y in range(y0, y1):
                if (y - y0) % 3 == 2:
                    van.put(x, y, shade(SEAT, 0.85))


def dice(van, f):
    for k, r in f.items():
        x0, y0, x1, y1 = van.rect(r)
        van.fill(r, (246, 246, 246), 0.06)
        van.put(x0, y0, BLACK)
        if k in ('nz', 'pz', 'up'):
            van.put(x1 - 1, y1 - 1, BLACK)


def plain(c):
    return lambda van, f: [van.fill(r, c, 0.04) for r in f.values()]


# ---------------------------------------------------------------- the van
def build():
    van = Van()
    van.bone('root', [0, 0, 0])
    van.bone('body', [0, 8, 0], 'root')
    b = 'body'
    m = metal()

    # floor and the engine bay (hidden under the hood, keeps the inside from being hollow)
    van.box(b, [-12, 4, -41], [24, 3, 84], (40, 40, 44))
    van.box(b, [-12, 7, -41], [24, 9, 14], (48, 48, 52))

    # ---- front
    van.box(b, [-16, 4, -44], [32, 9, 3], BLUE, paint=fascia)
    van.box(b, [-16, 13, -43], [32, 2, 4], BLUE, paint=m)
    van.box(b, [-16, 2.5, -43], [32, 2, 2], CLAD, paint=plain(CLAD))            # chin spoiler
    van.box(b, [8, 10.5, -44.4], [8, 4, 2], (226, 232, 236), paint=headlight)
    van.box(b, [-16, 10.5, -44.4], [8, 4, 2], (226, 232, 236), paint=headlight)
    for hx in (15.6, -16.6):                                                       # wrapping round the corners
        van.box(b, [hx, 10.5, -43.4], [1, 4, 3], (226, 232, 236), paint=headlight)
    # hood: one plate from the nose up to the cowl, tilted 15 degrees
    van.box(b, [-16, 14.5, -42], [32, 2, 15], BLUE, paint=hood, pivot=[0, 15.5, -42], rotation=[15, 0, 0])
    van.box(b, [-16, 17, -29], [32, 3, 3], BLACK, paint=plain(BLACK))           # cowl
    # windshield and A-pillars, raked 35 degrees
    van.box(b, [-15, 19, -28], [30, 1, 22], GLASS, paint=windshield, pivot=[0, 19.5, -28], rotation=[35, 0, 0])
    for x in (15, -17):
        van.box(b, [x, 19, -28], [2, 1, 22], BLACK, paint=plain(BLACK), pivot=[0, 19.5, -28], rotation=[35, 0, 0])

    # ---- the sides, built as skins so the driver's door can open
    for side in (1, -1):
        x = 16 if side > 0 else -17
        fender = m if side > 0 else metal(PRIMER)              # the primer fender on the passenger side
        # front fender, stepped to follow the hood, with the wheel arch cut out
        van.box(b, [x, 4, -41], [1, 11, 7], BLUE, paint=fender)
        van.box(b, [x, 14, -34], [1, 3, 6], BLUE, paint=fender)
        van.box(b, [x, 14, -28], [1, 6, 10], BLUE, paint=fender)
        van.box(b, [x, 12, -34], [1, 2, 3], BLUE, paint=fender)            # arch corners
        van.box(b, [x, 12, -21], [1, 2, 3], BLUE, paint=fender)
        # rocker cladding under the doors
        van.box(b, [x, 4, -18], [1, 3, 37], CLAD, paint=plain(CLAD))
        # wheel arches: black plastic flares over the tyres, dark liners inside so the wells are not hollow
        fx = 16.4 if side > 0 else -17.6
        lx = 12.0 if side > 0 else -16.0
        for wz in (-26, 27):
            van.box(b, [fx, 13.2, wz - 7], [1.2, 1.3, 14], CLAD, paint=plain(CLAD))
            van.box(b, [fx, 8, wz - 8.2], [1.2, 6, 1.4], CLAD, paint=plain(CLAD), pivot=[fx, 13.5, wz - 7],
                    rotation=[-28, 0, 0])
            van.box(b, [fx, 8, wz + 6.8], [1.2, 6, 1.4], CLAD, paint=plain(CLAD), pivot=[fx, 13.5, wz + 7],
                    rotation=[28, 0, 0])
            van.box(b, [lx, 13.6, wz - 7], [4, 1, 14], BLACK, paint=plain(BLACK))
        # body-side molding, a dark rub strip along the middle of the doors
        if side < 0:
            van.box(b, [x - 0.15, 10, -18], [1.2, 1, 53], CLAD, paint=plain(CLAD))
        else:
            van.box(b, [x - 0.05, 10, -3], [1.2, 1, 38], CLAD, paint=plain(CLAD))
        # behind the front door: middle and rear panels, the rear arch, the corner
        if side < 0:
            van.box(b, [x, 7, -18], [1, 13, 15], BLUE, paint=door_side(lambda k: k == 'nx'))   # passenger door
            van.box(b, [x, 7, -3], [1, 13, 17], BLUE, paint=door_side(lambda k: k == 'px'))    # sliding door
            van.box(b, [x, 7, 14], [1, 13, 5], BLUE, paint=m)
            van.box(b, [x + 0.1, 18.5, 14], [1, 1, 21], BLACK, paint=plain(BLACK))             # its track
        else:
            van.box(b, [x, 7, -3], [1, 13, 22], BLUE, paint=m)
            van.box(b, [x + 0.1, 15, 26], [1, 2, 2], BLACK, paint=plain(BLACK))                 # fuel door
        van.box(b, [x, 14, 19], [1, 6, 16], BLUE, paint=m)
        van.box(b, [x, 12, 19], [1, 2, 3], BLUE, paint=m)
        van.box(b, [x, 12, 32], [1, 2, 3], BLUE, paint=m)
        van.box(b, [x, 5, 35], [1, 15, 6], BLUE, paint=m)
        # windows and black pillars above the belt line
        if side < 0:
            van.box(b, [x, 20, -17], [1, 10, 14], GLASS, paint=glass)
        van.box(b, [x, 20, -24], [1, 5, 6], GLASS, paint=glass)                 # the little front quarter glass
        van.box(b, [x, 25, -21], [1, 4, 3], GLASS, paint=glass)
        van.box(b, [x, 20, -3], [1, 11, 2], BLACK, paint=plain(BLACK))          # B pillar
        van.box(b, [x, 20, -1], [1, 11, 15], GLASS, paint=glass)
        van.box(b, [x, 20, 14], [1, 11, 2], BLACK, paint=plain(BLACK))          # C pillar
        van.box(b, [x, 20, 16], [1, 11, 21], GLASS, paint=glass)
        van.box(b, [x, 20, 37], [1, 11, 4], BLUE, paint=m)                      # D pillar
        van.box(b, [x, 30, -12], [1, 1, 49], BLACK, paint=plain(BLACK))         # drip rail
        # mirror
        mx = 17 if side > 0 else -20
        van.box(b, [mx, 20, -19], [3, 3, 2], BLACK, paint=plain(BLACK))
        # tail lamp on the D pillar's corner
        tx = 14 if side > 0 else -17
        van.box(b, [tx, 12, 41], [3, 14, 3], RED_LAMP, paint=tail_lamp)

    # driver's door (+X), hinged at its front edge
    van.bone('door_l', [17, 13, -18], b)
    van.box('door_l', [16, 7, -18], [1, 13, 15], BLUE, paint=door_side(lambda k: k == 'px'))
    van.box('door_l', [16, 20, -17], [1, 10, 14], GLASS, paint=glass)
    van.box('door_l', [16, 30, -17], [1, 1, 14], BLACK, paint=plain(BLACK))
    van.box('door_l', [16.05, 10, -18], [1.2, 1, 15], CLAD, paint=plain(CLAD))

    # ---- roof
    van.box(b, [-16, 31, -11], [32, 2, 52], BLUE, paint=m)
    van.box(b, [-15, 31, -13], [30, 1, 2], BLUE, paint=m)
    for x in (12, -14):
        van.box(b, [x, 33, -6], [2, 1, 44], BLACK, paint=plain(BLACK))          # roof rails
        van.box(b, [x, 33, -7], [2, 1, 1], CLAD, paint=plain(CLAD))
        van.box(b, [x, 33, 38], [2, 1, 1], CLAD, paint=plain(CLAD))
    van.box(b, [-16, 17, -33], [1, 10, 1], BLACK, pivot=[-15.5, 17, -32.5], rotation=[-12, 0, 0],
            paint=plain(BLACK))                                                   # mast antenna, passenger fender
    # ---- rear
    van.box(b, [-15, 7, 41], [30, 13, 2], BLUE, paint=hatch)
    van.box(b, [-14, 20, 40], [28, 10, 1], GLASS, paint=glass)
    van.box(b, [-15, 30, 39], [30, 1, 3], BLUE, paint=m)                         # spoiler lip over the glass
    for x in (14, -16):
        van.box(b, [x, 20, 39], [2, 11, 2], BLUE, paint=m)
    van.box(b, [-16, 4, 43], [32, 4, 2], CLAD, paint=plain(CLAD))                # bumper
    van.box(b, [-15, 4, 41], [30, 3, 2], (40, 40, 44))

    # ---- inside: dash, wheel, seats, and the fuzzy dice
    van.box(b, [-15, 15, -27], [30, 5, 6], DASH, paint=plain(DASH))
    van.box(b, [7, 18, -23], [2, 2, 4], DASH, paint=plain(DASH))                 # steering column
    for x, w in ((3, 10), (-13, 10)):
        van.box(b, [x, 7, -14], [w, 5, 8], SEAT, paint=seat)
        van.box(b, [x, 12, -6], [w, 13, 2], SEAT, paint=seat)
        van.box(b, [x + 2, 25, -6], [w - 4, 3, 2], SEAT, paint=seat)
    van.box(b, [-14, 7, 4], [28, 5, 7], SEAT, paint=seat)
    van.box(b, [-14, 12, 11], [28, 11, 2], SEAT, paint=seat)
    van.box(b, [-14, 7, 24], [28, 5, 8], SEAT, paint=seat)
    van.box(b, [-14, 12, 32], [28, 11, 2], SEAT, paint=seat)
    van.box(b, [-1, 29, -12], [2, 1, 1], BLACK, paint=plain(BLACK))              # rear-view mirror
    van.bone('steering_wheel', [8, 21, -19], b, rotation=[-35, 0, 0])
    sw = 'steering_wheel'
    van.box(sw, [5, 24, -19.5], [6, 1, 1], BLACK, paint=plain(BLACK))
    van.box(sw, [5, 18, -19.5], [6, 1, 1], BLACK, paint=plain(BLACK))
    van.box(sw, [4, 19, -19.5], [1, 5, 1], BLACK, paint=plain(BLACK))
    van.box(sw, [11, 19, -19.5], [1, 5, 1], BLACK, paint=plain(BLACK))
    van.box(sw, [7, 20, -19.5], [2, 2, 1], DASH, paint=plain(DASH))
    van.bone('dice', [0, 29, -11.5], b)
    van.box('dice', [-0.5, 25, -11.5], [1, 4, 0], (240, 240, 240), paint=plain((240, 240, 240)))
    van.box('dice', [-2, 23, -12.5], [2, 2, 2], (246, 246, 246), paint=dice)
    van.box('dice', [0.5, 22.5, -12], [2, 2, 2], (246, 246, 246), paint=dice)

    # ---- wheels: two crossed boxes make an octagon; hubcap on the outside
    for name, x, z, steer in (('fl', 1, -26, True), ('fr', -1, -26, True), ('rl', 1, 27, False), ('rr', -1, 27, False)):
        px = 14.5 * x
        parent = 'root'
        if steer:
            parent = 'steer_' + name
            van.bone(parent, [px, 6.5, z], 'root')
        wb = 'wheel_' + name
        van.bone(wb, [px, 6.5, z], parent)
        x0 = 12.5 if x > 0 else -16.5
        van.box(wb, [x0, 0, z - 6.5], [4, 13, 13], TIRE, paint=tire)
        van.box(wb, [x0, 0, z - 6.5], [4, 13, 13], TIRE, paint=tire, pivot=[px, 6.5, z], rotation=[45, 0, 0])
        cap_x = 16 if x > 0 else -17
        if name == 'fl':
            van.box(wb, [cap_x - 0.5 * x, 3, z - 3.5], [1, 7, 7], (44, 44, 48), paint=steel_wheel)   # lost its hubcap
        else:
            for rot in (None, [45, 0, 0]):
                van.box(wb, [cap_x, 3, z - 3.5], [1, 7, 7], CHROME, paint=hubcap,
                        pivot=[px, 6.5, z] if rot else None, rotation=rot)
    return van


# ---------------------------------------------------------------- animations
def animations():
    def kf(pairs):
        return {('%.4g' % t if t else '0.0'): {'vector': v} for t, v in pairs}
    door_out = [0, -62, 0]
    # the Skyline's driver's door (right-hand drive, gen_skyline.py: door_r, on the model's -X). GeckoLib negates an
    # animation's Y rotation and the cubes' x: +62 here turned its back edge into the cabin (2026-10-06, the user: "the
    # door opens in towards the car"); -62 swings it out
    door_out_r = [0, -62, 0]
    anims = {
        'animation.caravan.door_open': {
            'loop': 'hold_on_last_frame', 'animation_length': 0.5,
            'bones': {'door_l': {'rotation': {
                '0.0': {'vector': [0, 0, 0]},
                '0.35': {'vector': [0, -66, 0], 'easing': 'easeOutQuad'},
                '0.5': {'vector': door_out, 'easing': 'easeOutBack'}}},
                'door_r': {'rotation': {
                    '0.0': {'vector': [0, 0, 0]},
                    '0.35': {'vector': [0, -66, 0], 'easing': 'easeOutQuad'},
                    '0.5': {'vector': door_out_r, 'easing': 'easeOutBack'}}}},
            'sound_effects': {'0.0': {'effect': 'door_open'}},
        },
        'animation.caravan.door_close': {
            'loop': 'hold_on_last_frame', 'animation_length': 0.5,
            'bones': {
                'door_l': {'rotation': {
                    '0.0': {'vector': door_out},
                    '0.3': {'vector': [0, 0, 0], 'easing': 'easeInQuad'},
                    '0.5': {'vector': [0, 0, 0]}}},
                'door_r': {'rotation': {
                    '0.0': {'vector': door_out_r},
                    '0.3': {'vector': [0, 0, 0], 'easing': 'easeInQuad'},
                    '0.5': {'vector': [0, 0, 0]}}},
                'body': {'rotation': kf([(0, [0, 0, 0]), (0.3, [0, 0, 0]), (0.36, [0, 0, 1.2]), (0.5, [0, 0, 0])])}},
            'sound_effects': {'0.3': {'effect': 'door_close'}},
        },
        # engine running: a fast idle shudder through the body
        'animation.caravan.engine_idle': {
            'loop': True, 'animation_length': 0.4,
            'bones': {'body': {'position': kf([(0, [0, 0, 0]), (0.1, [0, 0.12, 0]), (0.2, [0, 0, 0]),
                                                (0.3, [0, 0.1, 0]), (0.4, [0, 0, 0])])}},
        },
        'animation.caravan.parked': {
            'loop': True, 'animation_length': 1.0,
            'bones': {'body': {'position': kf([(0, [0, 0, 0])])}},
        },
    }
    return {'format_version': '1.8.0', 'animations': anims}


def main():
    van = build()
    bones = [van.bones[n] for n in van.order]
    for bo in bones:
        if not bo['cubes']:
            del bo['cubes']
    geo = {
        'format_version': '1.12.0',
        'minecraft:geometry': [{
            'description': {
                'identifier': 'geometry.caravan', 'texture_width': TEX, 'texture_height': TEX,
                'visible_bounds_width': 7, 'visible_bounds_height': 3.5, 'visible_bounds_offset': [0, 1.2, 0],
            },
            'bones': bones,
        }],
    }
    out = os.path.join(ASSETS, 'geo/entity/caravan.geo.json')
    with open(out, 'w', newline='\n') as f:
        json.dump(geo, f, separators=(',', ':'))
    print(out)
    print(van.p.save(os.path.join(ASSETS, 'textures/entity/caravan.png')))
    glow = os.path.join(ASSETS, 'textures/entity/caravan_glowmask.png')
    van.glow.save(glow)
    print(glow)
    anim = os.path.join(ASSETS, 'animations/entity/caravan.animation.json')
    with open(anim, 'w', newline='\n') as f:
        json.dump(animations(), f, separators=(',', ':'))
    print(anim)


if __name__ == '__main__':
    main()
