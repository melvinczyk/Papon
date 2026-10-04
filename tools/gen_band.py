#!/usr/bin/env python3
"""Generates the people of El Coliseo Papa: the mariachi band and the crowd in the stands.

Writes, under src/main/resources/assets/papon/:
  geo/entity/mariachi.geo.json   textures/entity/mariachi.png    (256x256)
  geo/entity/arena_fan.geo.json  textures/entity/arena_fan.png   (128x128)

Both keep the Walking Crops Papon's body, arms, legs and brows bone for bone at the same UVs
(tools/src/papon_base.png), like every other Papon in the mod.

mariachi   one rig for the whole band (boss/MariachiEntity, BandRole): the renderer shows only the
           instrument of a member's role. Arms split like the boss's (gen_vendor.rig_hands) so reaches
           do not warp what they hold.
  the mouth    the painted bar is taken off the face and rebuilt as a lip rig for the lip sync
               (client/MariachiModel, Rhubarb shapes A-H/X): `mouth` (width) > `lip_up`, `lip_low`
               (drops as the mouth opens), `mouth_in` (the dark inside, scaled down from the upper
               lip), `teeth`, `tongue`
  the suit     black charro trousers with silver conchos and a shirt with a red bow tie, always on;
               `jacket` (+ `sleeve_r/_l`) and `sombrero` are put on in the wake-up, from
               `jacket_chair` (over the chair's back) and `sombrero_floor` (by the chair)
  the chair    `chair`, a top-level bone (stays put while he gets up)
  instruments  `guitar` (the singer's is the same), `vihuela`, `guitarron`, `violin` + `bow`, `trumpet`;
               each also has a `*_floor` copy lying by the chair until he picks it up
  the singer   `mic_stand` (top level, in front of him) and `shades` (aviators)
arena_fan  a fan in the stands, drawn hundreds of times by client/ColiseumCrowdRenderer (no animation
           file: it poses the bones itself). Hats `sombrero`, `cap`, `foam_hat` (a wedge of foam papa),
           props `foam_finger` (right), `flag` (left), `maraca_r/_l`, a `jersey`, and `mouth_in` (cheering).
"""
from PIL import Image

from gen_vendor import Model, bone, cube, rig_hands, band, HAND_R, HAND_L
from geo_util import Painter

BLACK = (30, 28, 34)
BLACK_HI = (52, 50, 58)
SILVER = (206, 210, 218)
GOLD = (228, 182, 70)
GOLD_DK = (176, 128, 40)
WHITE = (238, 236, 228)
RED = (190, 30, 36)
LIP = (107, 82, 45)
LIP_HI = (134, 105, 62)
WOOD = (150, 92, 44)
WOOD_DK = (104, 60, 28)
WOOD_HI = (190, 128, 64)
BRASS = (232, 186, 70)
BRASS_DK = (176, 128, 36)
SPRUCE = (226, 186, 120)


def studs(p, rect, color=SILVER, step=2, row=-1):
    x0, y0, x1, y1 = (int(n) for n in rect)
    y = y1 - 1 if row < 0 else y0 + row
    for x in range(x0, x1, step):
        p.put(x, y, color)


def edge(p, rect, color, sides='tblr'):
    x0, y0, x1, y1 = (int(n) for n in rect)
    for x in range(x0, x1):
        if 't' in sides:
            p.put(x, y0, color)
        if 'b' in sides:
            p.put(x, y1 - 1, color)
    for y in range(y0, y1):
        if 'l' in sides:
            p.put(x0, y, color)
        if 'r' in sides:
            p.put(x1 - 1, y, color)


def unpaint_mouth(m):
    """The face's mouth bar (row 13 of the 16x20 front face at 16,16) back to skin: the lips are cubes now."""
    skin = [(0xc8, 0x97, 0x3a), (0xd9, 0xaa, 0x51)]
    for x in range(18, 30):
        m.p.put(x, 29, skin[0] if x in (18, 29) else skin[1])
        m.p.put(x, 30, skin[0])


