"""Filmstrips of a mesh celebrity's animation, to check timing and poses without starting Minecraft.

  python3.11 tools/anim_preview.py <name> <animation> [frames=10] [view=front|side|34] [out.png]
  e.g. python3.11 tools/anim_preview.py edp445 walk_edp445 12 side

Evaluates the GeckoLib animation the way the game does (catmull-rom, linear and the easings the
generators use), poses the rig from the geo's pivots (rotations in pose_solver's convention),
skins the mesh (meshes/<name>.json) and the prop cubes with it, and has Blender render the frames
side by side (tools/blender_filmstrip.py). Bone visibility and scale keys are honoured, so props
pop in and out as in game.
"""
import json
import math
import os
import subprocess
import sys

import numpy as np

import pose_solver as PS

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/papon')
BLENDER = '/Applications/Blender.app/Contents/MacOS/Blender'


# ---------------------------------------------------------------- easing
def ease(name, t, arg=None):
    if name in (None, 'linear'):
        return t
    if name == 'easeInOutSine':
        return -(math.cos(math.pi * t) - 1) / 2
    if name == 'easeInSine':
        return 1 - math.cos(t * math.pi / 2)
    if name == 'easeOutSine':
        return math.sin(t * math.pi / 2)
    p = {'Quad': 2, 'Cubic': 3, 'Quart': 4, 'Quint': 5}
    for k, n in p.items():
        if name == 'easeIn' + k:
            return t ** n
        if name == 'easeOut' + k:
            return 1 - (1 - t) ** n
        if name == 'easeInOut' + k:
            return 2 ** (n - 1) * t ** n if t < 0.5 else 1 - (-2 * t + 2) ** n / 2
    if name in ('easeOutBack', 'easeInBack', 'easeInOutBack'):
        c1 = arg if arg is not None else 1.70158
        c3 = c1 + 1
        if name == 'easeOutBack':
            return 1 + c3 * (t - 1) ** 3 + c1 * (t - 1) ** 2
        if name == 'easeInBack':
            return c3 * t ** 3 - c1 * t ** 2
        c2 = c1 * 1.525
        return ((2 * t) ** 2 * ((c2 + 1) * 2 * t - c2)) / 2 if t < 0.5 else \
            ((2 * t - 2) ** 2 * ((c2 + 1) * (t * 2 - 2) + c2) + 2) / 2
    if name == 'easeOutBounce':
        n1, d1 = 7.5625, 2.75
        if t < 1 / d1:
            return n1 * t * t
        if t < 2 / d1:
            t -= 1.5 / d1
            return n1 * t * t + 0.75
        if t < 2.5 / d1:
            t -= 2.25 / d1
            return n1 * t * t + 0.9375
        t -= 2.625 / d1
        return n1 * t * t + 0.984375
    if name == 'easeOutElastic':
        return 0 if t == 0 else 1 if t == 1 else 2 ** (-10 * t) * math.sin((t * 10 - 0.75) * 2 * math.pi / 3) + 1
    return t


def channel(keys, t, length, loop):
    """Value of one channel (dict time -> keyframe) at time t."""
    ks = sorted((float(k), v) for k, v in keys.items())
    if loop and length:
        t = t % length
    if t <= ks[0][0]:
        return np.array(ks[0][1]['vector'], float)
    if t >= ks[-1][0]:
        return np.array(ks[-1][1]['vector'], float)
    i = max(j for j in range(len(ks)) if ks[j][0] <= t)
    (t0, a), (t1, b) = ks[i], ks[i + 1]
    u = (t - t0) / max(t1 - t0, 1e-9)
    va, vb = np.array(a['vector'], float), np.array(b['vector'], float)
    if b.get('lerp_mode') == 'catmullrom':
        p0 = np.array(ks[i - 1][1]['vector'], float) if i > 0 else va
        p3 = np.array(ks[i + 2][1]['vector'], float) if i + 2 < len(ks) else vb
        return 0.5 * (2 * va + (-p0 + vb) * u + (2 * p0 - 5 * va + 4 * vb - p3) * u * u + (-p0 + 3 * va - 3 * vb + p3) * u ** 3)
    e = ease(b.get('easing'), u, (b.get('easingArgs') or [None])[0])
    return va + (vb - va) * e


