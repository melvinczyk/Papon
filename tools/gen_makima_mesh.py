#!/usr/bin/env python3
"""Makima (entity/MakimaEntity) as a skinned mesh, from the user's model (tools/src/makima/, gitignored:
makima_pose.fbx and its five BaseColor textures).

  python tools/gen_makima_mesh.py

The model is one unrigged mesh in her pose (upper arms out level, forearms crossed in front of her chest), a clothed shell:
no body under the shirt and trousers. Blender (tools/makima_fbx.py) dumps it; without the FBX (it lives on the Mac) the rest
geometry is read back out of the built mesh itself (meshes/makima.json: positions, UVs and triangles never change here, only
the rig), so the rig can be rebuilt anywhere. Her pose is the bind pose; the skeleton (2026-10-07, the user: "drastically
improve its model"):

  torso (pelvis) > spine > chest > neck > head > braid > braid2 > braid3      the braid a chain, swung by the renderer
  chest > breast_l/r (bounce), tie (hangs, MakimaRenderer turns it to the ground), shoulder_l/r (clavicle) > arm > forearm > hand
  torso > leg > shin > foot

Weights: every piece gets hard labels first (the nearest bone segment among the ones it may follow: a hand only its hand, a
trouser leg only its side's leg, the shirt the body and both arms), then the shirt, trousers, face and back hair are diffused
over their own surface (heat flow, implicit, SIGMA wide: a smooth bend at every joint, never across a gap), the bust laid on
the chest as two soft ellipsoids, and each vertex keeps its INFLUENCES strongest bones. Her five textures go into one 2048
atlas (QUADS). Writes, under src/main/resources/assets/papon/: meshes/makima.json, textures/entity/makima_mesh.png (only from
the FBX's textures) and geo/entity/makima_mesh.geo.json (rig only: every bone of the animations in gen_makima_anims.py, and
the locators they and the renderer use).

(2026-10-07, the user: "add different facial expressions ... eyes opening and closing, her well known look ... the way she
tilts her head"; "her arm bends ... less plastic and more natural") Fingers: tools/makima_hands.py (three bones a finger, found
on each hand's surface; their curl and spread axes in the mesh's "hands"). The face: tools/makima_face.py (sliding lids,
lashes riding them, morphs for the smile, the open mouth and the brows, the irises on bones of their own, eye_l and eye_r,
which the renderer moves and the geo leaves out, a blush), appended after the model's own geometry ("base" in the mesh).
"""
import json
import os
import subprocess

import numpy as np
from PIL import Image

import makima_face
import makima_hands

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/papon')
SRC = os.path.join(ROOT, 'tools/src/makima')
RAW = os.path.join(ROOT, 'tools/.cache/makima/raw.npz')
BUILT = os.path.join(ASSETS, 'meshes/makima.json')
BLENDER = os.environ.get('BLENDER', '/Applications/Blender.app/Contents/MacOS/Blender')

MATS = ['skin', 'trouser', 'shirt', 'hair', 'eyes']          # tools/makima_fbx.py MATS, in order
SKIN, TROUSER, SHIRT, HAIR, EYE = range(5)
ATLAS = 2048
# texture -> (x, y, size) in the atlas (pixels, y down)
QUADS = {'skin': (0, 0, 1024), 'shirt': (1024, 0, 1024), 'hair': (0, 1024, 1024), 'trouser': (1024, 1024, 768),
         'eyes': (1792, 1024, 256)}
PX = 16 * 1.2           # rig pixels per metre: 1.63 m tall -> 1.96 blocks (the user: bigger)
INFLUENCES = 4          # bones per vertex (SkinnedMesh "influences")
SIGMA = 0.034           # metres: how wide a joint's bend is spread over the surface
TAU = 0.012             # metres over which a hard label prefers the nearer of two segments
SHOULDER_BLEND = (-0.05, 0.075)     # metres along the upper arm from the shoulder joint over which the shirt turns arm

