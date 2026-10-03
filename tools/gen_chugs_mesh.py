#!/usr/bin/env python3
"""Builds Badlands Chugs as a semi-photorealistic skinned mesh for the customer rig.

Like EDP445 (gen_edp_mesh.py) he is drawn by client/mesh/MeshCrowdRenderer: the GeckoLib
customer rig and animations run as usual, and this mesh is skinned onto the six moving bones.
There is no game asset of him, so the body is built here:

  body     the MakeHuman 1.1 base mesh (CC0), morphed with MakeHuman's own targets (African male,
           middle-aged, max weight, double chin, round fat head) and then fattened along its
           normals per body part (torso, belly, neck, arms, legs) to his build
  rig      MakeHuman's default skeleton and weights (CC0) folded onto the rig's six bones, two
           weights per vertex; the A-pose arms are swung down about the shoulders
  face     a frontal photo of him, warped onto the head with a thin-plate spline through matched
           landmarks (eyes, nose, mouth corners, chin, head outline) and projected from the front;
           the eyeballs get the photo's eyes the same way
  skin     his skin tone measured from the photo, with pore noise, baked ambient occlusion and the
           shine on his bald head
  clothes  painted in 3D like EDP's: his black tee with BADLANDS across the chest, black pants,
           black shoes; the mesh under the clothes is smoothed so it reads as cloth

Downloads what it needs into tools/.cache/chugs (gitignored) on first run:
  python3 tools/gen_chugs_mesh.py
Writes, under src/main/resources/assets/papon/:
  meshes/badlands_chugs.json                skinned triangle mesh, rig-space pixels
  textures/entity/badlands_chugs_mesh.png   2048x2048 atlas (MakeHuman UV layout)
  geo/entity/badlands_chugs_mesh.geo.json   the rig: bones at the mesh's joints + jug and taco
The blocky badlands_chugs (gen_customers.py) is untouched; its texture still supplies the jug and
taco, which stay cubes on this rig. `--preview DIR` also writes software-rendered turnarounds.
"""
import json
import math
import os
import subprocess
import sys
import urllib.request

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/papon')
CACHE = os.path.join(ROOT, 'tools/.cache/chugs')
MH_RAW = 'https://raw.githubusercontent.com/makehumancommunity/makehuman/master/makehuman/data/'
PHOTO_URL = 'https://i.ytimg.com/vi/kYnmaUPVqcA/maxresdefault.jpg'   # frontal, bald, goatee, even light
OUT_TEX = 2048
HEAD_TOP_PX = 35.0      # the blocky Chugs is 35 px tall
BONES = ['torso', 'head', 'arm_r', 'arm_l', 'leg_r', 'leg_l', 'forearm_r', 'forearm_l']   # forearms: his own anims only
ARM_HANG_DEG = 16.0
PROP_SCALE = 0.8
PROP_NUDGE = {'jug': np.array([0, 1.0, 2.5]), 'taco': np.array([0, 0, 1.5])}   # blocky arm is thicker: in to the palm     # arms end this far out from vertical (his belly is in the way)

OLD = 0.2               # MakeHuman age mix: ~38 years (older reads gaunt on him)
WEIGHT = 1.8            # past MakeHuman's max-weight target
TARGETS = [             # (target, weight); macro targets get the age mix applied below
    ('macrodetails/african-male-young', 1 - OLD), ('macrodetails/african-male-old', OLD),
    ('macrodetails/universal-male-young-averagemuscle-maxweight', (1 - OLD) * WEIGHT),
    ('macrodetails/universal-male-old-averagemuscle-maxweight', OLD * WEIGHT),
    ('stomach/stomach-pregnant-incr', 1.0), ('neck/neck-double-incr', 1.0), ('head/head-fat-incr', 1.0),
    ('head/head-round', 0.4), ('neck/neck-scale-horiz-incr', 1.0), ('neck/neck-scale-depth-incr', 0.8),
    ('torso/torso-scale-depth-incr', 0.5), ('torso/torso-scale-horiz-incr', 0.3),
    # his face: wide and round, full cheeks, broad nose, full lips, long chin, smiling eyes
    ('head/head-scale-horiz-incr', 0.5), ('chin/chin-height-incr', 0.4), ('chin/chin-width-incr', 0.5),
    ('cheek/l-cheek-volume-incr', 1.0), ('cheek/r-cheek-volume-incr', 1.0),
    ('cheek/l-cheek-inner-incr', 0.5), ('cheek/r-cheek-inner-incr', 0.5),
    ('nose/nose-scale-horiz-incr', 0.6), ('nose/nose-flaring-incr', 0.6), ('nose/nose-nostrils-width-incr', 0.5),
    ('nose/nose-volume-incr', 0.4), ('mouth/mouth-scale-horiz-incr', 0.4), ('mouth/mouth-angles-up', 1.0),
    ('mouth/mouth-lowerlip-volume-incr', 0.4), ('mouth/mouth-upperlip-volume-incr', 0.3),
    ('eyes/l-eye-height2-decr', 0.4), ('eyes/r-eye-height2-decr', 0.4),
    ('eyes/l-eye-bag-incr', 0.4), ('eyes/r-eye-bag-incr', 0.4),
]
FAT = {'torso': 0.6, 'uarm': 0.38, 'larm': 0.15, 'uleg': 0.35, 'lleg': 0.15, 'neck': 0.55}   # decimetres
BELLY = (np.array([0.0, 4.0, 1.3]), (2.6, 1.6), 0.65)    # centre, (rx, ry), push

