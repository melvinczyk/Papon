"""Semi-photorealistic people for the Taco Truck crowd, built from MakeHuman (CC0) and a photo.

Each celebrity has a small script (gen_chugs_mesh.py, gen_edp_mesh.py) that describes him as a
`Character`; this module turns that into a skinned mesh for client/mesh/MeshCrowdRenderer:

  body     the MakeHuman 1.1 base mesh, morphed with MakeHuman's own targets, then fattened along
           its normals per body part and with belly bulges (`fat`, `bellies`); anatomy under the
           clothes is ironed flat (navel, nipples) and smoothed so it reads as cloth
  rig      MakeHuman's default skeleton and weights folded onto the customer rig (`BONES`), two
           weights per vertex: the six shared bones, elbows (forearm_r/_l), a jaw and the upper
           eyelids (lid_r/_l). The A-pose arms are swung down about the shoulders.
  mouth    MakeHuman's teeth and tongue helpers ride the head and jaw, so the mouth can open
  face     a frontal photo, warped onto the head with a thin-plate spline through matched
           `landmarks` (mesh vertex <-> photo pixel), matte and white-balanced to `skin`
  skin     his tone with pore noise, baked ambient occlusion and a soft sheen on a bald head
  clothes  painted in 3D by the character's `clothes()`
  props    held items and accessories as cubes on the rig, painted into the same atlas (a reserved
           block in its top-left corner), so a character needs no other texture

Downloads what it needs into tools/.cache (gitignored). Writes, under src/main/resources/assets/papon/:
  meshes/<name>.json, textures/entity/<name>_mesh.png (2048x2048), geo/entity/<name>_mesh.geo.json
"""
import json
import math
import os
import shutil
import subprocess
import urllib.request
from collections import deque

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/papon')
CACHE = os.path.join(ROOT, 'tools/.cache')
MH_RAW = 'https://raw.githubusercontent.com/makehumancommunity/makehuman/master/makehuman/data/'
OUT_TEX = 2048
BONES = ['torso', 'head', 'arm_r', 'arm_l', 'leg_r', 'leg_l', 'forearm_r', 'forearm_l', 'jaw', 'lid_r', 'lid_l',
         'shin_r', 'shin_l']
HEAD_BONES = ('head', 'jaw', 'lid_r', 'lid_l')
# Atlas regions MakeHuman's body UVs leave empty (checked against a rasterised coverage map).
# Props pack into these blocks in order (texture pixels, x0 y0 x1 y1).
PROP_BLOCKS = [(4, 4, 352, 190), (360, 2, 1640, 28), (1680, 4, 2044, 300)]
EYE_BOXES = {'helper-r-eye': (0.82, 0.93, 0.90, 0.99), 'helper-l-eye': (0.91, 0.93, 0.99, 0.99)}   # u0 v0 u1 v1
MOUTH_BOXES = {'helper-upper-teeth': (0.82, 0.885, 0.90, 0.92), 'helper-lower-teeth': (0.91, 0.885, 0.99, 0.92),
               'helper-tongue': (0.82, 0.845, 0.90, 0.875)}
UNDER_SHIRT = [((0.381, 0.565), 0.035), ((0.325, 0.702), 0.04), ((0.435, 0.702), 0.04)]   # navel, nipples (UV)


# ---------------------------------------------------------------- math helpers
def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


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


