#!/usr/bin/env python3
"""Generates the sky of Chef Papon's fight: a potato sun and a potato moon (client/PotatoSky swaps them
in for the vanilla ones while a fight is on, day or night: it is a long fight).

Writes, under src/main/resources/assets/papon/textures/environment/:
  potato_sun.png           128x128  a golden potato with Papon's grin, french fries for rays
  potato_moon_phases.png   512x256  the vanilla layout (4x2 cells: full, waning gibbous, last quarter,
                                    waning crescent, new, waxing crescent, first quarter, waxing
                                    gibbous): a pale silver potato asleep, its eyes for craters

The sky draws these additively (black is see-through, dark colours let the sky through), so the faces
are drawn in deeper, dimmer tones of the body rather than in black.
Painted at 4x and filtered down.
"""
import math
import os

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'src/main/resources/assets/papon/textures/environment')
S = 4


def potato_outline(cx, cy, rx, ry, n=90, seed=0.6):
    pts = []
    for k in range(n):
        t = k / n * math.pi * 2
        lump = 1 + 0.04 * math.sin(3 * t + seed) + 0.025 * math.sin(5 * t + 2.0 + seed)
        pts.append((cx + rx * lump * math.cos(t), cy + ry * lump * math.sin(t)))
    return pts


def sun():
    n = 128 * S
    im = Image.new('RGB', (n, n), (0, 0, 0))
    c = n / 2
    u = n / 128
    # the glow: a soft warm halo
    glow = Image.new('RGB', (n, n), (0, 0, 0))
    gd = ImageDraw.Draw(glow)
    for r in range(60, 20, -2):
        k = (60 - r) / 40
        gd.ellipse([c - r * u, c - r * u, c + r * u, c + r * u], fill=(int(90 * k), int(60 * k), int(14 * k)))
    im = Image.blend(im, glow.filter(ImageFilter.GaussianBlur(6 * u)), 1.0)
    d = ImageDraw.Draw(im)
    # fries for rays, round the potato
    for i in range(14):
        a = i / 14 * math.pi * 2 + 0.11
        L = (17 if i % 2 else 13) * u
        r0 = 25 * u
        w = 2.6 * u
        bx, by = c + math.cos(a) * r0, c + math.sin(a) * r0 * 1.08
        tx, ty = c + math.cos(a) * (r0 + L), c + math.sin(a) * (r0 + L) * 1.08
        nx, ny = -math.sin(a) * w, math.cos(a) * w
        d.polygon([(bx - nx, by - ny), (bx + nx, by + ny), (tx + nx, ty + ny), (tx - nx, ty - ny)], fill=(250, 196, 70))
        d.line([(bx, by), (tx, ty)], fill=(255, 236, 150), width=int(1.2 * u))
        d.ellipse([tx - w, ty - w, tx + w, ty + w], fill=(255, 224, 120))
    # the potato
    pts = potato_outline(c, c + 1 * u, 23 * u, 26 * u)
    d.polygon(pts, fill=(255, 214, 100))
    hi = Image.new('L', (n, n), 0)
    ImageDraw.Draw(hi).ellipse([c - 16 * u, c - 20 * u, c + 4 * u, c - 2 * u], fill=110)
    hi = hi.filter(ImageFilter.GaussianBlur(4 * u))
    mask = Image.new('L', (n, n), 0)
    ImageDraw.Draw(mask).polygon(pts, fill=255)
    bright = Image.new('RGB', (n, n), (255, 246, 196))
    im.paste(bright, (0, 0), Image.composite(hi, Image.new('L', (n, n), 0), mask))
    d = ImageDraw.Draw(im)
    for ex, ey in ((-14, -9), (13, 9), (-9, 14), (6, -17)):              # potato eyes in the skin
        d.ellipse([c + (ex - 1.4) * u, c + (ey - 1) * u, c + (ex + 1.4) * u, c + (ey + 1) * u], fill=(216, 150, 52))
    face = (176, 92, 22)
    for side in (-1, 1):                                                  # brows, eyes, cheeks
        bx = c + side * 8.5 * u
        d.arc([bx - 5 * u, c - 14 * u, bx + 5 * u, c - 7 * u], 200, 340, fill=face, width=int(2.2 * u))
        d.ellipse([bx - 2.6 * u, c - 6.5 * u, bx + 2.6 * u, c + 0.5 * u], fill=face)
        d.ellipse([bx - 1.6 * u, c - 5.5 * u, bx - 0.2 * u, c - 4.1 * u], fill=(255, 250, 220))
        d.ellipse([c + side * 15 * u - 3.5 * u, c + 3 * u, c + side * 15 * u + 3.5 * u, c + 7 * u], fill=(255, 170, 110))
    d.chord([c - 12 * u, c - 6 * u, c + 12 * u, c + 15 * u], 15, 165, fill=face)   # the grin
    d.chord([c - 6 * u, c + 7 * u, c + 6 * u, c + 14 * u], 25, 155, fill=(240, 120, 80))
    return im.resize((128, 128), Image.LANCZOS)