def mouth_rig(m, z=-8.0):
    """The lips on the front of the body. Every piece is a block deep, sunk into the body, so only its
    front shows; each sits a hair further out than the one behind it."""
    lip_paint = lambda p, f: [p.put(x, int(f['front'][1]), LIP_HI) for x in range(int(f['front'][0]) + 3, int(f['front'][2]) - 3)]
    m.add(bone('mouth', [0, 10.5, z], 'body'))
    m.add(bone('lip_up', [0, 10.5, z], 'mouth', [m.box([-5, 10, z - 0.16], [10, 1, 1], LIP, extra=lip_paint, noise=0.03)]))
    m.add(bone('mouth_in', [0, 10, z], 'mouth', [m.box([-4, 9, z - 0.06], [8, 1, 1], (52, 20, 16), noise=0.05)]))
    m.add(bone('teeth', [0, 10, z], 'mouth', [m.box([-3.5, 9.4, z - 0.1], [7, 1, 1], WHITE, noise=0.02)]))
    m.add(bone('lip_low', [0, 10, z], 'mouth', [m.box([-4.5, 9.3, z - 0.15], [9, 1, 1], (92, 68, 36), noise=0.03)]))
    m.add(bone('tongue', [0, 9.5, z], 'lip_low', [m.box([-2, 9.4, z - 0.08], [4, 1, 1], (196, 82, 92), noise=0.04)]))


