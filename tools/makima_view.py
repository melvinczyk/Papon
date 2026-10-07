#!/usr/bin/env python3
"""Makima posed and drawn in software (no Blender: tools/mesh_preview.render), several views in one sheet, a figure of a
player (a box per limb, Steve's proportions) where the rider goes. For tuning tools/gen_makima_anims.py and the rider's
fit (MakimaEntity.Fit) without starting the game.

  python tools/makima_view.py out.png <animation> [t=0] [views=side,34,front,top] [size=560x420] [rider=1]
                                        [face=look|calm|blink|...|smile:1,lid_up_l:0.5,...] [gaze=gx:gy] [lbs=1]

Faces (makima_face.apply, the same as MakimaRenderer.Face) by preset name (FACES) or channel:weight pairs; gaze -1..1 each
way (her irises slid toward her left / up). Skinning is dual quaternion, as in the game (lbs=1: linear, the old way).

Views: name (side, side2, 34, 34b, front, back, top, face, low) or yaw:pitch:dist:tx:ty:tz (blocks, from her feet,
her front toward -Z as she stands in the rig). The rider comes from the animation's fit (gen_makima_anims.FIT).
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

import anim_preview as AP
import makima_face
import mesh_preview as MP

ASSETS = AP.ASSETS
VIEWS = {'side': (90, 8, 4.2, 0, 0.6, 0), 'side2': (-90, 8, 4.2, 0, 0.6, 0), '34': (-40, 22, 4.4, 0, 0.6, 0),
         '34b': (140, 22, 4.4, 0, 0.6, 0), 'front': (0, 8, 4.2, 0, 0.8, 0), 'back': (180, 8, 4.2, 0, 0.8, 0),
         'top': (0, 88, 4.6, 0, 0, 0), 'face': (0, 4, 1.1, 0, 1.75, 0),
         'close': (0, 0, 0.3, 0, 1.75, 0), 'close34': (30, 4, 0.32, 0, 1.75, 0), 'eyes': (0, 0, 0.17, -0.005, 1.78, 0),
         'low': (-25, 2, 3.4, 0, 0.4, 0)}


# expressions as MakimaRenderer.Face sets them (its presets: keep in step)
FACES = {
    'calm': {'lid_up': 0.18, 'lid_lo': 0.06, 'smile': 0.15},
    'look': {'lid_up': 0.42, 'lid_lo': 0.2, 'smile': 0.55, 'brow_down': 0.2},
    'blink': {'lid_up': 1.0, 'lid_lo': 0.0},
    'eyesmile': {'lid_up': 0.62, 'lid_lo': 0.38, 'smile': 0.9, 'brow_raise': 0.3},
    'pleasure': {'lid_up': 0.55, 'lid_lo': 0.12, 'open': 0.45, 'brow_worry': 0.8, 'blush': 0.8},
    'climax': {'lid_up': 0.96, 'lid_lo': 0.3, 'open': 0.9, 'brow_worry': 1.0, 'blush': 1.0},
    'open': {'open': 1.0},
    'smile': {'smile': 1.0},
}


def expression(spec):
    """A preset name or channel:weight pairs -> makima_face.apply's channels (lid_up -> both eyes)."""
    out = {}
    for part in spec.split(','):
        if part in FACES:
            out.update(FACES[part])
        elif ':' in part:
            k, v = part.split(':')
            out[k] = float(v)
    for k in ('lid_up', 'lid_lo'):
        if k in out:
            v = out.pop(k)
            out.setdefault(k + '_l', v)
            out.setdefault(k + '_r', v)
    return out


def quat(R):
    """A rotation matrix's unit quaternion (w, x, y, z)."""
    w = math.sqrt(max(0.0, 1 + R[0, 0] + R[1, 1] + R[2, 2])) / 2
    x = math.sqrt(max(0.0, 1 + R[0, 0] - R[1, 1] - R[2, 2])) / 2
    y = math.sqrt(max(0.0, 1 - R[0, 0] + R[1, 1] - R[2, 2])) / 2
    z = math.sqrt(max(0.0, 1 - R[0, 0] - R[1, 1] + R[2, 2])) / 2
    x = math.copysign(x, R[2, 1] - R[1, 2])
    y = math.copysign(y, R[0, 2] - R[2, 0])
    z = math.copysign(z, R[1, 0] - R[0, 1])
    return np.array([w, x, y, z])


