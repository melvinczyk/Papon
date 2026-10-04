#!/usr/bin/env python3
"""Generates La Bandera Papa: the Mexican tricolour with Papon's smiling potato face for the coat of arms.

One design, used everywhere it flies (the user's call: the potato smiley in the middle of the flags,
the same on every one of them):
  textures/entity/potato_flag.png     120x68 (7:4, the real flag's proportions): green, white, red;
                                      in the white, Papon's face on a golden potato under a crown of
                                      fries, inside a laurel wreath tied with a tricolour ribbon.
                                      Drawn as waving cloth by client/ArenaFlagRenderer (the coliseum's
                                      podium, arches and the band's backdrop) and in the hands of the fans
                                      in the stands (client/ColiseumCrowdRenderer).
  drape(), emblem()                   used by gen_blocks.py for the stripe blocks (his box's canopy) in the
                                      same colours, and the emblem block (old builds) with the same face.

The emblem is painted at 8x and filtered down, so its curves read at the flag's size.
"""
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/papon')

W, H = 120, 68
S = 8

GREEN = (22, 108, 62)
WHITE = (242, 238, 228)
RED = (202, 34, 44)
POTATO = (222, 168, 74)
POTATO_HI = (244, 202, 112)
POTATO_DK = (172, 116, 40)
OUTLINE = (92, 58, 22)
BROW = (86, 54, 22)
FRY = (250, 210, 84)
FRY_HI = (255, 236, 150)
FRY_DK = (204, 150, 40)
LEAF = (46, 120, 50)
LEAF_DK = (28, 82, 36)
LEAF_HI = (98, 162, 70)
BERRY = (196, 36, 44)
BLUSH = (236, 120, 110)


def emblem(size):
    """The coat of arms on a transparent square canvas, `size` px on a side (painted at size * S)."""
    n = size * S
    im = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    c = n / 2
    u = n / 34.0                       # one emblem pixel

    def ell(cx, cy, rx, ry, fill, outline=None, width=0):
        d.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=fill, outline=outline, width=int(width))

    # the wreath: two laurel branches from the knot at the bottom up round both sides
    r = 13.2 * u
    for side in (-1, 1):
        for i in range(10):
            a = math.radians(16 + i * 14)                 # from the knot at the bottom up round his side
            x = c + side * r * math.sin(a)
            y = c + 1.5 * u + r * math.cos(a)
            tx, ty = side * math.cos(a), -math.sin(a)     # along the branch, upward
            nx, ny = side * math.sin(a), math.cos(a)      # outward
            for k, col in ((1, LEAF if i % 2 else LEAF_HI), (-1, LEAF_DK if i % 2 else LEAF)):
                lx, ly = x + nx * k * 1.3 * u, y + ny * k * 1.3 * u
                lw = int(5 * u)
                leaf = Image.new('RGBA', (lw, lw), (0, 0, 0, 0))
                ImageDraw.Draw(leaf).ellipse([lw * 0.1, lw * 0.34, lw * 0.9, lw * 0.66], fill=col)
                # pointing up the branch, splayed out (outer leaf) or in (inner leaf)
                ang = -math.degrees(math.atan2(ty, tx)) + k * side * -28
                leaf = leaf.rotate(ang, resample=Image.BICUBIC)
                im.alpha_composite(leaf, (int(lx - lw / 2), int(ly - lw / 2)))
            if i in (2, 6):
                ell(x + nx * 2.4 * u, y + ny * 2.4 * u, 0.85 * u, 0.85 * u, BERRY)
    # the knot: a tricolour ribbon, two tails
    for side, col in ((-1, GREEN), (1, RED)):
        d.polygon([(c, c + 14.2 * u), (c + side * 5.0 * u, c + 16.8 * u), (c + side * 3.4 * u, c + 15.0 * u),
                   (c + side * 4.6 * u, c + 13.0 * u)], fill=col)
    ell(c, c + 14.2 * u, 1.6 * u, 1.3 * u, WHITE, outline=(170, 160, 150), width=0.35 * u)

    # the crown of fries, fanned behind his head
    for i, ang in enumerate((-42, -22, -6, 8, 24, 42)):
        L = (8.2 if i in (2, 3) else 7.0) * u
        a = math.radians(ang)
        bx, by = c + math.sin(a) * 6.0 * u, c - 6.0 * u
        tx, ty = bx + math.sin(a) * L, by - math.cos(a) * L
        w = 1.25 * u
        nx, ny = math.cos(a) * w, math.sin(a) * w
        d.polygon([(bx - nx, by - ny), (bx + nx, by + ny), (tx + nx, ty + ny), (tx - nx, ty - ny)], fill=FRY, outline=FRY_DK)
        d.line([(bx - nx * 0.3, by - ny * 0.3), (tx - nx * 0.3, ty - ny * 0.3)], fill=FRY_HI, width=int(0.5 * u))
        ell(tx, ty, w * 0.95, w * 0.95, FRY_HI if i % 2 else FRY)

    # the potato: a lumpy golden oval, shaded, a few eyes in the skin
    cx, cy = c, c + 1.0 * u
    rx, ry = 9.6 * u, 10.6 * u
    pts = []
    for k in range(72):
        t = k / 72 * math.pi * 2
        lump = 1 + 0.035 * math.sin(3 * t + 0.6) + 0.025 * math.sin(5 * t + 2.1)
        pts.append((cx + rx * lump * math.cos(t), cy + ry * lump * math.sin(t)))
    d.polygon(pts, fill=POTATO, outline=OUTLINE)
    d.line(pts + [pts[0]], fill=OUTLINE, width=int(0.9 * u))
    # light from the top left, shadow round the bottom right
    shade = Image.new('RGBA', im.size, (0, 0, 0, 0))
    sd = ImageDraw.Draw(shade)
    sd.ellipse([cx - rx * 0.78, cy - ry * 0.86, cx + rx * 0.2, cy - ry * 0.05], fill=POTATO_HI + (150,))
    sd.ellipse([cx - rx * 0.1, cy + ry * 0.2, cx + rx * 0.95, cy + ry * 0.98], fill=POTATO_DK + (120,))
    shade = shade.filter(ImageFilter.GaussianBlur(1.6 * u))
    mask = Image.new('L', im.size, 0)
    ImageDraw.Draw(mask).polygon(pts, fill=255)
    mask = mask.filter(ImageFilter.MinFilter(int(1.2 * u) | 1))
    im.paste(Image.alpha_composite(im, shade), (0, 0), mask)
    d = ImageDraw.Draw(im)
    for ex, ey in ((-6.2, -5.5), (6.6, 3.8), (-5.4, 6.8), (2.8, -8.2)):
        ell(cx + ex * u, cy + ey * u, 0.55 * u, 0.4 * u, POTATO_DK)

    # his face: Papon's brows and eyes, rosy cheeks, the big smile
    for side in (-1, 1):
        bx = cx + side * 3.6 * u
        d.arc([bx - 2.0 * u, cy - 6.0 * u, bx + 2.0 * u, cy - 3.6 * u], 200, 340, fill=BROW, width=int(1.0 * u))
        ell(bx, cy - 1.8 * u, 1.25 * u, 1.65 * u, (40, 26, 14))
        ell(bx - 0.45 * u, cy - 2.4 * u, 0.42 * u, 0.42 * u, (255, 250, 236))
        ell(cx + side * 6.0 * u, cy + 1.8 * u, 1.6 * u, 1.0 * u, BLUSH + (170,))
    # the smile: a wide dark arc, a sliver of tongue, a highlight on the lip
    d.chord([cx - 5.4 * u, cy - 2.6 * u, cx + 5.4 * u, cy + 6.4 * u], 12, 168, fill=(70, 30, 20))
    d.chord([cx - 3.0 * u, cy + 3.0 * u, cx + 3.0 * u, cy + 6.2 * u], 20, 160, fill=(214, 92, 84))
    d.arc([cx - 5.4 * u, cy - 2.6 * u, cx + 5.4 * u, cy + 6.4 * u], 12, 168, fill=OUTLINE, width=int(0.7 * u))
    for side in (-1, 1):
        ell(cx + side * 5.4 * u, cy + 1.6 * u, 0.55 * u, 0.55 * u, OUTLINE)

    return im.resize((size, size), Image.LANCZOS)


