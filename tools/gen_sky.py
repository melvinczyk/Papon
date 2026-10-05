#!/usr/bin/env python3
"""Generates the sky of Chef Papon's fight (client/PotatoSky swaps them in for the vanilla ones while a fight is on, day or
night: it is a long fight; client/MuertosSky draws the moon over his coliseum in phase four).

(2026-10-04, redone: the user found the cartoon potato sun and moon "stupid", and gave a sugar skull for the moon.)

Writes, under src/main/resources/assets/papon/textures/environment/:
  potato_sun.png           256x256  a sun stone in gold (Tonatiuh's, the Aztec sun): eight great rays and the small ones
                                    between, a ring of day signs, the four movements (Ollin) round a calm potato sun face
                                    with jade ear spools, all glowing; black is see-through (the sky adds it)
  potato_moon_phases.png   512x256  the vanilla layout (4x2 cells: full, waning gibbous, last quarter, waning crescent, new,
                                    waxing crescent, first quarter, waxing gibbous): the user's sugar skull
                                    (tools/src/sugar_skull.webp) as a moon, silver and pale, its colours only a hint; the
                                    unlit side still faintly there (earthshine), so the skull always reads
  muertos_moon.png         256x256  (with alpha) the Day of the Dead moon in phase four: the same skull in full colour,
                                    brighter and more saturated, moonlit bone, its eye sockets and nose burning jade, a
                                    glow round it

The sky draws the sun and the phases additively (black is see-through, dark colours let the sky through), so their lines
are drawn in deeper golds and greys rather than in black. Painted at 4x (the sun) and filtered down.
"""
import math
import os

from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageOps

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'src/main/resources/assets/papon/textures/environment')
SKULL = os.path.join(ROOT, 'tools/src/sugar_skull.webp')
S = 4


# ------------------------------------------------------------------ the sun stone

