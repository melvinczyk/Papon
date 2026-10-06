#!/usr/bin/env python3
"""The boss bars of phases two, three and four (client/BossBarRenderer; phase one keeps gen_ui.py's taco shell).

(2026-10-06, the user: "each phase needs its own distinct, creative, tasteful health-bar artwork... not the same bar recoloured
four times"; two: still the charismatic potato boss; three: the potato god, imposing, divine, ancient; four: the Final Guardian,
Dia de los Muertos, sugar skulls, the most serious bar of the fight, never tacky.)

Each sheet is 512x256 texels drawn at two texels to a GUI pixel (BossBarRenderer draws it at half scale), painted at four times
that and filtered down. GUI-pixel layout of every sheet:
  frame       0,0    256x40   the bar's body; its channel (where the fill goes) at 40,19 204x10, dark
  fill        0,40   256x10   the fill, tiling every 32 GUI px (scrolled sideways)
  medallion   0,52   48x48    on the bar's left end, centred on (20, 24) of the frame
  overlay     48,52  48x48    drawn over the medallion by code: three, the sun's rays (turned slowly); four, a soft glow
  sprites     96,52  16x16 each: two, a sparkle; four, a candle flame (two frames) and a marigold petal

  bar_p2  El Papa Horneado: a baked potato skin bezel, papel picado strung over it in fiesta colours, a salt shaker at
          the end (the shaker that baked him); melted butter for the fill, salt glinting in it; his baked face in a crown
          of fries on the medallion
  bar_p3  Para Siempre: a blade of polished gold with a step-fret engraved along it and a crimson enamel channel, golden
          rays rising off it toward a sun disc, a feathered tip; divine fire for the fill, a white-hot core; the medallion
          a sun stone round the golden god
  bar_p4  The Final Guardian: obsidian and bone, stepped corners, a xicalcoliuhqui fret carved in bone along its foot,
          cempasuchil on its brow, a candle at its end; the fill marigold fire over magenta; the medallion his sugar skull
          (the user's, tools/src/sugar_skull.webp) on a ring of marigold petals

  python tools/gen_boss_bars.py
"""
import math
import os

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'src/main/resources/assets/papon/textures/gui')
SS = 8          # painted at 8 px to a GUI px, saved at 2
W, H = 256, 128

rng = np.random.default_rng(2026)


class Canvas:
    def __init__(self):
        self.h, self.w = H * SS, W * SS
        self.rgb = np.zeros((self.h, self.w, 3))
        self.a = np.zeros((self.h, self.w))
        ys, xs = np.mgrid[0:self.h, 0:self.w]
        self.x = (xs + 0.5) / SS            # GUI px
        self.y = (ys + 0.5) / SS

    def paint(self, mask, colour, alpha=1.0):
        """Over-composite colour (an rgb array or tuple) where mask (0..1) says."""
        m = np.clip(mask, 0, 1) * alpha
        c = np.asarray(colour, float)
        if c.ndim == 1:
            c = c[None, None, :] / 255.0
        else:
            c = c / 255.0
        self.rgb = self.rgb * (1 - m[..., None]) + c * m[..., None] * np.ones_like(self.rgb)
        self.a = self.a + m * (1 - self.a)

    def cut(self, mask):
        m = np.clip(mask, 0, 1)
        self.a *= 1 - m

    def save(self, name):
        img = np.dstack([self.rgb * 255, self.a * 255]).clip(0, 255).astype(np.uint8)
        im = Image.fromarray(img, 'RGBA')
        # premultiply for a clean filter down, then back
        arr = np.asarray(im, float)
        arr[..., :3] *= arr[..., 3:4] / 255
        small = Image.fromarray(arr.clip(0, 255).astype(np.uint8), 'RGBA').resize((W * 2, H * 2), Image.LANCZOS)
        s = np.asarray(small, float)
        a = s[..., 3:4]
        s[..., :3] = np.where(a > 0, s[..., :3] * 255 / np.maximum(a, 1), 0)
        out = Image.fromarray(s.clip(0, 255).astype(np.uint8), 'RGBA')
        path = os.path.join(OUT, name)
        out.save(path)
        print(path)


def aa(d, soft=1.0):
    """Coverage from a signed distance (GUI px, negative inside)."""
    return np.clip(0.5 - d * SS / (2 * soft), 0, 1)


