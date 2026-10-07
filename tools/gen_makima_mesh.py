#!/usr/bin/env python3
"""Makima (entity/MakimaEntity) as a skinned mesh, from the user's model (tools/src/makima/, gitignored:
makima_pose.fbx and its five BaseColor textures).

  python3.11 tools/gen_makima_mesh.py

The model is one unrigged mesh in her pose (both hands in front of her chest, fingers crossed), a
clothed shell: no body under the shirt and trousers. Blender (tools/makima_fbx.py) dumps it; here it
is rigged onto the bones below (her pose is the bind pose) and skinned by body part:

  head    face, neck, ears, eyes and hair, the neck blended into the chest
  braid   the braid down her back, from the back of her head
  chest   shirt (above the waist) and tie; arms and forearms out of the sleeves; hands on the forearms
  torso   the pelvis: the trousers' seat and the shirt's tucked hem; legs and shins below it, each
          side kept to its own leg; shoes and feet on the shins

Every vertex follows the two bones it is nearest (distance to each bone's segment less its radius,
blended over TAU). Her five textures go into one 2048 atlas (QUADS). Writes, under
src/main/resources/assets/papon/: meshes/makima.json, textures/entity/makima_mesh.png and
geo/entity/makima_mesh.geo.json (rig only: every bone of the animations in gen_makima_anims.py).
"""
import json
import os
import subprocess

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/papon')
SRC = os.path.join(ROOT, 'tools/src/makima')
RAW = os.path.join(ROOT, 'tools/.cache/makima/raw.npz')
BLENDER = os.environ.get('BLENDER', '/Applications/Blender.app/Contents/MacOS/Blender')

MATS = ['skin', 'trouser', 'shirt', 'hair', 'eyes']          # tools/makima_fbx.py MATS, in order
SKIN, TROUSER, SHIRT, HAIR, EYE = range(5)
ATLAS = 2048
# texture -> (x, y, size) in the atlas (pixels, y down)
QUADS = {'skin': (0, 0, 1024), 'shirt': (1024, 0, 1024), 'hair': (0, 1024, 1024), 'trouser': (1024, 1024, 768),
         'eyes': (1792, 1024, 256)}
PX = 16 * 1.2           # rig pixels per metre: 1.63 m tall -> 1.96 blocks (the user: bigger)
TAU = 0.012             # metres over which a vertex blends between two bones

# Bones in the model's metres (Z up, her front toward -Y, her left at +X): pivot, parent, and the segment
# (start, end, radius) a vertex measures its distance to. Measured off the mesh (slices of the trousers
# and sleeves); wrists come from the hands (wrist()).
HIP = (0.0, 0.0, 0.90)
WAIST = (0.0, 0.0, 1.06)
NECK = (0.0, 0.0, 1.38)
SHOULDER = {'l': (0.15, 0.01, 1.30), 'r': (-0.15, 0.01, 1.30)}
ELBOW = {'l': (0.255, 0.025, 1.28), 'r': (-0.235, 0.015, 1.27)}
HIPJ = {'l': (0.07, 0.0, 0.86), 'r': (-0.065, 0.0, 0.86)}
KNEE = {'l': (0.062, 0.0, 0.47), 'r': (-0.04, 0.01, 0.47)}
ANKLE = {'l': (0.08, 0.03, 0.08), 'r': (-0.035, 0.035, 0.08)}
BRAID = (0.0, 0.12, 1.44)
MOUTH = (0.0, -0.064, 1.425)     # her lips (client/MakimaRenderer.MOUTH drools from here)
BRAID_FROM = (1.38, 1.46)        # the back hair is the head's above this band, the braid's below it


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def load():
    fbx = os.path.join(SRC, 'makima_pose.fbx')
    if not os.path.exists(RAW) or os.path.getmtime(RAW) < os.path.getmtime(fbx):
        subprocess.run([BLENDER, '-b', '--factory-startup', '--python', os.path.join(ROOT, 'tools/makima_fbx.py')],
                       check=True, capture_output=True)
    d = np.load(RAW)
    return d['P'], d['tris'], d['uvs'], d['mats']


