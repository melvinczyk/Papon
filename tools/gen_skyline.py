#!/usr/bin/env python3
"""Generates the car Ascended Papon makes of his van: a Nissan Skyline GT-R R34 in black pearl, a flaming phoenix on the
hood, a purple underglow (drawn by client/CaravanRenderer under it).

Rebuilt 2026-10-06 (the user: "the Nissan Skyline currently looks broken and unfinished... the rear windshield is
positioned much too low and does not properly connect to the surrounding bodywork... treat this as a complete vehicle
quality pass"). It was boxes; it is now a triangle mesh (client/mesh/SkinnedMesh, drawn by client/CaravanRenderer over
the rig's bones):
  - the body a loft of real cross-sections along its length (a crisp shoulder line, flared arches cut round the wheels,
    the rocker tucked under, the nose and the tail closed by their panels: the front fascia and the tail painted on them)
  - the cabin a second loft sitting on the belt line: the windshield, the roof and the fastback glass one surface, so the
    glass meets the roof and the deck with no gaps; the side glass between the pillars
  - a cockpit to sit in: the dash with its binnacle and gauges (lit, their needles drawn live by the renderer), the
    multi-function display, a three-spoke wheel, the console and the shifter, red-stitched buckets, door cards; the roof
    high enough for a seated player's head (it was under it: you drove from on top of the hood)
  - right-hand drive, like the real R34: the driver's door on the right (door_r)
  - the four round tail lamps, the angular headlamps, the GT wing with its high stop lamp, the mirrors, the big exhaust
  - wheels turned from a profile: tyres with tread and shoulders, five-spoke rims in gunmetal, discs and gold calipers
The rig is the van's (gen_caravan.py) so CaravanEntity and CaravanRenderer drive it unchanged, plus door_r:
  root > body > door_r, steering_wheel       root > steer_fl > wheel_fl, steer_fr > wheel_fr, wheel_rl, wheel_rr
Front is -Z, +X the passenger's side; 1.26x real (92 px long, 36 wide, 27.5 tall, a 54 px wheelbase, 13 px tyres).

Writes: geo/entity/skyline.geo.json (the bones, no cubes), meshes/skyline.json (with "groups": index ranges the renderer
draws in their own passes: glass, lamps, brake, reverse, gauges, gloss, underglow), textures/entity/skyline.png (the
atlas) and textures/entity/skyline_lamps.png (what glows, the same layout).

  python tools/gen_skyline.py            (PYTHONPATH=tools)
  python tools/mesh_preview.py skyline out.png
"""
import json
import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont
from scipy.interpolate import PchipInterpolator

from geo_util import ASSETS

TEX = 1024

# ---------------------------------------------------------------- dimensions (px)
Z_NOSE, Z_FAXLE, Z_COWL, Z_ROOF0, Z_ROOF1, Z_DECK, Z_RAXLE, Z_TAIL = -45.6, -27.0, -13.6, 1.5, 21.5, 34.0, 27.0, 46.8
ROOF_H = 27.9
WHEEL_R, WHEEL_X, TYRE_W = 6.5, 14.6, 5.0
ARCH_R = 7.9
DOOR_Z0, DOOR_Z1 = -12.6, 12.0
SEAT_X, SEAT_Z, SEAT_Y = 8.0, 2.4, 7.5          # the driver's bucket (on -X), its cushion top
WHEEL_C = (-SEAT_X, 14.1, -4.4)                   # the steering wheel's centre
WHEEL_TILT = 24.0                                  # the rig's convention: its top leans away from you, toward the dash


def curve(pts):
    z, v = zip(*pts)
    return PchipInterpolator(np.array(z, float), np.array(v, float), extrapolate=True)


# the lower body's top along its length (hood, belt, deck), its half-width, its bottom
TOP = curve([(Z_NOSE, 12.2), (-44.6, 13.3), (-42.5, 14.2), (-30, 16.3), (-14, 18.2), (0, 18.4), (20, 18.8), (Z_DECK, 19.2),
             (42, 19.7), (45.4, 19.5), (Z_TAIL, 18.6)])
HALF = curve([(Z_NOSE, 15.9), (-45.0, 16.8), (-43.4, 17.35), (-37, 17.7), (-27, 18.05), (-18, 17.65), (-8, 17.4), (8, 17.45),
              (19, 17.8), (27, 18.2), (36, 17.95), (43.5, 17.45), (46.1, 16.9), (Z_TAIL, 16.1)])
CREASE = curve([(Z_NOSE, 10.4), (-40, 12.6), (-27, 13.8), (-14, 15.4), (0, 15.6), (20, 15.9), (34, 16.4), (Z_TAIL, 16.0)])
BOTTOM = curve([(Z_NOSE, 2.7), (-40, 2.8), (-20, 3.0), (20, 3.0), (40, 3.3), (Z_TAIL, 3.6)])
# the cabin: half-width at the belt and at the roof; the top's height
ROOFW = curve([(Z_COWL, 14.6), (-6, 13.6), (Z_ROOF0, 12.9), (12, 12.7), (Z_ROOF1, 12.8), (28, 13.6), (Z_DECK, 14.4)])


def roof_y(z):
    """The cabin's top along its length: up the windshield, the roof, down the fastback glass."""
    if z <= Z_COWL:
        return float(TOP(z))
    if z < Z_ROOF0:
        k = (z - Z_COWL) / (Z_ROOF0 - Z_COWL)
        k = 1 - (1 - k) ** 1.25                       # a little curve into the roof
        return float(TOP(Z_COWL)) + (ROOF_H - float(TOP(Z_COWL))) * k
    if z <= Z_ROOF1:
        return ROOF_H - 0.25 * ((z - Z_ROOF0) / (Z_ROOF1 - Z_ROOF0)) ** 2
    if z < Z_DECK:
        k = (z - Z_ROOF1) / (Z_DECK - Z_ROOF1)
        k = k ** 1.15
        return (ROOF_H - 0.25) + (float(TOP(Z_DECK)) - (ROOF_H - 0.25)) * k
    return float(TOP(z))


def arch_floor(z):
    """How high the body's bottom is cut away over a wheel (the arch, a circle round the axle), or None."""
    for za in (Z_FAXLE, Z_RAXLE):
        d = z - za
        if abs(d) < ARCH_R:
            return WHEEL_R + math.sqrt(ARCH_R * ARCH_R - d * d)
    return None