def value_noise(shape, cell, seed):
    rng = np.random.default_rng(seed)
    h, w = shape
    g = rng.random((h // cell + 2, w // cell + 2))
    img = Image.fromarray((g * 255).astype(np.uint8)).resize((w + 2 * cell, h + 2 * cell), Image.BICUBIC)
    return np.asarray(img, float)[cell:cell + h, cell:cell + w] / 255.0 - 0.5


def rot(axis, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    if axis == 'x':
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == 'y':
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def tps_fit(src, dst, reg=1e-6):
    """Thin-plate spline from 2D points `src` to `dst`; returns a function mapping (N,2) arrays."""
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
    """Bilinear sample of an HxWxC float image at pixel coords (N,2)."""
    h, w = img.shape[:2]
    x = np.clip(xy[:, 0] - 0.5, 0, w - 1.001)
    y = np.clip(xy[:, 1] - 0.5, 0, h - 1.001)
    x0, y0 = x.astype(int), y.astype(int)
    fx, fy = (x - x0)[:, None], (y - y0)[:, None]
    return (img[y0, x0] * (1 - fx) * (1 - fy) + img[y0, x0 + 1] * fx * (1 - fy)
            + img[y0 + 1, x0] * (1 - fx) * fy + img[y0 + 1, x0 + 1] * fx * fy)


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


# ---------------------------------------------------------------- mesh helpers
def vertex_normals(P, quads):
    a, b, c, d = (P[quads[:, k]] for k in range(4))
    n = np.cross(c - a, d - b)
    N = np.zeros_like(P)
    for k in range(4):
        np.add.at(N, quads[:, k], n)
    return N / np.maximum(np.linalg.norm(N, axis=1, keepdims=True), 1e-9)


def neighbours(quads):
    pairs = np.concatenate([quads[:, [k, (k + 1) % 4]] for k in range(4)])
    pairs = np.concatenate([pairs, pairs[:, ::-1]])
    return np.unique(pairs, axis=0)


def smooth_scalar(f, pairs, iters):
    n = len(f)
    deg = np.bincount(pairs[:, 0], minlength=n).astype(float)
    for _ in range(iters):
        acc = np.bincount(pairs[:, 0], weights=f[pairs[:, 1]], minlength=n)
        f = np.where(deg > 0, 0.5 * f + 0.5 * acc / np.maximum(deg, 1), f)
    return f


def laplacian(P, pairs):
    n = len(P)
    deg = np.maximum(np.bincount(pairs[:, 0], minlength=n), 1)[:, None]
    acc = np.stack([np.bincount(pairs[:, 0], weights=P[pairs[:, 1], i], minlength=n) for i in range(3)], 1)
    return acc / deg - P


def taubin(P, pairs, mask, iters, k=0.5):
    """Taubin smoothing (shrink, then inflate) of the masked vertices: removes detail, keeps volume."""
    for step in [k, -k * 1.06] * iters:
        P = P + (mask * step)[:, None] * laplacian(P, pairs)
    return P


def part_of(bone):
    for prefix, part in (('upperarm', 'uarm'), ('shoulder', 'uarm'), ('lowerarm', 'larm'), ('upperleg', 'uleg'),
                         ('lowerleg', 'lleg'), ('neck', 'neck'), ('spine', 'torso'), ('pelvis', 'torso'),
                         ('root', 'torso'), ('breast', 'torso'), ('clavicle', 'torso')):
        if bone.startswith(prefix):
            return part
    return None


def fold(bone):
    """MakeHuman bone -> [(rig bone, share)]."""
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
    if base.startswith('upperleg'):
        return [('leg_' + side, 1.0)]
    if base.startswith(('lowerleg', 'foot', 'toe')):
        return [('shin_' + side, 1.0)]
    if base == 'neck01':
        return [('torso', 0.6), ('head', 0.4)]
    if base == 'neck02':
        return [('torso', 0.25), ('head', 0.75)]
    if base.startswith(('spine', 'pelvis', 'root', 'breast')):
        return [('torso', 1.0)]
    if base in ('jaw', 'special04', 'oris01', 'oris07') or base.startswith('tongue'):
        return [('jaw', 1.0)]
    if base == 'orbicularis03':
        return [('lid_' + side, 1.0)]
    return [('head', 1.0)]


# ---------------------------------------------------------------- props
class Box:
    """A prop cube in rig pixels, painted by `paint(face, u, v) -> rgb` with u, v in cube pixels
    across that face (u to the right and v down, as the face is seen from outside)."""

    def __init__(self, origin, size, paint, rotation=None, pivot=None):
        self.origin, self.size, self.paint = np.array(origin, float), np.array(size, float), paint
        self.rotation, self.pivot = rotation, pivot


class Prop:
    """A bone of cubes. `anchor(B)` gives its pivot in rig pixels; boxes are relative to it (a list,
    or a function of the built body returning one)."""

    def __init__(self, name, parent, anchor, boxes):
        self.name, self.parent, self.anchor, self.boxes = name, parent, anchor, boxes


TEXELS = 8   # atlas texels per prop pixel
BLEND = 20   # texels: detail finer than this comes from the first photo only


def paint_props(props, B, rgb):
    """Packs every prop face into PROP_BLOCKS, paints it, and returns the geo bones."""
    blocks = list(PROP_BLOCKS)
    x0, y0, x1, y1 = blocks.pop(0)
    cx, cy, row = x0, y0, 0
    bones = []
    for prop in props:
        pivot = prop.anchor(B)
        cubes = []
        for box in (prop.boxes(B) if callable(prop.boxes) else prop.boxes):
            w, h, d = box.size
            faces = {}
            # face -> (width, height) in cube pixels, as seen from outside
            dims = {'north': (w, h), 'south': (w, h), 'east': (d, h), 'west': (d, h), 'up': (w, d), 'down': (w, d)}
            for face, (fw, fh) in dims.items():
                tw, th = max(1, int(round(fw * TEXELS))), max(1, int(round(fh * TEXELS)))
                if cx + tw > x1:
                    cx, cy, row = x0, cy + row + 2, 0
                while cy + th > y1 or cx + tw > x1:
                    if not blocks:
                        raise SystemExit('props do not fit in PROP_BLOCKS')
                    x0, y0, x1, y1 = blocks.pop(0)
                    cx, cy, row = x0, y0, 0
                us = (np.arange(tw) + 0.5) / TEXELS
                vs = (np.arange(th) + 0.5) / TEXELS
                U, Vv = np.meshgrid(us, vs)
                rgb[cy:cy + th, cx:cx + tw] = np.clip(box.paint(face, U, Vv), 0, 255)
                uv, size = [cx, cy], [tw, th]
                if face == 'down':     # bedrock stores the bottom face flipped vertically
                    uv, size = [cx, cy + th], [tw, -th]
                faces[face] = {'uv': uv, 'uv_size': size}
                cx += tw + 2
                row = max(row, th)
            cube = {'origin': r3(pivot + box.origin), 'size': r3(box.size), 'uv': faces}
            if box.rotation is not None:
                cube['rotation'] = r3(box.rotation)
                cube['pivot'] = r3(pivot + (box.pivot if box.pivot is not None else box.origin + box.size / 2))
            cubes.append(cube)
        bones.append({'name': prop.name, 'parent': prop.parent, 'pivot': r3(pivot), 'cubes': cubes})
    return bones


def r3(p):
    return [round(float(v), 3) for v in p]


# ---------------------------------------------------------------- the character
class Photo:
    """A reference photo of the face: where to get it, matched `landmarks` (as Character.landmarks),
    his head's ellipse in it, and how much it counts toward the blended skin tone and shading."""

    def __init__(self, file, url, landmarks, head_ellipse, weight=1.0):
        self.file, self.url, self.landmarks, self.head_ellipse, self.weight = file, url, landmarks, head_ellipse, weight


class Character:
    name = None                 # entity id: writes meshes/<name>.json, <name>_mesh png and geo
    head_top_px = 33.0
    targets = []                # [(MakeHuman target, weight)]
    fat = {}                    # decimetres along the normal, per part (torso uarm larm uleg lleg neck)
    bellies = []                # [(t, z, (rx, ry), push, sag)]: gaussian bulges on the front at height t between
                                # the hips (0) and the neck (1), z forward; sag = how far they hang down
    arm_hang_deg = 16.0         # arms hang this far out from vertical
    finger_curl = (22.0, 34.0, 22.0)   # relaxed bend at each finger joint, knuckle first (degrees)
    trousers = (0.0, 0.0)       # how far trouser legs stand off the thigh and shin (decimetres)
    shoe_puff = 0.14            # how much bigger than the foot a shoe is (decimetres)
    shoulder_slim = 0.6         # share of the fat kept off the tops of the shoulders (shows the neck)
    decimate = 0.7              # Blender keeps this share of the body's faces (head and hands keep all)
    belly_smooth = 0.0          # smoothing over the fattened trunk (0..1)
    photo_url = None            # one photo: photo_url, landmarks, head_ellipse; or several in `photos`
    landmarks = []              # [(vertex | 'r_eye' | 'l_eye' | ('side', y, sign), (photo x, photo y))]
    head_ellipse = None         # his head in the photo: (cx, cy, rx, ry)
    photos = []                 # [Photo]: the first gives the face its detail, all of them its tone
    face_floor = 7.5            # the photo stops fading out below this height (MakeHuman y)
    skin = np.array([100, 66, 50], float)
    iris = np.array([46, 29, 20], float)
    props = []                  # [Prop]

    # ---- hooks
    def photo_fix(self, photo):
        """Edit the photo (pixel array) before it is projected."""
        return photo

    def clothes(self, T):
        """[(mask, colour)] per texel; T is the texel context (see paint)."""
        return []

    def covered(self, pos, w, J):
        """0..1 per vertex: how much is under clothes (ironed smooth)."""
        return np.zeros(len(pos))

    # ---- build
    def build(self):
        V, VT, F = load_obj(mh('3dobjs/base.obj'))
        for rel, wt in self.targets:
            i, d = load_target(rel)
            V[i] += wt * d
        skel = json.load(open(mh('rigs/default.mhskel')))
        weights = json.load(open(mh('rigs/default_weights.mhw')))['weights']
        jpos = {k: V[v].mean(0) for k, v in skel['joints'].items()}
        J = {b: [jpos[d['head']], jpos[d['tail']]] for b, d in skel['bones'].items()}

        groups = {}
        for f in F:
            groups.setdefault(f[2], []).append(f)
        body_f = groups['body']
        eye_f = groups['helper-l-eye'] + groups['helper-r-eye']
        mouth_f = {g: groups[g] for g in MOUTH_BOXES}
        quads = np.array([f[0] for f in body_f])
        body_verts = np.unique(quads)
        eye_verts = np.unique([i for f in eye_f for i in f[0]])

        V, pairs = self.fatten(V, quads, body_verts, weights, J)
        V = self.relax_hands(V, weights, J)
        w = np.zeros((len(V), len(BONES)))
        for bone, lst in weights.items():
            a = np.array(lst)
            for rb, share in fold(bone):
                np.add.at(w[:, BONES.index(rb)], a[:, 0].astype(int), a[:, 1] * share)
        w[eye_verts] = 0
        w[eye_verts, BONES.index('head')] = 1
        for g, bone in (('helper-upper-teeth', 'head'), ('helper-lower-teeth', 'jaw'), ('helper-tongue', 'jaw')):
            vs = np.unique([i for f in mouth_f[g] for i in f[0]])
            w[vs] = 0
            w[vs, BONES.index(bone)] = 1
        w /= np.maximum(w.sum(1, keepdims=True), 1e-9)

        # cloth: smooth away anatomy under the clothes, then iron the navel and nipples flat
        cover = self.covered(V, w, J)
        V = self.dress(V, w, J, quads, pairs, body_verts, cover)
        V = taubin(V, pairs, cover, 30)
        vuv = np.zeros((len(V), 2))
        for f in body_f:
            vuv[f[0]] = VT[f[1]]
        spot = np.zeros(len(V))
        for (u, v), r in UNDER_SHIRT:
            spot = np.maximum(spot, 1 - smoothstep(r * 0.6, r, np.linalg.norm(vuv - (u, v), axis=1)))
        spot[np.setdiff1d(np.arange(len(V)), body_verts)] = 0
        for _ in range(60):
            V = V + (spot * 0.5)[:, None] * laplacian(V, pairs)
        # and the crotch under trousers: no anatomy showing through
        hip_y = (J['upperleg01.L'][0][1] + J['upperleg01.R'][0][1]) / 2
        crotch = np.zeros(len(V))
        crotch[body_verts] = (1 - smoothstep(0.35, 0.7, np.abs(V[body_verts, 0]))) \
            * (1 - smoothstep(0.5, 0.9, np.abs(V[body_verts, 1] - hip_y + 0.3))) * smoothstep(-0.2, 0.3, V[body_verts, 2])
        crotch *= cover
        V = taubin(V, pairs, crotch, 40)

        mouth_quads = [f[0] for g in MOUTH_BOXES for f in mouth_f[g]]
        all_quads = np.array([f[0] for f in body_f + eye_f] + mouth_quads)
        N_rest = vertex_normals(V, all_quads)
        Jh = {b: J[b][0].copy() for b in J}     # bone heads, swung along with the arms
        P = self.swing_arms(V.copy(), w, Jh)
        N = vertex_normals(P, all_quads)
        ao = ambient_occlusion(P, N, body_verts)
        ao = smooth_scalar(ao, pairs, 3)
        B = dict(V=V, P=P, N=N, N_rest=N_rest, VT=VT, body_f=body_f, eye_f=eye_f, mouth_f=mouth_f, w=w, J=J,
                 Jh=Jh, quads=quads, body_verts=body_verts, eye_verts=eye_verts, ao=ao)
        lo = P[body_verts, 1].min()
        B['scale'] = self.head_top_px / (P[body_verts, 1].max() - lo)
        B['floor'] = lo
        return B

    def fatten(self, V, quads, body_verts, weights, J):
        amount = np.zeros(len(V))
        face = np.zeros(len(V))
        trunk = np.zeros(len(V))      # where belly bulges may go: the torso, not the head or limbs
        for bone, lst in weights.items():
            a = np.array(lst)
            part = part_of(bone)
            if part in ('torso', 'uleg'):
                np.add.at(trunk, a[:, 0].astype(int), a[:, 1])
            if part:
                np.add.at(amount, a[:, 0].astype(int), a[:, 1] * self.fat.get(part, 0))
            elif not bone.startswith(('finger', 'wrist', 'metacarpal', 'foot', 'toe')):
                np.add.at(face, a[:, 0].astype(int), a[:, 1])
        pairs = neighbours(quads)
        amount = smooth_scalar(amount, pairs, 20)
        amount *= 1 - np.clip(face, 0, 1)     # never on the face: the eyelids would close
        trunk = smooth_scalar(np.clip(trunk, 0, 1), pairs, 6)
        P = V.copy()
        steps = 8
        neck_y = J['neck01'][0][1]
        for _ in range(steps):
            N = vertex_normals(P, quads)
            # less on the tops of the shoulders, or they swallow the neck
            shoulders = smoothstep(neck_y - 1.6, neck_y - 0.6, P[:, 1]) * smoothstep(0.2, 0.8, N[:, 1])
            push = (amount * (1 - self.shoulder_slim * shoulders))[:, None] * N
            hip = (J['upperleg01.L'][0][1] + J['upperleg01.R'][0][1]) / 2
            neck = J['neck01'][0][1]
            for t, cz, (rx, ry), k, sag in self.bellies:
                d = P - np.array([0, hip + t * (neck - hip), cz])
                g = (np.exp(-((d[:, 0] / rx) ** 2 + (d[:, 1] / ry) ** 2)) * smoothstep(-0.3, 0.6, P[:, 2]))[:, None]
                push = push + k * (g * trunk[:, None]) * (N + np.array([0, -sag, 0]))
            P[body_verts] += push[body_verts] / steps
        if self.belly_smooth:     # big bulges leave creases and ledges where they overlap: iron them out
            P = taubin(P, pairs, trunk * smoothstep(-0.3, 0.6, P[:, 2]) * self.belly_smooth, 25)
        return P, pairs

    def relax_hands(self, V, weights, J):
        """MakeHuman's hands are splayed flat; real hands at rest curl a little. Bends each finger
        at its three joints (distal first, so the proximal bend carries the rest along) about the
        line across the knuckles, and tucks the thumb in a touch."""
        V = V.copy()
        wt = {b: np.array(l) for b, l in weights.items()}

        def bend(bones, pivot, axis, deg):
            m = np.zeros(len(V))
            for b in bones:
                if b in wt:
                    np.add.at(m, wt[b][:, 0].astype(int), wt[b][:, 1])
            m = np.clip(m, 0, 1)
            a = axis / np.linalg.norm(axis)
            t = math.radians(deg)
            K = np.array([[0, -a[2], a[1]], [a[2], 0, -a[0]], [-a[1], a[0], 0]])
            R = np.eye(3) + math.sin(t) * K + (1 - math.cos(t)) * K @ K
            return V + m[:, None] * ((V - pivot) @ R.T + pivot - V)

        for side, sign in (('L', 1), ('R', -1)):
            across = J['finger5-1.' + side][0] - J['finger2-1.' + side][0]
            for f in range(2, 6):
                for k, deg in ((3, self.finger_curl[2]), (2, self.finger_curl[1]), (1, self.finger_curl[0])):
                    bones = ['finger%d-%d.%s' % (f, j, side) for j in range(k, 4)]
                    V = bend(bones, J[bones[0]][0], across, sign * deg)
            thumb = ['finger1-%d.%s' % (j, side) for j in (2, 3)]
            V = bend(thumb, J[thumb[0]][0], across, sign * self.finger_curl[0] * 0.5)
        return V

    def dress(self, V, w, J, quads, pairs, body_verts, cover):
        """Real clothes stand off the body: trouser legs hang loose (`trousers`: decimetres off the
        thigh and shin), and the feet become shoes (each foot, toes and all, pulled onto a rounded
        box round it: a sneaker's shape)."""
        leg = w[:, BONES.index('leg_r')] + w[:, BONES.index('leg_l')]
        shin = w[:, BONES.index('shin_r')] + w[:, BONES.index('shin_l')]
        ankle = (J['foot.L'][0][1] + J['foot.R'][0][1]) / 2
        foot = np.zeros(len(V))
        foot[body_verts] = smoothstep(ankle + 0.35, ankle + 0.05, V[body_verts, 1])
        P = V.copy()
        up, lo = self.trousers
        loose = np.zeros(len(V))
        loose[body_verts] = (cover * (leg * up + shin * lo))[body_verts] * (1 - foot[body_verts])
        loose = smooth_scalar(loose, pairs, 6)
        for _ in range(4):
            N = vertex_normals(P, quads)
            P[body_verts] += (loose[:, None] * N)[body_verts] / 4
        floor = P[body_verts, 1].min()
        for sign in (1, -1):
            mine = body_verts[(np.sign(P[body_verts, 0]) == sign) & (foot[body_verts] > 0)]
            core = mine[foot[mine] > 0.95]
            lo3, hi3 = P[core].min(0), P[core].max(0)
            lo3, hi3 = lo3 - [self.shoe_puff, 0, self.shoe_puff], hi3 + [self.shoe_puff, 0, self.shoe_puff * 1.2]
            lo3[1], hi3[1] = floor, ankle + 0.1
            c, r = (lo3 + hi3) / 2, (hi3 - lo3) / 2
            d = (P[mine] - c) / r
            n = (np.abs(d[:, 0]) ** 3 + np.abs(d[:, 2]) ** 3) ** (4 / 3) + np.abs(d[:, 1]) ** 4
            on = c + (P[mine] - c) / np.maximum(n, 1e-9)[:, None] ** 0.25
            P[mine] = P[mine] + (on - P[mine]) * foot[mine][:, None]
        return taubin(P, pairs, foot * 0.5, 6)

    def swing_arms(self, P, w, J):
        """Swings each A-pose arm down about its shoulder until it hangs arm_hang_deg off vertical."""
        for arm, side, sign in (('arm_r', 'R', 1), ('arm_l', 'L', -1)):
            sh, wr = J['upperarm01.' + side], J['wrist.' + side]
            d = wr - sh
            below = math.degrees(math.atan2(-d[1], abs(d[0])))
            R = rot('z', sign * (90 - self.arm_hang_deg - below))
            a = (w[:, BONES.index(arm)] + w[:, BONES.index('fore' + arm)])[:, None]
            P = P + a * ((P - sh) @ R.T + sh - P)
            for k in list(J):
                if k.endswith('.' + side) and k.split('.')[0].startswith(
                        ('upperarm', 'lowerarm', 'wrist', 'metacarpal', 'finger')):
                    J[k] = (J[k] - sh) @ R.T + sh
        return P

    # ---- rig space
    def to_rig(self, p, B):
        """MakeHuman decimetres (x = his left, front +Z) -> rig pixels (right at -X, front -Z, feet at 0)."""
        q = (np.asarray(p, float) - [0, B['floor'], 0]) * B['scale']
        q[..., 2] *= -1
        return q

    def eye(self, B, side):
        vs = np.unique([i for f in B['eye_f'] if f[2] == 'helper-%s-eye' % side for i in f[0]])
        return B['V'][vs].mean(0), (B['V'][vs, 0].max() - B['V'][vs, 0].min()) / 2

    def all_photos(self):
        return self.photos or [Photo('photo.jpg', self.photo_url, self.landmarks, self.head_ellipse)]

    def landmark_points(self, B, landmarks=None):
        V, head = B['V'], B['w'][:, BONES.index('head')]
        bv = B['body_verts']
        out = []
        for k, _ in (self.landmarks if landmarks is None else landmarks):
            if isinstance(k, str):
                out.append(self.eye(B, k[0])[0][:2])
            elif isinstance(k, tuple):
                _, y, sign = k
                cand = bv[(np.abs(V[bv, 1] - y) < 0.05) & (head[bv] > 0.85)]
                out.append(V[cand[np.argmax(sign * V[cand, 0])]][:2])
            else:
                out.append(V[k][:2])
        return out

    # ---- uv
    def corner_uvs(self, B):
        """Per-face-corner UVs: MakeHuman's for the body; eyeballs, teeth and tongue mapped into boxes."""
        V, VT = B['V'], B['VT']
        faces = [(f[0], [tuple(VT[t]) for t in f[1]], 'body') for f in B['body_f']]
        for g, (u0, v0, u1, v1) in EYE_BOXES.items():
            # spherical about the eyeball's centre, its front in the middle of the box; faces across
            # the seam at the back collapse to a point so they never smear over the front
            fs = [f for f in B['eye_f'] if f[2] == g]
            c = V[np.unique([i for f in fs for i in f[0]])].mean(0)
            for f in fs:
                d = V[f[0]] - c
                theta = np.arctan2(d[:, 0], d[:, 2])
                phi = np.arcsin(np.clip(d[:, 1] / np.linalg.norm(d, axis=1), -1, 1))
                if theta.max() - theta.min() > math.pi:
                    theta[:] = math.pi
                t = np.stack([0.5 + theta / (2 * math.pi), 0.5 + phi / math.pi], 1)
                faces.append((f[0], [(u0 + a * (u1 - u0), v0 + b * (v1 - v0)) for a, b in t], 'eye'))
        for g, (u0, v0, u1, v1) in MOUTH_BOXES.items():
            fs = B['mouth_f'][g]
            vs = np.unique([i for f in fs for i in f[0]])
            lo, hi = V[vs][:, [0, 2]].min(0), V[vs][:, [0, 2]].max(0)
            for f in fs:      # seen from above: teeth and tongue are mostly horizontal
                t = (V[f[0]][:, [0, 2]] - lo) / np.maximum(hi - lo, 1e-6)
                faces.append((f[0], [(u0 + a * (u1 - u0), v0 + b * (v1 - v0)) for a, b in t], g))
        return faces

    # ---- paint
    def project(self, photo, T, B, primary):
        """One photo warped onto the face: (colour, weight) per texel, matted to his skin tone."""
        img = Image.open(fetch(self.name + '/' + photo.file, photo.url)).convert('RGB')
        img = np.asarray(img.filter(ImageFilter.UnsharpMask(radius=3, percent=50, threshold=3)), float)
        img = self.photo_fix(img) if primary else img
        tps = tps_fit(self.landmark_points(B, photo.landmarks), [p for _, p in photo.landmarks])
        head = sum(T.bone(b) for b in HEAD_BONES)
        near = (head > 0.2) | (T.kind == 1)
        pxy = np.zeros((len(T.pos), 2))
        pxy[near] = tps(T.pos[near, :2])
        cx, cy, rx, ry = photo.head_ellipse
        ell = ((pxy[:, 0] - cx) / rx) ** 2 + ((pxy[:, 1] - cy) / ry) ** 2
        # facing the camera fully; fading out where the photo sees the surface edge-on
        wf = near * smoothstep(-0.05, 0.35, T.nrm[:, 2]) * smoothstep(self.face_floor - 0.1, self.face_floor + 0.1, T.y) \
            * (1 - smoothstep(0.85, 1.0, ell)) * (T.kind == 0)
        skinness = smoothstep(18, 40, img[..., 0] - img[..., 2]) * smoothstep(25, 60, img.sum(2) / 3)
        skinness = soft_blur(skinness, 4)[..., None]
        wf = wf * sample(skinness, np.where(wf[:, None] > 0, pxy, 0))[:, 0]
        face = np.zeros((len(T.pos), 3))
        face[wf > 0] = sample(img, pxy[wf > 0])
        return self.matte(face, wf > 0.95), wf

    def paint(self, B, faces):
        tris, tri_uv, kinds = [], [], []
        kind_id = {'body': 0, 'eye': 1, 'helper-upper-teeth': 2, 'helper-lower-teeth': 2, 'helper-tongue': 3}
        for vs, uvs, kind in faces:
            for a, b, c in ((0, 1, 2), (0, 2, 3)):
                tris.append((vs[a], vs[b], vs[c]))
                tri_uv.append((uvs[a], uvs[b], uvs[c]))
                kinds.append(kind_id[kind])
        kind_v = np.zeros((len(B['V']), 1))
        kind_v[B['eye_verts']] = 1
        for g in MOUTH_BOXES:
            kind_v[np.unique([i for f in B['mouth_f'][g] for i in f[0]])] = kind_id[g]
        attrs = {'pos': B['V'], 'nrm': B['N_rest'], 'w': B['w'], 'ao': B['ao'][:, None], 'kind': kind_v}
        cov, G = rasterize(tris, tri_uv, attrs, OUT_TEX)
        iy, ix = np.nonzero(cov)
        T = Texels(self, B, G, iy, ix)
        eye = T.kind == 1

        # --- the photos, warped onto the face. Detail comes from the first; the broad tone and
        # shading (anything wider than ~BLEND texels) is the weighted average of all of them, so no
        # one photo's lighting or lens decides how his face is shaded.
        photos = self.all_photos()
        projected = [self.project(p, T, B, i == 0) for i, p in enumerate(photos)]
        face, wf = projected[0]
        if len(projected) > 1:
            def low(colour, weight):
                img = np.zeros((OUT_TEX, OUT_TEX, 4))
                img[iy, ix, :3] = colour * weight[:, None]
                img[iy, ix, 3] = weight
                img = np.stack([soft_blur(img[..., c], BLEND) for c in range(4)], 2)[iy, ix]
                return img[:, :3] / np.maximum(img[:, 3:], 1e-6), img[:, 3]
            lows = [(low(f, w), p.weight) for (f, w), p in zip(projected, photos)]
            num = sum(l[0] * (l[1] * k)[:, None] for l, k in lows)
            den = sum(l[1] * k for l, k in lows)
            mix = num / np.maximum(den, 1e-6)[:, None]
            face = face + (mix - lows[0][0][0]) * (wf > 0)[:, None]

        # --- skin everywhere else: the photo's own tone carried smoothly out past its edge (so there
        # is no mask line), his tone further away; pores, occlusion, a soft sheen on the scalp
        img = np.zeros((OUT_TEX, OUT_TEX, 4))
        img[iy, ix, :3] = face * (wf * (0.85 + 0.15 * T.ao) ** -1)[:, None]
        img[iy, ix, 3] = wf
        img = np.stack([soft_blur(img[..., c], 48) for c in range(4)], 2)[iy, ix]
        reach = smoothstep(0.0, 0.08, img[:, 3])
        tone = self.skin + (img[:, :3] / np.maximum(img[:, 3:], 1e-6) - self.skin) * reach[:, None]
        base = tone * (1 + 0.10 * T.n_mid + 0.07 * T.n_fine)[:, None] * (0.45 + 0.55 * T.ao)[:, None]
        shine = T.bone('head') * smoothstep(9.0, 9.7, T.y) * smoothstep(0.35, 0.9, T.nrm[:, 1])
        base = base + (self.skin * 1.5 - base) * (0.18 * shine)[:, None]
        face = face * (0.85 + 0.15 * T.ao)[:, None]
        col = base + (face - base) * wf[:, None]
        # inside the mouth: dark, wet, red
        mouth = self.mouth_inside(T, B)
        col = col + (np.array([58, 22, 24.0]) * (0.6 + 0.4 * T.ao)[:, None] - col) * mouth[:, None]
        col[eye] = self.eyeballs(T.pos[eye], T.nrm[eye], B)
        teeth, tongue = T.kind == 2, T.kind == 3
        col[teeth] = np.array([222, 212, 192.0]) * (0.85 + 0.15 * T.n_fine[teeth])[:, None]
        col[tongue] = np.array([150, 66, 70.0]) * (0.8 + 0.2 * T.n_mid[tongue])[:, None]

        # --- clothes, in order (later ones on top)
        for mask, c in self.clothes(T):
            mask = np.where(T.kind == 0, mask, 0)
            col = col + (c - col) * mask[:, None]

        rgb = np.zeros((OUT_TEX, OUT_TEX, 3))
        rgb[iy, ix] = np.clip(col, 0, 255)
        rgb = dilate(rgb, cov, 12)
        rgb[rgb.sum(2) == 0] = self.skin * 0.5
        return rgb

    def matte(self, face, core):
        """Flattens the photo's highlights, white-balances it to his skin and evens its hue."""
        if core.sum() < 50:
            return face
        lum = face.mean(1, keepdims=True)
        knee = np.percentile(lum[core], 55)
        tint = np.median(face[core], axis=0)
        hi = smoothstep(knee * 0.95, knee * 1.4, lum)
        lum2 = np.where(lum > knee, knee + (lum - knee) * 0.1, lum)
        face = face * (lum2 / np.maximum(lum, 1e-6))
        face = face + (tint * lum2 / tint.mean() - face) * (0.85 * hi)
        face *= self.skin / tint
        ratio = face.mean(1, keepdims=True) / self.skin.mean()
        face *= np.clip(ratio, 0.05, 4) ** 0.2
        return face + (face.mean(1, keepdims=True) * (self.skin / self.skin.mean()) - face) * 0.6

    def mouth_inside(self, T, B):
        """Body texels behind the lips (the mouth bag): only seen when the jaw opens."""
        lip = B['V'][466]        # the middle of the mouth, on the lip line
        d = T.pos - lip
        return (T.kind == 0) * smoothstep(-0.1, -0.22, d[:, 2]) * smoothstep(-0.75, -0.55, d[:, 2]) \
            * (1 - smoothstep(0.2, 0.3, np.abs(d[:, 0]))) \
            * (1 - smoothstep(0.2, 0.32, np.abs(d[:, 1]))) * smoothstep(0.3, -0.1, T.nrm[:, 2]) \
            * ((T.bone('head') + T.bone('jaw')) > 0.5)

    def eyeballs(self, pos, nrm, B):
        """Painted eyes: off-white sclera, iris looking straight ahead, pupil, a catchlight."""
        col = np.zeros((len(pos), 3))
        for side in 'rl':
            c, r = self.eye(B, side)
            mine = (pos[:, 0] > 0) == (c[0] > 0)
            d = np.hypot(pos[:, 0] - c[0], pos[:, 1] - c[1]) / r
            front = nrm[:, 2] > 0
            e = np.where((front & (d < 0.45))[:, None], self.iris * (0.8 + 0.4 * d)[:, None], np.array([180, 166, 150.0]))
            e = np.where((front & (d < 0.2))[:, None], np.array([8, 6, 6.0]), e)
            e = np.where((front & (np.abs(d - 0.45) < 0.05))[:, None], self.iris * 0.5, e)
            hl = front & (np.hypot(pos[:, 0] - c[0] + 0.12 * r, pos[:, 1] - c[1] - 0.15 * r) / r < 0.08)
            e = np.where(hl[:, None], np.array([235, 235, 230.0]), e)
            e = np.where(front[:, None], e, np.array([140, 110, 100.0]))
            col[mine] = e[mine]
        return col

    # ---- export
    def optimize(self, B, faces):
        """Blender pass (tools/blender_decimate.py): the body down to `decimate` of its faces; the
        head, hands, eyes and mouth keep every vertex. Returns (P, w, polygons) with polygons as
        (vertex ids, corner uvs). Without Blender, the full mesh."""
        P, w = B['P'], B['w']
        polys = [(list(vs), list(uv)) for vs, uv, _ in faces]
        blender = os.environ.get('BLENDER') or next((b for b in (
            '/Applications/Blender.app/Contents/MacOS/Blender', 'C:/Program Files/Blender Foundation/Blender 5.2/blender.exe')
            if os.path.exists(b)), None) or shutil.which('blender')
        if not blender or self.decimate >= 1:
            print('Blender not found: mesh not decimated')
            return P, w, polys
        protect = np.clip(sum(w[:, BONES.index(b)] for b in HEAD_BONES) * 3, 0, 1)
        Jh = B['Jh']
        for side in 'LR':
            a, c = Jh['lowerarm01.' + side], Jh['wrist.' + side]
            t = ((P - a) @ (c - a)) / ((c - a) @ (c - a))
            protect = np.maximum(protect, 0.85 * smoothstep(0.75, 0.9, t) * (w[:, BONES.index('forearm_' + side.lower())] > 0.3))
        # the front of the torso carries printed text and the belly's silhouette: decimate it gently
        protect = np.maximum(protect, 0.5 * w[:, BONES.index('torso')] * smoothstep(0.2, 0.6, B['N'][:, 2]))
        protect[np.setdiff1d(np.arange(len(P)), B['body_verts'])] = 1
        tmp = os.path.join(CACHE, self.name)
        os.makedirs(tmp, exist_ok=True)
        src, dst = os.path.join(tmp, 'mesh_in.npz'), os.path.join(tmp, 'mesh_out.npz')
        np.savez(src, P=P, W=w, protect=protect, faces=np.array([vs for vs, _ in polys]),
                 uvs=np.array([uv for _, uv in polys], float))
        out = subprocess.run([blender, '-b', '--factory-startup', '--python', os.path.join(ROOT, 'tools/blender_decimate.py'),
                              '--', src, dst, str(self.decimate)], capture_output=True, text=True)
        if out.returncode != 0 or not os.path.exists(dst):
            raise SystemExit('Blender decimation failed:\n' + out.stdout[-2000:] + out.stderr[-2000:])
        print(next(l for l in out.stdout.splitlines() if l.startswith('decimated')))
        d = np.load(dst)
        W2 = d['W'].astype(float)
        W2 /= np.maximum(W2.sum(1, keepdims=True), 1e-9)
        polys, k = [], 0
        for n in d['sizes']:
            polys.append((list(d['loops'][k:k + n]), [tuple(u) for u in d['uvs'][k:k + n]]))
            k += n
        os.remove(src)
        os.remove(dst)
        return d['P'].astype(float), W2, polys

    def export(self, B, faces):
        P0, W, polys = self.optimize(B, faces)
        # smooth normals, area-weighted (Newell) over each polygon
        N0 = np.zeros_like(P0)
        for vs, _ in polys:
            q = P0[vs]
            N0[vs] += np.cross(q, np.roll(q, -1, 0)).sum(0)
        N0 /= np.maximum(np.linalg.norm(N0, axis=1, keepdims=True), 1e-9)
        key, positions, uvs, joints, wts, indices = {}, [], [], [], [], []
        for vs, cuv in polys:
            ids = []
            for v, uv in zip(vs, cuv):
                k = (int(v), round(float(uv[0]), 5), round(float(uv[1]), 5))
                if k not in key:
                    key[k] = len(key)
                    positions.append(int(v))
                    uvs.extend([round(float(uv[0]), 4), round(float(uv[1]), 4)])
                ids.append(key[k])
            # quads as they are; triangles as quads with the last corner doubled
            indices.extend(ids if len(ids) == 4 else [ids[0], ids[1], ids[2], ids[2]])
        vi = np.array(positions)
        P = self.to_rig(P0[vi], B)
        N = N0[vi] * [1, 1, -1]
        for v in vi:
            order = np.argsort(-W[v])[:2]
            ws = W[v][order]
            ws = ws / ws.sum() if ws.sum() > 0 else np.array([1.0, 0.0])
            joints.extend(int(o) for o in order)
            wts.extend(round(float(a), 3) for a in ws)
        mesh = {
            'credit': 'MakeHuman 1.1 base mesh, targets, skeleton and weights (CC0), shaped and painted by '
                      'tools/mh_human.py, decimated in Blender by tools/blender_decimate.py',
            'texture': 'papon:textures/entity/%s_mesh.png' % self.name,
            'bones': BONES,
            'corners': 4,
            'positions': P.round(3).flatten().tolist(), 'normals': N.round(3).flatten().tolist(), 'uvs': uvs,
            'indices': indices, 'joints': joints, 'weights': wts,
        }
        os.makedirs(os.path.join(ASSETS, 'meshes'), exist_ok=True)
        with open(os.path.join(ASSETS, 'meshes/%s.json' % self.name), 'w') as f:
            json.dump(mesh, f, separators=(',', ':'))
        return len(vi), len(indices) // 4

    def joint(self, B, name, swung=False):
        """A MakeHuman bone head in rig pixels (swung = after the arms came down)."""
        return self.to_rig(B['Jh'][name] if swung else B['J'][name][0], B)

    def hand(self, B, side):
        """The palm, in rig pixels."""
        return self.to_rig((B['Jh']['wrist.' + side] + B['Jh']['finger3-1.' + side]) / 2, B)

    def build_geo(self, B, rgb):
        hip = (self.joint(B, 'upperleg01.L') + self.joint(B, 'upperleg01.R')) / 2
        bones = [
            {'name': 'root', 'pivot': [0, 0, 0]},
            {'name': 'leg_r', 'parent': 'root', 'pivot': r3(self.joint(B, 'upperleg01.R'))},
            {'name': 'leg_l', 'parent': 'root', 'pivot': r3(self.joint(B, 'upperleg01.L'))},
            {'name': 'shin_r', 'parent': 'leg_r', 'pivot': r3(self.joint(B, 'lowerleg01.R'))},
            {'name': 'shin_l', 'parent': 'leg_l', 'pivot': r3(self.joint(B, 'lowerleg01.L'))},
            {'name': 'torso', 'parent': 'root', 'pivot': r3([0, hip[1], hip[2]])},
            {'name': 'head', 'parent': 'torso', 'pivot': r3(self.joint(B, 'neck02'))},
            {'name': 'jaw', 'parent': 'head', 'pivot': r3(self.joint(B, 'jaw'))},
            {'name': 'lid_r', 'parent': 'head', 'pivot': r3(self.to_rig(self.eye(B, 'r')[0], B))},
            {'name': 'lid_l', 'parent': 'head', 'pivot': r3(self.to_rig(self.eye(B, 'l')[0], B))},
            {'name': 'arm_r', 'parent': 'torso', 'pivot': r3(self.joint(B, 'upperarm01.R', True))},
            {'name': 'arm_l', 'parent': 'torso', 'pivot': r3(self.joint(B, 'upperarm01.L', True))},
            {'name': 'forearm_r', 'parent': 'arm_r', 'pivot': r3(self.joint(B, 'lowerarm01.R', True))},
            {'name': 'forearm_l', 'parent': 'arm_l', 'pivot': r3(self.joint(B, 'lowerarm01.L', True))},
        ]
        bones += paint_props(self.props, B, rgb)
        # where things are, for tools/pose_solver.py (GeckoLib ignores locators it has no use for)
        V = B['V']
        er, el = self.eye(B, 'r')[0], self.eye(B, 'l')[0]
        def front(y):     # the most forward body vertex near the midline at height y
            bv = B['body_verts']
            near = bv[(np.abs(V[bv, 1] - y) < 0.3) & (np.abs(V[bv, 0]) < 1.2)]
            return V[near[np.argmax(V[near, 2])]] * [0, 1, 1]
        hip = (B['J']['upperleg01.L'][0][1] + B['J']['upperleg01.R'][0][1]) / 2
        neck = B['J']['neck01'][0][1]
        locators = {
            'mouth': r3(self.to_rig(V[466], B)),
            'eyes': r3(self.to_rig((er + el) / 2, B)),
            'forehead': r3(self.to_rig(V[220] + [0, 0.35, 0], B)),
            'chest': r3(self.to_rig(front(hip + 0.8 * (neck - hip)), B)),
            'belly': r3(self.to_rig(front(hip + 0.4 * (neck - hip)), B)),
        }
        named = {b['name']: b for b in bones}
        named['head']['locators'] = {k: v for k, v in locators.items() if k in ('mouth', 'eyes', 'forehead')}
        named['torso']['locators'] = {k: v for k, v in locators.items() if k in ('chest', 'belly')}
        geo = {'format_version': '1.12.0', 'minecraft:geometry': [{
            'description': {'identifier': 'geometry.%s_mesh' % self.name, 'texture_width': OUT_TEX,
                            'texture_height': OUT_TEX, 'visible_bounds_width': 3, 'visible_bounds_height': 3.5,
                            'visible_bounds_offset': [0, 1.5, 0]},
            'bones': bones}]}
        path = os.path.join(ASSETS, 'geo/entity/%s_mesh.geo.json' % self.name)
        with open(path, 'w') as f:
            json.dump(geo, f, indent='\t')
        return path

    def generate(self):
        B = self.build()
        faces = self.corner_uvs(B)
        rgb = self.paint(B, faces)
        print(self.build_geo(B, rgb))
        Image.fromarray(rgb.astype(np.uint8)).save(
            os.path.join(ASSETS, 'textures/entity/%s_mesh.png' % self.name), optimize=True)
        nv, nt = self.export(B, faces)
        print(self.name, 'mesh', nv, 'vertices,', nt, 'polygons')
        return B


class Texels:
    """Everything known about the covered atlas texels, for painting (one row per texel)."""

    def __init__(self, ch, B, G, iy, ix):
        self.B, self.J = B, B['J']
        self.pos, self.nrm, self.w = G['pos'][iy, ix], G['nrm'][iy, ix], G['w'][iy, ix]
        self.nrm = self.nrm / np.maximum(np.linalg.norm(self.nrm, axis=1, keepdims=True), 1e-9)
        self.ao, self.kind = G['ao'][iy, ix, 0], np.rint(G['kind'][iy, ix, 0]).astype(int)
        self.x, self.y, self.z = self.pos[:, 0], self.pos[:, 1], self.pos[:, 2]
        shape = (OUT_TEX, OUT_TEX)
        self.n_fine = value_noise(shape, 3, 1)[iy, ix]
        self.n_mid = value_noise(shape, 24, 2)[iy, ix]
        self.folds = value_noise(shape, 40, 3)[iy, ix]

    def bone(self, name):
        return self.w[:, BONES.index(name)]

    def arm(self):
        return sum(self.bone(b) for b in ('arm_r', 'arm_l', 'forearm_r', 'forearm_l'))

    def along(self, side, start, end):
        """Position along a MakeHuman bone chain, 0 at `start`'s head, 1 at `end`'s head."""
        a, b = self.J[start + '.' + side][0], self.J[end + '.' + side][0]
        axis = b - a
        return ((self.pos - a) @ axis) / (axis @ axis)

    def cloth(self, colour, folds=0.12):
        return colour * (1 + folds * self.folds + 0.05 * self.n_fine)[:, None] * (0.5 + 0.65 * self.ao)[:, None]


# ---------------------------------------------------------------- texture plumbing
def rasterize(tris, tri_uv, attrs, size):
    """UV-space G-buffer: for each texel covered by a triangle, the barycentric blend of each
    per-vertex attribute array. Returns (covered mask, {name: HxWxC})."""
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


def ambient_occlusion(P, N, verts, step=0.12, rays=48, reach=3.0):
    """Per-vertex ambient occlusion against a voxelised solid of the (posed) body."""
    lo = P[verts].min(0) - 0.5
    shape = np.ceil((P[verts].max(0) + 0.5 - lo) / step).astype(int) + 1
    occ = np.zeros(shape, bool)
    idx = np.floor((P[verts] - lo) / step).astype(int)
    occ[idx[:, 0], idx[:, 1], idx[:, 2]] = True
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
            if 0 <= a < shape[0] and 0 <= b < shape[1] and 0 <= c < shape[2] and not outside[a, b, c] \
                    and not shell[a, b, c]:
                outside[a, b, c] = True
                q.append((a, b, c))
    solid = ~outside
    rng = np.random.default_rng(7)
    dirs = rng.normal(size=(rays, 3))
    dirs /= np.linalg.norm(dirs, axis=1, keepdims=True)
    ao = np.ones(len(P))
    pv, nv = P[verts], N[verts]
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