def qmul(a, b):
    w1, x1, y1, z1 = np.moveaxis(a, -1, 0)
    w2, x2, y2, z2 = np.moveaxis(b, -1, 0)
    return np.stack([w1 * w2 - x1 * x2 - y1 * y2 - z1 * z2, w1 * x2 + x1 * w2 + y1 * z2 - z1 * y2,
                     w1 * y2 - x1 * z2 + y1 * w2 + z1 * x2, w1 * z2 + x1 * y2 - y1 * x2 + z1 * w2], -1)


def dqs(mats, J, W, Ph, Nr=None):
    """Dual quaternion skinning (MakimaRenderer's): each vertex's bones' rigid motions blended as dual quaternions (and
    the stored normals Nr turned by the blend, if given: (positions, normals))."""
    Q = np.array([quat(m[:3, :3]) for m in mats])
    T = np.concatenate([np.zeros((len(mats), 1)), mats[:, :3, 3]], 1)
    D = 0.5 * qmul(T, Q)
    q0 = Q[J[:, 0]]
    real = np.zeros((len(J), 4))
    dual = np.zeros((len(J), 4))
    for i in range(J.shape[1]):
        q, d = Q[J[:, i]], D[J[:, i]]
        sg = np.sign((q * q0).sum(1, keepdims=True))
        sg[sg == 0] = 1
        real += W[:, i:i + 1] * sg * q
        dual += W[:, i:i + 1] * sg * d
    n = np.linalg.norm(real, axis=1, keepdims=True)
    real, dual = real / n, dual / n
    w, v = real[:, :1], real[:, 1:]
    p = Ph[:, :3]
    rot = p + 2 * np.cross(v, np.cross(v, p) + w * p)
    t = 2 * (w * dual[:, 1:] - dual[:, :1] * v + np.cross(v, dual[:, 1:]))
    if Nr is not None:
        return rot + t, Nr + 2 * np.cross(v, np.cross(v, Nr) + w * Nr)
    return rot + t


def load():
    geo = json.load(open(os.path.join(ASSETS, 'geo/entity/makima_mesh.geo.json')))['minecraft:geometry'][0]
    mesh = json.load(open(os.path.join(ASSETS, 'meshes/makima.json')))
    anims = json.load(open(os.path.join(ASSETS, 'animations/entity/makima.animation.json')))['animations']
    return geo, mesh, anims


def posed(geo, mesh, anim, t, expr=None, gaze=(0, 0), lbs=False):
    """Vertices in blocks, as the game draws them (x mirrored the way GeckoLib bakes), her feet at the origin."""
    P = np.array(mesh['positions'], float).reshape(-1, 3)
    if expr:
        P = makima_face.apply(mesh, P, expr)
    k = mesh.get('influences', 2)
    J = np.array(mesh['joints']).reshape(-1, k)
    W = np.array(mesh['weights'], float).reshape(-1, k)
    M, _ = AP.pose(geo, anim, t)
    face = mesh.get('face')
    for s in 'lr':
        # her irises: the head's pose, slid across the eye
        T = np.eye(4)
        if face:
            T[:3, 3] = gaze[0] * face['gaze'][0] * np.array(face['ex']) + gaze[1] * face['gaze'][1] * np.array(face['ey'])
        M['eye_' + s] = M['head'] @ T
    mats = np.stack([M[b] for b in mesh['bones']])
    Ph = np.hstack([P, np.ones((len(P), 1))])
    Nr = np.array(mesh['normals'], float).reshape(-1, 3)
    if lbs:
        X = np.zeros_like(P)
        Nn = np.zeros_like(P)
        for i in range(k):
            X += W[:, i:i + 1] * np.einsum('nij,nj->ni', mats[J[:, i]], Ph)[:, :3]
            Nn += W[:, i:i + 1] * np.einsum('nij,nj->ni', mats[J[:, i]][:, :3, :3], Nr)
    else:
        X, Nn = dqs(mats, J, W, Ph, Nr)
    X = X / 16.0
    X[:, 0] *= -1
    Nn[:, 0] *= -1
    posed.normals = Nn / np.maximum(np.linalg.norm(Nn, axis=1, keepdims=True), 1e-9)
    return X


def normals(X, tris):
    N = np.zeros_like(X)
    fn = np.cross(X[tris[:, 1]] - X[tris[:, 0]], X[tris[:, 2]] - X[tris[:, 0]])
    for c in range(3):
        np.add.at(N, tris[:, c], fn)
    return N / np.maximum(np.linalg.norm(N, axis=1, keepdims=True), 1e-9)


