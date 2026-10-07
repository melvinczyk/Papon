#!/usr/bin/env python3
"""Generates the trash can (block/TrashCanBlock): a clean brushed-steel bin with a friendly blue face on its front, a
hinged lid with a bail handle, and its window (client/TrashCanScreen).

Writes, under src/main/resources/assets/papon/ (block textures 32x32: two pixels to a model unit):
  textures/block/trash_can_side.png     brushed steel, soft vertical ribs, lit from above
  textures/block/trash_can_front.png    the same with the face: a rounded blue plate, eyes, a smile
  textures/block/trash_can_happy.png    the face while it eats (transparent round it): eyes squeezed shut, mouth open
  textures/block/trash_can_top.png      the lid: two pressed rings
  textures/block/trash_can_inside.png   the dark inside, seen when the lid flaps
  textures/block/trash_can_bottom.png
  textures/gui/trash_can.png            the window: one big slot over the inventory, vanilla style
  models/block/trash_can.json           the bin without its lid (client/TrashCanRenderer draws the lid and the happy face)
  models/block/trash_can_lid.json       the lid, closed, hinged at the back (+Z) edge
  models/block/trash_can_happy.json     a plane just off the front carrying the happy face
  models/item/trash_can.json            bin and lid together
  blockstates/trash_can.json            facing (the model's front faces north)
"""
import json
import math
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/papon')
N = 32

STEEL = (178, 186, 192)
STEEL_HI = (214, 220, 224)
BLUE = (52, 118, 214)
BLUE_HI = (104, 164, 240)
BLUE_DK = (30, 76, 156)
INK = (22, 32, 58)
WHITE = (246, 248, 252)
MOUTH = (34, 22, 40)
TONGUE = (236, 106, 120)
CHEEK = (120, 156, 236)


def clamp(v):
    return max(0, min(255, int(round(v))))


def mul(c, k):
    return tuple(clamp(v * k) for v in c[:3]) + (255,)


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


def steel():
    """Brushed steel: a soft rib every 4 px (its crest lit), a faint brushed grain, a touch darker toward the foot."""
    im = Image.new('RGBA', (N, N))
    for y in range(N):
        for x in range(N):
            rib = math.cos((x + 0.5) / 4.0 * 2 * math.pi)
            grain = 0.012 * math.sin(y * 2.7 + x * 0.3) + 0.008 * math.sin(y * 7.1)
            k = 1 + 0.045 * rib + grain - 0.06 * (y / (N - 1))
            c = mul(STEEL, k)
            if rib > 0.92:
                c = mix(c, STEEL_HI, 0.3)
            im.putpixel((x, y), c)
    return im


# The face sits on the front drum's north face: u 3..13, v 4..16 (px 6..26, rows 8..32).
PLATE = (8, 11, 24, 29)


def rounded(x, y, box, r):
    x0, y0, x1, y1 = box
    cx = min(max(x, x0 + r), x1 - r)
    cy = min(max(y, y0 + r), y1 - r)
    return (x - cx) ** 2 + (y - cy) ** 2 <= (r + 0.35) ** 2