def rrect(cv, x0, y0, x1, y1, r):
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    hx, hy = (x1 - x0) / 2 - r, (y1 - y0) / 2 - r
    qx, qy = np.abs(cv.x - cx) - hx, np.abs(cv.y - cy) - hy
    return np.hypot(np.maximum(qx, 0), np.maximum(qy, 0)) + np.minimum(np.maximum(qx, qy), 0) - r


def circle(cv, cx, cy, r):
    return np.hypot(cv.x - cx, cv.y - cy) - r


def ellipse(cv, cx, cy, rx, ry):
    return (np.hypot((cv.x - cx) / rx, (cv.y - cy) / ry) - 1) * min(rx, ry)


def poly(cv, pts):
    """Signed distance to a polygon (GUI px)."""
    x, y = cv.x, cv.y
    d = np.full(x.shape, np.inf)
    s = np.ones(x.shape)
    n = len(pts)
    for i in range(n):
        ax, ay = pts[i]
        bx, by = pts[i - 1]
        ex, ey = bx - ax, by - ay
        wx, wy = x - ax, y - ay
        t = np.clip((wx * ex + wy * ey) / (ex * ex + ey * ey), 0, 1)
        d = np.minimum(d, np.hypot(wx - ex * t, wy - ey * t))
        c1 = (y >= ay)
        c2 = (y < by)
        c3 = (ex * wy > ey * wx)
        flip = (c1 & c2 & c3) | (~c1 & ~c2 & ~c3)
        s = np.where(flip, -s, s)
    return s * d


def box(cv, x0, y0, x1, y1):
    return rrect(cv, x0, y0, x1, y1, 0.001)


def lerp(a, b, k):
    a, b = np.asarray(a, float), np.asarray(b, float)
    k = np.asarray(k, float)[..., None]
    return a + (b - a) * k


def vgrad(cv, y0, y1, stops):
    """A vertical gradient through colour stops [(k, rgb)] between y0 and y1."""
    k = np.clip((cv.y - y0) / (y1 - y0), 0, 1)
    out = np.zeros(cv.y.shape + (3,))
    for (k0, c0), (k1, c1) in zip(stops, stops[1:]):
        m = (k >= k0) & (k <= k1)
        t = (k - k0) / max(1e-6, k1 - k0)
        out[m] = lerp(c0, c1, t)[m]
    out[k < stops[0][0]] = stops[0][1]
    out[k > stops[-1][0]] = stops[-1][1]
    return out


def noise(cv, scale, seed):
    """Smooth value noise (0..1) at a GUI-px scale."""
    r = np.random.default_rng(seed)
    gx, gy = int(W / scale) + 3, int(H / scale) + 3
    g = r.random((gy, gx))
    fx, fy = cv.x / scale, cv.y / scale
    x0, y0 = np.floor(fx).astype(int), np.floor(fy).astype(int)
    tx, ty = fx - x0, fy - y0
    tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
    a = g[y0, x0] * (1 - tx) + g[y0, x0 + 1] * tx
    b = g[y0 + 1, x0] * (1 - tx) + g[y0 + 1, x0 + 1] * tx
    return a * (1 - ty) + b * ty


def region(cv, x0, y0, x1, y1):
    return (cv.x >= x0) & (cv.x < x1) & (cv.y >= y0) & (cv.y < y1)


CH = (40, 19, 244, 29)       # the channel


def channel(cv, base, line):
    d = box(cv, *CH)
    cv.paint(aa(d), base)
    # an inner shadow along its top, a fine line round it
    k = np.clip((cv.y - CH[1]) / 2.5, 0, 1)
    cv.paint(aa(d) * (1 - k) * 0.6, (0, 0, 0))
    ring = np.abs(d + 0.0) - 0.35
    cv.paint(aa(ring) * 0.9, line)