def components(n, tris):
    par = np.arange(n)

    def find(a):
        while par[a] != a:
            par[a] = par[par[a]]
            a = par[a]
        return a
    for a, b, c in tris:
        ra, rb, rc = find(a), find(b), find(c)
        par[rb] = ra
        par[rc] = ra
    return np.array([find(i) for i in range(n)])


def seg_dist(P, a, b):
    a, b = np.array(a, float), np.array(b, float)
    ab = b - a
    t = np.clip(((P - a) @ ab) / (ab @ ab), 0, 1)
    return np.linalg.norm(P - (a + t[:, None] * ab), axis=1)


def wrist(P, hand, side):
    """The hand's vertices nearest its elbow: where it comes out of the cuff."""
    d = np.linalg.norm(P[hand] - ELBOW[side], axis=1)
    return P[hand[np.argsort(d)[:30]]].mean(0)


def rig(P, tris, mats):
    n = len(P)
    comp = components(n, tris)
    vmat = np.zeros(n, int)
    vmat[tris.ravel()] = np.repeat(mats, 3)
    groups = {}
    for i, r in enumerate(comp):
        groups.setdefault(r, []).append(i)
    groups = [np.array(g) for g in groups.values()]

    def mat_of(g):
        return np.bincount(vmat[g], minlength=5).argmax()

    # the two hands: the big skin pieces in front of the chest
    hands = [g for g in groups if mat_of(g) == SKIN and len(g) > 500 and P[g, 1].mean() < -0.1]
    assert len(hands) == 2, 'expected two hands'
    hand = {}
    for side in 'lr':
        hand[side] = min(hands, key=lambda g: np.linalg.norm(P[g] - ELBOW[side], axis=1).min())
    assert hand['l'] is not hand['r']
    WRIST = {s: wrist(P, hand[s], s) for s in 'lr'}

    bones = ['torso', 'chest', 'head', 'braid', 'arm_l', 'arm_r', 'forearm_l', 'forearm_r', 'leg_l', 'leg_r',
             'shin_l', 'shin_r']
    seg = {
        'torso': (HIP, WAIST, 0.12), 'chest': (WAIST, NECK, 0.10), 'head': ((0, 0.01, 1.42), (0, 0.0, 1.58), 0.06),
        'arm_l': (SHOULDER['l'], ELBOW['l'], 0.045), 'arm_r': (SHOULDER['r'], ELBOW['r'], 0.045),
        'forearm_l': (ELBOW['l'], WRIST['l'], 0.04), 'forearm_r': (ELBOW['r'], WRIST['r'], 0.04),
        'leg_l': (HIPJ['l'], KNEE['l'], 0.065), 'leg_r': (HIPJ['r'], KNEE['r'], 0.065),
        'shin_l': (KNEE['l'], ANKLE['l'], 0.045), 'shin_r': (KNEE['r'], ANKLE['r'], 0.045),
    }
    W = np.zeros((n, len(bones)))
    B = {b: i for i, b in enumerate(bones)}

    def nearest(vs, cands):
        """Soft-nearest of `cands` (bone names) for vertices vs."""
        d = np.stack([np.maximum(seg_dist(P[vs], *seg[c][:2]) - seg[c][2], 0) for c in cands], 1)
        w = np.exp(-(d - d.min(1, keepdims=True)) / TAU)
        W[np.ix_(vs, [B[c] for c in cands])] = w / w.sum(1, keepdims=True)

    def rigid(vs, bone):
        W[vs] = 0
        W[vs, B[bone]] = 1

    # where the legs part: halfway between the two legs' centre lines at each height
    def divider(z):
        def cx(side):
            return np.interp(z, [ANKLE[side][2], KNEE[side][2], HIPJ[side][2]], [ANKLE[side][0], KNEE[side][0], HIPJ[side][0]])
        return (cx('l') + cx('r')) / 2

    for g in groups:
        m, c = mat_of(g), P[g].mean(0)
        if any(g is h for h in hands):
            rigid(g, 'forearm_l' if g is hand['l'] else 'forearm_r')
        elif m in (SKIN, EYE, HAIR) and c[2] > 1.3:
            if m == HAIR and c[1] > 0.0 and P[g, 2].min() < 1.3:
                # the back hair and braid: the head's at the top, the braid's below
                k = smoothstep(BRAID_FROM[0], BRAID_FROM[1], P[g, 2])
                W[g] = 0
                W[g, B['head']] = k
                W[g, B['braid']] = 1 - k
            elif m == SKIN and len(g) > 1000:
                nearest(g, ['head', 'chest'])           # the face and neck
            else:
                rigid(g, 'head')
        elif m == HAIR:
            rigid(g, 'braid')                           # the braid's tie
        elif m == SHIRT and len(g) > 1000:
            nearest(g, ['torso', 'chest', 'arm_l', 'arm_r', 'forearm_l', 'forearm_r'])
        elif m == SHIRT and len(g) > 100:
            rigid(g, 'chest')                           # the tie
        elif m == SHIRT:
            nearest(g, ['chest', 'arm_l', 'arm_r', 'forearm_l', 'forearm_r'])     # buttons: collar, placket, cuffs
        elif m == TROUSER and len(g) > 1000:
            left = P[g, 0] > divider(P[g, 2])
            nearest(g[left], ['torso', 'chest', 'leg_l', 'shin_l'])
            nearest(g[~left], ['torso', 'chest', 'leg_r', 'shin_r'])
        elif c[2] > KNEE['l'][2]:
            nearest(g, ['torso', 'chest'])              # belt loops
        else:
            rigid(g, 'shin_l' if c[0] > divider(c[2]) else 'shin_r')     # shoes, feet
    assert np.allclose(W.sum(1), 1), 'unweighted vertices'
    joints = {'hip': HIP, 'waist': WAIST, 'neck': NECK, 'braid': BRAID, 'wrist_l': WRIST['l'], 'wrist_r': WRIST['r']}
    return bones, W, joints


