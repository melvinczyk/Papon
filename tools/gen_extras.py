#!/usr/bin/env python3
"""Odds and ends for the end of the fight and the crowd:

  textures/gui/potato_splat.png           384x128: three splats of mashed potato (client/PotatoSplat lays
                                           them over the screen when a fan's potato hits you)
  textures/mob_effect/splattered.png       18x18: the Splattered effect icon (a splat)
  textures/mob_effect/post_nut_clarity.png 18x18: Post Nut Clarity (the body pillow, effect/PostNutClarityEffect): a pale,
                                           blank face, thousand-yard stare, a bead of sweat
  textures/item/body_pillow.png            256x256: the dakimakura Gerbin gives the winner: Makima (Chainsaw Man),
                                           the user's picture (tools/src/makima_pillow.jpg, its two panels found by
                                           their outlines and straightened row by row): front 96x256 at (0,0), back
                                           96x256 at (96,0), the white cotton at (192,0). Without the picture, the
                                           drawn fan art below.
  models/item/body_pillow.json             builtin/entity: the pillow is a soft mesh drawn by client/PillowMesh
                                           (squeezed, folded, slouched), here only its display transforms
  textures/entity/tongue.png               the player's tongue, licking it
  textures/entity/sun_stone.png            256x256: El Sol Azteca (phase three): a Piedra del Sol in gold and stone, its rings
                                           of glyphs and sun rays round Papon's grinning potato face (client/BossRenderer)

To use another picture, put it at tools/src/makima_pillow.jpg (two pillow panels side by side, outlined
in a dark line, front on the left) and run this again.

  python tools/gen_extras.py
"""
import json
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/papon')


# ===================================================================== potato splats

def blob(rng, size, lobes):
    """A splat outline: a circle pushed about by a few sine lobes, with drips."""
    cx = cy = size / 2
    pts = []
    phase = [rng.random() * 6.28 for _ in range(4)]
    for i in range(180):
        a = i / 180 * math.tau
        r = size * 0.30 * (1 + 0.16 * math.sin(lobes * a + phase[0]) + 0.08 * math.sin((lobes + 3) * a + phase[1])
                           + 0.05 * math.sin(11 * a + phase[2]))
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts


def splats():
    rng = random.Random(77)
    S = 128
    big = 4
    sheet = Image.new('RGBA', (S * 3, S), (0, 0, 0, 0))
    for k in range(3):
        im = Image.new('RGBA', (S * big, S * big), (0, 0, 0, 0))
        d = ImageDraw.Draw(im)
        pts = [(x * big, y * big) for x, y in blob(rng, S, 5 + k)]
        mash = (238, 220, 168, 246)
        d.polygon(pts, fill=mash)
        # satellites: little flecks flung off it
        for _ in range(9):
            a = rng.random() * math.tau
            r = S * (0.36 + rng.random() * 0.1)
            rr = S * (0.025 + rng.random() * 0.035)
            x, y = S / 2 + r * math.cos(a), S / 2 + r * math.sin(a)
            d.ellipse([(x - rr) * big, (y - rr) * big, (x + rr) * big, (y + rr) * big], fill=mash)
        # drips running down
        for _ in range(3):
            x = S * (0.32 + rng.random() * 0.36)
            y0 = S * 0.62
            ln = S * (0.12 + rng.random() * 0.2)
            w = S * (0.025 + rng.random() * 0.02)
            d.rounded_rectangle([(x - w) * big, y0 * big, (x + w) * big, (y0 + ln) * big], radius=w * big, fill=mash)
            d.ellipse([(x - w * 1.4) * big, (y0 + ln - w) * big, (x + w * 1.4) * big, (y0 + ln + w * 1.6) * big], fill=mash)
        im = im.resize((S, S), Image.LANCZOS)
        px = im.load()
        # shading: lumpy mash, darker toward the edges, a buttery shine, bits of brown skin
        for y in range(S):
            for x in range(S):
                r, g, b, a = px[x, y]
                if a == 0:
                    continue
                n = math.sin(x * 0.45 + y * 0.2) * math.sin(y * 0.38 - x * 0.12) + rng.random() * 0.3
                dist = math.hypot(x - S / 2, y - S / 2) / (S * 0.4)
                k2 = 1.0 - 0.18 * max(0, dist - 0.4) + 0.05 * n
                px[x, y] = (int(min(255, r * k2)), int(min(255, g * k2)), int(min(255, b * k2 * 0.97)), a)
        d = ImageDraw.Draw(im)
        for _ in range(26):
            x, y = S / 2 + rng.gauss(0, S * 0.13), S / 2 + rng.gauss(0, S * 0.13)
            if px[int(max(0, min(S - 1, x))), int(max(0, min(S - 1, y)))][3] < 200:
                continue
            w = 1 + rng.random() * 2.5
            d.ellipse([x - w, y - w * 0.6, x + w, y + w * 0.6], fill=(150, 104, 52, 230))
        for _ in range(5):
            x, y = S * 0.42 + rng.random() * S * 0.1, S * 0.36 + rng.random() * S * 0.1
            d.ellipse([x - 4, y - 2.5, x + 4, y + 2.5], fill=(255, 250, 226, 200))
        # a pat of melting butter on one
        if k == 1:
            d.rectangle([S * 0.52, S * 0.40, S * 0.62, S * 0.48], fill=(252, 226, 96, 240))
        sheet.alpha_composite(im, (k * S, 0))
    out = os.path.join(ASSETS, 'textures/gui/potato_splat.png')
    os.makedirs(os.path.dirname(out), exist_ok=True)
    sheet.save(out)
    icon = sheet.crop((0, 0, S, S)).resize((18, 18), Image.LANCZOS)
    icon.save(os.path.join(ASSETS, 'textures/mob_effect/splattered.png'))
    return out


