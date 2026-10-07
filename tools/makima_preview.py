"""Stills of Makima's poses (gen_makima_anims.py), each framed on its own over a ground grid, to tune them
without starting Minecraft.

  python3.11 tools/makima_preview.py out.png missionary doggy [view=34|side|front|back|top] [t=0]

A figure of a player (1.8 tall, a box per limb) can stand in where the rider goes: env MAKIMA_RIDER="x,y,z,yaw,pitch"
in blocks from her feet (her front toward -Z), pitch tipping the body forward about the feet.
"""
import json
import os
import subprocess
import sys

import numpy as np

import anim_preview as AP

ROOT = AP.ROOT
ASSETS = AP.ASSETS


def posed(anim_name, t):
    geo = json.load(open(os.path.join(ASSETS, 'geo/entity/makima_mesh.geo.json')))['minecraft:geometry'][0]
    mesh = json.load(open(os.path.join(ASSETS, 'meshes/makima.json')))
    anim = json.load(open(os.path.join(ASSETS, 'animations/entity/makima.animation.json')))['animations']['animation.makima.' + anim_name]
    P = np.array(mesh['positions'], float).reshape(-1, 3)
    J = np.array(mesh['joints']).reshape(-1, 2)
    W = np.array(mesh['weights'], float).reshape(-1, 2)
    M, _ = AP.pose(geo, anim, t)
    Ph = np.hstack([P, np.ones((len(P), 1))])
    X = np.zeros_like(P)
    for k in range(2):
        mats = np.stack([M[mesh['bones'][j]] for j in J[:, k]])
        X += W[:, k:k + 1] * np.einsum('nij,nj->ni', mats, Ph)[:, :3]
    return X, mesh


def main():
    out, names = sys.argv[1], [a for a in sys.argv[2:] if '=' not in a]
    opts = dict(a.split('=') for a in sys.argv[2:] if '=' in a)
    frames, mesh = [], None
    for n in names:
        X, mesh = posed(n, float(opts.get('t', 0)))
        frames.append(X.round(3).tolist())
    rider = os.environ.get('MAKIMA_RIDER')
    tmp = os.path.join(ROOT, 'tools/.cache/makima_preview.json')
    json.dump({'texture': os.path.join(ASSETS, mesh['texture'].split(':')[1]), 'uvs': mesh['uvs'],
               'faces': np.array(mesh['indices']).reshape(-1, 3).tolist(), 'frames': frames, 'names': names,
               'view': opts.get('view', '34'), 'out': out,
               'rider': [float(v) for v in rider.split(',')] if rider else None}, open(tmp, 'w'))
    r = subprocess.run([AP.BLENDER, '-b', '--factory-startup', '--python', os.path.join(ROOT, 'tools/blender_makima.py'), '--', tmp],
                       capture_output=True, text=True)
    if 'wrote' not in r.stdout:
        print(r.stdout[-2000:], r.stderr[-2000:])
    from PIL import Image
    parts = [out.replace('.png', '_%d.png' % k) for k in range(len(names))]
    ims = [Image.open(p) for p in parts]
    sheet = Image.new('RGB', (sum(i.width for i in ims), max(i.height for i in ims)))
    x = 0
    for i, p in zip(ims, parts):
        sheet.paste(i, (x, 0))
        x += i.width
        os.remove(p)
    sheet.save(out)
    print(out)


if __name__ == '__main__':
    main()