def to_rig(p, floor):
    p = np.asarray(p, float)
    return np.stack([p[..., 0], p[..., 2] - floor, p[..., 1]], -1) * PX


def atlas():
    img = Image.new('RGB', (ATLAS, ATLAS), (0, 0, 0))
    for name, (x, y, s) in QUADS.items():
        src = Image.open(os.path.join(SRC, '001_makima_%s_mtl_BaseColor.png' % name)).convert('RGB')
        img.paste(src.resize((s, s), Image.LANCZOS), (x, y))
    img.save(os.path.join(ASSETS, 'textures/entity/makima_mesh.png'), optimize=True)


def export(P, tris, uvs, mats, bones, W, floor):
    # smooth normals, area-weighted
    N = np.zeros_like(P)
    fn = np.cross(P[tris[:, 1]] - P[tris[:, 0]], P[tris[:, 2]] - P[tris[:, 0]])
    for k in range(3):
        np.add.at(N, tris[:, k], fn)
    N /= np.maximum(np.linalg.norm(N, axis=1, keepdims=True), 1e-12)
    key, verts, out_uv, indices = {}, [], [], []
    for t, tri in enumerate(tris):
        x, y, s = QUADS[MATS[mats[t]]]
        for k in range(3):
            u, v = uvs[t, k]
            # into the atlas: OBJ-style v (up), as SkinnedMesh expects
            au, av = (x + u * s) / ATLAS, 1 - (y + (1 - v) * s) / ATLAS
            kk = (int(tri[k]), round(float(au), 5), round(float(av), 5))
            if kk not in key:
                key[kk] = len(verts)
                verts.append(int(tri[k]))
                out_uv += [round(float(au), 5), round(float(av), 5)]
            indices.append(key[kk])
    vi = np.array(verts)
    pos = to_rig(P[vi], floor)
    nrm = N[vi][:, [0, 2, 1]]
    joints, wts = [], []
    for v in vi:
        order = np.argsort(-W[v])[:2]
        w = W[v][order]
        w = w / w.sum()
        joints += [int(o) for o in order]
        wts += [round(float(a), 3) for a in w]
    mesh = {'credit': 'Makima: the user\'s model (makima_pose.fbx), rigged and atlased by tools/gen_makima_mesh.py',
            'texture': 'papon:textures/entity/makima_mesh.png', 'bones': bones, 'corners': 3,
            'positions': pos.round(3).flatten().tolist(), 'normals': nrm.round(3).flatten().tolist(), 'uvs': out_uv,
            'indices': indices, 'joints': joints, 'weights': wts}
    with open(os.path.join(ASSETS, 'meshes/makima.json'), 'w') as f:
        json.dump(mesh, f, separators=(',', ':'))
    return len(vi), len(indices) // 3