def suit(m):
    """Trousers, shirt, bow tie: what they slept in."""
    def trousers(p, f):
        for s in ('right', 'left', 'front', 'back'):
            p.fill(f[s], BLACK, 0.04)
        for s in ('right', 'left'):
            x0, y0, x1, y1 = (int(n) for n in f[s])
            for y in range(y0, y1, 2):
                p.put((x0 + x1) // 2, y, SILVER)
    for side, x in (('r', -7), ('l', 1)):
        m.add(bone('pant_' + side, [x + 3, 4, 0], 'leg_' + side,
                   [m.box([x, 0, -3], [6, 4, 6], BLACK, extra=trousers, inflate=0.25)]))
    shirt = m.box([-4, 3.6, -8.04], [8, 3, 1], WHITE, noise=0.02,
                  extra=lambda p, f: [p.put(int(f['front'][0]) + 3 + (k % 2), int(f['front'][1]) + k, (210, 206, 196)) for k in range(3)])
    # the mono: two wings and a knot
    tie = [m.box([-3, 4.6, -8.5], [2.5, 1.6, 1], RED, noise=0.04), m.box([0.5, 4.6, -8.5], [2.5, 1.6, 1], RED, noise=0.04),
           m.box([-0.5, 4.9, -8.7], [1, 1, 1], (150, 18, 24), noise=0.02)]
    m.add(bone('shirt', [0, 6, -8], 'body', [shirt] + tie))
    # eyelids, shut while they sleep (and for a blink)
    lid = lambda p, f: [p.put(x, int(f['front'][3]) - 1, (110, 76, 30)) for x in range(int(f['front'][0]), int(f['front'][2]))]
    m.add(bone('eyelids', [0, 19.5, -8], 'body', [m.box([-7, 18, -8.12], [5, 3, 1], (0xd9, 0xaa, 0x51), extra=lid, noise=0.03),
                                                   m.box([2, 18, -8.12], [5, 3, 1], (0xd9, 0xaa, 0x51), extra=lid, noise=0.03)]))


def jacket_paint(p, f):
    for s in ('front', 'back', 'right', 'left'):
        p.fill(f[s], BLACK, 0.04)
        studs(p, f[s])
    x0, y0, x1, y1 = (int(n) for n in f['back'])
    # gold embroidery across the back: a scroll
    for x in range(x0 + 2, x1 - 2):
        if (x - x0) % 3 != 1:
            p.put(x, y0 + 2, GOLD)
    for x in range(x0 + 4, x1 - 4, 4):
        p.put(x, y0 + 3, GOLD)
        p.put(x + 1, y0 + 1, GOLD)


def jacket(m):
    """The charro jacket: short, open at the front, silver botonadura along every edge, gold on the back."""
    jk = [m.box([-8.4, 3.6, 0], [17, 5, 8], BLACK, extra=jacket_paint, inflate=0.1),
          m.box([-8.4, 3.6, -8.4], [4, 5, 9], BLACK, extra=jacket_paint, inflate=0.1),
          m.box([4.4, 3.6, -8.4], [4, 5, 9], BLACK, extra=jacket_paint, inflate=0.1)]
    m.add(bone('jacket', [0, 6, 0], 'body', jk))

    def sleeve(p, f):
        for s in ('front', 'back', 'right', 'left', 'bottom'):
            p.fill(f[s], BLACK, 0.04)
        for s in ('right', 'left'):
            x0, y0, x1, y1 = (int(n) for n in f[s])
            for y in range(y0, y1, 2):
                p.put((x0 + x1) // 2, y, SILVER)
        studs(p, f['front'], GOLD, 1)
    # the upper arm only, on the arm itself (not its stretching mesh): on a long reach the potato arm
    # comes out of the cuff instead of the whole sleeve turning into a long black beam
    m.add(bone('sleeve_r', [-8, 11, 0], 'arm_r', [m.box([-12, 6.5, -3], [4, 5.5, 6], BLACK, extra=sleeve, inflate=0.3)]))
    m.add(bone('sleeve_l', [8, 11, 0], 'arm_l', [m.box([8, 6.5, -3], [4, 5.5, 6], BLACK, extra=sleeve, inflate=0.3)]))
    # the same jacket, hung over the chair's back while he sleeps
    m.add(bone('jacket_chair', [0, 14, 9], 'chair', [
        m.box([-7, 9, 8.6], [14, 7, 1], BLACK, extra=jacket_paint),
        m.box([-7, 16, 7.6], [14, 1, 3], BLACK, extra=lambda p, f: studs(p, f['front'])),
    ]))


def sombrero(m, name, parent, pivot, at=(0, 24, 0), scale=1.0):
    """The mariachi's sombrero: a wide black felt brim turned up at the edge, a tall crown, gold braid."""
    x, y, z = at
    r = 13
    def brim(p, f):
        x0, y0, x1, y1 = (int(n) for n in f['top'])
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
        for yy in range(y0, y1):
            for xx in range(x0, x1):
                d = max(abs(xx + 0.5 - cx), abs(yy + 0.5 - cy))
                if int(d) in (5, 6):
                    p.put(xx, yy, GOLD, 0.05)
                elif int(d) == r - 1:
                    p.put(xx, yy, GOLD_DK, 0.05)
        for s in ('front', 'back', 'left', 'right'):
            p.fill(f[s], GOLD, 0.04)
    def turned(p, f):
        for s in ('front', 'back', 'left', 'right', 'top'):
            p.fill(f[s], BLACK_HI, 0.04)
        studs(p, f['front'], GOLD, 3, 0)
    def crown(p, f):
        for s in ('front', 'back', 'left', 'right'):
            band(p, f[s], 1, GOLD)
            band(p, f[s], 2, GOLD_DK)
            x0, y0, x1, y1 = (int(n) for n in f[s])
            for xx in range(x0, x1, 3):
                p.put(xx, y0 + 4, GOLD)
    cubes = [
        m.box([x - r, y, z - r], [2 * r, 1, 2 * r], BLACK, extra=brim, noise=0.03),
        m.box([x - r, y + 1, z - r], [2 * r, 1, 1], BLACK_HI, extra=turned),
        m.box([x - r, y + 1, z + r - 1], [2 * r, 1, 1], BLACK_HI, extra=turned),
        m.box([x - r, y + 1, z - r + 1], [1, 1, 2 * r - 2], BLACK_HI, extra=turned),
        m.box([x + r - 1, y + 1, z - r + 1], [1, 1, 2 * r - 2], BLACK_HI, extra=turned),
        m.box([x - 5, y + 1, z - 5], [10, 7, 10], BLACK, extra=crown, noise=0.03),
        m.box([x - 4, y + 8, z - 4], [8, 1, 8], BLACK_HI, noise=0.03),
    ]
    m.add(bone(name, pivot, parent, cubes))


def chair(m):
    """A wooden folding chair from the back of a truck, as wide as a Papon."""
    def wood(p, f):
        for s in f.values():
            x0, y0, x1, y1 = (int(n) for n in s)
            for yy in range(y0, y1):
                for xx in range(x0, x1):
                    if (xx * 3 + yy) % 7 == 0:
                        p.put(xx, yy, WOOD_DK)
    c = [m.box([-8, 6, -7], [16, 1, 15], WOOD, extra=wood, noise=0.06)]
    for lx in (-8, 7):
        for lz in (-7, 7):
            c.append(m.box([lx, 0, lz], [1, 6, 1], WOOD_DK, noise=0.05))
    for lx in (-8, 7):
        c.append(m.box([lx, 7, 8], [1, 11, 1], WOOD_DK, noise=0.05))
    c.append(m.box([-8, 13, 8.2], [16, 3, 1], WOOD, extra=wood, noise=0.06))
    c.append(m.box([-8, 17, 8.2], [16, 1, 1], WOOD_HI, noise=0.05))
    m.add(bone('chair', [0, 0, 0], None, c))


def guitar_cubes(m, kind):
    """A guitar strapped across his front, neck up to his left. Origin = the bone's pivot (centre of
    the soundhole). The singer's and the guitarist's: honey spruce; the vihuela: small, a rounded
    back; the guitarron: huge and deep."""
    size = {'guitar': (11, 9, 3), 'vihuela': (9, 7.5, 4), 'guitarron': (15, 13, 6)}[kind]
    w, h, d = size
    top = SPRUCE if kind != 'guitarron' else (196, 150, 92)
    def body(p, f):
        p.fill(f['front'], top, 0.05)
        x0, y0, x1, y1 = (int(n) for n in f['front'])
        cx, cy = (x0 + x1) // 2, (y0 + y1) // 2 + 1
        for yy in range(y0, y1):
            for xx in range(x0, x1):
                dd = ((xx + 0.5 - cx) ** 2 + (yy + 0.5 - cy) ** 2) ** 0.5
                if dd < max(1.2, w / 7):
                    p.put(xx, yy, (40, 24, 14))
                elif dd < max(1.2, w / 7) + 1:
                    p.put(xx, yy, GOLD if kind == 'guitar' else WOOD_DK)
        p.fill((x0 + 1, y1 - 2, x1 - 1, y1 - 1), WOOD_DK, 0.02)       # the bridge
        edge(p, f['front'], WOOD_DK)
        for s in ('right', 'left', 'top', 'bottom', 'back'):
            p.fill(f[s], WOOD if kind != 'guitarron' else (120, 70, 36), 0.06)
    neck_len = {'guitar': 13, 'vihuela': 9, 'guitarron': 13}[kind]
    def neck(p, f):
        for s in f.values():
            p.fill(s, (60, 36, 20), 0.05)
        x0, y0, x1, y1 = (int(n) for n in f['front'])
        for xx in range(x0, x1, 2):
            for yy in range(y0, y1):
                p.put(xx, yy, SILVER)
    cubes = [
        m.box([-w / 2, -h / 2, -d], [w, h, d], top, extra=body, noise=0.05),
        m.box([-w / 2 + 1.5, h / 2, -d + 0.5], [w - 3, max(2, h / 3), d - 1], top, extra=body, noise=0.05),   # the upper bout
        m.box([-1, h / 2 + max(2, h / 3), -d / 2 - 0.5], [2, neck_len, 1], (60, 36, 20), extra=neck),
        m.box([-1.5, h / 2 + max(2, h / 3) + neck_len, -d / 2 - 0.75], [3, 3, 1.5], WOOD_DK, noise=0.06),   # the head
    ]
    if kind == 'vihuela':
        cubes.append(m.box([-w / 2 + 1, -h / 2 + 1, 0], [w - 2, h - 2, 2], WOOD_HI, noise=0.06))           # the bowl back
    return cubes


def brass_paint(p, f):
    for s in f.values():
        x0, y0, x1, y1 = (int(n) for n in s)
        for xx in range(x0, x1):
            p.put(xx, y0, (255, 230, 140))


def trumpet_cubes(m):
    """Along -Z from the mouthpiece (the bone's pivot): leadpipe, the valve block on top, the bell
    flaring at the end. The three valve caps are their own bones (trumpet_valves)."""
    return [
        m.box([-0.5, -0.5, -9], [1, 1, 9], BRASS, extra=brass_paint, noise=0.04),
        m.box([-0.5, -2.5, -8], [1, 2, 6], BRASS_DK, noise=0.04),           # the lower loop
        m.box([-1, 0.5, -6], [2, 2, 3], BRASS, extra=brass_paint, noise=0.04),     # valve casings
        m.box([-1.5, -1.5, -11], [3, 3, 2], BRASS, extra=brass_paint, noise=0.04),
        m.box([-2.5, -2.5, -13], [5, 5, 2], BRASS, extra=brass_paint, noise=0.04),  # the bell
    ]


def trumpet_floor_cubes(m):
    """The trumpet lying by the chair: one piece, valves and all."""
    return trumpet_cubes(m) + [m.box([-0.6, 2.5, z], [0.5, 0.8, 0.5], SILVER) for z in (-5.8, -4.8, -3.8)]


def trumpet_valves(m, parent, pivot):
    """The valve caps, one bone each, pressed down (client/MariachiModel) for the fingering of each note."""
    out = []
    for i, z in enumerate((-5.8, -4.8, -3.8)):
        cap = [m.box([-0.6, 2.5, z], [0.5, 0.8, 0.5], SILVER), m.box([-0.75, 3.3, z - 0.1], [0.8, 0.3, 0.7], (240, 236, 226))]
        out.append(bone('valve_%d' % (i + 1), [pivot[0], pivot[1] + 2.9, pivot[2] + z + 0.25], parent, placed(cap, pivot)))
    return out


def requinto_cubes(m):
    """The singer's requinto: a small Spanish guitar (a third smaller than the band's), so it sits
    low across his belly, neck out to his left, well under his microphone. Built like guitar_cubes:
    neck along +Y from the soundhole (the bone's pivot)."""
    w, h, d = 6.5, 5, 2.5
    def body(p, f):
        p.fill(f['front'], SPRUCE, 0.05)
        x0, y0, x1, y1 = (int(n) for n in f['front'])
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2 + 0.5
        for yy in range(y0, y1):
            for xx in range(x0, x1):
                dd = ((xx + 0.5 - cx) ** 2 + (yy + 0.5 - cy) ** 2) ** 0.5
                if dd < 1.1:
                    p.put(xx, yy, (40, 24, 14))
                elif dd < 2.0:
                    p.put(xx, yy, GOLD)
        p.fill((x0 + 1, y1 - 2, x1 - 1, y1 - 1), WOOD_DK, 0.02)       # the bridge
        edge(p, f['front'], WOOD_DK)
        for s in ('right', 'left', 'top', 'bottom', 'back'):
            p.fill(f[s], WOOD_HI, 0.06)
            edge(p, f[s], WOOD_DK, 'tb')
    def neck(p, f):
        for s in f.values():
            p.fill(s, (60, 36, 20), 0.05)
        x0, y0, x1, y1 = (int(n) for n in f['front'])
        for yy in range(y0, y1, 2):
            for xx in range(x0, x1):
                p.put(xx, yy, SILVER)
    def head(p, f):
        for s in f.values():
            p.fill(s, WOOD_DK, 0.06)
        x0, y0, x1, y1 = (int(n) for n in f['front'])
        for yy in range(y0, y1):
            p.put(x0, yy, SILVER)
            p.put(x1 - 1, yy, SILVER)
    return [
        m.box([-w / 2, -h / 2 - 0.5, -d], [w, h, d], SPRUCE, extra=body, noise=0.05),
        m.box([-w / 2 + 1, h / 2 - 0.5, -d + 0.25], [w - 2, 2, d - 0.5], SPRUCE, extra=body, noise=0.05),   # the upper bout
        m.box([-0.75, h / 2 + 1.5, -d / 2 - 0.5], [1.5, 8, 1], (60, 36, 20), extra=neck),
        m.box([-1.25, h / 2 + 9.5, -d / 2 - 0.75], [2.5, 2.5, 1.25], WOOD_DK, extra=head),
    ]


def maraca_cubes(m, x):
    return [
        m.box([x + 0.5, 0, -1], [1, 4, 1], (120, 82, 40)),
        m.box([x - 0.5, -3, -2], [3, 3, 3], (214, 60, 48), noise=0.08,
              extra=lambda p, f: [p.put(int(r[0]) + 1, int(r[1]) + 1, (250, 214, 90)) for r in f.values()]),
    ]


def grille_paint(p, f):
    for s in f.values():
        x0, y0, x1, y1 = (int(n) for n in s)
        for yy in range(y0, y1):
            for xx in range(x0, x1):
                p.put(xx, yy, (150, 150, 158) if (xx + yy) % 2 else (90, 90, 98))


def black_metal(p, f):
    for s in f.values():
        p.fill(s, (34, 34, 38), 0.05)


def hand_mic_cubes(m, x):
    """A handheld microphone: the handle in his fist, the grille out past it along the arm."""
    return [
        m.box([x + 0.5, 0.5, -0.5], [1, 4, 1], (34, 34, 38), extra=black_metal),      # the handle, in his fist
        m.box([x, -1.5, -1], [2, 2, 2], (130, 130, 138), extra=grille_paint),       # the grille, out past it
    ]


def cheeks(m):
    """Trumpet cheeks: a pad each side of the mouth, flat (scale 0) until he blows; then they puff out
    (client/MariachiModel, as loud as the note), with a flush of pink."""
    def puff(p, f):
        p.fill(f['front'], (0xd9, 0xaa, 0x51), 0.03)
        x0, y0, x1, y1 = (int(n) for n in f['front'])
        p.put(x0 + 1, y0 + 1, (226, 150, 110))
        for s in ('right', 'left', 'top', 'bottom'):
            p.fill(f[s], (0xc8, 0x97, 0x3a), 0.03)
    for side, x in (('r', -8.0), ('l', 5.0)):
        m.add(bone('cheek_' + side, [x + 1.5, 10, -8], 'body', [m.box([x, 8.5, -8.9], [3, 3, 1], (0xd9, 0xaa, 0x51), extra=puff)]))


VARNISH = (150, 58, 26)
VARNISH_DK = (104, 38, 18)
EBONY = (26, 20, 18)


def violin_cubes(m):
    """Top (strings) along +Y, scroll along -Z, the chin end at +Z: lower and upper bouts with a waist
    between, f-holes, the fingerboard over the top, bridge, tailpiece, chinrest and the strings."""
    def top(p, f):
        p.fill(f['top'], VARNISH, 0.06)
        x0, y0, x1, y1 = (int(n) for n in f['top'])
        cy = (y0 + y1) // 2
        for yy in range(max(y0, cy - 1), min(y1, cy + 1)):
            p.put(x0 + 1, yy, (30, 14, 8))       # f-holes
            p.put(x1 - 2, yy, (30, 14, 8))
        edge(p, f['top'], VARNISH_DK)
        for k in ('front', 'back', 'right', 'left', 'bottom'):
            p.fill(f[k], VARNISH_DK, 0.06)
    def dark(p, f):
        for r in f.values():
            p.fill(r, EBONY, 0.03)
    def string(p, f):
        for r in f.values():
            p.fill(r, (214, 216, 222), 0.02)
    return [
        m.box([-2.75, -0.75, 0.5], [5.5, 1.5, 3.5], VARNISH, extra=top, noise=0.06),     # lower bout
        m.box([-2.0, -0.75, -1.0], [4, 1.5, 1.5], VARNISH, extra=top, noise=0.06),       # the waist
        m.box([-2.5, -0.75, -4.0], [5, 1.5, 3], VARNISH, extra=top, noise=0.06),         # upper bout
        m.box([-0.5, -0.25, -10], [1, 1, 6], (60, 34, 18), noise=0.04),                  # the neck
        m.box([-0.75, -0.5, -11.5], [1.5, 1.5, 1.5], VARNISH_DK, noise=0.05),            # the scroll
        m.box([-0.55, 0.75, -9.5], [1.1, 0.35, 8], EBONY, extra=dark),                   # fingerboard
        m.box([-1.25, 0.75, 0.6], [2.5, 0.6, 0.3], (226, 200, 150), noise=0.03),         # the bridge
        m.box([-0.6, 0.75, 1.3], [1.2, 0.3, 2.0], EBONY, extra=dark),                    # tailpiece
        m.box([-1.6, 0.75, 3.1], [3.2, 0.45, 0.8], EBONY, extra=dark),                   # chinrest
        m.box([-0.35, 1.12, -9.5], [0.7, 0.1, 11.0], (214, 216, 222), extra=string),     # the strings
    ]


REQUINTO = [-5.0, 2.0, -8.6]      # the requinto's soundhole, on his right, low
TRUMPET = [0, 10.5, -8.4]          # the trumpet's mouthpiece, at his lips
MIC = [0, 5.4, -12.6]              # the singer's microphone grille
# the violin: its chin end against the left corner of his face, the scroll out to his left and
# forward, the top tilted towards his bow arm (band_ik.orient finds the rotation for those directions)
VIOLIN_SCROLL = (0.6, 0.14, -0.79)
VIOLIN_TOP = (-0.5, 0.86, 0.0)
VIOLIN_CHIN = (7.6, 5.6, -8.6)


def violin_place():
    import numpy as np
    from band_ik import orient
    rot, sw, _ = orient(VIOLIN_SCROLL, VIOLIN_TOP)
    pivot = [round(float(v), 3) for v in np.array(VIOLIN_CHIN) + sw * 4.0]
    return pivot, rot


VIOLIN, VIOLIN_ROT = violin_place()


def placed(cubes, pivot):
    """Instrument cubes are built around (0, 0, 0); geo cube origins are absolute: move them to the pivot."""
    for c in cubes:
        c['origin'] = [round(o + p, 4) for o, p in zip(c['origin'], pivot)]
    return cubes


def held(name, pivot, parent, cubes, rotation=None):
    return bone(name, pivot, parent, placed(cubes, pivot), rotation=rotation)


def mariachi():
    m = Model('mariachi', 1810, tex=256)
    unpaint_mouth(m)
    rig_hands(m)
    mouth_rig(m)
    suit(m)
    chair(m)
    jacket(m)
    sombrero(m, 'sombrero', 'body', [0, 24, 0])
    sombrero(m, 'sombrero_floor', None, [13, 0, 2], at=(13, 0, 2))
    # the instruments, played: strapped to his front (guitars), at his lips (trumpet), on his shoulder (violin)
    m.add(held('guitar', [0, 0.5, -11.5], 'body', guitar_cubes(m, 'guitar'), rotation=[0, 0, 56]))
    # the singer's: small, low and flat across him, clear of the microphone
    m.add(held('requinto', REQUINTO, 'body', requinto_cubes(m), rotation=[0, 0, 80]))
    m.add(held('vihuela', [0, 2.5, -11], 'body', guitar_cubes(m, 'vihuela'), rotation=[0, 0, 50]))
    m.add(held('guitarron', [0, -1, -12], 'body', guitar_cubes(m, 'guitarron'), rotation=[0, 0, 44]))
    m.add(held('trumpet', TRUMPET, 'body', trumpet_cubes(m)))
    m.add(*trumpet_valves(m, 'trumpet', TRUMPET))
    cheeks(m)
    m.add(held('violin', VIOLIN, 'body', violin_cubes(m), rotation=VIOLIN_ROT))
    m.add(bone('bow', HAND_R, 'hand_r', [
        m.box([-10.25, 3.5, -16], [0.5, 0.5, 18], (90, 50, 26)),
        m.box([-10.5, 2.6, -15.5], [0.25, 1, 17], (236, 232, 220)),
    ], rotation=[0, 60, 0]))
    # ...and lying by the chair until picked up (top-level: they stay where they are)
    m.add(held('guitar_floor', [10, 1.5, -4], None, guitar_cubes(m, 'guitar'), rotation=[-80, 10, 0]))
    m.add(held('vihuela_floor', [10, 1.8, -4], None, guitar_cubes(m, 'vihuela'), rotation=[-80, 10, 0]))
    m.add(held('guitarron_floor', [9, 7, 9], None, guitar_cubes(m, 'guitarron'), rotation=[-12, 0, 8]))
    m.add(held('trumpet_floor', [10, 0.6, -2], None, trumpet_floor_cubes(m), rotation=[0, -40, 90]))
    m.add(held('violin_floor', [10, 0.8, -3], None, violin_cubes(m), rotation=[0, -30, 0]))
    m.add(held('requinto_floor', [10, 1.2, -4], None, requinto_cubes(m), rotation=[-80, 10, 0]))
    # the coro: a maraca in the right hand, a hand mic in the left (on the floor by the chair while they sleep)
    m.add(bone('maraca', HAND_R, 'hand_r', maraca_cubes(m, -11)))
    m.add(bone('hand_mic', HAND_L, 'hand_l', hand_mic_cubes(m, 9)))
    m.add(held('maraca_floor', [10, 0.5, -2], None, maraca_cubes(m, -0.5), rotation=[0, 0, 90]))
    m.add(held('hand_mic_floor', [11, 0.5, -5], None, hand_mic_cubes(m, -1), rotation=[0, 30, 90]))
    # the singer: the stand in front of his left foot, the boom over to the middle, the microphone low
    # and tilted up at his mouth: under his lip (the lip sync stays in view), over the requinto's neck
    m.add(bone('mic_stand', [3, 0, -17.5], None, [
        m.box([0.5, 0, -20], [5, 0.5, 5], (34, 34, 38), extra=black_metal),
        m.box([2.5, 0.5, -18], [1, 3.5, 1], (34, 34, 38), extra=black_metal),
    ]))
    m.add(bone('mic_boom', [3, 4, -17.5], 'mic_stand', [
        m.box([2.5, 3.5, -18], [1, 1, 5], (34, 34, 38), extra=black_metal),
    ], rotation=[-30, -24, 0]))
    m.add(held('mic_head', MIC, 'mic_stand', [
        m.box([-0.6, -2.4, -0.6], [1.2, 2.6, 1.2], (60, 60, 66), extra=black_metal),         # the handle
        m.box([-1.25, 0.2, -1.25], [2.5, 2.5, 2.5], (130, 130, 138), extra=grille_paint),   # the grille, at his mouth
    ], rotation=[-38, 0, 0]))
    def lens(p, f):
        p.fill(f['front'], (22, 30, 44), 0.05)
        x0, y0, x1, y1 = (int(n) for n in f['front'])
        p.put(x0 + 1, y0, (220, 236, 255))
        edge(p, f['front'], GOLD, 't')
    m.add(bone('shades', [0, 19.5, -8], 'body', [
        m.box([-7, 18.5, -8.5], [6, 3, 1], (22, 30, 44), extra=lens),
        m.box([1, 18.5, -8.5], [6, 3, 1], (22, 30, 44), extra=lens),
        m.box([-1, 20.5, -8.4], [2, 1, 1], GOLD),
        m.box([-8.3, 20.5, -8], [0.5, 0.5, 8], GOLD), m.box([7.8, 20.5, -8], [0.5, 0.5, 8], GOLD),
    ]))
    return m.save()


# ===================================================================== the crowd

def arena_fan():
    m = Model('arena_fan', 4242)
    # a little open mouth for the cheering (under the painted bar)
    m.add(bone('mouth_in', [0, 10, -8], 'body', [m.box([-4, 9, -8.06], [8, 1, 1], (52, 20, 16))]))
    straw = (222, 192, 110)
    m.add(bone('sombrero', [0, 24, 0], 'body', [
        m.box([-11, 24, -11], [22, 1, 22], straw, noise=0.08,
              extra=lambda p, f: [band(p, f[s], 0, (196, 52, 40)) for s in ('front', 'back', 'left', 'right')]),
        m.box([-5, 25, -5], [10, 5, 10], straw, noise=0.08,
              extra=lambda p, f: [(band(p, f[s], 3, (196, 52, 40)), band(p, f[s], 4, (60, 140, 60))) for s in ('front', 'back', 'left', 'right')]),
    ]))
    m.add(bone('cap', [0, 24, 0], 'body', [
        m.box([-8.5, 24, -8.5], [17, 3, 17], (40, 120, 64), noise=0.05),
        m.box([-5, 24, -15.5], [10, 1, 7], (30, 96, 50), noise=0.05),
    ]))
    def foam(p, f):
        for s in f.values():
            x0, y0, x1, y1 = (int(n) for n in s)
            for yy in range(y0, y1):
                for xx in range(x0, x1):
                    if (xx * 5 + yy * 3) % 7 == 0:
                        p.put(xx, yy, (214, 168, 52))
    m.add(bone('foam_hat', [0, 24, 0], 'body', [
        m.box([-8, 24, -6], [16, 4, 12], (246, 202, 72), extra=foam, noise=0.04),
        m.box([-6, 28, -4], [12, 3, 8], (246, 202, 72), extra=foam, noise=0.04),
    ]))
    def jersey(p, f):
        p.fill(f['front'], (32, 120, 62), 0.04)
        p.fill(f['back'], (32, 120, 62), 0.04)
        x0, y0, x1, y1 = (int(n) for n in f['back'])
        for (xx, yy) in ((1, 1), (2, 1), (3, 1), (3, 2), (2, 3), (2, 4), (5, 1), (6, 1), (7, 1), (5, 2), (7, 2), (5, 3), (7, 3), (5, 4), (6, 4), (7, 4)):
            p.put(x0 + 4 + xx, y0 + yy, WHITE)
        for s in ('front', 'back', 'left', 'right'):
            band(p, f[s], 0, WHITE)
    m.add(bone('jersey', [0, 6, 0], 'body', [m.box([-8, 4, -8], [16, 5, 16], (32, 120, 62), extra=jersey, inflate=0.2)]))
    def finger(p, f):
        p.fill(f['front'], (226, 40, 44), 0.04)
        x0, y0, x1, y1 = (int(n) for n in f['front'])
        p.put(x0 + 1, y0 + 4, WHITE)
        p.put(x0 + 2, y0 + 4, WHITE)
    m.add(bone('foam_finger', HAND_R, 'arm_r', [
        m.box([-12, -1, -4], [4, 6, 2], (226, 40, 44), extra=finger, noise=0.03),
        m.box([-11, -6, -4], [2, 5, 2], (226, 40, 44), noise=0.03),
    ]))
    def pennant(p, f):
        x0, y0, x1, y1 = (int(n) for n in f['right'])
        n = x1 - x0
        for i in range(n):
            c = (32, 122, 64) if i < n / 3 else (238, 234, 222) if i < 2 * n / 3 else (196, 40, 44)
            for yy in range(y0, y1):
                p.put(x0 + i, yy, c)
        x0, y0, x1, y1 = (int(n) for n in f['left'])
        for i in range(x1 - x0):
            c = (196, 40, 44) if i < n / 3 else (238, 234, 222) if i < 2 * n / 3 else (32, 122, 64)
            for yy in range(y0, y1):
                p.put(x0 + i, yy, c)
    m.add(bone('flag', HAND_L, 'arm_l', [
        m.box([9.5, -12, -0.5], [1, 14, 1], (120, 82, 40)),
        m.box([10, -12, -9], [0.5, 6, 9], WHITE, extra=pennant),
    ]))
    for side, h, x in (('r', HAND_R, -11), ('l', HAND_L, 9)):
        m.add(bone('maraca_' + side, h, 'arm_' + side, [
            m.box([x + 0.5, 0, -1], [1, 4, 1], (120, 82, 40)),
            m.box([x - 0.5, -3, -2], [3, 3, 3], (214, 60, 48), noise=0.08,
                  extra=lambda p, f: [p.put(int(r[0]) + 1, int(r[1]) + 1, (250, 214, 90)) for r in f.values()]),
        ]))
    return m.save()


if __name__ == '__main__':
    for p in mariachi() + arena_fan():
        print(p)