# Face landmarks -> photo pixel (1280x720). A landmark is a MakeHuman body vertex, 'r_eye'/'l_eye' (the
# eyeball's centre) or ('side', y, sign): the head's silhouette at height y. All follow the morphs, so
# the photo stays registered when the shape changes. Photo sides are mirrored from his unobstructed
# left side about x = 685 (a glass covers his right).
LANDMARKS = [
    ('r_eye', (642.5, 269.0)), ('l_eye', (732.5, 271.0)),
    (5054, (682.5, 327.5)),                             # under the nose
    (5057, (647.5, 322.0)), (11674, (715.0, 322.0)),    # nostrils
    (411, (637.5, 370.0)), (7171, (722.5, 375.0)),      # mouth corners
    (466, (682.5, 380.0)),                              # mouth
    (5162, (677.0, 468.0)),                             # chin
    (881, (685.0, 140.0)),                              # top of the head
    (873, (581.0, 270.0)), (7578, (789.0, 270.0)),      # sides of the face at the eyes (in front of the ears)
    (('side', 9.28, -1), (601.0, 200.0)), (('side', 9.28, 1), (769.0, 200.0)),   # temples
    (('side', 8.05, -1), (571.0, 375.0)), (('side', 8.05, 1), (799.0, 375.0)),   # cheeks
    (('side', 7.77, -1), (595.0, 450.0)), (('side', 7.77, 1), (775.0, 450.0)),   # jowls
    (220, (685.0, 250.0)),                              # between the brows
]
UNDER_SHIRT = [((0.381, 0.565), 0.035), ((0.325, 0.702), 0.04), ((0.435, 0.702), 0.04)]   # navel, nipples (UV)
SKIN = np.array([104, 68, 52], float)   # his tone in daylight; the photo is white-balanced to it
TEE, PANTS, SHOE, SOLE = (np.array(c, float) for c in ((30, 30, 33), (36, 37, 42), (24, 24, 26), (70, 68, 66)))