# ---------------------------------------------------------------- the texture atlas
class Atlas:
    """Regions of the albedo and the glow textures; swatches are flat colours (8x8 cells, UV at their centre)."""

    def __init__(self):
        self.img = Image.new('RGBA', (TEX, TEX), (0, 0, 0, 255))
        self.glow = Image.new('RGBA', (TEX, TEX), (0, 0, 0, 255))
        self.sw = {}
        self.next = 0

    def swatch(self, name, rgba, glow=None):
        i = self.next
        self.next += 1
        x, y = (i % 64) * 16, TEX - 64 + (i // 64) * 16
        self.img.paste(tuple(rgba) if len(rgba) == 4 else tuple(rgba) + (255,), (x, y, x + 16, y + 16))
        if glow:
            self.glow.paste(tuple(glow) + (255,), (x, y, x + 16, y + 16))
        self.sw[name] = ((x + 8) / TEX, (y + 8) / TEX)
        return self.sw[name]


PAINT = (22, 24, 33)                               # black pearl: black with a cold blue in it
PAINT_RGB = PAINT
A = Atlas()
A.swatch('paint', PAINT)
A.swatch('under', (14, 14, 16))
A.swatch('well', (10, 10, 12))
A.swatch('carbon', (30, 31, 34))
A.swatch('trim', (12, 12, 14))
A.swatch('chrome', (190, 194, 200))
A.swatch('gun', (70, 72, 78))
A.swatch('gun_hi', (120, 123, 130))
A.swatch('gun_dark', (34, 35, 38))
A.swatch('glass', (26, 34, 44, 118))
A.swatch('glass_dark', (14, 16, 20, 200))
A.swatch('mirror', (150, 160, 175))
A.swatch('interior', (34, 34, 36))
A.swatch('carpet', (24, 24, 26))
A.swatch('headliner', (58, 58, 60))
A.swatch('dash', (28, 28, 30))
A.swatch('seat', (30, 30, 32))
A.swatch('seat_red', (150, 24, 28))
A.swatch('alu', (160, 162, 166))
A.swatch('rubber', (20, 20, 21))
A.swatch('disc', (96, 96, 100))
A.swatch('caliper', (214, 168, 42))
A.swatch('lamp_red', (150, 14, 20), glow=(255, 30, 36))
A.swatch('lamp_red_dim', (110, 10, 14), glow=(150, 12, 16))
A.swatch('lamp_amber', (220, 120, 20), glow=(255, 150, 30))
A.swatch('lamp_white', (210, 214, 220), glow=(255, 250, 235))
A.swatch('reverse', (190, 192, 196))
A.swatch('stop', (120, 12, 16), glow=(255, 24, 30))
A.swatch('purple', (40, 20, 60), glow=(170, 70, 255))
A.swatch('exhaust_in', (8, 8, 9))
A.swatch('plate', (232, 230, 220))
A.swatch('red_badge', (200, 30, 34))

# detail regions (x, y, w, h) in texels
R_HOOD = (0, 0, 512, 512)
R_FRONT = (512, 0, 512, 256)
R_TAIL = (512, 256, 512, 256)
R_TREAD = (0, 512, 512, 32)
R_SIDEWALL = (0, 544, 512, 32)
R_GAUGES = (512, 512, 256, 128)
R_MFD = (768, 512, 128, 64)
R_FABRIC = (896, 512, 128, 128)
R_TAILLAMP = (768, 576, 64, 64)
R_TAILLAMP_IN = (832, 576, 64, 64)
R_SIDE = {1: (0, 576, 512, 96), -1: (0, 672, 512, 96)}   # the body's sides (u along z, v down y), +X and -X
R_DASH = (512, 640, 128, 128)
R_CARBON = (640, 640, 128, 128)
R_DOORCARD = (768, 640, 128, 128)
R_MIRROR = (896, 640, 64, 32)                               # what a mirror's glass shows: sky over the road behind
SIDE_Y0, SIDE_Y1 = 21.5, 2.0                                 # the side regions' top and bottom (px)


def region_uv(r, u, v):
    """A point in a region, (u, v) 0..1 across it, as texture UV."""
    x, y, w, h = r
    return ((x + u * w) / TEX, (y + v * h) / TEX)


def font(size):
    for f in ('C:/Windows/Fonts/arialbd.ttf', '/Library/Fonts/Arial Bold.ttf', '/System/Library/Fonts/Supplemental/Arial Bold.ttf',
              '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'):
        if os.path.exists(f):
            return ImageFont.truetype(f, size)
    return ImageFont.load_default()


def paint_hood():
    """The phoenix on the hood (the region maps x -16..16 across, z from the nose (top) to the cowl (bottom)): wings
    spread across the hood, its head toward the nose, flames streaming back off its wings and its tail toward the
    windshield. Drawn four times over and scaled down (smooth edges), gold over orange over red with a dark keyline."""
    S = 4
    W = 512 * S
    im = Image.new('RGBA', (W, W), PAINT + (255,))
    d = ImageDraw.Draw(im)

    def P(x, y):
        return (W / 2 + x * S, y * S)

    def poly(pts, fill):
        d.polygon([P(*p) for p in pts], fill=fill)

    def tongue(root, tip, width, bend, fill):
        """A flame: a tapered curve from root to tip, bent sideways."""
        (x0, y0), (x1, y1) = root, tip
        n = 18
        dx, dy = x1 - x0, y1 - y0
        L = math.hypot(dx, dy) or 1
        nx, ny = -dy / L, dx / L
        left, right = [], []
        for i in range(n + 1):
            t = i / n
            cx = x0 + dx * t + nx * bend * math.sin(math.pi * t) * L * 0.25
            cy = y0 + dy * t + ny * bend * math.sin(math.pi * t) * L * 0.25
            w = width * (1 - t) ** 0.8 * (0.6 + 0.4 * math.sin(math.pi * min(1, t * 1.6)))
            left.append((cx + nx * w, cy + ny * w))
            right.append((cx - nx * w, cy - ny * w))
        poly(left + right[::-1], fill)

    gold, orange, red, cream, key = (246, 186, 64, 255), (238, 112, 28, 255), (196, 38, 26, 255), (255, 232, 150, 255), (70, 20, 10, 255)
    # flames streaming back off the tail toward the windshield
    for k, (x, ln, b) in enumerate([(-34, 150, -0.5), (-14, 200, 0.35), (0, 230, -0.15), (14, 200, -0.35), (34, 150, 0.5),
                                    (-56, 110, -0.7), (56, 110, 0.7)]):
        for col, sc in ((key, 1.18), (red, 1.0), (orange, 0.72), (gold, 0.45), (cream, 0.2)):
            tongue((x * 0.6, 300), (x, 300 + ln), 22 * sc, b, col)
    # the wings: a fan of feathers each side, swept back, flames off their trailing edges
    for side in (-1, 1):
        for i in range(9):
            t = i / 8
            sx, sy = side * (26 + 8 * t), 190 + 10 * t
            tx = side * (70 + 160 * (1 - t * 0.55))
            ty = 205 + 120 * t + 40 * (1 - t) * 0
            for col, sc in ((key, 1.2), (red, 1.0), (orange, 0.75), (gold, 0.5)):
                tongue((sx, sy), (tx, ty), (17 - 6 * t) * sc, side * (0.35 + 0.3 * t), col)
        # flames trailing off the feather tips
        for i in range(6):
            t = i / 5
            rx = side * (190 - 120 * t)
            ry = 250 + 60 * t
            for col, sc in ((key, 1.2), (red, 1.0), (orange, 0.7), (gold, 0.4)):
                tongue((rx, ry), (rx + side * (20 - 10 * t), ry + 70 + 30 * t), 10 * sc, -side * 0.6, col)
        # the wing's leading edge, a sweep of gold from the shoulder out to the tip
        for col, sc in ((key, 1.25), (gold, 1.0), (cream, 0.45)):
            tongue((side * 22, 186), (side * 236, 212), 15 * sc, -side * 0.55, col)
    # the body and the tail fan
    for col, sc in ((key, 1.15), (red, 1.0), (orange, 0.8), (gold, 0.55)):
        poly([(0, 150 - 8 * sc), (26 * sc, 200), (22 * sc, 280), (0, 330 + 10 * sc), (-22 * sc, 280), (-26 * sc, 200)], col)
    # the head: toward the nose, a crest of flame on it, the beak, the eye
    for col, sc in ((key, 1.2), (gold, 1.0)):
        d.ellipse([P(-17 * sc, 108 - 4 * sc), P(17 * sc, 152 + 4 * sc)], fill=col)
    for col, sc in ((key, 1.25), (orange, 1.0), (gold, 0.6)):
        for x, b in ((-10, -0.4), (0, 0.1), (10, 0.4)):
            tongue((x, 116), (x * 2.2, 60 - abs(x) * 0.8), 7 * sc, b, col)
    poly([(-7, 104), (7, 104), (0, 78)], key)
    poly([(-5, 104), (5, 104), (0, 84)], cream)
    for side in (-1, 1):
        d.ellipse([P(side * 8 - 3, 120), P(side * 8 + 3, 127)], fill=key)
    # the hood's shut lines: down each side where it meets the fenders, across behind the nose and at the cowl
    shut = (8, 8, 10, 255)
    for side in (-1, 1):
        d.line([P(side * 232, 30), P(side * 236, 500)], fill=shut, width=S * 2)
    d.line([P(-232, 30), P(232, 30)], fill=shut, width=S * 2)
    d.line([P(-236, 500), P(236, 500)], fill=shut, width=S * 2)
    im = im.resize((512, 512), Image.LANCZOS)
    A.img.paste(im, (R_HOOD[0], R_HOOD[1]))


def paint_front():
    """The front fascia (x -18..18 across with +X on the right of the picture, y 20 at the top .. 2 at the bottom): the
    angular headlamps at the corners, the upper grille between them with its badge, the big lower intake with its mesh,
    the fog lamps and the brake ducts each side of it, the carbon lip."""
    x0, y0, w, h = R_FRONT
    S = 2
    im = Image.new('RGBA', (w * S, h * S), PAINT + (255,))
    gl = Image.new('RGBA', (w * S, h * S), (0, 0, 0, 255))
    d, g = ImageDraw.Draw(im), ImageDraw.Draw(gl)

    def P(x, y):
        return ((x + 18) / 36 * w * S, (20 - y) / 18 * h * S)

    def poly(pts, fill, glow=None):
        d.polygon([P(*p) for p in pts], fill=fill)
        if glow:
            g.polygon([P(*p) for p in pts], fill=glow)

    for s in (-1, 1):
        # the headlamp: an angular housing, two projectors, the amber corner
        house = [(s * 6.6, 10.4), (s * 15.9, 10.7), (s * 16.4, 12.1), (s * 15.6, 12.55), (s * 7.4, 12.3)]
        poly(house, (36, 38, 44, 255))
        lens = [(s * 7.2, 10.75), (s * 15.5, 11.0), (s * 15.9, 12.0), (s * 7.7, 11.95)]
        poly(lens, (150, 158, 170, 255))
        for cx in (9.4, 12.6):
            cx *= s
            d.ellipse([P(cx - 1.05, 12.0), P(cx + 1.05, 10.7)], fill=(232, 236, 240, 255))
            g.ellipse([P(cx - 1.05, 12.0), P(cx + 1.05, 10.7)], fill=(255, 250, 232, 255))
            d.ellipse([P(cx - 0.45, 11.65), P(cx + 0.45, 11.05)], fill=(255, 255, 255, 255))
        poly([(s * 14.6, 10.85), (s * 15.6, 10.95), (s * 15.85, 11.75), (s * 14.85, 11.7)], (230, 140, 30, 255), (255, 150, 40, 255))
        # brake duct and fog lamp low at each side
        poly([(s * 11.2, 4.4), (s * 16.2, 4.8), (s * 16.6, 8.0), (s * 11.6, 7.6)], (12, 12, 14, 255))
        for k in range(6):
            yy = 4.9 + k * 0.5
            d.line([P(s * 11.5, yy), P(s * 16.3, yy + 0.3)], fill=(34, 34, 38, 255), width=2)
        d.ellipse([P(s * 13.0 - 0.8, 7.2), P(s * 13.0 + 0.8, 5.9)], fill=(236, 236, 226, 255))
        g.ellipse([P(s * 13.0 - 0.8, 7.2), P(s * 13.0 + 0.8, 5.9)], fill=(255, 255, 238, 255))
    # the upper grille: a slot between the lamps, its mesh, the badge
    poly([(-6.2, 10.6), (6.2, 10.6), (5.8, 12.2), (-5.8, 12.2)], (10, 10, 12, 255))
    for k in range(-5, 6):
        d.line([P(k * 1.1, 10.7), P(k * 1.05, 12.1)], fill=(36, 36, 40, 255), width=2)
    d.rectangle([P(-1.3, 11.95), P(1.3, 10.9)], fill=(200, 202, 206, 255))
    d.rectangle([P(-1.0, 11.75), P(1.0, 11.1)], fill=(190, 28, 32, 255))
    # the lower intake: wide and deep, a honeycomb, the bar across it
    poly([(-9.8, 4.0), (9.8, 4.0), (10.4, 8.9), (-10.4, 8.9)], (8, 8, 9, 255))
    for yy in np.arange(4.3, 8.8, 0.55):
        for xx in np.arange(-10.0, 10.0, 0.7):
            off = 0.35 if int(round((yy - 4.3) / 0.55)) % 2 else 0
            cx, cy = xx + off, yy
            d.ellipse([P(cx - 0.22, cy + 0.2), P(cx + 0.22, cy - 0.2)], fill=(30, 30, 33, 255))
    poly([(-10.2, 7.0), (10.2, 7.0), (10.25, 7.35), (-10.25, 7.35)], PAINT + (255,))
    # the lip
    poly([(-17.4, 2.4), (17.4, 2.4), (17.6, 3.5), (-17.6, 3.5)], (30, 31, 34, 255))
    # the plate, offset to the passenger's side
    poly([(1.8, 8.95), (7.0, 8.95), (7.0, 10.25), (1.8, 10.25)], (234, 232, 222, 255))
    im = im.resize((w, h), Image.LANCZOS)
    gl = gl.resize((w, h), Image.LANCZOS)
    A.img.paste(im, (x0, y0))
    A.glow.paste(gl, (x0, y0))


def paint_sides():
    """The body's sides (2026-10-06, the user: "improve the textures further"): the black pearl shaded the way light lies
    on it (brighter on the shoulder, a shadow under the crease, darkening down to the rocker and round the arches' lips),
    the pearl's flake, the flush door handles, the GT-R's vent behind the front wheels, the fuel door on the left."""
    for sx in (1, -1):
        x0, y0, w, h = R_SIDE[sx]
        S = 2
        W, H = w * S, h * S
        zs = Z_NOSE + (np.arange(W) + 0.5) / W * (Z_TAIL - Z_NOSE)
        ys = SIDE_Y0 - (np.arange(H) + 0.5) / H * (SIDE_Y0 - SIDE_Y1)
        Z, Y = np.meshgrid(zs, ys)
        cr = CREASE(Z)
        k = np.where(Y > cr, 1.12 + 0.06 * np.clip((Y - cr) / 2.0, 0, 1),
                     0.62 + 0.38 * np.clip((Y - 3.0) / np.maximum(cr - 3.0, 1e-3), 0, 1) ** 0.7)
        k = np.where((Y <= cr) & (Y > cr - 0.7), k * 0.78, k)                 # the shadow under the crease
        for za in (Z_FAXLE, Z_RAXLE):                                           # the arches' lips
            d = np.sqrt((Z - za) ** 2 + (Y - WHEEL_R) ** 2) - ARCH_R
            k = np.where((d > 0) & (d < 0.9), k * (0.72 + 0.28 * d / 0.9), k)
        r = np.random.default_rng(7 + sx)
        flake = r.normal(0, 0.035, k.shape)
        spark = r.random(k.shape) < 0.012
        rgb = np.stack([np.clip(PAINT[c] * (k + flake), 0, 255) for c in range(3)], -1)
        rgb[spark] += np.array([30, 26, 44])
        im = Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8)).convert('RGBA')
        d = ImageDraw.Draw(im)

        def P(z, y):
            return ((z - Z_NOSE) / (Z_TAIL - Z_NOSE) * W, (SIDE_Y0 - y) / (SIDE_Y0 - SIDE_Y1) * H)
        # the door handle: a flush recess under the crease near the door's back edge, its pull in it
        hz0, hz1, hy0, hy1 = (8.0, 10.8, 14.1, 14.85)
        d.rounded_rectangle([P(hz0, hy1), P(hz1, hy0)], radius=3, fill=(9, 9, 11, 255))
        d.rounded_rectangle([P(hz0 + 0.3, hy1 - 0.18), P(hz1 - 0.3, hy1 - 0.42)], radius=2, fill=(96, 98, 106, 255))
        d.line([P(hz0, hy1 + 0.05), P(hz1, hy1 + 0.05)], fill=(48, 52, 64, 255), width=1)
        # the vent behind the front wheel: a dark mouth, three fins across it
        vz = Z_FAXLE + ARCH_R + 0.9
        poly = [P(vz, 13.4), P(vz + 3.0, 13.4), P(vz + 3.4, 10.4), P(vz + 0.4, 10.4)]
        d.polygon(poly, fill=(7, 7, 8, 255))
        for fy in (12.6, 11.8, 11.0):
            sl = (13.4 - fy) / 3.0 * 0.4
            d.line([P(vz + sl + 0.1, fy), P(vz + 3.0 + sl - 0.1, fy)], fill=(30, 32, 40, 255), width=3)
        d.line(poly + [poly[0]], fill=(40, 44, 56, 255), width=1)
        # the fuel door, on the left rear quarter
        if sx > 0:
            d.rounded_rectangle([P(30.6, 17.0), P(33.4, 14.5)], radius=5, outline=(8, 8, 10, 255), width=2)
            d.ellipse([P(31.3, 15.2), P(31.8, 14.8)], fill=(10, 10, 12, 255))
        im = im.resize((w, h), Image.LANCZOS)
        A.img.paste(im, (x0, y0))


