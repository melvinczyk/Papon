#!/usr/bin/env python3
"""Makima in a Blender scene from the user's reference (2026-10-07): seen from below and behind in a backlit alley, hands on
her hips, looking back over her right shoulder; white shirt under a black patent corset laced up the back, blue jeans,
thigh-high patent boots on heels, black gloves, the braid flying out.

  python tools/makima_scene.py                 # pose, write tools/out/makima_scene.blend and render .png
  python tools/makima_scene.py preview out.png # the pose only, in software (tools/makima_view.py), several views

Her body is the game's (meshes/makima.json, posed with tools/gen_makima_anims.py's IK and skinned as the game skins it); the
outfit is cut from her own surface (a shell offset along her normals over the faces it covers, by bone and height in her
rest pose), so it fits her in any pose. Blender side: tools/blender_makima_scene.py.
"""
import json
import math
import os
import subprocess
import sys

import numpy as np

import anim_preview as AP
import gen_makima_anims as GA
import makima_view as MV
from gen_makima_anims import F

ROOT = AP.ROOT
OUT = os.path.join(ROOT, 'tools/out')
BLENDER = os.environ.get('BLENDER') or next((b for b in (AP.BLENDER, 'C:/Program Files/Blender Foundation/Blender 5.2/blender.exe')
                                             if os.path.exists(b)), AP.BLENDER)

# the trunk's turns (degrees, GeckoLib: rot X < 0 tips back, Y turns her toward her left): hips rolled up on her left leg, the
# small of her back arched, the shoulders turned toward her right and the head over that shoulder, chin down at the camera
TRUNK = {'torso': (-4, -6, 4), 'spine': (-6, 10, -2), 'chest': (-3, 16, 2), 'neck': (-2, 38, 0), 'head': (-6, 66, -4)}


def pose():
    p = GA.body(TRUNK)
    hip = {s: GA.at(GA.body({}), 'leg_' + s) for s in 'lr'}
    # feet a little more than hip width, the left leg straight under her weight, the right eased out
    p = GA.legs(p, {'l': (hip['l'] * [1, 0, 1] + F(0.0, 0.1, 0.07), F(1, 0, 0.1)),
                    'r': (hip['r'] * [1, 0, 1] + F(0.05, 0.1, -0.1), F(1, 0, -0.3))},
                {'l': F(1, -0.15, 0.2), 'r': F(1, -0.2, -0.35)})
    # hands on her hips, a little behind, the elbows out and back
    w = GA.at(p, 'torso')
    p = GA.arms(p, {'l': (w + F(-0.06, -0.02, 0.2), F(-0.45, 0.2, 1)),
                    'r': (w + F(-0.02, 0.03, -0.2), F(-0.4, 0.25, -1))}, (0.1, 0.2, 10))
    p = GA.hand_to(p, 'l', F(-0.35, -1, 0.05), F(0, 0, -1))     # fingers down over the back of her hip
    p = GA.hand_to(p, 'r', F(0.25, -1, 0), F(0.1, 0, 1))        # on her hip, fingers down and forward
    p = GA.hands(p, 'soft')
    # the braid swung out behind her to her left, as if she had just turned
    p = GA.hang(p, [('braid', 'braid2'), ('braid2', 'braid3'), ('braid3', 'braid_tip')], 'chest', None,
                stiff=(0.15, 0.45, 0.4), fall=F(-0.45, -0.25, 1.0))
    p = GA.hang(p, [('tie', 'tie_tip')], 'chest', (0, 0, -1), stiff=(0.15,), rest_out=True)
    return p


def anim(p):
    return {'bones': {b: {'rotation': {'0': {'vector': list(r)}}, 'position': {'0': {'vector': list(q)}}} for b, (r, q) in p.items()}}


def posed(p):
    geo, mesh, _ = MV.load()
    X = MV.posed(geo, mesh, anim(p), 0, MV.expression('look'), (-0.6, 0.35))
    return X, MV.posed.normals, mesh, geo