# Joints in the model's metres (Z up, her front toward -Y, her left at +X). Measured off the mesh (slices of the trousers,
# sleeves, shirt and hair); wrists come from the hands (wrist()).
HIP = (0.0, 0.0, 0.90)
WAIST = (0.0, 0.0, 1.04)
CHEST = (0.0, 0.0, 1.17)
NECK = (0.0, 0.0, 1.33)
HEAD = (0.0, 0.0, 1.41)
CLAVICLE = {'l': (0.025, 0.0, 1.32), 'r': (-0.025, 0.0, 1.32)}
SHOULDER = {'l': (0.15, 0.01, 1.30), 'r': (-0.15, 0.01, 1.30)}
ELBOW = {'l': (0.255, 0.025, 1.28), 'r': (-0.235, 0.015, 1.27)}
HIPJ = {'l': (0.07, 0.0, 0.86), 'r': (-0.065, 0.0, 0.86)}
KNEE = {'l': (0.062, 0.0, 0.47), 'r': (-0.04, 0.01, 0.47)}
ANKLE = {'l': (0.08, 0.03, 0.08), 'r': (-0.035, 0.035, 0.08)}
TOE = {'l': (0.09, -0.09, 0.02), 'r': (-0.03, -0.085, 0.02)}
BRAID = [(0.0, 0.11, 1.42), (0.01, 0.137, 1.32), (0.02, 0.135, 1.22), (0.03, 0.135, 1.11)]     # pivots, then the tip
BREAST = {'l': (0.055, -0.07, 1.21), 'r': (-0.055, -0.07, 1.21)}
BREAST_R = (0.068, 0.07, 0.075)          # the soft ellipsoid each breast's weight falls off over (x, y, z radii)
TIE = ((0.0, -0.03, 1.33), (-0.032, -0.115, 1.07))     # knot, tip
MOUTH = (0.0, -0.064, 1.425)     # her lips (MakimaRenderer drools from here)
EYES = (0.0, -0.045, 1.478)      # between her eyes (the rider's camera looks here: client/MakimaRider)
CROTCH = (0.0, 0.0, 0.81)        # where her legs part (the rider's hips meet her here: gen_makima_anims fit)
SEAT = (0.0, 0.095, 0.91)        # the back of her hips
LAP = (0.0, -0.08, 0.86)         # the front of her hips

BONES = ['torso', 'spine', 'chest', 'neck', 'head', 'braid', 'braid2', 'braid3', 'breast_l', 'breast_r', 'tie',
         'shoulder_l', 'shoulder_r', 'arm_l', 'arm_r', 'forearm_l', 'forearm_r', 'hand_l', 'hand_r',
         'leg_l', 'leg_r', 'shin_l', 'shin_r', 'foot_l', 'foot_r'] + makima_hands.names('l') + makima_hands.names('r') + [
         'eye_l', 'eye_r']
EYE_BONES = ('eye_l', 'eye_r')     # not in the geo: MakimaRenderer puts them on the head's pose, moved by her gaze


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def load():
    """Rest geometry: vertices (model metres), triangles, corner UVs (0..1 of each texture) and material per triangle."""
    fbx = os.path.join(SRC, 'makima_pose.fbx')
    if os.path.exists(fbx):
        if not os.path.exists(RAW) or os.path.getmtime(RAW) < os.path.getmtime(fbx):
            subprocess.run([BLENDER, '-b', '--factory-startup', '--python', os.path.join(ROOT, 'tools/makima_fbx.py')],
                           check=True, capture_output=True)
        d = np.load(RAW)
        return d['P'], d['tris'], d['uvs'], d['mats'], True
    return recover() + (False,)