def side_uv(p):
    x, y, z = p
    return region_uv(R_SIDE[1 if x >= 0 else -1], (z - Z_NOSE) / (Z_TAIL - Z_NOSE), (SIDE_Y0 - y) / (SIDE_Y0 - SIDE_Y1))


def paint_interior():
    """The cabin's surfaces (what fills your view from the driver's seat): the dash's grained leather, a twill carbon
    weave for the centre stack and the console, the door cards (vinyl, a stitched alcantara insert, the speaker)."""
    r = np.random.default_rng(11)
    x0, y0, w, h = R_DASH
    g = r.normal(0, 1, (h, w))
    g = np.array(Image.fromarray(((g - g.min()) / np.ptp(g) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.8))) / 255.0
    base = np.array([30, 30, 32])
    rgb = base[None, None, :] * (0.86 + 0.28 * g[..., None])
    A.img.paste(Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8)).convert('RGBA'), (x0, y0))
    # twill carbon: two over two, each tow catching the light along its own direction
    x0, y0, w, h = R_CARBON
    yy, xx = np.mgrid[0:h, 0:w]
    cell = 8
    a, b = (xx // cell), (yy // cell)
    warp = ((a + b) // 2) % 2 == 0
    fx, fy = (xx % cell) / cell, (yy % cell) / cell
    sheen = np.where(warp, 0.55 + 0.45 * np.sin(np.pi * fy), 0.55 + 0.45 * np.sin(np.pi * fx))
    v = 18 + 26 * sheen * np.where(warp, 1.0, 0.8)
    rgb = np.stack([v, v * 1.02, v * 1.08], -1)
    A.img.paste(Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8)).convert('RGBA'), (x0, y0))
    # the door card (u along the door from front to back, v down from the belt): vinyl, an insert, the speaker
    x0, y0, w, h = R_DOORCARD
    gr = r.normal(0, 3, (h, w))
    im = Image.fromarray(np.clip(np.full((h, w, 3), 36.0) + gr[..., None], 0, 255).astype(np.uint8)).convert('RGBA')
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, w, h * 0.12], fill=(24, 24, 26, 255))                                  # the top roll
    d.rounded_rectangle([w * 0.12, h * 0.2, w * 0.86, h * 0.5], radius=6, fill=(44, 44, 47, 255))   # the insert
    for k in range(int(w * 0.14), int(w * 0.84), 4):
        d.point([(k, int(h * 0.215)), (k, int(h * 0.485))], fill=(150, 26, 30, 255))          # red stitching
    d.ellipse([w * 0.08, h * 0.62, w * 0.36, h * 0.9], fill=(18, 18, 20, 255))                  # the speaker
    for k in range(5):
        rr = 3 + k * 3.5
        c = (w * 0.22, h * 0.76)
        d.ellipse([c[0] - rr, c[1] - rr, c[0] + rr, c[1] + rr], outline=(30, 30, 33, 255))
    d.rounded_rectangle([w * 0.62, h * 0.24, w * 0.76, h * 0.3], radius=2, fill=(150, 152, 158, 255))   # the inside handle
    A.img.paste(im, (x0, y0))


def paint_mirror():
    """A mirror's glass: the sky behind (pale at the horizon), the road under it, a soft sheen across (a flat grey box read
    as a grey box)."""
    x0, y0, w, h = R_MIRROR
    yy, xx = np.mgrid[0:h, 0:w] / np.array([h, w])[:, None, None]
    sky = np.array([150, 176, 205]) * (0.8 + 0.2 * yy[..., None] / 0.55)
    road = np.array([70, 72, 76]) * (1.0 - 0.3 * (yy[..., None] - 0.55) / 0.45)
    rgb = np.where(yy[..., None] < 0.55, sky, road)
    rgb = rgb * (0.9 + 0.2 * np.exp(-((xx[..., None] - yy[..., None] * 0.6 - 0.25) / 0.12) ** 2))
    A.img.paste(Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8)).convert('RGBA'), (x0, y0))


def mirror_face(pts):
    """The glass of a mirror (a box face): the region stretched over it, upright."""
    P = np.array(pts, float)
    span = P.max(0) - P.min(0)
    a = 0 if span[0] >= span[2] else 2
    return [region_uv(R_MIRROR, (p[a] - P[:, a].min()) / max(span[a], 1e-6), (P[:, 1].max() - p[1]) / max(span[1], 1e-6)) for p in pts]


def planar(region, dens=6.0):
    """Box faces mapped flat onto a region at `dens` texels a pixel (a face too big for it is squeezed to fit)."""
    x0, y0, w, h = region

    def f(pts):
        P = np.array(pts, float)
        span = P.max(0) - P.min(0)
        ax = [i for i in range(3) if i != int(np.argmin(span))]
        if 1 in ax:
            a, b = [i for i in ax if i != 1][0], 1
        else:
            a, b = ax
        ua = max(span[a], w / dens)
        vb = max(span[b], h / dens)
        return [region_uv(region, (p[a] - P[:, a].min()) / ua * 0.98 + 0.01, (P[:, b].max() - p[b]) / vb * 0.98 + 0.01) for p in pts]
    return f


def tail_xy(x):
    return x


def paint_tail():
    """The tail panel (x -18..18, the car's right on the picture's right as you stand behind it; y 22 top .. 2): the
    plate in its recess, the GT-R and SKYLINE badges, the bumper and the diffuser. The four lamps are their own discs."""
    x0, y0, w, h = R_TAIL
    S = 2
    im = Image.new('RGBA', (w * S, h * S), PAINT + (255,))
    d = ImageDraw.Draw(im)

    def P(x, y):
        return ((18 - x) / 36 * w * S, (22 - y) / 20 * h * S)

    def poly(pts, fill):
        d.polygon([P(*p) for p in pts], fill=fill)

    # the bumper's lower half, the diffuser with its fins
    poly([(-16.5, 3.4), (16.5, 3.4), (16.0, 6.2), (-16.0, 6.2)], (14, 14, 16, 255))
    for k in range(-4, 5):
        d.line([P(k * 3.0, 3.5), P(k * 3.0, 6.0)], fill=(40, 40, 44, 255), width=4)
    # the plate recess and plate
    poly([(-4.6, 9.4), (4.6, 9.4), (4.4, 13.2), (-4.4, 13.2)], (10, 10, 12, 255))
    poly([(-3.9, 9.9), (3.9, 9.9), (3.9, 12.7), (-3.9, 12.7)], (234, 232, 222, 255))
    f = font(int(h * S / 20 * 1.5))
    d.text(P(0, 11.3), '33-34', fill=(40, 60, 120, 255), font=f, anchor='mm')
    # badges: GT-R on the right, SKYLINE on the left
    fb = font(int(h * S / 20 * 1.15))
    d.text(P(-9.5, 12.0), 'GT-R', fill=(205, 208, 214, 255), font=fb, anchor='mm')
    d.text(P(9.5, 12.0), 'SKYLINE', fill=(205, 208, 214, 255), font=fb, anchor='mm')
    # the bumper's top edge (the bumper is its own box, proud of the panel: build_details)
    poly([(-17, 9.0), (17, 9.0), (17, 9.35), (-17, 9.35)], (10, 10, 12, 255))
    im = im.resize((w, h), Image.LANCZOS)
    A.img.paste(im, (x0, y0))


