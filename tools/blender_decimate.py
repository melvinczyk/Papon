"""Blender pass for the mesh celebrities: decimates the body, keeps the face, hands and eyes dense.

  Blender -b --python tools/blender_decimate.py -- <in.npz> <out.npz> <ratio>

Run by mh_human.py (Character.optimize); not meant to be run by hand. The input is the painted
MakeHuman mesh: vertex positions, skin weights (one column per rig bone), a `protect` weight per
vertex (1 = keep every vertex: face, ears, hands, eyeballs, teeth), and quads with their corner UVs.
Blender's collapse decimator (mirror-symmetric in X) reduces the rest to `ratio` of its faces,
interpolating UVs and weights, and the triangles it leaves are joined back into quads, which is what
Minecraft's entity renderer draws. The output has the same fields for the reduced mesh, with
polygons of 3 or 4 corners.
"""
import sys

import bmesh
import bpy
import numpy as np


def main():
    src, dst, ratio = sys.argv[sys.argv.index('--') + 1:][:3]
    d = np.load(src)
    P, W, protect = d['P'], d['W'], d['protect']
    faces, uvs = d['faces'], d['uvs']          # (F, 4) vertex indices, (F, 4, 2) corner UVs

    bpy.ops.wm.read_factory_settings(use_empty=True)
    mesh = bpy.data.meshes.new('body')
    mesh.from_pydata(P.tolist(), [], faces.tolist())
    uv = mesh.uv_layers.new(name='uv')
    uv.data.foreach_set('uv', uvs.reshape(-1).astype(np.float32))
    ob = bpy.data.objects.new('body', mesh)
    bpy.context.scene.collection.objects.link(ob)
    for j in range(W.shape[1]):
        g = ob.vertex_groups.new(name='b%d' % j)
        for v in np.nonzero(W[:, j] > 1e-4)[0]:
            g.add([int(v)], float(W[v, j]), 'REPLACE')
    keep = ob.vertex_groups.new(name='protect')
    for v in range(len(P)):
        keep.add([v], float(protect[v]), 'REPLACE')

    # collapse decimation only lets go of what is outside the group: weight 1 = untouched
    mod = ob.modifiers.new('dec', 'DECIMATE')
    mod.decimate_type = 'COLLAPSE'
    mod.ratio = float(ratio)
    mod.use_symmetry = True
    mod.symmetry_axis = 'X'
    mod.vertex_group = 'protect'
    mod.invert_vertex_group = True
    mod.vertex_group_factor = 1.0
    bpy.context.view_layer.objects.active = ob
    bpy.ops.object.modifier_apply(modifier='dec')

    bm = bmesh.new()
    bm.from_mesh(ob.data)
    bmesh.ops.join_triangles(bm, faces=bm.faces[:], cmp_uvs=True, cmp_seam=False, cmp_sharp=False,
                             cmp_materials=False, angle_face_threshold=0.7, angle_shape_threshold=0.9)
    bm.to_mesh(ob.data)
    bm.free()

    me = ob.data
    n = len(me.vertices)
    co = np.zeros(n * 3, np.float32)
    me.vertices.foreach_get('co', co)
    W2 = np.zeros((n, W.shape[1]), np.float32)
    for v in me.vertices:
        for g in v.groups:
            name = ob.vertex_groups[g.group].name
            if name.startswith('b'):
                W2[v.index, int(name[1:])] = g.weight
    sizes = np.zeros(len(me.polygons), np.int32)
    me.polygons.foreach_get('loop_total', sizes)
    loops = np.zeros(len(me.loops), np.int32)
    me.loops.foreach_get('vertex_index', loops)
    luv = np.zeros(len(me.loops) * 2, np.float32)
    me.uv_layers['uv'].data.foreach_get('uv', luv)
    np.savez(dst, P=co.reshape(-1, 3), W=W2, sizes=sizes, loops=loops, uvs=luv.reshape(-1, 2))
    print('decimated: %d -> %d vertices, %d polygons (%d quads)'
          % (len(P), n, len(sizes), int((sizes == 4).sum())))


main()
