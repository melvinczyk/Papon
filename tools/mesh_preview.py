#!/usr/bin/env python3
"""A software preview of a mesh (assets/papon/meshes/<name>.json, client/mesh/SkinnedMesh's format) in its rest pose:
textured, two-light Lambert shading like an entity's, translucent groups blended after the opaque ones; several views
in one sheet. For shaping a generated mesh (tools/gen_skyline.py) without starting the game.

  python tools/mesh_preview.py skyline out.png [views] [size]
  views: comma list of name or yaw:pitch:dist[:tx:ty:tz]   (front34, rear34, side, front, rear, top, cabin, low34)
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/papon')

VIEWS = {
    'front34': (-35, 18, 120, 0, 12, 0), 'rear34': (145, 18, 120, 0, 12, 0), 'side': (90, 6, 115, 0, 12, 0),
    'front': (0, 8, 110, 0, 12, 0), 'rear': (180, 10, 110, 0, 12, 0), 'top': (0, 89, 120, 0, 10, 0),
    'cabin': (150, 32, 60, 0, 16, 5), 'low34': (-30, 4, 100, 0, 10, 0), 'other34': (35, 18, 120, 0, 12, 0),
}


def load(name):
    with open(os.path.join(ASSETS, 'meshes', name + '.json')) as f:
        m = json.load(f)
    ns, path = m['texture'].split(':')
    tex = np.asarray(Image.open(os.path.join(ASSETS, path)).convert('RGBA'), dtype=np.float32) / 255
    P = np.array(m['positions'], np.float32).reshape(-1, 3)
    N = np.array(m['normals'], np.float32).reshape(-1, 3)
    # as SkinnedMesh loads it (x mirrored the way GeckoLib bakes cubes): what the game shows
    P[:, 0] *= -1
    N[:, 0] *= -1
    UV = np.array(m['uvs'], np.float32).reshape(-1, 2)
    UV[:, 1] = 1 - UV[:, 1]
    idx = np.array(m['indices'], np.int64)
    groups = m.get('groups', {'opaque': [0, len(idx)]})
    return P, N, UV, idx, groups, tex


def camera(yaw, pitch, dist, target):
    y, p = math.radians(yaw), math.radians(pitch)
    # yaw 0 looks at the car's front (from -Z toward +Z)
    eye = np.array(target) + dist * np.array([math.sin(y) * math.cos(p), math.sin(p), -math.cos(y) * math.cos(p)])
    f = np.array(target) - eye
    f /= np.linalg.norm(f)
    r = np.cross(f, [0, 1, 0])
    if np.linalg.norm(r) < 1e-6:
        r = np.array([1.0, 0, 0])
    r /= np.linalg.norm(r)
    u = np.cross(r, f)
    return eye, r, u, f


def render(P, N, UV, idx, groups, tex, view, size=(720, 450)):
    yaw, pitch, dist, tx, ty, tz = view
    W, H = size
    eye, r, u, f = camera(yaw, pitch, dist, (tx, ty, tz))
    rel = P - eye
    cx, cy, cz = rel @ r, rel @ u, rel @ f
    fov = math.radians(40)
    sc = (H / 2) / math.tan(fov / 2)
    sx = W / 2 + cx / np.maximum(cz, 1e-3) * sc
    sy = H / 2 - cy / np.maximum(cz, 1e-3) * sc
    L1 = np.array([0.2, 1.0, -0.7]); L1 /= np.linalg.norm(L1)
    L2 = np.array([-0.2, 1.0, 0.7]); L2 /= np.linalg.norm(L2)
    shade = np.clip(0.4 + 0.6 * (np.clip(N @ L1, 0, 1) + np.clip(N @ L2, 0, 1)) * 0.6, 0, 1)
    img = np.zeros((H, W, 3), np.float32) + np.array([0.62, 0.74, 0.9], np.float32)
    img[int(H * 0.62):] = [0.45, 0.47, 0.42]
    zb = np.full((H, W), np.inf, np.float32)
    th, tw = tex.shape[:2]

    def draw(quads, translucent):
        tris = []
        for q in quads:
            tris.append((q[0], q[1], q[2]))
            if q[3] != q[2]:
                tris.append((q[0], q[2], q[3]))
        for a, b, c in tris:
            if cz[a] < 1 or cz[b] < 1 or cz[c] < 1:
                continue
            xs = np.array([sx[a], sx[b], sx[c]]); ys = np.array([sy[a], sy[b], sy[c]])
            x0, x1 = max(int(xs.min()), 0), min(int(xs.max()) + 1, W)
            y0, y1 = max(int(ys.min()), 0), min(int(ys.max()) + 1, H)
            if x0 >= x1 or y0 >= y1:
                continue
            den = (ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2])
            if abs(den) < 1e-9:
                continue
            gx, gy = np.meshgrid(np.arange(x0, x1) + 0.5, np.arange(y0, y1) + 0.5)
            w0 = ((ys[1] - ys[2]) * (gx - xs[2]) + (xs[2] - xs[1]) * (gy - ys[2])) / den
            w1 = ((ys[2] - ys[0]) * (gx - xs[2]) + (xs[0] - xs[2]) * (gy - ys[2])) / den
            w2 = 1 - w0 - w1
            m = (w0 >= -1e-4) & (w1 >= -1e-4) & (w2 >= -1e-4)
            if not m.any():
                continue
            iz = w0 / cz[a] + w1 / cz[b] + w2 / cz[c]
            z = 1 / iz
            sub = zb[y0:y1, x0:x1]
            m &= z < sub - (0.0 if not translucent else 0.02)
            if not m.any():
                continue
            pu = (w0 * UV[a, 0] / cz[a] + w1 * UV[b, 0] / cz[b] + w2 * UV[c, 0] / cz[c]) * z
            pv = (w0 * UV[a, 1] / cz[a] + w1 * UV[b, 1] / cz[b] + w2 * UV[c, 1] / cz[c]) * z
            ti = np.clip((pv * th).astype(int), 0, th - 1)
            tj = np.clip((pu * tw).astype(int), 0, tw - 1)
            col = tex[ti, tj]
            s = (w0 * shade[a] + w1 * shade[b] + w2 * shade[c])
            rgb = col[..., :3] * s[..., None]
            region = img[y0:y1, x0:x1]
            if translucent:
                al = col[..., 3:4]
                region[m] = (region * (1 - al) + rgb * al)[m]
            else:
                keep = m & (col[..., 3] > 0.1)
                region[keep] = rgb[keep]
                sub[keep] = z[keep]

    def quads_of(g):
        if g not in groups:
            return []
        s0, n = groups[g]
        q = idx[s0:s0 + n].reshape(-1, 4)
        return q
    draw(quads_of('opaque'), False)
    gl = quads_of('glass')
    if len(gl):
        d = np.array([cz[q].mean() for q in gl])
        draw(gl[np.argsort(-d)], True)
    return Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))


def main():
    name = sys.argv[1]
    out = sys.argv[2]
    names = (sys.argv[3] if len(sys.argv) > 3 else 'front34,rear34,side,front,rear,cabin').split(',')
    size = tuple(int(v) for v in sys.argv[4].split('x')) if len(sys.argv) > 4 else (720, 450)
    P, N, UV, idx, groups, tex = load(name)
    shots = []
    for n in names:
        v = VIEWS[n] if n in VIEWS else tuple(float(t) for t in n.split(':')) + (0, 12, 0)[len(n.split(':')) - 3:] if ':' in n else VIEWS['front34']
        if len(v) == 3:
            v = v + (0, 12, 0)
        shots.append(render(P, N, UV, idx, groups, tex, v, size))
    cols = 2 if len(shots) > 1 else 1
    rows = (len(shots) + cols - 1) // cols
    sheet = Image.new('RGB', (size[0] * cols, size[1] * rows))
    for k, im in enumerate(shots):
        sheet.paste(im, ((k % cols) * size[0], (k // cols) * size[1]))
    sheet.save(out)
    print(out)


if __name__ == '__main__':
    main()
