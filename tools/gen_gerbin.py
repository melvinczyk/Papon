#!/usr/bin/env python3
"""Generates Gerbin, the dishwasher in the back of the Taco Truck.

Writes, under src/main/resources/assets/papon/:
  geo/entity/gerbin.geo.json   textures/entity/gerbin.png (128x128)

Gerbin is a chort (Imps & Demons, realmrpg_demons:chort): the same bones and cubes at the same
UVs (tools/src/chort_base.png pasted at 0,0), renamed to this mod's rig conventions, with the
chort's sack left out (he works bare-handed) and its right arm hanging at rest instead of
shouldering the sack. Dressed for the sink: a white dishwasher's apron (bib, waist ties, a flap
over the thighs) and yellow rubber gloves.

Bones: root > leg_r, leg_l, torso > head (> ear_r, ear_l, horns), tail, arm_r (> glove_r, knife,
tomato), arm_l (> glove_l, brush, lettuce_held), apron, apron_flap.

His work stations are props in his own model, placed where the truck's blocks are when he stands
at his spot (TacoTruck.GERBIN_CHOP / GERBIN_SINK), rendered at 1.25x:
  chop_station  facing the prep counter (a full block 0.5..1.5 ahead, top at 12.8 px):
                cutting board, lettuce (shrinks as it is chopped), shreds, the lettuce crate
                front-left, the bowl of chopped lettuce front-right
  sink_station  facing the water cauldron: the basket of dirty tomatoes on the counter front-right,
                the bowl of clean ones on the barrel front-left (he tosses them in), suds on the water
Animations: gen_npc_anims.py (gerbin.animation.json).
"""
import os

from PIL import Image

from geo_util import ASSETS, ROOT, Packer, Painter, bone, cube, faces, write_geo

TEX = 128
SIDE = {'right': 'east', 'front': 'north', 'left': 'west', 'back': 'south', 'top': 'up', 'bottom': 'down'}
SCALE = 1.25
COUNTER = 16 / SCALE          # a full block's top, in model pixels


class Model:
    def __init__(self):
        self.bones = []
        self.p = Painter(TEX, TEX, 445)
        base = Image.open(os.path.join(ROOT, 'tools/src/chort_base.png')).convert('RGBA')
        self.p.im.paste(base, (0, 0))
        self.packers = [Packer(64, 64), Packer(TEX, 64)]
        self.offsets = [(64, 0), (0, 64)]

    def box(self, origin, size, color, noise=0.05, inflate=0, extra=None):
        w, h, d = size
        for packer, (ox, oy) in zip(self.packers, self.offsets):
            try:
                u, v = packer.take(w, h, d)
                break
            except ValueError:
                continue
        else:
            raise ValueError('gerbin: texture full')
        u, v = u + ox, v + oy
        f = faces(u, v, w, h, d)
        for r in f.values():
            self.p.fill(r, color, noise)
        if extra:
            extra(self.p, {k: f[v_] for k, v_ in SIDE.items()})
        return cube(origin, size, (u, v), inflate)

    def add(self, *bones):
        self.bones.extend(bones)


def chort_bones():
    """realmrpg_demons chort.geo.json, bone for bone, minus the sack."""
    return [
        bone('root', [0, 0, 0]),
        bone('leg_r', [-2.5, 7, 0.5], 'root', [cube([-4, 4, -3], [3, 4, 5], [48, 0], 0.01),
                                               cube([-4, 0, -1], [3, 4, 3], [24, 25])]),
        bone('leg_l', [2.5, 7, 0.5], 'root', [cube([1, 4, -3], [3, 4, 5], [48, 9], 0.01),
                                              cube([1, 0, -1], [3, 4, 3], [36, 25])]),
        bone('torso', [0, 8, 0], 'root', [cube([-4, 8, -2], [8, 8, 4], [0, 12])]),
        bone('tail', [0, 8.5, 2], 'torso', [cube([0, 8, 2], [0, 6, 7], [48, 19])]),
        bone('head', [0, 16, 0], 'torso', [cube([-1, 15, -5], [2, 3, 2], [48, 18]),
                                           cube([-3, 16, -3], [6, 6, 6], [0, 0])]),
        bone('horns', [0, 22, 0], 'head', [cube([1, 22, -1], [2, 3, 2], [24, 0]),
                                           cube([-3, 22, -1], [2, 3, 2], [32, 0])]),
        bone('ear_r', [-3, 19, 0], 'head', [cube([-5, 18, 0], [2, 2, 0], [1, 4])], rotation=[0, 45, 0]),
        bone('ear_l', [3, 19, 0], 'head', [cube([3, 18, 0], [2, 2, 0], [0, 1])], rotation=[0, -45, 0]),
        bone('arm_l', [4, 14, 0.5], 'torso', [cube([4, 6, -1], [3, 10, 3], [36, 8], 0.01)]),
        # the chort carries his sack over this shoulder; Gerbin's arm hangs like the other one
        bone('arm_r', [-4, 14, 0.5], 'torso', [cube([-7, 6, -1], [3, 10, 3], [24, 8], 0.01)]),
    ]