# ---------------------------------------------------------------- sources
def fetch(rel, url):
    path = os.path.join(CACHE, rel)
    if not os.path.exists(path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        try:
            req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
            with urllib.request.urlopen(req) as r:
                data = r.read()
            with open(path, 'wb') as f:
                f.write(data)
        except OSError:   # python.org builds on macOS often lack CA certificates: let curl fetch it
            subprocess.run(['curl', '-sfL', '-A', 'Mozilla/5.0', '-o', path, url], check=True)
    return path


def mh(rel):
    return fetch('makehuman/' + rel, MH_RAW + rel)


def load_obj(path):
    V, VT, F = [], [], []
    group = None
    for line in open(path):
        if line.startswith('v '):
            V.append([float(x) for x in line.split()[1:4]])
        elif line.startswith('vt '):
            VT.append([float(x) for x in line.split()[1:3]])
        elif line.startswith('g '):
            group = line.split()[1]
        elif line.startswith('f '):
            corners = [t.split('/') for t in line.split()[1:]]
            F.append(([int(c[0]) - 1 for c in corners], [int(c[1]) - 1 for c in corners], group))
    return np.array(V), np.array(VT), F


def load_target(rel):
    idx, d = [], []
    for line in open(mh('targets/' + rel + '.target')):
        if line.startswith('#') or not line.strip():
            continue
        p = line.split()
        idx.append(int(p[0]))
        d.append([float(x) for x in p[1:4]])
    return np.array(idx), np.array(d)


# ---------------------------------------------------------------- body
def vertex_normals(P, quads):
    a, b, c, d = (P[quads[:, k]] for k in range(4))
    n = np.cross(c - a, d - b)
    N = np.zeros_like(P)
    for k in range(4):
        np.add.at(N, quads[:, k], n)
    return N / np.maximum(np.linalg.norm(N, axis=1, keepdims=True), 1e-9)


def neighbours(n, quads):
    pairs = np.concatenate([quads[:, [k, (k + 1) % 4]] for k in range(4)])
    pairs = np.concatenate([pairs, pairs[:, ::-1]])
    pairs = np.unique(pairs, axis=0)
    return pairs


def smooth_scalar(f, pairs, n, iters):
    deg = np.bincount(pairs[:, 0], minlength=n).astype(float)
    for _ in range(iters):
        acc = np.bincount(pairs[:, 0], weights=f[pairs[:, 1]], minlength=n)
        f = np.where(deg > 0, 0.5 * f + 0.5 * acc / np.maximum(deg, 1), f)
    return f


def smooth_points(P, pairs, mask, iters, k=0.5):
    """Taubin smoothing (shrink, then inflate) of the masked vertices: removes detail, keeps volume."""
    n = len(P)
    deg = np.bincount(pairs[:, 0], minlength=n).astype(float)
    for step in [k, -k * 1.06] * iters:
        acc = np.stack([np.bincount(pairs[:, 0], weights=P[pairs[:, 1], i], minlength=n) for i in range(3)], 1)
        avg = acc / np.maximum(deg, 1)[:, None]
        P = P + (mask * step)[:, None] * (avg - P) * (deg > 0)[:, None]
    return P


def part_of(bone):
    for prefix, part in (('upperarm', 'uarm'), ('shoulder', 'uarm'), ('lowerarm', 'larm'), ('upperleg', 'uleg'),
                         ('lowerleg', 'lleg'), ('neck', 'neck'), ('spine', 'torso'), ('pelvis', 'torso'),
                         ('root', 'torso'), ('breast', 'torso'), ('clavicle', 'torso')):
        if bone.startswith(prefix):
            return part
    return None


def fatten(V, quads, body_verts, weights):
    amount = np.zeros(len(V))
    for bone, lst in weights.items():
        part = part_of(bone)
        if part:
            a = np.array(lst)
            np.add.at(amount, a[:, 0].astype(int), a[:, 1] * FAT[part])
    pairs = neighbours(len(V), quads)
    amount = smooth_scalar(amount, pairs, len(V), 20)
    # the smoothing spreads the neck's fat up the face; keep it off the face (eyelids would close)
    face = np.zeros(len(V))
    for bone, lst in weights.items():
        if part_of(bone) is None and not bone.startswith(('finger', 'wrist', 'metacarpal', 'foot', 'toe')):
            a = np.array(lst)
            np.add.at(face, a[:, 0].astype(int), a[:, 1])
    amount *= 1 - np.clip(face, 0, 1)
    centre, (rx, ry), push = BELLY
    P = V.copy()
    steps = 6
    for _ in range(steps):
        N = vertex_normals(P, quads)
        d = P - centre
        belly = np.exp(-((d[:, 0] / rx) ** 2 + (d[:, 1] / ry) ** 2)) * (P[:, 2] > 0)
        off = (amount + push * belly)[:, None] * N / steps
        P[body_verts] += off[body_verts]
    return P, pairs


# ---------------------------------------------------------------- rig
def fold(bone):
    side = 'l' if bone.endswith('.L') else 'r' if bone.endswith('.R') else None
    base = bone.split('.')[0]
    if base.startswith('upperarm'):
        return [('arm_' + side, 1.0)]
    if base.startswith(('lowerarm', 'wrist', 'metacarpal', 'finger')):
        return [('forearm_' + side, 1.0)]
    if base.startswith('shoulder'):
        return [('torso', 0.5), ('arm_' + side, 0.5)]
    if base.startswith('clavicle'):
        return [('torso', 0.85), ('arm_' + side, 0.15)]
    if base.startswith(('upperleg', 'lowerleg', 'foot', 'toe')):
        return [('leg_' + side, 1.0)]
    if base == 'neck01':
        return [('torso', 0.6), ('head', 0.4)]
    if base == 'neck02':
        return [('torso', 0.25), ('head', 0.75)]
    if base.startswith(('spine', 'pelvis', 'root', 'breast')):
        return [('torso', 1.0)]
    return [('head', 1.0)]


def rig_weights(n, weights, eyes):
    acc = np.zeros((n, len(BONES)))
    for bone, lst in weights.items():
        a = np.array(lst)
        for rb, share in fold(bone):
            np.add.at(acc[:, BONES.index(rb)], a[:, 0].astype(int), a[:, 1] * share)
    acc[eyes] = 0
    acc[eyes, BONES.index('head')] = 1
    return acc


def rot_z(deg):
    a = math.radians(deg)
    return np.array([[math.cos(a), -math.sin(a), 0], [math.sin(a), math.cos(a), 0], [0, 0, 1]])


def swing_arms(P, N, w, J):
    """Swing each A-pose arm down about its shoulder until it hangs ARM_HANG_DEG off vertical."""
    for arm, side, sign in (('arm_r', 'R', 1), ('arm_l', 'L', -1)):
        sh, wr = J['upperarm01.' + side], J['wrist.' + side]
        d = wr - sh
        below = math.degrees(math.atan2(-d[1], abs(d[0])))     # angle under the horizontal
        drop = 90 - ARM_HANG_DEG - below
        R = rot_z(sign * drop)
        a = (w[:, BONES.index(arm)] + w[:, BONES.index('fore' + arm)])[:, None]
        P = P + a * ((P - sh) @ R.T + sh - P)
        N = N + a * (N @ R.T - N)
        for k in list(J):
            if k.endswith('.' + side) and k.split('.')[0].startswith(('upperarm', 'lowerarm', 'wrist', 'metacarpal', 'finger')):
                J[k] = (J[k] - sh) @ R.T + sh
    return P, N / np.maximum(np.linalg.norm(N, axis=1, keepdims=True), 1e-9)


# ---------------------------------------------------------------- texture helpers
def tps_fit(src, dst, reg=1e-6):
    src, dst = np.asarray(src, float), np.asarray(dst, float)
    n = len(src)
    r = np.linalg.norm(src[:, None] - src[None], axis=2)
    K = np.where(r > 0, r * r * np.log(np.maximum(r, 1e-12)), 0) + reg * np.eye(n)
    Pm = np.hstack([np.ones((n, 1)), src])
    A = np.zeros((n + 3, n + 3))
    A[:n, :n], A[:n, n:], A[n:, :n] = K, Pm, Pm.T
    b = np.zeros((n + 3, 2))
    b[:n] = dst
    sol = np.linalg.solve(A, b)

    def apply(p):
        p = np.asarray(p, float)
        r = np.linalg.norm(p[:, None] - src[None], axis=2)
        U = np.where(r > 0, r * r * np.log(np.maximum(r, 1e-12)), 0)
        return U @ sol[:n] + sol[n] + p @ sol[n + 1:]
    return apply


def sample(img, xy):
    """Bilinear sample of an HxWx3 float image at pixel coords (N,2)."""
    h, w = img.shape[:2]
    x = np.clip(xy[:, 0] - 0.5, 0, w - 1.001)
    y = np.clip(xy[:, 1] - 0.5, 0, h - 1.001)
    x0, y0 = x.astype(int), y.astype(int)
    fx, fy = (x - x0)[:, None], (y - y0)[:, None]
    return (img[y0, x0] * (1 - fx) * (1 - fy) + img[y0, x0 + 1] * fx * (1 - fy)
            + img[y0 + 1, x0] * (1 - fx) * fy + img[y0 + 1, x0 + 1] * fx * fy)


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def value_noise(shape, cell, seed):
    rng = np.random.default_rng(seed)
    h, w = shape
    g = rng.random((h // cell + 2, w // cell + 2))
    img = Image.fromarray((g * 255).astype(np.uint8)).resize((w + 2 * cell, h + 2 * cell), Image.BICUBIC)
    return np.asarray(img, float)[cell:cell + h, cell:cell + w] / 255.0 - 0.5


def rasterize(tris, tri_uv, attrs, size):
    """UV-space G-buffer: for each texel covered by a triangle, the barycentric blend of each
    per-vertex attribute array. Returns (covered mask, {name: HxWxC}) ."""
    H = W = size
    covered = np.zeros((H, W), bool)
    out = {k: np.zeros((H, W, v.shape[1])) for k, v in attrs.items()}
    for t, uvt in zip(tris, tri_uv):
        p = np.array([[u * W, (1 - v) * H] for u, v in uvt])
        x0, y0 = np.floor(p.min(0)).astype(int)
        x1, y1 = np.ceil(p.max(0)).astype(int)
        x0, y0, x1, y1 = max(x0, 0), max(y0, 0), min(x1, W - 1), min(y1, H - 1)
        if x1 < x0 or y1 < y0:
            continue
        d = (p[1, 1] - p[2, 1]) * (p[0, 0] - p[2, 0]) + (p[2, 0] - p[1, 0]) * (p[0, 1] - p[2, 1])
        if abs(d) < 1e-12:
            continue
        xs, ys = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        l0 = ((p[1, 1] - p[2, 1]) * (xs - p[2, 0]) + (p[2, 0] - p[1, 0]) * (ys - p[2, 1])) / d
        l1 = ((p[2, 1] - p[0, 1]) * (xs - p[2, 0]) + (p[0, 0] - p[2, 0]) * (ys - p[2, 1])) / d
        l2 = 1 - l0 - l1
        m = (l0 >= -0.01) & (l1 >= -0.01) & (l2 >= -0.01)
        if not m.any():
            continue
        iy, ix = np.nonzero(m)
        gy, gx = iy + y0, ix + x0
        covered[gy, gx] = True
        L = np.stack([l0[m], l1[m], l2[m]], 1)
        for k, v in attrs.items():
            out[k][gy, gx] = L @ v[list(t)]
    return covered, out


def dilate(rgb, mask, steps):
    """Bleeds colour outward from `mask` so mip-mapping and UV seams never show the background."""
    rgb, region = rgb.copy(), mask.copy()
    for _ in range(steps):
        acc = np.zeros_like(rgb)
        cnt = np.zeros(region.shape)
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
            sr = np.roll(region, (dy, dx), (0, 1))
            acc += np.roll(rgb, (dy, dx), (0, 1)) * sr[..., None]
            cnt += sr
        grow = (cnt > 0) & ~region
        if not grow.any():
            break
        rgb[grow] = acc[grow] / cnt[grow][:, None]
        region |= grow
    return rgb


def text_mask(text, width, height):
    """White-on-black text image, as a float mask, sized to fill width x height."""
    font = None
    for path in ('/System/Library/Fonts/Supplemental/Impact.ttf', '/System/Library/Fonts/Supplemental/Arial Black.ttf',
                 'C:/Windows/Fonts/impact.ttf', 'C:/Windows/Fonts/ariblk.ttf'):
        if os.path.exists(path):
            font = ImageFont.truetype(path, 200)
            break
    if font is None:
        font = ImageFont.load_default(size=200)
    box = font.getbbox(text)
    img = Image.new('L', (box[2] - box[0] + 20, box[3] - box[1] + 20), 0)
    ImageDraw.Draw(img).text((10 - box[0], 10 - box[1]), text, fill=255, font=font)
    img = img.resize((width, height), Image.LANCZOS)
    return np.asarray(img, float) / 255.0


# ---------------------------------------------------------------- AO
def ambient_occlusion(P, N, verts, step=0.12, rays=48, reach=3.0):
    """Per-vertex ambient occlusion against a voxelised solid of the (posed) body."""
    lo = P[verts].min(0) - 0.5
    shape = np.ceil((P[verts].max(0) + 0.5 - lo) / step).astype(int) + 1
    occ = np.zeros(shape, bool)
    # densely sample the surface into voxels: the vertices plus edge midpoints is enough at this step
    idx = np.floor((P[verts] - lo) / step).astype(int)
    occ[idx[:, 0], idx[:, 1], idx[:, 2]] = True
    # close the shell and fill it: anything the outside can't reach is solid
    from collections import deque
    shell = occ.copy()
    for ax in range(3):
        shell |= np.roll(occ, 1, ax) | np.roll(occ, -1, ax)
    outside = np.zeros(shape, bool)
    q = deque([(0, 0, 0)])
    outside[0, 0, 0] = True
    while q:
        x, y, z = q.popleft()
        for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            a, b, c = x + dx, y + dy, z + dz
            if 0 <= a < shape[0] and 0 <= b < shape[1] and 0 <= c < shape[2] and not outside[a, b, c] and not shell[a, b, c]:
                outside[a, b, c] = True
                q.append((a, b, c))
    solid = ~outside
    rng = np.random.default_rng(7)
    dirs = rng.normal(size=(rays, 3))
    dirs /= np.linalg.norm(dirs, axis=1, keepdims=True)
    ao = np.ones(len(P))
    pv = P[verts]
    nv = N[verts]
    hits = np.zeros(len(verts))
    total = np.zeros(len(verts))
    for d in dirs:
        cos = nv @ d
        use = cos > 0.05
        o = pv + nv * (step * 2.2)
        blocked = np.zeros(len(verts), bool)
        for t in np.arange(step, reach, step):
            q_ = np.floor((o + d * t - lo) / step).astype(int)
            inb = np.all((q_ >= 0) & (q_ < shape), axis=1)
            qq = np.clip(q_, 0, shape - 1)
            blocked |= inb & solid[qq[:, 0], qq[:, 1], qq[:, 2]]
        hits += (blocked & use) * cos
        total += use * cos
    ao[verts] = 1 - hits / np.maximum(total, 1e-6)
    return ao


# ---------------------------------------------------------------- clothes
NECK_HOLE = 1.05   # radius of the tee's neck hole around the neck axis, decimetres


def collar_height(x, z, J):
    """Height of the crew neck's edge: at the neck base, dipping at the front."""
    return J['neck01'][0][1] - 0.12 - 0.3 * smoothstep(0.2, 1.2, z) * (1 - smoothstep(0.3, 0.9, np.abs(x)))


def clothes_masks(pos, w, J):
    """Soft masks (0..1) for tee, pants and shoes at A-pose positions `pos` with rig weights `w`."""
    x, y, z = pos[:, 0], pos[:, 1], pos[:, 2]
    arm = sum(w[:, BONES.index(b)] for b in ('arm_r', 'arm_l', 'forearm_r', 'forearm_l'))
    head = w[:, BONES.index('head')]
    ankle = (J['foot.L'][0][1] + J['foot.R'][0][1]) / 2 + 0.25
    hem = (J['upperleg01.L'][0][1] + J['upperleg01.R'][0][1]) / 2 + 0.55
    collar = collar_height(x, z, J)
    opening = 1 - smoothstep(NECK_HOLE - 0.05, NECK_HOLE + 0.05, np.hypot(x, z - J['neck01'][0][2]))
    sleeve = np.zeros(len(pos))
    for side in ('L', 'R'):
        sh, el = J['upperarm01.' + side][0], J['lowerarm01.' + side][0]
        axis = el - sh
        t = ((pos - sh) @ axis) / (axis @ axis)
        mine = (np.sign(x) == (1 if side == 'L' else -1))
        sleeve = np.maximum(sleeve, mine * (1 - smoothstep(0.55, 0.62, t)))
    on_body = np.clip(1 - arm, 0, 1)
    tee = np.clip(on_body + arm * sleeve, 0, 1) * smoothstep(hem - 0.04, hem + 0.04, y) \
        * (1 - opening * smoothstep(collar - 0.03, collar + 0.03, y)) * (1 - smoothstep(0.6, 0.8, head))
    shoes = 1 - smoothstep(ankle - 0.03, ankle + 0.03, y)
    pants = (1 - smoothstep(hem - 0.04, hem + 0.04, y)) * np.clip(1 - arm, 0, 1) * (1 - shoes)
    return tee, pants, shoes, ankle


def build(preview=None):
    os.makedirs(CACHE, exist_ok=True)
    V, VT, F = load_obj(mh('3dobjs/base.obj'))
    for rel, wt in TARGETS:
        i, d = load_target(rel)
        V[i] += wt * d
    skel = json.load(open(mh('rigs/default.mhskel')))
    weights = json.load(open(mh('rigs/default_weights.mhw')))['weights']
    jpos = {k: V[v].mean(0) for k, v in skel['joints'].items()}
    J = {b: [jpos[d['head']], jpos[d['tail']]] for b, d in skel['bones'].items()}

    body_f = [f for f in F if f[2] == 'body']
    eye_f = [f for f in F if f[2] in ('helper-l-eye', 'helper-r-eye')]
    quads = np.array([f[0] for f in body_f])
    body_verts = np.unique(quads)
    eye_verts = np.unique([i for f in eye_f for i in f[0]])
    V, pairs = fatten(V, quads, body_verts, weights)
    w = rig_weights(len(V), weights, eye_verts)
    w /= np.maximum(w.sum(1, keepdims=True), 1e-9)

    # cloth: smooth away anatomy under the tee and pants (keeps the silhouette)
    tee, pants, shoes, ankle = clothes_masks(V, w, J)
    covered = np.clip(tee + pants, 0, 1)
    V = smooth_points(V, pairs, covered, 30, 0.5)
    # Taubin keeps the deep pits: iron out the navel and nipples (found by their UV spots)
    vuv = np.zeros((len(V), 2))
    for f in body_f:
        vuv[f[0]] = VT[f[1]]
    spot = np.zeros(len(V))
    for (u, v), r in UNDER_SHIRT:
        spot = np.maximum(spot, 1 - smoothstep(r * 0.6, r, np.linalg.norm(vuv - (u, v), axis=1)))
    spot[eye_verts] = 0
    n = len(V)
    deg = np.maximum(np.bincount(pairs[:, 0], minlength=n), 1)[:, None]
    for _ in range(60):
        acc = np.stack([np.bincount(pairs[:, 0], weights=V[pairs[:, 1], i], minlength=n) for i in range(3)], 1)
        V = V + (spot * 0.5)[:, None] * (acc / deg - V)

    all_quads = np.array([f[0] for f in body_f + eye_f])
    N_rest = vertex_normals(V, all_quads)
    # the rig skins the posed mesh (arms down); the texture is painted at rest (A-pose)
    Jh = {b: J[b][0].copy() for b in J}     # bone heads, swung along with the arms
    P, _ = swing_arms(V.copy(), N_rest.copy(), w, Jh)
    N = vertex_normals(P, all_quads)
    ao = ambient_occlusion(P, N, body_verts)
    ao = smooth_scalar(ao, pairs, len(ao), 3)

    return dict(V=V, P=P, N=N, N_rest=N_rest, VT=VT, body_f=body_f, eye_f=eye_f, w=w, J=J, Jh=Jh,
                quads=quads, body_verts=body_verts, eye_verts=eye_verts, ao=ao, ankle=ankle)


# ---------------------------------------------------------------- paint
def landmark_points(B):
    eyes = {side + '_eye': B['V'][np.unique([i for f in B['eye_f'] if f[2] == 'helper-%s-eye' % side
                                              for i in f[0]])].mean(0) for side in 'rl'}
    V, head = B['V'], B['w'][:, BONES.index('head')]
    bv = B['body_verts']
    out = []
    for k, _ in LANDMARKS:
        if isinstance(k, str):
            out.append(eyes[k][:2])
        elif isinstance(k, tuple):
            _, y, sign = k
            cand = bv[(np.abs(V[bv, 1] - y) < 0.05) & (head[bv] > 0.4)]
            out.append(V[cand[np.argmax(sign * V[cand, 0])]][:2])
        else:
            out.append(V[k][:2])
    return out


EYE_BOXES = {'helper-r-eye': (0.84, 0.90, 0.91, 0.97), 'helper-l-eye': (0.92, 0.90, 0.99, 0.97)}   # u0 v0 u1 v1


def corner_uvs(B):
    """Per-face-corner UVs: MakeHuman's for the body; each eyeball planar-mapped into its own box."""
    V, VT = B['V'], B['VT']
    faces = []
    for f in B['body_f']:
        faces.append((f[0], [tuple(VT[t]) for t in f[1]], 'body'))
    for g in EYE_BOXES:
        # spherical map about the eyeball's centre, front of the eye in the middle of its box; faces
        # across the seam at the back collapse to a point so they never smear over the front
        fs = [f for f in B['eye_f'] if f[2] == g]
        vs = np.unique([i for f in fs for i in f[0]])
        c = V[vs].mean(0)
        u0, v0, u1, v1 = EYE_BOXES[g]
        for f in fs:
            d = V[f[0]] - c
            theta = np.arctan2(d[:, 0], d[:, 2])
            phi = np.arcsin(np.clip(d[:, 1] / np.linalg.norm(d, axis=1), -1, 1))
            if theta.max() - theta.min() > math.pi:
                theta[:] = math.pi
            t = np.stack([0.5 + theta / (2 * math.pi), 0.5 + phi / math.pi], 1)
            faces.append((f[0], [(u0 + a * (u1 - u0), v0 + b * (v1 - v0)) for a, b in t], 'eye'))
    return faces


def eyeballs(pos, nrm, B):
    """Painted eyes: off-white sclera, dark brown iris looking straight ahead, pupil, a catchlight."""
    col = np.zeros((len(pos), 3))
    for side in 'rl':
        vs = np.unique([i for f in B['eye_f'] if f[2] == 'helper-%s-eye' % side for i in f[0]])
        c = B['V'][vs].mean(0)
        r = (B['V'][vs, 0].max() - B['V'][vs, 0].min()) / 2
        mine = (pos[:, 0] > 0) == (c[0] > 0)
        d = np.hypot(pos[:, 0] - c[0], pos[:, 1] - c[1]) / r
        front = nrm[:, 2] > 0
        iris = front & (d < 0.45)
        e = np.where(iris[:, None], np.array([46, 29, 20.0]) * (0.8 + 0.4 * d)[:, None], np.array([196, 182, 166.0]))
        e = np.where((front & (d < 0.2))[:, None], np.array([8, 6, 6.0]), e)
        e = np.where((front & (np.abs(d - 0.45) < 0.05))[:, None], np.array([24, 16, 12.0]), e)
        hl = front & (np.hypot(pos[:, 0] - c[0] + 0.12 * r, pos[:, 1] - c[1] - 0.15 * r) / r < 0.08)
        e = np.where(hl[:, None], np.array([240, 240, 236.0]), e)
        e = np.where(front[:, None], e, np.array([150, 120, 110.0]))
        col[mine] = e[mine]
    return col


# He is clean-shaven, but the photo has a mustache and goatee: inside SHAVE the photo is repainted
# with the skin around it (a harmonic fill from the edge in), except the open mouth (KEEP).
# Photo pixels, (cx, cy, rx, ry) ellipses.
SHAVE = [(683, 352, 62, 22), (683, 448, 86, 54), (628, 400, 30, 52), (738, 400, 30, 52)]
KEEP = (682, 388, 42, 27)


def soft_blur(a, r):
    """Near-Gaussian blur of a float image: three box passes of radius r/2 along each axis."""
    k = max(1, int(r // 2))
    for axis in (0, 1):
        for _ in range(3):
            pad = np.pad(a, [(k + 1, k) if i == axis else (0, 0) for i in range(a.ndim)], mode='edge')
            c = np.cumsum(pad, axis=axis)
            hi = np.take(c, range(2 * k + 1, c.shape[axis]), axis=axis)
            lo = np.take(c, range(0, c.shape[axis] - 2 * k - 1), axis=axis)
            a = (hi - lo) / (2 * k + 1)
    return a


def ellipse_mask(shape, cx, cy, rx, ry, soft=0.25):
    yy, xx = np.mgrid[0:shape[0], 0:shape[1]]
    return 1 - smoothstep(1 - soft, 1.0, ((xx - cx) / rx) ** 2 + ((yy - cy) / ry) ** 2)


def shave(photo):
    x0, y0, x1, y1 = 540, 280, 830, 540           # work on the lower face only
    crop = photo[y0:y1, x0:x1].copy()
    zone = np.zeros(crop.shape[:2])
    for cx, cy, rx, ry in SHAVE:
        zone = np.maximum(zone, ellipse_mask(zone.shape, cx - x0, cy - y0, rx, ry))
    zone *= 1 - ellipse_mask(zone.shape, KEEP[0] - x0, KEEP[1] - y0, KEEP[2], KEEP[3], 0.35)
    unknown = zone > 0.02
    fill = crop.copy()
    fill[unknown] = crop[~unknown].mean(0)
    for _ in range(1500):                          # Jacobi: each unknown pixel -> mean of its neighbours
        avg = (np.roll(fill, 1, 0) + np.roll(fill, -1, 0) + np.roll(fill, 1, 1) + np.roll(fill, -1, 1)) / 4
        fill[unknown] = avg[unknown]
    # keep a trace of the skin's fine texture so the patch doesn't look airbrushed
    detail = crop - np.stack([soft_blur(crop[..., c], 6) for c in range(3)], 2)
    fill = fill + np.clip(detail, -6, 6) * 0.5
    out = photo.copy()
    out[y0:y1, x0:x1] = crop + (fill - crop) * zone[..., None]
    return out


def paint(B, faces):
    # sharpened: the face is ~250 texels across in the atlas and mip-mapping softens it further
    photo = Image.open(fetch('photo.jpg', PHOTO_URL)).convert('RGB')
    photo = np.asarray(photo.filter(ImageFilter.UnsharpMask(radius=3, percent=50, threshold=3)), float)
    photo = shave(photo)
    tris, tri_uv = [], []
    eye_flag = np.zeros((len(B['V']), 1))
    eye_flag[B['eye_verts']] = 1
    for vs, uvs, _ in faces:
        for a, b, c in ((0, 1, 2), (0, 2, 3)):
            tris.append((vs[a], vs[b], vs[c]))
            tri_uv.append((uvs[a], uvs[b], uvs[c]))
    attrs = {'pos': B['V'], 'nrm': B['N_rest'], 'w': B['w'], 'ao': B['ao'][:, None], 'eye': eye_flag}
    cov, G = rasterize(tris, tri_uv, attrs, OUT_TEX)
    iy, ix = np.nonzero(cov)
    pos, nrm, w = G['pos'][iy, ix], G['nrm'][iy, ix], G['w'][iy, ix]
    nrm /= np.maximum(np.linalg.norm(nrm, axis=1, keepdims=True), 1e-9)
    ao, eye = G['ao'][iy, ix, 0], G['eye'][iy, ix, 0] > 0.5
    x, y, z = pos[:, 0], pos[:, 1], pos[:, 2]
    head = w[:, BONES.index('head')]

    # --- the photo, warped onto the face
    tps = tps_fit(landmark_points(B), [p for _, p in LANDMARKS])
    near = (head > 0.2) | eye
    pxy = np.zeros((len(pos), 2))
    pxy[near] = tps(pos[near, :2])
    cx, cy = 685.0, 310.0     # his head in the photo, as an ellipse
    ell = ((pxy[:, 0] - cx) / 118.0) ** 2 + ((pxy[:, 1] - cy) / 175.0) ** 2
    wf = near * smoothstep(0.12, 0.5, nrm[:, 2]) * smoothstep(7.45, 7.65, y) * (1 - smoothstep(0.8, 1.0, ell))
    wf = np.where(eye, near * smoothstep(0.0, 0.3, nrm[:, 2]), wf)
    # where the photo shows his skin (not the wall, not his shirt), feathered
    skinness = smoothstep(28, 50, photo[..., 0] - photo[..., 2]) * smoothstep(40, 80, photo.sum(2) / 3)
    skinness = np.asarray(Image.fromarray((skinness * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(3)),
                          float)[..., None] / 255
    wf = wf * np.where(eye, 1, sample(skinness, np.where(wf[:, None] > 0, pxy, 0))[:, 0])
    face = np.zeros((len(pos), 3))
    face[wf > 0] = sample(photo, pxy[wf > 0])
    core = (wf > 0.95) & ~eye
    # tame the photo's specular highlights: bright, desaturated pixels go grey once balanced, so pull
    # them down and back to his skin hue; then balance the photo's warm light to his tone
    lum = face.mean(1, keepdims=True)
    knee = np.percentile(lum[core], 55)
    tint = np.median(face[core], axis=0)
    hi = smoothstep(knee * 0.95, knee * 1.4, lum)
    lum2 = np.where(lum > knee, knee + (lum - knee) * 0.1, lum)
    face = face * (lum2 / np.maximum(lum, 1e-6))
    face = face + (tint * lum2 / tint.mean() - face) * (0.85 * hi)
    face *= SKIN / tint
    # a little contrast about his skin tone keeps the brows, eyes and nostrils crisp
    ratio = face.mean(1, keepdims=True) / SKIN.mean()
    face *= np.clip(ratio, 0.05, 4) ** 0.2
    # even out the photo's colour (its warm light leaves orange blotches): keep its light and shade,
    # take most of the hue from his skin tone
    face = face + (face.mean(1, keepdims=True) * (SKIN / SKIN.mean()) - face) * 0.6
    skin = SKIN

    # --- skin everywhere else: his tone, pores, occlusion, the shine on his head
    shape = (OUT_TEX, OUT_TEX)
    n_fine = value_noise(shape, 3, 1)[iy, ix]
    n_mid = value_noise(shape, 24, 2)[iy, ix]
    occl = (0.45 + 0.55 * ao)[:, None]
    base = skin * (1 + 0.10 * n_mid + 0.07 * n_fine)[:, None] * occl
    shine = head * smoothstep(9.0, 9.7, y) * smoothstep(0.35, 0.9, nrm[:, 1])
    base = base + (skin * 1.5 - base) * (0.18 * shine)[:, None]   # a soft sheen, not a gloss
    face = face * (0.85 + 0.15 * ao)[:, None]
    col = base + (face - base) * wf[:, None]
    col[eye] = eyeballs(pos[eye], nrm[eye], B)

    # --- clothes
    tee, pants, shoes, ankle = clothes_masks(pos, w, B['J'])
    tee, pants, shoes = (np.where(eye, 0, m) for m in (tee, pants, shoes))
    folds = value_noise(shape, 40, 3)[iy, ix]
    cloth_occ = (0.5 + 0.65 * ao)[:, None]
    tee_col = TEE * (1 + 0.12 * folds + 0.05 * n_fine)[:, None] * cloth_occ
    collar = collar_height(x, z, B['J'])
    hole = np.hypot(x, z - B['J']['neck01'][0][2]) < NECK_HOLE + 0.05
    rib = smoothstep(collar - 0.14, collar - 0.1, y) * (y < collar) * hole
    tee_col = tee_col * (1 + 0.35 * rib)[:, None]
    front = smoothstep(0.1, 0.3, nrm[:, 2])
    for text, top, height, half in (('BADLANDS', 5.95, 0.5, 1.45), ('CHUGS', 5.32, 0.36, 0.75)):
        m = text_mask(text, 600, 120)
        u = (x + half) / (2 * half)
        v = (top - y) / height
        inside = (u >= 0) & (u < 1) & (v >= 0) & (v < 1) & (front > 0)
        ink = np.zeros(len(pos))
        ink[inside] = m[(v[inside] * m.shape[0]).astype(int), (u[inside] * m.shape[1]).astype(int)]
        ink *= front * (0.88 + 0.12 * n_fine)
        tee_col = tee_col + (np.array([232, 230, 224.0]) * (0.75 + 0.3 * ao)[:, None] - tee_col) * ink[:, None]
    pants_col = PANTS * (1 + 0.1 * folds + 0.06 * n_fine)[:, None] * cloth_occ
    sole = y < (ankle - 0.62)
    shoe_col = np.where(sole[:, None], SOLE, SHOE) * (0.6 + 0.5 * ao)[:, None]
    total = tee + pants + shoes
    k = 1 / np.maximum(total, 1)
    col = col * (1 - np.minimum(total, 1))[:, None] + (tee * k)[:, None] * tee_col \
        + (pants * k)[:, None] * pants_col + (shoes * k)[:, None] * shoe_col

    rgb = np.zeros((OUT_TEX, OUT_TEX, 3))
    rgb[iy, ix] = np.clip(col, 0, 255)
    rgb = dilate(rgb, cov, 12)
    filled = rgb.sum(2) > 0
    rgb[~filled] = skin * 0.5
    return Image.fromarray(rgb.astype(np.uint8))


# ---------------------------------------------------------------- export
def to_rig(p, B):
    """MakeHuman decimetres (x = his left, front +Z) -> rig pixels (right at -X, front -Z, feet at 0)."""
    P = B['P'][B['body_verts']]
    scale = HEAD_TOP_PX / (P[:, 1].max() - P[:, 1].min())
    q = (np.asarray(p, float) - [0, P[:, 1].min(), 0]) * scale
    q[..., 2] *= -1
    return q


def export(B, faces):
    key, positions, normals, uvs, joints, wts, indices = {}, [], [], [], [], [], []
    for vs, cuv, _ in faces:
        ids = []
        for v, uv in zip(vs, cuv):
            k = (v, round(uv[0], 5), round(uv[1], 5))
            if k not in key:
                key[k] = len(key)
                positions.append(v)
                uvs.extend([round(float(uv[0]), 4), round(float(uv[1]), 4)])
            ids.append(key[k])
        indices.extend([ids[0], ids[1], ids[2], ids[0], ids[2], ids[3]])
    vi = np.array(positions)
    P = to_rig(B['P'][vi], B)
    N = B['N'][vi] * [1, 1, -1]
    for v in vi:
        order = np.argsort(-B['w'][v])[:2]
        ws = B['w'][v][order]
        ws = ws / ws.sum() if ws.sum() > 0 else np.array([1.0, 0.0])
        joints.extend(int(o) for o in order)
        wts.extend(round(float(a), 3) for a in ws)
    mesh = {
        'credit': 'MakeHuman 1.1 base mesh, targets, skeleton and weights (CC0), shaped and painted by '
                  'tools/gen_chugs_mesh.py',
        'texture': 'papon:textures/entity/badlands_chugs_mesh.png',
        'bones': BONES,
        'positions': P.round(3).flatten().tolist(), 'normals': N.round(3).flatten().tolist(), 'uvs': uvs,
        'indices': indices, 'joints': joints, 'weights': wts,
    }
    with open(os.path.join(ASSETS, 'meshes/badlands_chugs.json'), 'w') as f:
        json.dump(mesh, f, separators=(',', ':'))
    return len(vi), len(indices) // 3


def per_face_uv(cube):
    u, v = cube['uv']
    w, h, d = cube['size']
    return {
        'north': {'uv': [u + d, v + d], 'uv_size': [w, h]},
        'east': {'uv': [u, v + d], 'uv_size': [d, h]},
        'south': {'uv': [u + 2 * d + w, v + d], 'uv_size': [w, h]},
        'west': {'uv': [u + d + w, v + d], 'uv_size': [d, h]},
        'up': {'uv': [u + d, v], 'uv_size': [w, d]},
        'down': {'uv': [u + d + w, v + d], 'uv_size': [w, -d]},
    }


def build_geo(B):
    blocky = json.load(open(os.path.join(ASSETS, 'geo/entity/badlands_chugs.geo.json')))['minecraft:geometry'][0]
    by_name = {b['name']: b for b in blocky['bones']}
    J, Jh = B['J'], B['Jh']
    r3 = lambda p: [round(float(v), 3) for v in p]
    rig = lambda p: to_rig(np.asarray(p)[None], B)[0]
    hip = rig((J['upperleg01.L'][0] + J['upperleg01.R'][0]) / 2)
    bones = [
        {'name': 'root', 'pivot': [0, 0, 0]},
        {'name': 'leg_r', 'parent': 'root', 'pivot': r3(rig(J['upperleg01.R'][0]))},
        {'name': 'leg_l', 'parent': 'root', 'pivot': r3(rig(J['upperleg01.L'][0]))},
        {'name': 'torso', 'parent': 'root', 'pivot': r3([0, hip[1], hip[2]])},
        {'name': 'head', 'parent': 'torso', 'pivot': r3(rig(J['neck02'][0]))},
        {'name': 'arm_r', 'parent': 'torso', 'pivot': r3(rig(Jh['upperarm01.R']))},
        {'name': 'arm_l', 'parent': 'torso', 'pivot': r3(rig(Jh['upperarm01.L']))},
        {'name': 'forearm_r', 'parent': 'arm_r', 'pivot': r3(rig(Jh['lowerarm01.R']))},
        {'name': 'forearm_l', 'parent': 'arm_l', 'pivot': r3(rig(Jh['lowerarm01.L']))},
    ]
    for prop, side, arm in (('jug', 'L', 'forearm_l'), ('taco', 'R', 'forearm_r')):
        hand = rig((Jh['wrist.' + side] + Jh['finger3-1.' + side]) / 2)
        old = by_name[prop]
        off = hand - np.array(old['pivot'], float) + PROP_NUDGE[prop]
        cubes = []
        for c in old['cubes']:   # scaled about the hand: the blocky props are sized for a blocky fist
            o = (np.array(c['origin'], float) + off - hand) * PROP_SCALE + hand
            cubes.append({'origin': r3(o), 'size': r3(np.array(c['size'], float) * PROP_SCALE),
                          'uv': per_face_uv(c)})
        bones.append({'name': prop, 'parent': arm, 'pivot': r3(hand), 'cubes': cubes})
    geo = {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': 'geometry.badlands_chugs_mesh', 'texture_width': 128, 'texture_height': 128,
                        'visible_bounds_width': 3, 'visible_bounds_height': 3.5, 'visible_bounds_offset': [0, 1.5, 0]},
        'bones': bones}]}
    path = os.path.join(ASSETS, 'geo/entity/badlands_chugs_mesh.geo.json')
    with open(path, 'w') as f:
        json.dump(geo, f, indent='\t')
    return path


def main():
    B = build()
    faces = corner_uvs(B)
    tex = paint(B, faces)
    tex.save(os.path.join(ASSETS, 'textures/entity/badlands_chugs_mesh.png'), optimize=True)
    nv, nt = export(B, faces)
    print(build_geo(B))
    print('mesh', nv, 'vertices,', nt, 'triangles')


if __name__ == '__main__':
    main()