# ====================================================================== phase two: El Papa Horneado
def p2():
    cv = Canvas()
    # the bezel: baked potato skin, mottled, the odd eye, a crust rim, light off its top
    bez = rrect(cv, 34, 14, 250, 34, 6)
    n1, n2 = noise(cv, 3.0, 1), noise(cv, 1.1, 2)
    skin = vgrad(cv, 14, 34, [(0, (232, 176, 92)), (0.35, (204, 140, 64)), (1, (150, 92, 40))])
    skin = skin * (0.86 + 0.22 * n1[..., None]) * (0.95 + 0.1 * n2[..., None])
    cv.paint(aa(bez), skin)
    for ex, ey in [(52, 31), (96, 16.5), (131, 31.5), (178, 16.2), (219, 31), (240, 17)]:
        cv.paint(aa(ellipse(cv, ex, ey, 1.4, 0.8)), (110, 66, 30), 0.85)
        cv.paint(aa(ellipse(cv, ex - 0.3, ey - 0.3, 0.6, 0.35)), (240, 196, 120), 0.6)
    cv.paint(aa(np.abs(bez + 0.5) - 0.55), (86, 46, 20))
    cv.paint(aa(np.abs(rrect(cv, 35.2, 15.2, 248.8, 32.8, 5) + 0.3) - 0.3) * (cv.y < 19), (255, 226, 150), 0.7)
    # coarse salt on the skin
    for i in range(70):
        sx, sy = 36 + rng.random() * 212, 15 + rng.random() * 18
        if CH[1] - 0.5 < sy < CH[3] + 0.5 and CH[0] - 0.5 < sx < CH[2] + 0.5:
            continue
        cv.paint(aa(circle(cv, sx, sy, 0.32)), (255, 252, 240), 0.9)
    channel(cv, (38, 20, 10), (92, 52, 22))
    # papel picado strung over the bar: a string sagging between pegs, cut-paper flags with punched patterns
    cols = [(34, 158, 78), (244, 242, 232), (214, 40, 48), (238, 92, 164), (250, 156, 40), (60, 170, 220)]
    xs = np.linspace(44, 236, 13)
    for i, fx in enumerate(xs):
        sag = 1.6 * math.sin(math.pi * ((fx - 40) % 64) / 64)
        top = 3.6 + sag
        fw, fh = 13.0, 10.5
        x0, x1 = fx - fw / 2, fx + fw / 2
        base = box(cv, x0, top, x1, top + fh)
        # the scalloped foot
        for k in range(5):
            base = np.maximum(base, -circle(cv, x0 + fw * (k + 0.5) / 5, top + fh + 0.35, fw / 10.5))
        col = cols[i % len(cols)]
        shade = vgrad(cv, top, top + fh, [(0, col), (1, tuple(int(c * 0.82) for c in col))])
        cv.paint(aa(base), shade)
        # punched: a little flower in the middle, diamonds either side, a row of holes along the top
        cx, cy = fx, top + fh * 0.55
        for a in range(6):
            ang = a * math.pi / 3
            cv.cut(aa(circle(cv, cx + math.cos(ang) * 1.35, cy + math.sin(ang) * 1.35, 0.62)))
        cv.cut(aa(circle(cv, cx, cy, 0.55)))
        for s in (-1, 1):
            dx = cx + s * 4.2
            cv.cut(aa(poly(cv, [(dx, cy - 1.4), (dx + 0.9, cy), (dx, cy + 1.4), (dx - 0.9, cy)])))
        for k in range(4):
            cv.cut(aa(circle(cv, x0 + 2 + k * 3, top + 1.6, 0.4)))
    # the string over them
    sx = np.linspace(38, 246, 400)
    for i in range(len(sx) - 1):
        pass
    sag = 1.6 * np.sin(np.pi * ((cv.x - 40) % 64) / 64)
    string = np.abs(cv.y - (3.4 + sag)) - 0.28
    cv.paint(aa(string) * region(cv, 37, 0, 247, 12), (240, 230, 210))
    # the salt shaker at the end (the one that baked him): glass, salt, a steel cap with holes, tilted
    sh = poly(cv, [(243, 13), (253, 13), (254.5, 33), (241.5, 33)])
    cv.paint(aa(sh), (214, 226, 232), 0.55)
    salt = poly(cv, [(242.6, 21), (253.6, 21), (254.3, 32.4), (242.0, 32.4)])
    cv.paint(aa(salt) * aa(sh), (250, 250, 246), 0.95)
    for i in range(30):
        cv.paint(aa(circle(cv, 243 + rng.random() * 10.5, 21.5 + rng.random() * 10.5, 0.25)), (200, 206, 214), 0.8)
    cv.paint(aa(np.abs(sh) - 0.3), (120, 132, 142))
    cv.paint(aa(box(cv, 244.5, 14, 245.5, 31.5)), (255, 255, 255), 0.55)
    cap = rrect(cv, 242, 8.5, 254, 13.5, 1.5)
    cv.paint(aa(cap), vgrad(cv, 8.5, 13.5, [(0, (236, 240, 244)), (0.5, (170, 176, 186)), (1, (110, 116, 126))]))
    for hx in (245, 248, 251):
        cv.paint(aa(circle(cv, hx, 10, 0.45)), (50, 54, 60))
    # ---- the fill: melted butter, a sheen along its top, melt streaks leaning back, salt glinting (tiles every 32)
    fy0 = 40
    reg = region(cv, 0, fy0, 256, fy0 + 10)
    k = (cv.y - fy0) / 10
    butter = vgrad(cv, fy0, fy0 + 10, [(0, (255, 242, 170)), (0.18, (255, 214, 92)), (0.6, (244, 168, 40)), (1, (196, 108, 22))])
    streak = 0.5 + 0.5 * np.sin((cv.x + (cv.y - fy0) * 1.4) * 2 * math.pi / 8)
    butter = butter * (0.92 + 0.12 * streak[..., None])
    cv.paint(reg, butter)
    cv.paint(reg & (k < 0.12), (255, 252, 226), 0.8)
    for i in range(10):
        sx, sy = (i * 3.2 + 1.1) % 32, fy0 + 2.5 + (i * 2.3) % 6
        for rep in range(8):
            cv.paint(aa(circle(cv, sx + rep * 32, sy, 0.34)) * reg, (255, 255, 255), 0.95)
    # ---- the medallion: his baked face in a crown of fries, on a fiesta sunburst, ringed with crust
    mx, my = 24, 76
    disc = circle(cv, mx, my, 21)
    ang = np.arctan2(cv.y - my, cv.x - mx)
    burst = (np.sin(ang * 12) > 0)
    back = np.where(burst[..., None], np.array([238, 92, 52]) / 1.0, np.array([250, 140, 50]) / 1.0)
    cv.paint(aa(disc), back)
    cv.paint(aa(disc) * np.clip(1 - np.hypot(cv.x - mx, cv.y - my) / 21, 0, 1), (255, 220, 120), 0.6)
    # fries, fanned up behind his head
    for i in range(7):
        a = math.radians(-90 + (i - 3) * 17)
        x0, y0 = mx + math.cos(a) * 6, my - 2 + math.sin(a) * 6
        x1, y1 = mx + math.cos(a) * 19.5, my - 2 + math.sin(a) * 19.5
        dx, dy = x1 - x0, y1 - y0
        L = math.hypot(dx, dy)
        nx, ny = -dy / L * 1.5, dx / L * 1.5
        f = poly(cv, [(x0 + nx, y0 + ny), (x1 + nx, y1 + ny), (x1 - nx, y1 - ny), (x0 - nx, y0 - ny)])
        cv.paint(aa(f), (255, 236, 140))
        cv.paint(aa(np.abs(f) - 0.4), (120, 62, 14))
        cv.paint(aa(circle(cv, x1, y1, 1.5)) * aa(f + 0.2), (226, 150, 50))
    # the potato: baked gold, shaded, a shine on his bald head
    head = ellipse(cv, mx, my + 2.5, 11.5, 13.5)
    hs = vgrad(cv, my - 11, my + 16, [(0, (255, 224, 140)), (0.5, (236, 176, 76)), (1, (176, 108, 40))])
    hs = hs * (0.9 + 0.12 * noise(cv, 1.4, 7)[..., None])
    cv.paint(aa(head), hs)
    cv.paint(aa(np.abs(head) - 0.45), (110, 62, 24))
    cv.paint(aa(ellipse(cv, mx - 3.5, my - 6.5, 3.2, 1.8)), (255, 246, 210), 0.75)
    # the face: confident brows, eyes with a glint, a showman's grin
    for s in (-1, 1):
        ex = mx + s * 4.2
        cv.paint(aa(ellipse(cv, ex, my + 2, 2.2, 2.6)), (255, 255, 250))
        cv.paint(aa(circle(cv, ex + 0.3 * s, my + 2.4, 1.3)), (34, 22, 16))
        cv.paint(aa(circle(cv, ex + 0.7 * s - 0.4, my + 1.8, 0.45)), (255, 255, 255))
        brow = poly(cv, [(ex - 3 * s, my - 2.4 + (0.6 if s > 0 else 0)), (ex + 2.8 * s, my - 1.2), (ex + 2.8 * s, my - 0.1), (ex - 3 * s, my - 1.2)])
        cv.paint(aa(brow), (70, 40, 20))
    smile = np.abs(np.hypot(cv.x - mx, (cv.y - (my + 6.2)) * 1.4) - 4.6) - 0.6
    cv.paint(aa(smile) * (cv.y > my + 8.6), (110, 40, 26))
    cv.paint(aa(ellipse(cv, mx, my + 10.4, 3.2, 1.1)) * (cv.y > my + 10.1), (250, 248, 240), 0.9)
    # salt on him
    for i in range(20):
        a, r = rng.random() * 6.28, rng.random() * 11
        cv.paint(aa(circle(cv, mx + math.cos(a) * r, my + 2 + math.sin(a) * r * 1.1, 0.28)), (255, 255, 255), 0.9)
    # the crust ring round it all: braided
    ring = np.abs(disc) - 1.6
    braid = 0.5 + 0.5 * np.sin(ang * 18 + np.hypot(cv.x - mx, cv.y - my) * 2)
    rc = lerp((140, 80, 30), (236, 176, 90), braid)
    cv.paint(aa(ring), rc)
    cv.paint(aa(np.abs(disc - 1.6) - 0.3), (70, 36, 14))
    cv.paint(aa(np.abs(disc + 1.6) - 0.3), (70, 36, 14))
    # sprite: a sparkle (four points)
    sxc, syc = 104, 60
    star = np.minimum(poly(cv, [(sxc, syc - 7), (sxc + 1.2, syc), (sxc, syc + 7), (sxc - 1.2, syc)]),
                      poly(cv, [(sxc - 7, syc), (sxc, syc - 1.2), (sxc + 7, syc), (sxc, syc + 1.2)]))
    cv.paint(aa(star), (255, 255, 255))
    cv.save('bar_p2.png')