# ===================================================================== the body pillow

HAIR = (214, 104, 86)
HAIR_DK = (168, 70, 60)
HAIR_LT = (238, 146, 122)
SKIN = (252, 228, 212)
SKIN_SH = (232, 196, 180)
SHIRT = (250, 250, 252)
SHIRT_SH = (214, 216, 226)
TIE = (24, 24, 30)
SUIT = (36, 36, 44)
EYE_RING = (226, 182, 60)
EYE_DK = (150, 96, 28)
SHEET = (252, 236, 240)
SHEET_DK = (240, 214, 224)


def ring_eye(d, cx, cy, r):
    """Her eyes: gold, ring inside ring."""
    d.ellipse([cx - r * 1.25, cy - r, cx + r * 1.25, cy + r], fill=(255, 255, 255))
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=EYE_RING)
    for k, c in ((0.78, EYE_DK), (0.62, EYE_RING), (0.42, EYE_DK), (0.26, EYE_RING), (0.12, (60, 34, 14))):
        d.ellipse([cx - r * k, cy - r * k, cx + r * k, cy + r * k], fill=c)
    d.ellipse([cx - r * 0.55, cy - r * 0.75, cx - r * 0.15, cy - r * 0.4], fill=(255, 252, 236))
    # the lash line over it
    d.arc([cx - r * 1.4, cy - r * 1.15, cx + r * 1.4, cy + r * 0.9], 200, 340, fill=(60, 30, 26), width=max(2, int(r * 0.3)))