def paint_details():
    # tread: blocks and grooves across the tyre's width (v), repeating round it (u)
    x0, y0, w, h = R_TREAD
    im = Image.new('RGBA', (w, h), (22, 22, 23, 255))
    d = ImageDraw.Draw(im)
    for k in range(0, w, 8):
        d.line([(k, 0), (k + 4, h)], fill=(12, 12, 13, 255), width=2)
    for yy in (h * 0.3, h * 0.7):
        d.line([(0, yy), (w, yy)], fill=(10, 10, 11, 255), width=2)
    A.img.paste(im, (x0, y0))
    # sidewall: dark, a lighter bead ring, a groove
    x0, y0, w, h = R_SIDEWALL
    im = Image.new('RGBA', (w, h), (26, 26, 27, 255))
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, w, 3], fill=(18, 18, 19, 255))
    d.rectangle([0, h * 0.86, w, h * 0.9], fill=(16, 16, 17, 255))
    # the lettering, raised and grey, twice round (v 0 is the tread's edge: the letters stand with their tops outward)
    S = 4
    big = im.resize((w * S, h * S))
    d = ImageDraw.Draw(big)
    f = font(int(h * S * 0.36))
    for k in range(2):
        cx = (k + 0.25) * w * S / 2
        d.text((cx, h * S * 0.5), 'PAPON R-SPEC', fill=(120, 120, 124, 255), font=f, anchor='mm')
        d.text((cx + w * S / 4, h * S * 0.5), '265/35 R18 93Y', fill=(92, 92, 96, 255), font=font(int(h * S * 0.24)), anchor='mm')
    A.img.paste(big.resize((w, h), Image.LANCZOS), (x0, y0))
    # the gauges: tach in the middle, speedo on the right, three small ones on the left (needles drawn live)
    x0, y0, w, h = R_GAUGES
    S = 4
    im = Image.new('RGBA', (w * S, h * S), (16, 16, 18, 255))
    gl = Image.new('RGBA', (w * S, h * S), (0, 0, 0, 255))
    d, g = ImageDraw.Draw(im), ImageDraw.Draw(gl)
    for cx, cy, r, n, red in ((128, 66, 54, 11, 8), (212, 70, 40, 9, None), (40, 40, 20, 5, None), (40, 96, 20, 5, None)):
        cx, cy, r = cx * S, cy * S, r * S
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(8, 8, 9, 255), outline=(70, 70, 76, 255), width=S * 2)
        for i in range(n):
            a = math.radians(225 - 270 * i / (n - 1))
            col = (255, 40, 40, 255) if red is not None and i >= red else (240, 240, 240, 255)
            p0 = (cx + math.cos(a) * r * 0.78, cy - math.sin(a) * r * 0.78)
            p1 = (cx + math.cos(a) * r * 0.94, cy - math.sin(a) * r * 0.94)
            d.line([p0, p1], fill=col, width=S * 2)
            g.line([p0, p1], fill=(255, 140, 40, 255) if col[1] > 100 else (255, 30, 30, 255), width=S * 2)
            for j in range(4):
                if i == n - 1:
                    break
                aa = math.radians(225 - 270 * (i + (j + 1) / 5) / (n - 1))
                q0 = (cx + math.cos(aa) * r * 0.86, cy - math.sin(aa) * r * 0.86)
                d.line([q0, p1 if False else (cx + math.cos(aa) * r * 0.94, cy - math.sin(aa) * r * 0.94)], fill=col, width=S)
                g.line([q0, (cx + math.cos(aa) * r * 0.94, cy - math.sin(aa) * r * 0.94)], fill=(200, 100, 30, 255), width=S)
        d.ellipse([cx - r * 0.12, cy - r * 0.12, cx + r * 0.12, cy + r * 0.12], fill=(40, 40, 44, 255))
    A.img.paste(im.resize((w, h), Image.LANCZOS), (x0, y0))
    A.glow.paste(gl.resize((w, h), Image.LANCZOS), (x0, y0))
    # the multi-function display: boost, oil, water as bars, cyan on black
    x0, y0, w, h = R_MFD
    im = Image.new('RGBA', (w, h), (6, 8, 10, 255))
    gl = Image.new('RGBA', (w, h), (0, 0, 0, 255))
    d, g = ImageDraw.Draw(im), ImageDraw.Draw(gl)
    for row in range(3):
        yy = 10 + row * 18
        n = (14, 9, 11)[row]
        for k in range(16):
            col = (60, 220, 255, 255) if k < n else (20, 50, 60, 255)
            d.rectangle([12 + k * 7, yy, 16 + k * 7, yy + 9], fill=col)
            g.rectangle([12 + k * 7, yy, 16 + k * 7, yy + 9], fill=(40, 200, 255, 255) if k < n else (6, 20, 26, 255))
    A.img.paste(im, (x0, y0))
    A.glow.paste(gl, (x0, y0))
    # seat fabric: dark suede, a quilted red-stitched panel down the middle
    x0, y0, w, h = R_FABRIC
    im = Image.new('RGBA', (w, h), (32, 32, 34, 255))
    d = ImageDraw.Draw(im)
    d.rectangle([w * 0.25, 0, w * 0.75, h], fill=(40, 40, 43, 255))
    for k in range(0, h, 10):
        d.line([(w * 0.25, k), (w * 0.75, k)], fill=(24, 24, 26, 255), width=1)
    for xx in (w * 0.25, w * 0.75):
        d.line([(xx, 0), (xx, h)], fill=(170, 26, 30, 255), width=2)
    A.img.paste(im, (x0, y0))
    # the tail lamps: the R34's rings (outer: red with a deep ring; inner: red with the reverse light's white centre)
    for r, inner in ((R_TAILLAMP, False), (R_TAILLAMP_IN, True)):
        x0, y0, w, h = r
        S = 4
        im = Image.new('RGBA', (w * S, h * S), (60, 6, 10, 255))
        gl = Image.new('RGBA', (w * S, h * S), (0, 0, 0, 255))
        d, g = ImageDraw.Draw(im), ImageDraw.Draw(gl)
        c = w * S / 2
        for rr, col, gc in ((1.0, (190, 20, 26), (255, 36, 40)), (0.78, (90, 8, 12), (140, 14, 18)), (0.62, (210, 26, 32), (255, 50, 50)),
                            (0.34, (120, 12, 16) if not inner else (220, 222, 226), (255, 30, 36) if not inner else (0, 0, 0))):
            R = rr * c * 0.98
            d.ellipse([c - R, c - R, c + R, c + R], fill=col + (255,))
            g.ellipse([c - R, c - R, c + R, c + R], fill=gc + (255,))
        A.img.paste(im.resize((w, h), Image.LANCZOS), (x0, y0))
        A.glow.paste(gl.resize((w, h), Image.LANCZOS), (x0, y0))


# ---------------------------------------------------------------- the mesh
BONES = ['body', 'door_r', 'steering_wheel', 'steer_fl', 'steer_fr', 'wheel_fl', 'wheel_fr', 'wheel_rl', 'wheel_rr', 'root']
B = {n: i for i, n in enumerate(BONES)}


class Mesh:
    def __init__(self):
        self.pos, self.nrm, self.uv, self.bone = [], [], [], []
        self.groups = {}
        self.seen = {}

    def vert(self, p, n, uv, bone):
        """A vertex (shared with any identical one already made: the loft's neighbouring quads meet on the same corners)."""
        nn = np.array(n, float)
        L = np.linalg.norm(nn)
        nn = tuple(nn / L) if L > 1e-9 else (0.0, 1.0, 0.0)
        pp, uu = tuple(float(x) for x in p), tuple(float(x) for x in uv)
        key = (tuple(round(v, 4) for v in pp), tuple(round(v, 3) for v in nn), tuple(round(v, 5) for v in uu), B[bone])
        hit = self.seen.get(key)
        if hit is not None:
            return hit
        self.pos.append(pp)
        self.nrm.append(nn)
        self.uv.append(uu)
        self.bone.append(B[bone])
        self.seen[key] = len(self.pos) - 1
        return len(self.pos) - 1

    def quad(self, pts, nrms, uvs, bone, group='opaque', extra=()):
        """A quad (or a triangle: its last corner repeated); pts counter-clockwise seen from outside."""
        idx = [self.vert(p, n, u, bone) for p, n, u in zip(pts, nrms, uvs)]
        if len(idx) == 3:
            idx.append(idx[2])
        for g in (group,) + tuple(extra):
            self.groups.setdefault(g, []).extend(idx)

    def flat(self, pts, uvs, bone, group='opaque', extra=()):
        p = [np.array(x, float) for x in pts]
        n = np.cross(p[1] - p[0], p[2] - p[0])
        if len(p) == 4 and np.linalg.norm(n) < 1e-9:
            n = np.cross(p[2] - p[1], p[3] - p[1])
        self.quad(pts, [n] * len(pts), uvs, bone, group, extra)


M = Mesh()


def sw(name):
    return [A.sw[name]] * 4


def loft(rings, mats, bone_of, crease_r=(), crease_s=(), skip=None, inner=None):
    """Quads between consecutive rings (each a list of points in order round the section), with smoothed normals
    (split at the crease ring indices and section indices). mats(s, r) -> (material, uv function or None, group extra)
    for the quad from ring s to s+1 between points r and r+1; None leaves it out. bone_of(s, r) -> bone name."""
    P = np.array(rings, float)                       # S x R x 3
    S, R = P.shape[0], P.shape[1]
    F = np.zeros((S - 1, R - 1, 3))
    for s in range(S - 1):
        for r in range(R - 1):
            a, b, c, d = P[s, r], P[s + 1, r], P[s + 1, r + 1], P[s, r + 1]
            n = np.cross(d - b, c - a)            # outward for rings ordered bottom-to-top with the sections along +z
            L = np.linalg.norm(n)
            F[s, r] = n / L if L > 1e-12 else 0

    def group_r(r):
        return sum(1 for c in crease_r if r >= c)

    def group_s(s):
        return sum(1 for c in crease_s if s >= c)

    def corner_normal(s, r, qs, qr):
        acc = np.zeros(3)
        for ds in (-1, 0):
            for dr in (-1, 0):
                ss, rr = s + ds, r + dr
                if 0 <= ss < S - 1 and 0 <= rr < R - 1 and group_r(rr) == group_r(qr) and group_s(ss) == group_s(qs):
                    acc += F[ss, rr]
        return acc if np.linalg.norm(acc) > 1e-9 else F[qs, qr]

    for s in range(S - 1):
        for r in range(R - 1):
            m = mats(s, r)
            if m is None:
                continue
            mat, uvf, extra = m
            corners = [(s, r), (s + 1, r), (s + 1, r + 1), (s, r + 1)]
            pts = [P[a, b] for a, b in corners]
            if np.linalg.norm(F[s, r]) < 1e-9:
                continue
            nrms = [corner_normal(a, b, s, r) for a, b in corners]
            uvs = [uvf(p) for p in pts] if uvf else sw(mat)
            group = 'glass' if mat.startswith('glass') else 'opaque'
            M.quad(pts, nrms, uvs, bone_of(s, r), group, extra)
            if inner is not None:
                im = inner(s, r)
                if im:
                    im, iuv = im if isinstance(im, tuple) else (im, None)
                    off = [p - n / max(np.linalg.norm(n), 1e-9) * 0.45 for p, n in zip(pts, nrms)]
                    M.quad(off[::-1], [-n for n in nrms[::-1]], [iuv(q) for q in off[::-1]] if iuv else sw(im), bone_of(s, r))


def z_samples():
    zs = set(np.round(np.arange(Z_NOSE, Z_TAIL + 0.01, 1.0), 3))
    for za in (Z_FAXLE, Z_RAXLE):
        zs |= set(np.round(np.arange(za - ARCH_R, za + ARCH_R + 0.01, 0.5), 3))
    zs |= {Z_NOSE, Z_NOSE + 0.35, Z_NOSE + 0.8, -44.6, Z_COWL, Z_ROOF0, Z_ROOF1, Z_DECK, DOOR_Z0, DOOR_Z1, -10.4, 11.9, 13.3, 19.6,
           Z_TAIL - 0.5, Z_TAIL - 0.2, Z_TAIL}
    zs |= set(np.round(np.arange(Z_COWL, Z_ROOF0, 0.75), 3)) | set(np.round(np.arange(Z_ROOF1, Z_DECK, 0.75), 3))
    return sorted(z for z in zs if Z_NOSE <= z <= Z_TAIL)


# body ring points, right half (x >= 0 is mirrored to -x for the other side); index meaning:
# 0 bottom centre, 1, 2 under the rocker, 3 the rocker's round, 4 low side, 5 mid side, 6 below the crease, 7 crease,
# 8 the shoulder, 9 top edge, 10, 11 top to the centre
BODY_N = 12