# ====================================================================== phase three: Para Siempre
GOLD_HI, GOLD, GOLD_DK = (255, 240, 170), (234, 182, 64), (150, 92, 22)


def gold(cv, y0, y1):
    return vgrad(cv, y0, y1, [(0, GOLD_HI), (0.3, (250, 214, 110)), (0.55, GOLD), (0.8, (198, 132, 36)), (1, GOLD_DK)])


def p3():
    cv = Canvas()
    # rays rising off the blade toward the sun, tallest by the medallion
    for i in range(30):
        x = 46 + i * 6.6
        h = 7.0 * (1 - i / 34) ** 1.6 + 1.0
        ray = poly(cv, [(x - 0.9, 15), (x, 15 - h), (x + 0.9, 15)])
        cv.paint(aa(ray), (255, 214, 110), 0.85 * (1 - i / 40))
    # the blade: a long hexagon of polished gold, pointed at its end, a crimson enamel channel set in it
    blade = poly(cv, [(30, 14), (238, 14), (252, 24), (238, 34), (30, 34)])
    cv.paint(aa(blade), gold(cv, 14, 34))
    cv.paint(aa(np.abs(blade) - 0.35), (110, 60, 10))
    inner = poly(cv, [(33, 16.2), (237, 16.2), (248.6, 24), (237, 31.8), (33, 31.8)])
    cv.paint(aa(np.abs(inner) - 0.22), (255, 246, 200), 0.8)
    # a step-fret engraved along it, above and below the channel
    for band_y in (15.6, 30.2):
        for i in range(34):
            x = 40 + i * 6
            if x > 236:
                break
            fret = np.minimum.reduce([box(cv, x, band_y + 1.6, x + 4.2, band_y + 2.3), box(cv, x + 3.5, band_y, x + 4.2, band_y + 2.3),
                                      box(cv, x + 1.6, band_y, x + 4.2, band_y + 0.7), box(cv, x + 1.6, band_y, x + 2.3, band_y + 1.3)])
            cv.paint(aa(fret), (168, 100, 24), 0.85)
    channel(cv, (40, 4, 14), (255, 222, 140))
    # the feathered tip: three golden feathers swept up off the point
    for i, (ang, ln) in enumerate([(-38, 15), (-22, 13), (-6, 10)]):
        a = math.radians(ang)
        bx, by = 242 - i * 2.5, 22
        tx, ty = bx + math.cos(a) * ln, by + math.sin(a) * ln
        nx, ny = -math.sin(a) * 2.2, math.cos(a) * 2.2
        f = poly(cv, [(bx, by), (bx + (tx - bx) * 0.5 + nx, by + (ty - by) * 0.5 + ny), (tx, ty), (bx + (tx - bx) * 0.5 - nx * 0.4, by + (ty - by) * 0.5 - ny * 0.4)])
        cv.paint(aa(f) * region(cv, 0, 0, 256, 40), gold(cv, ty, by + 2))
        cv.paint(aa(np.abs(f) - 0.25) * region(cv, 0, 0, 256, 40), (130, 74, 14))
    # ---- the fill: divine fire, crimson to scarlet, tongues of gold licking along its top, a white-hot core (tiles every 32)
    fy0 = 40
    reg = region(cv, 0, fy0, 256, fy0 + 10)
    k = (cv.y - fy0) / 10
    base = vgrad(cv, fy0, fy0 + 10, [(0, (255, 120, 60)), (0.3, (236, 40, 46)), (0.7, (170, 12, 36)), (1, (96, 4, 24))])
    cv.paint(reg, base)
    tongue = 0.5 + 0.5 * np.sin(cv.x * 2 * math.pi / 8 + np.sin(cv.x * 2 * math.pi / 32) * 1.5)
    lick = (k < 0.18 + 0.32 * tongue)
    cv.paint(reg & lick, lerp((255, 196, 70), (255, 120, 40), np.clip(k / 0.5, 0, 1)), 0.85)
    core = np.exp(-((k - 0.42) / 0.07) ** 2)
    cv.paint(reg * core, (255, 248, 220), 0.85)
    # ---- the medallion: a sun stone round the golden god (its rays are the overlay, turned by code)
    mx, my = 24, 76
    d = circle(cv, mx, my, 17)
    cv.paint(aa(d), (150, 14, 36))
    rr = np.hypot(cv.x - mx, cv.y - my)
    ang = np.arctan2(cv.y - my, cv.x - mx)
    cv.paint(aa(d) * np.clip(1 - rr / 17, 0, 1), (255, 90, 70), 0.5)
    # rings of glyphs: little gold squares round the rim, a fret ring inside it
    cv.paint(aa(np.abs(d + 2.5) - 0.35), (255, 220, 130))
    for i in range(20):
        a = i * math.pi / 10
        gx, gy = mx + math.cos(a) * 15.3, my + math.sin(a) * 15.3
        cv.paint(aa(circle(cv, gx, gy, 0.8)), (255, 226, 140))
    # the god: a golden potato, eyes closed, serene, a halo behind his crown
    cv.paint(aa(np.abs(circle(cv, mx, my - 1, 11.8)) - 0.5), (255, 240, 190), 0.9)
    head = ellipse(cv, mx, my + 1.5, 8.6, 10.2)
    cv.paint(aa(head), gold(cv, my - 9, my + 12))
    cv.paint(aa(np.abs(head) - 0.4), (120, 66, 12))
    cv.paint(aa(ellipse(cv, mx - 2.6, my - 4.6, 2.5, 1.4)), (255, 252, 230), 0.8)
    for s in (-1, 1):
        lid = np.abs(np.hypot((cv.x - (mx + s * 3.3)), (cv.y - (my + 0.4)) * 1.6) - 1.9) - 0.32
        cv.paint(aa(lid) * (cv.y > my + 0.4), (110, 50, 10))
    mouth = np.abs(np.hypot(cv.x - mx, (cv.y - (my + 4.4)) * 1.6) - 2.0) - 0.3
    cv.paint(aa(mouth) * (cv.y > my + 5.2), (130, 60, 12))
    # a crown of three points
    crown = poly(cv, [(mx - 5.5, my - 7.5), (mx - 5.5, my - 11.5), (mx - 2.7, my - 9.2), (mx, my - 13), (mx + 2.7, my - 9.2), (mx + 5.5, my - 11.5), (mx + 5.5, my - 7.5)])
    cv.paint(aa(crown), gold(cv, my - 13, my - 7))
    cv.paint(aa(circle(cv, mx, my - 10, 0.9)), (220, 30, 50))
    cv.paint(aa(np.abs(d) - 0.6), (110, 60, 10))
    cv.paint(aa(np.abs(d - 0.9) - 0.4), (255, 230, 150))
    # ---- the overlay: the sun's rays, sixteen, long and short (turned slowly by code)
    ox, oy = 72, 76
    ang = np.arctan2(cv.y - oy, cv.x - ox)
    rr = np.hypot(cv.x - ox, cv.y - oy)
    for i in range(16):
        a = i * math.pi / 8
        ln = 23 if i % 2 == 0 else 20.5
        c, s = math.cos(a), math.sin(a)
        w = 2.6 if i % 2 == 0 else 1.9
        p = [(ox + c * 16.5 - s * w, oy + s * 16.5 + c * w), (ox + c * ln, oy + s * ln), (ox + c * 16.5 + s * w, oy + s * 16.5 - c * w)]
        ray = poly(cv, p)
        cv.paint(aa(ray), gold(cv, oy - 24, oy + 24) * 0 + np.array(GOLD_HI if i % 2 == 0 else GOLD))
        cv.paint(aa(np.abs(ray) - 0.22), (140, 80, 14))
    cv.save('bar_p3.png')