def recover():
    """The rest geometry back out of the built mesh: vertices welded again where the export split them for their UVs (same
    position and normal), rig pixels back to metres, each corner's material from its atlas quad."""
    m = json.load(open(BUILT))
    geo = json.load(open(os.path.join(ASSETS, 'geo/entity/makima_mesh.geo.json')))['minecraft:geometry'][0]
    torso = next(b for b in geo['bones'] if b['name'] == 'torso')
    floor = HIP[2] - torso['pivot'][1] / PX
    nv, ni = m.get('base', (len(m['positions']) // 3, len(m['indices'])))
    R = np.array(m['positions'], float).reshape(-1, 3)[:nv]
    N = np.array(m['normals'], float).reshape(-1, 3)[:nv]
    UV = np.array(m['uvs'], float).reshape(-1, 2)[:nv]
    _, first, inv = np.unique(np.round(np.hstack([R, N]), 3), axis=0, return_index=True, return_inverse=True)
    inv = inv.ravel()
    P = np.stack([R[:, 0], R[:, 2], R[:, 1]], 1)[first] / PX + [0, 0, floor]
    idx = np.array(m['indices'][:ni]).reshape(-1, 3)
    tris = inv[idx]
    ax, ay = UV[:, 0] * ATLAS, (1 - UV[:, 1]) * ATLAS
    mat = np.zeros(len(UV), int)
    uv = np.zeros_like(UV)
    for i, name in enumerate(MATS):
        x, y, s = QUADS[name]
        hit = (ax >= x - 0.5) & (ax <= x + s + 0.5) & (ay >= y - 0.5) & (ay <= y + s + 0.5)
        mat[hit] = i
        uv[hit, 0] = (ax[hit] - x) / s
        uv[hit, 1] = 1 - (ay[hit] - y) / s
    return P, tris, uv[idx], mat[idx[:, 0]]


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


def diffuse(P, tris, W, free, sigma=SIGMA):
    """Heat flow of the weights over the surface for SIGMA metres (implicit: (I - tL) W' = W), only among `free` vertices
    (the rest hold theirs and are left out of the system: a hand never blurs into its cuff)."""
    import scipy.sparse as sp
    import scipy.sparse.linalg as sl
    n = len(P)
    e = np.concatenate([tris[:, [0, 1]], tris[:, [1, 2]], tris[:, [2, 0]]])
    e = np.unique(np.sort(e, 1), axis=0)
    e = e[free[e[:, 0]] & free[e[:, 1]]]
    ln = np.maximum(np.linalg.norm(P[e[:, 0]] - P[e[:, 1]], axis=1), 1e-5)
    w = 1 / ln
    A = sp.coo_matrix((np.concatenate([w, w]), (np.concatenate([e[:, 0], e[:, 1]]), np.concatenate([e[:, 1], e[:, 0]]))),
                      shape=(n, n)).tocsr()
    deg = np.asarray(A.sum(1)).ravel()
    h2 = np.zeros(n)
    np.add.at(h2, e[:, 0], ln ** 2)
    np.add.at(h2, e[:, 1], ln ** 2)
    cnt = np.zeros(n)
    np.add.at(cnt, e[:, 0], 1)
    np.add.at(cnt, e[:, 1], 1)
    ok = deg > 0
    h2[ok] /= cnt[ok]
    # Laplacian ~ 4 / h^2 (mean of the neighbours - itself); heat after t spreads a point over sigma = sqrt(2t)
    k = np.zeros(n)
    k[ok] = 4 / h2[ok]
    L = sp.diags(np.where(ok, k / np.where(ok, deg, 1), 0)) @ A - sp.diags(k)
    t = sigma ** 2 / 2
    M = (sp.identity(n) - t * L).tocsc()
    out = W.copy()
    cols = np.where(W[free].sum(0) > 0)[0]
    sol = sl.splu(M).solve(W[:, cols])
    out[np.ix_(free, cols)] = np.asarray(sol)[free]
    out[free] = np.maximum(out[free], 0)
    out[free] /= out[free].sum(1, keepdims=True)
    return out


def pieces(P, tris, mats):
    """The model's connected pieces (vertex index arrays) and a function giving a piece's material."""
    n = len(P)
    comp = components(n, tris)
    vmat = np.zeros(n, int)
    vmat[tris.ravel()] = np.repeat(mats, 3)
    groups = {}
    for i, r in enumerate(comp):
        groups.setdefault(r, []).append(i)

    def mat_of(g):
        return np.bincount(vmat[g], minlength=5).argmax()
    return [np.array(g) for g in groups.values()], mat_of


def rig(P, tris, mats):
    n = len(P)
    groups, mat_of = pieces(P, tris, mats)

    # the two hands: the big skin pieces in front of the chest
    hands = [g for g in groups if mat_of(g) == SKIN and len(g) > 500 and P[g, 1].mean() < -0.1]
    assert len(hands) == 2, 'expected two hands'
    hand = {}
    for side in 'lr':
        hand[side] = min(hands, key=lambda g: np.linalg.norm(P[g] - ELBOW[side], axis=1).min())
    assert hand['l'] is not hand['r']
    WRIST = {s: wrist(P, hand[s], s) for s in 'lr'}

    seg = {
        'torso': ((0, 0, 0.84), (0, 0, 0.98), 0.12), 'spine': ((0, 0, 0.98), (0, 0, 1.12), 0.10),
        'chest': ((0, 0, 1.12), (0, 0, 1.31), 0.10), 'neck': ((0, 0, 1.33), (0, 0, 1.42), 0.04),
        'head': ((0, 0.01, 1.44), (0, 0, 1.58), 0.07),
        'braid': (BRAID[0], BRAID[1], 0.02), 'braid2': (BRAID[1], BRAID[2], 0.02), 'braid3': (BRAID[2], BRAID[3], 0.02),
    }
    for s in 'lr':
        seg['shoulder_' + s] = (CLAVICLE[s], SHOULDER[s], 0.05)
        seg['arm_' + s] = (SHOULDER[s], ELBOW[s], 0.045)
        seg['forearm_' + s] = (ELBOW[s], WRIST[s], 0.04)
        seg['leg_' + s] = (HIPJ[s], KNEE[s], 0.065)
        seg['shin_' + s] = (KNEE[s], ANKLE[s], 0.045)
    B = {b: i for i, b in enumerate(BONES)}
    W = np.zeros((n, len(BONES)))
    free = np.zeros(n, bool)

    def nearest(vs, cands):
        """Hard labels: each vertex to the nearest of `cands` (distance to the bone's segment less its radius)."""
        d = np.stack([np.maximum(seg_dist(P[vs], *seg[c][:2]) - seg[c][2], 0) for c in cands], 1)
        W[vs] = 0
        W[vs, [B[cands[i]] for i in d.argmin(1)]] = 1

    def rigid(vs, bone):
        W[vs] = 0
        W[vs, B[bone]] = 1

    def sleeves(g, WRIST, nearest, W, B):
        """The shirt: her forearms are crossed right in front of her chest, so the nearest bone in space would give the chest
        to the forearms. Along the surface instead: each sleeve is what lies within the arm's length of its cuff (geodesic
        distance from the cuff's opening), forearm, upper arm, then the shoulder; the rest the body's (and the clavicles')."""
        import scipy.sparse as sp
        from scipy.sparse.csgraph import dijkstra
        t = tris[np.isin(tris[:, 0], g)]
        e = np.unique(np.sort(np.concatenate([t[:, [0, 1]], t[:, [1, 2]], t[:, [2, 0]]]), 1), axis=0)
        ln = np.linalg.norm(P[e[:, 0]] - P[e[:, 1]], axis=1)
        G = sp.coo_matrix((ln, (e[:, 0], e[:, 1])), shape=(n, n)).tocsr()
        cnt = {}
        for a, b in np.sort(np.concatenate([t[:, [0, 1]], t[:, [1, 2]], t[:, [2, 0]]]), 1):
            cnt[(a, b)] = cnt.get((a, b), 0) + 1
        rim = np.array(sorted({v for k, c in cnt.items() if c == 1 for v in k}))
        d = {}
        for s in 'lr':
            cuff = rim[np.linalg.norm(P[rim] - WRIST[s], axis=1) < 0.06]
            assert len(cuff) > 8, 'no cuff on the %s sleeve' % s
            d[s] = dijkstra(G, directed=False, indices=cuff, min_only=True)[g]
        body = np.ones(len(g), bool)
        for s, o in (('l', 'r'), ('r', 'l')):
            fore = np.linalg.norm(np.array(ELBOW[s]) - WRIST[s])
            upper = fore + np.linalg.norm(np.array(SHOULDER[s]) - ELBOW[s])
            mine = d[s] < d[o]
            for bone, lo, hi in (('forearm_' + s, -1, fore), ('arm_' + s, fore, upper), ('shoulder_' + s, upper, upper + 0.045)):
                k = mine & (d[s] >= lo) & (d[s] < hi)
                W[g[k]] = 0
                W[g[k], B[bone]] = 1
                body &= ~k
        nearest(g[body], ['torso', 'spine', 'chest', 'shoulder_l', 'shoulder_r'])

    def legs_apart(g):
        """Which of the trousers' vertices are the left leg's: below the crotch the legs are two tubes joined by nothing (her
        right leg crosses toward her left, so no line in space parts them); above it, by side."""
        low = g[P[g, 2] < CROTCH[2] - 0.02]
        t = tris[np.isin(tris, low).all(1)]
        sub = components(n, t)[low]
        roots, counts = np.unique(sub, return_counts=True)
        big = roots[np.argsort(-counts)[:2]]
        lx = {r: P[low[sub == r], 0].mean() for r in big}
        lroot = max(big, key=lambda r: lx[r])
        left = P[g, 0] > 0
        pos = {v: i for i, v in enumerate(g)}
        for v, r in zip(low, sub):
            if r in lx:
                left[pos[v]] = r == lroot
        return left

    def divider(z):
        """Where the legs part: halfway between the two legs' centre lines at that height."""
        def cx(side):
            return np.interp(z, [ANKLE[side][2], KNEE[side][2], HIPJ[side][2]], [ANKLE[side][0], KNEE[side][0], HIPJ[side][0]])
        return (cx('l') + cx('r')) / 2

    copy = []          # small pieces that take the weights of the nearest vertex of a big one (buttons, collar, belt loops)
    shirt = trousers = None
    for g in groups:
        m, c = mat_of(g), P[g].mean(0)
        if any(g is h for h in hands):
            rigid(g, 'hand_l' if g is hand['l'] else 'hand_r')
        elif m in (SKIN, EYE, HAIR) and c[2] > 1.3:
            if m == HAIR and c[1] > 0.0 and P[g, 2].min() < 1.3:
                # the back hair and the braid: the head's above the nape, then the braid's three links down to its tie
                z = P[g, 2]
                lab = np.where(z > 1.43, 'head', np.where(z > 1.33, 'braid', np.where(z > 1.23, 'braid2', 'braid3')))
                W[g] = 0
                W[g, [B[x] for x in lab]] = 1
                free[g] = True
            elif m == SKIN and len(g) > 1000:
                nearest(g, ['head', 'neck', 'chest'])          # the face and neck
                W[g[P[g, 2] > 1.45]] = 0
                W[g[P[g, 2] > 1.45], B['head']] = 1
                free[g] = True
            else:
                rigid(g, 'head')                               # eyes, ears, teeth, tongue, lashes, bangs
        elif m == HAIR:
            rigid(g, 'braid3')                                 # the braid's tie
        elif m == SHIRT and len(g) > 1000:
            shirt = g
            sleeves(g, WRIST, nearest, W, B)
            free[g] = True
        elif m == SHIRT and len(g) > 100:
            # the tie: its own bone (it hangs), the knot the chest's
            rigid(g, 'tie')
            k = P[g, 2] > TIE[0][2] - 0.035
            W[g[k]] = 0
            W[g[k], B['chest']] = 1
            free[g] = True
        elif m == SHIRT:
            copy.append(('shirt', g))
        elif m == TROUSER and len(g) > 1000:
            trousers = g
            left = legs_apart(g)
            for s, vs in (('l', g[left]), ('r', g[~left])):
                # the body or the thigh by nearness; then the thigh's below the knee's plane (the bisector of thigh and
                # shin) the shin's: by radius alone the wider thigh took the top of the calf
                nearest(vs, ['torso', 'spine', 'leg_' + s])
                th = np.subtract(KNEE[s], HIPJ[s])
                sh = np.subtract(ANKLE[s], KNEE[s])
                nrm = th / np.linalg.norm(th) + sh / np.linalg.norm(sh)
                below = vs[(W[vs, B['leg_' + s]] > 0) & ((P[vs] - KNEE[s]) @ nrm > 0)]
                rigid(below, 'shin_' + s)
            free[g] = True
        elif c[2] > KNEE['l'][2]:
            copy.append(('trousers', g))
        else:
            rigid(g, 'foot_l' if c[0] > divider(c[2]) else 'foot_r')     # shoes, feet
    W = diffuse(P, tris, W, free)
    # the fingers: hard by segment, then blended over the hand alone, a knuckle wide
    fingers, axes, hfree = [], {}, np.zeros(n, bool)
    for s in 'lr':
        src = hand[s][np.argsort(np.linalg.norm(P[hand[s]] - ELBOW[s], axis=1))[:30]]
        bones, labels, axes[s] = makima_hands.build(P, tris, hand[s], s, src, None)
        fingers += bones
        for b, vs in labels.items():
            rigid(vs, b)
        hfree[hand[s]] = True
    W = diffuse(P, tris, W, hfree, makima_hands.SIGMA)
    # the irises on their own bones (the renderer slides them where she looks)
    for g in groups:
        if mat_of(g) == EYE and len(g) < 400 and P[g, 2].mean() > 1.3:
            rigid(g, 'eye_l' if P[g, 0].mean() > 0 else 'eye_r')
    # knees and elbows bend far further than the rest (kneeling: 150 degrees): a narrower blend there keeps the front of
    # the joint round instead of folding it flat
    for s in 'lr':
        for a, b, lo, hi in (('leg_' + s, 'shin_' + s, 0.25, 0.75), ('arm_' + s, 'forearm_' + s, 0.12, 0.88)):
            tot = W[:, B[a]] + W[:, B[b]]
            k = tot > 1e-6
            f = smoothstep(lo, hi, W[k, B[b]] / tot[k])
            W[k, B[b]] = f * tot[k]
            W[k, B[a]] = (1 - f) * tot[k]
    # the shoulders (2026-10-07): from the model's level arms to hanging ones is a swing of 80 degrees, and weights diffused
    # over the surface left the armpit's folds behind as flaps; across the joint the shirt is now blended by a plane (past
    # it the arm's, inside it the body's, SHOULDER_BLEND wide), the top of the shoulder inside it the collarbone's
    for s, sg in (('l', 1), ('r', -1)):
        a = np.subtract(ELBOW[s], SHOULDER[s])
        a /= np.linalg.norm(a)
        vs = shirt[(sg * P[shirt, 0] > 0.02) & (np.linalg.norm(P[shirt] - SHOULDER[s], axis=1) < 0.17)]
        d = (P[vs] - SHOULDER[s]) @ a
        vs, d = vs[d < 0.1], d[d < 0.1]
        free_w = 1 - W[vs, B['forearm_' + s]]
        arm = smoothstep(*SHOULDER_BLEND, d) * free_w
        body = free_w - arm
        top = smoothstep(SHOULDER[s][2] - 0.07, SHOULDER[s][2] - 0.02, P[vs, 2]) * smoothstep(-0.11, -0.03, d)
        fore = W[vs, B['forearm_' + s]].copy()
        W[vs] = 0
        W[vs, B['forearm_' + s]] = fore
        W[vs, B['arm_' + s]] = arm
        W[vs, B['shoulder_' + s]] = body * top * 0.85
        W[vs, B['chest']] = body * (1 - top * 0.85)
    # the bust: two soft ellipsoids on the front of the shirt's body (not its sleeves)
    body = shirt[W[shirt][:, [B['torso'], B['spine'], B['chest']]].sum(1) > 0.5]
    for s in 'lr':
        q = (P[body] - BREAST[s]) / BREAST_R
        k = smoothstep(1.0, 0.3, np.linalg.norm(q, axis=1)) * smoothstep(-0.035, -0.06, P[body, 1])
        W[body] *= (1 - k)[:, None]
        W[body, B['breast_' + s]] += k
    for big, g in copy:
        src = shirt if big == 'shirt' else trousers
        near = src[np.argmin(np.linalg.norm(P[src][None, :, :] - P[g][:, None, :], axis=2), axis=1)]
        W[g] = W[near]
    assert np.allclose(W.sum(1), 1), 'unweighted vertices'
    joints = {'wrist_l': WRIST['l'], 'wrist_r': WRIST['r']}
    for s in 'lr':     # the middle of the hand's far end: the finger tips' mean (the farthest 40 from the wrist)
        far = hand[s][np.argsort(-np.linalg.norm(P[hand[s]] - WRIST[s], axis=1))[:40]]
        joints['finger_' + s] = P[far].mean(0)
    return W, joints, fingers, axes


def to_rig(p, floor):
    p = np.asarray(p, float)
    return np.stack([p[..., 0], p[..., 2] - floor, p[..., 1]], -1) * PX


def atlas():
    img = Image.new('RGB', (ATLAS, ATLAS), (0, 0, 0))
    for name, (x, y, s) in QUADS.items():
        src = Image.open(os.path.join(SRC, '001_makima_%s_mtl_BaseColor.png' % name)).convert('RGB')
        img.paste(src.resize((s, s), Image.LANCZOS), (x, y))
    img.save(os.path.join(ASSETS, 'textures/entity/makima_mesh.png'), optimize=True)


def swap(v):
    """A direction (model metres) into rig coordinates."""
    v = np.asarray(v, float)
    return np.stack([v[..., 0], v[..., 2], v[..., 1]], -1)


def export(P, tris, uvs, mats, W, floor, face, axes):
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
    nb, ib = len(vi), len(indices)
    copies = {}
    for e, v in enumerate(verts):
        copies.setdefault(v, []).append(e)
    # the face's own geometry after the model's: lids and mouth in the body's run, the blush in a run of its own
    X, NX, UVX, TX, run = face['extra']
    run = np.array(run)
    indices += [nb + int(i) for t in TX[run == 'body'] for i in t]
    blush = [nb + int(i) for t in TX[run == 'blush'] for i in t]
    groups = {'blush': [len(indices), len(blush)]}
    indices += blush
    pos = np.concatenate([to_rig(P[vi], floor), to_rig(X, floor)])
    nrm = np.concatenate([N[vi][:, [0, 2, 1]], NX[:, [0, 2, 1]]])
    out_uv += [round(float(a), 5) for u, v in UVX for a in (u, 1 - v)]
    joints, wts = [], []
    for v in vi:
        order = np.argsort(-W[v])[:INFLUENCES]
        w = np.where(W[v][order] < 0.01, 0, W[v][order])
        w = np.round(w / w.sum(), 3)
        w[0] = round(1 - w[1:].sum(), 3)
        joints += [int(o) if w[i] > 0 else int(order[0]) for i, o in enumerate(order)]
        wts += [float(a) for a in w]
    for _ in range(len(X)):
        joints += [BONES.index('head')] * INFLUENCES
        wts += [1.0] + [0.0] * (INFLUENCES - 1)

    def px(d):
        return [round(float(a), 4) for a in (swap(d) * PX).ravel()]

    def flat(a):
        return [round(float(v), 4) for v in np.ravel(a)]
    morphs = {}
    for name, (midx, md, xd) in face['morph'].items():
        v, d = [], []
        for i, dd in zip(midx, md):
            if np.abs(dd).max() > 1e-7:
                for e in copies.get(int(i), []):
                    v.append(e)
                    d.append(dd)
        for i, dd in enumerate(xd):
            if np.abs(dd).max() > 1e-7:
                v.append(nb + i)
                d.append(dd)
        morphs[name] = {'v': v, 'd': px(np.array(d))}
    followers = []
    for fo in face['followers']:
        v, col = [], []
        for i, c in zip(fo['verts'], fo['col']):
            for e in copies.get(int(i), []):
                v.append(e)
                col.append(round(float(c), 3))
        followers.append({'lid': fo['lid'], 'v': v, 'col': col, 'w': fo['w']})
    ex, ey, f = face['frame']
    rig_face = {
        'cols': makima_face.LID_COLS, 'rows': makima_face.LID_ROWS, 'samples': makima_face.LID_SAMPLES,
        'lids': [{'name': L['name'], 'base': nb + L['base'], 'track': flat(to_rig(L['track'], floor)),
                  'cap': flat(to_rig(L['cap'], floor))} for L in face['lids']],
        'followers': followers, 'morphs': morphs,
        'eyes': {s: flat(to_rig(c, floor)) for s, c in face['eyes'].items()},
        'ex': flat(swap(ex)), 'ey': flat(swap(ey)), 'fwd': flat(swap(f)),
        'gaze': flat(np.array(makima_face.GAZE) * PX), 'mouth': flat(to_rig(face['mouth'][0], floor)),
    }
    # the fingers' axes in rig coordinates: the swap is a mirror, so +angle about the negated axis curls there
    hands = {s: {k: {'curl': flat(-swap(v['curl'])), 'spread': flat(-swap(v['spread'])), 'angle': round(v['angle'], 2),
                     'length': round(v['length'] * PX, 3)} for k, v in ax.items()} for s, ax in axes.items()}
    mesh = {'credit': 'Makima: the user\'s model (makima_pose.fbx), rigged and atlased by tools/gen_makima_mesh.py',
            'texture': 'papon:textures/entity/makima_mesh.png', 'bones': BONES, 'corners': 3, 'influences': INFLUENCES,
            'base': [nb, ib], 'groups': groups,
            'positions': pos.round(3).flatten().tolist(), 'normals': nrm.round(3).flatten().tolist(), 'uvs': out_uv,
            'indices': indices, 'joints': joints, 'weights': wts, 'face': rig_face, 'hands': hands}
    with open(BUILT, 'w') as f:
        json.dump(mesh, f, separators=(',', ':'))
    return len(pos), len(indices) // 3


def geo(joints, floor, fingers):
    def r(p):
        return [round(float(v), 3) for v in to_rig(p, floor)]
    bones = [
        {'name': 'root', 'pivot': [0, 0, 0]},
        {'name': 'torso', 'parent': 'root', 'pivot': r(HIP)},
        {'name': 'spine', 'parent': 'torso', 'pivot': r(WAIST)},
        {'name': 'chest', 'parent': 'spine', 'pivot': r(CHEST)},
        {'name': 'neck', 'parent': 'chest', 'pivot': r(NECK)},
        {'name': 'head', 'parent': 'neck', 'pivot': r(HEAD)},
        {'name': 'braid', 'parent': 'head', 'pivot': r(BRAID[0])},
        {'name': 'braid2', 'parent': 'braid', 'pivot': r(BRAID[1])},
        {'name': 'braid3', 'parent': 'braid2', 'pivot': r(BRAID[2])},
        {'name': 'breast_l', 'parent': 'chest', 'pivot': r(np.array(BREAST['l']) + [0, 0.05, 0])},
        {'name': 'breast_r', 'parent': 'chest', 'pivot': r(np.array(BREAST['r']) + [0, 0.05, 0])},
        {'name': 'tie', 'parent': 'chest', 'pivot': r(TIE[0])},
    ]
    for s in 'lr':
        bones += [
            {'name': 'shoulder_' + s, 'parent': 'chest', 'pivot': r(CLAVICLE[s])},
            {'name': 'arm_' + s, 'parent': 'shoulder_' + s, 'pivot': r(SHOULDER[s])},
            {'name': 'forearm_' + s, 'parent': 'arm_' + s, 'pivot': r(ELBOW[s])},
            {'name': 'hand_' + s, 'parent': 'forearm_' + s, 'pivot': r(joints['wrist_' + s])},
            {'name': 'leg_' + s, 'parent': 'torso', 'pivot': r(HIPJ[s])},
            {'name': 'shin_' + s, 'parent': 'leg_' + s, 'pivot': r(KNEE[s])},
            {'name': 'foot_' + s, 'parent': 'shin_' + s, 'pivot': r(ANKLE[s])},
        ]
    bones += [{'name': b, 'parent': parent, 'pivot': r(pivot)} for b, parent, pivot in fingers]
    named = {b['name']: b for b in bones}
    for s in 'lr':
        named['forearm_' + s]['locators'] = {'wrist_' + s: r(joints['wrist_' + s])}
        named['hand_' + s]['locators'] = {'finger_' + s: r(joints['finger_' + s])}
        named['shin_' + s]['locators'] = {'ankle_' + s: r(ANKLE[s])}
        named['foot_' + s]['locators'] = {'toe_' + s: r(TOE[s])}
    named['head']['locators'] = {'mouth': r(MOUTH), 'eyes': r(EYES)}
    named['torso']['locators'] = {'crotch': r(CROTCH), 'seat': r(SEAT), 'lap': r(LAP)}
    named['braid3']['locators'] = {'braid_tip': r(BRAID[3])}
    named['tie']['locators'] = {'tie_tip': r(TIE[1])}
    g = {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': 'geometry.makima_mesh', 'texture_width': ATLAS, 'texture_height': ATLAS,
                        'visible_bounds_width': 5, 'visible_bounds_height': 3, 'visible_bounds_offset': [0, 1.0, 0]},
        'bones': bones}]}
    with open(os.path.join(ASSETS, 'geo/entity/makima_mesh.geo.json'), 'w') as f:
        json.dump(g, f, separators=(',', ':'))


def main():
    P, tris, uvs, mats, from_fbx = load()
    floor = P[:, 2].min()
    W, joints, fingers, axes = rig(P, tris, mats)
    geo(joints, floor, fingers)
    if from_fbx:
        atlas()
    # the face rig (sampling the skin as the model has it), then its patches painted into the atlas's free corner
    png = os.path.join(ASSETS, 'textures/entity/makima_mesh.png')
    x, y, s = QUADS['skin']
    skin = np.asarray(Image.open(png).convert('RGB'), float)[y:y + s, x:x + s] / 255
    groups, mat_of = pieces(P, tris, mats)
    face = makima_face.build(P, tris, uvs, mats, groups, mat_of, skin, MOUTH)
    makima_face.paint(Image.open(png).convert('RGBA'), face['colours']).save(png, optimize=True)
    nv, nt = export(P, tris, uvs, mats, W, floor, face, axes)
    print('makima mesh', nv, 'vertices,', nt, 'triangles; %.2f blocks tall' % ((P[:, 2].max() - floor) * PX / 16))


if __name__ == '__main__':
    main()