def body_ring(z):
    w = float(HALF(z))
    t = float(TOP(z))
    yb = float(BOTTOM(z))
    cr = float(CREASE(z))
    arch = arch_floor(z)
    # nose and tail: the corners round off in plan as the panel closes
    endk = min(1.0, (z - Z_NOSE) / 1.6, (Z_TAIL - z) / 1.2)
    endk = max(0.0, endk)
    if arch is not None:
        yb = max(yb, arch)
    cr = max(cr, yb + 1.2)
    t = max(t, cr + 0.9)
    crown = 0.45
    side_lo = yb + 0.6
    hs = cr - 0.7 - side_lo
    pts = [
        (0, yb), (w * 0.55, yb), (w - 1.8, yb), (w - 0.65, yb + 0.55),
        (w - 0.12, side_lo + hs * 0.3), (w, side_lo + hs * 0.7), (w, cr - 0.7), (w - 0.08, cr),
        (w - 0.6, cr + (t - cr) * 0.6), (w - 1.7, t - 0.18), (w * 0.55, t + crown * 0.75), (0, t + crown)]
    out = []
    for i, (x, y) in enumerate(pts):
        # the very ends pull the sides in a touch (rounded corners in plan)
        if i >= 1:
            x = x - (1 - endk) * 0.9 * min(1.0, x / max(w, 1e-3))
        out.append((x, y, z))
    return out


def full_ring(half):
    """Right-half points (bottom centre .. top centre) to a full ring: -x side reversed then the +x side."""
    left = [(-x, y, z) for (x, y, z) in reversed(half)]
    return left[:-1] + half if False else left + half[1:]


# the cabin ring, right half: 0 belt, 1 belt molding top, 2 side glass top, 3 roof edge, 4 roof corner, 5, 6 top centre
CAB_N = 7


def cabin_ring(z):
    b = float(TOP(z)) - 0.15
    wb = float(HALF(z)) - 1.55
    wr = float(ROOFW(z))
    y = roof_y(z)
    h = max(0.0, y - b)
    crown = 0.35 * min(1.0, h / 4)

    def frac(y_):
        return 0 if h < 1e-6 else (y_ - b) / h
    y1 = b + min(0.45, h * 0.2)
    y2 = b + max(h - 1.25, h * 0.55)
    y3 = b + max(h - 0.5, h * 0.82)
    y4 = b + max(h - 0.06, h * 0.97)

    def xs(yy):
        k = frac(yy)
        return wb + (wr + 0.45 - wb) * min(1.0, k * h / max(h - 1.25, 1e-3)) if h > 1.3 else wb + (wr - wb) * k
    pts = [(wb, b), (wb - 0.3, y1), (xs(y2), y2), (wr + 0.15, y3), (wr - 1.0, y4), (wr * 0.5, y + crown * 0.7), (0, y + crown)]
    return [(x, yy, z) for x, yy in pts]


def mirror_ring(half, flip):
    return [((-x if flip else x), y, z) for (x, y, z) in half]


def door_card_uv(p):
    x, y, z = p
    return region_uv(R_DOORCARD, (z - DOOR_Z0) / (DOOR_Z1 - DOOR_Z0) if x >= 0 else (DOOR_Z1 - z) / (DOOR_Z1 - DOOR_Z0),
                     float(np.clip((18.4 - y) / (18.4 - 6.0), 0, 1)))


def hood_uv(p):
    x, y, z = p
    return region_uv(R_HOOD, (x + 16) / 32, (z - (Z_NOSE - 0.4)) / ((Z_COWL + 0.4) - (Z_NOSE - 0.4)))


def build_body():
    zs = z_samples()
    halves = [body_ring(z) for z in zs]
    for side in (1, -1):
        rings = [mirror_ring(h, side < 0) for h in halves]
        if side < 0:
            rings = [list(reversed(r)) for r in rings]           # keep the winding outward on the mirrored side

        def ri(r):
            """Ring segment index as on the right half (0 = bottom centre segment)."""
            return r if side > 0 else BODY_N - 2 - r

        def mats(s, r):
            j = ri(r)
            z = 0.5 * (zs[s] + zs[s + 1])
            under_cabin = Z_COWL + 0.2 < z < Z_DECK - 0.2
            in_arch = arch_floor(z) is not None
            rocker = -19.0 < z < 19.0
            if j <= 1:
                return ('well' if in_arch else 'under', None, ())
            if j == 2:
                return ('well' if in_arch else 'carbon' if rocker else 'under', None, ())
            if j == 3:
                return ('carbon' if rocker else 'paint', None, ('gloss',) if not rocker else ())
            if j >= 9 and under_cabin:
                return None
            if j >= 8 and z < Z_COWL + 0.2:
                return ('paint', hood_uv, ('gloss',))
            if 4 <= j <= 8:
                return ('paint', side_uv, ('gloss',))
            return ('paint', None, ('gloss',))

        def bone_of(s, r):
            z = 0.5 * (zs[s] + zs[s + 1])
            j = ri(r)
            if side < 0 and DOOR_Z0 <= z <= DOOR_Z1 and 4 <= j <= 8:
                return 'door_r'
            return 'body'

        def inner(s, r):
            z = 0.5 * (zs[s] + zs[s + 1])
            j = ri(r)
            if DOOR_Z0 < z < DOOR_Z1 and 4 <= j <= 8:
                return ('interior', door_card_uv)
            if Z_COWL < z < Z_DECK - 1 and 4 <= j <= 8:
                return 'interior'
            return None
        creases = [ri(c) for c in (3, 7, 9)] if side > 0 else [BODY_N - 1 - c for c in (3, 7, 9)]
        loft(rings, mats, bone_of, crease_r=sorted(creases), inner=inner)

    # the nose and the tail: closing panels painted with the fascia and the tail
    for end, z in (('front', zs[0]), ('tail', zs[-1])):
        h = body_ring(z)
        ring = [(-x, y, z) for (x, y, _) in reversed(h)] + [(x, y, z) for (x, y, _) in h[1:]]
        cy = 0.5 * (h[0][1] + h[-1][1])
        c = (0.0, cy, z)
        n = (0, 0, -1) if end == 'front' else (0, 0, 1)
        for i in range(len(ring) - 1):
            a, b2 = ring[i], ring[i + 1]
            tri = [c, b2, a] if end == 'front' else [c, a, b2]
            if end == 'front':
                uvs = [region_uv(R_FRONT, (p[0] + 18) / 36, (20 - p[1]) / 18) for p in tri]
            else:
                uvs = [region_uv(R_TAIL, (18 - p[0]) / 36, (22 - p[1]) / 20) for p in tri]
            M.quad(tri, [n] * 3, uvs, 'body', 'opaque', ('lamps', 'gloss') if end == 'front' else ('gloss',))


def build_cabin():
    zs = [z for z in z_samples() if Z_COWL <= z <= Z_DECK]
    halves = [cabin_ring(z) for z in zs]
    for side in (1, -1):
        rings = [mirror_ring(h, side < 0) for h in halves]
        if side < 0:
            rings = [list(reversed(r)) for r in rings]

        def ri(r):
            return r if side > 0 else CAB_N - 2 - r

        def mats(s, r):
            k = ri(r)
            z = 0.5 * (zs[s] + zs[s + 1])
            if k == 0:
                return ('trim', None, ())
            if k == 1:
                if -10.4 < z < 11.9 or 13.3 < z < 19.6:
                    return ('glass', None, ('gloss',))
                if 11.9 <= z <= 13.3:
                    return ('trim', None, ())
                return ('paint', None, ('gloss',))
            if k == 2:
                return ('trim', None, ()) if -10.4 < z < 19.6 else ('paint', None, ('gloss',))
            if k == 3:
                return ('paint', None, ('gloss',))
            # the top: windshield, roof, back glass
            if z < Z_ROOF0 - 0.1 or z > Z_ROOF1 + 0.1:
                return ('glass', None, ('gloss',))
            return ('paint', None, ('gloss',))

        def bone_of(s, r):
            z = 0.5 * (zs[s] + zs[s + 1])
            if side < 0 and DOOR_Z0 <= z <= DOOR_Z1 and ri(r) <= 2:
                return 'door_r'
            return 'body'

        def inner(s, r):
            k = ri(r)
            z = 0.5 * (zs[s] + zs[s + 1])
            if k >= 3 and Z_ROOF0 - 0.1 <= z <= Z_ROOF1 + 0.1:
                return 'headliner'
            if k == 3 or (k == 2 and not (-10.4 < z < 19.6)) or (k == 1 and not (-10.4 < z < 11.9 or 13.3 < z < 19.6)):
                return 'headliner'
            return None
        creases = sorted([ri(c) for c in (1, 3)] if side > 0 else [CAB_N - 1 - c for c in (1, 3)])
        cs = [zs.index(Z_ROOF0), zs.index(Z_ROOF1)]
        loft(rings, mats, bone_of, crease_r=creases, crease_s=cs, inner=inner)