def preview(out):
    X, N, mesh, _ = posed(pose())
    from PIL import Image
    tex = np.asarray(Image.open(os.path.join(AP.ASSETS, mesh['texture'].split(':')[1])).convert('RGBA'), np.float32) / 255
    MV.over_tex = tex
    UV = np.array(mesh['uvs'], float).reshape(-1, 2)
    UV[:, 1] = 1 - UV[:, 1]
    idx = np.array(mesh['indices'])
    tris = idx[:mesh['groups']['blush'][0]].reshape(-1, 3)
    frames = [(v, MV.render(X, N, UV, tris, tex[..., :3], [], vv, (420, 560)))
              for v, vv in [('back', (180, 8, 4.0, 0, 1.0, 0)), ('low', (160, -22, 2.6, 0, 1.2, 0)),
                            ('side', (90, 8, 4.0, 0, 1.0, 0)), ('front', (0, 8, 4.0, 0, 1.0, 0)),
                            ('face', (200, 0, 1.0, 0, 1.75, 0))]]
    MV.sheet(out, frames, (420, 560))


# ---------------------------------------------------------------- the outfit, cut from her surface
def rest_bone(mesh):
    """Each vertex's heaviest bone (name) and her rest positions (rig pixels)."""
    k = mesh['influences']
    J = np.array(mesh['joints']).reshape(-1, k)
    W = np.array(mesh['weights'], float).reshape(-1, k)
    top = J[np.arange(len(J)), W.argmax(1)]
    names = np.array(mesh['bones'])[top]
    return names, np.array(mesh['positions'], float).reshape(-1, 3)


def regions(mesh):
    """Per triangle: which atlas quad it samples (skin, shirt, hair, trouser, eyes) from its uv centre."""
    UV = np.array(mesh['uvs'], float).reshape(-1, 2)
    idx = np.array(mesh['indices'])
    tris = idx[:mesh['groups']['blush'][0]].reshape(-1, 3)
    c = UV[tris].mean(1)
    u, v = c[:, 0] * 2048, (1 - c[:, 1]) * 2048        # atlas pixels, down from the top (gen_makima_mesh.QUADS)
    lab = np.full(len(tris), 'skin', object)
    lab[(u >= 1024) & (v < 1024)] = 'shirt'
    lab[(u < 1024) & (v >= 1024)] = 'hair'
    lab[(u >= 1024) & (u < 1792) & (v >= 1024)] = 'trouser'
    lab[(u >= 1792) & (v >= 1024)] = 'eyes'
    return tris, lab


UNDER_BUST = GA.PIV['chest'][1] + 0.3      # rig pixels: the corset's top edge, a touch under her bust
CORSET_LOW = GA.PIV['torso'][1] + 0.55       # and its bottom, over the jeans' waistband
BOOT_TOP = 0.5                               # of the way from the knee up to the hip joint


def cuts(M):
    """Each garment's clean edges: planes (point, normal: what lies beyond is cut away) in the posed frame, blocks."""
    def plane(bone, p, n):
        q = (M[bone] @ np.append(p, 1))[:3] / 16
        m = M[bone][:3, :3] @ np.asarray(n, float)
        q[0] *= -1
        m[0] *= -1
        return [q.round(5).tolist(), (m / np.linalg.norm(m)).round(5).tolist()]
    out = {'corset': [plane('torso', (0, CORSET_LOW, 0), (0, -1, 0)), plane('chest', (0, UNDER_BUST, 0), (0, 1, 0))]}
    for s in 'lr':
        hip, knee = GA.PIV['leg_' + s], GA.PIV['shin_' + s]
        out['boot_' + s] = [plane('leg_' + s, knee + (hip - knee) * BOOT_TOP, hip - knee)]
    return out


