#!/usr/bin/env python3
"""Odds and ends for the end of the fight and the crowd:

  textures/gui/potato_splat.png           384x128: three splats of mashed potato (client/PotatoSplat lays
                                           them over the screen when a fan's potato hits you)
  textures/mob_effect/splattered.png       18x18: the Splattered effect icon (a splat)
  textures/item/body_pillow.png            128x128: the dakimakura Gerbin gives the winner, a fan-art
                                           Makima (Chainsaw Man) drawn here: front 48x128 at (0,0), back
                                           48x128 at (48,0), the seam strip 8x128 at (96,0), the ends at (104,0)
  models/item/body_pillow.json             a long soft pillow (6 x 16 x 2.5) with that texture (third person:
                                           drawn by client/PillowLayer instead)
  textures/entity/tongue.png               the player's tongue, licking it

The pillow art is our own drawing (it is not from anywhere); to use another picture, draw it over
the front/back regions of body_pillow.png (48x128 each) and leave the rest.

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


def pillow():
    W, H = 48, 128
    big = 4
    tex = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
    f = front(W * big, H * big).resize((W, H), Image.LANCZOS)
    b = back(W * big, H * big).resize((W, H), Image.LANCZOS)
    woof(b, W, H)
    tex.paste(f, (0, 0))
    tex.paste(b, (48, 0))
    # the side seam and the ends: white cotton, a pink piping
    side = Image.new('RGBA', (8, 128), (246, 242, 244, 255))
    sd = ImageDraw.Draw(side)
    sd.line([3, 0, 3, 127], fill=(236, 160, 180, 255), width=2)
    tex.paste(side, (96, 0))
    end = Image.new('RGBA', (8, 8), (246, 242, 244, 255))
    ImageDraw.Draw(end).rectangle([0, 3, 7, 4], fill=(236, 160, 180, 255))
    tex.paste(end, (104, 0))
    out = os.path.join(ASSETS, 'textures/item/body_pillow.png')
    tex.save(out)
    # the model: 6 x 16 x 2.5, standing up in the item's 16-unit box; UVs in 16ths of the texture
    u = lambda px: px / 8.0
    faces = {
        'north': {'uv': [u(0), u(0), u(48), u(128)], 'texture': '#0'},
        'south': {'uv': [u(48), u(0), u(96), u(128)], 'texture': '#0'},
        'east': {'uv': [u(96), u(0), u(100), u(128)], 'texture': '#0'},
        'west': {'uv': [u(100), u(0), u(104), u(128)], 'texture': '#0'},
        'up': {'uv': [u(104), u(0), u(112), u(8)], 'texture': '#0'},
        'down': {'uv': [u(104), u(0), u(112), u(8)], 'texture': '#0'},
    }
    model = {
        'texture_size': [128, 128],
        'textures': {'0': 'papon:item/body_pillow', 'particle': 'papon:item/body_pillow'},
        'elements': [
            {'from': [5, 0, 6.75], 'to': [11, 16, 9.25], 'faces': faces},
        ],
        'display': {
            'gui': {'rotation': [0, 180, -30], 'translation': [0, 0, 0], 'scale': [0.85, 0.85, 0.85]},
            'ground': {'rotation': [90, 0, 0], 'translation': [0, 2, 0], 'scale': [0.6, 0.6, 0.6]},
            'fixed': {'rotation': [0, 180, 0], 'scale': [1, 1, 1]},
            # in third person the pillow is drawn by client/PillowLayer (hugged, licked, under the arm)
            'thirdperson_righthand': {'rotation': [0, 180, 0], 'translation': [0, 3, 2], 'scale': [0, 0, 0]},
            'thirdperson_lefthand': {'rotation': [0, 180, 0], 'translation': [0, 3, 2], 'scale': [0, 0, 0]},
            'firstperson_righthand': {'rotation': [0, 160, 10], 'translation': [-2, 2, 0], 'scale': [0.7, 0.7, 0.7]},
            'firstperson_lefthand': {'rotation': [0, 200, -10], 'translation': [-2, 2, 0], 'scale': [0.7, 0.7, 0.7]},
            'head': {'rotation': [0, 180, 0], 'translation': [0, -6, -6], 'scale': [1, 1, 1]},
        },
    }
    mpath = os.path.join(ASSETS, 'models/item/body_pillow.json')
    with open(mpath, 'w', newline='\n') as fh:
        json.dump(model, fh, indent=2)
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


if __name__ == '__main__':
    print(splats())
    print(*pillow())
    print(tongue())