def outline(p, rect, color):
    x0, y0, x1, y1 = (int(n) for n in rect)
    for x in range(x0, x1):
        for y in range(y0, y1):
            if x in (x0, x1 - 1) or y in (y0, y1 - 1):
                p.put(x, y, color)


def build():
    m = Model()
    m.bones = chort_bones()

    # ---- the dishwasher's apron: white, a little grey at the hem, wet patches on the front
    white, white_dk, wet = (238, 240, 236), (196, 200, 198), (206, 222, 230)

    def wet_spots(p, f):
        x0, y0, x1, y1 = (int(n) for n in f['front'])
        rng = p.rng
        for _ in range(4):
            x, y = rng.randrange(x0, x1), rng.randrange(y0, y1)
            p.put(x, y, wet, 0.03)
            if x + 1 < x1:
                p.put(x + 1, y, wet, 0.03)

    def bib_paint(p, f):
        wet_spots(p, f)
        x0, y0, x1, y1 = (int(n) for n in f['front'])
        p.fill((x0 + 2, y1 - 3, x1 - 2, y1 - 2), white_dk, 0.02)     # the pocket seam

    def flap_paint(p, f):
        wet_spots(p, f)
        x0, y0, x1, y1 = (int(n) for n in f['front'])
        p.fill((x0, y1 - 1, x1, y1), white_dk, 0.03)                   # grubby hem

    m.add(bone('apron', [0, 12, 0], 'torso', [
        m.box([-3, 9, -2.6], [6, 7, 1], white, extra=bib_paint, noise=0.03),          # bib
        m.box([-4, 8, -2], [8, 1, 4], white_dk, inflate=0.3, noise=0.03),             # waist ties
        m.box([-1, 7, 2.2], [2, 2, 1], white_dk, noise=0.03),                          # the bow at the back
        m.box([-3, 15, -2], [1, 1, 4], white_dk, inflate=0.15, noise=0.03),            # neck strap
        m.box([2, 15, -2], [1, 1, 4], white_dk, inflate=0.15, noise=0.03),
    ]))
    # the skirt hangs from the waist over the thighs; it sways on its own
    m.add(bone('apron_flap', [0, 8.5, -3.2], 'torso', [
        m.box([-4, 3, -3.7], [8, 6, 1], white, extra=flap_paint, noise=0.03)]))

    # ---- yellow rubber gloves up to the forearm
    yellow, yellow_dk = (246, 210, 48), (214, 170, 30)

    def cuff(p, f):
        for s in ('front', 'back', 'left', 'right'):
            x0, y0, x1, y1 = (int(n) for n in f[s])
            p.fill((x0, y0, x1, y0 + 1), yellow_dk, 0.02)
    m.add(bone('glove_r', [-5.5, 10, 0.5], 'arm_r', [m.box([-7, 6, -1], [3, 5, 3], yellow, inflate=0.25, extra=cuff)]))
    m.add(bone('glove_l', [5.5, 10, 0.5], 'arm_l', [m.box([4, 6, -1], [3, 5, 3], yellow, inflate=0.25, extra=cuff)]))

    # ---- hand props. Hands are at the bottom of the arms (y 6..7). Held items run along the
    # arm's axis (local -Y), so with the arm out in front they point forward.
    steel, steel_dk, wood = (214, 220, 226), (150, 156, 164), (84, 52, 30)

    def edge(p, f):
        for s in ('left', 'right'):
            x0, y0, x1, y1 = (int(n) for n in f[s])
            p.fill((x0, y0, x0 + 1, y1), (246, 248, 250), 0.0)
    m.add(bone('knife', [-5.5, 6.5, 0.5], 'arm_r', [
        m.box([-6, 5, 0], [1, 3, 1], wood, noise=0.08),                               # handle in the fist
        m.box([-6, 0, -0.5], [1, 5, 2], steel, inflate=-0.3, extra=edge, noise=0.02),  # blade
    ]))
    tomato, tomato_dk, stem = (222, 46, 38), (170, 28, 24), (70, 140, 50)

    def tomato_paint(p, f):
        x0, y0, x1, y1 = (int(n) for n in f['top'])
        p.put((x0 + x1) // 2, (y0 + y1) // 2, stem)
        for s in ('front', 'right'):
            x0, y0, x1, y1 = (int(n) for n in f[s])
            p.put(x0, y0, (255, 140, 120))                                             # shine
    m.add(bone('tomato', [-5.5, 6.5, 0.5], 'arm_r', [
        m.box([-7, 3, -1], [3, 3, 3], tomato, extra=tomato_paint, noise=0.06)]))
    m.add(bone('brush', [5.5, 6.5, 0.5], 'arm_l', [
        m.box([4.5, 4, -1], [2, 2, 3], (196, 120, 60), noise=0.06),                     # wooden back
        m.box([4.5, 3, -1], [2, 1, 3], (240, 232, 200), noise=0.1),                     # bristles
    ]))
    lettuce, lettuce_lt, lettuce_dk = (110, 186, 66), (170, 220, 110), (70, 140, 44)

    def leafy(p, f):
        for r in f.values():
            x0, y0, x1, y1 = (int(n) for n in r)
            for x in range(x0, x1):
                for y in range(y0, y1):
                    if p.rng.random() < 0.35:
                        p.put(x, y, lettuce_lt if p.rng.random() < 0.6 else lettuce_dk, 0.05)
    m.add(bone('lettuce_held', [5.5, 6.5, 0.5], 'arm_l', [m.box([3.5, 2, -2], [4, 4, 4], lettuce, extra=leafy)]))
    # where the body pillow goes when he brings it to the winner (client/GerbinRenderer draws the item here)
    m.add(bone('pillow', [0, 11, -5], 'torso', []))

    # ---- the prep counter: everything sits on top of the block in front of him
    c = COUNTER
    board_wood, board_dk = (206, 160, 104), (168, 122, 74)

    def board_paint(p, f):
        x0, y0, x1, y1 = (int(n) for n in f['top'])
        for x in range(x0, x1):
            for y in range(y0, y1):
                if (y - y0) % 3 == 0:
                    p.put(x, y, board_dk, 0.04)
        for _ in range(6):                                          # knife nicks
            p.put(p.rng.randrange(x0, x1), p.rng.randrange(y0, y1), (150, 104, 60))
    crate, crate_dk = (178, 128, 74), (128, 86, 46)

    def slats(p, f):
        for s in ('front', 'back', 'left', 'right'):
            x0, y0, x1, y1 = (int(n) for n in f[s])
            for y in range(y0, y1):
                if (y - y0) % 2 == 1:
                    p.fill((x0, y, x1, y + 1), crate_dk, 0.03)
    bowl, bowl_dk = (60, 116, 186), (40, 84, 140)

    def bowl_rim(p, f):
        x0, y0, x1, y1 = (int(n) for n in f['top'])
        p.fill((x0 + 1, y0 + 1, x1 - 1, y1 - 1), lettuce, 0.15)
        outline(p, f['top'], bowl_dk)
    m.add(bone('chop_station', [0, c, 0], None, [
        m.box([-4, c, -16], [7, 1, 9], board_wood, extra=board_paint, noise=0.03),     # cutting board
        m.box([7, c, -16], [5, 4, 6], crate, extra=slats, noise=0.05),                 # lettuce crate, front-left
        m.box([8, c + 4, -15], [3, 2, 4], lettuce, extra=leafy),                       # heads in the crate
        m.box([-12, c, -15], [5, 3, 5], bowl, extra=bowl_rim, noise=0.04),             # bowl of chopped lettuce
    ]))
    # the head on the board shrinks as he chops it (scaled about its back edge, where the knife works toward)
    m.add(bone('chop_lettuce', [-1, c + 1, -9], 'chop_station', [m.box([-3, c + 1, -15], [4, 4, 5], lettuce, extra=leafy)]))
    m.add(bone('chop_shreds', [0, c + 1, -12], 'chop_station', [
        m.box([-3, c + 1, -11], [5, 1, 3], lettuce_lt, extra=leafy, noise=0.15),
        m.box([-2, c + 2, -10.5], [3, 1, 2], lettuce, extra=leafy, noise=0.15),
    ]))

    # ---- the sink
    wicker, wicker_dk = (196, 150, 80), (150, 104, 50)

    def weave(p, f):
        for r in f.values():
            x0, y0, x1, y1 = (int(n) for n in r)
            for x in range(x0, x1):
                for y in range(y0, y1):
                    if (x + y) % 2:
                        p.put(x, y, wicker_dk, 0.04)
    suds = (244, 248, 252)

    def bubbles(p, f):
        x0, y0, x1, y1 = (int(n) for n in f['top'])
        for x in range(x0, x1):
            for y in range(y0, y1):
                if p.rng.random() < 0.3:
                    p.put(x, y, (200, 220, 240))
    m.add(bone('sink_station', [0, c, 0], None, [
        m.box([-13, c, -15], [6, 3, 6], wicker, extra=weave, noise=0.05),              # dirty tomatoes, front-right
        m.box([-12, c + 3, -14], [2, 2, 2], tomato, noise=0.08),
        m.box([-10, c + 3, -12], [2, 2, 2], tomato_dk, noise=0.08),
        m.box([7, c, -14], [5, 2, 5], bowl, extra=lambda p, f: outline(p, f['top'], bowl_dk), noise=0.04),  # clean, front-left
        m.box([8, c + 1.5, -13], [2, 2, 2], tomato, noise=0.08),
        m.box([-5.5, c - 1.2, -15], [8, 1, 8], suds, inflate=-0.2, extra=bubbles, noise=0.03),  # suds on the water
    ]))
    return m


def main():
    m = build()
    geo = write_geo('gerbin', m.bones, TEX, TEX, bounds=(3, 3, 1.2))
    tex = m.p.save(os.path.join(ASSETS, 'textures/entity/gerbin.png'))
    print(geo)
    print(tex)


if __name__ == '__main__':
    main()