def outfit(mesh):
    """Triangle masks for each garment (by heaviest bone of every corner and height in her rest pose)."""
    names, P = rest_bone(mesh)
    tris, lab = regions(mesh)
    y = P[:, 1]
    piv = GA.PIV
    knee = piv['shin_l'][1]
    hipj = piv['leg_l'][1]
    under_bust = UNDER_BUST
    trunk = {'torso', 'spine', 'chest', 'glute_l', 'glute_r', 'breast_l', 'breast_r', 'leg_l', 'leg_r'}
    legb = {'leg_l', 'leg_r', 'shin_l', 'shin_r', 'foot_l', 'foot_r'}
    handb = {b for b in mesh['bones'] if b.startswith('f_') or b.startswith('hand_')}
    allin = lambda ok: ok[tris].all(1)
    anyin = lambda ok: ok[tris].any(1)
    t_y = y[tris].mean(1)

    is_trunk = np.isin(names, list(trunk))
    corset = allin(is_trunk) & (t_y < under_bust + 1.2) & (t_y > piv['torso'][1] - 0.6) & np.isin(lab, ['shirt', 'trouser'])
    # a thigh-high boot per leg: the leg up to her hip joint (cut clean at mid-thigh in Blender: cuts())
    boots = {s: allin(np.isin(names, ['leg_' + s, 'shin_' + s, 'foot_' + s])) & (t_y < hipj + 0.5) &
             np.isin(lab, ['trouser', 'skin']) for s in 'lr'}
    # gloves: the hand (the shirt's cuffs stay over them)
    glove = allin(np.isin(names, list(handb))) & (lab == 'skin')
    return tris, lab, {'corset': corset, 'boot_l': boots['l'], 'boot_r': boots['r'], 'glove': glove}


def lacing(mesh, tris, corset, X, N, rows=8, gap=0.5):
    """The corset's lacing up the small of her back: per row the eyelet either side of her spine (posed, blocks) and the
    normal out of her there."""
    _, P = rest_bone(mesh)
    vs = np.unique(tris[corset])
    back = vs[P[vs, 2] > 0.4]
    out = []
    for y in np.linspace(CORSET_LOW + 0.45, UNDER_BUST - 0.35, rows):
        row = []
        for side in (gap, -gap):
            d = np.abs(P[back, 0] - side) + np.abs(P[back, 1] - y) - 0.05 * P[back, 2]
            v = back[d.argmin()]
            row.append([X[v].round(5).tolist(), N[v].round(4).tolist()])
        out.append(row)
    return out


def main():
    if len(sys.argv) > 2 and sys.argv[1] == 'preview':
        return preview(sys.argv[2])
    os.makedirs(OUT, exist_ok=True)
    p = pose()
    X, N, mesh, geo = posed(p)
    tris, lab, garments = outfit(mesh)
    M = GA.world(p)

    def where(name, bone=None):
        loc = GA.LOC[name] if name in GA.LOC else GA.PIV[name]
        q = (M[bone or GA.LOC_BONE.get(name, name)] @ np.append(loc, 1))[:3] / 16
        q[0] *= -1
        return q.round(4).tolist()
    marks = {k: where(k) for k in ('crotch', 'seat', 'eyes', 'ankle_l', 'ankle_r', 'toe_l', 'toe_r', 'wrist_l', 'wrist_r')}
    marks.update({k: where(k) for k in ('leg_l', 'leg_r', 'torso', 'chest', 'spine', 'head', 'foot_l', 'foot_r', 'shin_l', 'shin_r', 'glute_l', 'glute_r')})
    # each foot's frame for the heels (heel to toe, blocks)
    data = {'texture': os.path.join(AP.ASSETS, mesh['texture'].split(':')[1]), 'positions': X.round(5).tolist(),
            'normals': N.round(4).tolist(), 'uvs': mesh['uvs'], 'tris': tris.tolist(), 'labels': lab.tolist(),
            'garments': {k: np.nonzero(v)[0].tolist() for k, v in garments.items()}, 'marks': marks,
            'cuts': cuts(M), 'laces': lacing(mesh, tris, garments['corset'], X, N),
            'blend': os.path.join(OUT, 'makima_scene.blend'), 'out': os.path.join(OUT, 'makima_scene.png'),
            'samples': int(os.environ.get('MAKIMA_SAMPLES', '64')), 'engine': os.environ.get('MAKIMA_ENGINE', 'cycles'),
            'size': [int(v) for v in os.environ.get('MAKIMA_SIZE', '768x1280').split('x')]}
    tmp = os.path.join(ROOT, 'tools/.cache/makima_scene.json')
    json.dump(data, open(tmp, 'w'))
    r = subprocess.run([BLENDER, '-b', '--factory-startup', '--python', os.path.join(ROOT, 'tools/blender_makima_scene.py'),
                        '--', tmp], capture_output=True, text=True)
    print(r.stdout[-3000:], r.stderr[-3000:])


if __name__ == '__main__':
    main()