def weave(im, rng, k=0.025):
    """A faint cloth grain over a finished texture."""
    px = im.load()
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            f = 1 + rng.uniform(-k, k) + (0.012 if (x + y) % 2 else -0.012)
            px[x, y] = (min(255, int(r * f)), min(255, int(g * f)), min(255, int(b * f)), a)


def flag():
    rng = random.Random(77)
    im = Image.new('RGBA', (W, H))
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 39, H], fill=GREEN)
    d.rectangle([40, 0, 79, H], fill=WHITE)
    d.rectangle([80, 0, W, H], fill=RED)
    e = emblem(40)
    im.alpha_composite(e, (40, (H - 40) // 2))
    weave(im, rng)
    # a hem down the hoist (the pole side) and along the fly
    for y in range(H):
        im.putpixel((0, y), tuple(int(v * 0.8) for v in GREEN) + (255,))
        im.putpixel((W - 1, y), tuple(int(v * 0.85) for v in RED) + (255,))
    return im


def drape(rng, color, emblem_im=None):
    """A stripe block (16x16): vertical folds; the emblem block carries the same face."""
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            fold = 0.9 + 0.12 * abs(((x + 2) % 6) - 3) / 3
            f = fold * (1 + rng.uniform(-0.015, 0.015))
            im.putpixel((x, y), tuple(max(0, min(255, int(v * f))) for v in color) + (255,))
    if emblem_im is not None:
        im.alpha_composite(emblem_im, (0, 0))
    return im


def save(im, rel):
    path = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)
    print(path)


if __name__ == '__main__':
    save(flag(), 'textures/entity/potato_flag.png')
    # a big preview for checking the art
    flag().resize((W * 6, H * 6), Image.NEAREST).save(os.path.join(ROOT, 'tools/blockbench/potato_flag_preview.png'))
