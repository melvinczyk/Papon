"""Blender half of gen_makima_mesh.py: reads tools/src/makima/makima_pose.fbx and dumps its mesh.

  $BLENDER -b --factory-startup --python tools/makima_fbx.py

Writes tools/.cache/makima/raw.npz: vertex positions in metres with the object's transform applied
(Z up, her front toward -Y), triangles (vertex ids), their corner UVs (wrapped into 0..1) and material
per triangle (MATS order).
"""
import os

import bpy
import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MATS = ['Skin', 'trouser', 'Shirt', 'Hair', 'Eye']

bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.fbx(filepath=os.path.join(ROOT, 'tools/src/makima/makima_pose.fbx'))
o = next(o for o in bpy.data.objects if o.type == 'MESH')
m = o.data
m.transform(o.matrix_world)
m.calc_loop_triangles()
uv = m.uv_layers.active.data
P = np.array([v.co[:] for v in m.vertices])
tris, uvs, mats = [], [], []
for t in m.loop_triangles:
    tris.append(t.vertices[:])
    uvs.append([uv[l].uv[:] for l in t.loops])
    mats.append(MATS.index(m.materials[t.material_index].name))
uvs = np.array(uvs) % 1.0
out = os.path.join(ROOT, 'tools/.cache/makima')
os.makedirs(out, exist_ok=True)
np.savez(os.path.join(out, 'raw.npz'), P=P, tris=np.array(tris), uvs=uvs, mats=np.array(mats))
print('makima: %d vertices, %d triangles' % (len(P), len(tris)))
