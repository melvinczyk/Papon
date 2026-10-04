#!/usr/bin/env python3
"""Generates the car Ascended Papon makes of his van: a Nissan Skyline GT-R R34 in black, a flaming eagle on the
hood, a purple underglow (drawn by client/CaravanRenderer under it; the skirts' undersides glow here too).

The same rig as the van (gen_caravan.py), so CaravanEntity and its renderer drive it unchanged:
  root > body > door_l, steering_wheel        root > steer_fl > wheel_fl, steer_fr > wheel_fr, wheel_rl, wheel_rr
Front is -Z, the driver's side +X. About the van's scale (1.15x real): 84 px long, 32 wide, 25 tall, wheelbase
50 px, 13 px tyres.

Shape: the R34's: a low wedge, the long hood with its bonnet duct, angular headlights, the big front intake, a
steep windshield to a short roof, a fastback rear window onto a short deck, the GT wing on two uprights, the
four round tail lamps, flared arches, side skirts, five-spoke wheels in gunmetal.

Writes geo/entity/skyline.geo.json, textures/entity/skyline.png (+ _glowmask); animations: the van's file.
"""
import json
import math
import os

from gen_caravan import Van, shade, plain, glass, tire, seat, CHROME, TIRE, SEAT, DASH, ASSETS

PAINT = (22, 22, 26)          # gloss black
PAINT_HI = (64, 66, 78)
CARBON = (34, 34, 38)
GUNMETAL = (70, 72, 78)
PURPLE = (170, 70, 255)
RED_LAMP = (204, 22, 30)


def black(van, f):
    """Gloss black: a cool sheen band along the top of each side, the roof catching the sky."""
    for k in ('nx', 'px', 'nz', 'pz'):
        x0, y0, x1, y1 = van.rect(f[k])
        hgt = max(1, y1 - y0)
        for y in range(y0, y1):
            t = (y - y0) / hgt
            c = PAINT_HI if t < 0.18 else PAINT
            for x in range(x0, x1):
                van.put(x, y, shade(c, 1.0 - 0.15 * t), 0.02)
    x0, y0, x1, y1 = van.rect(f['up'])
    for x in range(x0, x1):
        for y in range(y0, y1):
            van.put(x, y, shade(PAINT_HI if (x + y) % 11 == 0 else PAINT, 1.25), 0.02)


def flame_eagle(van, f):
    """The hood: gloss black under a flaming eagle (the bonnet decal), wings spread across it, head toward the
    front of the car (the up face's bottom rows), flames streaming back over the wings and the tail."""
    black(van, f)
    x0, y0, x1, y1 = van.rect(f['up'])
    w, h = x1 - x0, y1 - y0
    cx = w / 2
    for y in range(h):
        for x in range(w):
            u = (x + 0.5 - cx) / (w / 2)             # -1 .. 1 across
            v = (y + 0.5) / h                       # 0 at the back (windshield) .. 1 at the nose
            body = (u / 0.16) ** 2 + ((v - 0.55) / 0.22) ** 2 < 1
            head = (u / 0.11) ** 2 + ((v - 0.80) / 0.07) ** 2 < 1
            beak = abs(u) < 0.05 and 0.86 < v < 0.92
            # the wings: swept back and up from the shoulders, their lower edge in feather fingers
            au = abs(u)
            wing_top = 0.62 - 0.42 * au
            wing_bot = 0.70 - 0.30 * au + (0.06 if (int(au * 10) % 2) else 0)
            wing = 0.12 < au < 0.95 and wing_top < v < wing_bot
            tail = abs(u) < 0.22 - 0.4 * (0.33 - v) and 0.12 < v < 0.36
            eagle = body or head or wing or tail
            # the flames: tongues streaming back (toward v = 0) off the wings and tail
            tongue = math.sin(u * 19 + 1.3) * 0.5 + 0.5
            reach = (0.10 + 0.22 * tongue) * (1 if au < 0.95 else 0)
            near = (wing_top - reach < v < wing_top and 0.12 < au < 0.98) or (0.02 < v < 0.14 and abs(u) < 0.18 + 0.1 * tongue)
            if eagle:
                if beak:
                    c = (250, 214, 60)
                elif head:
                    c = (250, 246, 236)                  # the eagle's white head
                else:
                    c = (255, 180, 40) if (x + y) % 4 else (255, 214, 90)
                van.put(x0 + x, y0 + y, c)
            elif near:
                t = (wing_top - v) / max(1e-3, reach) if v < wing_top else (0.14 - v) / 0.12
                t = max(0.0, min(1.0, t))
                c = (255, 222, 90) if t < 0.33 else (255, 132, 30) if t < 0.7 else (214, 40, 22)
                van.put(x0 + x, y0 + y, c)
    # the eye
    van.put(x0 + int(cx) + 1, y0 + int(0.80 * h), (20, 20, 20))