# ====================================================================== phase four: the Final Guardian
BONE, BONE_DK = (238, 226, 200), (170, 152, 124)
OBS, OBS_HI = (20, 16, 28), (74, 52, 104)
MARIGOLD, MARI_DK = (255, 150, 26), (206, 84, 10)


def marigold(cv, x, y, r):
    for layer, (rad, col) in enumerate([(r, MARI_DK), (r * 0.78, MARIGOLD), (r * 0.5, (255, 196, 70))]):
        for i in range(10):
            a = i * math.pi / 5 + layer * 0.3
            cv.paint(aa(circle(cv, x + math.cos(a) * rad * 0.55, y + math.sin(a) * rad * 0.55, rad * 0.5)), col)
    cv.paint(aa(circle(cv, x, y, r * 0.22)), (120, 50, 10))


def p4():
    cv = Canvas()
    # obsidian with stepped corners, a violet sheen across it
    pts = [(34, 16), (36, 16), (36, 14), (248, 14), (248, 16), (250, 16), (250, 32), (248, 32), (248, 34), (36, 34), (36, 32), (34, 32)]
    body = poly(cv, pts)
    sheen = vgrad(cv, 14, 34, [(0, OBS_HI), (0.25, (40, 30, 56)), (0.6, OBS), (1, (10, 8, 14))])
    sheen = sheen * (0.9 + 0.2 * noise(cv, 2.0, 11)[..., None])
    cv.paint(aa(body), sheen)
    cv.paint(aa(np.abs(body) - 0.4), BONE)
    inner = poly(cv, [(36.8, 17.4), (37.8, 17.4), (37.8, 15.6), (246.2, 15.6), (246.2, 17.4), (247.2, 17.4), (247.2, 30.6), (246.2, 30.6),
                      (246.2, 32.4), (37.8, 32.4), (37.8, 30.6), (36.8, 30.6)])
    cv.paint(aa(np.abs(inner) - 0.2), BONE_DK, 0.8)
    # a xicalcoliuhqui carved along its foot, bone in the stone
    for i in range(26):
        x = 44 + i * 7.8
        if x > 240:
            break
        y = 30.0
        hook = np.minimum.reduce([box(cv, x, y + 1.6, x + 5.5, y + 2.4), box(cv, x + 4.7, y, x + 5.5, y + 2.4), box(cv, x + 2.4, y, x + 5.5, y + 0.8),
                                  box(cv, x + 2.4, y, x + 3.2, y + 1.2)])
        cv.paint(aa(hook), BONE, 0.9)
    channel(cv, (16, 8, 22), (120, 90, 140))
    # faint skulls in the dark of the channel (seen as it empties)
    for i in range(12):
        x = 50 + i * 16.5
        cv.paint(aa(ellipse(cv, x, 23.4, 2.4, 2.2)) * aa(box(cv, *CH)), (42, 26, 54))
        for s in (-1, 1):
            cv.paint(aa(circle(cv, x + s * 0.95, 23.6, 0.6)), (16, 8, 22))
    # cempasuchil on its brow
    for x in (88, 142, 196):
        marigold(cv, x, 14.2, 3.4)
    for x in (115, 169):
        marigold(cv, x, 14.6, 2.2)
    # a candle at its end, wax run down it (its flame: a sprite, drawn by code)
    cand = rrect(cv, 248.5, 6.5, 254.5, 22, 0.8)
    cv.paint(aa(cand), vgrad(cv, 6.5, 22, [(0, (255, 250, 236)), (1, (210, 196, 170))]))
    cv.paint(aa(ellipse(cv, 249.2, 9.5, 0.8, 2.2)), (250, 244, 228))
    cv.paint(aa(np.abs(cand) - 0.25), (150, 130, 100))
    cv.paint(aa(box(cv, 251.3, 4.8, 251.8, 6.6)), (40, 30, 26))
    # ---- the fill: marigold fire over magenta, petals drifting in it, a bright lip (tiles every 32)
    fy0 = 40
    reg = region(cv, 0, fy0, 256, fy0 + 10)
    k = (cv.y - fy0) / 10
    base = vgrad(cv, fy0, fy0 + 10, [(0, (255, 226, 130)), (0.15, (255, 168, 40)), (0.55, (238, 96, 30)), (0.85, (200, 30, 110)), (1, (120, 16, 90))])
    cv.paint(reg, base)
    for i in range(6):
        px, py = (i * 5.7 + 2) % 32, fy0 + 3 + (i * 3.1) % 5
        for rep in range(8):
            pe = ellipse(cv, px + rep * 32, py, 1.4, 0.7)
            cv.paint(aa(pe) * reg, (255, 200, 90), 0.75)
            cv.paint(aa(np.abs(pe) - 0.15) * reg, (190, 70, 10), 0.6)
    cv.paint(reg & (k < 0.1), (255, 246, 214), 0.8)
    # ---- the medallion: his sugar skull on obsidian in a ring of marigold petals, bone round it
    mx, my = 24, 76
    for i in range(18):
        a = i * math.pi / 9
        pe = ellipse(cv, mx + math.cos(a) * 19, my + math.sin(a) * 19, 3.4, 3.4)
        cv.paint(aa(pe), MARI_DK if i % 2 else MARIGOLD)
    plate = circle(cv, mx, my, 17.5)
    cv.paint(aa(plate), (18, 12, 26))
    cv.paint(aa(np.abs(plate) - 0.7), BONE)
    skull = Image.open(os.path.join(ROOT, 'tools/src/sugar_skull.webp')).convert('RGBA')
    sh = 27.0
    sw = sh * skull.size[0] / skull.size[1]
    px = int(round(sw * SS)), int(round(sh * SS))
    sk = np.asarray(skull.resize(px, Image.LANCZOS), float) / 255
    x0, y0 = int(round((mx - sw / 2) * SS)), int(round((my - sh / 2 + 0.4) * SS))
    sub = (slice(y0, y0 + px[1]), slice(x0, x0 + px[0]))
    clip = aa(plate + 0.8)[sub]
    a = sk[..., 3] * clip
    cv.rgb[sub] = cv.rgb[sub] * (1 - a[..., None]) + sk[..., :3] * a[..., None]
    cv.a[sub] = cv.a[sub] + a * (1 - cv.a[sub])
    # ---- the overlay: a soft glow of the dead round it (code breathes it)
    ox, oy = 72, 76
    rr = np.hypot(cv.x - ox, cv.y - oy)
    glow = np.clip(1 - np.abs(rr - 18) / 6, 0, 1) ** 2 * region(cv, 48, 52, 96, 100)
    cv.paint(glow, (150, 255, 210), 0.9)
    # sprites: the candle's flame, two frames, and a marigold petal
    for f, (fx, lean) in enumerate([(104, 0.0), (120, 0.8)]):
        fl = poly(cv, [(fx + lean, 53), (fx + 2.6, 61), (fx, 66), (fx - 2.6, 61)])
        cv.paint(aa(fl), vgrad(cv, 53, 66, [(0, (255, 200, 80)), (0.6, (255, 150, 40)), (1, (255, 250, 220))]))
        cv.paint(aa(ellipse(cv, fx, 62.5, 1.0, 2.0)), (255, 255, 240))
    pe = ellipse(cv, 136, 60, 4.5, 2.4)
    cv.paint(aa(pe), vgrad(cv, 57, 63, [(0, (255, 196, 70)), (1, MARI_DK)]))
    cv.paint(aa(np.abs(box(cv, 132, 59.8, 140, 60.2))), (190, 80, 10), 0.6)
    cv.save('bar_p4.png')


if __name__ == '__main__':
    p2()
    p3()
    p4()