# ---------------------------------------------------------------- primitives
def box(c0, c1, mat, bone='body', group='opaque', extra=(), bevel=0.0, uvmap=None, rot=None, pivot=None, uvface=None):
    """An axis-aligned box from c0 to c1, its edges chamfered by `bevel`; rotated by rot (degrees about X, Y, Z in that
    order, right-handed: a positive X turn tips the top back toward +Z, the car's rear) round pivot. uvmap(face, p) -> uv for detail textures."""
    x0, y0, z0 = c0
    x1, y1, z1 = c1
    b = min(bevel, (x1 - x0) / 2.01, (y1 - y0) / 2.01, (z1 - z0) / 2.01)
    R = np.eye(3)
    if rot:
        ax, ay, az = (math.radians(v) for v in rot)
        Rx = np.array([[1, 0, 0], [0, math.cos(ax), -math.sin(ax)], [0, math.sin(ax), math.cos(ax)]])
        Ry = np.array([[math.cos(ay), 0, math.sin(ay)], [0, 1, 0], [-math.sin(ay), 0, math.cos(ay)]])
        Rz = np.array([[math.cos(az), -math.sin(az), 0], [math.sin(az), math.cos(az), 0], [0, 0, 1]])
        R = Rz @ Ry @ Rx
    pv = np.array(pivot if pivot is not None else ((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2), float)

    def T(p):
        return tuple(R @ (np.array(p, float) - pv) + pv)

    def face(pts, mat_):
        q = [T(p) for p in pts]
        uvs = uvface(pts) if uvface else [uvmap(p) for p in pts] if uvmap else sw(mat_)
        M.flat(q, uvs, bone, group, extra)
    X0, X1, Y0, Y1, Z0, Z1 = x0 + b, x1 - b, y0 + b, y1 - b, z0 + b, z1 - b
    # six faces
    face([(X0, Y0, z0), (X0, Y1, z0), (X1, Y1, z0), (X1, Y0, z0)], mat)          # -Z
    face([(X1, Y0, z1), (X1, Y1, z1), (X0, Y1, z1), (X0, Y0, z1)], mat)          # +Z
    face([(x0, Y0, Z1), (x0, Y1, Z1), (x0, Y1, Z0), (x0, Y0, Z0)], mat)          # -X
    face([(x1, Y0, Z0), (x1, Y1, Z0), (x1, Y1, Z1), (x1, Y0, Z1)], mat)          # +X
    face([(X0, y1, Z0), (X0, y1, Z1), (X1, y1, Z1), (X1, y1, Z0)], mat)          # +Y
    face([(X0, y0, Z1), (X0, y0, Z0), (X1, y0, Z0), (X1, y0, Z1)], mat)          # -Y
    if b <= 0:
        return
    # edge chamfers
    face([(X0, Y1, z0), (X0, y1, Z0), (X1, y1, Z0), (X1, Y1, z0)], mat)
    face([(X1, Y1, z1), (X1, y1, Z1), (X0, y1, Z1), (X0, Y1, z1)], mat)
    face([(X1, Y0, z0), (X1, y0, Z0), (X0, y0, Z0), (X0, Y0, z0)], mat)
    face([(X0, Y0, z1), (X0, y0, Z1), (X1, y0, Z1), (X1, Y0, z1)], mat)
    face([(x0, Y1, Z0), (X0, y1, Z0), (X0, y1, Z1), (x0, Y1, Z1)], mat)
    face([(x1, Y1, Z1), (X1, y1, Z1), (X1, y1, Z0), (x1, Y1, Z0)], mat)
    face([(x0, Y0, Z1), (X0, y0, Z1), (X0, y0, Z0), (x0, Y0, Z0)], mat)
    face([(x1, Y0, Z0), (X1, y0, Z0), (X1, y0, Z1), (x1, Y0, Z1)], mat)
    face([(X0, Y0, z0), (x0, Y0, Z0), (x0, Y1, Z0), (X0, Y1, z0)], mat)
    face([(X1, Y1, z0), (x1, Y1, Z0), (x1, Y0, Z0), (X1, Y0, z0)], mat)
    face([(X1, Y0, z1), (x1, Y0, Z1), (x1, Y1, Z1), (X1, Y1, z1)], mat)
    face([(X0, Y1, z1), (x0, Y1, Z1), (x0, Y0, Z1), (X0, Y0, z1)], mat)
    # corners
    for sx in (0, 1):
        for sy in (0, 1):
            for sz in (0, 1):
                px = (x0, x1)[sx]
                py = (y0, y1)[sy]
                pz = (z0, z1)[sz]
                Xi = (X0, X1)[sx]
                Yi = (Y0, Y1)[sy]
                Zi = (Z0, Z1)[sz]
                tri = [(px, Yi, Zi), (Xi, py, Zi), (Xi, Yi, pz)]
                if (sx + sy + sz) % 2 == 0:
                    tri = tri[::-1]
                face(tri, mat)


def revolve(profile, axis_c, seg, bone, mat_of, uv_of=None, group='opaque', extra=(), side=1):
    """A surface turned about the X axis through axis_c: profile = [(dx, r)], dx along X (times side), r the radius.
    mat_of(i) -> material for the band from profile point i to i+1; uv_of(i, a, k) -> uv (a 0..1 round, k 0..1 along)."""
    cx, cy, cz = axis_c
    for i in range(len(profile) - 1):
        (d0, r0), (d1, r1) = profile[i], profile[i + 1]
        mat = mat_of(i)
        if mat is None:
            continue
        for j in range(seg):
            a0, a1 = 2 * math.pi * j / seg, 2 * math.pi * (j + 1) / seg
            pts, nrm = [], []
            for (d, r, a) in ((d0, r0, a0), (d1, r1, a0), (d1, r1, a1), (d0, r0, a1)):
                pts.append((cx + d * side, cy + r * math.cos(a), cz + r * math.sin(a)))
            # normal: the profile's tangent turned a quarter (outward from the car for side 1), mirrored for side -1
            tr = r1 - r0
            nx_, nr = -tr * side, d1 - d0
            for (a) in (a0, a0, a1, a1):
                nrm.append((nx_, nr * math.cos(a), nr * math.sin(a)))
            if side < 0:
                pts, nrm = pts[::-1], nrm[::-1]
            if uv_of:
                ks = (0, 1, 1, 0) if side > 0 else (0, 1, 1, 0)[::-1]
                as_ = (j / seg, j / seg, (j + 1) / seg, (j + 1) / seg) if side > 0 else ((j + 1) / seg, (j + 1) / seg, j / seg, j / seg)
                uvs = [uv_of(i, aa, kk) for aa, kk in zip(as_, ks)]
            else:
                uvs = sw(mat)
            M.quad(pts, nrm, uvs, bone, group if not mat.startswith('glass') else 'glass', extra)


def disc(center, r, normal_axis, sign, bone, uv_center_region=None, mat='trim', seg=20, group='opaque', extra=()):
    """A flat disc facing along +-X or +-Z (normal_axis 'x' or 'z'), a fan of triangles; a region maps onto it whole."""
    cx, cy, cz = center
    n = (sign, 0, 0) if normal_axis == 'x' else (0, 0, sign)
    for j in range(seg):
        a0, a1 = 2 * math.pi * j / seg, 2 * math.pi * (j + 1) / seg
        if normal_axis == 'x':
            p0 = (cx, cy + r * math.cos(a0), cz + r * math.sin(a0))
            p1 = (cx, cy + r * math.cos(a1), cz + r * math.sin(a1))
        else:
            p0 = (cx + r * math.cos(a0), cy + r * math.sin(a0), cz)
            p1 = (cx + r * math.cos(a1), cy + r * math.sin(a1), cz)
        tri = [center, p0, p1]
        test = np.cross(np.array(p0) - center, np.array(p1) - center)
        if np.dot(test, n) < 0:
            tri = [center, p1, p0]
        if uv_center_region:
            def uvp(p):
                if normal_axis == 'x':
                    u, v = (p[2] - cz) / (2 * r) + 0.5, 0.5 - (p[1] - cy) / (2 * r)
                else:
                    u, v = (p[0] - cx) / (2 * r) * -sign + 0.5, 0.5 - (p[1] - cy) / (2 * r)
                return region_uv(uv_center_region, u, v)
            uvs = [uvp(p) for p in tri]
        else:
            uvs = sw(mat)
        M.quad(tri, [n] * 3, uvs, bone, group, extra)


def cylinder_z(c, r, z0, z1, mat, bone='body', seg=16, cap_mat=None, group='opaque', extra=()):
    """A cylinder along Z (exhaust tips, lamp bodies)."""
    cx, cy = c
    for j in range(seg):
        a0, a1 = 2 * math.pi * j / seg, 2 * math.pi * (j + 1) / seg
        pts = [(cx + r * math.cos(a0), cy + r * math.sin(a0), z0), (cx + r * math.cos(a1), cy + r * math.sin(a1), z0),
               (cx + r * math.cos(a1), cy + r * math.sin(a1), z1), (cx + r * math.cos(a0), cy + r * math.sin(a0), z1)]
        nrm = [(math.cos(a0), math.sin(a0), 0), (math.cos(a1), math.sin(a1), 0), (math.cos(a1), math.sin(a1), 0), (math.cos(a0), math.sin(a0), 0)]
        M.quad(pts, nrm, sw(mat), bone, group, extra)


# ---------------------------------------------------------------- parts
def build_wheels():
    for name, sx, z in (('fl', 1, Z_FAXLE), ('fr', -1, Z_FAXLE), ('rl', 1, Z_RAXLE), ('rr', -1, Z_RAXLE)):
        bone = 'wheel_' + name
        c = (sx * WHEEL_X, WHEEL_R, z)
        hw = TYRE_W / 2
        rim_r = 4.55
        # the tyre turned from its section: inner bead, sidewall, shoulder, tread, shoulder, sidewall, outer bead
        prof = [(-hw + 0.1, rim_r), (-hw - 0.08, rim_r + 0.7), (-hw, 5.8), (-hw + 0.45, 6.3), (-hw + 1.1, WHEEL_R),
                (hw - 1.1, WHEEL_R), (hw - 0.45, 6.3), (hw, 5.8), (hw + 0.08, rim_r + 0.7), (hw - 0.1, rim_r)]

        def tmat(i):
            return 'rubber'

        def tuv(i, a, k, prof=prof, sx=sx):
            if i == 4:
                return region_uv(R_TREAD, a, k)
            r = prof[i][1] + (prof[i + 1][1] - prof[i][1]) * k
            return region_uv(R_SIDEWALL, a if sx > 0 else 1 - a, float(np.clip((WHEEL_R - r) / (WHEEL_R - rim_r), 0, 1)))
        revolve(prof, c, 28, bone, tmat, tuv, side=sx)
        # the rim: the barrel inside, the lip, the face's dish
        revolve([(hw - 0.15, rim_r), (hw - 0.15, rim_r - 0.4), (hw - 0.6, rim_r - 0.45), (-hw + 0.4, rim_r - 0.45)], c, 28, bone,
                lambda i: 'gun_hi' if i == 0 else 'gun' if i == 1 else 'gun_dark', side=sx)
        # five spokes from the hub to the lip, a little concave, gunmetal with a brighter face
        face_x = sx * (WHEEL_X + hw - 0.55)
        back_x = sx * (WHEEL_X + hw - 1.25)
        for k in range(5):
            a = 2 * math.pi * k / 5 + math.pi / 2
            for half, wid in ((0, 0.62), (1, 0.42)):
                pass
            dirv = np.array([math.cos(a), math.sin(a)])
            perp = np.array([-dirv[1], dirv[0]])
            r0, r1 = 1.15, rim_r - 0.42
            w0, w1 = 0.62, 0.42
            q = []
            for rr, ww, xx in ((r0, w0, face_x + sx * 0.25), (r1, w1, face_x)):
                for s2 in (-1, 1):
                    yy, zz = c[1] + dirv[0] * rr + perp[0] * ww * s2, c[2] + dirv[1] * rr + perp[1] * ww * s2
                    q.append((xx, yy, zz))
            fpts = [q[0], q[2], q[3], q[1]]
            M.flat(fpts if sx > 0 else fpts[::-1], sw('gun_hi'), bone)
            bpts = [(back_x, p[1], p[2]) for p in fpts]
            M.flat(bpts[::-1] if sx > 0 else bpts, sw('gun_dark'), bone)
            for e in ((0, 1), (2, 3)):
                a_, b_ = fpts[e[0]], fpts[e[1]]
                side_q = [a_, (back_x, a_[1], a_[2]), (back_x, b_[1], b_[2]), b_]
                M.flat(side_q if sx > 0 else side_q[::-1], sw('gun'), bone)
        # the hub and its centre cap
        disc((face_x + sx * 0.3, c[1], c[2]), 1.25, 'x', sx, bone, mat='gun_hi', seg=14)
        disc((face_x + sx * 0.36, c[1], c[2]), 0.55, 'x', sx, bone, mat='red_badge', seg=10)
        for k in range(5):
            la = 2 * math.pi * k / 5 + math.pi / 2 + math.pi / 5
            disc((face_x + sx * 0.33, c[1] + math.cos(la) * 0.88, c[2] + math.sin(la) * 0.88), 0.17, 'x', sx, bone, mat='chrome', seg=6)
        # behind the spokes: the disc (on the wheel, it turns) and the caliper (on the knuckle, it does not)
        disc((sx * (WHEEL_X + 0.3), c[1], c[2]), 3.5, 'x', sx, bone, mat='disc', seg=22)
        cal_bone = 'steer_' + name if name in ('fl', 'fr') else 'body'
        ca = math.radians(35 if z < 0 else 145)
        cy, cz = c[1] + math.sin(ca) * 2.9, c[2] - math.cos(ca) * 2.9 * (1 if z < 0 else 1)
        box((sx * (WHEEL_X + 0.55) - 0.6, cy - 1.0, cz - 1.5), (sx * (WHEEL_X + 0.55) + 0.6, cy + 1.0, cz + 1.5), 'caliper', cal_bone,
            bevel=0.25, rot=(math.degrees(-ca) + 90 if z < 0 else math.degrees(ca) - 90, 0, 0))


def build_wells():
    """Liners in the arches (so nothing hollow shows round the tyres) and the floor under the car."""
    for z0 in (Z_FAXLE, Z_RAXLE):
        for sx in (1, -1):
            x_in, x_out = sx * 10.8, sx * 17.6
            seg = 14
            for j in range(seg):
                a0 = math.pi * (-0.08 + 1.16 * j / seg)
                a1 = math.pi * (-0.08 + 1.16 * (j + 1) / seg)
                r = ARCH_R - 0.05
                p = [(x_in, WHEEL_R + r * math.sin(a0), z0 + r * math.cos(a0)), (x_out, WHEEL_R + r * math.sin(a0), z0 + r * math.cos(a0)),
                     (x_out, WHEEL_R + r * math.sin(a1), z0 + r * math.cos(a1)), (x_in, WHEEL_R + r * math.sin(a1), z0 + r * math.cos(a1))]
                n = [(0, -math.sin(a), -math.cos(a)) for a in (a0, a0, a1, a1)]
                if sx < 0:
                    p, n = p[::-1], n[::-1]
                M.quad(p, n, sw('well'), 'body')
            # the inner wall of the well
            box((min(x_in, x_in - sx * 0.3), 3.0, z0 - ARCH_R), (max(x_in, x_in - sx * 0.3), WHEEL_R + ARCH_R, z0 + ARCH_R), 'well')
    box((-15.5, 2.9, -44.0), (15.5, 3.2, 46.0), 'under')


def build_interior():
    # floor, tunnel, firewall, the shelf behind the rear seats
    box((-16.3, 3.6, Z_COWL), (16.3, 4.2, 30.5), 'carpet')
    box((-2.6, 4.2, Z_COWL), (2.6, 7.4, 14.0), 'carpet', bevel=0.6)
    box((-16.3, 4.0, Z_COWL - 0.6), (16.3, 17.8, Z_COWL), 'dash', uvface=planar(R_DASH))
    box((-15.8, 4.0, 28.5), (15.8, 18.6, 30.5), 'interior')
    box((-15.6, 18.4, 24.0), (15.6, 18.9, Z_DECK - 0.6), 'carpet')
    # the dash: its top from the windshield back, its face, the binnacle over the gauges
    box((-16.2, 15.0, Z_COWL - 0.2), (16.2, 19.4, -7.4), 'dash', bevel=0.8, uvface=planar(R_DASH, 3.5))
    box((-16.0, 11.5, -9.0), (16.0, 15.6, -6.6), 'dash', bevel=0.7, uvface=planar(R_DASH, 3.5))
    box((-12.8, 18.6, -10.6), (-3.2, 20.5, -6.6), 'dash', bevel=0.7, uvface=planar(R_DASH))   # the binnacle (driver on -X)
    # a carbon strip across the dash's face, the vents in it (the outer pair and the centre's)
    box((-15.6, 13.0, -6.7), (15.6, 14.6, -6.45), 'carbon', uvface=planar(R_CARBON, 4))
    for vx in (-14.2, -2.0, 2.0, 14.2):
        box((vx - 1.3, 13.15, -6.5), (vx + 1.3, 14.45, -6.35), 'trim')
        for k in range(3):
            box((vx - 1.2, 13.4 + k * 0.4, -6.37), (vx + 1.2, 13.52 + k * 0.4, -6.3), 'gun')
    # the gauge face, lit (the renderer draws the needles over it)
    gx0, gx1, gy0, gy1, gz = -12.2, -3.8, 15.9, 19.3, -6.75
    pts = [(gx1, gy0, gz), (gx1, gy1, gz), (gx0, gy1, gz), (gx0, gy0, gz)]
    uvs = [region_uv(R_GAUGES, (gx1 - p[0]) / (gx1 - gx0), (gy1 - p[1]) / (gy1 - gy0)) for p in pts]
    M.flat(pts, uvs, 'body', 'opaque', ('gauges',))
    # the centre stack: the multi-function display on top, the climate panel under it; the console and the shifter
    box((-3.0, 8.0, -7.4), (3.0, 16.6, -5.4), 'carbon', bevel=0.5, uvface=planar(R_CARBON, 5))
    pts = [(2.4, 14.6, -5.38), (2.4, 16.2, -5.38), (-2.4, 16.2, -5.38), (-2.4, 14.6, -5.38)]
    uvs = [region_uv(R_MFD, (2.4 - p[0]) / 4.8, (16.2 - p[1]) / 1.6) for p in pts]
    M.flat(pts, uvs, 'body', 'opaque', ('gauges',))
    box((-2.0, 11.0, -5.5), (2.0, 13.4, -5.3), 'alu')
    box((-2.4, 7.4, -5.6), (2.4, 9.0, 8.0), 'carbon', bevel=0.5, uvface=planar(R_CARBON, 5))
    for k in range(3):                                                              # the climate knobs
        disc((-1.3 + k * 1.3, 10.2, -5.36), 0.42, 'z', 1, 'body', mat='alu', seg=10)
    box((-0.25, 9.0, -0.6), (0.25, 11.4, -0.1), 'alu')                              # shifter
    box((-0.7, 11.4, -0.9), (0.7, 12.6, 0.4), 'trim', bevel=0.35)
    box((-0.9, 9.0, -1.4), (0.9, 9.3, 0.9), 'trim')                                  # its boot
    # the steering column from the dash to the wheel
    box((WHEEL_C[0] - 0.8, WHEEL_C[1] - 1.6, -7.6), (WHEEL_C[0] + 0.8, WHEEL_C[1] - 0.2, WHEEL_C[2] - 0.3), 'trim', bevel=0.3,
        rot=(24, 0, 0), pivot=(WHEEL_C[0], WHEEL_C[1], WHEEL_C[2]))
    # the door cards' arm rests and pulls; the sills
    for sx in (1, -1):
        b = 'door_r' if sx < 0 else 'body'
        box((sx * 15.6 - 0.7, 11.4, -9.5), (sx * 15.6 + 0.7, 12.4, 4.0), 'interior', b, bevel=0.3)
        box((sx * 16.2 - 0.6, 3.6, -12.0), (sx * 16.2 + 0.6, 6.0, 12.0), 'alu' if False else 'trim')
    # the buckets: cushion, bolsters, backrest raked, headrest
    for sx in (1, -1):
        x = sx * SEAT_X
        fab = lambda p: region_uv(R_FABRIC, 0.5, 0.5)
        box((x - 4.4, 4.2, SEAT_Z - 5.5), (x + 4.4, SEAT_Y - 0.6, SEAT_Z + 3.0), 'seat', bevel=0.9, rot=(6, 0, 0), uvface=planar(R_FABRIC, 12))
        box((x - 2.6, SEAT_Y - 0.7, SEAT_Z - 5.0), (x + 2.6, SEAT_Y, SEAT_Z + 2.6), 'seat_red', bevel=0.3, rot=(6, 0, 0))
        for s2 in (-1, 1):
            box((x + s2 * 3.7 - 0.8, SEAT_Y - 0.6, SEAT_Z - 5.2), (x + s2 * 3.7 + 0.8, SEAT_Y + 1.2, SEAT_Z + 2.8), 'seat', bevel=0.5, rot=(6, 0, 0))
        back = (x, SEAT_Y, SEAT_Z + 3.0)
        box((x - 4.4, SEAT_Y - 1.0, SEAT_Z + 2.6), (x + 4.4, SEAT_Y + 12.4, SEAT_Z + 5.0), 'seat', bevel=0.9, rot=(14, 0, 0), pivot=back,
            uvface=planar(R_FABRIC, 9))
        box((x - 2.6, SEAT_Y + 0.4, SEAT_Z + 2.35), (x + 2.6, SEAT_Y + 11.0, SEAT_Z + 2.7), 'seat_red', bevel=0.2, rot=(14, 0, 0), pivot=back)
        for s2 in (-1, 1):
            box((x + s2 * 3.9 - 0.8, SEAT_Y, SEAT_Z + 1.8), (x + s2 * 3.9 + 0.8, SEAT_Y + 10.0, SEAT_Z + 4.6), 'seat', bevel=0.5, rot=(14, 0, 0), pivot=back)
        box((x - 2.1, SEAT_Y + 12.6, SEAT_Z + 2.9), (x + 2.1, SEAT_Y + 15.6, SEAT_Z + 4.9), 'seat', bevel=0.8, rot=(14, 0, 0), pivot=back)
    # the rear seats: two small cushions and their backs against the shelf wall
    for sx in (1, -1):
        x = sx * 7.4
        box((x - 4.6, 4.2, 16.0), (x + 4.6, 8.6, 22.6), 'seat', bevel=0.9)
        box((x - 4.6, 8.0, 22.4), (x + 4.6, 18.4, 25.0), 'seat', bevel=0.9, rot=(18, 0, 0), pivot=(x, 8.0, 22.4))
    # the rear-view mirror (2026-10-07, the user: "it is not attached to the roof"): its stem went up through the clear
    # windshield to nothing, its glass faced the windshield. Now a black ceramic pad bonded under the glass at its top (as
    # on a real windshield: what it hangs from reads), the stem down from it, the housing tilted to the driver (on -X), the
    # glass on its back, toward you
    # the pad: a black band under the glass from the mount back under the roof's front edge, following the glass's curve
    # in short tilted pieces (it stopped short of the roof: with the clear glass between, it still read as floating)
    zs = np.arange(-2.6, 3.01, 0.4)
    for z0, z1 in zip(zs[:-1], zs[1:]):
        y0, y1 = float(roof_y(z0)) - 0.62, float(roof_y(z1)) - 0.62
        tilt = -math.degrees(math.atan2(y1 - y0, z1 - z0))
        zc, yc = 0.5 * (z0 + z1), 0.5 * (y0 + y1)
        half = 0.5 * math.hypot(z1 - z0, y1 - y0) + 0.03
        box((-3.0, yc - 0.1, zc - half), (3.0, yc + 0.1, zc + half), 'trim', rot=(tilt, 0, 0), pivot=(0, yc, zc))
    py = float(roof_y(-0.7)) - 0.62
    box((-0.32, 25.5, -0.95), (0.32, py + 0.05, -0.4), 'trim', bevel=0.12)
    mc = (0.0, 25.15, -1.2)
    box((-2.6, 24.45, -1.55), (2.6, 25.85, -0.85), 'trim', bevel=0.3, rot=(8, -14, 0), pivot=mc)
    box((-2.35, 24.65, -0.86), (2.35, 25.65, -0.8), 'mirror', rot=(8, -14, 0), pivot=mc, uvface=mirror_face)


def build_steering_wheel():
    """On its own bone, built square to the car (the bone's rotation rakes it): a rim, three spokes, the hub."""
    cx, cy, cz = WHEEL_C
    R, tube, seg = 3.05, 0.4, 26
    for j in range(seg):
        for k in range(6):
            a0, a1 = 2 * math.pi * j / seg, 2 * math.pi * (j + 1) / seg
            b0, b1 = 2 * math.pi * k / 6, 2 * math.pi * (k + 1) / 6
            pts, nrm = [], []
            for a, b2 in ((a0, b0), (a1, b0), (a1, b1), (a0, b1)):
                ring = np.array([math.cos(a), math.sin(a), 0])
                n = ring * math.cos(b2) + np.array([0, 0, 1]) * math.sin(b2)
                p = np.array([cx, cy, cz]) + ring * R + n * tube
                pts.append(tuple(p))
                nrm.append(tuple(n))
            M.quad(pts, nrm, sw('trim' if not (5 <= j <= 8) else 'seat_red'), 'steering_wheel')
    for a in (0.0, math.pi, -math.pi / 2):
        d = np.array([math.cos(a), math.sin(a), 0])
        p0 = np.array([cx, cy, cz]) + d * 1.0
        p1 = np.array([cx, cy, cz]) + d * (R - 0.2)
        perp = np.array([-d[1], d[0], 0]) * 0.45
        for zz, mat in ((-0.25, 'alu'), (0.25, 'alu')):
            q = [p0 - perp + [0, 0, zz], p1 - perp + [0, 0, zz], p1 + perp + [0, 0, zz], p0 + perp + [0, 0, zz]]
            M.flat([tuple(v) for v in (q if zz > 0 else q[::-1])], sw(mat), 'steering_wheel')
    box((cx - 1.2, cy - 1.2, cz - 0.6), (cx + 1.2, cy + 1.2, cz + 0.45), 'trim', 'steering_wheel', bevel=0.4)
    disc((cx, cy, cz + 0.47), 0.55, 'z', 1, 'steering_wheel', mat='red_badge', seg=10)


def build_details():
    # the tail lamps: four round discs set into the tail, a dark bezel round each
    for x, inner in ((-12.4, False), (-6.6, True), (6.6, True), (12.4, False)):
        c = (x, 15.0, Z_TAIL)
        cylinder_z((x, 15.0), 2.05, Z_TAIL - 0.2, Z_TAIL + 0.35, 'trim')
        disc((x, 15.0, Z_TAIL + 0.36), 1.9, 'z', 1, 'body', R_TAILLAMP_IN if inner else R_TAILLAMP, group='opaque',
             extra=('lamps', 'brake') if not inner else ('lamps', 'brake', 'reverse'))
    # the rear bumper: its own panel standing out from the tail under the lamps, its face the tail's lower half
    by0, by1, bz0, bz1 = 3.5, 9.0, Z_TAIL - 1.2, Z_TAIL + 0.75
    bx = float(HALF(Z_TAIL - 0.6)) - 0.5
    box((-bx, by0, bz0), (bx, by1, bz1), 'paint', bevel=0.55, extra=('gloss',))
    pts = [(bx - 0.5, by0 + 0.3, bz1 + 0.01), (bx - 0.5, by1 - 0.4, bz1 + 0.01), (-bx + 0.5, by1 - 0.4, bz1 + 0.01), (-bx + 0.5, by0 + 0.3, bz1 + 0.01)]
    uvs = [region_uv(R_TAIL, (18 - p[0]) / 36, (22 - p[1]) / 20) for p in pts]
    M.flat(pts, uvs, 'body', 'opaque', ('gloss',))
    # the exhaust: one big tip under the right-hand side, its dark inside
    cylinder_z((-11.2, 4.7), 1.55, Z_TAIL - 1.0, Z_TAIL + 1.6, 'chrome')
    disc((-11.2, 4.7, Z_TAIL + 1.6), 1.4, 'z', 1, 'body', mat='exhaust_in', seg=16)
    # the wing on its uprights, its end plates, the high stop lamp along its back edge
    for x in (-10.8, 10.8):
        box((x - 0.55, 19.4, 39.0), (x + 0.55, 23.0, 42.6), 'carbon', bevel=0.2, rot=(-8, 0, 0))
    box((-16.4, 22.6, 37.6), (16.4, 23.5, 44.6), 'carbon', bevel=0.35, rot=(-6, 0, 0), pivot=(0, 23, 41), extra=())
    box((-12.0, 23.15, 44.05), (12.0, 23.55, 44.7), 'stop', rot=(-6, 0, 0), pivot=(0, 23, 41), extra=('lamps', 'brake'))
    for x in (-16.6, 16.6):
        box((x - 0.3, 21.6, 37.2), (x + 0.3, 24.6, 45.0), 'carbon', bevel=0.15)
    # mirrors on the doors' front corners (the right one swings with its door). (2026-10-06, the user: "the mirror isn't
    # attached to the car": it hung 2 px off the glass) Each grows out of a sail on the door's top at the side glass's front
    # corner: the sail, an arm out from it, the housing on the arm's end, the glass in its back
    for sx in (1, -1):
        b = 'door_r' if sx < 0 else 'body'

        def X(a, c):
            return (min(sx * a, sx * c), max(sx * a, sx * c))
        xa, xb = X(15.0, 17.05)
        box((xa, 17.0, -12.4), (xb, 19.3, -9.7), 'trim', b, bevel=0.45)                         # the sail
        xa, xb = X(16.6, 18.4)
        box((xa, 18.35, -11.4), (xb, 19.15, -10.4), 'paint', b, bevel=0.3, extra=('gloss',))   # the arm
        xa, xb = X(17.7, 20.5)
        box((xa, 17.85, -11.95), (xb, 20.35, -9.95), 'paint', b, bevel=0.65, extra=('gloss',), rot=(0, sx * -7, 0))
        xa, xb = X(17.95, 20.25)
        box((xa, 18.1, -10.0), (xb, 20.1, -9.86), 'mirror', b, rot=(0, sx * -7, 0), pivot=(sx * 19.1, 19.1, -10.95), uvface=mirror_face)
        # side marker on the front fender, amber
        fx = sx * (float(HALF(-36)) + 0.05)
        box((fx - 0.12, 11.6, -37.4), (fx + 0.12, 12.4, -35.6), 'lamp_amber', 'body', extra=('lamps',))
    # the purple strips under the skirts (lit with the underglow)
    for sx in (1, -1):
        x = sx * (float(HALF(0)) - 1.6)
        pts = [(x - 0.5, 2.95, -18.5), (x - 0.5, 2.95, 18.5), (x + 0.5, 2.95, 18.5), (x + 0.5, 2.95, -18.5)]
        M.flat(pts if sx > 0 else pts[::-1], sw('purple'), 'body', 'opaque', ('underglow',))
    # wipers parked at the bottom of the windshield
    for x0 in (-11.5, 0.5):
        y = float(TOP(Z_COWL)) + 0.35
        box((x0, y, Z_COWL + 0.5), (x0 + 11.0, y + 0.35, Z_COWL + 0.9), 'trim', rot=(0, -6, 0))
    # panel gaps: the doors' shut lines and the hood's, thin and dark just proud of the paint
    for sx in (1, -1):
        for z in (DOOR_Z0, DOOR_Z1):
            ring = body_ring(z)
            for i in range(4, 8):
                (x0, y0, _), (x1, y1, _) = ring[i], ring[i + 1]
                xa, xb = sx * (x0 + 0.04), sx * (x1 + 0.04)
                q = [(xa, y0, z - 0.09), (xa, y0, z + 0.09), (xb, y1, z + 0.09), (xb, y1, z - 0.09)]
                M.flat(q if sx > 0 else q[::-1], sw('trim'), 'door_r' if sx < 0 and z == DOOR_Z1 else 'body')


def build():
    paint_hood()
    paint_front()
    paint_tail()
    paint_details()
    paint_sides()
    paint_interior()
    paint_mirror()
    build_body()
    build_cabin()
    build_wells()
    build_interior()
    build_steering_wheel()
    build_wheels()
    build_details()


# ---------------------------------------------------------------- the rig and the files
def rig():
    def bone(name, pivot, parent=None, rotation=None):
        b = {'name': name, 'pivot': [float(v) for v in pivot]}
        if parent:
            b['parent'] = parent
        if rotation:
            b['rotation'] = [float(v) for v in rotation]
        return b
    hinge = (-(float(HALF(DOOR_Z0)) + 0.1), 10.0, DOOR_Z0)
    bones = [bone('root', (0, 0, 0)), bone('body', (0, 8, 0), 'root'), bone('door_r', hinge, 'body'),
             bone('steering_wheel', WHEEL_C, 'body', rotation=(WHEEL_TILT, 0, 0))]
    for name, sx, z, steer in (('fl', 1, Z_FAXLE, True), ('fr', -1, Z_FAXLE, True), ('rl', 1, Z_RAXLE, False), ('rr', -1, Z_RAXLE, False)):
        parent = 'root'
        if steer:
            parent = 'steer_' + name
            bones.append(bone(parent, (sx * WHEEL_X, WHEEL_R, z), 'root'))
        bones.append(bone('wheel_' + name, (sx * WHEEL_X, WHEEL_R, z), parent))
    return {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': 'geometry.skyline', 'texture_width': TEX, 'texture_height': TEX,
                        'visible_bounds_width': 7, 'visible_bounds_height': 3, 'visible_bounds_offset': [0, 1.0, 0]},
        'bones': bones}]}