def plate(im):
    """The rounded blue plate, lit at the top, a darker rim round it."""
    x0, y0, x1, y1 = PLATE
    b = (x0, y0, x1 - 1, y1 - 1)
    for y in range(y0, y1):
        for x in range(x0, x1):
            if not rounded(x, y, b, 3):
                continue
            t = (y - y0) / (y1 - y0 - 1)
            c = mix(BLUE_HI, BLUE, min(1, t * 2.2)) if t < 0.45 else mix(BLUE, BLUE_DK, (t - 0.45) / 0.55 * 0.6)
            edge = not all(rounded(x + dx, y + dy, b, 3) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            im.putpixel((x, y), mix(c, BLUE_DK, 0.7) if edge else c)


def put(im, pts, c):
    for x, y in pts:
        im.putpixel((x, y), c if len(c) == 4 else c + (255,))


def face(im):
    """Neutral: two big white eyes, a wide smile, soft cheeks."""
    plate(im)
    for ex in (11, 18):
        for y in range(14, 20):
            for x in range(ex, ex + 4):
                if x - ex in (0, 3) and y in (14, 19):
                    continue
                im.putpixel((x, y), WHITE + (255,))
        put(im, [(ex + 1, 16), (ex + 2, 16), (ex + 1, 17), (ex + 2, 17)], INK)
        put(im, [(ex + 2, 16)], WHITE)
    put(im, [(10, 21), (11, 21), (21, 21), (22, 21)], CHEEK)
    put(im, [(12, 22), (20, 22)], INK)
    put(im, [(x, 23) for x in range(13, 20)], INK)
    put(im, [(x, 24) for x in range(14, 19)], mix(BLUE_DK, BLUE, 0.5))
    return im


def happy():
    """Eating: eyes squeezed into happy arcs, mouth open wide with a tongue (transparent outside the plate)."""
    im = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    plate(im)
    for ex in (11, 18):
        put(im, [(ex, 17), (ex + 1, 16), (ex + 2, 16), (ex + 3, 17)], INK)
    put(im, [(10, 20), (11, 20), (21, 20), (22, 20)], CHEEK)
    for y in range(20, 26):
        for x in range(12, 21):
            if (x in (12, 20) and y in (20, 25)) or (y == 20 and x in (13, 19)):
                continue
            im.putpixel((x, y), (TONGUE if y >= 23 and 14 <= x <= 18 else MOUTH) + (255,))
    put(im, [(x, 20) for x in range(14, 19)], WHITE)
    return im


def lid_top():
    im = Image.new('RGBA', (N, N))
    for y in range(N):
        for x in range(N):
            r = math.hypot(x + 0.5 - 16, y + 0.5 - 16)
            k = 1.05 - 0.004 * r
            for ring in (7.5, 11.5):
                d = r - ring
                if -1.0 < d < 0:
                    k += 0.08
                elif 0 <= d < 1.0:
                    k -= 0.1
            im.putpixel((x, y), mul(STEEL, k))
    return im


def bottom():
    return Image.eval(steel(), lambda v: int(v * 0.72))


def inside():
    im = Image.new('RGBA', (N, N))
    for y in range(N):
        for x in range(N):
            r = math.hypot(x + 0.5 - 16, y + 0.5 - 16)
            im.putpixel((x, y), mul((58, 62, 68), 1.05 - 0.025 * r))
    return im


# ===================================================================== the window (176x166, vanilla style)
GUI_BG, GUI_HI, GUI_SH, GUI_EDGE = (198, 198, 198), (255, 255, 255), (85, 85, 85), (0, 0, 0)
SLOT_BG, SLOT_HI, SLOT_SH = (139, 139, 139), (255, 255, 255), (55, 55, 55)


def gui():
    W, H = 176, 166
    im = Image.new('RGBA', (256, 256), (0, 0, 0, 0))
    px = im.load()
    for y in range(H):
        for x in range(W):
            # vanilla's panel: a black outline with its corners cut, white bevel top-left, grey bevel bottom-right
            if (x in (0, W - 1) and y in (0, 1, H - 2, H - 1)) or (y in (0, H - 1) and x in (0, 1, W - 2, W - 1)):
                continue
            if x == 0 or y == 0 or x == W - 1 or y == H - 1:
                c = GUI_EDGE
            elif (x <= 2 and y <= H - 4) or (y <= 2 and x <= W - 4):
                c = GUI_HI
            elif x >= W - 3 or y >= H - 3:
                c = GUI_SH
            else:
                c = GUI_BG
            px[x, y] = c + (255,)
    for x, y in ((1, 1), (W - 2, 1), (1, H - 2), (W - 2, H - 2)):
        px[x, y] = GUI_EDGE + (255,)

    def slot(x, y, size=18):
        for j in range(size):
            for i in range(size):
                if (i == 0 or j == 0) and not (i == size - 1 or j == size - 1):
                    c = SLOT_SH
                elif (i == size - 1 or j == size - 1) and not (i == 0 or j == 0):
                    c = SLOT_HI
                elif i == 0 or j == 0:
                    c = SLOT_BG
                else:
                    c = SLOT_BG
                px[x + i, y + j] = c + (255,)
    # the big slot round the trash slot (its item at 80, 35), a faint bin drawn in it
    slot(75, 30, 26)
    ghost = (124, 124, 124, 255)
    for x in range(85, 91):
        px[x, 35] = ghost
    for x in range(81, 95):
        px[x, 36] = ghost
        px[x, 37] = ghost
    for y in range(39, 51):
        for x in range(82, 94):
            if (x in (82, 93) and y == 50):
                continue
            px[x, y] = (132, 132, 132, 255) if x in (85, 88, 91) and 41 <= y <= 48 else ghost
    for row in range(3):
        for col in range(9):
            slot(7 + col * 18, 83 + row * 18)
    for col in range(9):
        slot(7 + col * 18, 141)
    return im


# ===================================================================== models
FACES = ('down', 'up', 'north', 'south', 'east', 'west')


def box(fr, to, tex='#side', faces=None, **kw):
    f = {d: {'texture': tex} for d in FACES}
    if faces:
        for d, t in faces.items():
            if t is None:
                f.pop(d, None)
            else:
                f[d] = t if isinstance(t, dict) else {'texture': t}
    e = {'from': fr, 'to': to, 'faces': f}
    e.update(kw)
    return e


def plus(x0, x1, z0, z1, y0, y1, tex='#side', faces=None, inset=1):
    """A round-ish can section: two boxes in a plus, their corners a model unit in (a chamfered square)."""
    return [box([x0, y0, z0 + inset], [x1, y1, z1 - inset], tex, faces), box([x0 + inset, y0, z0], [x1 - inset, y1, z1], tex, faces)]


def ring(lo, hi, y0, y1, inner=(3, 13), inset=1):
    """A plus section with the opening (inner square) left out: four boxes, overlapping at the chamfers."""
    a, b = inner
    return [box([lo + inset, y0, lo], [hi - inset, y1, a]), box([lo + inset, y0, b], [hi - inset, y1, hi]),
            box([lo, y0, lo + inset], [a, y1, hi - inset]), box([b, y0, lo + inset], [hi, y1, hi - inset])]


def body():
    els = []
    # the drum, up to the inside's floor (y 12); its front carries the face
    a, b = plus(2, 14, 2, 14, 0, 12, faces={'up': None})
    b['faces']['north'] = {'texture': '#front'}
    a['faces']['down'] = b['faces']['down'] = {'texture': '#bottom'}
    els += [a, b]
    els.append(box([3, 11.9, 3], [13, 12, 13], faces={d: None for d in FACES if d != 'up'} | {'up': '#inside'}))
    # the wall above it and the rolled rim, a half unit proud
    els += ring(2, 14, 12, 13.25)
    els += ring(1.5, 14.5, 13.25, 14)
    # the foot
    els += plus(1.75, 14.25, 1.75, 14.25, 0, 0.75, '#bottom', inset=1)
    # the handles on east and west: a grip on two lugs
    for x0, x1, gx0, gx1 in ((14, 15.25, 15.25, 16), (0.75, 2, 0, 0.75)):
        els += [box([x0, 10, 5.5], [x1, 10.75, 6.5], '#bottom'), box([x0, 10, 9.5], [x1, 10.75, 10.5], '#bottom'),
                box([gx0, 9.75, 5.5], [gx1, 11, 10.5], '#side')]
    # the hinge knuckles at the back, either side of the lid's
    els += [box([4, 13, 14], [6, 14.25, 15]), box([10, 13, 14], [12, 14.25, 15])]
    return els


LID_Y = 14


def lid():
    """Closed, its hinge on the back edge (z 14.75, y 14.1: client/TrashCanRenderer swings it from there)."""
    y0, y1 = LID_Y, LID_Y + 1
    els = plus(1.25, 14.75, 1.25, 14.75, y0, y1, '#side', {'up': '#top', 'down': '#bottom'}, inset=1)
    els += plus(4, 12, 4, 12, y1, y1 + 0.5, '#side', {'up': '#top'}, inset=1)
    els += [box([5.5, y1 + 0.5, 7.5], [6.5, y1 + 1.75, 8.5], '#bottom'), box([9.5, y1 + 0.5, 7.5], [10.5, y1 + 1.75, 8.5], '#bottom'),
            box([5.5, y1 + 1.75, 7.25], [10.5, y1 + 2.5, 8.75], '#side')]
    els.append(box([6, 13.5, 14.25], [10, 14.75, 15.25]))
    return els


def happy_plane():
    """Just off the front drum face, with the same UVs as #front."""
    return [{'from': [3, 0, 1.98], 'to': [13, 12, 1.98], 'faces': {'north': {'texture': '#face'}}}]


TEX = {'particle': 'papon:block/trash_can_side', 'side': 'papon:block/trash_can_side', 'front': 'papon:block/trash_can_front',
       'top': 'papon:block/trash_can_top', 'bottom': 'papon:block/trash_can_bottom', 'inside': 'papon:block/trash_can_inside',
       'face': 'papon:block/trash_can_happy'}


def write_json(rel, data):
    path = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', newline='\n') as f:
        json.dump(data, f, separators=(',', ':'))
    print(path)


def write_png(rel, im):
    path = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)
    print(path)


def model(els):
    used = {k: v for k, v in TEX.items() if k == 'particle' or any(f.get('texture') == '#' + k for e in els for f in e['faces'].values())}
    return {'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout', 'textures': used, 'elements': els}


def main():
    write_png('textures/block/trash_can_side.png', steel())
    write_png('textures/block/trash_can_front.png', face(steel()))
    write_png('textures/block/trash_can_happy.png', happy())
    write_png('textures/block/trash_can_top.png', lid_top())
    write_png('textures/block/trash_can_bottom.png', bottom())
    write_png('textures/block/trash_can_inside.png', inside())
    write_png('textures/gui/trash_can.png', gui())
    for name, els in (('trash_can', body()), ('trash_can_lid', lid()), ('trash_can_happy', happy_plane())):
        write_json('models/block/%s.json' % name, model(els))
    write_json('models/item/trash_can.json', model(body() + lid()))
    write_json('blockstates/trash_can.json', {'variants': {
        'facing=%s' % f: {'model': 'papon:block/trash_can', 'y': y} for f, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270))}})


if __name__ == '__main__':
    main()