# ---------------------------------------------------------------- posing
def pose(geo, anim, t):
    """World matrix (4x4, rig pixels) per bone, and per-bone scale for visibility."""
    bones = {b['name']: b for b in geo['bones']}
    length, loop = anim.get('animation_length', 0), anim.get('loop') is True
    M, S = {}, {}

    def world(name):
        if name in M:
            return M[name]
        b = bones[name]
        ch = anim['bones'].get(name, {})
        r = channel(ch['rotation'], t, length, loop) if 'rotation' in ch else np.zeros(3)
        p = channel(ch['position'], t, length, loop) if 'position' in ch else np.zeros(3)
        s = channel(ch['scale'], t, length, loop) if 'scale' in ch else np.ones(3)
        piv = np.array(b['pivot'], float)
        L = np.eye(4)
        L[:3, :3] = PS.rot(*r) @ np.diag(s)
        L[:3, 3] = piv - L[:3, :3] @ piv + p * [-1, 1, 1]
        parent = b.get('parent')
        M[name] = (world(parent) if parent else np.eye(4)) @ L
        S[name] = (S[parent] if parent else 1.0) * float(np.mean(s))
        return M[name]

    for n in bones:
        world(n)
    return M, S


def cube_mesh(c):
    o, sz = np.array(c['origin'], float), np.array(c['size'], float)
    corners = np.array([[x, y, z] for x in (0, 1) for y in (0, 1) for z in (0, 1)], float) * sz + o
    if 'rotation' in c:
        piv = np.array(c['pivot'], float)
        corners = (corners - piv) @ PS.rot(*c['rotation']).T + piv
    faces = [[0, 1, 3, 2], [4, 6, 7, 5], [0, 4, 5, 1], [2, 3, 7, 6], [0, 2, 6, 4], [1, 5, 7, 3]]
    return corners, faces


def main():
    name, anim_name = sys.argv[1], sys.argv[2]
    frames = int(sys.argv[3]) if len(sys.argv) > 3 else 10
    view = sys.argv[4] if len(sys.argv) > 4 else 'front'
    out = sys.argv[5] if len(sys.argv) > 5 else os.path.join(ROOT, 'tools/.cache', '%s_%s.png' % (name, anim_name))
    geo = json.load(open(os.path.join(ASSETS, 'geo/entity/%s_mesh.geo.json' % name)))['minecraft:geometry'][0]
    mesh = json.load(open(os.path.join(ASSETS, 'meshes/%s.json' % name)))
    anims = json.load(open(os.path.join(ASSETS, 'animations/entity/customer.animation.json')))['animations']
    anim = anims['animation.customer.' + anim_name]
    length = anim.get('animation_length', 1.0)
    P = np.array(mesh['positions'], float).reshape(-1, 3)
    J = np.array(mesh['joints']).reshape(-1, 2)
    W = np.array(mesh['weights'], float).reshape(-1, 2)
    idx = np.array(mesh['indices']).reshape(-1, mesh.get('corners', 3))
    names = mesh['bones']
    times = [length * k / frames for k in range(frames)] if anim.get('loop') is True else \
        [length * k / (frames - 1) for k in range(frames)]
    posed, props = [], []
    for t in times:
        M, S = pose(geo, anim, t)
        Ph = np.hstack([P, np.ones((len(P), 1))])
        X = np.zeros_like(P)
        for k in range(2):
            mats = np.stack([M[names[j]] for j in J[:, k]])
            X += W[:, k:k + 1] * np.einsum('nij,nj->ni', mats, Ph)[:, :3]
        posed.append(X)
        cubes = []
        for b in geo['bones']:
            if not b.get('cubes') or S[b['name']] < 0.05:
                continue
            for c in b['cubes']:
                v, f = cube_mesh(c)
                v = (M[b['name']] @ np.hstack([v, np.ones((8, 1))]).T).T[:, :3]
                cubes.append((v, f))
        props.append(cubes)
    tmp = os.path.join(ROOT, 'tools/.cache/filmstrip.json')
    json.dump({'texture': os.path.join(ASSETS, mesh['texture'].split(':')[1]), 'uvs': mesh['uvs'],
               'faces': [list(dict.fromkeys(f)) for f in idx.tolist()],
               'frames': [x.round(3).tolist() for x in posed],
               'props': [[(v.round(3).tolist(), f) for v, f in cs] for cs in props],
               'times': times, 'view': view, 'out': out}, open(tmp, 'w'))
    r = subprocess.run([BLENDER, '-b', '--factory-startup', '--python', os.path.join(ROOT, 'tools/blender_filmstrip.py'),
                        '--', tmp], capture_output=True, text=True)
    if 'wrote' not in r.stdout:
        print(r.stdout[-1500:], r.stderr[-1500:])
    print(out)


if __name__ == '__main__':
    main()