def box(lo, hi, R, o):
    """A box (blocks) turned by R about the origin then moved to o: 8 corners, 12 triangles."""
    c = np.array([[x, y, z] for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2])])
    f = [[0, 1, 3, 2], [4, 6, 7, 5], [0, 4, 5, 1], [2, 3, 7, 6], [0, 2, 6, 4], [1, 5, 7, 3]]
    return c @ R.T + o, [[q[0], q[1], q[2]] for q in f] + [[q[0], q[2], q[3]] for q in f]


def rider(fit):
    """Steve from the fit (gen_makima_anims.RIDER): his hip joint at hip = (at, up) of her frame (at along her facing: -z
    here), facing back toward her (+z), tipped `tilt` degrees forward about the hips; legs straight down the body, angled
    back to kneel on the ground, or along the body lying down."""
    (a, up), tilt, mode = fit['hip'], fit['tilt'], fit['legs']
    t = math.radians(tilt)

    def R(ang):          # forward (toward +z) about the hips: up turns toward +z
        return np.array([[1, 0, 0], [0, math.cos(ang), -math.sin(ang)], [0, math.sin(ang), math.cos(ang)]])
    Rb = R(t)
    H = np.array([0, up, -a])
    if mode == 'kneel':
        leg = math.acos(max(-1, min(1, up / 0.75)))      # back from straight down to the knees on the ground
        Rl = R(leg)
    elif mode == 'lie':
        Rl = Rb
    else:
        Rl = R(0)
    out = []
    for lo, hi, col, rot in [((-0.25, 0.75, -0.25), (0.25, 1.25, 0.25), (0.55, 0.42, 0.3), Rb),
                             ((-0.25, 0.0, -0.125), (0.25, 0.75, 0.125), (0.3, 0.7, 0.7), Rb),
                             ((-0.5, 0.0, -0.125), (-0.25, 0.75, 0.125), (0.75, 0.55, 0.4), Rb),
                             ((0.25, 0.0, -0.125), (0.5, 0.75, 0.125), (0.75, 0.55, 0.4), Rb),
                             ((-0.25, -0.75, -0.125), (0.0, 0.0, 0.125), (0.25, 0.25, 0.6), Rl),
                             ((0.0, -0.75, -0.125), (0.25, 0.0, 0.125), (0.25, 0.25, 0.6), Rl)]:
        V, F = box(lo, hi, rot, H)
        out.append((V, F, col))
    return out


def render(X, N, UV, tris, tex, extra, view, size, over=None):
    yaw, pitch, dist, tx, ty, tz = view
    W, H = size
    eye, r, u, f = MP.camera(yaw, pitch, dist, (tx, ty, tz))
    img = np.zeros((H, W, 3), np.float32) + np.array([0.62, 0.74, 0.9], np.float32)
    zb = np.full((H, W), np.inf, np.float32)
    th, tw = tex.shape[:2]
    fov = math.radians(50)
    sc = (H / 2) / math.tan(fov / 2)
    L1 = np.array([0.2, 1.0, -0.7]); L1 /= np.linalg.norm(L1)
    L2 = np.array([-0.2, 1.0, 0.7]); L2 /= np.linalg.norm(L2)

    def project(P):
        rel = P - eye
        cx, cy, cz = rel @ r, rel @ u, rel @ f
        return W / 2 + cx / np.maximum(cz, 1e-3) * sc, H / 2 - cy / np.maximum(cz, 1e-3) * sc, cz

    def draw(P, Nn, T, uv=None, color=None):
        sx, sy, cz = project(P)
        shade = np.clip(0.4 + 0.6 * (np.abs(Nn @ L1) * 0.7 + np.clip(Nn @ L2, 0, 1) * 0.5), 0, 1)
        for a, b, c in T:
            if cz[a] < 0.05 or cz[b] < 0.05 or cz[c] < 0.05:
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
            z = 1 / (w0 / cz[a] + w1 / cz[b] + w2 / cz[c])
            sub = zb[y0:y1, x0:x1]
            m &= z < sub
            if not m.any():
                continue
            s = (w0 * shade[a] + w1 * shade[b] + w2 * shade[c])
            if uv is not None:
                pu = (w0 * uv[a, 0] / cz[a] + w1 * uv[b, 0] / cz[b] + w2 * uv[c, 0] / cz[c]) * z
                pv = (w0 * uv[a, 1] / cz[a] + w1 * uv[b, 1] / cz[b] + w2 * uv[c, 1] / cz[c]) * z
                col = tex[np.clip((pv * th).astype(int), 0, th - 1), np.clip((pu * tw).astype(int), 0, tw - 1)][..., :3]
            else:
                col = np.broadcast_to(np.array(color, np.float32), s.shape + (3,))
            region = img[y0:y1, x0:x1]
            region[m] = (col * s[..., None])[m]
            sub[m] = z[m]

    # the ground: a grid of blocks
    for gx in range(-3, 3):
        for gz in range(-3, 3):
            g = np.array([[x, 0, z] for x in (gx, gx + 1) for z in (gz, gz + 1)], float)
            draw(g, np.tile([0, 1, 0], (4, 1)).astype(float), [(0, 1, 3), (0, 3, 2)],
                 color=(0.5, 0.52, 0.47) if (gx + gz) % 2 else (0.46, 0.48, 0.43))
    for V, F, col in extra:
        Fa = np.array(F)
        Nn = normals(V, Fa)
        draw(V, Nn, F, color=col)
    draw(X, N, tris, uv=UV)
    if over is not None:
        # translucent runs (the blush): alpha-blended over what is drawn, depth-tested, not written
        tris2, alpha = over
        sx, sy, cz = project(X)
        for a, b, c in tris2:
            xs = np.array([sx[a], sx[b], sx[c]])
            ys = np.array([sy[a], sy[b], sy[c]])
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
            z = 1 / (w0 / cz[a] + w1 / cz[b] + w2 / cz[c])
            m &= z < zb[y0:y1, x0:x1] + 0.002
            if not m.any():
                continue
            pu = (w0 * UV[a, 0] / cz[a] + w1 * UV[b, 0] / cz[b] + w2 * UV[c, 0] / cz[c]) * z
            pv = (w0 * UV[a, 1] / cz[a] + w1 * UV[b, 1] / cz[b] + w2 * UV[c, 1] / cz[c]) * z
            col = over_tex[np.clip((pv * th).astype(int), 0, th - 1), np.clip((pu * tw).astype(int), 0, tw - 1)]
            al = col[..., 3:4] * alpha
            region = img[y0:y1, x0:x1]
            region[m] = (region * (1 - al) + col[..., :3] * al)[m]
    # block lines on the ground
    out = (np.clip(img, 0, 1) * 255).astype(np.uint8)
    return Image.fromarray(out)