def geo(joints, floor):
    def r(p):
        return [round(float(v), 3) for v in to_rig(p, floor)]
    bones = [
        {'name': 'root', 'pivot': [0, 0, 0]},
        {'name': 'torso', 'parent': 'root', 'pivot': r(HIP)},
        {'name': 'leg_l', 'parent': 'torso', 'pivot': r(HIPJ['l'])},
        {'name': 'leg_r', 'parent': 'torso', 'pivot': r(HIPJ['r'])},
        {'name': 'shin_l', 'parent': 'leg_l', 'pivot': r(KNEE['l'])},
        {'name': 'shin_r', 'parent': 'leg_r', 'pivot': r(KNEE['r'])},
        {'name': 'chest', 'parent': 'torso', 'pivot': r(WAIST)},
        {'name': 'head', 'parent': 'chest', 'pivot': r(NECK)},
        {'name': 'braid', 'parent': 'head', 'pivot': r(BRAID)},
        {'name': 'arm_l', 'parent': 'chest', 'pivot': r(SHOULDER['l'])},
        {'name': 'arm_r', 'parent': 'chest', 'pivot': r(SHOULDER['r'])},
        {'name': 'forearm_l', 'parent': 'arm_l', 'pivot': r(ELBOW['l'])},
        {'name': 'forearm_r', 'parent': 'arm_r', 'pivot': r(ELBOW['r'])},
    ]
    named = {b['name']: b for b in bones}
    named['forearm_l']['locators'] = {'wrist_l': r(joints['wrist_l'])}
    named['forearm_r']['locators'] = {'wrist_r': r(joints['wrist_r'])}
    named['shin_l']['locators'] = {'ankle_l': r(ANKLE['l'])}
    named['shin_r']['locators'] = {'ankle_r': r(ANKLE['r'])}
    named['head']['locators'] = {'mouth': r(MOUTH)}
    g = {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': 'geometry.makima_mesh', 'texture_width': ATLAS, 'texture_height': ATLAS,
                        'visible_bounds_width': 4, 'visible_bounds_height': 3, 'visible_bounds_offset': [0, 1.0, 0]},
        'bones': bones}]}
    with open(os.path.join(ASSETS, 'geo/entity/makima_mesh.geo.json'), 'w') as f:
        json.dump(g, f, separators=(',', ':'))


def main():
    P, tris, uvs, mats = load()
    floor = P[:, 2].min()
    bones, W, joints = rig(P, tris, mats)
    geo(joints, floor)
    atlas()
    nv, nt = export(P, tris, uvs, mats, bones, W, floor)
    print('makima mesh', nv, 'vertices,', nt, 'triangles; %.2f blocks tall' % ((P[:, 2].max() - floor) * PX / 16))


if __name__ == '__main__':
    main()
