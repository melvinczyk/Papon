#!/usr/bin/env python3
"""Makima's materials (2026-10-07, the user: "a major visual-quality overhaul ... the best-looking, most polished ...
recognizable Minecraft implementation of Makima"): her atlas as the model painted it (tools/src/makima_atlas_base.png,
written by gen_makima_mesh.atlas() from the FBX's textures; on a machine without them it is read from there) restyled for the
game's lighting and her anime look, before makima_face paints its patches:

  hair     the model's dark auburn lifted toward her coral red (lighter, a touch pinker, a little less saturated), the
           shading kept; the scalp painted under the hair on the face's texture taken along
  irises   repainted at full resolution: gold, concentric rings (her eyes' signature: darker orange bands, inner ones
           finer), a small dark pupil, a dark limbal rim, the upper third shaded by the lid, two catchlights
  skin     a warm ivory (the model's pink shading read grey in the game's light), a faint warm flush on the cheeks and nose, the lips a touch rosier
  shirt    the lavender cast taken out (white with cool grey shading), kept off pure white with its folds deepened (the
           game lights a front fully: it washed out to a flat white)
  ring     an anime highlight ring round her crown, baked from her geometry (bake_hair: gen_makima_mesh passes it)
  trousers lifted a little and their folds' contrast raised, so black reads as cloth with shape and not one black mass

  python tools/makima_look.py        (gen_makima_mesh runs it; standalone it rewrites the atlas from the base)
"""
import colorsys
import os

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASE = os.path.join(ROOT, 'tools/src/makima_atlas_base.png')
OUT = os.path.join(ROOT, 'src/main/resources/assets/papon/textures/entity/makima_mesh.png')
# gen_makima_mesh.QUADS (x, y, size), kept in step
QUADS = {'skin': (0, 0, 1024), 'shirt': (1024, 0, 1024), 'hair': (0, 1024, 1024), 'trouser': (1024, 1024, 768),
         'eyes': (1792, 1024, 256)}
HAIR_V, HAIR_S, HAIR_H = 1.16, 0.9, -0.006      # value gain (soft knee), saturation gain, hue shift (turns)
# her face on the skin texture (pixels of the 1024 skin quad): cheek centres, nose tip, lips (from the painted face)
CHEEKS = ((255, 400), (450, 400))
NOSE = (345, 368)
LIPS = (356, 431)


