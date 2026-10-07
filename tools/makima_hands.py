#!/usr/bin/env python3
"""Makima's fingers (tools/gen_makima_mesh.py): each hand is one closed skin surface with no bones of its own in the model,
so the fingers are found on it: geodesic distance from the wrist splits it into five tubes past the knuckles (the thumb
first: the lowest split), the palm is shared out to the nearest finger, and each finger gets three bones along its centre
line (thumb: metacarpal, proximal, distal; the others: proximal, middle, distal at 0.45 and 0.74 of the finger's length
past the knuckle). Weights are hard by segment, then diffused over the hand alone (SIGMA: a knuckle's width).

Each finger also gets its curl axis (the tip swings toward the palm about it) and its spread axis (toward the middle
finger), written into meshes/makima.json "hands" in rig coordinates (rotating +angle about the stored axis curls, in the
animations' rig space; client/MakimaRenderer mirrors x for the game) for tools/gen_makima_anims.py and the renderer.
"""
import numpy as np

FINGERS = ['thumb', 'index', 'middle', 'ring', 'pinky']
SIGMA = 0.007


def unit(v):
    v = np.asarray(v, float)
    return v / np.linalg.norm(v)


def bone(finger, k, side):
    return 'f_%s%d_%s' % (finger, k, side)


def names(side):
    return [bone(f, k, side) for f in FINGERS for k in (1, 2, 3)]


def build(P, tris, hand, side, wrist_src, components):
    """One hand (model vertex indices `hand`): bones [(name, parent, pivot)], hard labels {bone: vertex indices}, and the
    finger axes (model coordinates)."""
    import scipy.sparse as sp
    from scipy.sparse.csgraph import dijkstra, connected_components
    n = len(P)
    t = tris[np.isin(tris[:, 0], hand)]
    e = np.unique(np.sort(np.concatenate([t[:, [0, 1]], t[:, [1, 2]], t[:, [2, 0]]]), 1), axis=0)
    ln = np.linalg.norm(P[e[:, 0]] - P[e[:, 1]], axis=1)
    G = sp.coo_matrix((ln, (e[:, 0], e[:, 1])), shape=(n, n)).tocsr()
    d = dijkstra(G, directed=False, indices=wrist_src, min_only=True)[hand]
    H = G[hand][:, hand]

    def parts(th, least=12):
        k = np.where(d > th)[0]
        nc, lab = connected_components(H[k][:, k], directed=False)
        sizes = np.bincount(lab)
        return [k[lab == i] for i in range(nc) if sizes[i] >= least]
    for th in np.arange(0.085, 0.15, 0.0025):
        tips = parts(th)
        if len(tips) == 5:
            break
    assert len(tips) == 5, 'expected five fingers on the %s hand' % side
    tipv = [p[np.argmax(d[p])] for p in tips]

    # the split level of each finger: the lowest threshold where its tip is still alone
    split = []
    for tv in tipv:
        s = th
        for lo in np.arange(th - 0.0025, 0.03, -0.0025):
            mine = next(p for p in parts(lo, 1) if tv in p)
            if any(o in mine for o in tipv if o != tv):
                break
            s = lo
        split.append(s)
    thumb = int(np.argmin(split))
    # nearest finger for every vertex (geodesic, from each finger's tube)
    D = np.stack([dijkstra(H, directed=False, indices=p, min_only=True) for p in tips], 1)
    label = D.argmin(1)

    def centre(f, at, width=0.003):
        k = (label == f) & (np.abs(d - at) < width)
        while k.sum() < 4:
            width *= 1.5
            k = (label == f) & (np.abs(d - at) < width)
        return P[hand[k]].mean(0)
    base = {f: centre(f, split[f]) for f in range(5)}
    rest = sorted((f for f in range(5) if f != thumb), key=lambda f: np.linalg.norm(base[f] - base[thumb]))
    order = [thumb] + rest
    bones, labels, axes = [], {}, {}
    tipd = {f: d[label == f].max() for f in range(5)}
    joints = {}
    for name, f in zip(FINGERS, order):
        if name == 'thumb':
            lv = [split[f] - 0.030, split[f] + 0.002, split[f] + 0.5 * (tipd[f] - split[f])]
        else:
            m = split[f] - 0.010
            L = tipd[f] - m
            lv = [m, m + 0.45 * L, m + 0.74 * L]
        piv = [centre(f, v) for v in lv]
        tip = P[hand[(label == f)][np.argsort(-d[label == f])[:8]]].mean(0)
        joints[name] = (piv, tip)
        for k in range(3):
            bones.append((bone(name, k + 1, side), bone(name, k, side) if k else 'hand_' + side, piv[k]))
            hi = lv[k + 1] if k < 2 else np.inf
            labels[bone(name, k + 1, side)] = hand[(label == f) & (d >= lv[k]) & (d < hi)]
    # axes: the back of the hand from the fingers' direction and the thumb's side (her right hand: dorsal = D x T)
    Dm = unit(joints['middle'][1] - joints['middle'][0][0])
    T = unit(joints['index'][0][0] - joints['pinky'][0][0])
    dorsal = unit(np.cross(Dm, T)) * (1 if side == 'r' else -1)
    volar = -dorsal
    for name in FINGERS:
        piv, tip = joints[name]
        Df = unit(tip - piv[0])
        nv = unit(volar - (volar @ Df) * Df)
        if name == 'thumb':
            nv = unit(unit(volar - 0.8 * T) - (unit(volar - 0.8 * T) @ Df) * Df)
        curl = unit(np.cross(Df, nv))
        spread = np.cross(Df, Dm)
        ang = float(np.degrees(np.arcsin(np.clip(np.linalg.norm(spread), 0, 1)))) if name not in ('middle', 'thumb') else 0.0
        axes[name] = {'curl': curl, 'spread': unit(spread) if ang > 0.5 else np.cross(Df, volar), 'angle': ang,
                      'length': float(np.linalg.norm(tip - piv[0]))}
    return bones, labels, axes