def front(W, H):
    """Lying on her back on the sheet, looking up at you: hair spread out on the pillow, the braid
    over her shoulder, white shirt, black tie, one hand up by her face."""
    im = Image.new('RGBA', (W, H), SHEET + (255,))
    d = ImageDraw.Draw(im)
    # the sheet's little pattern
    for y in range(0, H, 40):
        for x in range(0 if (y // 40) % 2 else 20, W, 40):
            d.ellipse([x - 3, y - 3, x + 3, y + 3], fill=SHEET_DK)
    cx = W / 2
    fy = H * 0.17        # face centre
    fr = W * 0.24
    # hair spread on the pillow behind her head
    d.ellipse([cx - fr * 2.0, fy - fr * 1.7, cx + fr * 2.0, fy + fr * 1.6], fill=HAIR)
    # soft strands curling through it
    for i in range(6):
        k = i / 5
        box = [cx - fr * (1.2 + 0.7 * k), fy - fr * (1.1 + 0.5 * k), cx + fr * (1.2 + 0.7 * k), fy + fr * (1.0 + 0.5 * k)]
        d.arc(box, 150 + 10 * i, 210 + 8 * i, fill=HAIR_DK if i % 2 else HAIR_LT, width=int(W * 0.022))
        d.arc(box, 330 - 8 * i, 30 - 10 * i, fill=HAIR_LT if i % 2 else HAIR_DK, width=int(W * 0.022))
    # neck and shoulders, shirt, jacket-less (the jacket is off: it is bedtime)
    d.rectangle([cx - fr * 0.35, fy + fr * 0.8, cx + fr * 0.35, fy + fr * 1.5], fill=SKIN_SH)
    body_top = fy + fr * 1.35
    d.rounded_rectangle([cx - fr * 1.6, body_top, cx + fr * 1.6, H * 0.62], radius=int(fr * 0.6), fill=SHIRT)
    # collar and tie
    d.polygon([(cx - fr * 0.55, body_top), (cx, body_top + fr * 0.55), (cx + fr * 0.55, body_top)], fill=SHIRT_SH)
    d.polygon([(cx - fr * 0.16, body_top + fr * 0.3), (cx + fr * 0.16, body_top + fr * 0.3), (cx + fr * 0.26, H * 0.45),
               (cx, H * 0.49), (cx - fr * 0.26, H * 0.45)], fill=TIE)
    d.polygon([(cx - fr * 0.22, body_top + fr * 0.12), (cx + fr * 0.22, body_top + fr * 0.12), (cx + fr * 0.14, body_top + fr * 0.38),
               (cx - fr * 0.14, body_top + fr * 0.38)], fill=(40, 40, 48))
    # shirt folds, buttons
    for y in range(int(body_top + fr), int(H * 0.6), int(W * 0.12)):
        d.line([cx + fr * 0.5, y, cx + fr * 1.1, y + W * 0.05], fill=SHIRT_SH, width=2)
    # trousers and shoes
    d.rounded_rectangle([cx - fr * 1.3, H * 0.6, cx + fr * 1.3, H * 0.68], radius=6, fill=SUIT)
    d.polygon([(cx - fr * 1.3, H * 0.66), (cx - fr * 0.08, H * 0.66), (cx - fr * 0.2, H * 0.92), (cx - fr * 1.05, H * 0.92)], fill=SUIT)
    d.polygon([(cx + fr * 1.3, H * 0.66), (cx + fr * 0.08, H * 0.66), (cx + fr * 0.2, H * 0.92), (cx + fr * 1.05, H * 0.92)], fill=SUIT)
    d.line([cx, H * 0.68, cx, H * 0.9], fill=(20, 20, 26), width=2)
    for s in (-1, 1):
        d.ellipse([cx + s * fr * 0.62 - fr * 0.5, H * 0.91, cx + s * fr * 0.62 + fr * 0.5, H * 0.96], fill=(16, 16, 20))
    # her left arm along her side, the right hand up by her face
    d.rounded_rectangle([cx + fr * 1.45, body_top + fr * 0.3, cx + fr * 1.95, H * 0.58], radius=int(fr * 0.25), fill=SHIRT)
    d.ellipse([cx + fr * 1.4, H * 0.56, cx + fr * 2.0, H * 0.61], fill=SKIN)
    d.polygon([(cx - fr * 1.45, body_top + fr * 0.4), (cx - fr * 1.95, body_top + fr * 0.6), (cx - fr * 1.7, fy + fr * 0.4),
               (cx - fr * 1.25, fy + fr * 0.5)], fill=SHIRT)
    d.ellipse([cx - fr * 1.85, fy - fr * 0.05, cx - fr * 1.15, fy + fr * 0.6], fill=SKIN)
    # the face
    d.ellipse([cx - fr, fy - fr * 1.05, cx + fr, fy + fr * 1.0], fill=SKIN)
    d.polygon([(cx - fr * 0.85, fy + fr * 0.2), (cx, fy + fr * 1.12), (cx + fr * 0.85, fy + fr * 0.2)], fill=SKIN)
    # bangs: parted, framing the face, a long lock down each side
    d.chord([cx - fr * 1.12, fy - fr * 1.3, cx + fr * 1.12, fy + fr * 0.2], 180, 360, fill=HAIR)
    for i, x in enumerate([-0.8, -0.45, -0.1, 0.25, 0.6]):
        d.polygon([(cx + (x - 0.2) * fr, fy - fr * 0.6), (cx + (x + 0.2) * fr, fy - fr * 0.6), (cx + (x + 0.05) * fr, fy - fr * (0.05 + 0.08 * (i % 2)))],
                  fill=HAIR if i % 2 else HAIR_DK)
    for s in (-1, 1):
        d.polygon([(cx + s * fr * 0.95, fy - fr * 0.5), (cx + s * fr * 1.18, fy - fr * 0.2), (cx + s * fr * 1.05, fy + fr * 0.95),
                   (cx + s * fr * 0.8, fy + fr * 0.55)], fill=HAIR)
    # the braid over her right shoulder
    bx, by = cx - fr * 0.95, fy + fr * 0.9
    for i in range(7):
        y = by + i * fr * 0.36
        x = bx - i * fr * 0.08
        d.ellipse([x - fr * 0.26, y - fr * 0.2, x + fr * 0.26, y + fr * 0.22], fill=HAIR if i % 2 else HAIR_LT, outline=HAIR_DK, width=2)
    d.polygon([(bx - fr * 0.7, by + fr * 2.5), (bx - fr * 0.4, by + fr * 2.5), (bx - fr * 0.55, by + fr * 2.85)], fill=HAIR_DK)
    # eyes, brows, a small smile, a blush
    ey = fy + fr * 0.05
    for s in (-1, 1):
        ring_eye(d, cx + s * fr * 0.42, ey, fr * 0.2)
        d.line([cx + s * fr * 0.22, ey - fr * 0.38, cx + s * fr * 0.62, ey - fr * 0.42], fill=HAIR_DK, width=2)
        d.ellipse([cx + s * fr * 0.55 - fr * 0.14, ey + fr * 0.28, cx + s * fr * 0.55 + fr * 0.14, ey + fr * 0.38], fill=(246, 180, 170))
    d.arc([cx - fr * 0.18, fy + fr * 0.42, cx + fr * 0.18, fy + fr * 0.62], 20, 160, fill=(170, 80, 76), width=2)
    return im


def back(W, H):
    """The other side: her face up close with that small smile, and the word she is known for."""
    im = Image.new('RGBA', (W, H), (44, 36, 48, 255))
    d = ImageDraw.Draw(im)
    for y in range(H):
        k = y / H
        d.line([0, y, W, y], fill=(int(60 + 120 * k), int(30 + 30 * k), int(52 + 20 * k)))
    cx, fy, fr = W / 2, H * 0.36, W * 0.36
    d.ellipse([cx - fr * 1.5, fy - fr * 1.4, cx + fr * 1.5, fy + fr * 2.2], fill=HAIR)
    d.ellipse([cx - fr, fy - fr * 1.05, cx + fr, fy + fr * 1.0], fill=SKIN)
    d.polygon([(cx - fr * 0.85, fy + fr * 0.2), (cx, fy + fr * 1.15), (cx + fr * 0.85, fy + fr * 0.2)], fill=SKIN)
    d.chord([cx - fr * 1.12, fy - fr * 1.3, cx + fr * 1.12, fy + fr * 0.2], 180, 360, fill=HAIR)
    for i, x in enumerate([-0.8, -0.45, -0.1, 0.25, 0.6]):
        d.polygon([(cx + (x - 0.2) * fr, fy - fr * 0.6), (cx + (x + 0.2) * fr, fy - fr * 0.6), (cx + (x + 0.05) * fr, fy - fr * 0.1)],
                  fill=HAIR if i % 2 else HAIR_DK)
    ey = fy + fr * 0.08
    for s in (-1, 1):
        ring_eye(d, cx + s * fr * 0.42, ey, fr * 0.2)
    d.arc([cx - fr * 0.16, fy + fr * 0.5, cx + fr * 0.16, fy + fr * 0.66], 20, 160, fill=(170, 80, 76), width=3)
    for s in (-1, 1):
        d.ellipse([cx + s * fr * 0.55 - fr * 0.14, ey + fr * 0.28, cx + s * fr * 0.55 + fr * 0.14, ey + fr * 0.38], fill=(246, 180, 170))
    # shirt and tie at the bottom of the frame
    d.rounded_rectangle([cx - fr * 1.4, fy + fr * 1.35, cx + fr * 1.4, H + 20], radius=20, fill=SHIRT)
    d.polygon([(cx - fr * 0.14, fy + fr * 1.45), (cx + fr * 0.14, fy + fr * 1.45), (cx + fr * 0.22, H), (cx - fr * 0.22, H)], fill=TIE)
    return im


def woof(im, W, H):
    """"Woof" over her, in big friendly letters, drawn as pixels (no font needed)."""
    letters = {
        'W': ['10001', '10001', '10101', '10101', '01010'],
        'o': ['00000', '01110', '10001', '10001', '01110'],
        'f': ['00110', '01000', '11100', '01000', '01000'],
        '.': ['00000', '00000', '00000', '00000', '01000'],
    }
    px = im.load()
    x0, y0, s = 3, int(H * 0.05), 2
    for ch in 'Woof':
        rows = letters[ch]
        for j, row in enumerate(rows):
            for i, c in enumerate(row):
                if c == '1':
                    for dy in range(s):
                        for dx in range(s):
                            px[x0 + i * s + dx, y0 + j * s + dy] = (255, 196, 206, 255)
        x0 += 5 * s + 1


PICTURE = os.path.join(ROOT, 'tools/src/makima_pillow.jpg')


def panels(path, W, H):
    """The two pillow panels of a dakimakura listing picture, each straightened into a W x H rectangle: per row the
    dark outline's left and right edges in each half of the picture, the inside between them stretched to the width;
    rows from the panel's first to last that are at least a third of its width wide."""
    import numpy as np
    src = Image.open(path).convert('RGB')
    a = np.asarray(src).astype(np.int32)
    grey = a.mean(axis=2)
    h, w = grey.shape
    out = []
    for x0, x1 in ((0, w // 2), (w // 2, w)):
        # the outline is the outermost dark line: look for it only in the outer quarter on each side (not in her hair),
        # then smooth the edges over the rows (a median), so a stray dark pixel cannot pull a row out of line
        q = (x1 - x0) // 4
        raw = {}
        for y in range(h):
            left = np.nonzero(grey[y, x0:x0 + q] < 90)[0]
            right = np.nonzero(grey[y, x1 - q:x1] < 90)[0]
            if len(left) and len(right):
                raw[y] = (x0 + left[0] + 3, x1 - q + right[-1] - 3)
        ys = sorted(raw)
        top, bottom = ys[0] + 4, ys[-1] - 4
        spans = []
        for y in range(top, bottom + 1):
            near = [raw[k] for k in range(y - 6, y + 7) if k in raw]
            if not near:
                continue
            spans.append((y, int(np.median([n[0] for n in near])), int(np.median([n[1] for n in near]))))
        by_y = {y: (l, r) for y, l, r in spans}
        rows = []
        last = (spans[len(spans) // 2][1], spans[len(spans) // 2][2])
        for y in range(top, bottom + 1):
            l, r = by_y.get(y, last)
            last = (l, r)
            row = src.crop((l, y, r, y + 1)).resize((W * 2, 1), Image.LANCZOS)
            rows.append(row)
        panel = Image.new('RGB', (W * 2, len(rows)))
        for i, row in enumerate(rows):
            panel.paste(row, (0, i))
        out.append(panel.resize((W, H), Image.LANCZOS).convert('RGBA'))
    return out


def pillow():
    tex = Image.new('RGBA', (256, 256), (0, 0, 0, 0))
    if os.path.exists(PICTURE):
        f, b = panels(PICTURE, 96, 256)
    else:
        W, H = 48, 128
        big = 4
        f = front(W * big, H * big).resize((96, 256), Image.LANCZOS)
        b = back(W * big, H * big).resize((W, H), Image.LANCZOS)
        woof(b, W, H)
        b = b.resize((96, 256), Image.NEAREST)
    tex.paste(f, (0, 0))
    tex.paste(b, (96, 0))
    cotton = Image.new('RGBA', (64, 256), (246, 242, 244, 255))
    tex.paste(cotton, (192, 0))
    out = os.path.join(ASSETS, 'textures/item/body_pillow.png')
    tex.save(out)
    model = {
        'parent': 'builtin/entity',
        'textures': {'particle': 'papon:item/body_pillow'},
        'display': {
            # client/PillowItemRenderer draws the mesh upright in the unit box, its length the box's height
            'gui': {'rotation': [0, 200, -24], 'translation': [0, 0, 0], 'scale': [0.95, 0.95, 0.95]},
            'ground': {'rotation': [90, 0, 0], 'translation': [0, 2, 0], 'scale': [0.6, 0.6, 0.6]},
            'fixed': {'rotation': [0, 180, 0], 'scale': [1, 1, 1]},
            # in third person the pillow is drawn by client/PillowLayer (hugged, licked, humped, under the arm)
            'thirdperson_righthand': {'rotation': [0, 180, 0], 'translation': [0, 3, 2], 'scale': [0, 0, 0]},
            'thirdperson_lefthand': {'rotation': [0, 180, 0], 'translation': [0, 3, 2], 'scale': [0, 0, 0]},
            'firstperson_righthand': {'rotation': [0, 160, 10], 'translation': [-2, 2, 0], 'scale': [0.62, 0.62, 0.62]},
            'firstperson_lefthand': {'rotation': [0, 200, -10], 'translation': [-2, 2, 0], 'scale': [0.62, 0.62, 0.62]},
            'head': {'rotation': [0, 180, 0], 'translation': [0, -6, -6], 'scale': [1, 1, 1]},
        },
    }
    mpath = os.path.join(ASSETS, 'models/item/body_pillow.json')
    with open(mpath, 'w', newline='\n') as fh:
        json.dump(model, fh, separators=(',', ':'))
    return out, mpath


def tongue():
    """16x16: the player's tongue when licking the pillow (client/PillowLayer): pink, a darker groove
    down the middle, a wet highlight."""
    im = Image.new('RGBA', (16, 16), (232, 112, 128, 255))
    d = ImageDraw.Draw(im)
    d.rectangle([7, 0, 8, 15], fill=(196, 78, 98, 255))
    d.rectangle([3, 2, 4, 6], fill=(255, 178, 190, 255))
    d.rectangle([11, 9, 12, 12], fill=(250, 150, 164, 255))
    out = os.path.join(ASSETS, 'textures/entity/tongue.png')
    im.save(out)
    return out


def sun_stone():
    """A potato Piedra del Sol: a disc of carved rings (rays, a band of glyph squares, dots, the four "movement" lobes)
    in gold with dark stone in the cuts and turquoise inlay, and in the middle Papon's face, grinning, tongue out like
    Tonatiuh's. Transparent outside the disc. Drawn at 4x and shrunk, so the carving reads at any size."""
    S = 1024
    im = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    c = S / 2
    gold, gold_dk, gold_lt = (232, 178, 64, 255), (150, 98, 30, 255), (255, 222, 120, 255)
    stone, turq, red = (70, 52, 34, 255), (60, 196, 186, 255), (204, 52, 44, 255)

    def ring(r0, r1, fill):
        d.ellipse([c - r1, c - r1, c + r1, c + r1], fill=fill)
        if r0 > 0:
            d.ellipse([c - r0, c - r0, c + r0, c + r0], fill=(0, 0, 0, 0))

    # the outer rays: sixteen points round the rim
    pts = []
    for i in range(32):
        a = math.pi * 2 * i / 32 - math.pi / 2
        r = 510 if i % 2 == 0 else 440
        pts.append((c + math.cos(a) * r, c + math.sin(a) * r))
    d.polygon(pts, fill=gold)
    d.ellipse([c - 450, c - 450, c + 450, c + 450], fill=gold_dk)
    d.ellipse([c - 436, c - 436, c + 436, c + 436], fill=gold)
    # a band of glyph squares, each a little carved potato glyph
    for i in range(40):
        a = math.pi * 2 * i / 40
        x, y = c + math.cos(a) * 395, c + math.sin(a) * 395
        k = 22
        d.rectangle([x - k, y - k, x + k, y + k], fill=stone)
        d.rectangle([x - k + 6, y - k + 6, x + k - 6, y + k - 6], fill=gold_lt if i % 3 else turq)
        d.ellipse([x - 8, y - 5, x + 8, y + 5], fill=stone)
    d.ellipse([c - 360, c - 360, c + 360, c + 360], fill=gold_dk)
    d.ellipse([c - 348, c - 348, c + 348, c + 348], fill=gold)
    # dots
    for i in range(60):
        a = math.pi * 2 * i / 60
        x, y = c + math.cos(a) * 325, c + math.sin(a) * 325
        d.ellipse([x - 9, y - 9, x + 9, y + 9], fill=stone if i % 2 else red)
    # the day signs: twenty boxes
    d.ellipse([c - 300, c - 300, c + 300, c + 300], fill=stone)
    d.ellipse([c - 290, c - 290, c + 290, c + 290], fill=gold)
    for i in range(20):
        a = math.pi * 2 * (i + 0.5) / 20
        x, y = c + math.cos(a) * 250, c + math.sin(a) * 250
        d.polygon([(x + math.cos(a + 1.57) * 26, y + math.sin(a + 1.57) * 26), (x + math.cos(a) * 30, y + math.sin(a) * 30),
                   (x - math.cos(a + 1.57) * 26, y - math.sin(a + 1.57) * 26), (x - math.cos(a) * 30, y - math.sin(a) * 30)],
                  fill=turq if i % 2 else gold_lt, outline=stone)
    d.ellipse([c - 205, c - 205, c + 205, c + 205], fill=stone)
    d.ellipse([c - 195, c - 195, c + 195, c + 195], fill=gold)
    # the four lobes (Nahui Ollin)
    for i in range(4):
        a = math.pi / 4 + math.pi / 2 * i
        x, y = c + math.cos(a) * 150, c + math.sin(a) * 150
        d.rectangle([x - 46, y - 46, x + 46, y + 46], fill=stone)
        d.rectangle([x - 36, y - 36, x + 36, y + 36], fill=gold_lt)
        d.ellipse([x - 18, y - 18, x + 18, y + 18], fill=red)
    # Papon in the middle: a golden potato face, white eyes, the grin, the tongue out
    d.ellipse([c - 120, c - 130, c + 120, c + 130], fill=(224, 164, 64, 255), outline=gold_dk, width=8)
    for sx in (-1, 1):
        d.rectangle([c + sx * 52 - 26, c - 50, c + sx * 52 + 26, c - 22], fill=(255, 255, 255, 255))
        d.rectangle([c + sx * 52 - 34, c - 74, c + sx * 52 + 30, c - 62], fill=(90, 54, 24, 255))
    d.rectangle([c - 64, c + 30, c + 64, c + 52], fill=(90, 54, 24, 255))
    d.rectangle([c - 22, c + 52, c + 22, c + 92], fill=red)
    d.rectangle([c - 22, c + 52, c + 22, c + 60], fill=(150, 30, 30, 255))
    im = im.resize((256, 256), Image.LANCZOS)
    out = os.path.join(ASSETS, 'textures/entity/sun_stone.png')
    im.save(out)
    return out


def white():
    """textures/misc/white.png: plain white, for solid shapes drawn in code and coloured per vertex (El Gigante's bones, the
    piñata's rope); vanilla 1.20.1 has no such texture."""
    from PIL import Image
    out = os.path.join(ASSETS, 'textures/misc/white.png')
    os.makedirs(os.path.dirname(out), exist_ok=True)
    Image.new('RGBA', (16, 16), (255, 255, 255, 255)).save(out)
    return out


def clarity_icon():
    """Post Nut Clarity: a pale grey-blue face gone blank (flat eyes, a flat line of a mouth), a bead of sweat."""
    S = 72
    im = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse([6, 6, S - 6, S - 6], fill=(28, 34, 44, 255))
    d.ellipse([10, 10, S - 10, S - 10], fill=(201, 214, 227, 255))
    d.ellipse([14, 12, S - 22, S - 30], fill=(222, 232, 241, 255))
    for x in (22, 42):
        d.rectangle([x, 30, x + 9, 34], fill=(46, 54, 66, 255))      # eyes: half shut, staring at nothing
        d.rectangle([x - 1, 25, x + 10, 27], fill=(140, 154, 170, 255))
    d.rectangle([26, 48, 46, 51], fill=(70, 80, 94, 255))            # a flat line of a mouth
    d.polygon([(55, 16), (51, 25), (59, 25)], fill=(140, 200, 245, 255))
    d.ellipse([50, 21, 60, 31], fill=(140, 200, 245, 255))           # a bead of sweat
    d.ellipse([53, 23, 56, 26], fill=(235, 248, 255, 255))
    out = os.path.join(ASSETS, 'textures/mob_effect/post_nut_clarity.png')
    im.resize((18, 18), Image.LANCZOS).save(out)
    return out


if __name__ == '__main__':
    import sys
    if sys.argv[1:] == ['white']:
        print(white())
        raise SystemExit
    print(white())
    print(splats())
    print(*pillow())
    print(tongue())
    print(sun_stone())