def sun():
    n = 256 * S
    c = n / 2
    u = n / 256
    im = Image.new('RGB', (n, n), (0, 0, 0))
    # the glow: a wide warm halo
    glow = Image.new('RGB', (n, n), (0, 0, 0))
    gd = ImageDraw.Draw(glow)
    for r in range(126, 40, -3):
        k = (126 - r) / 86
        gd.ellipse([c - r * u, c - r * u, c + r * u, c + r * u], fill=(int(120 * k), int(70 * k), int(12 * k)))
    im = Image.blend(im, glow.filter(ImageFilter.GaussianBlur(10 * u)), 1.0)
    d = ImageDraw.Draw(im)
    gold, deep, pale, hot = (250, 196, 72), (176, 110, 24), (255, 236, 160), (255, 150, 40)
    # the eight great rays (pointed, two-toned) and the small rays between them
    for i in range(16):
        a = i / 16 * math.pi * 2 - math.pi / 2
        big = i % 2 == 0
        r0, r1, w = 74 * u, (118 if big else 100) * u, (13 if big else 8) * u
        bx, by = c + math.cos(a) * r0, c + math.sin(a) * r0
        tx, ty = c + math.cos(a) * r1, c + math.sin(a) * r1
        nx, ny = -math.sin(a) * w, math.cos(a) * w
        d.polygon([(bx - nx, by - ny), (tx, ty), (bx + nx, by + ny)], fill=gold if big else hot)
        d.polygon([(bx, by), (tx, ty), (bx + nx, by + ny)], fill=pale if big else gold)
    # the rings
    def ring(r, width, col):
        d.ellipse([c - r * u, c - r * u, c + r * u, c + r * u], outline=col, width=int(width * u))
    d.ellipse([c - 76 * u, c - 76 * u, c + 76 * u, c + 76 * u], fill=(150, 92, 20))
    ring(76, 3, pale)
    # the ring of the day signs: twenty little glyph boxes
    for i in range(20):
        a = i / 20 * math.pi * 2
        x, y = c + math.cos(a) * 66 * u, c + math.sin(a) * 66 * u
        s = 6.2 * u
        rot = a + math.pi / 2
        pts = []
        for px, py in ((-s, -s * 0.8), (s, -s * 0.8), (s, s * 0.8), (-s, s * 0.8)):
            pts.append((x + px * math.cos(rot) - py * math.sin(rot), y + px * math.sin(rot) + py * math.cos(rot)))
        d.polygon(pts, fill=gold, outline=deep)
        # a mark in each: a dot, a bar or a little spiral, by turns
        if i % 3 == 0:
            d.ellipse([x - 2 * u, y - 2 * u, x + 2 * u, y + 2 * u], fill=deep)
        elif i % 3 == 1:
            d.line([(pts[0][0] * 0.5 + pts[3][0] * 0.5, pts[0][1] * 0.5 + pts[3][1] * 0.5),
                    (pts[1][0] * 0.5 + pts[2][0] * 0.5, pts[1][1] * 0.5 + pts[2][1] * 0.5)], fill=deep, width=int(1.6 * u))
        else:
            d.arc([x - 3 * u, y - 3 * u, x + 3 * u, y + 3 * u], 0, 270, fill=deep, width=int(1.4 * u))
    ring(57, 2.5, pale)
    d.ellipse([c - 55 * u, c - 55 * u, c + 55 * u, c + 55 * u], fill=(190, 124, 30))
    # the four movements (Ollin): four square panels round the face, between the diagonals
    for i in range(4):
        a = i * math.pi / 2 + math.pi / 4
        x, y = c + math.cos(a) * 38 * u, c + math.sin(a) * 38 * u
        s = 11 * u
        d.rectangle([x - s, y - s, x + s, y + s], fill=gold, outline=pale, width=int(1.6 * u))
        d.rectangle([x - s * 0.55, y - s * 0.55, x + s * 0.55, y + s * 0.55], outline=deep, width=int(1.6 * u))
        d.ellipse([x - 2.4 * u, y - 2.4 * u, x + 2.4 * u, y + 2.4 * u], fill=hot)
    # the face: a calm potato sun, eyes closed, jade ear spools, a smile and its tongue of light
    fx, fy = c, c
    pts = []
    for k in range(80):
        t = k / 80 * math.pi * 2
        lump = 1 + 0.035 * math.sin(3 * t + 0.6) + 0.02 * math.sin(5 * t + 2.0)
        pts.append((fx + 25 * u * lump * math.cos(t), fy + 29 * u * lump * math.sin(t)))
    d.polygon(pts, fill=(255, 210, 96), outline=pale)
    hi = Image.new('L', (n, n), 0)
    ImageDraw.Draw(hi).ellipse([fx - 18 * u, fy - 24 * u, fx + 2 * u, fy - 4 * u], fill=120)
    hi = hi.filter(ImageFilter.GaussianBlur(5 * u))
    mask = Image.new('L', (n, n), 0)
    ImageDraw.Draw(mask).polygon(pts, fill=255)
    im.paste(Image.new('RGB', (n, n), (255, 246, 200)), (0, 0), Image.composite(hi, Image.new('L', (n, n), 0), mask))
    d = ImageDraw.Draw(im)
    face = (168, 92, 20)
    for side in (-1, 1):
        ex = fx + side * 9.5 * u
        d.arc([ex - 5.5 * u, fy - 9 * u, ex + 5.5 * u, fy - 1 * u], 20, 160, fill=face, width=int(2.4 * u))       # eyes closed
        d.arc([ex - 6 * u, fy - 17 * u, ex + 6 * u, fy - 10 * u], 200, 340, fill=face, width=int(2 * u))       # brows
        sx = fx + side * 28 * u
        d.ellipse([sx - 5 * u, fy - 3 * u, sx + 5 * u, fy + 7 * u], fill=(60, 200, 150), outline=pale, width=int(1.2 * u))  # jade spools
        d.ellipse([sx - 2 * u, fy, sx + 2 * u, fy + 4 * u], fill=(150, 255, 210))
        d.ellipse([fx + side * 15 * u - 3.5 * u, fy + 4 * u, fx + side * 15 * u + 3.5 * u, fy + 8 * u], fill=(255, 168, 96))
    d.chord([fx - 10 * u, fy + 2 * u, fx + 10 * u, fy + 17 * u], 15, 165, fill=face)
    d.polygon([(fx - 3.5 * u, fy + 11 * u), (fx + 3.5 * u, fy + 11 * u), (fx, fy + 22 * u)], fill=(255, 140, 60))         # the tongue of light
    d.ellipse([fx - 6 * u, fy - 27 * u, fx + 6 * u, fy - 21 * u], fill=hot)                                             # a jewel on the brow
    # the stone's own light
    im = Image.blend(im, im.filter(ImageFilter.GaussianBlur(3 * u)), 0.25)
    return im.resize((256, 256), Image.LANCZOS)


# ------------------------------------------------------------------ the skull moon