def smoothstep(e0, e1, x):
    t = np.clip((np.asarray(x, float) - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def rgb_to_hsv(a):
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    mx, mn = a.max(-1), a.min(-1)
    d = mx - mn
    h = np.zeros_like(mx)
    k = d > 1e-9
    rr = k & (mx == r)
    gg = k & (mx == g) & ~rr
    bb = k & ~rr & ~gg
    h[rr] = ((g - b)[rr] / d[rr]) % 6
    h[gg] = (b - r)[gg] / d[gg] + 2
    h[bb] = (r - g)[bb] / d[bb] + 4
    return np.stack([h / 6, np.where(mx > 1e-9, d / np.maximum(mx, 1e-9), 0), mx], -1)


def hsv_to_rgb(a):
    h, s, v = a[..., 0] % 1 * 6, a[..., 1], a[..., 2]
    i = np.floor(h).astype(int) % 6
    f = h - np.floor(h)
    p, q, t = v * (1 - s), v * (1 - s * f), v * (1 - s * (1 - f))
    out = np.zeros(a.shape)
    for n, (r, g, b) in enumerate(((v, t, p), (q, v, p), (p, v, t), (p, q, v), (t, p, v), (v, p, q))):
        m = i == n
        out[..., 0][m], out[..., 1][m], out[..., 2][m] = r[m], g[m], b[m]
    return out


def knee(v, gain):
    """v * gain, rolled off softly toward 1 (highlights keep their shape)."""
    x = v * gain
    return np.where(x < 0.8, x, 0.8 + 0.2 * (1 - np.exp(-(x - 0.8) / 0.2)))


def hair(rgb):
    hsv = rgb_to_hsv(rgb)
    hsv[..., 0] += HAIR_H
    hsv[..., 1] = np.clip(hsv[..., 1] * HAIR_S, 0, 1)
    hsv[..., 2] = knee(hsv[..., 2], HAIR_V)
    return hsv_to_rgb(hsv)


def iris(size):
    """Her ringed iris, painted procedurally (the dish's uv's cover the whole square: the disk fills it)."""
    yy, xx = np.mgrid[0:size, 0:size]
    x, y = (xx + 0.5) / size * 2 - 1, 1 - (yy + 0.5) / size * 2        # y up
    r = np.hypot(x, y) / 0.97
    # gold, warmer outward
    inner, outer = np.array([1.0, 0.86, 0.38]), np.array([0.98, 0.58, 0.16])
    rgb = inner + (outer - inner) * smoothstep(0.15, 0.85, r)[..., None]
    # the rings: darker orange-red bands, the inner ones finer (her eyes' concentric circles)
    for c, w, k in ((0.26, 0.026, 0.7), (0.43, 0.032, 0.8), (0.61, 0.036, 0.85), (0.78, 0.03, 0.75)):
        band = smoothstep(w, w * 0.3, np.abs(r - c)) * k
        rgb = rgb * (1 - band[..., None]) + np.array([0.78, 0.27, 0.10]) * band[..., None]
    # fine lighter lines between the bands (anime eyes are drawn line on line)
    for c in (0.36, 0.55, 0.72):
        line = smoothstep(0.012, 0.0, np.abs(r - c)) * 0.45
        rgb = rgb * (1 - line[..., None]) + np.array([1.0, 0.93, 0.62]) * line[..., None]
    # pupil and limbal rim
    pupil = smoothstep(0.15, 0.11, r)
    rgb = rgb * (1 - pupil[..., None]) + np.array([0.18, 0.04, 0.03]) * pupil[..., None]
    rim = smoothstep(0.86, 0.97, r)
    rgb = rgb * (1 - rim[..., None]) + np.array([0.36, 0.10, 0.04]) * rim[..., None]
    # the upper lid's shadow over the top third, a lift at the bottom
    shade = 1 - 0.45 * smoothstep(0.05, 0.85, y) * smoothstep(1.2, 0.3, r)
    lift = 1 + 0.12 * smoothstep(-0.2, -0.8, y)
    rgb = rgb * (shade * lift)[..., None]
    # catchlights: a soft one upper left, a small one lower right
    for (cx, cy), rad, k in (((-0.34, 0.2), 0.17, 1.0), ((0.32, -0.36), 0.08, 0.75)):
        hl = smoothstep(rad, rad * 0.55, np.hypot(x - cx, (y - cy) * 1.15)) * k
        rgb = rgb * (1 - hl[..., None]) + hl[..., None]
    return np.clip(rgb, 0, 1)


def blob(shape, c, rx, ry):
    yy, xx = np.mgrid[0:shape[0], 0:shape[1]]
    return smoothstep(1.0, 0.0, np.hypot((xx - c[0]) / rx, (yy - c[1]) / ry))


def look(img):
    """The base atlas (PIL) restyled (see the module's doc): a new RGBA image."""
    a = np.asarray(img.convert('RGBA')).astype(float) / 255
    rgb = a[..., :3]
    # hair
    x, y, s = QUADS['hair']
    rgb[y:y + s, x:x + s] = hair(rgb[y:y + s, x:x + s])
    # skin: the scalp under the hair (saturated red) as the hair; the rest cleaned up
    x, y, s = QUADS['skin']
    sk = rgb[y:y + s, x:x + s]
    hsv = rgb_to_hsv(sk)
    red = (hsv[..., 0] < 0.05) | (hsv[..., 0] > 0.97)
    scalp = smoothstep(0.5, 0.62, hsv[..., 1]) * red
    skin = smoothstep(0.62, 0.5, hsv[..., 1]) * smoothstep(0.55, 0.75, hsv[..., 2])
    sh = hsv.copy()
    sh[..., 0] = np.where(red, sh[..., 0] + 0.028, sh[..., 0])      # from pink toward a warm peach ivory
    sh[..., 1] = np.clip(sh[..., 1] * 1.15 + 0.03, 0, 1)
    sh[..., 2] = sh[..., 2] * 0.98
    clean = hsv_to_rgb(sh)
    sk = sk * (1 - skin[..., None]) + clean * skin[..., None]
    sk = sk * (1 - scalp[..., None]) + hair(sk) * scalp[..., None]
    # a faint warm flush on the cheeks and the tip of the nose, the lips rosier
    flush = np.maximum(np.maximum(blob(sk.shape, CHEEKS[0], 70, 42), blob(sk.shape, CHEEKS[1], 70, 42)) * 0.16,
                       blob(sk.shape, NOSE, 18, 14) * 0.12) * skin
    sk = sk * (1 - flush[..., None]) + np.array([0.98, 0.62, 0.60]) * flush[..., None]
    lip = blob(sk.shape, LIPS, 48, 9) * (hsv[..., 1] > 0.18) * (hsv[..., 2] < 0.9) * 0.45
    sk = sk * (1 - lip[..., None]) + np.array([0.86, 0.42, 0.44]) * lip[..., None]
    rgb[y:y + s, x:x + s] = sk
    # shirt: white with cool grey shading, not lavender
    x, y, s = QUADS['shirt']
    sh = rgb_to_hsv(rgb[y:y + s, x:x + s])
    blue = (((sh[..., 0] > 0.55) & (sh[..., 0] < 0.8)) | (sh[..., 1] < 0.05)) & (sh[..., 1] < 0.35) & (sh[..., 2] > 0.5)
    sh[..., 1] = np.where(blue, sh[..., 1] * 0.45, sh[..., 1])
    # the game lights her front fully: the cloth kept off pure white and its folds deepened, so it has shape in daylight
    v = sh[..., 2]
    sh[..., 2] = np.where(blue, np.clip(0.9 + (v - 0.93) * 1.4, 0, 0.93), v)
    rgb[y:y + s, x:x + s] = hsv_to_rgb(sh)
    # trousers: lifted a little, folds' contrast raised about their middle
    x, y, s = QUADS['trouser']
    t = rgb[y:y + s, x:x + s]
    grey = (np.abs(t[..., 0] - 0.73) < 0.02) & (np.abs(t[..., 1] - 0.73) < 0.02)      # the model's empty background
    lum = t.mean(-1, keepdims=True)
    t2 = np.clip((t - lum) * 1.1 + (0.22 + (lum - 0.2) * 1.55), 0, 1)
    rgb[y:y + s, x:x + s] = np.where(grey[..., None], t, t2)
    # irises
    x, y, s = QUADS['eyes']
    rgb[y:y + s, x:x + s] = iris(s)
    a[..., :3] = np.clip(rgb, 0, 1)
    return Image.fromarray((a * 255).round().astype(np.uint8), 'RGBA')


# the hair's highlight ring (bake_hair): degrees down from the crown, its zigzag, its half width and strength
RING_AT, RING_ZIG, RING_W, RING_K = 37.0, 1.5, 3.6, 0.45
RING_COL = np.array([1.0, 0.78, 0.68])
HAIR_MAT = 3        # gen_makima_mesh.HAIR


def bake_hair(img, P, tris, uvs, mats):
    """The anime highlight ring round her crown (the 'angel ring'), baked from her geometry into the hair's texture: every
    texel of a hair triangle on the outside of her head gets the ring's light by where it is (its angle down from the crown,
    zigzagging round her head, strongest at her front and sides). P in the model's metres (Z up, her front -Y), uvs per
    triangle corner (0..1 of the hair's texture, v up), mats per triangle."""
    a = np.asarray(img.convert('RGBA')).astype(float) / 255
    tris, uvs, mats = np.asarray(tris), np.asarray(uvs, float), np.asarray(mats)
    eyes = P[tris[mats == 4].ravel()]
    eyes = eyes[eyes[:, 2] > 1.4]
    centre = eyes.mean(0) + [0, 0.065, -0.012]           # the head's middle, behind and below her eyes
    hair = np.where(mats == HAIR_MAT)[0]
    d = P[tris[hair].ravel()] - centre
    r = np.linalg.norm(d, axis=1)
    th = np.degrees(np.arccos(np.clip(d[:, 2] / np.maximum(r, 1e-9), -1, 1)))
    ph = np.arctan2(d[:, 0], -d[:, 1])
    # the outermost hair round the head, per direction (inner layers stay dark)
    bins = (np.clip(th // 6, 0, 29).astype(int), ((ph + np.pi) // (np.pi / 18)).astype(int) % 36)
    rmax = np.zeros((30, 36))
    np.maximum.at(rmax, bins, r)
    x0, y0, s = QUADS['hair']
    for t in hair:
        q = P[tris[t]] - centre
        if np.linalg.norm(q, axis=1).max() > 0.17 or q[:, 2].max() < -0.02:
            continue
        uv = uvs[t] * s
        lo, hi = np.floor(uv.min(0)).astype(int) - 1, np.ceil(uv.max(0)).astype(int) + 1
        gx, gy = np.meshgrid(np.arange(lo[0], hi[0]) + 0.5, np.arange(lo[1], hi[1]) + 0.5)
        A, B, C = uv
        den = (B[1] - C[1]) * (A[0] - C[0]) + (C[0] - B[0]) * (A[1] - C[1])
        if abs(den) < 1e-12:
            continue
        w0 = ((B[1] - C[1]) * (gx - C[0]) + (C[0] - B[0]) * (gy - C[1])) / den
        w1 = ((C[1] - A[1]) * (gx - C[0]) + (A[0] - C[0]) * (gy - C[1])) / den
        w2 = 1 - w0 - w1
        m = (w0 > -0.03) & (w1 > -0.03) & (w2 > -0.03)
        if not m.any():
            continue
        pts = np.outer(w0[m], q[0]) + np.outer(w1[m], q[1]) + np.outer(w2[m], q[2])
        rr = np.linalg.norm(pts, axis=1)
        tt = np.degrees(np.arccos(np.clip(pts[:, 2] / np.maximum(rr, 1e-9), -1, 1)))
        pp = np.arctan2(pts[:, 0], -pts[:, 1])
        outer = rmax[np.clip(tt // 6, 0, 29).astype(int), ((pp + np.pi) // (np.pi / 18)).astype(int) % 36]
        centre_t = RING_AT + RING_ZIG * np.sin(5 * pp + 1)
        # a band, its lower edge spiking down along the locks (a sawtooth round her head), its top edge clean
        saw = np.abs(((pp * 16 / np.pi) % 2) - 1)
        lo_edge = centre_t + RING_W * (0.5 + 1.6 * saw ** 2)
        hi_edge = centre_t - RING_W * 0.55
        k = np.minimum(smoothstep(lo_edge, lo_edge - 1.2, tt), smoothstep(hi_edge, hi_edge + 0.8, tt)) * RING_K
        k += smoothstep(RING_W * 3.5, RING_W, np.abs(tt - centre_t)) * 0.1          # a soft sheen round it
        k *= smoothstep(-0.6, 0.1, np.cos(pp)) * smoothstep(outer - 0.014, outer - 0.004, rr)
        px = np.clip((gx[m] - 0.5).astype(int), 0, s - 1) + x0
        py = np.clip((s - (gy[m] - 0.5)).astype(int), 0, s - 1) + y0       # v up -> rows down
        cur = a[py, px, :3]
        a[py, px, :3] = cur + (np.maximum(cur, RING_COL * cur.max(-1, keepdims=True) ** 0.35) - cur) * k[:, None]
    return Image.fromarray((np.clip(a, 0, 1) * 255).round().astype(np.uint8), 'RGBA')


def base():
    """The model's atlas as atlas() wrote it (bootstrapped once from the built texture, which then was exactly that)."""
    if not os.path.exists(BASE):
        Image.open(OUT).convert('RGB').save(BASE, optimize=True)
    return Image.open(BASE)


if __name__ == '__main__':
    look(base()).save(OUT, optimize=True)
    print(OUT)