def moon_cell(phase):
    """One phase (0 full .. 4 new .. 7 waxing gibbous), 128x128 painted at 4x."""
    n = 128 * S
    c = n / 2
    u = n / 128
    body = Image.new('RGB', (n, n), (0, 0, 0))
    d = ImageDraw.Draw(body)
    rx, ry = 34 * u, 40 * u
    pts = potato_outline(c, c, rx, ry, seed=2.2)
    d.polygon(pts, fill=(214, 222, 240))
    hi = Image.new('L', (n, n), 0)
    ImageDraw.Draw(hi).ellipse([c - 24 * u, c - 30 * u, c + 6 * u, c - 4 * u], fill=90)
    hi = hi.filter(ImageFilter.GaussianBlur(5 * u))
    mask = Image.new('L', (n, n), 0)
    ImageDraw.Draw(mask).polygon(pts, fill=255)
    body.paste(Image.new('RGB', (n, n), (246, 250, 255)), (0, 0), Image.composite(hi, Image.new('L', (n, n), 0), mask))
    d = ImageDraw.Draw(body)
    crater = (150, 158, 184)
    for ex, ey, r in ((-20, -18, 4.2), (18, 14, 5.0), (-14, 22, 3.4), (10, -26, 3.0), (24, -6, 2.6), (-26, 6, 2.4)):
        d.ellipse([c + (ex - r) * u, c + (ey - r * 0.8) * u, c + (ex + r) * u, c + (ey + r * 0.8) * u], fill=crater)
        d.arc([c + (ex - r) * u, c + (ey - r * 0.8) * u, c + (ex + r) * u, c + (ey + r * 0.8) * u], 200, 360,
              fill=(236, 240, 252), width=int(0.9 * u))
    face = (112, 120, 150)
    for side in (-1, 1):                                                   # asleep: closed eyes, a soft smile
        bx = c + side * 11 * u
        d.arc([bx - 5.5 * u, c - 9 * u, bx + 5.5 * u, c - 1 * u], 20, 160, fill=face, width=int(2.4 * u))
        d.ellipse([c + side * 19 * u - 4 * u, c + 4 * u, c + side * 19 * u + 4 * u, c + 8 * u], fill=(200, 176, 214))
    d.arc([c - 8 * u, c + 2 * u, c + 8 * u, c + 14 * u], 30, 150, fill=face, width=int(2.2 * u))
    # the phase: lit where the sunlight falls, a faint earthshine on the rest
    lit = Image.new('L', (n, n), 0)
    lp = lit.load()
    mp = mask.load()
    if phase <= 4:
        thr, waxing = math.cos(phase * math.pi / 4), False
    else:
        thr, waxing = -math.cos((8 - phase) * math.pi / 4), True
    for y in range(n):
        xs = [x for x in range(0, n, 2) if mp[x, y]]
        if not xs:
            continue
        x0, x1 = xs[0], xs[-1]
        mid, half = (x0 + x1) / 2, max(1, (x1 - x0) / 2)
        for x in range(x0, x1 + 1):
            nx = (x - mid) / half
            on = nx > thr if waxing else nx < thr
            lp[x, y] = 255 if on else 0
    lit = lit.filter(ImageFilter.GaussianBlur(2 * u))
    dark = Image.eval(body, lambda v: int(v * 0.07))
    out = Image.composite(body, dark, lit)
    out = Image.composite(out, Image.new('RGB', (n, n), (0, 0, 0)), mask)
    return out.resize((128, 128), Image.LANCZOS)


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    sun().save(os.path.join(OUT, 'potato_sun.png'))
    sheet = Image.new('RGB', (512, 256), (0, 0, 0))
    for p in range(8):
        sheet.paste(moon_cell(p), ((p % 4) * 128, (p // 4) * 128))
    sheet.save(os.path.join(OUT, 'potato_moon_phases.png'))
    print(OUT)