def write():
    order = ['opaque', 'glass']
    flagged = ['lamps', 'brake', 'reverse', 'gauges', 'gloss', 'underglow']
    idx = []
    groups = {}
    for g in order:
        groups[g] = [len(idx), len(M.groups.get(g, []))]
        idx.extend(M.groups.get(g, []))
    for g in flagged:
        groups[g] = [len(idx), len(M.groups.get(g, []))]
        idx.extend(M.groups.get(g, []))
    pos = [round(v, 4) for p in M.pos for v in p]
    nrm = [round(v, 3) for n in M.nrm for v in n]
    uv = [round(v, 5) for t in M.uv for v in (t[0], 1 - t[1])]           # stored OBJ-style (the loader flips v)
    joints, weights = [], []
    for b in M.bone:
        joints += [b, 0]
        weights += [1.0, 0.0]
    mesh = {'credit': 'tools/gen_skyline.py', 'texture': 'papon:textures/entity/skyline.png', 'bones': BONES, 'corners': 4,
            'groups': groups, 'positions': pos, 'normals': nrm, 'uvs': uv, 'indices': idx, 'joints': joints, 'weights': weights}
    os.makedirs(os.path.join(ASSETS, 'meshes'), exist_ok=True)
    out = os.path.join(ASSETS, 'meshes/skyline.json')
    with open(out, 'w', newline='\n') as f:
        json.dump(mesh, f, separators=(',', ':'))
    print(out, len(M.pos), 'vertices', len(idx) // 4, 'quads drawn', {k: v[1] // 4 for k, v in groups.items()})
    geo = os.path.join(ASSETS, 'geo/entity/skyline.geo.json')
    with open(geo, 'w', newline='\n') as f:
        json.dump(rig(), f, separators=(',', ':'))
    print(geo)
    A.img.save(os.path.join(ASSETS, 'textures/entity/skyline.png'))
    A.glow.save(os.path.join(ASSETS, 'textures/entity/skyline_lamps.png'))
    print('textures/entity/skyline.png, skyline_lamps.png')


if __name__ == '__main__':
    build()
    write()