def sheet(out, frames, size=(560, 420)):
    cols = min(3, len(frames))
    rows = (len(frames) + cols - 1) // cols
    S = Image.new('RGB', (size[0] * cols, size[1] * rows))
    from PIL import ImageDraw
    for k, (label, im) in enumerate(frames):
        ImageDraw.Draw(im).text((5, 5), label, fill=(20, 20, 20))
        S.paste(im, ((k % cols) * size[0], (k // cols) * size[1]))
    S.save(out)
    print(out)


def main():
    out = sys.argv[1]
    names = [a for a in sys.argv[2:] if '=' not in a]
    opts = dict(a.split('=', 1) for a in sys.argv[2:] if '=' in a)
    size = tuple(int(v) for v in opts.get('size', '560x420').split('x'))
    views = opts.get('views', 'side,34,front').split(',')
    geo, mesh, anims = load()
    global over_tex
    over_tex = np.asarray(Image.open(os.path.join(ASSETS, mesh['texture'].split(':')[1])).convert('RGBA'), np.float32) / 255
    tex = over_tex[..., :3]
    UV = np.array(mesh['uvs'], float).reshape(-1, 2)
    UV[:, 1] = 1 - UV[:, 1]
    idx = np.array(mesh['indices'])
    run = mesh.get('groups', {}).get('blush', [len(idx), 0])
    tris = idx[:run[0]].reshape(-1, 3)
    blush_tris = idx[run[0]:run[0] + run[1]].reshape(-1, 3)
    expr = expression(opts['face']) if 'face' in opts else None
    gaze = tuple(float(a) for a in opts.get('gaze', '0:0').split(':'))
    fits = {}
    if opts.get('rider', '1') == '1':
        try:
            import gen_makima_anims as GA
            fits = getattr(GA, 'RIDER_PREVIEW', {})
        except Exception as e:      # noqa: BLE001
            print('no fit:', e)
    frames = []
    for n in names:
        X = posed(geo, mesh, anims['animation.makima.' + n], float(opts.get('t', 0)), expr, gaze, opts.get('lbs') == '1')
        N = posed.normals          # the stored normals, skinned: what the game lights
        over = (blush_tris, (expr or {}).get('blush', 0)) if (expr or {}).get('blush') else None
        extra = rider(fits[n]) if n in fits else []
        for v in views:
            vv = VIEWS[v] if v in VIEWS else tuple(float(a) for a in v.split(':'))
            frames.append(('%s %s' % (n, v), render(X, N, UV, tris, tex, extra, vv, size, over)))
    sheet(out, frames, size)


if __name__ == '__main__':
    main()
