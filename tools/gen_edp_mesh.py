#!/usr/bin/env python3
"""Converts the EDP445 character from the EDP SIM game into the mod's EDP445 customer.

GeckoLib can only draw cubes, so the character is drawn by client/mesh/MeshCrowdRenderer: it
keeps the customer rig (root, torso, head, arm_r/l, leg_r/l and the props) and the shared
customer animations, and skins this mesh onto those bones every frame.

Reads the game's Unity data (needs `pip install UnityPy numpy pillow`):
  python3 tools/gen_edp_mesh.py "<EDP SIM>/EDP SIM_Data"
Writes, under src/main/resources/assets/papon/:
  meshes/edp445.json                  skinned triangle mesh, rig-space pixels
  textures/entity/edp445_mesh.png     the skin atlas, 1024x1024, with his clothes painted on
  geo/entity/edp445_mesh.geo.json     the rig: bones at the mesh's joints, beanie + taco + phone

What it does to the source:
  - the Unity humanoid (Mixamo bone names) is turned to face -Z with its right side at -X,
    the rig's conventions, and scaled so the top of his head is at 33 px like the blocky EDP;
  - the T-pose arms are swung down about the shoulders (weighted, so the shoulders blend);
  - the 54 Unity bones are folded onto the rig's six moving bones (two weights per vertex);
  - the source body is unclothed, so the atlas is repainted where clothes go: the midnight-green
    445 jersey (EDP over 445 on the back, white sleeve stripes), dark pants, and shoes. The
    shading of the original texture is kept so folds and contours still read.
The blocky EDP445 (gen_customers.py) is untouched; its texture still supplies the beanie, taco
and phone, which are cubes on this rig.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/papon')
OUT_TEX = 1024
HEAD_TOP_PX = 33.0
ARM_DROP_DEG = 80.0
BONES = ['torso', 'head', 'arm_r', 'arm_l', 'leg_r', 'leg_l']

# Unity bone -> (rig bone, share), extra shares go to the next listed entry
FOLD = {
    'Hips': [('torso', 1.0)],
    'Spine': [('torso', 1.0)], 'Spine1': [('torso', 1.0)], 'Spine2': [('torso', 1.0)],
    'Neck': [('torso', 0.4), ('head', 0.6)], 'Head': [('head', 1.0)],
    'RightShoulder': [('torso', 0.7), ('arm_r', 0.3)], 'LeftShoulder': [('torso', 0.7), ('arm_l', 0.3)],
}


def fold(name):
    if name in FOLD:
        return FOLD[name]
    for side, arm, leg in (('Right', 'arm_r', 'leg_r'), ('Left', 'arm_l', 'leg_l')):
        if name.startswith(side):
            rest = name[len(side):]
            if rest.startswith(('Arm', 'ForeArm', 'Hand', 'Finger')):
                return [(arm, 1.0)]
            if rest.startswith(('UpLeg', 'Leg', 'Foot', 'Toe')):
                return [(leg, 1.0)]
    return [('torso', 1.0)]


def mat(m):
    return np.array([[m.e00, m.e01, m.e02, m.e03], [m.e10, m.e11, m.e12, m.e13],
                     [m.e20, m.e21, m.e22, m.e23], [m.e30, m.e31, m.e32, m.e33]], float)


def load(data_dir):
    import UnityPy
    from UnityPy.helpers.MeshHelper import MeshHandler
    env = UnityPy.load(data_dir)
    parts = {}
    texture = None
    for obj in env.objects:
        if obj.type.name == 'Texture2D' and texture is None:
            t = obj.read()
            if t.m_Name == 'EDP_color':
                texture = t.image.convert('RGBA')
        if obj.type.name != 'SkinnedMeshRenderer' or obj.assets_file.name != 'level0':
            continue
        smr = obj.read()
        mesh = smr.m_Mesh.read()
        h = MeshHandler(mesh)
        h.process()
        names = [b.read().m_GameObject.read().m_Name for b in smr.m_Bones]
        tris = []
        for sub in h.get_triangles():
            tris.extend(sub)
        idx = getattr(h, 'm_BoneIndices', None)
        parts[mesh.m_Name] = {
            'v': np.array(h.m_Vertices, float), 'n': np.array(h.m_Normals, float),
            'uv': np.array(h.m_UV0, float), 'tri': np.array(tris, int),
            'w': np.array(h.m_BoneWeights, float) if h.m_BoneWeights else None,
            'bi': np.array(idx, int) if idx else None,
            'bones': names, 'bind': [mat(m) for m in mesh.m_BindPose],
        }
    if texture is None or 'H_DDS_LowRes' not in parts:
        sys.exit('EDP_color texture or H_DDS_LowRes mesh not found in ' + data_dir)
    return parts, texture


def joints(body):
    """World position of each bone in the bind pose, by name."""
    out = {}
    for name, bind in zip(body['bones'], body['bind']):
        out[name] = np.linalg.inv(bind) @ np.array([0, 0, 0, 1.0])
    return {k: v[:3] for k, v in out.items()}


def rig_weights(part):
    """Per vertex: up to two (rig bone index, weight)."""
    n = len(part['v'])
    acc = np.zeros((n, len(BONES)))
    if part['w'] is None:
        acc[:, BONES.index('head')] = 1.0
        return acc
    for vi in range(n):
        for k in range(4):
            w = part['w'][vi][k]
            if w <= 0:
                continue
            for bone, share in fold(part['bones'][part['bi'][vi][k]]):
                acc[vi, BONES.index(bone)] += w * share
    return acc


def to_rig(p, scale):
    """Unity (x right, y up, z forward) -> rig pixels (right at -X, front at -Z)."""
    q = np.array(p, float) * scale
    q[..., 0] *= -1
    q[..., 2] *= -1
    return q


def rot_z(deg):
    a = math.radians(deg)
    return np.array([[math.cos(a), -math.sin(a), 0], [math.sin(a), math.cos(a), 0], [0, 0, 1]])


# ---------------------------------------------------------------- clothes
GREEN, GREEN_DK = np.array([0, 82, 90]), np.array([0, 58, 64])
WHITE, SILVER = np.array([240, 240, 236]), np.array([170, 176, 180])
PANTS, SHOE, SOLE = np.array([58, 60, 66]), np.array([36, 36, 40]), np.array([228, 228, 222])
GLYPH = {
    '4': ['W.W', 'W.W', 'WWW', '..W', '..W'],
    '5': ['WWW', 'W..', 'WWW', '..W', 'WWW'],
    'E': ['WWW', 'W..', 'WW.', 'W..', 'WWW'],
    'D': ['WW.', 'W.W', 'W.W', 'W.W', 'WW.'],
    'P': ['WWW', 'W.W', 'WWW', 'W..', 'W..'],
}


def glyph_at(text, u, v):
    """Is (u, v) in [0,1)^2 on an ink pixel of `text` set in 3x5 glyphs with 1-column gaps?"""
    cols = len(text) * 4 - 1
    cx, cy = int(u * cols), int(v * 5)
    if not (0 <= cx < cols and 0 <= cy < 5) or cx % 4 == 3:
        return False
    return GLYPH[text[cx // 4]][cy][cx % 4] == 'W'


def box_blur(a, r):
    pad = np.pad(a, r, mode='edge')
    c = pad.cumsum(0).cumsum(1)
    c = np.pad(c, ((1, 0), (1, 0)))
    k = 2 * r + 1
    return (c[k:, k:] - c[:-k, k:] - c[k:, :-k] + c[:-k, :-k]) / (k * k)


def text_band(text, xx, yy, half_w, top, bottom, mirror):
    """Ink test for a line of text across the band; mirror for the back of the shirt.
    In game a viewer facing his front sees rig -X on their left, so text runs toward +X."""
    if not (bottom < yy < top and abs(xx) < half_w):
        return False
    u = (xx + half_w) / (2 * half_w)
    return glyph_at(text, 1 - u if mirror else u, (top - yy) / (top - bottom))


def clothe(tex, body, j, scale):
    """Repaints the atlas under the body's triangles where his clothes are. Positions are the
    T-pose rig-space positions (before the arms come down), so arms are clear of the torso."""
    img = np.asarray(tex.resize((OUT_TEX, OUT_TEX), Image.LANCZOS)).astype(float)
    rgb = img[..., :3]
    # Clothes keep only the broad shading of the body underneath (folds, the belly's curve), not
    # skin detail: a heavy box blur of the luminance.
    lum = box_blur(rgb.mean(axis=2), 7)
    out = rgb.copy()
    painted = np.zeros(lum.shape, bool)
    covered = np.zeros(lum.shape, bool)

    pos = to_rig(body['v'], scale)
    nrm = to_rig(body['n'], 1.0)
    jr = {k: to_rig(v, scale) for k, v in j.items()}
    waist = jr['Spine'][1] + 0.4
    neck = jr['Neck'][1] - 0.6
    ankle = (jr['RightFoot'][1] + jr['LeftFoot'][1]) / 2 + 0.6
    sleeve = abs(jr['RightArm'][0]) + 2.9
    skin_mean = np.median(lum)

    H = OUT_TEX
    uv = body['uv']
    for tri in body['tri']:
        a, b, c = tri
        t = np.array([[uv[a][0] * H, (1 - uv[a][1]) * H], [uv[b][0] * H, (1 - uv[b][1]) * H],
                      [uv[c][0] * H, (1 - uv[c][1]) * H]])
        x0, y0 = np.floor(t.min(0)).astype(int)
        x1, y1 = np.ceil(t.max(0)).astype(int)
        x0, y0 = max(x0, 0), max(y0, 0)
        x1, y1 = min(x1, H - 1), min(y1, H - 1)
        if x1 < x0 or y1 < y0:
            continue
        xs, ys = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        d = (t[1, 1] - t[2, 1]) * (t[0, 0] - t[2, 0]) + (t[2, 0] - t[1, 0]) * (t[0, 1] - t[2, 1])
        if abs(d) < 1e-9:
            continue
        l0 = ((t[1, 1] - t[2, 1]) * (xs - t[2, 0]) + (t[2, 0] - t[1, 0]) * (ys - t[2, 1])) / d
        l1 = ((t[2, 1] - t[0, 1]) * (xs - t[2, 0]) + (t[0, 0] - t[2, 0]) * (ys - t[2, 1])) / d
        l2 = 1 - l0 - l1
        inside = (l0 >= -0.02) & (l1 >= -0.02) & (l2 >= -0.02)
        if not inside.any():
            continue
        P = l0[..., None] * pos[a] + l1[..., None] * pos[b] + l2[..., None] * pos[c]
        N = l0[..., None] * nrm[a] + l1[..., None] * nrm[b] + l2[..., None] * nrm[c]
        X, Y, Z, NZ = P[..., 0], P[..., 1], P[..., 2], N[..., 2]
        iy, ix = np.nonzero(inside)
        gy, gx = iy + y0, ix + x0
        covered[gy, gx] = True
        for k in range(len(iy)):
            xx, yy, zz, nz = X[iy[k], ix[k]], Y[iy[k], ix[k]], Z[iy[k], ix[k]], NZ[iy[k], ix[k]]
            col = None
            if yy < ankle:
                col = SOLE if yy < 0.45 else SHOE
            elif yy < waist:
                col = PANTS
                if yy > waist - 0.5:
                    col = PANTS * 0.8   # waistband
            elif yy < neck and abs(xx) < sleeve:
                col = GREEN
                if abs(xx) > sleeve - 1.2:
                    col = WHITE if abs(xx) < sleeve - 0.5 else GREEN   # sleeve stripe
                vneck = yy > neck - 1.6 and abs(xx) < (yy - (neck - 1.6)) * 0.9 and zz < 0
                if vneck:
                    col = None
                elif yy > neck - 0.4:
                    col = WHITE   # collar
                elif nz < -0.25 and text_band('445', xx, yy, 3.0, neck - 1.9, neck - 5.4, False):
                    col = WHITE
                elif nz > 0.25 and text_band('EDP', xx, yy, 2.4, neck - 1.0, neck - 2.7, True):
                    col = SILVER
                elif nz > 0.25 and text_band('445', xx, yy, 3.0, neck - 3.2, neck - 6.4, True):
                    col = WHITE
                if yy < waist + 0.6 and col is not None and (col == GREEN).all():
                    col = GREEN_DK   # hem
            if col is not None:
                shade = np.clip(lum[gy[k], gx[k]] / skin_mean, 0.8, 1.15)
                out[gy[k], gx[k]] = np.clip(col * shade, 0, 255)
                painted[gy[k], gx[k]] = True

    # bleed painted texels a few pixels outward so UV seams never show skin
    region = painted.copy()
    for _ in range(3):
        grow = np.zeros_like(region)
        acc = np.zeros_like(out)
        cnt = np.zeros(region.shape)
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            sr = np.roll(region, (dy, dx), (0, 1))
            so = np.roll(out, (dy, dx), (0, 1))
            m = sr & ~region & ~covered
            acc[m] += so[m]
            cnt[m] += 1
            grow |= m
        out[grow] = acc[grow] / cnt[grow][:, None]
        region |= grow
    res = img.copy()
    res[..., :3] = out
    res[..., 3] = 255
    return Image.fromarray(res.astype(np.uint8))


# ---------------------------------------------------------------- rig
def per_face_uv(cube):
    """Box-UV cube from the blocky model -> the same texture rects as per-face UVs."""
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


def moved(cube, offset, scale=1.0):
    """A blocky-model cube re-anchored at `offset` (its own centre moves there), resized."""
    o = np.array(cube['origin'], float)
    s = np.array(cube['size'], float)
    c = o + s / 2
    ns = s * scale
    return {'origin': list(np.round(c + offset - ns / 2, 3)), 'size': list(np.round(ns, 3)), 'uv': per_face_uv(cube)}


def build_geo(jr, hand_r, hand_l, head_box):
    blocky = json.load(open(os.path.join(ASSETS, 'geo/entity/edp445.geo.json')))['minecraft:geometry'][0]
    by_name = {b['name']: b for b in blocky['bones']}
    r3 = lambda p: [round(float(x), 3) for x in p]
    hip = (jr['RightUpLeg'] + jr['LeftUpLeg']) / 2
    bones = [
        {'name': 'root', 'pivot': [0, 0, 0]},
        {'name': 'leg_r', 'parent': 'root', 'pivot': r3(jr['RightUpLeg'])},
        {'name': 'leg_l', 'parent': 'root', 'pivot': r3(jr['LeftUpLeg'])},
        {'name': 'torso', 'parent': 'root', 'pivot': r3([0, hip[1], hip[2]])},
        {'name': 'head', 'parent': 'torso', 'pivot': r3(jr['Neck'])},
        {'name': 'arm_r', 'parent': 'torso', 'pivot': r3(jr['RightArm'])},
        {'name': 'arm_l', 'parent': 'torso', 'pivot': r3(jr['LeftArm'])},
    ]
    # beanie sized to his real head: the blocky beanie is 10 wide on a 10-wide head
    (hx0, hy0, hz0), (hx1, hy1, hz1) = head_box
    hw = max(hx1 - hx0, hz1 - hz0)
    k = (hw + 0.5) / 10.0
    top = np.array([(hx0 + hx1) / 2, hy1, (hz0 + hz1) / 2])
    old = by_name['beanie']
    old_top = np.array([0, 33.0, 0])
    beanie = []
    for c in old['cubes']:
        o = np.array(c['origin'], float)
        s = np.array(c['size'], float)
        centre = o + s / 2
        nc = top + (centre - old_top) * k + np.array([0, -0.6, 0])
        ns = s * k
        beanie.append({'origin': r3(nc - ns / 2), 'size': r3(ns), 'uv': per_face_uv(c)})
    bones.append({'name': 'beanie', 'parent': 'head', 'pivot': r3(top), 'cubes': beanie})
    for prop, hand, arm in (('taco', hand_r, 'arm_r'), ('phone', hand_l, 'arm_l')):
        old = by_name[prop]
        off = hand - np.array(old['pivot'], float)
        bones.append({'name': prop, 'parent': arm, 'pivot': r3(hand),
                      'cubes': [moved(c, off) for c in old['cubes']]})
    geo = {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': 'geometry.edp445_mesh', 'texture_width': 128, 'texture_height': 128,
                        'visible_bounds_width': 3, 'visible_bounds_height': 3.5, 'visible_bounds_offset': [0, 1.5, 0]},
        'bones': bones}]}
    path = os.path.join(ASSETS, 'geo/entity/edp445_mesh.geo.json')
    with open(path, 'w') as f:
        json.dump(geo, f, indent='\t')
    return path


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    parts, tex = load(sys.argv[1])
    body = parts['H_DDS_LowRes']
    j = joints(body)
    scale = HEAD_TOP_PX / body['v'][:, 1].max()
    jr = {k: to_rig(v, scale) for k, v in j.items()}

    clothed = clothe(tex, body, j, scale)
    os.makedirs(os.path.join(ASSETS, 'textures/entity'), exist_ok=True)
    clothed.save(os.path.join(ASSETS, 'textures/entity/edp445_mesh.png'))

    positions, normals, uvs, indices, joints_out, weights_out = [], [], [], [], [], []
    hands = {}
    head_pts = []
    for name in ('H_DDS_LowRes', 'l_L_eye', 'l_R_eye', 'l_TeethUp', 'l_TeethDown', 'l_L_gland', 'l_R_gland'):
        if name not in parts:
            continue
        p = parts[name]
        base = len(positions) // 3   # vertices already written by earlier parts
        w = rig_weights(p)
        P = to_rig(p['v'], scale)
        N = to_rig(p['n'], 1.0)
        # swing the arms down from the T-pose, about each shoulder joint
        for arm, shoulder, sign in (('arm_r', jr['RightArm'], 1), ('arm_l', jr['LeftArm'], -1)):
            R = rot_z(sign * ARM_DROP_DEG)
            a = w[:, BONES.index(arm)][:, None]
            P = P + a * ((P - shoulder) @ R.T + shoulder - P)
            N = N + a * (N @ R.T - N)
        N = N / np.maximum(np.linalg.norm(N, axis=1, keepdims=True), 1e-6)
        for vi in range(len(P)):
            order = np.argsort(-w[vi])[:2]
            ws = w[vi][order]
            ws = ws / ws.sum() if ws.sum() > 0 else np.array([1.0, 0.0])
            joints_out.extend(int(o) for o in order)
            weights_out.extend(round(float(x), 3) for x in ws)
            if w[vi][BONES.index('head')] > 0.95 and name == 'H_DDS_LowRes':
                head_pts.append(P[vi])
        positions.extend(P.round(3).flatten().tolist())
        normals.extend(N.round(3).flatten().tolist())
        uvs.extend(np.round(p['uv'], 4).flatten().tolist())
        indices.extend((p['tri'].flatten() + base).tolist())
        if name == 'H_DDS_LowRes':
            for hand, shoulder, sign, key in (('RightHand', jr['RightArm'], 1, 'r'), ('LeftHand', jr['LeftArm'], -1, 'l')):
                hp = (jr[hand] - shoulder) @ rot_z(sign * ARM_DROP_DEG).T + shoulder
                hands[key] = hp + np.array([0, -1.2, 0])   # the palm, a little below the wrist joint
    vcount = len(positions) // 3
    assert max(indices) < vcount, (max(indices), vcount)

    mesh = {
        'credit': 'Converted from EDP SIM by tools/gen_edp_mesh.py',
        'texture': 'papon:textures/entity/edp445_mesh.png',
        'bones': BONES,
        'positions': positions, 'normals': normals, 'uvs': uvs, 'indices': indices,
        'joints': joints_out, 'weights': weights_out,
    }
    os.makedirs(os.path.join(ASSETS, 'meshes'), exist_ok=True)
    with open(os.path.join(ASSETS, 'meshes/edp445.json'), 'w') as f:
        json.dump(mesh, f, separators=(',', ':'))
    hp = np.array(head_pts)
    print(build_geo(jr, hands['r'], hands['l'], (hp.min(0), hp.max(0))))
    print('mesh', vcount, 'vertices,', len(indices) // 3, 'triangles; scale', round(scale, 3), 'px/m')
    for k in ('RightUpLeg', 'Neck', 'RightArm', 'Head'):
        print(' ', k, np.round(jr[k], 2))


if __name__ == '__main__':
    main()