def skull_square(size):
    """The user's sugar skull on a transparent square, centred, a margin round it (RGBA)."""
    src = Image.open(SKULL).convert('RGBA')
    bbox = src.getchannel('A').point(lambda v: 255 if v > 8 else 0).getbbox() or (0, 0) + src.size
    sk = src.crop(bbox)
    side = int(max(sk.size) * 1.12)
    sq = Image.new('RGBA', (side, side), (0, 0, 0, 0))
    sq.paste(sk, ((side - sk.width) // 2, (side - sk.height) // 2), sk)
    return sq.resize((size, size), Image.LANCZOS)


def moon_skull(size):
    """The skull as a moon: silver and pale (its colours only a hint), on black, a soft glow round it."""
    sk = skull_square(size * S)
    rgb, a = sk.convert('RGB'), sk.getchannel('A')
    grey = ImageOps.grayscale(rgb).convert('RGB')
    tinted = Image.blend(grey, rgb, 0.22)
    # moonlight: lifted toward a cool silver, the dark lines kept dim grey (additive: never black inside)
    tinted = Image.eval(tinted, lambda v: int(40 + v * 0.86))
    tinted = Image.merge('RGB', [ch.point(lambda v, m=m: int(min(255, v * m))) for ch, m in zip(tinted.split(), (0.92, 0.97, 1.08))])
    out = Image.new('RGB', tinted.size, (0, 0, 0))
    halo = a.filter(ImageFilter.GaussianBlur(14 * S)).point(lambda v: int(v * 0.35))
    out.paste(Image.new('RGB', tinted.size, (150, 170, 210)), (0, 0), halo)
    out.paste(tinted, (0, 0), a)
    return out.resize((size, size), Image.LANCZOS), a.resize((size, size), Image.LANCZOS)


def moon_cell(phase, skull, alpha):
    """One phase (0 full .. 4 new .. 7 waxing gibbous): the lit part as is, the rest by earthshine."""
    n = skull.width
    lit = Image.new('L', (n, n), 0)
    lp = lit.load()
    ap = alpha.load()
    if phase <= 4:
        thr, waxing = math.cos(phase * math.pi / 4), False
    else:
        thr, waxing = -math.cos((8 - phase) * math.pi / 4), True
    for y in range(n):
        xs = [x for x in range(n) if ap[x, y] > 20]
        if not xs:
            continue
        x0, x1 = xs[0], xs[-1]
        mid, half = (x0 + x1) / 2, max(1, (x1 - x0) / 2)
        for x in range(x0, x1 + 1):
            nx = (x - mid) / half
            lp[x, y] = 255 if (nx > thr if waxing else nx < thr) else 0
    lit = lit.filter(ImageFilter.GaussianBlur(3))
    dark = Image.eval(skull, lambda v: int(v * 0.3))
    return Image.composite(skull, dark, lit)


def dead_moon(size=256):
    """Phase four's moon: the skull in full colour, brighter, the bone moonlit, the sockets burning jade, a glow round it."""
    sk = skull_square(size * 2)
    rgb, a = sk.convert('RGB'), sk.getchannel('A')
    rgb = ImageEnhance.Color(rgb).enhance(1.35)
    rgb = ImageEnhance.Brightness(rgb).enhance(1.12)
    rgb = Image.merge('RGB', [ch.point(lambda v, m=m: int(min(255, v * m))) for ch, m in zip(rgb.split(), (0.96, 1.0, 1.08))])
    n = rgb.width
    # the eye sockets and the nose: the darkest big patches inside the skull: burning jade from the inside out
    lum = ImageOps.grayscale(rgb)
    hole = Image.new('L', (n, n), 0)
    hp, lp, ap = hole.load(), lum.load(), a.load()
    for y in range(n):
        for x in range(n):
            if ap[x, y] > 200 and lp[x, y] < 62:
                hp[x, y] = 255
    # only the sockets and the nose (the cheeks' shadows are dark too): ellipses round them, in the picture's own place
    # (its sockets at (178, 300) and (345, 300), the nose at (262, 385), of 520x700; the square adds a margin: skull_square)
    k = n / 732.0
    where = Image.new('L', (n, n), 0)
    wd = ImageDraw.Draw(where)
    for (x, y, rx, ry) in ((284, 316, 74, 76), (451, 316, 74, 76), (368, 401, 42, 58)):
        wd.ellipse([(x - rx) * k, (y - ry) * k, (x + rx) * k, (y + ry) * k], fill=255)
    hole = Image.composite(hole, Image.new('L', (n, n), 0), where)
    hole = hole.filter(ImageFilter.MinFilter(5)).filter(ImageFilter.MaxFilter(9)).filter(ImageFilter.GaussianBlur(6))
    glow = Image.new('RGB', (n, n), (70, 255, 170))
    core = Image.new('RGB', (n, n), (220, 255, 240))
    rgb.paste(glow, (0, 0), hole.point(lambda v: int(v * 0.85)))
    rgb.paste(core, (0, 0), hole.filter(ImageFilter.GaussianBlur(4)).point(lambda v: max(0, int((v - 140) * 2.2))))
    # an aura round the whole skull
    aura = a.filter(ImageFilter.GaussianBlur(18)).point(lambda v: int(v * 0.6))
    out = Image.new('RGBA', (n, n), (90, 255, 190, 0))
    out.putalpha(aura)
    body = rgb.convert('RGBA')
    body.putalpha(a)
    out = Image.alpha_composite(out, body)
    return out.resize((size, size), Image.LANCZOS)


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    sun().save(os.path.join(OUT, 'potato_sun.png'))
    skull, alpha = moon_skull(128)
    sheet = Image.new('RGB', (512, 256), (0, 0, 0))
    for p in range(8):
        sheet.paste(moon_cell(p, skull, alpha), ((p % 4) * 128, (p // 4) * 128))
    sheet.save(os.path.join(OUT, 'potato_moon_phases.png'))
    dead_moon().save(os.path.join(OUT, 'muertos_moon.png'))
    print(OUT)
