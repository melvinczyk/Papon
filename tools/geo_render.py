#!/usr/bin/env python3
"""A software preview of a generated geo model (box UV), for checking geometry without Blockbench or the game.

  python tools/geo_render.py <geo name> <out.png> [views=front34,rear34,side,top,back] [texture name]

Reads assets/papon/geo/entity/<name>.geo.json and textures/entity/<texture or name>.png; poses the rest pose (bone and
cube rotations in the file's convention as GeckoLib draws them: X positive lifts the +Z end, Y as written, Z negated);
draws each view with a z-buffer, flat Lambert shading, translucent texels blended over what is behind them (drawn after the
opaque ones). Faces follow the generators' semantic map (gen_caravan.FACE: the -X face takes the 'east' rect).
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/papon')


def rot(rx, ry, rz):
    a, b, c = math.radians(-rx), math.radians(ry), math.radians(-rz)
    X = np.array([[1, 0, 0], [0, math.cos(a), -math.sin(a)], [0, math.sin(a), math.cos(a)]])
    Y = np.array([[math.cos(b), 0, math.sin(b)], [0, 1, 0], [-math.sin(b), 0, math.cos(b)]])
    Z = np.array([[math.cos(c), -math.sin(c), 0], [math.sin(c), math.cos(c), 0], [0, 0, 1]])
    return Z @ Y @ X


def quads(geo):
    """(corners 4x3, uv 4x2) for every face of every cube, posed."""
    bones = {b['name']: b for b in geo['bones']}

    def chain(name):
        out = []
        while name:
            b = bones[name]
            out.append(b)
            name = b.get('parent')
        return out

    res = []
    for b in geo['bones']:
        for c in b.get('cubes', []):
            o = np.array(c['origin'], float)
            s = np.array(c['size'], float)
            inf = c.get('inflate', 0)
            lo, hi = o - inf, o + s + inf
            w, h, d = (max(1, int(round(v))) if v >= 1 else (1 if v > 0 else 0) for v in s)
            u, v = c['uv']
            r = {'up': (u + d, v, u + d + w, v + d), 'down': (u + d + w, v, u + d + 2 * w, v + d),
                 'east': (u, v + d, u + d, v + d + h), 'north': (u + d, v + d, u + d + w, v + d + h),
                 'west': (u + d + w, v + d, u + 2 * d + w, v + d + h), 'south': (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h)}
            x0, y0, z0 = lo
            x1, y1, z1 = hi
            # corners listed so (0)->(1) runs along the texture's u, (0)->(3) down its v (row 0 = the top)
            fs = {
                'north': [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
                'south': [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
                'east': [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
                'west': [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
                'up': [(x1, y1, z1), (x0, y1, z1), (x0, y1, z0), (x1, y1, z0)],
                'down': [(x1, y0, z0), (x0, y0, z0), (x0, y0, z1), (x1, y0, z1)],
            }
            for k, pts in fs.items():
                p = np.array(pts, float)
                if 'rotation' in c:
                    pv = np.array(c['pivot'], float)
                    p = (rot(*c['rotation']) @ (p - pv).T).T + pv
                for bb in chain(b['name']):
                    if bb.get('rotation'):
                        pv = np.array(bb['pivot'], float)
                        p = (rot(*bb['rotation']) @ (p - pv).T).T + pv
                x_0, y_0, x_1, y_1 = r[k]
                uv = np.array([(x_0, y_0), (x_1, y_0), (x_1, y_1), (x_0, y_1)], float)
                res.append((p, uv))
    return res


VIEWS = {'front34': (-35, 18), 'rear34': (145, 18), 'side': (90, 4), 'top': (0, 80), 'back': (180, 8), 'front': (0, 8),
         'otherside': (-90, 4), 'low34': (-30, -4), 'rearlow': (160, 2)}


def render(geo, tex, yaw, pitch, size=(640, 400)):
    W, H = size
    qs = quads(geo)
    allp = np.concatenate([q[0] for q in qs])
    centre = (allp.min(0) + allp.max(0)) / 2
    radius = np.linalg.norm(allp.max(0) - allp.min(0)) / 2
    a, e = math.radians(yaw), math.radians(pitch)
    # camera on a sphere, looking at the centre (yaw 0 = in front of the nose: -Z)
    fwd = -np.array([math.sin(a) * math.cos(e), math.sin(e), -math.cos(a) * math.cos(e)])
    right = np.cross(fwd, [0, 1, 0]); right /= np.linalg.norm(right)
    up = np.cross(right, fwd)
    scale = min(W, H) * 0.48 / radius
    img = np.zeros((H, W, 3)) + np.array([226, 230, 236]) / 255
    zb = np.full((H, W), np.inf)
    T = np.asarray(tex.convert('RGBA'), float) / 255
    th, tw = T.shape[:2]
    light = np.array([0.4, 0.8, -0.45]); light /= np.linalg.norm(light)
    trans = []

    def raster(p, uv, alpha_pass):
        q = p - centre
        sx = q @ right * scale + W / 2
        sy = -(q @ up) * scale + H / 2
        sz = q @ fwd
        n = np.cross(p[1] - p[0], p[3] - p[0])
        nn = np.linalg.norm(n)
        if nn < 1e-9:
            return
        n /= nn
        shade = 0.55 + 0.45 * abs(n @ light)
        for tri in ((0, 1, 2), (0, 2, 3)):
            xs, ys, zs = sx[list(tri)], sy[list(tri)], sz[list(tri)]
            uvs = uv[list(tri)]
            x0, x1 = int(max(0, math.floor(xs.min()))), int(min(W - 1, math.ceil(xs.max())))
            y0, y1 = int(max(0, math.floor(ys.min()))), int(min(H - 1, math.ceil(ys.max())))
            if x0 > x1 or y0 > y1:
                continue
            gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            d = (ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2])
            if abs(d) < 1e-9:
                continue
            l0 = ((ys[1] - ys[2]) * (gx - xs[2]) + (xs[2] - xs[1]) * (gy - ys[2])) / d
            l1 = ((ys[2] - ys[0]) * (gx - xs[2]) + (xs[0] - xs[2]) * (gy - ys[2])) / d
            l2 = 1 - l0 - l1
            m = (l0 >= -1e-6) & (l1 >= -1e-6) & (l2 >= -1e-6)
            if not m.any():
                continue
            z = l0 * zs[0] + l1 * zs[1] + l2 * zs[2]
            u = l0 * uvs[0, 0] + l1 * uvs[1, 0] + l2 * uvs[2, 0]
            v = l0 * uvs[0, 1] + l1 * uvs[1, 1] + l2 * uvs[2, 1]
            ui = np.clip(np.floor(u - 1e-4 * np.sign(u - uvs[:, 0].mean())).astype(int), 0, tw - 1)
            vi = np.clip(np.floor(v - 1e-4 * np.sign(v - uvs[:, 1].mean())).astype(int), 0, th - 1)
            c = T[vi, ui]
            sub = zb[y0:y1 + 1, x0:x1 + 1]
            vis = m & (z < sub) & (c[..., 3] > 0.02)
            if alpha_pass:
                vis &= c[..., 3] < 0.98
                al = c[..., 3:4]
                blk = img[y0:y1 + 1, x0:x1 + 1]
                blk[vis] = (blk * (1 - al) + c[..., :3] * shade * al)[vis]
            else:
                vis &= c[..., 3] >= 0.98
                img[y0:y1 + 1, x0:x1 + 1][vis] = (c[..., :3] * shade)[vis]
                sub[vis] = z[vis]

    for p, uv in qs:
        raster(p, uv, False)
    order = sorted(qs, key=lambda q: -((q[0].mean(0) - centre) @ fwd))
    for p, uv in order:
        raster(p, uv, True)
    return Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))


def main():
    name, out = sys.argv[1], sys.argv[2]
    views = sys.argv[3].split(',') if len(sys.argv) > 3 else ['front34', 'rear34', 'side', 'top']
    texname = sys.argv[4] if len(sys.argv) > 4 else name
    geo = json.load(open(os.path.join(ASSETS, 'geo/entity', name + '.geo.json')))['minecraft:geometry'][0]
    tex = Image.open(os.path.join(ASSETS, 'textures/entity', texname + '.png'))
    ims = [render(geo, tex, *VIEWS[v]) for v in views]
    cols = 2 if len(ims) > 1 else 1
    rows = (len(ims) + cols - 1) // cols
    sheet = Image.new('RGB', (640 * cols, 400 * rows), (255, 255, 255))
    for i, im in enumerate(ims):
        sheet.paste(im, ((i % cols) * 640, (i // cols) * 400))
    sheet.save(out)
    print(out)


if __name__ == '__main__':
    main()