def r34_headlight(van, f):
    for k, r in f.items():
        x0, y0, x1, y1 = van.rect(r)
        van.fill(r, (214, 222, 232), 0.04)
        van.lit(r, (255, 252, 230))
        if k == 'nz':
            van.fill((x0, y0, x1, y0 + 1), (30, 30, 34), 0.02)        # the angry brow over the lamps
            van.put((x0 + x1) // 2 - 2, (y0 + y1) // 2, CHROME)
            van.put((x0 + x1) // 2 + 2, (y0 + y1) // 2, CHROME)


def intake(van, f):
    black(van, f)
    x0, y0, x1, y1 = van.rect(f['nz'])
    cx = (x0 + x1) // 2
    # the big lower intake and the grille over it, a mesh; the GT-R badge in red
    van.fill((x0 + 4, y0 + 4, x1 - 4, y1 - 1), (14, 14, 16), 0.02)
    for x in range(x0 + 4, x1 - 4):
        for y in range(y0 + 4, y1 - 1):
            if (x + y) % 2 == 0:
                van.put(x, y, (40, 40, 44))
    van.fill((cx - 6, y0, cx + 6, y0 + 3), (14, 14, 16), 0.02)
    van.fill((cx - 1, y0 + 1, cx + 1, y0 + 2), (220, 30, 30), 0.0)
    # fog lamps
    van.fill((x0 + 1, y0 + 5, x0 + 3, y0 + 7), (240, 240, 220), 0.02)
    van.fill((x1 - 3, y0 + 5, x1 - 1, y0 + 7), (240, 240, 220), 0.02)
    van.lit((x0 + 1, y0 + 5, x0 + 3, y0 + 7), (255, 255, 230))
    van.lit((x1 - 3, y0 + 5, x1 - 1, y0 + 7), (255, 255, 230))


def tail(van, f):
    """The rear panel: four round tail lamps (the R34's), the plate, the GT-R badge."""
    black(van, f)
    x0, y0, x1, y1 = van.rect(f['pz'])
    w = x1 - x0
    for cx in (x0 + 4, x0 + 9, x1 - 10, x1 - 5):
        cy = y0 + 3
        for dx in range(-2, 2):
            for dy in range(-2, 2):
                if (dx + 0.5) ** 2 + (dy + 0.5) ** 2 <= 4.2:
                    van.put(cx + dx, cy + dy, RED_LAMP if (dx + 0.5) ** 2 + (dy + 0.5) ** 2 > 1.2 else (255, 120, 110))
                    van.lit((cx + dx, cy + dy, cx + dx + 1, cy + dy + 1), (255, 40, 40))
    cx = (x0 + x1) // 2
    van.fill((cx - 4, y1 - 5, cx + 4, y1 - 2), (232, 228, 214), 0.02)
    van.fill((cx - 1, y0 + 2, cx + 2, y0 + 3), (200, 200, 206), 0.02)


def five_spoke(van, f):
    for k, r in f.items():
        x0, y0, x1, y1 = van.rect(r)
        van.fill(r, GUNMETAL, 0.04)
        if k in ('px', 'nx'):
            cx, cy = (x0 + x1 - 1) / 2, (y0 + y1 - 1) / 2
            for x in range(x0, x1):
                for y in range(y0, y1):
                    dx, dy = x - cx, y - cy
                    rr = math.hypot(dx, dy)
                    a = (math.atan2(dy, dx) + math.pi) / (2 * math.pi) * 5
                    spoke = abs(a - round(a)) < 0.16
                    if rr > 2.9:
                        van.put(x, y, shade(GUNMETAL, 1.15))
                    elif rr < 1.0:
                        van.put(x, y, (190, 40, 40))           # the centre cap
                    elif not spoke:
                        van.put(x, y, (18, 18, 20))            # the dark between the spokes


def skirt(van, f):
    plain(CARBON)(van, f)
    x0, y0, x1, y1 = van.rect(f['down'])
    van.lit((x0, y0, x1, y1), PURPLE)                           # the underglow strip


def bucket(van, f):
    seat(van, f)
    for r in f.values():
        x0, y0, x1, y1 = van.rect(r)
        for y in range(y0, y1):
            van.put(x0, y, (180, 30, 30))                          # red stitching


def build():
    van = Van()
    van.bone('root', [0, 0, 0])
    van.bone('body', [0, 8, 0], 'root')
    b = 'body'
    # underbody and the engine bay
    van.box(b, [-14, 3, -40], [28, 3, 80], (30, 30, 34))
    van.box(b, [-14, 6, -40], [28, 7, 26], (40, 40, 44))
    # ---- front: the bumper with the big intake, the hood (the flaming eagle) with its duct, the headlights
    van.box(b, [-16, 3, -43], [32, 9, 4], PAINT, paint=intake)
    van.box(b, [-16, 2, -42], [32, 1.5, 3], CARBON, paint=plain(CARBON))                     # the lip
    van.box(b, [-16, 12, -42], [32, 2, 28], PAINT, paint=flame_eagle, pivot=[0, 13, -42], rotation=[4, 0, 0])
    van.box(b, [-4, 14, -30], [8, 0.8, 6], (14, 14, 16), paint=plain((14, 14, 16)))           # the bonnet duct
    for hx in (9, -16):
        van.box(b, [hx, 11.6, -43.4], [7, 2, 2], (214, 222, 232), paint=r34_headlight)
    # ---- the sides: fenders flared over the arches, the doors, the skirts
    for side in (1, -1):
        x = 16 if side > 0 else -17
        van.box(b, [x, 4, -41], [1, 9, 7], PAINT, paint=black)
        van.box(b, [x, 11, -34], [1, 3, 14], PAINT, paint=black)
        van.box(b, [x, 4, -20], [1, 10, 2], PAINT, paint=black)
        flare = 16.6 if side > 0 else -17.8
        for wz in (-26, 25):
            van.box(b, [flare, 11.6, wz - 7.5], [1.2, 1.4, 15], PAINT, paint=black)            # the flared arch lip
        van.box(b, [x - 0.4 * side, 3, -19], [1.4, 2, 37], CARBON, paint=skirt)               # side skirt
        if side < 0:
            van.box(b, [x, 5, -18], [1, 10, 20], PAINT, paint=black)                          # passenger door
        van.box(b, [x, 5, 2], [1, 10, 16], PAINT, paint=black)
        van.box(b, [x, 11, 18], [1, 4, 14], PAINT, paint=black)
        van.box(b, [x, 4, 32], [1, 11, 9], PAINT, paint=black)
        # glass: the side window under the roofline, raked at both ends
        if side < 0:
            van.box(b, [x, 15, -14], [1, 7, 16], (40, 54, 70), paint=lambda v, f: glass(v, f, 130))
        van.box(b, [x, 15, 2], [1, 7, 9], (40, 54, 70), paint=lambda v, f: glass(v, f, 130))
        van.box(b, [x, 15, 11], [1, 6, 5], PAINT, paint=black)                                # C pillar
        van.box(b, [x - 0.2 * side, 15, -19], [1, 1, 34], PAINT, paint=black)                 # the belt line
        mx = 17 if side > 0 else -19.5
        van.box(b, [mx, 15.5, -14], [2.5, 2, 3], PAINT, paint=black)                          # mirror
    # the driver's door (+X)
    van.bone('door_l', [17, 10, -18], b)
    van.box('door_l', [16, 5, -18], [1, 10, 20], PAINT, paint=black)
    van.box('door_l', [16, 15, -14], [1, 7, 16], (40, 54, 70), paint=lambda v, f: glass(v, f, 130))
    # ---- the greenhouse: the windshield, the short roof, the fastback rear glass
    van.box(b, [-14, 14.5, -16], [28, 1, 16], (40, 54, 70), paint=lambda v, f: glass(v, f, 110), pivot=[0, 15, -16], rotation=[38, 0, 0])
    for x in (13.5, -15):
        van.box(b, [x, 14.5, -16], [1.5, 1, 16], PAINT, paint=black, pivot=[0, 15, -16], rotation=[38, 0, 0])
    van.box(b, [-14, 23.5, -4], [28, 1.5, 15], PAINT, paint=black)                            # roof
    van.box(b, [-13, 15, 11], [26, 1, 12], (40, 54, 70), paint=lambda v, f: glass(v, f, 110), pivot=[0, 24, 11], rotation=[-38, 0, 0])
    van.box(b, [-16, 14, 23], [32, 2, 16], PAINT, paint=black)                                # the deck
    # ---- the GT wing on its two uprights
    for x in (10, -11.5):
        van.box(b, [x, 16, 34], [1.5, 4.5, 2.5], CARBON, paint=plain(CARBON))
    van.box(b, [-16, 20.5, 32], [32, 1, 6], CARBON, paint=plain(CARBON), pivot=[0, 21, 35], rotation=[-6, 0, 0])
    for x in (15.5, -16.5):
        van.box(b, [x, 19.5, 32], [1, 3, 6], CARBON, paint=plain(CARBON))                    # end plates
    # ---- rear: the tail panel with the four round lamps, the bumper, the diffuser, twin exhausts
    van.box(b, [-16, 8, 39], [32, 7, 2], PAINT, paint=tail)
    van.box(b, [-16, 3, 39], [32, 5, 3], PAINT, paint=black)
    van.box(b, [-14, 2, 41], [28, 2, 2], CARBON, paint=plain(CARBON))
    for x in (8, 10.5):
        van.box(b, [x, 3, 42], [2, 2, 2], CHROME, paint=plain((150, 150, 156)))
    # ---- inside: dash, buckets, the wheel
    van.box(b, [-14, 12, -20], [28, 4, 5], DASH, paint=plain(DASH))
    van.box(b, [6, 13, -17], [2, 2, 4], DASH, paint=plain(DASH))
    for x in (3, -12):
        van.box(b, [x, 6, -10], [9, 4, 8], SEAT, paint=bucket)
        van.box(b, [x, 10, -3], [9, 12, 2], SEAT, paint=bucket)
    van.box(b, [-12, 6, 4], [24, 4, 7], SEAT, paint=seat)
    van.bone('steering_wheel', [7.5, 17, -15], b, rotation=[-35, 0, 0])
    sw = 'steering_wheel'
    van.box(sw, [5, 19.5, -15.5], [5, 1, 1], (20, 20, 22), paint=plain((20, 20, 22)))
    van.box(sw, [5, 14.5, -15.5], [5, 1, 1], (20, 20, 22), paint=plain((20, 20, 22)))
    van.box(sw, [4, 15.5, -15.5], [1, 4, 1], (20, 20, 22), paint=plain((20, 20, 22)))
    van.box(sw, [10, 15.5, -15.5], [1, 4, 1], (20, 20, 22), paint=plain((20, 20, 22)))
    # ---- wheels: two crossed boxes, five-spoke faces outside
    for name, x, z, steer in (('fl', 1, -26, True), ('fr', -1, -26, True), ('rl', 1, 25, False), ('rr', -1, 25, False)):
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
        for rot in (None, [36, 0, 0]):
            van.box(wb, [cap_x, 2.5, z - 4], [1, 8, 8], GUNMETAL, paint=five_spoke, pivot=[px, 6.5, z] if rot else None, rotation=rot)
    return van


def main():
    van = build()
    bones = [van.bones[n] for n in van.order]
    for bo in bones:
        if not bo['cubes']:
            del bo['cubes']
    geo = {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': 'geometry.skyline', 'texture_width': 512, 'texture_height': 512,
                        'visible_bounds_width': 7, 'visible_bounds_height': 3, 'visible_bounds_offset': [0, 1.0, 0]},
        'bones': bones}]}
    out = os.path.join(ASSETS, 'geo/entity/skyline.geo.json')
    with open(out, 'w', newline='\n') as f:
        json.dump(geo, f, indent='\t')
    print(out)
    print(van.p.save(os.path.join(ASSETS, 'textures/entity/skyline.png')))
    glow = os.path.join(ASSETS, 'textures/entity/skyline_glowmask.png')
    van.glow.save(glow)
    print(glow)


if __name__ == '__main__':
    main()
